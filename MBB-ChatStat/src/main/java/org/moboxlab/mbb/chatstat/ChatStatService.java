package org.moboxlab.mbb.chatstat;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Event.GroupMessageEvent;
import org.moboxlab.moboxbot.API.Plugin;
import org.moboxlab.moboxbot.API.Storage.StorageService;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 群聊内容统计
 */
public class ChatStatService {
    private static final String TABLE = "plugin_mbb_chatstat_messages";
    private static final long DAY_MILLIS = 86400000L;
    private final Plugin plugin;

    public ChatStatService(Plugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        storage().update("CREATE TABLE IF NOT EXISTS `"+TABLE+"` ("
                + "`ID` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`messageID` INTEGER NOT NULL DEFAULT 0,"
                + "`groupID` INTEGER NOT NULL DEFAULT 0,"
                + "`groupName` TEXT NOT NULL DEFAULT '',"
                + "`userID` INTEGER NOT NULL DEFAULT 0,"
                + "`userName` TEXT NOT NULL DEFAULT '',"
                + "`messageTime` INTEGER NOT NULL DEFAULT 0,"
                + "`statDate` TEXT NOT NULL DEFAULT '',"
                + "`content` TEXT NOT NULL DEFAULT '',"
                + "`hasImage` INTEGER NOT NULL DEFAULT 0,"
                + "`hasAt` INTEGER NOT NULL DEFAULT 0,"
                + "`hasFace` INTEGER NOT NULL DEFAULT 0,"
                + "`hasReply` INTEGER NOT NULL DEFAULT 0,"
                + "`updateTime` INTEGER NOT NULL DEFAULT 0"
                + ")");
        storage().update("CREATE INDEX IF NOT EXISTS `idx_plugin_mbb_chatstat_group_time` ON `"+TABLE+"` (`groupID`,`messageTime`)");
        storage().update("CREATE INDEX IF NOT EXISTS `idx_plugin_mbb_chatstat_user_time` ON `"+TABLE+"` (`userID`,`messageTime`)");
    }

    public void record(GroupMessageEvent event) {
        if (event == null || !plugin.getConfig().getBoolean("enable",true)) return;
        long selfID = event.getRaw().getLongValue("self_id");
        if (selfID > 0 && selfID == event.getUserID()) return;
        String content = extractContent(event.getMessage());
        int maxLength = plugin.getConfig().getInt("maxContentLength",500);
        if (maxLength < 50) maxLength = 50;
        if (content.length() > maxLength) content = content.substring(0,maxLength)+"...";
        long messageTime = event.getRaw().getLongValue("time") * 1000L;
        if (messageTime <= 0) messageTime = System.currentTimeMillis();
        JSONObject sender = event.getSender();
        String userName = sender == null ? "" : sender.getString("card");
        if (userName == null || userName.trim().isEmpty()) userName = sender == null ? "" : sender.getString("nickname");
        if (userName == null) userName = "";
        storage().insert("INSERT INTO `"+TABLE+"` (`messageID`,`groupID`,`groupName`,`userID`,`userName`,`messageTime`,`statDate`,`content`,`hasImage`,`hasAt`,`hasFace`,`hasReply`,`updateTime`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                event.getMessageID(),event.getGroupID(),safe(event.getRaw().getString("group_name")),
                event.getUserID(),userName,messageTime,formatDate(messageTime),content,
                hasType(event.getMessage(),"image"),hasType(event.getMessage(),"at"),
                hasType(event.getMessage(),"face"),hasType(event.getMessage(),"reply"),
                System.currentTimeMillis());
    }

    public JSONObject groupStats(long groupID,int days) {
        long startTime = startTime(days);
        JSONObject result = new JSONObject(true);
        result.put("scope","group");
        result.put("groupID",groupID);
        result.put("groupName",groupName(groupID));
        result.put("days",days);
        result.put("summary",summary("`groupID`=?",groupID,startTime));
        result.put("top",queryTop("userID","userName","`groupID`=?",groupID,startTime));
        result.put("recent",queryRecent("`groupID`=?",groupID,startTime));
        return result;
    }

    public JSONObject userStats(long userID,int days) {
        long startTime = startTime(days);
        JSONObject result = new JSONObject(true);
        result.put("scope","user");
        result.put("userID",userID);
        result.put("days",days);
        result.put("summary",summary("`userID`=?",userID,startTime));
        result.put("top",queryTop("groupID","groupName","`userID`=?",userID,startTime));
        result.put("recent",queryRecent("`userID`=?",userID,startTime));
        return result;
    }

    public JSONArray aiMessages(long groupID,long userID,int days,int limit) {
        if (groupID > 0) return queryRecent("`groupID`=?",groupID,startTime(days),limit);
        return queryRecent("`userID`=?",userID,startTime(days),limit);
    }

