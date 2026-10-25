package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Command.CommandPermission;
import org.moboxlab.moboxbot.API.Command.CommandSender;
import org.moboxlab.moboxbot.API.Util.ImageUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Roleplay 命令处理器：RoleplayMemoryCommandHandler
 */
public class RoleplayMemoryCommandHandler {
    private final RoleplayCommand command;

    public RoleplayMemoryCommandHandler(RoleplayCommand command) {
        this.command = command;
    }

    void handleMemory(CommandSender sender,String[] args) {
        long groupID = sender.getGroupID();
        if (args.length > 2 && "backup".equalsIgnoreCase(args[2])) {
            String file = command.service().backupAllMemories("manual");
            sender.sendMessage(file.isEmpty()
                    ? "记忆备份失败，请查看控制台日志。"
                    : "全部记忆已备份：backup/"+file);
            return;
        }
        if (args.length > 2 && "backups".equalsIgnoreCase(args[2])) {
            List<String> backups = command.service().listMemoryBackups();
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
            JSONObject result = command.service().restoreAllMemories(args[3]);
            if (!result.getBooleanValue("status")) {
                sender.sendMessage(result.getString("message"));
                return;
            }
            sender.sendMessage(result.getString("message")
                    +" 短期 "+result.getIntValue("shortTerm")
                    +" 条，结构化上下文 "+result.getIntValue("structuredContext")
                    +" 条，长期 "+result.getIntValue("longTerm")
                    +" 条，永久 "+result.getIntValue("globalMemory")+" 条"
                    +(result.getBooleanValue("emotion") ? "，情绪关系已恢复。" : "，备份不含情绪关系。"));
            return;
        }
        if (args.length > 2 && "merge".equalsIgnoreCase(args[2])) {
            boolean started = command.service().mergeLongMemoryNow(groupID,true,sender::sendMessage);
            sender.sendMessage(started
                    ? "长期记忆整理合并已开始，完成后会在当前会话反馈结果。"
                    : "当前群已有长期记忆整理任务在执行。");
            return;
        }
        if (args.length > 2 && "timeline".equalsIgnoreCase(args[2])) {
            int page = args.length > 3 ? command.parsePage(args,3) : 1;
            int pageSize = 12;
            int total = command.service().memoryCount(groupID);
            int totalPages = Math.max(1,(total + pageSize - 1) / pageSize);
            if (page > totalPages) page = totalPages;
            JSONArray events = command.service().memoryQueryService().timeline(groupID,page,pageSize);
            JSONArray rows = new JSONArray();
            for (Object object : events) {
                JSONObject item = (JSONObject)object;
                JSONObject copy = new JSONObject(true);
                copy.put("type",item.getString("type"));
                copy.put("subjectID",item.getLongValue("subjectID"));
                copy.put("importance",item.getIntValue("importance"));
                copy.put("eventTime",item.getLongValue("eventTime"));
                copy.put("content","["+command.service().formatMemoryTime(item.getLongValue("eventTime"))+"] "
                        +item.getString("content"));
                rows.add(copy);
            }
            JSONObject data = new JSONObject(true);
            data.put("role",command.service().getRoleName());
            data.put("groupID",groupID);
            data.put("shortSummary","事件时间线：按事件发生时间倒序排列，第 "+page+" / "+totalPages+" 页");
            data.put("memories",rows);
            byte[] image = RoleplayMemoryImageRenderer.render(data,page,pageSize);
            if (image != null) {
                sender.sendImage(ImageUtil.toBase64Uri(image));
                return;
            }
            StringBuilder builder = new StringBuilder("事件时间线（第 "+page+" / "+totalPages+" 页）：");
            for (Object object : rows) {
                JSONObject item = (JSONObject)object;
                builder.append("\n- [").append(item.getString("type")).append("] ")
                        .append(item.getString("content"));
            }
            sender.sendMessage(builder.toString());
            return;
        }
        if (args.length > 2 && "conflicts".equalsIgnoreCase(args[2])) {
            int limit = args.length > 3 ? command.parsePage(args,3) : 12;
            if (limit > 20) limit = 20;
            JSONArray archived = command.service().memoryQueryService().archived(groupID,limit);
            if (archived.isEmpty()) {
                sender.sendMessage("当前群没有被冲突归档的长期记忆。");
                return;
            }
            StringBuilder builder = new StringBuilder("已归档冲突记忆：");
            for (Object object : archived) {
                JSONObject item = (JSONObject)object;
                builder.append("\n#").append(item.getLongValue("id"))
                        .append(" [").append(item.getString("type")).append("] ")
                        .append(item.getString("content"))
                        .append(" -> 新记忆#").append(item.getLongValue("supersededBy"));
            }
            sender.sendMessage(builder.toString());
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
        JSONObject result = command.service().memoryStats(groupID);
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

    void handleGlobalMemory(CommandSender sender,String[] args) {
        RoleplayGlobalMemoryService memoryService = command.service().getGlobalMemoryService();
        if (args.length > 2 && "backup".equalsIgnoreCase(args[2])) {
            String file = command.service().backupAllMemories("global-memory-backup");
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
        JSONArray memories = memoryService.list(command.plugin().getRoleplayConfig().globalMemoryMaxItems);
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
                +command.safeList(command.plugin().getRoleplayConfig().globalMemoryLearnGroups)+"；总记忆数："+memories.size());
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

    void handleGlobalMemoryGroups(CommandSender sender,String[] args) {
        String action = args.length > 3 ? args[3].toLowerCase() : "list";
        String current = command.plugin().getRoleplayConfig().globalMemoryLearnGroups;
        if ("list".equals(action)) {
            sender.sendMessage("永久记忆学习白名单："
                    +(current == null || current.trim().isEmpty() ? "空" : current));
            return;
        }
        if ("clear".equals(action)) {
            boolean changed = command.plugin().setGlobalMemoryLearnGroups("");
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
        if (!"set".equals(action)) groups.addAll(command.splitCsv(current));
        for (int i = 4; i < args.length; i++) {
            for (String item : command.splitCsv(args[i])) {
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
                for (String item : command.splitCsv(args[i])) groups.remove(item);
            }
        }
        String value = command.joinCsv(groups);
        boolean changed = command.plugin().setGlobalMemoryLearnGroups(value);
        sender.sendMessage(changed
                ? "永久记忆学习白名单已更新："+(value.isEmpty() ? "空" : value)
                : "保存配置失败，请检查 config.yml 权限。");
    }
}
