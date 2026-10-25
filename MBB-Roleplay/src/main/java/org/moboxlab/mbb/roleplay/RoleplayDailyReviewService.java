package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Plugin;
import org.moboxlab.moboxbot.API.PluginService;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每日复盘服务
 *
 * 按本地时区在配置的小时执行一次，把当天群聊压成一条短期记忆。
 * 独立于 RoleplayService，避免主服务继续膨胀。
 */
public class RoleplayDailyReviewService {
    private final Plugin plugin;
    private final RoleplayService service;
    private volatile RoleplayConfig config;
    private volatile boolean scheduled = false;
    private volatile String lastDay = "";
    private final Set<Long> running = Collections.newSetFromMap(new ConcurrentHashMap<Long,Boolean>());

    public RoleplayDailyReviewService(Plugin plugin,RoleplayConfig config,RoleplayService service) {
        this.plugin = plugin;
        this.config = config;
        this.service = service;
    }

    public void reload(RoleplayConfig config) {
        this.config = config;
    }

    public void ensureTask() {
        if (scheduled) return;
        scheduled = true;
        plugin.getServer().getPluginManager().runTaskTimer(plugin,this::tick,60,3600);
    }

    public void trigger(long groupID) {
        if (groupID <= 0 || !config.dailyReviewEnable) return;
        String day = java.time.ZonedDateTime.now(java.time.ZoneId.of(config.timeZone))
                .toLocalDate().toString();
        service.submitBackground(() -> reviewGroup(groupID,day));
    }

    private void tick() {
        if (!config.dailyReviewEnable) return;
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(java.time.ZoneId.of(config.timeZone));
        if (now.getHour() != config.dailyReviewHour) return;
        String day = now.toLocalDate().toString();
        if (day.equals(lastDay)) return;
        lastDay = day;
        JSONArray groups = service.listGroups();
        for (Object object : groups) {
            long groupID = object == null ? 0L : Long.parseLong(String.valueOf(object));
            if (groupID <= 0) continue;
            service.submitBackground(() -> reviewGroup(groupID,day));
        }
        plugin.getLogger().sendInfo("[复盘] "+day+" 每日复盘已开始，群数="+groups.size());
    }

    private void reviewGroup(long groupID,String day) {
        if (!running.add(groupID)) return;
        try {
            long now = System.currentTimeMillis();
            List<JSONObject> rows = service.storage().query(
                    "SELECT `messageID`,`userName`,`content`,`messageTime`,`ID` FROM `"+RoleplayService.MSG_TABLE+"` "
                            + "WHERE `groupID`=? AND `messageTime`>=? ORDER BY `messageTime` ASC,`ID` ASC LIMIT ?",
                    groupID,now - 24L * 60L * 60L * 1000L,config.dailyReviewMaxMessages);
            if (rows == null || rows.isEmpty()) return;
            StringBuilder transcript = new StringBuilder();
            long lastTime = 0L;
            long lastID = 0L;
            for (JSONObject row : rows) {
                lastTime = row.getLongValue("messageTime");
                lastID = row.getLongValue("ID");
                transcript.append("[").append(new java.text.SimpleDateFormat("HH:mm",java.util.Locale.CHINA)
                                .format(new java.util.Date(lastTime))).append("] ")
                        .append(service.safe(row.getString("userName"))).append("：")
                        .append(service.safe(row.getString("content"))).append("\n");
            }
            PluginService ai = plugin.getServer().getPluginManager().getService("MBB-AI");
            if (ai == null) return;
            JSONArray messages = new JSONArray();
            messages.add(service.message("system","你是角色扮演插件的每日复盘器。角色："+service.getRoleName()
                    +"。把下面一天内的群聊整理成一条短期记忆，保留主要话题、群内氛围、角色自己做过的事、"
                    +"群友之间的重要互动和有长期价值的信息；忽略零碎寒暄、重复内容、广告、图片和表情。"
                    +"同时更新当前群的结构化上下文：topics 是仍在进行的话题，background 是接续对话必须知道的前提，"
                    +"progress 是已确认的进展或结论，pending 是还没完成的请求或待办，agreements 是明确同意过的做法。"
                    +"已解决的事项要从 pending 移除；没有内容的字段返回空数组。"
                    +"只输出 JSON，不要 Markdown：{\"shortTerm\":\"整理后的短期记忆\","
                    +"\"context\":{\"topics\":[],\"background\":[],\"progress\":[],\"pending\":[],\"agreements\":[]}}。"
                    +"已有短期记忆如下：\n"+service.shortSummary(groupID)
                    +"\n\n已有结构化上下文如下：\n"+service.contextCompactor().modelText(groupID)));
            messages.add(service.message("user",transcript.toString()));
            JSONObject params = new JSONObject(true);
            String profile = config.memoryProfile == null || config.memoryProfile.trim().isEmpty()
                    ? config.aiProfile : config.memoryProfile.trim();
            params.put("profile",profile);
            params.put("maxTokens",config.dailyReviewMaxTokens);
            params.put("temperature",0.1);
            params.put("reasoningEffort",config.memoryReasoningEffort);
            params.put("timeoutSeconds",config.dailyReviewTimeoutSecond);
            params.put("retryCount",0);
            params.put("sessionId","roleplay-daily-"+groupID+"-"+day);
            params.put("messages",messages);
            RoleplayAiMetadata.tagTemplate(params,"roleplay.daily_review",
                    RoleplayDailyReviewService.class);
            long startTime = System.currentTimeMillis();
            JSONObject response = ai.call("chat",params);
            RoleplayAiLog.log(plugin.getLogger(),"每日复盘",groupID,response,
                    System.currentTimeMillis() - startTime);
            if (response == null || !response.getBooleanValue("status")) return;
            JSONObject parsed = service.parseJson(response.getString("content"));
            if (parsed == null) parsed = service.parseJson(response.getString("reasoningContent"));
            String summary = parsed == null ? "" : service.safe(parsed.getString("shortTerm")).trim();
            if (summary.isEmpty()) {
                plugin.getLogger().sendWarn("[复盘] 群"+groupID+" 没有返回有效 shortTerm");
                return;
            }
            service.saveMemoryState(groupID,summary,lastTime,lastID);
            service.contextCompactor().saveFromModel(groupID,parsed,lastID);
            plugin.getLogger().sendInfo("[复盘] 群"+groupID+" 每日复盘完成，短期记忆"
                    +summary.length()+"字");
        } catch (Exception e) {
            plugin.getLogger().sendException(e);
        } finally {
            running.remove(groupID);
        }
    }
}
