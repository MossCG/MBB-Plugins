package org.moboxlab.mbb.roleplay;

import org.moboxlab.moboxbot.API.Plugin;

/**
 * MBB-Roleplay 配置
 */
public class RoleplayConfig {
    public static final double DEFAULT_OTHER_ROLE_BOT_REPLY_CHANCE = 0.50;
    public static final int DEFAULT_MAX_CONSECUTIVE_OTHER_ROLE_MESSAGES = 2;
    public static final String DEFAULT_OTHER_ROLE_BOT_NAMES =
            "桃井,小桃,桃,才羽桃井,绿,小绿,才羽绿,爱丽丝,天童爱丽丝,モモイ,ミドリ";

    public boolean enable = true;
    public String aiProfile = "default";
    public int replyCooldownSecond = 5;
    public int maxRepliesPerHour = 180;
    public int initialAffinity = 70;
    public double interestReplyChance = 0.65;
    public int conversationWindowSecond = 180;
    public double continuationReplyChance = 0.80;
    public double otherParticipantReplyChance = 0.45;
    public double otherRoleBotReplyChance = DEFAULT_OTHER_ROLE_BOT_REPLY_CHANCE;
    public int maxConsecutiveOtherRoleMessages = DEFAULT_MAX_CONSECUTIVE_OTHER_ROLE_MESSAGES;
    public String otherRoleBotNames = DEFAULT_OTHER_ROLE_BOT_NAMES;
    public String otherRoleBotQQs = "";
    public int shortContextMessages = 80;
    public int shortTermDays = 3;
    public int memoryUpdateMessages = 50;
    public int memoryExtractMessages = 300;
    public int memoryExtractBatches = 3;
    public boolean activeMemory = true;
    public int maxLongMemories = 150;
    public int replyMaxTokens = 1200;
    public int replySegmentMaxChars = 160;
    public int replyMaxSegments = 2;
    public int recentReplyCheckCount = 8;
    public double repeatSimilarityThreshold = 0.72;
    public int repeatCheckMinChars = 6;
    public int repeatOpeningLimit = 2;
    public String personaFile = "persona.json";
    public int minMessageLength = 2;
    public String commandPrefixes = "/,!,＃,#";

