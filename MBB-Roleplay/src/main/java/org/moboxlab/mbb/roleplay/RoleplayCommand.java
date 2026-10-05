package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Command.BotCommand;
import org.moboxlab.moboxbot.API.Command.CommandPermission;
import org.moboxlab.moboxbot.API.Command.CommandSender;
import org.moboxlab.moboxbot.API.Util.ImageUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
                "/role config [repair]",
                "/role bot [status]",
                "/role bot chance <0.3-1>",
                "/role bot max <1-10>",
                "/role bot qq [list|add|remove|set|clear] [QQ...]",
                "/role bot name [list|add|remove|set|clear|reset] [名称...]",
                "/role memory [页码]",
                "/role memory merge",
                "/role memory backup",
                "/role memory backups",
                "/role memory restore <文件名>",
                "/role gmemory [页码]",
                "/role gmemory backup",
                "/role gmemory merge",
                "/role gmemory group [list|add|remove|clear] [群号...]",
                "/role speech [stats|reload|search <文本>|on|off]",
                "/role forget",
                "/role persona [文件名]",
                "/role persona reset <文件名>");
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
        if ("config".equals(action)) {
            handleConfig(sender,args);
            return true;
        }
        if ("bot".equals(action)) {
            handleBot(sender,args);
            return true;
        }
        if ("memory".equals(action)) {
            handleMemory(sender,args);
            return true;
        }
        if ("gmemory".equals(action)) {
            handleGlobalMemory(sender,args);
            return true;
        }
        if ("speech".equals(action)) {
            handleSpeech(sender,args);
            return true;
        }
        if ("forget".equals(action)) {
            long groupID = sender.getGroupID();
            if (groupID <= 0) {
                sender.sendMessage("请在群聊中使用。");
                return true;
            }
            service.clearMemory(groupID);
            sender.sendMessage("当前群的角色记忆、聊天上下文和 AI 会话已清空。");
            return true;
        }
        if ("persona".equals(action)) {
            if (args.length < 3) {
                sender.sendMessage("当前角色设定文件："+plugin.getPersonaFileName()
                        +"\n可用：persona-aris.json、persona-momoi.json、persona-midori.json");
                return true;
            }
            if ("reset".equalsIgnoreCase(args[2])) {
                if (args.length < 4) {
                    sender.sendMessage("用法：/role persona reset <文件名>");
                    return true;
                }
                boolean reset = plugin.resetPersona(args[3]);
                sender.sendMessage(reset
                        ? "角色设定已重置为内置版本："+args[3]
                        : "重置失败，请检查文件名或插件资源。");
                return true;
            }
            boolean changed = plugin.switchPersona(args[2]);
            sender.sendMessage(changed ? "角色设定已切换："+args[2] : "切换失败，请检查文件名是否存在。");
            return true;
        }
        sender.sendMessage("用法：/role status | enable/disable | groups | reload | memory | forget | persona");
        return true;
    }

    private void handleMemory(CommandSender sender,String[] args) {
        long groupID = sender.getGroupID();
        if (args.length > 2 && "backup".equalsIgnoreCase(args[2])) {
            String file = service.backupAllMemories("manual");
            sender.sendMessage(file.isEmpty()
                    ? "记忆备份失败，请查看控制台日志。"
                    : "全部记忆已备份：backup/"+file);
            return;
        }
        if (args.length > 2 && "backups".equalsIgnoreCase(args[2])) {
            List<String> backups = service.listMemoryBackups();
            if (backups.isEmpty()) {
                sender.sendMessage("backup 目录里还没有记忆备份。");
                return;
            }
            StringBuilder builder = new StringBuilder("记忆备份列表：");
            for (String file : backups) builder.append("\n").append(file);
            sender.sendMessage(builder.toString());
            return;
        }
        if (args.length > 2 && "restore".equalsIgnoreCase(args[2])) {
            if (!sender.hasPermission(CommandPermission.OWNER)) {
                sender.sendMessage("恢复记忆仅 owner 可用。");
                return;
            }
            if (args.length < 4) {
                sender.sendMessage("用法：/role memory restore <文件名>");
                return;
            }
            JSONObject result = service.restoreAllMemories(args[3]);
            if (!result.getBooleanValue("status")) {
                sender.sendMessage(result.getString("message"));
                return;
            }
            sender.sendMessage(result.getString("message")
                    +" 短期 "+result.getIntValue("shortTerm")
                    +" 条，长期 "+result.getIntValue("longTerm")
                    +" 条，永久 "+result.getIntValue("globalMemory")+" 条。");
            return;
        }
        if (args.length > 2 && "merge".equalsIgnoreCase(args[2])) {
            boolean started = service.mergeLongMemoryNow(groupID);
            sender.sendMessage(started
                    ? "长期记忆整理合并已开始，请查看控制台日志确认结果。"
                    : "当前群已有长期记忆整理任务在执行。");
            return;
        }
        int page = 1;
        if (args.length > 2) {
            try {
                page = Integer.parseInt(args[2]);
                if (page < 1) page = 1;
            } catch (Exception ignored) {
            }
        }
        JSONObject result = service.memoryStats(groupID);
        JSONArray memories = result.getJSONArray("memories");
        int pageSize = 12;
        int total = memories == null ? 0 : memories.size();
        int totalPages = Math.max(1,(total + pageSize - 1) / pageSize);
        if (page > totalPages) page = totalPages;
        JSONArray pageMemories = new JSONArray();
        if (memories != null) {
            int start = (page - 1) * pageSize;
            int end = Math.min(total,start + pageSize);
            for (int i = start; i < end; i++) pageMemories.add(memories.getJSONObject(i));
        }
        JSONObject pageData = new JSONObject(true);
        pageData.putAll(result);
        pageData.put("memories",memories == null ? new JSONArray() : memories);
        byte[] image = RoleplayMemoryImageRenderer.render(pageData,page,pageSize);
        if (image != null) {
            sender.sendImage(ImageUtil.toBase64Uri(image));
            return;
        }
        StringBuilder builder = new StringBuilder("角色："+result.getString("role"))
                .append("\n短期记忆：").append(result.getString("shortSummary"))
                .append("\n长期记忆数：").append(total)
                .append("\n第 ").append(page).append(" / ").append(totalPages).append(" 页");
        for (int i = 0; i < pageMemories.size(); i++) {
            JSONObject item = pageMemories.getJSONObject(i);
            builder.append("\n- [").append(item.getString("type")).append("] ")
                    .append(item.getString("content"));
        }
        sender.sendMessage(builder.toString());
    }

    private void handleGlobalMemory(CommandSender sender,String[] args) {
        RoleplayGlobalMemoryService memoryService = service.getGlobalMemoryService();
        if (args.length > 2 && "backup".equalsIgnoreCase(args[2])) {
            String file = service.backupAllMemories("global-memory-backup");
            sender.sendMessage(file.isEmpty()
                    ? "永久记忆备份失败，请查看控制台日志。"
                    : "全部记忆已备份：backup/"+file);
            return;
        }
        if (args.length > 2 && "merge".equalsIgnoreCase(args[2])) {
            memoryService.mergeNow();
            sender.sendMessage("永久记忆整理合并已执行，请查看控制台日志确认结果。");
            return;
        }
        if (args.length > 2 && "group".equalsIgnoreCase(args[2])) {
            handleGlobalMemoryGroups(sender,args);
            return;
        }
        int page = 1;
        if (args.length > 2) {
            try {
                page = Math.max(1,Integer.parseInt(args[2]));
            } catch (Exception ignored) {
            }
        }
        JSONArray memories = memoryService.list(plugin.getRoleplayConfig().globalMemoryMaxItems);
        int pageSize = 12;
        int totalPages = Math.max(1,(memories.size() + pageSize - 1) / pageSize);
        if (page > totalPages) page = totalPages;
        JSONArray pageMemories = new JSONArray();
        int start = (page - 1) * pageSize;
        int end = Math.min(memories.size(),start + pageSize);
        for (int i = start; i < end; i++) {
            JSONObject item = new JSONObject(true);
            item.put("type",memories.getJSONObject(i).getString("type"));
            item.put("subjectID",0L);
            item.put("importance",memories.getJSONObject(i).getIntValue("importance"));
            item.put("content",memories.getJSONObject(i).getString("content"));
            pageMemories.add(item);
        }
        JSONObject data = new JSONObject(true);
        data.put("role","全局永久记忆");
        data.put("groupID",0L);
        data.put("shortSummary","所有群共享，不绑定用户。学习白名单："
                +safeList(plugin.getRoleplayConfig().globalMemoryLearnGroups)+"；总记忆数："+memories.size());
        data.put("memories",memories);
        byte[] image = RoleplayMemoryImageRenderer.render(data,page,pageSize);
        if (image != null) {
            sender.sendImage(ImageUtil.toBase64Uri(image));
        } else {
            StringBuilder builder = new StringBuilder("全局永久记忆：");
            for (int i = 0; i < pageMemories.size(); i++) {
                JSONObject item = pageMemories.getJSONObject(i);
                builder.append("\n#").append(i + start + 1)
                        .append(" [").append(item.getString("type")).append("] ")
                        .append(item.getString("content"));
            }
            sender.sendMessage(builder.toString());
        }
    }

    private void handleGlobalMemoryGroups(CommandSender sender,String[] args) {
        String action = args.length > 3 ? args[3].toLowerCase() : "list";
        String current = plugin.getRoleplayConfig().globalMemoryLearnGroups;
        if ("list".equals(action)) {
            sender.sendMessage("永久记忆学习白名单："
                    +(current == null || current.trim().isEmpty() ? "空" : current));
            return;
        }
        if ("clear".equals(action)) {
            boolean changed = plugin.setGlobalMemoryLearnGroups("");
            sender.sendMessage(changed ? "永久记忆学习白名单已清空。" : "保存配置失败，请检查 config.yml 权限。");
            return;
        }
        if (!"add".equals(action) && !"remove".equals(action) && !"set".equals(action)) {
            sender.sendMessage("用法：/role gmemory group [list|add|remove|set|clear] [群号...]");
            return;
        }
        if (args.length < 5) {
            sender.sendMessage("请提供要设置的群号。");
            return;
        }
        Set<String> groups = new LinkedHashSet<>();
        if (!"set".equals(action)) groups.addAll(splitCsv(current));
        for (int i = 4; i < args.length; i++) {
            for (String item : splitCsv(args[i])) {
                try {
                    long groupID = Long.parseLong(item);
                    if (groupID <= 0) throw new NumberFormatException();
                    groups.add(String.valueOf(groupID));
                } catch (Exception e) {
                    sender.sendMessage("群号格式不正确："+item);
                    return;
                }
            }
        }
        if ("remove".equals(action)) {
            for (int i = 4; i < args.length; i++) {
                for (String item : splitCsv(args[i])) groups.remove(item);
            }
        }
        String value = joinCsv(groups);
        boolean changed = plugin.setGlobalMemoryLearnGroups(value);
        sender.sendMessage(changed
                ? "永久记忆学习白名单已更新："+(value.isEmpty() ? "空" : value)
                : "保存配置失败，请检查 config.yml 权限。");
    }

    private String safeList(String value) {
        return value == null || value.trim().isEmpty() ? "空" : value.trim();
    }

    private void handleSpeech(CommandSender sender,String[] args) {
        RoleplaySpeechCorpusService speech = service.getSpeechCorpusService();
        String action = args.length > 2 ? args[2].toLowerCase() : "stats";
        if ("stats".equals(action)) {
            JSONObject stats = speech.stats();
            sender.sendMessage("台词语料："
                    +"\n启用："+(stats.getBooleanValue("enabled") ? "是" : "否")
                    +"\n条数："+stats.getIntValue("count")
                    +"\n标签数："+stats.getIntValue("tagCount")
                    +"\n文件："+stats.getString("path"));
            return;
        }
        if ("reload".equals(action)) {
            plugin.reloadRoleplay();
            sender.sendMessage("台词语料已重载，当前条数："+speech.count());
            return;
        }
        if ("on".equals(action) || "off".equals(action)) {
            boolean changed = plugin.setSpeechCorpusEnable("on".equals(action));
            sender.sendMessage(changed
                    ? "台词语料检索已"+("on".equals(action) ? "开启。" : "关闭。")
                    : "保存配置失败，请检查 config.yml 权限。");
            return;
        }
        if ("search".equals(action)) {
            if (args.length < 4) {
                sender.sendMessage("用法：/role speech search <文本>");
                return;
            }
            JSONArray results = speech.search(joinArgs(args,3),10);
            if (results.isEmpty()) {
                sender.sendMessage("没有检索到合适的台词示例。");
                return;
            }
            StringBuilder builder = new StringBuilder("台词检索结果：");
            for (int i = 0; i < results.size(); i++) {
                JSONObject item = results.getJSONObject(i);
                builder.append("\n#").append(i + 1)
                        .append(" ").append(item.getString("text"))
                        .append(" [").append(item.getString("emotion"))
                        .append("/").append(item.getString("scene")).append("]");
            }
            sender.sendMessage(builder.toString());
            return;
        }
        sender.sendMessage("用法：/role speech stats | reload | search <文本> | on | off");
    }

    private String joinArgs(String[] args,int start) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (builder.length() > 0) builder.append(" ");
            builder.append(args[i]);
        }
        return builder.toString().trim();
    }

    private void handleConfig(CommandSender sender,String[] args) {
        if (args.length > 2 && "repair".equalsIgnoreCase(args[2])) {
            int repaired = plugin.repairConfig();
            sender.sendMessage(repaired > 0
                    ? "配置补全完成，共处理 "+repaired+" 项。"
                    : "配置没有缺失项，无需补全。");
            return;
        }
        sender.sendMessage("Roleplay 配置：\n"
                +"文件："+plugin.getConfig().getPath()+"\n"
                +"结构版本："+plugin.getConfig().getString("configVersion","未知")+"\n"
                +"缺失配置会在启动和重载时自动补全。\n"
                +"手动补全：/role config repair");
    }

    private void handleBot(CommandSender sender,String[] args) {
        if (args.length < 3 || "status".equalsIgnoreCase(args[2])) {
            sendBotStatus(sender);
            return;
        }
        String action = args[2].toLowerCase();
        if ("chance".equals(action)) {
            if (args.length < 4) {
                sender.sendMessage("用法：/role bot chance <0.3-1>");
                return;
            }
            double chance;
            try {
                chance = Double.parseDouble(args[3]);
            } catch (Exception e) {
                sender.sendMessage("概率格式不正确。");
                return;
            }
            if (chance < 0 || chance > 1) {
                sender.sendMessage("概率必须在 0 到 1 之间。");
                return;
            }
            boolean changed = plugin.setOtherRoleBotReplyChance(chance);
            sender.sendMessage(changed
                    ? "机器人互聊概率已设置为："+chance
                    : "保存配置失败，请检查 config.yml 权限。");
            if (changed && (chance < 0.3 || chance > 0.7)) {
                sender.sendMessage("提示：推荐范围是 0.3 到 0.7。");
            }
            return;
        }
        if ("max".equals(action)) {
            if (args.length < 4) {
                sender.sendMessage("用法：/role bot max <1-10>");
                return;
            }
            int count;
            try {
                count = Integer.parseInt(args[3]);
            } catch (Exception e) {
                sender.sendMessage("次数格式不正确。");
                return;
            }
            if (count < 1 || count > 10) {
                sender.sendMessage("次数必须在 1 到 10 之间。");
                return;
            }
            boolean changed = plugin.setMaxConsecutiveOtherRoleMessages(count);
            sender.sendMessage(changed
                    ? "连续回应其他角色的上限已设置为："+count
                    : "保存配置失败，请检查 config.yml 权限。");
            return;
        }
        if ("qq".equals(action)) {
            handleBotCsv(sender,args,true);
            return;
        }
        if ("name".equals(action)) {
            handleBotCsv(sender,args,false);
            return;
        }
        sender.sendMessage("用法：/role bot status | chance <0.3-1> | max <1-10> | qq | name");
    }

    private void handleBotCsv(CommandSender sender,String[] args,boolean qq) {
        String action = args.length > 3 ? args[3].toLowerCase() : "list";
        String current = qq ? plugin.getRoleplayConfig().otherRoleBotQQs : plugin.getRoleplayConfig().otherRoleBotNames;
        if ("list".equals(action)) {
            sender.sendMessage(qq
                    ? "其他角色机器人 QQ："+(current == null || current.trim().isEmpty() ? "未配置" : current)
                    : "其他角色机器人名称："+(current == null || current.trim().isEmpty() ? "未配置" : current));
            return;
        }
        if (!qq && "reset".equals(action)) {
            boolean changed = plugin.setOtherRoleBotNames(RoleplayConfig.DEFAULT_OTHER_ROLE_BOT_NAMES);
            sender.sendMessage(changed ? "其他角色机器人名称已恢复默认。" : "保存配置失败，请检查 config.yml 权限。");
            return;
        }
        if ("clear".equals(action)) {
            boolean changed = qq ? plugin.setOtherRoleBotQQs("") : plugin.setOtherRoleBotNames("");
            sender.sendMessage(changed
                    ? (qq ? "其他角色机器人 QQ 已清空。" : "其他角色机器人名称已清空。")
                    : "保存配置失败，请检查 config.yml 权限。");
            return;
        }
        if (!"add".equals(action) && !"remove".equals(action) && !"set".equals(action)) {
            sender.sendMessage("用法：/role bot "+(qq ? "qq" : "name")+" [list|add|remove|set|clear"
                    +(!qq ? "|reset" : "")+"] [值...]");
            return;
        }
        if (args.length < 5) {
            sender.sendMessage("请提供要"+(qq ? "设置的 QQ" : "设置的名称")+"。");
            return;
        }
        Set<String> values = new LinkedHashSet<>();
        if (!"set".equals(action)) values.addAll(splitCsv(current));
        for (int i = 4; i < args.length; i++) {
            for (String item : splitCsv(args[i])) {
                if (qq) {
                    try {
                        long userID = Long.parseLong(item);
                        if (userID <= 0) throw new NumberFormatException();
                        values.add(String.valueOf(userID));
                    } catch (Exception e) {
                        sender.sendMessage("QQ 格式不正确："+item);
                        return;
                    }
                } else if (!item.isEmpty()) {
                    values.add(item);
                }
            }
        }
        if ("remove".equals(action)) {
            for (int i = 4; i < args.length; i++) {
                for (String item : splitCsv(args[i])) values.remove(item);
            }
        }
        String value = joinCsv(values);
        boolean changed = qq ? plugin.setOtherRoleBotQQs(value) : plugin.setOtherRoleBotNames(value);
        sender.sendMessage(changed
                ? (qq ? "其他角色机器人 QQ 已更新：" : "其他角色机器人名称已更新：")
                + (value.isEmpty() ? "空" : value)
                : "保存配置失败，请检查 config.yml 权限。");
    }

    private void sendBotStatus(CommandSender sender) {
        RoleplayConfig config = plugin.getRoleplayConfig();
        sender.sendMessage("机器人互聊设置：\n"
                +"接话概率："+config.otherRoleBotReplyChance+"\n"
                +"连续上限："+config.maxConsecutiveOtherRoleMessages+" 条\n"
                +"机器人 QQ："+(config.otherRoleBotQQs == null || config.otherRoleBotQQs.isEmpty()
                ? "未配置" : config.otherRoleBotQQs)+"\n"
                +"名称识别："+(config.otherRoleBotNames == null || config.otherRoleBotNames.isEmpty()
                ? "未配置" : config.otherRoleBotNames));
    }

    private Set<String> splitCsv(String text) {
        Set<String> result = new LinkedHashSet<>();
        if (text == null || text.trim().isEmpty()) return result;
        for (String item : text.split("[,，]")) {
            String value = item.trim();
            if (!value.isEmpty()) result.add(value);
        }
        return result;
    }

    private String joinCsv(Set<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) builder.append(",");
            builder.append(value);
        }
        return builder.toString();
    }
}
