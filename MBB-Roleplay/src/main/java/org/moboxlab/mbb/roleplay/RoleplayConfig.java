package org.moboxlab.mbb.roleplay;

import org.moboxlab.moboxbot.API.Plugin;

import java.util.Locale;

/**
 * MBB-Roleplay 配置
 */
public class RoleplayConfig {
    public static final double DEFAULT_OTHER_ROLE_BOT_REPLY_CHANCE = 0.50;
    public static final String DEFAULT_WEB_WHITELIST =
            "wikipedia.org,baike.baidu.com,bilibili.com,biligame.com,wiki.biligame.com,"
                    +"gamekee.com,bluearchive.wiki,kivo.wiki,moegirl.org.cn,bangumi.tv,bgm.tv,"
                    +"huijiwiki.com,prts.wiki,wiki.gg,game8.jp,appmedia.jp,kamigame.jp,"
                    +"ani.gamer.com.tw,fandom.com,namu.wiki,arca.live,github.com,"
                    +"raw.githubusercontent.com,deepwiki.com,duckduckgo.com,bing.com";
    public static final int DEFAULT_MAX_CONSECUTIVE_OTHER_ROLE_MESSAGES = 2;
    public static final String DEFAULT_OTHER_ROLE_BOT_NAMES = "";

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
    public boolean memberMemoryEnable = true;
    public int memberFieldMaxChars = 120;
    public boolean friendMemoryEnable = true;
    public String friendMemoryProfile = "";
    public int friendMemoryMinNewMessages = 6;
    public int friendMemoryIntervalHours = 12;
    public int friendMemoryContextMessages = 120;
    public int friendMemoryMaxTokens = 1200;
    public int friendMemoryTimeoutSecond = 120;
    public int friendMemoryInjectMaxChars = 1800;
    public boolean randomActivityEnable = true;
    public String randomActivityServices = "";
    public int randomActivityMemoryImportance = 3;
    public boolean randomActivityShareEnable = true;
    public double randomActivityShareMinScore = 0.75;
    public int randomActivityTimeoutSecond = 300;
    public int emotionDecayMinute = 30;
    public int emotionEventCooldownSecond = 300;
    public boolean emotionAnalyzeEnable = true;
    public String emotionAnalyzeMode = "significant";
    public String emotionAnalyzeProfile = "";
    public int emotionAnalyzeCooldownSecond = 300;
    public int emotionAnalyzeMaxTokens = 2400;
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
    public String replyWillingnessMode = "shadow";
    public double replyWillingnessThreshold = 0.35;
    public int replyWillingnessWindowSecond = 300;
    public double otherRoleBotReplyChance = DEFAULT_OTHER_ROLE_BOT_REPLY_CHANCE;
    public int maxConsecutiveOtherRoleMessages = DEFAULT_MAX_CONSECUTIVE_OTHER_ROLE_MESSAGES;
    public String otherRoleBotNames = DEFAULT_OTHER_ROLE_BOT_NAMES;
    public String otherRoleBotQQs = "";
    public boolean addressedOtherMemberSkip = true;
    public int splitMessageSuppressSecond = 20;
    public int splitMessageSuppressMaxChars = 20;
    public boolean directReplyBypassCooldown = true;
    public int shortContextMessages = 200;
    public int shortContextMaxChars = 12000;
    public boolean structuredContextEnable = true;
    public int structuredContextMaxChars = 1800;
    public int structuredContextItemMaxChars = 160;
    public int shortTermDays = 3;
    public int memoryUpdateMessages = 50;
    public int memoryExtractMessages = 300;
    public int memoryExtractMaxChars = 16000;
    public int memoryExtractBatches = 3;
    public int memoryMaxTokens = 12000;
    public int memoryMergeMaxTokens = 32000;
    public int memoryMergeBatchSize = 100;
    public int memoryMergeMaxRounds = 3;
    public int memoryMergeRetryCount = 3;
    public int memoryMergeConcurrency = 4;
    public int memoryMergeCooldownMinute = 60;
    public int globalMemoryMergeCooldownMinute = 60;
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
    public int speechRetrievalCount = 2;
    public int speechRetrievalMaxChars = 1200;
    public double speechRetrievalMinScore = 0.35;
    public int speechSpoilerLevel = 1;
    public double speechSimilarityThreshold = 0.78;
    public int speechSimilarityMinChars = 6;
    public boolean activeMemory = true;
    public boolean imageUnderstandingEnable = true;
    public String imageUnderstandingMode = "addressed";
    public int imageUnderstandingMaxPerHour = 30;
    public boolean imageUnderstandingInjectOcr = true;
    public String imageUnderstandingProfile = "";
    public int imageUnderstandingMaxChars = 600;
    public int visionReferenceMaxChars = 32000;
    public int imageContextTimeoutSecond = 300;
    public boolean imageAsyncEnable = true;
    public boolean hubEnable = true;
    public boolean hubVisionShareEnable = true;
    public int hubVisionWaitMs = 15000;
    public boolean hubTurnShareEnable = true;
    public boolean hubTaskDedupEnable = true;
    public boolean hubChatGuardEnable = true;
    public boolean hubTypingBroadcastEnable = true;
    public boolean memoryRelevanceSort = true;
    public int memoryRelevancePoolSize = 600;
    public int memoryRelevanceMaxChars = 12000;
    public boolean memoryConflictResolveEnable = true;
    public int memoryConflictMinOverlap = 2;
    public int globalMemoryRelevanceMaxChars = 10000;
    public boolean stickerAttachEnable = true;
    public int stickerAttachWindowSecond = 5;
    public int stickerAttachMaxWaitSecond = 15;
    public boolean stickerAttachWaitUntilNextMessage = true;
    public double stickerSendChance = 0.35;
    public boolean stickerVisionEnable = true;
    public int stickerVisionCandidates = 6;
    public String stickerVisionProfile = "";
    public int stickerVisionMaxTokens = 600;
    public int stickerVisionTimeoutSecond = 45;
    public boolean messageBatchEnable = true;
    public boolean learnEnable = true;
    public String learnDirectory = "train-corpus";
    public int learnContextBefore = 6;
    public int learnContextAfter = 3;
    public String modelServiceUrl = "http://127.0.0.1:8770";
    public String modelServiceToken = "";
    public int modelServiceTimeoutSecond = 120;
    public boolean trainEnable = true;
    public int trainContextBefore = 4;
    public int trainMaxSamplesPerRun = 2000;
    public int trainMaxChars = 200;
    public boolean trainIncludeBotMessages = false;
    public boolean trainAutoStart = true;
    public int messageBatchWindowSecond = 2;
    public String messageBatchDebounceMode = "natural";
    public int messageBatchMaxWaitSecond = 8;
    public int messageBatchMaxMessages = 10;
    public int messageBatchMaxAgeSecond = 20;
    public int batchMaxSegments = 4;
    public int turnThreads = 4;
    public boolean pokeReplyEnable = true;
    public boolean pokeBackEnable = true;
    public int pokeReplyMaxChars = 20;
    public int pokeBackCooldownSecond = 60;
    public int pokeStreakWindowSecond = 60;
    public int pokeStreakThreshold = 3;
    public boolean quoteReplyEnable = true;
    public int promptTotalChars = 120000;
    public boolean conversationAppendEnable = true;
    public int conversationContextTokenLimit = 1000000;
    public double conversationContextCompactPercent = 0.75;
    public double conversationContextKeepPercent = 0.20;
    public int conversationAppendRecentMessages = 20;
    public String conversationCompactionProfile = "";
    public int conversationCompactionMaxTokens = 6000;
    public int conversationCompactionTimeoutSecond = 300;
    public boolean routerEnable = true;
    public String routerProfile = "";
    public int routerMaxTokens = 2400;
    public String routerReasoningEffort = "none";
    public boolean styleEnable = false;
    public int styleMaxChars = 60;
    public boolean styleLongReplyEnable = false;
    public String styleProfile = "";
    public int styleMaxTokens = 1200;
    public String styleReasoningEffort = "low";
    public boolean styleProactiveEnable = false;
    public int styleFailureThreshold = 2;
    public int styleFailureCooldownSecond = 600;
    public int maxLongMemories = 150;
    public boolean topicFatigueEnable = true;
    public int topicFatigueWindowMinute = 30;
    public int topicFatigueMinCount = 6;
    public int topicFatigueSelfCount = 3;
    public int topicFatigueKeepHours = 168;
    public boolean threadEnable = true;
    public int threadWindowSecond = 180;
    public boolean replyFeedbackEnable = true;
    public int replyFeedbackWindowSecond = 180;
    public int replyFeedbackMinSamples = 6;
    public double replyFeedbackMaxPenalty = 0.25;
    public double replyFeedbackBonus = 0.10;
    public int replyFeedbackHistory = 30;
    public boolean replyReviewEnable = true;
    public String replyReviewProfile = "";
    public int replyReviewMaxTokens = 1200;
    public String replyReviewReasoningEffort = "none";
    public int replyReviewTimeoutSecond = 60;
    public int replyReviewMinScore = 70;
    public boolean toolEnable = true;
    public String toolCoreTools = "tool_search,end_turn,message_send,sticker";
    public int toolLoadedMaxTools = 8;
    public int toolSearchMaxResults = 4;
    public int toolMaxSteps = 16;
    public int toolMaxCalls = 12;
    public int toolResultMaxChars = 16000;
    public int toolResultTotalMaxChars = 48000;
    public int toolLiveMessageMax = 10;
    public int toolLoopMaxMillis = 75000;
    public int toolCallMaxMillis = 20000;
    public boolean toolParallelReadOnlyEnable = true;
    public int toolParallelMax = 4;
    public int toolAiRetryCount = 0;
    public boolean toolTurnPersistEnable = true;
    public int toolTurnKeepHours = 24;
    public int toolRecoverMaxMinutes = 60;
    public int toolMemoryInjectItems = 8;
    public int toolMemoryInjectMaxChars = 6000;
    public int toolMemoryKeepHours = 72;
    public int toolMemoryMaxRows = 500;
    public boolean workspaceEnable = true;
    public int workspaceMaxReadChars = 32000;
    public int workspaceMaxWriteChars = 64000;
    public int workspaceMaxFilesPerList = 200;
    public long workspaceMaxTotalBytes = 64L * 1024L * 1024L;
    public int workspaceMaxMediaBytes = 8 * 1024 * 1024;
    public boolean workspaceHistoryEnable = true;
    public int workspaceHistoryMaxVersions = 10;
    public int workspaceTrashKeepDays = 30;
    public boolean webEnable = true;
    public String webWhitelist = DEFAULT_WEB_WHITELIST;
    public String webSearchUrl = "https://www.bing.com/search?q={query}";
    public String webSearchFallbackUrl = "";
    public int webSearchMaxResults = 10;
    public int webOwnerAuthWindowSecond = 300;
    public int webTimeoutSecond = 15;
    public int webMaxBytes = 262144;
    public int webMaxChars = 12000;
    public int webImageMaxBytes = 8 * 1024 * 1024;
    public int webCacheSecond = 300;
    public boolean contextHardLimitEnable = false;
    public boolean pacingEnable = true;
    public int pacingBaseMs = 350;
    public int pacingPerCharMs = 25;
    public int pacingMaxMs = 2500;
    public double pacingNightMultiplier = 1.40;
    public double pacingActiveMultiplier = 0.80;
    public double pacingActiveThreshold = 0.70;
    public int pacingNightStartHour = 0;
    public int pacingNightEndHour = 6;
    public boolean safetyGuardEnable = true;
    public boolean promptInjectionGuardEnable = true;
    public boolean privacyGuardEnable = true;
    public boolean expressionLearnEnable = true;
    public int expressionMinCount = 3;
    public int expressionMinUsers = 2;
    public int expressionMaxCandidates = 200;
    public int expressionInjectMaxChars = 800;
    public boolean expressionVectorEnable = true;
    public String expressionEmbeddingProfile = "";
    public int expressionEmbeddingTimeoutSecond = 60;
    public int expressionVectorTopK = 6;
    public double expressionVectorMinScore = 0.25;
    public int expressionVectorQueryCacheMinute = 10;
    public boolean expressionReviewEnable = true;
    public String expressionReviewMode = "balanced";
    public String expressionReviewProfile = "";
    public int expressionReviewIntervalHours = 24;
    public int expressionReviewMaxCandidates = 50;
    public int expressionReviewMaxTokens = 3000;
    public int expressionReviewTimeoutSecond = 120;
    public double expressionAutoApproveMinQuality = 0.80;
    public double expressionBalancedRejectMaxQuality = 0.25;
    public boolean attentionDriftEnable = true;
    public int attentionDecayMinute = 30;
    public double attentionLowThreshold = 0.25;
    public double attentionHighThreshold = 0.70;
    public double attentionMaxPenalty = 0.20;
    public double attentionBonus = 0.05;
    public int attentionTopicLimit = 200;
    public boolean dailyReviewEnable = true;
    public int dailyReviewHour = 4;
    public int dailyReviewMaxMessages = 300;
    public int dailyReviewMaxTokens = 4000;
    public int dailyReviewTimeoutSecond = 180;
    public boolean traceEnable = true;
    public int traceKeepHours = 72;
    public int traceMaxRows = 2000;
    public boolean evalEnable = true;
    public String evalProfile = "";
    public int evalMaxTokens = 1200;
    public int evalTimeoutSecond = 60;
    public int replyMaxTokens = 4000;
    public int replyImageMaxTokens = 4000;
    public int replySegmentMaxChars = 30;
    public String replySplitPunctuation = "。！？!?；;，、：,:～~…";
    public int replyMaxSegments = 5;
    public int replyBeatMaxSegments = 5;
    public int replyBeatBaseDelayMs = 450;
    public int replyBeatPerCharMs = 35;
    public int replyBeatMaxDelayMs = 2200;
    public boolean replyLinkFilterEnable = true;
    public boolean cameraEnable = true;
    /** 生图是否启用独立 prompt 编译层 */
    public boolean drawPromptCompilerEnable = true;
    /** 生图 prompt 编译使用的模型配置，留空使用 aiProfile */
    public String drawPromptProfile = "";
    public int drawPromptMaxTokens = 1800;
    public String drawPromptReasoningEffort = "none";
    public int drawPromptTimeoutSecond = 120;
    public int drawPromptMaxChars = 1600;
    public int drawPromptNegativeMaxChars = 800;
    public int drawPromptKnowledgeMaxChars = 1200;
    public int recentReplyCheckCount = 8;
    public boolean repeatSuppressEnable = false;
    public double repeatSimilarityThreshold = 0.72;
    public int repeatCommonRunMinChars = 8;
    public int repeatCheckMinChars = 6;
    public int repeatOpeningLimit = 2;
    public String personaFile = "persona-aris.json";
    public int minMessageLength = 2;
    public boolean quietReplyEnable = true;
    public boolean privateChatEnable = true;
    public String privateChatWhitelist = "";
    public int privateChatFriendCacheSecond = 300;
    public int privateChatContextMessages = 40;
    public int privateChatMaxReplyChars = 160;
    public int privateChatImageDebounceSecond = 4;
    public int privateChatLiveMergeMaxRounds = 2;
    public int privateChatPendingMaxMessages = 10;
    public boolean privateChatInputStatusEnable = true;
    public int privateChatInputStatusGraceSecond = 3;
    public int privateChatInputStatusMaxWaitSecond = 10;
    public boolean friendRequestRoleDecisionEnable = true;
    public String friendRequestRoleDecisionProfile = "";
    public double friendRequestRoleDecisionMinConfidence = 0.55;
    public int friendRequestRoleDecisionMaxTokens = 1600;
    public int friendRequestRoleDecisionTimeoutSecond = 60;
    public boolean randomTickEnable = true;
    public int randomTickMinMinute = 10;
    public int randomTickMaxMinute = 30;
    public int randomTickQuietStartHour = 0;
    public int randomTickQuietEndHour = 8;
    public boolean randomTickMorningGreetingEnable = true;
    public int randomTickMorningStartHour = 8;
    public int randomTickMorningEndHour = 10;
    public double randomTickMorningChance = 0.80;
    public int randomTickMorningMaxGroups = 3;
    public int randomTickMorningMinMessages = 3;
    public boolean randomTickPrivateFollowUpEnable = true;
    public int randomTickPrivateFollowUpInitialMinute = 2;
    public int randomTickPrivateFollowUpMaxMinute = 120;
    public int randomTickPrivateFollowUpFreshMinute = 720;
    public int randomTickGroupWindowMinute = 360;
    public int randomTickGroupMaxActions = 2;
    public int randomTickQzoneDailyMax = 2;
    public double randomTickQzoneDayChance = 0.20;
    public double randomTickQzoneEveningChance = 0.80;
    public double randomTickQzoneNightChance = 0.03;
    public int randomTickQzoneEveningStartHour = 18;
    public int randomTickQzoneEveningEndHour = 23;
    public int randomTickQzoneNightEndHour = 7;
    public int randomTickOwnerRecentWindowMinute = 180;
    public double randomTickOwnerRecentBoostChance = 0.30;
    public int randomTickDedupeHours = 24;
    public int randomTickMaxTokens = 1200;
    public int randomTickTimeoutSecond = 90;
    public String randomTickReasoningEffort = "low";
    public String commandPrefixes = "/,!,＃,#";