    public static RoleplayConfig load(Plugin plugin) {
        RoleplayConfig config = new RoleplayConfig();
        config.enable = plugin.getConfig().getBoolean("enable",true);
        config.aiProfile = plugin.getConfig().getString("aiProfile","default");
        config.replyCooldownSecond = plugin.getConfig().getInt("replyCooldownSecond",5);
        config.maxRepliesPerHour = plugin.getConfig().getInt("maxRepliesPerHour",180);
        config.initialAffinity = plugin.getConfig().getInt("initialAffinity",70);
        try {
            config.interestReplyChance = Double.parseDouble(plugin.getConfig().getString("interestReplyChance","0.65"));
        } catch (Exception e) {
            config.interestReplyChance = 0.65;
        }
        config.conversationWindowSecond = plugin.getConfig().getInt("conversationWindowSecond",180);
        try {
            config.continuationReplyChance = Double.parseDouble(plugin.getConfig().getString("continuationReplyChance","0.80"));
        } catch (Exception e) {
            config.continuationReplyChance = 0.80;
        }
        try {
            config.otherParticipantReplyChance = Double.parseDouble(plugin.getConfig().getString("otherParticipantReplyChance","0.45"));
        } catch (Exception e) {
            config.otherParticipantReplyChance = 0.45;
        }
        try {
            config.otherRoleBotReplyChance = Double.parseDouble(plugin.getConfig().getString(
                    "otherRoleBotReplyChance",String.valueOf(DEFAULT_OTHER_ROLE_BOT_REPLY_CHANCE)));
        } catch (Exception e) {
            config.otherRoleBotReplyChance = DEFAULT_OTHER_ROLE_BOT_REPLY_CHANCE;
        }
        config.maxConsecutiveOtherRoleMessages = plugin.getConfig().getInt(
                "maxConsecutiveOtherRoleMessages",DEFAULT_MAX_CONSECUTIVE_OTHER_ROLE_MESSAGES);
        config.otherRoleBotNames = plugin.getConfig().getString("otherRoleBotNames",config.otherRoleBotNames);
        config.otherRoleBotQQs = plugin.getConfig().getString("otherRoleBotQQs","");
        config.shortContextMessages = plugin.getConfig().getInt("shortContextMessages",80);
        config.shortTermDays = plugin.getConfig().getInt("shortTermDays",3);
        config.memoryUpdateMessages = plugin.getConfig().getInt("memoryUpdateMessages",50);
        config.memoryExtractMessages = plugin.getConfig().getInt("memoryExtractMessages",300);
        config.memoryExtractBatches = plugin.getConfig().getInt("memoryExtractBatches",3);
        config.activeMemory = plugin.getConfig().getBoolean("activeMemory",true);
        config.maxLongMemories = plugin.getConfig().getInt("maxLongMemories",150);
        config.replyMaxTokens = plugin.getConfig().getInt("replyMaxTokens",1200);
        config.replySegmentMaxChars = plugin.getConfig().getInt("replySegmentMaxChars",160);
        config.replyMaxSegments = plugin.getConfig().getInt("replyMaxSegments",2);
        config.recentReplyCheckCount = plugin.getConfig().getInt("recentReplyCheckCount",8);
        try {
            config.repeatSimilarityThreshold = Double.parseDouble(plugin.getConfig().getString("repeatSimilarityThreshold","0.72"));
        } catch (Exception e) {
            config.repeatSimilarityThreshold = 0.72;
        }
        config.repeatCheckMinChars = plugin.getConfig().getInt("repeatCheckMinChars",6);
        config.repeatOpeningLimit = plugin.getConfig().getInt("repeatOpeningLimit",2);
        config.personaFile = plugin.getConfig().getString("personaFile","persona.json");
        config.minMessageLength = plugin.getConfig().getInt("minMessageLength",2);
        config.commandPrefixes = plugin.getConfig().getString("commandPrefixes","/,!,＃,#");
        if (config.aiProfile == null || config.aiProfile.trim().isEmpty()) config.aiProfile = "default";
        if (config.replyCooldownSecond < 0) config.replyCooldownSecond = 0;
        if (config.maxRepliesPerHour < 1) config.maxRepliesPerHour = 1;
        if (config.initialAffinity < 0) config.initialAffinity = 0;
        if (config.initialAffinity > 100) config.initialAffinity = 100;
        if (config.interestReplyChance < 0) config.interestReplyChance = 0;
        if (config.interestReplyChance > 1) config.interestReplyChance = 1;
        if (config.conversationWindowSecond < 10) config.conversationWindowSecond = 10;
        if (config.continuationReplyChance < 0) config.continuationReplyChance = 0;
        if (config.continuationReplyChance > 1) config.continuationReplyChance = 1;
        if (config.otherParticipantReplyChance < 0) config.otherParticipantReplyChance = 0;
        if (config.otherParticipantReplyChance > 1) config.otherParticipantReplyChance = 1;
        if (config.otherRoleBotReplyChance < 0) config.otherRoleBotReplyChance = 0;
        if (config.otherRoleBotReplyChance > 1) config.otherRoleBotReplyChance = 1;
        if (config.maxConsecutiveOtherRoleMessages < 0) config.maxConsecutiveOtherRoleMessages = 0;
        if (config.otherRoleBotNames == null) config.otherRoleBotNames = "";
        if (config.otherRoleBotQQs == null) config.otherRoleBotQQs = "";
        if (config.shortContextMessages < 1) config.shortContextMessages = 1;
        if (config.shortContextMessages > 300) config.shortContextMessages = 300;
        if (config.shortTermDays < 1) config.shortTermDays = 1;
        if (config.memoryUpdateMessages < 5) config.memoryUpdateMessages = 5;
        if (config.memoryExtractMessages < 50) config.memoryExtractMessages = 50;
        if (config.memoryExtractMessages > 1000) config.memoryExtractMessages = 1000;
        if (config.memoryExtractBatches < 1) config.memoryExtractBatches = 1;
        if (config.memoryExtractBatches > 10) config.memoryExtractBatches = 10;
        if (config.maxLongMemories < 5) config.maxLongMemories = 5;
        if (config.maxLongMemories > 500) config.maxLongMemories = 500;
        if (config.replyMaxTokens < 200) config.replyMaxTokens = 200;
        if (config.replyMaxTokens > 8000) config.replyMaxTokens = 8000;
        if (config.replySegmentMaxChars < 40) config.replySegmentMaxChars = 40;
        if (config.replySegmentMaxChars > 300) config.replySegmentMaxChars = 300;
        if (config.replyMaxSegments < 1) config.replyMaxSegments = 1;
        if (config.replyMaxSegments > 2) config.replyMaxSegments = 2;
        if (config.recentReplyCheckCount < 1) config.recentReplyCheckCount = 1;
        if (config.recentReplyCheckCount > 30) config.recentReplyCheckCount = 30;
        if (config.repeatSimilarityThreshold < 0.3) config.repeatSimilarityThreshold = 0.3;
        if (config.repeatSimilarityThreshold > 1) config.repeatSimilarityThreshold = 1;
        if (config.repeatCheckMinChars < 2) config.repeatCheckMinChars = 2;
        if (config.repeatOpeningLimit < 1) config.repeatOpeningLimit = 1;
        if (config.minMessageLength < 1) config.minMessageLength = 1;
        if (config.commandPrefixes == null) config.commandPrefixes = "/,!,＃,#";
        return config;
    }
}
