package org.moboxlab.mbb.ai;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Plugin;
import org.moboxlab.moboxbot.API.Storage.StorageService;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AI 使用统计
 */
public class AIStatsService {
    private static final String TABLE = "plugin_mbb_ai_usage";
    private final Plugin plugin;
    private final Object lock = new Object();

    public AIStatsService(Plugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        storage().update("CREATE TABLE IF NOT EXISTS `"+TABLE+"` ("
                + "`ID` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`statDate` TEXT NOT NULL,"
                + "`profile` TEXT NOT NULL,"
                + "`model` TEXT NOT NULL,"
                + "`action` TEXT NOT NULL,"
                + "`requests` INTEGER NOT NULL DEFAULT 0,"
                + "`successes` INTEGER NOT NULL DEFAULT 0,"
                + "`failures` INTEGER NOT NULL DEFAULT 0,"
                + "`promptTokens` INTEGER NOT NULL DEFAULT 0,"
                + "`completionTokens` INTEGER NOT NULL DEFAULT 0,"
                + "`totalTokens` INTEGER NOT NULL DEFAULT 0,"
                + "`totalLatencyMs` INTEGER NOT NULL DEFAULT 0,"
                + "`cachedHits` INTEGER NOT NULL DEFAULT 0,"
                + "`cachedPromptTokens` INTEGER NOT NULL DEFAULT 0,"
                + "`lastRequestTime` INTEGER NOT NULL DEFAULT 0,"
                + "`updateTime` INTEGER NOT NULL DEFAULT 0,"
                + "UNIQUE(`statDate`,`profile`,`model`,`action`)"
                + ")");
        ensureColumn("cachedPromptTokens","INTEGER NOT NULL DEFAULT 0");
    }

    /**
     * 老库补列，避免升级后统计写入失败
     */
    private void ensureColumn(String column,String definition) {
        try {
            List<JSONObject> columns = storage().query("PRAGMA table_info(`"+TABLE+"`)");
            if (columns != null) {
                for (JSONObject row : columns) {
                    if (column.equalsIgnoreCase(row.getString("name"))) return;
                }
            }
            storage().update("ALTER TABLE `"+TABLE+"` ADD COLUMN `"+column+"` "+definition);
        } catch (Exception e) {
            plugin.getLogger().sendWarn("补充 AI 统计表字段失败："+column+" "+e.getMessage());
        }
    }

    public void record(AIProfile profile,String action,boolean success,boolean cached,
                       long latencyMs,JSONObject usage) {
        if (profile == null) return;
        String date = formatDate(new Date());
        String model = profile.model == null ? "" : profile.model;
        String act = action == null ? "unknown" : action;
        long prompt = usage == null ? 0L : usage.getLongValue("promptTokens");
        long completion = usage == null ? 0L : usage.getLongValue("completionTokens");
        long total = usage == null ? 0L : usage.getLongValue("totalTokens");
        long cachedPrompt = usage == null ? 0L : usage.getLongValue("cachedPromptTokens");
        synchronized (lock) {
            JSONObject exists = storage().queryOne(
                    "SELECT `ID` FROM `"+TABLE+"` WHERE `statDate`=? AND `profile`=? AND `model`=? AND `action`=?",
                    date,profile.name,model,act);
            long now = System.currentTimeMillis();
            if (exists == null) {
                storage().insert("INSERT INTO `"+TABLE+"` (`statDate`,`profile`,`model`,`action`,`requests`,`successes`,`failures`,`promptTokens`,`completionTokens`,`totalTokens`,`totalLatencyMs`,`cachedHits`,`cachedPromptTokens`,`lastRequestTime`,`updateTime`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        date,profile.name,model,act,1,success ? 1 : 0,success ? 0 : 1,
                        prompt,completion,total,Math.max(0,latencyMs),cached ? 1 : 0,cachedPrompt,now,now);
            } else {
                storage().update("UPDATE `"+TABLE+"` SET `requests`=`requests`+1,"
                                + "`successes`=`successes`+?,`failures`=`failures`+?,"
                                + "`promptTokens`=`promptTokens`+?,`completionTokens`=`completionTokens`+?,"
                                + "`totalTokens`=`totalTokens`+?,`totalLatencyMs`=`totalLatencyMs`+?,"
                                + "`cachedHits`=`cachedHits`+?,`cachedPromptTokens`=`cachedPromptTokens`+?,"
                                + "`lastRequestTime`=?,`updateTime`=? "
                                + "WHERE `statDate`=? AND `profile`=? AND `model`=? AND `action`=?",
                        success ? 1 : 0,success ? 0 : 1,prompt,completion,total,
                        Math.max(0,latencyMs),cached ? 1 : 0,cachedPrompt,
                        now,now,date,profile.name,model,act);
            }
        }
    }

    public JSONObject summary(int days) {
        if (days < 1) days = 7;
        if (days > 30) days = 30;
        JSONObject result = new JSONObject(true);
        result.put("status",true);
        result.put("generatedAt",System.currentTimeMillis());
        result.put("total",readSummary(null));
        result.put("today",readSummary(formatDate(new Date())));
        result.put("daily",readDaily(days));
        result.put("profiles",readGroup("profile"));
        result.put("models",readGroup("model"));
        return result;
    }

