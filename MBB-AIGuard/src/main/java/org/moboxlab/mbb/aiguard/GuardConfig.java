package org.moboxlab.mbb.aiguard;

import org.moboxlab.moboxbot.API.Plugin;

/**
 * MBB-AIGuard 配置
 */
public class GuardConfig {
    public boolean enable = true;
    public boolean observeOnly = true;
    public String aiProfile = "default";
    public int contextMessages = 15;
    public int historyDays = 7;
    public int consecutiveRiskDays = 3;
    public int candidateRiskScore = 35;
    public int riskThreshold = 70;
    public int checkCooldownSecond = 60;
    public int maxAiChecksPerMinute = 10;
    public boolean alertCurrentGroup = false;
    public boolean alertAdminPrivate = true;
    public boolean whitelistBypassSafety = false;
    public int retentionDays = 30;
    public boolean defaultGroupEnable = false;

    public static GuardConfig load(Plugin plugin) {
        GuardConfig config = new GuardConfig();
        config.enable = plugin.getConfig().getBoolean("enable",true);
        config.observeOnly = plugin.getConfig().getBoolean("observeOnly",true);
        config.aiProfile = plugin.getConfig().getString("aiProfile","default");
        config.contextMessages = plugin.getConfig().getInt("contextMessages",15);
        config.historyDays = plugin.getConfig().getInt("historyDays",7);
        config.consecutiveRiskDays = plugin.getConfig().getInt("consecutiveRiskDays",3);
        config.candidateRiskScore = plugin.getConfig().getInt("candidateRiskScore",35);
        config.riskThreshold = plugin.getConfig().getInt("riskThreshold",70);
        config.checkCooldownSecond = plugin.getConfig().getInt("checkCooldownSecond",60);
        config.maxAiChecksPerMinute = plugin.getConfig().getInt("maxAiChecksPerMinute",10);
        config.alertCurrentGroup = plugin.getConfig().getBoolean("alertCurrentGroup",false);
        config.alertAdminPrivate = plugin.getConfig().getBoolean("alertAdminPrivate",true);
        config.whitelistBypassSafety = plugin.getConfig().getBoolean("whitelistBypassSafety",false);
        config.retentionDays = plugin.getConfig().getInt("retentionDays",30);
        config.defaultGroupEnable = plugin.getConfig().getBoolean("defaultGroupEnable",false);
        if (config.aiProfile == null || config.aiProfile.trim().isEmpty()) config.aiProfile = "default";
        if (config.contextMessages < 1) config.contextMessages = 1;
        if (config.contextMessages > 50) config.contextMessages = 50;
        if (config.historyDays < 1) config.historyDays = 1;
        if (config.historyDays > 30) config.historyDays = 30;
        if (config.consecutiveRiskDays < 1) config.consecutiveRiskDays = 1;
        if (config.riskThreshold < 1) config.riskThreshold = 1;
        if (config.riskThreshold > 100) config.riskThreshold = 100;
        if (config.checkCooldownSecond < 0) config.checkCooldownSecond = 0;
        if (config.maxAiChecksPerMinute < 1) config.maxAiChecksPerMinute = 1;
        if (config.retentionDays < 1) config.retentionDays = 1;
        return config;
    }
}
