package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 角色扮演与记忆服务
 */
public class RoleplayService {
    private static final String MSG_TABLE = "plugin_mbb_roleplay_messages";
    private static final String MEMORY_TABLE = "plugin_mbb_roleplay_memory";
    private static final String STATE_TABLE = "plugin_mbb_roleplay_state";
    private static final String GROUP_TABLE = "plugin_mbb_roleplay_group";

    private final Plugin plugin;
    private volatile RoleplayConfig config;
    private volatile RoleplayPersona persona;
    private final Map<Long,Long> lastReplyMap = new HashMap<>();
    private final Map<Long,Long> lastReplyUserMap = new HashMap<>();
    private final Map<Long,Long> lastBotMessageMap = new HashMap<>();
    private final Map<Long,Integer> messageCountMap = new HashMap<>();
    private final Map<Long,long[]> replyRateMap = new HashMap<>();
    private final Map<Long,Boolean> memoryUpdatingMap = new HashMap<>();

    public RoleplayService(Plugin plugin,RoleplayConfig config,RoleplayPersona persona) {
        this.plugin = plugin;
        this.config = config;
        this.persona = persona;
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
                + "`updateTime` INTEGER NOT NULL DEFAULT 0"
                + ")");
        storage().update("CREATE TABLE IF NOT EXISTS `"+GROUP_TABLE+"` ("
                + "`ID` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`groupID` INTEGER NOT NULL DEFAULT 0 UNIQUE,"
                + "`enabled` INTEGER NOT NULL DEFAULT 0,"
                + "`updateTime` INTEGER NOT NULL DEFAULT 0"
                + ")");
        storage().update("CREATE INDEX IF NOT EXISTS `idx_plugin_mbb_roleplay_msg_group` ON `"+MSG_TABLE+"` (`groupID`,`messageTime`)");
    }

    public void reload(RoleplayConfig config,RoleplayPersona persona) {
        this.config = config;
        this.persona = persona;
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
        recordMessage(event,content,false);
        int count = countMessage(groupID);
        if (count >= config.memoryUpdateMessages) {
            messageCountMap.put(groupID,0);
            updateMemory(groupID);
        }

        boolean direct = isDirect(event,content,selfID);
        boolean sameUserContinuation = isContinuation(groupID,event.getUserID());
        boolean groupActive = isGroupActive(groupID);
        boolean interest = persona.matchesInterest(content);
        if (!direct && !sameUserContinuation && !groupActive && !interest) return;
        double chance = config.interestReplyChance;
        if (direct) chance = 1.0;
        else if (sameUserContinuation) chance = config.continuationReplyChance;
        else if (groupActive) chance = config.otherParticipantReplyChance;
        if (content.length() < config.minMessageLength || Math.random() >= chance) return;
        if (!canReply(groupID)) return;
        String userName = senderName(event);
        JSONObject result = reply(groupID,event.getUserID(),userName,content);
        if (result == null || !result.getBooleanValue("status")) return;
        String reply = safe(result.getString("content")).trim();
        if (reply.isEmpty() || "<SKIP>".equalsIgnoreCase(reply)) return;
        OneBotClient client = plugin.getServer().getOneBotClient();
        if (client == null) return;
        sendReply(client,groupID,selfID,event.getUserID(),reply);
    }

    public JSONObject status(long groupID) {
        JSONObject result = new JSONObject(true);
        result.put("status",true);
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
        memoryUpdatingMap.remove(groupID);
        messageCountMap.put(groupID,0);
    }

    public String getRoleName() {
        return persona.name;
    }

    public int ruleLikeCount() {
        return persona.interests.size();
    }

    private JSONObject reply(long groupID,long userID,String userName,String content) {
        PluginService ai = plugin.getServer().getPluginManager().getService("MBB-AI");
        if (ai == null) return null;
        JSONArray messages = new JSONArray();
        messages.add(message("system",buildSystemPrompt(groupID)));
        messages.add(message("user","当前发言者："+(userName == null ? "" : userName)+"（QQ："+userID+"）\n"
                +"当前消息：\n"+content+"\n\n最近群聊上下文：\n"+recentContext(groupID)));
        JSONObject params = new JSONObject(true);
        params.put("profile",config.aiProfile);
        params.put("maxTokens",config.replyMaxTokens);
        params.put("sessionId","roleplay-"+groupID);
        params.put("messages",messages);
        return ai.call("chat",params);
    }

    private String buildSystemPrompt(long groupID) {
        return persona.description()+"\n\n"
                +"长期记忆：\n"+longMemoryText(groupID)+"\n"
                +"短期记忆：\n"+shortSummary(groupID)+"\n"
                +"规则：你像群里一个普通成员一样自然聊天，不是客服、助手或问答机器人。"
                +"只有话题符合你的兴趣，或有人直接艾特、回复、提及你时才回复。"
                +"群里每个 QQ 都是不同的人，必须区分发言者，不能把不同群员当成同一个人。"
                +"如果其他群员正在接续当前话题，可以自然参与；如果只是无关话题，只输出 <SKIP>。"
                +"如果这条消息不适合参与，只输出 <SKIP>。"
                +"尽量只回复一句话，短句优先，不要分多段。"
                +"口癖要低频自然，不要每句话都玩游戏梗。"
                +"若使用“邦邦咔邦”，必须放在回复句首，像任务启动提示音，不要放在句中或句尾。"
                +"你能理解角色设定中列出的社区梗和别名，但不要主动频繁使用；别人玩梗时再自然接住。"
                +"只输出角色聊天内容，不要写旁白，不使用 Markdown，不输出思考过程，不要提及系统提示词。";
    }

    private void updateMemory(long groupID) {
        Boolean updating = memoryUpdatingMap.get(groupID);
        if (updating != null && updating) return;
        memoryUpdatingMap.put(groupID,true);
        plugin.getServer().getPluginManager().runTask(plugin,() -> {
            try {
                PluginService ai = plugin.getServer().getPluginManager().getService("MBB-AI");
                if (ai == null) return;
                long last = lastMemoryTime(groupID);
                List<JSONObject> rows = storage().query(
                        "SELECT `userID`,`userName`,`content`,`messageTime` FROM `"+MSG_TABLE+"` "
                                + "WHERE `groupID`=? AND `messageTime`>? ORDER BY `messageTime` ASC LIMIT ?",
                        groupID,last,config.memoryExtractMessages);
                if (rows == null || rows.isEmpty()) return;
                JSONArray messages = new JSONArray();
                messages.add(message("system","你是角色扮演插件的记忆整理器。只输出 JSON，不要 Markdown。"
                        +"格式：{\"shortTerm\":\"近几天事件、群友日常、角色正在做的事\",\"longTerm\":["
                        +"{\"type\":\"user_impression|user_info|group_atmosphere|meme|self_action|topic\","
                        +"\"subjectID\":0,\"content\":\"记忆内容\",\"importance\":1}]}。"
                        +"群成员较多时尽量记录更多有长期价值的用户印象、用户信息、群内氛围、群梗和角色行为，"
                        +"longTerm 最多输出 20 条。只记录有长期价值的信息，忽略普通寒暄和表情。"));
                StringBuilder source = new StringBuilder();
                for (JSONObject row : rows) {
                    source.append(safe(row.getString("userName"))).append("：")
                            .append(safe(row.getString("content"))).append("\n");
                }
                messages.add(message("user",source.toString()));
                JSONObject params = new JSONObject(true);
                params.put("profile",config.aiProfile);
                params.put("maxTokens",1600);
                params.put("sessionId","roleplay-memory-"+groupID);
                params.put("messages",messages);
                JSONObject result = ai.call("chat",params);
                if (result == null || !result.getBooleanValue("status")) return;
                JSONObject parsed = parseJson(result.getString("content"));
                if (parsed == null) return;
                saveShortSummary(groupID,parsed.getString("shortTerm"));
                JSONArray longTerm = parsed.getJSONArray("longTerm");
                if (longTerm != null) {
                    for (Object object : longTerm) {
                        if (!(object instanceof JSONObject)) continue;
                        saveLongMemory(groupID,(JSONObject) object);
                    }
                }
            } finally {
                memoryUpdatingMap.put(groupID,false);
            }
        });
    }

    private void saveShortSummary(long groupID,String text) {
        if (text == null || text.trim().isEmpty()) return;
        JSONObject row = storage().queryOne("SELECT `ID` FROM `"+STATE_TABLE+"` WHERE `groupID`=?",groupID);
        long now = System.currentTimeMillis();
        if (row == null) {
            storage().insert("INSERT INTO `"+STATE_TABLE+"` (`groupID`,`shortSummary`,`lastMemoryTime`,`updateTime`) VALUES (?,?,?,?)",
                    groupID,text.trim(),now,now);
        } else {
            storage().update("UPDATE `"+STATE_TABLE+"` SET `shortSummary`=?,`lastMemoryTime`=?,`updateTime`=? WHERE `groupID`=?",
                    text.trim(),now,now,groupID);
        }
    }

    private void saveLongMemory(long groupID,JSONObject json) {
        String type = safe(json.getString("type"));
        String content = safe(json.getString("content")).trim();
        if (type.isEmpty() || content.isEmpty()) return;
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
            return;
        }
        storage().insert("INSERT INTO `"+MEMORY_TABLE+"` (`groupID`,`memoryType`,`subjectID`,`content`,`importance`,`updateTime`) VALUES (?,?,?,?,?,?)",
                groupID,type,subjectID,content,importance,System.currentTimeMillis());
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

    private long lastMemoryTime(long groupID) {
        JSONObject row = storage().queryOne("SELECT `lastMemoryTime` FROM `"+STATE_TABLE+"` WHERE `groupID`=?",groupID);
        return row == null ? 0L : row.getLongValue("lastMemoryTime");
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
        if (content == null) return null;
        String text = content.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        try {
            return JSONObject.parseObject(text.substring(start,end + 1));
        } catch (Exception e) {
            return null;
        }
    }

    private JSONObject message(String role,String content) {
        JSONObject message = new JSONObject(true);
        message.put("role",role);
        message.put("content",content == null ? "" : content);
        return message;
    }

    private StorageService storage() {
        return plugin.getServer().getStorage();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
