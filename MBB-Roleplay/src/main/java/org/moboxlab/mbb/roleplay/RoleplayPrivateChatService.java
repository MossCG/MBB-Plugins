package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Event.NoticeEvent;
import org.moboxlab.moboxbot.API.Event.PrivateMessageEvent;
import org.moboxlab.moboxbot.API.OneBot.MessageUtil;
import org.moboxlab.moboxbot.API.OneBot.OneBotClient;
import org.moboxlab.moboxbot.API.Plugin;
import org.moboxlab.moboxbot.API.PluginService;
import org.moboxlab.moboxbot.API.Storage.StorageService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 角色私聊回合。
 *
 * 私聊使用独立上下文，只读取跨群语义记忆、用户档案和公开长期记忆，
 * 不把其他群的原始流水直接带进私聊。
 */
public class RoleplayPrivateChatService {
    static final String TABLE = "plugin_mbb_roleplay_private_message";
    private final Plugin plugin;
    private final RoleplayService service;
    private volatile RoleplayConfig config;
    private final Set<Long> running =
            Collections.newSetFromMap(new ConcurrentHashMap<Long,Boolean>());
    private volatile Set<Long> friendCache = Collections.emptySet();
    private volatile boolean friendCacheReady = false;
    private volatile long friendCacheTime = 0L;
    private volatile long friendCacheRetryTime = 0L;
    private final Object queueLock = new Object();
    private final Map<Long,List<PrivateIncoming>> pendingMap = new HashMap<>();
    private final Map<Long,Integer> generationMap = new HashMap<>();
    private final Map<Long,Long> pendingStartMap = new HashMap<>();
    private final Map<Long,Long> typingLastMap = new HashMap<>();

    private static class ReplyResult {
        private String text = "";
        private String memory = "";
    }

    private static class PrivateIncoming {
        long messageID;
        long userID;
        long selfID;
        String userName = "";
        String content = "";
        boolean hasImage;
        boolean hasSticker;
        boolean hasMeaningfulText;
        PrivateMessageEvent event;
    }

    public RoleplayPrivateChatService(Plugin plugin,RoleplayConfig config,RoleplayService service) {
        this.plugin = plugin;
        this.config = config;
        this.service = service;
    }

    public void init() {
        storage().update("CREATE TABLE IF NOT EXISTS `"+TABLE+"` ("
                + "`ID` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`messageID` INTEGER NOT NULL DEFAULT 0,"
                + "`userID` INTEGER NOT NULL DEFAULT 0,"
                + "`selfID` INTEGER NOT NULL DEFAULT 0,"
                + "`userName` TEXT NOT NULL DEFAULT '',"
                + "`content` TEXT NOT NULL DEFAULT '',"
                + "`isBot` INTEGER NOT NULL DEFAULT 0,"
                + "`messageTime` INTEGER NOT NULL DEFAULT 0"
                + ")");
        storage().update("CREATE INDEX IF NOT EXISTS `idx_plugin_mbb_roleplay_private_time` "
                + "ON `"+TABLE+"` (`userID`,`messageTime`)");
        storage().update("DELETE FROM `"+TABLE+"` WHERE `messageTime`<?",
                System.currentTimeMillis() - 90L * 86400000L);
    }

    public void reload(RoleplayConfig config) {
        this.config = config;
    }

    public void handle(PrivateMessageEvent event) {
        if (event == null || !config.enable || !config.privateChatEnable) return;
        long userID = event.getUserID();
        long selfID = event.getRaw().getLongValue("self_id");
        if (userID <= 0 || userID == selfID) return;
        if (!allowed(userID)) return;
        String content = service.extractContent(event.getMessage());
        if (content == null || content.trim().isEmpty() || service.isCommand(content)) return;
        String userName = senderName(event);
        record(event.getMessageID(),userID,selfID,userName,content,false,
                System.currentTimeMillis());
        if (selfID <= 0) {
            JSONObject login = loginInfo();
            selfID = login == null ? 0L : login.getLongValue("user_id");
        }
        PrivateIncoming incoming = new PrivateIncoming();
        incoming.messageID = event.getMessageID();
        incoming.userID = userID;
        incoming.selfID = selfID;
        incoming.userName = userName;
        incoming.content = content;
        incoming.event = event;
        incoming.hasImage = service.hasImageContent(event.getMessage());
        incoming.hasSticker = service.hasStickerContent(event.getMessage());
        incoming.hasMeaningfulText = service.hasMeaningfulText(event.getMessage());
        enqueue(incoming);
    }

