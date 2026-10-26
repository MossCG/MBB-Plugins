package org.moboxlab.mbb.roleplay;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 回复去舞台化清洗
 *
 * 只处理确定的格式污染：角色名前缀、括号动作、旁白行、Markdown 和列表标记。
 */
public class RoleplayReplySanitizer {
    private static final Pattern LEADING_STAGE = Pattern.compile(
            "^\\s*[（(【\\[][^）)】\\]]{1,40}[）)】\\]]\\s*");
    private static final Pattern ASTERISK_ACTION = Pattern.compile("\\*[^*\\n]{1,40}\\*");
    private static final Pattern INLINE_STAGE_ACTION = Pattern.compile(
            "[（(](?:叹气|扶额|笑|沉默|皱眉|摇头|点头|转身|小声|停顿|无奈|认真|害羞|动作)"
                    +"[^）)]{0,24}[）)]");
    private static final Pattern NARRATION_LINE = Pattern.compile(
            "(?m)^\\s*(旁白|心理活动|内心|动作|镜头|场景描写|神态)[:：].*$");
    private static final Pattern HEADING_LINE = Pattern.compile("(?m)^\\s*#{1,6}\\s*");
    private static final Pattern LIST_MARKER = Pattern.compile("(?m)^\\s*[-*+]\\s+");

    public static String clean(RoleplayPersona persona,String botName,String reply) {
        String value = reply == null ? "" : reply.replace("\r\n","\n").replace('\r','\n').trim();
        if (value.isEmpty()) return "";
        if (RoleplayToolProtocolGuard.containsMarker(value)) {
            value = RoleplayToolProtocolGuard.strip(value);
            if (value.isEmpty()) return "";
        }
        value = value.replace("```","").replace("**","").replace("__","").replace("~~","");
        value = NARRATION_LINE.matcher(value).replaceAll("");
        value = HEADING_LINE.matcher(value).replaceAll("");
        value = LIST_MARKER.matcher(value).replaceAll("");
        value = ASTERISK_ACTION.matcher(value).replaceAll("");
        value = INLINE_STAGE_ACTION.matcher(value).replaceAll("");
        while (LEADING_STAGE.matcher(value).find()) {
            value = LEADING_STAGE.matcher(value).replaceFirst("").trim();
        }
        value = stripSpeakerPrefix(persona,botName,value);
        value = stripOuterQuotes(value);
        value = value.replaceAll("[ \\t\\u00A0]+"," ")
                .replaceAll(" *\\n *","\n")
                .replaceAll("\\n{3,}","\n\n")
                .trim();
        return removeNarrationOnlyLines(value);
    }

    private static String stripSpeakerPrefix(RoleplayPersona persona,String botName,String value) {
        List<String> names = new ArrayList<>();
        if (persona != null) {
            names.add(persona.name);
            names.addAll(persona.aliases);
        }
        if (botName != null) names.add(botName);
        names.add("角色");
        names.add("回复");
        String text = value.trim();
        for (String name : names) {
            if (name == null || name.trim().isEmpty()) continue;
            String prefix = name.trim();
            if (text.startsWith(prefix+"：") || text.startsWith(prefix+":")) {
                return text.substring(prefix.length()+1).trim();
            }
        }
        return text;
    }

    private static String stripOuterQuotes(String value) {
        String text = value.trim();
        if (text.length() < 2) return text;
        boolean quoted = (text.startsWith("\"") && text.endsWith("\""))
                || (text.startsWith("“") && text.endsWith("”"));
        return quoted ? text.substring(1,text.length()-1).trim() : text;
    }

    private static String removeNarrationOnlyLines(String value) {
        String[] lines = value.split("\n",-1);
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            String text = line == null ? "" : line.trim();
            if (text.matches("[（(【\\[][^）)】\\]]{1,60}[）)】\\]]")) continue;
            if (builder.length() > 0) builder.append("\n");
            builder.append(text);
        }
        return builder.toString().trim();
    }
}
