package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Plugin;
import org.moboxlab.moboxbot.API.Storage.StorageService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 群内表达候选
 *
 * 从群聊里提取重复出现的新短语，先进入 pending 待审；管理员 promote 后才注入提示词。
 * 这样可以学习群内梗和表达方式，又不会把广告词、骂人话直接学进角色。
 */
public class RoleplayExpressionService {
    static final String TABLE = "plugin_mbb_roleplay_expression";
    private static final Pattern TOKEN = Pattern.compile("[\\p{IsHan}a-zA-Z0-9_]{2,16}");
    private static final Pattern CQ_AT = Pattern.compile("\\[CQ:at,[^]]*]");
    private static final Pattern AT_NUMBER = Pattern.compile("@\\s*\\d{4,}");
    private static final Pattern LONG_DIGITS = Pattern.compile("\\d{4,}");
    private static final Set<String> STOP = new HashSet<>(Arrays.asList(
            "这个","那个","什么","怎么","为什么","可以","应该","不是","没有","就是","然后","因为","所以",
            "真的","感觉","有点","一个","现在","今天","明天","昨天","我们","你们","他们","自己","哈哈",
            "哈哈哈","哈哈哈哈","哈哈哈哈哈","谢谢","好的","知道","不知道","可能","应该","这样","那样"));

    private static class Candidate {
        private int count;
        private final Set<Long> users = new HashSet<>();
        private String sample = "";
        private boolean saved;
    }

    private final Plugin plugin;
    private volatile RoleplayConfig config;
    private final RoleplayExpressionVectorService vectorService;
    private final Map<String,Candidate> candidates = new ConcurrentHashMap<>();
    private final Map<Long,List<JSONObject>> approved = new ConcurrentHashMap<>();

    public RoleplayExpressionService(Plugin plugin,RoleplayConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.vectorService = new RoleplayExpressionVectorService(plugin,config);
    }

    public void init() {
        storage().update("CREATE TABLE IF NOT EXISTS `"+TABLE+"` ("
                + "`ID` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`groupID` INTEGER NOT NULL DEFAULT 0,"
                + "`content` TEXT NOT NULL DEFAULT '',"
                + "`count` INTEGER NOT NULL DEFAULT 0,"
                + "`userCount` INTEGER NOT NULL DEFAULT 0,"
                + "`sample` TEXT NOT NULL DEFAULT '',"
                + "`status` TEXT NOT NULL DEFAULT 'pending',"
                + "`meaning` TEXT NOT NULL DEFAULT '',"
                + "`quality` REAL NOT NULL DEFAULT 0,"
                + "`reviewReason` TEXT NOT NULL DEFAULT '',"
                + "`reviewTime` INTEGER NOT NULL DEFAULT 0,"
                + "`embedding` TEXT NOT NULL DEFAULT '',"
                + "`embeddingModel` TEXT NOT NULL DEFAULT '',"
                + "`embeddingTime` INTEGER NOT NULL DEFAULT 0,"
                + "`createTime` INTEGER NOT NULL DEFAULT 0,"
                + "`updateTime` INTEGER NOT NULL DEFAULT 0,"
                + "UNIQUE(`groupID`,`content`)"
                + ")");
        storage().update("CREATE INDEX IF NOT EXISTS `idx_plugin_mbb_roleplay_expression_status` "
                + "ON `"+TABLE+"` (`groupID`,`status`)");
        ensureColumn("meaning","TEXT NOT NULL DEFAULT ''");
        ensureColumn("quality","REAL NOT NULL DEFAULT 0");
        ensureColumn("reviewReason","TEXT NOT NULL DEFAULT ''");
        ensureColumn("reviewTime","INTEGER NOT NULL DEFAULT 0");
        ensureColumn("embedding","TEXT NOT NULL DEFAULT ''");
        ensureColumn("embeddingModel","TEXT NOT NULL DEFAULT ''");
        ensureColumn("embeddingTime","INTEGER NOT NULL DEFAULT 0");
        cleanupNumericCandidates();
        reloadApproved();
        scheduleMissingEmbeddings();
    }

