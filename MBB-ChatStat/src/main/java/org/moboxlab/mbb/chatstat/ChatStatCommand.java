package org.moboxlab.mbb.chatstat;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Command.BotCommand;
import org.moboxlab.moboxbot.API.Command.CommandPermission;
import org.moboxlab.moboxbot.API.Command.CommandSender;
import org.moboxlab.moboxbot.API.PluginService;
import org.moboxlab.moboxbot.API.Util.ImageUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * /chatstat 群聊统计命令
 */
public class ChatStatCommand extends BotCommand {
    private final ChatStatPlugin plugin;
    private final ChatStatService statsService;

    public ChatStatCommand(ChatStatPlugin plugin,ChatStatService statsService) {
        this.plugin = plugin;
        this.statsService = statsService;
    }

    @Override
    public List<String> prefix() {
        List<String> prefixList = new ArrayList<>();
        prefixList.add("chatstat");
        prefixList.add("cstat");
        return prefixList;
    }

    @Override
    public CommandPermission permission() {
        return CommandPermission.BOT_ADMIN;
    }

    @Override
    public int cooldownSeconds() {
        return 5;
    }

    @Override
    public String description() {
        return "统计群聊内容与用户发言";
    }

    @Override
    public List<String> usage() {
        return Arrays.asList(
                "/chatstat group [群号] [天数] [ai]",
                "/chatstat user <QQ> [天数] [ai]");
    }

    @Override
    public boolean execute(CommandSender sender,String[] args) {
        if (args.length < 2) {
            sender.sendMessage("用法：/chatstat group [群号] [天数] [ai] | /chatstat user <QQ> [天数] [ai]");
            return true;
        }
        String action = args[1].toLowerCase();
        if ("group".equals(action)) {
            return groupStats(sender,args);
        }
        if ("user".equals(action)) {
            return userStats(sender,args);
        }
        sender.sendMessage("用法：/chatstat group [群号] [天数] [ai] | /chatstat user <QQ> [天数] [ai]");
        return true;
    }

    private boolean groupStats(CommandSender sender,String[] args) {
        boolean withAi = hasAiFlag(args);
        int end = args.length - (withAi ? 1 : 0);
        long groupID = sender.getGroupID();
        int dayIndex = 2;
        if (end >= 3) {
            try {
                groupID = Long.parseLong(args[2]);
                dayIndex = 3;
            } catch (Exception ignored) {
            }
        }
        if (groupID <= 0) {
            sender.sendMessage("请在群聊中使用，或指定群号：/chatstat group <群号> [天数]");
            return true;
        }
        int days = parseDays(args,dayIndex,end);
        JSONObject stats = statsService.groupStats(groupID,days);
        sendImage(sender,stats,"群聊统计图片生成失败！");
        if (withAi) runAiSummary(sender,stats,groupID,0L,days);
        return true;
    }

    private boolean userStats(CommandSender sender,String[] args) {
        boolean withAi = hasAiFlag(args);
        int end = args.length - (withAi ? 1 : 0);
        if (end < 3) {
            sender.sendMessage("用法：/chatstat user <QQ> [天数] [ai]");
            return true;
        }
        long userID;
        try {
            userID = Long.parseLong(args[2]);
        } catch (Exception e) {
            sender.sendMessage("QQ 号格式不正确！");
            return true;
        }
        int days = parseDays(args,3,end);
        JSONObject stats = statsService.userStats(userID,days);
        sendImage(sender,stats,"用户群聊统计图片生成失败！");
        if (withAi) runAiSummary(sender,stats,0L,userID,days);
        return true;
    }

    private void runAiSummary(CommandSender sender,JSONObject stats,long groupID,long userID,int days) {
        plugin.getServer().getPluginManager().runTask(plugin,() ->
                sendAiSummary(sender,stats,groupID,userID,days));
    }

