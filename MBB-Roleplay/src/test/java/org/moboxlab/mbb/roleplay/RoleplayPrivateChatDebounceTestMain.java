package org.moboxlab.mbb.roleplay;

/**
 * 私聊图片防抖和队列默认配置测试。
 */
public class RoleplayPrivateChatDebounceTestMain {
    public static void main(String[] args) {
        RoleplayConfig config = new RoleplayConfig();
        if (config.privateChatImageDebounceSecond != 4
                || config.privateChatLiveMergeMaxRounds != 2
                || config.privateChatPendingMaxMessages != 10
                || !config.privateChatInputStatusEnable
                || config.privateChatInputStatusGraceSecond != 3
                || config.privateChatInputStatusMaxWaitSecond != 10) {
            throw new AssertionError("私聊防抖默认配置不正确");
        }
        if (RoleplayPrivateChatDebounce.delaySeconds(true,false,4) != 4
                || RoleplayPrivateChatDebounce.delaySeconds(true,true,4) != 0
                || RoleplayPrivateChatDebounce.delaySeconds(false,false,4) != 0) {
            throw new AssertionError("私聊图片防抖策略不正确");
        }
        if (RoleplayPrivateChatDebounce.typingDelaySeconds(
                1000L,0L,2000L,3,10) != 2
                || RoleplayPrivateChatDebounce.typingDelaySeconds(
                9000L,1000L,9000L,3,10) != 2
                || RoleplayPrivateChatDebounce.typingDelaySeconds(
                1000L,0L,4000L,3,10) != 0) {
            throw new AssertionError("input_status 三秒等待或绝对上限计算不正确");
        }
    }
}
