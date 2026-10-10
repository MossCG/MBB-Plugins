package org.moboxlab.mbb.antispam;

import org.moboxlab.moboxbot.API.Plugin;

/**
 * MBB-AntiSpam 配置
 */
public class AntiSpamConfig {
    public boolean enable = true;
    public boolean defaultGroupEnable = true;
    public String defaultAction = "log";
    public boolean alertAdminPrivate = true;
    public boolean alertCurrentGroup = false;
    public boolean bypassAdmin = true;
    public String bypassUsers = "";
    public String whitelistUsers = "";
    public int retentionDays = 30;

    public boolean rateEnable = true;
    public int rateWindowSecond = 10;
    public int rateMaxMessages = 6;
    public int rateLongWindowSecond = 60;
    public int rateLongMaxMessages = 20;

    public boolean repeatEnable = true;
    public int repeatFingerprintChars = 64;
    public int repeatWindowSecond = 10;
    public int repeatMaxCount = 3;
    public int repeatLongWindowSecond = 60;
    public int repeatLongMaxCount = 5;

    public boolean longTextEnable = true;
    public int longTextMaxChars = 800;

    public boolean mediaFloodEnable = true;
    public int mediaFloodMaxCount = 5;

    public boolean mentionEnable = true;
    public int mentionWindowSecond = 60;
    public int mentionMaxCount = 8;
    public boolean mentionAllEnable = true;

    public int minMessageLength = 2;

    /**
     * 玩梗豁免的人数区间：发过同一句话的不同用户数落在 [min, max] 内按玩梗只记录
     *
     * 低于下限说明只有自己在刷，按个人刷屏处置；高于上限说明是全群集体刷屏，照常处置。
     * 下限最少为 2（1 个人不存在"一起玩梗"），默认 3；上限默认 4，且不会小于下限。
     */
    public boolean repeatBanterForgive = true;
    public int repeatBanterMinUsers = 3;
    public int repeatBanterMaxUsers = 4;
    /**
     * 集体刷屏先整群提醒一次；多少秒内同一句话继续被刷，就对提醒之后的参与者开始处置
     *
     * 0 表示不对集体刷屏整群提醒（直接按违规处置）。
     */
    public int collectiveWarnCooldownSecond = 300;
    /**
     * 群内复读的阈值：窗口秒数与允许次数，0 表示关闭
     *
     * 统计的是本群所有人的发言（不按发言人区分），只要同一句话被反复发就算，
     * 不需要群里每个人都发过。阈值由夹取逻辑保证不低于个人阈值 repeatMaxCount，
     * 避免个人连刷先命中群级规则。
     */
    public int repeatGroupWindowSecond = 10;
    public int repeatGroupCount = 6;
    public int repeatGroupLongWindowSecond = 60;
    public int repeatGroupLongCount = 10;

    public boolean forgiveFirst = true;
    public boolean noticeEnable = true;
    public int noticeCooldownSecond = 600;
    /**
     * 违规计数方式：session 表示同一波刷屏只计一次（同一用户间隔不足
     * violationCooldownSecond 秒的连续命中合并为一波），message 表示每条命中都计数
     */
    public String violationCountMode = "session";
    /** 同一波刷屏的最短间隔秒数，只在该间隔之后再次刷屏才累计下一次违规 */
    public int violationCooldownSecond = 60;

    public int deleteAfterViolations = 2;
    public int banAfterViolations = 3;
    public int violationWindowSecond = 300;
    /**
     * 单次禁言时长，单位分钟（QQ 的禁言本身以分钟为基本单位）
     *
     * 同时兼容旧的 banDurationSecond：加载时按秒比较，取更长的一边，
     * 避免用户已经改过旧键时新键被旧值顶掉。
     */
    public int banDurationMinute = 5;
    public int banDurationSecond = 300;

    public int maxTrackedUsers = 5000;

