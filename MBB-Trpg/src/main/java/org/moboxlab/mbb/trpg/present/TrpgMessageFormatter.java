package org.moboxlab.mbb.trpg.present;

/**
 * 消息清洗与长度控制（纯函数）。
 *
 * <p>规整口径见呈现规范 §2.1：去 Markdown 残符、去 emoji、压空白、统一换行；
 * 字数一律按码点统计，避免代理对（emoji、罕见汉字）被算成两个字符。</p>
 */
public final class TrpgMessageFormatter {

    /** 截断时追加的省略号。 */
    public static final char ELLIPSIS = '…';

    /** 清洗文本：换行归一、去 Markdown 残符、去 emoji、压空白、去首尾空白。 */
    public String clean(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder stripped = new StringBuilder(normalized.length());
        int length = normalized.length();
        for (int index = 0; index < length; ) {
            int codePoint = normalized.codePointAt(index);
            index += Character.charCount(codePoint);
            if (isEmoji(codePoint)) {
                continue;
            }
            stripped.appendCodePoint(codePoint);
        }
        String noEmoji = stripped.toString()
                .replace("**", "")
                .replace("__", "")
                .replace("~~", "")
                .replace("`", "")
                .replace("|", "");
        StringBuilder result = new StringBuilder(noEmoji.length());
        String[] lines = noEmoji.split("\n", -1);
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = stripLinePrefix(lines[lineIndex]);
            line = collapseSpaces(line).trim();
            if (lineIndex > 0) {
                result.append('\n');
            }
            result.append(line);
        }
        String collapsed = result.toString().replaceAll("\n{3,}", "\n\n");
        return collapsed.trim();
    }

    /** 去掉行首 Markdown 记号：标题井号、引用尖括号、列表符号。 */
    private static String stripLinePrefix(String line) {
        if (line == null) {
            return "";
        }
        String current = line;
        while (current.startsWith(">")) {
            current = current.substring(1).trim();
        }
        if (current.startsWith("#")) {
            int index = 0;
            while (index < current.length() && current.charAt(index) == '#') {
                index++;
            }
            current = current.substring(index).trim();
        }
        if (current.startsWith("- ") || current.startsWith("* ") || current.startsWith("+ ")) {
            current = current.substring(2).trim();
        }
        return current;
    }

    /** 把连续空白压成一个：ASCII 空格与全角空格各自收敛，制表符转空格。 */
    private static String collapseSpaces(String line) {
        if (line == null) {
            return "";
        }
        return line.replace('\t', ' ')
                .replaceAll(" {2,}", " ")
                .replaceAll("　{2,}", "　");
    }

    /** 是否 emoji 或变体选择符（emoji 一律不进入群消息）。 */
    private static boolean isEmoji(int codePoint) {
        return (codePoint >= 0x1F000 && codePoint <= 0x1FAFF)
                || (codePoint >= 0x2600 && codePoint <= 0x27BF)
                || (codePoint >= 0x2B00 && codePoint <= 0x2BFF)
                || (codePoint >= 0x1F1E6 && codePoint <= 0x1F1FF)
                || codePoint == 0xFE0F
                || codePoint == 0x200D
                || codePoint == 0x20E3;
    }

    /** 按码点统计可见字符数。 */
    public int count(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.codePointCount(0, text.length());
    }

    /** 超过 maxChars 个码点时截断并补一个省略号；不切断代理对。 */
    public String truncate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        int limit = maxChars <= 0 ? 0 : maxChars;
        if (count(text) <= limit) {
            return text;
        }
        if (limit == 0) {
            return "";
        }
        int keep = limit - 1;
        int end = text.offsetByCodePoints(0, keep);
        return text.substring(0, end) + ELLIPSIS;
    }

    /** 先清洗再按上限截断（模板正文统一走这里）。 */
    public String cleanAndTruncate(String text, int maxChars) {
        return truncate(clean(text), maxChars);
    }

    /** 按码点截取前 maxChars 个字符，不加省略号；不切断代理对。 */
    public String take(String text, int maxChars) {
        if (text == null || text.isEmpty() || maxChars <= 0) {
            return "";
        }
        int available = count(text);
        if (available <= maxChars) {
            return text;
        }
        int end = text.offsetByCodePoints(0, maxChars);
        return text.substring(0, end);
    }
}
