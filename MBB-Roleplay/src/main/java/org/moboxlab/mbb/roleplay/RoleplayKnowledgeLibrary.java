package org.moboxlab.mbb.roleplay;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库
 *
 * 一个库对应 knowledge/ 下的一个目录，库元信息来自目录里的 _index.md。
 * 库内维护小节级倒排统计，用于 bigram 检索打分。
 */
public class RoleplayKnowledgeLibrary {
    public String id = "";
    public String name = "";
    public String scope = "";
    public int version = 1;
    public int priority = 60;
    public int maxInjectChars = 2000;
    public boolean enabled = true;
    public String path = "";
    public List<RoleplayKnowledgeEntry> entries = new ArrayList<>();
    private final Map<String,Integer> documentFrequencyMap = new LinkedHashMap<>();

    public void buildIndex() {
        documentFrequencyMap.clear();
        for (RoleplayKnowledgeEntry entry : entries) {
            entry.buildIndex();
            for (RoleplayKnowledgeEntry.Section section : entry.sections) {
                for (String bigram : section.bigrams) {
                    Integer count = documentFrequencyMap.get(bigram);
                    documentFrequencyMap.put(bigram,count == null ? 1 : count + 1);
                }
            }
        }
    }

    public int sectionCount() {
        int count = 0;
        for (RoleplayKnowledgeEntry entry : entries) count += entry.sectionCount();
        return count;
    }

    /**
     * 某个 bigram 出现在多少个小节里，用于计算 idf
     */
    public int documentFrequency(String bigram) {
        Integer count = documentFrequencyMap.get(bigram);
        return count == null ? 1 : count;
    }
}
