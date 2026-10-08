package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 执行层输出
 *
 * 约定模型返回 {"text":"正文","actions":[{"type":"poke_back","args":{}}],"quote":true}。
 * 动作和正文是同一次表达，所以放在同一个结果里，而不是先决定动作再让模型配台词。
 *
 * 解析分三级：严格 JSON、截取最外层大括号、宽松字段提取。
 * 三级都失败且内容看起来就是结构化结果时，宁可这一轮不发，也不把 JSON 原文发到群里。
 */
public class RoleplayReplyDraft {
    /**
     * 合批回合里的一段回复
     * to 是批内消息序号，1 起算，0 表示不指定；每段可以各自引用不同的消息
     */
    public static class Segment {
        public String text = "";
        public boolean quote = false;
        public int to = 0;
        public final List<RoleplaySkillCall> actions = new ArrayList<>();
    }

    public String text = "";
    public boolean quote = false;
    public final List<RoleplaySkillCall> actions = new ArrayList<>();
    public final List<Segment> segments = new ArrayList<>();
    /** 是否成功按约定解析出结构，false 表示走了退化路径 */
    public boolean structured = false;
    /** 是否通过容错修复或宽松提取恢复，调用方可以据此输出诊断日志 */
    public boolean repaired = false;
    /** 看起来是结构化结果但没能解析，调用方应记录日志并跳过发送 */
    public boolean malformed = false;

    public static RoleplayReplyDraft parse(String content) {
        RoleplayReplyDraft draft = new RoleplayReplyDraft();
        String raw = content == null ? "" : content.trim();
        if (raw.isEmpty()) return draft;

        JSONObject json = tryParse(raw);
        boolean repaired = false;
        if (json == null) {
            String repairedRaw = repairJsonLike(raw);
            if (!repairedRaw.equals(raw)) {
                json = tryParse(repairedRaw);
                repaired = json != null;
            }
        }
        if (json != null && (json.getString("text") != null
                || json.getJSONArray("segments") != null)) {
            draft.structured = true;
            draft.repaired = repaired;
            draft.text = json.getString("text") == null ? "" : json.getString("text").trim();
            draft.quote = json.getBooleanValue("quote");
            JSONArray actions = json.getJSONArray("actions");
            if (actions != null) {
                for (Object item : actions) {
                    RoleplaySkillCall call = parseCall(item);
                    if (call != null) draft.addCall(call);
                }
            }
            JSONArray segments = json.getJSONArray("segments");
            if (segments != null) {
                for (Object item : segments) {
                    Segment segment = parseSegment(item);
                    if (segment != null) draft.segments.add(segment);
                }
            }
            if (draft.text.isEmpty() && !draft.segments.isEmpty()) {
                draft.text = draft.segments.get(0).text;
            }
            return draft;
        }

        // 严格解析失败：模型可能漏了转义或夹了说明，做一次宽松提取，避免把 JSON 原文发出去
        String lenient = lenientText(repairJsonLike(raw));
        if (lenient != null) {
            draft.repaired = true;
            draft.text = lenient.trim();
            draft.quote = raw.contains("\"quote\":true") || raw.contains("\"quote\": true");
            if (raw.contains("poke_back")) draft.addCall(new RoleplaySkillCall("poke_back"));
            return draft;
        }

        if (looksLikeStructured(raw)) {
            draft.malformed = true;
            return draft;
        }

        draft.text = raw;
        return draft;
    }

    /** 同类动作只保留一次，避免模型重复输出 */
    public void addCall(RoleplaySkillCall call) {
        if (call == null || call.type.isEmpty()) return;
        for (RoleplaySkillCall existing : actions) {
            if (existing.type.equals(call.type)) return;
        }
        actions.add(call);
    }

    public boolean hasAction(String type) {
        return action(type) != null;
    }

    /**
     * 统一按“段”处理：单段结果包装成一段，合批结果直接返回多段
     */
    public List<Segment> effectiveSegments() {
        if (!segments.isEmpty()) return segments;
        List<Segment> result = new ArrayList<>();
        Segment segment = new Segment();
        segment.text = text;
        segment.quote = quote;
        segment.actions.addAll(actions);
        result.add(segment);
        return result;
    }

