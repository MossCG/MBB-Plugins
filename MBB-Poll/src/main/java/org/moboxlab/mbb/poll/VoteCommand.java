package org.moboxlab.mbb.poll;

import org.moboxlab.moboxbot.API.Command.BotCommand;
import org.moboxlab.moboxbot.API.Command.CommandPermission;
import org.moboxlab.moboxbot.API.Command.CommandSender;

import java.util.ArrayList;
import java.util.List;

/**
 * /vote 参与投票
 */
public class VoteCommand extends BotCommand {
    @Override
    public List<String> prefix() {
        List<String> prefixList = new ArrayList<>();
        prefixList.add("vote");
        return prefixList;
    }

    @Override
    public CommandPermission permission() {
        return CommandPermission.EVERYONE;
    }

    @Override
    public int cooldownSeconds() {
        return 1;
    }

    @Override
    public String description() {
        return "参与当前群投票";
    }

    @Override
    public boolean execute(CommandSender sender,String[] args) {
        if (!sender.isGroup()) {
            sender.sendMessage("投票只能在群里参与哦！");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("用法：/vote <序号>");
            return true;
        }
        int index;
        try {
            index = Integer.parseInt(args[1]);
        } catch (Exception e) {
            sender.sendMessage("序号必须是数字！");
            return true;
        }
        sender.sendMessage(PollService.vote(sender.getGroupID(),sender.getUserID(),index));
        return true;
    }
}
