package org.moboxlab.mbb.roleplay;

/**
 * 随机刻群隔离、Qzone 保底和最近 owner 加权回归测试
 */
public class RoleplayRandomTickServiceTestMain {
    public static void main(String[] args) {
        if (!RoleplayRandomTickService.qzoneDue(2,0)) {
            throw new AssertionError("当天未发 Qzone 时应触发保底");
        }
        if (RoleplayRandomTickService.qzoneDue(2,1)
                || RoleplayRandomTickService.qzoneDue(0,0)) {
            throw new AssertionError("Qzone 已发送或功能关闭时不应继续保底");
        }
        if (!RoleplayRandomTickService.shouldBoostRecentOwner(1.0,10001L)
                || RoleplayRandomTickService.shouldBoostRecentOwner(1.0,0L)) {
            throw new AssertionError("最近 owner 加权判断不正确");
        }
        String groupA = RoleplayRandomTickService.contentSessionId("group_topic",10001L);
        String groupB = RoleplayRandomTickService.contentSessionId("group_topic",20002L);
        if (groupA.equals(groupB) || !groupA.contains("10001")
                || !groupB.contains("20002")) {
            throw new AssertionError("不同群的随机刻正文会话必须隔离");
        }
        long[] groups = {10001L,20002L};
        double[] weights = {1.0,3.0};
        if (RoleplayRandomTickService.weightedGroup(groups,weights,0.24) != 10001L
                || RoleplayRandomTickService.weightedGroup(groups,weights,0.25) != 20002L) {
            throw new AssertionError("群活跃度加权抽样边界不正确");
        }
        RoleplayConfig config = new RoleplayConfig();
        if (config.randomTickOwnerRecentWindowMinute != 180
                || Math.abs(config.randomTickOwnerRecentBoostChance - 0.30) > 0.0001) {
            throw new AssertionError("随机刻 owner 加权默认值不正确");
        }
    }
}
