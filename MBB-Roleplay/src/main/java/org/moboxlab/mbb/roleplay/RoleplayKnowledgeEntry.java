package org.moboxlab.mbb.roleplay;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 知识库条目
 *
 * 对应 knowledge/&lt;库名&gt;/ 下的一个 md 文件，文件头是元信息，正文按小节切分。
 * 小节是检索与注入的最小单位，避免整条档案一次性塞进提示词。
 */
public class RoleplayKnowledgeEntry {

    /**
     * 条目的一个小节
     */
    public static class Section {
        public String title = "";
        public String text = "";
        public Set<String> bigrams = new LinkedHashSet<>();

        public void buildIndex() {
            bigrams = SpeechCorpusEntry.bigrams(
                    SpeechCorpusEntry.normalize(title+" "+text));
        }
    }

    public String id = "";
    public String name = "";
    public String summary = "";
    public String libraryID = "";
    public String filePath = "";
    public List<String> aliases = new ArrayList<>();
    public List<String> tags = new ArrayList<>();
    public List<String> keywords = new ArrayList<>();
    public String school = "";
    public String club = "";
    public String category = "";
    public String source = "";
    public int version = 1;
    public int priority = 60;
    public int spoiler = 0;
    public List<Section> sections = new ArrayList<>();
    public Set<String> nameBigrams = new LinkedHashSet<>();

    public void buildIndex() {
        for (Section section : sections) {
            section.buildIndex();
        }
        nameBigrams = SpeechCorpusEntry.bigrams(SpeechCorpusEntry.normalize(name));
    }

    /**
     * 实体锁定：查询文本里出现正式名或别名时直接锁定该条目，不再依赖全文打分
     */
    public boolean matchesEntity(String normalizedQuery) {
        if (normalizedQuery == null || normalizedQuery.isEmpty()) return false;
        String normalizedName = SpeechCorpusEntry.normalize(name);
        if (normalizedName.length() >= 2 && normalizedQuery.contains(normalizedName)) return true;
        for (String alias : aliases) {
            String value = SpeechCorpusEntry.normalize(alias);
            if (value.length() >= 2 && normalizedQuery.contains(value)) return true;
        }
        return false;
    }

    public int sectionCount() {
        return sections.size();
    }
}
