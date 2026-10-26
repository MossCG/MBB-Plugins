package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Command.BotCommand;
import org.moboxlab.moboxbot.API.Command.CommandPermission;
import org.moboxlab.moboxbot.API.Command.CommandSender;
import org.moboxlab.moboxbot.API.Util.ImageUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * /role 角色扮演管理命令
 */
public class RoleplayCommand extends BotCommand {
    private static final Pattern CQ_AT_QQ = Pattern.compile("(?i)\\[CQ:at,[^\\]]*?qq=([^,\\]]+)");
    private static final Pattern TEXT_AT_QQ = Pattern.compile("(?<!\\S)@(\\d+)");

    private final RoleplayPlugin plugin;
    private final RoleplayService service;
    private final RoleplayMemoryCommandHandler roleplayMemoryCommandHandler;
    private final RoleplayEmotionCommandHandler roleplayEmotionCommandHandler;
    private final RoleplayModerationCommandHandler roleplayModerationCommandHandler;
    private final RoleplayContentCommandHandler roleplayContentCommandHandler;
    private final RoleplayFeedbackCommandHandler roleplayFeedbackCommandHandler;
    private final RoleplayMemberCommandHandler roleplayMemberCommandHandler;
    private final RoleplayBotCommandHandler roleplayBotCommandHandler;
    private final RoleplayTrainCommandHandler roleplayTrainCommandHandler;

    RoleplayService service() {
        return service;
    }

    RoleplayPlugin plugin() {
        return plugin;
    }

    public RoleplayCommand(RoleplayPlugin plugin,RoleplayService service) {
        this.plugin = plugin;
        this.service = service;
        this.roleplayMemoryCommandHandler = new RoleplayMemoryCommandHandler(this);
        this.roleplayEmotionCommandHandler = new RoleplayEmotionCommandHandler(this);
        this.roleplayModerationCommandHandler = new RoleplayModerationCommandHandler(this);
        this.roleplayContentCommandHandler = new RoleplayContentCommandHandler(this);
        this.roleplayFeedbackCommandHandler = new RoleplayFeedbackCommandHandler(this);
        this.roleplayMemberCommandHandler = new RoleplayMemberCommandHandler(this);
        this.roleplayBotCommandHandler = new RoleplayBotCommandHandler(this);
        this.roleplayTrainCommandHandler = new RoleplayTrainCommandHandler(this);
    }

    @Override
    public List<String> prefix() {
        List<String> prefixList = new ArrayList<>();
        prefixList.add("role");
        return prefixList;
    }

    @Override
    public CommandPermission permission() {
        //quiet add/remove 允许群员自助使用，其余子命令在 execute 内按 BOT_ADMIN 拦截
        return CommandPermission.EVERYONE;
    }

    @Override
    public int cooldownSeconds() {
        return 3;
    }

    @Override
    public String description() {
        return "管理角色扮演插件";
    }

    @Override
    public List<String> usage() {
        return Arrays.asList(
                "/role help [分类]",
                "/role status",
                "/role random",
                "/role context [status|compact|clear]",
                "/role quiet add|remove");
    }

