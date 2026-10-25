package org.moboxlab.mbb.roleplay;

/**
 * 私聊图片防抖策略。
 */
public class RoleplayPrivateChatDebounce {
    static int delaySeconds(boolean hasMedia,boolean hasMeaningfulText,int imageWaitSecond) {
        if (!hasMedia || hasMeaningfulText) return 0;
        return Math.max(1,imageWaitSecond);
    }

    static int typingDelaySeconds(long lastTypingTime,long firstPendingTime,long now,
                                  int graceSecond,int maxWaitSecond) {
        if (lastTypingTime <= 0) return 0;
        long target = lastTypingTime + Math.max(1,graceSecond) * 1000L;
        if (firstPendingTime > 0 && maxWaitSecond > 0) {
            target = Math.min(target,firstPendingTime + maxWaitSecond * 1000L);
        }
        long remain = target - now;
        return remain <= 0 ? 0 : (int)((remain + 999L) / 1000L);
    }

    static boolean conversationEnded(String latestUserContent,String latestBotContent,
                                     boolean latestMessageFromBot) {
        if (isClosingText(latestUserContent)) return true;
        return latestMessageFromBot && isClosingText(latestBotContent);
    }

    static boolean isClosingText(String content) {
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) return false;
        return text.contains("晚安") || text.contains("睡了") || text.contains("睡啦")
                || text.contains("睡觉了") || text.contains("去睡") || text.contains("先睡")
                || text.contains("关灯") || text.contains("不聊了") || text.contains("下线了")
                || text.contains("明天见") || text.contains("明天聊") || text.contains("回头聊")
                || text.contains("拜拜") || text.contains("再见");
    }
}
