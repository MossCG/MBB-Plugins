package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 角色记忆查询与相关性排序
 *
 * 负责长期记忆读取、相关性注入、短期摘要、记忆游标、记忆计数和上下文预算截断。
 */
public class RoleplayMemoryQueryService {
    private final RoleplayService service;

    public RoleplayMemoryQueryService(RoleplayService service) {
        this.service = service;
    }

    String longMemoryText(long groupID,long userID,String query) {
        //相关性排序时先取更大的候选池，避免重要度低但很贴题的老记忆根本没机会参与排序
        int pool = service.config().memoryRelevanceSort
                ? Math.max(service.config().memoryRelevancePoolSize,service.config().maxLongMemories)
                : service.config().maxLongMemories;
        JSONArray memories = longMemories(groupID,true,pool);
        if (memories.isEmpty()) return "暂无长期记忆。";
        if (!service.config().memoryRelevanceSort) {
            StringBuilder plain = new StringBuilder();
            for (int i = 0; i < memories.size(); i++) {
                JSONObject item = memories.getJSONObject(i);
                plain.append("- [").append(service.formatMemoryTime(item.getLongValue("eventTime"))).append("] [")
                        .append(service.safe(item.getString("type"))).append("] ")
                        .append(service.safe(item.getString("content"))).append("\n");
            }
            return plain.toString();
        }
        List<JSONObject> ranked = rankMemories(memories,userID,query);
        StringBuilder builder = new StringBuilder();
        int maxChars = service.config().memoryRelevanceMaxChars;
        int used = 0;
        int count = 0;
        for (JSONObject item : ranked) {
            String line = "- ["+service.formatMemoryTime(item.getLongValue("eventTime"))+"] ["
                    +service.safe(item.getString("type"))+"] "
                    +service.safe(item.getString("content"))+"\n";
            if (count > 0 && used + line.length() > maxChars) break;
            builder.append(line);
            used += line.length();
            count++;
        }
        service.plugin().getLogger().sendInfo("[记忆] 当前上下文"+groupID+" 长期记忆按相关性注入 "+count
                +"/"+memories.size()+" 条，占用 "+used+" 字符");
        return builder.length() == 0 ? "暂无长期记忆。" : builder.toString();
    }

    String privateMemoryText(long userID,String query,int maxChars) {
        if (userID <= 0) return "暂无私聊长期记忆。";
        List<JSONObject> rows = service.storage().query(
                "SELECT `memoryType`,`content`,`importance`,`eventTime`,`updateTime` FROM `"
                        + RoleplayService.MEMORY_TABLE + "` "
                        + "WHERE `memoryStatus`='active' AND `visibility`='private' "
                        + "AND `scopeType`='private' AND `scopeID`=? "
                        + "ORDER BY `importance` DESC,`updateTime` DESC LIMIT 200",
                userID);
        if (rows == null || rows.isEmpty()) return "暂无私聊长期记忆。";
        Set<String> queryBigrams = SpeechCorpusEntry.bigrams(
                SpeechCorpusEntry.normalize(query == null ? "" : query));
        String normalizedQuery = SpeechCorpusEntry.normalize(query == null ? "" : query);
        long now = System.currentTimeMillis();
        List<MemoryScore> scored = new ArrayList<>();
        for (JSONObject row : rows) {
            String content = service.safe(row.getString("content"));
            Set<String> contentBigrams = SpeechCorpusEntry.bigrams(
                    SpeechCorpusEntry.normalize(content));
            double score = row.getIntValue("importance") * 0.8;
            if (!queryBigrams.isEmpty()) {
                int overlap = 0;
                for (String bigram : queryBigrams) {
                    if (contentBigrams.contains(bigram)) overlap++;
                }
                score += (double)overlap / queryBigrams.size() * 8.0;
            }
            if (normalizedQuery.length() >= 2
                    && SpeechCorpusEntry.normalize(content).contains(normalizedQuery)) {
                score += 4.0;
            }
            long eventTime = row.getLongValue("eventTime");
            score += recencyBonus(eventTime > 0 ? eventTime : row.getLongValue("updateTime"),now);
            scored.add(new MemoryScore(row,score));
        }
        Collections.sort(scored,new Comparator<MemoryScore>() {
            @Override
            public int compare(MemoryScore left,MemoryScore right) {
                return Double.compare(right.score,left.score);
            }
        });
        int budget = maxChars > 0 ? maxChars : service.config().memoryRelevanceMaxChars;
        StringBuilder builder = new StringBuilder();
        int used = 0;
        int count = 0;
        for (MemoryScore item : scored) {
            JSONObject row = item.memory;
            String line = "- "+service.safe(row.getString("content"))+"\n";
            if (count > 0 && used + line.length() > budget) break;
            builder.append(line);
            used += line.length();
            count++;
        }
        service.plugin().getLogger().sendInfo("[私聊记忆] 用户"+userID+" 按相关性注入 "
                +count+"/"+scored.size()+" 条，占用 "+used+" 字符");
        return builder.length() == 0 ? "暂无私聊长期记忆。" : builder.toString();
    }

