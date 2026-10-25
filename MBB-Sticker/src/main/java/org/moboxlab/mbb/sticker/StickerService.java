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
    private final StickerReceiveService receiveService;
    private final Map<String,Deque<String>> recentMap = new ConcurrentHashMap<>();

    public StickerService(StickerPlugin plugin,StickerLibrary library,
                          StickerReceiveService receiveService) {
        this.plugin = plugin;
        this.library = library;
        this.receiveService = receiveService;
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
        if ("candidates".equalsIgnoreCase(action)) return candidates(params);
        if ("pick".equalsIgnoreCase(action)) return pick(params);
        if ("add".equalsIgnoreCase(action)) return add(params);
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

    private JSONObject add(JSONObject params) {
        if (params == null) return error("缺少图片参数");
        JSONObject data = new JSONObject(true);
        data.put("file",safe(params.getString("file")));
        data.put("file_unique",safe(params.getString("fileUnique")));
        data.put("file_id",safe(params.getString("fileId")));
        data.put("url",safe(params.getString("url")));
        List<String> tags = readTags(params.get("tags"));
        return receiveService.importImage(data,safe(params.getString("source")),
                tags,safe(params.getString("description")));
    }

    private JSONObject random(JSONObject params) {
        List<String> tags = readTags(params == null ? null : params.get("tags"));
        String key = params == null ? "" : safe(params.getString("groupID"))+"|"+safe(params.getString("userID"));
        Set<String> avoid = avoid(key);
        StickerEntry entry = library.random(tags,avoid);
        if (entry == null) return error("没有可用的表情包");
        library.markUsed(entry.id);
        rememberRecent(key,entry.id);
        JSONObject result = new JSONObject(true);
        result.put("status",true);
        result.put("id",entry.id);
        return entryResult(result,entry);
    }

    private JSONObject candidates(JSONObject params) {
        List<String> tags = readTags(params == null ? null : params.get("tags"));
        String key = params == null ? "" : safe(params.getString("groupID"))+"|"+safe(params.getString("userID"));
        int limit = params == null ? 6 : params.getIntValue("limit");
        if (limit < 1) limit = 1;
        if (limit > 12) limit = 12;
        List<StickerEntry> entries = library.candidates(tags,avoid(key),limit);
        JSONArray array = new JSONArray();
        for (StickerEntry entry : entries) {
            JSONObject item = new JSONObject(true);
            item.put("id",entry.id);
            item.put("file",library.toUri(entry.file));
            JSONArray tagArray = new JSONArray();
            tagArray.addAll(entry.tags);
            item.put("tags",tagArray);
            item.put("description",entry.description);
            item.put("useCount",entry.useCount);
            array.add(item);
        }
        JSONObject result = new JSONObject(true);
        result.put("status",true);
        result.put("candidates",array);
        result.put("count",array.size());
        return result;
    }

    private JSONObject pick(JSONObject params) {
        if (params == null) return error("缺少表情包 ID");
        String id = safe(params.getString("id"));
        StickerEntry entry = library.get(id);
        if (entry == null || !entry.enabled) return error("没有找到表情包："+id);
        library.markUsed(entry.id);
        String key = safe(params.getString("groupID"))+"|"+safe(params.getString("userID"));
        rememberRecent(key,entry.id);
        JSONObject result = new JSONObject(true);
        result.put("status",true);
        result.put("id",entry.id);
        return entryResult(result,entry);
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

    private JSONObject entryResult(JSONObject result,StickerEntry entry) {
        //对外始终给可用的绝对 file:// 地址，存盘用相对路径
        result.put("file",library.toUri(entry.file));
        JSONArray tagArray = new JSONArray();
        tagArray.addAll(entry.tags);
        result.put("tags",tagArray);
        result.put("description",entry.description);
        return result;
    }

    private Set<String> avoid(String key) {
        Set<String> result = new TreeSet<>();
        Deque<String> recent = recentMap.get(key);
        if (recent != null) {
            synchronized (recent) {
                result.addAll(recent);
            }
        }
        return result;
    }

    private void rememberRecent(String key,String id) {
        if (key.equals("|") || id == null || id.isEmpty()) return;
        Deque<String> queue = recentMap.get(key);
        if (queue == null) {
            Deque<String> newQueue = new ArrayDeque<>();
            Deque<String> existing = recentMap.putIfAbsent(key,newQueue);
            queue = existing == null ? newQueue : existing;
        }
        synchronized (queue) {
            queue.addLast(id);
            int limit = Math.max(1,plugin.getConfig().getInt("recentAvoidCount",20));
            while (queue.size() > limit) queue.removeFirst();
        }
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
