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
    public static final String REMINDER_MARKER = "\u200B";

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
                + "`target` TEXT NOT NULL DEFAULT 'user',"
                + "`task` TEXT NOT NULL DEFAULT '',"
                + "`remindTime` INTEGER NOT NULL DEFAULT 0,"
                + "`createdAt` INTEGER NOT NULL DEFAULT 0,"
                + "`status` TEXT NOT NULL DEFAULT 'pending'"
                + ")");
        ensureColumn("relation","TEXT NOT NULL DEFAULT ''");
        ensureColumn("target","TEXT NOT NULL DEFAULT 'user'");
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
                        + "(`groupID`,`userID`,`userName`,`relation`,`target`,`task`,`remindTime`,`createdAt`,`status`) "
                        + "VALUES (?,?,?,?,?,?,?,?,?)",
                groupID,userID,userName,relation,"user",result.task,result.remindTime,now,"pending");
        schedule(id,groupID,userID,result.task,relation,"user",result.remindTime,result.remindTime);
        String confirmation = generateCreationText(id,groupID,userID,result.task,relation,result.remindTime);
        plugin.getLogger().sendInfo("[提醒] 创建 #"+id+" 群"+groupID+" 用户"+userID
                +" 时间="+formatTime(result.remindTime)+" 内容="+result.task
                +" 识别="+result.source);
        sendAt(groupID,userID,REMINDER_MARKER+confirmation);
        return true;
    }

    public boolean createFromAi(long groupID,long userID,String userName,String relation,
                                String timeText,String task,String target) {
        if (!config.reminderEnable) return false;
        long remindTime = parseReminderTime(timeText);
        String value = safe(task).trim();
        String targetValue = "self".equalsIgnoreCase(safe(target).trim()) ? "self" : "user";
        if (remindTime <= 0 || value.isEmpty()) {
            plugin.getLogger().sendWarn("[提醒] AI上下文创建失败：时间或任务无效，群"+groupID
                    +" 用户"+userID+" 时间="+safe(timeText)+" 内容="+value);
            return false;
        }
        long now = System.currentTimeMillis();
        if (remindTime <= now) {
            plugin.getLogger().sendWarn("[提醒] AI上下文创建失败：时间已过去，群"+groupID
                    +" 用户"+userID+" 时间="+formatTime(remindTime));
            return false;
        }
        long maxMillis = now + Math.max(1,config.reminderMaxDays) * 86400000L;
        if (remindTime > maxMillis) {
            plugin.getLogger().sendWarn("[提醒] AI上下文创建失败：超过最大天数，群"+groupID
                    +" 用户"+userID+" 时间="+formatTime(remindTime));
            return false;
        }
        JSONObject exists = storage().queryOne("SELECT `ID` FROM `"+TABLE+"` "
                        + "WHERE `groupID`=? AND `userID`=? AND `remindTime`=? AND `task`=? "
                        + "AND `target`=? AND `status`='pending' LIMIT 1",
                groupID,userID,remindTime,value,targetValue);
        if (exists != null) return true;
        long id = storage().insert("INSERT INTO `"+TABLE+"` "
                        + "(`groupID`,`userID`,`userName`,`relation`,`target`,`task`,`remindTime`,`createdAt`,`status`) "
                        + "VALUES (?,?,?,?,?,?,?,?,?)",
                groupID,userID,safe(userName),safe(relation),targetValue,value,remindTime,now,"pending");
        schedule(id,groupID,userID,value,safe(relation),targetValue,remindTime,remindTime);
        plugin.getLogger().sendInfo("[提醒] AI上下文创建 #"+id+" 群"+groupID+" 用户"+userID
                +" 目标="+targetValue+" 时间="+formatTime(remindTime)+" 内容="+value);
        return true;
    }

    public JSONArray listPending(long groupID,long userID) {
        JSONArray result = new JSONArray();
        List<JSONObject> rows = storage().query(
                "SELECT `ID`,`target`,`task`,`remindTime` FROM `"+TABLE+"` "
                        + "WHERE `groupID`=? AND `userID`=? AND `status`='pending' "
                        + "ORDER BY `remindTime` ASC",
                groupID,userID);
        if (rows == null) return result;
        for (JSONObject row : rows) {
            JSONObject item = new JSONObject(true);
            item.put("id",row.getLongValue("ID"));
            item.put("target",safe(row.getString("target")));
            item.put("task",safe(row.getString("task")));
            item.put("remindTime",row.getLongValue("remindTime"));
            result.add(item);
        }
        return result;
    }

    public JSONObject getPending(long id,long groupID,long userID) {
        JSONObject row = storage().queryOne(
                "SELECT `ID`,`target`,`task`,`remindTime` FROM `"+TABLE+"` "
                        + "WHERE `ID`=? AND `groupID`=? AND `userID`=? AND `status`='pending'",
                id,groupID,userID);
        if (row == null) return null;
        JSONObject item = new JSONObject(true);
        item.put("id",row.getLongValue("ID"));
        item.put("target",safe(row.getString("target")));
        item.put("task",safe(row.getString("task")));
        item.put("remindTime",row.getLongValue("remindTime"));
        return item;
    }

    public boolean update(long id,long groupID,long userID,long remindTime,String task) {
        JSONObject row = getPending(id,groupID,userID);
        if (row == null || remindTime <= System.currentTimeMillis() || safe(task).trim().isEmpty()) {
            return false;
        }
        String target = safe(row.getString("target"));
        String relation = "";
        JSONObject detail = storage().queryOne(
                "SELECT `relation` FROM `"+TABLE+"` WHERE `ID`=?",id);
        if (detail != null) relation = safe(detail.getString("relation"));
        int changed = storage().update(
                "UPDATE `"+TABLE+"` SET `task`=?,`remindTime`=? WHERE `ID`=? AND `groupID`=? AND `userID`=? AND `status`='pending'",
                safe(task).trim(),remindTime,id,groupID,userID);
        if (changed <= 0) return false;
        schedule(id,groupID,userID,safe(task).trim(),relation,target,remindTime,remindTime);
        plugin.getLogger().sendInfo("[提醒] 修改 #"+id+" 群"+groupID+" 用户"+userID
                +" 时间="+formatTime(remindTime)+" 内容="+safe(task).trim());
        return true;
    }

    public boolean cancel(long id,long groupID,long userID) {
        int changed = storage().update(
                "UPDATE `"+TABLE+"` SET `status`='cancelled' "
                        + "WHERE `ID`=? AND `groupID`=? AND `userID`=? AND `status`='pending'",
                id,groupID,userID);
        if (changed > 0) {
            plugin.getLogger().sendInfo("[提醒] 取消 #"+id+" 群"+groupID+" 用户"+userID);
            return true;
        }
        return false;
    }

    public int cancelAll(long groupID,long userID) {
        int changed = storage().update(
                "UPDATE `"+TABLE+"` SET `status`='cancelled' "
                        + "WHERE `groupID`=? AND `userID`=? AND `status`='pending'",
                groupID,userID);
        if (changed > 0) {
            plugin.getLogger().sendInfo("[提醒] 清空 群"+groupID+" 用户"+userID
                    +" 共取消 "+changed+" 条");
        }
        return changed;
    }

    public long parseEditTime(String text) {
        long direct = parseReminderTime(text);
        if (direct > 0) return direct;
        RoleplayReminderParser.Result result = RoleplayReminderParser.parse(
                "提醒我 "+safe(text)+" 做这件事",config,System.currentTimeMillis());
        return result.valid ? result.remindTime : -1L;
    }

    public String pendingText(long groupID,long userID) {
        JSONArray rows = listPending(groupID,userID);
        if (rows.isEmpty()) return "无。";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.getJSONObject(i);
            builder.append("#").append(row.getLongValue("id"))
                    .append(" ").append(formatTime(row.getLongValue("remindTime")))
                    .append(" ").append("self".equals(row.getString("target")) ? "角色自提醒" : "提醒本人")
                    .append(" ").append(safe(row.getString("task"))).append("\n");
        }
        return builder.toString();
    }

    public String executeAiAction(long groupID,long userID,String userName,String relation,
                                  String action,long id,String timeText,String task,String target) {
        String value = safe(action).trim().toLowerCase(Locale.CHINA);
        if (value.isEmpty()) value = "create";
        if ("create".equals(value)) {
            boolean created = createFromAi(groupID,userID,userName,relation,timeText,task,target);
            if (!created) return "提醒创建失败，请检查时间和内容。";
            return "提醒已创建："+(parseReminderTime(timeText) > 0
                    ? formatTime(parseReminderTime(timeText)) : safe(timeText))
                    +" "+safe(task);
        }
        if ("delete".equals(value) || "remove".equals(value) || "cancel".equals(value)) {
            return cancel(id,groupID,userID) ? "提醒 #"+id+" 已取消。" : "没有找到可取消的提醒。";
        }
        if ("edit".equals(value) || "update".equals(value)) {
            long remindTime = parseEditTime(timeText);
            if (remindTime <= 0) return "没有识别出新的提醒时间。";
            boolean changed = update(id,groupID,userID,remindTime,task);
            return changed
                    ? "提醒 #"+id+" 已修改为："+formatTime(remindTime)+" "+safe(task)
                    : "提醒修改失败，请检查 ID、时间或内容。";
        }
        if ("list".equals(value)) {
            String pending = pendingText(groupID,userID);
            return "当前待触发提醒：\n"+pending;
        }
        if ("show".equals(value)) {
            JSONObject row = getPending(id,groupID,userID);
            if (row == null) return "没有找到这条提醒。";
            return "提醒 #"+id+"\n时间："+formatTime(row.getLongValue("remindTime"))
                    +"\n目标："+("self".equals(row.getString("target")) ? "角色自提醒" : "提醒本人")
                    +"\n内容："+safe(row.getString("task"));
        }
        return "不支持的提醒操作："+value;
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
                "SELECT `ID`,`groupID`,`userID`,`relation`,`target`,`task`,`remindTime` FROM `"+TABLE+"` "
                        + "WHERE `status`='pending' ORDER BY `remindTime` ASC");
        if (rows == null || rows.isEmpty()) return;
        for (JSONObject row : rows) {
            schedule(row.getLongValue("ID"),row.getLongValue("groupID"),row.getLongValue("userID"),
                    safe(row.getString("task")),safe(row.getString("relation")),
                    safe(row.getString("target")),row.getLongValue("remindTime"),
                    row.getLongValue("remindTime"));
        }
        plugin.getLogger().sendInfo("[提醒] 已恢复 "+rows.size()+" 条待触发提醒");
    }

    private void schedule(long id,long groupID,long userID,String task,String relation,
                          String target,long expectedRemindTime,long runAtTime) {
        long delay = Math.max(1L,(runAtTime - System.currentTimeMillis() + 999L) / 1000L);
        plugin.getServer().getPluginManager().runTaskLater(plugin,
                () -> trigger(id,groupID,userID,task,relation,target,expectedRemindTime),delay);
    }

    private void trigger(long id,long groupID,long userID,String task,String relation,
                         String target,long expectedRemindTime) {
        JSONObject row = storage().queryOne(
                "SELECT `status`,`remindTime` FROM `"+TABLE+"` WHERE `ID`=?",id);
        if (row == null || !"pending".equals(row.getString("status"))
                || row.getLongValue("remindTime") != expectedRemindTime) return;
        plugin.getLogger().sendInfo("[提醒] 触发 #"+id+" 群"+groupID+" 用户"+userID
                +" 内容="+task);
        String text = generateReminderText(id,groupID,userID,task,relation,target);
        OneBotClient client = plugin.getServer().getOneBotClient();
        JSONObject response = client == null ? null
                : client.sendGroupMessage(groupID,MessageUtil.message(
                MessageUtil.at(userID),MessageUtil.text(" "+REMINDER_MARKER+text)));
        if (response != null && response.getIntValue("retcode") == 0) {
            storage().update("UPDATE `"+TABLE+"` SET `status`='done' WHERE `ID`=?",id);
            plugin.getLogger().sendInfo("[提醒] 发送成功 #"+id+" 群"+groupID+" 用户"+userID);
        } else {
            plugin.getLogger().sendWarn("[提醒] 发送失败 #"+id+" 群"+groupID
                    +" 用户"+userID+"，60 秒后重试");
            schedule(id,groupID,userID,task,relation,target,expectedRemindTime,
                    System.currentTimeMillis() + 60000L);
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

    private String generateReminderText(long id,long groupID,long userID,String task,
                                        String relation,String target) {
        PluginService ai = plugin.getServer().getPluginManager().getService("MBB-AI");
        if (ai == null) return triggerText(task);
        String relationship = safe(relation).trim().isEmpty() ? "朋友" : safe(relation).trim();
        try {
            JSONArray messages = new JSONArray();
            messages.add(message("system",persona.description()+"\n\n"
                    +(("self".equals(target))
                    ? "现在是角色自己的定时任务触发时间。请结合任务内容和角色设定，主动发一句自然聊天内容，"
                    +"可以提醒自己继续或结束某件事，也可以自然邀请群里的人一起行动。"
                    : "现在是主动提醒群友的时间。请用角色平时的自然聊天语气，生成一句直接提醒对方该做什么的话。")
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

    public void sendAt(long groupID,long userID,String text) {
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

    public String formatTime(long time) {
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
