package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 本地网络用语词集。
 *
 * 默认文件在插件目录 network-slangs.json。消息命中时只注入少量相关词条，
 * 模型也可以调用 slang_search 主动查询，不把整份词表常驻上下文。
 */
public class RoleplaySlangLexicon {
    private static final String FILE_NAME = "network-slangs.json";

    private final RoleplayService service;
    private volatile List<Entry> entries = Collections.emptyList();
    private volatile int maxInjected = 4;
    private volatile int maxUsedPerReply = 2;
    private volatile long loadedAt;
    private volatile String loadError = "";

    public RoleplaySlangLexicon(RoleplayService service) {
        this.service = service;
    }

    public synchronized void reload() {
        File file = new File(service.plugin().getDataFolder(),FILE_NAME);
        try {
            if (!file.isFile()) throw new IllegalStateException("词集文件不存在："+file.getAbsolutePath());
            String text = new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);
            JSONObject json = JSONObject.parseObject(text);
            if (json == null) throw new IllegalStateException("词集不是合法 JSON");
            List<Entry> loaded = apply(json);
            entries = loaded;
            loadedAt = System.currentTimeMillis();
            loadError = "";
            service.plugin().getLogger().sendInfo("[网络用语] 已加载 "+loaded.size()
                    +" 条，最多自动注入 "+maxInjected+" 条");
        } catch (Exception e) {
            entries = Collections.emptyList();
            loadError = service.safe(e.getMessage());
            loadedAt = System.currentTimeMillis();
            service.plugin().getLogger().sendWarn("[网络用语] 词集加载失败："
                    +loadError+"，本轮不注入网络用语");
        }
    }

    static RoleplaySlangLexicon forTesting(JSONObject json) {
        RoleplaySlangLexicon lexicon = new RoleplaySlangLexicon(null);
        lexicon.entries = lexicon.apply(json);
        return lexicon;
    }

    public String promptFor(String message) {
        List<Entry> matched = matches(message,maxInjected);
        if (matched.isEmpty()) return "";
        StringBuilder builder = new StringBuilder("【本轮网络用语理解】\n")
                .append("下面只帮助理解当前消息，不是要求复述。只选当前语境真正合适的词，能理解不代表必须使用。\n");
        for (Entry entry : matched) {
            builder.append("- ").append(entry.label()).append("：").append(entry.meaning);
            if (!entry.usage.isEmpty()) builder.append(" ").append(entry.usage);
            if (!entry.avoid.isEmpty()) builder.append(" 避免：").append(entry.avoid);
            if ("high".equals(entry.risk)) builder.append(" 风险：只作识别，不参与起哄或扩散。");
            builder.append("\n");
        }
        builder.append("一轮最多使用 ").append(maxUsedPerReply)
                .append(" 个网络用语；不确定含义就短问，不要自造缩写或把缩写翻译成完整解释。\n");
        return builder.toString();
    }

    public JSONArray search(String query,int limit) {
        String text = normalize(query);
        JSONArray result = new JSONArray();
        if (text.isEmpty()) return result;
        int max = Math.max(1,Math.min(20,limit));
        for (Entry entry : entries) {
            if (result.size() >= max) break;
            if (entry.matchesQuery(text)) result.add(entry.toJson());
        }
        return result;
    }

    public JSONObject status() {
        JSONObject result = new JSONObject(true);
        result.put("file",new File(service.plugin().getDataFolder(),FILE_NAME).getAbsolutePath());
        result.put("entries",entries.size());
        result.put("maxInjected",maxInjected);
        result.put("maxUsedPerReply",maxUsedPerReply);
        result.put("loadedAt",loadedAt);
        result.put("error",loadError);
        return result;
    }

    int entryCount() {
        return entries.size();
    }

    private List<Entry> matches(String message,int limit) {
        List<Entry> result = new ArrayList<>();
        String text = message == null ? "" : message;
        if (text.trim().isEmpty()) return result;
        for (Entry entry : entries) {
            if (result.size() >= limit) break;
            if (entry.matches(text)) result.add(entry);
        }
        return result;
    }

    private List<Entry> apply(JSONObject json) {
        int configuredInjected = json == null ? 0 : json.getIntValue("maxInjected");
        maxInjected = configuredInjected <= 0 ? 4 : Math.max(1,Math.min(8,configuredInjected));
        int configuredUsed = json == null ? 0 : json.getIntValue("maxUsedPerReply");
        maxUsedPerReply = configuredUsed <= 0 ? 2 : Math.max(1,Math.min(2,configuredUsed));
        List<Entry> loaded = new ArrayList<>();
        JSONArray array = json == null ? null : json.getJSONArray("entries");
        if (array != null) {
            for (Object value : array) {
                if (!(value instanceof JSONObject)) continue;
                Entry entry = parseEntry((JSONObject)value);
                if (entry != null) loaded.add(entry);
            }
        }
        loaded.sort(new Comparator<Entry>() {
            @Override
            public int compare(Entry left,Entry right) {
                return Integer.compare(right.priority,left.priority);
            }
        });
        return loaded;
    }

    private static Entry parseEntry(JSONObject json) {
        String id = safe(json.getString("id")).trim();
        String meaning = safe(json.getString("meaning")).trim();
        if (id.isEmpty() || meaning.isEmpty()) return null;
        Entry entry = new Entry();
        entry.id = id;
        entry.category = safe(json.getString("category")).trim();
        entry.meaning = truncate(meaning,240);
        entry.usage = truncate(safe(json.getString("usage")).trim(),160);
        entry.avoid = truncate(safe(json.getString("avoid")).trim(),160);
        entry.risk = safe(json.getString("risk")).trim().toLowerCase(Locale.ROOT);
        if (entry.risk.isEmpty()) entry.risk = "low";
        entry.priority = json.getIntValue("priority");
        JSONArray triggers = json.getJSONArray("triggers");
        if (triggers != null) {
            for (Object value : triggers) {
                if (!(value instanceof JSONObject)) continue;
                JSONObject triggerJson = (JSONObject)value;
                String text = safe(triggerJson.getString("text")).trim();
                if (text.isEmpty()) continue;
                String mode = safe(triggerJson.getString("mode")).trim().toLowerCase(Locale.ROOT);
                if (mode.isEmpty()) mode = text.matches("[a-z0-9]+") ? "token" : "literal";
                entry.triggers.add(new Trigger(text,mode));
            }
        }
        return entry.triggers.isEmpty() ? null : entry;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String value,int maxChars) {
        String text = safe(value);
        return text.length() <= maxChars ? text : text.substring(0,maxChars);
    }

    private static final class Entry {
        private String id = "";
        private String category = "";
        private String meaning = "";
        private String usage = "";
        private String avoid = "";
        private String risk = "low";
        private int priority;
        private final List<Trigger> triggers = new ArrayList<>();

        private boolean matches(String text) {
            for (Trigger trigger : triggers) {
                if (trigger.matches(text)) return true;
            }
            return false;
        }

        private boolean matchesQuery(String query) {
            if (normalize(id).contains(query) || normalize(category).contains(query)
                    || normalize(meaning).contains(query)) return true;
            for (Trigger trigger : triggers) {
                String value = normalize(trigger.text);
                if (value.contains(query) || query.contains(value)) return true;
            }
            return false;
        }

        private String label() {
            Set<String> labels = new LinkedHashSet<>();
            for (Trigger trigger : triggers) labels.add(trigger.text);
            return String.join("/",labels)+(category.isEmpty() ? "" : "（"+category+"）");
        }

        private JSONObject toJson() {
            JSONObject result = new JSONObject(true);
            result.put("id",id);
            result.put("category",category);
            result.put("meaning",meaning);
            result.put("usage",usage);
            result.put("avoid",avoid);
            result.put("risk",risk);
            result.put("priority",priority);
            JSONArray values = new JSONArray();
            for (Trigger trigger : triggers) values.add(trigger.text);
            result.put("triggers",values);
            return result;
        }
    }

    private static final class Trigger {
        private final String text;
        private final String mode;
        private final Pattern pattern;

        private Trigger(String text,String mode) {
            this.text = text;
            this.mode = mode;
            this.pattern = compile(text,mode);
        }

        private boolean matches(String content) {
            if ("literal".equals(mode)) return normalize(content).contains(normalize(text));
            return pattern != null && pattern.matcher(content == null ? "" : content).find();
        }

        private static Pattern compile(String text,String mode) {
            String quoted = Pattern.quote(text.toLowerCase(Locale.ROOT));
            if ("token".equals(mode)) {
                return Pattern.compile("(?i)(^|[^a-z0-9])"+quoted+"([^a-z0-9]|$)");
            }
            if ("standalone".equals(mode)) {
                return Pattern.compile("(?i)(^|[\\s，。！？、,.!?])"+quoted
                        +"([\\s，。！？、,.!?]|$)");
            }
            return null;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
