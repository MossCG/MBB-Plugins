package org.moboxlab.mbb.ai;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 兼容部分 OpenAI 网关把工具调用写成正文标签的情况。
 *
 * 典型格式为 DSML invoke/parameter 标签。这里只解析明确的协议标签，
 * 不猜测普通聊天正文，解析结果仍转换为标准 tools/tool_calls 结构。
 */
public class ToolCallTextParser {
    private static final Pattern INVOKE_OPEN = Pattern.compile(
            "(?is)<\\s*(?!/|!)(?:\\|\\s*DSML\\s*\\|\\s*)?invoke\\b([^>]*)>");
    private static final Pattern INVOKE_CLOSE = Pattern.compile(
            "(?is)<\\s*/\\s*(?:\\|\\s*DSML\\s*\\|\\s*)?invoke\\s*>");
    private static final Pattern PARAMETER_OPEN = Pattern.compile(
            "(?is)<\\s*(?!/|!)(?:\\|\\s*DSML\\s*\\|\\s*)?parameter\\b([^>]*)>");
    private static final Pattern PARAMETER_CLOSE = Pattern.compile(
            "(?is)<\\s*/\\s*(?:\\|\\s*DSML\\s*\\|\\s*)?parameter\\s*>");
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "(?is)\\b([a-z][a-z0-9_-]*)\\s*=\\s*([\"'])(.*?)\\2");
    private static final Pattern PROTOCOL_MARKER = Pattern.compile(
            "(?is)<\\s*/?\\s*\\|\\s*DSML\\s*\\|");

    private ToolCallTextParser() {
    }

    public static boolean containsProtocolMarker(String content) {
        return PROTOCOL_MARKER.matcher(normalize(content)).find();
    }

    public static JSONArray parse(String content) {
        JSONArray result = new JSONArray();
        String value = normalize(content);
        if (!containsProtocolMarker(value)) return result;
        Matcher invoke = INVOKE_OPEN.matcher(value);
        int sequence = 0;
        while (invoke.find()) {
            String name = attribute(invoke.group(1),"name").trim();
            if (name.isEmpty()) continue;
            int bodyStart = invoke.end();
            Matcher nextInvoke = INVOKE_OPEN.matcher(value);
            int nextStart = nextInvoke.find(bodyStart) ? nextInvoke.start() : value.length();
            Matcher close = INVOKE_CLOSE.matcher(value);
            int bodyEnd = close.find(bodyStart) && close.start() < nextStart
                    ? close.start() : nextStart;
            if (bodyEnd < bodyStart) bodyEnd = value.length();
            JSONObject arguments = parseParameters(value.substring(bodyStart,bodyEnd));
            JSONObject function = new JSONObject(true);
            function.put("name",name);
            function.put("arguments",arguments.toJSONString());
            JSONObject call = new JSONObject(true);
            call.put("id","call_dsml_"+(++sequence));
            call.put("type","function");
            call.put("function",function);
            result.add(call);
            invoke.region(Math.max(bodyEnd,invoke.end()),value.length());
        }
        return result;
    }

    public static String stripProtocolBlocks(String content) {
        String value = normalize(content);
        if (!containsProtocolMarker(value)) return content == null ? "" : content.trim();
        int first = firstProtocolMarker(value);
        if (first < 0) return "";
        return value.substring(0,first).trim();
    }

    private static JSONObject parseParameters(String body) {
        JSONObject result = new JSONObject(true);
        String value = normalize(body);
        Matcher parameter = PARAMETER_OPEN.matcher(value);
        while (parameter.find()) {
            String name = attribute(parameter.group(1),"name").trim();
            if (name.isEmpty()) continue;
            int valueStart = parameter.end();
            Matcher next = PARAMETER_OPEN.matcher(value);
            int nextStart = next.find(valueStart) ? next.start() : value.length();
            Matcher close = PARAMETER_CLOSE.matcher(value);
            int valueEnd = close.find(valueStart) && close.start() < nextStart
                    ? close.start() : nextStart;
            String raw = value.substring(valueStart,Math.max(valueStart,valueEnd)).trim();
            result.put(name,parseValue(raw,attribute(parameter.group(1),"string")));
            parameter.region(Math.max(valueEnd,parameter.end()),value.length());
        }
        return result;
    }

    private static Object parseValue(String raw,String stringAttribute) {
        String value = raw == null ? "" : raw.trim();
        if ("false".equalsIgnoreCase(stringAttribute) && !value.isEmpty()) {
            Object parsed = tryParse(value);
            if (parsed != null) return parsed;
        }
        if (!value.isEmpty() && (value.charAt(0) == '{' || value.charAt(0) == '[')) {
            Object parsed = tryParse(value);
            if (parsed != null) return parsed;
        }
        return value;
    }

    private static Object tryParse(String value) {
        try {
            return JSON.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String attribute(String attributes,String name) {
        if (attributes == null || attributes.trim().isEmpty()) return "";
        Matcher matcher = ATTRIBUTE.matcher(attributes);
        while (matcher.find()) {
            if (name.equalsIgnoreCase(matcher.group(1))) {
                return matcher.group(3) == null ? "" : matcher.group(3);
            }
        }
        return "";
    }

    private static int firstProtocolMarker(String value) {
        Matcher matcher = PROTOCOL_MARKER.matcher(value);
        return matcher.find() ? matcher.start() : -1;
    }

    private static String normalize(String content) {
        return content == null ? "" : content
                .replace('｜','|').replace('＜','<').replace('＞','>');
    }
}
