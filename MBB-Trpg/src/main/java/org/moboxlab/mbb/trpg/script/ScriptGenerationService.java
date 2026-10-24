package org.moboxlab.mbb.trpg.script;

import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import org.moboxlab.mbb.trpg.ai.AiClient;
import org.moboxlab.mbb.trpg.ai.AiErrorTypes;
import org.moboxlab.mbb.trpg.ai.AiRequest;
import org.moboxlab.mbb.trpg.ai.AiResult;
import org.moboxlab.moboxbot.API.PluginLogger;

/**
 * 剧本生成服务：一次性的 MBB-AI chat 调用 + 与导入完全相同的校验与落盘链路（§8.3）。
 *
 * <p>插件内只有一个生成槽位：调用方先用 {@link #tryReserve()} 抢占槽位，抢到后再把
 * {@link #generateReserved} 交给装配层提供的单线程 daemon executor；抢不到立刻回
 * {@code E_AI_BUSY}，不排队。这样「预检 → 入队 → 开跑」之间不存在第二个任务插队的窗口。</p>
 *
 * <p>任何失败都不落盘：原始返回（空内容写占位记录）存入 imports/rejected/，
 * 回执带上错误码与手动导入退路；失败与成功都记一条中文日志，便于不翻存档也能排查。</p>
 */
public final class ScriptGenerationService {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String DEFAULT_AUTHOR = "MBB-AI";
    private static final int MAX_REPORTED = 5;

    private final ScriptLibrary library;
    private final ScriptValidator validator;
    private final AiClient aiClient;
    private final ScriptRejectedStore rejected;
    private final PluginLogger logger;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ScriptGenerationService(ScriptLibrary library, ScriptValidator validator,
                                   AiClient aiClient, ScriptRejectedStore rejected) {
        this(library, validator, aiClient, rejected, null);
    }

    public ScriptGenerationService(ScriptLibrary library, ScriptValidator validator,
                                   AiClient aiClient, ScriptRejectedStore rejected,
                                   PluginLogger logger) {
        if (library == null) {
            throw new IllegalArgumentException("剧本库不能为空");
        }
        if (validator == null) {
            throw new IllegalArgumentException("校验器不能为空");
        }
        if (aiClient == null) {
            throw new IllegalArgumentException("AI 客户端不能为空");
        }
        if (rejected == null) {
            throw new IllegalArgumentException("拒绝原文存档不能为空");
        }
        this.library = library;
        this.validator = validator;
        this.aiClient = aiClient;
        this.rejected = rejected;
        this.logger = logger;
    }

    /** 当前是否有生成任务在跑。 */
    public boolean isRunning() {
        return running.get();
    }

    /** 释放已抢占但未能提交执行的槽位（提交执行器失败时用）。 */
    public void releaseReservation() {
        running.set(false);
    }

    /**
     * 抢占唯一的生成槽位；抢到后必须调用 {@link #generateReserved} 释放。
     *
     * @return 抢到返回 true，已有任务在跑返回 false
     */
    public boolean tryReserve() {
        return running.compareAndSet(false, true);
    }

    /**
     * 同步生成一份剧本并落盘；调用方负责放到自有 executor 上执行，并在内部释放槽位。
     *
     * @param theme      题材（不含空白）
     * @param players    玩家人数，同时作为 minPlayers 与 maxPlayers
     * @param minutes    预计时长
     * @param operatorId 发起人（只用于拒绝原文文件名与日志）
     */
    public ScriptGenerationOutcome generate(String theme, int players, int minutes,
                                            long operatorId) {
        if (!tryReserve()) {
            return failure(ScriptErrorCodes.E_AI_BUSY, "", null, 0, 0L);
        }
        return generateReserved(theme, players, minutes, operatorId);
    }

    /**
     * 在已抢占槽位的前提下同步生成；无论成功失败都会释放槽位。
     *
     * @see #tryReserve()
     */
    public ScriptGenerationOutcome generateReserved(String theme, int players, int minutes,
                                                    long operatorId) {
        long startedAt = System.currentTimeMillis();
        try {
            AiRequest request = ScriptGenerationPrompts.build(theme, players, minutes);
            AiResult result = aiClient.chat(request);
            if (!result.success()) {
                String code = mapError(result.errorType());
                return fail(code, result.content(), operatorId, null, 0, startedAt);
            }
            String raw = stripFence(result.content());
            JSONObject json;
            try {
                json = JSON.parseObject(raw);
            } catch (RuntimeException error) {
                return fail(ScriptErrorCodes.E_AI_OUTPUT, result.content(), operatorId, null, 0,
                        startedAt);
            }
            if (json == null) {
                return fail(ScriptErrorCodes.E_AI_OUTPUT, result.content(), operatorId, null, 0,
                        startedAt);
            }
            json.put("id", nextId());
            if (isBlank(json.getString("author"))) {
                json.put("author", DEFAULT_AUTHOR);
            }
            byte[] content = json.toJSONString().getBytes(UTF8);
            ScriptValidationResult validation = validator.validateGenerated(content);
            if (!validation.ok()) {
                return fail(ScriptErrorCodes.E_AI_VALIDATION, result.content(), operatorId,
                        validation.reportedErrors(), validation.errorCount(), startedAt);
            }
            return save(content, result.content(), operatorId, startedAt);
        } finally {
            running.set(false);
        }
    }

