package org.moboxlab.mbb.aiguard;

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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * AI 风险审查服务
 */
public class GuardService {
    private static final String EVENT_TABLE = "plugin_mbb_aiguard_events";
    private static final String DAILY_TABLE = "plugin_mbb_aiguard_daily";
    private static final String RECENT_TABLE = "plugin_mbb_aiguard_recent";
    private static final String WHITELIST_TABLE = "plugin_mbb_aiguard_whitelist";
    private static final String GROUP_TABLE = "plugin_mbb_aiguard_group_config";

    private final Plugin plugin;
    private final GuardConfig config;
    private final GuardRuleEngine ruleEngine;
    private final Map<String,Long> lastCheckMap = new LinkedHashMap<>();
    private final Map<String,long[]> rateMap = new LinkedHashMap<>();

    public GuardService(Plugin plugin,GuardConfig config,GuardRuleEngine ruleEngine) {
        this.plugin = plugin;
        this.config = config;
        this.ruleEngine = ruleEngine;
    }

    public void init() {
        storage().update("CREATE TABLE IF NOT EXISTS `"+EVENT_TABLE+"` ("
                + "`ID` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`groupID` INTEGER NOT NULL DEFAULT 0,"
                + "`userID` INTEGER NOT NULL DEFAULT 0,"
                + "`messageID` INTEGER NOT NULL DEFAULT 0,"
                + "`messageTime` INTEGER NOT NULL DEFAULT 0,"
                + "`riskScore` INTEGER NOT NULL DEFAULT 0,"
                + "`categories` TEXT NOT NULL DEFAULT '',"
                + "`reason` TEXT NOT NULL DEFAULT '',"
                + "`evidence` TEXT NOT NULL DEFAULT '',"
                + "`action` TEXT NOT NULL DEFAULT '',"
                + "`safety` INTEGER NOT NULL DEFAULT 0,"
                + "`updateTime` INTEGER NOT NULL DEFAULT 0"
                + ")");
        storage().update("CREATE TABLE IF NOT EXISTS `"+DAILY_TABLE+"` ("
                + "`ID` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`statDate` TEXT NOT NULL DEFAULT '',"
                + "`groupID` INTEGER NOT NULL DEFAULT 0,"
                + "`userID` INTEGER NOT NULL DEFAULT 0,"
                + "`category` TEXT NOT NULL DEFAULT '',"
                + "`count` INTEGER NOT NULL DEFAULT 0,"
                + "`maxScore` INTEGER NOT NULL DEFAULT 0,"
                + "`lastTime` INTEGER NOT NULL DEFAULT 0,"
                + "UNIQUE(`statDate`,`groupID`,`userID`,`category`)"
                + ")");
        storage().update("CREATE TABLE IF NOT EXISTS `"+RECENT_TABLE+"` ("
                + "`ID` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`groupID` INTEGER NOT NULL DEFAULT 0,"
                + "`userID` INTEGER NOT NULL DEFAULT 0,"
                + "`userName` TEXT NOT NULL DEFAULT '',"
                + "`messageTime` INTEGER NOT NULL DEFAULT 0,"
                + "`content` TEXT NOT NULL DEFAULT ''"
                + ")");
        storage().update("CREATE TABLE IF NOT EXISTS `"+WHITELIST_TABLE+"` ("
                + "`ID` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`scope` TEXT NOT NULL DEFAULT 'group',"
                + "`userID` INTEGER NOT NULL DEFAULT 0,"
                + "`groupID` INTEGER NOT NULL DEFAULT 0,"
                + "`operatorID` INTEGER NOT NULL DEFAULT 0,"
                + "`createTime` INTEGER NOT NULL DEFAULT 0,"
                + "UNIQUE(`scope`,`userID`,`groupID`)"
                + ")");
        storage().update("CREATE TABLE IF NOT EXISTS `"+GROUP_TABLE+"` ("
                + "`ID` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`groupID` INTEGER NOT NULL DEFAULT 0 UNIQUE,"
                + "`enabled` INTEGER NOT NULL DEFAULT 0,"
                + "`threshold` INTEGER NOT NULL DEFAULT 70,"
                + "`updateTime` INTEGER NOT NULL DEFAULT 0"
                + ")");
        storage().update("CREATE INDEX IF NOT EXISTS `idx_plugin_mbb_aiguard_event_time` ON `"+EVENT_TABLE+"` (`messageTime`)");
        storage().update("CREATE INDEX IF NOT EXISTS `idx_plugin_mbb_aiguard_recent_group` ON `"+RECENT_TABLE+"` (`groupID`,`messageTime`)");
    }

