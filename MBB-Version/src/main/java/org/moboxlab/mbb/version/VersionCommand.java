package org.moboxlab.mbb.version;

import org.moboxlab.moboxbot.API.Command.BotCommand;
import org.moboxlab.moboxbot.API.Command.CommandPermission;
import org.moboxlab.moboxbot.API.Command.CommandSender;
import org.moboxlab.moboxbot.API.MoBoxBotAPI;
import org.moboxlab.moboxbot.API.Util.ImageUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * /version 版本命令
 */
public class VersionCommand extends BotCommand {
    private final VersionPlugin plugin;

    public VersionCommand(VersionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> prefix() {
        List<String> prefixList = new ArrayList<>();
        prefixList.add("version");
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
        return "显示版本信息";
    }

    @Override
    public boolean execute(CommandSender sender,String[] args) {
        List<String> lines = new ArrayList<>();
        lines.add("MoBoxBot："+MoBoxBotAPI.getVersion());
        lines.add("插件 API："+MoBoxBotAPI.getApiVersion());
        lines.add("插件版本："+plugin.getVersion());
        lines.add("Java："+System.getProperty("java.version","未知"));
        lines.add("系统："+System.getProperty("os.name","未知")+" "+System.getProperty("os.arch",""));
        byte[] image = ImageUtil.renderText("MoBoxBot 版本信息",lines);
        if (image != null) {
            sender.sendImage(ImageUtil.toBase64Uri(image));
        } else {
            sender.sendMessage("版本信息图片生成失败，已回退为文本：");
            for (String line : lines) sender.sendMessage(line);
        }
        return true;
    }
}
