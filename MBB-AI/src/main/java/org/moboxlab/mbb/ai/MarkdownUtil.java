package org.moboxlab.mbb.ai;

/**
 * AI 输出纯文本化
 */
public class MarkdownUtil {
    public static String toPlainText(String text) {
        if (text == null) return "";
        String value = text.replace("\r","").trim();
        value = value.replaceAll("(?s)```[a-zA-Z0-9_-]*\\s*","");
        value = value.replace("```","");
        value = value.replaceAll("`([^`]+)`","$1");
        value = value.replaceAll("\\*\\*([^*]+)\\*\\*","$1");
        value = value.replaceAll("__([^_]+)__","$1");
        value = value.replaceAll("~~([^~]+)~~","$1");
        value = value.replaceAll("(?m)^#{1,6}\\s*","");
        value = value.replaceAll("(?m)^>\\s*","");
        value = value.replaceAll("(?m)^[-*+]\\s+","- ");
        value = value.replaceAll("(?m)^\\s*---\\s*$","");
        value = value.replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)","$1");
        value = value.replaceAll("(?<![*])\\*([^*\\n]+)\\*(?![*])","$1");
        value = value.replaceAll("(?<!_)_([^_\\n]+)_(?!_)","$1");
        value = value.replaceAll("\\n{3,}","\n\n");
        return value.trim();
    }
}
