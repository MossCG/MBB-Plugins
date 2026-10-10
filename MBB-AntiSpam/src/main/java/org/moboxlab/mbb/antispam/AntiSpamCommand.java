package org.moboxlab.mbb.antispam;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Command.BotCommand;
import org.moboxlab.moboxbot.API.Command.CommandPermission;
import org.moboxlab.moboxbot.API.Command.CommandSender;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * /antispam 刷屏治理管理命令
 */
public class AntiSpamCommand extends BotCommand {
    private final AntiSpamPlugin plugin;
    private final AntiSpamService spamService;

    public AntiSpamCommand(AntiSpamPlugin plugin,AntiSpamService spamService) {
        this.plugin = plugin;
        this.spamService = spamService;
    }

    @Override
    public List<String> prefix() {
        List<String> prefixList = new ArrayList<>();
        prefixList.add("antispam");
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
        return "管理群聊刷屏治理";
    }

    @Override
    public List<String> usage() {
        return Arrays.asList(
                "/antispam status",
                "/antispam on [群号]",
                "/antispam off [群号]",
                "/antispam groups",
                "/antispam log [页码]",
                "/antispam clear",
                "/antispam reset");
    }

    @Override
    public boolean execute(CommandSender sender,String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase() : "status";
        AntiSpamConfig config = plugin.getAntiSpamConfig();
        if ("status".equals(action)) {
            long groupID = sender.getGroupID();
            sendStatus(sender,config,groupID);
            return true;
        }
        if ("groups".equals(action)) {
            JSONArray groups = spamService.listEnabledGroups();
            if (groups.isEmpty()) {
                sender.sendMessage("还没有任何群单独开启过刷屏治理"
                        +"（新群按 defaultGroupEnable="+config.defaultGroupEnable+" 处理）。");
                return true;
            }
            StringBuilder builder = new StringBuilder("已单独开启刷屏治理的群：");
            for (Object object : groups) {
                builder.append("\n").append(((JSONObject) object).getLongValue("groupID"));
            }
            sender.sendMessage(builder.toString());
            return true;
        }
        if ("on".equals(action) || "off".equals(action)) {
            long groupID = resolveGroupID(sender,args);
            if (groupID <= 0) {
                sender.sendMessage("请在群聊中使用，或指定群号。");
                return true;
            }
            spamService.setGroupEnabled(groupID,"on".equals(action));
            sender.sendMessage("群 "+groupID+" 的刷屏治理已"
                    +("on".equals(action) ? "开启" : "关闭")+"。");
            return true;
        }
        if ("log".equals(action)) {
            long groupID = sender.getGroupID();
            if (groupID <= 0) {
                sender.sendMessage("请在群聊中使用。");
                return true;
            }
            int page = args.length > 2 ? parsePage(args[2]) : 1;
            int limit = 10;
            JSONArray events = spamService.recentEvents(groupID,limit);
            if (events.isEmpty()) {
                sender.sendMessage("当前群还没有刷屏记录。");
                return true;
            }
            int total = spamService.countEvents(groupID);
            StringBuilder builder = new StringBuilder("当前群刷屏记录，共 "+total+" 条，"
                    +"第 "+page+" 页（最多 "+limit+" 条）：");
            SimpleDateFormat format = new SimpleDateFormat("MM-dd HH:mm",Locale.CHINA);
            for (Object object : events) {
                JSONObject item = (JSONObject) object;
                builder.append("\n").append(format.format(new Date(item.getLongValue("eventTime"))))
                        .append(" ").append(item.getLongValue("userID"))
                        .append(" ").append(item.getString("rule"))
                        .append(" 第").append(item.getIntValue("violationCount")).append("次")
                        .append(" 处置=").append(actionText(item.getString("action")));
            }
            sender.sendMessage(builder.toString());
            return true;
        }
        if ("clear".equals(action)) {
            long groupID = sender.getGroupID();
            if (groupID <= 0) {
                sender.sendMessage("请在群聊中使用。");
                return true;
            }
            int count = spamService.clearEvents(groupID);
            sender.sendMessage("当前群刷屏记录已清空，共删除 "+count+" 条。");
            return true;
        }
        if ("reset".equals(action)) {
            spamService.resetWindows();
            sender.sendMessage("全部群的刷屏统计窗口已重置。");
            return true;
        }
        sender.sendMessage("用法：/antispam status | on/off [群号] | groups | log [页码] | clear | reset");
        return true;
    }