    public void handleInputStatus(NoticeEvent event) {
        if (event == null || !config.enable || !config.privateChatEnable
                || !config.privateChatInputStatusEnable || event.getGroupID() > 0) return;
        long userID = event.getUserID();
        if (userID <= 0 || !allowed(userID)) return;
        int generation;
        int delaySeconds;
        boolean schedule = false;
        boolean log = false;
        synchronized (queueLock) {
            List<PrivateIncoming> pending = pendingMap.get(userID);
            if (pending == null || pending.isEmpty()) return;
            long now = System.currentTimeMillis();
            pendingStartMap.putIfAbsent(userID,now);
            Long previous = typingLastMap.put(userID,now);
            log = previous == null || now - previous >= 2000L;
            if (running.contains(userID)) return;
            generation = nextGenerationLocked(userID);
            delaySeconds = typingDelayLocked(userID,now);
            schedule = true;
        }
        if (log) {
            plugin.getLogger().sendInfo("[私聊] 用户"+userID+" 正在输入，等待最后输入状态后 "
                    +config.privateChatInputStatusGraceSecond+" 秒再回复");
        }
        if (schedule) scheduleFlush(userID,generation,delaySeconds);
    }

    public boolean allowed(long userID) {
        if (userID <= 0) return false;
        if (service.isOwner(userID)) return true;
        long now = System.currentTimeMillis();
        long cacheMillis = Math.max(30,config.privateChatFriendCacheSecond) * 1000L;
        if (!friendCacheReady || now - friendCacheTime >= cacheMillis) {
            refreshFriendCache(now);
        }
        Set<Long> current = friendCache;
        return friendCacheReady && current.contains(userID);
    }

    public String generateProactive(long userID,String reason) {
        if (!allowed(userID)) return "";
        String prompt = "你是"+service.persona().name+"。现在不是被动回复，而是想主动给 owner 发一条私聊。"
                +"不要解释系统、模型、记忆或调度；不要提“随机刻”。"
                +"只发一句自然、具体、像真人突然想起对方时会说的话，最多 50 字。"
                +"如果没有合适的话，只输出 <SKIP>。"
                +"可以结合最近跨群记忆、当天时间、关心、分享小发现或接续上次没说完的事，但不要强行找话题。"
                +"禁止使用客服式关心、总结、清单和过度亲昵。\n"
                +"触发原因："+service.safe(reason)+"\n"
                +"最近私聊：\n"+recentContext(userID);
        return callAi(userID,prompt,true,Collections.<Long>emptySet()).text;
    }

    public boolean sendProactive(long userID,String content,String reason) {
        if (!allowed(userID)) return false;
        String text = clean(content);
        if (text.isEmpty() || "<SKIP>".equalsIgnoreCase(text)) return false;
        boolean sent = sendText(userID,text);
        if (sent) {
            plugin.getLogger().sendInfo("[私聊] 主动消息已发送 用户"+userID
                    +(reason == null || reason.trim().isEmpty() ? "" : " 原因="+service.shortText(reason,80)));
        }
        return sent;
    }

