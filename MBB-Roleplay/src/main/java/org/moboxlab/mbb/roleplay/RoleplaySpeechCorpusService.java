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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 角色台词语料检索
 */
public class RoleplaySpeechCorpusService {
    private static final String[] MIDORI_TOPIC_KEYWORDS = {
            "画","美术","设计","配色","构图","画稿","草稿","赶稿","画笔"
    };
    private static final String[] MIDORI_TOPIC_TAGS = {
            "美术","角色设计","配色","构图","画画","设计","画稿","草稿","赶稿","画笔"
    };
    private static final String[] MIDORI_TOPIC_TEXT = {
            "画稿","草稿","赶稿","画笔"
    };
    private static final String[] MOMOI_TOPIC_KEYWORDS = {
            "剧本","剧情","第一幕","第二幕","第三幕","创作","写作","大纲","台词","幕"
    };
    private static final String[] MOMOI_TOPIC_TAGS = {
            "剧本","写作","创作","剧情","大纲","台词"
    };
    private static final String[] MOMOI_TOPIC_TEXT = {
            "第一幕","第二幕","第三幕","剧本","大纲"
    };
    private final Plugin plugin;
    private volatile RoleplayConfig config;
    private volatile List<SpeechCorpusEntry> entries = new ArrayList<>();
    private volatile String corpusPath = "";

