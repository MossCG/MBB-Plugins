package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.List;
import java.util.UUID;

/**
 * 角色群开关、状态与记忆概览
 *
 * 负责群启用状态、上下文 token、状态卡片数据、记忆统计和清空记忆。
 */
public class RoleplayGroupStatusService {
    private final RoleplayService service;

    public RoleplayGroupStatusService(RoleplayService service) {
        this.service = service;
    }

    public JSONObject status(long groupID) {
        JSONObject result = new JSONObject(true);
        result.put("status",true);
        result.put("groupID",groupID);
        result.put("role",service.persona().name);
        result.put("groupEnabled",groupID <= 0 || isGroupEnabled(groupID));
        result.put("shortSummary",service.shortSummary(groupID));
        result.put("memoryCount",service.memoryCount(groupID));
        result.put("mood",service.emotionService().moodStats(groupID,1).getJSONObject("mood"));
        return result;
    }

    public boolean isGroupEnabled(long groupID) {
        JSONObject row = service.storage().queryOne("SELECT `enabled` FROM `"+RoleplayService.GROUP_TABLE+"` WHERE `groupID`=?",groupID);
        return row != null && row.getIntValue("enabled") == 1;
    }

    String contextToken(long groupID) {
        JSONObject row = service.storage().queryOne(
                "SELECT `contextToken` FROM `"+RoleplayService.GROUP_TABLE+"` WHERE `groupID`=?",groupID);
        if (row != null && row.getString("contextToken") != null
                && !row.getString("contextToken").trim().isEmpty()) {
            return row.getString("contextToken").trim();
        }
        String token = newContextToken();
        if (row == null) {
            service.storage().insert("INSERT INTO `"+RoleplayService.GROUP_TABLE+"` "
                            + "(`groupID`,`enabled`,`contextToken`,`updateTime`) VALUES (?,?,?,?)",
                    groupID,0,token,System.currentTimeMillis());
        } else {
            service.storage().update("UPDATE `"+RoleplayService.GROUP_TABLE+"` SET `contextToken`=?,`updateTime`=? WHERE `groupID`=?",
                    token,System.currentTimeMillis(),groupID);
        }
        return token;
    }

    void rotateContextToken(long groupID) {
        String token = newContextToken();
        int rows = service.storage().update("UPDATE `"+RoleplayService.GROUP_TABLE+"` SET `contextToken`=?,`updateTime`=? WHERE `groupID`=?",
                token,System.currentTimeMillis(),groupID);
        if (rows <= 0) {
            service.storage().insert("INSERT INTO `"+RoleplayService.GROUP_TABLE+"` "
                            + "(`groupID`,`enabled`,`contextToken`,`updateTime`) VALUES (?,?,?,?)",
                    groupID,0,token,System.currentTimeMillis());
        }
    }

    String newContextToken() {
        return UUID.randomUUID().toString().replace("-","");
    }

    public void setGroupEnabled(long groupID,boolean enabled) {
        JSONObject row = service.storage().queryOne("SELECT `ID` FROM `"+RoleplayService.GROUP_TABLE+"` WHERE `groupID`=?",groupID);
        long now = System.currentTimeMillis();
        if (row == null) {
            service.storage().insert("INSERT INTO `"+RoleplayService.GROUP_TABLE+"` (`groupID`,`enabled`,`updateTime`) VALUES (?,?,?)",
                    groupID,enabled ? 1 : 0,now);
        } else {
            service.storage().update("UPDATE `"+RoleplayService.GROUP_TABLE+"` SET `enabled`=?,`updateTime`=? WHERE `groupID`=?",
                    enabled ? 1 : 0,now,groupID);
        }
    }

    public JSONArray listGroups() {
        JSONArray result = new JSONArray();
        List<JSONObject> rows = service.storage().query("SELECT `groupID` FROM `"+RoleplayService.GROUP_TABLE+"` WHERE `enabled`=1 ORDER BY `groupID` ASC");
        if (rows == null) return result;
        for (JSONObject row : rows) result.add(row.getLongValue("groupID"));
        return result;
    }

    public JSONObject memoryStats(long groupID) {
        JSONObject result = status(groupID);
        result.put("shortSummary",service.shortSummary(groupID));
        result.put("structuredContext",service.contextCompactor().load(groupID).toJson());
        result.put("memories",service.longMemories(groupID,false));
        return result;
    }

    public void clearMemory(long groupID) {
        service.storage().update("DELETE FROM `"+RoleplayService.MEMORY_TABLE+"` WHERE `groupID`=?",groupID);
        service.storage().update("DELETE FROM `"+RoleplayService.STATE_TABLE+"` WHERE `groupID`=?",groupID);
        service.contextCompactor().delete(groupID);
        service.storage().update("DELETE FROM `"+RoleplayService.MSG_TABLE+"` WHERE `groupID`=?",groupID);
        rotateContextToken(groupID);
        service.memoryUpdatingMap.remove(groupID);
        service.pendingMemoryMap.remove(groupID);
        service.rateLimitService().resetGroup(groupID);
        service.conversationStateMap.remove(groupID);

    }
}