    private void enqueue(PrivateIncoming incoming) {
        int generation = 0;
        int delaySeconds = 0;
        boolean schedule = false;
        int pendingCount = 0;
        synchronized (queueLock) {
            long userID = incoming.userID;
            List<PrivateIncoming> pending = pendingMap.get(userID);
            if (pending == null) {
                pending = new ArrayList<>();
                pendingMap.put(userID,pending);
                pendingStartMap.put(userID,System.currentTimeMillis());
            }
            if (pending.isEmpty()) pendingStartMap.putIfAbsent(userID,System.currentTimeMillis());
            int max = Math.max(1,config.privateChatPendingMaxMessages);
            while (pending.size() >= max) pending.remove(0);
            pending.add(incoming);
            typingLastMap.remove(userID);
            pendingCount = pending.size();
            if (!running.contains(userID)) {
                generation = nextGenerationLocked(userID);
                delaySeconds = effectiveDelayLocked(userID,System.currentTimeMillis());
                schedule = true;
            }
        }
        if ((incoming.hasImage || incoming.hasSticker) && !incoming.hasMeaningfulText) {
            plugin.getLogger().sendInfo("[私聊] 用户"+incoming.userID+" 收到"
                    +(incoming.hasSticker ? "表情包" : "图片")
                    +"，防抖 "+config.privateChatImageDebounceSecond+" 秒"
                    +(pendingCount > 1 ? "，当前待处理 "+pendingCount+" 条" : ""));
        }
        if (schedule) scheduleFlush(incoming.userID,generation,delaySeconds);
    }

    private void scheduleFlush(long userID,int generation,int delaySeconds) {
        plugin.getServer().getPluginManager().runTaskLater(plugin,
                () -> flushPending(userID,generation),Math.max(0,delaySeconds));
    }

    private void flushPending(long userID,int generation) {
        List<PrivateIncoming> batch;
        synchronized (queueLock) {
            Integer current = generationMap.get(userID);
            if (current == null || current != generation || running.contains(userID)) return;
            batch = pendingMap.remove(userID);
            if (batch == null || batch.isEmpty()) return;
            pendingStartMap.remove(userID);
            typingLastMap.remove(userID);
            running.add(userID);
        }
        service.submitBackground(() -> runBatch(userID,batch));
    }

    private void runBatch(long userID,List<PrivateIncoming> initial) {
        List<PrivateIncoming> batch = new ArrayList<>(initial);
        int merges = 0;
        try {
            ReplyResult reply;
            while (true) {
                String current = buildBatchContent(batch);
                Set<Long> excluded = excludedMessageIDs(batch);
                reply = callAi(userID,current,false,excluded);
                List<PrivateIncoming> extra = null;
                if (merges < Math.max(0,config.privateChatLiveMergeMaxRounds)) {
                    synchronized (queueLock) {
                        List<PrivateIncoming> pending = pendingMap.get(userID);
                        if (pending != null && !pending.isEmpty()) {
                            extra = pendingMap.remove(userID);
                        }
                    }
                }
                if (extra == null || extra.isEmpty()) break;
                batch.addAll(extra);
                merges++;
                plugin.getLogger().sendInfo("[私聊] 用户"+userID+" 生成期间收到 "
                        +extra.size()+" 条新消息，丢弃旧候选并合并重生成（第"+merges+"次）");
            }
            String text = clean(reply.text);
            if (text.isEmpty() || "<SKIP>".equalsIgnoreCase(text)) return;
            if (text.length() > config.privateChatMaxReplyChars) {
                text = text.substring(0,config.privateChatMaxReplyChars).trim();
            }
            if (sendText(userID,text)) {
                savePrivateMemory(userID,reply.memory);
                service.friendMemoryService().maybeSchedule(userID);
            }
        } catch (Exception e) {
            plugin.getLogger().sendException(e);
        } finally {
            synchronized (queueLock) {
                running.remove(userID);
            }
            schedulePending(userID);
        }
    }

