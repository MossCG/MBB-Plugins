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
                "/role blacklist [list|add|remove|clear] [QQ] [原因]",
                "/role quiet [on|off|list|add <QQ>|remove <QQ>|clear]",
                "/role member [QQ]",
                "/role member clear <QQ>",
                "/role mood",
                "/role mood reset",
                "/role emotion [QQ] [页码]",
                "/role emotion reset [QQ]",
                "/role emotion log [页码]",
                "/role emotion affinity set <QQ> <0-100>",
                "/role emotion reason set <QQ> <原因>",
                "/role emotion reason clear <QQ>",
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
                "/role kb [list|reload|search <文本>|on|off]",
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
                    +"\n短期记忆："+result.getString("shortSummary")
                    +"\n当前情绪："+result.getJSONObject("mood").getString("label"));
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
        if ("mood".equals(action)) {
            handleMood(sender,args);
            return true;
        }
        if ("emotion".equals(action)) {
            handleEmotion(sender,args);
            return true;
        }
        if ("blacklist".equals(action)) {
            handleBlacklist(sender,args);
            return true;
        }
        if ("quiet".equals(action)) {
            handleQuiet(sender,args);
            return true;
        }
        if ("member".equals(action)) {
            handleMember(sender,args);
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
        if ("kb".equals(action) || "knowledge".equals(action)) {
            handleKnowledge(sender,args);
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
                        +"\n内置示例：persona-aris.json；其他 persona 请放入插件数据目录后自行切换。");
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
                    +" 条，永久 "+result.getIntValue("globalMemory")+" 条"
                    +(result.getBooleanValue("emotion") ? "，情绪关系已恢复。" : "，备份不含情绪关系。"));
            return;
        }
        if (args.length > 2 && "merge".equalsIgnoreCase(args[2])) {
            boolean started = service.mergeLongMemoryNow(groupID,true,sender::sendMessage);
            sender.sendMessage(started
                    ? "长期记忆整理合并已开始，完成后会在当前会话反馈结果。"
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
            if (memoryService.isMerging()) {
                sender.sendMessage("永久记忆已有合并任务在执行。");
                return;
            }
            sender.sendMessage("永久记忆整理合并已开始，完成后会在当前会话反馈结果。");
            sender.sendMessage(memoryService.mergeNow(true));
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

    private void handleKnowledge(CommandSender sender,String[] args) {
        RoleplayKnowledgeService knowledge = service.getKnowledgeService();
        String action = args.length > 2 ? args[2].toLowerCase() : "list";
        if ("list".equals(action) || "stats".equals(action)) {
            JSONObject stats = knowledge.stats();
            StringBuilder builder = new StringBuilder("知识库："
                    +"\n启用："+(stats.getBooleanValue("enabled") ? "是" : "否")
                    +"\n库数："+stats.getIntValue("libraryCount")
                    +" 条目："+stats.getIntValue("entryCount")
                    +" 小节："+stats.getIntValue("sectionCount")
                    +"\n目录："+stats.getString("path"));
            JSONArray libraries = stats.getJSONArray("libraries");
            for (int i = 0; i < libraries.size(); i++) {
                JSONObject item = libraries.getJSONObject(i);
                builder.append("\n- ").append(item.getString("id"))
                        .append("（").append(item.getString("name")).append("）")
                        .append(item.getBooleanValue("enabled") ? "" : " [未启用]")
                        .append(" 条目=").append(item.getIntValue("entries"))
                        .append(" 小节=").append(item.getIntValue("sections"));
                String scope = item.getString("scope");
                if (scope != null && !scope.isEmpty()) builder.append("\n  ").append(scope);
            }
            if (libraries.isEmpty()) {
                builder.append("\n还没有知识库，按 KNOWLEDGE.md 把内容放进该目录。");
            }
            sender.sendMessage(builder.toString());
            return;
        }
        if ("reload".equals(action)) {
            plugin.reloadRoleplay();
            sender.sendMessage("知识库已重载，当前 "+knowledge.entryCount()+" 条目，"
                    +knowledge.sectionCount()+" 小节。");
            return;
        }
        if ("on".equals(action) || "off".equals(action)) {
            boolean changed = plugin.setKnowledgeEnable("on".equals(action));
            sender.sendMessage(changed
                    ? "知识库已"+("on".equals(action) ? "开启。" : "关闭。")
                    : "保存配置失败，请检查 config.yml 权限。");
            return;
        }
        if ("search".equals(action)) {
            if (args.length < 4) {
                sender.sendMessage("用法：/role kb search <文本>");
                return;
            }
            JSONArray results = knowledge.search(joinArgs(args,3),6);
            if (results.isEmpty()) {
                sender.sendMessage("没有检索到知识库内容，检查库名、别名和最低分设置。");
                return;
            }
            StringBuilder builder = new StringBuilder("知识库检索结果：");
            for (int i = 0; i < results.size(); i++) {
                JSONObject item = results.getJSONObject(i);
                builder.append("\n#").append(i + 1)
                        .append(" [").append(item.getString("library")).append("] ")
                        .append(item.getString("entry")).append(" · ")
                        .append(item.getString("section"))
                        .append(" 分数=").append(item.getDoubleValue("score"))
                        .append(item.getBooleanValue("locked") ? " 锁定" : "")
                        .append("\n  ").append(item.getString("text"));
            }
            sender.sendMessage(builder.toString());
            return;
        }
        sender.sendMessage("用法：/role kb list | reload | search <文本> | on | off");
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

    private void handleMood(CommandSender sender,String[] args) {
        long groupID = sender.getGroupID();
        if (groupID <= 0) {
            sender.sendMessage("请在群聊中使用，或指定群号。");
            return;
        }
        RoleplayEmotionService emotionService = service.getEmotionService();
        if (args.length > 2 && "reset".equalsIgnoreCase(args[2])) {
            if (!sender.hasPermission(CommandPermission.OWNER)) {
                sender.sendMessage("重置群情绪仅 owner 可用。");
                return;
            }
            emotionService.resetMood(groupID);
            sender.sendMessage("当前群情绪已重置。");
            return;
        }
        int page = parsePage(args,2);
        JSONObject data = emotionService.moodStats(groupID,100);
        byte[] image = RoleplayEmotionImageRenderer.render(data,page,8);
        if (image != null) {
            sender.sendImage(ImageUtil.toBase64Uri(image));
            return;
        }
        sender.sendMessage("当前群情绪："
                +data.getJSONObject("mood").getString("label")
                +"\n心情："+data.getJSONObject("mood").getIntValue("valence")
                +"\n精力："+data.getJSONObject("mood").getIntValue("energy")
                +"\n耐心："+data.getJSONObject("mood").getIntValue("patience"));
    }

    private void handleEmotion(CommandSender sender,String[] args) {
        RoleplayEmotionService emotionService = service.getEmotionService();
        long groupID = sender.getGroupID();
        if (groupID <= 0) {
            sender.sendMessage("请在群聊中使用，或指定群号。");
            return;
        }
        if (args.length > 2 && "reason".equalsIgnoreCase(args[2])) {
            handleEmotionReason(sender,args,groupID);
            return;
        }
        if (args.length > 2 && "affinity".equalsIgnoreCase(args[2])) {
            handleEmotionAffinity(sender,args,groupID);
            return;
        }
        if (args.length > 2 && "log".equalsIgnoreCase(args[2])) {
            int page = parsePage(args,3);
            JSONObject data = emotionService.moodStats(groupID,100);
            byte[] image = RoleplayEmotionImageRenderer.render(data,page,8);
            if (image != null) {
                sender.sendImage(ImageUtil.toBase64Uri(image));
                return;
            }
            sender.sendMessage("当前群情绪事件已输出到控制台日志。");
            return;
        }
        long userID = sender.getUserID();
        int page = 1;
        if (args.length > 2) {
            if ("reset".equalsIgnoreCase(args[2])) {
                if (args.length > 3) userID = parseUserID(args[3]);
                if (userID <= 0) {
                    sender.sendMessage("QQ 格式不正确。");
                    return;
                }
                if (!sender.hasPermission(CommandPermission.OWNER)
                        && userID != sender.getUserID()) {
                    sender.sendMessage("重置其他人的关系仅 owner 可用。");
                    return;
                }
                emotionService.resetRelation(groupID,userID);
                sender.sendMessage("用户 "+userID+" 的关系状态已重置。");
                return;
            }
            long parsed = parseUserID(args[2]);
            if (parsed > 0) userID = parsed;
            page = userID == parsed ? parsePage(args,3) : parsePage(args,2);
        }
        if (userID <= 0) {
            sender.sendMessage("QQ 格式不正确。");
            return;
        }
        JSONObject data = emotionService.emotionStats(groupID,userID,100);
        byte[] image = RoleplayEmotionImageRenderer.render(data,page,8);
        if (image != null) {
            sender.sendImage(ImageUtil.toBase64Uri(image));
            return;
        }
        JSONObject relation = data.getJSONObject("relation");
        sender.sendMessage("用户关系 QQ："+userID
                +"\n态度："+relation.getString("emotionLabel")
                +"\n好感："+formatDecimal(relation.getDoubleValue("affinity"))
                +"\n信任："+formatDecimal(relation.getDoubleValue("trust"))
                +"\n厌烦："+formatDecimal(relation.getDoubleValue("annoyance"))
                +"\n原因："+relation.getString("emotionReason"));
    }

    private void handleEmotionReason(CommandSender sender,String[] args,long groupID) {
        String action = args.length > 3 ? args[3].toLowerCase() : "help";
        if ("set".equals(action)) {
            if (!sender.hasPermission(CommandPermission.OWNER)) {
                sender.sendMessage("设置关系原因仅 owner 可用。");
                return;
            }
            if (args.length < 6) {
                sender.sendMessage("用法：/role emotion reason set <QQ> <原因>");
                return;
            }
            long userID = parseUserID(args[4]);
            String reason = joinArgs(args,5);
            if (userID <= 0 || reason.isEmpty()) {
                sender.sendMessage("QQ 或原因格式不正确。");
                return;
            }
            service.getEmotionService().setRelationReason(groupID,userID,reason);
            sender.sendMessage("已设置用户 "+userID+" 的情绪原因。");
            return;
        }
        if ("clear".equals(action)) {
            if (!sender.hasPermission(CommandPermission.OWNER)) {
                sender.sendMessage("清空关系原因仅 owner 可用。");
                return;
            }
            if (args.length < 5) {
                sender.sendMessage("用法：/role emotion reason clear <QQ>");
                return;
            }
            long userID = parseUserID(args[4]);
            if (userID <= 0) {
                sender.sendMessage("QQ 格式不正确。");
                return;
            }
            service.getEmotionService().clearRelationReason(groupID,userID);
            sender.sendMessage("已清空用户 "+userID+" 的情绪原因。");
            return;
        }
        sender.sendMessage("用法：/role emotion reason set <QQ> <原因> | clear <QQ>");
    }

    private void handleEmotionAffinity(CommandSender sender,String[] args,long groupID) {
        if (args.length < 6 || !"set".equalsIgnoreCase(args[3])) {
            sender.sendMessage("用法：/role emotion affinity set <QQ> <0-100>");
            return;
        }
        long userID = parseUserID(args[4]);
        double affinity;
        try {
            affinity = Double.parseDouble(args[5]);
        } catch (Exception e) {
            sender.sendMessage("好感度格式不正确。");
            return;
        }
        if (userID <= 0 || affinity < 0 || affinity > 100) {
            sender.sendMessage("QQ 或好感度范围不正确。");
            return;
        }
        service.getEmotionService().setRelationAffinity(groupID,userID,affinity);
        sender.sendMessage("用户 "+userID+" 的好感度已设置为 "+formatDecimal(affinity)+"。");
    }

    private void handleBlacklist(CommandSender sender,String[] args) {
        long groupID = sender.getGroupID();
        if (groupID <= 0) {
            sender.sendMessage("请在群聊中使用。");
            return;
        }
        String action = args.length > 2 ? args[2].toLowerCase() : "list";
        if ("list".equals(action)) {
            JSONArray entries = service.getBlacklistService().list(groupID);
            if (entries == null || entries.isEmpty()) {
                sender.sendMessage("当前群黑名单为空。");
                return;
            }
            StringBuilder builder = new StringBuilder("当前群黑名单：");
            for (Object object : entries) {
                JSONObject item = (JSONObject) object;
                builder.append("\n").append(item.getLongValue("userID"));
                String reason = item.getString("reason");
                if (reason != null && !reason.trim().isEmpty()) {
                    builder.append("  ").append(reason.trim());
                }
            }
            sender.sendMessage(builder.toString());
            return;
        }
        if ("clear".equals(action)) {
            service.getBlacklistService().clear(groupID);
            sender.sendMessage("当前群黑名单已清空。");
            return;
        }
        if (args.length < 4) {
            sender.sendMessage("用法：/role blacklist add <QQ> [原因] | remove <QQ> | clear | list");
            return;
        }
        long userID = parseUserID(args[3]);
        if (userID <= 0) {
            sender.sendMessage("QQ 格式不正确。");
            return;
        }
        if ("add".equals(action)) {
            String reason = joinArgs(args,4);
            service.blacklistAdd(groupID,userID,reason,sender.getUserID());
            sender.sendMessage("用户 "+userID+" 已加入当前群黑名单。");
            return;
        }
        if ("remove".equals(action)) {
            boolean removed = service.getBlacklistService().remove(groupID,userID);
            sender.sendMessage(removed
                    ? "用户 "+userID+" 已移出黑名单。"
                    : "用户 "+userID+" 不在黑名单中。");
            return;
        }
        sender.sendMessage("用法：/role blacklist add <QQ> [原因] | remove <QQ> | clear | list");
    }

    /**
     * /role quiet：免打扰名单，名单内的群员只在主动叫到角色时才得到回复
     */
    private void handleQuiet(CommandSender sender,String[] args) {
        long groupID = sender.getGroupID();
        if (groupID <= 0) {
            sender.sendMessage("请在群聊中使用。");
            return;
        }
        RoleplayQuietService quiet = service.getQuietService();
        String action = args.length > 2 ? args[2].toLowerCase() : "status";
        if ("on".equals(action) || "off".equals(action)) {
            if (!sender.hasPermission(CommandPermission.BOT_ADMIN)) {
                sender.sendMessage("开关免打扰名单需要机器人管理员权限。");
                return;
            }
            boolean enabled = "on".equals(action);
            quiet.setEnabled(groupID,enabled);
            sender.sendMessage("本群免打扰名单已"+("on".equals(action) ? "启用" : "停用")+"。");
            return;
        }
        if ("list".equals(action)) {
            JSONArray entries = quiet.list(groupID);
            StringBuilder builder = new StringBuilder("本群免打扰名单"
                    +"（"+(quiet.isEnabled(groupID) ? "已启用" : "已停用")+"）：");
            if (entries == null || entries.isEmpty()) {
                builder.append("\n空");
            } else {
                for (Object object : entries) {
                    builder.append("\n").append(((JSONObject) object).getLongValue("userID"));
                }
            }
            builder.append("\n名单内的群员只在主动叫到角色时才得到回复，其他群员不受影响。");
            sender.sendMessage(builder.toString());
            return;
        }
        if ("clear".equals(action)) {
            quiet.clear(groupID);
            sender.sendMessage("本群免打扰名单已清空。");
            return;
        }
        if ("remove".equals(action)) {
            long targetID = sender.getUserID();
            if (args.length > 3) {
                targetID = parseUserID(args[3]);
                if (targetID <= 0) {
                    sender.sendMessage("QQ 格式不正确。");
                    return;
                }
                if (targetID != sender.getUserID()
                        && !sender.hasPermission(CommandPermission.BOT_ADMIN)) {
                    sender.sendMessage("只能把自己移出免打扰名单，移除他人需要机器人管理员权限。");
                    return;
                }
            }
            boolean removed = quiet.remove(groupID,targetID);
            sender.sendMessage(removed
                    ? "用户 "+targetID+" 已移出免打扰名单。"
                    : "用户 "+targetID+" 不在免打扰名单中。");
            return;
        }
        if ("add".equals(action)) {
            long targetID = sender.getUserID();
            if (args.length > 3) {
                //群员自助：只允许把自己加入名单，避免被他人强制免打扰
                targetID = parseUserID(args[3]);
                if (targetID <= 0) {
                    sender.sendMessage("QQ 格式不正确。");
                    return;
                }
                if (targetID != sender.getUserID()
                        && !sender.hasPermission(CommandPermission.BOT_ADMIN)) {
                    sender.sendMessage("只能把自己加入免打扰名单，添加他人需要机器人管理员权限。");
                    return;
                }
            }
            quiet.add(groupID,targetID,sender.getUserID());
            sender.sendMessage("用户 "+targetID+" 已加入本群免打扰名单，"
                    +"只有主动叫到角色时才会得到回复。");
            return;
        }
        sender.sendMessage("用法：/role quiet [on|off|list|add <QQ>|remove <QQ>|clear]"
                +"\n群员可自助使用 /role quiet add 与 /role quiet remove。");
    }

    /**
     * /role member [QQ]：查看角色对某个群员的个人印象
     */
    private void handleMember(CommandSender sender,String[] args) {
        long groupID = sender.getGroupID();
        if (groupID <= 0) {
            sender.sendMessage("请在群聊中使用。");
            return;
        }
        String action = args.length > 2 ? args[2].toLowerCase() : "";
        if ("clear".equals(action)) {
            if (args.length < 4) {
                sender.sendMessage("用法：/role member clear <QQ>");
                return;
            }
            long targetID = parseUserID(args[3]);
            if (targetID <= 0) {
                sender.sendMessage("QQ 格式不正确。");
                return;
            }
            sender.sendMessage(service.getMemberService().remove(groupID,targetID)
                    ? "已清空对 "+targetID+" 的个人印象。"
                    : "没有找到 "+targetID+" 的个人印象。");
            return;
        }
        long userID = sender.getUserID();
        if (args.length > 2 && !action.isEmpty()) {
            userID = parseUserID(args[2]);
            if (userID <= 0) {
                sender.sendMessage("QQ 格式不正确。");
                return;
            }
        }
        JSONObject profile = service.getMemberService().profile(groupID,userID);
        if (profile == null) {
            sender.sendMessage("还没有对 "+userID+" 的个人印象。");
            return;
        }
        sender.sendMessage("对 "+userID+"（"+profile.getString("userName")+"）的个人印象："
                +"\n称呼："+orDash(profile.getString("alias"))
                +"\n喜欢："+orDash(profile.getString("likes"))
                +"\n不喜欢："+orDash(profile.getString("dislikes"))
                +"\n相处方式："+orDash(profile.getString("notes")));
    }

    private String orDash(String value) {
        return value == null || value.trim().isEmpty() ? "暂无" : value.trim();
    }

    private long parseUserID(String value) {
        try {
            long userID = Long.parseLong(value == null ? "" : value.trim());
            return userID > 0 ? userID : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private int parsePage(String[] args,int index) {
        if (args == null || args.length <= index) return 1;
        try {
            int page = Integer.parseInt(args[index]);
            return page < 1 ? 1 : page;
        } catch (Exception e) {
            return 1;
        }
    }

    private String formatDecimal(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.001) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(java.util.Locale.CHINA,"%.1f",value);
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
