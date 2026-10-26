package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Event.GroupMessageEvent;
import org.moboxlab.moboxbot.API.Event.NoticeEvent;
import org.moboxlab.moboxbot.API.OneBot.MessageUtil;
import org.moboxlab.moboxbot.API.OneBot.OneBotClient;
import org.moboxlab.moboxbot.API.Plugin;
import org.moboxlab.moboxbot.API.PluginService;
import org.moboxlab.moboxbot.API.Storage.StorageService;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 角色扮演与记忆服务
 */
public class RoleplayService {
    static final String MSG_TABLE = "plugin_mbb_roleplay_messages";
    static final String MEMORY_TABLE = "plugin_mbb_roleplay_memory";
    static final String STATE_TABLE = "plugin_mbb_roleplay_state";
    static final String GROUP_TABLE = "plugin_mbb_roleplay_group";
    static final String TOPIC_TABLE = "plugin_mbb_roleplay_topic";
    static final String CONTEXT_TABLE = "plugin_mbb_roleplay_context_state";
    /** 引用回复里被引用内容的截断长度 */

    /** 分句时优先保留的句末标点 */

    private final Plugin plugin;
    private final RoleplayReminderService reminderService;
    private final RoleplayTimeService timeService;
    private final RoleplayTodoService todoService;
    private final RoleplayGlobalMemoryService globalMemoryService;
    private final RoleplaySpeechCorpusService speechCorpusService;
    private final RoleplayKnowledgeService knowledgeService;
    private final RoleplayActionService actionService;
    private final RoleplayEmotionService emotionService;
    private final RoleplayMemberService memberService;
    private final RoleplayFriendMemoryService friendMemoryService;
    private final RoleplayBlacklistService blacklistService;
    private final RoleplayQuietService quietService;
    private final RoleplayReplyFeedbackService replyFeedbackService;
    private final RoleplayExpressionService expressionService;
    private final RoleplayExpressionReviewService expressionReviewService;
    private final RoleplayAttentionService attentionService;
    private final RoleplayReplyWillingnessService replyWillingnessService;
    private final RoleplayDailyReviewService dailyReviewService;
    private final RoleplayMessageSender messageSender;
    private final RoleplayContextService contextService;
    private final RoleplayContextCompactor contextCompactor;
    private final RoleplayConversationContextService conversationContextService;
    private final RoleplayMemoryDedupService memoryDedupService;
    private final RoleplayPromptAssembler promptAssembler;
    private final RoleplayMemoryBackupService memoryBackupService;
    private final RoleplayMemoryUpdateService memoryUpdateService;
    private final RoleplayPrivateChatService privateChatService;
    private final RoleplayFriendRequestService friendRequestService;
    private final RoleplayRandomTickService randomTickService;
    private final RoleplayActivityScheduler activityScheduler;
    private final RoleplayBatchScheduler batchScheduler;
    private final RoleplayPokeService pokeService;
    private final RoleplayReplyExecutor replyExecutor;
    private final RoleplayTurnProcessor turnProcessor;
    private final RoleplayTextUtils textUtils;
    private final RoleplayJsonUtils jsonUtils;
    private final RoleplayImageContextService imageContextService;
    private final RoleplayHubService hubService;
    private final RoleplayStickerService stickerService;
    private final RoleplayAddressService addressService;
    private final RoleplayRateLimitService rateLimitService;
    private final RoleplayMemoryQueryService memoryQueryService;
    private final RoleplayBrainMemoryService brainMemoryService;
    private final RoleplayDrawNotifyService drawNotifyService;
    private final RoleplayDrawPromptService drawPromptService;
    private final RoleplayTraceService traceService;
    private final RoleplayEvalService evalService;
    private final RoleplayReplyReviewService replyReviewService;
    private final RoleplayThreadService threadService;
    private final RoleplayPacingService pacingService;
    private final RoleplaySafetyService safetyService;
    private final RoleplayGroupStatusService groupStatusService;
    private final RoleplayMessagePreprocessor messagePreprocessor;
    private final RoleplaySchemaService schemaService;
    private final RoleplayExecutorService executorService;
    private final RoleplayReplyGenerationService replyGenerationService;
    private final RoleplayRoutingService routingService;
    private final RoleplayPromptMaterialService promptMaterialService;
    private final RoleplayMessageContentService contentService;
    private final RoleplayLearnService learnService;
    private final RoleplayModelClient modelClient;
    private final RoleplayTrainService trainService;
    private final RoleplaySkillRegistry skillRegistry;
    private final RoleplaySlangLexicon slangLexicon;
    private final RoleplayMemeService memeService;
    private final RoleplayToolLoopService toolLoopService;
    private final RoleplayToolMemoryService toolMemoryService;
    private final RoleplayToolTurnService toolTurnService;
    private final RoleplayRouter router;
    private volatile RoleplayConfig config;
    private volatile RoleplayPersona persona;
    final Map<Long,RoleplayConversationState> conversationStateMap = new ConcurrentHashMap<>();


    final Map<Long,Boolean> memoryUpdatingMap = new ConcurrentHashMap<>();
    final Map<Long,String> pendingMemoryMap = new ConcurrentHashMap<>();
    final Map<Long,String> memoryErrorTypeMap = new ConcurrentHashMap<>();
    final Map<Long,Boolean> memoryMergingMap = new ConcurrentHashMap<>();



    //被并入延后回合的消息 ID，处理下一批时直接跳过，避免同一句话回两次
    final Set<Long> mergedMessageIDs = Collections.newSetFromMap(
            new ConcurrentHashMap<Long,Boolean>());
    final Object groupQueueLock = new Object();
    final Map<Long,Boolean> groupBusyMap = new HashMap<>();
    //回合执行线程池：路由与生成都是长耗时 AI 调用，不再占用共享的定时任务线程


    //后台任务线程池：记忆整理、情绪分析、识图这类长耗时调用同样不占共享定时线程

