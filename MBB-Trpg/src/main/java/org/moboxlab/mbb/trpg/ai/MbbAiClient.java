package org.moboxlab.mbb.trpg.ai;

import java.util.Locale;

import org.moboxlab.moboxbot.API.Plugin;
import org.moboxlab.moboxbot.API.PluginService;
import org.moboxlab.mbb.trpg.config.TrpgConfig;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * MBB-AI {@code chat} 服务的插件侧适配：把 {@link AiRequest} 翻译成消息与参数，再把响应映射成
 * {@link AiResult}。
 *
 * <p>只通过公开的 {@link PluginService#call(String, JSONObject)} 访问 MBB-AI，不触碰主程序内部类。
 * 网络与参数异常统一折叠成失败结果，不向上抛异常。</p>
 */
public final class MbbAiClient implements AiClient {

    /** MBB-AI 服务名。 */
    public static final String SERVICE_NAME = "MBB-AI";

    private final Plugin plugin;
    private final TrpgConfig config;

    public MbbAiClient(Plugin plugin) {
        this(plugin, null);
    }

    /**
     * @param plugin 插件实例
     * @param config 插件配置；用于透传 {@code aiHost.retryCount}，为 null 时按 0 处理
     */
    public MbbAiClient(Plugin plugin, TrpgConfig config) {
        if (plugin == null) {
            throw new IllegalArgumentException("插件实例不能为空");
        }
        this.plugin = plugin;
        this.config = config;
    }

    @Override
    public AiResult chat(AiRequest request) {
        long startTime = System.currentTimeMillis();
        if (request == null) {
            return AiResult.failure(AiErrorTypes.PARAMS, false, 0L);
        }
        PluginService ai = plugin.getServer().getPluginManager().getService(SERVICE_NAME);
        if (ai == null) {
            return AiResult.failure(AiErrorTypes.DISABLED, false, elapsed(startTime));
        }
        JSONObject params = new JSONObject(true);
        params.put("messages", messages(request));
        if (request.timeoutSeconds() > 0) {
            params.put("timeoutSeconds", request.timeoutSeconds());
        }
        if (request.maxTokens() > 0) {
            params.put("maxTokens", request.maxTokens());
        }
        if (request.temperature() >= 0.0D) {
            params.put("temperature", request.temperature());
        }
        if (!request.sessionId().isEmpty()) {
            params.put("sessionId", request.sessionId());
        }
        // 插件侧只管调用次数；MBB-AI 的服务层重试由 provider 配置决定（§9.5 默认 0）。
        params.put("retryCount", config == null ? 0 : config.aiRetryCount());
        JSONObject response;
        try {
            response = ai.call("chat", params);
        } catch (RuntimeException error) {
            return AiResult.failure(AiErrorTypes.UNKNOWN, true, elapsed(startTime));
        }
        long elapsed = elapsed(startTime);
        if (response == null) {
            return AiResult.failure(AiErrorTypes.UNKNOWN, true, elapsed);
        }
        if (response.getBooleanValue("status")) {
            JSONObject usage = response.getJSONObject("usage");
            return AiResult.success(response.getString("content"),
                    response.getString("finishReason"),
                    usage == null ? "" : usage.toJSONString(),
                    response.getBooleanValue("cached"), elapsed);
        }
        // 契约（docs/MBB-AI.md）失败体用 errorType；旧实现读 code，导致 busy/timeout 全折成 unknown。
        String errorType = response.getString("errorType");
        if (errorType == null || errorType.isEmpty()) {
            errorType = response.getString("code");
        }
        boolean retryable = response.getBooleanValue("retryable");
        return AiResult.failure(mapError(errorType), retryable, elapsed);
    }

    private static JSONArray messages(AiRequest request) {
        JSONArray messages = new JSONArray();
        if (!request.stablePrefix().isEmpty()) {
            messages.add(message("system", request.stablePrefix()));
        }
        StringBuilder userText = new StringBuilder();
        if (!request.cacheable().isEmpty()) {
            userText.append(request.cacheable());
        }
        if (!request.dynamic().isEmpty()) {
            if (userText.length() > 0) {
                userText.append('\n');
            }
            userText.append(request.dynamic());
        }
        if (userText.length() > 0) {
            messages.add(message("user", userText.toString()));
        }
        return messages;
    }

    private static JSONObject message(String role, String content) {
        JSONObject message = new JSONObject(true);
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    /**
     * 归一 MBB-AI 的错误类型（§9.5）：只折叠插件认识的取值，未列出的原样保留交给引擎。
     *
     * <p>{@code unavailable}、{@code unknown} 及后续新增码不在插件降级表里，原样透传；
     * 引擎按「不计失败 + 通用中文标签」处理，避免把它们误记成「服务未启用」。</p>
     *
     * @param code MBB-AI 返回的 errorType 或 code，可能为空
     */
    public static String mapError(String code) {
        if (code == null || code.isEmpty()) {
            return AiErrorTypes.UNKNOWN;
        }
        String lower = code.toLowerCase(Locale.ROOT);
        if (AiErrorTypes.NETWORK.equals(lower) || lower.startsWith(AiErrorTypes.HTTP_PREFIX)) {
            return lower;
        }
        if (AiErrorTypes.DISABLED.equals(lower)) {
            return AiErrorTypes.DISABLED;
        }
        if (AiErrorTypes.BUSY.equals(lower)) {
            return AiErrorTypes.BUSY;
        }
        if (AiErrorTypes.TIMEOUT.equals(lower)) {
            return AiErrorTypes.TIMEOUT;
        }
        if (AiErrorTypes.INTERRUPTED.equals(lower)) {
            return AiErrorTypes.INTERRUPTED;
        }
        if (AiErrorTypes.PARAMS.equals(lower)) {
            return AiErrorTypes.PARAMS;
        }
        return lower;
    }

    private static long elapsed(long startTime) {
        return System.currentTimeMillis() - startTime;
    }
}