    @Override
    public boolean execute(CommandSender sender,String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase() : "status";
        if ("help".equals(action)) {
            handleHelp(sender,args);
            return true;
        }
        if ("quiet".equals(action)) {
            handleQuiet(sender,args);
            return true;
        }
        if (!sender.hasPermission(CommandPermission.BOT_ADMIN)) {
            sender.sendMessage("管理角色扮演插件需要机器人管理员权限。");
            return true;
        }
        if ("status".equals(action)) {
            JSONObject result = service.status(sender.getGroupID());
            sender.sendMessage("角色："+result.getString("role")
                    +"\n当前群："+(result.getBooleanValue("groupEnabled") ? "已开启" : "未开启")
                    +"\n长期记忆数："+result.getIntValue("memoryCount")
                    +"\n短期记忆："+result.getString("shortSummary")
                    +"\n当前情绪："+result.getJSONObject("mood").getString("label"));
            return true;
        }
        if ("random".equals(action)) {
            JSONObject result = service.randomTickService().status();
            JSONObject activity = service.activityScheduler().status();
            StringBuilder message = new StringBuilder();
            message.append("随机刻：").append(result.getBooleanValue("enable") ? "开" : "关")
                    .append("\n下次时间：").append(service.formatMemoryTime(result.getLongValue("dueTime")))
                    .append("\n上次执行：").append(service.formatMemoryTime(result.getLongValue("lastRunTime")))
                    .append("\n上次行为：").append(result.getString("lastAction"))
                    .append(" ").append(result.getString("lastStatus"))
                    .append("\n原因：").append(result.getString("lastReason"))
                    .append("\n活动调度：").append(activity.getBooleanValue("enable") ? "开" : "关")
                    .append("\n活动插件：").append(activity.getJSONArray("services").isEmpty()
                            ? "未配置"
                            : String.join("、",activity.getJSONArray("services").toJavaList(String.class)));
            sender.sendMessage(message.toString());
            return true;
        }
        if ("context".equals(action)) {
            long groupID = sender.getGroupID();
            if (groupID <= 0) {
                sender.sendMessage("请在群聊中使用。");
                return true;
            }
            String sub = args.length > 2 ? args[2].toLowerCase() : "status";
            if ("clear".equals(sub)) {
                service.conversationContextService().clear("group",groupID);
                sender.sendMessage("当前群的追加式会话上下文已清空。");
                return true;
            }
            if ("compact".equals(sub)) {
                service.submitBackground(() -> {
                    boolean compacted = service.conversationContextService().compactNow("group",groupID);
                    sender.sendMessage(compacted
                            ? "当前群会话上下文已压缩。"
                            : "当前群可供压缩的消息不足。");
                });
                sender.sendMessage("开始压缩当前群会话上下文。");
                return true;
            }
            JSONObject status = service.conversationContextService().status("group",groupID);
            sender.sendMessage("追加式上下文："+(status.getBooleanValue("enable") ? "开" : "关")
                    +"\n窗口上限："+status.getIntValue("tokenLimit")+" Token（按字符保守估算）"
                    +"\n当前估算："+status.getLongValue("estimatedChars")+" Token"
                    +"\n窗口消息："+status.getIntValue("messageCount")+" 条"
                    +"\n压缩摘要："+status.getIntValue("summaryChars")+" 字符"
                    +"\n压缩游标："+status.getLongValue("compactedThroughID"));
            return true;
        }
        if ("groups".equals(action)) {
            JSONArray groups = service.listGroups();
            if (groups.isEmpty()) {
                sender.sendMessage("当前没有开启角色扮演的群。");
            } else {
                StringBuilder builder = new StringBuilder("已开启群：");
                for (Object group : groups) builder.append("\n").append(group);
                sender.sendMessage(builder.toString());
            }
            return true;
        }
        if ("enable".equals(action) || "disable".equals(action)) {
            long groupID = sender.getGroupID();
            if (args.length > 2) {
                try {
                    groupID = Long.parseLong(args[2]);
                } catch (Exception e) {
                    sender.sendMessage("群号格式不正确！");
                    return true;
                }
            }
            if (groupID <= 0) {
                sender.sendMessage("请在群聊中使用，或指定群号。");
                return true;
            }
            service.setGroupEnabled(groupID,"enable".equals(action));
            sender.sendMessage("群 "+groupID+" 的角色扮演已"+("enable".equals(action) ? "开启" : "关闭")+"。");
            return true;
        }
        if ("reload".equals(action)) {
            plugin.reloadRoleplay();
            sender.sendMessage("角色设定和配置已重载。");
            return true;
        }
        if ("config".equals(action)) {
            handleConfig(sender,args);
            return true;
        }
        if ("mood".equals(action)) {
            handleMood(sender,args);
            return true;
        }
        if ("emotion".equals(action)) {
            handleEmotion(sender,args);
            return true;
        }
        if ("blacklist".equals(action)) {
            handleBlacklist(sender,args);
            return true;
        }
        if ("feedback".equals(action)) {
            handleFeedback(sender,args);
            return true;
        }
        if ("expression".equals(action)) {
            handleExpression(sender,args);
            return true;
        }
        if ("review".equals(action)) {
            handleReview(sender,args);
            return true;
        }
        if ("attention".equals(action)) {
            handleAttention(sender,args);
            return true;
        }
        if ("trace".equals(action)) {
            handleTrace(sender,args);
            return true;
        }
        if ("tool".equals(action)) {
            handleTool(sender,args);
            return true;
        }
        if ("eval".equals(action)) {
            handleEval(sender,args);
            return true;
        }
        if ("member".equals(action)) {
            handleMember(sender,args);
            return true;
        }
        if ("bot".equals(action)) {
            handleBot(sender,args);
            return true;
        }
        if ("memory".equals(action)) {
            handleMemory(sender,args);
            return true;
        }
        if ("gmemory".equals(action)) {
            handleGlobalMemory(sender,args);
            return true;
        }
        if ("speech".equals(action)) {
            handleSpeech(sender,args);
            return true;
        }
        if ("train".equals(action)) {
            handleTrain(sender,args);
            return true;
        }
        if ("kb".equals(action) || "knowledge".equals(action)) {
            handleKnowledge(sender,args);
            return true;
        }
        if ("forget".equals(action)) {
            long groupID = sender.getGroupID();
            if (groupID <= 0) {
                sender.sendMessage("请在群聊中使用。");
                return true;
            }
            service.clearMemory(groupID);
            service.conversationContextService().clear("group",groupID);
            sender.sendMessage("当前群的角色记忆、聊天上下文和 AI 会话已清空。");
            return true;
        }
        if ("persona".equals(action)) {
            if (args.length < 3) {
                sender.sendMessage("当前角色设定文件："+plugin.getPersonaFileName()
                        +"\n内置示例：persona-aris.json；其他 persona 请放入插件数据目录后自行切换。");
                return true;
            }
            if ("reset".equalsIgnoreCase(args[2])) {
                if (args.length < 4) {
                    sender.sendMessage("用法：/role persona reset <文件名>");
                    return true;
                }
                boolean reset = plugin.resetPersona(args[3]);
                sender.sendMessage(reset
                        ? "角色设定已重置为内置版本："+args[3]
                        : "重置失败，请检查文件名或插件资源。");
                return true;
            }
            boolean changed = plugin.switchPersona(args[2]);
            sender.sendMessage(changed ? "角色设定已切换："+args[2] : "切换失败，请检查文件名是否存在。");
            return true;
        }
        sender.sendMessage("用法：/role status | random | context | enable/disable | groups | reload | memory | forget | persona");
        return true;
    }