    private ScriptGenerationOutcome save(byte[] content, String raw, long operatorId,
                                         long startedAt) {
        try {
            ScriptImportResult imported = library.saveGenerated(content);
            ScriptInfo info = imported.script();
            log("剧本生成成功：id=" + info.id() + "，发起人=" + operatorId + "，耗时="
                    + (System.currentTimeMillis() - startedAt) + "ms");
            return ScriptGenerationOutcome.success(info, info.sceneCount(),
                    System.currentTimeMillis() - startedAt);
        } catch (ScriptValidationException error) {
            ScriptValidationResult failedResult = error.validation();
            return fail(ScriptErrorCodes.E_AI_VALIDATION, raw, operatorId,
                    failedResult == null ? null : failedResult.reportedErrors(),
                    failedResult == null ? 0 : failedResult.errorCount(), startedAt);
        } catch (ScriptException error) {
            return fail(ScriptErrorCodes.E_AI_FAILED, raw, operatorId, null, 0, startedAt);
        }
    }

    /** 失败统一出口：存档原文、记日志、组装回执结果。 */
    private ScriptGenerationOutcome fail(String code, String raw, long operatorId,
                                         List<ScriptIssue> errors, int errorCount,
                                         long startedAt) {
        String saved = saveRaw(raw, operatorId, code);
        warn("剧本生成失败：" + code + "，发起人=" + operatorId
                + (saved.isEmpty() ? "" : "，原文已存入 imports/rejected/" + saved));
        return failure(code, saved, errors, errorCount, System.currentTimeMillis() - startedAt);
    }

    /** 生成剧本 id：gen-<yyyyMMddHHmmss>-<4 位 36 进制随机>。 */
    static String nextId() {
        String time = new SimpleDateFormat("yyyyMMddHHmmss", Locale.ROOT).format(new Date());
        int random = ThreadLocalRandom.current().nextInt(36 * 36 * 36 * 36);
        String suffix = Integer.toString(random, 36);
        StringBuilder padded = new StringBuilder();
        for (int i = suffix.length(); i < 4; i++) {
            padded.append('0');
        }
        padded.append(suffix);
        return "gen-" + time + "-" + padded;
    }

    /**
     * 剥掉整条内容外面的一对 Markdown 代码围栏；只有围栏包住整体时才剥。
     */
    static String stripFence(String content) {
        String text = content == null ? "" : content.trim();
        if (!text.startsWith("```") || !text.endsWith("```") || text.length() < 6) {
            return text;
        }
        int firstNewline = text.indexOf('\n');
        if (firstNewline < 0) {
            return text;
        }
        String inner = text.substring(firstNewline + 1, text.length() - 3);
        return inner.trim();
    }

    private String saveRaw(String raw, long operatorId, String code) {
        java.io.File file = rejected.save(raw, operatorId, code);
        return file == null ? "" : file.getName();
    }

    /**
     * 把 AI 端错误类型映射为插件侧六类 {@code E_AI_*}。
     *
     * <p>只有「服务关闭」算 disabled；参数错误、网络失败等都归 E_AI_FAILED，
     * 与 README 的六类分类保持一致。</p>
     */
    static String mapError(String errorType) {
        if (AiErrorTypes.DISABLED.equals(errorType)) {
            return ScriptErrorCodes.E_AI_DISABLED;
        }
        if (AiErrorTypes.BUSY.equals(errorType)) {
            return ScriptErrorCodes.E_AI_BUSY;
        }
        if (AiErrorTypes.TIMEOUT.equals(errorType)) {
            return ScriptErrorCodes.E_AI_TIMEOUT;
        }
        return ScriptErrorCodes.E_AI_FAILED;
    }

    private static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    private static ScriptGenerationOutcome failure(String code, String savedFile,
                                                   List<ScriptIssue> errors, int errorCount,
                                                   long elapsedMs) {
        return ScriptGenerationOutcome.failure(code, savedFile,
                errors == null ? Collections.<ScriptIssue>emptyList()
                        : new java.util.ArrayList<ScriptIssue>(
                                errors.subList(0, Math.min(MAX_REPORTED, errors.size()))),
                errorCount, elapsedMs);
    }

    private void warn(String message) {
        if (logger != null) {
            logger.sendWarn("[MBB-Trpg] " + message);
        }
    }

    private void log(String message) {
        if (logger != null) {
            logger.sendInfo("[MBB-Trpg] " + message);
        }
    }
}