    public void handle(GroupMessageEvent event) {
        if (event == null || !config.enable) return;
        if (!isGroupEnabled(event.getGroupID())) return;
        String content = extractContent(event.getMessage());
        List<GuardMatch> matches = ruleEngine.match(content);
        boolean safetyMatch = hasSafety(matches);
        if (isWhitelisted(event.getUserID(),event.getGroupID())
                && !(safetyMatch && !config.whitelistBypassSafety)) return;
        recordRecent(event,content);
        if (matches.isEmpty()) return;
        if (!checkCooldown(event.getGroupID(),event.getUserID())) return;

        JSONObject result = analyze(event.getGroupID(),event.getUserID(),content,matches,event.getRaw().getString("group_name"));
        if (result == null) result = fallback(matches);
        int score = result.getIntValue("score");
        String categories = result.getString("categories");
        String action = result.getString("action");
        boolean safety = result.getBooleanValue("safety") || safetyMatch;
        if (score < config.candidateRiskScore && !safety) return;

        recordEvent(event,score,categories,result.getString("reason"),result.getString("evidence"),action,safety);
        recordDaily(event.getGroupID(),event.getUserID(),categories,score);
        if (safety || score >= getGroupThreshold(event.getGroupID())) {
            alert(event.getGroupID(),event.getUserID(),score,categories,result.getString("reason"),result.getString("evidence"),safety);
        }
    }

    public JSONObject test(long groupID,long userID,String content) {
        List<GuardMatch> matches = ruleEngine.match(content);
        if (matches.isEmpty()) {
            JSONObject result = new JSONObject(true);
            result.put("status",true);
            result.put("score",0);
            result.put("categories","无");
            result.put("reason","未命中任何规则");
            result.put("evidence","");
            result.put("action","ignore");
            result.put("safety",false);
            return result;
        }
        JSONObject result = analyze(groupID,userID,content,matches,"");
        return result == null ? fallback(matches) : result;
    }

    public boolean isGroupEnabled(long groupID) {
        JSONObject row = storage().queryOne("SELECT `enabled` FROM `"+GROUP_TABLE+"` WHERE `groupID`=?",groupID);
        if (row == null) return config.defaultGroupEnable;
        return row.getIntValue("enabled") == 1;
    }

    public void setGroupEnabled(long groupID,boolean enabled) {
        JSONObject row = storage().queryOne("SELECT `ID` FROM `"+GROUP_TABLE+"` WHERE `groupID`=?",groupID);
        long now = System.currentTimeMillis();
        if (row == null) {
            storage().insert("INSERT INTO `"+GROUP_TABLE+"` (`groupID`,`enabled`,`threshold`,`updateTime`) VALUES (?,?,?,?)",
                    groupID,enabled ? 1 : 0,config.riskThreshold,now);
        } else {
            storage().update("UPDATE `"+GROUP_TABLE+"` SET `enabled`=?,`updateTime`=? WHERE `groupID`=?",
                    enabled ? 1 : 0,now,groupID);
        }
    }

    public int getGroupThreshold(long groupID) {
        JSONObject row = storage().queryOne("SELECT `threshold` FROM `"+GROUP_TABLE+"` WHERE `groupID`=?",groupID);
        return row == null || row.getIntValue("threshold") <= 0 ? config.riskThreshold : row.getIntValue("threshold");
    }