    String safeList(String value) {
        return value == null || value.trim().isEmpty() ? "空" : value.trim();
    }



    String joinArgs(String[] args,int start) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (builder.length() > 0) builder.append(" ");
            builder.append(args[i]);
        }
        return builder.toString().trim();
    }


    private void handleMemory(CommandSender sender,String[] args) {
        roleplayMemoryCommandHandler.handleMemory(sender,args);
    }

    private void handleGlobalMemory(CommandSender sender,String[] args) {
        roleplayMemoryCommandHandler.handleGlobalMemory(sender,args);
    }

    private void handleGlobalMemoryGroups(CommandSender sender,String[] args) {
        roleplayMemoryCommandHandler.handleGlobalMemoryGroups(sender,args);
    }

    private void handleMood(CommandSender sender,String[] args) {
        roleplayEmotionCommandHandler.handleMood(sender,args);
    }

    private void handleEmotion(CommandSender sender,String[] args) {
        roleplayEmotionCommandHandler.handleEmotion(sender,args);
    }

    private void handleEmotionReason(CommandSender sender,String[] args,long groupID) {
        roleplayEmotionCommandHandler.handleEmotionReason(sender,args,groupID);
    }

    private void handleEmotionAffinity(CommandSender sender,String[] args,long groupID) {
        roleplayEmotionCommandHandler.handleEmotionAffinity(sender,args,groupID);
    }

    private void handleBlacklist(CommandSender sender,String[] args) {
        roleplayModerationCommandHandler.handleBlacklist(sender,args);
    }

    private void handleQuiet(CommandSender sender,String[] args) {
        roleplayModerationCommandHandler.handleQuiet(sender,args);
    }

    private void handleSpeech(CommandSender sender,String[] args) {
        roleplayContentCommandHandler.handleSpeech(sender,args);
    }

    private void handleTrain(CommandSender sender,String[] args) {
        roleplayTrainCommandHandler.handleTrain(sender,args);
    }

    private void handleKnowledge(CommandSender sender,String[] args) {
        roleplayContentCommandHandler.handleKnowledge(sender,args);
    }

    private void handleFeedback(CommandSender sender,String[] args) {
        roleplayFeedbackCommandHandler.handleFeedback(sender,args);
    }

    private void handleExpression(CommandSender sender,String[] args) {
        roleplayFeedbackCommandHandler.handleExpression(sender,args);
    }

    private void handleReview(CommandSender sender,String[] args) {
        roleplayFeedbackCommandHandler.handleReview(sender,args);
    }

    private void handleAttention(CommandSender sender,String[] args) {
        roleplayFeedbackCommandHandler.handleAttention(sender,args);
    }

    private void handleTrace(CommandSender sender,String[] args) {
        roleplayFeedbackCommandHandler.handleTrace(sender,args);
    }

    private void handleTool(CommandSender sender,String[] args) {
        roleplayFeedbackCommandHandler.handleTool(sender,args);
    }

    private void handleEval(CommandSender sender,String[] args) {
        roleplayFeedbackCommandHandler.handleEval(sender,args);
    }

    private void handleMember(CommandSender sender,String[] args) {
        roleplayMemberCommandHandler.handleMember(sender,args);
    }

    private void handleHelp(CommandSender sender,String[] args) {
        roleplayMemberCommandHandler.handleHelp(sender,args);
    }

    private void handleBot(CommandSender sender,String[] args) {
        roleplayBotCommandHandler.handleBot(sender,args);
    }

    private void handleBotCsv(CommandSender sender,String[] args,boolean qq) {
        roleplayBotCommandHandler.handleBotCsv(sender,args,qq);
    }

    private void sendBotStatus(CommandSender sender) {
        roleplayBotCommandHandler.sendBotStatus(sender);
    }

    private void handleConfig(CommandSender sender,String[] args) {
        if (args.length > 2 && "repair".equalsIgnoreCase(args[2])) {
            int repaired = plugin.repairConfig();
            sender.sendMessage(repaired > 0
                    ? "配置补全完成，共处理 "+repaired+" 项。"
                    : "配置没有缺失项，无需补全。");
            return;
        }
        sender.sendMessage("Roleplay 配置：\n"
                +"文件："+plugin.getConfig().getPath()+"\n"
                +"结构版本："+plugin.getConfig().getString("configVersion","未知")+"\n"
                +"缺失配置会在启动和重载时自动补全。\n"
                +"手动补全：/role config repair");
    }






    /**
     * /role quiet：免打扰名单，名单内的群员只在主动叫到角色时才得到回复
     */

    /**
     * /role review [now]：手动触发当前群每日复盘
     */

    /**
     * /role attention [reset]：查看或重置当前群注意力
     */
    /**
     * /role help：按分类查看完整命令表
     */
    /**
     * /role feedback：查看或重置主动发言反馈统计
     */

    /**
     * /role expression：复核群内表达候选
     */
    /**
     * /role member [QQ]：查看角色对某个群员的个人印象
     */

    String orDash(String value) {
        return value == null || value.trim().isEmpty() ? "暂无" : value.trim();
    }

    long parseUserID(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) return 0L;
        Matcher cq = CQ_AT_QQ.matcher(text);
        if (cq.find()) text = cleanAtValue(cq.group(1));
        else {
            Matcher plain = TEXT_AT_QQ.matcher(text);
            if (plain.find()) text = plain.group(1);
        }
        try {
            long userID = Long.parseLong(text);
            return userID > 0 ? userID : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private String cleanAtValue(String value) {
        String text = value == null ? "" : value.trim();
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1,text.length() - 1).trim();
        }
        return text;
    }

    int parsePage(String[] args,int index) {
        if (args == null || args.length <= index) return 1;
        try {
            int page = Integer.parseInt(args[index]);
            return page < 1 ? 1 : page;
        } catch (Exception e) {
            return 1;
        }
    }

    String formatDecimal(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.001) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(java.util.Locale.CHINA,"%.1f",value);
    }




    Set<String> splitCsv(String text) {
        Set<String> result = new LinkedHashSet<>();
        if (text == null || text.trim().isEmpty()) return result;
        for (String item : text.split("[,，]")) {
            String value = item.trim();
            if (!value.isEmpty()) result.add(value);
        }
        return result;
    }

    String joinCsv(Set<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) builder.append(",");
            builder.append(value);
        }
        return builder.toString();
    }
}
