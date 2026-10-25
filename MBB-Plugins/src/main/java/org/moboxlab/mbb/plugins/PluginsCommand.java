package org.moboxlab.mbb.plugins;

import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Command.BotCommand;
import org.moboxlab.moboxbot.API.Command.CommandPermission;
import org.moboxlab.moboxbot.API.Command.CommandSender;
import org.moboxlab.moboxbot.API.MoBoxBotAPI;
import org.moboxlab.moboxbot.API.PluginInfo;
import org.moboxlab.moboxbot.API.PluginManager;
import org.moboxlab.moboxbot.API.Util.ImageUtil;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
                "/plugins check",
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
            if ("check".equals(action)) {
                checkPluginVersions(sender);
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
            sender.sendMessage("用法：/plugins [list] | check | info <插件名> | enable/disable/reload <插件名>");
            return true;
        }
        sendPluginImage(sender);
        return true;
    }

    private void checkPluginVersions(CommandSender sender) {
        File file = findVersionManifest();
        if (file == null) {
            sender.sendMessage("没有找到插件版本清单。请把 out/versions.json 放到：");
            sender.sendMessage(expectedManifestPath());
            return;
        }
        JSONObject root;
        try {
            if (file.length() > 1024 * 1024) {
                sender.sendMessage("versions.json 超过 1MB，已拒绝读取。");
                return;
            }
            byte[] bytes = Files.readAllBytes(file.toPath());
            root = JSONObject.parseObject(new String(bytes,StandardCharsets.UTF_8));
        } catch (Exception e) {
            sender.sendMessage("versions.json 读取失败："+safe(e.getMessage()));
            return;
        }
        JSONObject manifest = root == null ? null : root.getJSONObject("plugins");
        if (manifest == null || manifest.isEmpty()) {
            sender.sendMessage("versions.json 格式不正确：缺少 plugins 对象。");
            return;
        }
        Map<String,PluginInfo> installed = new LinkedHashMap<>();
        List<PluginInfo> plugins = MoBoxBotAPI.getServer().getPluginInfoList();
        if (plugins != null) {
            for (PluginInfo info : plugins) installed.put(info.name,info);
        }
        List<String> updates = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> localNewer = new ArrayList<>();
        int latestCount = 0;
        for (String name : manifest.keySet()) {
            String latest = manifestVersion(manifest.get(name));
            if (name == null || name.trim().isEmpty() || latest.isEmpty()) continue;
            latestCount++;
            PluginInfo current = installed.get(name);
            if (current == null) {
                missing.add("[未安装] "+name+" "+latest);
                continue;
            }
            int compared = compareVersions(current.version,latest);
            if (compared < 0) {
                updates.add("[可更新] "+name+" "+safe(current.version)+" -> "+latest);
            } else if (compared > 0) {
                localNewer.add("[本地较新] "+name+" "+safe(current.version)+" > "+latest);
            }
        }
        List<String> unlisted = new ArrayList<>();
        for (String name : installed.keySet()) {
            if (!manifest.containsKey(name)) unlisted.add(name);
        }
        int currentCount = latestCount - updates.size() - missing.size() - localNewer.size();
        sender.sendMessage("插件版本检查：清单 "+latestCount
                +" 个，可更新 "+updates.size()
                +"，已最新 "+Math.max(0,currentCount)
                +"，未安装 "+missing.size()
                +"，本地较新 "+localNewer.size());
        sender.sendMessage("清单来源："+file.getAbsolutePath());
        if (!updates.isEmpty()) sender.sendMessage(joinLines(updates,12));
        if (!missing.isEmpty()) sender.sendMessage(joinLines(missing,12));
        if (!localNewer.isEmpty()) sender.sendMessage(joinLines(localNewer,12));
        if (!unlisted.isEmpty()) {
            sender.sendMessage("[清单缺失] "+String.join("、",unlisted));
        }
        if (updates.isEmpty() && missing.isEmpty() && localNewer.isEmpty()) {
            sender.sendMessage("清单内插件均已是当前最新版本。");
        }
    }

    static int compareVersions(String left,String right) {
        String[] leftParts = versionParts(left);
        String[] rightParts = versionParts(right);
        int count = Math.max(leftParts.length,rightParts.length);
        for (int i = 0; i < count; i++) {
            long a = part(leftParts,i);
            long b = part(rightParts,i);
            if (a < b) return -1;
            if (a > b) return 1;
        }
        return 0;
    }

    private static String[] versionParts(String version) {
        String value = version == null ? "" : version.trim();
        if (value.startsWith("V") || value.startsWith("v")) value = value.substring(1);
        return value.isEmpty() ? new String[0] : value.split("\\.");
    }

    private static long part(String[] parts,int index) {
        if (parts == null || index < 0 || index >= parts.length) return 0L;
        String value = parts[index] == null ? "" : parts[index].trim();
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') break;
            digits.append(c);
        }
        if (digits.length() == 0) return 0L;
        try {
            return Long.parseLong(digits.toString());
        } catch (Exception e) {
            return Long.MAX_VALUE;
        }
    }

    private String manifestVersion(Object value) {
        if (value == null) return "";
        if (value instanceof JSONObject) {
            String version = ((JSONObject)value).getString("version");
            return version == null ? "" : version.trim();
        }
        return String.valueOf(value).trim();
    }

    private File findVersionManifest() {
        List<File> candidates = new ArrayList<>();
        String dataFolder = plugin.getDataFolder();
        if (dataFolder != null && !dataFolder.trim().isEmpty()) {
            candidates.add(new File(dataFolder,"versions.json"));
        }
        String userDir = System.getProperty("user.dir","");
        candidates.add(new File(userDir,"MoBoxBot/plugins/MBB-Plugins/versions.json"));
        candidates.add(new File(userDir,"plugins/MBB-Plugins/versions.json"));
        for (File file : candidates) {
            if (file.isFile() && file.canRead()) return file;
        }
        return null;
    }

    private String expectedManifestPath() {
        String dataFolder = plugin.getDataFolder();
        if (dataFolder != null && !dataFolder.trim().isEmpty()) {
            return new File(dataFolder,"versions.json").getAbsolutePath();
        }
        return new File(System.getProperty("user.dir",""),
                "MoBoxBot/plugins/MBB-Plugins/versions.json").getAbsolutePath();
    }

    private String joinLines(List<String> lines,int limit) {
        StringBuilder builder = new StringBuilder();
        int count = Math.min(limit,lines.size());
        for (int i = 0; i < count; i++) {
            if (builder.length() > 0) builder.append("\n");
            builder.append(lines.get(i));
        }
        if (lines.size() > count) {
            builder.append("\n……另有 ").append(lines.size() - count).append(" 项");
        }
        return builder.toString();
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
        return text == null || text.trim().isEmpty() ? "未知" : text;
    }
}
