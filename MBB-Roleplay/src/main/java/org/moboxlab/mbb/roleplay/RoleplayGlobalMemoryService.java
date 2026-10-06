package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Plugin;
import org.moboxlab.moboxbot.API.PluginService;
import org.moboxlab.moboxbot.API.Storage.StorageService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 所有群共享的角色永久记忆
 */
public class RoleplayGlobalMemoryService {
    private static final String TABLE = "plugin_mbb_roleplay_global_memory";

    private final Plugin plugin;
    private final RoleplayService service;
    private volatile RoleplayConfig config;
    private volatile boolean merging = false;

    public RoleplayGlobalMemoryService(Plugin plugin,RoleplayConfig config,RoleplayService service) {
        this.plugin = plugin;
        this.config = config;
        this.service = service;
    }

    public void init() {
        storage().update("CREATE TABLE IF NOT EXISTS `"+TABLE+"` ("
                + "`ID` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`memoryType` TEXT NOT NULL DEFAULT '',"
                + "`content` TEXT NOT NULL DEFAULT '',"
                + "`importance` INTEGER NOT NULL DEFAULT 1,"
                + "`sourceGroupID` INTEGER NOT NULL DEFAULT 0,"
                + "`sourceUserID` INTEGER NOT NULL DEFAULT 0,"
                + "`updateTime` INTEGER NOT NULL DEFAULT 0"
                + ")");
        storage().update("CREATE INDEX IF NOT EXISTS `idx_plugin_mbb_roleplay_global_memory_time` "
                + "ON `"+TABLE+"` (`importance`,`updateTime`)");
    }

    public void reload(RoleplayConfig config) {
        this.config = config;
    }

