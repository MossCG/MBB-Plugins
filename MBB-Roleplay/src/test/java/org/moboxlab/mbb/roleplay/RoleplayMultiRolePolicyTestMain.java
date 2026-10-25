package org.moboxlab.mbb.roleplay;

/**
 * 直接点名、多角色名单和冷却绕过策略测试。
 */
public class RoleplayMultiRolePolicyTestMain {
    public static void main(String[] args) {
        RoleplayConfig config = new RoleplayConfig();
        if (!config.directReplyBypassCooldown) {
            throw new AssertionError("直接点名默认应绕过普通回复冷却");
        }
        if (RoleplayMultiRolePolicy.directChance(true,0.32) != 1.0
                || RoleplayMultiRolePolicy.directChance(false,0.32) != 0.32) {
            throw new AssertionError("直接点名必须强制回复概率为 1");
        }
        if (!RoleplayMultiRolePolicy.bypassCooldown(config,true)
                || RoleplayMultiRolePolicy.bypassCooldown(config,false)) {
            throw new AssertionError("直接点名冷却绕过判断不正确");
        }
        if (RoleplayMultiRolePolicy.multiRoleConfigured(config)) {
            throw new AssertionError("空角色名单不应被视为已配置");
        }
        config.otherRoleBotQQs = "10001";
        if (!RoleplayMultiRolePolicy.multiRoleConfigured(config)) {
            throw new AssertionError("填写角色 QQ 后应视为已配置");
        }
        config.otherRoleBotQQs = "";
        config.otherRoleBotNames = "小桃,小绿";
        if (!RoleplayMultiRolePolicy.multiRoleConfigured(config)) {
            throw new AssertionError("填写角色名后应视为已配置");
        }
    }
}