    private static double parseDouble(String value,double defaultValue) {
        try {
            return Double.parseDouble(value == null ? "" : value.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

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
        config.memberMemoryEnable = plugin.getConfig().getBoolean("memberMemoryEnable",true);
        config.memberFieldMaxChars = plugin.getConfig().getInt("memberFieldMaxChars",120);
        config.friendMemoryEnable = plugin.getConfig().getBoolean("friendMemoryEnable",true);
        config.friendMemoryProfile = plugin.getConfig().getString("friendMemoryProfile","");
        config.friendMemoryMinNewMessages = plugin.getConfig().getInt("friendMemoryMinNewMessages",6);
        config.friendMemoryIntervalHours = plugin.getConfig().getInt("friendMemoryIntervalHours",12);
        config.friendMemoryContextMessages = plugin.getConfig().getInt("friendMemoryContextMessages",120);
        config.friendMemoryMaxTokens = plugin.getConfig().getInt("friendMemoryMaxTokens",1200);
        config.friendMemoryTimeoutSecond = plugin.getConfig().getInt("friendMemoryTimeoutSecond",120);
        config.friendMemoryInjectMaxChars = plugin.getConfig().getInt("friendMemoryInjectMaxChars",1800);
        config.randomActivityEnable = plugin.getConfig().getBoolean("randomActivityEnable",true);
        config.randomActivityServices = plugin.getConfig().getString("randomActivityServices","");
        config.randomActivityMemoryImportance = plugin.getConfig().getInt("randomActivityMemoryImportance",3);
        config.randomActivityShareEnable = plugin.getConfig().getBoolean("randomActivityShareEnable",true);
        try {
            config.randomActivityShareMinScore = Double.parseDouble(
                    plugin.getConfig().getString("randomActivityShareMinScore","0.75"));
        } catch (Exception e) {
            config.randomActivityShareMinScore = 0.75;
        }
        config.randomActivityTimeoutSecond = plugin.getConfig().getInt("randomActivityTimeoutSecond",300);
        config.emotionDecayMinute = plugin.getConfig().getInt("emotionDecayMinute",30);
        config.emotionEventCooldownSecond = plugin.getConfig().getInt("emotionEventCooldownSecond",300);
        config.emotionAnalyzeEnable = plugin.getConfig().getBoolean("emotionAnalyzeEnable",true);
        config.emotionAnalyzeMode = plugin.getConfig().getString("emotionAnalyzeMode","significant");
        config.emotionAnalyzeProfile = plugin.getConfig().getString("emotionAnalyzeProfile","");
        config.emotionAnalyzeCooldownSecond = plugin.getConfig().getInt("emotionAnalyzeCooldownSecond",300);
        config.emotionAnalyzeMaxTokens = plugin.getConfig().getInt("emotionAnalyzeMaxTokens",2400);
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
        config.replyWillingnessMode =
                plugin.getConfig().getString("replyWillingnessMode","shadow");
        try {
            config.replyWillingnessThreshold = Double.parseDouble(
                    plugin.getConfig().getString("replyWillingnessThreshold","0.35"));
        } catch (Exception e) {
            config.replyWillingnessThreshold = 0.35;
        }
        config.replyWillingnessWindowSecond =
                plugin.getConfig().getInt("replyWillingnessWindowSecond",300);
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
        config.directReplyBypassCooldown =
                plugin.getConfig().getBoolean("directReplyBypassCooldown",true);
        config.shortContextMessages = plugin.getConfig().getInt("shortContextMessages",200);
        config.shortContextMaxChars = plugin.getConfig().getInt("shortContextMaxChars",12000);
        config.structuredContextEnable =
                plugin.getConfig().getBoolean("structuredContextEnable",true);
        config.structuredContextMaxChars =
                plugin.getConfig().getInt("structuredContextMaxChars",1800);
        config.structuredContextItemMaxChars =
                plugin.getConfig().getInt("structuredContextItemMaxChars",160);
        config.shortTermDays = plugin.getConfig().getInt("shortTermDays",3);
        config.memoryUpdateMessages = plugin.getConfig().getInt("memoryUpdateMessages",50);
        config.memoryExtractMessages = plugin.getConfig().getInt("memoryExtractMessages",300);
        config.memoryExtractMaxChars = plugin.getConfig().getInt("memoryExtractMaxChars",16000);
        config.memoryExtractBatches = plugin.getConfig().getInt("memoryExtractBatches",3);
        config.memoryMaxTokens = plugin.getConfig().getInt("memoryMaxTokens",12000);
        config.memoryMergeMaxTokens = plugin.getConfig().getInt("memoryMergeMaxTokens",32000);
        config.memoryMergeBatchSize = plugin.getConfig().getInt("memoryMergeBatchSize",100);
        config.memoryMergeMaxRounds = plugin.getConfig().getInt("memoryMergeMaxRounds",3);
        config.memoryMergeRetryCount = plugin.getConfig().getInt("memoryMergeRetryCount",3);
        config.memoryMergeConcurrency = plugin.getConfig().getInt("memoryMergeConcurrency",4);
        config.memoryMergeCooldownMinute = plugin.getConfig().getInt("memoryMergeCooldownMinute",60);
        config.globalMemoryMergeCooldownMinute =
                plugin.getConfig().getInt("globalMemoryMergeCooldownMinute",60);
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
        config.speechRetrievalCount = plugin.getConfig().getInt("speechRetrievalCount",2);
        config.speechRetrievalMaxChars = plugin.getConfig().getInt("speechRetrievalMaxChars",1200);
        try {
            config.speechRetrievalMinScore = Double.parseDouble(
                    plugin.getConfig().getString("speechRetrievalMinScore","0.35"));
        } catch (Exception e) {
            config.speechRetrievalMinScore = 0.35;
        }
        config.speechSpoilerLevel = plugin.getConfig().getInt("speechSpoilerLevel",1);
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
        config.visionReferenceMaxChars =
                plugin.getConfig().getInt("visionReferenceMaxChars",32000);
        config.imageContextTimeoutSecond = plugin.getConfig().getInt("imageContextTimeoutSecond",300);
        config.imageAsyncEnable = plugin.getConfig().getBoolean("imageAsyncEnable",true);
        config.hubEnable = plugin.getConfig().getBoolean("hubEnable",true);
        config.hubVisionShareEnable = plugin.getConfig().getBoolean("hubVisionShareEnable",true);
        config.hubVisionWaitMs = plugin.getConfig().getInt("hubVisionWaitMs",15000);
        config.hubTurnShareEnable = plugin.getConfig().getBoolean("hubTurnShareEnable",true);
        config.hubTaskDedupEnable = plugin.getConfig().getBoolean("hubTaskDedupEnable",true);
        config.hubChatGuardEnable = plugin.getConfig().getBoolean("hubChatGuardEnable",true);
        config.hubTypingBroadcastEnable = plugin.getConfig().getBoolean("hubTypingBroadcastEnable",true);
        config.memoryRelevanceSort = plugin.getConfig().getBoolean("memoryRelevanceSort",true);
        config.memoryRelevancePoolSize =
                plugin.getConfig().getInt("memoryRelevancePoolSize",600);
        config.memoryRelevanceMaxChars =
                plugin.getConfig().getInt("memoryRelevanceMaxChars",12000);
        config.memoryConflictResolveEnable =
                plugin.getConfig().getBoolean("memoryConflictResolveEnable",true);
        config.memoryConflictMinOverlap =
                plugin.getConfig().getInt("memoryConflictMinOverlap",2);
        config.globalMemoryRelevanceMaxChars =
                plugin.getConfig().getInt("globalMemoryRelevanceMaxChars",10000);
        config.stickerAttachEnable = plugin.getConfig().getBoolean("stickerAttachEnable",true);
        config.stickerAttachWindowSecond = plugin.getConfig().getInt("stickerAttachWindowSecond",5);
        config.stickerAttachMaxWaitSecond = plugin.getConfig().getInt("stickerAttachMaxWaitSecond",15);
        config.stickerAttachWaitUntilNextMessage =
                plugin.getConfig().getBoolean("stickerAttachWaitUntilNextMessage",true);
        config.stickerSendChance = parseDouble(
                plugin.getConfig().getString("stickerSendChance","0.35"),0.35);
        config.stickerVisionEnable =
                plugin.getConfig().getBoolean("stickerVisionEnable",true);
        config.stickerVisionCandidates =
                plugin.getConfig().getInt("stickerVisionCandidates",6);
        config.stickerVisionProfile =
                plugin.getConfig().getString("stickerVisionProfile","");
        config.stickerVisionMaxTokens =
                plugin.getConfig().getInt("stickerVisionMaxTokens",600);
        config.stickerVisionTimeoutSecond =
                plugin.getConfig().getInt("stickerVisionTimeoutSecond",45);
        config.messageBatchEnable = plugin.getConfig().getBoolean("messageBatchEnable",true);
        config.learnEnable = plugin.getConfig().getBoolean("learnEnable",true);
        config.learnDirectory = plugin.getConfig().getString("learnDirectory","train-corpus");
        config.learnContextBefore = plugin.getConfig().getInt("learnContextBefore",6);
        config.learnContextAfter = plugin.getConfig().getInt("learnContextAfter",3);
        config.modelServiceUrl = plugin.getConfig().getString("modelServiceUrl","http://127.0.0.1:8770");
        config.modelServiceToken = plugin.getConfig().getString("modelServiceToken","");
        config.modelServiceTimeoutSecond = plugin.getConfig().getInt("modelServiceTimeoutSecond",120);
        config.trainEnable = plugin.getConfig().getBoolean("trainEnable",true);
        config.trainContextBefore = plugin.getConfig().getInt("trainContextBefore",4);
        config.trainMaxSamplesPerRun = plugin.getConfig().getInt("trainMaxSamplesPerRun",2000);
        config.trainMaxChars = plugin.getConfig().getInt("trainMaxChars",200);
        config.trainIncludeBotMessages = plugin.getConfig().getBoolean("trainIncludeBotMessages",false);
        config.trainAutoStart = plugin.getConfig().getBoolean("trainAutoStart",true);
        config.messageBatchWindowSecond = plugin.getConfig().getInt("messageBatchWindowSecond",2);
        config.messageBatchDebounceMode =
                plugin.getConfig().getString("messageBatchDebounceMode","natural");
        config.messageBatchMaxWaitSecond =
                plugin.getConfig().getInt("messageBatchMaxWaitSecond",8);
        config.messageBatchMaxMessages = plugin.getConfig().getInt("messageBatchMaxMessages",10);
        config.messageBatchMaxAgeSecond = plugin.getConfig().getInt("messageBatchMaxAgeSecond",20);
        config.batchMaxSegments = plugin.getConfig().getInt("batchMaxSegments",4);
        config.pokeReplyEnable = plugin.getConfig().getBoolean("pokeReplyEnable",true);
        config.pokeBackEnable = plugin.getConfig().getBoolean("pokeBackEnable",true);
        config.pokeReplyMaxChars = plugin.getConfig().getInt("pokeReplyMaxChars",20);
        config.pokeBackCooldownSecond = plugin.getConfig().getInt("pokeBackCooldownSecond",60);
        config.quoteReplyEnable = plugin.getConfig().getBoolean("quoteReplyEnable",true);
        config.promptTotalChars = plugin.getConfig().getInt("promptTotalChars",120000);
        config.conversationAppendEnable =
                plugin.getConfig().getBoolean("conversationAppendEnable",true);
        config.conversationContextTokenLimit =
                plugin.getConfig().getInt("conversationContextTokenLimit",1000000);
        try {
            config.conversationContextCompactPercent = Double.parseDouble(
                    plugin.getConfig().getString("conversationContextCompactPercent","0.75"));
        } catch (Exception e) {
            config.conversationContextCompactPercent = 0.75;
        }
        try {
            config.conversationContextKeepPercent = Double.parseDouble(
                    plugin.getConfig().getString("conversationContextKeepPercent","0.20"));
        } catch (Exception e) {
            config.conversationContextKeepPercent = 0.20;
        }
        config.conversationAppendRecentMessages =
                plugin.getConfig().getInt("conversationAppendRecentMessages",20);
        config.conversationCompactionProfile =
                plugin.getConfig().getString("conversationCompactionProfile","");
        config.conversationCompactionMaxTokens =
                plugin.getConfig().getInt("conversationCompactionMaxTokens",6000);
        config.conversationCompactionTimeoutSecond =
                plugin.getConfig().getInt("conversationCompactionTimeoutSecond",300);
        config.routerEnable = plugin.getConfig().getBoolean("routerEnable",true);
        config.routerProfile = plugin.getConfig().getString("routerProfile","");
        config.routerMaxTokens = plugin.getConfig().getInt("routerMaxTokens",2400);
        config.routerReasoningEffort = normalizeEffort(
                plugin.getConfig().getString("routerReasoningEffort","none"));
        config.styleEnable = plugin.getConfig().getBoolean("styleEnable",false);
        config.styleMaxChars = plugin.getConfig().getInt("styleMaxChars",60);
        config.styleLongReplyEnable = plugin.getConfig().getBoolean("styleLongReplyEnable",false);
        config.styleProfile = plugin.getConfig().getString("styleProfile","");
        config.styleMaxTokens = plugin.getConfig().getInt("styleMaxTokens",1200);
        config.styleReasoningEffort = normalizeEffort(
                plugin.getConfig().getString("styleReasoningEffort","low"));
        config.styleProactiveEnable = plugin.getConfig().getBoolean("styleProactiveEnable",false);
        config.styleFailureThreshold = plugin.getConfig().getInt("styleFailureThreshold",2);
        config.styleFailureCooldownSecond = plugin.getConfig().getInt("styleFailureCooldownSecond",600);
        config.maxLongMemories = plugin.getConfig().getInt("maxLongMemories",150);
        config.topicFatigueEnable = plugin.getConfig().getBoolean("topicFatigueEnable",true);
        config.topicFatigueWindowMinute = plugin.getConfig().getInt("topicFatigueWindowMinute",30);
        config.topicFatigueMinCount = plugin.getConfig().getInt("topicFatigueMinCount",6);
        config.topicFatigueSelfCount = plugin.getConfig().getInt("topicFatigueSelfCount",3);
        config.topicFatigueKeepHours = plugin.getConfig().getInt("topicFatigueKeepHours",168);
        config.threadEnable = plugin.getConfig().getBoolean("threadEnable",true);
        config.threadWindowSecond = plugin.getConfig().getInt("threadWindowSecond",180);
        config.replyFeedbackEnable = plugin.getConfig().getBoolean("replyFeedbackEnable",true);
        config.replyFeedbackWindowSecond = plugin.getConfig().getInt("replyFeedbackWindowSecond",180);
        config.replyFeedbackMinSamples = plugin.getConfig().getInt("replyFeedbackMinSamples",6);
        config.replyFeedbackMaxPenalty = parseDouble(plugin.getConfig().getString("replyFeedbackMaxPenalty","0.25"),0.25);
        config.replyFeedbackBonus = parseDouble(plugin.getConfig().getString("replyFeedbackBonus","0.10"),0.10);
        config.replyFeedbackHistory = plugin.getConfig().getInt("replyFeedbackHistory",30);
        config.replyReviewEnable = plugin.getConfig().getBoolean("replyReviewEnable",true);
        config.replyReviewProfile = plugin.getConfig().getString("replyReviewProfile","");
        config.replyReviewMaxTokens = plugin.getConfig().getInt("replyReviewMaxTokens",1200);
        config.replyReviewReasoningEffort = normalizeEffort(
                plugin.getConfig().getString("replyReviewReasoningEffort","none"));
        config.replyReviewTimeoutSecond = plugin.getConfig().getInt("replyReviewTimeoutSecond",60);
        config.replyReviewMinScore = plugin.getConfig().getInt("replyReviewMinScore",70);
        config.toolEnable = plugin.getConfig().getBoolean("toolEnable",true);
        config.toolCoreTools = plugin.getConfig().getString("toolCoreTools",
                "tool_search,end_turn,message_send,sticker");
        config.toolLoadedMaxTools = plugin.getConfig().getInt("toolLoadedMaxTools",8);
        config.toolSearchMaxResults = plugin.getConfig().getInt("toolSearchMaxResults",4);
        config.toolMaxSteps = plugin.getConfig().getInt("toolMaxSteps",16);
        config.toolMaxCalls = plugin.getConfig().getInt("toolMaxCalls",12);
        config.toolResultMaxChars = plugin.getConfig().getInt("toolResultMaxChars",16000);
        config.toolResultTotalMaxChars =
                plugin.getConfig().getInt("toolResultTotalMaxChars",48000);
        config.toolLiveMessageMax = plugin.getConfig().getInt("toolLiveMessageMax",10);
        config.toolLoopMaxMillis = plugin.getConfig().getInt("toolLoopMaxMillis",75000);
        config.toolCallMaxMillis = plugin.getConfig().getInt("toolCallMaxMillis",20000);
        config.toolParallelReadOnlyEnable =
                plugin.getConfig().getBoolean("toolParallelReadOnlyEnable",true);
        config.toolParallelMax = plugin.getConfig().getInt("toolParallelMax",4);
        config.toolAiRetryCount = plugin.getConfig().getInt("toolAiRetryCount",0);
        config.toolTurnPersistEnable =
                plugin.getConfig().getBoolean("toolTurnPersistEnable",true);
        config.toolTurnKeepHours = plugin.getConfig().getInt("toolTurnKeepHours",24);
        config.toolRecoverMaxMinutes =
                plugin.getConfig().getInt("toolRecoverMaxMinutes",60);
        config.toolMemoryInjectItems = plugin.getConfig().getInt("toolMemoryInjectItems",8);
        config.toolMemoryInjectMaxChars = plugin.getConfig().getInt("toolMemoryInjectMaxChars",6000);
        config.toolMemoryKeepHours = plugin.getConfig().getInt("toolMemoryKeepHours",72);
        config.toolMemoryMaxRows = plugin.getConfig().getInt("toolMemoryMaxRows",500);
        config.workspaceEnable = plugin.getConfig().getBoolean("workspaceEnable",true);
        config.workspaceMaxReadChars = plugin.getConfig().getInt("workspaceMaxReadChars",32000);
        config.workspaceMaxWriteChars = plugin.getConfig().getInt("workspaceMaxWriteChars",64000);
        config.workspaceMaxFilesPerList = plugin.getConfig().getInt("workspaceMaxFilesPerList",200);
        config.workspaceMaxTotalBytes = plugin.getConfig().getInt("workspaceMaxTotalBytes",
                64 * 1024 * 1024);
        config.workspaceMaxMediaBytes = plugin.getConfig().getInt(
                "workspaceMaxMediaBytes",8 * 1024 * 1024);
        config.workspaceHistoryEnable =
                plugin.getConfig().getBoolean("workspaceHistoryEnable",true);
        config.workspaceHistoryMaxVersions =
                plugin.getConfig().getInt("workspaceHistoryMaxVersions",10);
        config.workspaceTrashKeepDays =
                plugin.getConfig().getInt("workspaceTrashKeepDays",30);
        config.webEnable = plugin.getConfig().getBoolean("webEnable",true);
        config.webWhitelist = plugin.getConfig().getString("webWhitelist",
                DEFAULT_WEB_WHITELIST);
        config.webSearchUrl = plugin.getConfig().getString("webSearchUrl",
                "https://www.bing.com/search?q={query}");
        config.webSearchFallbackUrl = plugin.getConfig().getString("webSearchFallbackUrl",
                "");
        config.webSearchMaxResults = plugin.getConfig().getInt("webSearchMaxResults",10);
        config.webOwnerAuthWindowSecond =
                plugin.getConfig().getInt("webOwnerAuthWindowSecond",300);
        config.webTimeoutSecond = plugin.getConfig().getInt("webTimeoutSecond",15);
        config.webMaxBytes = plugin.getConfig().getInt("webMaxBytes",262144);
        config.webMaxChars = plugin.getConfig().getInt("webMaxChars",12000);
        config.webImageMaxBytes = plugin.getConfig().getInt("webImageMaxBytes",8 * 1024 * 1024);
        config.webCacheSecond = plugin.getConfig().getInt("webCacheSecond",300);
        config.contextHardLimitEnable = plugin.getConfig().getBoolean("contextHardLimitEnable",false);
        config.pacingEnable = plugin.getConfig().getBoolean("pacingEnable",true);
        config.pacingBaseMs = plugin.getConfig().getInt("pacingBaseMs",350);
        config.pacingPerCharMs = plugin.getConfig().getInt("pacingPerCharMs",25);
        config.pacingMaxMs = plugin.getConfig().getInt("pacingMaxMs",2500);
        config.pacingNightMultiplier = parseDouble(
                plugin.getConfig().getString("pacingNightMultiplier","1.40"),1.40);
        config.pacingActiveMultiplier = parseDouble(
                plugin.getConfig().getString("pacingActiveMultiplier","0.80"),0.80);
        config.pacingActiveThreshold = parseDouble(
                plugin.getConfig().getString("pacingActiveThreshold","0.70"),0.70);
        config.pacingNightStartHour = plugin.getConfig().getInt("pacingNightStartHour",0);
        config.pacingNightEndHour = plugin.getConfig().getInt("pacingNightEndHour",6);
        config.safetyGuardEnable = plugin.getConfig().getBoolean("safetyGuardEnable",true);
        config.promptInjectionGuardEnable =
                plugin.getConfig().getBoolean("promptInjectionGuardEnable",true);
        config.privacyGuardEnable = plugin.getConfig().getBoolean("privacyGuardEnable",true);
        config.expressionLearnEnable = plugin.getConfig().getBoolean("expressionLearnEnable",true);
        config.expressionMinCount = plugin.getConfig().getInt("expressionMinCount",3);
        config.expressionMinUsers = plugin.getConfig().getInt("expressionMinUsers",2);
        config.expressionMaxCandidates = plugin.getConfig().getInt("expressionMaxCandidates",200);
        config.expressionInjectMaxChars = plugin.getConfig().getInt("expressionInjectMaxChars",800);
        config.expressionVectorEnable =
                plugin.getConfig().getBoolean("expressionVectorEnable",true);
        config.expressionEmbeddingProfile =
                plugin.getConfig().getString("expressionEmbeddingProfile","");
        config.expressionEmbeddingTimeoutSecond =
                plugin.getConfig().getInt("expressionEmbeddingTimeoutSecond",60);
        config.expressionVectorTopK = plugin.getConfig().getInt("expressionVectorTopK",6);
        config.expressionVectorMinScore = parseDouble(
                plugin.getConfig().getString("expressionVectorMinScore","0.25"),0.25);
        config.expressionVectorQueryCacheMinute =
                plugin.getConfig().getInt("expressionVectorQueryCacheMinute",10);
        config.expressionReviewEnable =
                plugin.getConfig().getBoolean("expressionReviewEnable",true);
        config.expressionReviewMode =
                plugin.getConfig().getString("expressionReviewMode","balanced");
        config.expressionReviewProfile =
                plugin.getConfig().getString("expressionReviewProfile","");
        config.expressionReviewIntervalHours =
                plugin.getConfig().getInt("expressionReviewIntervalHours",24);
        config.expressionReviewMaxCandidates =
                plugin.getConfig().getInt("expressionReviewMaxCandidates",50);
        config.expressionReviewMaxTokens =
                plugin.getConfig().getInt("expressionReviewMaxTokens",3000);
        config.expressionReviewTimeoutSecond =
                plugin.getConfig().getInt("expressionReviewTimeoutSecond",120);
        config.expressionAutoApproveMinQuality = parseDouble(
                plugin.getConfig().getString("expressionAutoApproveMinQuality","0.80"),0.80);
        config.expressionBalancedRejectMaxQuality = parseDouble(
                plugin.getConfig().getString("expressionBalancedRejectMaxQuality","0.25"),0.25);
        config.attentionDriftEnable = plugin.getConfig().getBoolean("attentionDriftEnable",true);
        config.attentionDecayMinute = plugin.getConfig().getInt("attentionDecayMinute",30);
        config.attentionLowThreshold = parseDouble(plugin.getConfig().getString("attentionLowThreshold","0.25"),0.25);
        config.attentionHighThreshold = parseDouble(plugin.getConfig().getString("attentionHighThreshold","0.70"),0.70);
        config.attentionMaxPenalty = parseDouble(plugin.getConfig().getString("attentionMaxPenalty","0.20"),0.20);
        config.attentionBonus = parseDouble(plugin.getConfig().getString("attentionBonus","0.05"),0.05);
        config.attentionTopicLimit = plugin.getConfig().getInt("attentionTopicLimit",200);
        config.dailyReviewEnable = plugin.getConfig().getBoolean("dailyReviewEnable",true);
        config.dailyReviewHour = plugin.getConfig().getInt("dailyReviewHour",4);
        config.dailyReviewMaxMessages = plugin.getConfig().getInt("dailyReviewMaxMessages",300);
        config.dailyReviewMaxTokens = plugin.getConfig().getInt("dailyReviewMaxTokens",4000);
        config.dailyReviewTimeoutSecond = plugin.getConfig().getInt("dailyReviewTimeoutSecond",180);
        config.traceEnable = plugin.getConfig().getBoolean("traceEnable",true);
        config.traceKeepHours = plugin.getConfig().getInt("traceKeepHours",72);
        config.traceMaxRows = plugin.getConfig().getInt("traceMaxRows",2000);
        config.evalEnable = plugin.getConfig().getBoolean("evalEnable",true);
        config.evalProfile = plugin.getConfig().getString("evalProfile","");
        config.evalMaxTokens = plugin.getConfig().getInt("evalMaxTokens",1200);
        config.evalTimeoutSecond = plugin.getConfig().getInt("evalTimeoutSecond",60);
        config.replyMaxTokens = plugin.getConfig().getInt("replyMaxTokens",4000);
        config.replyImageMaxTokens = plugin.getConfig().getInt("replyImageMaxTokens",4000);
        config.replySegmentMaxChars = plugin.getConfig().getInt("replySegmentMaxChars",30);
        config.replySplitPunctuation = plugin.getConfig().getString(
                "replySplitPunctuation","。！？!?；;，、：,:～~…");
        config.replyMaxSegments = plugin.getConfig().getInt("replyMaxSegments",5);
        config.replyBeatMaxSegments = plugin.getConfig().getInt("replyBeatMaxSegments",5);
        config.replyBeatBaseDelayMs = plugin.getConfig().getInt("replyBeatBaseDelayMs",450);
        config.replyBeatPerCharMs = plugin.getConfig().getInt("replyBeatPerCharMs",35);
        config.replyBeatMaxDelayMs = plugin.getConfig().getInt("replyBeatMaxDelayMs",2200);
        config.replyLinkFilterEnable = plugin.getConfig().getBoolean("replyLinkFilterEnable",true);
        config.cameraEnable = plugin.getConfig().getBoolean("cameraEnable",true);
        config.drawPromptCompilerEnable =
                plugin.getConfig().getBoolean("drawPromptCompilerEnable",true);
        config.drawPromptProfile = plugin.getConfig().getString("drawPromptProfile","");
        config.drawPromptMaxTokens = plugin.getConfig().getInt("drawPromptMaxTokens",1800);
        config.drawPromptReasoningEffort = normalizeEffort(
                plugin.getConfig().getString("drawPromptReasoningEffort","none"));
        config.drawPromptTimeoutSecond = plugin.getConfig().getInt("drawPromptTimeoutSecond",120);
        config.drawPromptMaxChars = plugin.getConfig().getInt("drawPromptMaxChars",1600);
        config.drawPromptNegativeMaxChars =
                plugin.getConfig().getInt("drawPromptNegativeMaxChars",800);
        config.drawPromptKnowledgeMaxChars =
                plugin.getConfig().getInt("drawPromptKnowledgeMaxChars",1200);
        config.recentReplyCheckCount = plugin.getConfig().getInt("recentReplyCheckCount",8);
        config.repeatSuppressEnable = plugin.getConfig().getBoolean("repeatSuppressEnable",false);
        try {
            config.repeatSimilarityThreshold = Double.parseDouble(plugin.getConfig().getString("repeatSimilarityThreshold","0.72"));
        } catch (Exception e) {
            config.repeatSimilarityThreshold = 0.72;
        }
        config.repeatCheckMinChars = plugin.getConfig().getInt("repeatCheckMinChars",6);
        config.repeatOpeningLimit = plugin.getConfig().getInt("repeatOpeningLimit",2);
        config.repeatCommonRunMinChars =
                plugin.getConfig().getInt("repeatCommonRunMinChars",8);
        config.personaFile = plugin.getConfig().getString("personaFile","persona-aris.json");
        config.minMessageLength = plugin.getConfig().getInt("minMessageLength",2);
        config.quietReplyEnable = plugin.getConfig().getBoolean("quietReplyEnable",true);
        config.privateChatEnable = plugin.getConfig().getBoolean("privateChatEnable",true);
        config.privateChatWhitelist = plugin.getConfig().getString("privateChatWhitelist","");
        config.privateChatFriendCacheSecond =
                plugin.getConfig().getInt("privateChatFriendCacheSecond",300);
        config.privateChatContextMessages = plugin.getConfig().getInt("privateChatContextMessages",40);
        config.privateChatMaxReplyChars = plugin.getConfig().getInt("privateChatMaxReplyChars",160);
        config.privateChatImageDebounceSecond =
                plugin.getConfig().getInt("privateChatImageDebounceSecond",4);
        config.privateChatLiveMergeMaxRounds =
                plugin.getConfig().getInt("privateChatLiveMergeMaxRounds",2);
        config.privateChatPendingMaxMessages =
                plugin.getConfig().getInt("privateChatPendingMaxMessages",10);
        config.privateChatInputStatusEnable =
                plugin.getConfig().getBoolean("privateChatInputStatusEnable",true);
        config.privateChatInputStatusGraceSecond =
                plugin.getConfig().getInt("privateChatInputStatusGraceSecond",3);
        config.privateChatInputStatusMaxWaitSecond =
                plugin.getConfig().getInt("privateChatInputStatusMaxWaitSecond",10);
        config.friendRequestRoleDecisionEnable =
                plugin.getConfig().getBoolean("friendRequestRoleDecisionEnable",true);
        config.friendRequestRoleDecisionProfile =
                plugin.getConfig().getString("friendRequestRoleDecisionProfile","");
        config.friendRequestRoleDecisionMinConfidence = parseDouble(
                plugin.getConfig().getString("friendRequestRoleDecisionMinConfidence","0.55"),0.55);
        config.friendRequestRoleDecisionMaxTokens =
                plugin.getConfig().getInt("friendRequestRoleDecisionMaxTokens",1600);
        config.friendRequestRoleDecisionTimeoutSecond =
                plugin.getConfig().getInt("friendRequestRoleDecisionTimeoutSecond",60);
        config.randomTickEnable = plugin.getConfig().getBoolean("randomTickEnable",true);
        config.randomTickMinMinute = plugin.getConfig().getInt("randomTickMinMinute",10);
        config.randomTickMaxMinute = plugin.getConfig().getInt("randomTickMaxMinute",30);
        config.randomTickQuietStartHour = plugin.getConfig().getInt("randomTickQuietStartHour",0);
        config.randomTickQuietEndHour = plugin.getConfig().getInt("randomTickQuietEndHour",8);
        config.randomTickMorningGreetingEnable =
                plugin.getConfig().getBoolean("randomTickMorningGreetingEnable",true);
        config.randomTickMorningStartHour =
                plugin.getConfig().getInt("randomTickMorningStartHour",8);
        config.randomTickMorningEndHour =
                plugin.getConfig().getInt("randomTickMorningEndHour",10);
        config.randomTickMorningChance = parseDouble(
                plugin.getConfig().getString("randomTickMorningChance","0.80"),0.80);
        config.randomTickMorningMaxGroups =
                plugin.getConfig().getInt("randomTickMorningMaxGroups",3);
        config.randomTickMorningMinMessages =
                plugin.getConfig().getInt("randomTickMorningMinMessages",3);
        config.randomTickPrivateFollowUpEnable =
                plugin.getConfig().getBoolean("randomTickPrivateFollowUpEnable",true);
        config.randomTickPrivateFollowUpInitialMinute =
                plugin.getConfig().getInt("randomTickPrivateFollowUpInitialMinute",2);
        config.randomTickPrivateFollowUpMaxMinute =
                plugin.getConfig().getInt("randomTickPrivateFollowUpMaxMinute",120);
        config.randomTickPrivateFollowUpFreshMinute =
                plugin.getConfig().getInt("randomTickPrivateFollowUpFreshMinute",720);
        config.randomTickGroupWindowMinute = plugin.getConfig().getInt("randomTickGroupWindowMinute",360);
        config.randomTickGroupMaxActions = plugin.getConfig().getInt("randomTickGroupMaxActions",2);
        config.randomTickQzoneDailyMax = plugin.getConfig().getInt("randomTickQzoneDailyMax",2);
        config.randomTickQzoneDayChance = parseDouble(
                plugin.getConfig().getString("randomTickQzoneDayChance","0.20"),0.20);
        config.randomTickQzoneEveningChance = parseDouble(
                plugin.getConfig().getString("randomTickQzoneEveningChance","0.80"),0.80);
        config.randomTickQzoneNightChance = parseDouble(
                plugin.getConfig().getString("randomTickQzoneNightChance","0.03"),0.03);
        config.randomTickQzoneEveningStartHour =
                plugin.getConfig().getInt("randomTickQzoneEveningStartHour",18);
        config.randomTickQzoneEveningEndHour =
                plugin.getConfig().getInt("randomTickQzoneEveningEndHour",23);
        config.randomTickQzoneNightEndHour =
                plugin.getConfig().getInt("randomTickQzoneNightEndHour",7);
        config.randomTickOwnerRecentWindowMinute =
                plugin.getConfig().getInt("randomTickOwnerRecentWindowMinute",180);
        config.randomTickOwnerRecentBoostChance = parseDouble(
                plugin.getConfig().getString("randomTickOwnerRecentBoostChance","0.30"),0.30);
        config.randomTickDedupeHours = plugin.getConfig().getInt("randomTickDedupeHours",24);
        config.randomTickMaxTokens = plugin.getConfig().getInt("randomTickMaxTokens",1200);
        config.randomTickTimeoutSecond = plugin.getConfig().getInt("randomTickTimeoutSecond",90);
        config.randomTickReasoningEffort = normalizeEffort(
                plugin.getConfig().getString("randomTickReasoningEffort","low"));
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
        if (config.memberFieldMaxChars < 20) config.memberFieldMaxChars = 20;
        if (config.memberFieldMaxChars > 400) config.memberFieldMaxChars = 400;
        if (config.friendMemoryProfile == null) config.friendMemoryProfile = "";
        if (config.friendMemoryMinNewMessages < 3) config.friendMemoryMinNewMessages = 3;
        if (config.friendMemoryMinNewMessages > 100) config.friendMemoryMinNewMessages = 100;
        if (config.friendMemoryIntervalHours < 1) config.friendMemoryIntervalHours = 1;
        if (config.friendMemoryIntervalHours > 168) config.friendMemoryIntervalHours = 168;
        if (config.friendMemoryContextMessages < 20) config.friendMemoryContextMessages = 20;
        if (config.friendMemoryContextMessages > 400) config.friendMemoryContextMessages = 400;
        if (config.friendMemoryMaxTokens < 400) config.friendMemoryMaxTokens = 400;
        if (config.friendMemoryMaxTokens > 4000) config.friendMemoryMaxTokens = 4000;
        if (config.friendMemoryTimeoutSecond < 30) config.friendMemoryTimeoutSecond = 30;
        if (config.friendMemoryTimeoutSecond > 600) config.friendMemoryTimeoutSecond = 600;
        if (config.friendMemoryInjectMaxChars < 200) config.friendMemoryInjectMaxChars = 200;
        if (config.friendMemoryInjectMaxChars > 8000) config.friendMemoryInjectMaxChars = 8000;
        if (config.randomActivityServices == null) config.randomActivityServices = "";
        if (config.randomActivityMemoryImportance < 1) config.randomActivityMemoryImportance = 1;
        if (config.randomActivityMemoryImportance > 10) config.randomActivityMemoryImportance = 10;
        if (config.randomActivityShareMinScore < 0) config.randomActivityShareMinScore = 0;
        if (config.randomActivityShareMinScore > 1) config.randomActivityShareMinScore = 1;
        if (config.randomActivityTimeoutSecond < 30) config.randomActivityTimeoutSecond = 30;
        if (config.randomActivityTimeoutSecond > 900) config.randomActivityTimeoutSecond = 900;
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
        if (config.replyWillingnessMode == null) config.replyWillingnessMode = "shadow";
        config.replyWillingnessMode = config.replyWillingnessMode.trim().toLowerCase();
        if (!"off".equals(config.replyWillingnessMode)
                && !"enforce".equals(config.replyWillingnessMode)) {
            config.replyWillingnessMode = "shadow";
        }
        if (config.replyWillingnessThreshold < 0) config.replyWillingnessThreshold = 0;
        if (config.replyWillingnessThreshold > 1) config.replyWillingnessThreshold = 1;
        if (config.replyWillingnessWindowSecond < 30) {
            config.replyWillingnessWindowSecond = 30;
        }
        if (config.replyWillingnessWindowSecond > 3600) {
            config.replyWillingnessWindowSecond = 3600;
        }
        if (config.otherRoleBotReplyChance < 0) config.otherRoleBotReplyChance = 0;
        if (config.otherRoleBotReplyChance > 1) config.otherRoleBotReplyChance = 1;
        if (config.maxConsecutiveOtherRoleMessages < 0) config.maxConsecutiveOtherRoleMessages = 0;
        if (config.otherRoleBotNames == null) config.otherRoleBotNames = "";
        if (config.otherRoleBotQQs == null) config.otherRoleBotQQs = "";
        if (config.splitMessageSuppressSecond < 0) config.splitMessageSuppressSecond = 0;
        if (config.splitMessageSuppressSecond > 300) config.splitMessageSuppressSecond = 300;
        if (config.splitMessageSuppressMaxChars < 4) config.splitMessageSuppressMaxChars = 4;
        if (config.splitMessageSuppressMaxChars > 60) config.splitMessageSuppressMaxChars = 60;
        if (config.memoryRelevanceMaxChars < 200) config.memoryRelevanceMaxChars = 200;
        if (config.memoryRelevanceMaxChars > 30000) config.memoryRelevanceMaxChars = 30000;
        if (config.memoryConflictMinOverlap < 1) config.memoryConflictMinOverlap = 1;
        if (config.memoryConflictMinOverlap > 8) config.memoryConflictMinOverlap = 8;
        if (config.memoryRelevancePoolSize < 50) config.memoryRelevancePoolSize = 50;
        if (config.memoryRelevancePoolSize > 3000) config.memoryRelevancePoolSize = 3000;
        if (config.globalMemoryRelevanceMaxChars < 200) {
            config.globalMemoryRelevanceMaxChars = 200;
        }
        if (config.globalMemoryRelevanceMaxChars > 30000) {
            config.globalMemoryRelevanceMaxChars = 30000;
        }
        if (config.shortContextMessages < 1) config.shortContextMessages = 1;
        if (config.shortContextMessages > 500) config.shortContextMessages = 500;
        if (config.shortContextMaxChars < 2000) config.shortContextMaxChars = 2000;
        if (config.shortContextMaxChars > 100000) config.shortContextMaxChars = 100000;
        if (config.structuredContextMaxChars < 300) config.structuredContextMaxChars = 300;
        if (config.structuredContextMaxChars > 8000) config.structuredContextMaxChars = 8000;
        if (config.structuredContextItemMaxChars < 40) config.structuredContextItemMaxChars = 40;
        if (config.structuredContextItemMaxChars > 500) config.structuredContextItemMaxChars = 500;
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
        if (config.memoryMergeRetryCount < 0) config.memoryMergeRetryCount = 0;
        if (config.memoryMergeRetryCount > 5) config.memoryMergeRetryCount = 5;
        if (config.memoryMergeConcurrency < 1) config.memoryMergeConcurrency = 1;
        if (config.memoryMergeConcurrency > 4) config.memoryMergeConcurrency = 4;
        if (config.memoryMergeCooldownMinute < 0) config.memoryMergeCooldownMinute = 0;
        if (config.memoryMergeCooldownMinute > 1440) config.memoryMergeCooldownMinute = 1440;
        if (config.globalMemoryMergeCooldownMinute < 0) {
            config.globalMemoryMergeCooldownMinute = 0;
        }
        if (config.globalMemoryMergeCooldownMinute > 1440) {
            config.globalMemoryMergeCooldownMinute = 1440;
        }
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
        if (config.speechSpoilerLevel < 0) config.speechSpoilerLevel = 0;
        if (config.speechSpoilerLevel > 2) config.speechSpoilerLevel = 2;
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
        if (config.visionReferenceMaxChars < 4000) config.visionReferenceMaxChars = 4000;
        if (config.visionReferenceMaxChars > 120000) config.visionReferenceMaxChars = 120000;
        if (config.imageContextTimeoutSecond < 10) config.imageContextTimeoutSecond = 10;
        if (config.imageContextTimeoutSecond > 3600) config.imageContextTimeoutSecond = 3600;
        if (config.hubVisionWaitMs < 1000) config.hubVisionWaitMs = 1000;
        if (config.hubVisionWaitMs > 60000) config.hubVisionWaitMs = 60000;
        if (config.stickerAttachWindowSecond < 3) config.stickerAttachWindowSecond = 3;
        if (config.stickerAttachWindowSecond > 15) config.stickerAttachWindowSecond = 15;
        if (config.stickerAttachMaxWaitSecond < config.stickerAttachWindowSecond) {
            config.stickerAttachMaxWaitSecond = config.stickerAttachWindowSecond;
        }
        if (config.stickerAttachMaxWaitSecond > 60) config.stickerAttachMaxWaitSecond = 60;
        if (config.stickerSendChance < 0) config.stickerSendChance = 0;
        if (config.stickerSendChance > 1) config.stickerSendChance = 1;
        if (config.stickerVisionCandidates < 2) config.stickerVisionCandidates = 2;
        if (config.stickerVisionCandidates > 9) config.stickerVisionCandidates = 9;
        if (config.stickerVisionProfile == null) config.stickerVisionProfile = "";
        if (config.stickerVisionMaxTokens < 200) config.stickerVisionMaxTokens = 200;
        if (config.stickerVisionMaxTokens > 2000) config.stickerVisionMaxTokens = 2000;
        if (config.stickerVisionTimeoutSecond < 10) config.stickerVisionTimeoutSecond = 10;
        if (config.stickerVisionTimeoutSecond > 180) config.stickerVisionTimeoutSecond = 180;
        if (config.messageBatchWindowSecond < 1) config.messageBatchWindowSecond = 1;
        if (config.messageBatchWindowSecond > 10) config.messageBatchWindowSecond = 10;
        if (config.messageBatchDebounceMode == null) config.messageBatchDebounceMode = "natural";
        config.messageBatchDebounceMode = config.messageBatchDebounceMode.trim().toLowerCase();
        if (!"off".equals(config.messageBatchDebounceMode)
                && !"fast".equals(config.messageBatchDebounceMode)
                && !"natural".equals(config.messageBatchDebounceMode)
                && !"steady".equals(config.messageBatchDebounceMode)) {
            config.messageBatchDebounceMode = "natural";
        }
        if (config.messageBatchMaxWaitSecond < 1) config.messageBatchMaxWaitSecond = 1;
        if (config.messageBatchMaxWaitSecond > 30) config.messageBatchMaxWaitSecond = 30;
        if (config.messageBatchMaxMessages < 2) config.messageBatchMaxMessages = 2;
        if (config.messageBatchMaxMessages > 50) config.messageBatchMaxMessages = 50;
        if (config.learnDirectory == null || config.learnDirectory.trim().isEmpty()) {
            config.learnDirectory = "train-corpus";
        }
        if (config.learnContextBefore < 0) config.learnContextBefore = 0;
        if (config.learnContextBefore > 50) config.learnContextBefore = 50;
        if (config.learnContextAfter < 0) config.learnContextAfter = 0;
        if (config.learnContextAfter > 50) config.learnContextAfter = 50;
        if (config.modelServiceUrl == null) config.modelServiceUrl = "";
        if (config.modelServiceToken == null) config.modelServiceToken = "";
        if (config.modelServiceTimeoutSecond < 10) config.modelServiceTimeoutSecond = 10;
        if (config.modelServiceTimeoutSecond > 1800) config.modelServiceTimeoutSecond = 1800;
        if (config.trainContextBefore < 0) config.trainContextBefore = 0;
        if (config.trainContextBefore > 50) config.trainContextBefore = 50;
        if (config.trainMaxSamplesPerRun < 10) config.trainMaxSamplesPerRun = 10;
        if (config.trainMaxSamplesPerRun > 20000) config.trainMaxSamplesPerRun = 20000;
        if (config.trainMaxChars < 10) config.trainMaxChars = 10;
        if (config.trainMaxChars > 2000) config.trainMaxChars = 2000;
        if (config.messageBatchMaxAgeSecond < 0) config.messageBatchMaxAgeSecond = 0;
        if (config.messageBatchMaxAgeSecond > 120) config.messageBatchMaxAgeSecond = 120;
        if (config.batchMaxSegments < 1) config.batchMaxSegments = 1;
        if (config.batchMaxSegments > 8) config.batchMaxSegments = 8;
        if (config.pokeBackCooldownSecond < 0) config.pokeBackCooldownSecond = 0;
        if (config.pokeBackCooldownSecond > 3600) config.pokeBackCooldownSecond = 3600;
        if (config.promptTotalChars < 4000) config.promptTotalChars = 4000;
        if (config.promptTotalChars > 400000) config.promptTotalChars = 400000;
        if (config.conversationContextTokenLimit < 40000) {
            config.conversationContextTokenLimit = 40000;
        }
        if (config.conversationContextTokenLimit > 2000000) {
            config.conversationContextTokenLimit = 2000000;
        }
        if (config.conversationContextCompactPercent < 0.4) {
            config.conversationContextCompactPercent = 0.4;
        }
        if (config.conversationContextCompactPercent > 0.95) {
            config.conversationContextCompactPercent = 0.95;
        }
        if (config.conversationContextKeepPercent < 0.05) {
            config.conversationContextKeepPercent = 0.05;
        }
        if (config.conversationContextKeepPercent
                >= config.conversationContextCompactPercent - 0.05) {
            config.conversationContextKeepPercent = Math.max(0.05,
                    config.conversationContextCompactPercent - 0.05);
        }
        if (config.conversationAppendRecentMessages < 4) {
            config.conversationAppendRecentMessages = 4;
        }
        if (config.conversationAppendRecentMessages > 100) {
            config.conversationAppendRecentMessages = 100;
        }
        if (config.conversationCompactionProfile == null) {
            config.conversationCompactionProfile = "";
        }
        if (config.conversationCompactionMaxTokens < 1000) {
            config.conversationCompactionMaxTokens = 1000;
        }
        if (config.conversationCompactionMaxTokens > 32000) {
            config.conversationCompactionMaxTokens = 32000;
        }
        if (config.conversationCompactionTimeoutSecond < 30) {
            config.conversationCompactionTimeoutSecond = 30;
        }
        if (config.conversationCompactionTimeoutSecond > 600) {
            config.conversationCompactionTimeoutSecond = 600;
        }
        if (config.routerProfile == null) config.routerProfile = "";
        if (config.routerMaxTokens < 200) config.routerMaxTokens = 200;
        if (config.routerMaxTokens > 4000) config.routerMaxTokens = 4000;
        if (config.styleProfile == null) config.styleProfile = "";
        if (config.styleMaxChars < 20) config.styleMaxChars = 20;
        if (config.styleMaxChars > 300) config.styleMaxChars = 300;
        if (config.styleFailureThreshold < 1) config.styleFailureThreshold = 1;
        if (config.styleFailureThreshold > 10) config.styleFailureThreshold = 10;
        if (config.styleFailureCooldownSecond < 30) config.styleFailureCooldownSecond = 30;
        if (config.styleFailureCooldownSecond > 3600) config.styleFailureCooldownSecond = 3600;
        if (config.styleMaxTokens < 100) config.styleMaxTokens = 100;
        if (config.styleMaxTokens > 2000) config.styleMaxTokens = 2000;
        if (config.maxLongMemories < 5) config.maxLongMemories = 5;
        if (config.maxLongMemories > 500) config.maxLongMemories = 500;
        if (config.topicFatigueWindowMinute < 1) config.topicFatigueWindowMinute = 1;
        if (config.topicFatigueWindowMinute > 1440) config.topicFatigueWindowMinute = 1440;
        if (config.topicFatigueMinCount < 2) config.topicFatigueMinCount = 2;
        if (config.topicFatigueMinCount > 100) config.topicFatigueMinCount = 100;
        if (config.topicFatigueSelfCount < 1) config.topicFatigueSelfCount = 1;
        if (config.topicFatigueSelfCount > 50) config.topicFatigueSelfCount = 50;
        if (config.topicFatigueKeepHours < 1) config.topicFatigueKeepHours = 1;
        if (config.topicFatigueKeepHours > 720) config.topicFatigueKeepHours = 720;
        if (config.threadWindowSecond < 30) config.threadWindowSecond = 30;
        if (config.threadWindowSecond > 3600) config.threadWindowSecond = 3600;
        if (config.replyFeedbackWindowSecond < 10) config.replyFeedbackWindowSecond = 10;
        if (config.replyFeedbackWindowSecond > 3600) config.replyFeedbackWindowSecond = 3600;
        if (config.replyFeedbackMinSamples < 1) config.replyFeedbackMinSamples = 1;
        if (config.replyFeedbackMinSamples > 100) config.replyFeedbackMinSamples = 100;
        if (config.replyFeedbackMaxPenalty < 0) config.replyFeedbackMaxPenalty = 0;
        if (config.replyFeedbackMaxPenalty > 1) config.replyFeedbackMaxPenalty = 1;
        if (config.replyFeedbackBonus < 0) config.replyFeedbackBonus = 0;
        if (config.replyFeedbackBonus > 0.5) config.replyFeedbackBonus = 0.5;
        if (config.replyFeedbackHistory < 5) config.replyFeedbackHistory = 5;
        if (config.replyFeedbackHistory > 200) config.replyFeedbackHistory = 200;
        if (config.replyReviewProfile == null) config.replyReviewProfile = "";
        if (config.replyReviewMaxTokens < 300) config.replyReviewMaxTokens = 300;
        if (config.replyReviewMaxTokens > 4000) config.replyReviewMaxTokens = 4000;
        if (config.replyReviewTimeoutSecond < 20) config.replyReviewTimeoutSecond = 20;
        if (config.replyReviewTimeoutSecond > 300) config.replyReviewTimeoutSecond = 300;
        if (config.replyReviewMinScore < 0) config.replyReviewMinScore = 0;
        if (config.replyReviewMinScore > 100) config.replyReviewMinScore = 100;
        if (config.toolMaxSteps < 1) config.toolMaxSteps = 1;
        if (config.toolMaxSteps > 16) config.toolMaxSteps = 16;
        if (config.toolCoreTools == null) config.toolCoreTools = "";
        if (config.toolLoadedMaxTools < 1) config.toolLoadedMaxTools = 1;
        if (config.toolLoadedMaxTools > 32) config.toolLoadedMaxTools = 32;
        if (config.toolSearchMaxResults < 1) config.toolSearchMaxResults = 1;
        if (config.toolSearchMaxResults > 10) config.toolSearchMaxResults = 10;
        if (config.toolMaxCalls < 1) config.toolMaxCalls = 1;
        if (config.toolMaxCalls > 32) config.toolMaxCalls = 32;
        if (config.toolResultMaxChars < 500) config.toolResultMaxChars = 500;
        if (config.toolResultMaxChars > 64000) config.toolResultMaxChars = 64000;
        if (config.toolResultTotalMaxChars < 4000) config.toolResultTotalMaxChars = 4000;
        if (config.toolResultTotalMaxChars > 200000) config.toolResultTotalMaxChars = 200000;
        if (config.toolLiveMessageMax < 1) config.toolLiveMessageMax = 1;
        if (config.toolLiveMessageMax > 30) config.toolLiveMessageMax = 30;
        if (config.toolLoopMaxMillis < 10000) config.toolLoopMaxMillis = 10000;
        if (config.toolLoopMaxMillis > 600000) config.toolLoopMaxMillis = 600000;
        if (config.toolCallMaxMillis < 1000) config.toolCallMaxMillis = 1000;
        if (config.toolCallMaxMillis > 120000) config.toolCallMaxMillis = 120000;
        if (config.toolParallelMax < 1) config.toolParallelMax = 1;
        if (config.toolParallelMax > 8) config.toolParallelMax = 8;
        if (config.toolAiRetryCount < 0) config.toolAiRetryCount = 0;
        if (config.toolAiRetryCount > 3) config.toolAiRetryCount = 3;
        if (config.toolTurnKeepHours < 1) config.toolTurnKeepHours = 1;
        if (config.toolTurnKeepHours > 168) config.toolTurnKeepHours = 168;
        if (config.toolRecoverMaxMinutes < 5) config.toolRecoverMaxMinutes = 5;
        if (config.toolRecoverMaxMinutes > 1440) config.toolRecoverMaxMinutes = 1440;
        if (config.toolMemoryInjectItems < 0) config.toolMemoryInjectItems = 0;
        if (config.toolMemoryInjectItems > 30) config.toolMemoryInjectItems = 30;
        if (config.toolMemoryInjectMaxChars < 500) config.toolMemoryInjectMaxChars = 500;
        if (config.toolMemoryInjectMaxChars > 32000) config.toolMemoryInjectMaxChars = 32000;
        if (config.toolMemoryKeepHours < 1) config.toolMemoryKeepHours = 1;
        if (config.toolMemoryKeepHours > 720) config.toolMemoryKeepHours = 720;
        if (config.toolMemoryMaxRows < 20) config.toolMemoryMaxRows = 20;
        if (config.toolMemoryMaxRows > 5000) config.toolMemoryMaxRows = 5000;
        if (config.workspaceMaxReadChars < 1000) config.workspaceMaxReadChars = 1000;
        if (config.workspaceMaxReadChars > 200000) config.workspaceMaxReadChars = 200000;
        if (config.workspaceMaxWriteChars < 1000) config.workspaceMaxWriteChars = 1000;
        if (config.workspaceMaxWriteChars > 500000) config.workspaceMaxWriteChars = 500000;
        if (config.workspaceMaxFilesPerList < 10) config.workspaceMaxFilesPerList = 10;
        if (config.workspaceMaxFilesPerList > 1000) config.workspaceMaxFilesPerList = 1000;
        if (config.workspaceMaxTotalBytes < 1024L * 1024L) {
            config.workspaceMaxTotalBytes = 1024L * 1024L;
        }
        if (config.workspaceMaxTotalBytes > 1024L * 1024L * 1024L) {
            config.workspaceMaxTotalBytes = 1024L * 1024L * 1024L;
        }
        if (config.workspaceMaxMediaBytes < 1024) config.workspaceMaxMediaBytes = 1024;
        if (config.workspaceMaxMediaBytes > 64 * 1024 * 1024) {
            config.workspaceMaxMediaBytes = 64 * 1024 * 1024;
        }
        if (config.workspaceHistoryMaxVersions < 0) config.workspaceHistoryMaxVersions = 0;
        if (config.workspaceHistoryMaxVersions > 50) config.workspaceHistoryMaxVersions = 50;
        if (config.workspaceTrashKeepDays < 1) config.workspaceTrashKeepDays = 1;
        if (config.workspaceTrashKeepDays > 365) config.workspaceTrashKeepDays = 365;
        if (config.webWhitelist == null) config.webWhitelist = "";
        if (config.webSearchUrl == null || config.webSearchUrl.trim().isEmpty()) {
            config.webSearchUrl = "https://www.bing.com/search?q={query}";
        }
        if (config.webSearchFallbackUrl == null) config.webSearchFallbackUrl = "";
        if (config.webSearchMaxResults < 1) config.webSearchMaxResults = 1;
        if (config.webSearchMaxResults > 10) config.webSearchMaxResults = 10;
        if (config.webOwnerAuthWindowSecond < 30) config.webOwnerAuthWindowSecond = 30;
        if (config.webOwnerAuthWindowSecond > 3600) config.webOwnerAuthWindowSecond = 3600;
        if (config.webTimeoutSecond < 3) config.webTimeoutSecond = 3;
        if (config.webTimeoutSecond > 60) config.webTimeoutSecond = 60;
        if (config.webMaxBytes < 4096) config.webMaxBytes = 4096;
        if (config.webMaxBytes > 4 * 1024 * 1024) config.webMaxBytes = 4 * 1024 * 1024;
        if (config.webMaxChars < 1000) config.webMaxChars = 1000;
        if (config.webMaxChars > 100000) config.webMaxChars = 100000;
        if (config.webImageMaxBytes < 1024 * 1024) config.webImageMaxBytes = 1024 * 1024;
        if (config.webImageMaxBytes > 20 * 1024 * 1024) config.webImageMaxBytes = 20 * 1024 * 1024;
        if (config.webCacheSecond < 0) config.webCacheSecond = 0;
        if (config.webCacheSecond > 86400) config.webCacheSecond = 86400;
        if (config.pacingBaseMs < 0) config.pacingBaseMs = 0;
        if (config.pacingPerCharMs < 0) config.pacingPerCharMs = 0;
        if (config.pacingMaxMs < 0) config.pacingMaxMs = 0;
        if (config.pacingNightMultiplier < 0.5) config.pacingNightMultiplier = 0.5;
        if (config.pacingNightMultiplier > 3.0) config.pacingNightMultiplier = 3.0;
        if (config.pacingActiveMultiplier < 0.3) config.pacingActiveMultiplier = 0.3;
        if (config.pacingActiveMultiplier > 1.5) config.pacingActiveMultiplier = 1.5;
        if (config.pacingActiveThreshold < 0) config.pacingActiveThreshold = 0;
        if (config.pacingActiveThreshold > 1) config.pacingActiveThreshold = 1;
        if (config.pacingNightStartHour < 0) config.pacingNightStartHour = 0;
        if (config.pacingNightStartHour > 23) config.pacingNightStartHour = 23;
        if (config.pacingNightEndHour < 0) config.pacingNightEndHour = 0;
        if (config.pacingNightEndHour > 24) config.pacingNightEndHour = 24;
        if (config.expressionMinCount < 2) config.expressionMinCount = 2;
        if (config.expressionMinCount > 100) config.expressionMinCount = 100;
        if (config.expressionMinUsers < 1) config.expressionMinUsers = 1;
        if (config.expressionMinUsers > 50) config.expressionMinUsers = 50;
        if (config.expressionMaxCandidates < 10) config.expressionMaxCandidates = 10;
        if (config.expressionMaxCandidates > 1000) config.expressionMaxCandidates = 1000;
        if (config.expressionInjectMaxChars < 100) config.expressionInjectMaxChars = 100;
        if (config.expressionInjectMaxChars > 4000) config.expressionInjectMaxChars = 4000;
        if (config.expressionEmbeddingProfile == null) config.expressionEmbeddingProfile = "";
        if (config.expressionEmbeddingTimeoutSecond < 10) {
            config.expressionEmbeddingTimeoutSecond = 10;
        }
        if (config.expressionEmbeddingTimeoutSecond > 300) {
            config.expressionEmbeddingTimeoutSecond = 300;
        }
        if (config.expressionVectorTopK < 1) config.expressionVectorTopK = 1;
        if (config.expressionVectorTopK > 20) config.expressionVectorTopK = 20;
        if (config.expressionVectorMinScore < -1) config.expressionVectorMinScore = -1;
        if (config.expressionVectorMinScore > 1) config.expressionVectorMinScore = 1;
        if (config.expressionVectorQueryCacheMinute < 1) {
            config.expressionVectorQueryCacheMinute = 1;
        }
        if (config.expressionVectorQueryCacheMinute > 1440) {
            config.expressionVectorQueryCacheMinute = 1440;
        }
        if (config.expressionReviewMode == null) config.expressionReviewMode = "balanced";
        config.expressionReviewMode = config.expressionReviewMode.trim().toLowerCase();
        if (!"auto".equals(config.expressionReviewMode)
                && !"suggest".equals(config.expressionReviewMode)
                && !"balanced".equals(config.expressionReviewMode)) {
            config.expressionReviewMode = "balanced";
        }
        if (config.expressionReviewProfile == null) config.expressionReviewProfile = "";
        if (config.expressionReviewIntervalHours < 1) config.expressionReviewIntervalHours = 1;
        if (config.expressionReviewIntervalHours > 168) config.expressionReviewIntervalHours = 168;
        if (config.expressionReviewMaxCandidates < 1) config.expressionReviewMaxCandidates = 1;
        if (config.expressionReviewMaxCandidates > 100) config.expressionReviewMaxCandidates = 100;
        if (config.expressionReviewMaxTokens < 800) config.expressionReviewMaxTokens = 800;
        if (config.expressionReviewMaxTokens > 16000) config.expressionReviewMaxTokens = 16000;
        if (config.expressionReviewTimeoutSecond < 30) config.expressionReviewTimeoutSecond = 30;
        if (config.expressionReviewTimeoutSecond > 600) config.expressionReviewTimeoutSecond = 600;
        if (config.expressionAutoApproveMinQuality < 0) config.expressionAutoApproveMinQuality = 0;
        if (config.expressionAutoApproveMinQuality > 1) config.expressionAutoApproveMinQuality = 1;
        if (config.expressionBalancedRejectMaxQuality < 0) {
            config.expressionBalancedRejectMaxQuality = 0;
        }
        if (config.expressionBalancedRejectMaxQuality
                > config.expressionAutoApproveMinQuality) {
            config.expressionBalancedRejectMaxQuality = config.expressionAutoApproveMinQuality;
        }
        if (config.attentionDecayMinute < 1) config.attentionDecayMinute = 1;
        if (config.attentionDecayMinute > 1440) config.attentionDecayMinute = 1440;
        if (config.attentionLowThreshold < 0.05) config.attentionLowThreshold = 0.05;
        if (config.attentionLowThreshold > 0.8) config.attentionLowThreshold = 0.8;
        if (config.attentionHighThreshold < config.attentionLowThreshold) config.attentionHighThreshold = config.attentionLowThreshold;
        if (config.attentionHighThreshold > 0.95) config.attentionHighThreshold = 0.95;
        if (config.attentionMaxPenalty < 0) config.attentionMaxPenalty = 0;
        if (config.attentionMaxPenalty > 1) config.attentionMaxPenalty = 1;
        if (config.attentionBonus < 0) config.attentionBonus = 0;
        if (config.attentionBonus > 0.5) config.attentionBonus = 0.5;
        if (config.attentionTopicLimit < 10) config.attentionTopicLimit = 10;
        if (config.attentionTopicLimit > 2000) config.attentionTopicLimit = 2000;
        if (config.dailyReviewHour < 0) config.dailyReviewHour = 0;
        if (config.dailyReviewHour > 23) config.dailyReviewHour = 23;
        if (config.dailyReviewMaxMessages < 20) config.dailyReviewMaxMessages = 20;
        if (config.dailyReviewMaxMessages > 2000) config.dailyReviewMaxMessages = 2000;
        if (config.dailyReviewMaxTokens < 500) config.dailyReviewMaxTokens = 500;
        if (config.dailyReviewMaxTokens > 16000) config.dailyReviewMaxTokens = 16000;
        if (config.dailyReviewTimeoutSecond < 30) config.dailyReviewTimeoutSecond = 30;
        if (config.dailyReviewTimeoutSecond > 600) config.dailyReviewTimeoutSecond = 600;
        if (config.traceKeepHours < 1) config.traceKeepHours = 1;
        if (config.traceKeepHours > 720) config.traceKeepHours = 720;
        if (config.traceMaxRows < 100) config.traceMaxRows = 100;
        if (config.traceMaxRows > 20000) config.traceMaxRows = 20000;
        if (config.evalProfile == null) config.evalProfile = "";
        if (config.evalMaxTokens < 300) config.evalMaxTokens = 300;
        if (config.evalMaxTokens > 4000) config.evalMaxTokens = 4000;
        if (config.evalTimeoutSecond < 20) config.evalTimeoutSecond = 20;
        if (config.evalTimeoutSecond > 300) config.evalTimeoutSecond = 300;
        if (config.replyMaxTokens < 200) config.replyMaxTokens = 200;
        if (config.replyMaxTokens > 32000) config.replyMaxTokens = 32000;
        if (config.drawPromptProfile == null) config.drawPromptProfile = "";
        if (config.drawPromptMaxTokens < 300) config.drawPromptMaxTokens = 300;
        if (config.drawPromptMaxTokens > 8000) config.drawPromptMaxTokens = 8000;
        if (config.drawPromptTimeoutSecond < 30) config.drawPromptTimeoutSecond = 30;
        if (config.drawPromptTimeoutSecond > 600) config.drawPromptTimeoutSecond = 600;
        if (config.drawPromptMaxChars < 300) config.drawPromptMaxChars = 300;
        if (config.drawPromptMaxChars > 4000) config.drawPromptMaxChars = 4000;
        if (config.drawPromptNegativeMaxChars < 100) config.drawPromptNegativeMaxChars = 100;
        if (config.drawPromptNegativeMaxChars > 2000) config.drawPromptNegativeMaxChars = 2000;
        if (config.drawPromptKnowledgeMaxChars < 400) config.drawPromptKnowledgeMaxChars = 400;
        if (config.drawPromptKnowledgeMaxChars > 4000) config.drawPromptKnowledgeMaxChars = 4000;
        if (config.replyImageMaxTokens < 400) config.replyImageMaxTokens = 400;
        if (config.replyImageMaxTokens > 16000) config.replyImageMaxTokens = 16000;
        if (config.replySegmentMaxChars < 8) config.replySegmentMaxChars = 8;
        if (config.replySegmentMaxChars > 60) config.replySegmentMaxChars = 60;
        if (config.replySplitPunctuation == null || config.replySplitPunctuation.isEmpty()) {
            config.replySplitPunctuation = "。！？!?；;，、：,:～~…";
        }
        if (config.replyMaxSegments < 1) config.replyMaxSegments = 1;
        if (config.replyMaxSegments > 5) config.replyMaxSegments = 5;
        if (config.replyBeatMaxSegments < 1) config.replyBeatMaxSegments = 1;
        if (config.replyBeatMaxSegments > 5) config.replyBeatMaxSegments = 5;
        if (config.replyBeatBaseDelayMs < 100) config.replyBeatBaseDelayMs = 100;
        if (config.replyBeatBaseDelayMs > 5000) config.replyBeatBaseDelayMs = 5000;
        if (config.replyBeatPerCharMs < 0) config.replyBeatPerCharMs = 0;
        if (config.replyBeatPerCharMs > 200) config.replyBeatPerCharMs = 200;
        if (config.replyBeatMaxDelayMs < 300) config.replyBeatMaxDelayMs = 300;
        if (config.replyBeatMaxDelayMs > 10000) config.replyBeatMaxDelayMs = 10000;
        if (config.pokeReplyMaxChars < 8) config.pokeReplyMaxChars = 8;
        if (config.pokeReplyMaxChars > 60) config.pokeReplyMaxChars = 60;
        if (config.recentReplyCheckCount < 1) config.recentReplyCheckCount = 1;
        if (config.recentReplyCheckCount > 30) config.recentReplyCheckCount = 30;
        if (config.repeatSimilarityThreshold < 0.3) config.repeatSimilarityThreshold = 0.3;
        if (config.repeatSimilarityThreshold > 1) config.repeatSimilarityThreshold = 1;
        if (config.repeatCommonRunMinChars < 4) config.repeatCommonRunMinChars = 4;
        if (config.repeatCommonRunMinChars > 40) config.repeatCommonRunMinChars = 40;
        if (config.repeatCheckMinChars < 2) config.repeatCheckMinChars = 2;
        if (config.repeatOpeningLimit < 1) config.repeatOpeningLimit = 1;
        if (config.minMessageLength < 1) config.minMessageLength = 1;
        if (config.privateChatWhitelist == null) config.privateChatWhitelist = "";
        if (config.privateChatFriendCacheSecond < 30) config.privateChatFriendCacheSecond = 30;
        if (config.privateChatFriendCacheSecond > 3600) config.privateChatFriendCacheSecond = 3600;
        if (config.privateChatContextMessages < 4) config.privateChatContextMessages = 4;
        if (config.privateChatContextMessages > 200) config.privateChatContextMessages = 200;
        if (config.privateChatMaxReplyChars < 20) config.privateChatMaxReplyChars = 20;
        if (config.privateChatMaxReplyChars > 1000) config.privateChatMaxReplyChars = 1000;
        if (config.privateChatImageDebounceSecond < 1) {
            config.privateChatImageDebounceSecond = 1;
        }
        if (config.privateChatImageDebounceSecond > 15) {
            config.privateChatImageDebounceSecond = 15;
        }
        if (config.privateChatLiveMergeMaxRounds < 0) {
            config.privateChatLiveMergeMaxRounds = 0;
        }
        if (config.privateChatLiveMergeMaxRounds > 4) {
            config.privateChatLiveMergeMaxRounds = 4;
        }
        if (config.privateChatPendingMaxMessages < 2) {
            config.privateChatPendingMaxMessages = 2;
        }
        if (config.privateChatPendingMaxMessages > 50) {
            config.privateChatPendingMaxMessages = 50;
        }
        if (config.privateChatInputStatusGraceSecond < 1) {
            config.privateChatInputStatusGraceSecond = 1;
        }
        if (config.privateChatInputStatusGraceSecond > 10) {
            config.privateChatInputStatusGraceSecond = 10;
        }
        if (config.privateChatInputStatusMaxWaitSecond < config.privateChatInputStatusGraceSecond) {
            config.privateChatInputStatusMaxWaitSecond = config.privateChatInputStatusGraceSecond;
        }
        if (config.privateChatInputStatusMaxWaitSecond > 60) {
            config.privateChatInputStatusMaxWaitSecond = 60;
        }
        if (config.friendRequestRoleDecisionProfile == null) {
            config.friendRequestRoleDecisionProfile = "";
        }
        if (config.friendRequestRoleDecisionMinConfidence < 0) {
            config.friendRequestRoleDecisionMinConfidence = 0;
        }
        if (config.friendRequestRoleDecisionMinConfidence > 1) {
            config.friendRequestRoleDecisionMinConfidence = 1;
        }
        if (config.friendRequestRoleDecisionMaxTokens < 400) {
            config.friendRequestRoleDecisionMaxTokens = 400;
        }
        if (config.friendRequestRoleDecisionMaxTokens > 8000) {
            config.friendRequestRoleDecisionMaxTokens = 8000;
        }
        if (config.friendRequestRoleDecisionTimeoutSecond < 15) {
            config.friendRequestRoleDecisionTimeoutSecond = 15;
        }
        if (config.friendRequestRoleDecisionTimeoutSecond > 300) {
            config.friendRequestRoleDecisionTimeoutSecond = 300;
        }
        if (config.randomTickMinMinute < 1) config.randomTickMinMinute = 1;
        if (config.randomTickMinMinute > 1440) config.randomTickMinMinute = 1440;
        if (config.randomTickMaxMinute < config.randomTickMinMinute) {
            config.randomTickMaxMinute = config.randomTickMinMinute;
        }
        if (config.randomTickMaxMinute > 1440) config.randomTickMaxMinute = 1440;
        if (config.randomTickQuietStartHour < 0) config.randomTickQuietStartHour = 0;
        if (config.randomTickQuietStartHour > 23) config.randomTickQuietStartHour = 23;
        if (config.randomTickQuietEndHour < 0) config.randomTickQuietEndHour = 0;
        if (config.randomTickQuietEndHour > 24) config.randomTickQuietEndHour = 24;
        if (config.randomTickMorningStartHour < 0) config.randomTickMorningStartHour = 0;
        if (config.randomTickMorningStartHour > 23) config.randomTickMorningStartHour = 23;
        if (config.randomTickMorningEndHour < config.randomTickMorningStartHour) {
            config.randomTickMorningEndHour = config.randomTickMorningStartHour;
        }
        if (config.randomTickMorningEndHour > 24) config.randomTickMorningEndHour = 24;
        if (config.randomTickMorningChance < 0) config.randomTickMorningChance = 0;
        if (config.randomTickMorningChance > 1) config.randomTickMorningChance = 1;
        if (config.randomTickMorningMaxGroups < 1) config.randomTickMorningMaxGroups = 1;
        if (config.randomTickMorningMaxGroups > 10) config.randomTickMorningMaxGroups = 10;
        if (config.randomTickMorningMinMessages < 1) config.randomTickMorningMinMessages = 1;
        if (config.randomTickMorningMinMessages > 100) config.randomTickMorningMinMessages = 100;
        if (config.randomTickPrivateFollowUpInitialMinute < 1) {
            config.randomTickPrivateFollowUpInitialMinute = 1;
        }
        if (config.randomTickPrivateFollowUpInitialMinute > 60) {
            config.randomTickPrivateFollowUpInitialMinute = 60;
        }
        if (config.randomTickPrivateFollowUpMaxMinute
                < config.randomTickPrivateFollowUpInitialMinute) {
            config.randomTickPrivateFollowUpMaxMinute =
                    config.randomTickPrivateFollowUpInitialMinute;
        }
        if (config.randomTickPrivateFollowUpMaxMinute > 1440) {
            config.randomTickPrivateFollowUpMaxMinute = 1440;
        }
        if (config.randomTickPrivateFollowUpFreshMinute
                < config.randomTickPrivateFollowUpMaxMinute) {
            config.randomTickPrivateFollowUpFreshMinute =
                    config.randomTickPrivateFollowUpMaxMinute;
        }
        if (config.randomTickPrivateFollowUpFreshMinute > 10080) {
            config.randomTickPrivateFollowUpFreshMinute = 10080;
        }
        if (config.randomTickGroupWindowMinute < 1) config.randomTickGroupWindowMinute = 1;
        if (config.randomTickGroupWindowMinute > 10080) config.randomTickGroupWindowMinute = 10080;
        if (config.randomTickGroupMaxActions < 0) config.randomTickGroupMaxActions = 0;
        if (config.randomTickGroupMaxActions > 100) config.randomTickGroupMaxActions = 100;
        if (config.randomTickQzoneDailyMax < 0) config.randomTickQzoneDailyMax = 0;
        if (config.randomTickQzoneDailyMax > 20) config.randomTickQzoneDailyMax = 20;
        if (config.randomTickQzoneDayChance < 0) config.randomTickQzoneDayChance = 0;
        if (config.randomTickQzoneDayChance > 1) config.randomTickQzoneDayChance = 1;
        if (config.randomTickQzoneEveningChance < 0) config.randomTickQzoneEveningChance = 0;
        if (config.randomTickQzoneEveningChance > 1) config.randomTickQzoneEveningChance = 1;
        if (config.randomTickQzoneNightChance < 0) config.randomTickQzoneNightChance = 0;
        if (config.randomTickQzoneNightChance > 1) config.randomTickQzoneNightChance = 1;
        if (config.randomTickQzoneEveningStartHour < 0) config.randomTickQzoneEveningStartHour = 0;
        if (config.randomTickQzoneEveningStartHour > 23) config.randomTickQzoneEveningStartHour = 23;
        if (config.randomTickQzoneEveningEndHour < config.randomTickQzoneEveningStartHour) {
            config.randomTickQzoneEveningEndHour = config.randomTickQzoneEveningStartHour;
        }
        if (config.randomTickQzoneEveningEndHour > 24) config.randomTickQzoneEveningEndHour = 24;
        if (config.randomTickQzoneNightEndHour < 0) config.randomTickQzoneNightEndHour = 0;
        if (config.randomTickQzoneNightEndHour > 12) config.randomTickQzoneNightEndHour = 12;
        if (config.randomTickOwnerRecentWindowMinute < 1) {
            config.randomTickOwnerRecentWindowMinute = 1;
        }
        if (config.randomTickOwnerRecentWindowMinute > 1440) {
            config.randomTickOwnerRecentWindowMinute = 1440;
        }
        if (config.randomTickOwnerRecentBoostChance < 0) {
            config.randomTickOwnerRecentBoostChance = 0;
        }
        if (config.randomTickOwnerRecentBoostChance > 1) {
            config.randomTickOwnerRecentBoostChance = 1;
        }
        if (config.randomTickDedupeHours < 1) config.randomTickDedupeHours = 1;
        if (config.randomTickDedupeHours > 720) config.randomTickDedupeHours = 720;
        if (config.randomTickMaxTokens < 200) config.randomTickMaxTokens = 200;
        if (config.randomTickMaxTokens > 8000) config.randomTickMaxTokens = 8000;
        if (config.randomTickTimeoutSecond < 20) config.randomTickTimeoutSecond = 20;
        if (config.randomTickTimeoutSecond > 600) config.randomTickTimeoutSecond = 600;
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
