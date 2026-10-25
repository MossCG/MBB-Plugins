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
}
