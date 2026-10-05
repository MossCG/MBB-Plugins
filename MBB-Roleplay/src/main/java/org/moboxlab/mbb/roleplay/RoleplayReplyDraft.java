package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 执行层输出
 *
 * 约定模型返回 {"text":"正文","actions":[{"type":"poke_back"}],"quote":true}。
 * 动作和正文是同一次表达，所以放在同一个结果里，而不是先决定动作再让模型配台词。
 *
 * 解析分三级：严格 JSON、截取最外层大括号、宽松字段提取。
 * 三级都失败且内容看起来就是结构化结果时，宁可这一轮不发，也不把 JSON 原文发到群里。
 */
public class RoleplayReplyDraft {
    public String text = "";
    public boolean quote = false;
    public final List<String> actions = new ArrayList<>();
    /** 是否成功按约定解析出结构，false 表示走了退化路径 */
    public boolean structured = false;
    /** 看起来是结构化结果但没能解析，调用方应记录日志并跳过发送 */
    public boolean malformed = false;

    public static RoleplayReplyDraft parse(String content) {
        RoleplayReplyDraft draft = new RoleplayReplyDraft();
        String raw = content == null ? "" : content.trim();
        if (raw.isEmpty()) return draft;

        JSONObject json = tryParse(raw);
        if (json != null && json.getString("text") != null) {
            draft.structured = true;
            draft.text = json.getString("text").trim();
            draft.quote = json.getBooleanValue("quote");
            JSONArray actions = json.getJSONArray("actions");
            if (actions != null) {
                for (Object item : actions) {
                    String type = actionType(item);
                    if (type == null || type.trim().isEmpty()) continue;
                    if (!draft.actions.contains(type.trim())) draft.actions.add(type.trim());
                }
            }
            return draft;
        }

        // 严格解析失败：模型可能漏了转义或夹了说明，做一次宽松提取，避免把 JSON 原文发出去
        String lenient = lenientText(raw);
        if (lenient != null) {
            draft.text = lenient.trim();
            draft.quote = raw.contains("\"quote\":true") || raw.contains("\"quote\": true");
            if (raw.contains("poke_back")) draft.actions.add("poke_back");
            return draft;
        }

        if (raw.startsWith("{") && raw.contains("\"text\"")) {
            draft.malformed = true;
            return draft;
        }

        draft.text = raw;
        return draft;
    }

    private static String actionType(Object item) {
        if (item instanceof String) return String.valueOf(item);
        if (item instanceof JSONObject) return ((JSONObject) item).getString("type");
        return null;
    }

    private static JSONObject tryParse(String raw) {
        try {
            return JSON.parseObject(raw);
        } catch (Exception ignored) {
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        try {
            return JSON.parseObject(raw.substring(start,end + 1));
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 从疑似结构化的文本里手工取出 text 字段
     * 只在严格解析失败时使用，取不到返回 null
     */
    private static String lenientText(String raw) {
        int key = raw.indexOf("\"text\"");
        if (key < 0) return null;
        int colon = raw.indexOf(':',key + 6);
        if (colon < 0) return null;
        int start = colon + 1;
        while (start < raw.length() && Character.isWhitespace(raw.charAt(start))) start++;
        if (start >= raw.length() || raw.charAt(start) != '"') return null;
        StringBuilder builder = new StringBuilder();
        boolean escaped = false;
        for (int i = start + 1; i < raw.length(); i++) {
            char current = raw.charAt(i);
            if (escaped) {
                if (current == 'n') builder.append('\n');
                else if (current == 'r') builder.append('\r');
                else if (current == 't') builder.append('\t');
                else builder.append(current);
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (current == '"') break;
            builder.append(current);
        }
        return builder.toString();
    }
}
