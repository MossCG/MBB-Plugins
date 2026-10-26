package org.moboxlab.mbb.trpg.ai;

/**
 * MBB-AI 适配层的底层错误类型，对应 {@link AiResult#errorType()}。
 *
 * <p>取值与 MBB-AI {@code chat} 失败体的 {@code errorType} 字段对齐；HTTP 类失败以
 * {@link #HTTP_PREFIX} 开头的字符串原样透传。与剧本生成的六类 {@code E_AI_*} 是两层：
 * 这里描述失败来源，生成服务负责折叠成对外错误码。</p>
 */
public final class AiErrorTypes {

    /** 未安装 MBB-AI 服务，或服务处于禁用状态。 */
    public static final String DISABLED = "disabled";

    /** 全局并发已满。 */
    public static final String BUSY = "busy";

    /** 超时（墙钟或 provider 超时）。 */
    public static final String TIMEOUT = "timeout";

    /** 网络错误。 */
    public static final String NETWORK = "network";

    /** 请求被中断：不计入可计失败（§9.6 降级表第 2 行）。 */
    public static final String INTERRUPTED = "interrupted";

    /** 请求参数不合法。 */
    public static final String PARAMS = "params";

    /** HTTP 类失败的前缀，实际值为 {@code http_401}、{@code http_500} 等。 */
    public static final String HTTP_PREFIX = "http_";

    /** 其他未知失败。 */
    public static final String UNKNOWN = "unknown";

    private AiErrorTypes() {
    }
}