    //合批缓冲：同一群在窗口内到达的消息先攒起来，窗口结束后当成一个回合处理
    final Map<Long,List<GroupMessageEvent>> batchBufferMap = new HashMap<>();
    final Map<Long,Integer> batchGenerationMap = new HashMap<>();
    final Map<Long,Long> batchBufferStartMap = new HashMap<>();
    final Map<Long,Boolean> batchCollectingMap = new HashMap<>();
    //按消息 ID 记录真正进入合批队列的时间，长工具回合后的补回消息不按原始发送时间过期
    final Map<Long,Long> batchQueuedAtMap = new ConcurrentHashMap<>();
    private static final Pattern REMEMBER_PAIR = Pattern.compile(
            "(?is)<\\s*remember\\s*>(.*?)<\\s*/\\s*remember\\s*>");
    private static final Pattern REMEMBER_OPEN = Pattern.compile(
            "(?is)<\\s*remember\\s*/?\\s*>");
    private static final Pattern REMEMBER_CLOSE = Pattern.compile(
            "(?is)<\\s*/\\s*remember\\s*>");
    private static final Pattern REMINDER_PAIR = Pattern.compile(
            "(?is)<\\s*reminder\\s*>(.*?)<\\s*/\\s*reminder\\s*>");
    private static final Pattern REMINDER_OPEN = Pattern.compile(
            "(?is)<\\s*reminder\\s*/?\\s*>");
    private static final Pattern GLOBAL_REMEMBER_PAIR = Pattern.compile(
            "(?is)<\\s*global[\\s_-]?remember\\s*>(.*?)"
                    + "<\\s*/\\s*global[\\s_-]?remember\\s*>");
    private static final Pattern GLOBAL_REMEMBER_OPEN = Pattern.compile(
            "(?is)<\\s*global[\\s_-]?remember\\s*/?\\s*>");
    private static final Pattern GLOBAL_REMEMBER_CLOSE = Pattern.compile(
            "(?is)<\\s*/\\s*global[\\s_-]?remember\\s*>");

    static class MemoryCursor {
        final long time;
        final long id;

        MemoryCursor(long time,long id) {
            this.time = time;
            this.id = id;
        }
    }

    static class RememberResult {
        final boolean requested;
        final String reply;
        final String memory;

        private RememberResult(boolean requested,String reply,String memory) {
            this.requested = requested;
            this.reply = reply;
            this.memory = memory;
        }
    }

    static class ReminderMarkerResult {
        final boolean requested;
        final String reply;
        final String time;
        final String task;
        final String target;
        final String action;
        final long id;

        private ReminderMarkerResult(boolean requested,String reply,String time,String task,
                                     String target,String action,long id) {
            this.requested = requested;
            this.reply = reply;
            this.time = time;
            this.task = task;
            this.target = target;
            this.action = action;
            this.id = id;
        }
    }

    static class GlobalRememberResult {
        final boolean requested;
        final String reply;
        final String content;

        private GlobalRememberResult(boolean requested,String reply,String content) {
            this.requested = requested;
            this.reply = reply;
            this.content = content;
        }
    }

    static class StickerMarkerResult {
        final boolean requested;
        final String reply;
        final String tags;

        StickerMarkerResult(boolean requested,String reply,String tags) {
            this.requested = requested;
            this.reply = reply;
            this.tags = tags;
        }
    }

    static class RecentImage {
        final JSONObject data;
        final long expireAt;
        volatile String hubKey = "";
        volatile String summary = "";

        RecentImage(JSONObject data,long expireAt) {
            this.data = data;
            this.expireAt = expireAt;
        }

        boolean expired() {
            return System.currentTimeMillis() > expireAt;
        }
    }

    static class RecentSticker {
        final String emotion;
        final long expireAt;

        RecentSticker(String emotion,long expireAt) {
            this.emotion = emotion;
            this.expireAt = expireAt;
        }

        boolean expired() {
            return System.currentTimeMillis() > expireAt;
        }
    }


    /**
     * 已通过准备阶段、等待路由和回复的消息
     * 合批时多条 PreparedMessage 一起送进同一次路由与生成
     */
    static class PreparedMessage {
        GroupMessageEvent event;
        long groupID;
        long selfID;
        long userID;
        String userName = "";
        String relationship = "朋友";
        String content = "";
        boolean otherRoleBot = false;
        boolean direct = false;
        boolean hasImage = false;
        String stickerEmotion = "";
        RecentImage imageContext;
        RoleplayDecisionEngine.Signals signals;
        RoleplayEmotionService.LocalEvent emotionEvent;
        String attentionTopic = "";
    }


    public RoleplayService(Plugin plugin,RoleplayConfig config,RoleplayPersona persona) {
        this.plugin = plugin;
        this.config = config;
        this.persona = persona;
        this.reminderService = new RoleplayReminderService(plugin,config,persona,this);
        this.timeService = new RoleplayTimeService(config);
        this.todoService = new RoleplayTodoService(plugin,this,reminderService,timeService);
        this.globalMemoryService = new RoleplayGlobalMemoryService(plugin,config,this);
        this.speechCorpusService = new RoleplaySpeechCorpusService(plugin,config);
        this.knowledgeService = new RoleplayKnowledgeService(plugin,config);
        this.actionService = new RoleplayActionService(plugin);
        this.memberService = new RoleplayMemberService(plugin,config);
        this.friendMemoryService = new RoleplayFriendMemoryService(plugin,config,this);
        this.emotionService = new RoleplayEmotionService(plugin,config,persona,this,memberService);
        this.blacklistService = new RoleplayBlacklistService(plugin);
        this.quietService = new RoleplayQuietService(plugin,config);
        this.replyFeedbackService = new RoleplayReplyFeedbackService(plugin,config);
        this.expressionService = new RoleplayExpressionService(plugin,config);
        this.expressionReviewService = new RoleplayExpressionReviewService(this);
        this.attentionService = new RoleplayAttentionService(plugin,config);
        this.replyWillingnessService = new RoleplayReplyWillingnessService(this);
        this.dailyReviewService = new RoleplayDailyReviewService(plugin,config,this);
        this.messageSender = new RoleplayMessageSender(this);
        this.contextService = new RoleplayContextService(this);
        this.contextCompactor = new RoleplayContextCompactor(this);
        this.conversationContextService = new RoleplayConversationContextService(this);
        this.memoryDedupService = new RoleplayMemoryDedupService(this);
        this.promptAssembler = new RoleplayPromptAssembler(this);
        this.memoryBackupService = new RoleplayMemoryBackupService(this);
        this.memoryUpdateService = new RoleplayMemoryUpdateService(this);
        this.privateChatService = new RoleplayPrivateChatService(plugin,config,this);
        this.friendRequestService = new RoleplayFriendRequestService(this);
        this.randomTickService = new RoleplayRandomTickService(plugin,config,this);
        this.activityScheduler = new RoleplayActivityScheduler(plugin,config,this);
        this.batchScheduler = new RoleplayBatchScheduler(this);
        this.pokeService = new RoleplayPokeService(this);
        this.replyExecutor = new RoleplayReplyExecutor(this);
        this.turnProcessor = new RoleplayTurnProcessor(this);
        this.textUtils = new RoleplayTextUtils(this);
        this.jsonUtils = new RoleplayJsonUtils();
        this.imageContextService = new RoleplayImageContextService(this);
        this.hubService = new RoleplayHubService(this);
        this.stickerService = new RoleplayStickerService(this);
        this.addressService = new RoleplayAddressService(this);
        this.rateLimitService = new RoleplayRateLimitService(this);
        this.memoryQueryService = new RoleplayMemoryQueryService(this);
        this.brainMemoryService = new RoleplayBrainMemoryService(this);
        this.drawNotifyService = new RoleplayDrawNotifyService(this);
        this.drawPromptService = new RoleplayDrawPromptService(this);
        this.traceService = new RoleplayTraceService(plugin,config);
        this.evalService = new RoleplayEvalService(this);
        this.replyReviewService = new RoleplayReplyReviewService(this);
        this.threadService = new RoleplayThreadService(this);
        this.pacingService = new RoleplayPacingService(this);
        this.safetyService = new RoleplaySafetyService(this);
        this.groupStatusService = new RoleplayGroupStatusService(this);
        this.messagePreprocessor = new RoleplayMessagePreprocessor(this);
        this.schemaService = new RoleplaySchemaService(this);
        this.executorService = new RoleplayExecutorService(this);
        this.replyGenerationService = new RoleplayReplyGenerationService(this);
        this.routingService = new RoleplayRoutingService(this);
        this.promptMaterialService = new RoleplayPromptMaterialService(this);
        this.contentService = new RoleplayMessageContentService(this);
        this.learnService = new RoleplayLearnService(this);
        this.modelClient = new RoleplayModelClient(this);
        this.trainService = new RoleplayTrainService(this);
        this.skillRegistry = new RoleplaySkillRegistry(this);
        this.toolMemoryService = new RoleplayToolMemoryService(plugin,config);
        this.toolTurnService = new RoleplayToolTurnService(plugin,config);
        this.slangLexicon = new RoleplaySlangLexicon(this);
        this.memeService = new RoleplayMemeService(this);
        this.toolLoopService = new RoleplayToolLoopService(this,config);
        this.router = new RoleplayRouter(plugin,this);
    }

