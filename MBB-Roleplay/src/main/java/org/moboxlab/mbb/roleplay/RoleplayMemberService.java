package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Plugin;
import org.moboxlab.moboxbot.API.Storage.StorageService;

import java.util.List;

/**
 * 群员个人记忆
 *
 * 每个 (群号, QQ) 一条，记录角色对这个群员的认知：
 * 应该怎么称呼他、他喜欢什么、不喜欢什么、应该怎么和他相处。
 * 由情绪分析那一轮 AI 调用顺手产出，不额外增加调用次数。
 */
public class RoleplayMemberService {
    private static final String TABLE = "plugin_mbb_roleplay_member";

    private final Plugin plugin;
    private volatile RoleplayConfig config;

    public RoleplayMemberService(Plugin plugin,RoleplayConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void init() {
        storage().update("CREATE TABLE IF NOT EXISTS `"+TABLE+"` ("
                + "`ID` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`groupID` INTEGER NOT NULL DEFAULT 0,"
                + "`userID` INTEGER NOT NULL DEFAULT 0,"
                + "`userName` TEXT NOT NULL DEFAULT '',"
                + "`alias` TEXT NOT NULL DEFAULT '',"
                + "`likes` TEXT NOT NULL DEFAULT '',"
                + "`dislikes` TEXT NOT NULL DEFAULT '',"
                + "`notes` TEXT NOT NULL DEFAULT '',"
                + "`createTime` INTEGER NOT NULL DEFAULT 0,"
                + "`updateTime` INTEGER NOT NULL DEFAULT 0"
                + ")");
        storage().update("CREATE UNIQUE INDEX IF NOT EXISTS `idx_plugin_mbb_roleplay_member_key` "
                + "ON `"+TABLE+"` (`groupID`,`userID`)");
    }

    public void reload(RoleplayConfig config) {
        this.config = config;
    }

    public JSONObject profile(long groupID,long userID) {
        if (groupID <= 0 || userID <= 0) return null;
        JSONObject row = storage().queryOne("SELECT * FROM `"+TABLE
                +"` WHERE `groupID`=? AND `userID`=?",groupID,userID);
        if (row == null) return null;
        JSONObject item = new JSONObject(true);
        item.put("userID",row.getLongValue("userID"));
        item.put("userName",row.getString("userName"));
        item.put("alias",row.getString("alias"));
        item.put("likes",row.getString("likes"));
        item.put("dislikes",row.getString("dislikes"));
        item.put("notes",row.getString("notes"));
        item.put("updateTime",row.getLongValue("updateTime"));
        return item;
    }

    /**
     * 用 AI 返回的 member 字段更新档案，只覆盖非空字段，空的保持原样。
     * 返回 true 表示有字段真的变了。
     */
    public synchronized boolean applyAi(long groupID,long userID,String userName,JSONObject member) {
        if (!config.memberMemoryEnable) return false;
        if (groupID <= 0 || userID <= 0 || member == null || member.isEmpty()) return false;
        String alias = limit(member.getString("alias"));
        String likes = limit(member.getString("likes"));
        String dislikes = limit(member.getString("dislikes"));
        String notes = limit(member.getString("notes"));
        if (alias.isEmpty() && likes.isEmpty() && dislikes.isEmpty() && notes.isEmpty()) return false;

        JSONObject existing = profile(groupID,userID);
        long now = System.currentTimeMillis();
        String mergedAlias = alias.isEmpty() ? field(existing,"alias") : alias;
        String mergedLikes = likes.isEmpty() ? field(existing,"likes") : likes;
        String mergedDislikes = dislikes.isEmpty() ? field(existing,"dislikes") : dislikes;
        String mergedNotes = notes.isEmpty() ? field(existing,"notes") : notes;
        String name = safe(userName);
        if (name.isEmpty()) name = field(existing,"userName");

        boolean changed = !alias.isEmpty() || !likes.isEmpty() || !dislikes.isEmpty() || !notes.isEmpty();
        if (existing == null) {
            storage().insert("INSERT INTO `"+TABLE+"` "
                            + "(`groupID`,`userID`,`userName`,`alias`,`likes`,`dislikes`,`notes`,"
                            + "`createTime`,`updateTime`) VALUES (?,?,?,?,?,?,?,?,?)",
                    groupID,userID,name,mergedAlias,mergedLikes,mergedDislikes,mergedNotes,now,now);
        } else {
            storage().update("UPDATE `"+TABLE+"` SET `userName`=?,`alias`=?,`likes`=?,`dislikes`=?,"
                            + "`notes`=?,`updateTime`=? WHERE `groupID`=? AND `userID`=?",
                    name,mergedAlias,mergedLikes,mergedDislikes,mergedNotes,now,groupID,userID);
        }
        if (!changed) return false;
        plugin.getLogger().sendInfo("[群员记忆] 群"+groupID+" 用户"+userID+" "+shortText(name,20)
                +" 称呼="+shortText(mergedAlias,20)
                +" 喜欢="+shortText(mergedLikes,24)
                +" 不喜欢="+shortText(mergedDislikes,24)
                +" 相处="+shortText(mergedNotes,30));
        return true;
    }

    /**
     * 注入给模型的群员印象，没有档案时返回空串
     */
    public String promptText(long groupID,long userID) {
        if (!config.memberMemoryEnable) return "";
        JSONObject item = profile(groupID,userID);
        if (item == null) return "";
        StringBuilder builder = new StringBuilder();
        appendLine(builder,"称呼",field(item,"alias"));
        appendLine(builder,"喜欢",field(item,"likes"));
        appendLine(builder,"不喜欢",field(item,"dislikes"));
        appendLine(builder,"相处方式",field(item,"notes"));
        if (builder.length() == 0) return "";
        return "你对这个群员的印象：\n"+builder;
    }

    public JSONArray exportAll() {
        JSONArray result = new JSONArray();
        List<JSONObject> rows = storage().query("SELECT * FROM `"+TABLE
                +"` ORDER BY `groupID` ASC,`userID` ASC");
        if (rows == null) return result;
        for (JSONObject row : rows) {
            JSONObject item = new JSONObject(true);
            item.put("groupID",row.getLongValue("groupID"));
            item.put("userID",row.getLongValue("userID"));
            item.put("userName",row.getString("userName"));
            item.put("alias",row.getString("alias"));
            item.put("likes",row.getString("likes"));
            item.put("dislikes",row.getString("dislikes"));
            item.put("notes",row.getString("notes"));
            item.put("updateTime",row.getLongValue("updateTime"));
            result.add(item);
        }
        return result;
    }

    public boolean remove(long groupID,long userID) {
        int changed = storage().update("DELETE FROM `"+TABLE
                +"` WHERE `groupID`=? AND `userID`=?",groupID,userID);
        return changed > 0;
    }

    public void restoreAll(JSONArray entries) {
        storage().update("DELETE FROM `"+TABLE+"`");
        if (entries == null) return;
        long now = System.currentTimeMillis();
        for (Object object : entries) {
            if (!(object instanceof JSONObject)) continue;
            JSONObject item = (JSONObject) object;
            long groupID = item.getLongValue("groupID");
            long userID = item.getLongValue("userID");
            if (groupID <= 0 || userID <= 0) continue;
            storage().insert("INSERT OR REPLACE INTO `"+TABLE+"` "
                            + "(`groupID`,`userID`,`userName`,`alias`,`likes`,`dislikes`,`notes`,"
                            + "`createTime`,`updateTime`) VALUES (?,?,?,?,?,?,?,?,?)",
                    groupID,userID,safe(item.getString("userName")),safe(item.getString("alias")),
                    safe(item.getString("likes")),safe(item.getString("dislikes")),
                    safe(item.getString("notes")),
                    item.getLongValue("updateTime") <= 0 ? now : item.getLongValue("updateTime"),
                    item.getLongValue("updateTime") <= 0 ? now : item.getLongValue("updateTime"));
        }
    }

    public int count(long groupID) {
        JSONObject row = storage().queryOne("SELECT COUNT(*) AS `count` FROM `"+TABLE
                +"` WHERE `groupID`=?",groupID);
        return row == null ? 0 : row.getIntValue("count");
    }

    private void appendLine(StringBuilder builder,String label,String value) {
        if (value == null || value.trim().isEmpty()) return;
        builder.append("- ").append(label).append("：").append(value.trim()).append("\n");
    }

    private String limit(String value) {
        String text = safe(value).replaceAll("\\s+"," ").trim();
        if (text.isEmpty()) return "";
        int max = Math.max(20,config.memberFieldMaxChars);
        return text.length() > max ? text.substring(0,max) : text;
    }

    private String field(JSONObject item,String key) {
        if (item == null) return "";
        String value = item.getString(key);
        return value == null ? "" : value.trim();
    }

    private String shortText(String value,int maxChars) {
        String text = safe(value);
        if (text.length() <= maxChars) return text;
        return text.substring(0,maxChars)+"...";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private StorageService storage() {
        return plugin.getServer().getStorage();
    }
}
