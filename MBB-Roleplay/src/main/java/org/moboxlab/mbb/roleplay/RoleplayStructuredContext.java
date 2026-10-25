package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 当前会话的可接续结构化状态。
 *
 * 固定五段，避免模型用自由文本重写摘要后丢失“正在进行什么”和“还有什么没做完”。
 */
public class RoleplayStructuredContext {
    private static final Map<String,String> LABELS = new LinkedHashMap<>();
    private static final Map<String,Integer> LIMITS = new LinkedHashMap<>();

    static {
        LABELS.put("topics","当前话题");
        LABELS.put("background","关键背景");
        LABELS.put("progress","进展与结论");
        LABELS.put("pending","未完事项");
        LABELS.put("agreements","对话约定");

        LIMITS.put("topics",8);
        LIMITS.put("background",10);
        LIMITS.put("progress",12);
        LIMITS.put("pending",10);
        LIMITS.put("agreements",10);
    }

    private final Map<String,List<String>> sections = new LinkedHashMap<>();

    private RoleplayStructuredContext() {
        for (String key : LABELS.keySet()) sections.put(key,new ArrayList<>());
    }

    public static RoleplayStructuredContext parse(JSONObject json,int itemMaxChars) {
        RoleplayStructuredContext result = new RoleplayStructuredContext();
        if (json == null) return result;
        int itemLimit = Math.max(40,Math.min(500,itemMaxChars));
        for (Map.Entry<String,String> entry : LABELS.entrySet()) {
            result.sections.put(entry.getKey(),cleanValues(
                    json.get(entry.getKey()),LIMITS.get(entry.getKey()),itemLimit));
        }
        return result;
    }

    public static RoleplayStructuredContext fromJson(String raw,int itemMaxChars) {
        if (raw == null || raw.trim().isEmpty()) return new RoleplayStructuredContext();
        try {
            return parse(JSONObject.parseObject(raw),itemMaxChars);
        } catch (Exception e) {
            return new RoleplayStructuredContext();
        }
    }

    /**
     * 旧模型或旧备份没有 context 字段时，至少把短期摘要保留为背景，不丢已有状态。
     */
    public static RoleplayStructuredContext fallback(String shortTerm,int itemMaxChars) {
        RoleplayStructuredContext result = new RoleplayStructuredContext();
        String text = shortTerm == null ? "" : shortTerm.trim();
        if (text.isEmpty()) return result;
        JSONObject json = new JSONObject(true);
        JSONArray background = new JSONArray();
        String[] parts = text.split("[\\r\\n]+|(?<=[。！？!?；;])");
        for (String part : parts) {
            if (part != null && !part.trim().isEmpty()) background.add(part.trim());
        }
        if (background.isEmpty()) background.add(text);
        json.put("background",background);
        return parse(json,itemMaxChars);
    }

    public boolean isEmpty() {
        for (List<String> values : sections.values()) {
            if (!values.isEmpty()) return false;
        }
        return true;
    }

    public JSONObject toJson() {
        JSONObject result = new JSONObject(true);
        for (Map.Entry<String,List<String>> entry : sections.entrySet()) {
            JSONArray values = new JSONArray();
            values.addAll(entry.getValue());
            result.put(entry.getKey(),values);
        }
        return result;
    }

    public String render(int maxChars) {
        int limit = Math.max(160,maxChars);
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String,String> entry : LABELS.entrySet()) {
            List<String> values = sections.get(entry.getKey());
            if (values == null || values.isEmpty()) continue;
            StringBuilder block = new StringBuilder();
            block.append("【").append(entry.getValue()).append("】\n");
            boolean hasLine = false;
            for (String value : values) {
                String line = "- "+value+"\n";
                if (result.length() + block.length() + line.length() > limit) break;
                block.append(line);
                hasLine = true;
            }
            if (!hasLine) continue;
            int separator = result.length() > 0 ? 1 : 0;
            if (result.length() + separator + block.length() > limit) break;
            if (separator > 0) result.append("\n");
            result.append(block);
        }
        return result.toString().trim();
    }

    private static List<String> cleanValues(Object raw,int maxItems,int itemMaxChars) {
        List<String> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        List<Object> values = new ArrayList<>();
        if (raw instanceof JSONArray) {
            values.addAll((JSONArray)raw);
        } else if (raw instanceof Iterable) {
            for (Object item : (Iterable<?>)raw) values.add(item);
        } else if (raw != null) {
            values.add(raw);
        }
        for (Object value : values) {
            if (value == null) continue;
            String text = String.valueOf(value).replace("\r"," ").replace("\n"," ")
                    .replaceAll("\\s+"," ").trim();
            while (text.startsWith("- ") || text.startsWith("· ")) {
                text = text.substring(2).trim();
            }
            if (text.isEmpty()) continue;
            if (text.length() > itemMaxChars) text = text.substring(0,itemMaxChars).trim();
            if (seen.add(text)) result.add(text);
            if (result.size() >= maxItems) break;
        }
        return result;
    }
}
