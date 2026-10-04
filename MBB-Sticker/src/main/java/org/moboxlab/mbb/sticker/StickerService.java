package org.moboxlab.mbb.sticker;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.PluginService;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 表情包公共服务
 */
public class StickerService implements PluginService {
    private final StickerPlugin plugin;
    private final StickerLibrary library;
    private final Map<String,Deque<String>> recentMap = new ConcurrentHashMap<>();

    public StickerService(StickerPlugin plugin,StickerLibrary library) {
        this.plugin = plugin;
        this.library = library;
    }

    @Override
    public String getName() {
        return "MBB-Sticker";
    }

    @Override
    public JSONObject call(String action,JSONObject params) {
        if (action == null) return error("缺少动作名");
        if ("tags".equalsIgnoreCase(action)) return tags();
        if ("random".equalsIgnoreCase(action)) return random(params);
        if ("stats".equalsIgnoreCase(action)) return stats();
        if ("reload".equalsIgnoreCase(action)) {
            library.load();
            return stats();
        }
        return error("不支持的动作："+action);
    }

    private JSONObject tags() {
        JSONObject result = new JSONObject(true);
        result.put("status",true);
        JSONArray array = new JSONArray();
        array.addAll(new TreeSet<>(library.availableTags()));
        result.put("tags",array);
        result.put("count",library.size());
        return result;
    }

    private JSONObject random(JSONObject params) {
        List<String> tags = readTags(params == null ? null : params.get("tags"));
        String key = params == null ? "" : safe(params.getString("groupID"))+"|"+safe(params.getString("userID"));
        Set<String> avoid = new TreeSet<>();
        Deque<String> recent = recentMap.get(key);
        if (recent != null) {
            synchronized (recent) {
                avoid.addAll(recent);
            }
        }
        StickerEntry entry = library.random(tags,avoid);
        if (entry == null) return error("没有可用的表情包");
        if (!key.equals("|")) {
            Deque<String> queue = recentMap.get(key);
            if (queue == null) {
                Deque<String> newQueue = new ArrayDeque<>();
                Deque<String> existing = recentMap.putIfAbsent(key,newQueue);
                queue = existing == null ? newQueue : existing;
            }
            synchronized (queue) {
                queue.addLast(entry.id);
                int limit = Math.max(1,plugin.getConfig().getInt("recentAvoidCount",20));
                while (queue.size() > limit) queue.removeFirst();
            }
        }
        JSONObject result = new JSONObject(true);
        result.put("status",true);
        result.put("id",entry.id);
        result.put("file",entry.file);
        JSONArray tagArray = new JSONArray();
        tagArray.addAll(entry.tags);
        result.put("tags",tagArray);
        result.put("description",entry.description);
        return result;
    }

    private JSONObject stats() {
        JSONObject result = new JSONObject(true);
        result.put("status",true);
        result.put("count",library.size());
        JSONArray tags = new JSONArray();
        tags.addAll(library.availableTags());
        result.put("tags",tags);
        return result;
    }

    private List<String> readTags(Object value) {
        List<String> result = new ArrayList<>();
        if (value == null) return result;
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray)value;
            for (Object item : array) {
                if (item != null) result.add(String.valueOf(item));
            }
            return result;
        }
        for (String item : String.valueOf(value).split("[,，]")) {
            if (!item.trim().isEmpty()) result.add(item.trim());
        }
        return result;
    }

    private JSONObject error(String message) {
        JSONObject result = new JSONObject(true);
        result.put("status",false);
        result.put("message",message);
        return result;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
