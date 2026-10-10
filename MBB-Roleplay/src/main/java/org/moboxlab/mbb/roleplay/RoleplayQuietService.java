package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Plugin;
import org.moboxlab.moboxbot.API.Storage.StorageService;

import java.util.List;

/**
 * 免打扰名单
 *
 * 名单内的群员不主动参与：角色只在对方主动叫到自己的时候才回复，
 * 其他情况一律保持沉默，其他群员不受影响。规则比黑名单轻。
 */
public class RoleplayQuietService {
    private static final String TABLE = "plugin_mbb_roleplay_quiet";
    /** 按群保存是否启用，未记录的群按默认值处理 */
    private static final String ENABLE_KEY = "roleplay-quiet-enable";

    private final Plugin plugin;

    public RoleplayQuietService(Plugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        storage().update("CREATE TABLE IF NOT EXISTS `"+TABLE+"` ("
                + "`ID` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`groupID` INTEGER NOT NULL DEFAULT 0,"
                + "`userID` INTEGER NOT NULL DEFAULT 0,"
                + "`operatorID` INTEGER NOT NULL DEFAULT 0,"
                + "`createTime` INTEGER NOT NULL DEFAULT 0,"
                + "`updateTime` INTEGER NOT NULL DEFAULT 0"
                + ")");
        storage().update("CREATE UNIQUE INDEX IF NOT EXISTS `idx_plugin_mbb_roleplay_quiet_key` "
                + "ON `"+TABLE+"` (`groupID`,`userID`)");
    }

    /**
     * 当前群是否启用免打扰名单，未记录过时返回默认值
     */
    public boolean isEnabled(long groupID) {
        if (groupID <= 0) return false;
        String value = storage().get(plugin,ENABLE_KEY+"."+groupID);
        if (value == null || value.trim().isEmpty()) return defaultEnabled();
        return "true".equalsIgnoreCase(value.trim());
    }

    public boolean setEnabled(long groupID,boolean enabled) {
        if (groupID <= 0) return false;
        storage().set(plugin,ENABLE_KEY+"."+groupID,enabled ? "true" : "false");
        return true;
    }

    /**
     * 某个群员在当前群是否处于免打扰状态
     */
    public boolean contains(long groupID,long userID) {
        if (groupID <= 0 || userID <= 0) return false;
        JSONObject row = storage().queryOne("SELECT `ID` FROM `"+TABLE
                +"` WHERE `groupID`=? AND `userID`=?",groupID,userID);
        return row != null && row.getLongValue("ID") > 0;
    }

    /**
     * 是否应该对这条消息保持沉默
     * 名单未启用、群员不在名单、或对方主动叫到角色时都返回 false
     */
    public boolean shouldStayQuiet(long groupID,long userID,boolean addressedToSelf) {
        if (addressedToSelf) return false;
        if (!isEnabled(groupID)) return false;
        return contains(groupID,userID);
    }

    public boolean add(long groupID,long userID,long operatorID) {
        if (groupID <= 0 || userID <= 0) return false;
        long now = System.currentTimeMillis();
        storage().insert("INSERT OR REPLACE INTO `"+TABLE+"` "
                        + "(`groupID`,`userID`,`operatorID`,`createTime`,`updateTime`) "
                        + "VALUES (?,?,?,?,?)",
                groupID,userID,operatorID,now,now);
        plugin.getLogger().sendInfo("[免打扰] 群"+groupID+" 用户"+userID+" 已加入名单"
                +"（操作者 "+operatorID+"）");
        return true;
    }

    public boolean remove(long groupID,long userID) {
        if (groupID <= 0 || userID <= 0) return false;
        int changed = storage().update("DELETE FROM `"+TABLE
                +"` WHERE `groupID`=? AND `userID`=?",groupID,userID);
        return changed > 0;
    }

    public void clear(long groupID) {
        if (groupID <= 0) return;
        storage().update("DELETE FROM `"+TABLE+"` WHERE `groupID`=?",groupID);
    }

    public JSONArray list(long groupID) {
        JSONArray result = new JSONArray();
        if (groupID <= 0) return result;
        List<JSONObject> rows = storage().query("SELECT * FROM `"+TABLE
                +"` WHERE `groupID`=? ORDER BY `updateTime` DESC,`ID` DESC",groupID);
        if (rows == null) return result;
        for (JSONObject row : rows) {
            JSONObject item = new JSONObject(true);
            item.put("userID",row.getLongValue("userID"));
            item.put("operatorID",row.getLongValue("operatorID"));
            item.put("createTime",row.getLongValue("createTime"));
            item.put("updateTime",row.getLongValue("updateTime"));
            result.add(item);
        }
        return result;
    }

    public int count(long groupID) {
        if (groupID <= 0) return 0;
        JSONObject row = storage().queryOne("SELECT COUNT(*) AS `count` FROM `"+TABLE
                +"` WHERE `groupID`=?",groupID);
        return row == null ? 0 : row.getIntValue("count");
    }

    private boolean defaultEnabled() {
        return plugin.getConfig().getBoolean("quietReplyEnable",true);
    }

    private StorageService storage() {
        return plugin.getServer().getStorage();
    }
}
