package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Command.BotCommand;
import org.moboxlab.moboxbot.API.Command.CommandPermission;
import org.moboxlab.moboxbot.API.Command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * /reminder 用户提醒管理命令
 */
public class RoleplayReminderCommand extends BotCommand {
    private final RoleplayReminderService reminderService;

    public RoleplayReminderCommand(RoleplayReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Override
    public List<String> prefix() {
        List<String> prefixList = new ArrayList<>();
        prefixList.add("reminder");
        prefixList.add("提醒");
        return prefixList;
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
        return "管理自己的定时提醒";
    }

    @Override
    public List<String> usage() {
        return Arrays.asList(
                "/reminder list [页码]",
                "/reminder show <ID>",
                "/reminder edit <ID> <时间> <内容>",
                "/reminder delete <ID>",
                "/reminder clear");
    }

    @Override
    public boolean execute(CommandSender sender,String[] args) {
        long groupID = sender.getGroupID();
        if (groupID <= 0) {
            sender.sendMessage("请在群聊中使用提醒管理命令。");
            return true;
        }
        long userID = sender.getUserID();
        String action = args.length > 1 ? args[1].toLowerCase() : "list";
        if ("list".equals(action)) {
            list(sender,groupID,userID,args);
            return true;
        }
        if ("show".equals(action)) {
            show(sender,groupID,userID,args);
            return true;
        }
        if ("edit".equals(action)) {
            edit(sender,groupID,userID,args);
            return true;
        }
        if ("delete".equals(action) || "remove".equals(action) || "del".equals(action)) {
            delete(sender,groupID,userID,args);
            return true;
        }
        if ("clear".equals(action)) {
            int count = reminderService.cancelAll(groupID,userID);
            sender.sendMessage(count > 0 ? "已取消 "+count+" 条待触发提醒。" : "当前没有待触发提醒。");
            return true;
        }
        sender.sendMessage("用法：/reminder list | show <ID> | edit <ID> <时间> <内容> | delete <ID> | clear");
        return true;
    }

    private void list(CommandSender sender,long groupID,long userID,String[] args) {
        int page = 1;
        if (args.length > 2) {
            try {
                page = Math.max(1,Integer.parseInt(args[2]));
            } catch (Exception ignored) {
            }
        }
        JSONArray rows = reminderService.listPending(groupID,userID);
        int pageSize = 10;
        int totalPages = Math.max(1,(rows.size() + pageSize - 1) / pageSize);
        if (page > totalPages) page = totalPages;
        if (rows.isEmpty()) {
            sender.sendMessage("当前没有待触发提醒。");
            return;
        }
        int start = (page - 1) * pageSize;
        int end = Math.min(rows.size(),start + pageSize);
        StringBuilder builder = new StringBuilder("待触发提醒 ")
                .append(page).append("/").append(totalPages).append("：");
        for (int i = start; i < end; i++) {
            JSONObject row = rows.getJSONObject(i);
            builder.append("\n#").append(row.getLongValue("id"))
                    .append("  ").append(reminderService.formatTime(row.getLongValue("remindTime")))
                    .append("  ").append(targetText(row.getString("target")))
                    .append("  ").append(safe(row.getString("task")));
        }
        sender.sendMessage(builder.toString());
    }

    private void show(CommandSender sender,long groupID,long userID,String[] args) {
        if (args.length < 3) {
            sender.sendMessage("用法：/reminder show <ID>");
            return;
        }
        long id = parseID(args[2]);
        if (id <= 0) {
            sender.sendMessage("提醒 ID 格式不正确。");
            return;
        }
        JSONObject row = reminderService.getPending(id,groupID,userID);
        if (row == null) {
            sender.sendMessage("没有找到属于你的待触发提醒。");
            return;
        }
        sender.sendMessage("提醒 #"+id+"\n"
                +"时间："+reminderService.formatTime(row.getLongValue("remindTime"))+"\n"
                +"目标："+targetText(row.getString("target"))+"\n"
                +"内容："+safe(row.getString("task")));
    }

    private void edit(CommandSender sender,long groupID,long userID,String[] args) {
        if (args.length < 5) {
            sender.sendMessage("用法：/reminder edit <ID> <时间> <内容>");
            return;
        }
        long id = parseID(args[2]);
        if (id <= 0) {
            sender.sendMessage("提醒 ID 格式不正确。");
            return;
        }
        long remindTime = reminderService.parseEditTime(args[3]);
        if (remindTime <= 0) {
            sender.sendMessage("没有识别出新的提醒时间，可以试试“下午三点”或“明天 08:00”。");
            return;
        }
        String task = joinArgs(args,4);
        boolean changed = reminderService.update(id,groupID,userID,remindTime,task);
        sender.sendMessage(changed
                ? "提醒 #"+id+" 已修改为："+reminderService.formatTime(remindTime)+" "+task
                : "修改失败，请检查 ID 是否属于你、时间是否已经过去。");
    }

    private void delete(CommandSender sender,long groupID,long userID,String[] args) {
        if (args.length < 3) {
            sender.sendMessage("用法：/reminder delete <ID>");
            return;
        }
        long id = parseID(args[2]);
        if (id <= 0) {
            sender.sendMessage("提醒 ID 格式不正确。");
            return;
        }
        sender.sendMessage(reminderService.cancel(id,groupID,userID)
                ? "提醒 #"+id+" 已取消。"
                : "没有找到属于你的待触发提醒。");
    }

    private long parseID(String text) {
        try {
            return Long.parseLong(text);
        } catch (Exception e) {
            return -1L;
        }
    }

    private String joinArgs(String[] args,int start) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (builder.length() > 0) builder.append(" ");
            builder.append(args[i]);
        }
        return builder.toString().trim();
    }

    private String targetText(String target) {
        return "self".equalsIgnoreCase(target) ? "角色自提醒" : "提醒本人";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
