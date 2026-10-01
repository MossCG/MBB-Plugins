package org.moboxlab.mbb.remind;

import com.alibaba.fastjson.JSONArray;
import org.moboxlab.moboxbot.API.Command.BotCommand;
import org.moboxlab.moboxbot.API.Command.CommandPermission;
import org.moboxlab.moboxbot.API.Command.CommandSender;
import org.moboxlab.moboxbot.API.MoBoxBotAPI;
import org.moboxlab.moboxbot.API.OneBot.MessageUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * /remind 定时提醒命令
 */
public class RemindCommand extends BotCommand {
    private final RemindPlugin plugin;
    private final int maxRemindSecond;

    public RemindCommand(RemindPlugin plugin,int maxRemindSecond) {
        this.plugin = plugin;
        this.maxRemindSecond = maxRemindSecond;
    }

    @Override
    public List<String> prefix() {
        List<String> prefixList = new ArrayList<>();
        prefixList.add("remind");
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
        return "设置定时提醒";
    }

    @Override
    public boolean execute(CommandSender sender,String[] args) {
        if (args.length < 3) {
            sender.sendMessage("用法：/remind <时间> <内容>，例如 /remind 10m 喝水");
            return true;
        }
        long delay = parseDelay(args[1]);
        if (delay <= 0 || delay > maxRemindSecond) {
            sender.sendMessage("时间格式不对或超出限制，支持 s/m/h/d，最长 "+maxRemindSecond+" 秒！");
            return true;
        }
        StringBuilder content = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            if (content.length() > 0) content.append(" ");
            content.append(args[i]);
        }
        final boolean group = sender.isGroup();
        final long groupID = sender.getGroupID();
        final long userID = sender.getUserID();
        final String text = content.toString();
        plugin.getServer().getPluginManager().runTaskLater(plugin,() -> {
            JSONArray message = MessageUtil.message(MessageUtil.text("提醒："+text));
            if (group) {
                MoBoxBotAPI.getServer().getOneBotClient().sendGroupMessage(groupID,message);
            } else {
                MoBoxBotAPI.getServer().getOneBotClient().sendPrivateMessage(userID,message);
            }
        },delay);
        sender.sendMessage("已设置提醒，"+formatDelay(delay)+"后提醒！");
        return true;
    }

    private long parseDelay(String text) {
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

    private String formatDelay(long seconds) {
        if (seconds % 86400L == 0) return (seconds / 86400L)+"天";
        if (seconds % 3600L == 0) return (seconds / 3600L)+"小时";
        if (seconds % 60L == 0) return (seconds / 60L)+"分钟";
        return seconds+"秒";
    }
}
