package org.moboxlab.mbb.poke;

import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Event.EventHandler;
import org.moboxlab.moboxbot.API.Event.EventPriority;
import org.moboxlab.moboxbot.API.Event.Listener;
import org.moboxlab.moboxbot.API.Event.NoticeEvent;
import org.moboxlab.moboxbot.API.OneBot.MessageUtil;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 戳一戳事件监听
 */
public class PokeListener implements Listener {
    private final PokePlugin plugin;

    public PokeListener(PokePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onNotice(NoticeEvent event) {
        if (!"notify".equals(event.getNoticeType()) && !"poke".equals(event.getNoticeType())) return;
        if (!"poke".equals(event.getSubType())) return;
        JSONObject raw = event.getRaw();
        long selfID = raw.getLongValue("self_id");
        if (selfID <= 0 || event.getTargetID() != selfID) return;

        final long userID = event.getUserID();
        final long groupID = event.getGroupID();
        if (userID <= 0) return;
        if (userID == selfID) return;

        List<List<Map<String,Object>>> replies = loadReplies();
        if (replies.isEmpty()) replies = fallbackReplies();
        List<Map<String,Object>> reply = replies.get(ThreadLocalRandom.current().nextInt(replies.size()));
        executeReply(reply,groupID,userID);
    }

    private void executeReply(List<Map<String,Object>> reply,long groupID,long userID) {
        if (reply == null) return;
        for (Map<String,Object> action : reply) {
            if (action == null || action.isEmpty()) continue;
            if (action.containsKey("message")) {
                sendMessage(groupID,userID,String.valueOf(action.get("message")));
            }
            if (action.containsKey("image")) {
                sendImage(groupID,userID,String.valueOf(action.get("image")));
            }
            if (action.containsKey("sleep")) {
                sleep(toDouble(action.get("sleep"),0.0));
            }
            if (action.containsKey("poke_back_message")) {
                pokeBack(groupID,userID);
                sendMessage(groupID,userID,String.valueOf(action.get("poke_back_message")));
                continue;
            }
            if (action.containsKey("poke_backmessage")) {
                pokeBack(groupID,userID);
                sendMessage(groupID,userID,String.valueOf(action.get("poke_backmessage")));
                continue;
            }
            if (action.containsKey("poke_back")) {
                Object value = action.get("poke_back");
                pokeBack(groupID,userID);
                if (value instanceof String && !isFlag(((String) value))) {
                    sendMessage(groupID,userID,(String) value);
                }
            }
        }
    }

    private void pokeBack(long groupID,long userID) {
        JSONObject params = new JSONObject(true);
        params.put("user_id",String.valueOf(userID));
        if (groupID > 0) {
            params.put("group_id",String.valueOf(groupID));
            plugin.getServer().getOneBotClient().callAction("group_poke",params);
        } else {
            plugin.getServer().getOneBotClient().callAction("friend_poke",params);
        }
    }

    private void sendMessage(long groupID,long userID,String text) {
        if (text == null || text.isEmpty()) return;
        if (groupID > 0) {
            plugin.getServer().getOneBotClient().sendGroupMessage(groupID,MessageUtil.message(MessageUtil.text(text)));
        } else {
            plugin.getServer().getOneBotClient().sendPrivateMessage(userID,MessageUtil.message(MessageUtil.text(text)));
        }
    }

    private void sendImage(long groupID,long userID,String file) {
        if (file == null || file.isEmpty()) return;
        String image = resolveImage(file);
        if (image == null) return;
        if (groupID > 0) {
            plugin.getServer().getOneBotClient().sendGroupMessage(groupID,MessageUtil.message(MessageUtil.image(image)));
        } else {
            plugin.getServer().getOneBotClient().sendPrivateMessage(userID,MessageUtil.message(MessageUtil.image(image)));
        }
    }

    private String resolveImage(String file) {
        String lower = file.toLowerCase();
        if (lower.startsWith("base64://")
                || lower.startsWith("http://")
                || lower.startsWith("https://")
                || lower.startsWith("file://")) {
            return file;
        }
        File local = new File(file);
        if (!local.isAbsolute()) local = new File(plugin.getDataFolder(),file);
        if (!local.exists() || !local.isFile()) {
            plugin.getLogger().sendWarn("图片文件不存在："+local.getAbsolutePath());
            return null;
        }
        String path = local.getAbsolutePath().replace("\\","/");
        if (!path.startsWith("/")) path = "/"+path;
        return "file://"+path;
    }

    private void sleep(double seconds) {
        if (seconds <= 0) return;
        double max = plugin.getConfig().getInt("maxSleepSecond",5);
        if (max <= 0) max = 5;
        if (seconds > max) seconds = max;
        try {
            Thread.sleep((long)(seconds * 1000L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @SuppressWarnings("unchecked")
    private List<List<Map<String,Object>>> loadReplies() {
        List<List<Map<String,Object>>> result = new ArrayList<>();
        File file = new File(plugin.getDataFolder()+"/replies.yml");
        if (!file.exists()) return result;
        FileInputStream input = null;
        try {
            input = new FileInputStream(file);
            LoaderOptions options = new LoaderOptions();
            Yaml yaml = new Yaml(new SafeConstructor(options));
            Object data = yaml.load(input);
            if (!(data instanceof Map)) return result;
            Object replys = ((Map<String,Object>) data).get("replys");
            if (!(replys instanceof Map)) return result;
            Map<String,Object> replyMap = (Map<String,Object>) replys;
            for (Object value : replyMap.values()) {
                if (!(value instanceof List)) continue;
                List<Map<String,Object>> actions = new ArrayList<>();
                for (Object item : (List<Object>) value) {
                    if (item instanceof Map) actions.add((Map<String,Object>) item);
                }
                if (!actions.isEmpty()) result.add(actions);
            }
        } catch (Exception e) {
            plugin.getLogger().sendWarn("读取 replies.yml 失败，已使用内置默认回复："+e.getMessage());
        } finally {
            try {
                if (input != null) input.close();
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private List<List<Map<String,Object>>> fallbackReplies() {
        List<List<Map<String,Object>>> result = new ArrayList<>();
        String[] texts = new String[]{"喵？","哈！","哈你喵！","不许戳喵！","杂鱼喵！","呜喵！","哼！"};
        for (String text : texts) {
            List<Map<String,Object>> actions = new ArrayList<>();
            Map<String,Object> action = new LinkedHashMap<>();
            action.put("message",text);
            actions.add(action);
            result.add(actions);
        }
        return result;
    }

    private double toDouble(Object value,double defaultValue) {
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private boolean isFlag(String text) {
        String value = text == null ? "" : text.trim().toLowerCase();
        return value.isEmpty() || "true".equals(value) || "1".equals(value) || "yes".equals(value);
    }
}