    public void setGroupThreshold(long groupID,int threshold) {
        if (threshold < 1) threshold = 1;
        if (threshold > 100) threshold = 100;
        JSONObject row = storage().queryOne("SELECT `ID` FROM `"+GROUP_TABLE+"` WHERE `groupID`=?",groupID);
        long now = System.currentTimeMillis();
        if (row == null) {
            storage().insert("INSERT INTO `"+GROUP_TABLE+"` (`groupID`,`enabled`,`threshold`,`updateTime`) VALUES (?,?,?,?)",
                    groupID,config.defaultGroupEnable ? 1 : 0,threshold,now);
        } else {
            storage().update("UPDATE `"+GROUP_TABLE+"` SET `threshold`=?,`updateTime`=? WHERE `groupID`=?",
                    threshold,now,groupID);
        }
    }

    public JSONArray listEnabledGroups() {
        JSONArray result = new JSONArray();
        List<JSONObject> rows = storage().query("SELECT `groupID`,`threshold` FROM `"+GROUP_TABLE+"` WHERE `enabled`=1 ORDER BY `groupID` ASC");
        if (rows == null) return result;
        for (JSONObject row : rows) {
            JSONObject item = new JSONObject(true);
            item.put("groupID",row.getLongValue("groupID"));
            item.put("threshold",row.getIntValue("threshold"));
            result.add(item);
        }
        return result;
    }

    public boolean isWhitelisted(long userID,long groupID) {
        JSONObject row = storage().queryOne(
                "SELECT `ID` FROM `"+WHITELIST_TABLE+"` WHERE `userID`=? AND (`scope`='global' OR (`scope`='group' AND `groupID`=?)) LIMIT 1",
                userID,groupID);
        return row != null;
    }

    public boolean addWhitelist(String scope,long userID,long groupID,long operatorID) {
        JSONObject exists = storage().queryOne(
                "SELECT `ID` FROM `"+WHITELIST_TABLE+"` WHERE `scope`=? AND `userID`=? AND `groupID`=?",
                scope,userID,groupID);
        if (exists != null) return false;
        storage().insert("INSERT INTO `"+WHITELIST_TABLE+"` (`scope`,`userID`,`groupID`,`operatorID`,`createTime`) VALUES (?,?,?,?,?)",
                scope,userID,groupID,operatorID,System.currentTimeMillis());
        return true;
    }

    public boolean removeWhitelist(String scope,long userID,long groupID) {
        int rows = storage().update("DELETE FROM `"+WHITELIST_TABLE+"` WHERE `scope`=? AND `userID`=? AND `groupID`=?",
                scope,userID,groupID);
        return rows > 0;
    }

    public JSONArray listWhitelist(long groupID) {
        JSONArray result = new JSONArray();
        List<JSONObject> rows = storage().query(
                "SELECT `scope`,`userID`,`groupID`,`operatorID`,`createTime` FROM `"+WHITELIST_TABLE+"` "
                        + "WHERE `scope`='global' OR `groupID`=? ORDER BY `scope` ASC,`userID` ASC",
                groupID);
        if (rows == null) return result;
        for (JSONObject row : rows) {
            JSONObject item = new JSONObject(true);
            item.put("scope",row.getString("scope"));
            item.put("userID",row.getLongValue("userID"));
            item.put("groupID",row.getLongValue("groupID"));
            item.put("operatorID",row.getLongValue("operatorID"));
            item.put("createTime",row.getLongValue("createTime"));
            result.add(item);
        }
        return result;
    }

    public JSONArray recentEvents(int limit) {
        if (limit < 1) limit = 10;
        if (limit > 50) limit = 50;
        JSONArray result = new JSONArray();
        List<JSONObject> rows = storage().query(
                "SELECT `ID`,`groupID`,`userID`,`messageTime`,`riskScore`,`categories`,`reason`,`action`,`safety` "
                        + "FROM `"+EVENT_TABLE+"` ORDER BY `messageTime` DESC LIMIT ?",limit);
        if (rows == null) return result;
        for (JSONObject row : rows) {
            JSONObject item = new JSONObject(true);
            item.put("id",row.getLongValue("ID"));
            item.put("groupID",row.getLongValue("groupID"));
            item.put("userID",row.getLongValue("userID"));
            item.put("messageTime",row.getLongValue("messageTime"));
            item.put("score",row.getIntValue("riskScore"));
            item.put("categories",row.getString("categories"));
            item.put("reason",row.getString("reason"));
            item.put("action",row.getString("action"));
            item.put("safety",row.getIntValue("safety") == 1);
            result.add(item);
        }
        return result;
    }

