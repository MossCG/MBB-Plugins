package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 记忆备份与恢复
 *
 * 负责短期、长期、永久、好友记忆、情绪、黑名单和群员印象的快照导出与恢复。
 */
public class RoleplayMemoryBackupService {
    private final RoleplayService service;

    public RoleplayMemoryBackupService(RoleplayService service) {
        this.service = service;
    }

    public synchronized String backupAllMemories(String reason) {
        try {
            File directory = backupDirectory();
            JSONObject root = new JSONObject(true);
            root.put("version",8);
            root.put("type","mbb-roleplay-memory-backup");
            root.put("createTime",System.currentTimeMillis());
            root.put("reason",service.safe(reason));
            root.put("shortTerm",exportShortTerm());
            root.put("structuredContext",service.contextCompactor().exportAll());
            root.put("longTerm",exportLongTerm());
            root.put("globalMemory",service.globalMemoryService().exportAll());
            root.put("emotion",service.emotionService().exportState());
            root.put("blacklist",service.blacklistService().exportAll());
            root.put("member",service.memberService().exportAll());
            root.put("friendMemory",service.friendMemoryService().exportAll());
            root.put("conversationContext",service.conversationContextService().exportAll());
            String name = "memory-backup-"
                    +new SimpleDateFormat("yyyyMMdd-HHmmss-SSS",Locale.CHINA).format(new Date())
                    +".json";
            File file = new File(directory,name);
            Files.write(Paths.get(file.getAbsolutePath()),
                    JSON.toJSONString(root,true).getBytes(StandardCharsets.UTF_8));
            service.plugin().getLogger().sendInfo("[记忆] 已备份全部记忆到 "+file.getAbsolutePath());
            return name;
        } catch (Exception e) {
            service.plugin().getLogger().sendWarn("备份全部记忆失败："+e.getMessage());
            return "";
        }
    }

    public List<String> listMemoryBackups() {
        List<String> result = new java.util.ArrayList<>();
        File[] files = backupDirectory().listFiles();
        if (files == null) return result;
        for (File file : files) {
            String name = file.getName();
            if (file.isFile() && name.startsWith("memory-backup-") && name.endsWith(".json")) {
                result.add(name);
            }
        }
        Collections.sort(result,Collections.reverseOrder());
        return result;
    }

