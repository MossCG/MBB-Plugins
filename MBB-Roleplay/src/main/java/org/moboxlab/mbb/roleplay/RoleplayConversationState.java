package org.moboxlab.mbb.roleplay;

/**
 * 按群维护的对话状态
 *
 * 路由层、执行层和技能共用同一份，避免各处各自猜"刚才发生了什么"。
 */
public class RoleplayConversationState {
    public long groupID = 0L;
    /** 角色最后一次发言时间 */
    public long lastReplyTime = 0L;
    /** 角色最后一次回复的对象 */
    public long lastReplyUser = 0L;
    /** 角色最后一条消息的 ID，用于判断别人是不是在引用它 */
    public long lastBotMessageID = 0L;
    /** 另一个角色机器人连续发言次数 */
    public int otherRoleStreak = 0;
    /** 角色自己连续发言次数，用于判断有没有霸屏 */
    public int botStreak = 0;
    /** 最近一次被直接叫到的时间 */
    public long lastAddressedTime = 0L;
    /** 最近一次路由给出的主题，仅用于日志与后续判断 */
    public String topic = "";
    /** 戳一戳回复策略轮换计数，避免每次固定戳回去 */
    public int pokeVariant = 0;
}