    public void reload(RoleplayConfig config) {
        this.config = config;
        vectorService.reload(config);
        scheduleMissingEmbeddings();
    }

    public void observe(long groupID,long userID,String content) {
        if (!config.expressionLearnEnable || groupID <= 0 || userID <= 0) return;
        List<String> phrases = extract(content);
        if (phrases.isEmpty()) return;
        for (String phrase : phrases) {
            String key = groupID+"|"+phrase;
            Candidate candidate = candidates.get(key);
            if (candidate == null) {
                candidate = new Candidate();
                Candidate previous = candidates.putIfAbsent(key,candidate);
                if (previous != null) candidate = previous;
            }
            synchronized (candidate) {
                candidate.count++;
                candidate.users.add(userID);
                if (candidate.sample.isEmpty()) candidate.sample = shortText(content,80);
                if (!candidate.saved && candidate.count >= config.expressionMinCount
                        && candidate.users.size() >= config.expressionMinUsers) {
                    saveCandidate(groupID,phrase,candidate);
                    candidate.saved = true;
                }
            }
        }
        trimCandidates();
    }

    public JSONArray list(long groupID,String status,int limit) {
        JSONArray result = new JSONArray();
        if (groupID <= 0) return result;
        if (limit < 1) limit = 1;
        if (limit > 100) limit = 100;
        String value = status == null || status.trim().isEmpty() ? "pending" : status.trim().toLowerCase();
        List<JSONObject> rows = storage().query("SELECT * FROM `"+TABLE
                +"` WHERE `groupID`=? AND `status`=? ORDER BY `count` DESC,`ID` DESC LIMIT "+limit,
                groupID,value);
        if (rows == null) return result;
        for (JSONObject row : rows) {
            JSONObject item = new JSONObject(true);
            item.put("id",row.getLongValue("ID"));
            item.put("content",row.getString("content"));
            item.put("count",row.getIntValue("count"));
            item.put("userCount",row.getIntValue("userCount"));
            item.put("sample",row.getString("sample"));
            item.put("status",row.getString("status"));
            item.put("meaning",row.getString("meaning"));
            item.put("quality",row.getDoubleValue("quality"));
            item.put("reviewReason",row.getString("reviewReason"));
            item.put("reviewTime",row.getLongValue("reviewTime"));
            item.put("updateTime",row.getLongValue("updateTime"));
            result.add(item);
        }
        return result;
    }

    public boolean setStatus(long id,String status) {
        if (id <= 0) return false;
        String value = status == null ? "" : status.trim().toLowerCase();
        if (!"pending".equals(value) && !"approved".equals(value) && !"rejected".equals(value)) return false;
        int changed = storage().update("UPDATE `"+TABLE+"` SET `status`=?,`updateTime`=? WHERE `ID`=?",
                value,System.currentTimeMillis(),id);
        if (changed > 0) {
            reloadApproved();
            if ("approved".equals(value)) scheduleMissingEmbeddings();
        }
        return changed > 0;
    }

    public int clear(long groupID) {
        if (groupID <= 0) return 0;
        int changed = storage().update("DELETE FROM `"+TABLE+"` WHERE `groupID`=?",groupID);
        reloadApproved();
        return changed;
    }

    public String promptText(long groupID) {
        return promptText(groupID,"");
    }

    public String promptText(long groupID,String query) {
        if (!config.expressionLearnEnable || groupID <= 0) return "";
        List<JSONObject> list = approved.get(groupID);
        if (list == null || list.isEmpty()) return "";
        list = vectorService.rank(list,query);
        StringBuilder builder = new StringBuilder("群内最近常用表达（可以自然使用，不要刻意堆砌）：\n");
        int used = 0;
        for (JSONObject item : list) {
            String line = "- "+safe(item.getString("content"))+"\n";
            if (used + line.length() > config.expressionInjectMaxChars) break;
            builder.append(line);
            used += line.length();
        }
        return used == 0 ? "" : builder.toString();
    }

    public int approvedCount(long groupID) {
        List<JSONObject> list = approved.get(groupID);
        return list == null ? 0 : list.size();
    }