    private void sendAiSummary(CommandSender sender,JSONObject stats,long groupID,long userID,int days) {
        PluginService ai = plugin.getServer().getPluginManager().getService("MBB-AI");
        if (ai == null) {
            sender.sendMessage("AI 总结不可用，请确认 MBB-AI 已启用！");
            return;
        }
        int limit = plugin.getConfig().getInt("aiSummaryMessageCount",0);
        JSONArray messages = statsService.aiMessages(groupID,userID,days,limit);
        StringBuilder source = new StringBuilder();
        source.append("统计范围：").append(groupID > 0 ? "群 "+groupID : "用户 "+userID)
                .append("，最近 ").append(days).append(" 天\n");
        source.append("统计摘要：").append(stats.getJSONObject("summary") == null ? "{}" : stats.getJSONObject("summary").toJSONString()).append("\n");
        source.append("最近消息：\n");
        if (messages == null || messages.isEmpty()) {
            source.append("没有可用于总结的消息。\n");
        } else {
            for (int i = 0; i < messages.size(); i++) {
                JSONObject item = messages.getJSONObject(i);
                source.append(i + 1).append(". ")
                        .append(safe(item.getString("userName"))).append("：")
                        .append(safe(item.getString("content"))).append("\n");
            }
        }
        JSONArray requestMessages = new JSONArray();
        requestMessages.add(message("system","你是群聊内容总结助手。请根据给定的统计和消息，用中文总结主要话题、活跃用户和整体氛围。"
                +"优先提炼主要内容、核心话题、重要结论和主要参与者；忽略寒暄、重复灌水、无实质内容的一两句小讨论和纯表情内容。"
                +"不要编造未出现的内容，总结要简洁清晰。只输出纯文本，禁止使用 Markdown、代码块、表格、标题、粗体或多余星号。"));
        requestMessages.add(message("user",source.toString()));
        JSONObject params = new JSONObject(true);
        params.put("profile",plugin.getConfig().getString("aiProfile","default"));
        params.put("maxTokens",plugin.getConfig().getInt("aiSummaryMaxTokens",8000));
        params.put("sessionId","moboxstat-"+Math.abs((groupID > 0 ? groupID : userID))+"-"+days);
        params.put("messages",requestMessages);
        JSONObject result = ai.call("chat",params);
        if (!result.getBooleanValue("status")) {
            sender.sendMessage("AI 总结失败："+safe(result.getString("message")));
            return;
        }
        String summary = safe(result.getString("content"));
        if (summary.isEmpty()) {
            summary = retrySummary(ai,source.toString(),groupID,userID,days);
        }
        if (summary.isEmpty()) {
            summary = fallbackSummary(stats);
        }
        byte[] image = ChatStatImageRenderer.renderAiSummary(stats,summary);
        if (image != null) {
            sender.sendImage(ImageUtil.toBase64Uri(image));
        } else {
            sender.sendMessage("AI 总结图片生成失败，已回退为文本：\n"+summary);
        }
    }

    private JSONObject message(String role,String content) {
        JSONObject message = new JSONObject(true);
        message.put("role",role);
        message.put("content",content == null ? "" : content);
        return message;
    }

    private boolean hasAiFlag(String[] args) {
        return args.length > 0 && "ai".equalsIgnoreCase(args[args.length - 1]);
    }

    private void sendImage(CommandSender sender,JSONObject stats,String failMessage) {
        byte[] image = ChatStatImageRenderer.render(stats);
        if (image != null) {
            sender.sendImage(ImageUtil.toBase64Uri(image));
        } else {
            sender.sendMessage(failMessage);
        }
    }

    private int parseDays(String[] args,int index,int end) {
        int days = plugin.getConfig().getInt("defaultDays",1);
        if (end > index) {
            try {
                days = Integer.parseInt(args[index]);
            } catch (Exception ignored) {
            }
        }
        if (days < 1) days = 1;
        if (days > 30) days = 30;
        return days;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String retrySummary(PluginService ai,String source,long groupID,long userID,int days) {
        JSONArray messages = new JSONArray();
        messages.add(message("system","只输出中文总结正文。重点总结主要内容、核心话题、重要结论和主要参与者，忽略零星闲聊和体量较小的讨论。"
                +"禁止输出思考过程，禁止使用 Markdown、代码块、表格、标题和多余星号。"));
        messages.add(message("user",source));
        JSONObject params = new JSONObject(true);
        params.put("profile",plugin.getConfig().getString("aiProfile","default"));
        params.put("maxTokens",Math.min(16000,Math.max(12000,plugin.getConfig().getInt("aiSummaryMaxTokens",8000) + 2000)));
        params.put("sessionId","moboxstat-"+Math.abs((groupID > 0 ? groupID : userID))+"-"+days+"-retry");
        params.put("messages",messages);
        JSONObject result = ai.call("chat",params);
        if (result == null || !result.getBooleanValue("status")) return "";
        return safe(result.getString("content"));
    }

    private String fallbackSummary(JSONObject stats) {
        JSONObject summary = stats.getJSONObject("summary");
        JSONArray top = stats.getJSONArray("top");
        JSONArray recent = stats.getJSONArray("recent");
        StringBuilder builder = new StringBuilder("模型暂时未返回总结，以下为统计摘要：\n");
        builder.append("消息数：").append(value(summary,"messages")).append("\n");
        if ("group".equals(stats.getString("scope"))) {
            builder.append("活跃人数：").append(value(summary,"users")).append("\n");
        } else {
            builder.append("活跃群数：").append(value(summary,"groups")).append("\n");
        }
        builder.append("图片数：").append(value(summary,"images"))
                .append("，@次数：").append(value(summary,"ats")).append("\n");
        if (top != null && !top.isEmpty()) {
            builder.append("主要活跃：");
            for (int i = 0; i < top.size() && i < 5; i++) {
                JSONObject item = top.getJSONObject(i);
                if (i > 0) builder.append("、");
                builder.append(safe(item.getString("name")));
            }
            builder.append("\n");
        }
        if (recent != null && !recent.isEmpty()) {
            builder.append("最近内容：\n");
            for (int i = 0; i < recent.size() && i < 5; i++) {
                JSONObject item = recent.getJSONObject(i);
                builder.append("- ").append(safe(item.getString("userName")))
                        .append("：").append(safe(item.getString("content"))).append("\n");
            }
        }
        return builder.toString().trim();
    }

    private long value(JSONObject row,String key) {
        return row == null ? 0L : row.getLongValue(key);
    }
}
