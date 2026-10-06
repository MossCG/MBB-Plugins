package org.moboxlab.mbb.comfyui;

import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Command.BotCommand;
import org.moboxlab.moboxbot.API.Command.CommandPermission;
import org.moboxlab.moboxbot.API.Command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * /comfyui 调试命令
 */
public class ComfyUICommand extends BotCommand {
    private final ComfyUIPlugin plugin;
    private final ComfyUIService service;

    public ComfyUICommand(ComfyUIPlugin plugin,ComfyUIService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public List<String> prefix() {
        List<String> prefixList = new ArrayList<>();
        prefixList.add("comfyui");
        prefixList.add("draw");
        return prefixList;
    }

    @Override
    public CommandPermission permission() {
        return CommandPermission.BOT_ADMIN;
    }

    @Override
    public String description() {
        return "ComfyUI 生图调试";
    }

    @Override
    public List<String> usage() {
        return Arrays.asList(
                "/comfyui status [群号]",
                "/comfyui test <prompt> [square|landscape|portrait|avatar]");
    }

    @Override
    public boolean execute(CommandSender sender,String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase() : "status";
        if ("reload".equals(action)) {
            plugin.reloadConfig();
            sender.sendMessage("ComfyUI 配置已重载。");
            return true;
        }
        if ("status".equals(action)) {
            long groupID = sender.getGroupID();
            if (args.length > 2) {
                try {
                    groupID = Long.parseLong(args[2]);
                } catch (Exception ignored) {
                }
            }
            JSONObject params = new JSONObject(true);
            params.put("groupID",groupID);
            JSONObject status = service.call("status",params);
            sender.sendMessage("ComfyUI："
                    +"\n可生成："+(status.getBooleanValue("ready") ? "是" : "否")
                    +"\n群冷却："+status.getLongValue("cooldownRemaining")+" 秒"
                    +"\n当前群任务中："+(status.getBooleanValue("busy") ? "是" : "否")
                    +"\n队列："+status.getIntValue("queueSize")+"/"+status.getIntValue("queueLimit")
                    +"\n默认尺寸："+status.getIntValue("defaultWidth")+"x"+status.getIntValue("defaultHeight")
                    +"\n最大尺寸："+status.getIntValue("maxWidth")+"x"+status.getIntValue("maxHeight"));
            return true;
        }
        if ("test".equals(action)) {
            if (args.length < 3) {
                sender.sendMessage("用法：/comfyui test <prompt> [尺寸]");
                return true;
            }
            StringBuilder prompt = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                if (i == args.length - 1 && isPreset(args[i])) continue;
                if (prompt.length() > 0) prompt.append(" ");
                prompt.append(args[i]);
            }
            String preset = args.length > 3 && isPreset(args[args.length - 1])
                    ? args[args.length - 1] : "square";
            JSONObject params = new JSONObject(true);
            params.put("groupID",sender.getGroupID());
            params.put("userID",sender.getUserID());
            params.put("messageID",0L);
            params.put("prompt",prompt.toString());
            params.put("size",preset);
            JSONObject result = service.call("generate",params);
            sender.sendMessage(result.getBooleanValue("status")
                    ? "测试生图已提交："+result.getString("taskID")
                    : result.getString("message"));
            return true;
        }
        sender.sendMessage("用法：/comfyui status | reload | test <prompt> [尺寸]");
        return true;
    }

    private boolean isPreset(String value) {
        if (value == null) return false;
        return "square".equalsIgnoreCase(value) || "landscape".equalsIgnoreCase(value)
                || "portrait".equalsIgnoreCase(value) || "avatar".equalsIgnoreCase(value);
    }
}
