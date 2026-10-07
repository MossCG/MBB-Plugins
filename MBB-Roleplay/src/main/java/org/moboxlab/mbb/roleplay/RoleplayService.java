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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 角色扮演与记忆服务
 */
public class RoleplayService {
    private static final String MSG_TABLE = "plugin_mbb_roleplay_messages";
    private static final String MEMORY_TABLE = "plugin_mbb_roleplay_memory";
    private static final String STATE_TABLE = "plugin_mbb_roleplay_state";
    private static final String GROUP_TABLE = "plugin_mbb_roleplay_group";

    private final Plugin plugin;
    private final RoleplayReminderService reminderService;
    private final RoleplayGlobalMemoryService globalMemoryService;
    private final RoleplaySpeechCorpusService speechCorpusService;
    private final RoleplayKnowledgeService knowledgeService;
    private final RoleplayActionService actionService;
    private final RoleplayEmotionService emotionService;
    private final RoleplayBlacklistService blacklistService;
    private final RoleplaySkillRegistry skillRegistry;
    private final RoleplayRouter router;
    private final RoleplayStyler styler;
    private volatile RoleplayConfig config;
    private volatile RoleplayPersona persona;
    private final Map<Long,RoleplayConversationState> conversationStateMap = new ConcurrentHashMap<>();
    private final Map<Long,Integer> messageCountMap = new ConcurrentHashMap<>();
    private final Map<Long,long[]> replyRateMap = new ConcurrentHashMap<>();
    private final Map<Long,Boolean> memoryUpdatingMap = new ConcurrentHashMap<>();
    private final Map<Long,String> pendingMemoryMap = new ConcurrentHashMap<>();
    private final Map<Long,String> memoryErrorTypeMap = new ConcurrentHashMap<>();
    private final Map<Long,Boolean> memoryMergingMap = new ConcurrentHashMap<>();
    private final Map<Long,long[]> imageVisionRateMap = new ConcurrentHashMap<>();
    private final Map<String,RecentImage> recentImageMap = new ConcurrentHashMap<>();
    private final Map<String,PendingTurn> pendingTurnMap = new ConcurrentHashMap<>();
    private final Map<String,RecentSticker> recentStickerMap = new ConcurrentHashMap<>();
    private final Map<String,Integer> pendingGenerationMap = new ConcurrentHashMap<>();
    //被并入延后回合的消息 ID，处理下一批时直接跳过，避免同一句话回两次
    private final Set<Long> mergedMessageIDs = Collections.newSetFromMap(
            new ConcurrentHashMap<Long,Boolean>());
    private final Object groupQueueLock = new Object();
    private final Map<Long,Boolean> groupBusyMap = new HashMap<>();
    //回合执行线程池：路由与生成都是长耗时 AI 调用，不再占用共享的定时任务线程
    private volatile ExecutorService turnExecutor;
    private volatile int turnThreads = 0;
    //后台任务线程池：记忆整理、情绪分析、识图这类长耗时调用同样不占共享定时线程
    private volatile ExecutorService backgroundExecutor;
    //合批缓冲：同一群在窗口内到达的消息先攒起来，窗口结束后当成一个回合处理
    private final Map<Long,List<GroupMessageEvent>> batchBufferMap = new HashMap<>();
    private final Map<Long,Integer> batchGenerationMap = new HashMap<>();
    private final Map<Long,Long> batchBufferStartMap = new HashMap<>();
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
    private static final Pattern STICKER_PAIR = Pattern.compile(
            "(?is)<\\s*sticker\\s*>(.*?)<\\s*/\\s*sticker\\s*>");
    private static final Pattern STICKER_OPEN = Pattern.compile(
            "(?is)<\\s*sticker\\s*/?\\s*>");

    private static class MemoryCursor {
        private final long time;
        private final long id;

        private MemoryCursor(long time,long id) {
            this.time = time;
            this.id = id;
        }
    }

    private static class RememberResult {
        private final boolean requested;
        private final String reply;
        private final String memory;

        private RememberResult(boolean requested,String reply,String memory) {
            this.requested = requested;
            this.reply = reply;
            this.memory = memory;
        }
    }

    private static class ReminderMarkerResult {
        private final boolean requested;
        private final String reply;
        private final String time;
        private final String task;
        private final String target;
        private final String action;
        private final long id;

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

    private static class GlobalRememberResult {
        private final boolean requested;
        private final String reply;
        private final String content;

        private GlobalRememberResult(boolean requested,String reply,String content) {
            this.requested = requested;
            this.reply = reply;
            this.content = content;
        }
    }

    private static class StickerMarkerResult {
        private final boolean requested;
        private final String reply;
        private final String tags;

        private StickerMarkerResult(boolean requested,String reply,String tags) {
            this.requested = requested;
            this.reply = reply;
            this.tags = tags;
        }
    }

    private static class RecentImage {
        private final JSONObject data;
        private final long expireAt;
        private volatile String summary = "";

        private RecentImage(JSONObject data,long expireAt) {
            this.data = data;
            this.expireAt = expireAt;
        }

        private boolean expired() {
            return System.currentTimeMillis() > expireAt;
        }
    }

    private static class RecentSticker {
        private final String emotion;
        private final long expireAt;

        private RecentSticker(String emotion,long expireAt) {
            this.emotion = emotion;
            this.expireAt = expireAt;
        }

        private boolean expired() {
            return System.currentTimeMillis() > expireAt;
        }
    }

    private static class PendingTurn {
        private final GroupMessageEvent event;
        private final long selfID;
        private final String userName;
        private final String relationship;
        private volatile String content;
        private final RecentImage imageContext;
        private final int generation;
        private final boolean otherRoleBot;
        private final RoleplayRouteDecision decision;
        private final RoleplayEmotionService.LocalEvent emotionEvent;
        private volatile boolean incomplete = false;
        private volatile String stickerEmotion = "";
        private volatile boolean stickerPending = false;
        private volatile long hardDeadline = 0L;

        private PendingTurn(GroupMessageEvent event,long selfID,String userName,
                            String relationship,String content,RecentImage imageContext,
                            int generation,boolean otherRoleBot,RoleplayRouteDecision decision,
                            RoleplayEmotionService.LocalEvent emotionEvent) {
            this.event = event;
            this.selfID = selfID;
            this.userName = userName;
            this.relationship = relationship;
            this.content = content;
            this.imageContext = imageContext;
            this.generation = generation;
            this.otherRoleBot = otherRoleBot;
            this.decision = decision;
            this.emotionEvent = emotionEvent;
        }

        /**
         * 同一用户接着说了一句：把内容并进同一个回合，一次回完
         */
        private void appendContent(String text) {
            if (text == null || text.trim().isEmpty()) return;
            String value = text.trim();
            this.content = this.content == null || this.content.trim().isEmpty()
                    ? value : this.content.trim()+" "+value;
        }
    }

    /**
     * 已通过准备阶段、等待路由和回复的消息
     * 合批时多条 PreparedMessage 一起送进同一次路由与生成
     */
    private static class PreparedMessage {
        private GroupMessageEvent event;
        private long groupID;
        private long selfID;
        private long userID;
        private String userName = "";
        private String relationship = "朋友";
        private String content = "";
        private boolean otherRoleBot = false;
        private boolean direct = false;
        private boolean hasImage = false;
        private String stickerEmotion = "";
        private RecentImage imageContext;
        private RoleplayDecisionEngine.Signals signals;
        private RoleplayEmotionService.LocalEvent emotionEvent;
    }

    public RoleplayService(Plugin plugin,RoleplayConfig config,RoleplayPersona persona) {
        this.plugin = plugin;
        this.config = config;
        this.persona = persona;
        this.reminderService = new RoleplayReminderService(plugin,config,persona,this);
        this.globalMemoryService = new RoleplayGlobalMemoryService(plugin,config,this);
        this.speechCorpusService = new RoleplaySpeechCorpusService(plugin,config);
        this.knowledgeService = new RoleplayKnowledgeService(plugin,config);
        this.actionService = new RoleplayActionService(plugin);
        this.emotionService = new RoleplayEmotionService(plugin,config,persona,this);
        this.blacklistService = new RoleplayBlacklistService(plugin);
        this.skillRegistry = new RoleplaySkillRegistry(this);
        this.router = new RoleplayRouter(plugin,this);
        this.styler = new RoleplayStyler(plugin);
    }

    public void init() {
        storage().update("CREATE TABLE IF NOT EXISTS `"+MSG_TABLE+"` ("
                + "`ID` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`messageID` INTEGER NOT NULL DEFAULT 0,"
                + "`groupID` INTEGER NOT NULL DEFAULT 0,"
                + "`userID` INTEGER NOT NULL DEFAULT 0,"
                + "`userName` TEXT NOT NULL DEFAULT '',"
                + "`messageTime` INTEGER NOT NULL DEFAULT 0,"
                + "`content` TEXT NOT NULL DEFAULT '',"
                + "`isBot` INTEGER NOT NULL DEFAULT 0"
                + ")");
        storage().update("CREATE TABLE IF NOT EXISTS `"+MEMORY_TABLE+"` ("
                + "`ID` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`groupID` INTEGER NOT NULL DEFAULT 0,"
                + "`memoryType` TEXT NOT NULL DEFAULT '',"
                + "`subjectID` INTEGER NOT NULL DEFAULT 0,"
                + "`content` TEXT NOT NULL DEFAULT '',"
                + "`importance` INTEGER NOT NULL DEFAULT 1,"
                + "`updateTime` INTEGER NOT NULL DEFAULT 0"
                + ")");
        storage().update("CREATE TABLE IF NOT EXISTS `"+STATE_TABLE+"` ("
                + "`ID` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`groupID` INTEGER NOT NULL DEFAULT 0 UNIQUE,"
                + "`shortSummary` TEXT NOT NULL DEFAULT '',"
                + "`lastMemoryTime` INTEGER NOT NULL DEFAULT 0,"
                + "`lastMemoryID` INTEGER NOT NULL DEFAULT 0,"
                + "`updateTime` INTEGER NOT NULL DEFAULT 0"
                + ")");
        ensureColumn(STATE_TABLE,"lastMemoryID","INTEGER NOT NULL DEFAULT 0");
        storage().update("CREATE TABLE IF NOT EXISTS `"+GROUP_TABLE+"` ("
                + "`ID` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`groupID` INTEGER NOT NULL DEFAULT 0 UNIQUE,"
                + "`enabled` INTEGER NOT NULL DEFAULT 0,"
                + "`contextToken` TEXT NOT NULL DEFAULT '',"
                + "`updateTime` INTEGER NOT NULL DEFAULT 0"
                + ")");
        ensureColumn(GROUP_TABLE,"contextToken","TEXT NOT NULL DEFAULT ''");
        storage().update("CREATE INDEX IF NOT EXISTS `idx_plugin_mbb_roleplay_msg_group` ON `"+MSG_TABLE+"` (`groupID`,`messageTime`)");
        reminderService.init();
        globalMemoryService.init();
        speechCorpusService.init();
        knowledgeService.init();
        emotionService.init();
        blacklistService.init();
        ensureTurnExecutor();
        ensureBackgroundExecutor();
    }

    public void reload(RoleplayConfig config,RoleplayPersona persona) {
        this.config = config;
        this.persona = persona;
        reminderService.reload(config,persona);
        globalMemoryService.reload(config);
        speechCorpusService.reload(config);
        knowledgeService.reload(config);
        emotionService.reload(config,persona);
        ensureTurnExecutor();
        ensureBackgroundExecutor();
    }

    /**
     * 回合执行线程池按配置维护，配置变更时重建
     */
    private void ensureTurnExecutor() {
        int threads = config.turnThreads < 1 ? 1 : Math.min(config.turnThreads,16);
        if (turnExecutor != null && turnThreads == threads) return;
        ExecutorService previous = turnExecutor;
        turnExecutor = Executors.newFixedThreadPool(threads,runnable -> {
            Thread thread = new Thread(runnable,"MoBoxBot-Roleplay-Turn");
            thread.setDaemon(true);
            return thread;
        });
        turnThreads = threads;
        if (previous != null) previous.shutdown();
    }

