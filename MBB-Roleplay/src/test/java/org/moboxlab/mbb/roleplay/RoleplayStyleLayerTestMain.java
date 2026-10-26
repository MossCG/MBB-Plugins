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
        if (config.replyMaxSegments != 5 || config.replyBeatMaxSegments != 5) {
            throw new AssertionError("单条与合批分段上限默认都应为 5");
        }

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

        if (!"这样就行了".equals(RoleplayReplySanitizer.stripTrailingFillerLaugh("这样就行了哈哈"))
                || !"这样就行了~".equals(RoleplayReplySanitizer.stripTrailingFillerLaugh("这样就行了，啊哈哈~"))
                || !"晚安".equals(RoleplayReplySanitizer.stripTrailingFillerLaugh("晚安\n哈哈哈"))) {
            throw new AssertionError("正文后的填充笑声应从句尾移除");
        }
        if (!"笑哈哈".equals(RoleplayReplySanitizer.stripTrailingFillerLaugh("笑哈哈"))
                || !"啊哈哈".equals(RoleplayReplySanitizer.stripTrailingFillerLaugh("啊哈哈"))) {
            throw new AssertionError("本身表示笑声或固定说法不应被误删");
        }
    }
}