    public boolean isLearnGroup(long groupID) {
        if (!config.globalMemoryEnable) return false;
        String value = config.globalMemoryLearnGroups;
        if (value == null || value.trim().isEmpty()) return false;
        for (String item : value.split(",")) {
            try {
                if (Long.parseLong(item.trim()) == groupID) return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    public boolean save(String type,String content,int importance,long sourceGroupID,long sourceUserID) {
        if (!config.globalMemoryEnable) return false;
        String memoryType = safe(type).trim();
        String value = safe(content).trim();
        if (memoryType.isEmpty()) memoryType = "note";
        if (value.isEmpty()) return false;
        if (importance < 1) importance = 1;
        if (importance > 5) importance = 5;
        JSONObject exists = storage().queryOne(
                "SELECT `ID` FROM `"+TABLE+"` WHERE `memoryType`=? AND `content`=?",
                memoryType,value);
        long now = System.currentTimeMillis();
        if (exists != null) {
            storage().update("UPDATE `"+TABLE+"` SET `importance`=MAX(`importance`,?),"
                            + "`sourceGroupID`=?,`sourceUserID`=?,`updateTime`=? WHERE `ID`=?",
                    importance,sourceGroupID,sourceUserID,now,exists.getLongValue("ID"));
            return true;
        }
        storage().insert("INSERT INTO `"+TABLE+"` "
                        + "(`memoryType`,`content`,`importance`,`sourceGroupID`,`sourceUserID`,`updateTime`) "
                        + "VALUES (?,?,?,?,?,?)",
                memoryType,value,importance,sourceGroupID,sourceUserID,now);
        plugin.getLogger().sendInfo("[永久记忆] 新增 ["+memoryType+"] "+shortText(value,120)
                +" 来源群"+sourceGroupID);
        mergeIfNeeded();
        return true;
    }

    public void mergeNow() {
        mergeNow(false);
    }

    public void mergeNow(boolean force) {
        if (merging) return;
        merging = true;
        try {
            long snapshotMaxId = maxId();
            if (snapshotMaxId <= 0) return;
            JSONArray snapshot = exportUpTo(snapshotMaxId);
            if (snapshot.size() < 2) return;
            PluginService ai = plugin.getServer().getPluginManager().getService("MBB-AI");
            if (ai == null) {
                plugin.getLogger().sendWarn("[永久记忆] 合并跳过：MBB-AI 未启用");
                return;
            }
            service.backupAllMemories("global-memory-merge");
            List<JSONObject> current = new ArrayList<>();
            for (Object object : snapshot) {
                if (!(object instanceof JSONObject)) continue;
                JSONObject item = (JSONObject) object;
                JSONObject normalized = new JSONObject(true);
                normalized.put("type",safe(item.getString("memoryType")));
                normalized.put("content",safe(item.getString("content")));
                normalized.put("importance",item.getIntValue("importance"));
                normalized.put("sourceGroupID",item.getLongValue("sourceGroupID"));
                normalized.put("sourceUserID",item.getLongValue("sourceUserID"));
                normalized.put("updateTime",item.getLongValue("updateTime"));
                current.add(normalized);
            }
            int original = current.size();
            int target = config.globalMemoryMaxItems;
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
                plugin.getLogger().sendInfo("[永久记忆] 合并第 "+round+"/"+maxRounds
                        +" 轮开始：输入 "+current.size()+" 条，共 "+batches.size()+" 批");
                List<JSONObject> mergedAll = new ArrayList<>();
                for (int i = 0; i < batches.size(); i++) {
                    List<JSONObject> batch = batches.get(i);
                    plugin.getLogger().sendInfo("[永久记忆] 合并第 "+round+" 轮 批次 "+(i+1)
                            +"/"+batches.size()+" 开始：输入 "+batch.size()+" 条");
                    List<JSONObject> merged = mergeGlobalBatch(ai,batch,round,i+1,batches.size());
                    if (merged == null) {
                        plugin.getLogger().sendWarn("[永久记忆] 合并第 "+round+" 轮 批次 "+(i+1)
                                +" 失败，放弃本次合并，原记忆保持不变");
                        return;
                    }
                    mergedAll.addAll(merged);
                    plugin.getLogger().sendInfo("[永久记忆] 合并第 "+round+" 轮 批次 "+(i+1)
                            +"/"+batches.size()+" 完成："+batch.size()+" -> "+merged.size()+" 条");
                }
                current = MemoryBatchSorter.sortForMerge(mergedAll);
                plugin.getLogger().sendInfo("[永久记忆] 合并第 "+round+" 轮完成：结果 "
                        +current.size()+" 条");
            }
            if (executedRounds == 0) {
                plugin.getLogger().sendInfo("[永久记忆] 未超过上限，跳过合并");
                return;
            }
            if (!isMergeResultSafe(original,current.size())) {
                plugin.getLogger().sendWarn("[永久记忆] 合并结果异常：原 "+original
                        +" 条，合并后仅 "+current.size()+" 条，放弃本次合并");
                return;
            }
            int newRows = Math.max(0,count() - original);
            JSONArray mergedArray = new JSONArray();
            mergedArray.addAll(current);
            deleteUpTo(snapshotMaxId);
            int saved = insertAll(mergedArray);
            plugin.getLogger().sendInfo("[永久记忆] 整理合并完成：原 "+original
                    +" 条，合并后 "+saved+" 条，合并期间新增保留 "+newRows+" 条");
        } finally {
            merging = false;
        }
    }

    private List<JSONObject> mergeGlobalBatch(PluginService ai,List<JSONObject> batch,
                                              int round,int batchNo,int batchCount) {
        JSONArray messages = new JSONArray();
        messages.add(message("system","你是永久记忆整理器。只处理当前这一批记忆，"
                +"当前角色："+service.getRoleName()+"。整理时必须基于该角色的视角，"
                +"区分角色自己的说话方式、习惯、知识，以及群友提供的通用信息。"
                +"请合并重复或高度相似的内容，保留所有有价值的信息，不要因为压缩而丢失关键内容。"
                +"只输出 JSON，不要 Markdown：{\"memories\":[{\"type\":\"speech_style|tone|habit|"
                +"knowledge|meme|note\",\"content\":\"整理后的内容\",\"importance\":1}]}。"));
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
        params.put("sessionId","roleplay-global-memory-merge-r"+round+"-b"+batchNo);
        params.put("messages",messages);
        long startTime = System.currentTimeMillis();
        JSONObject result = ai.call("chat",params);
        RoleplayAiLog.log(plugin.getLogger(),"永久记忆合并 第"+round+"轮 批次"+batchNo+"/"+batchCount,
                0L,result,System.currentTimeMillis() - startTime);
        if (result == null || !result.getBooleanValue("status")) {
            plugin.getLogger().sendWarn("[永久记忆] 合并批次失败："
                    +safe(result == null ? "" : result.getString("message")));
            return null;
        }
        if ("length".equalsIgnoreCase(safe(result.getString("finishReason")))) {
            plugin.getLogger().sendWarn("[永久记忆] 合并批次输出被截断");
            return null;
        }
        JSONObject parsed = parseJson(result.getString("content"));
        if (parsed == null) parsed = parseJson(result.getString("reasoningContent"));
        JSONArray merged = parsed == null ? null : parsed.getJSONArray("memories");
        if (merged == null || merged.isEmpty()) {
            plugin.getLogger().sendWarn("[永久记忆] 合并批次没有返回有效 memories");
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
            if (type.isEmpty()) type = "note";
            int importance = item.getIntValue("importance");
            if (importance < 1) importance = 1;
            if (importance > 5) importance = 5;
            JSONObject memory = new JSONObject(true);
            memory.put("type",type);
            memory.put("content",content);
            memory.put("importance",importance);
            memory.put("sourceGroupID",item.getLongValue("sourceGroupID"));
            memory.put("sourceUserID",item.getLongValue("sourceUserID"));
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

    private void mergeIfNeeded() {
        if (count() <= config.globalMemoryMaxItems) return;
        plugin.getServer().getPluginManager().runTask(plugin,this::mergeNow);
    }

    private JSONObject parseJson(String content) {
        if (content == null) return null;
        String text = content.trim();
        text = text.replaceAll("(?s)```[a-zA-Z0-9_-]*\\s*","").replace("```","").trim();
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

    public JSONArray list(int limit) {
        JSONArray result = new JSONArray();
        if (limit < 1) limit = config.globalMemoryMaxItems;
        List<JSONObject> rows = storage().query(
                "SELECT `ID`,`memoryType`,`content`,`importance`,`sourceGroupID`,`updateTime` "
                        + "FROM `"+TABLE+"` ORDER BY `importance` DESC,`updateTime` DESC LIMIT ?",
                Math.min(limit,config.globalMemoryMaxItems));
        if (rows == null) return result;
        for (JSONObject row : rows) {
            JSONObject item = new JSONObject(true);
            item.put("id",row.getLongValue("ID"));
            item.put("type",row.getString("memoryType"));
            item.put("content",row.getString("content"));
            item.put("importance",row.getIntValue("importance"));
            item.put("sourceGroupID",row.getLongValue("sourceGroupID"));
            item.put("updateTime",row.getLongValue("updateTime"));
            result.add(item);
        }
        return result;
    }

    public int count() {
        List<JSONObject> rows = storage().query("SELECT COUNT(*) AS `count` FROM `"+TABLE+"`");
        return rows == null || rows.isEmpty() ? 0 : rows.get(0).getIntValue("count");
    }

    public String promptText() {
        JSONArray memories = list(config.globalMemoryInjectItems);
        if (memories.isEmpty()) return "暂无全局永久记忆。";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < memories.size(); i++) {
            JSONObject item = memories.getJSONObject(i);
            builder.append("- [").append(safe(item.getString("type"))).append("] ")
                    .append(safe(item.getString("content"))).append("\n");
        }
        return builder.toString();
    }

    public JSONArray exportAll() {
        JSONArray result = new JSONArray();
        List<JSONObject> rows = storage().query(
                "SELECT `ID`,`memoryType`,`content`,`importance`,`sourceGroupID`,`sourceUserID`,`updateTime` "
                        + "FROM `"+TABLE+"` ORDER BY `ID` ASC");
        if (rows == null) return result;
        for (JSONObject row : rows) {
            JSONObject item = new JSONObject(true);
            item.put("id",row.getLongValue("ID"));
            item.put("memoryType",row.getString("memoryType"));
            item.put("content",row.getString("content"));
            item.put("importance",row.getIntValue("importance"));
            item.put("sourceGroupID",row.getLongValue("sourceGroupID"));
            item.put("sourceUserID",row.getLongValue("sourceUserID"));
            item.put("updateTime",row.getLongValue("updateTime"));
            result.add(item);
        }
        return result;
    }

    public boolean replaceAll(JSONArray memories) {
        storage().update("DELETE FROM `"+TABLE+"`");
        insertAll(memories);
        return true;
    }

    public long maxId() {
        List<JSONObject> rows = storage().query("SELECT MAX(`ID`) AS `maxID` FROM `"+TABLE+"`");
        return rows == null || rows.isEmpty() ? 0L : rows.get(0).getLongValue("maxID");
    }

    public JSONArray exportUpTo(long maxId) {
        JSONArray result = new JSONArray();
        List<JSONObject> rows = storage().query(
                "SELECT `ID`,`memoryType`,`content`,`importance`,`sourceGroupID`,`sourceUserID`,`updateTime` "
                        + "FROM `"+TABLE+"` WHERE `ID`<=? ORDER BY `ID` ASC",maxId);
        if (rows == null) return result;
        for (JSONObject row : rows) {
            JSONObject item = new JSONObject(true);
            item.put("id",row.getLongValue("ID"));
            item.put("memoryType",row.getString("memoryType"));
            item.put("content",row.getString("content"));
            item.put("importance",row.getIntValue("importance"));
            item.put("sourceGroupID",row.getLongValue("sourceGroupID"));
            item.put("sourceUserID",row.getLongValue("sourceUserID"));
            item.put("updateTime",row.getLongValue("updateTime"));
            result.add(item);
        }
        return result;
    }

    public void deleteUpTo(long maxId) {
        storage().update("DELETE FROM `"+TABLE+"` WHERE `ID`<=?",maxId);
    }

    public int insertAll(JSONArray memories) {
        if (memories == null) return 0;
        int saved = 0;
        long now = System.currentTimeMillis();
        for (Object object : memories) {
            if (!(object instanceof JSONObject)) continue;
            JSONObject item = (JSONObject) object;
            String type = safe(item.getString("memoryType"));
            if (type.isEmpty()) type = safe(item.getString("type"));
            String content = safe(item.getString("content")).trim();
            if (content.isEmpty()) continue;
            if (type.isEmpty()) type = "note";
            int importance = item.getIntValue("importance");
            if (importance < 1) importance = 1;
            if (importance > 5) importance = 5;
            long sourceGroupID = item.getLongValue("sourceGroupID");
            long sourceUserID = item.getLongValue("sourceUserID");
            long updateTime = item.getLongValue("updateTime");
            if (updateTime <= 0) updateTime = now;
            storage().insert("INSERT INTO `"+TABLE+"` "
                            + "(`memoryType`,`content`,`importance`,`sourceGroupID`,`sourceUserID`,`updateTime`) "
                            + "VALUES (?,?,?,?,?,?)",
                    type,content,importance,sourceGroupID,sourceUserID,updateTime);
            saved++;
        }
        return saved;
    }

    public String formatTime(long time) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.CHINA);
        return format.format(new Date(time));
    }

    private String shortText(String text,int maxChars) {
        String value = safe(text).replace("\n"," ").trim();
        return value.length() <= maxChars ? value : value.substring(0,maxChars)+"...";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private StorageService storage() {
        return plugin.getServer().getStorage();
    }
}