    public static AntiSpamConfig load(Plugin plugin) {
        AntiSpamConfig config = new AntiSpamConfig();
        config.enable = plugin.getConfig().getBoolean("enable",true);
        config.defaultGroupEnable = plugin.getConfig().getBoolean("defaultGroupEnable",true);
        config.defaultAction = plugin.getConfig().getString("defaultAction","log");
        config.alertAdminPrivate = plugin.getConfig().getBoolean("alertAdminPrivate",true);
        config.alertCurrentGroup = plugin.getConfig().getBoolean("alertCurrentGroup",false);
        config.bypassAdmin = plugin.getConfig().getBoolean("bypassAdmin",true);
        config.bypassUsers = plugin.getConfig().getString("bypassUsers","");
        config.whitelistUsers = plugin.getConfig().getString("whitelistUsers","");
        config.retentionDays = plugin.getConfig().getInt("retentionDays",30);

        config.rateEnable = plugin.getConfig().getBoolean("rateEnable",true);
        config.rateWindowSecond = plugin.getConfig().getInt("rateWindowSecond",10);
        config.rateMaxMessages = plugin.getConfig().getInt("rateMaxMessages",6);
        config.rateLongWindowSecond = plugin.getConfig().getInt("rateLongWindowSecond",60);
        config.rateLongMaxMessages = plugin.getConfig().getInt("rateLongMaxMessages",20);

        config.repeatEnable = plugin.getConfig().getBoolean("repeatEnable",true);
        config.repeatFingerprintChars = plugin.getConfig().getInt("repeatFingerprintChars",64);
        config.repeatWindowSecond = plugin.getConfig().getInt("repeatWindowSecond",10);
        config.repeatMaxCount = plugin.getConfig().getInt("repeatMaxCount",3);
        config.repeatLongWindowSecond = plugin.getConfig().getInt("repeatLongWindowSecond",60);
        config.repeatLongMaxCount = plugin.getConfig().getInt("repeatLongMaxCount",5);

        config.longTextEnable = plugin.getConfig().getBoolean("longTextEnable",true);
        config.longTextMaxChars = plugin.getConfig().getInt("longTextMaxChars",800);

        config.mediaFloodEnable = plugin.getConfig().getBoolean("mediaFloodEnable",true);
        config.mediaFloodMaxCount = plugin.getConfig().getInt("mediaFloodMaxCount",5);

        config.mentionEnable = plugin.getConfig().getBoolean("mentionEnable",true);
        config.mentionWindowSecond = plugin.getConfig().getInt("mentionWindowSecond",60);
        config.mentionMaxCount = plugin.getConfig().getInt("mentionMaxCount",8);
        config.mentionAllEnable = plugin.getConfig().getBoolean("mentionAllEnable",true);

        config.minMessageLength = plugin.getConfig().getInt("minMessageLength",2);

        config.repeatBanterForgive = plugin.getConfig().getBoolean("repeatBanterForgive",true);
        config.repeatBanterMinUsers = plugin.getConfig().getInt("repeatBanterMinUsers",3);
        config.repeatBanterMaxUsers = plugin.getConfig().getInt("repeatBanterMaxUsers",4);
        config.collectiveWarnCooldownSecond =
                plugin.getConfig().getInt("collectiveWarnCooldownSecond",300);
        //旧默认 1 表示"2 人及以上都算玩梗"，迁移到新的下限 3
        if (config.repeatBanterMinUsers < 2) config.repeatBanterMinUsers = 3;
        config.repeatGroupWindowSecond = plugin.getConfig().getInt("repeatGroupWindowSecond",10);
        config.repeatGroupCount = plugin.getConfig().getInt("repeatGroupCount",6);
        config.repeatGroupLongWindowSecond = plugin.getConfig().getInt("repeatGroupLongWindowSecond",60);
        config.repeatGroupLongCount = plugin.getConfig().getInt("repeatGroupLongCount",10);

        config.forgiveFirst = plugin.getConfig().getBoolean("forgiveFirst",true);
        config.noticeEnable = plugin.getConfig().getBoolean("noticeEnable",true);
        config.noticeCooldownSecond = plugin.getConfig().getInt("noticeCooldownSecond",600);
        config.violationCountMode = plugin.getConfig().getString("violationCountMode","session");
        config.violationCooldownSecond = plugin.getConfig().getInt("violationCooldownSecond",60);

        config.deleteAfterViolations = plugin.getConfig().getInt("deleteAfterViolations",2);
        config.banAfterViolations = plugin.getConfig().getInt("banAfterViolations",3);
        config.violationWindowSecond = plugin.getConfig().getInt("violationWindowSecond",300);
        boolean hasMinuteKey = plugin.getConfig().contains("banDurationMinute");
        boolean hasSecondKey = plugin.getConfig().contains("banDurationSecond");
        config.banDurationMinute = plugin.getConfig().getInt("banDurationMinute",5);
        config.banDurationSecond = plugin.getConfig().getInt("banDurationSecond",300);
        if (config.banDurationMinute < 1) config.banDurationMinute = 1;
        if (config.banDurationMinute > 43200) config.banDurationMinute = 43200;
        if (hasSecondKey && config.banDurationSecond < 60) config.banDurationSecond = 60;
        if (hasSecondKey && config.banDurationSecond > 43200 * 60) {
            config.banDurationSecond = 43200 * 60;
        }
        if (!hasMinuteKey && hasSecondKey) {
            //旧配置只有秒：换算成分钟，向上取整避免出现 0 分钟
            config.banDurationMinute = (config.banDurationSecond + 59) / 60;
        } else {
            //以分钟为准，秒值由分钟推导，两个键显示与生效保持一致
            config.banDurationSecond = config.banDurationMinute * 60;
        }

        config.maxTrackedUsers = plugin.getConfig().getInt("maxTrackedUsers",5000);
        return clamp(config);
    }

