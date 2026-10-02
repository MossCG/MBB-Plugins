package org.moboxlab.mbb.chat;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Command.BotCommand;
import org.moboxlab.moboxbot.API.Command.CommandPermission;
import org.moboxlab.moboxbot.API.Command.CommandSender;
import org.moboxlab.moboxbot.API.PluginService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * /chat AI 对话命令
 */
public class ChatCommand extends BotCommand {
    private static final String WHITELIST_KEY = "chat-whitelist";
    private static final int MAX_PERSONA_LENGTH = 2000;
    private final ChatPlugin plugin;

    public ChatCommand(ChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> prefix() {
        List<String> prefixList = new ArrayList<>();
        prefixList.add("chat");
        return prefixList;
    }

    @Override
    public CommandPermission permission() {
        return CommandPermission.EVERYONE;
    }

    @Override
    public int cooldownSeconds() {
        return 3;
    }

    @Override
    public String description() {
        return "与 AI 对话，每人独立上下文";
    }

    @Override
    public List<String> usage() {
        return Arrays.asList(
                "/chat <内容>",
                "/chat new",
                "/chat status",
                "/chat persona [set <内容>|reset]",
                "/chat whitelist [list|add <QQ>|remove <QQ>]");
    }

    @Override
    public boolean execute(CommandSender sender,String[] args) {
        if (args.length <= 1) {
            if (!canUse(sender)) {
                sendNoPermission(sender);
                return true;
            }
            sender.sendMessage("用法：/chat <内容>，/chat new 清空上下文，/chat status 查看上下文。");
            return true;
        }
        String action = args[1].toLowerCase();
        if ("whitelist".equals(action)) {
            handleWhitelist(sender,args);
            return true;
        }
        if (!canUse(sender)) {
            sendNoPermission(sender);
            return true;
        }
        if ("persona".equals(action) || "prompt".equals(action)) {
            handlePersona(sender,args);
            return true;
        }
        if ("new".equals(action) || "reset".equals(action)) {
            plugin.getServer().getStorage().remove(plugin,contextKey(sender.getUserID()));
            sender.sendMessage("已清空你的 AI 对话上下文！");
            return true;
        }
        if ("status".equals(action)) {
            JSONArray context = loadContext(sender.getUserID());
            sender.sendMessage("你的 AI 上下文消息数："+context.size()
                    +"，模型配置："+plugin.getConfig().getString("profile","default"));
            return true;
        }
        String content = joinArgs(args,1);
        if (content.isEmpty()) {
            sender.sendMessage("聊天内容不能为空！");
            return true;
        }
        plugin.getServer().getPluginManager().runTask(plugin,() -> handleChat(sender,content));
        return true;
    }

    private void handleWhitelist(CommandSender sender,String[] args) {
        if (!sender.hasPermission(CommandPermission.BOT_ADMIN)) {
            sender.sendMessage("只有机器人管理员及以上可以管理 AI 对话白名单！");
            return;
        }
        String action = args.length > 2 ? args[2].toLowerCase() : "list";
        Set<Long> whitelist = loadWhitelist();
        if ("list".equals(action)) {
            if (whitelist.isEmpty()) {
                sender.sendMessage("AI 对话白名单为空。");
                return;
            }
            sender.sendMessage("AI 对话白名单："+joinLongs(whitelist));
            return;
        }
        if (!"add".equals(action) && !"remove".equals(action)) {
            sender.sendMessage("用法：/chat whitelist [list] | add <QQ> | remove <QQ>");
            return;
        }
        if (args.length < 4) {
            sender.sendMessage("用法：/chat whitelist "+action+" <QQ>");
            return;
        }
        List<Long> ids = parseIDs(args[3]);
        if (ids.isEmpty()) {
            sender.sendMessage("QQ 号格式不正确！");
            return;
        }
        int changed = 0;
        for (Long id : ids) {
            boolean result = "add".equals(action) ? whitelist.add(id) : whitelist.remove(id);
            if (result) changed++;
        }
        if (changed > 0) saveWhitelist(whitelist);
        sender.sendMessage("白名单"+(("add".equals(action)) ? "添加" : "移除")
                +"完成，变更 "+changed+" 个。");
    }

    private boolean canUse(CommandSender sender) {
        if (sender.hasPermission(CommandPermission.BOT_ADMIN)) return true;
        return loadWhitelist().contains(sender.getUserID());
    }

    private void sendNoPermission(CommandSender sender) {
        sender.sendMessage("你没有使用 AI 对话的权限，请联系机器人管理员添加白名单！");
    }

    private void handlePersona(CommandSender sender,String[] args) {
        String action = args.length > 2 ? args[2].toLowerCase() : "show";
        if ("show".equals(action) || "list".equals(action)) {
            String persona = loadPersona(sender.getUserID());
            sender.sendMessage("当前人设：\n"+persona);
            return;
        }
        if ("reset".equals(action) || "clear".equals(action)) {
            plugin.getServer().getStorage().remove(plugin,personaKey(sender.getUserID()));
            sender.sendMessage("已恢复默认鲸鱼女仆娘人设！");
            return;
        }
        if (!"set".equals(action) || args.length < 4) {
            sender.sendMessage("用法：/chat persona | /chat persona set <内容> | /chat persona reset");
            return;
        }
        String persona = joinArgs(args,3).trim();
        if (persona.isEmpty()) {
            sender.sendMessage("人设内容不能为空！");
            return;
        }
        if (persona.length() > MAX_PERSONA_LENGTH) {
            sender.sendMessage("人设内容不能超过 "+MAX_PERSONA_LENGTH+" 个字符！");
            return;
        }
        plugin.getServer().getStorage().set(plugin,personaKey(sender.getUserID()),persona);
        sender.sendMessage("已保存你的自定义人设！使用 /chat persona reset 可恢复默认。");
    }

    private void handleChat(CommandSender sender,String content) {
        PluginService ai = plugin.getServer().getPluginManager().getService("MBB-AI");
        if (ai == null) {
            sender.sendMessage("AI 服务不可用，请确认 MBB-AI 已启用！");
            return;
        }
        JSONArray context = loadContext(sender.getUserID());
        JSONArray messages = new JSONArray();
        messages.add(message("system",systemMessage(sender.getUserID())));
        for (int i = 0; i < context.size(); i++) {
            JSONObject item = context.getJSONObject(i);
            if (item != null) messages.add(item);
        }
        messages.add(message("user",content));

        JSONObject params = new JSONObject(true);
        params.put("profile",plugin.getConfig().getString("profile","default"));
        params.put("messages",messages);
        params.put("sessionId","moboxbot-user-"+sender.getUserID());
        JSONObject result = ai.call("chat",params);
        if (!result.getBooleanValue("status")) {
            sender.sendMessage("AI 对话失败："+safe(result.getString("message")));
            return;
        }
        String reply = safe(result.getString("content"));
        if (reply.isEmpty()) {
            sender.sendMessage("AI 返回了空内容。");
            return;
        }

        context.add(message("user",content));
        context.add(message("assistant",reply));
        trimContext(context);
        plugin.getServer().getStorage().set(plugin,contextKey(sender.getUserID()),context.toJSONString());
        sendLongMessage(sender,reply);
    }

    private JSONArray loadContext(long userID) {
        String value = plugin.getServer().getStorage().get(plugin,contextKey(userID));
        if (value == null || value.trim().isEmpty()) return new JSONArray();
        try {
            JSONArray context = JSONArray.parseArray(value);
            return context == null ? new JSONArray() : context;
        } catch (Exception e) {
            plugin.getLogger().sendWarn("读取用户上下文失败，已重置："+userID);
            return new JSONArray();
        }
    }

    private void trimContext(JSONArray context) {
        int max = plugin.getConfig().getInt("maxContextMessages",20);
        if (max < 2) max = 2;
        while (context.size() > max) context.remove(0);
        if (context.size() % 2 != 0) context.remove(0);
    }

    private void sendLongMessage(CommandSender sender,String content) {
        int chunkLength = plugin.getConfig().getInt("replyChunkLength",1000);
        if (chunkLength < 100) chunkLength = 100;
        if (content.length() <= chunkLength) {
            sender.sendMessage(content);
            return;
        }
        for (int i = 0; i < content.length(); i += chunkLength) {
            int end = Math.min(content.length(),i + chunkLength);
            sender.sendMessage(content.substring(i,end));
        }
    }

    private JSONObject message(String role,String content) {
        JSONObject message = new JSONObject(true);
        message.put("role",role);
        message.put("content",content == null ? "" : content);
        return message;
    }

    private String contextKey(long userID) {
        return "chat-context-"+userID;
    }

    private String personaKey(long userID) {
        return "chat-persona-"+userID;
    }

    private String loadPersona(long userID) {
        String value = plugin.getServer().getStorage().get(plugin,personaKey(userID));
        if (value == null || value.trim().isEmpty()) return plugin.getDefaultPersona();
        return value;
    }

    private String systemMessage(long userID) {
        return loadPersona(userID)
                +"\n【输出格式】只输出纯文本，禁止使用 Markdown 语法。"
                +"不要使用 **、__、#、>、代码块、表格、链接或多余星号。";
    }

    private Set<Long> loadWhitelist() {
        Set<Long> result = new LinkedHashSet<>();
        String value = plugin.getServer().getStorage().get(plugin,WHITELIST_KEY);
        if (value == null || value.trim().isEmpty()) return result;
        try {
            JSONArray array = JSONArray.parseArray(value);
            if (array == null) return result;
            for (Object item : array) {
                try {
                    long id = Long.parseLong(String.valueOf(item).trim());
                    if (id > 0) result.add(id);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            plugin.getLogger().sendWarn("读取 AI 对话白名单失败，已按空名单处理！");
        }
        return result;
    }

    private void saveWhitelist(Set<Long> whitelist) {
        JSONArray array = new JSONArray();
        for (Long id : whitelist) array.add(String.valueOf(id));
        plugin.getServer().getStorage().set(plugin,WHITELIST_KEY,array.toJSONString());
    }

    private List<Long> parseIDs(String text) {
        List<Long> result = new ArrayList<>();
        if (text == null) return result;
        for (String item : text.split(",")) {
            try {
                long id = Long.parseLong(item.trim());
                if (id > 0 && !result.contains(id)) result.add(id);
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private String joinLongs(Set<Long> values) {
        StringBuilder builder = new StringBuilder();
        for (Long value : values) {
            if (builder.length() > 0) builder.append(",");
            builder.append(value);
        }
        return builder.toString();
    }

    private String joinArgs(String[] args,int start) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (builder.length() > 0) builder.append(" ");
            builder.append(args[i]);
        }
        return builder.toString().trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