    public void init() {
        schemaService.initSchema();
        conversationContextService.init();
        memoryDedupService.init();
        activityScheduler.init();
        contextCompactor.recoverLegacySummaries();
        reminderService.init();
        todoService.init();
        globalMemoryService.init();
        speechCorpusService.init();
        knowledgeService.init();
        emotionService.init();
        memberService.init();
        friendMemoryService.init();
        blacklistService.init();
        quietService.init();
        replyFeedbackService.init();
        expressionService.init();
        expressionReviewService.start();
        traceService.init();
        toolMemoryService.init();
        toolTurnService.init();
        slangLexicon.reload();
        threadService.init();
        privateChatService.init();
        friendMemoryService.scheduleBackfill();
        randomTickService.init();
        attentionService.reload(config);
        executorService.ensureTurnExecutor();
        executorService.ensureBackgroundExecutor();
        dailyReviewService.ensureTask();
        randomTickService.ensureTask();
        activityScheduler.ensureTask();
        if (config.replyReviewEnable) {
            plugin.getLogger().sendInfo("[角色] 回复风格、链接处理和长句分句已统一归入回复复核。");
        } else {
            plugin.getLogger().sendWarn("[角色] 回复复核已关闭；语义风格和链接清理不执行，"
                    +"本地链接过滤与最终拆分仍作为发送兜底。");
        }
    }

    public void reload(RoleplayConfig config,RoleplayPersona persona) {
        this.config = config;
        this.persona = persona;
        reminderService.reload(config,persona);
        timeService.reload(config);
        globalMemoryService.reload(config);
        speechCorpusService.reload(config);
        knowledgeService.reload(config);
        emotionService.reload(config,persona);
        memberService.reload(config);
        friendMemoryService.reload(config);
        privateChatService.reload(config);
        friendMemoryService.scheduleBackfill();
        randomTickService.reload(config);
        quietService.reload(config);
        replyFeedbackService.reload(config);
        expressionService.reload(config);
        traceService.reload(config);
        threadService.reload(config);
        attentionService.reload(config);
        dailyReviewService.reload(config);
        toolLoopService.reload(config);
        toolMemoryService.reload(config);
        toolTurnService.reload(config);
        slangLexicon.reload();
        executorService.ensureTurnExecutor();
        executorService.ensureBackgroundExecutor();
        dailyReviewService.ensureTask();
        randomTickService.ensureTask();
        activityScheduler.reload(config);
        activityScheduler.ensureTask();
    }

    /**
     * 每日复盘：按本地时区在配置的小时执行一次，把当天消息压成短期记忆。
     */
    public void triggerDailyReview(long groupID) {
        dailyReviewService.trigger(groupID);
    }



    /**
     * 把一整个回合丢到独立线程池执行；线程池不可用时退回当前线程，保证功能不丢
     */

    /**
     * 记忆整理、情绪分析、识图等后台 AI 调用统一走这个线程池
     */


    /**
     * 群消息入口
     * 同一个群先按窗口攒批，窗口结束后整批处理一次；处理期间到达的消息进入下一批，
     * 这样连续快速发言不会被逐条排队，旧消息也不会挤掉新消息。
     */
    public void handle(GroupMessageEvent event) {
        // /role learn 需要拿到完整事件（含引用段），所以在进入合批和路由之前先拦截
        if (learnService.handleCommand(event)) return;
        batchScheduler.handle(event);
    }

    boolean processGroupMessage(GroupMessageEvent event) {
        return turnProcessor.processGroupMessage(event);
    }

    /**
     * 合批回合：先把批内每条消息各自过一遍准备阶段，再只做一次路由和一次生成
     * 返回 true 表示这一轮交给了延后回合，调用方不能当成已结束
     */
    boolean processMessageBatch(long groupID,List<GroupMessageEvent> batch) {
        return turnProcessor.processMessageBatch(groupID,batch);
    }

    void wakePendingTurnForUser(long groupID,GroupMessageEvent event) {
        turnProcessor.wakePendingTurnForUser(groupID,event);
    }

    void markStickerRecognitionPending(long groupID,long userID) {
        turnProcessor.markStickerRecognitionPending(groupID,userID);
    }

    void finishStickerRecognition(long groupID,long userID,String emotion) {
        turnProcessor.finishStickerRecognition(groupID,userID,emotion);
    }