    public RoleplaySpeechCorpusService(Plugin plugin,RoleplayConfig config) {
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

    public int count() {
        return entries.size();
    }

    public String getCorpusPath() {
        return corpusPath;
    }

    public String promptText(String query,String context) {
        return promptText(query,context,"","");
    }

    /**
     * 带情绪与场景提示的检索。
     * 情绪与场景是加成而不是过滤条件：语料里 neutral 占大多数，
     * 硬过滤会把可用台词砍掉大半，加成只影响同类候选之间的排序。
     */
    public String promptText(String query,String context,String emotionHint,String sceneHint) {
        List<SpeechCorpusEntry> selected = retrieve(query,context,emotionHint,sceneHint,
                config.speechRetrievalCount,config.speechRetrievalMaxChars);
        if (selected.isEmpty()) return "";
        StringBuilder builder = new StringBuilder(
                "参考语气示例（只参考表达方式，不要直接照抄，也不要沿用示例里的具体话题、职业内容或项目进度）：\n");
        for (SpeechCorpusEntry entry : selected) {
            builder.append("- ").append(entry.text).append("\n");
        }
        return builder.toString();
    }

    public JSONArray search(String query,int limit) {
        JSONArray result = new JSONArray();
        List<SpeechCorpusEntry> selected = retrieve(query,"","","",limit,Integer.MAX_VALUE);
        for (SpeechCorpusEntry entry : selected) {
            JSONObject item = new JSONObject(true);
            item.put("id",entry.id);
            item.put("text",entry.text);
            item.put("tags",entry.tags);
            item.put("emotion",entry.emotion);
            item.put("scene",entry.scene);
            item.put("weight",entry.weight);
            result.add(item);
        }
        return result;
    }

    public JSONObject stats() {
        JSONObject result = new JSONObject(true);
        result.put("enabled",config.speechCorpusEnable);
        result.put("count",entries.size());
        result.put("path",corpusPath);
        Set<String> tags = new LinkedHashSet<>();
        for (SpeechCorpusEntry entry : entries) tags.addAll(entry.tags);
        result.put("tagCount",tags.size());
        return result;
    }

    public boolean tooSimilar(String reply) {
        if (!config.speechCorpusEnable || entries.isEmpty()) return false;
        String normalized = SpeechCorpusEntry.normalize(reply);
        if (normalized.length() < config.speechSimilarityMinChars) return false;
        Set<String> bigrams = SpeechCorpusEntry.bigrams(normalized);
        for (SpeechCorpusEntry entry : entries) {
            if (dice(bigrams,entry.bigrams) >= config.speechSimilarityThreshold) return true;
        }
        return false;
    }

    private List<SpeechCorpusEntry> retrieve(String query,String context,String emotionHint,
                                             String sceneHint,int limit,int maxChars) {
        if (!config.speechCorpusEnable || entries.isEmpty() || limit <= 0) return new ArrayList<>();
        String queryText = SpeechCorpusEntry.normalize(query);
        String contextText = SpeechCorpusEntry.normalize(context);
        Set<String> queryBigrams = SpeechCorpusEntry.bigrams(queryText);
        Set<String> contextBigrams = SpeechCorpusEntry.bigrams(contextText);
        Set<String> emotions = splitHint(emotionHint);
        String scene = safe(sceneHint).trim();
        if (queryBigrams.isEmpty() && contextBigrams.isEmpty()
                && emotions.isEmpty() && scene.isEmpty()) return new ArrayList<>();

        List<ScoredEntry> scored = new ArrayList<>();
        for (SpeechCorpusEntry entry : entries) {
            //超过允许等级的剧透台词直接不参与检索
            if (entry.spoiler > config.speechSpoilerLevel) continue;
            double score = dice(queryBigrams,entry.bigrams);
            if (!contextBigrams.isEmpty()) {
                score = score * 0.8 + dice(contextBigrams,entry.bigrams) * 0.2;
            }
            for (String tag : entry.tags) {
                String tagText = SpeechCorpusEntry.normalize(tag);
                if (!tagText.isEmpty() && (queryText.contains(tagText) || contextText.contains(tagText))) {
                    score += 0.12;
                }
            }
            //情绪与场景命中给加成：让“什么场合说哪种话”参与排序，而不是只看字面相似
            if (!emotions.isEmpty() && emotions.contains(entry.emotion)) score += 0.2;
            if (!scene.isEmpty() && scene.equals(entry.scene)) score += 0.12;
            score += (entry.weight - 1.0) * 0.05;
            score *= topicPenalty(entry,queryText,contextText);
            if (score >= config.speechRetrievalMinScore) scored.add(new ScoredEntry(entry,score));
        }
        Collections.sort(scored,new Comparator<ScoredEntry>() {
            @Override
            public int compare(ScoredEntry left,ScoredEntry right) {
                return Double.compare(right.score,left.score);
            }
        });

        List<SpeechCorpusEntry> result = new ArrayList<>();
        //同一出处最多取一条，避免整批示例都来自同一段剧情、语气高度雷同
        Set<String> usedSources = new LinkedHashSet<>();
        int totalChars = 0;
        for (ScoredEntry scoredEntry : scored) {
            SpeechCorpusEntry entry = scoredEntry.entry;
            String source = safe(entry.source).trim();
            if (!source.isEmpty() && usedSources.contains(source)) continue;
            boolean tooSimilar = false;
            for (SpeechCorpusEntry selected : result) {
                if (dice(entry.bigrams,selected.bigrams) >= 0.85) {
                    tooSimilar = true;
                    break;
                }
            }
            if (tooSimilar) continue;
            if (totalChars + entry.text.length() > maxChars && !result.isEmpty()) break;
            result.add(entry);
            if (!source.isEmpty()) usedSources.add(source);
            totalChars += entry.text.length();
            if (result.size() >= limit) break;
        }
        return result;
    }

    /**
     * 日常话题下降低角色职业/项目类语料的召回权重，避免每轮都被画笔或剧本带偏。
     */
    private double topicPenalty(SpeechCorpusEntry entry,String queryText,String contextText) {
        String key = personaKey(config.personaFile);
        String text = safe(queryText) + safe(contextText);
        if ("midori".equals(key) && !containsAny(text,MIDORI_TOPIC_KEYWORDS)) {
            if (containsAnyTag(entry,MIDORI_TOPIC_TAGS) || containsAny(entry.normalized,MIDORI_TOPIC_TEXT)) {
                return 0.35;
            }
        }
        if ("momoi".equals(key) && !containsAny(text,MOMOI_TOPIC_KEYWORDS)) {
            if (containsAnyTag(entry,MOMOI_TOPIC_TAGS) || containsAny(entry.normalized,MOMOI_TOPIC_TEXT)) {
                return 0.45;
            }
        }
        return 1.0;
    }

    private boolean containsAny(String text,String... keywords) {
        String value = safe(text);
        for (String keyword : keywords) {
            if (value.contains(keyword)) return true;
        }
        return false;
    }

    private boolean containsAnyTag(SpeechCorpusEntry entry,String... keywords) {
        if (entry == null || entry.tags == null) return false;
        for (String tag : entry.tags) {
            if (containsAny(SpeechCorpusEntry.normalize(tag),keywords)) return true;
        }
        return false;
    }

    /**
     * 把逗号分隔的情绪提示拆成集合
     */
    private Set<String> splitHint(String value) {
        Set<String> result = new LinkedHashSet<>();
        String text = safe(value).trim();
        if (text.isEmpty()) return result;
        for (String item : text.split(",")) {
            String label = item == null ? "" : item.trim();
            if (!label.isEmpty()) result.add(label);
        }
        return result;
    }

    private void load() {
        entries = new ArrayList<>();
        corpusPath = "";
        if (!config.speechCorpusEnable) return;
        String key = personaKey(config.personaFile);
        File file = new File(plugin.getDataFolder(),
                config.speechCorpusDirectory+"/speech-corpus-"+key+".jsonl");
        corpusPath = file.getAbsolutePath();
        if (!file.exists()) {
            plugin.getLogger().sendInfo("[语料] 未找到 "+corpusPath+"，跳过台词检索");
            return;
        }
        try {
            List<String> lines = Files.readAllLines(file.toPath(),StandardCharsets.UTF_8);
            int lineNumber = 0;
            for (String line : lines) {
                lineNumber++;
                String text = line == null ? "" : line.trim();
                if (text.isEmpty() || text.startsWith("#")) continue;
                JSONObject json;
                try {
                    json = JSONObject.parseObject(text);
                } catch (Exception e) {
                    plugin.getLogger().sendWarn("[语料] "+file.getName()+" 第 "+lineNumber+" 行不是合法 JSON");
                    continue;
                }
                if (json == null) continue;
                SpeechCorpusEntry entry = new SpeechCorpusEntry();
                entry.id = safe(json.getString("id"));
                entry.role = safe(json.getString("role"));
                entry.text = safe(json.getString("text")).trim();
                entry.emotion = safe(json.getString("emotion"));
                entry.scene = safe(json.getString("scene"));
                entry.source = safe(json.getString("source"));
                entry.weight = json.get("weight") == null ? 1.0 : json.getDoubleValue("weight");
                entry.spoiler = json.getIntValue("spoiler");
                JSONArray tags = json.getJSONArray("tags");
                if (tags != null) {
                    for (Object tag : tags) {
                        if (tag != null && !String.valueOf(tag).trim().isEmpty()) {
                            entry.tags.add(String.valueOf(tag).trim());
                        }
                    }
                }
                if (entry.text.isEmpty()) continue;
                if (entry.weight <= 0) entry.weight = 1.0;
                if (entry.emotion.isEmpty()) entry.emotion = "neutral";
                if (entry.scene.isEmpty()) entry.scene = "group_chat";
                entry.buildIndex();
                entries.add(entry);
            }
            plugin.getLogger().sendInfo("[语料] 已加载 "+entries.size()+" 条台词："+corpusPath);
        } catch (Exception e) {
            plugin.getLogger().sendWarn("[语料] 读取失败："+e.getMessage());
        }
    }

    private String personaKey(String personaFile) {
        String value = safe(personaFile).toLowerCase();
        if (value.contains("momoi")) return "momoi";
        if (value.contains("midori")) return "midori";
        if (value.contains("aris") || value.contains("alice")) return "aris";
        return "default";
    }

    private double dice(Set<String> left,Set<String> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) return 0;
        int intersection = 0;
        for (String item : left) {
            if (right.contains(item)) intersection++;
        }
        return 2.0 * intersection / (left.size() + right.size());
    }

    private static class ScoredEntry {
        private final SpeechCorpusEntry entry;
        private final double score;

        private ScoredEntry(SpeechCorpusEntry entry,double score) {
            this.entry = entry;
            this.score = score;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
