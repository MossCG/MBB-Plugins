package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.OneBot.MessageUtil;
import org.moboxlab.moboxbot.API.OneBot.OneBotClient;
import org.moboxlab.moboxbot.API.Plugin;
import org.moboxlab.moboxbot.API.PluginService;
import org.moboxlab.moboxbot.API.Storage.StorageService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 低频主动行为随机刻。
 *
 * 随机刻只负责提出机会，不保证每次都有动作。夜间、频率、去重和候选资格
 * 都先由代码判断，模型只负责决定这次是否值得做以及具体说什么。
 */
public class RoleplayRandomTickService {
    private static final String TABLE = "plugin_mbb_roleplay_random_tick";
    private static final String ACTION_PRIVATE_OWNER = "private_owner";
    private static final String ACTION_GROUP_TOPIC = "group_topic";
    private static final String ACTION_QZONE = "qzone_post";
    private static final String STATUS_SENT = "sent";
    private static final String STATUS_NO_OP = "no_op";

    private final Plugin plugin;
    private final RoleplayService service;
    private volatile RoleplayConfig config;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile boolean scheduled = false;

    private static class OwnerInteraction {
        private long ownerID;
        private long lastTime;
    }

    private static class GroupCandidate {
        private long groupID;
        private int botMessages;
        private double weight;
    }

    public RoleplayRandomTickService(Plugin plugin,RoleplayConfig config,RoleplayService service) {
        this.plugin = plugin;
        this.config = config;
        this.service = service;
    }

    public void init() {
        storage().update("CREATE TABLE IF NOT EXISTS `"+TABLE+"` ("
                + "`ID` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`profile` TEXT NOT NULL DEFAULT '',"
                + "`dueTime` INTEGER NOT NULL DEFAULT 0,"
                + "`lastRunTime` INTEGER NOT NULL DEFAULT 0,"
                + "`action` TEXT NOT NULL DEFAULT '',"
                + "`status` TEXT NOT NULL DEFAULT '',"
                + "`targetType` TEXT NOT NULL DEFAULT '',"
                + "`targetID` INTEGER NOT NULL DEFAULT 0,"
                + "`fingerprint` TEXT NOT NULL DEFAULT '',"
                + "`reason` TEXT NOT NULL DEFAULT '',"
                + "`createTime` INTEGER NOT NULL DEFAULT 0"
                + ")");
        storage().update("CREATE INDEX IF NOT EXISTS `idx_plugin_mbb_roleplay_random_tick_profile` "
                + "ON `"+TABLE+"` (`profile`,`ID`)");
        storage().update("CREATE INDEX IF NOT EXISTS `idx_plugin_mbb_roleplay_random_tick_action` "
                + "ON `"+TABLE+"` (`action`,`createTime`)");
        cleanup();
    }

    public void reload(RoleplayConfig config) {
        this.config = config;
    }

    public void ensureTask() {
        if (scheduled) return;
        scheduled = true;
        plugin.getServer().getPluginManager().runTaskTimer(plugin,this::tick,60,60);
    }

    public JSONObject status() {
        JSONObject row = latest();
        JSONObject result = new JSONObject(true);
        result.put("status",true);
        result.put("enable",config.randomTickEnable);
        result.put("dueTime",row == null ? 0L : row.getLongValue("dueTime"));
        result.put("lastRunTime",row == null ? 0L : row.getLongValue("lastRunTime"));
        result.put("lastAction",row == null ? "" : service.safe(row.getString("action")));
        result.put("lastStatus",row == null ? "" : service.safe(row.getString("status")));
        result.put("lastReason",row == null ? "" : service.safe(row.getString("reason")));
        return result;
    }

    private void tick() {
        if (!config.randomTickEnable || running.get()) return;
        long now = System.currentTimeMillis();
        JSONObject row = latest();
        long due = row == null ? 0L : row.getLongValue("dueTime");
        if (due <= 0) {
            saveSchedule(nextDelay(now));
            return;
        }
        if (now < due) return;
        if (isQuietHour()) {
            saveSchedule(nextMorning());
            record(ACTION_NONE(),STATUS_NO_OP,"system",0,"夜间静默",now);
            return;
        }
        if (!running.compareAndSet(false,true)) return;
        service.submitBackground(this::runOnce);
    }

