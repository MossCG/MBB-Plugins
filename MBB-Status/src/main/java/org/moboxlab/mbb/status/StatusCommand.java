package org.moboxlab.mbb.status;

import org.moboxlab.moboxbot.API.Command.BotCommand;
import org.moboxlab.moboxbot.API.Command.CommandPermission;
import org.moboxlab.moboxbot.API.Command.CommandSender;

import java.util.ArrayList;
import java.util.List;

/**
 * /status 运行状态命令
 */
public class StatusCommand extends BotCommand {
    @Override
    public List<String> prefix() {
        List<String> prefixList = new ArrayList<>();
        prefixList.add("status");
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
        return "获取当前运行状态";
    }

    @Override
    public boolean execute(CommandSender sender,String[] args) {
        sender.sendMessage(SystemStatusService.getStatusText());
        return true;
    }
}