    /**
     * 把明显不合理的配置修正到可用范围，避免运行期出现除以零或负数窗口
     */
    public static AntiSpamConfig clamp(AntiSpamConfig config) {
        if (config.defaultAction == null || config.defaultAction.trim().isEmpty()) {
            config.defaultAction = "log";
        }
        if (!"ignore".equalsIgnoreCase(config.defaultAction)
                && !"log".equalsIgnoreCase(config.defaultAction)
                && !"alert".equalsIgnoreCase(config.defaultAction)) {
            config.defaultAction = "log";
        }
        if (config.retentionDays < 1) config.retentionDays = 1;
        if (config.rateWindowSecond < 1) config.rateWindowSecond = 1;
        if (config.rateMaxMessages < 2) config.rateMaxMessages = 2;
        if (config.rateLongWindowSecond < config.rateWindowSecond) {
            config.rateLongWindowSecond = config.rateWindowSecond;
        }
        if (config.rateLongMaxMessages < config.rateMaxMessages) {
            config.rateLongMaxMessages = config.rateMaxMessages;
        }
        if (config.repeatFingerprintChars < 8) config.repeatFingerprintChars = 8;
        if (config.repeatFingerprintChars > 500) config.repeatFingerprintChars = 500;
        if (config.repeatWindowSecond < 1) config.repeatWindowSecond = 1;
        if (config.repeatMaxCount < 2) config.repeatMaxCount = 2;
        if (config.repeatLongWindowSecond < config.repeatWindowSecond) {
            config.repeatLongWindowSecond = config.repeatWindowSecond;
        }
        if (config.repeatLongMaxCount < config.repeatMaxCount) {
            config.repeatLongMaxCount = config.repeatMaxCount;
        }
        if (config.longTextMaxChars < 0) config.longTextMaxChars = 0;
        if (config.mediaFloodMaxCount < 1) config.mediaFloodMaxCount = 1;
        if (config.repeatBanterMinUsers < 2) config.repeatBanterMinUsers = 2;
        if (config.repeatBanterMinUsers > 100) config.repeatBanterMinUsers = 100;
        if (config.repeatBanterMaxUsers < config.repeatBanterMinUsers) {
            config.repeatBanterMaxUsers = config.repeatBanterMinUsers;
        }
        if (config.repeatBanterMaxUsers > 100) config.repeatBanterMaxUsers = 100;
        if (config.collectiveWarnCooldownSecond < 0) config.collectiveWarnCooldownSecond = 0;
        if (config.repeatGroupWindowSecond < 1) config.repeatGroupWindowSecond = 1;
        if (config.repeatGroupCount < 0) config.repeatGroupCount = 0;
        //群内复读阈值不得低于个人复读阈值，否则一个人连刷会先命中群级规则
        if (config.repeatGroupCount > 0 && config.repeatGroupCount < config.repeatMaxCount) {
            config.repeatGroupCount = config.repeatMaxCount;
        }
        if (config.repeatGroupLongWindowSecond < config.repeatGroupWindowSecond) {
            config.repeatGroupLongWindowSecond = config.repeatGroupWindowSecond;
        }
        if (config.repeatGroupLongCount < config.repeatGroupCount) {
            config.repeatGroupLongCount = config.repeatGroupCount;
        }
        if (config.noticeCooldownSecond < 0) config.noticeCooldownSecond = 0;
        if (config.violationCountMode == null
                || !"message".equalsIgnoreCase(config.violationCountMode.trim())) {
            config.violationCountMode = "session";
        } else {
            config.violationCountMode = "message";
        }
        if (config.violationCooldownSecond < 0) config.violationCooldownSecond = 0;
        if (config.mentionWindowSecond < 1) config.mentionWindowSecond = 1;
        if (config.mentionMaxCount < 2) config.mentionMaxCount = 2;
        if (config.minMessageLength < 0) config.minMessageLength = 0;
        if (config.deleteAfterViolations < 0) config.deleteAfterViolations = 0;
        if (config.banAfterViolations < 0) config.banAfterViolations = 0;
        if (config.violationWindowSecond < 1) config.violationWindowSecond = 1;
        if (config.banDurationSecond < 1) config.banDurationSecond = 1;
        if (config.banDurationMinute < 1) config.banDurationMinute = 1;
        if (config.banDurationMinute > 43200) config.banDurationMinute = 43200;
        if (config.maxTrackedUsers < 100) config.maxTrackedUsers = 100;
        return config;
    }
}
