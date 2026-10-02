package org.moboxlab.mbb.ai;

import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Command.BotCommand;
import org.moboxlab.moboxbot.API.Command.CommandPermission;
import org.moboxlab.moboxbot.API.Command.CommandSender;
import org.moboxlab.moboxbot.API.Util.ImageUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * /ai 管理命令
 */
public class AICommand extends BotCommand {
    private final AIPlugin plugin;

    public AICommand(AIPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> prefix() {
        List<String> prefixList = new ArrayList<>();
        prefixList.add("ai");
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
        return "管理公用 AI 服务";
    }

    @Override
    public List<String> usage() {
        return Arrays.asList(
                "/ai status",
                "/ai usage [天数]",
                "/ai reload");
    }

    @Override
    public boolean execute(CommandSender sender,String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase() : "status";
        if ("usage".equals(action)) {
            int days = parseDays(args);
            JSONObject params = new JSONObject(true);
            params.put("days",days);
            JSONObject usage = plugin.getService().call("usage",params);
            byte[] image = AIImageRenderer.renderUsage(usage,days);
            if (image != null) {
                sender.sendImage(ImageUtil.toBase64Uri(image));
            } else {
                sender.sendMessage("AI 统计图片生成失败，请查看控制台日志！");
            }
            return true;
        }
        JSONObject result;
        if ("reload".equals(action)) {
            result = plugin.getService().call("reload",new JSONObject(true));
            if (!result.getBooleanValue("status")) {
                sender.sendMessage("AI 操作失败："+safe(result.getString("message")));
                return true;
            }
        } else if ("status".equals(action)) {
            result = plugin.getService().call("status",new JSONObject(true));
        } else {
            sender.sendMessage("用法：/ai status | /ai usage [天数] | /ai reload");
            return true;
        }
        JSONObject usage = plugin.getService().call("usage",new JSONObject(true));
        byte[] image = AIImageRenderer.renderStatus(result,usage,"reload".equals(action) ? "配置已重载" : null);
        if (image != null) {
            sender.sendImage(ImageUtil.toBase64Uri(image));
        } else {
            sender.sendMessage("AI 状态图片生成失败，请查看控制台日志！");
        }
        return true;
    }

    private int parseDays(String[] args) {
        if (args.length < 3) return 7;
        try {
            int days = Integer.parseInt(args[2]);
            if (days < 1) return 7;
            return Math.min(days,30);
        } catch (Exception e) {
            return 7;
        }
    }

    private String safe(String text) {
        return text == null ? "未知错误" : text;
    }
}
