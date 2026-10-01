package org.moboxlab.mbb.reload;

import org.moboxlab.moboxbot.API.Command.BotCommand;
import org.moboxlab.moboxbot.API.Command.CommandPermission;
import org.moboxlab.moboxbot.API.Command.CommandSender;
import org.moboxlab.moboxbot.API.MoBoxBotAPI;

import java.util.ArrayList;
import java.util.List;

/**
 * /reload 重载命令
 */
public class ReloadCommand extends BotCommand {
    @Override
    public List<String> prefix() {
        List<String> prefixList = new ArrayList<>();
        prefixList.add("reload");
        return prefixList;
    }

    @Override
    public CommandPermission permission() {
        return CommandPermission.OWNER;
    }

    @Override
    public int cooldownSeconds() {
        return 10;
    }

    @Override
    public String description() {
        return "重载主程序配置";
    }

    @Override
    public boolean execute(CommandSender sender,String[] args) {
        MoBoxBotAPI.getServer().reloadConfig();
        sender.sendMessage("配置已重载！");
        return true;
    }
}
