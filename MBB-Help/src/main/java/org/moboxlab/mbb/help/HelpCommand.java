package org.moboxlab.mbb.help;

import org.moboxlab.moboxbot.API.Command.BotCommand;
import org.moboxlab.moboxbot.API.Command.CommandInfo;
import org.moboxlab.moboxbot.API.Command.CommandPermission;
import org.moboxlab.moboxbot.API.Command.CommandSender;
import org.moboxlab.moboxbot.API.MoBoxBotAPI;
import org.moboxlab.moboxbot.API.Util.ImageUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * /help 帮助命令
 */
public class HelpCommand extends BotCommand {
    private final HelpPlugin plugin;

    public HelpCommand(HelpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> prefix() {
        List<String> prefixList = new ArrayList<>();
        prefixList.add("help");
        return prefixList;
    }

    @Override
    public CommandPermission permission() {
        return CommandPermission.EVERYONE;
    }

    @Override
    public int cooldownSeconds() {
        return 5;
    }

    @Override
    public String description() {
        return "显示当前权限可用的命令";
    }

    @Override
    public boolean execute(CommandSender sender,String[] args) {
        int userLevel = userLevel(sender);
        Map<CommandPermission,List<CommandInfo>> groups = new LinkedHashMap<>();
        for (CommandPermission permission : orderedPermissions()) {
            groups.put(permission,new ArrayList<CommandInfo>());
        }
        List<CommandInfo> commands = MoBoxBotAPI.getServer().getCommandList();
        if (commands != null) {
            for (CommandInfo command : commands) {
                if (permissionLevel(command.permission) > userLevel) continue;
                List<CommandInfo> list = groups.get(command.permission);
                if (list != null) list.add(command);
            }
        }

        List<String> lines = new ArrayList<>();
        for (CommandPermission permission : orderedPermissions()) {
            List<CommandInfo> list = groups.get(permission);
            if (list == null || list.isEmpty()) continue;
            lines.add("【"+permissionName(permission)+"】");
            for (CommandInfo command : list) {
                StringBuilder line = new StringBuilder();
                line.append(" /").append(command.name);
                if (command.aliases != null && !command.aliases.isEmpty()) {
                    line.append("（别名：");
                    for (int i = 0; i < command.aliases.size(); i++) {
                        if (i > 0) line.append(",");
                        line.append(command.aliases.get(i));
                    }
                    line.append("）");
                }
                if (command.description != null && !command.description.isEmpty()) {
                    line.append("  ").append(command.description);
                }
                if (command.source != null && !command.source.isEmpty()) {
                    line.append("  [").append(command.source).append("]");
                }
                lines.add(line.toString());
            }
        }
        if (lines.isEmpty()) lines.add("当前没有可用命令");

        String title = plugin.getConfig().getString("title","MoBoxBot 命令帮助");
        byte[] image = ImageUtil.renderText(title,lines);
        if (image != null) {
            sender.sendImage(ImageUtil.toBase64Uri(image));
        } else {
            sender.sendMessage("命令帮助图片生成失败，已回退为文本：");
            for (String line : lines) sender.sendMessage(line);
        }
        return true;
    }

    private int userLevel(CommandSender sender) {
        if (sender.hasPermission(CommandPermission.OWNER)) return permissionLevel(CommandPermission.OWNER);
        if (sender.hasPermission(CommandPermission.BOT_ADMIN)) return permissionLevel(CommandPermission.BOT_ADMIN);
        if (sender.hasPermission(CommandPermission.GROUP_OWNER)) return permissionLevel(CommandPermission.GROUP_OWNER);
        if (sender.hasPermission(CommandPermission.GROUP_ADMIN)) return permissionLevel(CommandPermission.GROUP_ADMIN);
        return permissionLevel(CommandPermission.EVERYONE);
    }

    private CommandPermission[] orderedPermissions() {
        return new CommandPermission[]{
                CommandPermission.OWNER,
                CommandPermission.BOT_ADMIN,
                CommandPermission.GROUP_OWNER,
                CommandPermission.GROUP_ADMIN,
                CommandPermission.EVERYONE
        };
    }

    private int permissionLevel(CommandPermission permission) {
        if (permission == CommandPermission.OWNER) return 4;
        if (permission == CommandPermission.BOT_ADMIN) return 3;
        if (permission == CommandPermission.GROUP_OWNER) return 2;
        if (permission == CommandPermission.GROUP_ADMIN) return 1;
        return 0;
    }

    private String permissionName(CommandPermission permission) {
        if (permission == CommandPermission.OWNER) return "所有者";
        if (permission == CommandPermission.BOT_ADMIN) return "管理员";
        if (permission == CommandPermission.GROUP_OWNER) return "群主";
        if (permission == CommandPermission.GROUP_ADMIN) return "群管理";
        return "所有人";
    }
}
