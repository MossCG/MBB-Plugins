package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Plugin;
import org.moboxlab.moboxbot.API.PluginService;
import org.moboxlab.moboxbot.API.Storage.StorageService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 所有群共享的角色永久记忆
 */
public class RoleplayGlobalMemoryService {
    private static final String TABLE = "plugin_mbb_roleplay_global_memory";
    private static final String GLOBAL_STATE_TABLE = "plugin_mbb_roleplay_global_state";
    /**合并写回前的保守语义去重阈值，只处理高度相似内容*/
    private static final double DEDUPE_SIMILARITY = 0.88;

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
        storage().update("CREATE TABLE IF NOT EXISTS `"+GLOBAL_STATE_TABLE+"` ("
                + "`stateKey` TEXT PRIMARY KEY,"
                + "`stateValue` INTEGER NOT NULL DEFAULT 0,"
                + "`updateTime` INTEGER NOT NULL DEFAULT 0"
                + ")");
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
        String memoryType = normalizeType(type);
        String value = safe(content).trim();
        if (value.isEmpty()) return false;
        if (importance < 1) importance = 1;
        if (importance > 5) importance = 5;
        JSONObject exists = findDuplicate(value);
        long now = System.currentTimeMillis();
        if (exists != null) {
            String mergedType = preferType(normalizeType(exists.getString("memoryType")),memoryType);
            String mergedContent = chooseContent(exists.getString("content"),value);
            storage().update("UPDATE `"+TABLE+"` SET `memoryType`=?,`content`=?,`importance`=MAX(`importance`,?),"
                            + "`sourceGroupID`=?,`sourceUserID`=?,`updateTime`=? WHERE `ID`=?",
                    mergedType,mergedContent,importance,sourceGroupID,sourceUserID,now,
                    exists.getLongValue("ID"));
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

    public boolean isMerging() {
        return merging;
    }

    public String mergeNow(boolean force) {
        if (merging) return "永久记忆已有合并任务在执行。";
        merging = true;
        try {
            long snapshotMaxId = maxId();
            if (snapshotMaxId <= 0) return "没有可合并的永久记忆。";
            JSONArray snapshot = exportUpTo(snapshotMaxId);
            if (snapshot.size() < 2) return "永久记忆不足 2 条，无需合并。";
            PluginService ai = plugin.getServer().getPluginManager().getService("MBB-AI");
            if (ai == null) {
                plugin.getLogger().sendWarn("[永久记忆] 合并跳过：MBB-AI 未启用");
                return "永久记忆合并失败：MBB-AI 未启用。";
            }
            markMergeTriggered();
            service.backupAllMemories("global-memory-merge");
            List<JSONObject> current = new ArrayList<>();
            for (Object object : snapshot) {
                if (!(object instanceof JSONObject)) continue;
                JSONObject item = (JSONObject) object;
                JSONObject normalized = new JSONObject(true);
                normalized.put("type",normalizeType(item.getString("memoryType")));
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
                List<JSONObject> sorted = MemoryBatchSorter.sortForGlobalMerge(current);
                List<List<JSONObject>> batches = MemoryBatchSorter.batches(sorted,batchSize);
                plugin.getLogger().sendInfo("[永久记忆] 合并第 "+round+"/"+maxRounds
                        +" 轮开始：输入 "+current.size()+" 条，共 "+batches.size()+" 批");
                List<JSONObject> mergedAll = new ArrayList<>();
                int concurrency = Math.max(1,Math.min(4,Math.min(config.memoryMergeConcurrency,
                        batches.size())));
                final int currentRound = round;
                ExecutorService executor = Executors.newFixedThreadPool(concurrency);
                try {
                    List<Future<List<JSONObject>>> futures = new ArrayList<>();
                    for (int i = 0; i < batches.size(); i++) {
                        List<JSONObject> batch = batches.get(i);
                        final int batchNo = i + 1;
                        plugin.getLogger().sendInfo("[永久记忆] 合并第 "+round+" 轮 批次 "
                                +batchNo+"/"+batches.size()+" 提交：输入 "+batch.size()
                                +" 条，并发 "+concurrency);
                        futures.add(executor.submit(() -> mergeGlobalBatchWithRetry(
                                ai,batch,currentRound,batchNo,batches.size())));
                    }
                    for (int i = 0; i < futures.size(); i++) {
                        List<JSONObject> merged = futureResult(futures.get(i));
                        if (merged == null) {
                            plugin.getLogger().sendWarn("[永久记忆] 合并第 "+round+" 轮 批次 "+(i+1)
                                    +" 失败，放弃本次合并，原记忆保持不变");
                            return "永久记忆合并失败，原记忆保持不变。";
                        }
                        mergedAll.addAll(merged);
                        plugin.getLogger().sendInfo("[永久记忆] 合并第 "+round+" 轮 批次 "+(i+1)
                                +"/"+batches.size()+" 完成："+batches.get(i).size()
                                +" -> "+merged.size()+" 条");
                    }
                } finally {
                    executor.shutdownNow();
                }
                current = MemoryBatchSorter.sortForMerge(mergedAll);
                plugin.getLogger().sendInfo("[永久记忆] 合并第 "+round+" 轮完成：结果 "
                        +current.size()+" 条");
            }
            if (executedRounds == 0) {
                plugin.getLogger().sendInfo("[永久记忆] 未超过上限，跳过合并");
                return "永久记忆未超过上限，未执行合并。";
            }
            int beforeDedupe = current.size();
            current = dedupeMemories(current);
            if (current.size() < beforeDedupe) {
                plugin.getLogger().sendInfo("[永久记忆] 合并写回前去重："
                        +beforeDedupe+" -> "+current.size()+" 条");
            }
            if (!isMergeResultSafe(original,current.size())) {
                plugin.getLogger().sendWarn("[永久记忆] 合并结果异常：原 "+original
                        +" 条，合并后仅 "+current.size()+" 条，放弃本次合并");
                return "永久记忆合并结果异常，已放弃，原记忆保持不变。";
            }
            int newRows = Math.max(0,count() - original);
            JSONArray mergedArray = new JSONArray();
            mergedArray.addAll(current);
            deleteUpTo(snapshotMaxId);
            int saved = insertAll(mergedArray);
            plugin.getLogger().sendInfo("[永久记忆] 整理合并完成：原 "+original
                    +" 条，合并后 "+saved+" 条，合并期间新增保留 "+newRows+" 条");
            return "永久记忆整理合并完成：原 "+original+" 条，合并后 "+saved
                    +" 条，合并期间新增保留 "+newRows+" 条。";
        } finally {
            merging = false;
        }
    }

    private List<JSONObject> mergeGlobalBatchWithRetry(PluginService ai,List<JSONObject> batch,
                                                       int round,int batchNo,int batchCount) {
        int retries = Math.max(0,Math.min(5,config.memoryMergeRetryCount));
        for (int attempt = 0; attempt <= retries; attempt++) {
            List<JSONObject> merged = mergeGlobalBatch(ai,batch,round,batchNo,batchCount);
            if (merged != null) return merged;
            if (attempt >= retries) break;
            plugin.getLogger().sendWarn("[永久记忆] 合并第 "+round+" 轮 批次 "
                    +batchNo+"/"+batchCount+" 第 "+(attempt+1)+" 次失败，准备重试（"
                    +(attempt+2)+"/"+(retries+1)+"）");
            if (!sleepQuietly(2000L * (attempt + 1))) return null;
        }
        return null;
    }

    private List<JSONObject> mergeGlobalBatch(PluginService ai,List<JSONObject> batch,
                                              int round,int batchNo,int batchCount) {
        JSONArray messages = new JSONArray();
        messages.add(message("system","你是永久记忆整理器。只处理当前这一批记忆，"
                +"当前角色："+service.getRoleName()+"。整理时必须基于该角色的视角，"
                +"区分角色自己的说话方式、习惯、知识，以及群友提供的通用信息。"
                +"游戏设定、专有名词、剧情事实由知识库负责，不要写进永久记忆；"
                +"请合并重复或高度相似的内容，保留所有有价值的信息，不要因为压缩而丢失关键内容。"
                +"保留人名、组织名、术语、活动名等专有名词的全称，不要自造缩写；只有原文中已经明确出现过的简称才可沿用。"
                +"type 只能原样使用 speech_style、tone、habit、lesson、meme、note；"
                +"speech_style 必须保留下划线，不要写成 speechstyle、speech-style 或 speech style。"
                +"同一语义即使被分到不同 type，也必须合并成一条，只保留最准确的 type，禁止输出同一件事的多个类型版本。"
                +"只输出 JSON，不要 Markdown：{\"memories\":[{\"type\":\"speech_style|tone|habit|"
                +"lesson|meme|note\",\"content\":\"整理后的内容\",\"importance\":1}]}。"));
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
            String type = normalizeType(item.getString("type"));
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

    private List<JSONObject> futureResult(Future<List<JSONObject>> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            plugin.getLogger().sendWarn("[永久记忆] 合并批次异常："
                    +safe(e.getCause() == null ? e.getMessage() : e.getCause().getMessage()));
            return null;
        }
    }

    private boolean sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean isMergeResultSafe(int original,int merged) {
        if (merged <= 0) return false;
        if (original >= 20 && merged < Math.max(2,original / 10)) return false;
        return true;
    }

    private void mergeIfNeeded() {
        if (count() <= config.globalMemoryMaxItems) return;
        long cooldownMs = Math.max(0,config.globalMemoryMergeCooldownMinute) * 60000L;
        long lastMerge = lastMergeTime();
        if (cooldownMs > 0 && lastMerge > 0
                && System.currentTimeMillis() - lastMerge < cooldownMs) {
            return;
        }
        service.submitBackground(this::mergeNow);
    }

    private long lastMergeTime() {
        JSONObject row = storage().queryOne(
                "SELECT `stateValue` FROM `"+GLOBAL_STATE_TABLE+"` WHERE `stateKey`=?",
                "lastMergeTime");
        return row == null ? 0L : row.getLongValue("stateValue");
    }

    private void markMergeTriggered() {
        long now = System.currentTimeMillis();
        JSONObject row = storage().queryOne(
                "SELECT `stateKey` FROM `"+GLOBAL_STATE_TABLE+"` WHERE `stateKey`=?",
                "lastMergeTime");
        if (row == null) {
            storage().insert("INSERT INTO `"+GLOBAL_STATE_TABLE+"` "
                            + "(`stateKey`,`stateValue`,`updateTime`) VALUES (?,?,?)",
                    "lastMergeTime",now,now);
        } else {
            storage().update("UPDATE `"+GLOBAL_STATE_TABLE+"` SET `stateValue`=?,`updateTime`=? "
                            + "WHERE `stateKey`=?",
                    now,now,"lastMergeTime");
        }
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
                "SELECT `ID`,`memoryType`,`content`,`importance`,`sourceGroupID`,`sourceUserID`,`updateTime` "
                        + "FROM `"+TABLE+"` ORDER BY `importance` DESC,`updateTime` DESC LIMIT ?",
                Math.min(limit,config.globalMemoryMaxItems));
        if (rows == null) return result;
        for (JSONObject row : rows) {
            JSONObject item = new JSONObject(true);
            item.put("id",row.getLongValue("ID"));
            item.put("type",normalizeType(row.getString("memoryType")));
            item.put("content",row.getString("content"));
            item.put("importance",row.getIntValue("importance"));
            item.put("sourceGroupID",row.getLongValue("sourceGroupID"));
            item.put("sourceUserID",row.getLongValue("sourceUserID"));
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
        return promptText("",0,0);
    }

    /**
     * 永久记忆按当前对话相关性排序后注入
     * 全局记忆是跨群共享的，所以以内容重合度与重要度为主，来源用户命中时再加权
     */
    public String promptText(String query,long userID,int maxChars) {
        //排序前先取全量候选池，避免按重要度预截断后贴题的旧记忆根本进不了排序
        JSONArray memories = list(Math.max(config.globalMemoryInjectItems,
                config.globalMemoryMaxItems));
        if (memories.isEmpty()) return "暂无全局永久记忆。";
        if (!config.memoryRelevanceSort) {
            StringBuilder plain = new StringBuilder();
            for (int i = 0; i < memories.size(); i++) {
                JSONObject item = memories.getJSONObject(i);
                plain.append("- [").append(safe(item.getString("type"))).append("] ")
                        .append(safe(item.getString("content"))).append("\n");
            }
            return plain.toString();
        }
        Set<String> queryBigrams = SpeechCorpusEntry.bigrams(
                SpeechCorpusEntry.normalize(query == null ? "" : query));
        long now = System.currentTimeMillis();
        List<ScoredMemory> scored = new ArrayList<>();
        for (int i = 0; i < memories.size(); i++) {
            JSONObject item = memories.getJSONObject(i);
            Set<String> bigrams = SpeechCorpusEntry.bigrams(
                    SpeechCorpusEntry.normalize(safe(item.getString("content"))));
            //重要度给足权重，避免一次无关的关键词命中就压过明显更重要的记忆
            double score = item.getIntValue("importance") * 0.8;
            if (!queryBigrams.isEmpty()) {
                int overlap = 0;
                for (String bigram : queryBigrams) {
                    if (bigrams.contains(bigram)) overlap++;
                }
                //按 query 长度归一化，长记忆不再靠体量天然占优
                score += (double) overlap / queryBigrams.size() * 8.0;
            }
            if (userID > 0 && item.getLongValue("sourceUserID") == userID) score += 1.5;
            score += recencyBonus(item.getLongValue("updateTime"),now);
            scored.add(new ScoredMemory(item,score));
        }
        Collections.sort(scored,new Comparator<ScoredMemory>() {
            @Override
            public int compare(ScoredMemory left,ScoredMemory right) {
                return Double.compare(right.score,left.score);
            }
        });
        int budget = maxChars > 0 ? maxChars : config.globalMemoryRelevanceMaxChars;
        StringBuilder builder = new StringBuilder();
        int used = 0;
        int count = 0;
        for (ScoredMemory scoredMemory : scored) {
            JSONObject item = scoredMemory.memory;
            String line = "- ["+safe(item.getString("type"))+"] "
                    +safe(item.getString("content"))+"\n";
            if (count > 0 && used + line.length() > budget) break;
            builder.append(line);
            used += line.length();
            count++;
        }
        plugin.getLogger().sendInfo("[永久记忆] 按相关性注入 "+count+"/"+memories.size()
                +" 条，占用 "+used+" 字符");
        return builder.length() == 0 ? "暂无全局永久记忆。" : builder.toString();
    }

    /**
     * 新记忆加分：随时间平滑衰减，越新越靠前
     */
    private static double recencyBonus(long updateTime,long now) {
        if (updateTime <= 0) return 0;
        double days = (now - updateTime) / 86400000.0;
        if (days < 0) days = 0;
        return 2.0 / (1.0 + days / 10.0);
    }

    private static class ScoredMemory {
        private final JSONObject memory;
        private final double score;

        private ScoredMemory(JSONObject memory,double score) {
            this.memory = memory;
            this.score = score;
        }
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
            item.put("memoryType",normalizeType(row.getString("memoryType")));
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
            item.put("memoryType",normalizeType(row.getString("memoryType")));
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
            String type = normalizeType(item.getString("memoryType"));
            if ("note".equals(type) && item.getString("type") != null) {
                type = normalizeType(item.getString("type"));
            }
            String content = safe(item.getString("content")).trim();
            if (content.isEmpty()) continue;
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

    /**
     * 旧数据和新模型输出都可能把 speech_style 写成 speechstyle / speech-style。
     * 这里统一归一化，但保留其他合法类型。
     */
    public static String normalizeType(String type) {
        String value = safeStatic(type).trim().toLowerCase(Locale.ROOT)
                .replace('-','_').replace(' ','_');
        if (value.isEmpty()) return "note";
        if ("speechstyle".equals(value) || "speech".equals(value)) return "speech_style";
        if ("knowledge".equals(value)) return "lesson";
        if ("speech_style".equals(value) || "tone".equals(value) || "habit".equals(value)
                || "lesson".equals(value) || "meme".equals(value) || "note".equals(value)) {
            return value;
        }
        return value;
    }

    private static String safeStatic(String value) {
        return value == null ? "" : value;
    }

    private static String normalizeContent(String content) {
        return SpeechCorpusEntry.normalize(safeStatic(content));
    }

    private JSONObject findDuplicate(String content) {
        String normalized = normalizeContent(content);
        if (normalized.isEmpty()) return null;
        List<JSONObject> rows = storage().query(
                "SELECT `ID`,`memoryType`,`content` FROM `"+TABLE+"`");
        if (rows == null) return null;
        for (JSONObject row : rows) {
            String other = normalizeContent(row.getString("content"));
            if (other.isEmpty()) continue;
            if (normalized.equals(other)) return row;
            if (normalized.length() >= 6 && other.length() >= 6
                    && similarity(normalized,other) >= DEDUPE_SIMILARITY) {
                return row;
            }
        }
        return null;
    }

    private List<JSONObject> dedupeMemories(List<JSONObject> memories) {
        List<JSONObject> result = new ArrayList<>();
        if (memories == null) return result;
        for (JSONObject item : memories) {
            if (item == null) continue;
            JSONObject duplicate = findSimilar(result,item.getString("content"));
            if (duplicate == null) {
                result.add(copyMemory(item));
            } else {
                mergeMemoryFields(duplicate,item);
            }
        }
        return result;
    }

    private JSONObject findSimilar(List<JSONObject> memories,String content) {
        String normalized = normalizeContent(content);
        if (normalized.isEmpty()) return null;
        for (JSONObject item : memories) {
            String other = normalizeContent(item.getString("content"));
            if (other.isEmpty()) continue;
            if (normalized.equals(other)) return item;
            if (normalized.length() >= 6 && other.length() >= 6
                    && similarity(normalized,other) >= DEDUPE_SIMILARITY) {
                return item;
            }
        }
        return null;
    }

    private JSONObject copyMemory(JSONObject item) {
        JSONObject copy = new JSONObject(true);
        copy.put("type",normalizeType(item.getString("type") == null
                ? item.getString("memoryType") : item.getString("type")));
        copy.put("content",safe(item.getString("content")));
        copy.put("importance",item.getIntValue("importance"));
        copy.put("sourceGroupID",item.getLongValue("sourceGroupID"));
        copy.put("sourceUserID",item.getLongValue("sourceUserID"));
        copy.put("updateTime",item.getLongValue("updateTime"));
        return copy;
    }

    private void mergeMemoryFields(JSONObject target,JSONObject incoming) {
        String targetType = normalizeType(target.getString("type") == null
                ? target.getString("memoryType") : target.getString("type"));
        String incomingType = normalizeType(incoming.getString("type") == null
                ? incoming.getString("memoryType") : incoming.getString("type"));
        target.put("type",preferType(targetType,incomingType));
        target.put("content",chooseContent(target.getString("content"),incoming.getString("content")));
        target.put("importance",Math.max(target.getIntValue("importance"),incoming.getIntValue("importance")));
        if (incoming.getLongValue("updateTime") >= target.getLongValue("updateTime")) {
            target.put("sourceGroupID",incoming.getLongValue("sourceGroupID"));
            target.put("sourceUserID",incoming.getLongValue("sourceUserID"));
            target.put("updateTime",incoming.getLongValue("updateTime"));
        }
    }

    private static String preferType(String first,String second) {
        String left = normalizeType(first);
        String right = normalizeType(second);
        return typePriority(left) >= typePriority(right) ? left : right;
    }

    private static int typePriority(String type) {
        String value = normalizeType(type);
        if ("speech_style".equals(value)) return 100;
        if ("tone".equals(value)) return 90;
        if ("habit".equals(value)) return 80;
        if ("lesson".equals(value)) return 70;
        if ("meme".equals(value)) return 60;
        if ("note".equals(value)) return 10;
        return 50;
    }

    private static String chooseContent(String first,String second) {
        String left = safeStatic(first).trim();
        String right = safeStatic(second).trim();
        if (left.isEmpty()) return right;
        if (right.isEmpty()) return left;
        return normalizeContent(right).length() > normalizeContent(left).length() ? right : left;
    }

    private double similarity(String left,String right) {
        if (left == null || right == null) return 0;
        if (left.equals(right)) return 1.0;
        Set<String> leftSet = SpeechCorpusEntry.bigrams(left);
        Set<String> rightSet = SpeechCorpusEntry.bigrams(right);
        if (leftSet.isEmpty() || rightSet.isEmpty()) return 0;
        int intersection = 0;
        for (String item : leftSet) {
            if (rightSet.contains(item)) intersection++;
        }
        int union = leftSet.size() + rightSet.size() - intersection;
        return union <= 0 ? 0 : intersection / (double) union;
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