    private void runOnce() {
        long now = System.currentTimeMillis();
        try {
            List<String> candidates = candidates();
            if (candidates.isEmpty()) {
                record("none",STATUS_NO_OP,"system",0,"没有可用主动行为",now);
                return;
            }
            OwnerInteraction recentOwner = recentOwnerInteraction(now);
            JSONObject decision;
            if (candidates.contains(ACTION_QZONE) && qzoneDueToday(now)) {
                decision = forcedDecision(ACTION_QZONE,"每日 Qzone 保底");
            } else if (candidates.contains(ACTION_PRIVATE_OWNER)
                    && shouldBoostRecentOwner(config.randomTickOwnerRecentBoostChance,
                    recentOwner.ownerID)) {
                decision = forcedDecision(ACTION_PRIVATE_OWNER,"优先找最近互动过的 owner");
            } else {
                decision = decide(candidates,now,recentOwner);
            }
            if (decision == null) {
                record("none",STATUS_NO_OP,"system",0,"随机刻决策失败",now);
                return;
            }
            String action = service.safe(decision.getString("action")).trim().toLowerCase(Locale.ROOT);
            if (action.isEmpty() || "none".equals(action) || !candidates.contains(action)) {
                record("none",STATUS_NO_OP,"system",0,
                        service.safe(decision.getString("reason")),now);
                return;
            }
            if (!allowedNow(action,now)) {
                record("none",STATUS_NO_OP,"system",0,"主动行为频率或去重拦截",now);
                return;
            }
            String reason = service.safe(decision.getString("reason")).trim();
            long targetID;
            if (ACTION_PRIVATE_OWNER.equals(action)) {
                targetID = privateOwnerTarget(recentOwner,now);
            } else if (ACTION_GROUP_TOPIC.equals(action)) {
                targetID = chooseGroup();
            } else if (ACTION_QZONE.equals(action)) {
                targetID = privateOwnerTarget(recentOwner,now);
            } else {
                targetID = 0L;
            }
            if (targetID <= 0) {
                record("none",STATUS_NO_OP,"system",0,
                        ACTION_GROUP_TOPIC.equals(action) ? "没有可用的群" : "owner 列表为空",now);
                return;
            }
            String content = clean(generateContent(action,reason,targetID));
            if (content.isEmpty() || "<SKIP>".equalsIgnoreCase(content)) {
                record("none",STATUS_NO_OP,"system",0,
                        reason.isEmpty() ? "这次没有生成自然内容" : reason,now);
                return;
            }
            String fingerprint = fingerprint(action,targetID,content);
            if (recentDuplicate(fingerprint,now)) {
                record("none",STATUS_NO_OP,"system",0,"近期已执行过相似主动行为",now);
                return;
            }
            boolean sent;
            if (ACTION_PRIVATE_OWNER.equals(action)) {
                sent = service.privateChatService().sendProactive(targetID,content,reason);
            } else if (ACTION_GROUP_TOPIC.equals(action)) {
                sent = sendGroup(targetID,content);
            } else {
                sent = sendQzone(targetID,content);
            }
            record(action,sent ? STATUS_SENT : STATUS_NO_OP,
                    ACTION_QZONE.equals(action) ? "qzone" : (ACTION_GROUP_TOPIC.equals(action) ? "group" : "private"),
                    targetID,fingerprint,reason,now);
            plugin.getLogger().sendInfo("[随机刻] "+(sent ? "已执行 " : "未执行 ")+action
                    +" 目标="+targetID+" 内容="+service.shortText(content,60));
        } catch (Exception e) {
            plugin.getLogger().sendWarn("[随机刻] 执行异常："+service.safe(e.getMessage()));
            record("none",STATUS_NO_OP,"system",0,"执行异常",now);
        } finally {
            running.set(false);
            saveSchedule(nextDelay(System.currentTimeMillis()));
        }
    }

