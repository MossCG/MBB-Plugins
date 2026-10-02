package org.moboxlab.mbb.aiguard;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Command.BotCommand;
import org.moboxlab.moboxbot.API.Command.CommandPermission;
import org.moboxlab.moboxbot.API.Command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * /guard 风险审查管理命令
 */
public class GuardCommand extends BotCommand {
    private final GuardPlugin plugin;
    private final GuardService guardService;

    public GuardCommand(GuardPlugin plugin,GuardService guardService) {
        this.plugin = plugin;
        this.guardService = guardService;
    }

    @Override
    public List<String> prefix() {
        List<String> prefixList = new ArrayList<>();
        prefixList.add("guard");
        return prefixList;
    }

    @Override
    public CommandPermission permission() {
        return CommandPermission.BOT_ADMIN;
    }

    @Override
    public int cooldownSeconds() {
        return 3;
    }

    @Override
    public String description() {
        return "管理 AI 群聊风险审查";
    }

    @Override
    public List<String> usage() {
        return Arrays.asList(
                "/guard status",
                "/guard enable [群号]",
                "/guard disable [群号]",
                "/guard groups",
                "/guard threshold <分数>",
                "/guard test <文本>",
                "/guard log [页码]",
                "/guard whitelist list [群号]",
                "/guard whitelist add <QQ> [群号]",
                "/guard whitelist remove <QQ> [群号]");
    }

    @Override
    public boolean execute(CommandSender sender,String[] args) {
        if (args.length < 2) {
            sender.sendMessage("用法：/guard status | enable/disable | groups | threshold | test | log | whitelist");
            return true;
        }
        String action = args[1].toLowerCase();
        if ("status".equals(action)) {
            sendStatus(sender);
            return true;
        }
        if ("groups".equals(action)) {
            sendGroups(sender);
            return true;
        }
        if ("enable".equals(action) || "disable".equals(action)) {
            setGroup(sender,args,"enable".equals(action));
            return true;
        }
        if ("threshold".equals(action)) {
            setThreshold(sender,args);
            return true;
        }
        if ("test".equals(action)) {
            testText(sender,args);
            return true;
        }
        if ("log".equals(action)) {
            sendLog(sender,args);
            return true;
        }
        if ("whitelist".equals(action)) {
            handleWhitelist(sender,args);
            return true;
        }
        sender.sendMessage("未知 /guard 子命令："+action);
        return true;
    }

    private void sendStatus(CommandSender sender) {
        long groupID = sender.getGroupID();
        boolean enabled = groupID > 0 && guardService.isGroupEnabled(groupID);
        sender.sendMessage("MBB-AIGuard 状态：\n"
                +"全局："+(plugin.getGuardConfig().enable ? "启用" : "关闭")+"\n"
                +"当前群："+(groupID > 0 ? (enabled ? "已开启" : "未开启") : "非群聊")+"\n"
                +"当前群阈值："+(groupID > 0 ? guardService.getGroupThreshold(groupID) : plugin.getGuardConfig().riskThreshold)+"\n"
                +"规则数："+guardService.ruleCount()+"\n"
                +"AI 配置："+plugin.getGuardConfig().aiProfile+"\n"
                +"模式："+(plugin.getGuardConfig().observeOnly ? "观察告警" : "告警"));
    }

    private void sendGroups(CommandSender sender) {
        JSONArray groups = guardService.listEnabledGroups();
        if (groups.isEmpty()) {
            sender.sendMessage("当前没有开启风险审查的群。");
            return;
        }
        StringBuilder builder = new StringBuilder("已开启审查的群：");
        for (int i = 0; i < groups.size(); i++) {
            JSONObject group = groups.getJSONObject(i);
            builder.append("\n").append(group.getLongValue("groupID"))
                    .append("，阈值 ").append(group.getIntValue("threshold"));
        }
        sender.sendMessage(builder.toString());
    }

    private void setGroup(CommandSender sender,String[] args,boolean enabled) {
        long groupID = sender.getGroupID();
        if (args.length > 2) {
            try {
                groupID = Long.parseLong(args[2]);
            } catch (Exception e) {
                sender.sendMessage("群号格式不正确！");
                return;
            }
        }
        if (groupID <= 0) {
            sender.sendMessage("请在群聊中使用，或指定群号：/guard "+(enabled ? "enable" : "disable")+" <群号>");
            return;
        }
        guardService.setGroupEnabled(groupID,enabled);
        sender.sendMessage("群 "+groupID+" 的风险审查已"+(enabled ? "开启" : "关闭")+"。");
    }

