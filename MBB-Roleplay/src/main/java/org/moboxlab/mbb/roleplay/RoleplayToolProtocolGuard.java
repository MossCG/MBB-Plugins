package org.moboxlab.mbb.roleplay;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 防止模型把工具调用协议当成聊天正文发送。
 */
public class RoleplayToolProtocolGuard {
    private static final Pattern PROTOCOL_MARKER = Pattern.compile(
            "(?is)<\\s*/?\\s*\\|\\s*DSML\\s*\\|");

    private RoleplayToolProtocolGuard() {
    }

    static boolean containsMarker(String content) {
        return PROTOCOL_MARKER.matcher(normalize(content)).find();
    }

    static String strip(String content) {
        String value = normalize(content);
        if (!containsMarker(value)) return content == null ? "" : content.trim();
        Matcher matcher = PROTOCOL_MARKER.matcher(value);
        int first = matcher.find() ? matcher.start() : -1;
        return first < 0 ? "" : value.substring(0,first).trim();
    }

    private static String normalize(String content) {
        return content == null ? "" : content
                .replace('｜','|').replace('＜','<').replace('＞','>');
    }
}
