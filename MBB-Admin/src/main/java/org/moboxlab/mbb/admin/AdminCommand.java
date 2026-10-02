package org.moboxlab.mbb.admin;

import org.moboxlab.moboxbot.API.Command.BotCommand;
import org.moboxlab.moboxbot.API.Command.CommandPermission;
import org.moboxlab.moboxbot.API.Command.CommandSender;
import org.moboxlab.moboxbot.API.MoBoxBotAPI;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * /admin 管理员管理命令
 */
public class AdminCommand extends BotCommand {
    @Override
    public List<String> prefix() {
        List<String> prefixList = new ArrayList<>();
        prefixList.add("admin");
        return prefixList;
    }

    @Override
    public CommandPermission permission() {
        return CommandPermission.OWNER;
    }

    @Override
    public int cooldownSeconds() {
        return 3;
    }

    @Override
    public String description() {
        return "增删查机器人管理员";
    }

    @Override
    public List<String> usage() {
        return Arrays.asList(
                "/admin list",
                "/admin add <QQ>",
                "/admin remove <QQ>");
    }

    @Override
    public boolean execute(CommandSender sender,String[] args) {
        if (args.length < 2 || "list".equalsIgnoreCase(args[1])) {
            List<Long> admins = MoBoxBotAPI.getServer().getAdminList();
            if (admins.isEmpty()) {
                sender.sendMessage("当前没有额外管理员，仅所有者拥有最高权限。");
                return true;
            }
            StringBuilder builder = new StringBuilder("当前管理员：");
            for (int i = 0; i < admins.size(); i++) {
                if (i > 0) builder.append(",");
                builder.append(admins.get(i));
            }
            sender.sendMessage(builder.toString());
            return true;
        }
        if ("add".equalsIgnoreCase(args[1])) {
            if (args.length < 3) {
                sender.sendMessage("用法：/admin add <QQ>");
                return true;
            }
            long userID = parseUserID(args[2]);
            if (userID <= 0) {
                sender.sendMessage("QQ 号格式不正确！");
                return true;
            }
            sender.sendMessage(MoBoxBotAPI.getServer().addAdmin(userID)
                    ? "已添加管理员："+userID
                    : "添加失败，可能已经是管理员。");
            return true;
        }
        if ("remove".equalsIgnoreCase(args[1])) {
            if (args.length < 3) {
                sender.sendMessage("用法：/admin remove <QQ>");
                return true;
            }
            long userID = parseUserID(args[2]);
            if (userID <= 0) {
                sender.sendMessage("QQ 号格式不正确！");
                return true;
            }
            sender.sendMessage(MoBoxBotAPI.getServer().removeAdmin(userID)
                    ? "已移除管理员："+userID
                    : "移除失败，该账号不在管理员列表中。");
            return true;
        }
        sender.sendMessage("用法：/admin list | /admin add <QQ> | /admin remove <QQ>");
        return true;
    }

    private long parseUserID(String text) {
        try {
            return Long.parseLong(text.trim());
        } catch (Exception e) {
            return -1;
        }
    }
}
