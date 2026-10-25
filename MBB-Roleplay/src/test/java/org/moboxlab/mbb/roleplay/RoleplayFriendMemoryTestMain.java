package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;

/**
 * 好友长期记忆配置、列表清洗和跨群好感取最高测试。
 */
public class RoleplayFriendMemoryTestMain {
    public static void main(String[] args) {
        RoleplayConfig config = new RoleplayConfig();
        if (!config.friendMemoryEnable
                || config.friendMemoryMinNewMessages != 6
                || config.friendMemoryIntervalHours != 12
                || config.friendMemoryContextMessages != 120
                || config.friendMemoryInjectMaxChars != 1800) {
            throw new AssertionError("好友长期记忆默认配置不正确");
        }

        JSONArray values = new JSONArray();
        values.add("喜欢布丁");
        values.add(" 喜欢布丁 ");
        values.add("喜欢打游戏");
        for (int i = 0; i < 12; i++) values.add("偏好"+i);
        JSONArray cleaned = RoleplayFriendMemoryService.sanitizeList(values);
        if (cleaned.size() != 8 || !"喜欢布丁".equals(cleaned.getString(0))) {
            throw new AssertionError("好友记忆列表应去重并限制为 8 项");
        }

        if (RoleplayRelationService.highestAffinity(65,81.5) != 81.5
                || RoleplayRelationService.highestAffinity(91,72) != 91
                || RoleplayRelationService.highestAffinity(-3,30) != 30
                || RoleplayRelationService.highestAffinity(120,100) != 100) {
            throw new AssertionError("跨群好感取最高值或边界夹取不正确");
        }
    }
}
