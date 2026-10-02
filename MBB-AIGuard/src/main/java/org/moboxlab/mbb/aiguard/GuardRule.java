package org.moboxlab.mbb.aiguard;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 单条风险规则
 */
public class GuardRule {
    public String category = "";
    public int risk = 0;
    public boolean safety = false;
    public List<String> keywords = new ArrayList<>();
    public List<Pattern> patterns = new ArrayList<>();

    public GuardMatch match(String content) {
        if (content == null || content.trim().isEmpty()) return null;
        String text = content.toLowerCase();
        GuardMatch match = new GuardMatch();
        match.category = category;
        match.risk = risk;
        match.safety = safety;
        for (String keyword : keywords) {
            if (keyword == null || keyword.trim().isEmpty()) continue;
            if (text.contains(keyword.toLowerCase())) match.keywords.add(keyword);
        }
        for (Pattern pattern : patterns) {
            if (pattern.matcher(text).find()) {
                if (match.keywords.isEmpty()) match.keywords.add("正则:"+pattern.pattern());
            }
        }
        return match.keywords.isEmpty() ? null : match;
    }
}