    public RoleplaySkillCall action(String type) {
        if (type == null) return null;
        for (RoleplaySkillCall call : actions) {
            if (type.equals(call.type)) return call;
        }
        return null;
    }

    private static RoleplaySkillCall parseCall(Object item) {
        if (item instanceof String) {
            return new RoleplaySkillCall(normalizeActionType(String.valueOf(item)));
        }
        if (!(item instanceof JSONObject)) return null;
        JSONObject json = (JSONObject) item;
        RoleplaySkillCall call = new RoleplaySkillCall(normalizeActionType(json.getString("type")));
        JSONObject args = json.getJSONObject("args");
        if (args != null) call.args = args;
        return call;
    }

    private static Segment parseSegment(Object item) {
        if (!(item instanceof JSONObject)) {
            if (item == null) return null;
            String value = String.valueOf(item).trim();
            if (value.isEmpty()) return null;
            Segment segment = new Segment();
            segment.text = value;
            return segment;
        }
        JSONObject json = (JSONObject) item;
        Segment segment = new Segment();
        String text = json.getString("text");
        segment.text = text == null ? "" : text.trim();
        if (segment.text.isEmpty()) return null;
        segment.quote = json.getBooleanValue("quote");
        segment.to = json.getIntValue("to");
        JSONArray actions = json.getJSONArray("actions");
        if (actions != null) {
            for (Object value : actions) {
                RoleplaySkillCall call = parseCall(value);
                if (call == null || call.type.isEmpty()) continue;
                boolean exists = false;
                for (RoleplaySkillCall existing : segment.actions) {
                    if (existing.type.equals(call.type)) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) segment.actions.add(call);
            }
        }
        return segment;
    }

    private static String normalizeActionType(String type) {
        if (type == null) return "";
        String value = type.trim().toLowerCase(Locale.ROOT);
        if ("poke".equals(value) || "poke_back".equals(value) || "poke-back".equals(value)
                || "戳回去".equals(value)) {
            return "poke-back";
        }
        return value;
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
     * 修复常见的结构化键名损坏，例如模型输出 {:text":"正文"}。
     */
    private static String repairJsonLike(String raw) {
        String value = raw == null ? "" : raw;
        value = value.replace("{:text\"","{\"text\"");
        value = value.replace("{:text","{\"text\"");
        value = value.replace(":text\"","\"text\"");
        value = value.replace(":text","\"text\"");
        value = normalizeFullwidthJson(value);
        return value;
    }

    /**
     * 中文输入法可能把 JSON 的引号、冒号和分隔逗号打成全角。
     * 引号统一转换，字符串内部的冒号和逗号保持原样。
     */
    private static String normalizeFullwidthJson(String raw) {
        String value = raw.replace('“','"').replace('”','"');
        StringBuilder builder = new StringBuilder(value.length());
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (escaped) {
                builder.append(current);
                escaped = false;
                continue;
            }
            if (current == '\\') {
                builder.append(current);
                escaped = true;
                continue;
            }
            if (current == '"') {
                inString = !inString;
                builder.append(current);
                continue;
            }
            if (!inString && current == '：') {
                builder.append(':');
            } else if (!inString && current == '，') {
                builder.append(',');
            } else {
                builder.append(current);
            }
        }
        return builder.toString();
    }

    /**
     * 判断内容是否明显属于结构化输出。损坏的 JSON 也不能作为普通聊天正文发送。
     */
    private static boolean looksLikeStructured(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (!value.startsWith("{")) return false;
        return value.contains("text") || value.contains("segments") || value.contains("actions");
    }

    /**
     * 从疑似结构化的文本里手工取出 text 字段
     * 只在严格解析失败时使用，取不到返回 null
     */
    private static String lenientText(String raw) {
        int key = raw.indexOf("\"text\"");
        int keyLength = 6;
        if (key < 0) {
            key = raw.indexOf("text");
            keyLength = 4;
        }
        if (key < 0) return null;
        int colon = raw.indexOf(':',key + keyLength);
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
