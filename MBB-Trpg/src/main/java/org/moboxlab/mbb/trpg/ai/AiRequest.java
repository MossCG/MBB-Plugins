package org.moboxlab.mbb.trpg.ai;

/**
 * 一次 AI 叙述请求（不可变）。
 *
 * <p>按提示词三段拆分：稳定前缀、可缓存中段、动态区；总预算由调用方控制。</p>
 */
public final class AiRequest {

    private final String triggerType;
    private final String stablePrefix;
    private final String cacheable;
    private final String dynamic;
    private final int timeoutSeconds;
    private final int maxTokens;
    private final double temperature;
    private final String sessionId;

    public AiRequest(String triggerType, String stablePrefix, String cacheable, String dynamic,
                     int timeoutSeconds) {
        this(triggerType, stablePrefix, cacheable, dynamic, timeoutSeconds, 0, -1.0D, "");
    }

    /**
     * 完整构造。
     *
     * @param maxTokens   输出 token 上限，0 表示用 MBB-AI 模型配置的默认值
     * @param temperature 采样温度，负数表示用模型默认值
     */
    public AiRequest(String triggerType, String stablePrefix, String cacheable, String dynamic,
                     int timeoutSeconds, int maxTokens, double temperature) {
        this(triggerType, stablePrefix, cacheable, dynamic, timeoutSeconds, maxTokens, temperature,
                "");
    }

    /**
     * 带会话标识的完整构造。
     *
     * @param sessionId 稳定的对话标识（MBB-AI 写入 x-opencode-session 头）；空串表示不带
     */
    public AiRequest(String triggerType, String stablePrefix, String cacheable, String dynamic,
                     int timeoutSeconds, int maxTokens, double temperature, String sessionId) {
        this.triggerType = triggerType == null ? "" : triggerType;
        this.stablePrefix = stablePrefix == null ? "" : stablePrefix;
        this.cacheable = cacheable == null ? "" : cacheable;
        this.dynamic = dynamic == null ? "" : dynamic;
        this.timeoutSeconds = timeoutSeconds;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.sessionId = sessionId == null ? "" : sessionId;
    }

    public String triggerType() {
        return triggerType;
    }

    public String stablePrefix() {
        return stablePrefix;
    }

    public String cacheable() {
        return cacheable;
    }

    public String dynamic() {
        return dynamic;
    }

    /** 传给服务方的超时秒数。 */
    public int timeoutSeconds() {
        return timeoutSeconds;
    }

    /** 输出 token 上限，0 表示用模型默认值。 */
    public int maxTokens() {
        return maxTokens;
    }

    /** 采样温度，负数表示用模型默认值。 */
    public double temperature() {
        return temperature;
    }

    /** 稳定对话标识；空串表示不传。 */
    public String sessionId() {
        return sessionId;
    }

    /** 三段拼接后的字符数，用于预算校验。 */
    public int totalChars() {
        return stablePrefix.length() + cacheable.length() + dynamic.length();
    }

    /** 拼成完整提示词。 */
    public String prompt() {
        StringBuilder builder = new StringBuilder();
        builder.append(stablePrefix).append('\n').append(cacheable).append('\n').append(dynamic);
        return builder.toString();
    }
}
