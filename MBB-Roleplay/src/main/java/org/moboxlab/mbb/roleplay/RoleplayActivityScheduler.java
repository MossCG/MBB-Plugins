package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.OneBot.MessageUtil;
import org.moboxlab.moboxbot.API.OneBot.OneBotClient;
import org.moboxlab.moboxbot.API.Plugin;
import org.moboxlab.moboxbot.API.PluginService;
import org.moboxlab.moboxbot.API.Storage.StorageService;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 外部插件活动调度器
 *
 * 与社交随机刻分开运行。活动插件通过 activity_plan 提供间隔和每日上限，
 * 通过 activity_run 执行领域逻辑；角色层统一处理记忆、分享、去重和审计。
 */
public class RoleplayActivityScheduler {
    private static final String STATE_TABLE = "plugin_mbb_roleplay_activity_state";
    private static final String LOG_TABLE = "plugin_mbb_roleplay_activity_log";
    private static final int DEFAULT_MIN_INTERVAL_MINUTE = 180;
    private static final int DEFAULT_MAX_INTERVAL_MINUTE = 360;
    private static final int DEFAULT_DAILY_MAX = 3;

    private static class ActivityPlan {
        String serviceName = "";
        int minIntervalMinute = DEFAULT_MIN_INTERVAL_MINUTE;
        int maxIntervalMinute = DEFAULT_MAX_INTERVAL_MINUTE;
        int dailyMax = DEFAULT_DAILY_MAX;
        int priority = 50;
        long dueTime;
    }

    private final Plugin plugin;
    private final RoleplayService service;
    private volatile RoleplayConfig config;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile boolean scheduled = false;

    public RoleplayActivityScheduler(Plugin plugin,RoleplayConfig config,RoleplayService service) {
        this.plugin = plugin;
        this.config = config;
        this.service = service;
    }

    public void init() {
        storage().update("CREATE TABLE IF NOT EXISTS `"+STATE_TABLE+"` ("
                + "`ID` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`profile` TEXT NOT NULL DEFAULT '',"
                + "`serviceName` TEXT NOT NULL DEFAULT '',"
                + "`dueTime` INTEGER NOT NULL DEFAULT 0,"
                + "`lastRunTime` INTEGER NOT NULL DEFAULT 0,"
                + "`status` TEXT NOT NULL DEFAULT '',"
                + "`lastSummary` TEXT NOT NULL DEFAULT '',"
                + "`lastFingerprint` TEXT NOT NULL DEFAULT '',"
                + "`updateTime` INTEGER NOT NULL DEFAULT 0,"
                + "UNIQUE(`profile`,`serviceName`)"
                + ")");
        storage().update("CREATE TABLE IF NOT EXISTS `"+LOG_TABLE+"` ("
                + "`ID` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`profile` TEXT NOT NULL DEFAULT '',"
                + "`serviceName` TEXT NOT NULL DEFAULT '',"
                + "`status` TEXT NOT NULL DEFAULT '',"
                + "`executed` INTEGER NOT NULL DEFAULT 0,"
                + "`summary` TEXT NOT NULL DEFAULT '',"
                + "`fingerprint` TEXT NOT NULL DEFAULT '',"
                + "`memoryID` INTEGER NOT NULL DEFAULT 0,"
                + "`shareStatus` TEXT NOT NULL DEFAULT '',"
                + "`reason` TEXT NOT NULL DEFAULT '',"
                + "`sourceUrl` TEXT NOT NULL DEFAULT '',"
                + "`cost` TEXT NOT NULL DEFAULT '',"
                + "`createTime` INTEGER NOT NULL DEFAULT 0"
                + ")");
        storage().update("CREATE INDEX IF NOT EXISTS `idx_plugin_mbb_roleplay_activity_log_profile` "
                + "ON `"+LOG_TABLE+"` (`profile`,`ID`)");
        storage().update("DELETE FROM `"+LOG_TABLE+"` WHERE `createTime`<?",
                System.currentTimeMillis() - 90L * 86400000L);
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
        JSONObject result = new JSONObject(true);
        result.put("status",true);
        result.put("enable",config.randomActivityEnable);
        JSONArray services = new JSONArray();
        for (String item : parseServices(config.randomActivityServices)) services.add(item);
        result.put("services",services);
        JSONArray states = new JSONArray();
        List<JSONObject> rows = storage().query(
                "SELECT `serviceName`,`dueTime`,`lastRunTime`,`status`,`lastSummary`,`updateTime` "
                        +"FROM `"+STATE_TABLE+"` WHERE `profile`=? ORDER BY `serviceName` ASC",
                service.getRoleName());
        if (rows != null) {
            for (JSONObject row : rows) {
                JSONObject item = new JSONObject(true);
                item.put("serviceName",row.getString("serviceName"));
                item.put("dueTime",row.getLongValue("dueTime"));
                item.put("lastRunTime",row.getLongValue("lastRunTime"));
                item.put("status",row.getString("status"));
                item.put("summary",row.getString("lastSummary"));
                states.add(item);
            }
        }
        result.put("states",states);
        return result;
    }

