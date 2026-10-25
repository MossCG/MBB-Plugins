package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 统一大脑记忆
 *
 * 用一个检索入口管理角色自我经历、跨群共享记忆、群内记忆和用户私聊记忆。
 * 数据空间统一，但读取时仍按 audience 与当前会话执行访问边界。
 */
public class RoleplayBrainMemoryService {
    public static final String SCOPE_SELF = "self";
    public static final String SCOPE_SHARED = "shared";
    public static final String SCOPE_USER = "user";
    public static final String SCOPE_GROUP = "group";
    public static final String CHANNEL_GROUP = "group";
    public static final String CHANNEL_PRIVATE = "private";

    private static class ScoredMemory {
        final JSONObject memory;
        final double score;

        ScoredMemory(JSONObject memory,double score) {
            this.memory = memory;
            this.score = score;
        }
    }

    private final RoleplayService service;

    public RoleplayBrainMemoryService(RoleplayService service) {
        this.service = service;
    }

    public JSONObject remember(JSONObject params) {
        JSONObject result = new JSONObject(true);
        if (params == null) {
            result.put("status",false);
            result.put("message","缺少记忆参数");
            return result;
        }
        String scope = normalizeScope(params.getString("scope"));
        long userID = params.getLongValue("userID");
        long groupID = params.getLongValue("groupID");
        if (SCOPE_USER.equals(scope) && userID <= 0) {
            result.put("status",false);
            result.put("message","用户记忆缺少 userID");
            return result;
        }
        if (SCOPE_GROUP.equals(scope) && groupID <= 0) {
            result.put("status",false);
            result.put("message","群记忆缺少 groupID");
            return result;
        }
        String content = service.safe(params.getString("content"))
                .replaceAll("\\s+"," ").trim();
        if (content.length() < 2) {
            result.put("status",false);
            result.put("message","记忆内容不能为空");
            return result;
        }
        if (content.length() > 2000) content = content.substring(0,2000).trim();
        String unsafe = service.getSafetyService().unsafeMemoryReason(content);
        if (!unsafe.isEmpty()) {
            result.put("status",false);
            result.put("message",unsafe);
            return result;
        }
        String type = service.safe(params.getString("type"));
        if (type.trim().isEmpty()) type = "note";
        int importance = Math.max(1,Math.min(10,params.getIntValue("importance")));
        if (params.getIntValue("importance") <= 0) importance = 3;
        long now = System.currentTimeMillis();
        long eventTime = params.getLongValue("eventTime");
        if (eventTime <= 0) eventTime = now;
        long validFrom = params.getLongValue("validFrom");
        if (validFrom <= 0) validFrom = now;
        long validUntil = Math.max(0,params.getLongValue("validUntil"));
        double confidence = params.getDoubleValue("confidence");
        if (confidence <= 0) confidence = 1.0;
        confidence = Math.max(0,Math.min(1,confidence));
        long scopeID = SCOPE_USER.equals(scope) ? userID : (SCOPE_GROUP.equals(scope) ? groupID : 0L);
        long sourceGroupID = params.getLongValue("sourceGroupID");
        if (sourceGroupID <= 0 && groupID > 0) sourceGroupID = groupID;
        String sourceChannel = service.safe(params.getString("sourceChannel"));
        if (sourceChannel.trim().isEmpty()) {
            sourceChannel = SCOPE_USER.equals(scope) ? CHANNEL_PRIVATE : CHANNEL_GROUP;
        }
        String visibility = visibility(scope);
        String audience = audience(scope);
        String sourceType = service.safe(params.getString("sourceType")).trim();
        String sourceId = service.safe(params.getString("sourceId")).trim();

        JSONObject existing = findExisting(scope,scopeID,type,content,sourceType,sourceId);
        if (existing == null) {
            service.storage().insert("INSERT INTO `"+RoleplayService.MEMORY_TABLE+"` "
                            +"(`groupID`,`scopeType`,`scopeID`,`sourceGroupID`,`sourceChannel`,`visibility`,"
                            +"`audience`,`sourceType`,`sourceId`,`confidence`,`validFrom`,`validUntil`,"
                            +"`memoryType`,`subjectID`,`content`,`importance`,`eventTime`,`memoryStatus`,"
                            +"`supersededBy`,`updateTime`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'active',0,?)",
                    0L,scope,scopeID,sourceGroupID,sourceChannel,visibility,audience,
                    sourceType,sourceId,confidence,validFrom,validUntil,type,
                    SCOPE_USER.equals(scope) ? userID : 0L,content,importance,eventTime,now);
            JSONObject row = service.storage().queryOne(
                    "SELECT `ID` FROM `"+RoleplayService.MEMORY_TABLE+"` "
                            +"WHERE `scopeType`=? AND `scopeID`=? AND `sourceType`=? AND `sourceId`=? "
                            +"ORDER BY `ID` DESC LIMIT 1",
                    scope,scopeID,sourceType,sourceId);
            result.put("id",row == null ? 0L : row.getLongValue("ID"));
        } else {
            service.storage().update("UPDATE `"+RoleplayService.MEMORY_TABLE+"` SET "
                            +"`content`=?,`importance`=?,`eventTime`=?,`confidence`=?,`validFrom`=?,"
                            +"`validUntil`=?,`sourceGroupID`=?,`sourceChannel`=?,`updateTime`=? WHERE `ID`=?",
                    content,importance,eventTime,confidence,validFrom,validUntil,sourceGroupID,
                    sourceChannel,now,existing.getLongValue("ID"));
            result.put("id",existing.getLongValue("ID"));
        }
        result.put("status",true);
        result.put("scope",scope);
        return result;
    }

