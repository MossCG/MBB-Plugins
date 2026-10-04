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
        long now = System.currentTimeMillis();
        RoleplayReminderParser.Result result = null;
        if (config.reminderAiParse && mightBeReminder(content)) {
            result = parseWithAi(event.getGroupID(),event.getUserID(),content,now);
            if (result != null && !result.intent) return false;
        }
        if (result == null) {
            result = RoleplayReminderParser.parse(content,config,now);
        }
        if (!result.intent) return false;
        long groupID = event.getGroupID();
        long userID = event.getUserID();
        String userName = senderName(event);
        String relation = relationship(event,false);
        if (!result.valid) {
            sendAt(groupID,userID,result.error);
            plugin.getLogger().sendWarn("[提醒] 群"+groupID+" 用户"+userID
                    +" 创建失败："+safe(result.error)+"，识别="+result.source
                    +"，原文："+shortText(content,120));
            return true;
        }
        long id = storage().insert("INSERT INTO `"+TABLE+"` "
                        + "(`groupID`,`userID`,`userName`,`relation`,`task`,`remindTime`,`createdAt`,`status`) "
                        + "VALUES (?,?,?,?,?,?,?,?)",
                groupID,userID,userName,relation,result.task,result.remindTime,now,"pending");
        schedule(id,groupID,userID,result.task,relation,result.remindTime);
        String confirmation = generateCreationText(id,groupID,userID,result.task,relation,result.remindTime);
        plugin.getLogger().sendInfo("[提醒] 创建 #"+id+" 群"+groupID+" 用户"+userID
                +" 时间="+formatTime(result.remindTime)+" 内容="+result.task
                +" 识别="+result.source);
        sendAt(groupID,userID,"提醒已设置："+confirmation);
        return true;
    }

    private RoleplayReminderParser.Result parseWithAi(long groupID,long userID,String content,long now) {
        PluginService ai = plugin.getServer().getPluginManager().getService("MBB-AI");
        if (ai == null) return null;
        try {
            JSONArray messages = new JSONArray();
            messages.add(message("system","你是定时提醒识别器。当前时间："
                    +formatTime(now)+"，时区："+resolveTimeZoneName()+"。"
                    +"判断用户是否在要求设置提醒。必须只输出 JSON，不要 Markdown、代码块或解释："
                    +"{\"hasReminder\":true/false,\"time\":\"yyyy-MM-dd HH:mm:ss\",\"task\":\"提醒内容\",\"reason\":\"\"}。"
                    +"如果无法确定具体时间、时间已经过去、或不是在设置提醒，hasReminder=false。"
                    +"time 必须使用当前时区，task 只保留要提醒的事情，不要包含“提醒我”等指令词。"));
            messages.add(message("user",content));
            JSONObject params = new JSONObject(true);
            params.put("profile",config.aiProfile);
            params.put("maxTokens",300);
            params.put("temperature",0.1);
            params.put("sessionId","roleplay-reminder-parse-"+groupID+"-"+userID);
            params.put("messages",messages);
            JSONObject response = ai.call("chat",params);
            if (response == null || !response.getBooleanValue("status")) {
                plugin.getLogger().sendWarn("[提醒] AI识别失败，回退规则解析："
                        +safe(response == null ? "" : response.getString("message")));
                return null;
            }
            JSONObject parsed = parseJson(response.getString("content"));
            if (parsed == null) {
                plugin.getLogger().sendWarn("[提醒] AI识别没有返回合法 JSON，回退规则解析："
                        +shortText(response.getString("content"),160));
                return null;
            }
            if (!parsed.getBooleanValue("hasReminder")) {
                RoleplayReminderParser.Result result = new RoleplayReminderParser.Result(false,false);
                result.source = "AI";
                return result;
            }
            long remindTime = parseReminderTime(parsed.getString("time"));
            String task = safe(parsed.getString("task")).trim();
            if (remindTime <= 0) {
                plugin.getLogger().sendWarn("[提醒] AI识别时间格式无效，回退规则解析："
                        +safe(parsed.getString("time")));
                return null;
            }
            RoleplayReminderParser.Result result = new RoleplayReminderParser.Result(true,false);
            result.source = "AI";
            result.remindTime = remindTime;
            result.task = task;
            if (remindTime <= now) {
                result.error = "AI 识别出的提醒时间已经过去。";
                return result;
            }
            long maxMillis = now + Math.max(1,config.reminderMaxDays) * 86400000L;
            if (remindTime > maxMillis) {
                result.error = "提醒时间太远了，最多支持 "+config.reminderMaxDays+" 天。";
                return result;
            }
            if (task.isEmpty()) task = "做这件事";
            result.task = task;
            result.valid = true;
            plugin.getLogger().sendInfo("[提醒] AI识别成功 群"+groupID+" 用户"+userID
                    +" 时间="+formatTime(remindTime)+" 内容="+task);
            return result;
        } catch (Exception e) {
            plugin.getLogger().sendWarn("[提醒] AI识别异常，回退规则解析："
                    +safe(e.getMessage()));
            return null;
        }
    }

    private boolean mightBeReminder(String content) {
        String text = safe(content);
        return text.contains("提醒") || text.contains("叫我") || text.contains("记得")
                || text.contains("闹钟") || text.contains("定时");
    }

    private long parseReminderTime(String text) {
        if (text == null || text.trim().isEmpty()) return -1;
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.CHINA);
            format.setTimeZone(resolveTimeZone(config.timeZone));
            return format.parse(text.trim()).getTime();
        } catch (Exception e) {
            return -1;
        }
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
                MessageUtil.at(userID),MessageUtil.text(" 提醒："+text)));
        if (response != null && response.getIntValue("retcode") == 0) {
            storage().update("UPDATE `"+TABLE+"` SET `status`='done' WHERE `ID`=?",id);
            plugin.getLogger().sendInfo("[提醒] 发送成功 #"+id+" 群"+groupID+" 用户"+userID);
        } else {
            plugin.getLogger().sendWarn("[提醒] 发送失败 #"+id+" 群"+groupID
                    +" 用户"+userID+"，60 秒后重试");
            schedule(id,groupID,userID,task,relation,System.currentTimeMillis() + 60000L);
        }
    }

    private String generateCreationText(long id,long groupID,long userID,String task,
                                        String relation,long remindTime) {
        PluginService ai = plugin.getServer().getPluginManager().getService("MBB-AI");
        if (ai == null) return formatTime(remindTime)+" 提醒你："+task;
        String relationship = safe(relation).trim().isEmpty() ? "朋友" : safe(relation).trim();
        try {
            JSONArray messages = new JSONArray();
            messages.add(message("system",persona.description()+"\n\n"
                    +"用户刚刚创建了一个提醒。请用角色平时的自然聊天语气，生成一句确认回复，"
                    +"自然告知会在什么时间提醒对方做什么。只输出确认正文，不要输出艾特、Markdown、旁白、"
                    +"思考过程或解释，不要提及 AI 和系统。当前时间："+formatTime(System.currentTimeMillis())+"\n"
                    +"提醒时间："+formatTime(remindTime)+"\n"
                    +"关系："+relationship+"\n"
                    +"提醒任务："+safe(task)));
            messages.add(message("user","请生成提醒创建成功的确认。"));
            JSONObject params = new JSONObject(true);
            params.put("profile",config.aiProfile);
            params.put("maxTokens",300);
            params.put("temperature",0.7);
            params.put("sessionId","roleplay-reminder-create-"+id+"-"+groupID);
            params.put("messages",messages);
            JSONObject result = ai.call("chat",params);
            if (result == null || !result.getBooleanValue("status")) {
                return formatTime(remindTime)+" 提醒你："+task;
            }
            String text = cleanAiText(result.getString("content"));
            if (text.isEmpty() || "<SKIP>".equalsIgnoreCase(text)) {
                return formatTime(remindTime)+" 提醒你："+task;
            }
            plugin.getLogger().sendInfo("[提醒] AI确认生成 #"+id+" 群"+groupID
                    +" 用户"+userID+" 内容="+text);
            return text;
        } catch (Exception e) {
            plugin.getLogger().sendWarn("[提醒] AI确认生成失败 #"+id+"，使用模板兜底："
                    +safe(e.getMessage()));
            return formatTime(remindTime)+" 提醒你："+task;
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
            String text = cleanAiText(result.getString("content"));
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

    private String cleanAiText(String content) {
        String text = safe(content).trim();
        text = text.replaceAll("^[@＠]\\S+\\s*","").replaceAll("\\s+"," ").trim();
        return text;
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

    private String resolveTimeZoneName() {
        String value = config.timeZone == null || config.timeZone.trim().isEmpty()
                ? "Asia/Shanghai" : config.timeZone.trim();
        TimeZone zone = TimeZone.getTimeZone(value);
        if ("GMT".equals(zone.getID()) && !"GMT".equalsIgnoreCase(value)) {
            return "Asia/Shanghai";
        }
        return value;
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
