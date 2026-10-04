package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Event.GroupMessageEvent;
import org.moboxlab.moboxbot.API.OneBot.MessageUtil;
import org.moboxlab.moboxbot.API.OneBot.OneBotClient;
import org.moboxlab.moboxbot.API.Plugin;
import org.moboxlab.moboxbot.API.Storage.StorageService;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 角色自然语言定时提醒
 */
public class RoleplayReminderService {
    private static final String TABLE = "plugin_mbb_roleplay_reminders";

    private final Plugin plugin;
    private volatile RoleplayConfig config;

    public RoleplayReminderService(Plugin plugin,RoleplayConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void init() {
        storage().update("CREATE TABLE IF NOT EXISTS `"+TABLE+"` ("
                + "`ID` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`groupID` INTEGER NOT NULL DEFAULT 0,"
                + "`userID` INTEGER NOT NULL DEFAULT 0,"
                + "`userName` TEXT NOT NULL DEFAULT '',"
                + "`task` TEXT NOT NULL DEFAULT '',"
                + "`remindTime` INTEGER NOT NULL DEFAULT 0,"
                + "`createdAt` INTEGER NOT NULL DEFAULT 0,"
                + "`status` TEXT NOT NULL DEFAULT 'pending'"
                + ")");
        storage().update("CREATE INDEX IF NOT EXISTS `idx_plugin_mbb_roleplay_reminder_time` "
                + "ON `"+TABLE+"` (`status`,`remindTime`)");
        restorePending();
    }

    public void reload(RoleplayConfig config) {
        this.config = config;
    }

    public boolean handle(GroupMessageEvent event,String content) {
        if (event == null || !config.reminderEnable) return false;
        RoleplayReminderParser.Result result =
                RoleplayReminderParser.parse(content,config,System.currentTimeMillis());
        if (!result.intent) return false;
        long groupID = event.getGroupID();
        long userID = event.getUserID();
        String userName = senderName(event);
        if (!result.valid) {
            sendAt(groupID,userID,result.error);
            plugin.getLogger().sendWarn("[提醒] 群"+groupID+" 用户"+userID
                    +" 创建失败："+safe(result.error)+"，原文："+shortText(content,120));
            return true;
        }
        long now = System.currentTimeMillis();
        long id = storage().insert("INSERT INTO `"+TABLE+"` "
                        + "(`groupID`,`userID`,`userName`,`task`,`remindTime`,`createdAt`,`status`) "
                        + "VALUES (?,?,?,?,?,?,?)",
                groupID,userID,userName,result.task,result.remindTime,now,"pending");
        schedule(id,groupID,userID,result.task,result.remindTime);
        plugin.getLogger().sendInfo("[提醒] 创建 #"+id+" 群"+groupID+" 用户"+userID
                +" 时间="+formatTime(result.remindTime)+" 内容="+result.task);
        sendAt(groupID,userID,formatTime(result.remindTime)+" 提醒你："+result.task);
        return true;
    }

    private void restorePending() {
        List<JSONObject> rows = storage().query(
                "SELECT `ID`,`groupID`,`userID`,`task`,`remindTime` FROM `"+TABLE+"` "
                        + "WHERE `status`='pending' ORDER BY `remindTime` ASC");
        if (rows == null || rows.isEmpty()) return;
        for (JSONObject row : rows) {
            schedule(row.getLongValue("ID"),row.getLongValue("groupID"),row.getLongValue("userID"),
                    safe(row.getString("task")),row.getLongValue("remindTime"));
        }
        plugin.getLogger().sendInfo("[提醒] 已恢复 "+rows.size()+" 条待触发提醒");
    }

    private void schedule(long id,long groupID,long userID,String task,long remindTime) {
        long delay = Math.max(1L,(remindTime - System.currentTimeMillis() + 999L) / 1000L);
        plugin.getServer().getPluginManager().runTaskLater(plugin,
                () -> trigger(id,groupID,userID,task),delay);
    }

    private void trigger(long id,long groupID,long userID,String task) {
        JSONObject row = storage().queryOne(
                "SELECT `status` FROM `"+TABLE+"` WHERE `ID`=?",id);
        if (row == null || !"pending".equals(row.getString("status"))) return;
        plugin.getLogger().sendInfo("[提醒] 触发 #"+id+" 群"+groupID+" 用户"+userID
                +" 内容="+task);
        String text = triggerText(task);
        OneBotClient client = plugin.getServer().getOneBotClient();
        JSONObject response = client == null ? null
                : client.sendGroupMessage(groupID,MessageUtil.message(
                MessageUtil.at(userID),MessageUtil.text(" "+text)));
        if (response != null && response.getIntValue("retcode") == 0) {
            storage().update("UPDATE `"+TABLE+"` SET `status`='done' WHERE `ID`=?",id);
            plugin.getLogger().sendInfo("[提醒] 发送成功 #"+id+" 群"+groupID+" 用户"+userID);
        } else {
            plugin.getLogger().sendWarn("[提醒] 发送失败 #"+id+" 群"+groupID
                    +" 用户"+userID+"，60 秒后重试");
            schedule(id,groupID,userID,task,System.currentTimeMillis() + 60000L);
        }
    }

    private void sendAt(long groupID,long userID,String text) {
        OneBotClient client = plugin.getServer().getOneBotClient();
        if (client == null) return;
        JSONArray message = MessageUtil.message(MessageUtil.at(userID),
                MessageUtil.text(" "+safe(text)));
        client.sendGroupMessage(groupID,message);
    }

    private String triggerText(String task) {
        String value = safe(task).trim();
        if (value.isEmpty()) return "该做这件事了";
        if (value.startsWith("该")) return value.endsWith("了") ? value : value+"了";
        return "该"+value+"了";
    }

    private String formatTime(long time) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.CHINA);
        format.setTimeZone(resolveTimeZone(config.timeZone));
        return format.format(new Date(time));
    }

    private TimeZone resolveTimeZone(String name) {
        String value = name == null || name.trim().isEmpty() ? "Asia/Shanghai" : name.trim();
        TimeZone zone = TimeZone.getTimeZone(value);
        if ("GMT".equals(zone.getID()) && !"GMT".equalsIgnoreCase(value)) {
            return TimeZone.getTimeZone("Asia/Shanghai");
        }
        return zone;
    }

    private String senderName(GroupMessageEvent event) {
        JSONObject sender = event.getSender();
        String name = sender == null ? "" : sender.getString("card");
        if (name == null || name.trim().isEmpty()) name = sender == null ? "" : sender.getString("nickname");
        return name == null || name.trim().isEmpty() ? String.valueOf(event.getUserID()) : name.trim();
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
