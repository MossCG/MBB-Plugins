package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Command.CommandPermission;
import org.moboxlab.moboxbot.API.Command.CommandSender;
import org.moboxlab.moboxbot.API.Util.ImageUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Roleplay 命令处理器：RoleplayFeedbackCommandHandler
 */
public class RoleplayFeedbackCommandHandler {
    private final RoleplayCommand command;

    public RoleplayFeedbackCommandHandler(RoleplayCommand command) {
        this.command = command;
    }

    void handleFeedback(CommandSender sender,String[] args) {
        long groupID = sender.getGroupID();
        if (groupID <= 0) {
            sender.sendMessage("请在群聊中使用。");
            return;
        }
        RoleplayReplyFeedbackService feedback = command.service().getReplyFeedbackService();
        if (args.length > 2 && "reset".equalsIgnoreCase(args[2])) {
            feedback.reset(groupID);
            sender.sendMessage("当前群主动发言反馈统计已重置。");
            return;
        }
        JSONObject stats = feedback.stats(groupID);
        sender.sendMessage("主动发言反馈："
                +"\n累计被接话："+stats.getLongValue("successCount")
                +"\n累计无人接："+stats.getLongValue("ignoreCount")
                +"\n最近样本："+stats.getIntValue("recentSuccess")+"/"+stats.getIntValue("recentCount")
                +"\n连续没人接时，非直接消息的回复概率会自动降低。");
    }

    void handleExpression(CommandSender sender,String[] args) {
        long groupID = sender.getGroupID();
        if (groupID <= 0) {
            sender.sendMessage("请在群聊中使用。");
            return;
        }
        RoleplayExpressionService expressions = command.service().getExpressionService();
        String action = args.length > 2 ? args[2].toLowerCase() : "list";
        if ("list".equals(action)) {
            String status = args.length > 3 ? args[3].toLowerCase() : "pending";
            JSONArray entries = expressions.list(groupID,status,30);
            if (entries.isEmpty()) {
                sender.sendMessage("当前群没有 "+status+" 表达候选。");
                return;
            }
            StringBuilder builder = new StringBuilder("表达候选（"+status+"）：");
            for (Object object : entries) {
                JSONObject item = (JSONObject) object;
                builder.append("\n#").append(item.getLongValue("id"))
                        .append(" ").append(item.getString("content"))
                        .append("（"+item.getIntValue("count")+" 次 / "
                        +item.getIntValue("userCount")+" 人");
                if (!command.service().safe(item.getString("meaning")).isEmpty()) {
                    builder.append("，含义：").append(item.getString("meaning"));
                }
                if (item.getDoubleValue("quality") > 0) {
                    builder.append("，质量：")
                            .append(String.format(Locale.ROOT,"%.2f",item.getDoubleValue("quality")));
                }
                builder.append("）");
            }
            sender.sendMessage(builder.toString());
            return;
        }
        if ("review".equals(action)) {
            boolean started = command.service().expressionReviewService().trigger(groupID);
            sender.sendMessage(started
                    ? "当前群表达候选 AI 审核已开始，完成后会写入理解与质量结果。"
                    : "当前群没有待审表达候选，或审核任务已经在执行。");
            return;
        }
        if ("promote".equals(action) || "reject".equals(action)) {
            if (args.length <= 3) {
                sender.sendMessage("用法：/role expression "+action+" <ID>");
                return;
            }
            long id = command.parseUserID(args[3]);
            boolean changed = expressions.setStatus(id,
                    "promote".equals(action) ? "approved" : "rejected");
            sender.sendMessage(changed ? "表达候选已更新。" : "没有找到该表达候选。");
            return;
        }
        if ("clear".equals(action)) {
            int count = expressions.clear(groupID);
            sender.sendMessage("当前群表达候选已清空，共删除 "+count+" 条。");
            return;
        }
        sender.sendMessage("用法：/role expression list [pending|approved|rejected]"
                +" | review | promote <ID> | reject <ID> | clear");
    }

