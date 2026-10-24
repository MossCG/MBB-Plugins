package org.moboxlab.mbb.trpg.ai;

/**
 * 触发 AI 叙述的窄端口（§9.3）：指令层与监听器只认这个接口，不依赖引擎实现。
 *
 * <p>调用方必须保证事件已经落库，因为实现会用当前快照的最新事件序号拼请求。</p>
 */
public interface AiTriggerSink {

    /**
     * 按合并窗口语义触发，并带上本条事件的真实序号与文本。
     *
     * <p>可合并触发必须走这个重载：引擎用 {@code eventSeq} 拼窗口、用 {@code eventText} 拼提示词，
     * 否则窗口无法按事件到达顺序拼接（§9.3）。</p>
     *
     * @param trigger   触发类型
     * @param groupId   发起群
     * @param eventSeq  本条事件在局日志里的序号；小于等于 0 时退回快照最新事件序号
     * @param eventText 本条事件的文本（玩家行动原文或检定结果摘要）
     */
    void fire(AiTrigger trigger, long groupId, long eventSeq, String eventText);

    /**
     * 立即入队（覆盖触发类型的默认可合并语义），用于 KP 代掷等需要即时叙述的场景。
     *
     * @param trigger 触发类型
     * @param groupId 发起群
     */
    void fireNow(AiTrigger trigger, long groupId);

    /**
     * 本局 AI 开关被重新打开时清掉去重缓存（§9.2）。
     *
     * @param sessionNo 局号
     */
    void onEnabled(long sessionNo);
}
