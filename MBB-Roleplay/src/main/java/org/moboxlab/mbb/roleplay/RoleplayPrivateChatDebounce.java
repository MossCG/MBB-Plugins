package org.moboxlab.mbb.roleplay;

/**
 * 私聊图片防抖策略。
 */
public class RoleplayPrivateChatDebounce {
    static int delaySeconds(boolean hasMedia,boolean hasMeaningfulText,int imageWaitSecond) {
        if (!hasMedia || hasMeaningfulText) return 0;
        return Math.max(1,imageWaitSecond);
    }
}
