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
        if (RoleplayToolProtocolGuard.containsMarker(raw)) {
            draft.malformed = true;
            return draft;
        }

        JSONObject json = tryParse(raw);
        boolean repaired = false;
        String repairedRaw = raw;
        if (json == null) {
            repairedRaw = repairJsonLike(raw);
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

        // 仍然解析不出来：手工取出 text，并尽量恢复 actions。
        // 之前这里只取正文、丢掉 actions，会导致路由已经选中的技能（比如 draw）被静默丢弃，
        // 用户只看到半句被截断的回复。
        String lenient = lenientText(repairedRaw);
        if (lenient != null) {
            draft.repaired = true;
            draft.text = lenient.trim();
            draft.quote = repairedRaw.contains("\"quote\":true") || repairedRaw.contains("\"quote\": true");
            lenientActions(repairedRaw,draft);
            if (repairedRaw.contains("poke_back")) draft.addCall(new RoleplaySkillCall("poke_back"));
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
        value = escapeTextValueQuotes(value);
        return value;
    }

    /**
     * 中文输入法可能把 JSON 的引号、冒号和分隔逗号打成全角。
     * 只有真正当分隔符用的全角引号才转成半角；出现在正文里的全角引号转义保留，
     * 否则会把正文从中间截断（例如“莉音会长也来？“妈妈你这旗袍…””）。
     */
    private static String normalizeFullwidthJson(String raw) {
        String value = raw == null ? "" : raw;
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
                // 字符串里出现、后面又不接结构符号的引号当成正文内容转义，
                // 否则会把正文从中间截断，还会让后面的全角逗号被误判成 JSON 分隔符
                if (inString && !isStructuralFollower(value,i + 1,true)) {
                    builder.append("\\\"");
                    continue;
                }
                inString = !inString;
                builder.append(current);
                continue;
            }
            if (current == '“' || current == '”') {
                if (!inString) {
                    builder.append('"');
                    inString = true;
                    continue;
                }
                if (isStructuralFollower(value,i + 1,true)) {
                    builder.append('"');
                    inString = false;
                } else {
                    builder.append("\\\"");
                }
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
     * 修复正文里没转义的引号：定位 text 字段的值，把内部引号转义后再交给 JSON 解析。
     * 只有后面跟着结构符号的引号才算真正的结束引号，其余都当成正文内容。
     */
    private static String escapeTextValueQuotes(String raw) {
        String value = raw == null ? "" : raw;
        int key = value.indexOf("\"text\"");
        int keyLength = 6;
        if (key < 0) {
            key = value.indexOf("text");
            keyLength = 4;
        }
        if (key < 0) return value;
        int colon = value.indexOf(':',key + keyLength);
        if (colon < 0) return value;
        int start = colon + 1;
        while (start < value.length() && Character.isWhitespace(value.charAt(start))) start++;
        if (start >= value.length() || value.charAt(start) != '"') return value;

        int end = -1;
        boolean escaped = false;
        for (int i = start + 1; i < value.length(); i++) {
            char current = value.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (current == '"' && isStructuralFollower(value,i + 1,false)) {
                end = i;
                break;
            }
        }
        if (end < 0) return value;

        StringBuilder content = new StringBuilder(end - start);
        boolean contentEscaped = false;
        for (int i = start + 1; i < end; i++) {
            char current = value.charAt(i);
            if (contentEscaped) {
                content.append('\\').append(current);
                contentEscaped = false;
                continue;
            }
            if (current == '\\') {
                contentEscaped = true;
                continue;
            }
            if (current == '"') {
                content.append("\\\"");
                continue;
            }
            content.append(current);
        }
        if (contentEscaped) content.append('\\');
        return value.substring(0,start + 1) + content + value.substring(end);
    }

    /**
     * 判断某个位置后面的第一个非空白字符是不是 JSON 结构符号。
     * allowColon 为 true 时把冒号也算进去，用于识别全角引号包住的对象键。
     */
    private static boolean isStructuralFollower(String value,int from,boolean allowColon) {
        int index = nextNonSpace(value,from);
        if (index < 0) return true;
        char next = value.charAt(index);
        if (next == ',' || next == '，' || next == '}' || next == ']') return true;
        return allowColon && (next == ':' || next == '：');
    }

    private static int nextNonSpace(String value,int from) {
        if (value == null) return -1;
        for (int i = Math.max(0,from); i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i))) return i;
        }
        return -1;
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
            if (current == '"') {
                // 只有后面跟着结构符号的引号才是结束引号，正文里的引号原样保留
                if (isStructuralFollower(raw,i + 1,false)) break;
                builder.append(current);
                continue;
            }
            builder.append(current);
        }
        return builder.toString();
    }

    /**
     * 宽松恢复 actions：定位 actions 数组，按大括号配对切出每个动作对象再解析。
     * 这样即使正文里有没转义的引号，draw 这类动作也不会被静默丢掉。
     */
    private static void lenientActions(String raw,RoleplayReplyDraft draft) {
        if (raw == null || draft == null) return;
        int key = raw.indexOf("\"actions\"");
        int keyLength = 9;
        if (key < 0) {
            key = raw.indexOf("actions");
            keyLength = 7;
        }
        if (key < 0) return;
        int start = raw.indexOf('[',key + keyLength);
        if (start < 0) return;
        int end = matching(raw,start,'[',']');
        if (end < 0) end = raw.length();
        int index = start + 1;
        while (index < end) {
            int objectStart = raw.indexOf('{',index);
            if (objectStart < 0 || objectStart >= end) break;
            int objectEnd = matching(raw,objectStart,'{','}');
            if (objectEnd < 0 || objectEnd > end) break;
            JSONObject json = tryParse(raw.substring(objectStart,objectEnd + 1));
            if (json != null) {
                RoleplaySkillCall call = parseCall(json);
                if (call != null) draft.addCall(call);
            }
            index = objectEnd + 1;
        }
    }

    /**
     * 找配对的右括号，字符串内部的括号不计入深度
     */
    private static int matching(String value,int start,char open,char close) {
        if (value == null || start < 0 || start >= value.length()) return -1;
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < value.length(); i++) {
            char current = value.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (current == '"') {
                inString = !inString;
                continue;
            }
            if (inString) continue;
            if (current == open) depth++;
            else if (current == close) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }
}
