package org.moboxlab.mbb.ping;

import org.moboxlab.moboxbot.API.Command.BotCommand;
import org.moboxlab.moboxbot.API.Command.CommandPermission;
import org.moboxlab.moboxbot.API.Command.CommandSender;
import org.moboxlab.moboxbot.API.MoBoxBotAPI;

import java.util.ArrayList;
import java.util.List;

/**
 * /ping 测试命令
 */
public class PingCommand extends BotCommand {
    @Override
    public List<String> prefix() {
        List<String> prefixList = new ArrayList<>();
        prefixList.add("ping");
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
        return "测试机器人是否运行中";
    }

    @Override
    public boolean execute(CommandSender sender,String[] args) {
        String version = MoBoxBotAPI.getVersion();
        sender.sendMessage("pong | MoBoxBot 运行中 | 版本："+version);
        return true;
    }
}
