package org.moboxlab.mbb.welcome;

import org.moboxlab.moboxbot.API.Command.BotCommand;
import org.moboxlab.moboxbot.API.Command.CommandPermission;
import org.moboxlab.moboxbot.API.Command.CommandSender;

import java.util.ArrayList;
import java.util.List;

/**
 * /welcome 群欢迎开关
 */
public class WelcomeCommand extends BotCommand {
    private final WelcomePlugin plugin;

    public WelcomeCommand(WelcomePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> prefix() {
        List<String> prefixList = new ArrayList<>();
        prefixList.add("welcome");
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
        return "切换本群欢迎功能";
    }

    @Override
    public boolean execute(CommandSender sender,String[] args) {
        if (!sender.isGroup()) {
            sender.sendMessage("这个命令只能在群里使用哦！");
            return true;
        }
        String key = "welcome_enabled_"+sender.getGroupID();
        String value = plugin.getServer().getStorage().get(plugin,key);
        boolean enabled = !"true".equalsIgnoreCase(value);
        plugin.getServer().getStorage().set(plugin,key,enabled ? "true" : "false");
        sender.sendMessage(enabled ? "本群欢迎功能已开启喵！" : "本群欢迎功能已关闭喵！");
        return true;
    }
}
