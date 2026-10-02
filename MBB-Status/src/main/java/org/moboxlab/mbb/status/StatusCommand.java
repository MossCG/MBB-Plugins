package org.moboxlab.mbb.status;

import org.moboxlab.moboxbot.API.Command.BotCommand;
import org.moboxlab.moboxbot.API.Command.CommandPermission;
import org.moboxlab.moboxbot.API.Command.CommandSender;
import org.moboxlab.moboxbot.API.Util.ImageUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * /status 运行状态命令
 */
public class StatusCommand extends BotCommand {
    private final StatusPlugin plugin;

    public StatusCommand(StatusPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> prefix() {
        List<String> prefixList = new ArrayList<>();
        prefixList.add("status");
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
        return "获取当前运行状态";
    }

    @Override
    public boolean execute(CommandSender sender,String[] args) {
        int refreshSecond = plugin.getConfig().getInt("refreshSecond",10);
        byte[] image = StatusImageRenderer.render(SystemStatusService.getStatusInfo(),refreshSecond);
        if (image != null) {
            sender.sendImage(ImageUtil.toBase64Uri(image));
        } else {
            sender.sendMessage("状态图片生成失败，已回退为文本：");
            sender.sendMessage(SystemStatusService.getStatusText());
        }
        return true;
    }
}
