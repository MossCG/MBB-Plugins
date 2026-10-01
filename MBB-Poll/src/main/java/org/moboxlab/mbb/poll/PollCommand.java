package org.moboxlab.mbb.poll;

import org.moboxlab.moboxbot.API.Command.BotCommand;
import org.moboxlab.moboxbot.API.Command.CommandPermission;
import org.moboxlab.moboxbot.API.Command.CommandSender;
import org.moboxlab.moboxbot.API.OneBot.MessageUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * /poll 发起投票
 */
public class PollCommand extends BotCommand {
    private final PollPlugin plugin;

    public PollCommand(PollPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> prefix() {
        List<String> prefixList = new ArrayList<>();
        prefixList.add("poll");
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
        return "发起群投票";
    }

    @Override
    public boolean execute(CommandSender sender,String[] args) {
        if (!sender.isGroup()) {
            sender.sendMessage("投票只能在群里发起哦！");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("用法：/poll <时长> <问题> [选项1 选项2 ...]，例如 /poll 5m 中午吃什么 火锅 烧烤");
            return true;
        }
        long duration = parseDuration(args[1]);
        if (duration < 10 || duration > 86400) {
            sender.sendMessage("时长支持 s/m/h/d，范围 10 秒到 24 小时！");
            return true;
        }
        String question = args[2];
        List<String> options = new ArrayList<>();
        if (args.length <= 3) {
            options.add("赞成");
            options.add("反对");
        } else {
            for (int i = 3; i < args.length; i++) {
                if (args[i] != null && !args[i].trim().isEmpty()) options.add(args[i].trim());
            }
            if (options.size() < 2) {
                sender.sendMessage("至少需要两个选项！");
                return true;
            }
            if (options.size() > 10) {
                sender.sendMessage("最多支持 10 个选项！");
                return true;
            }
        }
        String error = PollService.create(sender.getGroupID(),sender.getUserID(),question,options,duration);
        if (error != null) {
            sender.sendMessage(error);
            return true;
        }
        final long groupID = sender.getGroupID();
        sender.sendMessage(buildStartMessage(question,options,duration));
        plugin.getServer().getPluginManager().runTaskLater(plugin,() -> finish(groupID),duration);
        return true;
    }

    private void finish(long groupID) {
        PollService.Poll poll = PollService.finish(groupID);
        if (poll == null) return;
        int[] counts = new int[poll.options.size()];
        for (Integer index : poll.votes.values()) {
            if (index >= 0 && index < counts.length) counts[index]++;
        }
        StringBuilder builder = new StringBuilder();
        builder.append(" 投票结束：").append(poll.question).append("\n");
        for (int i = 0; i < poll.options.size(); i++) {
            builder.append(i + 1).append(". ").append(poll.options.get(i)).append("：").append(counts[i]).append(" 票\n");
        }
        plugin.getServer().getOneBotClient().sendGroupMessage(
                groupID,
                MessageUtil.message(MessageUtil.at(poll.initiatorID),MessageUtil.text(builder.toString())));
    }

    private String buildStartMessage(String question,List<String> options,long duration) {
        StringBuilder builder = new StringBuilder();
        builder.append("投票开始：").append(question).append("\n");
        for (int i = 0; i < options.size(); i++) {
            builder.append(i + 1).append(". ").append(options.get(i)).append("\n");
        }
        builder.append("发送 /vote <序号> 参与，时长 ").append(formatDuration(duration)).append("。");
        return builder.toString();
    }

    private long parseDuration(String text) {
        if (text == null || text.length() < 2) return -1;
        String unit = text.substring(text.length() - 1).toLowerCase();
        long value;
        try {
            value = Long.parseLong(text.substring(0,text.length() - 1));
        } catch (Exception e) {
            return -1;
        }
        if (value <= 0) return -1;
        if ("s".equals(unit)) return value;
        if ("m".equals(unit)) return value * 60L;
        if ("h".equals(unit)) return value * 3600L;
        if ("d".equals(unit)) return value * 86400L;
        return -1;
    }

    private String formatDuration(long seconds) {
        if (seconds % 3600L == 0) return (seconds / 3600L)+"小时";
        if (seconds % 60L == 0) return (seconds / 60L)+"分钟";
        return seconds+"秒";
    }
}