    public synchronized JSONObject restoreAllMemories(String fileName) {
        JSONObject result = new JSONObject(true);
        String name = fileName == null ? "" : fileName.trim();
        if (name.isEmpty()) {
            result.put("status",false);
            result.put("message","请提供备份文件名。");
            return result;
        }
        if (name.contains("/") || name.contains("\\") || name.contains("..")) {
            result.put("status",false);
            result.put("message","备份文件名不合法。");
            return result;
        }
        File file = new File(backupDirectory(),name);
        if (!file.exists()) {
            result.put("status",false);
            result.put("message","找不到备份文件："+name);
            return result;
        }
        try {
            String text = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath())),
                    StandardCharsets.UTF_8);
            JSONObject root = JSONObject.parseObject(text);
            if (root == null || !"mbb-roleplay-memory-backup".equals(root.getString("type"))) {
                result.put("status",false);
                result.put("message","备份文件格式不正确。");
                return result;
            }
            JSONArray shortTerm = root.getJSONArray("shortTerm");
            JSONArray structuredContext = root.getJSONArray("structuredContext");
            JSONArray longTerm = root.getJSONArray("longTerm");
            JSONArray globalMemory = root.getJSONArray("globalMemory");
            JSONObject emotion = root.getJSONObject("emotion");
            JSONArray blacklist = root.getJSONArray("blacklist");
            JSONArray member = root.getJSONArray("member");
            JSONArray friendMemory = root.getJSONArray("friendMemory");
            JSONObject conversationContext = root.getJSONObject("conversationContext");
            backupAllMemories("before-restore-"+name);
            service.storage().update("DELETE FROM `"+RoleplayService.MEMORY_TABLE+"`");
            service.storage().update("DELETE FROM `"+RoleplayService.STATE_TABLE+"`");
            service.contextCompactor().clearAll();
            service.globalMemoryService().replaceAll(globalMemory);
            service.emotionService().restoreState(emotion);
            service.blacklistService().restoreAll(blacklist);
            service.memberService().restoreAll(member);
            if (root.containsKey("friendMemory")) {
                service.friendMemoryService().restoreAll(friendMemory);
            }
            int conversationSaved = 0;
            if (root.containsKey("conversationContext")) {
                conversationSaved = service.conversationContextService().restoreAll(conversationContext);
            }
            int shortSaved = restoreShortTerm(shortTerm);
            int contextSaved = service.contextCompactor().restoreAll(structuredContext);
            if (contextSaved == 0) contextSaved = service.contextCompactor().recoverLegacySummaries();
            int longSaved = restoreLongTerm(longTerm);
            service.clearRuntimeMemoryState();
            result.put("status",true);
            result.put("message","记忆已恢复。");
            result.put("shortTerm",shortSaved);
            result.put("structuredContext",contextSaved);
            result.put("longTerm",longSaved);
            result.put("globalMemory",globalMemory == null ? 0 : globalMemory.size());
            result.put("emotion",emotion != null);
            result.put("blacklist",blacklist == null ? 0 : blacklist.size());
            result.put("member",member == null ? 0 : member.size());
            result.put("friendMemory",friendMemory == null ? 0 : friendMemory.size());
            result.put("conversationContext",conversationSaved);
            service.plugin().getLogger().sendInfo("[记忆] 已从 "+name+" 恢复记忆：短期 "+shortSaved
                    +" 条，结构化上下文 "+contextSaved+" 条，长期 "+longSaved+" 条，永久 "
                    +(globalMemory == null ? 0 : globalMemory.size())+" 条，好友 "
                    +(friendMemory == null ? 0 : friendMemory.size())+" 条，追加上下文 "
                    +conversationSaved+" 条");
            return result;
        } catch (Exception e) {
            service.plugin().getLogger().sendException(e);
            result.put("status",false);
            result.put("message","恢复失败："+e.getMessage());
            return result;
        }
    }
    private File backupDirectory() {
        File directory = new File(service.plugin().getDataFolder(),"backup");
        if (!directory.exists()) directory.mkdirs();
        return directory;
    }

    private JSONArray exportShortTerm() {
        JSONArray result = new JSONArray();
        List<JSONObject> rows = service.storage().query(
                "SELECT `groupID`,`shortSummary`,`lastMemoryTime`,`lastMemoryID`,`lastMergeTime`,`updateTime` "
                        + "FROM `"+RoleplayService.STATE_TABLE+"` ORDER BY `groupID` ASC");
        if (rows == null) return result;
        for (JSONObject row : rows) {
            JSONObject item = new JSONObject(true);
            item.put("groupID",row.getLongValue("groupID"));
            item.put("shortSummary",row.getString("shortSummary"));
            item.put("lastMemoryTime",row.getLongValue("lastMemoryTime"));
            item.put("lastMemoryID",row.getLongValue("lastMemoryID"));
            item.put("lastMergeTime",row.getLongValue("lastMergeTime"));
            item.put("updateTime",row.getLongValue("updateTime"));
            result.add(item);
        }
        return result;
    }

    private JSONArray exportLongTerm() {
        JSONArray result = new JSONArray();
        List<JSONObject> rows = service.storage().query(
                "SELECT `groupID`,`scopeType`,`scopeID`,`sourceGroupID`,`sourceChannel`,`visibility`,"
                        + "`audience`,`sourceType`,`sourceId`,`confidence`,`validFrom`,`validUntil`,"
                        + "`memoryType`,`subjectID`,`content`,`importance`,`eventTime`,`memoryStatus`,`supersededBy`,`updateTime` "
                        + "FROM `"+RoleplayService.MEMORY_TABLE+"` ORDER BY `groupID` ASC,`ID` ASC");
        if (rows == null) return result;
        for (JSONObject row : rows) {
            JSONObject item = new JSONObject(true);
            item.put("groupID",row.getLongValue("groupID"));
            item.put("scopeType",row.getString("scopeType"));
            item.put("scopeID",row.getLongValue("scopeID"));
            item.put("sourceGroupID",row.getLongValue("sourceGroupID"));
            item.put("sourceChannel",row.getString("sourceChannel"));
            item.put("visibility",row.getString("visibility"));
            item.put("audience",row.getString("audience"));
            item.put("sourceType",row.getString("sourceType"));
            item.put("sourceId",row.getString("sourceId"));
            item.put("confidence",row.getDoubleValue("confidence"));
            item.put("validFrom",row.getLongValue("validFrom"));
            item.put("validUntil",row.getLongValue("validUntil"));
            item.put("type",row.getString("memoryType"));
            item.put("subjectID",row.getLongValue("subjectID"));
            item.put("content",row.getString("content"));
            item.put("importance",row.getIntValue("importance"));
            item.put("eventTime",row.getLongValue("eventTime"));
            item.put("memoryStatus",row.getString("memoryStatus"));
            item.put("supersededBy",row.getLongValue("supersededBy"));
            item.put("updateTime",row.getLongValue("updateTime"));
            result.add(item);
        }
        return result;
    }

    private int restoreShortTerm(JSONArray memories) {
        if (memories == null) return 0;
        int saved = 0;
        boolean hasGlobal = false;
        String firstSummary = "";
        long firstTime = 0L;
        long firstID = 0L;
        long firstUpdate = 0L;
        for (Object object : memories) {
            if (!(object instanceof JSONObject)) continue;
            JSONObject item = (JSONObject) object;
            long groupID = item.getLongValue("groupID");
            if (groupID < 0) continue;
            if (groupID == 0) hasGlobal = true;
            if (firstSummary.isEmpty() && !service.safe(item.getString("shortSummary")).trim().isEmpty()) {
                firstSummary = service.safe(item.getString("shortSummary"));
                firstTime = item.getLongValue("lastMemoryTime");
                firstID = item.getLongValue("lastMemoryID");
                firstUpdate = item.getLongValue("updateTime");
            }
            service.storage().insert("INSERT OR REPLACE INTO `"+RoleplayService.STATE_TABLE+"` "
                            + "(`groupID`,`shortSummary`,`lastMemoryTime`,`lastMemoryID`,`lastMergeTime`,`updateTime`) "
                            + "VALUES (?,?,?,?,?,?)",
                    groupID,service.safe(item.getString("shortSummary")),
                    item.getLongValue("lastMemoryTime"),item.getLongValue("lastMemoryID"),
                    item.getLongValue("lastMergeTime"),item.getLongValue("updateTime"));
            saved++;
        }
        if (!hasGlobal && !firstSummary.isEmpty()) {
            long now = System.currentTimeMillis();
            service.storage().insert("INSERT OR REPLACE INTO `"+RoleplayService.STATE_TABLE+"` "
                            + "(`groupID`,`shortSummary`,`lastMemoryTime`,`lastMemoryID`,`lastMergeTime`,`updateTime`) "
                            + "VALUES (?,?,?,?,?,?)",
                    0L,firstSummary,firstTime,firstID,0L,firstUpdate > 0 ? firstUpdate : now);
            saved++;
        }
        return saved;
    }

    private int restoreLongTerm(JSONArray memories) {
        if (memories == null) return 0;
        int saved = 0;
        for (Object object : memories) {
            if (!(object instanceof JSONObject)) continue;
            JSONObject item = (JSONObject) object;
            long groupID = item.getLongValue("groupID");
            String content = service.safe(item.getString("content")).trim();
            if (groupID < 0 || content.isEmpty()) continue;
            String scopeType = service.safe(item.getString("scopeType"));
            if (scopeType.isEmpty()) scopeType = "global";
            long scopeID = item.getLongValue("scopeID");
            long sourceGroupID = item.getLongValue("sourceGroupID");
            if (sourceGroupID <= 0) sourceGroupID = groupID;
            String sourceChannel = service.safe(item.getString("sourceChannel"));
            if (sourceChannel.isEmpty()) sourceChannel = "group";
            String visibility = service.safe(item.getString("visibility"));
            if (visibility.isEmpty()) visibility = "shared";
            String audience = service.safe(item.getString("audience"));
            if (audience.isEmpty()) {
                audience = "private".equals(visibility) ? "private-user"
                        : ("self".equals(visibility) ? "self"
                        : ("group".equals(visibility) ? "group" : "shared"));
            }
            service.storage().insert("INSERT INTO `"+RoleplayService.MEMORY_TABLE+"` "
                            + "(`groupID`,`scopeType`,`scopeID`,`sourceGroupID`,`sourceChannel`,`visibility`,"
                            + "`audience`,`sourceType`,`sourceId`,`confidence`,`validFrom`,`validUntil`,"
                            + "`memoryType`,`subjectID`,`content`,`importance`,`eventTime`,`memoryStatus`,`supersededBy`,`updateTime`) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    groupID,scopeType,scopeID,sourceGroupID,sourceChannel,visibility,audience,
                    service.safe(item.getString("sourceType")),service.safe(item.getString("sourceId")),
                    item.getDoubleValue("confidence") <= 0 ? 1.0 : item.getDoubleValue("confidence"),
                    item.getLongValue("validFrom"),item.getLongValue("validUntil"),
                    service.safe(item.getString("type")),item.getLongValue("subjectID"),
                    content,item.getIntValue("importance"),
                    item.getLongValue("eventTime") > 0 ? item.getLongValue("eventTime")
                            : item.getLongValue("updateTime"),
                    service.safe(item.getString("memoryStatus")).isEmpty()
                            ? "active" : item.getString("memoryStatus"),
                    item.getLongValue("supersededBy"),
                    item.getLongValue("updateTime"));
            saved++;
        }
        return saved;
    }
}
