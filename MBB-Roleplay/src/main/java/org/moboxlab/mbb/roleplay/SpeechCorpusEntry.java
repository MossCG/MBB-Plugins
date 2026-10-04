package org.moboxlab.mbb.roleplay;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 角色台词语料条目
 */
public class SpeechCorpusEntry {
    public String id = "";
    public String role = "";
    public String text = "";
    public String emotion = "neutral";
    public String scene = "group_chat";
    public String source = "";
    public double weight = 1.0;
    public int spoiler = 0;
    public List<String> tags = new ArrayList<>();
    public String normalized = "";
    public Set<String> bigrams = new LinkedHashSet<>();

    public void buildIndex() {
        normalized = normalize(text);
        bigrams = bigrams(normalized);
    }

    public static String normalize(String text) {
        if (text == null) return "";
        return text.toLowerCase()
                .replaceAll("[^\\u4e00-\\u9fa5a-z0-9]","")
                .trim();
    }

    public static Set<String> bigrams(String text) {
        Set<String> result = new LinkedHashSet<>();
        if (text == null || text.length() < 2) return result;
        for (int i = 0; i < text.length() - 1; i++) {
            result.add(text.substring(i,i + 2));
        }
        return result;
    }
}