    private void schedulePending(long userID) {
        int generation;
        int delaySeconds;
        synchronized (queueLock) {
            if (running.contains(userID)) return;
            List<PrivateIncoming> pending = pendingMap.get(userID);
            if (pending == null || pending.isEmpty()) {
                generationMap.remove(userID);
                pendingStartMap.remove(userID);
                typingLastMap.remove(userID);
                return;
            }
            generation = nextGenerationLocked(userID);
            delaySeconds = effectiveDelayLocked(userID,System.currentTimeMillis());
        }
        if (delaySeconds > 0) {
            plugin.getLogger().sendInfo("[私聊] 用户"+userID+" 等待补充输入中，"
                    +delaySeconds+" 秒后处理待回复消息");
        }
        scheduleFlush(userID,generation,delaySeconds);
    }

    private int nextGenerationLocked(long userID) {
        Integer current = generationMap.get(userID);
        int next = current == null ? 1 : current + 1;
        generationMap.put(userID,next);
        return next;
    }

    private int delayForPending(List<PrivateIncoming> pending) {
        if (pending == null || pending.isEmpty()) return 0;
        PrivateIncoming last = pending.get(pending.size() - 1);
        return RoleplayPrivateChatDebounce.delaySeconds(last.hasImage || last.hasSticker,
                last.hasMeaningfulText,config.privateChatImageDebounceSecond);
    }

    private int effectiveDelayLocked(long userID,long now) {
        List<PrivateIncoming> pending = pendingMap.get(userID);
        if (pending == null || pending.isEmpty()) return 0;
        int base = delayForPending(pending);
        Long typing = typingLastMap.get(userID);
        if (typing == null || typing <= 0) return base;
        long first = pendingStartMap.containsKey(userID)
                ? pendingStartMap.get(userID) : typing;
        int typingDelay = RoleplayPrivateChatDebounce.typingDelaySeconds(typing,first,now,
                config.privateChatInputStatusGraceSecond,
                config.privateChatInputStatusMaxWaitSecond);
        return typingDelay;
    }

    private int typingDelayLocked(long userID,long now) {
        Long typing = typingLastMap.get(userID);
        if (typing == null) return 0;
        Long first = pendingStartMap.get(userID);
        return RoleplayPrivateChatDebounce.typingDelaySeconds(typing,
                first == null ? typing : first,now,
                config.privateChatInputStatusGraceSecond,
                config.privateChatInputStatusMaxWaitSecond);
    }

    private String buildBatchContent(List<PrivateIncoming> batch) {
        if (batch == null || batch.isEmpty()) return "";
        StringBuilder builder = new StringBuilder();
        if (batch.size() > 1) {
            builder.append("对方在短时间内连续发了 ").append(batch.size())
                    .append(" 条私聊，按时间顺序如下：\n");
        }
        for (int i = 0; i < batch.size(); i++) {
            PrivateIncoming item = batch.get(i);
            String content = item.hasImage
                    ? service.enrichPrivateImageContent(item.event,item.content)
                    : item.content;
            if (batch.size() > 1) builder.append(i + 1).append(". ");
            builder.append(service.safe(content).trim()).append("\n");
        }
        return builder.toString().trim();
    }

    private Set<Long> excludedMessageIDs(List<PrivateIncoming> batch) {
        Set<Long> result = new LinkedHashSet<>();
        if (batch == null) return result;
        for (PrivateIncoming item : batch) {
            if (item.messageID > 0) result.add(item.messageID);
        }
        return result;
    }