    private void tick() {
        if (!config.randomActivityEnable || isQuietHour()) return;
        if (!running.compareAndSet(false,true)) return;
        try {
            service.submitBackground(this::runCycle);
        } catch (Exception e) {
            running.set(false);
            plugin.getLogger().sendException(e);
        }
    }

    private void runCycle() {
        try {
            long now = System.currentTimeMillis();
            List<String> services = parseServices(config.randomActivityServices);
            if (services.isEmpty()) return;
            List<ActivityPlan> due = new ArrayList<>();
            for (String serviceName : services) {
                ActivityPlan plan = duePlan(serviceName,now);
                if (plan != null) due.add(plan);
            }
            if (due.isEmpty()) return;
            Collections.sort(due,new Comparator<ActivityPlan>() {
                @Override
                public int compare(ActivityPlan left,ActivityPlan right) {
                    if (left.dueTime < right.dueTime) return -1;
                    if (left.dueTime > right.dueTime) return 1;
                    return Integer.compare(right.priority,left.priority);
                }
            });
            runActivity(due.get(0));
        } catch (Exception e) {
            plugin.getLogger().sendException(e);
        } finally {
            running.set(false);
        }
    }

    private ActivityPlan duePlan(String serviceName,long now) {
        PluginService provider = plugin.getServer().getPluginManager().getService(serviceName);
        if (provider == null) return null;
        JSONObject state = storage().queryOne(
                "SELECT `dueTime` FROM `"+STATE_TABLE+"` WHERE `profile`=? AND `serviceName`=?",
                service.getRoleName(),serviceName);
        if (state == null) {
            ActivityPlan plan = loadPlan(provider,serviceName);
            if (plan != null) schedule(serviceName,now + randomIntervalMillis(
                    plan.minIntervalMinute,plan.maxIntervalMinute,Math.random()),"scheduled","","",now);
            return null;
        }
        if (state.getLongValue("dueTime") > now) return null;
        ActivityPlan plan = loadPlan(provider,serviceName);
        if (plan == null) {
            schedule(serviceName,now + 3600000L,"unsupported","plan 调用失败","",now);
            return null;
        }
        int dailyCount = dailyExecutedCount(serviceName,startOfToday(now));
        if (dailyCount >= plan.dailyMax) {
            schedule(serviceName,nextDayStart(now),"daily_limit","今日次数已满","",now);
            return null;
        }
        plan.dueTime = state.getLongValue("dueTime");
        return plan;
    }

    private ActivityPlan loadPlan(PluginService provider,String serviceName) {
        JSONObject params = new JSONObject(true);
        params.put("profile",service.getRoleName());
        params.put("roleName",service.getRoleName());
        params.put("timeoutSeconds",config.randomActivityTimeoutSecond);
        JSONObject result = provider.call("activity_plan",params);
        if (result == null || !result.getBooleanValue("status")
                || !result.getBooleanValue("enabled")) {
            return null;
        }
        ActivityPlan plan = new ActivityPlan();
        plan.serviceName = serviceName;
        plan.minIntervalMinute = Math.max(1,result.getIntValue("minIntervalMinute"));
        if (plan.minIntervalMinute <= 1) plan.minIntervalMinute = DEFAULT_MIN_INTERVAL_MINUTE;
        plan.maxIntervalMinute = Math.max(plan.minIntervalMinute,result.getIntValue("maxIntervalMinute"));
        if (plan.maxIntervalMinute <= plan.minIntervalMinute) {
            plan.maxIntervalMinute = DEFAULT_MAX_INTERVAL_MINUTE;
        }
        plan.dailyMax = result.getIntValue("dailyMax");
        if (plan.dailyMax <= 0) plan.dailyMax = DEFAULT_DAILY_MAX;
        plan.priority = result.getIntValue("priority");
        if (plan.priority <= 0) plan.priority = 50;
        return plan;
    }

