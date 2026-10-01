package org.moboxlab.mbb.random;

import org.moboxlab.moboxbot.API.Command.BotCommand;
import org.moboxlab.moboxbot.API.Command.CommandPermission;
import org.moboxlab.moboxbot.API.Command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * /random 随机数命令
 */
public class RandomCommand extends BotCommand {
    private static final int MAX_RANGE = 1000000;

    @Override
    public List<String> prefix() {
        List<String> prefixList = new ArrayList<>();
        prefixList.add("random");
        return prefixList;
    }

    @Override
    public CommandPermission permission() {
        return CommandPermission.EVERYONE;
    }

    @Override
    public int cooldownSeconds() {
        return 2;
    }

    @Override
    public String description() {
        return "生成指定范围内的随机数";
    }

    @Override
    public boolean execute(CommandSender sender,String[] args) {
        int min = 1;
        int max = 100;
        try {
            if (args.length == 2) {
                max = Integer.parseInt(args[1]);
            }
            if (args.length >= 3) {
                min = Integer.parseInt(args[1]);
                max = Integer.parseInt(args[2]);
            }
        } catch (Exception e) {
            sender.sendMessage("参数必须是整数，例如 /random 1 100");
            return true;
        }
        if (min > max) {
            sender.sendMessage("最小值不能大于最大值！");
            return true;
        }
        long range = (long) max - min;
        if (range > MAX_RANGE) {
            sender.sendMessage("随机范围不能超过 "+MAX_RANGE+"！");
            return true;
        }
        int result = ThreadLocalRandom.current().nextInt(min,max + 1);
        sender.sendMessage("随机结果："+result+"（范围 "+min+" ~ "+max+"）");
        return true;
    }
}