    List<JSONObject> rankMemories(JSONArray memories,long userID,String query) {
        Set<String> queryBigrams = SpeechCorpusEntry.bigrams(
                SpeechCorpusEntry.normalize(query == null ? "" : query));
        String normalizedQuery = SpeechCorpusEntry.normalize(query == null ? "" : query);
        long now = System.currentTimeMillis();
        List<MemoryScore> scored = new ArrayList<>();
        for (int i = 0; i < memories.size(); i++) {
            JSONObject item = memories.getJSONObject(i);
            Set<String> bigrams = SpeechCorpusEntry.bigrams(
                    SpeechCorpusEntry.normalize(service.safe(item.getString("content"))));
            String normalizedContent = SpeechCorpusEntry.normalize(service.safe(item.getString("content")));
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
            if (userID > 0 && item.getLongValue("subjectID") == userID) score += 2.0;
            //混合检索：完整关键词命中比 bigram 更可信，事件时间优先于写入时间
            if (normalizedQuery.length() >= 2 && normalizedContent.contains(normalizedQuery)) {
                score += 4.0;
            }
            long eventTime = item.getLongValue("eventTime");
            score += recencyBonus(eventTime > 0 ? eventTime : item.getLongValue("updateTime"),now);
            scored.add(new MemoryScore(item,score));
        }
        Collections.sort(scored,new Comparator<MemoryScore>() {
            @Override
            public int compare(MemoryScore left,MemoryScore right) {
                return Double.compare(right.score,left.score);
            }
        });
        List<JSONObject> result = new ArrayList<>();
        for (MemoryScore item : scored) result.add(item.memory);
        return result;
    }

    double recencyBonus(long updateTime,long now) {
        if (updateTime <= 0) return 0;
        double days = (now - updateTime) / 86400000.0;
        if (days < 0) days = 0;
        return 2.0 / (1.0 + days / 10.0);
    }

    JSONArray longMemories(long groupID) {
        return longMemories(groupID,true);
    }

    JSONArray longMemories(long groupID,boolean excludeBlacklisted) {
        return longMemories(groupID,excludeBlacklisted,service.config().maxLongMemories);
    }

    JSONArray longMemories(long groupID,boolean excludeBlacklisted,int limit) {
        JSONArray result = new JSONArray();
        int capped = limit < 1 ? service.config().maxLongMemories : limit;
        //长期记忆已经升级为跨群共享；groupID 只保留为来源，不再作为读取隔离条件。
        List<JSONObject> rows = service.storage().query(
                "SELECT `memoryType`,`subjectID`,`content`,`importance`,`eventTime`,`updateTime` FROM `"+RoleplayService.MEMORY_TABLE+"` "
                        + "WHERE `memoryStatus`='active' AND `visibility`='shared' "
                        + "ORDER BY `importance` DESC,`updateTime` DESC LIMIT ?",
                capped);
        if (rows == null) return result;
        Set<Long> blacklisted = excludeBlacklisted
                ? service.blacklistService().userIDs(groupID) : Collections.<Long>emptySet();
        for (JSONObject row : rows) {
            long subjectID = row.getLongValue("subjectID");
            if (subjectID > 0 && blacklisted.contains(subjectID)) continue;
            JSONObject item = new JSONObject(true);
            item.put("type",row.getString("memoryType"));
            item.put("subjectID",subjectID);
            item.put("content",row.getString("content"));
            item.put("importance",row.getIntValue("importance"));
            item.put("eventTime",row.getLongValue("eventTime"));
            item.put("updateTime",row.getLongValue("updateTime"));
            result.add(item);
        }
        return result;
    }