    public JSONArray search(long groupID,long userID,String channel,String query,int limit) {
        JSONArray result = new JSONArray();
        List<ScoredMemory> ranked = ranked(groupID,userID,channel,query,false);
        int capped = Math.max(1,Math.min(50,limit));
        for (int i = 0; i < ranked.size() && i < capped; i++) {
            JSONObject item = new JSONObject(true);
            JSONObject memory = ranked.get(i).memory;
            item.put("id",memory.getLongValue("ID"));
            item.put("scope",memory.getString("scopeType"));
            item.put("audience",memory.getString("audience"));
            item.put("type",memory.getString("memoryType"));
            item.put("content",memory.getString("content"));
            item.put("importance",memory.getIntValue("importance"));
            item.put("eventTime",memory.getLongValue("eventTime"));
            item.put("sourceType",memory.getString("sourceType"));
            item.put("sourceId",memory.getString("sourceId"));
            item.put("confidence",memory.getDoubleValue("confidence"));
            item.put("score",ranked.get(i).score);
            result.add(item);
        }
        return result;
    }

    public String selfPromptText(long groupID,long userID,String query,int maxChars) {
        List<ScoredMemory> ranked = ranked(groupID,userID,CHANNEL_GROUP,query,true);
        return formatPrompt(ranked,maxChars,"角色自己的经历");
    }

    public String unifiedPromptText(long groupID,long userID,String channel,String query,int maxChars) {
        List<ScoredMemory> ranked = ranked(groupID,userID,channel,query,false);
        return formatPrompt(ranked,maxChars,"统一记忆");
    }

    public JSONObject status() {
        JSONObject result = new JSONObject(true);
        JSONArray rows = new JSONArray();
        List<JSONObject> scopes = service.storage().query(
                "SELECT `scopeType`,COUNT(*) AS `count` FROM `"+RoleplayService.MEMORY_TABLE+"` "
                        +"WHERE `memoryStatus`='active' GROUP BY `scopeType` ORDER BY `scopeType` ASC");
        if (scopes != null) {
            for (JSONObject row : scopes) {
                JSONObject item = new JSONObject(true);
                item.put("scope",row.getString("scopeType"));
                item.put("count",row.getIntValue("count"));
                rows.add(item);
            }
        }
        result.put("status",true);
        result.put("scopes",rows);
        return result;
    }

    static boolean canRead(String audience,String visibility,String scopeType,long scopeID,
                           String channel,long currentUserID,long currentGroupID) {
        String safeAudience = safe(audience).toLowerCase(Locale.ROOT);
        String safeVisibility = safe(visibility).toLowerCase(Locale.ROOT);
        String safeScope = safe(scopeType).toLowerCase(Locale.ROOT);
        String safeChannel = safe(channel).toLowerCase(Locale.ROOT);
        if (SCOPE_SELF.equals(safeAudience) || SCOPE_SELF.equals(safeScope)
                || "public".equals(safeAudience) || SCOPE_SHARED.equals(safeAudience)
                || SCOPE_SHARED.equals(safeVisibility)) {
            return true;
        }
        if (SCOPE_USER.equals(safeAudience) || "private-user".equals(safeAudience)
                || "private".equals(safeVisibility) || "private".equals(safeScope)) {
            return CHANNEL_PRIVATE.equals(safeChannel) && currentUserID > 0 && scopeID == currentUserID;
        }
        if (SCOPE_GROUP.equals(safeAudience) || "group".equals(safeVisibility)
                || SCOPE_GROUP.equals(safeScope)) {
            return CHANNEL_GROUP.equals(safeChannel) && currentGroupID > 0 && scopeID == currentGroupID;
        }
        return false;
    }

    static String normalizeScope(String scope) {
        String value = safe(scope).trim().toLowerCase(Locale.ROOT);
        if (SCOPE_SHARED.equals(value) || SCOPE_USER.equals(value) || SCOPE_GROUP.equals(value)) {
            return value;
        }
        return SCOPE_SELF;
    }

    static String audience(String scope) {
        if (SCOPE_USER.equals(scope)) return "private-user";
        if (SCOPE_GROUP.equals(scope)) return SCOPE_GROUP;
        if (SCOPE_SHARED.equals(scope)) return SCOPE_SHARED;
        return SCOPE_SELF;
    }

