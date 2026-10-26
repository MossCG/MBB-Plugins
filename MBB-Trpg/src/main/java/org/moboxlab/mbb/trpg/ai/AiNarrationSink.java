package org.moboxlab.mbb.trpg.ai;

/**
 * AI 主持叙述的发送端口。
 *
 * <p>这是引擎与呈现层之间的窄端口：引擎只提供群号、正文与降级原因，
 * 具体文案模板、消息格式与发送实现由接线方决定。引擎不直接触碰群消息 API。</p>
 */
public interface AiNarrationSink {

    /**
     * 发送一条叙述。
     *
     * @param groupId       群号
     * @param narration     叙述正文。正常叙述为模型输出；降级时为剧本回退原文，
     *                      为空则表示没有可回退的原文，呈现层只发降级提示
     * @param degraded      是否为降级
     * @param degradeReason 降级原因（busy / network / http_&lt;code&gt; / timeout / unavailable /
     *                      interrupted / verification / truncated / queue_full / queue_age），
     *                      非降级时为空字符串
     */
    void send(long groupId, String narration, boolean degraded, String degradeReason);

    /**
     * 通知群内 AI 主持已因连续失败进入挂起状态。
     *
     * <p>同一局同一挂起周期只应提示一次，由引擎保证调用时机，呈现层只负责模板与发送。</p>
     */
    void notifySuspended(long groupId);
}
