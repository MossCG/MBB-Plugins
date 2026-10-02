package org.moboxlab.mbb.chatstat;

import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Command.BotCommand;
import org.moboxlab.moboxbot.API.Command.CommandPermission;
import org.moboxlab.moboxbot.API.Command.CommandSender;
import org.moboxlab.moboxbot.API.Util.ImageUtil;

import java.util.ArrayList;
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
    public boolean execute(CommandSender sender,String[] args) {
        if (args.length < 2) {
            sender.sendMessage("用法：/chatstat group [群号] [天数] | /chatstat user <QQ> [天数]");
            return true;
        }
        String action = args[1].toLowerCase();
        if ("group".equals(action)) {
            return groupStats(sender,args);
        }
        if ("user".equals(action)) {
            return userStats(sender,args);
        }
        sender.sendMessage("用法：/chatstat group [群号] [天数] | /chatstat user <QQ> [天数]");
        return true;
    }

    private boolean groupStats(CommandSender sender,String[] args) {
        long groupID = sender.getGroupID();
        int dayIndex = 2;
        if (args.length >= 3) {
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
        int days = parseDays(args,dayIndex);
        JSONObject stats = statsService.groupStats(groupID,days);
        sendImage(sender,stats,"群聊统计图片生成失败！");
        return true;
    }

    private boolean userStats(CommandSender sender,String[] args) {
        if (args.length < 3) {
            sender.sendMessage("用法：/chatstat user <QQ> [天数]");
            return true;
        }
        long userID;
        try {
            userID = Long.parseLong(args[2]);
        } catch (Exception e) {
            sender.sendMessage("QQ 号格式不正确！");
            return true;
        }
        int days = parseDays(args,3);
        JSONObject stats = statsService.userStats(userID,days);
        sendImage(sender,stats,"用户群聊统计图片生成失败！");
        return true;
    }

    private void sendImage(CommandSender sender,JSONObject stats,String failMessage) {
        byte[] image = ChatStatImageRenderer.render(stats);
        if (image != null) {
            sender.sendImage(ImageUtil.toBase64Uri(image));
        } else {
            sender.sendMessage(failMessage);
        }
    }

    private int parseDays(String[] args,int index) {
        int days = plugin.getConfig().getInt("defaultDays",1);
        if (args.length > index) {
            try {
                days = Integer.parseInt(args[index]);
            } catch (Exception ignored) {
            }
        }
        if (days < 1) days = 1;
        if (days > 30) days = 30;
        return days;
    }
}
