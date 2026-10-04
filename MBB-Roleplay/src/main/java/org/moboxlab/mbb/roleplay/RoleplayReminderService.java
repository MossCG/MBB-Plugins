package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Event.GroupMessageEvent;
import org.moboxlab.moboxbot.API.OneBot.MessageUtil;
import org.moboxlab.moboxbot.API.OneBot.OneBotClient;
import org.moboxlab.moboxbot.API.Plugin;
import org.moboxlab.moboxbot.API.PluginService;
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
    private volatile RoleplayPersona persona;

    public RoleplayReminderService(Plugin plugin,RoleplayConfig config,RoleplayPersona persona) {
        this.plugin = plugin;
        this.config = config;
        this.persona = persona;
    }

    public void init() {
        storage().update("CREATE TABLE IF NOT EXISTS `"+TABLE+"` ("
                + "`ID` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`groupID` INTEGER NOT NULL DEFAULT 0,"
                + "`userID` INTEGER NOT NULL DEFAULT 0,"
                + "`userName` TEXT NOT NULL DEFAULT '',"
                + "`relation` TEXT NOT NULL DEFAULT '',"
                + "`task` TEXT NOT NULL DEFAULT '',"
                + "`remindTime` INTEGER NOT NULL DEFAULT 0,"
                + "`createdAt` INTEGER NOT NULL DEFAULT 0,"
                + "`status` TEXT NOT NULL DEFAULT 'pending'"
                + ")");
        ensureColumn("relation","TEXT NOT NULL DEFAULT ''");
        storage().update("CREATE INDEX IF NOT EXISTS `idx_plugin_mbb_roleplay_reminder_time` "
                + "ON `"+TABLE+"` (`status`,`remindTime`)");
        restorePending();
    }

    public void reload(RoleplayConfig config,RoleplayPersona persona) {
        this.config = config;
        this.persona = persona;
    }

    public boolean handle(GroupMessageEvent event,String content) {
        if (event == null || !config.reminderEnable) return false;
        RoleplayReminderParser.Result result =
                RoleplayReminderParser.parse(content,config,System.currentTimeMillis());
        if (!result.intent) return false;
        long groupID = event.getGroupID();
        long userID = event.getUserID();
        String userName = senderName(event);
        String relation = relationship(event,false);
        if (!result.valid) {
            sendAt(groupID,userID,result.error);
            plugin.getLogger().sendWarn("[提醒] 群"+groupID+" 用户"+userID
                    +" 创建失败："+safe(result.error)+"，原文："+shortText(content,120));
            return true;
        }
        long now = System.currentTimeMillis();
        long id = storage().insert("INSERT INTO `"+TABLE+"` "
                        + "(`groupID`,`userID`,`userName`,`relation`,`task`,`remindTime`,`createdAt`,`status`) "
                        + "VALUES (?,?,?,?,?,?,?,?)",
                groupID,userID,userName,relation,result.task,result.remindTime,now,"pending");
        schedule(id,groupID,userID,result.task,relation,result.remindTime);
        plugin.getLogger().sendInfo("[提醒] 创建 #"+id+" 群"+groupID+" 用户"+userID
                +" 时间="+formatTime(result.remindTime)+" 内容="+result.task);
        sendAt(groupID,userID,formatTime(result.remindTime)+" 提醒你："+result.task);
        return true;
    }

    private void restorePending() {
        List<JSONObject> rows = storage().query(
                "SELECT `ID`,`groupID`,`userID`,`relation`,`task`,`remindTime` FROM `"+TABLE+"` "
                        + "WHERE `status`='pending' ORDER BY `remindTime` ASC");
        if (rows == null || rows.isEmpty()) return;
        for (JSONObject row : rows) {
            schedule(row.getLongValue("ID"),row.getLongValue("groupID"),row.getLongValue("userID"),
                    safe(row.getString("task")),safe(row.getString("relation")),row.getLongValue("remindTime"));
        }
        plugin.getLogger().sendInfo("[提醒] 已恢复 "+rows.size()+" 条待触发提醒");
    }

    private void schedule(long id,long groupID,long userID,String task,String relation,long remindTime) {
        long delay = Math.max(1L,(remindTime - System.currentTimeMillis() + 999L) / 1000L);
        plugin.getServer().getPluginManager().runTaskLater(plugin,
                () -> trigger(id,groupID,userID,task,relation),delay);
    }

    private void trigger(long id,long groupID,long userID,String task,String relation) {
        JSONObject row = storage().queryOne(
                "SELECT `status` FROM `"+TABLE+"` WHERE `ID`=?",id);
        if (row == null || !"pending".equals(row.getString("status"))) return;
        plugin.getLogger().sendInfo("[提醒] 触发 #"+id+" 群"+groupID+" 用户"+userID
                +" 内容="+task);
        String text = generateReminderText(id,groupID,userID,task,relation);
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
            schedule(id,groupID,userID,task,relation,System.currentTimeMillis() + 60000L);
        }
    }

    private String generateReminderText(long id,long groupID,long userID,String task,String relation) {
        PluginService ai = plugin.getServer().getPluginManager().getService("MBB-AI");
        if (ai == null) return triggerText(task);
        String relationship = safe(relation).trim().isEmpty() ? "朋友" : safe(relation).trim();
        try {
            JSONArray messages = new JSONArray();
            messages.add(message("system",persona.description()+"\n\n"
                    +"现在是主动提醒群友的时间。请用角色平时的自然聊天语气，生成一句直接提醒对方该做什么的话。"
                    +"只输出提醒正文，不要输出艾特、Markdown、旁白、思考过程或解释，不要提及 AI 和系统。"
                    +"当前时间："+formatTime(System.currentTimeMillis())+"\n"
                    +"关系："+relationship+"\n"
                    +"提醒任务："+safe(task)));
            messages.add(message("user","请生成到点提醒内容。"));
            JSONObject params = new JSONObject(true);
            params.put("profile",config.aiProfile);
            params.put("maxTokens",300);
            params.put("temperature",0.7);
            params.put("sessionId","roleplay-reminder-"+id+"-"+groupID);
            params.put("messages",messages);
            JSONObject result = ai.call("chat",params);
            if (result == null || !result.getBooleanValue("status")) return triggerText(task);
            String text = safe(result.getString("content")).trim();
            text = text.replaceAll("^[@＠]\\S+\\s*","").replaceAll("\\s+"," ").trim();
            if (text.isEmpty() || "<SKIP>".equalsIgnoreCase(text)) return triggerText(task);
            plugin.getLogger().sendInfo("[提醒] AI生成 #"+id+" 群"+groupID
                    +" 用户"+userID+" 内容="+text);
            return text;
        } catch (Exception e) {
            plugin.getLogger().sendWarn("[提醒] AI生成失败 #"+id+"，使用模板兜底："
                    +safe(e.getMessage()));
            return triggerText(task);
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

    private String relationship(GroupMessageEvent event,boolean otherRoleBot) {
        if (otherRoleBot) return "其他角色机器人";
        JSONObject sender = event == null ? null : event.getSender();
        String role = sender == null ? "" : safe(sender.getString("role")).trim().toLowerCase(Locale.CHINA);
        if ("owner".equals(role) || "admin".equals(role)) return "老师";
        return "朋友";
    }

    private JSONObject message(String role,String content) {
        JSONObject message = new JSONObject(true);
        message.put("role",role);
        message.put("content",content == null ? "" : content);
        return message;
    }

    private void ensureColumn(String column,String definition) {
        List<JSONObject> columns = storage().query("PRAGMA table_info(`"+TABLE+"`)");
        if (columns != null) {
            for (JSONObject item : columns) {
                if (column.equalsIgnoreCase(safe(item.getString("name")))) return;
            }
        }
        storage().update("ALTER TABLE `"+TABLE+"` ADD COLUMN `"+column+"` "+definition);
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
