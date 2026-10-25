package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.List;

/**
 * 群级结构化上下文压缩。
 *
 * 短期摘要继续负责跨群背景；这里只保存当前群可接续的话题、进展和约定。
 */
public class RoleplayContextCompactor {
    private final RoleplayService service;

    public RoleplayContextCompactor(RoleplayService service) {
        this.service = service;
    }

    public boolean saveFromModel(long groupID,JSONObject parsed,long lastMessageID) {
        if (groupID <= 0 || !service.config().structuredContextEnable) return false;
        JSONObject raw = parsed == null ? null : parsed.getJSONObject("context");
        RoleplayStructuredContext context = RoleplayStructuredContext.parse(raw,
                service.config().structuredContextItemMaxChars);
        if (context.isEmpty()) return false;
        return save(groupID,context,lastMessageID,System.currentTimeMillis());
    }

    /**
     * 旧版本只有群级短摘要，没有结构化表。升级时把旧群摘要转成 background，
     * 不读取 groupID=0 的跨群摘要，避免把别的群内容带进当前群。
     */
    public int recoverLegacySummaries() {
        if (!service.config().structuredContextEnable) return 0;
        List<JSONObject> rows = service.storage().query(
                "SELECT s.`groupID`,s.`shortSummary`,s.`lastMemoryID`,s.`updateTime` "
                        + "FROM `"+RoleplayService.STATE_TABLE+"` s "
                        + "LEFT JOIN `"+RoleplayService.CONTEXT_TABLE+"` c ON c.`groupID`=s.`groupID` "
                        + "WHERE s.`groupID`>0 AND c.`ID` IS NULL "
                        + "AND TRIM(s.`shortSummary`)<>'' ORDER BY s.`groupID` ASC LIMIT ?",
                500);
        if (rows == null || rows.isEmpty()) return 0;
        int saved = 0;
        for (JSONObject row : rows) {
            RoleplayStructuredContext context = RoleplayStructuredContext.fallback(
                    row.getString("shortSummary"),service.config().structuredContextItemMaxChars);
            if (context.isEmpty()) continue;
            long updateTime = row.getLongValue("updateTime");
            if (save(row.getLongValue("groupID"),context,row.getLongValue("lastMemoryID"),
                    updateTime > 0 ? updateTime : System.currentTimeMillis())) {
                saved++;
            }
        }
        if (saved > 0) {
            service.plugin().getLogger().sendInfo("[记忆] 已从旧群短期摘要恢复结构化上下文 "
                    +saved+" 条");
        }
        return saved;
    }

    private boolean save(long groupID,RoleplayStructuredContext context,long lastMessageID,long updateTime) {
        String json = JSONObject.toJSONString(context.toJson());
        JSONObject exists = service.storage().queryOne(
                "SELECT `ID` FROM `"+RoleplayService.CONTEXT_TABLE+"` WHERE `groupID`=?",groupID);
        if (exists == null) {
            service.storage().insert("INSERT INTO `"+RoleplayService.CONTEXT_TABLE+"` "
                            + "(`groupID`,`stateJson`,`lastMessageID`,`updateTime`) VALUES (?,?,?,?)",
                    groupID,json,lastMessageID,updateTime);
        } else {
            service.storage().update("UPDATE `"+RoleplayService.CONTEXT_TABLE+"` SET "
                            + "`stateJson`=?,`lastMessageID`=?,`updateTime`=? WHERE `groupID`=?",
                    json,lastMessageID,updateTime,groupID);
        }
        return true;
    }

    public String promptText(long groupID) {
        if (!service.config().structuredContextEnable) return "";
        RoleplayStructuredContext context = load(groupID);
        if (context.isEmpty()) return "";
        String rendered = context.render(service.config().structuredContextMaxChars);
        if (rendered.isEmpty()) return "";
        return "当前群结构化上下文（用于接续对话，不要逐条复述；已解决的事项不要继续当成待办）：\n"
                +rendered;
    }

    public String modelText(long groupID) {
        if (groupID <= 0) return "暂无";
        RoleplayStructuredContext context = load(groupID);
        String rendered = context.render(service.config().structuredContextMaxChars);
        return rendered.isEmpty() ? "暂无" : rendered;
    }

    public RoleplayStructuredContext load(long groupID) {
        if (groupID <= 0) return RoleplayStructuredContext.fromJson("",0);
        JSONObject row = service.storage().queryOne(
                "SELECT `stateJson` FROM `"+RoleplayService.CONTEXT_TABLE+"` WHERE `groupID`=?",
                groupID);
        return RoleplayStructuredContext.fromJson(
                row == null ? "" : row.getString("stateJson"),
                service.config().structuredContextItemMaxChars);
    }

    public JSONArray exportAll() {
        JSONArray result = new JSONArray();
        List<JSONObject> rows = service.storage().query(
                "SELECT `groupID`,`stateJson`,`lastMessageID`,`updateTime` FROM `"
                        +RoleplayService.CONTEXT_TABLE+"` ORDER BY `groupID` ASC");
        if (rows == null) return result;
        for (JSONObject row : rows) {
            JSONObject item = new JSONObject(true);
            item.put("groupID",row.getLongValue("groupID"));
            item.put("stateJson",row.getString("stateJson"));
            item.put("lastMessageID",row.getLongValue("lastMessageID"));
            item.put("updateTime",row.getLongValue("updateTime"));
            result.add(item);
        }
        return result;
    }

    public int restoreAll(JSONArray items) {
        if (items == null) return 0;
        int saved = 0;
        for (Object object : items) {
            if (!(object instanceof JSONObject)) continue;
            JSONObject item = (JSONObject)object;
            long groupID = item.getLongValue("groupID");
            if (groupID <= 0) continue;
            RoleplayStructuredContext context = RoleplayStructuredContext.fromJson(
                    item.getString("stateJson"),service.config().structuredContextItemMaxChars);
            if (context.isEmpty()) continue;
            service.storage().insert("INSERT OR REPLACE INTO `"+RoleplayService.CONTEXT_TABLE+"` "
                            + "(`groupID`,`stateJson`,`lastMessageID`,`updateTime`) VALUES (?,?,?,?)",
                    groupID,JSONObject.toJSONString(context.toJson()),
                    item.getLongValue("lastMessageID"),item.getLongValue("updateTime"));
            saved++;
        }
        return saved;
    }

    public void delete(long groupID) {
        if (groupID <= 0) return;
        service.storage().update("DELETE FROM `"+RoleplayService.CONTEXT_TABLE+"` WHERE `groupID`=?",
                groupID);
    }

    public void clearAll() {
        service.storage().update("DELETE FROM `"+RoleplayService.CONTEXT_TABLE+"`");
    }
}