    private void runActivity(ActivityPlan plan) {
        long now = System.currentTimeMillis();
        PluginService provider = plugin.getServer().getPluginManager().getService(plan.serviceName);
        if (provider == null) {
            schedule(plan.serviceName,now + 3600000L,"missing","插件服务不可用","",now);
            return;
        }
        JSONObject params = new JSONObject(true);
        params.put("profile",service.getRoleName());
        params.put("roleName",service.getRoleName());
        params.put("trigger","random_activity");
        params.put("currentTime",service.currentTimeText());
        params.put("timeoutSeconds",config.randomActivityTimeoutSecond);
        params.put("recentSelfMemory",service.brainMemoryService().selfPromptText(
                0L,0L,"最近生活与兴趣",2000));
        params.put("recentActivities",recentActivities(8));
        JSONObject result;
        try {
            result = provider.call("activity_run",params);
        } catch (Exception e) {
            result = null;
            plugin.getLogger().sendWarn("[活动] "+plan.serviceName+" 调用异常："
                    +service.safe(e.getMessage()));
        }
        if (result == null || !result.getBooleanValue("status")) {
            String reason = result == null ? "插件无返回" : service.safe(result.getString("message"));
            record(plan.serviceName,"failed",false,"","","",reason,"","",now);
            schedule(plan.serviceName,now + 3600000L,"failed",reason,"",now);
            return;
        }
        if (!result.getBooleanValue("executed")) {
            String reason = service.safe(result.getString("reason"));
            record(plan.serviceName,"no_op",false,"","","",reason,"","",now);
            schedule(plan.serviceName,nextDue(plan,result,now),"no_op",reason,"",now);
            return;
        }
        int nextMinute = result.getIntValue("nextCheckMinute");
        long next = nextMinute > 0
                ? now + Math.max(plan.minIntervalMinute,nextMinute) * 60000L
                : now + randomIntervalMillis(plan.minIntervalMinute,plan.maxIntervalMinute,Math.random());
        String summary = service.shortText(service.safe(result.getString("summary")).trim(),500);
        String sourceUrl = service.safe(result.getString("sourceUrl")).trim();
        String externalId = service.safe(result.getString("externalId")).trim();
        String fingerprint = service.safe(result.getString("fingerprint")).trim();
        if (fingerprint.isEmpty()) {
            fingerprint = plan.serviceName+"|"+externalId+"|"+SpeechCorpusEntry.normalize(summary);
        }
        if (fingerprint.length() > 300) fingerprint = fingerprint.substring(0,300);
        long memoryID = rememberActivity(plan,result,externalId,fingerprint,now);
        String shareStatus = shareActivity(result.getJSONObject("share"),sourceUrl,now);
        record(plan.serviceName,"executed",true,summary,fingerprint,String.valueOf(memoryID),
                service.safe(result.getString("reason")),shareStatus,sourceUrl,
                result.get("cost") == null ? "" : result.get("cost").toString(),now);
        schedule(plan.serviceName,next,"executed",summary,fingerprint,now);
        plugin.getLogger().sendInfo("[活动] "+plan.serviceName+" 已执行"
                +(summary.isEmpty() ? "" : "："+service.shortText(summary,80))
                +(shareStatus.isEmpty() ? "" : " 分享="+shareStatus));
    }

    private long rememberActivity(ActivityPlan plan,JSONObject result,String externalId,
                                  String fingerprint,long now) {
        JSONObject memory = result.getJSONObject("memory");
        String content = service.safe(result.getString("memoryContent")).trim();
        if (memory != null && !service.safe(memory.getString("content")).trim().isEmpty()) {
            content = memory.getString("content").trim();
        }
        if (content.isEmpty()) return 0L;
        JSONObject params = new JSONObject(true);
        params.put("scope","self");
        params.put("type",memory == null || service.safe(memory.getString("type")).trim().isEmpty()
                ? "activity" : memory.getString("type").trim());
        params.put("content",content);
        params.put("importance",memory == null
                ? config.randomActivityMemoryImportance : memory.getIntValue("importance"));
        params.put("eventTime",now);
        params.put("sourceType",plan.serviceName);
        params.put("sourceId",externalId.isEmpty() ? fingerprint : externalId);
        params.put("confidence",memory == null ? 0.9 : memory.getDoubleValue("confidence"));
        JSONObject saved = service.brainMemoryService().remember(params);
        return saved.getBooleanValue("status") ? saved.getLongValue("id") : 0L;
    }

