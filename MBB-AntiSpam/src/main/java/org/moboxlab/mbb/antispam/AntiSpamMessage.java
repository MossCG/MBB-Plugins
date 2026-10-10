package org.moboxlab.mbb.antispam;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * 消息内容提取
 *
 * 把 OneBot 消息段数组转成便于统计的纯文本，并统计艾特与 @全体成员。
 */
public final class AntiSpamMessage {

    /** 提取后的消息内容 */
    public static class Content {
        public String text = "";
        public int mentionCount = 0;
        public boolean mentionAll = false;
        public boolean hasImage = false;
    }

    private AntiSpamMessage() {
    }

    public static Content extract(JSONArray message) {
        Content content = new Content();
        if (message == null) return content;
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < message.size(); i++) {
            JSONObject segment = message.getJSONObject(i);
            if (segment == null) continue;
            String type = segment.getString("type");
            if (type == null) continue;
            JSONObject data = segment.getJSONObject("data");
            if ("text".equals(type)) {
                String text = data == null ? null : data.getString("text");
                if (text != null) builder.append(text);
                continue;
            }
            if ("at".equals(type)) {
                //@全体成员 在 OneBot 里是 qq=all，直接取 long 会抛异常，必须先看原始值
                Object raw = data == null ? null : data.get("qq");
                String qqText = raw == null ? "" : String.valueOf(raw).trim();
                long qq = parseLong(qqText);
                if ("all".equalsIgnoreCase(qqText) || qq == 0L) {
                    content.mentionAll = true;
                }
                content.mentionCount++;
                continue;
            }
            if ("image".equals(type)) {
                content.hasImage = true;
                builder.append("[图片]");
                continue;
            }
            if ("face".equals(type)) {
                builder.append("[表情]");
                continue;
            }
            if ("record".equals(type)) {
                builder.append("[语音]");
                continue;
            }
            if ("reply".equals(type)) {
                builder.append("[回复]");
            }
        }
        content.text = builder.toString().trim();
        return content;
    }

    /**
     * 复读指纹：只保留中文与字母并统一小写，忽略空白、标点与数字
     *
     * 忽略数字是为了让「刷屏测试1」「刷屏测试2」这类只改结尾数字的连发也能被判为复读；
     * 忽略标点与空白是为了让「哈哈哈哈哈」与「哈哈哈 哈哈」视为同一内容。
     */
    public static String fingerprint(String text,int maxChars) {
        if (text == null) return "";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char value = text.charAt(i);
            if (Character.isLetter(value)) {
                builder.append(Character.toLowerCase(value));
            }
        }
        if (builder.length() == 0) return "";
        int limit = Math.min(maxChars,builder.length());
        return builder.substring(0,limit);
    }

    private static long parseLong(String value) {
        if (value == null || value.isEmpty()) return 0L;
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return 0L;
        }
    }
}