    /**
     * 消息准备：过滤、识图、记流水、规则预判、情绪观察和提醒识别
     * 返回 null 表示这条消息本轮不需要角色出声
     */


    /**
     * 合批回合：先把批内每条消息各自过一遍准备阶段，再只做一次路由和一次生成
     * 返回 true 表示这一轮交给了延后回合，调用方不能当成已结束
     */


    /**
     * 批内主消息：优先取最后一条直接点名角色的消息，没有就取最后一条
     */






    /**
     * 戳一戳事件
     * 走和群消息同一条链路，只是把当前消息换成"有人戳了你"，戳回去由执行层决定
     */
    public void handlePoke(NoticeEvent event) {
        pokeService.handlePoke(event);
    }

    /**
     * 私聊消息入口。私聊使用独立上下文，不进入群合批和群话题疲劳。
     */
    public void handlePrivate(org.moboxlab.moboxbot.API.Event.PrivateMessageEvent event) {
        privateChatService.handle(event);
    }

    public void handlePrivateInputStatus(org.moboxlab.moboxbot.API.Event.NoticeEvent event) {
        privateChatService.handleInputStatus(event);
    }

    String enrichPrivateImageContent(org.moboxlab.moboxbot.API.Event.PrivateMessageEvent event,
                                     String content) {
        return imageContextService.enrichPrivateImageContent(event,content);
    }

    JSONObject recentImageForSave(long groupID,long userID,String content) {
        RoleplayService.RecentImage recent = imageContextService.findRecentImageContext(
                groupID,userID,content);
        return recent == null ? null : recent.data;
    }