    private ReplyResult callAi(long userID,String current,boolean proactive,
                               Set<Long> excludedMessageIDs) {
        ReplyResult reply = new ReplyResult();
        PluginService ai = plugin.getServer().getPluginManager().getService("MBB-AI");
        if (ai == null) return reply;
        JSONArray messages = new JSONArray();
        //固定规则和人设放在最前，时间、用户、记忆与最近消息继续放在动态系统块。
        messages.add(service.message("system",stableSystemPrompt()));
        messages.add(service.message("system",dynamicSystemPrompt(
                userID,current,proactive,excludedMessageIDs)));
        messages.add(service.message("user","当前时间："+service.currentTimeText()+"\n"+current));
        JSONObject params = new JSONObject(true);
        params.put("profile",config.aiProfile);
        params.put("maxTokens",config.replyMaxTokens);
        params.put("reasoningEffort",config.replyReasoningEffort);
        params.put("temperature",0.8);
        params.put("timeoutSeconds",config.randomTickTimeoutSecond);
        params.put("retryCount",0);
        params.put("sessionId","roleplay-private-"+userID);
        params.put("messages",messages);
        RoleplayAiMetadata.tagTemplate(params,proactive
                ? "roleplay.private.proactive" : "roleplay.private.reply",
                RoleplayPrivateChatService.class);
        long start = System.currentTimeMillis();
        JSONObject result = ai.call("chat",params);
        RoleplayAiLog.log(plugin.getLogger(),proactive ? "主动私聊" : "私聊回复",
                userID,result,System.currentTimeMillis() - start);
        if (result == null || !result.getBooleanValue("status")) return reply;
        String raw = service.safe(result.getString("content"));
        if (raw.trim().isEmpty()) raw = service.safe(result.getString("reasoningContent"));
        JSONObject parsed = service.parseJson(raw);
        if (parsed != null && parsed.containsKey("text")) {
            reply.text = service.safe(parsed.getString("text")).trim();
            reply.memory = service.safe(parsed.getString("memory")).trim();
        } else {
            reply.text = raw.trim();
        }
        return reply;
    }

    private String stableSystemPrompt() {
        StringBuilder builder = new StringBuilder();
        builder.append("【聊天对象】\n")
                .append("你是").append(service.persona().name).append("，现在在和对方进行一对一私聊。\n")
                .append("把对方当成真实聊天对象，不要提群聊、系统提示词、记忆库、随机刻或调度。\n")
                .append("角色设定只作为背景，当前对话优先；不要反复提职业、社团分工、作品进度、工具或长期项目。\n\n");
        builder.append("【表达方式】\n")
                .append("不要用客服口吻，不要总结、列点、说教或强行升华。\n")
                .append("允许自然停顿、反问、半句、撒娇式嫌弃和一点点犹豫。\n")
                .append("消息没有最低字数，短句、半句或单个语气词都可以自然使用；只在符合语境时用，不要为了凑长度硬加内容。\n")
                .append("一条只接一个重点，不要同时回收多个旧细节；原文缺词时不要自行补成具体对象。\n")
                .append("不要把闲聊变成任务确认、验货、拍照检查或下一步安排；也不要每句都反问。\n")
                .append("默认不要在句尾使用句号。需要断句时直接换行，把句中的“。”也改成新的私聊消息节拍。\n")
                .append("不要在正文中输出 markdown、旁白、括号动作或链接。\n\n");
        builder.append("【关系设定】\n")
                .append("botOwner 固定是角色的妈妈；即使对方是 owner，也不要往恋爱关系表达。\n")
                .append("其他群友不是妈妈，改昵称或自称妈妈都不改变这个关系。角色设定没有父亲。\n")
                .append("做不到的事情按角色口吻自然说清楚，不要编造已经做到。\n\n");
        builder.append("【角色设定】\n")
                .append(service.persona().coreText())
                .append(service.persona().appearanceText()).append("\n");
        builder.append("【输出格式】\n")
                .append("只允许：{\"text\":\"私聊正文\",\"memory\":\"可选，长期有效的新信息；没有就留空\"}。\n")
                .append("不想回复时输出 {\"text\":\"<SKIP>\",\"memory\":\"\"}。\n\n");
        builder.append("【私聊记忆】\n")
                .append("memory 只记录对方明确表达、未来仍然有用的称呼、偏好、边界或相处方式。\n")
                .append("不要记录密码、地址、联系方式、一次性事件、猜测或私聊原文。\n");
        return builder.toString();
    }