    private String shareActivity(JSONObject share,String sourceUrl,long now) {
        if (!config.randomActivityShareEnable || share == null || share.isEmpty()) return "";
        double score = share.containsKey("score") ? share.getDoubleValue("score") : 1.0;
        if (score < config.randomActivityShareMinScore) return "score_blocked";
        String channel = service.safe(share.getString("channel")).trim().toLowerCase(Locale.ROOT);
        long targetID = share.getLongValue("targetID");
        String content = service.safe(share.getString("content")).trim();
        if (content.isEmpty()) return "empty";
        if (content.length() > 500) content = content.substring(0,500).trim();
        if (!sourceUrl.isEmpty() && !content.contains(sourceUrl)) {
            content = content+"\n"+sourceUrl;
        }
        if ("private".equals(channel)) {
            if (targetID <= 0) targetID = ownerOrZero();
            return targetID > 0 && service.privateChatService().sendProactive(
                    targetID,content,service.safe(share.getString("reason")))
                    ? "private" : "private_failed";
        }
        if ("group".equals(channel)) {
            return targetID > 0 && sendGroup(targetID,content) ? "group" : "group_failed";
        }
        if ("qzone".equals(channel)) {
            if (targetID <= 0) targetID = ownerOrZero();
            String text = RoleplayReplySanitizer.clean(service.persona(),
                    plugin.getServer().getBotName(),content).trim();
            return sendQzone(targetID,text) ? "qzone" : "qzone_failed";
        }
        return "unsupported";
    }

    private boolean sendGroup(long groupID,String content) {
        OneBotClient client = plugin.getServer().getOneBotClient();
        if (client == null || !client.isConnected()) return false;
        String text = RoleplayReplySanitizer.clean(service.persona(),
                plugin.getServer().getBotName(),content).trim();
        if (text.isEmpty()) return false;
        text = service.messageSender().normalizeSentencePeriods(groupID,text);
        JSONObject response = client.sendGroupMessage(groupID,
                MessageUtil.message(MessageUtil.text(text)));
        if (response == null || response.getIntValue("retcode") != 0) return false;
        long messageID = response.getJSONObject("data") == null
                ? 0L : response.getJSONObject("data").getLongValue("message_id");
        service.recordBotMessage(groupID,loginID(),text,messageID);
        return true;
    }

    private long loginID() {
        OneBotClient client = plugin.getServer().getOneBotClient();
        if (client == null || !client.isConnected()) return 0L;
        JSONObject response = client.callAction("get_login_info",new JSONObject(true));
        JSONObject data = response == null ? null : response.getJSONObject("data");
        return data == null ? 0L : data.getLongValue("user_id");
    }

    private boolean sendQzone(long ownerID,String content) {
        PluginService qq = plugin.getServer().getPluginManager().getService("MBB-QQ");
        if (qq == null || ownerID <= 0) return false;
        JSONObject params = new JSONObject(true);
        params.put("operatorID",ownerID);
        params.put("content",content);
        params.put("ugcRight",config.qzoneUgcRight);
        JSONObject result = qq.call("qzone_post",params);
        return result != null && result.getBooleanValue("status");
    }

    private long ownerOrZero() {
        List<Long> owners = plugin.getServer().getOwnerList();
        return owners == null || owners.isEmpty() || owners.get(0) == null ? 0L : owners.get(0);
    }

    private long nextDue(ActivityPlan plan,JSONObject result,long now) {
        int minute = result.getIntValue("nextCheckMinute");
        if (minute > 0) return now + Math.max(plan.minIntervalMinute,minute) * 60000L;
        return now + randomIntervalMillis(plan.minIntervalMinute,plan.maxIntervalMinute,Math.random());
    }

    private int dailyExecutedCount(String serviceName,long since) {
        JSONObject row = storage().queryOne(
                "SELECT COUNT(*) AS `count` FROM `"+LOG_TABLE+"` WHERE `profile`=? "
                        +"AND `serviceName`=? AND `executed`=1 AND `createTime`>=?",
                service.getRoleName(),serviceName,since);
        return row == null ? 0 : row.getIntValue("count");
    }

    private String recentActivities(int limit) {
        List<JSONObject> rows = storage().query(
                "SELECT `serviceName`,`status`,`summary`,`reason`,`shareStatus`,`createTime` FROM `"
                        +LOG_TABLE+"` WHERE `profile`=? ORDER BY `ID` DESC LIMIT ?",
                service.getRoleName(),Math.max(1,limit));
        if (rows == null || rows.isEmpty()) return "暂无。";
        StringBuilder builder = new StringBuilder();
        for (JSONObject row : rows) {
            builder.append("- ").append(service.formatMemoryTime(row.getLongValue("createTime")))
                    .append(" ").append(service.safe(row.getString("serviceName")))
                    .append(" ").append(service.safe(row.getString("status")));
            String summary = service.safe(row.getString("summary"));
            if (!summary.isEmpty()) builder.append("：").append(service.shortText(summary,100));
            builder.append("\n");
        }
        return builder.toString();
    }