    JSONObject currentOrRecentImage(long groupID,long userID,String eventRaw,String content) {
        try {
            JSONObject raw = JSONObject.parseObject(eventRaw);
            JSONArray message = raw == null ? null : raw.getJSONArray("message");
            if (message != null) {
                for (int i = 0; i < message.size(); i++) {
                    JSONObject segment = message.getJSONObject(i);
                    if (segment == null) continue;
                    String type = segment.getString("type");
                    if ("image".equals(type) || "mface".equals(type)) {
                        return segment.getJSONObject("data");
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return recentImageForSave(groupID,userID,content);
    }

    RoleplayWorkspaceService workspaceService() {
        return toolLoopService.registry().workspace();
    }


    void executeSkillCalls(RoleplayRouteDecision decision,RoleplayReplyDraft draft,
                           long groupID,long userID,long messageID,long selfID,
                           String userName,String relationship,String content) {
        replyExecutor.executeSkillCalls(decision,draft,groupID,userID,messageID,selfID,
                userName,relationship,content);
    }

    void ensurePokeBackAction(RoleplayReplyDraft draft,RoleplayRouteDecision decision,String reply) {
        replyExecutor.ensurePokeBackAction(draft,decision,reply);
    }














    void saveMemoryState(long groupID,String shortTerm,long lastTime,long lastID) {
        memoryUpdateService.saveMemoryState(groupID,shortTerm,lastTime,lastID);
    }

    void triggerMemory(long groupID,String reason) {
        memoryUpdateService.triggerMemory(groupID,reason);
    }

    void triggerMemory(long groupID,String reason,String directMemory) {
        memoryUpdateService.triggerMemory(groupID,reason,directMemory);
    }

    void mergeLongMemoryIfNeeded(long groupID) {
        memoryUpdateService.mergeLongMemoryIfNeeded(groupID);
    }

    public boolean mergeLongMemoryNow(long groupID) {
        return memoryUpdateService.mergeLongMemoryNow(groupID);
    }

    public boolean mergeLongMemoryNow(long groupID,boolean force) {
        return memoryUpdateService.mergeLongMemoryNow(groupID,force);
    }

    public boolean mergeLongMemoryNow(long groupID,boolean force,Consumer<String> callback) {
        return memoryUpdateService.mergeLongMemoryNow(groupID,force,callback);
    }


    public String getRoleName() {
        return persona.name;
    }

    public RoleplayReminderService getReminderService() {
        return reminderService;
    }

    public RoleplayGlobalMemoryService getGlobalMemoryService() {
        return globalMemoryService;
    }

    public RoleplaySpeechCorpusService getSpeechCorpusService() {
        return speechCorpusService;
    }

    public RoleplayKnowledgeService getKnowledgeService() {
        return knowledgeService;
    }

    public RoleplayActionService getActionService() {
        return actionService;
    }

    public RoleplayEmotionService getEmotionService() {
        return emotionService;
    }

    public RoleplayBlacklistService getBlacklistService() {
        return blacklistService;
    }

    public RoleplayQuietService getQuietService() {
        return quietService;
    }

    public RoleplayReplyFeedbackService getReplyFeedbackService() {
        return replyFeedbackService;
    }

    RoleplayReplyFeedbackService replyFeedbackService() {
        return replyFeedbackService;
    }

    public RoleplayExpressionService getExpressionService() {
        return expressionService;
    }

    RoleplayExpressionReviewService expressionReviewService() {
        return expressionReviewService;
    }

    public RoleplayAttentionService getAttentionService() {
        return attentionService;
    }

    RoleplayReplyWillingnessService replyWillingnessService() {
        return replyWillingnessService;
    }

    RoleplayPromptAssembler promptAssembler() {
        return promptAssembler;
    }

    RoleplayContextService contextService() {
        return contextService;
    }

    RoleplayContextCompactor contextCompactor() {
        return contextCompactor;
    }

    RoleplayConversationContextService conversationContextService() {
        return conversationContextService;
    }

    RoleplayMemoryDedupService memoryDedupService() {
        return memoryDedupService;
    }

    RoleplayReplyExecutor replyExecutor() {
        return replyExecutor;
    }

    RoleplayHubService hubService() {
        return hubService;
    }

    RoleplayLearnService learnService() {
        return learnService;
    }

    RoleplayModelClient modelClient() {
        return modelClient;
    }

    RoleplayTrainService trainService() {
        return trainService;
    }

    RoleplayRateLimitService rateLimitService() {
        return rateLimitService;
    }

    RoleplayMemoryQueryService memoryQueryService() {
        return memoryQueryService;
    }

    RoleplayBrainMemoryService brainMemoryService() {
        return brainMemoryService;
    }

    RoleplayPrivateChatService privateChatService() {
        return privateChatService;
    }

    RoleplayFriendRequestService friendRequestService() {
        return friendRequestService;
    }

    RoleplayRandomTickService randomTickService() {
        return randomTickService;
    }

    RoleplayActivityScheduler activityScheduler() {
        return activityScheduler;
    }

    RoleplayBatchScheduler batchScheduler() {
        return batchScheduler;
    }

    RoleplayMessageSender messageSender() {
        return messageSender;
    }

    RoleplaySpeechCorpusService speechCorpusService() {
        return speechCorpusService;
    }

    RoleplayToolLoopService toolLoopService() {
        return toolLoopService;
    }

    RoleplayToolMemoryService toolMemoryService() {
        return toolMemoryService;
    }

    RoleplayToolTurnService toolTurnService() {
        return toolTurnService;
    }

    RoleplayRouter router() {
        return router;
    }

    RoleplaySkillRegistry skillRegistry() {
        return skillRegistry;
    }

    RoleplaySlangLexicon slangLexicon() {
        return slangLexicon;
    }

    RoleplayMemeService memeService() {
        return memeService;
    }

    RoleplayEmotionService emotionService() {
        return emotionService;
    }

    RoleplayPersona persona() {
        return persona;
    }

    RoleplayAttentionService attentionService() {
        return attentionService;
    }

    RoleplayKnowledgeService knowledgeService() {
        return knowledgeService;
    }

    RoleplayMemberService memberService() {
        return memberService;
    }

    RoleplayFriendMemoryService friendMemoryService() {
        return friendMemoryService;
    }

    RoleplayGlobalMemoryService globalMemoryService() {
        return globalMemoryService;
    }

    RoleplayBlacklistService blacklistService() {
        return blacklistService;
    }

    RoleplayExpressionService expressionService() {
        return expressionService;
    }

    public void blacklistAdd(long groupID,long userID,String reason,long operatorID) {
        blacklistService.add(groupID,userID,reason,operatorID);
    }

    /** 技能注册表使用的内部入口，只在本包内可见 */
    Plugin plugin() {
        return plugin;
    }

    RoleplayConfig config() {
        return config;
    }

    void rememberNow(long groupID,String content) {
        triggerMemory(groupID,"主动记忆",content);
    }

    boolean sendStickerMessage(long groupID,long userID,String tags) {
        return stickerService.sendSticker(groupID,userID,tags);
    }

    boolean sendStickerMessage(long groupID,long userID,String tags,String context) {
        return stickerService.sendSticker(groupID,userID,tags,context);
    }

    boolean isOwnerUser(long userID) {
        return contentService.isOwner(userID);
    }

    boolean ownerAuthorizedInternalWeb(long groupID,long userID,String rawUrl) {
        return contentService.ownerAuthorizedInternalWeb(groupID,userID,rawUrl);
    }

    boolean isInducedMemory(String content) {
        return contentService.isInducedMemoryRequest(content);
    }

    public int ruleLikeCount() {
        return persona.interests.size();
    }

    public RoleplayMemberService getMemberService() {
        return memberService;
    }

    public RoleplayFriendMemoryService getFriendMemoryService() {
        return friendMemoryService;
    }

    public RoleplayBrainMemoryService getBrainMemoryService() {
        return brainMemoryService;
    }

    RoleplayTodoService todoService() {
        return todoService;
    }

    RoleplayTimeService timeService() {
        return timeService;
    }

    public RoleplayDrawPromptService getDrawPromptService() {
        return drawPromptService;
    }

    public RoleplayTraceService getTraceService() {
        return traceService;
    }

    public RoleplayEvalService getEvalService() {
        return evalService;
    }

    public RoleplayReplyReviewService getReplyReviewService() {
        return replyReviewService;
    }

    public RoleplayThreadService getThreadService() {
        return threadService;
    }

    public RoleplayPacingService getPacingService() {
        return pacingService;
    }

    public RoleplaySafetyService getSafetyService() {
        return safetyService;
    }





    /**
     * 提醒资料。
     * 只有对方确实在问提醒安排时才给完整清单，其他情况只给条数与最近一条，
     * 否则模型会把待办当成聊天素材逐条念出来。
     */

    /**
     * 可选资料清单，给路由层点名用
     */

    /**
     * 角色了解的学生一句话印象。
     * 学生档案库启用时用条目 summary 覆盖人设里写死的印象，避免两处说法不一致。
     */

    /**
     * 识图用的学生外貌参考。
     * 知识库学生库启用时从条目取外貌并按总预算均分，否则退回插件自带的学生图鉴。
     */

    /**
     * 语料检索用的情绪提示。
     * 只在情绪明显偏离基线时才给：语料里 neutral 占四分之三，给成 neutral 等于没给。
     */

    /**
     * 语料检索用的场景提示，取值必须是语料里出现过的 scene
     */



    /**
     * 给路由层看的发言指向说明，避免把别人之间的对话当成对角色说的
     */



    /**
     * 产出本轮决策
     * 规则先算一遍作为兜底，路由可用时以路由结果为准，但规则保留否决权
     */


    public synchronized String backupAllMemories(String reason) {
        return memoryBackupService.backupAllMemories(reason);
    }

    public List<String> listMemoryBackups() {
        return memoryBackupService.listMemoryBackups();
    }

    public synchronized JSONObject restoreAllMemories(String fileName) {
        return memoryBackupService.restoreAllMemories(fileName);
    }


    void clearRuntimeMemoryState() {        conversationStateMap.clear();
        rateLimitService.clearRuntimeState();
        pendingMemoryMap.clear();
        memoryErrorTypeMap.clear();
        memoryMergingMap.clear();
        imageContextService.clearRuntimeState();
        stickerService.clearRuntimeState();
        turnProcessor.clearRuntimeState();
        emotionService.clearCache();
    }














    /**
     * 执行技能调用
     * 技能必须已注册且在本轮开放清单内，校验失败只丢弃该动作，不影响正文
     */














    /**
     * 这条消息是否艾特了普通群成员：既不是角色自己，也不是另一个角色机器人
     */






    /**
     * 只读的限流判断，用于在调用路由之前提前跳过，避免冷却期还去请求模型
     */


    RoleplayConversationState state(long groupID) {
        RoleplayConversationState existing = conversationStateMap.get(groupID);
        if (existing != null) return existing;
        RoleplayConversationState created = new RoleplayConversationState();
        created.groupID = groupID;
        RoleplayConversationState previous = conversationStateMap.putIfAbsent(groupID,created);
        return previous == null ? created : previous;
    }


    /**
     * 这条消息是否引用了机器人自己的上一条消息
     */

    /**
     * 被引用消息的 id，没有引用时返回 0
     */

    /**
     * 被引用消息的文本，形如「[引用 某人：内容] 」。
     * 先从本地消息流水里找，找不到再退回 OneBot 的 get_msg，两者都拿不到就返回空串。
     */

    /**
     * 本地流水里没有这条消息时，向 OneBot 要一次原文
     */


    /**
     * 这条消息是否艾特了机器人自己
     */



    /**
     * 同一用户刚被回复过：用于抑制"一句话拆成两句"时的第二次回复
     */

    /**
     * 半句识别：结尾正好是角色名字，或者停在连接词上，说明这句话还没说完
     * 命中后延后回合会等同一用户补充，并把补充内容并进同一个回合
     */


    /**
     * 上一条用户消息是不是没说完的半句
     * 当前消息已经记过流水，所以取倒数第二条
     */








    /**
     * 两条回复的最长公共子串长度。
     * “布丁明早验，咖喱七点，别糊锅”和“布丁明早验，咖喱七点，再不睡就凉了”
     * 的 bigram 相似度只有 0.5 左右，但公共片段有 10 个字，说明信息是重复的。
     */




    RememberResult extractRemember(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new RememberResult(false,"","");
        }
        Matcher pair = REMEMBER_PAIR.matcher(text);
        if (pair.find()) {
            String reply = (text.substring(0,pair.start())+text.substring(pair.end())).trim();
            String memory = cleanRememberText(pair.group(1));
            return new RememberResult(true,reply,memory);
        }
        Matcher open = REMEMBER_OPEN.matcher(text);
        if (open.find()) {
            String reply = text.substring(0,open.start()).trim();
            String memory = cleanRememberText(text.substring(open.end()));
            return new RememberResult(true,reply,memory);
        }
        Matcher close = REMEMBER_CLOSE.matcher(text);
        if (close.find()) {
            String reply = (text.substring(0,close.start())+text.substring(close.end())).trim();
            return new RememberResult(true,reply,"");
        }
        return new RememberResult(false,text.trim(),"");
    }

    ReminderMarkerResult extractReminderMarker(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new ReminderMarkerResult(false,"","","","","create",0L);
        }
        Matcher pair = REMINDER_PAIR.matcher(text);
        if (pair.find()) {
            String reply = (text.substring(0,pair.start())+text.substring(pair.end())).trim();
            return parseReminderMarker(pair.group(1),reply);
        }
        Matcher open = REMINDER_OPEN.matcher(text);
        if (open.find()) {
            String reply = text.substring(0,open.start()).trim();
            return parseReminderMarker(text.substring(open.end()),reply);
        }
        return new ReminderMarkerResult(false,text.trim(),"","","","create",0L);
    }

    GlobalRememberResult extractGlobalRemember(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new GlobalRememberResult(false,"","");
        }
        Matcher pair = GLOBAL_REMEMBER_PAIR.matcher(text);
        if (pair.find()) {
            String reply = (text.substring(0,pair.start())+text.substring(pair.end())).trim();
            return new GlobalRememberResult(true,reply,cleanGlobalRememberText(pair.group(1)));
        }
        Matcher open = GLOBAL_REMEMBER_OPEN.matcher(text);
        if (open.find()) {
            String reply = text.substring(0,open.start()).trim();
            String content = cleanGlobalRememberText(text.substring(open.end()));
            return new GlobalRememberResult(true,reply,content);
        }
        return new GlobalRememberResult(false,text.trim(),"");
    }




    private String cleanGlobalRememberText(String text) {
        if (text == null) return "";
        return GLOBAL_REMEMBER_CLOSE.matcher(text).replaceAll("").trim();
    }

    private ReminderMarkerResult parseReminderMarker(String json,String reply) {
        JSONObject parsed = parseJson(json);
        if (parsed == null) {
            return new ReminderMarkerResult(true,reply,"","","","create",0L);
        }
        return new ReminderMarkerResult(true,reply,
                safe(parsed.getString("time")),
                safe(parsed.getString("task")),
                safe(parsed.getString("target")),
                safe(parsed.getString("action")).trim().isEmpty()
                        ? "create" : safe(parsed.getString("action")).trim(),
                parsed.getLongValue("id"));
    }

    private String cleanRememberText(String text) {
        if (text == null) return "";
        return REMEMBER_CLOSE.matcher(text).replaceAll("").trim();
    }

















    /**
     * 同一用户又发言时立刻结束他的表情包等待，不再让回合干等到窗口结束
     */












    /**
     * 纯图片与表情消息的识图
     * 放到独立异步任务里跑，识图完成后合并表情语气并放行等待中的回合，
     * 这样一张图不会卡住同一群后面的消息。
     */
















    String safe(String value) {
        return textUtils.safe(value);
    }

    String shortText(String text,int maxChars) {
        return textUtils.shortText(text,maxChars);
    }

    String currentTimeText() {
        return textUtils.currentTimeText();
    }

    String formatMemoryTime(long time) {
        return textUtils.formatMemoryTime(time);
    }

    String normalizeAddressText(String content) {
        return textUtils.normalizeAddressText(content);
    }

    boolean containsRoleAlias(String text,String alias) {
        return textUtils.containsRoleAlias(text,alias);
    }

    boolean startsWithAlias(String text,String alias) {
        return textUtils.startsWithAlias(text,alias);
    }

    boolean isOwnRoleName(String name) {
        return textUtils.isOwnRoleName(name);
    }

    String joinArray(JSONArray array) {
        return textUtils.joinArray(array);
    }

    boolean containsCSVLong(String csv,long value) {
        return textUtils.containsCSVLong(csv,value);
    }

    long eventTimeMillis(GroupMessageEvent event) {
        return textUtils.eventTimeMillis(event);
    }

    String senderName(GroupMessageEvent event) {
        return textUtils.senderName(event);
    }

    String openingOf(String text) {
        return textUtils.openingOf(text);
    }

    int longestCommonRun(String left,String right) {
        return textUtils.longestCommonRun(left,right);
    }

    double similarity(String left,String right) {
        return textUtils.similarity(left,right);
    }

    Set<String> bigrams(String text) {
        return textUtils.bigrams(text);
    }

    String normalizeForSimilarity(String text) {
        return textUtils.normalizeForSimilarity(text);
    }

    boolean sleepQuietly(long millis) {
        return textUtils.sleepQuietly(millis);
    }


    JSONObject parseJson(String content) {
        return jsonUtils.parseJson(content);
    }

    JSONObject message(String role,String content) {
        return jsonUtils.message(role,content);
    }


    boolean hasImageContent(JSONArray message) {
        return imageContextService.hasImageContent(message);
    }

    boolean hasMeaningfulText(JSONArray message) {
        return imageContextService.hasMeaningfulText(message);
    }

    RecentImage rememberImageContext(GroupMessageEvent event,long groupID) {
        return imageContextService.rememberImageContext(event,groupID);
    }

    RecentImage findRecentImageContext(long groupID,long userID,String content) {
        return imageContextService.findRecentImageContext(groupID,userID,content);
    }

    JSONObject imageParams(RecentImage recent) {
        return imageContextService.imageParams(recent);
    }

    JSONObject imageData(RecentImage recent) {
        return imageContextService.imageData(recent);
    }

    String imageSummary(RecentImage recent) {
        return imageContextService.imageSummary(recent);
    }

    String recentImageKey(long groupID,long userID) {
        return imageContextService.recentImageKey(groupID,userID);
    }

    boolean shouldUnderstandImages(GroupMessageEvent event,String content,
                                   boolean direct,boolean sameUserContinuation) {
        return imageContextService.shouldUnderstandImages(event,content,direct,sameUserContinuation);
    }

    boolean hasStickerContent(JSONArray message) {
        return imageContextService.hasStickerContent(message);
    }

    void dispatchImageUnderstanding(GroupMessageEvent event,long groupID,
                                    RecentImage imageContext,boolean hasSticker,
                                    boolean direct,boolean sameUserContinuation) {
        imageContextService.dispatchImageUnderstanding(event,groupID,imageContext,hasSticker,
                direct,sameUserContinuation);
    }

    String enrichImageContent(GroupMessageEvent event,String context,long groupID,
                              RecentImage currentImage) {
        return imageContextService.enrichImageContent(event,context,groupID,currentImage);
    }


    StickerMarkerResult extractStickerMarker(String text) {
        return stickerService.extractStickerMarker(text);
    }

    String stickerPrompt() {
        return stickerService.stickerPrompt();
    }

    void rememberStickerEmotion(long groupID,long userID,String emotion) {
        stickerService.rememberStickerEmotion(groupID,userID,emotion);
    }

    String recentStickerEmotion(long groupID,long userID) {
        return stickerService.recentStickerEmotion(groupID,userID);
    }

    String mergeStickerEmotion(String content,String emotion) {
        return stickerService.mergeStickerEmotion(content,emotion);
    }


    boolean isAddressedToOtherRole(GroupMessageEvent event,String content,long selfID) {
        return addressService.isAddressedToOtherRole(event,content,selfID);
    }

    boolean isMultiRoleAddress(GroupMessageEvent event,String content,long selfID) {
        return addressService.isMultiRoleAddress(event,content,selfID);
    }

    boolean mentionsSelfRole(GroupMessageEvent event,String content,long selfID) {
        return addressService.mentionsSelfRole(event,content,selfID);
    }

    boolean mentionsOtherMember(GroupMessageEvent event,long selfID) {
        return addressService.mentionsOtherMember(event,selfID);
    }

    boolean isDirect(GroupMessageEvent event,String content,long selfID) {
        return addressService.isDirect(event,content,selfID);
    }

    boolean isQuotingSelf(GroupMessageEvent event,long selfID) {
        return addressService.isQuotingSelf(event,selfID);
    }

    long quotedMessageID(GroupMessageEvent event) {
        return addressService.quotedMessageID(event);
    }

    String quotedText(long groupID,GroupMessageEvent event) {
        return addressService.quotedText(groupID,event);
    }

    boolean isMentioningSelf(GroupMessageEvent event,long selfID) {
        return addressService.isMentioningSelf(event,selfID);
    }

    String relationshipLabel(GroupMessageEvent event,boolean otherRoleBot) {
        return addressService.relationshipLabel(event,otherRoleBot);
    }

    boolean isOtherRoleBot(GroupMessageEvent event,long selfID) {
        return addressService.isOtherRoleBot(event,selfID);
    }

    boolean isRoleParticipantUser(long userID) {
        return addressService.isRoleParticipantUser(userID);
    }


    int countMessage(long groupID) {
        return rateLimitService.countMessage(groupID);
    }

    boolean rateLimited(long groupID) {
        return rateLimitService.rateLimited(groupID);
    }

    boolean rateLimited(long groupID,boolean bypassCooldown) {
        return rateLimitService.rateLimited(groupID,bypassCooldown);
    }

    boolean canReply(long groupID) {
        return rateLimitService.canReply(groupID);
    }

    boolean canReply(long groupID,boolean bypassCooldown) {
        return rateLimitService.canReply(groupID,bypassCooldown);
    }

    boolean shouldSuppressRepeat(long groupID,String reply) {
        return rateLimitService.shouldSuppressRepeat(groupID,reply);
    }

    boolean isGroupActive(long groupID) {
        return rateLimitService.isGroupActive(groupID);
    }

    int updateOtherRoleMessageStreak(long groupID,boolean otherRoleBot) {
        return rateLimitService.updateOtherRoleMessageStreak(groupID,otherRoleBot);
    }

    boolean justRepliedToSameUser(long groupID,long userID) {
        return rateLimitService.justRepliedToSameUser(groupID,userID);
    }

    boolean isContinuation(long groupID,long userID) {
        return rateLimitService.isContinuation(groupID,userID);
    }


    String longMemoryText(long groupID,long userID,String query) {
        return memoryQueryService.longMemoryText(groupID,userID,query);
    }

    String privateMemoryText(long userID,String query,int maxChars) {
        return memoryQueryService.privateMemoryText(userID,query,maxChars);
    }

    JSONArray longMemories(long groupID) {
        return memoryQueryService.longMemories(groupID);
    }

    JSONArray longMemories(long groupID,boolean excludeBlacklisted) {
        return memoryQueryService.longMemories(groupID,excludeBlacklisted);
    }

    String shortSummary(long groupID) {
        return memoryQueryService.shortSummary(groupID);
    }

    String groupShortSummary(long groupID) {
        return memoryQueryService.groupShortSummary(groupID);
    }

    MemoryCursor memoryCursor(long groupID) {
        return memoryQueryService.memoryCursor(groupID);
    }

    int memoryCount(long groupID) {
        return memoryQueryService.memoryCount(groupID);
    }

    List<JSONObject> limitMemoryRows(List<JSONObject> rows,int maxChars) {
        return memoryQueryService.limitMemoryRows(rows,maxChars);
    }


    public JSONObject notifyDrawComplete(JSONObject params) {
        return drawNotifyService.notifyDrawComplete(params);
    }

    public JSONObject prepareDrawComplete(JSONObject params) {
        return drawNotifyService.prepareDrawComplete(params);
    }

    public JSONObject sendDrawNotify(JSONObject params) {
        return drawNotifyService.sendDrawNotify(params);
    }

    public JSONObject notifyDrawFailed(JSONObject params) {
        return drawNotifyService.notifyDrawFailed(params);
    }


    public JSONObject status(long groupID) {
        return groupStatusService.status(groupID);
    }

    public boolean isGroupEnabled(long groupID) {
        return groupStatusService.isGroupEnabled(groupID);
    }

    String contextToken(long groupID) {
        return groupStatusService.contextToken(groupID);
    }

    public void setGroupEnabled(long groupID,boolean enabled) {
        groupStatusService.setGroupEnabled(groupID,enabled);
    }

    public JSONArray listGroups() {
        return groupStatusService.listGroups();
    }

    public JSONObject memoryStats(long groupID) {
        return groupStatusService.memoryStats(groupID);
    }

    public void clearMemory(long groupID) {
        groupStatusService.clearMemory(groupID);
        conversationContextService.clear("group",groupID);
    }


    RoleplayService.PreparedMessage prepareMessage(GroupMessageEvent event) {
        return messagePreprocessor.prepareMessage(event);
    }


    void submitTurn(Runnable task) {
        executorService.submitTurn(task);
    }

    void submitBackground(Runnable task) {
        executorService.submitBackground(task);
    }

    public void shutdown() {
        toolLoopService.shutdown();
        executorService.shutdown();
    }

    void recoverToolTurns() {
        toolLoopService.recoverInterruptedTurns();
    }

    void completeRecoveredToolTurn(RoleplayToolTurnService.RecoveredTurn turn,JSONObject result) {
        if (turn == null || turn.context == null || result == null) return;
        try {
            JSONObject raw = JSONObject.parseObject(turn.context.eventRaw);
            if (raw == null) {
                plugin.getLogger().sendWarn("[工具] 群"+turn.context.groupID
                        +" 工具回合缺少原始事件，无法恢复发送");
                return;
            }
            GroupMessageEvent event = new GroupMessageEvent();
            event.setGroupID(turn.context.groupID);
            event.setMessageID(turn.context.messageID);
            event.setUserID(turn.context.userID);
            event.setRawMessage(raw.getString("raw_message"));
            event.setMessage(raw.getJSONArray("message"));
            event.setSender(raw.getJSONObject("sender"));
            event.setRaw(raw);
            RoleplayRouteDecision decision = new RoleplayRouteDecision();
            decision.reply = true;
            decision.addressed = "thread";
            decision.confidence = 1.0;
            decision.chance = 1.0;
            plugin.getLogger().sendWarn("[工具] 群"+turn.context.groupID
                    +" 正在恢复进程中断前的工具回合；可能产生一次补偿回复");
            replyExecutor.processReplyResult(event,turn.context.groupID,turn.context.selfID,
                    turn.context.userName,turn.context.relationship,turn.context.message,
                    result,decision,null);
        } catch (Exception e) {
            plugin.getLogger().sendException(e);
        }
    }


    JSONObject reply(long groupID,long userID,String userName,String content,
                     boolean otherRoleBot,String relationship,RecentImage imageContext,
                     RoleplayRouteDecision decision,long messageID,long selfID,
                     String eventRaw,boolean ownerUser) {
        return replyGenerationService.reply(groupID,userID,userName,content,otherRoleBot,
                relationship,imageContext,decision,messageID,selfID,eventRaw,ownerUser);
    }

    JSONObject reply(long groupID,long userID,String userName,String content,
                     boolean otherRoleBot,String relationship,RecentImage imageContext,
                     RoleplayRouteDecision decision,String batchText,int batchCount,
                     long messageID,long selfID,String eventRaw,boolean ownerUser) {
        return replyGenerationService.reply(groupID,userID,userName,content,otherRoleBot,
                relationship,imageContext,decision,batchText,batchCount,messageID,selfID,
                eventRaw,ownerUser);
    }


    RoleplayRouteDecision routeDecision(RoleplayDecisionEngine.Signals signals,
                                        GroupMessageEvent event,long groupID,long selfID,
                                        String content) {
        return routingService.routeDecision(signals,event,groupID,selfID,content);
    }

    String emotionRouterText(long groupID,long userID,boolean otherRoleBot) {
        return routingService.emotionRouterText(groupID,userID,otherRoleBot);
    }

    String atTargetInfo(GroupMessageEvent event) {
        return routingService.atTargetInfo(event);
    }

    String quotedTargetInfo(long groupID,GroupMessageEvent event) {
        return routingService.quotedTargetInfo(groupID,event);
    }


    String reminderPromptText(long groupID,long userID,String content) {
        return promptMaterialService.reminderPromptText(groupID,userID,content);
    }

    String materialCatalogue() {
        return promptMaterialService.materialCatalogue();
    }

    String studentBriefText() {
        return promptMaterialService.studentBriefText();
    }

    String visionReferenceText() {
        return promptMaterialService.visionReferenceText();
    }

    String speechPrompt(long groupID,long userID,String content,boolean otherRoleBot,
                        RoleplayRouteDecision decision) {
        return promptMaterialService.speechPrompt(groupID,userID,content,otherRoleBot,decision);
    }


    boolean containsIgnoredContent(JSONArray message) {
        return contentService.containsIgnoredContent(message);
    }

    String extractContent(JSONArray message) {
        return contentService.extractContent(message);
    }

    void recordMessage(GroupMessageEvent event,String content,boolean isBot) {
        contentService.recordMessage(event,content,isBot);
    }

    void recordBotMessage(long groupID,long botID,String content,long messageID) {
        contentService.recordBotMessage(groupID,botID,content,messageID);
    }

    boolean isCommand(String content) {
        return contentService.isCommand(content);
    }

    boolean isReminderNotification(String content) {
        return contentService.isReminderNotification(content);
    }

    boolean isOwner(long userID) {
        return contentService.isOwner(userID);
    }

    boolean isInducedMemoryRequest(String content) {
        return contentService.isInducedMemoryRequest(content);
    }

    boolean looksIncomplete(String content) {
        return contentService.looksIncomplete(content);
    }

    boolean completesPreviousIncomplete(long groupID,long userID) {
        return contentService.completesPreviousIncomplete(groupID,userID);
    }

    StorageService storage() {
        return plugin.getServer().getStorage();
    }

}
