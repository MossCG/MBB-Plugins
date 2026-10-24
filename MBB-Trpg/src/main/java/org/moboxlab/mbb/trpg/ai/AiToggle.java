package org.moboxlab.mbb.trpg.ai;

/**
 * {@code /trpg ai on|off} 的判定结果（规格 §9.2）。
 *
 * <p>自 v1.1 起开关经 {@code SessionService} 落库：枚举只表达「参数是否合法、开关是否生效」，
 * 具体文案与写库结果由局面服务给出。</p>
 */
public enum AiToggle {

    /** 参数为 on 且全局已启用：按授权写入 {@code aiEnabled=true}；是否「清除挂起」由局面服务写入后判定。 */
    ON_ACK(true),

    /** 参数为 off：写入 {@code aiEnabled=false}，挂起自然失效。 */
    OFF_ACK(true),

    /** 参数为空或无法识别：回用法提示。 */
    USAGE(false);

    private final boolean accepted;

    AiToggle(boolean accepted) {
        this.accepted = accepted;
    }

    /** 是否按成功回执（其余走拒绝）。 */
    public boolean accepted() {
        return accepted;
    }

}