    private void sendStatus(CommandSender sender,AntiSpamConfig config,long groupID) {
        boolean groupEnabled = groupID > 0 ? spamService.isGroupEnabled(groupID) : config.defaultGroupEnable;
        sender.sendMessage("刷屏治理状态："
                +"\n插件开关："+(config.enable ? "开" : "关")
                +"\n当前群："+(groupID <= 0 ? "私聊（按默认值显示）" : String.valueOf(groupID))
                +" -> "+(groupEnabled ? "开启" : "关闭")
                +"\n默认动作："+config.defaultAction
                +"\n限流："+(config.rateEnable ? "开" : "关")
                +"（"+config.rateWindowSecond+" 秒 "+config.rateMaxMessages+" 条 / "
                +config.rateLongWindowSecond+" 秒 "+config.rateLongMaxMessages+" 条）"
                +"\n复读："+(config.repeatEnable ? "开" : "关")
                +"（"+config.repeatWindowSecond+" 秒 "+config.repeatMaxCount+" 次 / "
                +config.repeatLongWindowSecond+" 秒 "+config.repeatLongMaxCount+" 次）"
                +"\n超长文本："+(config.longTextEnable ? config.longTextMaxChars+" 字符" : "关")
                +"\n图片表情连发："+(config.mediaFloodEnable
                ? config.mediaFloodMaxCount+" 个起记录（只记录不处置）" : "关")
                +"\n艾特刷屏："+(config.mentionEnable ? config.mentionWindowSecond+" 秒 "
                +config.mentionMaxCount+" 人" : "关")
                +"（@全体："+(config.mentionAllEnable ? "直接命中" : "不处理")+"）"
                +"\n人性化设置：首次命中"+(config.forgiveFirst ? "只提醒" : "直接计数")
                +" / 提醒方式"+(config.noticeEnable
                ? "群内艾特（"+config.noticeCooldownSecond+" 秒冷却）" : "关")
                +" / 多人玩梗"+(config.repeatBanterForgive
                ? "不处罚（≥"+config.repeatBanterMinUsers+" 人）" : "照常计数")
                +"\n违规计数："+("message".equals(config.violationCountMode)
                ? "每条命中都计数" : "同一波只算一次（"+config.violationCooldownSecond+" 秒内合并）")
                +"\n处置阶梯：撤回≥"+config.deleteAfterViolations
                +" 次 / 禁言≥"+config.banAfterViolations+" 次"
                +"（窗口 "+config.violationWindowSecond+" 秒，禁言 "
                +config.banDurationMinute+" 分钟）"
                +"\n注意：撤回消息和禁言都需要机器人是该群管理员，否则会被 QQ 拒绝"
                +"\n管理员豁免："+(config.bypassAdmin ? "是" : "否")
                +"\n当前追踪用户数："+spamService.trackedUsers());
    }

    private long resolveGroupID(CommandSender sender,String[] args) {
        if (args.length > 2) {
            try {
                return Long.parseLong(args[2].trim());
            } catch (Exception e) {
                return 0L;
            }
        }
        return sender.getGroupID();
    }

    private int parsePage(String value) {
        try {
            int page = Integer.parseInt(value == null ? "" : value.trim());
            return page < 1 ? 1 : page;
        } catch (Exception e) {
            return 1;
        }
    }

    private String actionText(String action) {
        if (action == null || action.trim().isEmpty()) return "记录";
        if ("forgiven".equals(action)) return "仅提醒";
        return action.trim();
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "无" : value.trim();
    }
}