    JSONArray timeline(long groupID,int page,int pageSize) {
        JSONArray result = new JSONArray();
        if (groupID <= 0) return result;
        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 12;
        if (pageSize > 50) pageSize = 50;
        long offset = (long)(page - 1) * pageSize;
        List<JSONObject> rows = service.storage().query(
                "SELECT `memoryType`,`subjectID`,`content`,`importance`,`eventTime`,`updateTime` "
                        + "FROM `"+RoleplayService.MEMORY_TABLE+"` WHERE `groupID`=? AND `memoryStatus`='active' "
                        + "ORDER BY `eventTime` DESC,`ID` DESC LIMIT "+pageSize+" OFFSET "+offset,
                groupID);
        if (rows == null) return result;
        for (JSONObject row : rows) {
            JSONObject item = new JSONObject(true);
            item.put("type",row.getString("memoryType"));
            item.put("subjectID",row.getLongValue("subjectID"));
            item.put("content",row.getString("content"));
            item.put("importance",row.getIntValue("importance"));
            item.put("eventTime",row.getLongValue("eventTime"));
            item.put("updateTime",row.getLongValue("updateTime"));
            result.add(item);
        }
        return result;
    }

    JSONArray archived(long groupID,int limit) {
        JSONArray result = new JSONArray();
        if (groupID <= 0) return result;
        if (limit < 1) limit = 1;
        if (limit > 50) limit = 50;
        List<JSONObject> rows = service.storage().query(
                "SELECT `ID`,`memoryType`,`subjectID`,`content`,`eventTime`,`supersededBy`,`updateTime` "
                        + "FROM `"+RoleplayService.MEMORY_TABLE+"` WHERE `groupID`=? AND `memoryStatus`='archived' "
                        + "ORDER BY `updateTime` DESC LIMIT "+limit,
                groupID);
        if (rows == null) return result;
        for (JSONObject row : rows) {
            JSONObject item = new JSONObject(true);
            item.put("id",row.getLongValue("ID"));
            item.put("type",row.getString("memoryType"));
            item.put("content",row.getString("content"));
            item.put("eventTime",row.getLongValue("eventTime"));
            item.put("supersededBy",row.getLongValue("supersededBy"));
            result.add(item);
        }
        return result;
    }

    String shortSummary(long groupID) {
        JSONObject global = service.storage().queryOne(
                "SELECT `shortSummary` FROM `"+RoleplayService.STATE_TABLE+"` WHERE `groupID`=0");
        if (global != null && global.getString("shortSummary") != null
                && !global.getString("shortSummary").trim().isEmpty()) {
            return global.getString("shortSummary");
        }
        JSONObject row = service.storage().queryOne(
                "SELECT `shortSummary` FROM `"+RoleplayService.STATE_TABLE+"` WHERE `groupID`=?",groupID);
        return row == null || row.getString("shortSummary") == null || row.getString("shortSummary").isEmpty()
                ? "暂无短期记忆。" : row.getString("shortSummary");
    }

    /**
     * 只读取指定群自己的短期摘要，不读取跨群全局摘要。
     */
    String groupShortSummary(long groupID) {
        if (groupID <= 0) return "暂无短期记忆。";
        JSONObject row = service.storage().queryOne(
                "SELECT `shortSummary` FROM `"+RoleplayService.STATE_TABLE+"` WHERE `groupID`=?",groupID);
        return row == null || row.getString("shortSummary") == null || row.getString("shortSummary").isEmpty()
                ? "暂无短期记忆。" : row.getString("shortSummary");
    }

    RoleplayService.MemoryCursor memoryCursor(long groupID) {
        JSONObject row = service.storage().queryOne(
                "SELECT `lastMemoryTime`,`lastMemoryID` FROM `"+RoleplayService.STATE_TABLE+"` WHERE `groupID`=?",groupID);
        return row == null
                ? new RoleplayService.MemoryCursor(0L,0L)
                : new RoleplayService.MemoryCursor(row.getLongValue("lastMemoryTime"),row.getLongValue("lastMemoryID"));
    }

    int memoryCount(long groupID) {
        List<JSONObject> rows = service.storage().query(
                "SELECT COUNT(*) AS `count` FROM `"+RoleplayService.MEMORY_TABLE+"` "
                        + "WHERE `memoryStatus`='active' AND `visibility`='shared'");
        return rows == null || rows.isEmpty() ? 0 : rows.get(0).getIntValue("count");
    }

    List<JSONObject> limitMemoryRows(List<JSONObject> rows,int maxChars) {
        List<JSONObject> result = new ArrayList<>();
        if (rows == null || rows.isEmpty()) return result;
        int total = 0;
        for (JSONObject row : rows) {
            String userName = service.safe(row.getString("userName"));
            String content = service.safe(row.getString("content"));
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

    /**
     * 新记忆加分：随时间平滑衰减，越新越靠前，避免老记忆长期霸占注入位
     */
    private static class MemoryScore {
        private final JSONObject memory;
        private final double score;

        private MemoryScore(JSONObject memory,double score) {
            this.memory = memory;
            this.score = score;
        }
    }
}
