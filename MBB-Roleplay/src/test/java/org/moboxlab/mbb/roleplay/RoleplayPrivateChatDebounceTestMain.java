package org.moboxlab.mbb.roleplay;

/**
 * 私聊图片防抖和队列默认配置测试。
 */
public class RoleplayPrivateChatDebounceTestMain {
    public static void main(String[] args) {
        RoleplayConfig config = new RoleplayConfig();
        if (config.privateChatImageDebounceSecond != 4
                || config.privateChatLiveMergeMaxRounds != 2
                || config.privateChatPendingMaxMessages != 10) {
            throw new AssertionError("私聊防抖默认配置不正确");
        }
        if (RoleplayPrivateChatDebounce.delaySeconds(true,false,4) != 4
                || RoleplayPrivateChatDebounce.delaySeconds(true,true,4) != 0
                || RoleplayPrivateChatDebounce.delaySeconds(false,false,4) != 0) {
            throw new AssertionError("私聊图片防抖策略不正确");
        }
    }
}
