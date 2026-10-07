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
    public int initialAffinity = 65;
    public int initialTrust = 60;
    public double initialGroupAdminMultiplier = 1.05;
    public double initialBotAdminMultiplier = 1.10;
    public double initialBotOwnerMultiplier = 1.15;
    public double relationDailyMaxDelta = 2.0;
    public int intimacyCloseAffinity = 75;
    public int intimacyVeryCloseAffinity = 90;
    public boolean emotionEnable = true;
    public int emotionDecayMinute = 30;
    public int emotionEventCooldownSecond = 300;
    public boolean emotionAnalyzeEnable = true;
    public String emotionAnalyzeMode = "significant";
    public String emotionAnalyzeProfile = "";
    public int emotionAnalyzeCooldownSecond = 300;
    public int emotionAnalyzeMaxTokens = 1200;
    public String emotionAnalyzeReasoningEffort = "low";
    public double emotionAnalyzeMaxDelta = 0.5;
    public double emotionAnalyzeMaxDecreaseDelta = 0.3;
    public int emotionReasonMaxChars = 120;
    public int emotionReasonMinStrength = 40;
    public int emotionReasonDecayDays = 30;
    public int emotionEventRetentionDays = 90;
    public double emotionPositiveReplyBonus = 0.05;
    public double emotionNegativeReplyPenalty = 0.15;
    public double emotionAffinityReplyBonus = 0.08;
    public double emotionAffinityReplyPenalty = 0.15;
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
    public boolean addressedOtherMemberSkip = true;
    public int splitMessageSuppressSecond = 20;
    public int splitMessageSuppressMaxChars = 20;
    public int shortContextMessages = 120;
    public int shortTermDays = 3;
    public int memoryUpdateMessages = 50;
    public int memoryExtractMessages = 300;
    public int memoryExtractMaxChars = 16000;
    public int memoryExtractBatches = 3;
    public int memoryMaxTokens = 12000;
    public int memoryMergeMaxTokens = 32000;
    public int memoryMergeBatchSize = 60;
    public int memoryMergeMaxRounds = 3;
    public int memoryTimeoutSecond = 300;
    public boolean globalMemoryEnable = true;
    public String globalMemoryLearnGroups = "";
    public int globalMemoryMaxItems = 300;
    public int globalMemoryInjectItems = 80;
    public boolean speechCorpusEnable = true;
    public String speechCorpusDirectory = "speech-corpus";
    public boolean knowledgeEnable = true;
    public String knowledgeDirectory = "knowledge";
    public String knowledgeStudentsLibrary = "ba.students";
    public int knowledgeMaxEntries = 4;
    public int knowledgeMaxSectionsPerEntry = 2;
    public double knowledgeMinScore = 1.0;
    public int knowledgeInjectMaxChars = 2000;
    public int knowledgeSpoilerLevel = 0;
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
    public boolean imageAsyncEnable = true;
    public boolean memoryRelevanceSort = true;
    public int memoryRelevancePoolSize = 600;
    public int memoryRelevanceMaxChars = 5000;
    public int globalMemoryRelevanceMaxChars = 4000;
    public boolean stickerAttachEnable = true;
    public int stickerAttachWindowSecond = 5;
    public int stickerAttachMaxWaitSecond = 15;
    public boolean stickerAttachWaitUntilNextMessage = true;
    public boolean messageBatchEnable = true;
    public int messageBatchWindowSecond = 2;
    public int messageBatchMaxMessages = 10;
    public int messageBatchMaxAgeSecond = 20;
    public int batchMaxSegments = 4;
    public int turnThreads = 4;
    public boolean pokeReplyEnable = true;
    public boolean pokeBackEnable = true;
    public int pokeBackCooldownSecond = 60;
    public int pokeStreakWindowSecond = 60;
    public int pokeStreakThreshold = 3;
    public boolean quoteReplyEnable = true;
    public int promptTotalChars = 26000;
    public boolean routerEnable = true;
    public String routerProfile = "";
    public int routerMaxTokens = 2400;
    public String routerReasoningEffort = "none";
    public boolean styleEnable = true;
    public int styleMaxChars = 60;
    public String styleProfile = "";
    public int styleMaxTokens = 400;
    public String styleReasoningEffort = "low";
    public boolean styleProactiveEnable = false;
    public int maxLongMemories = 150;
    public int replyMaxTokens = 1200;
    public int replyImageMaxTokens = 4000;
    public int replySegmentMaxChars = 20;
    public String replySplitPunctuation = "。！？!?；;，、：,:～~";
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
        config.initialAffinity = plugin.getConfig().getInt("initialAffinity",65);
        config.initialTrust = plugin.getConfig().getInt("initialTrust",60);
        try {
            config.initialGroupAdminMultiplier = Double.parseDouble(
                    plugin.getConfig().getString("initialGroupAdminMultiplier","1.05"));
        } catch (Exception e) {
            config.initialGroupAdminMultiplier = 1.05;
        }
        try {
            config.initialBotAdminMultiplier = Double.parseDouble(
                    plugin.getConfig().getString("initialBotAdminMultiplier","1.10"));
        } catch (Exception e) {
            config.initialBotAdminMultiplier = 1.10;
        }
        try {
            config.initialBotOwnerMultiplier = Double.parseDouble(
                    plugin.getConfig().getString("initialBotOwnerMultiplier","1.15"));
        } catch (Exception e) {
            config.initialBotOwnerMultiplier = 1.15;
        }
        try {
            config.relationDailyMaxDelta = Double.parseDouble(
                    plugin.getConfig().getString("relationDailyMaxDelta","2.0"));
        } catch (Exception e) {
            config.relationDailyMaxDelta = 2.0;
        }
        config.intimacyCloseAffinity = plugin.getConfig().getInt("intimacyCloseAffinity",75);
        config.intimacyVeryCloseAffinity = plugin.getConfig().getInt("intimacyVeryCloseAffinity",90);
        config.emotionEnable = plugin.getConfig().getBoolean("emotionEnable",true);
        config.emotionDecayMinute = plugin.getConfig().getInt("emotionDecayMinute",30);
        config.emotionEventCooldownSecond = plugin.getConfig().getInt("emotionEventCooldownSecond",300);
        config.emotionAnalyzeEnable = plugin.getConfig().getBoolean("emotionAnalyzeEnable",true);
        config.emotionAnalyzeMode = plugin.getConfig().getString("emotionAnalyzeMode","significant");
        config.emotionAnalyzeProfile = plugin.getConfig().getString("emotionAnalyzeProfile","");
        config.emotionAnalyzeCooldownSecond = plugin.getConfig().getInt("emotionAnalyzeCooldownSecond",300);
        config.emotionAnalyzeMaxTokens = plugin.getConfig().getInt("emotionAnalyzeMaxTokens",1200);
        config.emotionAnalyzeReasoningEffort = normalizeEffort(
                plugin.getConfig().getString("emotionAnalyzeReasoningEffort","low"));
        try {
            config.emotionAnalyzeMaxDelta = Double.parseDouble(
                    plugin.getConfig().getString("emotionAnalyzeMaxDelta","0.5"));
        } catch (Exception e) {
            config.emotionAnalyzeMaxDelta = 0.5;
        }
        try {
            config.emotionAnalyzeMaxDecreaseDelta = Double.parseDouble(
                    plugin.getConfig().getString("emotionAnalyzeMaxDecreaseDelta","0.3"));
        } catch (Exception e) {
            config.emotionAnalyzeMaxDecreaseDelta = 0.3;
        }
        config.emotionReasonMaxChars = plugin.getConfig().getInt("emotionReasonMaxChars",120);
        config.emotionReasonMinStrength = plugin.getConfig().getInt("emotionReasonMinStrength",40);
        config.emotionReasonDecayDays = plugin.getConfig().getInt("emotionReasonDecayDays",30);
        config.emotionEventRetentionDays = plugin.getConfig().getInt("emotionEventRetentionDays",90);
        try {
            config.emotionPositiveReplyBonus = Double.parseDouble(
                    plugin.getConfig().getString("emotionPositiveReplyBonus","0.05"));
        } catch (Exception e) {
            config.emotionPositiveReplyBonus = 0.05;
        }
        try {
            config.emotionNegativeReplyPenalty = Double.parseDouble(
                    plugin.getConfig().getString("emotionNegativeReplyPenalty","0.15"));
        } catch (Exception e) {
            config.emotionNegativeReplyPenalty = 0.15;
        }
        try {
            config.emotionAffinityReplyBonus = Double.parseDouble(
                    plugin.getConfig().getString("emotionAffinityReplyBonus","0.08"));
        } catch (Exception e) {
            config.emotionAffinityReplyBonus = 0.08;
        }
        try {
            config.emotionAffinityReplyPenalty = Double.parseDouble(
                    plugin.getConfig().getString("emotionAffinityReplyPenalty","0.15"));
        } catch (Exception e) {
            config.emotionAffinityReplyPenalty = 0.15;
        }
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
        config.addressedOtherMemberSkip =
                plugin.getConfig().getBoolean("addressedOtherMemberSkip",true);
        config.splitMessageSuppressSecond =
                plugin.getConfig().getInt("splitMessageSuppressSecond",20);
        config.splitMessageSuppressMaxChars =
                plugin.getConfig().getInt("splitMessageSuppressMaxChars",20);
        config.shortContextMessages = plugin.getConfig().getInt("shortContextMessages",120);
        config.shortTermDays = plugin.getConfig().getInt("shortTermDays",3);
        config.memoryUpdateMessages = plugin.getConfig().getInt("memoryUpdateMessages",50);
        config.memoryExtractMessages = plugin.getConfig().getInt("memoryExtractMessages",300);
        config.memoryExtractMaxChars = plugin.getConfig().getInt("memoryExtractMaxChars",16000);
        config.memoryExtractBatches = plugin.getConfig().getInt("memoryExtractBatches",3);
        config.memoryMaxTokens = plugin.getConfig().getInt("memoryMaxTokens",12000);
        config.memoryMergeMaxTokens = plugin.getConfig().getInt("memoryMergeMaxTokens",32000);
        config.memoryMergeBatchSize = plugin.getConfig().getInt("memoryMergeBatchSize",60);
        config.memoryMergeMaxRounds = plugin.getConfig().getInt("memoryMergeMaxRounds",3);
        config.memoryTimeoutSecond = plugin.getConfig().getInt("memoryTimeoutSecond",300);
        config.globalMemoryEnable = plugin.getConfig().getBoolean("globalMemoryEnable",true);
        config.globalMemoryLearnGroups = plugin.getConfig().getString("globalMemoryLearnGroups","");
        config.globalMemoryMaxItems = plugin.getConfig().getInt("globalMemoryMaxItems",300);
        config.globalMemoryInjectItems = plugin.getConfig().getInt("globalMemoryInjectItems",80);
        config.speechCorpusEnable = plugin.getConfig().getBoolean("speechCorpusEnable",true);
        config.speechCorpusDirectory = plugin.getConfig().getString("speechCorpusDirectory","speech-corpus");
        config.knowledgeEnable = plugin.getConfig().getBoolean("knowledgeEnable",true);
        config.knowledgeDirectory = plugin.getConfig().getString("knowledgeDirectory","knowledge");
        config.knowledgeStudentsLibrary =
                plugin.getConfig().getString("knowledgeStudentsLibrary","ba.students");
        config.knowledgeMaxEntries = plugin.getConfig().getInt("knowledgeMaxEntries",4);
        config.knowledgeMaxSectionsPerEntry =
                plugin.getConfig().getInt("knowledgeMaxSectionsPerEntry",2);
        try {
            config.knowledgeMinScore = Double.parseDouble(
                    plugin.getConfig().getString("knowledgeMinScore","1.0"));
        } catch (Exception e) {
            config.knowledgeMinScore = 1.0;
        }
        config.knowledgeInjectMaxChars = plugin.getConfig().getInt("knowledgeInjectMaxChars",2000);
        config.knowledgeSpoilerLevel = plugin.getConfig().getInt("knowledgeSpoilerLevel",0);
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
        config.imageAsyncEnable = plugin.getConfig().getBoolean("imageAsyncEnable",true);
        config.memoryRelevanceSort = plugin.getConfig().getBoolean("memoryRelevanceSort",true);
        config.memoryRelevancePoolSize =
                plugin.getConfig().getInt("memoryRelevancePoolSize",600);
        config.memoryRelevanceMaxChars =
                plugin.getConfig().getInt("memoryRelevanceMaxChars",5000);
        config.globalMemoryRelevanceMaxChars =
                plugin.getConfig().getInt("globalMemoryRelevanceMaxChars",4000);
        config.stickerAttachEnable = plugin.getConfig().getBoolean("stickerAttachEnable",true);
        config.stickerAttachWindowSecond = plugin.getConfig().getInt("stickerAttachWindowSecond",5);
        config.stickerAttachMaxWaitSecond = plugin.getConfig().getInt("stickerAttachMaxWaitSecond",15);
        config.stickerAttachWaitUntilNextMessage =
                plugin.getConfig().getBoolean("stickerAttachWaitUntilNextMessage",true);
        config.messageBatchEnable = plugin.getConfig().getBoolean("messageBatchEnable",true);
        config.messageBatchWindowSecond = plugin.getConfig().getInt("messageBatchWindowSecond",2);
        config.messageBatchMaxMessages = plugin.getConfig().getInt("messageBatchMaxMessages",10);
        config.messageBatchMaxAgeSecond = plugin.getConfig().getInt("messageBatchMaxAgeSecond",20);
        config.batchMaxSegments = plugin.getConfig().getInt("batchMaxSegments",4);
        config.pokeReplyEnable = plugin.getConfig().getBoolean("pokeReplyEnable",true);
        config.pokeBackEnable = plugin.getConfig().getBoolean("pokeBackEnable",true);
        config.pokeBackCooldownSecond = plugin.getConfig().getInt("pokeBackCooldownSecond",60);
        config.quoteReplyEnable = plugin.getConfig().getBoolean("quoteReplyEnable",true);
        config.promptTotalChars = plugin.getConfig().getInt("promptTotalChars",26000);
        config.routerEnable = plugin.getConfig().getBoolean("routerEnable",true);
        config.routerProfile = plugin.getConfig().getString("routerProfile","");
        config.routerMaxTokens = plugin.getConfig().getInt("routerMaxTokens",2400);
        config.routerReasoningEffort = normalizeEffort(
                plugin.getConfig().getString("routerReasoningEffort","none"));
        config.styleEnable = plugin.getConfig().getBoolean("styleEnable",true);
        config.styleMaxChars = plugin.getConfig().getInt("styleMaxChars",60);
        config.styleProfile = plugin.getConfig().getString("styleProfile","");
        config.styleMaxTokens = plugin.getConfig().getInt("styleMaxTokens",400);
        config.styleReasoningEffort = normalizeEffort(
                plugin.getConfig().getString("styleReasoningEffort","low"));
        config.styleProactiveEnable = plugin.getConfig().getBoolean("styleProactiveEnable",false);
        config.maxLongMemories = plugin.getConfig().getInt("maxLongMemories",150);
        config.replyMaxTokens = plugin.getConfig().getInt("replyMaxTokens",1200);
        config.replyImageMaxTokens = plugin.getConfig().getInt("replyImageMaxTokens",4000);
        config.replySegmentMaxChars = plugin.getConfig().getInt("replySegmentMaxChars",20);
        config.replySplitPunctuation = plugin.getConfig().getString(
                "replySplitPunctuation","。！？!?；;，、：,:～~");
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
        if (config.initialTrust < 0) config.initialTrust = 0;
        if (config.initialTrust > 100) config.initialTrust = 100;
        if (config.initialGroupAdminMultiplier < 1) config.initialGroupAdminMultiplier = 1;
        if (config.initialGroupAdminMultiplier > 2) config.initialGroupAdminMultiplier = 2;
        if (config.initialBotAdminMultiplier < 1) config.initialBotAdminMultiplier = 1;
        if (config.initialBotAdminMultiplier > 2) config.initialBotAdminMultiplier = 2;
        if (config.initialBotOwnerMultiplier < 1) config.initialBotOwnerMultiplier = 1;
        if (config.initialBotOwnerMultiplier > 2) config.initialBotOwnerMultiplier = 2;
        if (config.relationDailyMaxDelta < 0.1) config.relationDailyMaxDelta = 0.1;
        if (config.relationDailyMaxDelta > 30) config.relationDailyMaxDelta = 30;
        if (config.intimacyCloseAffinity < 0) config.intimacyCloseAffinity = 0;
        if (config.intimacyCloseAffinity > 100) config.intimacyCloseAffinity = 100;
        if (config.intimacyVeryCloseAffinity < config.intimacyCloseAffinity) {
            config.intimacyVeryCloseAffinity = config.intimacyCloseAffinity;
        }
        if (config.intimacyVeryCloseAffinity > 100) config.intimacyVeryCloseAffinity = 100;
        if (config.emotionDecayMinute < 5) config.emotionDecayMinute = 5;
        if (config.emotionDecayMinute > 1440) config.emotionDecayMinute = 1440;
        if (config.emotionEventCooldownSecond < 0) config.emotionEventCooldownSecond = 0;
        if (config.emotionEventCooldownSecond > 3600) config.emotionEventCooldownSecond = 3600;
        if (config.emotionAnalyzeMode == null || config.emotionAnalyzeMode.trim().isEmpty()) {
            config.emotionAnalyzeMode = "significant";
        }
        config.emotionAnalyzeMode = config.emotionAnalyzeMode.trim().toLowerCase(Locale.ROOT);
        if (!"off".equals(config.emotionAnalyzeMode)
                && !"direct".equals(config.emotionAnalyzeMode)
                && !"significant".equals(config.emotionAnalyzeMode)) {
            config.emotionAnalyzeMode = "significant";
        }
        if (config.emotionAnalyzeProfile == null) config.emotionAnalyzeProfile = "";
        if (config.emotionAnalyzeCooldownSecond < 10) config.emotionAnalyzeCooldownSecond = 10;
        if (config.emotionAnalyzeCooldownSecond > 3600) config.emotionAnalyzeCooldownSecond = 3600;
        if (config.emotionAnalyzeMaxTokens < 400) config.emotionAnalyzeMaxTokens = 400;
        if (config.emotionAnalyzeMaxTokens > 4000) config.emotionAnalyzeMaxTokens = 4000;
        if (config.emotionAnalyzeMaxDelta < 0.1) config.emotionAnalyzeMaxDelta = 0.1;
        if (config.emotionAnalyzeMaxDelta > 3) config.emotionAnalyzeMaxDelta = 3;
        //关系数值降低的幅度不允许超过提升的幅度
        if (config.emotionAnalyzeMaxDecreaseDelta < 0.05) {
            config.emotionAnalyzeMaxDecreaseDelta = 0.05;
        }
        if (config.emotionAnalyzeMaxDecreaseDelta > config.emotionAnalyzeMaxDelta) {
            config.emotionAnalyzeMaxDecreaseDelta = config.emotionAnalyzeMaxDelta;
        }
        if (config.emotionReasonMaxChars < 20) config.emotionReasonMaxChars = 20;
        if (config.emotionReasonMaxChars > 500) config.emotionReasonMaxChars = 500;
        if (config.emotionReasonMinStrength < 1) config.emotionReasonMinStrength = 1;
        if (config.emotionReasonMinStrength > 100) config.emotionReasonMinStrength = 100;
        if (config.emotionReasonDecayDays < 1) config.emotionReasonDecayDays = 1;
        if (config.emotionReasonDecayDays > 3650) config.emotionReasonDecayDays = 3650;
        if (config.emotionEventRetentionDays < 1) config.emotionEventRetentionDays = 1;
        if (config.emotionEventRetentionDays > 3650) config.emotionEventRetentionDays = 3650;
        if (config.emotionPositiveReplyBonus < 0) config.emotionPositiveReplyBonus = 0;
        if (config.emotionPositiveReplyBonus > 0.5) config.emotionPositiveReplyBonus = 0.5;
        if (config.emotionNegativeReplyPenalty < 0) config.emotionNegativeReplyPenalty = 0;
        if (config.emotionNegativeReplyPenalty > 1) config.emotionNegativeReplyPenalty = 1;
        if (config.emotionAffinityReplyBonus < 0) config.emotionAffinityReplyBonus = 0;
        if (config.emotionAffinityReplyBonus > 0.5) config.emotionAffinityReplyBonus = 0.5;
        if (config.emotionAffinityReplyPenalty < 0) config.emotionAffinityReplyPenalty = 0;
        if (config.emotionAffinityReplyPenalty > 1) config.emotionAffinityReplyPenalty = 1;
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
        if (config.splitMessageSuppressSecond < 0) config.splitMessageSuppressSecond = 0;
        if (config.splitMessageSuppressSecond > 300) config.splitMessageSuppressSecond = 300;
        if (config.splitMessageSuppressMaxChars < 4) config.splitMessageSuppressMaxChars = 4;
        if (config.splitMessageSuppressMaxChars > 60) config.splitMessageSuppressMaxChars = 60;
        if (config.memoryRelevanceMaxChars < 200) config.memoryRelevanceMaxChars = 200;
        if (config.memoryRelevanceMaxChars > 8000) config.memoryRelevanceMaxChars = 8000;
        if (config.memoryRelevancePoolSize < 50) config.memoryRelevancePoolSize = 50;
        if (config.memoryRelevancePoolSize > 3000) config.memoryRelevancePoolSize = 3000;
        if (config.globalMemoryRelevanceMaxChars < 200) {
            config.globalMemoryRelevanceMaxChars = 200;
        }
        if (config.globalMemoryRelevanceMaxChars > 8000) {
            config.globalMemoryRelevanceMaxChars = 8000;
        }
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
        if (config.memoryMergeMaxTokens < 4000) config.memoryMergeMaxTokens = 4000;
        if (config.memoryMergeMaxTokens > 64000) config.memoryMergeMaxTokens = 64000;
        if (config.memoryMergeBatchSize < 10) config.memoryMergeBatchSize = 10;
        if (config.memoryMergeBatchSize > 200) config.memoryMergeBatchSize = 200;
        if (config.memoryMergeMaxRounds < 1) config.memoryMergeMaxRounds = 1;
        if (config.memoryMergeMaxRounds > 10) config.memoryMergeMaxRounds = 10;
        if (config.memoryTimeoutSecond < 30) config.memoryTimeoutSecond = 30;
        if (config.memoryTimeoutSecond > 600) config.memoryTimeoutSecond = 600;
        if (config.globalMemoryLearnGroups == null) config.globalMemoryLearnGroups = "";
        if (config.globalMemoryMaxItems < 20) config.globalMemoryMaxItems = 20;
        if (config.globalMemoryMaxItems > 1000) config.globalMemoryMaxItems = 1000;
        if (config.globalMemoryInjectItems < 10) config.globalMemoryInjectItems = 10;
        if (config.globalMemoryInjectItems > 300) config.globalMemoryInjectItems = 300;
        if (config.speechCorpusDirectory == null || config.speechCorpusDirectory.trim().isEmpty()) {
            config.speechCorpusDirectory = "speech-corpus";
        }
        if (config.knowledgeDirectory == null || config.knowledgeDirectory.trim().isEmpty()) {
            config.knowledgeDirectory = "knowledge";
        }
        if (config.knowledgeStudentsLibrary == null) config.knowledgeStudentsLibrary = "";
        if (config.knowledgeMaxEntries < 1) config.knowledgeMaxEntries = 1;
        if (config.knowledgeMaxEntries > 20) config.knowledgeMaxEntries = 20;
        if (config.knowledgeMaxSectionsPerEntry < 1) config.knowledgeMaxSectionsPerEntry = 1;
        if (config.knowledgeMaxSectionsPerEntry > 6) config.knowledgeMaxSectionsPerEntry = 6;
        if (config.knowledgeMinScore < 0) config.knowledgeMinScore = 0;
        if (config.knowledgeMinScore > 20) config.knowledgeMinScore = 20;
        if (config.knowledgeInjectMaxChars < 200) config.knowledgeInjectMaxChars = 200;
        if (config.knowledgeInjectMaxChars > 8000) config.knowledgeInjectMaxChars = 8000;
        if (config.knowledgeSpoilerLevel < 0) config.knowledgeSpoilerLevel = 0;
        if (config.knowledgeSpoilerLevel > 2) config.knowledgeSpoilerLevel = 2;
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
        if (config.stickerAttachWindowSecond < 3) config.stickerAttachWindowSecond = 3;
        if (config.stickerAttachWindowSecond > 15) config.stickerAttachWindowSecond = 15;
        if (config.stickerAttachMaxWaitSecond < config.stickerAttachWindowSecond) {
            config.stickerAttachMaxWaitSecond = config.stickerAttachWindowSecond;
        }
        if (config.stickerAttachMaxWaitSecond > 60) config.stickerAttachMaxWaitSecond = 60;
        if (config.messageBatchWindowSecond < 1) config.messageBatchWindowSecond = 1;
        if (config.messageBatchWindowSecond > 10) config.messageBatchWindowSecond = 10;
        if (config.messageBatchMaxMessages < 2) config.messageBatchMaxMessages = 2;
        if (config.messageBatchMaxMessages > 50) config.messageBatchMaxMessages = 50;
        if (config.messageBatchMaxAgeSecond < 0) config.messageBatchMaxAgeSecond = 0;
        if (config.messageBatchMaxAgeSecond > 120) config.messageBatchMaxAgeSecond = 120;
        if (config.batchMaxSegments < 1) config.batchMaxSegments = 1;
        if (config.batchMaxSegments > 8) config.batchMaxSegments = 8;
        if (config.pokeBackCooldownSecond < 0) config.pokeBackCooldownSecond = 0;
        if (config.pokeBackCooldownSecond > 3600) config.pokeBackCooldownSecond = 3600;
        if (config.promptTotalChars < 4000) config.promptTotalChars = 4000;
        if (config.promptTotalChars > 64000) config.promptTotalChars = 64000;
        if (config.routerProfile == null) config.routerProfile = "";
        if (config.routerMaxTokens < 200) config.routerMaxTokens = 200;
        if (config.routerMaxTokens > 4000) config.routerMaxTokens = 4000;
        if (config.styleProfile == null) config.styleProfile = "";
        if (config.styleMaxChars < 20) config.styleMaxChars = 20;
        if (config.styleMaxChars > 300) config.styleMaxChars = 300;
        if (config.styleMaxTokens < 100) config.styleMaxTokens = 100;
        if (config.styleMaxTokens > 2000) config.styleMaxTokens = 2000;
        if (config.maxLongMemories < 5) config.maxLongMemories = 5;
        if (config.maxLongMemories > 500) config.maxLongMemories = 500;
        if (config.replyMaxTokens < 200) config.replyMaxTokens = 200;
        if (config.replyMaxTokens > 8000) config.replyMaxTokens = 8000;
        if (config.replyImageMaxTokens < 400) config.replyImageMaxTokens = 400;
        if (config.replyImageMaxTokens > 16000) config.replyImageMaxTokens = 16000;
        if (config.replySegmentMaxChars < 8) config.replySegmentMaxChars = 8;
        if (config.replySegmentMaxChars > 30) config.replySegmentMaxChars = 30;
        if (config.replySplitPunctuation == null || config.replySplitPunctuation.isEmpty()) {
            config.replySplitPunctuation = "。！？!?；;，、：,:～~";
        }
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

    // 思考强度只允许 none/low/medium/high，留空表示不向接口发送该字段
    private static String normalizeEffort(String value) {
        if (value == null) return "";
        String text = value.trim().toLowerCase(Locale.ROOT);
        if ("none".equals(text) || "low".equals(text)
                || "medium".equals(text) || "high".equals(text)) return text;
        return "";
    }
}
