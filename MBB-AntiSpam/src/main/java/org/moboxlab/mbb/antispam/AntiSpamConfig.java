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

    public boolean mentionEnable = true;
    public int mentionWindowSecond = 60;
    public int mentionMaxCount = 8;
    public boolean mentionAllEnable = true;

    public int minMessageLength = 2;

    public int deleteAfterViolations = 2;
    public int banAfterViolations = 3;
    public int violationWindowSecond = 300;
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

        config.mentionEnable = plugin.getConfig().getBoolean("mentionEnable",true);
        config.mentionWindowSecond = plugin.getConfig().getInt("mentionWindowSecond",60);
        config.mentionMaxCount = plugin.getConfig().getInt("mentionMaxCount",8);
        config.mentionAllEnable = plugin.getConfig().getBoolean("mentionAllEnable",true);

        config.minMessageLength = plugin.getConfig().getInt("minMessageLength",2);

        config.deleteAfterViolations = plugin.getConfig().getInt("deleteAfterViolations",2);
        config.banAfterViolations = plugin.getConfig().getInt("banAfterViolations",3);
        config.violationWindowSecond = plugin.getConfig().getInt("violationWindowSecond",300);
        config.banDurationSecond = plugin.getConfig().getInt("banDurationSecond",300);

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
        if (config.mentionWindowSecond < 1) config.mentionWindowSecond = 1;
        if (config.mentionMaxCount < 2) config.mentionMaxCount = 2;
        if (config.minMessageLength < 0) config.minMessageLength = 0;
        if (config.deleteAfterViolations < 0) config.deleteAfterViolations = 0;
        if (config.banAfterViolations < 0) config.banAfterViolations = 0;
        if (config.violationWindowSecond < 1) config.violationWindowSecond = 1;
        if (config.banDurationSecond < 1) config.banDurationSecond = 1;
        if (config.maxTrackedUsers < 100) config.maxTrackedUsers = 100;
        return config;
    }
}
