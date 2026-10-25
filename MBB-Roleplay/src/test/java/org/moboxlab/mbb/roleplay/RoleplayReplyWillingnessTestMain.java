package org.moboxlab.mbb.roleplay;

/**
 * 本地回复意愿门控的特征方向与默认 shadow 模式测试。
 */
public class RoleplayReplyWillingnessTestMain {
    public static void main(String[] args) {
        RoleplayConfig config = new RoleplayConfig();
        if (!"shadow".equals(config.replyWillingnessMode)
                || config.replyWillingnessThreshold != 0.35
                || config.replyWillingnessWindowSecond != 300) {
            throw new AssertionError("回复意愿门控默认配置不正确");
        }

        RoleplayReplyWillingnessService.Calculation direct = calculate(config,signals(true),
                0.5,0,0.0);
        if (direct.probability < config.replyWillingnessThreshold) {
            throw new AssertionError("直接点名不应落入低意愿");
        }

        RoleplayDecisionEngine.Signals other = signals(false);
        other.addressedToOtherMember = true;
        RoleplayReplyWillingnessService.Calculation addressedOther = calculate(
                config,other,0.5,0,0.0);
        RoleplayReplyWillingnessService.Calculation ambient = calculate(
                config,signals(false),0.5,0,0.0);
        if (addressedOther.probability >= ambient.probability) {
            throw new AssertionError("明确指向其他人时必须降低回复意愿");
        }

        RoleplayDecisionEngine.Signals fatigued = signals(false);
        fatigued.topicFatigued = true;
        RoleplayReplyWillingnessService.Calculation tired = calculate(
                config,fatigued,0.5,0,0.0);
        if (tired.probability >= ambient.probability) {
            throw new AssertionError("话题疲劳时必须降低回复意愿");
        }

        RoleplayReplyWillingnessService.Calculation selfHeavy = calculate(
                config,signals(false),0.5,20,0.8);
        if (selfHeavy.probability >= ambient.probability) {
            throw new AssertionError("角色近期发言占比过高时必须降低回复意愿");
        }

        RoleplayReplyWillingnessService.Calculation question = calculate(
                config,signals(false),0.5,0,0.0);
        RoleplayDecisionEngine.Signals ignored = signals(false);
        ignored.contentLength = 30;
        RoleplayReplyWillingnessService.Calculation withQuestion =
                RoleplayReplyWillingnessService.calculate(config,ignored,
                        true,false,1,0,0.0,0.5);
        if (withQuestion.probability <= question.probability - 0.1) {
            throw new AssertionError("普通问题不应被明显压低回复意愿");
        }
    }

    private static RoleplayReplyWillingnessService.Calculation calculate(
            RoleplayConfig config,RoleplayDecisionEngine.Signals signals,
            double attention,int recentMessages,double selfRatio) {
        return RoleplayReplyWillingnessService.calculate(config,signals,
                false,false,1,recentMessages,selfRatio,attention);
    }

    private static RoleplayDecisionEngine.Signals signals(boolean direct) {
        RoleplayDecisionEngine.Signals signals = new RoleplayDecisionEngine.Signals();
        signals.direct = direct;
        signals.mentioningSelf = direct;
        signals.contentLength = 30;
        return signals;
    }
}
