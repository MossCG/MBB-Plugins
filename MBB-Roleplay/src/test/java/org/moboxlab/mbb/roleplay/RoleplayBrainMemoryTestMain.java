package org.moboxlab.mbb.roleplay;

/**
 * 统一大脑记忆访问边界测试。
 */
public class RoleplayBrainMemoryTestMain {
    public static void main(String[] args) {
        if (!RoleplayBrainMemoryService.canRead(
                "self","self","self",0,"group",123,456)) {
            throw new AssertionError("自我记忆应在所有场景可见");
        }
        if (!RoleplayBrainMemoryService.canRead(
                "shared","shared","shared",0,"private",123,0)) {
            throw new AssertionError("共享记忆应在群聊和私聊可见");
        }
        if (!RoleplayBrainMemoryService.canRead(
                "group","group","group",456,"group",123,456)) {
            throw new AssertionError("群记忆应在对应群可见");
        }
        if (RoleplayBrainMemoryService.canRead(
                "group","group","group",999,"group",123,456)) {
            throw new AssertionError("群记忆不应跨群可见");
        }
        if (!RoleplayBrainMemoryService.canRead(
                "private-user","private","private",123,"private",123,0)) {
            throw new AssertionError("私聊记忆应在本人私聊可见");
        }
        if (RoleplayBrainMemoryService.canRead(
                "private-user","private","private",123,"group",123,456)
                || RoleplayBrainMemoryService.canRead(
                "private-user","private","private",999,"private",123,0)) {
            throw new AssertionError("私聊记忆不应进入群聊或泄露给其他用户");
        }
        if (!"self".equals(RoleplayBrainMemoryService.normalizeScope(""))) {
            throw new AssertionError("未知 scope 应回退为 self");
        }
    }
}