    public JSONArray pendingForReview(long groupID,int limit) {
        JSONArray result = new JSONArray();
        if (groupID <= 0) return result;
        if (limit < 1) limit = 1;
        if (limit > 100) limit = 100;
        List<JSONObject> rows = storage().query(
                "SELECT `ID`,`content`,`count`,`userCount`,`sample`,`meaning`,`quality` "
                        + "FROM `"+TABLE+"` WHERE `groupID`=? AND `status`='pending' "
                        + "ORDER BY `count` DESC,`ID` DESC LIMIT "+limit,groupID);
        if (rows == null) return result;
        for (JSONObject row : rows) {
            JSONObject item = new JSONObject(true);
            item.put("id",row.getLongValue("ID"));
            item.put("content",row.getString("content"));
            item.put("count",row.getIntValue("count"));
            item.put("userCount",row.getIntValue("userCount"));
            item.put("sample",row.getString("sample"));
            item.put("meaning",row.getString("meaning"));
            item.put("quality",row.getDoubleValue("quality"));
            result.add(item);
        }
        return result;
    }

    public boolean saveReview(long id,String meaning,double quality,String reason,String status) {
        String value = status == null ? "" : status.trim().toLowerCase();
        if (!"pending".equals(value) && !"approved".equals(value) && !"rejected".equals(value)) {
            return false;
        }
        double score = Math.max(0,Math.min(1,quality));
        int changed = storage().update("UPDATE `"+TABLE+"` SET "
                        + "`meaning`=?,`quality`=?,`reviewReason`=?,`reviewTime`=?,`status`=?,"
                        + "`updateTime`=? WHERE `ID`=?",
                safe(meaning).trim(),score,safe(reason).trim(),System.currentTimeMillis(),
                value,System.currentTimeMillis(),id);
        if (changed > 0) {
            reloadApproved();
            if ("approved".equals(value)) scheduleMissingEmbeddings();
        }
        return changed > 0;
    }

    public boolean ensureEmbedding(long id) {
        if (id <= 0 || !config.expressionVectorEnable) return false;
        JSONObject row = storage().queryOne(
                "SELECT `content` FROM `"+TABLE+"` WHERE `ID`=? AND `status`='approved'",id);
        if (row == null) return false;
        RoleplayExpressionVectorService.EmbeddingResult embedding =
                vectorService.embed(row.getString("content"));
        if (!embedding.success) {
            plugin.getLogger().sendWarn("[表达] #"+id+" 向量生成失败："+embedding.message);
            return false;
        }
        storage().update("UPDATE `"+TABLE+"` SET `embedding`=?,`embeddingModel`=?,"
                        + "`embeddingTime`=?,`updateTime`=? WHERE `ID`=?",
                embedding.embedding.toJSONString(),embedding.model,System.currentTimeMillis(),
                System.currentTimeMillis(),id);
        return true;
    }

    void reloadApproved() {
        approved.clear();
        List<JSONObject> rows = storage().query("SELECT `ID`,`groupID`,`content`,`meaning`,"
                + "`count`,`embedding`,`embeddingModel` FROM `"+TABLE
                +"` WHERE `status`='approved' ORDER BY `count` DESC,`ID` DESC");
        if (rows == null) return;
        for (JSONObject row : rows) {
            long groupID = row.getLongValue("groupID");
            if (groupID <= 0) continue;
            List<JSONObject> list = approved.get(groupID);
            if (list == null) {
                list = new ArrayList<>();
                approved.put(groupID,list);
            }
            if (list.size() >= config.expressionMaxCandidates) continue;
            JSONObject item = new JSONObject(true);
            item.put("id",row.getLongValue("ID"));
            item.put("content",row.getString("content"));
            item.put("meaning",row.getString("meaning"));
            item.put("count",row.getIntValue("count"));
            item.put("embeddingModel",row.getString("embeddingModel"));
            item.put("embedding",vectorService.parseVector(row.getString("embedding")));
            list.add(item);
        }
    }