    private JSONObject decide(List<String> candidates,long now,OwnerInteraction recentOwner) {
        PluginService ai = plugin.getServer().getPluginManager().getService("MBB-AI");
        if (ai == null) return null;
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是").append(service.persona().name).append("的主动行为决策器。\n")
                .append("当前时间：").append(service.currentTimeText()).append("\n")
                .append("随机刻不是任务，不要求每次都行动。没有自然、具体、值得说的话时返回 none。\n")
                .append("可用行为：\n");
        for (String candidate : candidates) {
            if (ACTION_PRIVATE_OWNER.equals(candidate)) {
                prompt.append("- private_owner：给 owner 发私聊；没有频率限制，但不要刷屏。\n");
            } else if (ACTION_GROUP_TOPIC.equals(candidate)) {
                prompt.append("- group_topic：主动发起一句自然话题；具体群由程序从最近活跃群中选择；全局每 6 小时最多 2 次。\n");
            } else if (ACTION_QZONE.equals(candidate)) {
                prompt.append("- qzone_post：发一条 QQ 空间说说；每天最多 2 次，默认仅自己可见。\n");
            }
        }
        if (recentOwner.ownerID > 0) {
            prompt.append("最近互动过的 owner：").append(recentOwner.ownerID)
                    .append("，最近互动时间：").append(service.formatMemoryTime(recentOwner.lastTime))
                    .append("。选择 private_owner 时优先找这个 owner。\n")
                    .append("对该 owner 的个人印象：\n")
                    .append(service.memberService().promptText(0L,recentOwner.ownerID)).append("\n");
        } else {
            prompt.append("最近没有互动明显的 owner。\n");
        }
        prompt.append("最近已执行的主动行为：\n").append(recentActions(now)).append("\n")
                .append("你只决定行为类型和原因，不填写群号、正文或链接。\n")
                .append("只输出 JSON，不要 markdown：")
                .append("{\"action\":\"none|private_owner|group_topic|qzone_post\",")
                .append("\"confidence\":0.0,\"reason\":\"\"}");
        JSONArray messages = new JSONArray();
        messages.add(service.message("system",prompt.toString()));
        messages.add(service.message("user","请判断这一次随机刻是否行动。"));
        JSONObject params = new JSONObject(true);
        params.put("profile",config.aiProfile);
        params.put("maxTokens",config.randomTickMaxTokens);
        params.put("temperature",0.8);
        params.put("reasoningEffort",config.randomTickReasoningEffort);
        params.put("timeoutSeconds",config.randomTickTimeoutSecond);
        params.put("retryCount",0);
        params.put("sessionId","roleplay-random-tick-"+service.getRoleName());
        params.put("messages",messages);
        long start = System.currentTimeMillis();
        JSONObject result = ai.call("chat",params);
        RoleplayAiLog.log(plugin.getLogger(),"随机刻决策",0L,result,
                System.currentTimeMillis() - start);
        if (result == null || !result.getBooleanValue("status")) return null;
        JSONObject parsed = service.parseJson(result.getString("content"));
        if (parsed == null) parsed = service.parseJson(result.getString("reasoningContent"));
        return parsed;
    }