    void handleReview(CommandSender sender,String[] args) {
        long groupID = sender.getGroupID();
        if (groupID <= 0) {
            sender.sendMessage("请在群聊中使用。");
            return;
        }
        if (args.length <= 2 || "now".equalsIgnoreCase(args[2])) {
            command.service().triggerDailyReview(groupID);
            sender.sendMessage("当前群每日复盘已开始，完成后会把结果写入短期记忆。");
            return;
        }
        sender.sendMessage("用法：/role review [now]");
    }

    void handleAttention(CommandSender sender,String[] args) {
        long groupID = sender.getGroupID();
        if (groupID <= 0) {
            sender.sendMessage("请在群聊中使用。");
            return;
        }
        if (args.length > 2 && "reset".equalsIgnoreCase(args[2])) {
            command.service().getAttentionService().reset(groupID);
            sender.sendMessage("当前群注意力已重置。");
            return;
        }
        double score = command.service().getAttentionService().score(groupID);
        sender.sendMessage("当前群注意力："+String.format(java.util.Locale.ROOT,"%.2f",score)
                +"\n0.5 为基线，低于阈值会降低非直接消息的接话概率。");
    }

    void handleTrace(CommandSender sender,String[] args) {
        long groupID = sender.getGroupID();
        if (groupID <= 0) {
            sender.sendMessage("请在群聊中使用。");
            return;
        }
        int limit = command.parsePage(args,2);
        if (limit > 10) limit = 10;
        JSONArray traces = command.service().getTraceService().list(groupID,limit);
        if (traces.isEmpty()) {
            sender.sendMessage("当前群还没有对话 trace。");
            return;
        }
        StringBuilder builder = new StringBuilder("最近对话 trace：");
        for (Object object : traces) {
            JSONObject item = (JSONObject)object;
            JSONObject data = item.getJSONObject("data");
            builder.append("\n#").append(item.getLongValue("id"))
                    .append(" ").append(item.getString("stage"))
                    .append(" 用户=").append(item.getLongValue("userID"))
                    .append(" 指向=").append(data.getString("addressed"))
                    .append(" 技能=").append(data.getString("skills"))
                    .append(" 字符=").append(data.getIntValue("systemPromptChars"))
                    .append("/").append(data.getIntValue("userPromptChars"))
                    .append(" 原因=").append(data.getString("reason"));
            String reply = data.getString("reply");
            if (reply != null && !reply.isEmpty()) {
                builder.append(" 回复=").append(reply);
            }
        }
        sender.sendMessage(builder.toString());
    }