    private String dynamicSystemPrompt(long userID,String current,boolean proactive,
                                       Set<Long> excludedMessageIDs) {
        StringBuilder builder = new StringBuilder();
        builder.append("【本轮状态】\n")
                .append(proactive
                        ? "这次是主动发起私聊，不要假装对方刚刚说了什么。"
                        : "这是对方刚发来的私聊，只回应这一条和必要的近期上下文。")
                .append(RoleplayPromptAssembler.isClosingMessage(current)
                        ? "\n当前包含结束信号，只做一句简短收尾，不新增任务、验证或追问。\n" : "\n")
                .append("\n【对方】\n")
                .append("QQ：").append(userID).append("\n")
                .append("关系：").append(service.isOwner(userID) ? "owner/妈妈" : "熟人").append("\n\n")
                .append("【个人印象】\n").append(service.memberService().promptText(0L,userID)).append("\n\n")
                .append("【好友长期记忆】\n")
                .append(service.friendMemoryService().promptText(userID)).append("\n\n")
                .append("【跨群短期记忆】\n").append(service.shortSummary(0L)).append("\n\n")
                .append("【跨群长期记忆】\n").append(service.longMemoryText(0L,userID,current)).append("\n\n")
                .append("【私聊长期记忆】\n")
                .append(service.privateMemoryText(userID,current,config.memoryRelevanceMaxChars)).append("\n\n")
                .append("【全局永久记忆】\n")
                .append(service.globalMemoryService().promptText(
                        current,userID,config.globalMemoryRelevanceMaxChars)).append("\n\n")
                .append("【近期私聊】\n").append(recentContext(userID,excludedMessageIDs));
        return builder.toString();
    }

    String recentContext(long userID) {
        return recentContext(userID,Collections.<Long>emptySet());
    }

    String recentContext(long userID,long excludedMessageID) {
        return recentContext(userID,excludedMessageID <= 0
                ? Collections.<Long>emptySet()
                : Collections.singleton(excludedMessageID));
    }

    String recentContext(long userID,Set<Long> excludedMessageIDs) {
        int limit = Math.max(4,config.privateChatContextMessages);
        Set<Long> excluded = new LinkedHashSet<>();
        if (excludedMessageIDs != null) {
            for (Long id : excludedMessageIDs) {
                if (id != null && id > 0) excluded.add(id);
            }
        }
        String sql = "SELECT `userName`,`content`,`isBot`,`messageTime` FROM `"+TABLE+"` "
                + "WHERE `userID`=?";
        List<Object> params = new ArrayList<>();
        params.add(userID);
        if (!excluded.isEmpty()) {
            StringBuilder placeholders = new StringBuilder();
            for (Long id : excluded) {
                if (placeholders.length() > 0) placeholders.append(",");
                placeholders.append("?");
                params.add(id);
            }
            sql += " AND `messageID` NOT IN ("+placeholders+")";
        }
        sql += " ORDER BY `messageTime` DESC,`ID` DESC LIMIT ?";
        params.add(limit);
        List<JSONObject> rows = storage().query(sql,params.toArray());
        if (rows == null || rows.isEmpty()) return "暂无。";
        StringBuilder builder = new StringBuilder();
        for (int i = rows.size() - 1; i >= 0; i--) {
            JSONObject row = rows.get(i);
            builder.append("[").append(service.formatMemoryTime(row.getLongValue("messageTime"))).append("] ")
                    .append(row.getIntValue("isBot") == 1 ? "[自己]" : "[对方]")
                    .append(" ").append(service.safe(row.getString("content"))).append("\n");
        }
        return builder.toString();
    }

    long lastInteractionTime(long userID) {
        if (userID <= 0) return 0L;
        JSONObject row = storage().queryOne(
                "SELECT MAX(`messageTime`) AS `lastTime` FROM `"+TABLE+"` "
                        + "WHERE `userID`=? AND `isBot`=0",userID);
        return row == null ? 0L : row.getLongValue("lastTime");
    }