    private String generateContent(String action,String reason,long targetID) {
        if (ACTION_PRIVATE_OWNER.equals(action)) {
            return service.privateChatService().generateProactive(targetID,reason);
        }
        PluginService ai = plugin.getServer().getPluginManager().getService("MBB-AI");
        if (ai == null) return "";
        StringBuilder system = new StringBuilder();
        system.append("你是").append(service.persona().name).append("。现在要根据随机刻写一句主动内容。")
                .append("不要解释调度、记忆或系统。只输出要发送的正文。")
                .append("如果没有自然内容，输出 <SKIP>。")
                .append("角色设定只作背景，不要反复提职业、社团分工、作品进度、工具或长期项目。")
                .append("不要输出 markdown、旁白、括号动作或链接。\n")
                .append("当前时间：").append(service.currentTimeText()).append("\n")
                .append("原因：").append(service.safe(reason)).append("\n")
                .append("角色设定：\n").append(service.persona().coreText()).append("\n");
        if (ACTION_GROUP_TOPIC.equals(action)) {
            system.append("\n【目标群资料】\n")
                    .append("群号：").append(targetID).append("\n")
                    .append("本群短期记忆：\n").append(service.groupShortSummary(targetID)).append("\n")
                    .append("本群近期消息：\n").append(service.contextService().recentContext(targetID))
                    .append("\n只写一句能在该群自然发起的话题，最多 50 字。")
                    .append("不要使用其他群的聊天内容、称呼、事件或关系。");
        } else {
            system.append("\n【Qzone 资料】\n")
                    .append("与 owner 的长期记忆：\n")
                    .append(service.privateMemoryText(targetID,"QQ空间 日常",
                            config.memoryRelevanceMaxChars)).append("\n")
                    .append("全局永久记忆：\n")
                    .append(service.globalMemoryService().promptText("QQ空间 日常",targetID,
                            config.globalMemoryRelevanceMaxChars)).append("\n")
                    .append("写一条 Qzone 说说，最多 80 字，像角色随手记录日常，不要营销口吻。")
                    .append("不要引用或复述任何群聊原文。");
        }
        JSONArray messages = new JSONArray();
        messages.add(service.message("system",system.toString()));
        messages.add(service.message("user","请生成正文。"));
        JSONObject params = new JSONObject(true);
        params.put("profile",config.aiProfile);
        params.put("maxTokens",480);
        params.put("temperature",0.9);
        params.put("reasoningEffort",config.replyReasoningEffort);
        params.put("timeoutSeconds",config.randomTickTimeoutSecond);
        params.put("retryCount",0);
        params.put("sessionId",contentSessionId(action,targetID));
        params.put("messages",messages);
        JSONObject result = ai.call("chat",params);
        if (result == null || !result.getBooleanValue("status")) return "";
        String content = service.safe(result.getString("content"));
        if (content.trim().isEmpty()) content = service.safe(result.getString("reasoningContent"));
        JSONObject parsed = service.parseJson(content);
        if (parsed != null && parsed.containsKey("text")) content = parsed.getString("text");
        return content;
    }

    private List<String> candidates() {
        List<String> result = new ArrayList<>();
        if (config.privateChatEnable && !owners().isEmpty()) result.add(ACTION_PRIVATE_OWNER);
        if (config.randomTickGroupMaxActions > 0 && !groupCandidates().isEmpty()) {
            result.add(ACTION_GROUP_TOPIC);
        }
        if (plugin.getServer().getPluginManager().getService("MBB-QQ") != null
                && !owners().isEmpty() && config.randomTickQzoneDailyMax > 0) {
            result.add(ACTION_QZONE);
        }
        return result;
    }

    private JSONObject forcedDecision(String action,String reason) {
        JSONObject decision = new JSONObject(true);
        decision.put("action",action);
        decision.put("confidence",1.0);
        decision.put("reason",reason);
        return decision;
    }

    private boolean qzoneDueToday(long now) {
        if (plugin.getServer().getPluginManager().getService("MBB-QQ") == null
                || owners().isEmpty() || config.randomTickQzoneDailyMax <= 0) {
            return false;
        }
        return qzoneDue(config.randomTickQzoneDailyMax,
                countAction(ACTION_QZONE,STATUS_SENT,startOfToday(now)));
    }

    static boolean qzoneDue(int dailyMax,int sentToday) {
        return dailyMax > 0 && sentToday == 0;
    }

    static boolean shouldBoostRecentOwner(double chance,long ownerID) {
        return ownerID > 0 && Math.random() < Math.max(0,Math.min(1,chance));
    }

    static String contentSessionId(String action,long targetID) {
        return ACTION_GROUP_TOPIC.equals(action)
                ? "roleplay-random-content-group-"+targetID
                : "roleplay-random-content-qzone-"+targetID;
    }

    private long chooseGroup() {
        List<GroupCandidate> candidates = groupCandidates();
        if (candidates.isEmpty()) return 0L;
        long[] groupIDs = new long[candidates.size()];
        double[] weights = new double[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            GroupCandidate candidate = candidates.get(i);
            groupIDs[i] = candidate.groupID;
            weights[i] = candidate.weight;
        }
        return weightedGroup(groupIDs,weights,Math.random());
    }

