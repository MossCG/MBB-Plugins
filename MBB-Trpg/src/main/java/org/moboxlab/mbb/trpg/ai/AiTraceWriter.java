package org.moboxlab.mbb.trpg.ai;

import org.moboxlab.mbb.trpg.config.TrpgConfig;
import org.moboxlab.mbb.trpg.session.Clock;
import org.moboxlab.mbb.trpg.store.AiTraceRow;

/** AI 审计写入：拼审计行、截断预览、告警写入失败；未配置 trace 或未开启审计时整体不写。 */
final class AiTraceWriter {

    /** 审计行里预览文本的最大长度，避免整段叙述撑大审计表。 */
    private static final int PREVIEW_MAX_CHARS = 240;

    private final TraceStore trace;
    private final TrpgConfig config;
    private final Clock clock;
    private final AiEngineLog log;

    AiTraceWriter(TraceStore trace, TrpgConfig config, Clock clock, AiEngineLog log) {
        this.trace = trace;
        this.config = config;
        this.clock = clock;
        this.log = log;
    }

    /** 距开始时刻的耗时；与审计时间戳同源，统一走注入的时钟。 */
    long elapsed(long startAt) {
        return clock.nowMillis() - startAt;
    }

    /**
     * 写一条审计行。
     *
     * <p>审计失败只打日志，不影响调用结果；写入异常同样吞掉，不向上抛。</p>
     */
    void write(AiCallRequest request, String resultStatus, String degradeReason, String errorType,
               int attempts, long elapsedMs, String finishReason, String usageJson, boolean cached,
               String preview) {
        if (trace == null || !config.aiTraceEnabled()) {
            return;
        }
        AiTraceRow row = new AiTraceRow(0L, request.groupId(), request.sessionNo(),
                request.latestEventSeq(), request.trigger().traceName(), resultStatus,
                degradeReason == null ? "" : degradeReason,
                errorType == null ? "" : errorType,
                Math.max(0, attempts), Math.max(0L, elapsedMs),
                finishReason == null ? "" : finishReason,
                usageJson == null ? "" : usageJson, cached,
                truncate(preview, PREVIEW_MAX_CHARS), clock.nowMillis());
        try {
            if (!trace.record(row)) {
                log.warn("AI 审计写入失败，群 " + request.groupId());
            }
        } catch (RuntimeException error) {
            log.warn("AI 审计写入异常，群 " + request.groupId() + "：" + error);
        }
    }

    /** 按码点截断，避免把 emoji 等代理对劈开写进审计预览。 */
    private static String truncate(String text, int maxChars) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (text.codePointCount(0, text.length()) <= maxChars) {
            return text;
        }
        int end = text.offsetByCodePoints(0, maxChars);
        return text.substring(0, end);
    }
}
