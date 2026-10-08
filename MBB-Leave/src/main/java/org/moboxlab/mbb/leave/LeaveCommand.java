package org.moboxlab.mbb.leave;

import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Command.BotCommand;
import org.moboxlab.moboxbot.API.Command.CommandPermission;
import org.moboxlab.moboxbot.API.Command.CommandSender;
import org.moboxlab.moboxbot.API.OneBot.MessageUtil;
import org.moboxlab.moboxbot.API.OneBot.OneBotClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * /leave 发送退群消息后退出当前群
 */
public class LeaveCommand extends BotCommand {
    private final LeavePlugin plugin;

    public LeaveCommand(LeavePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> prefix() {
        List<String> prefixList = new ArrayList<>();
        prefixList.add("leave");
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
        return "发送退群消息后退出当前群";
    }

    @Override
    public List<String> usage() {
        return Arrays.asList("/leave [退群消息]");
    }

    @Override
    public boolean execute(CommandSender sender,String[] args) {
        if (!sender.isGroup()) {
            sender.sendMessage("该指令只能在群里使用。");
            return false;
        }
        OneBotClient client = plugin.getServer().getOneBotClient();
        if (client == null || !client.isConnected()) {
            sender.sendMessage("OneBot 未连接，无法退群。");
            return false;
        }
        long groupID = sender.getGroupID();
        String message = args == null || args.length == 0
                ? plugin.getConfig().getString("leaveText","再见啦，有缘再见。")
                : joinArgs(args);
        if (message == null || message.trim().isEmpty()) {
            message = "再见啦，有缘再见。";
        }
        message = message.trim();

        JSONObject sent = client.sendGroupMessage(groupID,MessageUtil.message(MessageUtil.text(message)));
        if (!success(sent)) {
            sender.sendMessage("退群消息发送失败，已取消退群。");
            return false;
        }

        JSONObject params = new JSONObject(true);
        params.put("group_id",String.valueOf(groupID));
        params.put("is_dismiss",false);
        JSONObject result = client.callAction("set_group_leave",params);
        if (!success(result)) {
            sender.sendMessage("退群请求失败，请检查控制台日志。");
            return false;
        }
        plugin.getLogger().sendInfo("[退群] 群"+groupID+" 由 owner "+sender.getUserID()
                +" 发起，退群消息："+message);
        return true;
    }

    private String joinArgs(String[] args) {
        StringBuilder builder = new StringBuilder();
        for (String arg : args) {
            if (arg == null || arg.trim().isEmpty()) continue;
            if (builder.length() > 0) builder.append(' ');
            builder.append(arg.trim());
        }
        return builder.toString();
    }

    private boolean success(JSONObject response) {
        if (response == null) return false;
        if (response.containsKey("retcode")) return response.getIntValue("retcode") == 0;
        return "ok".equalsIgnoreCase(response.getString("status"));
    }
}
