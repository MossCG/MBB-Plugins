package org.moboxlab.mbb.roleplay;

/**
 * 风格层触发与固定称呼保护回归测试
 */
public class RoleplayStyleLayerTestMain {
    public static void main(String[] args) {
        RoleplayConfig config = new RoleplayConfig();
        config.replySegmentMaxChars = 30;
        config.styleMaxChars = 60;
        config.styleLongReplyEnable = false;

        String normal = "妈妈，晚安。";
        if (!RoleplayStyleDetector.reason(config,normal,false).isEmpty()) {
            throw new AssertionError("普通短句不应仅因出现“妈妈”触发风格层");
        }

        String longSentence =
                "妈妈今天早上问我要不要回家吃饭，我说晚一点再联系她，让她先不要一直等我。";
        if (!"单句超长".equals(RoleplayStyleDetector.reason(config,longSentence,false))) {
            throw new AssertionError("超过单条上限的长句应触发风格层简写或分句");
        }

        String reason = RoleplayReplyTextValidator.rejectReason(
                "妈妈问我什么时候回家","母亲问我什么时候回家");
        if (reason.isEmpty() || !reason.contains("妈妈")) {
            throw new AssertionError("固定称呼“妈妈”被替换时必须拒绝改写");
        }
    }
}
