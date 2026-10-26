package org.moboxlab.mbb.roleplay;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 回复改写结果校验
 *
 * 风格层和复核层都只能改写表达，不能把正文替换成媒体占位符、
 * 结构化数据或明显丢失事实的短句。
 */
public class RoleplayReplyTextValidator {
    private static final String[] PROTECTED_TERMS = {
            "妈妈","爸爸","姐姐","妹妹","哥哥","弟弟","老师","主人"
    };
    private static final Pattern MEDIA_PLACEHOLDER = Pattern.compile(
            "(?i)[\\[［【](?:图片|表情包?|视频|语音|文件|动画表情|image|photo|sticker)[\\]］】]"
                    +"|<(?:image|photo|sticker)>");
    private static final Pattern CQ_CODE = Pattern.compile("(?i)\\[CQ:[^]]+]");
    private static final Pattern MARKDOWN_MEDIA = Pattern.compile(
            "!\\[[^]]*]\\([^)]*\\)|<img\\b[^>]*>",Pattern.CASE_INSENSITIVE);
    private static final Pattern URL = Pattern.compile(
            "(?i)https?://|www\\.");
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");
    private static final Pattern NON_MEANINGFUL = Pattern.compile("[\\p{P}\\p{S}\\s]");

    public static String rejectReason(String original,String candidate) {
        String before = original == null ? "" : original.trim();
        String after = candidate == null ? "" : candidate.trim();
        if (before.isEmpty()) return "";
        if (after.isEmpty()) return "改写结果为空";
        if (after.equals(before)) return "";
        if (RoleplayToolProtocolGuard.containsMarker(after)) return "改写结果包含工具调用协议";
        if (MEDIA_PLACEHOLDER.matcher(after).find()) return "改写结果包含媒体占位符";
        if (CQ_CODE.matcher(after).find()) return "改写结果包含 CQ 码";
        if (MARKDOWN_MEDIA.matcher(after).find()) return "改写结果包含 Markdown 或 HTML 媒体";
        if (after.contains("```")) return "改写结果包含 Markdown 代码块";
        if (after.startsWith("{") && (after.contains("\"text\"") || after.contains("\"actions\""))) {
            return "改写结果像结构化 JSON";
        }
        if (URL.matcher(after).find()) return "改写结果包含网址";
        if (after.length() > before.length() * 1.2 + 10) return "改写结果明显变长";
        if (meaningfulLength(after) < 2) return "改写结果没有有效文字";
        if (before.length() >= 8
                && after.length() * 4 < before.length()) {
            return "改写结果过度缩短";
        }
        if (!numbers(before).equals(numbers(after))) return "改写结果改变了数字";
        String termReason = protectedTermReason(before,after);
        if (!termReason.isEmpty()) return termReason;
        return "";
    }

    private static String protectedTermReason(String before,String after) {
        for (String term : PROTECTED_TERMS) {
            if (countOf(before,term) != countOf(after,term)) {
                return "改写结果改变了固定称呼："+term;
            }
        }
        return "";
    }

    private static int countOf(String value,String target) {
        int count = 0;
        int index = value.indexOf(target);
        while (index >= 0) {
            count++;
            index = value.indexOf(target,index + target.length());
        }
        return count;
    }

    private static int meaningfulLength(String value) {
        return NON_MEANINGFUL.matcher(value == null ? "" : value).replaceAll("").length();
    }

    private static Set<String> numbers(String value) {
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = NUMBER.matcher(value == null ? "" : value);
        while (matcher.find()) result.add(matcher.group());
        return result;
    }
}
