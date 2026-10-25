package org.moboxlab.mbb.roleplay;

/**
 * 多角色同时点名与直接回复策略。
 */
public class RoleplayMultiRolePolicy {
    static double directChance(boolean direct,double chance) {
        return direct ? 1.0 : chance;
    }

    static boolean bypassCooldown(RoleplayConfig config,boolean direct) {
        return direct && config.directReplyBypassCooldown;
    }

    static boolean multiRoleConfigured(RoleplayConfig config) {
        if (config == null) return false;
        return !safe(config.otherRoleBotNames).isEmpty()
                || !safe(config.otherRoleBotQQs).isEmpty();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
