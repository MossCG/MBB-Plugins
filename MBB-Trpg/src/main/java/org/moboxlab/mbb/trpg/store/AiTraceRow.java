package org.moboxlab.mbb.trpg.store;

/**
 * plugin_mbb_trpg_ai_trace 行对象（不可变）。
 *
 * <p>审计只记摘要，不存完整提示词与输出；preview 由写入方截断到 240 字。</p>
 */
public final class AiTraceRow {

    private final long id;
    private final long groupId;
    private final long sessionId;
    private final long eventSeq;
    private final String triggerType;
    private final String resultStatus;
    private final String degradeReason;
    private final String errorType;
    private final int attempts;
    private final long elapsedMs;
    private final String finishReason;
    private final String usageJson;
    private final boolean cached;
    private final String preview;
    private final long createTime;

    public AiTraceRow(long id, long groupId, long sessionId, long eventSeq, String triggerType,
                      String resultStatus, String degradeReason, String errorType, int attempts,
                      long elapsedMs, String finishReason, String usageJson, boolean cached,
                      String preview, long createTime) {
        this.id = id;
        this.groupId = groupId;
        this.sessionId = sessionId;
        this.eventSeq = eventSeq;
        this.triggerType = triggerType == null ? "" : triggerType;
        this.resultStatus = resultStatus == null ? "" : resultStatus;
        this.degradeReason = degradeReason == null ? "" : degradeReason;
        this.errorType = errorType == null ? "" : errorType;
        this.attempts = attempts;
        this.elapsedMs = elapsedMs;
        this.finishReason = finishReason == null ? "" : finishReason;
        this.usageJson = usageJson == null ? "" : usageJson;
        this.cached = cached;
        this.preview = preview == null ? "" : preview;
        this.createTime = createTime;
    }

    public long id() {
        return id;
    }

    public long groupId() {
        return groupId;
    }

    public long sessionId() {
        return sessionId;
    }

    public long eventSeq() {
        return eventSeq;
    }

    public String triggerType() {
        return triggerType;
    }

    public String resultStatus() {
        return resultStatus;
    }

    public String degradeReason() {
        return degradeReason;
    }

    public String errorType() {
        return errorType;
    }

    public int attempts() {
        return attempts;
    }

    public long elapsedMs() {
        return elapsedMs;
    }

    public String finishReason() {
        return finishReason;
    }

    public String usageJson() {
        return usageJson;
    }

    public boolean cached() {
        return cached;
    }

    public String preview() {
        return preview;
    }

    public long createTime() {
        return createTime;
    }
}
