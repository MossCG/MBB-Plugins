package org.moboxlab.mbb.roleplay;

/**
 * 规则决策器
 *
 * 只负责把已经采集到的信号换算成"是否回复"和"回复概率"，信号采集仍在 RoleplayService。
 * 这样 AI 路由层上线时只需要替换这一层，信号采集和后续流程都不用动。
 */
public class RoleplayDecisionEngine {

    /** 一轮消息的判定信号 */
    public static class Signals {
        public boolean otherRoleBot = false;
        public boolean addressedToOtherRole = false;
        public boolean addressedToOtherMember = false;
        public boolean reminderNotification = false;
        public int otherRoleStreak = 0;
        public boolean direct = false;
        public boolean quotingSelf = false;
        public boolean mentioningSelf = false;
        public boolean sameUserContinuation = false;
        public boolean justRepliedToSameUser = false;
        public boolean completesPreviousMessage = false;
        public boolean groupActive = false;
        public boolean interest = false;
        public boolean recentImageQuestion = false;
        public int contentLength = 0;
    }

    /**
     * 硬性跳过条件，命中后不再消耗概率判定
     * 返回 null 表示可以继续，返回文本表示跳过原因
     */
    public static String skipReason(RoleplayConfig config,Signals signals) {
        if (signals.otherRoleBot && signals.reminderNotification) return "另一个机器人的提醒通知";
        if (signals.otherRoleBot && signals.otherRoleStreak > config.maxConsecutiveOtherRoleMessages) {
            return "与另一个机器人连续往返超过上限";
        }
        //明确艾特了其他成员，又没有提到角色，说明这句话不是对角色说的
        if (signals.addressedToOtherMember && !signals.mentioningSelf && !signals.quotingSelf) {
            return "消息艾特的是其他成员";
        }
        //同一用户刚被回复过，紧接着的短句多半是补充，不再重复接一次
        if (signals.justRepliedToSameUser && !signals.mentioningSelf && !signals.quotingSelf
                && !signals.direct
                && !signals.completesPreviousMessage
                && signals.contentLength <= config.splitMessageSuppressMaxChars) {
            return "同一用户刚被回复，短句视为补充";
        }
        return null;
    }

    /**
     * 概率决策，行为与分层改造前保持一致
     */
    public static RoleplayRouteDecision decide(RoleplayConfig config,Signals signals) {
        RoleplayRouteDecision decision = new RoleplayRouteDecision();
        decision.otherRoleBot = signals.otherRoleBot;
        if (!signals.direct && !signals.sameUserContinuation && !signals.groupActive
                && !signals.interest && !signals.recentImageQuestion) {
            decision.reason = "未被叫到且不相关";
            return decision;
        }
        double chance = config.interestReplyChance;
        if (signals.otherRoleBot) {
            decision.addressed = "ambient";
            chance = config.otherRoleBotReplyChance;
        } else if (signals.direct) {
            decision.addressed = "direct";
            chance = 1.0;
        } else if (signals.recentImageQuestion) {
            decision.addressed = "thread";
            chance = 1.0;
        } else if (signals.sameUserContinuation) {
            decision.addressed = "thread";
            chance = config.continuationReplyChance;
        } else if (signals.groupActive) {
            decision.addressed = "ambient";
            chance = config.otherParticipantReplyChance;
        }
        decision.chance = chance;
        decision.confidence = chance;
        if (signals.contentLength < config.minMessageLength) {
            decision.reason = "消息过短";
            return decision;
        }
        decision.reply = true;
        decision.reason = "规则命中";
        // 引用是发送时属性：被回复或被艾特时由规则强制，模型只能在此基础上追加建议
        decision.quoteRequired = config.quoteReplyEnable
                && (signals.quotingSelf || signals.mentioningSelf);
        return decision;
    }
}