    static long weightedGroup(long[] groupIDs,double[] weights,double roll) {
        if (groupIDs == null || weights == null
                || groupIDs.length == 0 || groupIDs.length != weights.length) return 0L;
        double totalWeight = 0;
        for (double weight : weights) totalWeight += Math.max(0,weight);
        if (totalWeight <= 0) return groupIDs[0];
        double target = Math.max(0,Math.min(0.999999999,roll)) * totalWeight;
        for (int i = 0; i < groupIDs.length; i++) {
            target -= Math.max(0,weights[i]);
            if (target < 0) return groupIDs[i];
        }
        return groupIDs[groupIDs.length - 1];
    }

    private long privateOwnerTarget(OwnerInteraction recent,long now) {
        long window = Math.max(1,config.randomTickOwnerRecentWindowMinute) * 60000L;
        if (recent != null && recent.ownerID > 0 && recent.lastTime >= now - window) {
            return recent.ownerID;
        }
        return ownerOrZero();
    }

    private OwnerInteraction recentOwnerInteraction(long now) {
        OwnerInteraction result = new OwnerInteraction();
        long since = now - Math.max(1,config.randomTickOwnerRecentWindowMinute) * 60000L;
        for (Long owner : owners()) {
            long last = 0L;
            JSONObject groupRow = storage().queryOne(
                    "SELECT MAX(`messageTime`) AS `lastTime` FROM `"+RoleplayService.MSG_TABLE+"` "
                            + "WHERE `userID`=? AND `isBot`=0 AND `messageTime`>=?",
                    owner,since);
            if (groupRow != null) last = Math.max(last,groupRow.getLongValue("lastTime"));
            last = Math.max(last,service.privateChatService().lastInteractionTime(owner));
            if (last >= since && last > result.lastTime) {
                result.ownerID = owner;
                result.lastTime = last;
            }
        }
        return result;
    }

    private boolean allowedNow(String action,long now) {
        if (ACTION_GROUP_TOPIC.equals(action)) {
            long since = now - Math.max(1,config.randomTickGroupWindowMinute) * 60000L;
            return countAction(ACTION_GROUP_TOPIC,STATUS_SENT,since) < config.randomTickGroupMaxActions;
        }
        if (ACTION_QZONE.equals(action)) {
            return countAction(ACTION_QZONE,STATUS_SENT,startOfToday(now)) < config.randomTickQzoneDailyMax;
        }
        //主动私聊 owner 不做频率限制。
        return true;
    }

    private boolean recentDuplicate(String fingerprint,long now) {
        if (fingerprint.isEmpty()) return false;
        long since = now - Math.max(1,config.randomTickDedupeHours) * 3600000L;
        List<JSONObject> rows = storage().query(
                "SELECT `ID` FROM `"+TABLE+"` WHERE `profile`=? AND `fingerprint`=? "
                        + "AND `status`=? AND `createTime`>=? LIMIT 1",
                service.getRoleName(),fingerprint,STATUS_SENT,since);
        return rows != null && !rows.isEmpty();
    }

    private int countAction(String action,String status,long since) {
        JSONObject row = storage().queryOne(
                "SELECT COUNT(*) AS `count` FROM `"+TABLE+"` "
                        + "WHERE `profile`=? AND `action`=? AND `status`=? AND `createTime`>=?",
                service.getRoleName(),action,status,since);
        return row == null ? 0 : row.getIntValue("count");
    }

    private String recentActions(long now) {
        List<JSONObject> rows = storage().query(
                "SELECT `action`,`targetType`,`targetID`,`status`,`reason`,`createTime` FROM `"+TABLE+"` "
                        + "WHERE `profile`=? AND `createTime`>=? ORDER BY `ID` DESC LIMIT 8",
                service.getRoleName(),now - 24L * 3600000L);
        if (rows == null || rows.isEmpty()) return "暂无。";
        StringBuilder builder = new StringBuilder();
        for (JSONObject row : rows) {
            builder.append("- ").append(service.formatMemoryTime(row.getLongValue("createTime")))
                    .append(" ").append(service.safe(row.getString("action")))
                    .append(" ").append(service.safe(row.getString("status")))
                    .append(" ").append(service.safe(row.getString("reason"))).append("\n");
        }
        return builder.toString();
    }

