package org.moboxlab.mbb.plugins;

import org.moboxlab.moboxbot.API.Command.BotCommand;
import org.moboxlab.moboxbot.API.Command.CommandPermission;
import org.moboxlab.moboxbot.API.Command.CommandSender;
import org.moboxlab.moboxbot.API.MoBoxBotAPI;
import org.moboxlab.moboxbot.API.PluginInfo;
import org.moboxlab.moboxbot.API.PluginManager;
import org.moboxlab.moboxbot.API.Util.ImageUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * /plugins 插件列表与管理命令
 */
public class PluginsCommand extends BotCommand {
    private final PluginsPlugin plugin;

    public PluginsCommand(PluginsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> prefix() {
        List<String> prefixList = new ArrayList<>();
        prefixList.add("plugins");
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
        return "查看和管理插件";
    }

    @Override
    public List<String> usage() {
        return Arrays.asList(
                "/plugins [list]",
                "/plugins info <插件名>",
                "/plugins enable <插件名>",
                "/plugins disable <插件名>",
                "/plugins reload <插件名>");
    }

    @Override
    public boolean execute(CommandSender sender,String[] args) {
        if (args.length >= 2) {
            String action = args[1].toLowerCase();
            if ("list".equals(action)) {
                sendPluginImage(sender);
                return true;
            }
            if ("info".equals(action)) {
                if (args.length < 3) {
                    sender.sendMessage("用法：/plugins info <插件名>");
                    return true;
                }
                sendPluginInfo(sender,args[2]);
                return true;
            }
            if ("enable".equals(action) || "disable".equals(action) || "reload".equals(action)) {
                if (args.length < 3) {
                    sender.sendMessage("用法：/plugins "+action+" <插件名>");
                    return true;
                }
                managePlugin(sender,action,args[2]);
                return true;
            }
            sender.sendMessage("用法：/plugins [list] | info <插件名> | enable/disable/reload <插件名>");
            return true;
        }
        sendPluginImage(sender);
        return true;
    }

    private void sendPluginImage(CommandSender sender) {
        List<PluginInfo> plugins = MoBoxBotAPI.getServer().getPluginInfoList();
        if (plugins == null || plugins.isEmpty()) {
            sender.sendMessage("当前没有加载任何插件！");
            return;
        }
        Collections.sort(plugins,new Comparator<PluginInfo>() {
            @Override
            public int compare(PluginInfo left,PluginInfo right) {
                return left.name.compareToIgnoreCase(right.name);
            }
        });
        List<String> lines = new ArrayList<>();
        for (PluginInfo info : plugins) {
            lines.add((info.enabled ? "[启用] " : "[停用] ")
                    +info.name+" "+info.version+" By "+safe(info.author));
            lines.add("    命令 "+info.commandCount
                    +" | 监听 "+info.listenerCount
                    +" | 任务 "+info.taskCount);
            lines.add("    说明："+safe(info.description));
        }
        byte[] image = ImageUtil.renderText("MoBoxBot 插件列表（"+plugins.size()+" 个）",lines);
        if (image != null) {
            sender.sendImage(ImageUtil.toBase64Uri(image));
        } else {
            sender.sendMessage("插件列表图片生成失败，已回退为文本：");
            for (String line : lines) sender.sendMessage(line);
        }
    }

    private void sendPluginInfo(CommandSender sender,String name) {
        PluginInfo info = findPlugin(name);
        if (info == null) {
            sender.sendMessage("没有找到插件："+name);
            return;
        }
        sender.sendMessage("插件："+info.name);
        sender.sendMessage("版本："+info.version+" By "+safe(info.author));
        sender.sendMessage("状态："+(info.enabled ? "启用" : "停用"));
        sender.sendMessage("命令 "+info.commandCount+" | 监听 "+info.listenerCount+" | 任务 "+info.taskCount);
        sender.sendMessage("说明："+safe(info.description));
    }

    private void managePlugin(CommandSender sender,String action,String name) {
        PluginManager manager = MoBoxBotAPI.getServer().getPluginManager();
        if (("disable".equals(action) || "reload".equals(action)) && plugin.getName().equals(name)) {
            sender.sendMessage("不能"+(("disable".equals(action)) ? "停用" : "重载")+" MBB-Plugins 自身！");
            return;
        }
        boolean result;
        if ("enable".equals(action)) {
            result = manager.enablePlugin(name);
            sender.sendMessage(result ? "插件已启用："+name : "插件启用失败："+name);
            return;
        }
        if ("disable".equals(action)) {
            result = manager.disablePlugin(name);
            sender.sendMessage(result ? "插件已停用："+name : "插件停用失败："+name);
            return;
        }
        result = manager.reloadPlugin(name);
        sender.sendMessage(result ? "插件已重载："+name : "插件重载失败："+name);
    }

    private PluginInfo findPlugin(String name) {
        List<PluginInfo> plugins = MoBoxBotAPI.getServer().getPluginInfoList();
        if (plugins == null || name == null) return null;
        for (PluginInfo info : plugins) {
            if (name.equalsIgnoreCase(info.name)) return info;
        }
        return null;
    }

    private String safe(String text) {
        return text == null || text.isEmpty() ? "未知" : text;
    }
}
