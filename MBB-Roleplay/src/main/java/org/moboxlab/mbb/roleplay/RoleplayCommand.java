package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Command.BotCommand;
import org.moboxlab.moboxbot.API.Command.CommandPermission;
import org.moboxlab.moboxbot.API.Command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * /role 角色扮演管理命令
 */
public class RoleplayCommand extends BotCommand {
    private final RoleplayPlugin plugin;
    private final RoleplayService service;

    public RoleplayCommand(RoleplayPlugin plugin,RoleplayService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public List<String> prefix() {
        List<String> prefixList = new ArrayList<>();
        prefixList.add("role");
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
        return "管理角色扮演插件";
    }

    @Override
    public List<String> usage() {
        return Arrays.asList(
                "/role status",
                "/role enable [群号]",
                "/role disable [群号]",
                "/role groups",
                "/role reload",
                "/role memory",
                "/role forget");
    }

    @Override
    public boolean execute(CommandSender sender,String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase() : "status";
        if ("status".equals(action)) {
            JSONObject result = service.status(sender.getGroupID());
            sender.sendMessage("角色："+result.getString("role")
                    +"\n当前群："+(result.getBooleanValue("groupEnabled") ? "已开启" : "未开启")
                    +"\n长期记忆数："+result.getIntValue("memoryCount")
                    +"\n短期记忆："+result.getString("shortSummary"));
            return true;
        }
        if ("groups".equals(action)) {
            JSONArray groups = service.listGroups();
            if (groups.isEmpty()) {
                sender.sendMessage("当前没有开启角色扮演的群。");
            } else {
                StringBuilder builder = new StringBuilder("已开启群：");
                for (Object group : groups) builder.append("\n").append(group);
                sender.sendMessage(builder.toString());
            }
            return true;
        }
        if ("enable".equals(action) || "disable".equals(action)) {
            long groupID = sender.getGroupID();
            if (args.length > 2) {
                try {
                    groupID = Long.parseLong(args[2]);
                } catch (Exception e) {
                    sender.sendMessage("群号格式不正确！");
                    return true;
                }
            }
            if (groupID <= 0) {
                sender.sendMessage("请在群聊中使用，或指定群号。");
                return true;
            }
            service.setGroupEnabled(groupID,"enable".equals(action));
            sender.sendMessage("群 "+groupID+" 的角色扮演已"+("enable".equals(action) ? "开启" : "关闭")+"。");
            return true;
        }
        if ("reload".equals(action)) {
            plugin.reloadRoleplay();
            sender.sendMessage("角色设定和配置已重载。");
            return true;
        }
        if ("memory".equals(action)) {
            long groupID = sender.getGroupID();
            JSONObject result = service.memoryStats(groupID);
            JSONArray memories = result.getJSONArray("memories");
            StringBuilder builder = new StringBuilder("角色："+result.getString("role"))
                    .append("\n短期记忆：").append(result.getString("shortSummary"))
                    .append("\n长期记忆数：").append(memories == null ? 0 : memories.size());
            if (memories != null) {
                for (int i = 0; i < memories.size() && i < 10; i++) {
                    JSONObject item = memories.getJSONObject(i);
                    builder.append("\n- [").append(item.getString("type")).append("] ")
                            .append(item.getString("content"));
                }
            }
            sender.sendMessage(builder.toString());
            return true;
        }
        if ("forget".equals(action)) {
            long groupID = sender.getGroupID();
            if (groupID <= 0) {
                sender.sendMessage("请在群聊中使用。");
                return true;
            }
            service.clearMemory(groupID);
            sender.sendMessage("当前群的角色记忆已清空。");
            return true;
        }
        sender.sendMessage("用法：/role status | enable/disable | groups | reload | memory | forget");
        return true;
    }
}
