package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Plugin;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 可选知识库
 *
 * 目录结构、文件头字段与长度限制见插件目录下的 KNOWLEDGE.md。
 * 加载时把每个库解析成条目与小节；检索先做实体锁定，再按 bigram 打分取小节，
 * 只注入命中的小节，不注入整篇档案。
 */
public class RoleplayKnowledgeService {
    private static final int SUMMARY_MAX_CHARS = 60;
    private static final int SECTION_MAX_CHARS = 800;
    private static final int ENTRY_MAX_CHARS = 3000;

    private final Plugin plugin;
    private volatile RoleplayConfig config;
    private volatile Map<String,RoleplayKnowledgeLibrary> libraryMap = new LinkedHashMap<>();
    private volatile String knowledgePath = "";

    public RoleplayKnowledgeService(Plugin plugin,RoleplayConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void init() {
        reload(config);
    }

    public void reload(RoleplayConfig config) {
        this.config = config;
        load();
    }

    public List<RoleplayKnowledgeLibrary> libraries() {
        return new ArrayList<>(libraryMap.values());
    }

    public List<RoleplayKnowledgeLibrary> enabledLibraries() {
        List<RoleplayKnowledgeLibrary> result = new ArrayList<>();
        for (RoleplayKnowledgeLibrary library : libraryMap.values()) {
            if (library.enabled) result.add(library);
        }
        return result;
    }

    /**
     * 学生档案库是否已经启用。
     * 启用时插件自带的 students.json 详细设定不再注入，避免同一份学生信息出现两次。
     */
    public boolean isStudentsLibraryEnabled() {
        if (!config.knowledgeEnable) return false;
        String expected = config.knowledgeStudentsLibrary;
        if (expected == null || expected.trim().isEmpty()) return false;
        for (RoleplayKnowledgeLibrary library : enabledLibraries()) {
            if (expected.trim().equals(library.id)) return true;
        }
        return false;
    }

    public int entryCount() {
        int count = 0;
        for (RoleplayKnowledgeLibrary library : libraryMap.values()) {
            count += library.entries.size();
        }
        return count;
    }

    public int sectionCount() {
        int count = 0;
        for (RoleplayKnowledgeLibrary library : libraryMap.values()) {
            count += library.sectionCount();
        }
        return count;
    }

    public String getKnowledgePath() {
        return knowledgePath;
    }

    /**
     * 给路由层看的资料目录，只列库名、覆盖范围和条目数
     */
    public String catalogueText() {
        StringBuilder builder = new StringBuilder();
        for (RoleplayKnowledgeLibrary library : enabledLibraries()) {
            builder.append("kb.").append(library.id).append("：")
                    .append(library.scope.isEmpty() ? library.name : library.scope)
                    .append("（").append(library.entries.size()).append(" 条）\n");
        }
        return builder.toString();
    }

    /**
     * 按查询文本检索并把命中内容组装成一段资料，未命中或未开启返回空串
     */
    public String injectText(String libraryID,String query) {
        RoleplayKnowledgeLibrary library = libraryMap.get(libraryID);
        if (library == null || !library.enabled) return "";
        List<Match> matches = retrieve(library,query,config.knowledgeMaxEntries);
        if (matches.isEmpty()) return "";
        int budget = library.maxInjectChars > 0 ? library.maxInjectChars
                : config.knowledgeInjectMaxChars;
        if (config.knowledgeInjectMaxChars > 0) budget = Math.min(budget,config.knowledgeInjectMaxChars);
        if (budget <= 0) budget = 2000;
        StringBuilder builder = new StringBuilder();
        int used = 0;
        int injectedEntries = 0;
        int injectedSections = 0;
        for (Match match : matches) {
            StringBuilder block = new StringBuilder();
            block.append("- ").append(match.entry.name);
            if (!match.entry.summary.isEmpty()) block.append("：").append(match.entry.summary);
            block.append("\n");
            for (ScoredSection scored : match.sections) {
                RoleplayKnowledgeEntry.Section section = scored.section;
                String text = flatten(section.text);
                if (text.isEmpty()) continue;
                block.append("  ").append(section.title).append("：").append(text).append("\n");
            }
            if (block.length() <= 0) continue;
            int remain = budget - used;
            if (remain <= 0) break;
            if (block.length() > remain) {
                if (injectedEntries > 0) break;
                builder.append(block.substring(0,remain));
                used += remain;
                injectedEntries++;
                break;
            }
            builder.append(block);
            used += block.length();
            injectedEntries++;
            injectedSections += match.sections.size();
        }
        if (injectedEntries == 0) return "";
        plugin.getLogger().sendInfo("[知识库] "+library.id+" 命中 "+injectedEntries+" 条 "
                +injectedSections+" 小节 占用="+used+" 字符 查询="+shortText(query,40));
        return "参考资料（"+library.name+"，只用来回答事实问题，不要照抄，也不要当成人设或自己的记忆）：\n"
                +builder.toString();
    }

    /**
     * 调试用检索，返回命中条目、小节和分数
     */
    public JSONArray search(String query,int limit) {
        JSONArray result = new JSONArray();
        for (RoleplayKnowledgeLibrary library : enabledLibraries()) {
            List<Match> matches = retrieve(library,query,limit);
            for (Match match : matches) {
                if (match.sections.isEmpty()) {
                    JSONObject item = new JSONObject(true);
                    item.put("library",library.id);
                    item.put("entry",match.entry.name);
                    item.put("id",match.entry.id);
                    item.put("section","摘要");
                    item.put("score",Math.round(match.score * 100) / 100.0);
                    item.put("locked",match.locked);
                    item.put("text",shortText(match.entry.summary,80));
                    result.add(item);
                    continue;
                }
                for (ScoredSection scored : match.sections) {
                    JSONObject item = new JSONObject(true);
                    item.put("library",library.id);
                    item.put("entry",match.entry.name);
                    item.put("id",match.entry.id);
                    item.put("section",scored.section.title);
                    item.put("score",Math.round(scored.score * 100) / 100.0);
                    item.put("locked",match.locked);
                    item.put("text",shortText(flatten(scored.section.text),80));
                    result.add(item);
                }
            }
        }
        return result;
    }

    public JSONObject stats() {
        JSONObject result = new JSONObject(true);
        result.put("enabled",config.knowledgeEnable);
        result.put("path",knowledgePath);
        result.put("libraryCount",libraryMap.size());
        result.put("entryCount",entryCount());
        result.put("sectionCount",sectionCount());
        JSONArray libraries = new JSONArray();
        for (RoleplayKnowledgeLibrary library : libraryMap.values()) {
            JSONObject item = new JSONObject(true);
            item.put("id",library.id);
            item.put("name",library.name);
            item.put("scope",library.scope);
            item.put("enabled",library.enabled);
            item.put("priority",library.priority);
            item.put("entries",library.entries.size());
            item.put("sections",library.sectionCount());
            libraries.add(item);
        }
        result.put("libraries",libraries);
        return result;
    }

    private void load() {
        Map<String,RoleplayKnowledgeLibrary> loaded = new LinkedHashMap<>();
        knowledgePath = "";
        File root = new File(plugin.getDataFolder(),config.knowledgeDirectory);
        knowledgePath = root.getAbsolutePath();
        if (!config.knowledgeEnable) {
            libraryMap = loaded;
            return;
        }
        if (!root.exists() || !root.isDirectory()) {
            plugin.getLogger().sendInfo("[知识库] 未找到目录 "+knowledgePath+"，跳过加载");
            libraryMap = loaded;
            return;
        }
        File[] directories = root.listFiles();
        if (directories == null) {
            libraryMap = loaded;
            return;
        }
        for (File directory : directories) {
            if (!directory.isDirectory()) continue;
            String libraryID = directory.getName();
            if (!libraryID.matches("[a-z0-9][a-z0-9.-]*")) {
                plugin.getLogger().sendWarn("[知识库] 跳过非法库名："+libraryID);
                continue;
            }
            RoleplayKnowledgeLibrary library = loadLibrary(directory,libraryID);
            if (library == null) continue;
            loaded.put(libraryID,library);
        }
        libraryMap = loaded;
        plugin.getLogger().sendInfo("[知识库] 已加载 "+loaded.size()+" 个库，"
                +entryCount()+" 条目，"+sectionCount()+" 小节");
    }

    private RoleplayKnowledgeLibrary loadLibrary(File directory,String libraryID) {
        RoleplayKnowledgeLibrary library = new RoleplayKnowledgeLibrary();
        library.id = libraryID;
        library.name = libraryID;
        library.path = directory.getAbsolutePath();
        File indexFile = new File(directory,"_index.md");
        if (indexFile.exists()) {
            applyLibraryIndex(indexFile,library);
        } else {
            plugin.getLogger().sendWarn("[知识库] "+libraryID+" 缺少 _index.md，使用默认元信息");
        }
        File[] files = directory.listFiles();
        if (files == null) return library;
        int missing = 0;
        int tooLong = 0;
        int noSection = 0;
        for (File file : files) {
            if (!file.isFile()) continue;
            String fileName = file.getName();
            if (!fileName.toLowerCase(Locale.ROOT).endsWith(".md")) continue;
            if (fileName.startsWith("_")) continue;
            RoleplayKnowledgeEntry entry = loadEntry(file,libraryID);
            if (entry == null) continue;
            if (entry.name.isEmpty()) {
                entry.name = entry.id;
                missing++;
            }
            if (entry.summary.isEmpty()) missing++;
            if (entry.sections.isEmpty()) noSection++;
            if (entry.summary.length() > SUMMARY_MAX_CHARS) tooLong++;
            int bodyChars = 0;
            for (RoleplayKnowledgeEntry.Section section : entry.sections) {
                bodyChars += section.text.length();
                if (section.text.length() > SECTION_MAX_CHARS) tooLong++;
            }
            if (bodyChars > ENTRY_MAX_CHARS) tooLong++;
            library.entries.add(entry);
        }
        if (missing > 0) {
            plugin.getLogger().sendWarn("[知识库] "+libraryID+" 有 "+missing
                    +" 处必填字段缺失（name/summary）");
        }
        if (noSection > 0) {
            plugin.getLogger().sendWarn("[知识库] "+libraryID+" 有 "+noSection
                    +" 个条目没有正文小节");
        }
        if (tooLong > 0) {
            plugin.getLogger().sendWarn("[知识库] "+libraryID+" 有 "+tooLong
                    +" 处超过长度限制，建议按 KNOWLEDGE.md 拆分");
        }
        library.buildIndex();
        return library;
    }

    private void applyLibraryIndex(File file,RoleplayKnowledgeLibrary library) {
        Header header = readHeader(file);
        if (header == null) return;
        library.id = value(header.fields,"id",library.id);
        if (!library.id.equals(file.getParentFile().getName())) {
            plugin.getLogger().sendWarn("[知识库] "+file.getParentFile().getName()
                    +" 的 _index.md 里 id 与目录名不一致："+library.id);
            library.id = file.getParentFile().getName();
        }
        library.name = value(header.fields,"name",library.name);
        library.scope = value(header.fields,"scope","");
        library.version = parseInt(header.fields.get("version"),1);
        library.priority = parseInt(header.fields.get("priority"),60);
        library.maxInjectChars = parseInt(header.fields.get("maxInjectChars"),2000);
        library.enabled = !"false".equalsIgnoreCase(value(header.fields,"enabled","true"));
    }

    private RoleplayKnowledgeEntry loadEntry(File file,String libraryID) {
        Header header = readHeader(file);
        if (header == null) return null;
        RoleplayKnowledgeEntry entry = new RoleplayKnowledgeEntry();
        entry.libraryID = libraryID;
        entry.filePath = file.getAbsolutePath();
        String fileName = file.getName();
        entry.id = fileName.toLowerCase(Locale.ROOT).endsWith(".md")
                ? fileName.substring(0,fileName.length() - 3) : fileName;
        Map<String,String> fields = header.fields;
        entry.id = value(fields,"id",entry.id);
        if (!entry.id.equals(fileName.substring(0,fileName.length() - 3))) {
            plugin.getLogger().sendWarn("[知识库] "+libraryID+"/"+fileName
                    +" 的 id 与文件名不一致："+entry.id);
            entry.id = fileName.substring(0,fileName.length() - 3);
        }
        entry.name = value(fields,"name","");
        entry.summary = value(fields,"summary","");
        entry.aliases = splitList(value(fields,"aliases",""));
        entry.tags = splitList(value(fields,"tags",""));
        entry.keywords = splitList(value(fields,"keywords",""));
        entry.school = value(fields,"school","");
        entry.club = value(fields,"club","");
        entry.category = value(fields,"category","");
        entry.source = value(fields,"source","");
        entry.version = parseInt(fields.get("version"),1);
        entry.priority = parseInt(fields.get("priority"),60);
        entry.spoiler = parseInt(fields.get("spoiler"),0);
        readSections(header,entry);
        return entry;
    }

    private void readSections(Header header,RoleplayKnowledgeEntry entry) {
        List<String> lines = header.lines;
        RoleplayKnowledgeEntry.Section current = null;
        StringBuilder preamble = new StringBuilder();
        for (int index = header.bodyStart; index < lines.size(); index++) {
            String trimmed = lines.get(index).trim();
            if (trimmed.startsWith("## ")) {
                current = new RoleplayKnowledgeEntry.Section();
                current.title = trimmed.substring(3).trim();
                entry.sections.add(current);
                continue;
            }
            if (current == null) {
                if (!trimmed.isEmpty()) preamble.append(trimmed).append("\n");
                continue;
            }
            if (trimmed.isEmpty()) continue;
            current.text = current.text.isEmpty() ? trimmed : current.text+"\n"+trimmed;
        }
        String text = preamble.toString().trim();
        if (!text.isEmpty()) {
            RoleplayKnowledgeEntry.Section section = new RoleplayKnowledgeEntry.Section();
            section.title = "概述";
            section.text = text;
            entry.sections.add(0,section);
        }
    }

    /**
     * 读取文件头并返回正文起始行号，没有文件头时正文从第一行开始
     */
    private Header readHeader(File file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file.toPath(),StandardCharsets.UTF_8);
        } catch (Exception e) {
            plugin.getLogger().sendWarn("[知识库] 读取失败："+file.getName()+" "+e.getMessage());
            return null;
        }
        if (!lines.isEmpty()) {
            lines.set(0,stripBom(lines.get(0)));
        }
        Header header = new Header();
        header.lines = lines;
        int index = 0;
        while (index < lines.size() && lines.get(index).trim().isEmpty()) index++;
        header.bodyStart = index;
        if (index >= lines.size() || !"---".equals(lines.get(index).trim())) {
            plugin.getLogger().sendWarn("[知识库] "+file.getName()+" 缺少文件头");
            return header;
        }
        index++;
        while (index < lines.size() && !"---".equals(lines.get(index).trim())) {
            String line = lines.get(index).trim();
            int split = line.indexOf(':');
            if (split > 0) {
                header.fields.put(line.substring(0,split).trim().toLowerCase(Locale.ROOT),
                        line.substring(split + 1).trim());
            }
            index++;
        }
        if (index < lines.size()) index++;
        header.bodyStart = index;
        return header;
    }

    private List<Match> retrieve(RoleplayKnowledgeLibrary library,String query,int limit) {
        List<Match> result = new ArrayList<>();
        if (library == null || query == null || limit <= 0) return result;
        String normalizedQuery = SpeechCorpusEntry.normalize(query);
        if (normalizedQuery.length() < 2) return result;
        Set<String> queryBigrams = SpeechCorpusEntry.bigrams(normalizedQuery);
        if (queryBigrams.isEmpty()) return result;
        int totalSections = Math.max(1,library.sectionCount());
        for (RoleplayKnowledgeEntry entry : library.entries) {
            if (entry.spoiler > config.knowledgeSpoilerLevel) continue;
            boolean locked = entry.matchesEntity(normalizedQuery);
            List<ScoredSection> scoredSections = new ArrayList<>();
            for (RoleplayKnowledgeEntry.Section section : entry.sections) {
                double score = sectionScore(section,queryBigrams,totalSections,library)
                        + entryBoost(entry,section,normalizedQuery);
                if (score < config.knowledgeMinScore) continue;
                scoredSections.add(new ScoredSection(section,score));
            }
            //命中实体但没有任何小节达标时，只注入摘要，至少让角色知道这是谁
            if (scoredSections.isEmpty() && !locked) continue;
            Collections.sort(scoredSections,new Comparator<ScoredSection>() {
                @Override
                public int compare(ScoredSection left,ScoredSection right) {
                    return Double.compare(right.score,left.score);
                }
            });
            int keep = Math.max(1,config.knowledgeMaxSectionsPerEntry);
            List<ScoredSection> sections = new ArrayList<>();
            double total = 0;
            for (int i = 0; i < scoredSections.size() && i < keep; i++) {
                ScoredSection scored = scoredSections.get(i);
                sections.add(scored);
                total += scored.score;
            }
            if (locked) total += 5.0;
            result.add(new Match(entry,sections,total,locked));
        }
        //命中实体时只保留被锁定的条目，避免问小绿却把小绿的属性套到别人身上
        boolean hasLocked = false;
        for (Match match : result) {
            if (match.locked) {
                hasLocked = true;
                break;
            }
        }
        if (hasLocked) {
            List<Match> lockedOnly = new ArrayList<>();
            for (Match match : result) {
                if (match.locked) lockedOnly.add(match);
            }
            result = lockedOnly;
        }
        Collections.sort(result,new Comparator<Match>() {
            @Override
            public int compare(Match left,Match right) {
                return Double.compare(right.score,left.score);
            }
        });
        if (result.size() > limit) return new ArrayList<>(result.subList(0,limit));
        return result;
    }

    private double sectionScore(RoleplayKnowledgeEntry.Section section,Set<String> queryBigrams,
                                int totalSections,RoleplayKnowledgeLibrary library) {
        if (section.bigrams.isEmpty()) return 0;
        double raw = 0;
        for (String bigram : queryBigrams) {
            if (!section.bigrams.contains(bigram)) continue;
            int frequency = library.documentFrequency(bigram);
            raw += Math.log(1.0 + (double)totalSections / (double)(1 + frequency));
        }
        return raw <= 0 ? 0 : raw;
    }

    private double entryBoost(RoleplayKnowledgeEntry entry,RoleplayKnowledgeEntry.Section section,
                              String normalizedQuery) {
        double boost = 0;
        for (String keyword : entry.keywords) {
            String value = SpeechCorpusEntry.normalize(keyword);
            if (value.length() >= 2 && normalizedQuery.contains(value)) boost += 1.0;
        }
        for (String tag : entry.tags) {
            String value = SpeechCorpusEntry.normalize(tag);
            if (value.length() >= 2 && normalizedQuery.contains(value)) boost += 0.6;
        }
        String title = SpeechCorpusEntry.normalize(section.title);
        if (title.length() >= 2 && normalizedQuery.contains(title)) boost += 2.0;
        boost += (entry.priority - 60) * 0.02;
        return boost;
    }

    private String flatten(String text) {
        if (text == null) return "";
        return text.replaceAll("\\s+"," ").trim();
    }

    private String shortText(String text,int maxChars) {
        String value = text == null ? "" : text.replaceAll("\\s+"," ").trim();
        if (value.length() <= maxChars) return value;
        return value.substring(0,maxChars)+"...";
    }

    private List<String> splitList(String value) {
        List<String> result = new ArrayList<>();
        if (value == null) return result;
        String[] parts = value.split(",");
        for (String part : parts) {
            String item = part == null ? "" : part.trim();
            if (!item.isEmpty() && !result.contains(item)) result.add(item);
        }
        return result;
    }

    private int parseInt(String value,int defaultValue) {
        if (value == null || value.trim().isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String value(Map<String,String> fields,String key,String defaultValue) {
        String value = fields.get(key);
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }

    private String stripBom(String line) {
        if (line == null) return "";
        return line.startsWith("\uFEFF") ? line.substring(1) : line;
    }

    private static class Header {
        private final Map<String,String> fields = new LinkedHashMap<>();
        private List<String> lines = new ArrayList<>();
        private int bodyStart = 0;
    }

    private static class ScoredSection {
        private final RoleplayKnowledgeEntry.Section section;
        private final double score;

        private ScoredSection(RoleplayKnowledgeEntry.Section section,double score) {
            this.section = section;
            this.score = score;
        }
    }

    private static class Match {
        private final RoleplayKnowledgeEntry entry;
        private final List<ScoredSection> sections;
        private final double score;
        private final boolean locked;

        private Match(RoleplayKnowledgeEntry entry,
                      List<ScoredSection> sections,
                      double score,boolean locked) {
            this.entry = entry;
            this.sections = sections;
            this.score = score;
            this.locked = locked;
        }
    }
}