    private void schedule(String serviceName,long due,String status,String reason,
                          String fingerprint,long now) {
        JSONObject row = storage().queryOne(
                "SELECT `ID` FROM `"+STATE_TABLE+"` WHERE `profile`=? AND `serviceName`=?",
                service.getRoleName(),serviceName);
        if (row == null) {
            storage().insert("INSERT INTO `"+STATE_TABLE+"` "
                            +"(`profile`,`serviceName`,`dueTime`,`lastRunTime`,`status`,"
                            +"`lastSummary`,`lastFingerprint`,`updateTime`) VALUES (?,?,?,?,?,?,?,?)",
                    service.getRoleName(),serviceName,due,now,status,
                    service.shortText(reason,300),fingerprint,now);
        } else {
            storage().update("UPDATE `"+STATE_TABLE+"` SET `dueTime`=?,`lastRunTime`=?,"
                            +"`status`=?,`lastSummary`=?,`lastFingerprint`=?,`updateTime`=? WHERE `ID`=?",
                    due,now,status,service.shortText(reason,300),fingerprint,now,
                    row.getLongValue("ID"));
        }
    }

    private void record(String serviceName,String status,boolean executed,String summary,
                        String fingerprint,String memoryID,String reason,String shareStatus,
                        String sourceUrl,long now) {
        record(serviceName,status,executed,summary,fingerprint,memoryID,reason,shareStatus,
                sourceUrl,"",now);
    }

    private void record(String serviceName,String status,boolean executed,String summary,
                        String fingerprint,String memoryID,String reason,String shareStatus,
                        String sourceUrl,String cost,long now) {
        storage().insert("INSERT INTO `"+LOG_TABLE+"` "
                        +"(`profile`,`serviceName`,`status`,`executed`,`summary`,`fingerprint`,"
                        +"`memoryID`,`shareStatus`,`reason`,`sourceUrl`,`cost`,`createTime`) "
                        +"VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                service.getRoleName(),serviceName,status,executed ? 1 : 0,
                service.shortText(summary,500),fingerprint,memoryID,
                shareStatus,service.shortText(reason,500),sourceUrl,cost,now);
    }

    static List<String> parseServices(String value) {
        List<String> result = new ArrayList<>();
        if (value == null) return result;
        Set<String> seen = new LinkedHashSet<>();
        for (String item : value.split(",")) {
            String name = item == null ? "" : item.trim();
            if (name.isEmpty() || !seen.add(name.toLowerCase(Locale.ROOT))) continue;
            result.add(name);
            if (result.size() >= 8) break;
        }
        return result;
    }

    static long randomIntervalMillis(int minMinute,int maxMinute,double roll) {
        int min = Math.max(1,minMinute);
        int max = Math.max(min,maxMinute);
        double safeRoll = Math.max(0,Math.min(0.999999,roll));
        long minute = min + (long)(safeRoll * (max - min + 1));
        return minute * 60000L;
    }

    private boolean isQuietHour() {
        int hour = ZonedDateTime.now(zone()).getHour();
        int start = config.randomTickQuietStartHour;
        int end = config.randomTickQuietEndHour;
        if (start == end) return false;
        return start < end ? hour >= start && hour < end : hour >= start || hour < end;
    }

    private long startOfToday(long now) {
        return ZonedDateTime.now(zone()).toLocalDate().atStartOfDay(zone())
                .toInstant().toEpochMilli();
    }

    private long nextDayStart(long now) {
        int wake = Math.max(0,Math.min(23,config.randomTickQuietEndHour));
        return ZonedDateTime.now(zone()).toLocalDate().plusDays(1)
                .atStartOfDay(zone()).plusHours(wake).toInstant().toEpochMilli()
                + (long)(Math.random() * 1800000L);
    }

    private ZoneId zone() {
        String value = config.timeZone == null || config.timeZone.trim().isEmpty()
                ? "Asia/Shanghai" : config.timeZone.trim();
        return ZoneId.of(value);
    }

    private StorageService storage() {
        return plugin.getServer().getStorage();
    }
}