    public int ruleCount() {
        return ruleEngine.size();
    }

    private JSONObject analyze(long groupID,long userID,String content,List<GuardMatch> matches,String groupName) {
        PluginService ai = plugin.getServer().getPluginManager().getService("MBB-AI");
        if (ai == null) return null;
        JSONArray messages = new JSONArray();
        messages.add(message("system","你是群聊内容风险审查器。只根据给出的当前消息、上下文、历史摘要和规则命中结果判断，"
                +"区分真实困难、玩梗、卖惨式求助和诈骗。不要编造证据。必须只返回 JSON："
                +"{\"risk\":true/false,\"score\":0-100,\"categories\":[\"分类\"],\"confidence\":0-1,"
                +"\"reason\":\"原因\",\"evidence\":[\"证据\"],\"action\":\"ignore|log|alert|warn\",\"safety\":false}。"
                +"自残、自杀等内容 safety=true，不得当作普通诈骗。"));
        StringBuilder source = new StringBuilder();
        source.append("群：").append(groupName == null ? "" : groupName).append("(").append(groupID).append(")\n");
        source.append("用户：").append(userID).append("\n");
        source.append("当前消息：").append(content).append("\n");
        source.append("规则命中：");
        for (GuardMatch match : matches) {
            source.append(match.category).append("(").append(match.risk).append(") ");
        }
        source.append("\n最近上下文：\n").append(loadContext(groupID));
        source.append("长期行为摘要：\n").append(historySummary(groupID,userID));
        messages.add(message("user",source.toString()));
        JSONObject params = new JSONObject(true);
        params.put("profile",config.aiProfile);
        params.put("sessionId","mobboxguard-"+groupID+"-"+userID);
        params.put("messages",messages);
        JSONObject response = ai.call("chat",params);
        if (response == null || !response.getBooleanValue("status")) return null;
        JSONObject parsed = parseJson(response.getString("content"));
        if (parsed == null) return null;
        int ruleScore = maxScore(matches);
        int score = Math.max(ruleScore,parsed.getIntValue("score"));
        Set<String> categories = new LinkedHashSet<>();
        JSONArray aiCategories = parsed.getJSONArray("categories");
        if (aiCategories != null) {
            for (Object category : aiCategories) {
                if (category != null) categories.add(String.valueOf(category));
            }
        }
        for (GuardMatch match : matches) categories.add(match.category);
        JSONObject result = new JSONObject(true);
        result.put("status",true);
        result.put("risk",parsed.getBooleanValue("risk") || score >= config.riskThreshold);
        result.put("score",Math.min(100,score));
        result.put("categories",join(categories));
        result.put("reason",safe(parsed.getString("reason")));
        result.put("evidence",joinArray(parsed.getJSONArray("evidence")));
        result.put("action",score >= config.riskThreshold ? "alert" : "log");
        result.put("safety",parsed.getBooleanValue("safety") || hasSafety(matches));
        return result;
    }

    private JSONObject fallback(List<GuardMatch> matches) {
        int score = maxScore(matches);
        Set<String> categories = new LinkedHashSet<>();
        List<String> evidence = new ArrayList<>();
        for (GuardMatch match : matches) {
            categories.add(match.category);
            evidence.addAll(match.keywords);
        }
        JSONObject result = new JSONObject(true);
        result.put("status",true);
        result.put("risk",score >= config.riskThreshold || hasSafety(matches));
        result.put("score",score);
        result.put("categories",join(categories));
        result.put("reason","规则引擎命中："+join(categories));
        result.put("evidence",joinStrings(evidence));
        result.put("action",score >= config.riskThreshold ? "alert" : "log");
        result.put("safety",hasSafety(matches));
        return result;
    }