    /**
     * 只有近期有消息、且角色开启的群才适合主动发起话题。
     * 角色在该群最近一天的发言量越高，被选中的基础权重越大。
     */
    private List<GroupCandidate> groupCandidates() {
        List<GroupCandidate> result = new ArrayList<>();
        JSONArray enabled = service.listGroups();
        if (enabled == null || enabled.isEmpty()) return result;
        long since = System.currentTimeMillis() - 24L * 3600000L;
        for (Object object : enabled) {
            long groupID = object == null ? 0L : Long.parseLong(String.valueOf(object));
            if (groupID <= 0) continue;
            JSONObject row = storage().queryOne(
                    "SELECT COUNT(*) AS `totalCount`,"
                            + "SUM(CASE WHEN `isBot`=1 THEN 1 ELSE 0 END) AS `botCount` "
                            + "FROM `"+RoleplayService.MSG_TABLE+"` "
                            + "WHERE `groupID`=? AND `messageTime`>=?",
                    groupID,since);
            if (row == null || row.getIntValue("totalCount") <= 0) continue;
            GroupCandidate candidate = new GroupCandidate();
            candidate.groupID = groupID;
            candidate.botMessages = Math.max(0,row.getIntValue("botCount"));
            result.add(candidate);
        }
        int maxBotMessages = 1;
        for (GroupCandidate candidate : result) {
            maxBotMessages = Math.max(maxBotMessages,candidate.botMessages);
        }
        for (GroupCandidate candidate : result) {
            double activity = (double)candidate.botMessages / maxBotMessages;
            candidate.weight = 0.25 + activity * 0.75;
        }
        return result;
    }

    private boolean sendGroup(long groupID,String content) {
        OneBotClient client = plugin.getServer().getOneBotClient();
        if (client == null || !client.isConnected()) return false;
        content = service.messageSender().normalizeSentencePeriods(groupID,content);
        if (content.isEmpty()) return false;
        JSONObject response = client.sendGroupMessage(groupID,
                MessageUtil.message(MessageUtil.text(content)));
        if (response == null || response.getIntValue("retcode") != 0) return false;
        long messageID = response.getJSONObject("data") == null
                ? 0L : response.getJSONObject("data").getLongValue("message_id");
        long selfID = loginID();
        service.recordBotMessage(groupID,selfID,content,messageID);
        return true;
    }

    private boolean sendQzone(long ownerID,String content) {
        PluginService qq = plugin.getServer().getPluginManager().getService("MBB-QQ");
        if (qq == null || ownerID <= 0) return false;
        JSONObject params = new JSONObject(true);
        params.put("operatorID",ownerID);
        params.put("content",content);
        params.put("ugcRight",64);
        JSONObject result = qq.call("qzone_post",params);
        return result != null && result.getBooleanValue("status");
    }

    private long loginID() {
        OneBotClient client = plugin.getServer().getOneBotClient();
        if (client == null || !client.isConnected()) return 0L;
        JSONObject response = client.callAction("get_login_info",new JSONObject(true));
        JSONObject data = response == null ? null : response.getJSONObject("data");
        return data == null ? 0L : data.getLongValue("user_id");
    }

    private List<Long> owners() {
        List<Long> result = new ArrayList<>();
        List<Long> owners = plugin.getServer().getOwnerList();
        if (owners == null) return result;
        for (Long owner : owners) if (owner != null && owner > 0) result.add(owner);
        return result;
    }

    private long ownerOrZero() {
        List<Long> owners = owners();
        return owners.isEmpty() ? 0L : owners.get(0);
    }

    private String fingerprint(String action,long target,String content) {
        String normalized = SpeechCorpusEntry.normalize(content);
        if (normalized.length() > 120) normalized = normalized.substring(0,120);
        return action+"|"+target+"|"+normalized;
    }

    private String clean(String text) {
        return RoleplayReplySanitizer.clean(service.persona(),plugin.getServer().getBotName(),
                service.safe(text)).trim();
    }

    private boolean isQuietHour() {
        int hour = hour();
        int start = config.randomTickQuietStartHour;
        int end = config.randomTickQuietEndHour;
        if (start == end) return false;
        return start < end ? hour >= start && hour < end : hour >= start || hour < end;
    }

