package org.moboxlab.mbb.roleplay;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 当前消息命中网络用语时，注入最小必要的含义和语境边界。
 *
 * 词表只帮助理解，不鼓励角色为了显得年轻而连续堆缩写。
 */
public class RoleplaySlangLexicon {
    private static final Pattern WC = token("wc");
    private static final Pattern HYW = token("hyw");
    private static final Pattern XSWL = token("xswl");
    private static final Pattern AWSL = token("awsl");
    private static final Pattern YYDS = token("yyds");
    private static final Pattern U1S1 = token("u1s1");
    private static final Pattern BOX = Pattern.compile(
            "(?s)(^|[\\s，。！？!?])盒([\\s，。！？!?]|$)");

    private RoleplaySlangLexicon() {
    }

    static String promptFor(String message) {
        String text = message == null ? "" : message.trim();
        if (text.isEmpty()) return "";
        List<String> lines = new ArrayList<>();
        if (WC.matcher(text).find()) {
            lines.add("- wc（卧槽）：群聊里通常表示惊讶、停顿或吐槽；若上下文明显指厕所、代码或专名则不要按此义。只在轻松闲聊里极低频使用，不用于严肃、安慰和争执。");
        }
        if (HYW.matcher(text).find()) {
            lines.add("- hyw：常见于问“何意味/什么意思”；没看懂对方的梗时可以用来短问，不要硬把梗翻成字面事实。");
        }
        if (XSWL.matcher(text).find()) {
            lines.add("- xswl：笑死我了；用于觉得好笑，不必再展开解释笑点。");
        }
        if (AWSL.matcher(text).find()) {
            lines.add("- awsl：啊我死了；多为被可爱到或受到强烈冲击，保持低频。");
        }
        if (YYDS.matcher(text).find()) {
            lines.add("- yyds：永远的神；表示高度认可，少用于普通夸奖，避免廉价化。");
        }
        if (U1S1.matcher(text).find()) {
            lines.add("- u1s1：有一说一；用于准备讲一个相对客观的判断。");
        }
        if (text.contains("绷不住")) {
            lines.add("- 绷不住：忍不住笑、破防或维持不住原表情；只接当前情绪，不连续复读。");
        }
        if (text.contains("开盒") || text.contains("盒武器") || BOX.matcher(text).find()) {
            lines.add("- 盒/开盒：指公开或追查个人信息。只作隐私风险识别，不参与起哄、不鼓励、不扩散；普通“盒子、盲盒”不按这个意思理解。");
        }
        if (lines.isEmpty()) return "";
        StringBuilder builder = new StringBuilder("【本轮网络用语理解】\n")
                .append("下面只帮助理解当前消息，不是要求复述。能理解即可，只有语境自然时极低频使用。\n");
        for (String line : lines) builder.append(line).append("\n");
        builder.append("一轮最多使用一个网络缩写；不确定含义就短问，不要自造缩写，也不要把每个缩写翻译成完整解释。\n");
        return builder.toString();
    }

    private static Pattern token(String value) {
        return Pattern.compile("(?i)(^|[^a-z0-9])"
                +Pattern.quote(value.toLowerCase(Locale.ROOT))+"([^a-z0-9]|$)");
    }
}