    private void setThreshold(CommandSender sender,String[] args) {
        long groupID = sender.getGroupID();
        if (groupID <= 0) {
            sender.sendMessage("请在群聊中使用。");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("用法：/guard threshold <分数>");
            return;
        }
        int threshold;
        try {
            threshold = Integer.parseInt(args[2]);
        } catch (Exception e) {
            sender.sendMessage("阈值必须是 1-100 的数字。");
            return;
        }
        guardService.setGroupThreshold(groupID,threshold);
        sender.sendMessage("当前群告警阈值已设置为："+guardService.getGroupThreshold(groupID));
    }

    private void testText(CommandSender sender,String[] args) {
        if (args.length < 3) {
            sender.sendMessage("用法：/guard test <文本>");
            return;
        }
        String text = joinArgs(args,2);
        plugin.getServer().getPluginManager().runTask(plugin,() -> {
            JSONObject result = guardService.test(sender.getGroupID(),sender.getUserID(),text);
            sender.sendMessage("风险测试结果：\n"
                    +"分数："+result.getIntValue("score")+"\n"
                    +"分类："+safe(result.getString("categories"))+"\n"
                    +"原因："+safe(result.getString("reason"))+"\n"
                    +"证据："+safe(result.getString("evidence"))+"\n"
                    +"建议动作："+safe(result.getString("action"))
                    +(result.getBooleanValue("safety") ? "\n安全分支：是" : ""));
        });
    }

    private void sendLog(CommandSender sender,String[] args) {
        int limit = 10;
        if (args.length > 2) {
            try {
                int page = Integer.parseInt(args[2]);
                if (page > 0) limit = Math.min(50,page * 10);
            } catch (Exception ignored) {
            }
        }
        JSONArray events = guardService.recentEvents(limit);
        if (events.isEmpty()) {
            sender.sendMessage("暂无风险审查记录。");
            return;
        }
        StringBuilder builder = new StringBuilder("最近风险事件：");
        for (int i = 0; i < events.size(); i++) {
            JSONObject event = events.getJSONObject(i);
            builder.append("\n#").append(event.getLongValue("id"))
                    .append(" 群").append(event.getLongValue("groupID"))
                    .append(" 用户").append(event.getLongValue("userID"))
                    .append(" 分数").append(event.getIntValue("score"))
                    .append(" 分类").append(safe(event.getString("categories")));
        }
        sender.sendMessage(builder.toString());
    }

    private void handleWhitelist(CommandSender sender,String[] args) {
        String action = args.length > 2 ? args[2].toLowerCase() : "list";
        long groupID = sender.getGroupID();
        if ("list".equals(action)) {
            long queryGroup = groupID;
            if (args.length > 3) {
                try {
                    queryGroup = Long.parseLong(args[3]);
                } catch (Exception e) {
                    sender.sendMessage("群号格式不正确！");
                    return;
                }
            }
            JSONArray list = guardService.listWhitelist(queryGroup);
            if (list.isEmpty()) {
                sender.sendMessage("白名单为空。");
                return;
            }
            StringBuilder builder = new StringBuilder("白名单：");
            for (int i = 0; i < list.size(); i++) {
                JSONObject item = list.getJSONObject(i);
                builder.append("\n").append(item.getString("scope"))
                        .append(" ").append(item.getLongValue("userID"));
                if ("group".equals(item.getString("scope"))) {
                    builder.append(" 群").append(item.getLongValue("groupID"));
                }
            }
            sender.sendMessage(builder.toString());
            return;
        }
        if (!"add".equals(action) && !"remove".equals(action)) {
            sender.sendMessage("用法：/guard whitelist [list|add <QQ> [群号]|remove <QQ> [群号]]");
            return;
        }
        if (args.length < 4) {
            sender.sendMessage("用法：/guard whitelist "+action+" <QQ> [群号]");
            return;
        }
        long userID;
        try {
            userID = Long.parseLong(args[3]);
        } catch (Exception e) {
            sender.sendMessage("QQ 号格式不正确！");
            return;
        }
        long scopeGroup = 0L;
        if (args.length > 4) {
            try {
                scopeGroup = Long.parseLong(args[4]);
            } catch (Exception e) {
                sender.sendMessage("群号格式不正确！");
                return;
            }
        }
        if (scopeGroup <= 0 && !sender.hasPermission(CommandPermission.OWNER)) {
            sender.sendMessage("只有机器人所有者可以管理全局白名单！");
            return;
        }
        String scope = scopeGroup > 0 ? "group" : "global";
        boolean changed = "add".equals(action)
                ? guardService.addWhitelist(scope,userID,scopeGroup,sender.getUserID())
                : guardService.removeWhitelist(scope,userID,scopeGroup);
        sender.sendMessage("白名单"+(changed ? "操作成功" : "未发生变化")+"。");
    }

    private String joinArgs(String[] args,int start) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (builder.length() > 0) builder.append(" ");
            builder.append(args[i]);
        }
        return builder.toString().trim();
    }

    private String safe(String value) {
        return value == null ? "无" : value;
    }
}
