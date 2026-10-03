package org.moboxlab.mbb.roleplay;

import org.moboxlab.moboxbot.API.Plugin;

/**
 * MBB-Roleplay 配置
 */
public class RoleplayConfig {
    public boolean enable = true;
    public String aiProfile = "default";
    public int replyCooldownSecond = 90;
    public int maxRepliesPerHour = 20;
    public double interestReplyChance = 0.35;
    public int shortContextMessages = 40;
    public int shortTermDays = 3;
    public int memoryUpdateMessages = 30;
    public int maxLongMemories = 30;
    public int replyMaxTokens = 1200;
    public String personaFile = "persona.json";
    public int minMessageLength = 4;

    public static RoleplayConfig load(Plugin plugin) {
        RoleplayConfig config = new RoleplayConfig();
        config.enable = plugin.getConfig().getBoolean("enable",true);
        config.aiProfile = plugin.getConfig().getString("aiProfile","default");
        config.replyCooldownSecond = plugin.getConfig().getInt("replyCooldownSecond",90);
        config.maxRepliesPerHour = plugin.getConfig().getInt("maxRepliesPerHour",20);
        try {
            config.interestReplyChance = Double.parseDouble(plugin.getConfig().getString("interestReplyChance","0.35"));
        } catch (Exception e) {
            config.interestReplyChance = 0.35;
        }
        config.shortContextMessages = plugin.getConfig().getInt("shortContextMessages",40);
        config.shortTermDays = plugin.getConfig().getInt("shortTermDays",3);
        config.memoryUpdateMessages = plugin.getConfig().getInt("memoryUpdateMessages",30);
        config.maxLongMemories = plugin.getConfig().getInt("maxLongMemories",30);
        config.replyMaxTokens = plugin.getConfig().getInt("replyMaxTokens",1200);
        config.personaFile = plugin.getConfig().getString("personaFile","persona.json");
        config.minMessageLength = plugin.getConfig().getInt("minMessageLength",4);
        if (config.aiProfile == null || config.aiProfile.trim().isEmpty()) config.aiProfile = "default";
        if (config.replyCooldownSecond < 0) config.replyCooldownSecond = 0;
        if (config.maxRepliesPerHour < 1) config.maxRepliesPerHour = 1;
        if (config.interestReplyChance < 0) config.interestReplyChance = 0;
        if (config.interestReplyChance > 1) config.interestReplyChance = 1;
        if (config.shortContextMessages < 1) config.shortContextMessages = 1;
        if (config.shortContextMessages > 200) config.shortContextMessages = 200;
        if (config.shortTermDays < 1) config.shortTermDays = 1;
        if (config.memoryUpdateMessages < 5) config.memoryUpdateMessages = 5;
        if (config.maxLongMemories < 5) config.maxLongMemories = 5;
        if (config.replyMaxTokens < 200) config.replyMaxTokens = 200;
        if (config.replyMaxTokens > 8000) config.replyMaxTokens = 8000;
        if (config.minMessageLength < 1) config.minMessageLength = 1;
        return config;
    }
}
