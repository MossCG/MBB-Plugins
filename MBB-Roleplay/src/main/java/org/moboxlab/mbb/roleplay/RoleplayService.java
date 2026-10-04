package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Event.GroupMessageEvent;
import org.moboxlab.moboxbot.API.OneBot.MessageUtil;
import org.moboxlab.moboxbot.API.OneBot.OneBotClient;
import org.moboxlab.moboxbot.API.Plugin;
import org.moboxlab.moboxbot.API.PluginService;
import org.moboxlab.moboxbot.API.Storage.StorageService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
    private volatile RoleplayConfig config;
    private volatile RoleplayPersona persona;
    private final Map<Long,Long> lastReplyMap = new HashMap<>();
    private final Map<Long,Long> lastReplyUserMap = new HashMap<>();
    private final Map<Long,Long> lastBotMessageMap = new HashMap<>();
    private final Map<Long,Integer> messageCountMap = new HashMap<>();
    private final Map<Long,long[]> replyRateMap = new HashMap<>();
    private final Map<Long,Boolean> memoryUpdatingMap = new HashMap<>();
    private final Map<Long,String> pendingMemoryMap = new HashMap<>();
    private final Map<Long,Integer> otherRoleMessageStreakMap = new HashMap<>();
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
            "(?is)<\\s*global_remember\\s*>(.*?)<\\s*/\\s*global_remember\\s*>");
    private static final Pattern GLOBAL_REMEMBER_OPEN = Pattern.compile(
            "(?is)<\\s*global_remember\\s*/?\\s*>");

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

    public RoleplayService(Plugin plugin,RoleplayConfig config,RoleplayPersona persona) {
        this.plugin = plugin;
        this.config = config;
        this.persona = persona;
        this.reminderService = new RoleplayReminderService(plugin,config,persona);
        this.globalMemoryService = new RoleplayGlobalMemoryService(plugin,config);
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
    }

    public void reload(RoleplayConfig config,RoleplayPersona persona) {
        this.config = config;
        this.persona = persona;
        reminderService.reload(config,persona);
        globalMemoryService.reload(config);
    }

    public void handle(GroupMessageEvent event) {
        if (event == null || !config.enable) return;
        long groupID = event.getGroupID();
        if (!isGroupEnabled(groupID)) return;
        long selfID = event.getRaw().getLongValue("self_id");
        if (selfID > 0 && selfID == event.getUserID()) return;
        if (containsIgnoredContent(event.getMessage())) return;
        String content = extractContent(event.getMessage());
        if (content == null || content.trim().isEmpty()) return;
        if (isCommand(content)) return;
        boolean otherRoleBot = isOtherRoleBot(event,selfID);
        int otherRoleStreak = updateOtherRoleMessageStreak(groupID,otherRoleBot);
        recordMessage(event,content,false);
        int count = countMessage(groupID);
        if (count >= config.memoryUpdateMessages) {
            messageCountMap.put(groupID,0);
            triggerMemory(groupID,"定时整理");
        }
        if (isAddressedToOtherRole(event,content,selfID)) {
            plugin.getLogger().sendInfo("[角色] 群"+groupID+" 跳过指向其他角色的消息："
                    +shortText(content,80));
            return;
        }
        if (otherRoleBot && isReminderNotification(content)) return;
        if (!otherRoleBot && reminderService.handle(event,content)) return;
        if (otherRoleBot && otherRoleStreak > config.maxConsecutiveOtherRoleMessages) return;

        boolean direct = isDirect(event,content,selfID);
        boolean sameUserContinuation = isContinuation(groupID,event.getUserID());
        boolean groupActive = isGroupActive(groupID);
        boolean interest = persona.matchesInterest(content);
        if (!direct && !sameUserContinuation && !groupActive && !interest) return;
        double chance = config.interestReplyChance;
        if (otherRoleBot) chance = config.otherRoleBotReplyChance;
        else if (direct) chance = 1.0;
        else if (sameUserContinuation) chance = config.continuationReplyChance;
        else if (groupActive) chance = config.otherParticipantReplyChance;
        if (content.length() < config.minMessageLength || Math.random() >= chance) return;
        if (!canReply(groupID)) return;
        String userName = senderName(event);
        String relationship = relationshipLabel(event,otherRoleBot);
        JSONObject result = reply(groupID,event.getUserID(),userName,content,otherRoleBot,relationship);
        if (result == null || !result.getBooleanValue("status")) return;
        String rawReply = safe(result.getString("content")).trim();
        ReminderMarkerResult reminderMarker = extractReminderMarker(rawReply);
        GlobalRememberResult globalRemember = extractGlobalRemember(reminderMarker.reply);
        RememberResult rememberResult = extractRemember(globalRemember.reply);
        String reply = rememberResult.reply.trim();
        if (reminderMarker.requested) {
            String actionResult = reminderService.executeAiAction(groupID,event.getUserID(),userName,
                    relationship,reminderMarker.action,reminderMarker.id,reminderMarker.time,
                    reminderMarker.task,reminderMarker.target);
            if (actionResult != null && !actionResult.isEmpty()) {
                reminderService.sendAt(groupID,event.getUserID(),
                        RoleplayReminderService.REMINDER_MARKER+actionResult);
            }
        }
        if (rememberResult.requested && config.activeMemory) {
            plugin.getLogger().sendInfo("[记忆] 群"+groupID+" 角色主动请求记忆");
            triggerMemory(groupID,"主动记忆",rememberResult.memory);
        }
        if (globalRemember.requested) {
            if (globalMemoryService.isLearnGroup(groupID)) {
                globalMemoryService.save("note",globalRemember.content,3,groupID,event.getUserID());
            } else {
                plugin.getLogger().sendWarn("[永久记忆] 群"+groupID
                        +" 不在学习白名单，忽略 <global_remember>");
            }
        }
        if (reply.isEmpty() || "<SKIP>".equalsIgnoreCase(reply)) return;
        if (shouldSuppressRepeat(groupID,reply)) {
            plugin.getLogger().sendInfo("[角色] 群"+groupID+" 跳过重复回复："+shortText(reply,80));
            return;
        }
        OneBotClient client = plugin.getServer().getOneBotClient();
        if (client == null) return;
        sendReply(client,groupID,selfID,event.getUserID(),reply);
    }

    public JSONObject status(long groupID) {
        JSONObject result = new JSONObject(true);
        result.put("status",true);
        result.put("groupID",groupID);
        result.put("role",persona.name);
        result.put("groupEnabled",groupID <= 0 || isGroupEnabled(groupID));
        result.put("shortSummary",shortSummary(groupID));
        result.put("memoryCount",memoryCount(groupID));
        return result;
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
        result.put("memories",longMemories(groupID));
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
        otherRoleMessageStreakMap.remove(groupID);
        lastReplyMap.remove(groupID);
        lastReplyUserMap.remove(groupID);
        lastBotMessageMap.remove(groupID);
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

    public int ruleLikeCount() {
        return persona.interests.size();
    }

    private JSONObject reply(long groupID,long userID,String userName,String content,
                             boolean otherRoleBot,String relationship) {
        PluginService ai = plugin.getServer().getPluginManager().getService("MBB-AI");
        if (ai == null) return null;
        JSONArray messages = new JSONArray();
        messages.add(message("system",buildSystemPrompt(groupID,userID,otherRoleBot,relationship)));
        messages.add(message("user","当前发言者："+(userName == null ? "" : userName)+"（QQ："+userID+"）\n"
                +"当前关系："+relationship+"\n"
                +"当前消息：\n"+content+"\n\n最近群聊上下文：\n"+recentContext(groupID)));
        JSONObject params = new JSONObject(true);
        params.put("profile",config.aiProfile);
        params.put("maxTokens",config.replyMaxTokens);
        params.put("sessionId","roleplay-"+groupID+"-"+contextToken(groupID));
        params.put("messages",messages);
        return ai.call("chat",params);
    }

    private String buildSystemPrompt(long groupID,long userID,boolean otherRoleBot,String relationship) {
        String recentReplies = recentRoleReplyText(groupID);
        return persona.description()+"\n\n"
                +"长期记忆：\n"+longMemoryText(groupID)+"\n"
                +"全局永久记忆：\n"+globalMemoryService.promptText()+"\n"
                +"短期记忆：\n"+shortSummary(groupID)+"\n"
                +"你最近说过的话：\n"+recentReplies+"\n"
                +"当前时间："+currentTimeText()+"\n"
                +"当前发言者关系："+relationship+"\n"
                +"当前用户的待触发提醒：\n"+reminderService.pendingText(groupID,userID)+"\n"
                +"关系规则：群主和管理员若不是另一个角色机器人，统一视为老师；其他真人成员都是朋友；"
                +"另一个角色机器人不按群权限归类，按角色设定中的同伴关系处理。"
                +"对老师要尊重、亲近、自然；对朋友要平等、轻松、友善。不要强行把朋友叫成老师。"
                +"所有真人成员的初始好感度为 "+config.initialAffinity+"/100，整体保持较高好感，"
                +"愿意接话、帮忙和分享，不要冷淡、疏远或无缘无故防备。"
                +"规则：你像群里一个普通成员一样自然聊天，不是客服、助手或问答机器人。"
                +"只有话题符合你的兴趣，或有人直接艾特、回复、提及你时才回复。"
                +"群里每个 QQ 都是不同的人，必须区分发言者，不能把不同群员当成同一个人。"
                +"如果其他群员正在接续当前话题，可以自然参与；如果只是无关话题，只输出 <SKIP>。"
                +(otherRoleBot ? "当前发言者是另一个角色机器人。不要和另一个机器人旁若无人地连续互动，"
                +"只有对方明确叫你、提出新问题、或真人正在参与时才简短回应；不要追问、捧哏或主动延长话题。" : "")
                +"如果这条消息不适合参与，只输出 <SKIP>。"
                +"尽量只回复一句话，短句优先，不要分多段。"
                +"不要使用“稳、没丢、记下、记账上”这些词，也不要使用“收到、记住了、已记录、明白、为你”等助理式确认。"
                +"消除 AI 味：不要总结、复述、列点、解释或给出完整方案，不要像客服一样端着说话。"
                +"像真人 QQ 聊天一样直接接话，可以省略主语，偶尔短促、吐槽、反问或只接半句。"
                +"少用破折号，不要用“——”；需要表示拖长音时使用波浪号，例如“欸~”。"
                +"如果话题涉及今天、现在、日期、周末、早晚或时间安排，必须以“当前时间”为准，不要自行猜测日期。"
                +"口癖要低频自然，不要每句话都玩游戏梗。"
                +"不要复述自己最近说过的话，也不要换同义词继续重复同一个细节。"
                +"同一件小事最多回应一次，除非出现了明确的新进展；没有新信息时只输出 <SKIP>。"
                +"不要固定使用同一句式或同一开头。像“姐姐……”“哼哼！”这类口癖在最近几条回复里出现过时，"
                +"必须换一种自然说法；最近 5 条回复中，同一种开头最多出现一次。"
                +"不要把“嗯”“嗯……”当作固定开场；最近 3 条回复里已经出现过“嗯”开头时，必须换一种直接的说法。"
                +"若使用“邦邦咔邦”，必须放在回复句首，像任务启动提示音，不要放在句中或句尾。"
                +"你能理解角色设定中列出的社区梗和别名，但不要主动频繁使用；别人玩梗时再自然接住。"
                +(config.activeMemory ? "如果当前内容出现了值得长期记忆的新人物信息、稳定偏好、重要事件、群梗，"
                +"或你自己的重要承诺与行为，把聊天正文写在 <remember> 前，把要记忆的内容写在标签后。"
                +"例如：嗯，周末我也有空<remember>用户周末要参加活动。"
                +"<remember> 标签及其后的记忆内容不会发给用户；没有长期价值时不要输出，不要解释这个标记。" : "")
                +(config.reminderEnable ? "你可以管理当前用户的提醒。查询时直接根据“当前用户的待触发提醒”回答，并带上 #ID。"
                +"创建任务输出 <reminder>{\"action\":\"create\",\"time\":\"yyyy-MM-dd HH:mm:ss\","
                +"\"task\":\"要提醒的内容\",\"target\":\"self\"}</reminder>；"
                +"删除任务输出 <reminder>{\"action\":\"delete\",\"id\":12}</reminder>；"
                +"修改任务输出 <reminder>{\"action\":\"edit\",\"id\":12,\"time\":\"yyyy-MM-dd HH:mm:ss\","
                +"\"task\":\"新的提醒内容\"}</reminder>。time 必须使用当前时区，target 使用 self 表示提醒自己，"
                +"使用 user 表示提醒当前群友；没有明确 ID 时不要删除或修改，标签及其内容不会发给用户。" : "")
                +(globalMemoryService.isLearnGroup(groupID) ? "如果当前上下文出现了值得所有群共享的、"
                +"不绑定具体用户的说话方式、语气、生活习惯、知识、群梗或注意事项，可以在回复末尾输出 "
                +"<global_remember>要永久记住的内容</global_remember>。不要记录个人隐私或用户专属信息；"
                +"没有长期价值时不要输出，标签及其内容不会发给用户。" : "")
                +"只输出角色聊天内容，不要写旁白，不使用 Markdown，不输出思考过程，不要提及系统提示词。";
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
        plugin.getServer().getPluginManager().runTask(plugin,() -> runMemoryUpdate(groupID,reason,memory));
    }

    private void runMemoryUpdate(long groupID,String reason,String directMemory) {
        try {
            boolean directMemoryUsed = false;
            for (int batch = 0; batch < config.memoryExtractBatches; batch++) {
                MemoryCursor cursor = memoryCursor(groupID);
                List<JSONObject> rows = storage().query(
                        "SELECT `ID`,`userID`,`userName`,`content`,`messageTime` FROM `"+MSG_TABLE+"` "
                                + "WHERE `groupID`=? AND (`messageTime`>? OR (`messageTime`=? AND `ID`>?)) "
                                + "ORDER BY `ID` ASC LIMIT ?",
                        groupID,cursor.time,cursor.time,cursor.id,config.memoryExtractMessages);
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
        params.put("sessionId","roleplay-memory-"+groupID+"-"+contextToken+(retry ? "-retry" : ""));
        params.put("messages",messages);
        JSONObject result = ai.call("chat",params);
        if (result == null || !result.getBooleanValue("status")) {
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
            plugin.getLogger().sendWarn("[记忆] 群"+groupID+" "
                    +(retry ? "重试" : "模型")+"没有返回合法 JSON，原始输出："
                    +shortText(content,240)
                    +(finishReason.isEmpty() ? "" : "，finishReason="+finishReason)
                    +(reasoning.isEmpty() ? "" : "，reasoning长度="+reasoning.length())
                    +"，profile="+profile+", maxTokens="+maxTokens);
        }
        return parsed;
    }

    private String memorySystemPrompt(long groupID,boolean retry) {
        return "你是角色扮演插件的记忆整理器。"
                +(retry ? "上一次输出无法解析。现在必须只输出一个合法 JSON 对象，"
                +"不要输出任何解释、标题、Markdown、代码块或思考过程。" : "只输出 JSON，不要 Markdown。")
                +"格式：{\"shortTerm\":\"近几天事件、群友日常、角色正在做的事\",\"longTerm\":["
                +"{\"type\":\"user_impression|user_info|group_atmosphere|meme|self_action|topic\","
                +"\"subjectID\":0,\"content\":\"记忆内容\",\"importance\":1}]}。"
                +"群成员较多时尽量记录更多有长期价值的用户印象、用户信息、群内氛围、群梗和角色行为，"
                +"longTerm 最多输出 20 条。只记录有长期价值的信息，忽略普通寒暄、重复聊天和表情。"
                +"如果输入中包含“角色主动标记的记忆内容”，必须优先把其中的长期价值整理进 longTerm。"
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
            source.append("角色主动标记的记忆内容：").append(directMemory.trim()).append("\n");
        }
        for (JSONObject row : rows) {
            source.append(safe(row.getString("userName"))).append("：")
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
            return true;
        }
        storage().insert("INSERT INTO `"+MEMORY_TABLE+"` (`groupID`,`memoryType`,`subjectID`,`content`,`importance`,`updateTime`) VALUES (?,?,?,?,?,?)",
                groupID,type,subjectID,content,importance,System.currentTimeMillis());
        return true;
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
                "SELECT `userName`,`content`,`isBot` FROM `"+MSG_TABLE+"` "
                        + "WHERE `groupID`=? ORDER BY `messageTime` DESC,`ID` DESC LIMIT ?",
                groupID,Math.max(20,config.recentReplyCheckCount * 3));
        if (rows == null || rows.isEmpty()) return result;
        List<JSONObject> selected = new ArrayList<>();
        for (JSONObject row : rows) {
            if (row.getIntValue("isBot") == 1 || isRoleParticipantName(row.getString("userName"))) {
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
                "SELECT `userName`,`content` FROM `"+MSG_TABLE+"` WHERE `groupID`=? ORDER BY `messageTime` DESC LIMIT ?",
                groupID,config.shortContextMessages);
        if (rows == null || rows.isEmpty()) return "无";
        StringBuilder builder = new StringBuilder();
        for (int i = rows.size() - 1; i >= 0; i--) {
            builder.append(safe(rows.get(i).getString("userName"))).append("：")
                    .append(safe(rows.get(i).getString("content"))).append("\n");
        }
        return builder.toString();
    }

    private String longMemoryText(long groupID) {
        JSONArray memories = longMemories(groupID);
        if (memories.isEmpty()) return "暂无长期记忆。";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < memories.size(); i++) {
            JSONObject item = memories.getJSONObject(i);
            builder.append("- [").append(safe(item.getString("type"))).append("] ")
                    .append(safe(item.getString("content"))).append("\n");
        }
        return builder.toString();
    }

    private JSONArray longMemories(long groupID) {
        JSONArray result = new JSONArray();
        List<JSONObject> rows = storage().query(
                "SELECT `memoryType`,`subjectID`,`content`,`importance` FROM `"+MEMORY_TABLE+"` "
                        + "WHERE `groupID`=? ORDER BY `importance` DESC,`updateTime` DESC LIMIT ?",
                groupID,config.maxLongMemories);
        if (rows == null) return result;
        for (JSONObject row : rows) {
            JSONObject item = new JSONObject(true);
            item.put("type",row.getString("memoryType"));
            item.put("subjectID",row.getLongValue("subjectID"));
            item.put("content",row.getString("content"));
            item.put("importance",row.getIntValue("importance"));
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

    private void sendReply(OneBotClient client,long groupID,long selfID,long userID,String reply) {
        List<String> segments = splitReply(reply);
        if (segments.isEmpty()) return;
        int count = Math.min(segments.size(),config.replyMaxSegments);
        for (int i = 0; i < count; i++) {
            JSONObject response = client.sendGroupMessage(groupID,MessageUtil.message(MessageUtil.text(segments.get(i))));
            if (response != null && response.getIntValue("retcode") == 0) {
                long messageID = response.getJSONObject("data") == null
                        ? 0L : response.getJSONObject("data").getLongValue("message_id");
                lastBotMessageMap.put(groupID,messageID);
                recordBotMessage(groupID,selfID,segments.get(i),messageID);
                lastReplyUserMap.put(groupID,userID);
            }
            if (i + 1 < count) {
                try {
                    Thread.sleep(250L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private List<String> splitReply(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return result;
        String normalized = text.replace("\\n","\n").replace("\r\n","\n").replace("\r","\n");
        String compact = normalized.replaceAll("\\s*\\n\\s*"," ").replaceAll("\\s+"," ").trim();
        addParagraph(result,compact,config.replySegmentMaxChars);
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
                char c = text.charAt(i);
                if (c == '。' || c == '！' || c == '？' || c == '!' || c == '?' || c == '；' || c == ';') {
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

    private String relationshipLabel(GroupMessageEvent event,boolean otherRoleBot) {
        if (otherRoleBot) return "其他角色机器人";
        JSONObject sender = event == null ? null : event.getSender();
        String role = sender == null ? "" : safe(sender.getString("role")).trim().toLowerCase(Locale.CHINA);
        if ("owner".equals(role) || "admin".equals(role)) return "老师";
        return "朋友";
    }

    private boolean isAddressedToOtherRole(GroupMessageEvent event,String content,long selfID) {
        if (event != null && event.getMessage() != null) {
            JSONArray message = event.getMessage();
            for (int i = 0; i < message.size(); i++) {
                JSONObject segment = message.getJSONObject(i);
                if (segment == null || !"at".equals(segment.getString("type"))) continue;
                JSONObject data = segment.getJSONObject("data");
                long atID = data == null ? 0L : data.getLongValue("qq");
                if (atID > 0 && atID != selfID) return true;
            }
        }
        String text = content == null ? "" : content.trim();
        if (text.startsWith("@")) {
            text = text.replaceFirst("^@[0-9]+\\s*","").trim();
        }
        String names = config.otherRoleBotNames+","+RoleplayConfig.DEFAULT_OTHER_ROLE_BOT_NAMES;
        for (String item : names.split(",")) {
            String name = item.trim();
            if (name.isEmpty() || isOwnRoleName(name)) continue;
            if (startsWithAlias(text,name)) return true;
        }
        return false;
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

    private boolean canReply(long groupID) {
        long now = System.currentTimeMillis();
        Long last = lastReplyMap.get(groupID);
        if (last != null && now - last < config.replyCooldownSecond * 1000L) return false;
        long hour = now / 3600000L;
        long[] rate = replyRateMap.get(groupID);
        if (rate == null || rate[0] != hour) {
            rate = new long[]{hour,1};
            replyRateMap.put(groupID,rate);
        } else {
            if (rate[1] >= config.maxRepliesPerHour) return false;
            rate[1]++;
        }
        lastReplyMap.put(groupID,now);
        return true;
    }

    private boolean isDirect(GroupMessageEvent event,String content,long selfID) {
        boolean mentionedSelf = false;
        boolean mentionedOther = false;
        JSONArray message = event.getMessage();
        if (message != null) {
            Long lastBot = lastBotMessageMap.get(event.getGroupID());
            for (int i = 0; i < message.size(); i++) {
                JSONObject segment = message.getJSONObject(i);
                if (segment == null) continue;
                JSONObject data = segment.getJSONObject("data");
                if ("at".equals(segment.getString("type")) && data != null) {
                    long atID = data.getLongValue("qq");
                    if (atID == selfID) mentionedSelf = true;
                    else if (atID > 0) mentionedOther = true;
                }
                if ("reply".equals(segment.getString("type")) && data != null && lastBot != null
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

    private boolean startsWithAlias(String text,String alias) {
        if (text == null || alias == null || alias.trim().isEmpty()) return false;
        String value = text.trim();
        String name = alias.trim();
        return value.startsWith(name) || value.startsWith("@"+name);
    }

    private boolean isContinuation(long groupID,long userID) {
        Long last = lastReplyMap.get(groupID);
        Long lastUser = lastReplyUserMap.get(groupID);
        return last != null && lastUser != null && lastUser == userID
                && System.currentTimeMillis() - last <= config.conversationWindowSecond * 1000L;
    }

    private boolean isGroupActive(long groupID) {
        Long last = lastReplyMap.get(groupID);
        return last != null && System.currentTimeMillis() - last <= config.conversationWindowSecond * 1000L;
    }

    private int updateOtherRoleMessageStreak(long groupID,boolean otherRoleBot) {
        if (!otherRoleBot) {
            otherRoleMessageStreakMap.remove(groupID);
            return 0;
        }
        Integer streak = otherRoleMessageStreakMap.get(groupID);
        streak = streak == null ? 0 : streak;
        streak++;
        otherRoleMessageStreakMap.put(groupID,streak);
        return streak;
    }

    private boolean isOtherRoleBot(GroupMessageEvent event,long selfID) {
        if (event == null || event.getUserID() <= 0 || event.getUserID() == selfID) return false;
        if (containsCSVLong(config.otherRoleBotQQs,event.getUserID())) return true;
        return isRoleParticipantName(senderName(event));
    }

    private boolean isRoleParticipantName(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        String value = name.trim();
        for (String item : config.otherRoleBotNames.split(",")) {
            String key = item.trim();
            if (!key.isEmpty() && (value.equalsIgnoreCase(key)
                    || (key.length() >= 2 && value.contains(key)))) return true;
        }
        return false;
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
            return new GlobalRememberResult(true,reply,pair.group(1).trim());
        }
        Matcher open = GLOBAL_REMEMBER_OPEN.matcher(text);
        if (open.find()) {
            String reply = text.substring(0,open.start()).trim();
            String content = text.substring(open.end()).trim();
            return new GlobalRememberResult(true,reply,content);
        }
        return new GlobalRememberResult(false,text.trim(),"");
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
            else if ("image".equals(type)) builder.append("[图片]");
            else if ("at".equals(type)) builder.append("@").append(data == null ? "" : safe(data.getString("qq")));
            else if ("face".equals(type)) builder.append("[表情]");
        }
        return builder.toString().trim();
    }

    private boolean containsIgnoredContent(JSONArray message) {
        if (message == null) return false;
        for (int i = 0; i < message.size(); i++) {
            JSONObject segment = message.getJSONObject(i);
            if (segment == null) continue;
            String type = segment.getString("type");
            if ("image".equals(type) || "mface".equals(type) || "face".equals(type)) return true;
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