    private void scheduleMissingEmbeddings() {
        if (!config.expressionVectorEnable) return;
        plugin.getServer().getPluginManager().runTaskLater(plugin,this::ensureMissingEmbeddings,5);
    }

    private void ensureMissingEmbeddings() {
        if (!config.expressionVectorEnable) return;
        List<JSONObject> rows = storage().query(
                "SELECT `ID` FROM `"+TABLE+"` WHERE `status`='approved' "
                        + "AND (`embedding`='' OR `embeddingModel`='') ORDER BY `ID` ASC LIMIT 30");
        if (rows == null || rows.isEmpty()) return;
        int saved = 0;
        for (JSONObject row : rows) {
            if (ensureEmbedding(row.getLongValue("ID"))) {
                saved++;
            } else {
                break;
            }
        }
        if (saved > 0) {
            reloadApproved();
            plugin.getLogger().sendInfo("[表达] 已生成 "+saved+" 条表达候选向量");
        }
    }

    private void saveCandidate(long groupID,String phrase,Candidate candidate) {
        long now = System.currentTimeMillis();
        storage().insert("INSERT OR IGNORE INTO `"+TABLE+"` "
                        + "(`groupID`,`content`,`count`,`userCount`,`sample`,`status`,`createTime`,`updateTime`) "
                        + "VALUES (?,?,?,?,?,'pending',?,?)",
                groupID,phrase,candidate.count,candidate.users.size(),candidate.sample,now,now);
        plugin.getLogger().sendInfo("[表达] 群"+groupID+" 发现候选："+phrase
                +" 次数="+candidate.count+" 人数="+candidate.users.size());
    }

    private List<String> extract(String content) {
        List<String> result = new ArrayList<>();
        if (content == null) return result;
        String text = content.trim();
        if (text.isEmpty() || text.contains("[图片") || text.contains("[表情") || text.contains("[语音")) return result;
        text = CQ_AT.matcher(text).replaceAll(" ");
        text = AT_NUMBER.matcher(text).replaceAll(" ");
        text = LONG_DIGITS.matcher(text).replaceAll(" ");
        Matcher matcher = TOKEN.matcher(text);
        while (matcher.find() && result.size() < 12) {
            String token = matcher.group();
            if (token.length() < 2 || STOP.contains(token)) continue;
            if (token.matches("\\d+")) continue;
            if (!result.contains(token)) result.add(token);
        }
        return result;
    }

    private void cleanupNumericCandidates() {
        List<JSONObject> rows = storage().query("SELECT `ID`,`content` FROM `"+TABLE+"`");
        if (rows == null) return;
        int removed = 0;
        for (JSONObject row : rows) {
            String content = safe(row.getString("content")).trim();
            if (!content.matches("\\d+")) continue;
            removed += storage().update("DELETE FROM `"+TABLE+"` WHERE `ID`=?",row.getLongValue("ID"));
        }
        if (removed > 0) {
            plugin.getLogger().sendInfo("[表达] 清理纯数字候选 "+removed+" 条");
        }
    }

    private void trimCandidates() {
        if (candidates.size() <= 10000) return;
        List<String> remove = new ArrayList<>();
        for (Map.Entry<String,Candidate> entry : candidates.entrySet()) {
            Candidate candidate = entry.getValue();
            synchronized (candidate) {
                if (!candidate.saved || candidate.count < config.expressionMinCount) remove.add(entry.getKey());
            }
            if (remove.size() >= 2000) break;
        }
        for (String key : remove) candidates.remove(key);
    }

    private String shortText(String text,int maxChars) {
        String value = safe(text).replace("\n"," ").trim();
        return value.length() <= maxChars ? value : value.substring(0,maxChars);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void ensureColumn(String column,String definition) {
        List<JSONObject> columns = storage().query("PRAGMA table_info(`"+TABLE+"`)");
        if (columns != null) {
            for (JSONObject row : columns) {
                if (column.equalsIgnoreCase(row.getString("name"))) return;
            }
        }
        storage().update("ALTER TABLE `"+TABLE+"` ADD COLUMN `"+column+"` "+definition);
    }

    private StorageService storage() {
        return plugin.getServer().getStorage();
    }
}
