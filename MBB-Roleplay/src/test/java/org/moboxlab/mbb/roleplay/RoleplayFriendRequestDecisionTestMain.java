package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONObject;

/**
 * 好友申请角色裁决阈值与备注清洗测试。
 */
public class RoleplayFriendRequestDecisionTestMain {
    public static void main(String[] args) {
        RoleplayConfig config = new RoleplayConfig();
        if (!config.friendRequestRoleDecisionEnable
                || config.friendRequestRoleDecisionMinConfidence != 0.55
                || config.friendRequestRoleDecisionMaxTokens != 1600
                || config.friendRequestRoleDecisionTimeoutSecond != 60) {
            throw new AssertionError("好友申请角色裁决默认配置不正确");
        }

        JSONObject approved = new JSONObject(true);
        approved.put("approve",true);
        approved.put("confidence",0.82);
        approved.put("reason","备注真诚，而且聊过相关内容");
        approved.put("remark","群友 小明");
        RoleplayFriendRequestService.Decision first =
                RoleplayFriendRequestService.parseDecision(approved,0.55);
        if (first == null || !first.approve || !"群友 小明".equals(first.remark)) {
            throw new AssertionError("高置信度好友申请应通过");
        }

        approved.put("confidence",0.4);
        RoleplayFriendRequestService.Decision low =
                RoleplayFriendRequestService.parseDecision(approved,0.55);
        if (low == null || low.approve || !low.reason.contains("低于通过阈值")) {
            throw new AssertionError("低于阈值的好友申请应转为拒绝");
        }

        JSONObject invalid = new JSONObject(true);
        invalid.put("reason","missing approve");
        if (RoleplayFriendRequestService.parseDecision(invalid,0.55) != null) {
            throw new AssertionError("缺少 approve 的裁决必须失败");
        }
    }
}
