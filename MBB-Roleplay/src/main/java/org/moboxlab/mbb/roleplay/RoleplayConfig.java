package org.moboxlab.mbb.roleplay;

import org.moboxlab.moboxbot.API.Plugin;

import java.util.Locale;

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
    public String memoryProfile = "";
    public String replyReasoningEffort = "low";
    public String memoryReasoningEffort = "low";
    public String timeZone = "Asia/Shanghai";
    public int replyCooldownSecond = 5;
    public int maxRepliesPerHour = 180;
    public int initialAffinity = 70;
    public boolean reminderEnable = true;
    public boolean reminderAiParse = true;
    public int reminderMaxDays = 30;
    public double interestReplyChance = 0.65;
    public int conversationWindowSecond = 180;
    public double continuationReplyChance = 0.80;
    public double otherParticipantReplyChance = 0.45;
    public double otherRoleBotReplyChance = DEFAULT_OTHER_ROLE_BOT_REPLY_CHANCE;
    public int maxConsecutiveOtherRoleMessages = DEFAULT_MAX_CONSECUTIVE_OTHER_ROLE_MESSAGES;
    public String otherRoleBotNames = DEFAULT_OTHER_ROLE_BOT_NAMES;
    public String otherRoleBotQQs = "";
    public int shortContextMessages = 120;
    public int shortTermDays = 3;
    public int memoryUpdateMessages = 50;
    public int memoryExtractMessages = 300;
    public int memoryExtractMaxChars = 16000;
    public int memoryExtractBatches = 3;
    public int memoryMaxTokens = 12000;
    public boolean globalMemoryEnable = true;
    public String globalMemoryLearnGroups = "";
    public int globalMemoryMaxItems = 300;
    public int globalMemoryInjectItems = 80;
    public boolean speechCorpusEnable = true;
    public String speechCorpusDirectory = "speech-corpus";
    public int speechRetrievalCount = 8;
    public int speechRetrievalMaxChars = 1200;
    public double speechRetrievalMinScore = 0.35;
    public double speechSimilarityThreshold = 0.78;
    public int speechSimilarityMinChars = 6;
    public boolean activeMemory = true;
    public boolean imageUnderstandingEnable = true;
    public String imageUnderstandingMode = "addressed";
    public int imageUnderstandingMaxPerHour = 30;
    public boolean imageUnderstandingInjectOcr = true;
    public String imageUnderstandingProfile = "";
    public int imageUnderstandingMaxChars = 600;
    public int imageContextTimeoutSecond = 300;
    public boolean stickerAttachEnable = true;
    public int stickerAttachWindowSecond = 7;
    public int stickerAttachMaxWaitSecond = 15;
    public int maxLongMemories = 150;
    public int replyMaxTokens = 1200;
    public int replyImageMaxTokens = 4000;
    public int replySegmentMaxChars = 160;
    public int replyMaxSegments = 2;
    public int recentReplyCheckCount = 8;
    public double repeatSimilarityThreshold = 0.72;
    public int repeatCheckMinChars = 6;
    public int repeatOpeningLimit = 2;
    public String personaFile = "persona-aris.json";
    public int minMessageLength = 2;
    public String commandPrefixes = "/,!,＃,#";

    public static RoleplayConfig load(Plugin plugin) {
        RoleplayConfig config = new RoleplayConfig();
        config.enable = plugin.getConfig().getBoolean("enable",true);
        config.aiProfile = plugin.getConfig().getString("aiProfile","default");
        config.memoryProfile = plugin.getConfig().getString("memoryProfile","");
        config.replyReasoningEffort = normalizeEffort(plugin.getConfig().getString("replyReasoningEffort","low"));
        config.memoryReasoningEffort = normalizeEffort(plugin.getConfig().getString("memoryReasoningEffort","low"));
        config.timeZone = plugin.getConfig().getString("timeZone","Asia/Shanghai");
        config.replyCooldownSecond = plugin.getConfig().getInt("replyCooldownSecond",5);
        config.maxRepliesPerHour = plugin.getConfig().getInt("maxRepliesPerHour",180);
        config.initialAffinity = plugin.getConfig().getInt("initialAffinity",70);
        config.reminderEnable = plugin.getConfig().getBoolean("reminderEnable",true);
        config.reminderAiParse = plugin.getConfig().getBoolean("reminderAiParse",true);
        config.reminderMaxDays = plugin.getConfig().getInt("reminderMaxDays",30);
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
        config.shortContextMessages = plugin.getConfig().getInt("shortContextMessages",120);
        config.shortTermDays = plugin.getConfig().getInt("shortTermDays",3);
        config.memoryUpdateMessages = plugin.getConfig().getInt("memoryUpdateMessages",50);
        config.memoryExtractMessages = plugin.getConfig().getInt("memoryExtractMessages",300);
        config.memoryExtractMaxChars = plugin.getConfig().getInt("memoryExtractMaxChars",16000);
        config.memoryExtractBatches = plugin.getConfig().getInt("memoryExtractBatches",3);
        config.memoryMaxTokens = plugin.getConfig().getInt("memoryMaxTokens",12000);
        config.globalMemoryEnable = plugin.getConfig().getBoolean("globalMemoryEnable",true);
        config.globalMemoryLearnGroups = plugin.getConfig().getString("globalMemoryLearnGroups","");
        config.globalMemoryMaxItems = plugin.getConfig().getInt("globalMemoryMaxItems",300);
        config.globalMemoryInjectItems = plugin.getConfig().getInt("globalMemoryInjectItems",80);
        config.speechCorpusEnable = plugin.getConfig().getBoolean("speechCorpusEnable",true);
        config.speechCorpusDirectory = plugin.getConfig().getString("speechCorpusDirectory","speech-corpus");
        config.speechRetrievalCount = plugin.getConfig().getInt("speechRetrievalCount",8);
        config.speechRetrievalMaxChars = plugin.getConfig().getInt("speechRetrievalMaxChars",1200);
        try {
            config.speechRetrievalMinScore = Double.parseDouble(
                    plugin.getConfig().getString("speechRetrievalMinScore","0.35"));
        } catch (Exception e) {
            config.speechRetrievalMinScore = 0.35;
        }
        try {
            config.speechSimilarityThreshold = Double.parseDouble(
                    plugin.getConfig().getString("speechSimilarityThreshold","0.78"));
        } catch (Exception e) {
            config.speechSimilarityThreshold = 0.78;
        }
        config.speechSimilarityMinChars = plugin.getConfig().getInt("speechSimilarityMinChars",6);
        config.activeMemory = plugin.getConfig().getBoolean("activeMemory",true);
        config.imageUnderstandingEnable = plugin.getConfig().getBoolean("imageUnderstandingEnable",true);
        config.imageUnderstandingMode = plugin.getConfig().getString("imageUnderstandingMode","addressed");
        config.imageUnderstandingMaxPerHour = plugin.getConfig().getInt("imageUnderstandingMaxPerHour",30);
        config.imageUnderstandingInjectOcr = plugin.getConfig().getBoolean("imageUnderstandingInjectOcr",true);
        config.imageUnderstandingProfile = plugin.getConfig().getString("imageUnderstandingProfile","");
        config.imageUnderstandingMaxChars = plugin.getConfig().getInt("imageUnderstandingMaxChars",600);
        config.imageContextTimeoutSecond = plugin.getConfig().getInt("imageContextTimeoutSecond",300);
        config.stickerAttachEnable = plugin.getConfig().getBoolean("stickerAttachEnable",true);
        config.stickerAttachWindowSecond = plugin.getConfig().getInt("stickerAttachWindowSecond",7);
        config.stickerAttachMaxWaitSecond = plugin.getConfig().getInt("stickerAttachMaxWaitSecond",15);
        config.maxLongMemories = plugin.getConfig().getInt("maxLongMemories",150);
        config.replyMaxTokens = plugin.getConfig().getInt("replyMaxTokens",1200);
        config.replyImageMaxTokens = plugin.getConfig().getInt("replyImageMaxTokens",4000);
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
        config.personaFile = plugin.getConfig().getString("personaFile","persona-aris.json");
        config.minMessageLength = plugin.getConfig().getInt("minMessageLength",2);
        config.commandPrefixes = plugin.getConfig().getString("commandPrefixes","/,!,＃,#");
        if (config.aiProfile == null || config.aiProfile.trim().isEmpty()) config.aiProfile = "default";
        if (config.memoryProfile == null) config.memoryProfile = "";
        if (config.timeZone == null || config.timeZone.trim().isEmpty()) config.timeZone = "Asia/Shanghai";
        if (config.replyCooldownSecond < 0) config.replyCooldownSecond = 0;
        if (config.maxRepliesPerHour < 1) config.maxRepliesPerHour = 1;
        if (config.initialAffinity < 0) config.initialAffinity = 0;
        if (config.initialAffinity > 100) config.initialAffinity = 100;
        if (config.reminderMaxDays < 1) config.reminderMaxDays = 1;
        if (config.reminderMaxDays > 365) config.reminderMaxDays = 365;
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
        if (config.otherRoleBotNames == null || config.otherRoleBotNames.trim().isEmpty()) {
            config.otherRoleBotNames = DEFAULT_OTHER_ROLE_BOT_NAMES;
        }
        if (config.otherRoleBotQQs == null) config.otherRoleBotQQs = "";
        if (config.shortContextMessages < 1) config.shortContextMessages = 1;
        if (config.shortContextMessages > 300) config.shortContextMessages = 300;
        if (config.shortTermDays < 1) config.shortTermDays = 1;
        if (config.memoryUpdateMessages < 5) config.memoryUpdateMessages = 5;
        if (config.memoryExtractMessages < 50) config.memoryExtractMessages = 50;
        if (config.memoryExtractMessages > 1000) config.memoryExtractMessages = 1000;
        if (config.memoryExtractMaxChars < 2000) config.memoryExtractMaxChars = 2000;
        if (config.memoryExtractMaxChars > 50000) config.memoryExtractMaxChars = 50000;
        if (config.memoryExtractBatches < 1) config.memoryExtractBatches = 1;
        if (config.memoryExtractBatches > 10) config.memoryExtractBatches = 10;
        if (config.memoryMaxTokens < 2000) config.memoryMaxTokens = 2000;
        if (config.memoryMaxTokens > 32000) config.memoryMaxTokens = 32000;
        if (config.globalMemoryLearnGroups == null) config.globalMemoryLearnGroups = "";
        if (config.globalMemoryMaxItems < 20) config.globalMemoryMaxItems = 20;
        if (config.globalMemoryMaxItems > 1000) config.globalMemoryMaxItems = 1000;
        if (config.globalMemoryInjectItems < 10) config.globalMemoryInjectItems = 10;
        if (config.globalMemoryInjectItems > 300) config.globalMemoryInjectItems = 300;
        if (config.speechCorpusDirectory == null || config.speechCorpusDirectory.trim().isEmpty()) {
            config.speechCorpusDirectory = "speech-corpus";
        }
        if (config.speechRetrievalCount < 1) config.speechRetrievalCount = 1;
        if (config.speechRetrievalCount > 30) config.speechRetrievalCount = 30;
        if (config.speechRetrievalMaxChars < 200) config.speechRetrievalMaxChars = 200;
        if (config.speechRetrievalMaxChars > 6000) config.speechRetrievalMaxChars = 6000;
        if (config.speechRetrievalMinScore < 0) config.speechRetrievalMinScore = 0;
        if (config.speechRetrievalMinScore > 1) config.speechRetrievalMinScore = 1;
        if (config.speechSimilarityThreshold < 0.3) config.speechSimilarityThreshold = 0.3;
        if (config.speechSimilarityThreshold > 1) config.speechSimilarityThreshold = 1;
        if (config.speechSimilarityMinChars < 2) config.speechSimilarityMinChars = 2;
        if (config.imageUnderstandingMode == null || config.imageUnderstandingMode.trim().isEmpty()) {
            config.imageUnderstandingMode = "addressed";
        }
        config.imageUnderstandingMode = config.imageUnderstandingMode.trim().toLowerCase();
        if (!"off".equals(config.imageUnderstandingMode)
                && !"addressed".equals(config.imageUnderstandingMode)
                && !"all".equals(config.imageUnderstandingMode)) {
            config.imageUnderstandingMode = "addressed";
        }
        if (config.imageUnderstandingMaxPerHour < 0) config.imageUnderstandingMaxPerHour = 0;
        if (config.imageUnderstandingMaxPerHour > 500) config.imageUnderstandingMaxPerHour = 500;
        if (config.imageUnderstandingProfile == null) config.imageUnderstandingProfile = "";
        if (config.imageUnderstandingMaxChars < 100) config.imageUnderstandingMaxChars = 100;
        if (config.imageUnderstandingMaxChars > 3000) config.imageUnderstandingMaxChars = 3000;
        if (config.imageContextTimeoutSecond < 10) config.imageContextTimeoutSecond = 10;
        if (config.imageContextTimeoutSecond > 3600) config.imageContextTimeoutSecond = 3600;
        if (config.stickerAttachWindowSecond < 7) config.stickerAttachWindowSecond = 7;
        if (config.stickerAttachWindowSecond > 15) config.stickerAttachWindowSecond = 15;
        if (config.stickerAttachMaxWaitSecond < config.stickerAttachWindowSecond) {
            config.stickerAttachMaxWaitSecond = config.stickerAttachWindowSecond;
        }
        if (config.stickerAttachMaxWaitSecond > 60) config.stickerAttachMaxWaitSecond = 60;
        if (config.maxLongMemories < 5) config.maxLongMemories = 5;
        if (config.maxLongMemories > 500) config.maxLongMemories = 500;
        if (config.replyMaxTokens < 200) config.replyMaxTokens = 200;
        if (config.replyMaxTokens > 8000) config.replyMaxTokens = 8000;
        if (config.replyImageMaxTokens < 400) config.replyImageMaxTokens = 400;
        if (config.replyImageMaxTokens > 16000) config.replyImageMaxTokens = 16000;
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

    // 思考强度只允许 low/medium/high，留空表示不向接口发送该字段
    private static String normalizeEffort(String value) {
        if (value == null) return "";
        String text = value.trim().toLowerCase(Locale.ROOT);
        if ("low".equals(text) || "medium".equals(text) || "high".equals(text)) return text;
        return "";
    }
}
