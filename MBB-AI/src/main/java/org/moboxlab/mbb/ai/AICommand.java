package org.moboxlab.mbb.ai;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Command.BotCommand;
import org.moboxlab.moboxbot.API.Command.CommandPermission;
import org.moboxlab.moboxbot.API.Command.CommandSender;

import java.util.ArrayList;
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
    public boolean execute(CommandSender sender,String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase() : "status";
        JSONObject result = plugin.getService().call(action,new JSONObject(true));
        if (!result.getBooleanValue("status")) {
            sender.sendMessage("AI 操作失败："+safe(result.getString("message")));
            return true;
        }
        if ("usage".equals(action)) {
            sender.sendMessage("AI 统计：请求 "+result.getLongValue("requests")
                    +"，成功 "+result.getLongValue("successes")
                    +"，失败 "+result.getLongValue("failures")
                    +"，Token "+result.getLongValue("totalTokens")
                    +"，缓存 "+result.getLongValue("cacheSize"));
            return true;
        }
        if ("reload".equals(action)) {
            sender.sendMessage(result.getString("message"));
        }
        StringBuilder builder = new StringBuilder("MBB-AI 状态：")
                .append(result.getBooleanValue("enable") ? "启用" : "关闭")
                .append("\n默认配置：").append(result.getString("defaultProfile"))
                .append("\n模型配置：");
        JSONArray profiles = result.getJSONArray("profiles");
        if (profiles == null || profiles.isEmpty()) {
            builder.append("无");
        } else {
            for (int i = 0; i < profiles.size(); i++) {
                JSONObject profile = profiles.getJSONObject(i);
                builder.append("\n- ").append(profile.getString("name"))
                        .append(" / ").append(profile.getString("model"))
                        .append(" / ").append(profile.getString("baseUrl"));
            }
        }
        sender.sendMessage(builder.toString());
        return true;
    }

    private String safe(String text) {
        return text == null ? "未知错误" : text;
    }
}
