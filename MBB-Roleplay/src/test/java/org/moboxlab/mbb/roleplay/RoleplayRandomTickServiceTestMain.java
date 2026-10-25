package org.moboxlab.mbb.roleplay;

/**
 * 随机刻群隔离、Qzone 保底和最近 owner 加权回归测试
 */
public class RoleplayRandomTickServiceTestMain {
    public static void main(String[] args) {
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
        if (!config.randomTickMorningGreetingEnable
                || config.randomTickMorningStartHour != 8
                || config.randomTickMorningEndHour != 10
                || Math.abs(config.randomTickMorningChance - 0.80) > 0.0001
                || config.randomTickMorningMaxGroups != 3
                || config.randomTickMorningMinMessages != 3) {
            throw new AssertionError("主动早安默认配置不正确");
        }
        if (!RoleplayRandomTickService.morningInWindow(8,8,10)
                || !RoleplayRandomTickService.morningInWindow(9,8,10)
                || RoleplayRandomTickService.morningInWindow(10,8,10)
                || RoleplayRandomTickService.morningInWindow(7,8,10)) {
            throw new AssertionError("主动早安时间窗口判断不正确");
        }
        if (!RoleplayRandomTickService.contentSessionId("morning_greeting",10001L)
                .contains("10001")) {
            throw new AssertionError("主动早安正文会话没有按群隔离");
        }
        if (!config.randomTickPrivateFollowUpEnable
                || config.randomTickPrivateFollowUpInitialMinute != 2
                || config.randomTickPrivateFollowUpMaxMinute != 120
                || config.randomTickPrivateFollowUpFreshMinute != 720) {
            throw new AssertionError("私聊主动追问默认配置不正确");
        }
        if (RoleplayRandomTickService.privateBackoffMillis(0,2,120) != 2L * 60000L
                || RoleplayRandomTickService.privateBackoffMillis(1,2,120) != 4L * 60000L
                || RoleplayRandomTickService.privateBackoffMillis(10,2,120) != 120L * 60000L) {
            throw new AssertionError("私聊主动追问指数退避不正确");
        }
        if (Math.abs(RoleplayRandomTickService.qzoneChanceForHour(
                12,18,23,7,0.20,0.80,0.03) - 0.20) > 0.0001
                || Math.abs(RoleplayRandomTickService.qzoneChanceForHour(
                20,18,23,7,0.20,0.80,0.03) - 0.80) > 0.0001
                || Math.abs(RoleplayRandomTickService.qzoneChanceForHour(
                2,18,23,7,0.20,0.80,0.03) - 0.03) > 0.0001) {
            throw new AssertionError("Qzone 分时段概率不正确");
        }
        if (!RoleplayRandomTickService.qzoneRandomDue(2,0)
                || !RoleplayRandomTickService.qzoneRandomDue(2,1)
                || RoleplayRandomTickService.qzoneRandomDue(2,2)
                || RoleplayRandomTickService.qzoneRandomDue(2,-1)) {
            throw new AssertionError("随机刻 Qzone 当日上限判断不正确");
        }
    }
}