    private int hour() {
        return java.time.ZonedDateTime.now(java.time.ZoneId.of(
                config.timeZone == null || config.timeZone.trim().isEmpty()
                        ? "Asia/Shanghai" : config.timeZone)).getHour();
    }

    private long nextMorning() {
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(java.time.ZoneId.of(config.timeZone));
        java.time.ZonedDateTime morning = now.toLocalDate().atStartOfDay(now.getZone())
                .plusHours(config.randomTickQuietEndHour);
        if (!morning.isAfter(now)) morning = morning.plusDays(1);
        return morning.toInstant().toEpochMilli() + 60000L + (long)(Math.random() * 240000L);
    }

    private long nextDelay(long now) {
        int min = Math.max(1,config.randomTickMinMinute);
        int max = Math.max(min,config.randomTickMaxMinute);
        long minutes = min + (long)(Math.random() * (max - min + 1));
        return now + minutes * 60000L;
    }

    private void saveSchedule(long due) {
        JSONObject row = latest();
        long now = System.currentTimeMillis();
        if (row == null) {
            storage().insert("INSERT INTO `"+TABLE+"` "
                            + "(`profile`,`dueTime`,`lastRunTime`,`action`,`status`,`targetType`,"
                            + "`targetID`,`fingerprint`,`reason`,`createTime`) VALUES (?,?,?,?,?,?,?,?,?,?)",
                    service.getRoleName(),due,0L,"schedule","scheduled","system",0L,"","",now);
        } else {
            storage().update("UPDATE `"+TABLE+"` SET `dueTime`=? WHERE `ID`=?",
                    due,row.getLongValue("ID"));
        }
    }

    private void record(String action,String status,String targetType,long targetID,
                        String reason,long now) {
        record(action,status,targetType,targetID,"",reason,now);
    }

    private void record(String action,String status,String targetType,long targetID,
                        String fingerprint,String reason,long now) {
        JSONObject row = latest();
        if (row != null && "scheduled".equals(row.getString("status"))) {
            storage().update("UPDATE `"+TABLE+"` SET `lastRunTime`=?,`action`=?,`status`=?,"
                            + "`targetType`=?,`targetID`=?,`fingerprint`=?,`reason`=?,`createTime`=? WHERE `ID`=?",
                    now,action,status,targetType,targetID,service.safe(fingerprint),
                    service.shortText(reason,500),now,
                    row.getLongValue("ID"));
        } else {
            storage().insert("INSERT INTO `"+TABLE+"` "
                            + "(`profile`,`dueTime`,`lastRunTime`,`action`,`status`,`targetType`,"
                            + "`targetID`,`fingerprint`,`reason`,`createTime`) VALUES (?,?,?,?,?,?,?,?,?,?)",
                    service.getRoleName(),0L,now,action,status,targetType,targetID,
                    service.safe(fingerprint),
                    service.shortText(reason,500),now);
        }
    }

    private JSONObject latest() {
        return storage().queryOne("SELECT * FROM `"+TABLE+"` WHERE `profile`=? "
                + "ORDER BY `ID` DESC LIMIT 1",service.getRoleName());
    }

    private long startOfToday(long now) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd",Locale.CHINA);
        format.setTimeZone(TimeZone.getTimeZone(
                config.timeZone == null || config.timeZone.trim().isEmpty()
                        ? "Asia/Shanghai" : config.timeZone));
        try {
            Date date = format.parse(format.format(new Date(now)));
            return date == null ? now - 86400000L : date.getTime();
        } catch (Exception e) {
            return now - 86400000L;
        }
    }

    private String ACTION_NONE() {
        return "none";
    }

    private void cleanup() {
        long cutoff = System.currentTimeMillis() - 30L * 86400000L;
        storage().update("DELETE FROM `"+TABLE+"` WHERE `createTime`<?",cutoff);
        storage().update("DELETE FROM `"+TABLE+"` WHERE `ID` NOT IN "
                + "(SELECT `ID` FROM `"+TABLE+"` ORDER BY `ID` DESC LIMIT 2000)");
    }

    private StorageService storage() {
        return plugin.getServer().getStorage();
    }
}