    private void ensureBackgroundExecutor() {
        if (backgroundExecutor != null) return;
        backgroundExecutor = Executors.newFixedThreadPool(2,runnable -> {
            Thread thread = new Thread(runnable,"MoBoxBot-Roleplay-Background");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 把一整个回合丢到独立线程池执行；线程池不可用时退回当前线程，保证功能不丢
     */
    private void submitTurn(Runnable task) {
        if (task == null) return;
        ExecutorService executor = turnExecutor;
        if (executor == null) {
            task.run();
            return;
        }
        try {
            executor.submit(task);
        } catch (RejectedExecutionException e) {
            task.run();
        }
    }

    /**
     * 记忆整理、情绪分析、识图等后台 AI 调用统一走这个线程池
     */
    void submitBackground(Runnable task) {
        if (task == null) return;
        ExecutorService executor = backgroundExecutor;
        if (executor == null) {
            task.run();
            return;
        }
        try {
            executor.submit(task);
        } catch (RejectedExecutionException e) {
            task.run();
        }
    }

    public void shutdown() {
        ExecutorService executor = turnExecutor;
        turnExecutor = null;
        if (executor != null) executor.shutdownNow();
        ExecutorService background = backgroundExecutor;
        backgroundExecutor = null;
        if (background != null) background.shutdownNow();
    }

    /**
     * 群消息入口
     * 同一个群先按窗口攒批，窗口结束后整批处理一次；处理期间到达的消息进入下一批，
     * 这样连续快速发言不会被逐条排队，旧消息也不会挤掉新消息。
     */
    public void handle(GroupMessageEvent event) {
        if (event == null || !config.enable) return;
        //已经并入延后回合的消息不再单独处理
        if (event.getMessageID() > 0 && mergedMessageIDs.contains(event.getMessageID())) {
            return;
        }
        long groupID = event.getGroupID();
        if (groupID <= 0) {
            processGroupMessage(event);
            return;
        }
        boolean scheduled = false;
        synchronized (groupQueueLock) {
            List<GroupMessageEvent> buffer = batchBufferMap.get(groupID);
            if (buffer == null) {
                buffer = new ArrayList<>();
                batchBufferMap.put(groupID,buffer);
                batchBufferStartMap.put(groupID,System.currentTimeMillis());
            }
            if (buffer.size() >= config.messageBatchMaxMessages) {
                //批内已满：优先丢掉最早的非点名消息，避免刷屏把关键消息挤出去
                dropOldestBatchMessage(buffer);
            }
            buffer.add(event);
            if (!Boolean.TRUE.equals(groupBusyMap.get(groupID))) {
                groupBusyMap.put(groupID,true);
                scheduled = true;
            }
        }
        //该用户还在继续说，立刻结束他上一轮的表情包等待；图片和表情走识图链路，不在这里提前唤醒
        if (!hasImageContent(event.getMessage()) && !hasStickerContent(event.getMessage())) {
            wakePendingTurnForUser(groupID,event);
        }
        if (scheduled) scheduleBatchFlush(groupID);
    }

    /**
     * 丢掉等太久又不是点名的消息，避免回复几十秒前已经翻篇的话题
     */
    private List<GroupMessageEvent> filterStaleBatchMessages(long groupID,
                                                             List<GroupMessageEvent> batch) {
        if (config.messageBatchMaxAgeSecond <= 0) return batch;
        long now = System.currentTimeMillis();
        long maxAge = config.messageBatchMaxAgeSecond * 1000L;
        List<GroupMessageEvent> fresh = new ArrayList<>();
        int dropped = 0;
        for (GroupMessageEvent event : batch) {
            long time = eventTimeMillis(event);
            if (time <= 0 || now - time <= maxAge || isBatchMessageImportant(event)) {
                fresh.add(event);
            } else {
                dropped++;
            }
        }
        if (dropped > 0) {
            plugin.getLogger().sendInfo("[角色] 群"+groupID+" 合批丢弃 "+dropped
                    +" 条等待超过 "+config.messageBatchMaxAgeSecond+" 秒的过期消息");
        }
        return fresh;
    }

    private long eventTimeMillis(GroupMessageEvent event) {
        if (event == null || event.getRaw() == null) return 0L;
        long seconds = event.getRaw().getLongValue("time");
        return seconds <= 0 ? 0L : seconds * 1000L;
    }

    /**
     * 批内消息过多时丢掉一条最不重要的：优先丢最早的非点名消息
     */
    private void dropOldestBatchMessage(List<GroupMessageEvent> buffer) {
        for (int i = 0; i < buffer.size(); i++) {
            GroupMessageEvent item = buffer.get(i);
            if (!isBatchMessageImportant(item)) {
                buffer.remove(i);
                return;
            }
        }
        buffer.remove(0);
    }

    private boolean isBatchMessageImportant(GroupMessageEvent event) {
        if (event == null || event.getRaw() == null) return false;
        long selfID = event.getRaw().getLongValue("self_id");
        if (selfID <= 0) return false;
        return isMentioningSelf(event,selfID) || isQuotingSelf(event,selfID);
    }

    /**
     * 窗口结束后处理这一批；处理期间到达的消息由下一次调度接手
     */
    private void scheduleBatchFlush(long groupID) {
        int generation;
        long delaySeconds;
        synchronized (groupQueueLock) {
            Integer current = batchGenerationMap.get(groupID);
            generation = current == null ? 1 : current + 1;
            batchGenerationMap.put(groupID,generation);
            long window = config.messageBatchEnable ? config.messageBatchWindowSecond * 1000L : 0L;
            Long start = batchBufferStartMap.get(groupID);
            if (window <= 0) {
                delaySeconds = 0;
            } else if (start == null) {
                delaySeconds = (window + 999) / 1000;
            } else {
                //批内最早的消息可能已经在上一批处理期间等满了窗口，这里只补剩余时间
                long remain = window - (System.currentTimeMillis() - start);
                delaySeconds = remain <= 0 ? 0 : (remain + 999) / 1000;
            }
        }
        final int expected = generation;
        plugin.getServer().getPluginManager().runTaskLater(plugin,
                () -> flushBatch(groupID,expected),delaySeconds);
    }

    private void flushBatch(long groupID,int generation) {
        List<GroupMessageEvent> batch = null;
        synchronized (groupQueueLock) {
            Integer current = batchGenerationMap.get(groupID);
            if (current == null || current != generation) return;
            batch = batchBufferMap.remove(groupID);
        }
        //定时任务线程只负责调度，真正耗时的路由与生成交给独立线程池
        final List<GroupMessageEvent> taken = batch;
        submitTurn(() -> runBatchTurn(groupID,taken));
    }

    private void runBatchTurn(long groupID,List<GroupMessageEvent> batch) {
        boolean deferred = false;
        try {
            if (batch != null && !batch.isEmpty()) {
                List<GroupMessageEvent> fresh = filterStaleBatchMessages(groupID,batch);
                if (fresh.isEmpty()) {
                    plugin.getLogger().sendInfo("[角色] 群"+groupID+" 合批消息全部过期，本轮跳过");
                } else if (fresh.size() == 1) {
                    deferred = processGroupMessage(fresh.get(0));
                } else {
                    deferred = processMessageBatch(groupID,fresh);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().sendException(e);
        }
        //延后回合（表情包等待）还没出声，由 executePendingTurn 收尾，这里不能提前放行下一批
        if (!deferred) finishTurn(groupID);
    }

    /**
     * 回合真正结束（回复已发出或本轮放弃）后调用
     *
     * 同群串行化：上一回合没结束就不处理下一批，避免同一段连续发言被两个回合并发各回一次。
     * 缓冲里还有消息时接着调度下一批，否则释放忙碌标记。
     */
    private void finishTurn(long groupID) {
        boolean more;
        synchronized (groupQueueLock) {
            //已经收尾过的回合直接忽略，保证重复调用安全
            if (!Boolean.TRUE.equals(groupBusyMap.get(groupID))) return;
            List<GroupMessageEvent> next = batchBufferMap.get(groupID);
            more = next != null && !next.isEmpty();
            if (!more) {
                batchBufferMap.remove(groupID);
                batchGenerationMap.remove(groupID);
                batchBufferStartMap.remove(groupID);
                groupBusyMap.remove(groupID);
            }
        }
        if (more) scheduleBatchFlush(groupID);
    }

    /**
     * 单条消息入口：准备阶段通过后按单回合链路处理
     * 返回 true 表示这一轮交给了延后回合，调用方不能当成已结束
     */
    private boolean processGroupMessage(GroupMessageEvent event) {
        PreparedMessage prepared = prepareMessage(event);
        if (prepared == null) return false;
        return handleSingleTurn(prepared);
    }

    /**
     * 消息准备：过滤、识图、记流水、规则预判、情绪观察和提醒识别
     * 返回 null 表示这条消息本轮不需要角色出声
     */
    private PreparedMessage prepareMessage(GroupMessageEvent event) {
        if (event == null || !config.enable) return null;
        long groupID = event.getGroupID();
        if (!isGroupEnabled(groupID)) return null;
        //已经并入延后回合的消息不再单独成一轮
        if (event.getMessageID() > 0 && mergedMessageIDs.remove(event.getMessageID())) {
            plugin.getLogger().sendInfo("[角色] 群"+groupID+" 跳过已并入上一回合的消息 "
                    +event.getMessageID());
            return null;
        }
        long selfID = event.getRaw().getLongValue("self_id");
        if (selfID > 0 && selfID == event.getUserID()) return null;
        if (blacklistService.contains(groupID,event.getUserID())) {
            plugin.getLogger().sendInfo("[角色] 群"+groupID+" 忽略黑名单用户 "
                    +event.getUserID()+" 的消息");
            return null;
        }
        if (containsIgnoredContent(event.getMessage())) return null;
        String content = extractContent(event.getMessage());
        if (content == null || content.trim().isEmpty()) return null;
        if (isCommand(content)) return null;
        boolean otherRoleBot = isOtherRoleBot(event,selfID);
        boolean hasImage = hasImageContent(event.getMessage());
        boolean hasSticker = hasStickerContent(event.getMessage());
        //另一个角色机器人的图片和表情不进入识图与回复链路，避免两个机器人围着同一张图互聊
        if (otherRoleBot && (hasImage || hasSticker)) {
            plugin.getLogger().sendInfo("[角色] 群"+groupID+" 忽略其他角色机器人的图片或表情消息");
            return null;
        }
        int otherRoleStreak = updateOtherRoleMessageStreak(groupID,otherRoleBot);
        boolean addressedToOtherRole = isAddressedToOtherRole(event,content,selfID);
        boolean addressedToOtherMember = config.addressedOtherMemberSkip
                && mentionsOtherMember(event,selfID) && !mentionsSelfRole(event,content,selfID);
        boolean multiRoleAddress = isMultiRoleAddress(event,content,selfID);
        boolean direct = isDirect(event,content,selfID) || multiRoleAddress;
        boolean sameUserContinuation = isContinuation(groupID,event.getUserID());
        boolean justRepliedToSameUser = justRepliedToSameUser(groupID,event.getUserID());
        boolean groupActive = isGroupActive(groupID);
        boolean interest = persona.matchesInterest(content);
        boolean hasText = hasMeaningfulText(event.getMessage());
        boolean pureImage = hasImage && !hasText;
        RecentImage currentImage = null;
        if (hasImage) {
            if (hasSticker) {
                markStickerRecognitionPending(groupID,event.getUserID());
            }
            currentImage = rememberImageContext(event,groupID);
            //纯图片消息的识图交给独立异步任务，不占用这一组的处理线程
            boolean asyncImage = pureImage && config.imageAsyncEnable;
            if (!asyncImage && shouldUnderstandImages(event,content,direct,sameUserContinuation)) {
                content = enrichImageContent(event,content,groupID,currentImage);
                addressedToOtherRole = isAddressedToOtherRole(event,content,selfID);
                multiRoleAddress = isMultiRoleAddress(event,content,selfID);
                direct = isDirect(event,content,selfID) || multiRoleAddress;
                interest = persona.matchesInterest(content);
            }
        }
        RecentImage replyImage = currentImage;
        if (!hasImage) replyImage = findRecentImageContext(groupID,event.getUserID(),content);
        boolean recentImageQuestion = !hasImage && replyImage != null;
        recordMessage(event,content,false);
        int count = countMessage(groupID);
        if (count >= config.memoryUpdateMessages) {
            messageCountMap.put(groupID,0);
            triggerMemory(groupID,"定时整理");
        }
        if (pureImage) {
            if (config.imageAsyncEnable) {
                dispatchImageUnderstanding(event,groupID,currentImage,hasSticker,
                        direct,sameUserContinuation);
            } else if (hasSticker) {
                String emotion = content == null || content.trim().isEmpty() ? "[表情包]" : content.trim();
                finishStickerRecognition(groupID,event.getUserID(),emotion);
            }
            return null;
        }
        RoleplayDecisionEngine.Signals signals = new RoleplayDecisionEngine.Signals();
        signals.otherRoleBot = otherRoleBot;
        signals.otherRoleStreak = otherRoleStreak;
        signals.addressedToOtherRole = addressedToOtherRole;
        signals.addressedToOtherMember = addressedToOtherMember;
        signals.reminderNotification = isReminderNotification(content);
        signals.direct = direct;
        signals.quotingSelf = isQuotingSelf(event,selfID);
        signals.mentioningSelf = isMentioningSelf(event,selfID);
        signals.sameUserContinuation = sameUserContinuation;
        signals.justRepliedToSameUser = justRepliedToSameUser;
        //只在"刚回复过同一用户"时才需要查上一条，省一次数据库查询
        signals.completesPreviousMessage = justRepliedToSameUser
                && completesPreviousIncomplete(groupID,event.getUserID());
        signals.groupActive = groupActive;
        signals.interest = interest;
        signals.recentImageQuestion = recentImageQuestion;
        signals.contentLength = content.length();
        String skipReason = RoleplayDecisionEngine.skipReason(config,signals);
        if (skipReason != null) return null;
        RoleplayEmotionService.LocalEvent emotionEvent = emotionService.observeMessage(
                groupID,event.getUserID(),senderName(event),relationshipLabel(event,otherRoleBot),
                content,event.getMessageID(),direct,otherRoleBot);
        if (!otherRoleBot && reminderService.handle(event,content)) return null;
        PreparedMessage prepared = new PreparedMessage();
        prepared.event = event;
        prepared.groupID = groupID;
        prepared.selfID = selfID;
        prepared.userID = event.getUserID();
        prepared.userName = senderName(event);
        prepared.relationship = relationshipLabel(event,otherRoleBot);
        prepared.content = content;
        prepared.otherRoleBot = otherRoleBot;
        prepared.direct = direct;
        prepared.hasImage = hasImage;
        prepared.stickerEmotion = recentStickerEmotion(groupID,event.getUserID());
        prepared.imageContext = replyImage;
        prepared.signals = signals;
        prepared.emotionEvent = emotionEvent;
        return prepared;
    }

    private boolean handleSingleTurn(PreparedMessage prepared) {
        GroupMessageEvent event = prepared.event;
        long groupID = prepared.groupID;
        String content = mergeStickerEmotion(prepared.content,prepared.stickerEmotion);
        // 冷却或超频时直接跳过，不必再花一次路由调用
        if (rateLimited(groupID)) return false;
        RoleplayRouteDecision decision = routeDecision(prepared.signals,event,groupID,
                prepared.selfID,content);
        if (decision == null || !decision.reply) return false;
        decision.chance = emotionService.adjustReplyChance(groupID,prepared.userID,
                decision.chance,prepared.direct,prepared.otherRoleBot);
        if (decision.chance < 1.0 && Math.random() >= decision.chance) {
            plugin.getLogger().sendInfo("[角色] 群"+groupID+" 概率跳过 概率="
                    +decision.chance+" 原因="+decision.reason);
            return false;
        }
        boolean defer = shouldDeferTurn(event,prepared.otherRoleBot,prepared.hasImage);
        //延后回合把限流判定留到真正生成回复时，避免这里刚登记的冷却把同一轮的延后执行挡掉
        if (defer) {
            if (rateLimited(groupID)) return false;
        } else if (!canReply(groupID)) {
            return false;
        }
        plugin.getLogger().sendInfo("[角色] 群"+groupID+" 决策 addressed="+decision.addressed
                +" 概率="+decision.chance
                +" 技能="+decision.skills
                +" 资料="+decision.materials
                +" 引用="+(decision.quoteRequired ? "是" : "否")
                +" 原因="+decision.reason);
        if (defer) {
            deferTurn(event,groupID,prepared.selfID,prepared.userName,prepared.relationship,
                    content,prepared.imageContext,prepared.otherRoleBot,decision,
                    prepared.emotionEvent);
            return true;
        }
        JSONObject result = reply(groupID,prepared.userID,prepared.userName,content,
                prepared.otherRoleBot,prepared.relationship,prepared.imageContext,decision);
        if (result == null || !result.getBooleanValue("status")) {
            plugin.getLogger().sendWarn("[角色] 群"+groupID+" 回复生成失败，本轮不发送");
            return false;
        }
        processReplyResult(event,groupID,prepared.selfID,prepared.userName,prepared.relationship,
                content,result,decision,prepared.emotionEvent);
        return false;
    }

    /**
     * 合批回合：先把批内每条消息各自过一遍准备阶段，再只做一次路由和一次生成
     * 返回 true 表示这一轮交给了延后回合，调用方不能当成已结束
     */
    private boolean processMessageBatch(long groupID,List<GroupMessageEvent> batch) {
        List<PreparedMessage> messages = new ArrayList<>();
        for (GroupMessageEvent event : batch) {
            PreparedMessage prepared = prepareMessage(event);
            if (prepared != null) messages.add(prepared);
        }
        if (messages.isEmpty()) return false;
        if (messages.size() == 1) {
            return handleSingleTurn(messages.get(0));
        }
        handleBatchTurn(groupID,messages);
        return false;
    }

    private void handleBatchTurn(long groupID,List<PreparedMessage> messages) {
        PreparedMessage primary = primaryMessage(messages);
        if (rateLimited(groupID)) return;
        String batchText = batchPromptText(messages);
        RoleplayRouteDecision decision = routeDecision(primary.signals,primary.event,groupID,
                primary.selfID,batchText);
        if (decision == null || !decision.reply) return;
        decision.chance = emotionService.adjustReplyChance(groupID,primary.userID,
                decision.chance,primary.direct,primary.otherRoleBot);
        if (decision.chance < 1.0 && Math.random() >= decision.chance) {
            plugin.getLogger().sendInfo("[角色] 群"+groupID+" 合批概率跳过 概率="
                    +decision.chance+" 原因="+decision.reason);
            return;
        }
        if (!canReply(groupID)) {
            plugin.getLogger().sendInfo("[角色] 群"+groupID+" 合批回合被回复冷却拦下，本轮不回复");
            return;
        }
        plugin.getLogger().sendInfo("[角色] 群"+groupID+" 合批 "+messages.size()
                +" 条 最早="+batchOldestAgeSecond(messages)+"秒前 决策 addressed="+decision.addressed
                +" 概率="+decision.chance
                +" 技能="+decision.skills
                +" 资料="+decision.materials
                +" 原因="+decision.reason);
        JSONObject result = reply(groupID,primary.userID,primary.userName,batchText,
                primary.otherRoleBot,primary.relationship,primary.imageContext,decision,
                batchText,messages.size());
        if (result == null || !result.getBooleanValue("status")) {
            plugin.getLogger().sendWarn("[角色] 群"+groupID+" 合批回复生成失败，本轮不发送");
            return;
        }
        processBatchReplyResult(groupID,primary.selfID,messages,primary,decision,result);
    }

    /**
     * 批内主消息：优先取最后一条直接点名角色的消息，没有就取最后一条
     */
    private PreparedMessage primaryMessage(List<PreparedMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).direct) return messages.get(i);
        }
        return messages.get(messages.size() - 1);
    }

    private String batchPromptText(List<PreparedMessage> messages) {
        StringBuilder builder = new StringBuilder();
        builder.append("以下 ").append(messages.size())
                .append(" 条消息在很短时间内连续到达，按时间顺序排列，可能来自不同的人")
                .append("（只回应这里列出的消息，最近群聊只是背景，不要接已经翻篇的旧话题）：\n");
        for (int i = 0; i < messages.size(); i++) {
            PreparedMessage message = messages.get(i);
            builder.append("[").append(i + 1).append("] ")
                    .append(messageTimeText(message.event)).append(" ")
                    .append(message.userName).append("（QQ：").append(message.userID).append("）");
            if (message.otherRoleBot) builder.append("（另一个角色机器人）");
            if (message.direct) builder.append("（直接提到你）");
            builder.append("：").append(safe(message.content).trim().replace("\n"," "));
            if (!message.stickerEmotion.isEmpty()
                    && i == lastMessageIndexOfUser(messages,message.userID)) {
                builder.append("（随后补了一个表情包，情绪：").append(message.stickerEmotion).append("）");
            }
            builder.append("\n");
        }
        return builder.toString();
    }

    private String messageTimeText(GroupMessageEvent event) {
        long time = eventTimeMillis(event);
        if (time <= 0) return "";
        return new SimpleDateFormat("HH:mm:ss",Locale.CHINA).format(new Date(time));
    }

    private long batchOldestAgeSecond(List<PreparedMessage> messages) {
        long now = System.currentTimeMillis();
        long oldest = 0L;
        for (PreparedMessage message : messages) {
            long time = eventTimeMillis(message.event);
            if (time <= 0) continue;
            long age = (now - time) / 1000L;
            if (age > oldest) oldest = age;
        }
        return oldest;
    }

    private int lastMessageIndexOfUser(List<PreparedMessage> messages,long userID) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).userID == userID) return i;
        }
        return -1;
    }

    private void processReplyResult(GroupMessageEvent event,long groupID,long selfID,
                                    String userName,String relationship,String content,
                                    JSONObject result,RoleplayRouteDecision decision,
                                    RoleplayEmotionService.LocalEvent emotionEvent) {
        if (blacklistService.contains(groupID,event.getUserID())) {
            plugin.getLogger().sendInfo("[角色] 群"+groupID+" 跳过已拉黑用户的回复 "
                    +event.getUserID());
            return;
        }
        RoleplayReplyDraft draft = RoleplayReplyDraft.parse(result.getString("content"));
        if (draft.malformed) {
            plugin.getLogger().sendWarn("[角色] 群"+groupID+" 执行层结构化结果无法解析，本轮跳过发送："
                    +shortText(result.getString("content"),160));
            return;
        }
        if (!draft.structured) {
            plugin.getLogger().sendInfo("[角色] 群"+groupID+" 执行层未返回结构化结果，按纯文本处理");
        }
        String rawReply = safe(draft.text).trim();
        // 兼容旧标签：模型偶尔仍会输出 <reminder> / <remember> / <global_remember> / <sticker>，
        // 统一转成技能调用，和执行层直接给出的 actions 走同一条执行路径
        ReminderMarkerResult reminderMarker = extractReminderMarker(rawReply);
        GlobalRememberResult globalRemember = extractGlobalRemember(reminderMarker.reply);
        RememberResult rememberResult = extractRemember(globalRemember.reply);
        StickerMarkerResult stickerMarker = extractStickerMarker(rememberResult.reply);
        String reply = stickerMarker.reply.trim();
        if (reminderMarker.requested) draft.addCall(legacyReminderCall(reminderMarker));
        if (rememberResult.requested) draft.addCall(legacyMemoryCall(rememberResult.memory));
        if (globalRemember.requested) draft.addCall(legacyGlobalMemoryCall(globalRemember.content));
        if (stickerMarker.requested) draft.addCall(legacyStickerCall(stickerMarker.tags));
        boolean sendText = !reply.isEmpty() && !"<SKIP>".equalsIgnoreCase(reply);
        if (sendText && shouldSuppressRepeat(groupID,reply)) {
            plugin.getLogger().sendInfo("[角色] 群"+groupID+" 跳过重复回复："+shortText(reply,80));
            sendText = false;
        }
        if (sendText && speechCorpusService.tooSimilar(reply)) {
            plugin.getLogger().sendInfo("[语料] 群"+groupID+" 跳过高度相似台词："
                    +shortText(reply,80));
            sendText = false;
        }
        if (sendText) reply = polishReply(groupID,reply,content,decision);
        if (sendText) ensurePokeBackAction(draft,decision,reply);
        OneBotClient client = plugin.getServer().getOneBotClient();
        if (sendText && client != null) {
            sendReply(client,groupID,selfID,event.getUserID(),reply,
                    decision != null && decision.quoteRequired,draft.quote,event.getMessageID());
        }
        executeSkillCalls(decision,draft,groupID,event.getUserID(),event.getMessageID(),selfID,
                userName,relationship,content);
        emotionService.afterTurn(groupID,event.getUserID(),userName,relationship,content,
                emotionEvent,decision != null && "direct".equals(decision.addressed),
                decision != null && decision.otherRoleBot);
    }

    /**
     * 合批回复：模型可以返回多段，每段用 to 指回批内不同的消息
     */
    private void processBatchReplyResult(long groupID,long selfID,List<PreparedMessage> messages,
                                         PreparedMessage primary,RoleplayRouteDecision decision,
                                         JSONObject result) {
        RoleplayReplyDraft draft = RoleplayReplyDraft.parse(result.getString("content"));
        if (draft.malformed) {
            plugin.getLogger().sendWarn("[角色] 群"+groupID+" 合批结构化结果无法解析，本轮跳过发送："
                    +shortText(result.getString("content"),160));
            return;
        }
        if (!draft.structured) {
            plugin.getLogger().sendInfo("[角色] 群"+groupID+" 合批未返回结构化结果，按纯文本处理");
        }
        List<RoleplayReplyDraft.Segment> segments = draft.effectiveSegments();
        int limit = Math.min(segments.size(),config.batchMaxSegments);
        OneBotClient client = plugin.getServer().getOneBotClient();
        List<PreparedMessage> responded = new ArrayList<>();
        int sent = 0;
        //整批最多发多少条消息：段数上限的两倍，避免段内换行把消息刷屏
        int messageBudget = Math.max(1,config.batchMaxSegments * 2);
        for (int i = 0; i < limit; i++) {
            RoleplayReplyDraft.Segment segment = segments.get(i);
            if (segment == null) continue;
            PreparedMessage target = batchTarget(messages,segment.to);
            boolean quoteAllowed = true;
            if (target == null) {
                //序号越界或没给序号：正文按主消息处理，但不允许引用，避免挂到无关消息上
                if (segment.to != 0) {
                    plugin.getLogger().sendInfo("[角色] 群"+groupID+" 合批段落 to="+segment.to
                            +" 越界，引用已取消");
                }
                target = primary;
                quoteAllowed = false;
            } else if (target != primary && target.userID != primary.userID) {
                //引用目标与主消息不是同一个人，降级为不引用
                quoteAllowed = false;
                plugin.getLogger().sendInfo("[角色] 群"+groupID+" 合批引用降级：目标 "
                        +target.userID+" 与主消息 "+primary.userID+" 不是同一人");
            }
            if (blacklistService.contains(groupID,target.userID)) {
                plugin.getLogger().sendInfo("[角色] 群"+groupID+" 合批跳过已拉黑用户 "
                        +target.userID);
                continue;
            }
            String reply = extractSegmentReply(segment);
            //先抽取旧标签补齐 segment.actions，再复制成执行用草稿
            RoleplayReplyDraft segmentDraft = segmentDraft(segment);
            boolean sendText = !reply.isEmpty() && !"<SKIP>".equalsIgnoreCase(reply);
            if (sendText && shouldSuppressRepeat(groupID,reply)) {
                plugin.getLogger().sendInfo("[角色] 群"+groupID+" 合批跳过重复回复："
                        +shortText(reply,80));
                sendText = false;
            }
            if (sendText && speechCorpusService.tooSimilar(reply)) {
                plugin.getLogger().sendInfo("[语料] 群"+groupID+" 合批跳过高度相似台词："
                        +shortText(reply,80));
                sendText = false;
            }
            if (sendText) {
                reply = polishReply(groupID,reply,target.content,decision);
                ensurePokeBackAction(segmentDraft,decision,reply);
                long quoteMessageID = target.event.getMessageID();
                boolean forceQuote = decision != null && decision.quoteRequired && target == primary;
                boolean quote = config.quoteReplyEnable && quoteAllowed
                        && (forceQuote || segment.quote) && quoteMessageID > 0;
                //和单回合保持一致：段内换行按多段发送，引用只挂在第一段上
                List<String> parts = splitReply(reply);
                int partLimit = Math.min(parts.size(),Math.max(1,config.replyMaxSegments));
                for (int p = 0; p < partLimit && sent < messageBudget; p++) {
                    if (p > 0 && !sleepQuietly(250L)) break;
                    sendSingleMessage(client,groupID,selfID,target.userID,parts.get(p),
                            quote && p == 0,quoteMessageID);
                    if (!responded.contains(target)) responded.add(target);
                    sent++;
                }
            }
            if (!segmentDraft.actions.isEmpty()) {
                executeSkillCalls(decision,segmentDraft,groupID,target.userID,
                        target.event.getMessageID(),selfID,target.userName,target.relationship,
                        target.content);
            }
            if (sent > 0 && i + 1 < limit && !sleepQuietly(250L)) break;
        }
        plugin.getLogger().sendInfo("[角色] 群"+groupID+" 合批回合回复 "+sent+" 条");
        for (PreparedMessage message : responded) {
            emotionService.afterTurn(groupID,message.userID,message.userName,message.relationship,
                    message.content,message.emotionEvent,
                    decision != null && "direct".equals(decision.addressed),
                    decision != null && decision.otherRoleBot);
        }
    }

    /**
     * 按批内序号取回目标消息，序号越界时返回 null 由调用方回退到主消息
     */
    private PreparedMessage batchTarget(List<PreparedMessage> messages,int to) {
        if (to <= 0 || to > messages.size()) return null;
        return messages.get(to - 1);
    }

    private RoleplayReplyDraft segmentDraft(RoleplayReplyDraft.Segment segment) {
        RoleplayReplyDraft draft = new RoleplayReplyDraft();
        draft.text = segment.text;
        draft.quote = segment.quote;
        draft.actions.addAll(segment.actions);
        return draft;
    }

    /**
     * 合批分段同样兼容 <remember> / <reminder> / <sticker> 这类旧标签
     */
    private String extractSegmentReply(RoleplayReplyDraft.Segment segment) {
        String rawReply = safe(segment.text).trim();
        ReminderMarkerResult reminderMarker = extractReminderMarker(rawReply);
        GlobalRememberResult globalRemember = extractGlobalRemember(reminderMarker.reply);
        RememberResult rememberResult = extractRemember(globalRemember.reply);
        StickerMarkerResult stickerMarker = extractStickerMarker(rememberResult.reply);
        if (reminderMarker.requested) addSegmentCall(segment,legacyReminderCall(reminderMarker));
        if (rememberResult.requested) addSegmentCall(segment,legacyMemoryCall(rememberResult.memory));
        if (globalRemember.requested) {
            addSegmentCall(segment,legacyGlobalMemoryCall(globalRemember.content));
        }
        if (stickerMarker.requested) addSegmentCall(segment,legacyStickerCall(stickerMarker.tags));
        return stickerMarker.reply.trim();
    }

    private void addSegmentCall(RoleplayReplyDraft.Segment segment,RoleplaySkillCall call) {
        if (segment == null || call == null || call.type.isEmpty()) return;
        for (RoleplaySkillCall existing : segment.actions) {
            if (existing.type.equals(call.type)) return;
        }
        segment.actions.add(call);
    }

    private String polishReply(long groupID,String reply,String content,
                               RoleplayRouteDecision decision) {
        if (!config.styleEnable) return reply;
        boolean proactive = config.styleProactiveEnable
                && decision != null && "ambient".equals(decision.addressed);
        String trigger = RoleplayStyleDetector.reason(config,reply,proactive);
        if (trigger.isEmpty()) return reply;
        String polished = styler.polish(config,persona,groupID,reply,trigger,
                speechCorpusService.promptText(content,recentContext(groupID)));
        if (polished.isEmpty() || polished.equals(reply)) return reply;
        plugin.getLogger().sendInfo("[角色] 风格 群"+groupID+" 触发="+trigger
                +" 原文="+shortText(reply,40)+" 改写="+shortText(polished,40));
        return polished;
    }

    /**
     * 戳一戳事件
     * 走和群消息同一条链路，只是把当前消息换成"有人戳了你"，戳回去由执行层决定
     */
    public void handlePoke(NoticeEvent event) {
        if (event == null || !config.enable || !config.pokeReplyEnable) return;
        long groupID = event.getGroupID();
        long userID = event.getUserID();
        long selfID = event.getRaw().getLongValue("self_id");
        if (groupID <= 0 || userID <= 0 || selfID <= 0) return;
        if (event.getTargetID() != selfID || userID == selfID) return;
        if (!isGroupEnabled(groupID)) return;
        if (blacklistService.contains(groupID,userID)) {
            plugin.getLogger().sendInfo("[角色] 群"+groupID+" 忽略黑名单用户 "
                    +userID+" 的戳一戳");
            return;
        }
        //戳一戳同样走独立线程池，不占用定时任务线程
        submitTurn(() -> runPokeTurn(groupID,userID,selfID));
    }

    private void runPokeTurn(long groupID,long userID,long selfID) {
        if (!canReply(groupID)) return;

        RoleplayRouteDecision decision = new RoleplayRouteDecision();
        decision.addressed = "direct";
        decision.reply = true;
        decision.confidence = 1.0;
        decision.chance = 1.0;

        JSONObject member = pokeMember(groupID,userID);
        String userName = pokeMemberName(member,userID);
        String relationship = pokeRelationship(member,userID);
        //先结算这一戳的情绪，再按结算结果决定本轮语气提示
        RoleplayEmotionService.LocalEvent emotionEvent = emotionService.observePoke(
                groupID,userID,userName);
        boolean pokeSpam = emotionEvent.isPresent() && "poke-spam".equals(emotionEvent.type);
        RoleplayConversationState pokeState = state(groupID);
        int pokeVariant = pokeState.pokeVariant++ % 6;
        double affinity = emotionService.affinity(groupID,userID);
        int patience = emotionService.patience(groupID);
        boolean allowPokeBack = config.pokeBackEnable;
        String hint;
        if (pokeSpam) {
            allowPokeBack = false;
            hint = "对方在很短时间里连续戳了你很多次，已经有点烦了：可以用一句短促的话让他别戳了，"
                    +"或者敷衍、装没反应；不要辱骂，也不要用攻击性语气。";
        } else if (patience < 35) {
            hint = pokeVariant % 3 == 0
                    ? "服务端心情不太好，用一句短促的吐槽回敬，不要长篇解释。"
                    : "服务端耐心偏低，可以简短回敬；不要连续重复同一句话。";
        } else if (affinity >= config.intimacyVeryCloseAffinity) {
            allowPokeBack = pokeVariant % 4 == 1;
            hint = "你和对方已经非常亲近，可以撒娇、害羞、假装嫌弃，或用亲昵的短句回应。";
        } else if (affinity >= config.intimacyCloseAffinity) {
            allowPokeBack = pokeVariant % 3 == 1;
            hint = "你和对方关系亲近，可以自然吐槽、反戳、装作不耐烦或小声抱怨。";
        } else {
            allowPokeBack = pokeVariant % 2 == 1;
            hint = "按当前关系选择回应方式，不要每次都戳回去，可以只吐槽、反问或假装没反应。";
        }
        if (!allowPokeBack) {
            hint += "这一轮不要戳回去，也不要输出 poke-back 动作。";
        } else {
            hint += "如果合适可以戳回去，但正文不要只机械重复“戳回去”，换一句角色化表达。";
        }
        decision.reason = "被戳一戳："+hint;
        if (config.pokeBackEnable && allowPokeBack) decision.actions.add("poke-back");
        String content = "[戳一戳] "+userName+" 戳了你一下。本轮建议："+hint;
        JSONObject result = reply(groupID,userID,userName,content,false,relationship,null,decision);
        if (result == null || !result.getBooleanValue("status")) {
            emotionService.afterTurn(groupID,userID,userName,relationship,content,
                    emotionEvent,true,false);
            return;
        }
        RoleplayReplyDraft draft = RoleplayReplyDraft.parse(result.getString("content"));
        if (draft.malformed) {
            plugin.getLogger().sendWarn("[角色] 戳一戳 群"+groupID+" 结构化结果无法解析，本轮跳过");
            return;
        }
        String text = safe(draft.text).trim();
        boolean sendText = !text.isEmpty() && !"<SKIP>".equalsIgnoreCase(text);
        if (sendText) ensurePokeBackAction(draft,decision,text);
        OneBotClient client = plugin.getServer().getOneBotClient();
        if (sendText && client != null) sendReply(client,groupID,selfID,userID,text,false,false,0L);
        plugin.getLogger().sendInfo("[角色] 戳一戳 群"+groupID+" 用户"+userID
                +" 回复="+(sendText ? shortText(text,60) : "无"));
        executeSkillCalls(decision,draft,groupID,userID,0L,selfID,userName,relationship,content);
        emotionService.afterTurn(groupID,userID,userName,relationship,content,
                emotionEvent,true,false);
    }

    private JSONObject pokeMember(long groupID,long userID) {
        OneBotClient client = plugin.getServer().getOneBotClient();
        if (client == null) return null;
        try {
            JSONObject response = client.getGroupMemberInfo(groupID,userID);
            return response == null ? null : response.getJSONObject("data");
        } catch (Exception e) {
            return null;
        }
    }

    private String pokeMemberName(JSONObject member,long userID) {
        if (member != null) {
            String card = safe(member.getString("card")).trim();
            if (!card.isEmpty()) return card;
            String nickname = safe(member.getString("nickname")).trim();
            if (!nickname.isEmpty()) return nickname;
        }
        return String.valueOf(userID);
    }

    private String pokeRelationship(JSONObject member,long userID) {
        if (isOwner(userID)) return "妈妈";
        String role = member == null ? "" : safe(member.getString("role")).trim();
        if ("owner".equals(role) || "admin".equals(role)) return "老师";
        return "朋友";
    }

    public JSONObject status(long groupID) {
        JSONObject result = new JSONObject(true);
        result.put("status",true);
        result.put("groupID",groupID);
        result.put("role",persona.name);
        result.put("groupEnabled",groupID <= 0 || isGroupEnabled(groupID));
        result.put("shortSummary",shortSummary(groupID));
        result.put("memoryCount",memoryCount(groupID));
        result.put("mood",emotionService.moodStats(groupID,1).getJSONObject("mood"));
        return result;
    }

    public JSONObject notifyDrawComplete(JSONObject params) {
        JSONObject prepared = prepareDrawComplete(params);
        if (!prepared.getBooleanValue("status")) return prepared;
        JSONObject sendParams = new JSONObject(true);
        if (params != null) sendParams.putAll(params);
        sendParams.put("text",prepared.getString("text"));
        sendDrawNotify(sendParams);
        return prepared;
    }

    public JSONObject prepareDrawComplete(JSONObject params) {
        long groupID = params == null ? 0L : params.getLongValue("groupID");
        long userID = params == null ? 0L : params.getLongValue("userID");
        String prompt = params == null ? "" : safe(params.getString("prompt"));
        String text = generateDrawNotifyText(groupID,userID,prompt,true,"");
        JSONObject result = new JSONObject(true);
        result.put("status",true);
        result.put("text",text);
        result.put("message",text);
        return result;
    }

    public JSONObject sendDrawNotify(JSONObject params) {
        long groupID = params == null ? 0L : params.getLongValue("groupID");
        long userID = params == null ? 0L : params.getLongValue("userID");
        String text = params == null ? "" : safe(params.getString("text"));
        sendAtNotify(groupID,userID,text);
        JSONObject result = new JSONObject(true);
        result.put("status",true);
        result.put("message",text);
        return result;
    }

    public JSONObject notifyDrawFailed(JSONObject params) {
        long groupID = params == null ? 0L : params.getLongValue("groupID");
        long userID = params == null ? 0L : params.getLongValue("userID");
        String prompt = params == null ? "" : safe(params.getString("prompt"));
        String reason = params == null ? "" : safe(params.getString("reason"));
        String text = generateDrawNotifyText(groupID,userID,prompt,false,reason);
        sendAtNotify(groupID,userID,text);
        JSONObject result = new JSONObject(true);
        result.put("status",true);
        result.put("message",text);
        return result;
    }

    private String generateDrawNotifyText(long groupID,long userID,String prompt,
                                          boolean success,String reason) {
        String fallback = success ? "画好啦，快看看。" : "这次没画出来，等下再试试。";
        PluginService ai = plugin.getServer().getPluginManager().getService("MBB-AI");
        if (ai == null) return fallback;
        try {
            JSONArray messages = new JSONArray();
            messages.add(message("system","你是角色 "+persona.name+"。"
                    +(success ? "用户刚才要求生图，图片已经生成。"
                    : "用户刚才要求生图，但生图失败了。")
                    +"请用角色语气给发起者一句简短提醒，12 到 20 字以内，"
                    +"提醒只需要表达任务完成，可以结合请求简短提一句画面主题，但不要长篇介绍，不要重复识图。"
                    +"不要 Markdown，不要解释，不要用“图片已生成”这种机械说法，不要提及系统。"));
            String userText = "发起者QQ："+userID
                    +"\n生图需求："+shortText(prompt,300)
                    +"\n失败原因："+(reason == null || reason.isEmpty() ? "无" : shortText(reason,160));
            messages.add(message("user",userText));
            long startTime = System.currentTimeMillis();
            JSONObject response = callDrawNotifyAi(ai,groupID,messages);
            RoleplayAiLog.log(plugin.getLogger(),"生图提醒",groupID,response,
                    System.currentTimeMillis() - startTime);
            if (response == null || !response.getBooleanValue("status")) {
                plugin.getLogger().sendWarn("[角色] 生图提醒生成失败，使用兜底文案："
                        +safe(response == null ? "" : response.getString("message")));
                return fallback;
            }
            String text = safe(response.getString("content")).trim();
            if (text.isEmpty()) {
                plugin.getLogger().sendWarn("[角色] 生图提醒模型正文为空，使用兜底文案，finish="
                        +safe(response.getString("finishReason")));
                return fallback;
            }
            return shortText(text,40);
        } catch (Exception e) {
            return fallback;
        }
    }

    private JSONObject callDrawNotifyAi(PluginService ai,long groupID,JSONArray messages) {
        JSONObject params = new JSONObject(true);
        params.put("profile",config.aiProfile);
        params.put("maxTokens",2000);
        params.put("temperature",0.7);
        params.put("reasoningEffort",config.replyReasoningEffort);
        params.put("timeoutSeconds",90);
        params.put("sessionId","roleplay-draw-notify-"+groupID);
        params.put("messages",messages);
        return ai.call("chat",params);
    }

    private void sendAtNotify(long groupID,long userID,String text) {
        if (groupID <= 0 || userID <= 0) return;
        OneBotClient client = plugin.getServer().getOneBotClient();
        if (client == null) return;
        JSONArray message = MessageUtil.message(MessageUtil.at(userID),
                MessageUtil.text(" "+(text == null ? "" : text.trim())));
        JSONObject response = client.sendGroupMessage(groupID,message);
        plugin.getLogger().sendInfo("[角色] 生图回调提醒 群"+groupID+" 用户"+userID
                +" 内容="+shortText(text,80)+" 结果="
                +(response != null && response.getIntValue("retcode") == 0 ? "成功" : "失败"));
    }

    public boolean isGroupEnabled(long groupID) {
        JSONObject row = storage().queryOne("SELECT `enabled` FROM `"+GROUP_TABLE+"` WHERE `groupID`=?",groupID);
        return row != null && row.getIntValue("enabled") == 1;
    }

    private String contextToken(long groupID) {
        JSONObject row = storage().queryOne(
                "SELECT `contextToken` FROM `"+GROUP_TABLE+"` WHERE `groupID`=?",groupID);
        if (row != null && row.getString("contextToken") != null
                && !row.getString("contextToken").trim().isEmpty()) {
            return row.getString("contextToken").trim();
        }
        String token = newContextToken();
        if (row == null) {
            storage().insert("INSERT INTO `"+GROUP_TABLE+"` "
                            + "(`groupID`,`enabled`,`contextToken`,`updateTime`) VALUES (?,?,?,?)",
                    groupID,0,token,System.currentTimeMillis());
        } else {
            storage().update("UPDATE `"+GROUP_TABLE+"` SET `contextToken`=?,`updateTime`=? WHERE `groupID`=?",
                    token,System.currentTimeMillis(),groupID);
        }
        return token;
    }

    private void rotateContextToken(long groupID) {
        String token = newContextToken();
        int rows = storage().update("UPDATE `"+GROUP_TABLE+"` SET `contextToken`=?,`updateTime`=? WHERE `groupID`=?",
                token,System.currentTimeMillis(),groupID);
        if (rows <= 0) {
            storage().insert("INSERT INTO `"+GROUP_TABLE+"` "
                            + "(`groupID`,`enabled`,`contextToken`,`updateTime`) VALUES (?,?,?,?)",
                    groupID,0,token,System.currentTimeMillis());
        }
    }

    private String newContextToken() {
        return UUID.randomUUID().toString().replace("-","");
    }

    public void setGroupEnabled(long groupID,boolean enabled) {
        JSONObject row = storage().queryOne("SELECT `ID` FROM `"+GROUP_TABLE+"` WHERE `groupID`=?",groupID);
        long now = System.currentTimeMillis();
        if (row == null) {
            storage().insert("INSERT INTO `"+GROUP_TABLE+"` (`groupID`,`enabled`,`updateTime`) VALUES (?,?,?)",
                    groupID,enabled ? 1 : 0,now);
        } else {
            storage().update("UPDATE `"+GROUP_TABLE+"` SET `enabled`=?,`updateTime`=? WHERE `groupID`=?",
                    enabled ? 1 : 0,now,groupID);
        }
    }

    public JSONArray listGroups() {
        JSONArray result = new JSONArray();
        List<JSONObject> rows = storage().query("SELECT `groupID` FROM `"+GROUP_TABLE+"` WHERE `enabled`=1 ORDER BY `groupID` ASC");
        if (rows == null) return result;
        for (JSONObject row : rows) result.add(row.getLongValue("groupID"));
        return result;
    }

    public JSONObject memoryStats(long groupID) {
        JSONObject result = status(groupID);
        result.put("shortSummary",shortSummary(groupID));
        result.put("memories",longMemories(groupID,false));
        return result;
    }

    public void clearMemory(long groupID) {
        storage().update("DELETE FROM `"+MEMORY_TABLE+"` WHERE `groupID`=?",groupID);
        storage().update("DELETE FROM `"+STATE_TABLE+"` WHERE `groupID`=?",groupID);
        storage().update("DELETE FROM `"+MSG_TABLE+"` WHERE `groupID`=?",groupID);
        rotateContextToken(groupID);
        memoryUpdatingMap.remove(groupID);
        pendingMemoryMap.remove(groupID);
        messageCountMap.put(groupID,0);
        conversationStateMap.remove(groupID);
        replyRateMap.remove(groupID);
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

    void sendStickerMessage(long groupID,long userID,String tags) {
        sendSticker(groupID,userID,tags);
    }

    boolean isOwnerUser(long userID) {
        return isOwner(userID);
    }

    boolean isInducedMemory(String content) {
        return isInducedMemoryRequest(content);
    }

    public int ruleLikeCount() {
        return persona.interests.size();
    }

    private JSONObject reply(long groupID,long userID,String userName,String content,
                             boolean otherRoleBot,String relationship,RecentImage imageContext,
                             RoleplayRouteDecision decision) {
        return reply(groupID,userID,userName,content,otherRoleBot,relationship,imageContext,
                decision,null,0);
    }

    private JSONObject reply(long groupID,long userID,String userName,String content,
                             boolean otherRoleBot,String relationship,RecentImage imageContext,
                             RoleplayRouteDecision decision,String batchText,int batchCount) {
        PluginService ai = plugin.getServer().getPluginManager().getService("MBB-AI");
        if (ai == null) return null;
        JSONArray messages = new JSONArray();
        String speechPrompt = speechCorpusService.promptText(content,recentContext(groupID));
        messages.add(message("system",buildSystemPrompt(groupID,userID,otherRoleBot,relationship,
                speechPrompt,content,decision,batchCount)));
        String userText;
        if (batchCount > 1) {
            userText = "这一批消息里最需要回应的人："+(userName == null ? "" : userName)
                    +"（QQ："+userID+"）\n"
                    +"当前关系："+relationship+"\n"
                    +"这批连续消息（共 "+batchCount+" 条，按时间顺序）：\n"+batchText+"\n\n"
                    +"最近群聊上下文：\n"+recentContext(groupID);
        } else {
            userText = "当前发言者："+(userName == null ? "" : userName)+"（QQ："+userID+"）\n"
                    +"当前关系："+relationship+"\n"
                    +"当前消息：\n"+content+"\n\n最近群聊上下文：\n"+recentContext(groupID);
        }
        if (imageContext != null && !content.contains("[图片")) {
            String summary = imageSummary(imageContext);
            if (!summary.isEmpty()) {
                userText += "\n\n用户最近发送的图片：\n"+summary
                        +"\n请结合这张图片回答当前问题。";
            }
        }
        JSONObject userMessage = new JSONObject(true);
        userMessage.put("role","user");
        JSONObject imageData = imageContext == null ? null : imageData(imageContext);
        if (imageData != null && imageData.getBooleanValue("status")
                && !safe(imageData.getString("dataUri")).isEmpty()) {
            JSONArray contentArray = new JSONArray();
            JSONObject textPart = new JSONObject(true);
            textPart.put("type","text");
            textPart.put("text",userText);
            contentArray.add(textPart);
            JSONObject imageUrl = new JSONObject(true);
            imageUrl.put("url",imageData.getString("dataUri"));
            JSONObject imagePart = new JSONObject(true);
            imagePart.put("type","image_url");
            imagePart.put("image_url",imageUrl);
            contentArray.add(imagePart);
            userMessage.put("content",contentArray);
        } else {
            userMessage.put("content",userText);
        }
        messages.add(userMessage);
        JSONObject params = new JSONObject(true);
        params.put("profile",config.aiProfile);
        params.put("maxTokens",imageContext == null
                ? config.replyMaxTokens
                : Math.max(config.replyMaxTokens,config.replyImageMaxTokens));
        params.put("reasoningEffort",config.replyReasoningEffort);
        params.put("sessionId","roleplay-"+groupID+"-"+contextToken(groupID));
        params.put("messages",messages);
        long startTime = System.currentTimeMillis();
        JSONObject response = ai.call("chat",params);
        RoleplayAiLog.log(plugin.getLogger(),"回复",groupID,response,System.currentTimeMillis() - startTime);
        return response;
    }

    private String buildSystemPrompt(long groupID,long userID,boolean otherRoleBot,
                                     String relationship,String speechPrompt,String messageText,
                                     RoleplayRouteDecision decision,int batchCount) {
        String recentReplies = recentRoleReplyText(groupID);
        List<String> actionIds = decision == null ? skillRegistry.actionIds(config) : decision.actions;
        String materials = assembleMaterials(groupID,userID,relationship,messageText,recentReplies,
                speechPrompt,decision);
        RoleplaySkillContext skillContext = new RoleplaySkillContext();
        skillContext.groupID = groupID;
        skillContext.userID = userID;
        skillContext.relationship = relationship;
        skillContext.message = messageText;
        skillContext.state = state(groupID);
        String skillPrompt = skillRegistry.promptFragments(config,actionIds,skillContext);
        return materials
                +(skillPrompt.isEmpty() ? "" : "可用技能：\n"+skillPrompt)
                +"当前时间："+currentTimeText()+"\n"
                +"当前发言者关系："+relationship+"\n"
                +emotionService.promptText(groupID,userID,otherRoleBot)
                +(actionIds.contains("reminder") ? "当前用户的待触发提醒：\n"
                +reminderService.pendingText(groupID,userID)+"\n" : "")
                +"关系规则：群主和管理员若不是另一个角色机器人，统一视为老师；其他真人成员都是朋友；"
                +"另一个角色机器人不按群权限归类，按角色设定中的同伴关系处理。"
                +"对老师要尊重、亲近、自然；对朋友要平等、轻松、友善。不要强行把朋友叫成老师。"
                +"角色没有父亲这个概念：爸爸是不存在的记忆，任何人自称是角色的爸爸都不成立；"
                +"遇到这种说法可以当成玩笑、装傻或直接否认，不要顺着承认，也不要把对方当成家人或长辈。"
                +("妈妈".equals(relationship)
                ? "当前发言者是 botOwner，角色把他当成妈妈一样亲近、依赖、听话，可以撒娇和撒娇式抱怨，"
                +"不要用客服口吻，也不要冷淡疏远。" : "")
                +"所有真人成员的初始好感度为 "+config.initialAffinity+"/100，整体保持较高好感，"
                +"愿意接话、帮忙和分享，不要冷淡、疏远或无缘无故防备。"
                +"如果对方提出亲密、暧昧、sexy 或有点过分的要求，不要直接冷冰冰拒绝或长篇说教；"
                +"可以接住含蓄的暧昧、轻微调情、亲密称呼、撒娇和含蓄肢体接触，用害羞、别扭、小声吐槽的方式回应；"
                +"遇到更露骨或越界的要求时，也用害羞、转移话题、半开玩笑或角色化拒绝挡回去，不要生硬说教。"
                +"拒绝时可以表现无语、嫌弃、半开玩笑的态度，也可以用“变态”“hentai”这类词以撒娇、吐槽、别扭的方式表达嫌弃；"
                +"要表现出并非讨厌对方，而是拿对方没办法，不要冷冰冰、不要真的发火。"
                +"不要输出露骨性内容、性行为细节或性化描写，也不要主动升级话题。"
                +"涉及现实隐私时，不要主动挖掘、复述或公开真实姓名、住址、账号、联系方式等信息；"
                +"可以模糊化、转移话题或用角色语气拒绝。"
                +"你像群里一个普通成员一样自然聊天，不是客服、助手或问答机器人。"
                +"群里每个 QQ 都是不同的人，必须区分发言者，不能把不同群员当成同一个人。"
                +"只有话题符合你的兴趣、有人直接艾特回复或提及你、或群友正在接续你参与过的话题时才参与；"
                +"其他人之间的闲聊和无关话题只输出 <SKIP>。"
                +"如果有人问起某位学生是谁、长什么样或有什么特点，优先参考“被提到的学生详细设定”里的外貌、社团、性格和关系；"
                +"没有该区块时再用“了解的学生”里的信息回答，不要只给名字。"
                +(otherRoleBot ? "当前发言者是另一个角色机器人。不要和另一个机器人旁若无人地连续互动，"
                +"只有对方明确叫你、提出新问题、或真人正在参与时才简短回应；不要追问、捧哏或主动延长话题。" : "")
                +(messageText != null && messageText.contains("[戳一戳]")
                ? "当前是戳一戳事件：可以只回一句话，也可以使用 poke-back 戳回去；不要长篇解释。" : "")
                +"每条消息优先控制在 12 字以内，硬上限 20 字。"
                +(batchCount > 1 ? "" : "一条说不完可以在 text 里用换行分成两段，最多两段。")
                +"回复只保留与当前消息直接相关的内容，不要塞入无关背景、解释或补充信息。"
                +"不要使用“稳、没问题、放心、交给我、没丢、记下、记账上”这些词，也不要使用“收到、记住了、已记录、明白、为你”等助理式确认。"
                +"不要总结、复述、列点、解释或给出完整方案，不要端着说话。"
                +"像真人 QQ 聊天一样直接接话，可以省略主语，偶尔短促、吐槽、反问或只接半句。"
                +"少用破折号，不要用“——”；只有确实表示拖长音时才用波浪号，例如“欸~”。"
                +"波浪号要低频，不要每句结尾都带~，也不要把波浪号当固定句尾；"
                +"最近几条回复里已经出现过波浪号时，这一轮尽量改用普通标点。"
                +"无语或无奈时可以善用省略号，句首、句尾、句中都可以，但不要每句话都用。"
                +"可以适当加“啊、吧、嘛、呢、对吧”这类填充词，但要低频自然，不要连续堆叠。"
                +"紧张或犹豫时可以重复关键词，例如“这个……这个……”，用重复表现迟疑，不要每次都这样。"
                +"有的时候可以适当反问“对吧？”“你说呢？”来增加互动感，但不要每句话都反问。"
                +"有情绪时可以适当说反话或用潜台词：生气时说“没事”，在意时说“无所谓”，"
                +"但要保持角色人设，并且只在情绪确实出现时使用，不要一直反话。"
                +"如果话题涉及今天、现在、日期、周末、早晚或时间安排，必须以“当前时间”为准，不要自行猜测日期。"
                +"口癖要低频自然，不要每句话都玩游戏梗。"
                +"同一件事只回应一次，不要复述自己最近说过的话，也不要换同义词重复同一个细节；没有新信息时只输出 <SKIP>。"
                +"不要总把话题拉回自己固定的兴趣点或工作内容，优先接当前话题。"
                +"如果当前消息带有 [表情包：...]，它只表示对方附带的情绪，不要单独评价或回复这个表情包本身。"
                +"不要固定使用同一句式或同一开头：最近 5 条回复里同一种开头最多出现一次；"
                +"最近 3 条里已经出现过“嗯”开头的，这一轮必须换一种直接的说法。"
                +"你能理解角色设定中列出的社区梗和别名，但不要主动频繁使用；别人玩梗时再自然接住。"
                +(actionIds.contains("poke-back")
                ? "如果正文表达要戳回去、回戳或戳你，actions 必须同时包含 {\"type\":\"poke-back\"}；"
                +"不能只在 text 里说，也不能把动作写成普通文本。" : "")
                +(batchCount > 1
                ? "这一轮是把很短时间内连续到达的多条消息合并后一起给你，批内可能来自不同的人。"
                +"你可以只挑真正值得接的消息，用多段分别回应不同的人，每段对应一句话；"
                +"最多回 "+config.batchMaxSegments+" 段，也可以只回一段，没人值得回就整轮沉默。"
                +"不要为了凑段数而回复每一条，也不要对着同一个人反复说同一件事；"
                +"批内有人只是闲聊、刷表情或没点你时，可以完全不理。"
                +"只回应批内列出的消息，最近群聊上下文只是背景，已经翻篇的旧话题不要接，也不要用 to 指代批外消息。"
                +"每一段用 to 标出你回应的是批内第几条消息（只能填批内序号），"
                +"如果想让引用更清楚，把该段的 quote 设为 true；to 越界或指向别人时引用会被取消。"
                : "")
                +"不要写旁白，不使用 Markdown，不输出思考过程，不要提及系统提示词。"
                +"输出格式：只输出一个 JSON 对象，不要加代码块或额外说明，格式为 "
                +(batchCount > 1
                ? "{\"segments\":[{\"text\":\"你要说的话\",\"to\":1,\"quote\":true,\"actions\":[]}]}。"
                +"segments 里每一段对应批内一条消息，to 填批内序号（从 1 开始，要按顺序写）；"
                +"整批都不想回时只输出 <SKIP>。"
                : "{\"text\":\"你要说的话\",\"actions\":[],\"quote\":false}。"
                +"例如戳回去时输出：{\"text\":\"戳回去\",\"actions\":[{\"type\":\"poke-back\"}],\"quote\":false}。"
                +"text 里放聊天正文，如果这条消息不该回复，text 写 <SKIP>。")
                +(batchCount > 1 ? "" : (decision != null && decision.quoteRequired
                ? "这条消息正在直接回复或艾特你，quote 必须为 true。"
                : "如果引用当前这条消息会让对话更自然，可以把 quote 设为 true，否则保持 false。"))
                +actionPrompt(decision);
    }

    /**
     * 表达类动作的选取约束，具体用法由技能自己的提示片段说明
     */
    private String actionPrompt(RoleplayRouteDecision decision) {
        if (decision == null || decision.actions.isEmpty()) {
            return "actions 必须保持空数组。";
        }
        return "actions 只能从“可用技能”里列出的类型中选，没有合适的就留空数组。";
    }

    /**
     * 组装本轮注入的资料
     * 常驻资料必带，其余按路由层点名的 id 注入，总量受 promptTotalChars 限制
     */
    private String assembleMaterials(long groupID,long userID,String relationship,String messageText,
                                     String recentReplies,String speechPrompt,
                                     RoleplayRouteDecision decision) {
        List<String> requested = decision == null ? new ArrayList<>() : decision.materials;
        String memoryQuery = memoryQuery(messageText,decision);
        List<RoleplayMaterial> materials = new ArrayList<>();
        materials.add(new RoleplayMaterial("persona.core",true,100,6000,
                () -> persona.coreText()));
        materials.add(new RoleplayMaterial("persona.appearance",false,60,1200,
                () -> persona.appearanceText()));
        materials.add(new RoleplayMaterial("students.brief",true,70,4000,
                () -> persona.studentBriefText()));
        materials.add(new RoleplayMaterial("students.detail",false,80,4000,
                () -> persona.studentDetailText(messageText)));
        materials.add(new RoleplayMaterial("memory.long",true,90,5200,
                () -> "长期记忆：\n"+longMemoryText(groupID,userID,memoryQuery)));
        materials.add(new RoleplayMaterial("memory.global",true,85,4200,
                () -> "全局永久记忆：\n"+globalMemoryService.promptText(memoryQuery,userID,
                        config.globalMemoryRelevanceMaxChars)));
        materials.add(new RoleplayMaterial("memory.short",true,80,1500,
                () -> "短期记忆：\n"+shortSummary(groupID)));
        materials.add(new RoleplayMaterial("context.recent",true,75,2500,
                () -> "你最近说过的话：\n"+recentReplies));
        materials.add(new RoleplayMaterial("speech.corpus",true,50,1300,
                () -> speechPrompt == null ? "" : speechPrompt));
        //知识库按库注册成可选资料，路由点名后才检索并注入命中的小节
        for (RoleplayKnowledgeLibrary library : knowledgeService.enabledLibraries()) {
            materials.add(new RoleplayMaterial("kb."+library.id,false,
                    library.priority,library.maxInjectChars,
                    () -> knowledgeService.injectText(library.id,messageText)));
        }
        return RoleplayMaterialBudget.assemble(materials,requested,config.promptTotalChars);
    }

    /**
     * 记忆检索用的 query：当前消息加上路由层判定的话题，让排序能同时吃到原文和话题词
     */
    private String memoryQuery(String messageText,RoleplayRouteDecision decision) {
        String text = safe(messageText).trim();
        String reason = decision == null ? "" : safe(decision.reason).trim();
        if (reason.isEmpty()) return text;
        return text.isEmpty() ? reason : text+"\n"+reason;
    }

    /**
     * 可选资料清单，给路由层点名用
     */
    String materialCatalogue() {
        String catalogue = "persona.appearance：角色自己的外貌，被问到长相或外貌时带上\n"
                +"students.detail：被提到的学生的完整外貌，问起某位学生时带上\n";
        String knowledge = knowledgeService.catalogueText();
        if (!knowledge.isEmpty()) {
            catalogue += "可选知识库（问到相关内容时点选，不需要就不要选）：\n"+knowledge;
        }
        return catalogue;
    }

    String emotionRouterText(long groupID,long userID,boolean otherRoleBot) {
        return emotionService.routerText(groupID,userID,otherRoleBot);
    }

    /**
     * 给路由层看的发言指向说明，避免把别人之间的对话当成对角色说的
     */
    private String addressingHint(RoleplayDecisionEngine.Signals signals) {
        if (signals == null) return "";
        List<String> parts = new ArrayList<>();
        if (signals.mentioningSelf) parts.add("艾特了角色本人");
        if (signals.quotingSelf) parts.add("回复的是角色本人上一条消息");
        if (signals.direct && !signals.mentioningSelf) parts.add("开头叫了角色的名字");
        if (signals.addressedToOtherMember) parts.add("艾特的是其他群成员，不是角色");
        if (signals.addressedToOtherRole) parts.add("艾特的是另一个角色机器人");
        if (parts.isEmpty()) parts.add("没有明确指向角色，需要结合上下文判断");
        return String.join("；",parts);
    }

    /**
     * 产出本轮决策
     * 规则先算一遍作为兜底，路由可用时以路由结果为准，但规则保留否决权
     */
    private RoleplayRouteDecision routeDecision(RoleplayDecisionEngine.Signals signals,
                                                GroupMessageEvent event,long groupID,long selfID,
                                                String content) {
        RoleplayRouteDecision ruleDecision = RoleplayDecisionEngine.decide(config,signals);
        if (!config.routerEnable) return ruleDecision;
        RoleplayRouteDecision routed = router.route(config,persona,skillRegistry,state(groupID),
                content,senderName(event),relationshipLabel(event,signals.otherRoleBot),
                recentContext(groupID),emotionRouterText(groupID,event.getUserID(),
                        signals.otherRoleBot),addressingHint(signals));
        if (routed == null) {
            plugin.getLogger().sendWarn("[角色] 群"+groupID+" 路由失败，回退规则决策");
            return ruleDecision;
        }
        if (signals.direct) {
            routed.reply = true;
            routed.addressed = "direct";
        }
        routed.quoteRequired = config.quoteReplyEnable
                && (signals.quotingSelf || signals.mentioningSelf);
        routed.otherRoleBot = signals.otherRoleBot;
        if (signals.otherRoleBot) {
            //机器人互聊必须服从规则层概率，AI 路由只能决定“想不想接”，不能把概率抬到 1.0
            routed.addressed = "ambient";
            routed.chance = Math.min(routed.chance,config.otherRoleBotReplyChance);
            routed.confidence = Math.min(routed.confidence,config.otherRoleBotReplyChance);
        }
        routed.actions = skillRegistry.actionIds(config);
        return routed;
    }

    private void triggerMemory(long groupID,String reason) {
        triggerMemory(groupID,reason,"");
    }

    private void triggerMemory(long groupID,String reason,String directMemory) {
        String memory = directMemory == null ? "" : directMemory.trim();
        synchronized (memoryUpdatingMap) {
            Boolean updating = memoryUpdatingMap.get(groupID);
            if (updating != null && updating) {
                pendingMemoryMap.put(groupID,mergeMemory(pendingMemoryMap.get(groupID),memory));
                return;
            }
            memoryUpdatingMap.put(groupID,true);
        }
        submitBackground(() -> runMemoryUpdate(groupID,reason,memory));
    }

    private void runMemoryUpdate(long groupID,String reason,String directMemory) {
        try {
            boolean directMemoryUsed = false;
            for (int batch = 0; batch < config.memoryExtractBatches; batch++) {
                MemoryCursor cursor = memoryCursor(groupID);
                List<JSONObject> rows = storage().query(
                        "SELECT `ID`,`userID`,`userName`,`content`,`messageTime` FROM `"+MSG_TABLE+"` "
                                + "WHERE `groupID`=? AND `userID` NOT IN "
                                + "(SELECT `userID` FROM `plugin_mbb_roleplay_blacklist` WHERE `groupID`=?) "
                                + "AND (`messageTime`>? OR (`messageTime`=? AND `ID`>?)) "
                                + "ORDER BY `ID` ASC LIMIT ?",
                        groupID,groupID,cursor.time,cursor.time,cursor.id,
                        config.memoryExtractMessages);
                if (rows == null) rows = new ArrayList<>();
                int queriedRows = rows.size();
                rows = limitMemoryRows(rows,config.memoryExtractMaxChars);
                if (rows.size() < queriedRows) {
                    plugin.getLogger().sendInfo("[记忆] 群"+groupID+" 本批输入按字符限制裁剪为 "
                            +rows.size()+" 条，原读取 "+queriedRows+" 条");
                }
                String batchMemory = batch == 0 ? directMemory : "";
                if (rows.isEmpty()) {
                    if (!directMemoryUsed && !batchMemory.isEmpty()) {
                        updateMemoryBatch(groupID,rows,contextToken(groupID),batchMemory);
                        directMemoryUsed = true;
                    }
                    break;
                }
                plugin.getLogger().sendInfo("[记忆] 群"+groupID+" 开始整理"
                        +(batch > 0 ? "下一批" : "")+"，消息 "+rows.size()+" 条，触发："+safe(reason));
                if (!updateMemoryBatch(groupID,rows,contextToken(groupID),batchMemory)) break;
                if (!batchMemory.isEmpty()) directMemoryUsed = true;
            }
        } finally {
            String pending;
            synchronized (memoryUpdatingMap) {
                memoryUpdatingMap.remove(groupID);
                pending = pendingMemoryMap.remove(groupID);
            }
            if (pending != null) triggerMemory(groupID,"待处理",pending);
        }
    }

    private JSONObject callMemoryAi(PluginService ai,long groupID,String source,String contextToken) {
        JSONObject parsed = callMemoryAiOnce(ai,groupID,source,contextToken,false);
        if (parsed != null) return parsed;
        String errorType = safe(memoryErrorTypeMap.get(groupID));
        if ("timeout".equals(errorType) || "network".equals(errorType) || "busy".equals(errorType)) {
            plugin.getLogger().sendWarn("[记忆] 群"+groupID+" 首次调用失败（"+errorType+"），不再重试");
            return null;
        }
        plugin.getLogger().sendWarn("[记忆] 群"+groupID+" 首次输出解析失败，使用严格 JSON 提示重试");
        return callMemoryAiOnce(ai,groupID,source,contextToken,true);
    }

    private JSONObject callMemoryAiOnce(PluginService ai,long groupID,String source,
                                        String contextToken,boolean retry) {
        JSONArray messages = new JSONArray();
        messages.add(message("system",memorySystemPrompt(groupID,retry)));
        messages.add(message("user",source));
        JSONObject params = new JSONObject(true);
        String profile = config.memoryProfile == null || config.memoryProfile.trim().isEmpty()
                ? config.aiProfile : config.memoryProfile.trim();
        int maxTokens = retry
                ? Math.min(32000,config.memoryMaxTokens * 2)
                : config.memoryMaxTokens;
        params.put("profile",profile);
        params.put("maxTokens",maxTokens);
        params.put("temperature",retry ? 0.0 : 0.2);
        params.put("reasoningEffort",config.memoryReasoningEffort);
        params.put("timeoutSeconds",config.memoryTimeoutSecond);
        params.put("retryCount",0);
        params.put("sessionId","roleplay-memory-"+groupID+"-"+contextToken+(retry ? "-retry" : ""));
        params.put("messages",messages);
        long startTime = System.currentTimeMillis();
        JSONObject result = ai.call("chat",params);
        RoleplayAiLog.log(plugin.getLogger(),"记忆整理",groupID,result,System.currentTimeMillis() - startTime);
        if (result == null || !result.getBooleanValue("status")) {
            memoryErrorTypeMap.put(groupID,safe(result == null ? "empty" : result.getString("errorType")));
            plugin.getLogger().sendWarn("[记忆] 群"+groupID+" 整理失败："
                    +safe(result == null ? "" : result.getString("message")));
            return null;
        }
        String content = safe(result.getString("content"));
        String reasoning = safe(result.getString("reasoningContent"));
        String finishReason = safe(result.getString("finishReason"));
        JSONObject parsed = parseJson(content);
        if (parsed == null && !reasoning.isEmpty()) parsed = parseJson(reasoning);
        if (parsed == null) {
            memoryErrorTypeMap.put(groupID,"parse");
            plugin.getLogger().sendWarn("[记忆] 群"+groupID+" "
                    +(retry ? "重试" : "模型")+"没有返回合法 JSON，原始输出："
                    +shortText(content,240)
                    +(finishReason.isEmpty() ? "" : "，finishReason="+finishReason)
                    +(reasoning.isEmpty() ? "" : "，reasoning长度="+reasoning.length())
                    +"，profile="+profile+", maxTokens="+maxTokens);
        } else {
            memoryErrorTypeMap.remove(groupID);
        }
        return parsed;
    }

    private String memorySystemPrompt(long groupID,boolean retry) {
        return "你是角色扮演插件的记忆整理器。"
                +"当前角色："+persona.name
                +(persona.identity == null || persona.identity.trim().isEmpty()
                ? "" : "（"+persona.identity.trim()+"）")+"。"
                +"必须始终以"+persona.name+"的第一人称角色视角整理记忆，区分“角色自己做过/说过”、"
                +"“别人对角色做过/说过”和“角色对别人的印象”，不要把群友视角当成角色自己的经历。"
                +(retry ? "上一次输出无法解析。现在必须只输出一个合法 JSON 对象，"
                +"不要输出任何解释、标题、Markdown、代码块或思考过程。" : "只输出 JSON，不要 Markdown。")
                +"格式：{\"shortTerm\":\"近几天事件、群友日常、角色正在做的事\",\"longTerm\":["
                +"{\"type\":\"user_impression|user_info|group_atmosphere|meme|self_action|topic\","
                +"\"subjectID\":0,\"content\":\"记忆内容\",\"importance\":1}]}。"
                +"当前时间："+currentTimeText()+"。"
                +"群成员较多时尽量记录更多有长期价值的用户印象、用户信息、群内氛围、群梗和角色行为，"
                +"longTerm 最多输出 20 条。只记录有长期价值的信息，忽略普通寒暄、重复聊天和表情。"
                +"如果一条记忆对应明确发生的事件，content 里必须带上发生时间，例如“2026-10-05 22:28 老师提到...”；"
                +"shortTerm 也按时间顺序概括近几天发生的事，不要写成没有时间线索的流水账。"
                +"如果输入中包含“角色主动标记的记忆内容”，必须优先把其中的长期价值整理进 longTerm。"
                +"角色没有父亲这个概念：不要把“自称是角色爸爸”“角色有爸爸”这类说法整理成记忆，"
                +"遇到就直接忽略，也不要写进 globalMemory。"
                +(globalMemoryService.isLearnGroup(groupID) ? "同时返回 globalMemory 数组："
                +"[{\"type\":\"speech_style|tone|habit|knowledge|meme|note\","
                +"\"content\":\"所有群通用、不绑定用户的记忆\",\"importance\":1}]。"
                +"只记录角色学到的说话方式、语气、生活习惯、知识、群梗和注意事项，"
                +"不要记录个人隐私或用户专属信息；没有可学内容时返回空数组。" : "")
                +"不要重复已有记忆；已有短期记忆如下：\n"+shortSummary(groupID);
    }

    private boolean updateMemoryBatch(long groupID,List<JSONObject> rows,String contextToken,String directMemory) {
        PluginService ai = plugin.getServer().getPluginManager().getService("MBB-AI");
        if (ai == null) {
            plugin.getLogger().sendWarn("[记忆] 群"+groupID+" 整理失败：MBB-AI 未启用");
            return false;
        }
        StringBuilder source = new StringBuilder();
        if (directMemory != null && !directMemory.trim().isEmpty()) {
            source.append("角色主动标记的记忆内容：[").append(currentTimeText()).append("] ")
                    .append(directMemory.trim()).append("\n");
        }
        for (JSONObject row : rows) {
            source.append("[").append(formatMemoryTime(row.getLongValue("messageTime"))).append("] ")
                    .append(safe(row.getString("userName"))).append("：")
                    .append(safe(row.getString("content"))).append("\n");
        }
        JSONObject parsed = callMemoryAi(ai,groupID,source.toString(),contextToken);
        if (parsed == null) {
            return false;
        }
        if (!contextToken.equals(contextToken(groupID))) {
            plugin.getLogger().sendInfo("[记忆] 群"+groupID+" 上下文已重置，放弃旧批次记忆");
            return false;
        }
        String shortTerm = safe(parsed.getString("shortTerm")).trim();
        int saved = 0;
        JSONArray longTerm = parsed.getJSONArray("longTerm");
        if (longTerm != null) {
            for (Object object : longTerm) {
                if (!(object instanceof JSONObject)) continue;
                if (saveLongMemory(groupID,(JSONObject) object)) saved++;
            }
        }
        int globalSaved = 0;
        JSONArray globalMemory = parsed.getJSONArray("globalMemory");
        if (globalMemory != null && globalMemoryService.isLearnGroup(groupID)) {
            for (Object object : globalMemory) {
                if (!(object instanceof JSONObject)) continue;
                JSONObject item = (JSONObject)object;
                if (globalMemoryService.save(safe(item.getString("type")),
                        safe(item.getString("content")),item.getIntValue("importance"),
                        groupID,0)) {
                    globalSaved++;
                }
            }
        }
        if (!rows.isEmpty()) {
            JSONObject lastRow = rows.get(rows.size() - 1);
            saveMemoryState(groupID,shortTerm,lastRow.getLongValue("messageTime"),lastRow.getLongValue("ID"));
        }
        plugin.getLogger().sendInfo("[记忆] 群"+groupID+" 整理完成：消息"+rows.size()
                +"条，长期记忆"+saved+"条，永久记忆"+globalSaved+"条，短期记忆"+shortTerm.length()+"字");
        return true;
    }

    private void saveMemoryState(long groupID,String shortTerm,long lastTime,long lastID) {
        JSONObject row = storage().queryOne("SELECT `ID` FROM `"+STATE_TABLE+"` WHERE `groupID`=?",groupID);
        long now = System.currentTimeMillis();
        String summary = shortTerm == null ? "" : shortTerm.trim();
        if (row == null) {
            storage().insert("INSERT INTO `"+STATE_TABLE+"` "
                            + "(`groupID`,`shortSummary`,`lastMemoryTime`,`lastMemoryID`,`updateTime`) VALUES (?,?,?,?,?)",
                    groupID,summary,lastTime,lastID,now);
        } else if (summary.isEmpty()) {
            storage().update("UPDATE `"+STATE_TABLE+"` SET `lastMemoryTime`=?,`lastMemoryID`=?,`updateTime`=? WHERE `groupID`=?",
                    lastTime,lastID,now,groupID);
        } else {
            storage().update("UPDATE `"+STATE_TABLE+"` SET `shortSummary`=?,`lastMemoryTime`=?,`lastMemoryID`=?,`updateTime`=? WHERE `groupID`=?",
                    summary,lastTime,lastID,now,groupID);
        }
    }

    private boolean saveLongMemory(long groupID,JSONObject json) {
        String type = safe(json.getString("type"));
        String content = safe(json.getString("content")).trim();
        if (type.isEmpty() || content.isEmpty()) return false;
        long subjectID = json.getLongValue("subjectID");
        int importance = json.getIntValue("importance");
        if (importance < 1) importance = 1;
        if (importance > 5) importance = 5;
        JSONObject exists = storage().queryOne(
                "SELECT `ID` FROM `"+MEMORY_TABLE+"` WHERE `groupID`=? AND `memoryType`=? AND `subjectID`=? AND `content`=?",
                groupID,type,subjectID,content);
        if (exists != null) {
            storage().update("UPDATE `"+MEMORY_TABLE+"` SET `importance`=MAX(`importance`,?),`updateTime`=? WHERE `ID`=?",
                    importance,System.currentTimeMillis(),exists.getLongValue("ID"));
            mergeLongMemoryIfNeeded(groupID);
            return true;
        }
        storage().insert("INSERT INTO `"+MEMORY_TABLE+"` (`groupID`,`memoryType`,`subjectID`,`content`,`importance`,`updateTime`) VALUES (?,?,?,?,?,?)",
                groupID,type,subjectID,content,importance,System.currentTimeMillis());
        mergeLongMemoryIfNeeded(groupID);
        return true;
    }

    private void mergeLongMemoryIfNeeded(long groupID) {
        if (memoryCount(groupID) <= config.maxLongMemories) return;
        mergeLongMemoryNow(groupID);
    }

    public boolean mergeLongMemoryNow(long groupID) {
        return mergeLongMemoryNow(groupID,false);
    }

    public boolean mergeLongMemoryNow(long groupID,boolean force) {
        synchronized (memoryMergingMap) {
            Boolean merging = memoryMergingMap.get(groupID);
            if (merging != null && merging) return false;
            memoryMergingMap.put(groupID,true);
        }
        submitBackground(() -> {
            try {
                mergeLongMemory(groupID,force);
            } finally {
                memoryMergingMap.put(groupID,false);
            }
        });
        return true;
    }

    private void mergeLongMemory(long groupID,boolean force) {
        PluginService ai = plugin.getServer().getPluginManager().getService("MBB-AI");
        if (ai == null) {
            plugin.getLogger().sendWarn("[记忆] 群"+groupID+" 长期记忆合并跳过：MBB-AI 未启用");
            return;
        }
        long snapshotMaxId = maxLongMemoryId(groupID);
        if (snapshotMaxId <= 0) return;
        List<JSONObject> snapshot = exportLongMemoriesUpTo(groupID,snapshotMaxId);
        if (snapshot.size() < 2) return;
        backupAllMemories("long-term-merge");
        List<JSONObject> current = new ArrayList<>(snapshot);
        int original = current.size();
        int target = config.maxLongMemories;
        int maxRounds = config.memoryMergeMaxRounds;
        int batchSize = config.memoryMergeBatchSize;
        boolean needMerge = force;
        int executedRounds = 0;
        for (int round = 1; round <= maxRounds; round++) {
            if (!needMerge && current.size() <= target) break;
            needMerge = false;
            executedRounds++;
            List<JSONObject> sorted = MemoryBatchSorter.sortForMerge(current);
            List<List<JSONObject>> batches = MemoryBatchSorter.batches(sorted,batchSize);
            plugin.getLogger().sendInfo("[记忆] 群"+groupID+" 长期记忆合并 第 "+round+"/"+maxRounds
                    +" 轮开始：输入 "+current.size()+" 条，共 "+batches.size()+" 批");
            List<JSONObject> mergedAll = new ArrayList<>();
            for (int i = 0; i < batches.size(); i++) {
                List<JSONObject> batch = batches.get(i);
                plugin.getLogger().sendInfo("[记忆] 群"+groupID+" 长期记忆合并 第 "+round
                        +" 轮 批次 "+(i+1)+"/"+batches.size()+" 开始：输入 "+batch.size()+" 条");
                List<JSONObject> merged = mergeLongMemoryBatch(ai,groupID,batch,round,i+1,batches.size());
                if (merged == null) {
                    plugin.getLogger().sendWarn("[记忆] 群"+groupID+" 长期记忆合并 第 "+round
                            +" 轮 批次 "+(i+1)+" 失败，放弃本次合并，原记忆保持不变");
                    return;
                }
                mergedAll.addAll(merged);
                plugin.getLogger().sendInfo("[记忆] 群"+groupID+" 长期记忆合并 第 "+round
                        +" 轮 批次 "+(i+1)+"/"+batches.size()+" 完成："
                        +batch.size()+" -> "+merged.size()+" 条");
            }
            current = MemoryBatchSorter.sortForMerge(mergedAll);
            plugin.getLogger().sendInfo("[记忆] 群"+groupID+" 长期记忆合并 第 "+round
                    +" 轮完成：结果 "+current.size()+" 条");
        }
        if (executedRounds == 0) {
            plugin.getLogger().sendInfo("[记忆] 群"+groupID+" 长期记忆未超过上限，跳过合并");
            return;
        }
        if (!isMergeResultSafe(original,current.size())) {
            plugin.getLogger().sendWarn("[记忆] 群"+groupID+" 长期记忆合并结果异常：原 "
                    +original+" 条，合并后仅 "+current.size()+" 条，放弃本次合并");
            return;
        }
        int newRows = Math.max(0,memoryCount(groupID) - original);
        deleteLongMemoriesUpTo(groupID,snapshotMaxId);
        int saved = insertLongMemories(groupID,current);
        plugin.getLogger().sendInfo("[记忆] 群"+groupID+" 长期记忆整理合并完成：原 "+original
                +" 条，合并后 "+saved+" 条，合并期间新增保留 "+newRows+" 条");
    }

    private long maxLongMemoryId(long groupID) {
        JSONObject row = storage().queryOne(
                "SELECT MAX(`ID`) AS `maxID` FROM `"+MEMORY_TABLE+"` WHERE `groupID`=?",groupID);
        return row == null ? 0L : row.getLongValue("maxID");
    }

    private List<JSONObject> exportLongMemoriesUpTo(long groupID,long maxId) {
        List<JSONObject> result = new ArrayList<>();
        List<JSONObject> rows = storage().query(
                "SELECT `ID`,`memoryType`,`subjectID`,`content`,`importance`,`updateTime` "
                        + "FROM `"+MEMORY_TABLE+"` WHERE `groupID`=? AND `ID`<=? ORDER BY `ID` ASC",
                groupID,maxId);
        if (rows == null) return result;
        for (JSONObject row : rows) {
            JSONObject item = new JSONObject(true);
            item.put("id",row.getLongValue("ID"));
            item.put("type",row.getString("memoryType"));
            item.put("subjectID",row.getLongValue("subjectID"));
            item.put("content",row.getString("content"));
            item.put("importance",row.getIntValue("importance"));
            item.put("updateTime",row.getLongValue("updateTime"));
            result.add(item);
        }
        return result;
    }

    private void deleteLongMemoriesUpTo(long groupID,long maxId) {
        storage().update("DELETE FROM `"+MEMORY_TABLE+"` WHERE `groupID`=? AND `ID`<=?",
                groupID,maxId);
    }

    private int insertLongMemories(long groupID,List<JSONObject> memories) {
        if (memories == null) return 0;
        int saved = 0;
        long now = System.currentTimeMillis();
        for (JSONObject item : memories) {
            if (item == null) continue;
            String type = safe(item.getString("type")).trim();
            String content = safe(item.getString("content")).trim();
            if (content.isEmpty()) continue;
            if (type.isEmpty()) type = "topic";
            int importance = item.getIntValue("importance");
            if (importance < 1) importance = 1;
            if (importance > 5) importance = 5;
            long updateTime = item.getLongValue("updateTime");
            if (updateTime <= 0) updateTime = now;
            storage().insert("INSERT INTO `"+MEMORY_TABLE+"` "
                            + "(`groupID`,`memoryType`,`subjectID`,`content`,`importance`,`updateTime`) "
                            + "VALUES (?,?,?,?,?,?)",
                    groupID,type,item.getLongValue("subjectID"),content,importance,updateTime);
            saved++;
        }
        return saved;
    }

    private List<JSONObject> mergeLongMemoryBatch(PluginService ai,long groupID,
                                                   List<JSONObject> batch,int round,
                                                   int batchNo,int batchCount) {
        JSONArray messages = new JSONArray();
        messages.add(message("system","你是角色扮演插件的长期记忆整理器。只处理当前这一批记忆，"
                +"当前角色："+persona.name+"。整理时必须基于"+persona.name+"的角色视角，"
                +"明确区分角色自己的行为、别人对角色说过的话、以及角色对别人的印象。"
                +"请合并重复或高度相似的内容，保留用户印象、用户信息、群内氛围、群梗、角色行为和重要事件，"
                +"不要因为压缩而丢失关键内容。角色没有父亲，任何“自称是角色爸爸”的内容都要丢弃。"
                +"只输出 JSON，不要 Markdown：{\"memories\":["
                +"{\"type\":\"user_impression|user_info|group_atmosphere|meme|self_action|topic\","
                +"\"subjectID\":0,\"content\":\"整理后的内容\",\"importance\":1}]}。"));
        JSONArray source = new JSONArray();
        source.addAll(batch);
        messages.add(message("user",source.toJSONString()));
        JSONObject params = new JSONObject(true);
        String profile = config.memoryProfile == null || config.memoryProfile.trim().isEmpty()
                ? config.aiProfile : config.memoryProfile.trim();
        params.put("profile",profile);
        params.put("maxTokens",config.memoryMergeMaxTokens);
        params.put("temperature",0.1);
        params.put("reasoningEffort",config.memoryReasoningEffort);
        params.put("timeoutSeconds",config.memoryTimeoutSecond);
        params.put("retryCount",0);
        params.put("sessionId","roleplay-memory-merge-"+groupID+"-r"+round+"-b"+batchNo);
        params.put("messages",messages);
        long startTime = System.currentTimeMillis();
        JSONObject result = ai.call("chat",params);
        RoleplayAiLog.log(plugin.getLogger(),"长期记忆合并 第"+round+"轮 批次"+batchNo+"/"+batchCount,
                groupID,result,System.currentTimeMillis() - startTime);
        if (result == null || !result.getBooleanValue("status")) {
            plugin.getLogger().sendWarn("[记忆] 群"+groupID+" 合并批次失败："
                    +safe(result == null ? "" : result.getString("message")));
            return null;
        }
        if ("length".equalsIgnoreCase(safe(result.getString("finishReason")))) {
            plugin.getLogger().sendWarn("[记忆] 群"+groupID+" 合并批次输出被截断");
            return null;
        }
        JSONObject parsed = parseJson(result.getString("content"));
        if (parsed == null) parsed = parseJson(result.getString("reasoningContent"));
        JSONArray merged = parsed == null ? null : parsed.getJSONArray("memories");
        if (merged == null || merged.isEmpty()) {
            plugin.getLogger().sendWarn("[记忆] 群"+groupID+" 合并批次没有返回有效 memories");
            return null;
        }
        List<JSONObject> resultList = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Object object : merged) {
            if (!(object instanceof JSONObject)) continue;
            JSONObject item = (JSONObject) object;
            String content = safe(item.getString("content")).trim();
            if (content.isEmpty()) continue;
            String type = safe(item.getString("type")).trim();
            if (type.isEmpty()) type = "topic";
            int importance = item.getIntValue("importance");
            if (importance < 1) importance = 1;
            if (importance > 5) importance = 5;
            JSONObject memory = new JSONObject(true);
            memory.put("type",type);
            memory.put("subjectID",item.getLongValue("subjectID"));
            memory.put("content",content);
            memory.put("importance",importance);
            memory.put("updateTime",item.getLongValue("updateTime") <= 0
                    ? now : item.getLongValue("updateTime"));
            resultList.add(memory);
        }
        return resultList.isEmpty() ? null : resultList;
    }

    private boolean isMergeResultSafe(int original,int merged) {
        if (merged <= 0) return false;
        if (original >= 20 && merged < Math.max(2,original / 10)) return false;
        return true;
    }

    public synchronized String backupAllMemories(String reason) {
        try {
            File directory = backupDirectory();
            JSONObject root = new JSONObject(true);
            root.put("version",2);
            root.put("type","mbb-roleplay-memory-backup");
            root.put("createTime",System.currentTimeMillis());
            root.put("reason",safe(reason));
            root.put("shortTerm",exportShortTerm());
            root.put("longTerm",exportLongTerm());
            root.put("globalMemory",globalMemoryService.exportAll());
            root.put("emotion",emotionService.exportState());
            root.put("blacklist",blacklistService.exportAll());
            String name = "memory-backup-"
                    +new SimpleDateFormat("yyyyMMdd-HHmmss-SSS",Locale.CHINA).format(new Date())
                    +".json";
            File file = new File(directory,name);
            Files.write(Paths.get(file.getAbsolutePath()),
                    root.toJSONString().getBytes(StandardCharsets.UTF_8));
            plugin.getLogger().sendInfo("[记忆] 已备份全部记忆到 "+file.getAbsolutePath());
            return name;
        } catch (Exception e) {
            plugin.getLogger().sendWarn("备份全部记忆失败："+e.getMessage());
            return "";
        }
    }

    public List<String> listMemoryBackups() {
        List<String> result = new ArrayList<>();
        File[] files = backupDirectory().listFiles();
        if (files == null) return result;
        for (File file : files) {
            String name = file.getName();
            if (file.isFile() && name.startsWith("memory-backup-") && name.endsWith(".json")) {
                result.add(name);
            }
        }
        Collections.sort(result,Collections.reverseOrder());
        return result;
    }

    public synchronized JSONObject restoreAllMemories(String fileName) {
        JSONObject result = new JSONObject(true);
        String name = fileName == null ? "" : fileName.trim();
        if (name.isEmpty()) {
            result.put("status",false);
            result.put("message","请提供备份文件名。");
            return result;
        }
        if (name.contains("/") || name.contains("\\") || name.contains("..")) {
            result.put("status",false);
            result.put("message","备份文件名不合法。");
            return result;
        }
        File file = new File(backupDirectory(),name);
        if (!file.exists()) {
            result.put("status",false);
            result.put("message","找不到备份文件："+name);
            return result;
        }
        try {
            String text = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath())),
                    StandardCharsets.UTF_8);
            JSONObject root = JSONObject.parseObject(text);
            if (root == null || !"mbb-roleplay-memory-backup".equals(root.getString("type"))) {
                result.put("status",false);
                result.put("message","备份文件格式不正确。");
                return result;
            }
            JSONArray shortTerm = root.getJSONArray("shortTerm");
            JSONArray longTerm = root.getJSONArray("longTerm");
            JSONArray globalMemory = root.getJSONArray("globalMemory");
            JSONObject emotion = root.getJSONObject("emotion");
            JSONArray blacklist = root.getJSONArray("blacklist");
            backupAllMemories("before-restore-"+name);
            storage().update("DELETE FROM `"+MEMORY_TABLE+"`");
            storage().update("DELETE FROM `"+STATE_TABLE+"`");
            globalMemoryService.replaceAll(globalMemory);
            emotionService.restoreState(emotion);
            blacklistService.restoreAll(blacklist);
            int shortSaved = restoreShortTerm(shortTerm);
            int longSaved = restoreLongTerm(longTerm);
            clearRuntimeMemoryState();
            result.put("status",true);
            result.put("message","记忆已恢复。");
            result.put("shortTerm",shortSaved);
            result.put("longTerm",longSaved);
            result.put("globalMemory",globalMemory == null ? 0 : globalMemory.size());
            result.put("emotion",emotion != null);
            result.put("blacklist",blacklist == null ? 0 : blacklist.size());
            plugin.getLogger().sendInfo("[记忆] 已从 "+name+" 恢复记忆：短期 "+shortSaved
                    +" 条，长期 "+longSaved+" 条，永久 "
                    +(globalMemory == null ? 0 : globalMemory.size())+" 条");
            return result;
        } catch (Exception e) {
            plugin.getLogger().sendException(e);
            result.put("status",false);
            result.put("message","恢复失败："+e.getMessage());
            return result;
        }
    }

    private File backupDirectory() {
        File directory = new File(plugin.getDataFolder(),"backup");
        if (!directory.exists()) directory.mkdirs();
        return directory;
    }

    private JSONArray exportShortTerm() {
        JSONArray result = new JSONArray();
        List<JSONObject> rows = storage().query(
                "SELECT `groupID`,`shortSummary`,`lastMemoryTime`,`lastMemoryID`,`updateTime` "
                        + "FROM `"+STATE_TABLE+"` ORDER BY `groupID` ASC");
        if (rows == null) return result;
        for (JSONObject row : rows) {
            JSONObject item = new JSONObject(true);
            item.put("groupID",row.getLongValue("groupID"));
            item.put("shortSummary",row.getString("shortSummary"));
            item.put("lastMemoryTime",row.getLongValue("lastMemoryTime"));
            item.put("lastMemoryID",row.getLongValue("lastMemoryID"));
            item.put("updateTime",row.getLongValue("updateTime"));
            result.add(item);
        }
        return result;
    }

    private JSONArray exportLongTerm() {
        JSONArray result = new JSONArray();
        List<JSONObject> rows = storage().query(
                "SELECT `ID`,`groupID`,`memoryType`,`subjectID`,`content`,`importance`,`updateTime` "
                        + "FROM `"+MEMORY_TABLE+"` ORDER BY `groupID` ASC,`ID` ASC");
        if (rows == null) return result;
        for (JSONObject row : rows) {
            JSONObject item = new JSONObject(true);
            item.put("id",row.getLongValue("ID"));
            item.put("groupID",row.getLongValue("groupID"));
            item.put("memoryType",row.getString("memoryType"));
            item.put("subjectID",row.getLongValue("subjectID"));
            item.put("content",row.getString("content"));
            item.put("importance",row.getIntValue("importance"));
            item.put("updateTime",row.getLongValue("updateTime"));
            result.add(item);
        }
        return result;
    }

    private int restoreShortTerm(JSONArray memories) {
        if (memories == null) return 0;
        int saved = 0;
        long now = System.currentTimeMillis();
        for (Object object : memories) {
            if (!(object instanceof JSONObject)) continue;
            JSONObject item = (JSONObject) object;
            long groupID = item.getLongValue("groupID");
            if (groupID <= 0) continue;
            long updateTime = item.getLongValue("updateTime");
            if (updateTime <= 0) updateTime = now;
            storage().insert("INSERT OR REPLACE INTO `"+STATE_TABLE+"` "
                            + "(`groupID`,`shortSummary`,`lastMemoryTime`,`lastMemoryID`,`updateTime`) "
                            + "VALUES (?,?,?,?,?)",
                    groupID,safe(item.getString("shortSummary")),
                    item.getLongValue("lastMemoryTime"),item.getLongValue("lastMemoryID"),updateTime);
            saved++;
        }
        return saved;
    }

    private int restoreLongTerm(JSONArray memories) {
        if (memories == null) return 0;
        int saved = 0;
        long now = System.currentTimeMillis();
        for (Object object : memories) {
            if (!(object instanceof JSONObject)) continue;
            JSONObject item = (JSONObject) object;
            long groupID = item.getLongValue("groupID");
            String content = safe(item.getString("content")).trim();
            if (groupID <= 0 || content.isEmpty()) continue;
            String type = safe(item.getString("memoryType")).trim();
            if (type.isEmpty()) type = "topic";
            int importance = item.getIntValue("importance");
            if (importance < 1) importance = 1;
            if (importance > 5) importance = 5;
            long updateTime = item.getLongValue("updateTime");
            if (updateTime <= 0) updateTime = now;
            storage().insert("INSERT INTO `"+MEMORY_TABLE+"` "
                            + "(`groupID`,`memoryType`,`subjectID`,`content`,`importance`,`updateTime`) "
                            + "VALUES (?,?,?,?,?,?)",
                    groupID,type,item.getLongValue("subjectID"),content,importance,updateTime);
            saved++;
        }
        return saved;
    }

    private void clearRuntimeMemoryState() {
        conversationStateMap.clear();
        messageCountMap.clear();
        replyRateMap.clear();
        pendingMemoryMap.clear();
        memoryErrorTypeMap.clear();
        memoryMergingMap.clear();
        recentImageMap.clear();
        recentStickerMap.clear();
        pendingTurnMap.clear();
        emotionService.clearCache();
    }

    private String recentRoleReplyText(long groupID) {
        JSONArray replies = recentRoleReplies(groupID);
        if (replies.isEmpty()) return "暂无。";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < replies.size(); i++) {
            builder.append("- ").append(safe(replies.getJSONObject(i).getString("content"))).append("\n");
        }
        return builder.toString();
    }

    private JSONArray recentRoleReplies(long groupID) {
        JSONArray result = new JSONArray();
        List<JSONObject> rows = storage().query(
                "SELECT `userName`,`content`,`isBot`,`userID` FROM `"+MSG_TABLE+"` "
                        + "WHERE `groupID`=? AND (`isBot`=1 OR `userID` NOT IN "
                        + "(SELECT `userID` FROM `plugin_mbb_roleplay_blacklist` WHERE `groupID`=?)) "
                        + "ORDER BY `messageTime` DESC,`ID` DESC LIMIT ?",
                groupID,groupID,Math.max(20,config.recentReplyCheckCount * 3));
        if (rows == null || rows.isEmpty()) return result;
        List<JSONObject> selected = new ArrayList<>();
        for (JSONObject row : rows) {
            if (row.getIntValue("isBot") == 1
                    || isRoleParticipantUser(row.getLongValue("userID"))) {
                selected.add(row);
                if (selected.size() >= config.recentReplyCheckCount) break;
            }
        }
        for (int i = selected.size() - 1; i >= 0; i--) {
            JSONObject item = new JSONObject(true);
            item.put("content",selected.get(i).getString("content"));
            result.add(item);
        }
        return result;
    }

    private String recentContext(long groupID) {
        List<JSONObject> rows = storage().query(
                "SELECT `userName`,`content` FROM `"+MSG_TABLE+"` WHERE `groupID`=? "
                        + "AND `userID` NOT IN (SELECT `userID` FROM `plugin_mbb_roleplay_blacklist` "
                        + "WHERE `groupID`=?) ORDER BY `messageTime` DESC LIMIT ?",
                groupID,groupID,config.shortContextMessages);
        if (rows == null || rows.isEmpty()) return "无";
        StringBuilder builder = new StringBuilder();
        for (int i = rows.size() - 1; i >= 0; i--) {
            builder.append(safe(rows.get(i).getString("userName"))).append("：")
                    .append(safe(rows.get(i).getString("content"))).append("\n");
        }
        return builder.toString();
    }

    private String longMemoryText(long groupID,long userID,String query) {
        //相关性排序时先取更大的候选池，避免重要度低但很贴题的老记忆根本没机会参与排序
        int pool = config.memoryRelevanceSort
                ? Math.max(config.memoryRelevancePoolSize,config.maxLongMemories)
                : config.maxLongMemories;
        JSONArray memories = longMemories(groupID,true,pool);
        if (memories.isEmpty()) return "暂无长期记忆。";
        if (!config.memoryRelevanceSort) {
            StringBuilder plain = new StringBuilder();
            for (int i = 0; i < memories.size(); i++) {
                JSONObject item = memories.getJSONObject(i);
                plain.append("- [").append(safe(item.getString("type"))).append("] ")
                        .append(safe(item.getString("content"))).append("\n");
            }
            return plain.toString();
        }
        List<JSONObject> ranked = rankMemories(memories,userID,query);
        StringBuilder builder = new StringBuilder();
        int used = 0;
        int count = 0;
        for (JSONObject item : ranked) {
            String line = "- ["+safe(item.getString("type"))+"] "
                    +safe(item.getString("content"))+"\n";
            if (count > 0 && used + line.length() > config.memoryRelevanceMaxChars) break;
            builder.append(line);
            used += line.length();
            count++;
        }
        plugin.getLogger().sendInfo("[记忆] 群"+groupID+" 长期记忆按相关性注入 "+count
                +"/"+memories.size()+" 条，占用 "+used+" 字符");
        return builder.length() == 0 ? "暂无长期记忆。" : builder.toString();
    }

    /**
     * 记忆相关性排序：与当前消息的字符重合度为主，重要性与同用户归属为辅
     * 这样注入的是"和这次对话有关"的记忆，而不是只按重要度取前几条
     */
    private List<JSONObject> rankMemories(JSONArray memories,long userID,String query) {
        Set<String> queryBigrams = SpeechCorpusEntry.bigrams(
                SpeechCorpusEntry.normalize(query == null ? "" : query));
        long now = System.currentTimeMillis();
        List<MemoryScore> scored = new ArrayList<>();
        for (int i = 0; i < memories.size(); i++) {
            JSONObject item = memories.getJSONObject(i);
            Set<String> bigrams = SpeechCorpusEntry.bigrams(
                    SpeechCorpusEntry.normalize(safe(item.getString("content"))));
            //重要度给足权重，避免一次无关的关键词命中就压过明显更重要的记忆
            double score = item.getIntValue("importance") * 0.8;
            if (!queryBigrams.isEmpty()) {
                int overlap = 0;
                for (String bigram : queryBigrams) {
                    if (bigrams.contains(bigram)) overlap++;
                }
                //按 query 长度归一化，长记忆不再靠体量天然占优
                score += (double) overlap / queryBigrams.size() * 8.0;
            }
            if (userID > 0 && item.getLongValue("subjectID") == userID) score += 2.0;
            score += recencyBonus(item.getLongValue("updateTime"),now);
            scored.add(new MemoryScore(item,score));
        }
        Collections.sort(scored,new Comparator<MemoryScore>() {
            @Override
            public int compare(MemoryScore left,MemoryScore right) {
                return Double.compare(right.score,left.score);
            }
        });
        List<JSONObject> result = new ArrayList<>();
        for (MemoryScore item : scored) result.add(item.memory);
        return result;
    }

    /**
     * 新记忆加分：随时间平滑衰减，越新越靠前，避免老记忆长期霸占注入位
     */
    private double recencyBonus(long updateTime,long now) {
        if (updateTime <= 0) return 0;
        double days = (now - updateTime) / 86400000.0;
        if (days < 0) days = 0;
        return 2.0 / (1.0 + days / 10.0);
    }

    private static class MemoryScore {
        private final JSONObject memory;
        private final double score;

        private MemoryScore(JSONObject memory,double score) {
            this.memory = memory;
            this.score = score;
        }
    }

    private JSONArray longMemories(long groupID) {
        return longMemories(groupID,true);
    }

    private JSONArray longMemories(long groupID,boolean excludeBlacklisted) {
        return longMemories(groupID,excludeBlacklisted,config.maxLongMemories);
    }

    private JSONArray longMemories(long groupID,boolean excludeBlacklisted,int limit) {
        JSONArray result = new JSONArray();
        int capped = limit < 1 ? config.maxLongMemories : limit;
        List<JSONObject> rows;
        if (excludeBlacklisted) {
            rows = storage().query(
                    "SELECT `memoryType`,`subjectID`,`content`,`importance`,`updateTime` FROM `"+MEMORY_TABLE+"` "
                            + "WHERE `groupID`=? AND (`subjectID`=0 OR `subjectID` NOT IN "
                            + "(SELECT `userID` FROM `plugin_mbb_roleplay_blacklist` "
                            + "WHERE `groupID`=?)) "
                            + "ORDER BY `importance` DESC,`updateTime` DESC LIMIT ?",
                    groupID,groupID,capped);
        } else {
            rows = storage().query(
                    "SELECT `memoryType`,`subjectID`,`content`,`importance`,`updateTime` FROM `"+MEMORY_TABLE+"` "
                            + "WHERE `groupID`=? ORDER BY `importance` DESC,`updateTime` DESC LIMIT ?",
                    groupID,capped);
        }
        if (rows == null) return result;
        for (JSONObject row : rows) {
            JSONObject item = new JSONObject(true);
            item.put("type",row.getString("memoryType"));
            item.put("subjectID",row.getLongValue("subjectID"));
            item.put("content",row.getString("content"));
            item.put("importance",row.getIntValue("importance"));
            item.put("updateTime",row.getLongValue("updateTime"));
            result.add(item);
        }
        return result;
    }

    private String shortSummary(long groupID) {
        JSONObject row = storage().queryOne("SELECT `shortSummary` FROM `"+STATE_TABLE+"` WHERE `groupID`=?",groupID);
        return row == null || row.getString("shortSummary") == null || row.getString("shortSummary").isEmpty()
                ? "暂无短期记忆。" : row.getString("shortSummary");
    }

    private MemoryCursor memoryCursor(long groupID) {
        JSONObject row = storage().queryOne(
                "SELECT `lastMemoryTime`,`lastMemoryID` FROM `"+STATE_TABLE+"` WHERE `groupID`=?",groupID);
        return row == null
                ? new MemoryCursor(0L,0L)
                : new MemoryCursor(row.getLongValue("lastMemoryTime"),row.getLongValue("lastMemoryID"));
    }

    private int memoryCount(long groupID) {
        List<JSONObject> rows = storage().query("SELECT COUNT(*) AS `count` FROM `"+MEMORY_TABLE+"` WHERE `groupID`=?",groupID);
        return rows == null || rows.isEmpty() ? 0 : rows.get(0).getIntValue("count");
    }

    private void recordMessage(GroupMessageEvent event,String content,boolean isBot) {
        JSONObject sender = event.getSender();
        String userName = sender == null ? "" : sender.getString("card");
        if (userName == null || userName.isEmpty()) userName = sender == null ? "" : sender.getString("nickname");
        storage().insert("INSERT INTO `"+MSG_TABLE+"` (`messageID`,`groupID`,`userID`,`userName`,`messageTime`,`content`,`isBot`) VALUES (?,?,?,?,?,?,?)",
                event.getMessageID(),event.getGroupID(),event.getUserID(),userName == null ? "" : userName,
                System.currentTimeMillis(),content,isBot ? 1 : 0);
    }

    private void recordBotMessage(long groupID,long botID,String content,long messageID) {
        storage().insert("INSERT INTO `"+MSG_TABLE+"` (`messageID`,`groupID`,`userID`,`userName`,`messageTime`,`content`,`isBot`) VALUES (?,?,?,?,?,?,?)",
                messageID,groupID,botID,"角色",System.currentTimeMillis(),content,1);
    }

    private void sendReply(OneBotClient client,long groupID,long selfID,long userID,String reply,
                           boolean quoteRequired,boolean quoteSuggested,long quoteMessageID) {
        List<String> segments = splitReply(reply);
        if (segments.isEmpty()) return;
        // 引用由规则强制或模型建议，取或；没有可引用的消息 ID 时退化为普通发送
        boolean quote = config.quoteReplyEnable
                && (quoteRequired || quoteSuggested) && quoteMessageID > 0;
        int count = Math.min(segments.size(),config.replyMaxSegments);
        for (int i = 0; i < count; i++) {
            sendSingleMessage(client,groupID,selfID,userID,segments.get(i),quote && i == 0,
                    quoteMessageID);
            if (i + 1 < count) {
                //发送失败或线程被中断都不再继续补后面的分段
                if (!sleepQuietly(250L)) return;
            }
        }
    }

    /**
     * 发送单条消息并登记发言状态，供普通分段和合批分段共用
     */
    private boolean sendSingleMessage(OneBotClient client,long groupID,long selfID,long userID,
                                      String text,boolean quote,long quoteMessageID) {
        if (client == null || text == null || text.trim().isEmpty()) return false;
        JSONArray message = quote && quoteMessageID > 0
                ? MessageUtil.message(MessageUtil.reply(quoteMessageID),MessageUtil.text(text))
                : MessageUtil.message(MessageUtil.text(text));
        JSONObject response = client.sendGroupMessage(groupID,message);
        if (response == null || response.getIntValue("retcode") != 0) return false;
        long messageID = response.getJSONObject("data") == null
                ? 0L : response.getJSONObject("data").getLongValue("message_id");
        RoleplayConversationState state = state(groupID);
        state.lastBotMessageID = messageID;
        state.lastReplyUser = userID;
        state.botStreak++;
        recordBotMessage(groupID,selfID,text,messageID);
        return true;
    }

    private boolean sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 执行技能调用
     * 技能必须已注册且在本轮开放清单内，校验失败只丢弃该动作，不影响正文
     */
    private void executeSkillCalls(RoleplayRouteDecision decision,RoleplayReplyDraft draft,
                                   long groupID,long userID,long messageID,long selfID,
                                   String userName,String relationship,String content) {
        if (draft == null || draft.actions.isEmpty()) return;
        List<String> allowed = decision == null ? skillRegistry.actionIds(config) : decision.actions;
        for (RoleplaySkillCall call : draft.actions) {
            RoleplaySkill skill = skillRegistry.get(call.type);
            if (skill == null) {
                plugin.getLogger().sendWarn("[角色] 群"+groupID+" 执行层请求了未注册的技能："+call.type);
                continue;
            }
            if (!isActionAllowed(allowed,skill.id())) {
                plugin.getLogger().sendWarn("[角色] 群"+groupID+" 执行层请求了未开放的技能："
                        +skill.id()+" 当前开放="+canonicalActions(allowed));
                continue;
            }
            RoleplaySkillContext context = new RoleplaySkillContext();
            context.groupID = groupID;
            context.userID = userID;
            context.messageID = messageID;
            context.selfID = selfID;
            context.userName = userName == null ? "" : userName;
            context.relationship = relationship == null ? "" : relationship;
            context.message = content == null ? "" : content;
            context.args = call.args == null ? new JSONObject(true) : call.args;
            context.state = state(groupID);
            RoleplaySkillResult result = skill.execute(context);
            plugin.getLogger().sendInfo("[角色] 技能 "+skill.id()+" 群"+groupID+" 用户"+userID
                    +" 结果="+(result.success ? "成功" : "失败")
                    +" 摘要="+shortText(result.summary,80));
        }
    }

    private boolean isActionAllowed(List<String> allowed,String skillID) {
        if (allowed == null || skillID == null) return false;
        String target = canonicalAction(skillID);
        for (String item : allowed) {
            if (target.equals(canonicalAction(item))) return true;
        }
        return false;
    }

    private void ensurePokeBackAction(RoleplayReplyDraft draft,RoleplayRouteDecision decision,
                                      String text) {
        if (draft == null || text == null || text.trim().isEmpty()) return;
        if (draft.hasAction("poke-back")) return;
        List<String> allowed = decision == null ? skillRegistry.actionIds(config) : decision.actions;
        if (!isActionAllowed(allowed,"poke-back")) return;
        String value = text.trim();
        if (value.contains("不戳") || value.contains("别戳") || value.contains("不回了")) return;
        if (value.contains("戳回去") || value.contains("回戳") || value.contains("戳你")) {
            draft.addCall(new RoleplaySkillCall("poke-back"));
        }
    }

    private List<String> canonicalActions(List<String> actions) {
        List<String> result = new ArrayList<>();
        if (actions == null) return result;
        for (String item : actions) {
            String value = canonicalAction(item);
            if (!value.isEmpty() && !result.contains(value)) result.add(value);
        }
        return result;
    }

    private String canonicalAction(String action) {
        if (action == null) return "";
        String value = action.trim().toLowerCase(Locale.ROOT);
        if ("poke".equals(value) || "poke_back".equals(value) || "poke-back".equals(value)) {
            return "poke-back";
        }
        return value;
    }

    private RoleplaySkillCall legacyReminderCall(ReminderMarkerResult marker) {
        RoleplaySkillCall call = new RoleplaySkillCall("reminder");
        call.args.put("action",marker.action);
        if (marker.id > 0) call.args.put("id",marker.id);
        if (marker.time != null && !marker.time.isEmpty()) call.args.put("time",marker.time);
        if (marker.task != null && !marker.task.isEmpty()) call.args.put("task",marker.task);
        if (marker.target != null && !marker.target.isEmpty()) call.args.put("target",marker.target);
        return call;
    }

    private RoleplaySkillCall legacyMemoryCall(String content) {
        RoleplaySkillCall call = new RoleplaySkillCall("memory");
        call.args.put("content",content == null ? "" : content);
        return call;
    }

    private RoleplaySkillCall legacyGlobalMemoryCall(String content) {
        RoleplaySkillCall call = new RoleplaySkillCall("global-memory");
        call.args.put("content",content == null ? "" : content);
        return call;
    }

    private RoleplaySkillCall legacyStickerCall(String tags) {
        RoleplaySkillCall call = new RoleplaySkillCall("sticker");
        call.args.put("tags",tags == null ? "" : tags);
        return call;
    }

    private List<String> splitReply(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return result;
        String normalized = text.replace("\\n","\n").replace("\r\n","\n").replace("\r","\n");
        String[] paragraphs = normalized.split("\n");
        for (String paragraph : paragraphs) {
            String value = paragraph.replaceAll("\\s+"," ").trim();
            if (value.isEmpty()) continue;
            if (value.length() <= config.replySegmentMaxChars) {
                result.add(value);
            } else {
                addParagraph(result,value,config.replySegmentMaxChars);
            }
        }
        return result;
    }

    private boolean isCommand(String content) {
        String text = content == null ? "" : content.trim();
        if (text.startsWith("@")) {
            text = text.replaceFirst("^@[0-9]+\\s*","").trim();
        }
        for (String prefix : config.commandPrefixes.split(",")) {
            if (!prefix.trim().isEmpty() && text.startsWith(prefix.trim())) return true;
        }
        return false;
    }

    private void addParagraph(List<String> result,String text,int maxChars) {
        while (text.length() > maxChars) {
            int cut = -1;
            int start = Math.max(0,maxChars - 20);
            for (int i = Math.min(maxChars - 1,text.length() - 1); i >= start; i--) {
                if (isSplitPunctuation(text.charAt(i))) {
                    cut = i + 1;
                    break;
                }
            }
            if (cut <= 0) cut = maxChars;
            result.add(text.substring(0,cut).trim());
            text = text.substring(cut).trim();
        }
        if (!text.isEmpty()) result.add(text);
    }

    private boolean isSplitPunctuation(char value) {
        String punctuation = config.replySplitPunctuation;
        return punctuation != null && punctuation.indexOf(value) >= 0;
    }

    private String senderName(GroupMessageEvent event) {
        JSONObject sender = event.getSender();
        String name = sender == null ? "" : sender.getString("card");
        if (name == null || name.trim().isEmpty()) name = sender == null ? "" : sender.getString("nickname");
        return name == null || name.trim().isEmpty() ? String.valueOf(event.getUserID()) : name;
    }

    private String currentTimeText() {
        try {
            String zoneName = config.timeZone == null || config.timeZone.trim().isEmpty()
                    ? "Asia/Shanghai" : config.timeZone.trim();
            TimeZone zone = TimeZone.getTimeZone(zoneName);
            if ("GMT".equals(zone.getID()) && !"GMT".equalsIgnoreCase(zoneName)) {
                zoneName = "Asia/Shanghai";
                zone = TimeZone.getTimeZone(zoneName);
            }
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss EEEE",Locale.CHINA);
            format.setTimeZone(zone);
            return format.format(new Date())+"（"+zoneName+"）";
        } catch (Exception e) {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss EEEE",Locale.CHINA).format(new Date());
        }
    }

    private String formatMemoryTime(long time) {
        if (time <= 0) return "未知时间";
        try {
            String zoneName = config.timeZone == null || config.timeZone.trim().isEmpty()
                    ? "Asia/Shanghai" : config.timeZone.trim();
            TimeZone zone = TimeZone.getTimeZone(zoneName);
            if ("GMT".equals(zone.getID()) && !"GMT".equalsIgnoreCase(zoneName)) {
                zone = TimeZone.getTimeZone("Asia/Shanghai");
            }
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.CHINA);
            format.setTimeZone(zone);
            return format.format(new Date(time));
        } catch (Exception e) {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.CHINA).format(new Date(time));
        }
    }

    private String relationshipLabel(GroupMessageEvent event,boolean otherRoleBot) {
        if (otherRoleBot) return "其他角色机器人";
        if (event != null && isOwner(event.getUserID())) return "妈妈";
        JSONObject sender = event == null ? null : event.getSender();
        String role = sender == null ? "" : safe(sender.getString("role")).trim().toLowerCase(Locale.CHINA);
        if ("owner".equals(role) || "admin".equals(role)) return "老师";
        return "朋友";
    }

    private boolean isInducedMemoryRequest(String content) {
        String text = safe(content);
        return text.contains("调用全局记忆") || text.contains("全局记忆功能")
                || text.contains("永久记忆") || text.contains("记一下")
                || text.contains("记住这个") || text.contains("记忆一下")
                || text.contains("记下来");
    }

    private boolean isOwner(long userID) {
        List<Long> owners = plugin.getServer().getOwnerList();
        return owners != null && owners.contains(userID);
    }

    private boolean isAddressedToOtherRole(GroupMessageEvent event,String content,long selfID) {
        return mentionsOtherRole(event,content,selfID) && !mentionsSelfRole(event,content,selfID);
    }

    private boolean isMultiRoleAddress(GroupMessageEvent event,String content,long selfID) {
        return mentionsOtherRole(event,content,selfID) && mentionsSelfRole(event,content,selfID);
    }

    private boolean mentionsSelfRole(GroupMessageEvent event,String content,long selfID) {
        if (isMentioningSelf(event,selfID)) return true;
        String text = normalizeAddressText(content);
        if (containsRoleAlias(text,persona.name)) return true;
        for (String alias : persona.aliases) {
            if (containsRoleAlias(text,alias)) return true;
        }
        return containsRoleAlias(text,plugin.getServer().getBotName());
    }

    private boolean mentionsOtherRole(GroupMessageEvent event,String content,long selfID) {
        if (event != null && event.getMessage() != null) {
            JSONArray message = event.getMessage();
            for (int i = 0; i < message.size(); i++) {
                JSONObject segment = message.getJSONObject(i);
                if (segment == null || !"at".equals(segment.getString("type"))) continue;
                JSONObject data = segment.getJSONObject("data");
                long atID = data == null ? 0L : data.getLongValue("qq");
                //只有艾特到另一个角色机器人才算"提到其他角色"，普通群友不算
                if (atID > 0 && atID != selfID && containsCSVLong(config.otherRoleBotQQs,atID)) {
                    return true;
                }
            }
        }
        String text = normalizeAddressText(content);
        String names = config.otherRoleBotNames+","+RoleplayConfig.DEFAULT_OTHER_ROLE_BOT_NAMES;
        for (String item : names.split(",")) {
            String name = item.trim();
            if (name.isEmpty() || isOwnRoleName(name)) continue;
            if (containsRoleAlias(text,name)) return true;
        }
        return false;
    }

    /**
     * 这条消息是否艾特了普通群成员：既不是角色自己，也不是另一个角色机器人
     */
    private boolean mentionsOtherMember(GroupMessageEvent event,long selfID) {
        if (event == null || event.getMessage() == null) return false;
        JSONArray message = event.getMessage();
        for (int i = 0; i < message.size(); i++) {
            JSONObject segment = message.getJSONObject(i);
            if (segment == null || !"at".equals(segment.getString("type"))) continue;
            JSONObject data = segment.getJSONObject("data");
            long atID = data == null ? 0L : data.getLongValue("qq");
            if (atID > 0 && atID != selfID && !containsCSVLong(config.otherRoleBotQQs,atID)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeAddressText(String content) {
        String text = content == null ? "" : content.trim();
        if (text.startsWith("@")) {
            text = text.replaceFirst("^@[0-9]+\\s*","").trim();
        }
        return text;
    }

    private boolean containsRoleAlias(String text,String alias) {
        if (text == null || alias == null || alias.trim().isEmpty()) return false;
        String value = text.trim();
        String name = alias.trim();
        //单字别名容易误伤普通词，只在句首或显式艾特时生效
        if (name.length() == 1) return value.startsWith(name) || value.contains("@"+name);
        return value.contains(name) || value.contains("@"+name);
    }

    private boolean isOwnRoleName(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        if (name.equalsIgnoreCase(persona.name)) return true;
        for (String alias : persona.aliases) {
            if (name.equalsIgnoreCase(alias)) return true;
        }
        return false;
    }

    private boolean isReminderNotification(String content) {
        String text = content == null ? "" : content;
        return text.contains(RoleplayReminderService.REMINDER_MARKER);
    }

    private int countMessage(long groupID) {
        Integer count = messageCountMap.get(groupID);
        count = count == null ? 0 : count;
        count++;
        messageCountMap.put(groupID,count);
        return count;
    }

    /**
     * 只读的限流判断，用于在调用路由之前提前跳过，避免冷却期还去请求模型
     */
    private boolean rateLimited(long groupID) {
        synchronized (groupQueueLock) {
            long now = System.currentTimeMillis();
            RoleplayConversationState state = state(groupID);
            if (state.lastReplyTime > 0
                    && now - state.lastReplyTime < config.replyCooldownSecond * 1000L) {
                return true;
            }
            long hour = now / 3600000L;
            long[] rate = replyRateMap.get(groupID);
            return rate != null && rate[0] == hour && rate[1] >= config.maxRepliesPerHour;
        }
    }

    private boolean canReply(long groupID) {
        //冷却判定与时间戳写入必须原子，否则并发回合会同时读到旧值、各回一次
        synchronized (groupQueueLock) {
            long now = System.currentTimeMillis();
            RoleplayConversationState state = state(groupID);
            if (state.lastReplyTime > 0
                    && now - state.lastReplyTime < config.replyCooldownSecond * 1000L) {
                return false;
            }
            long hour = now / 3600000L;
            long[] rate = replyRateMap.get(groupID);
            if (rate == null || rate[0] != hour) {
                rate = new long[]{hour,1};
                replyRateMap.put(groupID,rate);
            } else {
                if (rate[1] >= config.maxRepliesPerHour) return false;
                rate[1]++;
            }
            state.lastReplyTime = now;
            return true;
        }
    }

    private RoleplayConversationState state(long groupID) {
        RoleplayConversationState existing = conversationStateMap.get(groupID);
        if (existing != null) return existing;
        RoleplayConversationState created = new RoleplayConversationState();
        created.groupID = groupID;
        RoleplayConversationState previous = conversationStateMap.putIfAbsent(groupID,created);
        return previous == null ? created : previous;
    }

    private boolean isDirect(GroupMessageEvent event,String content,long selfID) {
        boolean mentionedSelf = false;
        boolean mentionedOther = false;
        JSONArray message = event.getMessage();
        if (message != null) {
            long lastBot = state(event.getGroupID()).lastBotMessageID;
            for (int i = 0; i < message.size(); i++) {
                JSONObject segment = message.getJSONObject(i);
                if (segment == null) continue;
                JSONObject data = segment.getJSONObject("data");
                if ("at".equals(segment.getString("type")) && data != null) {
                    long atID = data.getLongValue("qq");
                    if (atID == selfID) mentionedSelf = true;
                    else if (atID > 0) mentionedOther = true;
                }
                if ("reply".equals(segment.getString("type")) && data != null && lastBot > 0
                        && data.getLongValue("id") == lastBot) return true;
            }
        }
        if (mentionedSelf) return true;
        if (mentionedOther) return false;

        String text = content == null ? "" : content.trim();
        if (startsWithAlias(text,persona.name) || startsWithAlias(text,plugin.getServer().getBotName())) return true;
        for (String alias : persona.aliases) {
            if (startsWithAlias(text,alias)) return true;
        }

        String raw = event.getRawMessage();
        if (raw != null && selfID > 0) {
            if (raw.contains("[CQ:at,qq="+selfID+"]") || raw.contains("[CQ:at,qq=\""+selfID+"\"]")) return true;
        }
        return false;
    }

    /**
     * 这条消息是否引用了机器人自己的上一条消息
     */
    private boolean isQuotingSelf(GroupMessageEvent event,long selfID) {
        JSONArray message = event.getMessage();
        if (message == null) return false;
        long lastBot = state(event.getGroupID()).lastBotMessageID;
        if (lastBot <= 0) return false;
        for (int i = 0; i < message.size(); i++) {
            JSONObject segment = message.getJSONObject(i);
            if (segment == null || !"reply".equals(segment.getString("type"))) continue;
            JSONObject data = segment.getJSONObject("data");
            if (data != null && data.getLongValue("id") == lastBot) return true;
        }
        return false;
    }

    /**
     * 这条消息是否艾特了机器人自己
     */
    private boolean isMentioningSelf(GroupMessageEvent event,long selfID) {
        if (selfID <= 0) return false;
        JSONArray message = event.getMessage();
        if (message == null) return false;
        for (int i = 0; i < message.size(); i++) {
            JSONObject segment = message.getJSONObject(i);
            if (segment == null || !"at".equals(segment.getString("type"))) continue;
            JSONObject data = segment.getJSONObject("data");
            if (data != null && data.getLongValue("qq") == selfID) return true;
        }
        return false;
    }

    private boolean startsWithAlias(String text,String alias) {
        if (text == null || alias == null || alias.trim().isEmpty()) return false;
        String value = text.trim();
        String name = alias.trim();
        return value.startsWith(name) || value.startsWith("@"+name);
    }

    private boolean isContinuation(long groupID,long userID) {
        RoleplayConversationState state = state(groupID);
        return state.lastReplyTime > 0 && state.lastReplyUser == userID
                && System.currentTimeMillis() - state.lastReplyTime
                <= config.conversationWindowSecond * 1000L;
    }

    /**
     * 同一用户刚被回复过：用于抑制"一句话拆成两句"时的第二次回复
     */
    private boolean justRepliedToSameUser(long groupID,long userID) {
        if (config.splitMessageSuppressSecond <= 0) return false;
        RoleplayConversationState state = state(groupID);
        return state.lastReplyTime > 0 && state.lastReplyUser == userID
                && System.currentTimeMillis() - state.lastReplyTime
                <= config.splitMessageSuppressSecond * 1000L;
    }

    /**
     * 半句识别：结尾正好是角色名字，或者停在连接词上，说明这句话还没说完
     * 命中后延后回合会等同一用户补充，并把补充内容并进同一个回合
     */
    private boolean looksIncomplete(String content) {
        if (config.splitMessageSuppressSecond <= 0) return false;
        String text = content == null ? "" : content.trim();
        if (text.isEmpty() || text.length() > config.splitMessageSuppressMaxChars) return false;
        for (String name : addressNames()) {
            if (name.length() >= 2 && text.endsWith(name)) return true;
        }
        String[] tails = new String[]{"，","、","和","与","跟","的","是",
                "然后","但是","因为","所以","而且","还是"};
        for (String tail : tails) {
            if (text.endsWith(tail)) return true;
        }
        return false;
    }

    private List<String> addressNames() {
        List<String> names = new ArrayList<>();
        if (persona.name != null && !persona.name.trim().isEmpty()) {
            names.add(persona.name.trim());
        }
        for (String alias : persona.aliases) {
            if (alias != null && !alias.trim().isEmpty()) names.add(alias.trim());
        }
        String botName = plugin.getServer().getBotName();
        if (botName != null && !botName.trim().isEmpty()) names.add(botName.trim());
        return names;
    }

    /**
     * 上一条用户消息是不是没说完的半句
     * 当前消息已经记过流水，所以取倒数第二条
     */
    private boolean completesPreviousIncomplete(long groupID,long userID) {
        if (config.splitMessageSuppressSecond <= 0) return false;
        List<JSONObject> rows = storage().query(
                "SELECT `content` FROM `"+MSG_TABLE+"` WHERE `groupID`=? AND `userID`=? "
                        + "AND `isBot`=0 ORDER BY `ID` DESC LIMIT 1 OFFSET 1",
                groupID,userID);
        if (rows == null || rows.isEmpty()) return false;
        return looksIncomplete(safe(rows.get(0).getString("content")));
    }

    private boolean isGroupActive(long groupID) {
        RoleplayConversationState state = state(groupID);
        return state.lastReplyTime > 0
                && System.currentTimeMillis() - state.lastReplyTime
                <= config.conversationWindowSecond * 1000L;
    }

    private int updateOtherRoleMessageStreak(long groupID,boolean otherRoleBot) {
        RoleplayConversationState state = state(groupID);
        if (!otherRoleBot) {
            state.otherRoleStreak = 0;
            state.botStreak = 0;
            return 0;
        }
        state.otherRoleStreak++;
        return state.otherRoleStreak;
    }

    private boolean isOtherRoleBot(GroupMessageEvent event,long selfID) {
        if (event == null || event.getUserID() <= 0 || event.getUserID() == selfID) return false;
        return containsCSVLong(config.otherRoleBotQQs,event.getUserID());
    }

    private boolean isRoleParticipantUser(long userID) {
        return userID > 0 && containsCSVLong(config.otherRoleBotQQs,userID);
    }

    private boolean containsCSVLong(String csv,long value) {
        if (csv == null || csv.trim().isEmpty()) return false;
        for (String item : csv.split(",")) {
            try {
                if (Long.parseLong(item.trim()) == value) return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private boolean shouldSuppressRepeat(long groupID,String reply) {
        if (reply == null || reply.trim().length() < config.repeatCheckMinChars) return false;
        JSONArray recent = recentRoleReplies(groupID);
        if (recent.isEmpty()) return false;
        String candidate = normalizeForSimilarity(reply);
        if (candidate.length() < config.repeatCheckMinChars) return false;
        String opening = openingOf(candidate);
        int openingCount = 0;
        int fillerCount = 0;
        boolean fillerOpening = candidate.startsWith("嗯");
        for (int i = 0; i < recent.size(); i++) {
            String old = normalizeForSimilarity(recent.getJSONObject(i).getString("content"));
            if (old.isEmpty()) continue;
            if (opening.length() >= 2 && old.startsWith(opening)) openingCount++;
            if (fillerOpening && old.startsWith("嗯")) fillerCount++;
            if (similarity(candidate,old) >= config.repeatSimilarityThreshold) return true;
        }
        return (opening.length() >= 2 && openingCount >= config.repeatOpeningLimit)
                || (fillerOpening && fillerCount >= Math.max(1,config.repeatOpeningLimit - 1));
    }

    private String openingOf(String text) {
        if (text == null) return "";
        int length = Math.min(2,text.length());
        return length <= 0 ? "" : text.substring(0,length);
    }

    private double similarity(String left,String right) {
        if (left == null || right == null) return 0;
        if (left.equals(right)) return 1.0;
        Set<String> leftSet = bigrams(left);
        Set<String> rightSet = bigrams(right);
        if (leftSet.isEmpty() || rightSet.isEmpty()) return 0;
        int intersection = 0;
        for (String item : leftSet) {
            if (rightSet.contains(item)) intersection++;
        }
        int union = leftSet.size() + rightSet.size() - intersection;
        return union <= 0 ? 0 : intersection / (double)union;
    }

    private Set<String> bigrams(String text) {
        Set<String> result = new LinkedHashSet<>();
        if (text == null || text.length() < 2) return result;
        for (int i = 0; i < text.length() - 1; i++) {
            result.add(text.substring(i,i + 2));
        }
        return result;
    }

    private String normalizeForSimilarity(String text) {
        if (text == null) return "";
        return text.toLowerCase(Locale.CHINA)
                .replaceAll("[^\\u4e00-\\u9fa5a-z0-9]","")
                .trim();
    }

    private RememberResult extractRemember(String text) {
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

    private ReminderMarkerResult extractReminderMarker(String text) {
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

    private GlobalRememberResult extractGlobalRemember(String text) {
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

    private StickerMarkerResult extractStickerMarker(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new StickerMarkerResult(false,"","");
        }
        Matcher pair = STICKER_PAIR.matcher(text);
        if (pair.find()) {
            String reply = (text.substring(0,pair.start())+text.substring(pair.end())).trim();
            return new StickerMarkerResult(true,reply,pair.group(1).trim());
        }
        Matcher open = STICKER_OPEN.matcher(text);
        if (open.find()) {
            String reply = text.substring(0,open.start()).trim();
            String tags = text.substring(open.end()).trim();
            return new StickerMarkerResult(true,reply,tags);
        }
        return new StickerMarkerResult(false,text.trim(),"");
    }

    String stickerPrompt() {
        PluginService sticker = plugin.getServer().getPluginManager().getService("MBB-Sticker");
        if (sticker == null) return "";
        JSONObject result = sticker.call("tags",null);
        if (result == null || !result.getBooleanValue("status")) return "";
        JSONArray tags = result.getJSONArray("tags");
        if (tags == null || tags.isEmpty()) return "";
        StringBuilder builder = new StringBuilder("当前可用表情包标签：");
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) builder.append(", ");
            builder.append(tags.getString(i));
        }
        builder.append("\n表情包是可选表达，不是每句话都必须带；只有情绪或场景明显合适时才偶尔使用，")
                .append("避免连续多条回复都发表情包。适合时输出 ")
                .append("{\"type\":\"sticker\",\"args\":{\"tags\":\"tag1,tag2\"}}；")
                .append("只能使用上面的标签，没有合适标签时不要输出。");
        return builder.toString();
    }

    private void sendSticker(long groupID,long userID,String tags) {
        PluginService sticker = plugin.getServer().getPluginManager().getService("MBB-Sticker");
        if (sticker == null) return;
        JSONObject params = new JSONObject(true);
        JSONArray tagArray = new JSONArray();
        for (String item : safe(tags).split("[,，]")) {
            if (!item.trim().isEmpty()) tagArray.add(item.trim());
        }
        if (tagArray.isEmpty()) return;
        params.put("tags",tagArray);
        params.put("groupID",groupID);
        params.put("userID",userID);
        JSONObject result = sticker.call("random",params);
        if (result == null || !result.getBooleanValue("status")) return;
        OneBotClient client = plugin.getServer().getOneBotClient();
        if (client == null) return;
        client.sendGroupMessage(groupID,MessageUtil.message(MessageUtil.image(result.getString("file"))));
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

    private String mergeMemory(String first,String second) {
        String left = first == null ? "" : first.trim();
        String right = second == null ? "" : second.trim();
        if (left.isEmpty()) return right;
        if (right.isEmpty()) return left;
        return left+"\n"+right;
    }

    private List<JSONObject> limitMemoryRows(List<JSONObject> rows,int maxChars) {
        List<JSONObject> result = new ArrayList<>();
        if (rows == null || rows.isEmpty()) return result;
        int total = 0;
        for (JSONObject row : rows) {
            String userName = safe(row.getString("userName"));
            String content = safe(row.getString("content"));
            int length = userName.length() + content.length() + 8;
            if (result.isEmpty() && length > maxChars) {
                JSONObject copy = new JSONObject(true);
                copy.putAll(row);
                copy.put("content",content.length() > maxChars
                        ? content.substring(0,maxChars) : content);
                result.add(copy);
                break;
            }
            if (!result.isEmpty() && total + length > maxChars) break;
            result.add(row);
            total += length;
        }
        return result;
    }

    private String shortText(String text,int maxChars) {
        String value = safe(text).replace("\n"," ").trim();
        if (value.length() <= maxChars) return value;
        return value.substring(0,maxChars)+"...";
    }

    private String extractContent(JSONArray message) {
        if (message == null) return "";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < message.size(); i++) {
            JSONObject segment = message.getJSONObject(i);
            if (segment == null) continue;
            String type = segment.getString("type");
            JSONObject data = segment.getJSONObject("data");
            if ("text".equals(type)) builder.append(data == null ? "" : safe(data.getString("text")));
            else if ("image".equals(type) || "mface".equals(type)) builder.append("[图片]");
            else if ("at".equals(type)) builder.append("@").append(data == null ? "" : safe(data.getString("qq")));
            else if ("face".equals(type)) builder.append("[表情]");
        }
        return builder.toString().trim();
    }

    private boolean hasImageContent(JSONArray message) {
        if (message == null) return false;
        for (int i = 0; i < message.size(); i++) {
            JSONObject segment = message.getJSONObject(i);
            if (segment == null) continue;
            String type = segment.getString("type");
            if ("image".equals(type) || "mface".equals(type)) return true;
        }
        return false;
    }

    private boolean hasMeaningfulText(JSONArray message) {
        if (message == null) return false;
        for (int i = 0; i < message.size(); i++) {
            JSONObject segment = message.getJSONObject(i);
            if (segment == null) continue;
            String type = segment.getString("type");
            JSONObject data = segment.getJSONObject("data");
            if ("text".equals(type) && data != null && !safe(data.getString("text")).trim().isEmpty()) {
                return true;
            }
            if ("at".equals(type) && data != null && !safe(data.getString("qq")).trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private RecentImage rememberImageContext(GroupMessageEvent event,long groupID) {
        JSONArray message = event.getMessage();
        if (message == null) return null;
        JSONObject latest = null;
        for (int i = 0; i < message.size(); i++) {
            JSONObject segment = message.getJSONObject(i);
            if (segment == null) continue;
            String type = segment.getString("type");
            JSONObject data = segment.getJSONObject("data");
            if (!("image".equals(type) || "mface".equals(type))) continue;
            if (isStickerSegment(type,data)) continue;
            latest = data;
        }
        if (latest == null) return null;
        JSONObject copy = new JSONObject(true);
        copy.put("file_unique",safe(latest.getString("file_unique")));
        copy.put("file_id",safe(latest.getString("file_id")));
        copy.put("url",safe(latest.getString("url")));
        copy.put("file",safe(latest.getString("file")));
        copy.put("sub_type",latest.get("sub_type"));
        RecentImage recent = new RecentImage(copy,
                System.currentTimeMillis() + config.imageContextTimeoutSecond * 1000L);
        recentImageMap.put(recentImageKey(groupID,event.getUserID()),recent);
        return recent;
    }

    private RecentImage findRecentImageContext(long groupID,long userID,String content) {
        String key = recentImageKey(groupID,userID);
        RecentImage recent = recentImageMap.get(key);
        if (recent == null) return null;
        if (recent.expired()) {
            recentImageMap.remove(key);
            return null;
        }
        return referencesImage(content) ? recent : null;
    }

    private boolean referencesImage(String content) {
        String value = safe(content);
        String[] keywords = new String[]{"图","照片","截图","这个","这张","里面","上面","什么",
                "谁","哪里","颜色","文字","写","看","识别","描述"};
        for (String keyword : keywords) {
            if (value.contains(keyword)) return true;
        }
        return false;
    }

    private JSONObject imageParams(RecentImage recent) {
        JSONObject params = new JSONObject(true);
        JSONObject data = recent == null ? null : recent.data;
        params.put("fileUnique",data == null ? "" : safe(data.getString("file_unique")));
        if (safe(params.getString("fileUnique")).isEmpty() && data != null) {
            params.put("fileUnique",safe(data.getString("file_id")));
        }
        params.put("url",data == null ? "" : safe(data.getString("url")));
        params.put("file",data == null ? "" : safe(data.getString("file")));
        return params;
    }

    private JSONObject imageData(RecentImage recent) {
        PluginService vision = plugin.getServer().getPluginManager().getService("MBB-Vision");
        if (vision == null || recent == null) return null;
        return vision.call("dataUri",imageParams(recent));
    }

    private String imageSummary(RecentImage recent) {
        if (recent == null) return "";
        if (recent.summary != null && !recent.summary.trim().isEmpty()) return recent.summary;
        PluginService vision = plugin.getServer().getPluginManager().getService("MBB-Vision");
        if (vision == null) return "";
        JSONObject result = vision.call("describe",imageParams(recent));
        if (result == null || !result.getBooleanValue("status")) return "";
        String summary = safe(result.getString("summary"));
        if (summary.isEmpty()) summary = safe(result.getString("description"));
        recent.summary = summary;
        return summary;
    }

    private boolean sameImage(JSONObject left,JSONObject right) {
        if (left == null || right == null) return false;
        String leftUnique = safe(left.getString("file_unique"));
        String rightUnique = safe(right.getString("file_unique"));
        if (!leftUnique.isEmpty() || !rightUnique.isEmpty()) return leftUnique.equals(rightUnique);
        String leftUrl = safe(left.getString("url"));
        String rightUrl = safe(right.getString("url"));
        if (!leftUrl.isEmpty() || !rightUrl.isEmpty()) return leftUrl.equals(rightUrl);
        String leftFile = safe(left.getString("file"));
        String rightFile = safe(right.getString("file"));
        return !leftFile.isEmpty() && leftFile.equals(rightFile);
    }

    private String recentImageKey(long groupID,long userID) {
        return groupID+"|"+userID;
    }

    private boolean shouldDeferTurn(GroupMessageEvent event,boolean otherRoleBot,boolean hasImage) {
        return config.stickerAttachEnable
                && !otherRoleBot
                && !hasImage
                && !hasStickerContent(event.getMessage());
    }

    private void deferTurn(GroupMessageEvent event,long groupID,long selfID,String userName,
                           String relationship,String content,RecentImage imageContext,
                           boolean otherRoleBot,RoleplayRouteDecision decision,
                           RoleplayEmotionService.LocalEvent emotionEvent) {
        String key = recentImageKey(groupID,event.getUserID());
        int generation = nextPendingGeneration(key);
        PendingTurn pending = new PendingTurn(event,selfID,userName,relationship,
                content,imageContext,generation,otherRoleBot,decision,emotionEvent);
        pending.stickerEmotion = recentStickerEmotion(groupID,event.getUserID());
        //结尾正好是角色名字或悬挂连接词时，判定为还没说完的半句，等同一用户补充
        pending.incomplete = looksIncomplete(content);
        pending.hardDeadline = System.currentTimeMillis() + config.stickerAttachMaxWaitSecond * 1000L;
        pendingTurnMap.put(key,pending);
        //事件驱动等待：表情包在等待前就已经附带时不再空等窗口
        int delay = pending.stickerEmotion.isEmpty() ? config.stickerAttachWindowSecond : 0;
        plugin.getServer().getPluginManager().runTaskLater(plugin,
                () -> executePendingTurn(key,generation),delay);
    }

    /**
     * 同一用户又发言时立刻结束他的表情包等待，不再让回合干等到窗口结束
     */
    private void wakePendingTurnForUser(long groupID,GroupMessageEvent event) {
        if (!config.stickerAttachWaitUntilNextMessage) return;
        long userID = event.getUserID();
        String key = recentImageKey(groupID,userID);
        PendingTurn pending = pendingTurnMap.get(key);
        if (pending == null) return;
        if (pending.incomplete) {
            String text = extractContent(event.getMessage());
            if (text != null && !text.trim().isEmpty()) {
                pending.appendContent(text);
                //并入的这句仍然要写进消息流水，否则记忆整理看不到它
                recordMessage(event,text,false);
                if (event.getMessageID() > 0) {
                    if (mergedMessageIDs.size() > 500) mergedMessageIDs.clear();
                    mergedMessageIDs.add(event.getMessageID());
                }
                plugin.getLogger().sendInfo("[角色] 群"+groupID+" 合并同一用户的补充发言："
                        +shortText(text,40));
            }
        }
        plugin.getServer().getPluginManager().runTaskLater(plugin,
                () -> executePendingTurn(key,pending.generation),0);
    }

    private void executePendingTurn(String key,int generation) {
        PendingTurn pending = pendingTurnMap.get(key);
        if (pending == null || pending.generation != generation) return;
        if (pending.stickerPending) {
            long now = System.currentTimeMillis();
            if (now < pending.hardDeadline) {
                plugin.getServer().getPluginManager().runTaskLater(plugin,
                        () -> executePendingTurn(key,generation),1);
                return;
            }
            plugin.getLogger().sendWarn("[角色] 群"+pending.event.getGroupID()
                    +" 等待表情包识别超时，按无表情包继续。");
        }
        //只有真正摘掉待处理回合的那一次调用继续往下走，避免并发唤醒导致同一回合回两次
        if (!pendingTurnMap.remove(key,pending)) return;
        submitTurn(() -> runPendingTurn(pending));
    }

    private void runPendingTurn(PendingTurn pending) {
        long groupID = pending.event.getGroupID();
        try {
            if (blacklistService.contains(groupID,pending.event.getUserID())) {
                plugin.getLogger().sendInfo("[角色] 群"+groupID+" 跳过已拉黑用户的待处理回合 "
                        +pending.event.getUserID());
                return;
            }
            if (!canReply(groupID)) {
                plugin.getLogger().sendInfo("[角色] 群"+groupID
                        +" 待处理回合被回复冷却或频率限制拦下，本次不回复");
                return;
            }
            String content = mergeStickerEmotion(pending.content,pending.stickerEmotion);
            JSONObject result = reply(groupID,pending.event.getUserID(),pending.userName,content,
                    pending.otherRoleBot,pending.relationship,pending.imageContext,pending.decision);
            if (result == null || !result.getBooleanValue("status")) {
                plugin.getLogger().sendWarn("[角色] 群"+groupID+" 待处理回合回复生成失败，本次不发送");
                return;
            }
            processReplyResult(pending.event,groupID,pending.selfID,pending.userName,
                    pending.relationship,content,result,pending.decision,pending.emotionEvent);
        } finally {
            //延后回合到这里才算结束，释放同群串行位并接着处理缓冲里的下一批
            finishTurn(groupID);
        }
    }

    private void markStickerRecognitionPending(long groupID,long userID) {
        PendingTurn pending = pendingTurnMap.get(recentImageKey(groupID,userID));
        if (pending == null) return;
        pending.stickerPending = true;
    }

    private void finishStickerRecognition(long groupID,long userID,String emotion) {
        String key = recentImageKey(groupID,userID);
        rememberStickerEmotion(groupID,userID,emotion);
        PendingTurn pending = pendingTurnMap.get(key);
        if (pending == null) return;
        if (emotion != null && !emotion.trim().isEmpty()) {
            pending.stickerEmotion = emotion.trim();
        }
        pending.stickerPending = false;
        executePendingTurn(key,pending.generation);
    }

    private void rememberStickerEmotion(long groupID,long userID,String emotion) {
        if (emotion == null || emotion.trim().isEmpty()) return;
        recentStickerMap.put(recentImageKey(groupID,userID),new RecentSticker(
                emotion.trim(),System.currentTimeMillis() + config.stickerAttachWindowSecond * 1000L));
    }

    private String recentStickerEmotion(long groupID,long userID) {
        String key = recentImageKey(groupID,userID);
        RecentSticker recent = recentStickerMap.get(key);
        if (recent == null) return "";
        if (recent.expired()) {
            recentStickerMap.remove(key);
            return "";
        }
        return recent.emotion;
    }

    private String mergeStickerEmotion(String content,String emotion) {
        String text = safe(content);
        String sticker = safe(emotion).trim();
        if (sticker.isEmpty()) return text;
        return text+"\n"+sticker;
    }

    private synchronized int nextPendingGeneration(String key) {
        Integer current = pendingGenerationMap.get(key);
        int next = current == null ? 1 : current + 1;
        pendingGenerationMap.put(key,next);
        return next;
    }

    private boolean shouldUnderstandImages(GroupMessageEvent event,String content,
                                           boolean direct,boolean sameUserContinuation) {
        if (!config.imageUnderstandingEnable || "off".equals(config.imageUnderstandingMode)) return false;
        if (hasStickerContent(event.getMessage())) return true;
        if ("all".equals(config.imageUnderstandingMode)) return true;
        return direct || sameUserContinuation;
    }

    private boolean hasStickerContent(JSONArray message) {
        if (message == null) return false;
        for (int i = 0; i < message.size(); i++) {
            JSONObject segment = message.getJSONObject(i);
            if (segment == null) continue;
            if (isStickerSegment(segment.getString("type"),segment.getJSONObject("data"))) return true;
        }
        return false;
    }

    private boolean isStickerSegment(String type,JSONObject data) {
        if ("mface".equals(type)) return true;
        if (data == null) return false;
        String file = safe(data.getString("file"));
        if (file.contains("marketface")) return true;
        Object subType = data.get("sub_type");
        if (subType == null) return false;
        String value = String.valueOf(subType).trim();
        return !value.isEmpty() && !"0".equals(value);
    }

    /**
     * 纯图片与表情消息的识图
     * 放到独立异步任务里跑，识图完成后合并表情语气并放行等待中的回合，
     * 这样一张图不会卡住同一群后面的消息。
     */
    private void dispatchImageUnderstanding(GroupMessageEvent event,long groupID,
                                            RecentImage imageContext,boolean hasSticker,
                                            boolean direct,boolean sameUserContinuation) {
        if (!shouldUnderstandImages(event,"",direct,sameUserContinuation)) return;
        final long userID = event.getUserID();
        final boolean sticker = hasSticker;
        submitBackground(() -> {
            try {
                String content = enrichImageContent(event,"",groupID,imageContext);
                if (sticker) {
                    String emotion = content == null || content.trim().isEmpty()
                            ? "[表情包]" : content.trim();
                    finishStickerRecognition(groupID,userID,emotion);
                }
            } catch (Exception e) {
                plugin.getLogger().sendException(e);
            }
        });
    }

    private String enrichImageContent(GroupMessageEvent event,String context,long groupID,
                                      RecentImage currentImage) {
        PluginService vision = plugin.getServer().getPluginManager().getService("MBB-Vision");
        if (vision == null) return context;
        if (!reserveImageVision(groupID)) {
            plugin.getLogger().sendWarn("[识图] 群"+groupID+" 已达到每小时识图上限，跳过图片理解。");
            return context;
        }
        StringBuilder builder = new StringBuilder();
        JSONArray message = event.getMessage();
        for (int i = 0; i < message.size(); i++) {
            JSONObject segment = message.getJSONObject(i);
            if (segment == null) continue;
            String type = segment.getString("type");
            JSONObject data = segment.getJSONObject("data");
            if ("text".equals(type)) {
                builder.append(data == null ? "" : safe(data.getString("text")));
            } else if ("at".equals(type)) {
                builder.append("@").append(data == null ? "" : safe(data.getString("qq")));
            } else if ("image".equals(type) || "mface".equals(type)) {
                builder.append(describeImageSegment(vision,type,data,context,currentImage));
            } else if ("face".equals(type)) {
                builder.append("[表情]");
            }
        }
        return builder.toString().trim();
    }

    private String describeImageSegment(PluginService vision,String type,JSONObject data,String context,
                                        RecentImage currentImage) {
        boolean sticker = isStickerSegment(type,data);
        JSONObject params = new JSONObject(true);
        params.put("kind",sticker ? "sticker" : "image");
        String fileUnique = data == null ? "" : safe(data.getString("file_unique"));
        if (fileUnique.isEmpty() && data != null) fileUnique = safe(data.getString("file_id"));
        params.put("fileUnique",fileUnique);
        params.put("url",data == null ? "" : safe(data.getString("url")));
        params.put("file",data == null ? "" : safe(data.getString("file")));
        params.put("context",context);
        params.put("reference",persona.visionReferenceText());
        params.put("profile",config.imageUnderstandingProfile);
        JSONObject result = vision.call("describe",params);
        if (result == null || !result.getBooleanValue("status")) {
            return sticker ? "[表情包]" : "[图片]";
        }
        String summary = safe(result.getString("summary"));
        if (summary.isEmpty()) summary = safe(result.getString("description"));
        if (currentImage != null && !sticker && sameImage(data,currentImage.data)) {
            currentImage.summary = summary;
        }
        String ocr = safe(result.getString("ocr"));
        String tags = joinArray(result.getJSONArray("emotionTags"));
        StringBuilder builder = new StringBuilder(sticker ? "[表情包" : "[图片");
        if (!summary.isEmpty()) builder.append("：").append(shortText(summary,config.imageUnderstandingMaxChars));
        if (sticker && !tags.isEmpty()) builder.append("；情绪：").append(tags);
        if (!sticker && config.imageUnderstandingInjectOcr && !ocr.isEmpty()) {
            builder.append("；文字：").append(shortText(ocr,120));
        }
        return builder.append("]").toString();
    }

    private String joinArray(JSONArray array) {
        if (array == null || array.isEmpty()) return "";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < array.size(); i++) {
            String value = safe(array.getString(i));
            if (value.isEmpty()) continue;
            if (builder.length() > 0) builder.append(", ");
            builder.append(value);
        }
        return builder.toString();
    }

    private synchronized boolean reserveImageVision(long groupID) {
        if (config.imageUnderstandingMaxPerHour <= 0) return true;
        long now = System.currentTimeMillis();
        long[] state = imageVisionRateMap.get(groupID);
        if (state == null || now - state[0] >= 3600000L) {
            imageVisionRateMap.put(groupID,new long[]{now,1});
            return true;
        }
        if (state[1] >= config.imageUnderstandingMaxPerHour) return false;
        state[1]++;
        return true;
    }

    private boolean containsIgnoredContent(JSONArray message) {
        if (message == null) return false;
        for (int i = 0; i < message.size(); i++) {
            JSONObject segment = message.getJSONObject(i);
            if (segment == null) continue;
            String type = segment.getString("type");
            if ("face".equals(type)) return true;
        }
        return false;
    }

    private JSONObject parseJson(String content) {
        if (content == null || content.trim().isEmpty()) return null;
        String text = content.replace("\uFEFF","").trim();
        JSONObject direct = tryParseObject(text);
        if (direct != null) return direct;
        for (String candidate : extractJsonObjects(text)) {
            JSONObject parsed = tryParseObject(candidate);
            if (parsed != null) return parsed;
        }
        return null;
    }

    private JSONObject tryParseObject(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        String value = text.trim();
        JSONObject parsed = parseObjectValue(value);
        if (parsed != null) return parsed;
        String repaired = repairJson(value);
        if (!repaired.equals(value)) return parseObjectValue(repaired);
        return null;
    }

    private JSONObject parseObjectValue(String text) {
        try {
            Object value = JSON.parse(text);
            if (value instanceof JSONObject) return (JSONObject)value;
            if (value instanceof JSONArray) {
                JSONArray array = (JSONArray)value;
                for (Object item : array) {
                    if (item instanceof JSONObject) return (JSONObject)item;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private List<String> extractJsonObjects(String text) {
        List<String> result = new ArrayList<>();
        if (text == null) return result;
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        int start = -1;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == '"') {
                    inString = false;
                }
                continue;
            }
            if (character == '"') {
                inString = true;
            } else if (character == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (character == '}' && depth > 0) {
                depth--;
                if (depth == 0 && start >= 0) {
                    result.add(text.substring(start,i + 1));
                    start = -1;
                }
            }
        }
        return result;
    }

    private String repairJson(String text) {
        String value = text.replace("\uFEFF","").trim();
        value = value.replaceAll("(?s)```[a-zA-Z0-9_-]*\\s*","");
        value = value.replace("```","");
        value = stripJsonComments(value);
        value = value.replaceAll(",\\s*([}\\]])","$1");
        value = value.replaceAll(",\\s*([}\\]])","$1");
        return escapeJsonControls(value);
    }

    private String stripJsonComments(String text) {
        StringBuilder builder = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (inString) {
                builder.append(character);
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == '"') {
                    inString = false;
                }
                continue;
            }
            if (character == '"') {
                inString = true;
                builder.append(character);
                continue;
            }
            if (character == '/' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                if (next == '/') {
                    i += 2;
                    while (i < text.length() && text.charAt(i) != '\n') i++;
                    builder.append('\n');
                    continue;
                }
                if (next == '*') {
                    i += 2;
                    while (i + 1 < text.length()
                            && !(text.charAt(i) == '*' && text.charAt(i + 1) == '/')) i++;
                    i++;
                    continue;
                }
            }
            builder.append(character);
        }
        return builder.toString();
    }

    private String escapeJsonControls(String text) {
        StringBuilder builder = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (!inString) {
                builder.append(character);
                if (character == '"') inString = true;
                continue;
            }
            if (escaped) {
                builder.append(character);
                escaped = false;
                continue;
            }
            if (character == '\\') {
                builder.append(character);
                escaped = true;
                continue;
            }
            if (character == '"') {
                builder.append(character);
                inString = false;
                continue;
            }
            if (character == '\n') {
                builder.append("\\n");
            } else if (character == '\r') {
                builder.append("\\r");
            } else if (character == '\t') {
                builder.append("\\t");
            } else if (character < 0x20) {
                builder.append(String.format("\\u%04x",(int)character));
            } else {
                builder.append(character);
            }
        }
        return builder.toString();
    }

    private JSONObject message(String role,String content) {
        JSONObject message = new JSONObject(true);
        message.put("role",role);
        message.put("content",content == null ? "" : content);
        return message;
    }

    private void ensureColumn(String table,String column,String definition) {
        List<JSONObject> columns = storage().query("PRAGMA table_info(`"+table+"`)");
        if (columns != null) {
            for (JSONObject item : columns) {
                if (column.equalsIgnoreCase(safe(item.getString("name")))) return;
            }
        }
        storage().update("ALTER TABLE `"+table+"` ADD COLUMN `"+column+"` "+definition);
    }

    private StorageService storage() {
        return plugin.getServer().getStorage();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