    private void recordRecent(GroupMessageEvent event,String content) {
        if (content == null || content.isEmpty()) return;
        JSONObject sender = event.getSender();
        String userName = sender == null ? "" : sender.getString("card");
        if (userName == null || userName.isEmpty()) userName = sender == null ? "" : sender.getString("nickname");
        storage().insert("INSERT INTO `"+RECENT_TABLE+"` (`groupID`,`userID`,`userName`,`messageTime`,`content`) VALUES (?,?,?,?,?)",
                event.getGroupID(),event.getUserID(),userName == null ? "" : userName,System.currentTimeMillis(),content);
        long expire = System.currentTimeMillis() - config.retentionDays * 86400000L;
        storage().update("DELETE FROM `"+RECENT_TABLE+"` WHERE `messageTime`<?",expire);
    }

    private String loadContext(long groupID) {
        List<JSONObject> rows = storage().query(
                "SELECT `userName`,`content` FROM `"+RECENT_TABLE+"` WHERE `groupID`=? ORDER BY `messageTime` DESC LIMIT ?",
                groupID,config.contextMessages);
        if (rows == null || rows.isEmpty()) return "无\n";
        StringBuilder builder = new StringBuilder();
        for (int i = rows.size() - 1; i >= 0; i--) {
            JSONObject row = rows.get(i);
            builder.append(safe(row.getString("userName"))).append("：").append(safe(row.getString("content"))).append("\n");
        }
        return builder.toString();
    }

    private String historySummary(long groupID,long userID) {
        long start = System.currentTimeMillis() - config.historyDays * 86400000L;
        List<JSONObject> rows = storage().query(
                "SELECT `statDate`,`category`,`count`,`maxScore` FROM `"+DAILY_TABLE+"` "
                        + "WHERE `groupID`=? AND `userID`=? AND `lastTime`>=? ORDER BY `statDate` DESC",
                groupID,userID,start);
        if (rows == null || rows.isEmpty()) return "无历史风险记录。\n";
        Map<String,List<JSONObject>> byDate = new LinkedHashMap<>();
        Set<String> riskDates = new HashSet<>();
        for (JSONObject row : rows) {
            String date = safe(row.getString("statDate"));
            List<JSONObject> list = byDate.get(date);
            if (list == null) {
                list = new ArrayList<>();
                byDate.put(date,list);
            }
            list.add(row);
            if (row.getIntValue("maxScore") >= config.candidateRiskScore) riskDates.add(date);
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String,List<JSONObject>> entry : byDate.entrySet()) {
            builder.append(entry.getKey()).append("：");
            for (JSONObject row : entry.getValue()) {
                builder.append(row.getString("category")).append("x").append(row.getIntValue("count")).append(" ");
            }
            builder.append("\n");
        }
        builder.append("连续风险天数：").append(consecutiveDays(riskDates)).append("\n");
        return builder.toString();
    }

    private int consecutiveDays(Set<String> dates) {
        int count = 0;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd",Locale.CHINA);
        java.util.Calendar calendar = java.util.Calendar.getInstance(Locale.CHINA);
        for (int i = 0; i < config.historyDays; i++) {
            String date = format.format(calendar.getTime());
            if (!dates.contains(date)) break;
            count++;
            calendar.add(java.util.Calendar.DAY_OF_MONTH,-1);
        }
        return count;
    }

    private void recordEvent(GroupMessageEvent event,int score,String categories,String reason,String evidence,String action,boolean safety) {
        storage().insert("INSERT INTO `"+EVENT_TABLE+"` (`groupID`,`userID`,`messageID`,`messageTime`,`riskScore`,`categories`,`reason`,`evidence`,`action`,`safety`,`updateTime`) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                event.getGroupID(),event.getUserID(),event.getMessageID(),System.currentTimeMillis(),
                score,categories,reason,evidence,action,safety ? 1 : 0,System.currentTimeMillis());
    }

