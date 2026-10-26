package org.moboxlab.mbb.trpg.ai;

import java.util.Locale;

/**
 * AI 主持触发类型。
 *
 * <p>关键节点（开场、场景切换、KP 代掷判定、理智/对抗判定等）走立即触发；
 * 局内成员自由文本行动与玩家本人自助判定的结果，才走 20 秒 / 5 条的合并窗口。
 * 同一事件只能走其中一条路径：玩家自助判定进入合并窗口，KP 或管理员代掷立即触发。</p>
 */
public enum AiTrigger {

    /** 开场叙述。 */
    BEGIN,
    /** 场景切换叙述。 */
    SCENE,
    /** 判定结果叙述（含 KP 代掷与玩家自助判定）。 */
    CHECK,
    /** 对抗判定结果叙述。 */
    OPPOSED,
    /** 理智判定结果叙述。 */
    SAN,
    /** 局内成员自由文本行动。 */
    PLAYER_ACTION,
    /** 合并窗口结算后的合并叙述。 */
    MERGED;

    /**
     * 是否允许进入合并窗口。
     *
     * <p>只有局内成员自由文本行动与玩家本人自助判定的结果走合并窗口；
     * KP / 管理员代掷走立即触发，由调用方传入 immediate 标记，而不是由枚举决定。</p>
     */
    public boolean mergeable() {
        return this == PLAYER_ACTION || this == CHECK;
    }

    /** 审计与 AiRequest.triggerType 使用的名称（小写）。 */
    public String traceName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
