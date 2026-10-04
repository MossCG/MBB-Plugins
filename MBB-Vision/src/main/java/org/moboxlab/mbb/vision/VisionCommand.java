package org.moboxlab.mbb.vision;

import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Command.BotCommand;
import org.moboxlab.moboxbot.API.Command.CommandPermission;
import org.moboxlab.moboxbot.API.Command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * /vision 识图管理命令
 */
public class VisionCommand extends BotCommand {
    private final VisionPlugin plugin;
    private final VisionCache cache;
    private final VisionService service;

    public VisionCommand(VisionPlugin plugin,VisionCache cache,VisionService service) {
        this.plugin = plugin;
        this.cache = cache;
        this.service = service;
    }

    @Override
    public List<String> prefix() {
        List<String> list = new ArrayList<>();
        list.add("vision");
        list.add("识图");
        return list;
    }

    @Override
    public CommandPermission permission() {
        return CommandPermission.BOT_ADMIN;
    }

    @Override
    public String description() {
        return "查看和管理识图缓存";
    }

    @Override
    public List<String> usage() {
        return Arrays.asList("/vision stats","/vision clear","/vision reload");
    }

    @Override
    public boolean execute(CommandSender sender,String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase() : "stats";
        if ("clear".equals(action)) {
            JSONObject result = service.call("clear",null);
            sender.sendMessage(result.getBooleanValue("status")
                    ? "识图缓存已清空，共 "+result.getIntValue("count")+" 条。" : "清空识图缓存失败！");
            return true;
        }
        if ("reload".equals(action)) {
            service.call("reload",null);
            sender.sendMessage("MBB-Vision 配置已重载。");
            return true;
        }
        JSONObject stats = cache.stats();
        sender.sendMessage("识图缓存数量："+stats.getIntValue("count")
                +"\n缓存类型："+stats.getJSONArray("kinds").toJSONString());
        return true;
    }
}