    static String visibility(String scope) {
        if (SCOPE_USER.equals(scope)) return "private";
        if (SCOPE_GROUP.equals(scope)) return SCOPE_GROUP;
        return SCOPE_SHARED.equals(scope) ? SCOPE_SHARED : SCOPE_SELF;
    }

    private JSONObject findExisting(String scope,long scopeID,String type,String content,
                                    String sourceType,String sourceId) {
        if (!sourceType.isEmpty() && !sourceId.isEmpty()) {
            JSONObject bySource = service.storage().queryOne(
                    "SELECT `ID` FROM `"+RoleplayService.MEMORY_TABLE+"` "
                            +"WHERE `memoryStatus`='active' AND `scopeType`=? AND `scopeID`=? "
                            +"AND `memoryType`=? AND `sourceType`=? AND `sourceId`=? LIMIT 1",
                    scope,scopeID,type,sourceType,sourceId);
            if (bySource != null) return bySource;
        }
        return service.storage().queryOne(
                "SELECT `ID` FROM `"+RoleplayService.MEMORY_TABLE+"` "
                        +"WHERE `memoryStatus`='active' AND `scopeType`=? AND `scopeID`=? "
                        +"AND `memoryType`=? AND `content`=? LIMIT 1",
                scope,scopeID,type,content);
    }

    private List<ScoredMemory> ranked(long groupID,long userID,String channel,String query,
                                      boolean selfOnly) {
        List<JSONObject> rows = service.storage().query(
                "SELECT * FROM `"+RoleplayService.MEMORY_TABLE+"` "
                        +"WHERE `memoryStatus`='active' "
                        +"AND (`validFrom`=0 OR `validFrom`<=?) "
                        +"AND (`validUntil`=0 OR `validUntil`>?) "
                        +"ORDER BY `importance` DESC,`updateTime` DESC LIMIT 600",
                System.currentTimeMillis(),System.currentTimeMillis());
        if (rows == null || rows.isEmpty()) return Collections.emptyList();
        Set<String> queryBigrams = SpeechCorpusEntry.bigrams(
                SpeechCorpusEntry.normalize(service.safe(query)));
        String normalizedQuery = SpeechCorpusEntry.normalize(service.safe(query));
        long now = System.currentTimeMillis();
        List<ScoredMemory> result = new ArrayList<>();
        for (JSONObject row : rows) {
            String scope = service.safe(row.getString("scopeType"));
            if (selfOnly && !SCOPE_SELF.equals(scope)) continue;
            if (!canRead(row.getString("audience"),row.getString("visibility"),scope,
                    row.getLongValue("scopeID"),channel,userID,groupID)) {
                continue;
            }
            String content = service.safe(row.getString("content"));
            String normalizedContent = SpeechCorpusEntry.normalize(content);
            Set<String> contentBigrams = SpeechCorpusEntry.bigrams(normalizedContent);
            double score = row.getIntValue("importance") * 0.8;
            if (!queryBigrams.isEmpty()) {
                int overlap = 0;
                for (String bigram : queryBigrams) {
                    if (contentBigrams.contains(bigram)) overlap++;
                }
                score += (double)overlap / queryBigrams.size() * 8.0;
            }
            if (normalizedQuery.length() >= 2 && normalizedContent.contains(normalizedQuery)) {
                score += 4.0;
            }
            if (SCOPE_SELF.equals(scope)) score += 1.0;
            long eventTime = row.getLongValue("eventTime");
            score += recencyBonus(eventTime > 0 ? eventTime : row.getLongValue("updateTime"),now);
            score *= Math.max(0.1,row.getDoubleValue("confidence"));
            result.add(new ScoredMemory(row,score));
        }
        Collections.sort(result,new Comparator<ScoredMemory>() {
            @Override
            public int compare(ScoredMemory left,ScoredMemory right) {
                return Double.compare(right.score,left.score);
            }
        });
        return result;
    }

    private String formatPrompt(List<ScoredMemory> ranked,int maxChars,String title) {
        if (ranked.isEmpty()) return "";
        StringBuilder builder = new StringBuilder(title).append("：\n");
        int used = 0;
        int count = 0;
        for (ScoredMemory item : ranked) {
            JSONObject row = item.memory;
            String line = "- ["+service.formatMemoryTime(row.getLongValue("eventTime"))+"] "
                    +service.safe(row.getString("content"))+"\n";
            if (count > 0 && used + line.length() > Math.max(200,maxChars)) break;
            builder.append(line);
            used += line.length();
            count++;
        }
        return count == 0 ? "" : builder.toString().trim();
    }

    private double recencyBonus(long time,long now) {
        if (time <= 0) return 0;
        double days = Math.max(0,(now - time) / 86400000.0);
        return 2.0 / (1.0 + days / 10.0);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
