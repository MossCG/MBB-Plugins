package org.moboxlab.mbb.roleplay;

import java.util.List;

/**
 * 活动调度器配置解析和随机间隔测试。
 */
public class RoleplayActivitySchedulerTestMain {
    public static void main(String[] args) {
        RoleplayConfig config = new RoleplayConfig();
        if (!config.randomActivityEnable || !config.randomActivityServices.isEmpty()
                || Math.abs(config.randomActivityShareMinScore - 0.75) > 0.0001
                || config.randomActivityTimeoutSecond != 300) {
            throw new AssertionError("活动调度器默认配置不正确");
        }

        List<String> services = RoleplayActivityScheduler.parseServices(
                " MBB-Bilibili, mbb-bilibili ,MBB-Shopping,,MBB-Other ");
        if (services.size() != 3
                || !"MBB-Bilibili".equals(services.get(0))
                || !"MBB-Shopping".equals(services.get(1))
                || !"MBB-Other".equals(services.get(2))) {
            throw new AssertionError("活动插件服务名解析不正确");
        }

        if (RoleplayActivityScheduler.randomIntervalMillis(180,360,0.0)
                != 180L * 60000L
                || RoleplayActivityScheduler.randomIntervalMillis(180,360,0.999999)
                != 360L * 60000L) {
            throw new AssertionError("活动随机间隔边界不正确");
        }
    }
}
