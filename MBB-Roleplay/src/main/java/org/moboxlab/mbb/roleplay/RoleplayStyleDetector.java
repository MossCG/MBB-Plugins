package org.moboxlab.mbb.roleplay;

import java.util.regex.Pattern;

/**
 * AI 味本地检测
 *
 * 风格层默认不调用，先用本地规则判断这条回复是否值得花一次改写。
 * 特征清单参考维基百科 Wikipedia:Signs of AI writing，并补了中文特有的书面语表达。
 */
public class RoleplayStyleDetector {

    /** 中文里最容易暴露 AI 味的书面语与套话 */
    private static final String[] AI_FLAVOR_WORDS = {
            "首先","其次","总的来说","综上所述","总而言之","简而言之","值得注意的是",
            "与此同时","换句话说","也就是说","需要注意的是","在一定程度上","具有重要意义",
            "希望这对你有帮助","希望能帮到你","如果需要我","作为一个","关于这一点",
            "不仅是","更是","让我来","我来帮你梳理","不难发现","可以看出","由此可见",
            "总体而言","从某种意义上","让我们","接下来","先说说","说到这里","无论如何",
            "一方面","另一方面","更重要的是","归根结底","毫无疑问","显而易见","换言之",
            "赋能","闭环","抓手","底层逻辑","链路","颗粒度","复盘","对齐","拉齐",
            "心智","打法","组合拳","增长飞轮","护城河","价值锚点","生态位",
            "算账","记账","欠账","还账","结账","账本","讨债"
    };
    private static final Pattern[] AI_FLAVOR_PATTERNS = {
            Pattern.compile("不是.{0,24}而是"),
            Pattern.compile("不仅是.{0,24}更是"),
            Pattern.compile("与其说.{0,24}不如说"),
            Pattern.compile("既.{0,20}又.{0,20}还"),
            Pattern.compile("第一[，,].{0,60}第二[，,].{0,60}第三"),
            Pattern.compile("其一[，,].{0,60}其二[，,].{0,60}其三"),
            Pattern.compile("无论.{0,30}还是.{0,30}都"),
            Pattern.compile("(总的来说|总而言之|归根结底)[。！？!?]?$")
    };

    /**
     * 返回触发原因，空字符串表示不需要风格化
     */
    public static String reason(RoleplayConfig config,String text,boolean proactive) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) return "";
        if (proactive) return "主动发言";
        if (value.length() > config.styleMaxChars) return "超长";
        if (value.contains("——")) return "破折号";
        if (value.contains("**") || value.contains("```")) return "Markdown残留";
        for (Pattern pattern : AI_FLAVOR_PATTERNS) {
            if (pattern.matcher(value).find()) return "AI排比或总结句";
        }
        for (String word : AI_FLAVOR_WORDS) {
            if (value.contains(word)) return "AI味词";
        }
        if (hasUniformSentenceLength(value)) return "句长均匀";
        if (hasListMarker(value)) return "列点";
        if (hasPairedQuote(value)) return "成对引号";
        return "";
    }

    private static boolean hasListMarker(String value) {
        String[] lines = value.split("\n");
        int count = 0;
        for (String line : lines) {
            String text = line.trim();
            if (text.startsWith("- ") || text.startsWith("* ") || text.startsWith("+ ")) count++;
            else if (text.matches("^[0-9]+[.、].*")) count++;
        }
        return count >= 2;
    }

    private static boolean hasPairedQuote(String value) {
        return countOf(value,"“") > 0 && countOf(value,"”") > 0;
    }

    private static boolean hasUniformSentenceLength(String value) {
        String[] parts = value.split("[。！？!?]");
        int count = 0;
        int min = Integer.MAX_VALUE;
        int max = 0;
        for (String part : parts) {
            String text = part.trim();
            if (text.isEmpty()) continue;
            count++;
            min = Math.min(min,text.length());
            max = Math.max(max,text.length());
        }
        return count >= 3 && min >= 4 && max - min <= 2;
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
}