    private JSONObject summary(String where,long id,long startTime) {
        List<JSONObject> rows = storage().query(
                "SELECT COUNT(*) AS `messages`,COUNT(DISTINCT `userID`) AS `users`,"
                        + "COUNT(DISTINCT `groupID`) AS `groups`,SUM(`hasImage`) AS `images`,"
                        + "SUM(`hasAt`) AS `ats`,SUM(`hasFace`) AS `faces` "
                        + "FROM `"+TABLE+"` WHERE "+where+" AND `messageTime`>=?",id,startTime);
        JSONObject row = rows == null || rows.isEmpty() ? null : rows.get(0);
        JSONObject result = new JSONObject(true);
        result.put("messages",value(row,"messages"));
        result.put("users",value(row,"users"));
        result.put("groups",value(row,"groups"));
        result.put("images",value(row,"images"));
        result.put("ats",value(row,"ats"));
        result.put("faces",value(row,"faces"));
        return result;
    }

    private JSONArray queryTop(String idColumn,String nameColumn,String where,long id,long startTime) {
        int limit = plugin.getConfig().getInt("topCount",10);
        if (limit < 1) limit = 1;
        if (limit > 20) limit = 20;
        JSONArray result = new JSONArray();
        List<JSONObject> rows = storage().query(
                "SELECT `"+idColumn+"` AS `id`,MAX(`"+nameColumn+"`) AS `name`,COUNT(*) AS `messages` "
                        + "FROM `"+TABLE+"` WHERE "+where+" AND `messageTime`>=? "
                        + "GROUP BY `"+idColumn+"` ORDER BY `messages` DESC LIMIT ?",
                id,startTime,limit);
        if (rows == null) return result;
        for (JSONObject row : rows) {
            JSONObject item = new JSONObject(true);
            item.put("id",row.getLongValue("id"));
            item.put("name",safe(row.getString("name")));
            item.put("messages",row.getLongValue("messages"));
            result.add(item);
        }
        return result;
    }

    private JSONArray queryRecent(String where,long id,long startTime) {
        int limit = plugin.getConfig().getInt("recentMessageCount",10);
        if (limit < 1) limit = 1;
        if (limit > 30) limit = 30;
        return queryRecent(where,id,startTime,limit);
    }

    private JSONArray queryRecent(String where,long id,long startTime,int limit) {
        if (limit < 1) limit = 1;
        if (limit > 100) limit = 100;
        JSONArray result = new JSONArray();
        List<JSONObject> rows = storage().query(
                "SELECT `groupID`,`groupName`,`userID`,`userName`,`messageTime`,`content` "
                        + "FROM `"+TABLE+"` WHERE "+where+" AND `messageTime`>=? "
                        + "ORDER BY `messageTime` DESC LIMIT ?",
                id,startTime,limit);
        if (rows == null) return result;
        for (JSONObject row : rows) {
            JSONObject item = new JSONObject(true);
            item.put("groupID",row.getLongValue("groupID"));
            item.put("groupName",safe(row.getString("groupName")));
            item.put("userID",row.getLongValue("userID"));
            item.put("userName",safe(row.getString("userName")));
            item.put("messageTime",row.getLongValue("messageTime"));
            item.put("content",safe(row.getString("content")));
            result.add(item);
        }
        return result;
    }

    private String groupName(long groupID) {
        JSONObject row = storage().queryOne("SELECT MAX(`groupName`) AS `groupName` FROM `"+TABLE+"` WHERE `groupID`=?",groupID);
        return row == null ? "" : safe(row.getString("groupName"));
    }

    private long startTime(int days) {
        if (days <= 1) {
            Calendar calendar = Calendar.getInstance(Locale.CHINA);
            calendar.set(Calendar.HOUR_OF_DAY,0);
            calendar.set(Calendar.MINUTE,0);
            calendar.set(Calendar.SECOND,0);
            calendar.set(Calendar.MILLISECOND,0);
            return calendar.getTimeInMillis();
        }
        return System.currentTimeMillis() - (days - 1L) * DAY_MILLIS;
    }

    private int hasType(JSONArray message,String type) {
        if (message == null) return 0;
        for (int i = 0; i < message.size(); i++) {
            JSONObject segment = message.getJSONObject(i);
            if (segment != null && type.equals(segment.getString("type"))) return 1;
        }
        return 0;
    }

    private String extractContent(JSONArray message) {
        if (message == null) return "";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < message.size(); i++) {
            JSONObject segment = message.getJSONObject(i);
            if (segment == null) continue;
            String type = segment.getString("type");
            JSONObject data = segment.getJSONObject("data");
            if ("text".equals(type)) {
                builder.append(data == null ? "" : safe(data.getString("text")));
            } else if ("image".equals(type)) {
                builder.append("[图片]");
            } else if ("at".equals(type)) {
                builder.append("@").append(data == null ? "" : safe(data.getString("qq")));
            } else if ("face".equals(type)) {
                builder.append("[表情]");
            } else if ("reply".equals(type)) {
                builder.append("[回复]");
            } else if ("record".equals(type)) {
                builder.append("[语音]");
            } else if ("video".equals(type)) {
                builder.append("[视频]");
            } else if ("json".equals(type)) {
                builder.append("[卡片]");
            } else {
                builder.append("[").append(type == null ? "消息" : type).append("]");
            }
        }
        return builder.toString().trim();
    }

    private StorageService storage() {
        return plugin.getServer().getStorage();
    }

    private long value(JSONObject row,String key) {
        return row == null ? 0L : row.getLongValue(key);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String formatDate(long time) {
        return new SimpleDateFormat("yyyy-MM-dd",Locale.CHINA).format(new Date(time));
    }
}