    void handleTool(CommandSender sender,String[] args) {
        long groupID = sender.getGroupID();
        if (groupID <= 0) {
            sender.sendMessage("请在群聊中使用。");
            return;
        }
        String action = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "stats";
        if ("cancel".equals(action)) {
            boolean cancelled = command.service().toolLoopService().cancelGroup(groupID);
            sender.sendMessage(cancelled
                    ? "当前群正在执行的工具回合已请求取消。"
                    : "当前群没有正在执行的工具回合。");
            return;
        }
        if ("test".equals(action)) {
            JSONObject test = command.service().toolLoopService().registry()
                    .selfTest(command.service().config());
            StringBuilder builder = new StringBuilder(test.getBooleanValue("status")
                    ? "工具自检通过：" : "工具自检发现问题：")
                    .append("\n检查工具：").append(test.getIntValue("checked"));
            JSONArray issues = test.getJSONArray("issues");
            if (issues != null) {
                for (Object issue : issues) builder.append("\n- ").append(String.valueOf(issue));
            }
            builder.append("\n工作区隔离：")
                    .append(test.getJSONObject("workspace").getBooleanValue("status") ? "通过" : "未启用或失败")
                    .append("\n网页白名单：")
                    .append(test.getJSONObject("web").getBooleanValue("status") ? "通过" : "未启用或失败");
            sender.sendMessage(builder.toString());
            return;
        }
        if ("clear".equals(action)) {
            int count = command.service().toolMemoryService().clear(groupID);
            sender.sendMessage("当前群工具结果上下文已清空，共删除 "+count+" 条。");
            return;
        }
        if ("trace".equals(action)) {
            int limit = args.length > 3 ? command.parsePage(args,3) : 10;
            JSONArray rows = command.service().toolMemoryService().recent(groupID,limit);
            if (rows.isEmpty()) {
                sender.sendMessage("当前群还没有工具调用记录。");
                return;
            }
            StringBuilder builder = new StringBuilder("最近工具调用：");
            for (Object object : rows) {
                JSONObject item = (JSONObject)object;
                builder.append("\n#").append(item.getLongValue("id"))
                        .append(" ").append(formatToolTime(item.getLongValue("createTime")))
                        .append(" ").append(item.getString("toolName"))
                        .append(" 用户=").append(item.getLongValue("userID"))
                        .append(" 摘要=").append(item.getString("summary"));
            }
            sender.sendMessage(builder.toString());
            return;
        }
        if (!"stats".equals(action)) {
            sender.sendMessage("用法：/role tool [stats|test|trace [条数]|cancel|clear]");
            return;
        }
        JSONObject stats = command.service().toolMemoryService().stats(groupID);
        StringBuilder builder = new StringBuilder("工具调用统计：")
                .append("\n累计记录：").append(stats.getIntValue("count"));
        JSONArray tools = stats.getJSONArray("tools");
        if (tools != null) {
            for (Object object : tools) {
                JSONObject item = (JSONObject)object;
                builder.append("\n- ").append(item.getString("tool"))
                        .append("：").append(item.getIntValue("count"));
            }
        }
        builder.append("\n工具结果最多保留 ")
                .append(command.service().config().toolMemoryKeepHours).append(" 小时。");
        sender.sendMessage(builder.toString());
    }

    private String formatToolTime(long time) {
        if (time <= 0) return "未知时间";
        return new SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(new Date(time));
    }

    void handleEval(CommandSender sender,String[] args) {
        RoleplayEvalService eval = command.service().getEvalService();
        String action = args.length > 2 ? args[2].toLowerCase() : "list";
        if ("list".equals(action)) {
            JSONArray cases = eval.list();
            if (cases.isEmpty()) {
                sender.sendMessage("没有可用的评测用例。");
                return;
            }
            StringBuilder builder = new StringBuilder("评测用例：");
            for (Object object : cases) {
                JSONObject item = (JSONObject)object;
                builder.append("\n").append(item.getString("id"))
                        .append(" - ").append(item.getString("name"));
            }
            sender.sendMessage(builder.toString());
            return;
        }
        if (!"run".equals(action)) {
            sender.sendMessage("用法：/role eval [list|run <id|all>]");
            return;
        }
        String target = args.length > 3 ? args[3] : "all";
        if ("all".equalsIgnoreCase(target)) {
            JSONArray cases = eval.list();
            int passed = 0;
            StringBuilder builder = new StringBuilder("评测结果：");
            for (Object object : cases) {
                JSONObject item = (JSONObject)object;
                JSONObject result = eval.run(item.getString("id"));
                if (result.getBooleanValue("pass")) passed++;
                builder.append("\n").append(item.getString("id"))
                        .append(" ").append(result.getBooleanValue("pass") ? "通过" : "失败")
                        .append(result.getString("reason").isEmpty() ? "" : "："+result.getString("reason"));
            }
            builder.append("\n通过 ").append(passed).append("/").append(cases.size());
            sender.sendMessage(builder.toString());
            return;
        }
        JSONObject result = eval.run(target);
        sender.sendMessage("评测 "+target+"："
                +(result.getBooleanValue("pass") ? "通过" : "失败")
                +(result.getString("reason").isEmpty() ? "" : "（"+result.getString("reason")+"）")
                +(result.getString("reply").isEmpty() ? "" : "\n回复："+result.getString("reply")));
    }
}