    private void recordDaily(long groupID,long userID,String categories,int score) {
        String date = new SimpleDateFormat("yyyy-MM-dd",Locale.CHINA).format(new Date());
        if (categories == null || categories.trim().isEmpty()) return;
        for (String category : categories.split(",")) {
            if (category.trim().isEmpty()) continue;
            JSONObject row = storage().queryOne(
                    "SELECT `ID` FROM `"+DAILY_TABLE+"` WHERE `statDate`=? AND `groupID`=? AND `userID`=? AND `category`=?",
                    date,groupID,userID,category.trim());
            if (row == null) {
                storage().insert("INSERT INTO `"+DAILY_TABLE+"` (`statDate`,`groupID`,`userID`,`category`,`count`,`maxScore`,`lastTime`) VALUES (?,?,?,?,?,?,?)",
                        date,groupID,userID,category.trim(),1,score,System.currentTimeMillis());
            } else {
                storage().update("UPDATE `"+DAILY_TABLE+"` SET `count`=`count`+1,`maxScore`=MAX(`maxScore`,?),`lastTime`=? WHERE `statDate`=? AND `groupID`=? AND `userID`=? AND `category`=?",
                        score,System.currentTimeMillis(),date,groupID,userID,category.trim());
            }
        }
    }

    private void alert(long groupID,long userID,int score,String categories,String reason,String evidence,boolean safety) {
        String text = "[AI 风控告警]\n"
                +"群："+groupID+"\n"
                +"用户："+userID+"\n"
                +"风险分："+score+"\n"
                +"分类："+safe(categories)+"\n"
                +(safety ? "安全分支：需要优先关怀和人工核实\n" : "")
                +"原因："+safe(reason)+"\n"
                +"证据："+safe(evidence);
        OneBotClient client = plugin.getServer().getOneBotClient();
        if (client == null) return;
        if (config.alertCurrentGroup) {
            client.sendGroupMessage(groupID,MessageUtil.message(MessageUtil.text(text)));
        }
        if (config.alertAdminPrivate) {
            List<Long> admins = plugin.getServer().getAdminList();
            if (admins != null) {
                for (Long admin : admins) {
                    if (admin != null && admin > 0) {
                        client.sendPrivateMessage(admin,MessageUtil.message(MessageUtil.text(text)));
                    }
                }
            }
        }
    }

    private boolean checkCooldown(long groupID,long userID) {
        String key = groupID+"|"+userID;
        long now = System.currentTimeMillis();
        Long last = lastCheckMap.get(key);
        if (last != null && now - last < config.checkCooldownSecond * 1000L) return false;
        long minute = now / 60000L;
        long[] rate = rateMap.get(key);
        if (rate == null || rate[0] != minute) {
            rate = new long[]{minute,1};
            rateMap.put(key,rate);
        } else {
            if (rate[1] >= config.maxAiChecksPerMinute) return false;
            rate[1]++;
        }
        lastCheckMap.put(key,now);
        return true;
    }

    private JSONObject parseJson(String content) {
        if (content == null) return null;
        String text = content.trim();
        if (text.startsWith("```")) {
            text = text.replace("```json","").replace("```","").trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        try {
            return JSONObject.parseObject(text.substring(start,end + 1));
        } catch (Exception e) {
            return null;
        }
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
            else if ("reply".equals(type)) builder.append("[回复]");
        }
        return builder.toString().trim();
    }

    private boolean hasSafety(List<GuardMatch> matches) {
        for (GuardMatch match : matches) if (match.safety) return true;
        return false;
    }

    private int maxScore(List<GuardMatch> matches) {
        int score = 0;
        for (GuardMatch match : matches) score = Math.max(score,match.risk);
        return score;
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

    private String join(Set<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) builder.append(",");
            builder.append(value);
        }
        return builder.toString();
    }

    private String joinArray(JSONArray array) {
        if (array == null) return "";
        StringBuilder builder = new StringBuilder();
        for (Object value : array) {
            if (value == null) continue;
            if (builder.length() > 0) builder.append("；");
            builder.append(String.valueOf(value));
        }
        return builder.toString();
    }

    private String joinStrings(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null || value.isEmpty()) continue;
            if (builder.length() > 0) builder.append("；");
            builder.append(value);
        }
        return builder.toString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