    private JSONObject readSummary(String date) {
        String sql = "SELECT SUM(`requests`) AS `requests`,SUM(`successes`) AS `successes`,"
                + "SUM(`failures`) AS `failures`,SUM(`promptTokens`) AS `promptTokens`,"
                + "SUM(`completionTokens`) AS `completionTokens`,SUM(`totalTokens`) AS `totalTokens`,"
                + "SUM(`totalLatencyMs`) AS `totalLatencyMs`,SUM(`cachedHits`) AS `cachedHits`,"
                + "SUM(`cachedPromptTokens`) AS `cachedPromptTokens` "
                + "FROM `"+TABLE+"`";
        List<JSONObject> rows;
        if (date == null) {
            rows = storage().query(sql);
        } else {
            rows = storage().query(sql+" WHERE `statDate`=?",date);
        }
        JSONObject row = rows == null || rows.isEmpty() ? null : rows.get(0);
        JSONObject result = new JSONObject(true);
        result.put("requests",value(row,"requests"));
        result.put("successes",value(row,"successes"));
        result.put("failures",value(row,"failures"));
        result.put("promptTokens",value(row,"promptTokens"));
        result.put("completionTokens",value(row,"completionTokens"));
        result.put("totalTokens",value(row,"totalTokens"));
        result.put("totalLatencyMs",value(row,"totalLatencyMs"));
        result.put("cachedHits",value(row,"cachedHits"));
        result.put("cachedPromptTokens",value(row,"cachedPromptTokens"));
        result.put("successRate",rate(value(row,"successes"),value(row,"requests")));
        result.put("cacheHitRate",rate(value(row,"cachedHits"),value(row,"requests")));
        //服务端提示词缓存命中率：命中的输入 token 占全部输入 token 的比例
        result.put("promptCacheHitRate",rate(value(row,"cachedPromptTokens"),value(row,"promptTokens")));
        result.put("averageLatencyMs",value(row,"requests") <= 0 ? 0 : value(row,"totalLatencyMs") / value(row,"requests"));
        return result;
    }

    private JSONArray readDaily(int days) {
        Map<String,JSONObject> map = new LinkedHashMap<>();
        List<JSONObject> rows = storage().query(
                "SELECT `statDate`,SUM(`requests`) AS `requests`,SUM(`successes`) AS `successes`,"
                        + "SUM(`failures`) AS `failures`,SUM(`promptTokens`) AS `promptTokens`,"
                        + "SUM(`completionTokens`) AS `completionTokens`,"
                        + "SUM(`totalTokens`) AS `totalTokens`,"
                        + "SUM(`cachedPromptTokens`) AS `cachedPromptTokens` "
                        + "FROM `"+TABLE+"` GROUP BY `statDate` ORDER BY `statDate` ASC");
        if (rows != null) {
            for (JSONObject row : rows) {
                String date = row.getString("statDate");
                if (date != null) map.put(date,row);
            }
        }
        JSONArray result = new JSONArray();
        Calendar calendar = Calendar.getInstance(Locale.CHINA);
        calendar.add(Calendar.DAY_OF_MONTH,-(days - 1));
        for (int i = 0; i < days; i++) {
            String date = formatDate(calendar.getTime());
            JSONObject row = map.get(date);
            JSONObject item = new JSONObject(true);
            item.put("date",date);
            item.put("requests",value(row,"requests"));
            item.put("successes",value(row,"successes"));
            item.put("failures",value(row,"failures"));
            item.put("promptTokens",value(row,"promptTokens"));
            item.put("completionTokens",value(row,"completionTokens"));
            item.put("totalTokens",value(row,"totalTokens"));
            item.put("cachedPromptTokens",value(row,"cachedPromptTokens"));
            result.add(item);
            calendar.add(Calendar.DAY_OF_MONTH,1);
        }
        return result;
    }

    private JSONArray readGroup(String column) {
        JSONArray result = new JSONArray();
        List<JSONObject> rows = storage().query(
                "SELECT `"+column+"`,SUM(`requests`) AS `requests`,SUM(`successes`) AS `successes`,"
                        + "SUM(`failures`) AS `failures`,SUM(`totalTokens`) AS `totalTokens`,"
                        + "SUM(`totalLatencyMs`) AS `totalLatencyMs` FROM `"+TABLE+"` "
                        + "GROUP BY `"+column+"` ORDER BY `requests` DESC");
        if (rows == null) return result;
        for (JSONObject row : rows) {
            JSONObject item = new JSONObject(true);
            item.put("name",row.getString(column));
            item.put("requests",value(row,"requests"));
            item.put("successes",value(row,"successes"));
            item.put("failures",value(row,"failures"));
            item.put("totalTokens",value(row,"totalTokens"));
            item.put("totalLatencyMs",value(row,"totalLatencyMs"));
            item.put("successRate",rate(value(row,"successes"),value(row,"requests")));
            result.add(item);
        }
        return result;
    }

    private StorageService storage() {
        return plugin.getServer().getStorage();
    }

    private long value(JSONObject row,String key) {
        return row == null ? 0L : row.getLongValue(key);
    }

    private double rate(long value,long total) {
        return total <= 0 ? 0.0 : value * 100.0 / total;
    }

    private String formatDate(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd",Locale.CHINA).format(date);
    }
}
