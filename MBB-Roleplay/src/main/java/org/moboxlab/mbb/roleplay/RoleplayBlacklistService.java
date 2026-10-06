package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Plugin;
import org.moboxlab.moboxbot.API.Storage.StorageService;

import java.util.List;

/**
 * 按群用户黑名单
 *
 * 黑名单用户的消息不会进入上下文，也不会触发角色回复。
 */
public class RoleplayBlacklistService {
    private static final String TABLE = "plugin_mbb_roleplay_blacklist";

    private final Plugin plugin;

    public RoleplayBlacklistService(Plugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        storage().update("CREATE TABLE IF NOT EXISTS `"+TABLE+"` ("
                + "`ID` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`groupID` INTEGER NOT NULL DEFAULT 0,"
                + "`userID` INTEGER NOT NULL DEFAULT 0,"
                + "`reason` TEXT NOT NULL DEFAULT '',"
                + "`operatorID` INTEGER NOT NULL DEFAULT 0,"
                + "`createTime` INTEGER NOT NULL DEFAULT 0,"
                + "`updateTime` INTEGER NOT NULL DEFAULT 0"
                + ")");
        storage().update("CREATE UNIQUE INDEX IF NOT EXISTS `idx_plugin_mbb_roleplay_blacklist_key` "
                + "ON `"+TABLE+"` (`groupID`,`userID`)");
    }

    public boolean contains(long groupID,long userID) {
        if (groupID <= 0 || userID <= 0) return false;
        JSONObject row = storage().queryOne("SELECT `ID` FROM `"+TABLE
                +"` WHERE `groupID`=? AND `userID`=?",groupID,userID);
        return row != null && row.getLongValue("ID") > 0;
    }

    public void add(long groupID,long userID,String reason,long operatorID) {
        if (groupID <= 0 || userID <= 0) return;
        long now = System.currentTimeMillis();
        storage().insert("INSERT OR REPLACE INTO `"+TABLE+"` "
                        + "(`groupID`,`userID`,`reason`,`operatorID`,`createTime`,`updateTime`) "
                        + "VALUES (?,?,?,?,?,?)",
                groupID,userID,safe(reason),operatorID,now,now);
        plugin.getLogger().sendInfo("[黑名单] 群"+groupID+" 已拉黑用户"+userID
                +(safe(reason).isEmpty() ? "" : " 原因="+safe(reason)));
    }

    public boolean remove(long groupID,long userID) {
        int changed = storage().update("DELETE FROM `"+TABLE
                +"` WHERE `groupID`=? AND `userID`=?",groupID,userID);
        return changed > 0;
    }

    public void clear(long groupID) {
        storage().update("DELETE FROM `"+TABLE+"` WHERE `groupID`=?",groupID);
    }

    public JSONArray list(long groupID) {
        JSONArray result = new JSONArray();
        List<JSONObject> rows = storage().query("SELECT * FROM `"+TABLE
                +"` WHERE `groupID`=? ORDER BY `updateTime` DESC,`ID` DESC",groupID);
        if (rows == null) return result;
        for (JSONObject row : rows) {
            JSONObject item = new JSONObject(true);
            item.put("userID",row.getLongValue("userID"));
            item.put("reason",row.getString("reason"));
            item.put("operatorID",row.getLongValue("operatorID"));
            item.put("createTime",row.getLongValue("createTime"));
            item.put("updateTime",row.getLongValue("updateTime"));
            result.add(item);
        }
        return result;
    }

    public int count(long groupID) {
        JSONObject row = storage().queryOne("SELECT COUNT(*) AS `count` FROM `"+TABLE
                +"` WHERE `groupID`=?",groupID);
        return row == null ? 0 : row.getIntValue("count");
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
            item.put("reason",row.getString("reason"));
            item.put("operatorID",row.getLongValue("operatorID"));
            item.put("createTime",row.getLongValue("createTime"));
            item.put("updateTime",row.getLongValue("updateTime"));
            result.add(item);
        }
        return result;
    }

    public void restoreAll(JSONArray entries) {
        storage().update("DELETE FROM `"+TABLE+"`");
        if (entries == null) return;
        for (Object object : entries) {
            if (!(object instanceof JSONObject)) continue;
            JSONObject item = (JSONObject) object;
            long groupID = item.getLongValue("groupID");
            long userID = item.getLongValue("userID");
            if (groupID <= 0 || userID <= 0) continue;
            long now = System.currentTimeMillis();
            storage().insert("INSERT OR REPLACE INTO `"+TABLE+"` "
                            + "(`groupID`,`userID`,`reason`,`operatorID`,`createTime`,`updateTime`) "
                            + "VALUES (?,?,?,?,?,?)",
                    groupID,userID,safe(item.getString("reason")),
                    item.getLongValue("operatorID"),
                    item.getLongValue("createTime") <= 0 ? now : item.getLongValue("createTime"),
                    item.getLongValue("updateTime") <= 0 ? now : item.getLongValue("updateTime"));
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private StorageService storage() {
        return plugin.getServer().getStorage();
    }
}