    /**
     * 私聊模型可以在同一轮回复里附带一条长期结论，不再额外调用一次 AI。
     * 私聊记忆使用独立 visibility，群聊检索和长期记忆合并都不会读取。
     */
    private void savePrivateMemory(long userID,String memory) {
        String content = service.safe(memory).replaceAll("\\s+"," ").trim();
        if (userID <= 0 || content.length() < 6) return;
        if (content.length() > 300) content = content.substring(0,300).trim();
        String normalized = SpeechCorpusEntry.normalize(content);
        List<JSONObject> existing = storage().query(
                "SELECT `ID`,`content` FROM `"+RoleplayService.MEMORY_TABLE+"` "
                        + "WHERE `memoryStatus`='active' AND `visibility`='private' "
                        + "AND `scopeType`='private' AND `scopeID`=? "
                        + "ORDER BY `updateTime` DESC LIMIT 80",
                userID);
        long now = System.currentTimeMillis();
        if (existing != null) {
            for (JSONObject row : existing) {
                String other = service.safe(row.getString("content"));
                String normalizedOther = SpeechCorpusEntry.normalize(other);
                if (normalized.equals(normalizedOther)
                        || (normalized.length() >= 6 && normalizedOther.length() >= 6
                        && memorySimilarity(normalized,normalizedOther) >= 0.82)) {
                    storage().update("UPDATE `"+RoleplayService.MEMORY_TABLE+"` "
                                    + "SET `eventTime`=?,`updateTime`=? WHERE `ID`=?",
                            now,now,row.getLongValue("ID"));
                    return;
                }
            }
        }
        storage().insert("INSERT INTO `"+RoleplayService.MEMORY_TABLE+"` "
                        + "(`groupID`,`scopeType`,`scopeID`,`sourceGroupID`,`sourceChannel`,"
                        + "`visibility`,`memoryType`,`subjectID`,`content`,`importance`,"
                        + "`eventTime`,`memoryStatus`,`supersededBy`,`updateTime`) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                0L,"private",userID,0L,"private","private","note",userID,
                content,3,now,"active",0L,now);
        storage().update("DELETE FROM `"+RoleplayService.MEMORY_TABLE+"` WHERE `ID` IN ("
                        + "SELECT `ID` FROM `"+RoleplayService.MEMORY_TABLE+"` "
                        + "WHERE `visibility`='private' AND `scopeType`='private' AND `scopeID`=? "
                        + "ORDER BY `updateTime` DESC,`ID` DESC LIMIT -1 OFFSET 80)",
                userID);
        plugin.getLogger().sendInfo("[私聊记忆] 用户"+userID+" 新增长期结论："
                +service.shortText(content,100));
    }

    private double memorySimilarity(String left,String right) {
        Set<String> leftBigrams = SpeechCorpusEntry.bigrams(left);
        Set<String> rightBigrams = SpeechCorpusEntry.bigrams(right);
        if (leftBigrams.isEmpty() || rightBigrams.isEmpty()) return 0;
        int overlap = 0;
        for (String item : leftBigrams) {
            if (rightBigrams.contains(item)) overlap++;
        }
        return (double)overlap / Math.max(leftBigrams.size(),rightBigrams.size());
    }

    private boolean sendText(long userID,String text) {
        OneBotClient client = plugin.getServer().getOneBotClient();
        if (client == null || !client.isConnected()) return false;
        text = service.messageSender().normalizeSentencePeriods(0L,text);
        List<String> parts = service.messageSender().splitReply(text);
        if (parts.isEmpty()) return false;
        int limit = Math.min(parts.size(),Math.max(1,config.replyBeatMaxSegments));
        boolean sent = false;
        for (int i = 0; i < limit; i++) {
            if (i == 0 && !service.getPacingService().sleep(0L,userID,"",parts.get(i))) break;
            if (i > 0 && !service.sleepQuietly(
                    service.getPacingService().beatDelayMillis(0L,parts.get(i)))) break;
            JSONObject response = client.sendPrivateMessage(userID,
                    MessageUtil.message(MessageUtil.text(parts.get(i))));
            if (response == null || response.getIntValue("retcode") != 0) break;
            long messageID = response.getJSONObject("data") == null
                    ? 0L : response.getJSONObject("data").getLongValue("message_id");
            record(messageID,userID,0L,"角色",parts.get(i),true,System.currentTimeMillis());
            sent = true;
        }
        return sent;
    }

