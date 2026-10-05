package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Plugin;
import org.moboxlab.moboxbot.API.PluginService;
import org.moboxlab.moboxbot.API.Storage.StorageService;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 所有群共享的角色永久记忆
 */
public class RoleplayGlobalMemoryService {
    private static final String TABLE = "plugin_mbb_roleplay_global_memory";

    private final Plugin plugin;
    private volatile RoleplayConfig config;
    private volatile boolean merging = false;

    public RoleplayGlobalMemoryService(Plugin plugin,RoleplayConfig config) {
        this.plugin = plugin;
        this.config = config;
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
        if (merging) return;
        merging = true;
        try {
            JSONArray memories = list(Math.max(count(),config.globalMemoryMaxItems));
            if (memories.size() < 2) return;
            PluginService ai = plugin.getServer().getPluginManager().getService("MBB-AI");
            if (ai == null) {
                plugin.getLogger().sendWarn("[永久记忆] 合并跳过：MBB-AI 未启用");
                return;
            }
            JSONArray messages = new JSONArray();
            messages.add(message("system","你是永久记忆整理器。请合并重复或高度相似的记忆，"
                    +"保留所有有价值的信息，不要因为压缩而丢失关键内容。只输出 JSON，不要 Markdown："
                    +"{\"memories\":[{\"type\":\"speech_style|tone|habit|knowledge|meme|note\","
                    +"\"content\":\"整理后的内容\",\"importance\":1}]}。最多输出 "
                    +config.globalMemoryMaxItems+" 条。"));
            JSONObject source = new JSONObject(true);
            source.put("memories",memories);
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
            params.put("sessionId","roleplay-global-memory-merge");
            params.put("messages",messages);
            long startTime = System.currentTimeMillis();
            JSONObject result = ai.call("chat",params);
            RoleplayAiLog.log(plugin.getLogger(),"永久记忆合并",0L,result,System.currentTimeMillis() - startTime);
            if (result == null || !result.getBooleanValue("status")) return;
            JSONObject parsed = parseJson(result.getString("content"));
            if (parsed == null) parsed = parseJson(result.getString("reasoningContent"));
            JSONArray merged = parsed == null ? null : parsed.getJSONArray("memories");
            if (merged == null || merged.isEmpty()) {
                if ("length".equalsIgnoreCase(safe(result.getString("finishReason")))) {
                    plugin.getLogger().sendWarn("[永久记忆] 合并输出被截断，可提高 memoryMergeMaxTokens "
                            +"或给 memoryProfile 配非 reasoning 模型");
                }
                plugin.getLogger().sendWarn("[永久记忆] 合并失败：模型没有返回有效 memories");
                return;
            }
            storage().update("DELETE FROM `"+TABLE+"`");
            int saved = 0;
            long now = System.currentTimeMillis();
            for (Object object : merged) {
                if (!(object instanceof JSONObject)) continue;
                JSONObject item = (JSONObject)object;
                String type = safe(item.getString("type")).trim();
                String content = safe(item.getString("content")).trim();
                if (content.isEmpty()) continue;
                if (type.isEmpty()) type = "note";
                int importance = item.getIntValue("importance");
                if (importance < 1) importance = 1;
                if (importance > 5) importance = 5;
                storage().insert("INSERT INTO `"+TABLE+"` "
                                + "(`memoryType`,`content`,`importance`,`sourceGroupID`,`sourceUserID`,`updateTime`) "
                                + "VALUES (?,?,?,?,?,?)",
                        type,content,importance,0L,0L,now);
                saved++;
            }
            plugin.getLogger().sendInfo("[永久记忆] 整理合并完成：原 "+memories.size()
                    +" 条，合并后 "+saved+" 条");
        } finally {
            merging = false;
        }
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

    public String backup() {
        try {
            JSONObject root = new JSONObject(true);
            root.put("version",1);
            root.put("exportTime",System.currentTimeMillis());
            root.put("count",count());
            root.put("memories",list(config.globalMemoryMaxItems));
            File file = new File(plugin.getDataFolder(),"global-memory-backup.json");
            Files.write(Paths.get(file.getAbsolutePath()),
                    root.toJSONString().getBytes(StandardCharsets.UTF_8));
            plugin.getLogger().sendInfo("[永久记忆] 已备份到 "+file.getAbsolutePath());
            return file.getAbsolutePath();
        } catch (Exception e) {
            plugin.getLogger().sendWarn("永久记忆备份失败："+e.getMessage());
            return "";
        }
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
