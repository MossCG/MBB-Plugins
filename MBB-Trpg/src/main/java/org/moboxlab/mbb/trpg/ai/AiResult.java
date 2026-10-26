package org.moboxlab.mbb.trpg.ai;

/** MBB-AI chat 结果的插件侧映射（不可变）。 */
public final class AiResult {

    private final boolean success;
    private final String content;
    private final String errorType;
    private final boolean retryable;
    private final String finishReason;
    private final String usageJson;
    private final boolean cached;
    private final long elapsedMs;

    public AiResult(boolean success, String content, String errorType, boolean retryable,
                    String finishReason, String usageJson, boolean cached, long elapsedMs) {
        this.success = success;
        this.content = content == null ? "" : content;
        this.errorType = errorType == null ? "" : errorType;
        this.retryable = retryable;
        this.finishReason = finishReason == null ? "" : finishReason;
        this.usageJson = usageJson == null ? "" : usageJson;
        this.cached = cached;
        this.elapsedMs = elapsedMs;
    }

    /** 成功结果。 */
    public static AiResult success(String content, String finishReason, String usageJson,
                                   boolean cached, long elapsedMs) {
        return new AiResult(true, content, "", false, finishReason, usageJson, cached, elapsedMs);
    }

    /** 失败结果。 */
    public static AiResult failure(String errorType, boolean retryable, long elapsedMs) {
        return new AiResult(false, "", errorType, retryable, "", "", false, elapsedMs);
    }

    public boolean success() {
        return success;
    }

    public String content() {
        return content;
    }

    public String errorType() {
        return errorType;
    }

    public boolean retryable() {
        return retryable;
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

    public long elapsedMs() {
        return elapsedMs;
    }
}