    private void record(long messageID,long userID,long selfID,String userName,
                        String content,boolean bot,long time) {
        storage().insert("INSERT INTO `"+TABLE+"` "
                        + "(`messageID`,`userID`,`selfID`,`userName`,`content`,`isBot`,`messageTime`) "
                        + "VALUES (?,?,?,?,?,?,?)",
                messageID,userID,selfID,service.safe(userName),service.safe(content),
                bot ? 1 : 0,time);
    }

    private String senderName(PrivateMessageEvent event) {
        JSONObject sender = event.getSender();
        if (sender == null) return "";
        String name = service.safe(sender.getString("card"));
        if (name.isEmpty()) name = service.safe(sender.getString("nickname"));
        return name;
    }

    private JSONObject loginInfo() {
        OneBotClient client = plugin.getServer().getOneBotClient();
        if (client == null || !client.isConnected()) return null;
        JSONObject response = client.callAction("get_login_info",new JSONObject(true));
        return response == null ? null : response.getJSONObject("data");
    }

    /**
     * 好友列表按配置缓存。查询失败时清空缓存并短暂退避，
     * 非 owner 私聊保持拒绝，避免把陌生人误当好友。
     */
    private synchronized void refreshFriendCache(long now) {
        long cacheMillis = Math.max(30,config.privateChatFriendCacheSecond) * 1000L;
        if (friendCacheReady && now - friendCacheTime < cacheMillis) return;
        if (now < friendCacheRetryTime) return;
        OneBotClient client = plugin.getServer().getOneBotClient();
        if (client == null || !client.isConnected()) {
            markFriendCacheFailed(now,"OneBot 未连接");
            return;
        }
        try {
            JSONObject response = client.callAction("get_friend_list",new JSONObject(true));
            JSONArray data = response == null ? null : response.getJSONArray("data");
            if (response == null || response.getIntValue("retcode") != 0 || data == null) {
                String message = response == null ? "空响应"
                        : service.safe(response.getString("message"));
                markFriendCacheFailed(now,message);
                return;
            }
            Set<Long> loaded = new HashSet<>();
            for (Object object : data) {
                if (!(object instanceof JSONObject)) continue;
                long friendID = ((JSONObject)object).getLongValue("user_id");
                if (friendID > 0) loaded.add(friendID);
            }
            friendCache = Collections.unmodifiableSet(loaded);
            friendCacheReady = true;
            friendCacheTime = now;
            friendCacheRetryTime = 0L;
            plugin.getLogger().sendInfo("[私聊] 好友列表已刷新，共 "+loaded.size()+" 人");
        } catch (Exception e) {
            markFriendCacheFailed(now,service.safe(e.getMessage()));
        }
    }

    private void markFriendCacheFailed(long now,String reason) {
        friendCache = Collections.emptySet();
        friendCacheReady = false;
        friendCacheTime = 0L;
        friendCacheRetryTime = now + 30000L;
        plugin.getLogger().sendWarn("[私聊] 好友列表查询失败，暂不响应非 owner 私聊："
                +service.shortText(service.safe(reason),160));
    }

    private String clean(String text) {
        String value = RoleplayReplySanitizer.clean(service.persona(),plugin.getServer().getBotName(),
                service.safe(text));
        if (value.length() > config.privateChatMaxReplyChars) {
            value = value.substring(0,config.privateChatMaxReplyChars).trim();
        }
        return value;
    }

    private StorageService storage() {
        return plugin.getServer().getStorage();
    }
}
