package org.moboxlab.mbb.sticker;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Command.BotCommand;
import org.moboxlab.moboxbot.API.Command.CommandPermission;
import org.moboxlab.moboxbot.API.Command.CommandSender;
import org.moboxlab.moboxbot.API.OneBot.MessageUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * /sticker 表情包命令
 */
public class StickerCommand extends BotCommand {
    private final StickerPlugin plugin;
    private final StickerLibrary library;
    private final StickerService service;
    private final StickerReceiveService receiveService;
    private final StickerTagger tagger;

    public StickerCommand(StickerPlugin plugin,StickerLibrary library,StickerService service,
                          StickerReceiveService receiveService,StickerTagger tagger) {
        this.plugin = plugin;
        this.library = library;
        this.service = service;
        this.receiveService = receiveService;
        this.tagger = tagger;
    }

    @Override
    public List<String> prefix() {
        List<String> list = new ArrayList<>();
        list.add("sticker");
        list.add("表情");
        list.add("表情包");
        return list;
    }

    @Override
    public CommandPermission permission() {
        return CommandPermission.EVERYONE;
    }

    @Override
    public int cooldownSeconds() {
        return 2;
    }

    @Override
    public String description() {
        return "发送、收录和管理表情包";
    }

    @Override
    public List<String> usage() {
        return Arrays.asList(
                "/sticker [标签...]",
                "/sticker receive",
                "/sticker receive stop",
                "/sticker list",
                "/sticker stats",
                "/sticker tag <ID> <标签...>",
                "/sticker retag <ID>",
                "/sticker remove <ID>",
                "/sticker reload");
    }

    @Override
    public boolean execute(CommandSender sender,String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase() : "";
        if ("receive".equals(action)) {
            if (sender.isGroup()) {
                sender.sendMessage("请在私聊中使用 /sticker receive。");
                return true;
            }
            if (args.length > 2 && "stop".equalsIgnoreCase(args[2])) {
                receiveService.stop(sender.getUserID());
                sender.sendMessage("已退出表情包收录模式。");
            } else {
                receiveService.start(sender.getUserID());
                int minutes = plugin.getConfig().getInt("receiveTimeoutMinutes",10);
                sender.sendMessage("已进入表情包收录模式，接下来发送的图片会自动收录并生成标签。"
                        +"\n发送 /sticker receive stop 结束，"+minutes+" 分钟无图片自动结束。");
            }
            return true;
        }
        if ("list".equals(action)) {
            if (!admin(sender)) return true;
            list(sender);
            return true;
        }
        if ("stats".equals(action)) {
            if (!admin(sender)) return true;
            sender.sendMessage("表情包数量："+library.size()
                    +"\n可用标签数："+library.availableTags().size()
                    +"\n标签："+String.join(", ",library.availableTags()));
            return true;
        }
        if ("tag".equals(action)) {
            if (!admin(sender)) return true;
            updateTags(sender,args);
            return true;
        }
        if ("retag".equals(action)) {
            if (!admin(sender)) return true;
            retag(sender,args);
            return true;
        }
        if ("remove".equals(action) || "delete".equals(action)) {
            if (!admin(sender)) return true;
            remove(sender,args);
            return true;
        }
        if ("reload".equals(action)) {
            if (!admin(sender)) return true;
            library.load();
            sender.sendMessage("表情包数据已重载，当前数量："+library.size());
            return true;
        }
        List<String> tags = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            for (String item : args[i].split("[,，]")) {
                if (!item.trim().isEmpty()) tags.add(item.trim());
            }
        }
        sendRandom(sender,tags);
        return true;
    }

    private void list(CommandSender sender) {
        List<StickerEntry> entries = library.all();
        if (entries.isEmpty()) {
            sender.sendMessage("当前没有收录表情包。");
            return;
        }
        StringBuilder builder = new StringBuilder("表情包列表：");
        for (int i = 0; i < entries.size() && i < 20; i++) {
            StickerEntry entry = entries.get(i);
            builder.append("\n").append(entry.id).append("  ")
                    .append(String.join(",",entry.tags));
        }
        if (entries.size() > 20) builder.append("\n...共 ").append(entries.size()).append(" 条");
        sender.sendMessage(builder.toString());
    }

    private void updateTags(CommandSender sender,String[] args) {
        if (args.length < 4) {
            sender.sendMessage("用法：/sticker tag <ID> <标签...>");
            return;
        }
        List<String> tags = new ArrayList<>();
        for (int i = 3; i < args.length; i++) {
            for (String item : args[i].split("[,，]")) {
                if (!item.trim().isEmpty()) tags.add(item.trim());
            }
        }
        boolean changed = library.updateTags(args[2],tags);
        sender.sendMessage(changed ? "标签已更新。" : "没有找到表情包 ID："+args[2]);
    }

    private void retag(CommandSender sender,String[] args) {
        if (args.length < 3) {
            sender.sendMessage("用法：/sticker retag <ID>");
            return;
        }
        StickerEntry entry = library.get(args[2]);
        if (entry == null) {
            sender.sendMessage("没有找到表情包 ID："+args[2]);
            return;
        }
        File file = StickerLibrary.fromFileUri(entry.file);
        if (file == null || !file.exists()) {
            sender.sendMessage("表情包文件不存在，无法重新识别。");
            return;
        }
        sender.sendMessage("正在重新识别情绪标签，请稍候...");
        plugin.getServer().getPluginManager().runTask(plugin,() -> {
            JSONObject tagged = tagger.tag(file);
            List<String> tags = tagList(tagged.getJSONArray("tags"));
            if (tags.isEmpty() || (tags.size() == 1 && "unlabeled".equals(tags.get(0)))) {
                sender.sendMessage("AI 没有返回有效情绪标签，已保留原标签。");
                return;
            }
            boolean changed = library.updateTags(entry.id,tags);
            sender.sendMessage(changed ? "标签已重新生成："+String.join(", ",tags)
                    : "没有找到表情包 ID："+entry.id);
        });
    }

    private void remove(CommandSender sender,String[] args) {
        if (args.length < 3) {
            sender.sendMessage("用法：/sticker remove <ID>");
            return;
        }
        sender.sendMessage(library.remove(args[2]) ? "表情包已删除。" : "没有找到表情包 ID："+args[2]);
    }

    private void sendRandom(CommandSender sender,List<String> tags) {
        JSONArray tagArray = new JSONArray();
        tagArray.addAll(tags);
        JSONObject params = new JSONObject(true);
        params.put("tags",tagArray);
        params.put("groupID",sender.getGroupID());
        params.put("userID",sender.getUserID());
        JSONObject result = service.call("random",params);
        if (result == null || !result.getBooleanValue("status")) {
            sender.sendMessage("没有找到匹配的表情包。");
            return;
        }
        if (sender.isGroup()) {
            plugin.getServer().getOneBotClient().sendGroupMessage(sender.getGroupID(),
                    MessageUtil.message(MessageUtil.image(result.getString("file"))));
        } else {
            plugin.getServer().getOneBotClient().sendPrivateMessage(sender.getUserID(),
                    MessageUtil.message(MessageUtil.image(result.getString("file"))));
        }
    }

    private List<String> tagList(JSONArray array) {
        List<String> result = new ArrayList<>();
        if (array == null) return result;
        for (Object item : array) {
            if (item != null && !String.valueOf(item).trim().isEmpty()) {
                result.add(String.valueOf(item).trim());
            }
        }
        return result;
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission(CommandPermission.BOT_ADMIN)) return true;
        sender.sendMessage("你没有权限使用这个命令！");
        return false;
    }
}
