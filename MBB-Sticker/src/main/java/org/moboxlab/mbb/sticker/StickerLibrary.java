package org.moboxlab.mbb.sticker;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Plugin;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * 平铺表情包库
 */
public class StickerLibrary {
    private final Plugin plugin;
    private final List<StickerEntry> entries = new ArrayList<>();
    private final Set<String> availableTags = new TreeSet<>();

    public StickerLibrary(Plugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        entries.clear();
        availableTags.clear();
        File file = new File(plugin.getDataFolder(),"stickers.json");
        if (!file.exists()) {
            save();
            return;
        }
        try {
            String text = new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);
            JSONObject root = JSONObject.parseObject(text);
            JSONArray array = root == null ? null : root.getJSONArray("stickers");
            if (array != null) {
                for (Object item : array) {
                    if (!(item instanceof JSONObject)) continue;
                    StickerEntry entry = StickerEntry.fromJson((JSONObject)item);
                    if (!entry.id.isEmpty() && !entry.file.isEmpty()) entries.add(entry);
                }
            }
            rebuildTags();
        } catch (Exception e) {
            plugin.getLogger().sendWarn("读取 stickers.json 失败："+e.getMessage());
        }
    }

    public void save() {
        try {
            File file = new File(plugin.getDataFolder(),"stickers.json");
            JSONObject root = new JSONObject(true);
            JSONArray array = new JSONArray();
            for (StickerEntry entry : entries) array.add(entry.toJson());
            root.put("stickers",array);
            Files.write(file.toPath(),root.toJSONString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            plugin.getLogger().sendWarn("保存 stickers.json 失败："+e.getMessage());
        }
    }

    public synchronized StickerEntry add(File file,List<String> tags,String source,String description) {
        return add(file,tags,source,description,"","",0);
    }

    public synchronized StickerEntry add(File file,List<String> tags,String source,String description,
                                         String hash,String fileUnique,int visionVersion) {
        StickerEntry entry = new StickerEntry();
        entry.id = newId();
        entry.file = toFileUri(file);
        entry.tags = normalizeTags(tags);
        entry.source = source == null ? "" : source;
        entry.description = description == null ? "" : description;
        entry.hash = hash == null ? "" : hash;
        entry.fileUnique = fileUnique == null ? "" : fileUnique;
        entry.visionVersion = visionVersion;
        entry.addedAt = System.currentTimeMillis();
        entry.lastSeenAt = entry.addedAt;
        entry.hitCount = 0;
        entries.add(entry);
        rebuildTags();
        save();
        return entry;
    }

    public synchronized boolean updateTags(String id,List<String> tags) {
        StickerEntry entry = get(id);
        if (entry == null) return false;
        entry.tags = normalizeTags(tags);
        rebuildTags();
        save();
        return true;
    }

    public synchronized boolean remove(String id) {
        StickerEntry entry = get(id);
        if (entry == null) return false;
        entries.remove(entry);
        try {
            File file = fromFileUri(entry.file);
            if (file != null && file.exists()) Files.deleteIfExists(file.toPath());
        } catch (Exception e) {
            plugin.getLogger().sendWarn("删除表情包文件失败："+e.getMessage());
        }
        rebuildTags();
        save();
        return true;
    }

    public synchronized StickerEntry random(List<String> tags,Set<String> avoid) {
        List<StickerEntry> candidates = new ArrayList<>();
        List<String> wanted = normalizeTags(tags);
        for (StickerEntry entry : entries) {
            if (!entry.enabled) continue;
            if (avoid != null && avoid.contains(entry.id)) continue;
            if (!wanted.isEmpty() && !containsAny(entry.tags,wanted)) continue;
            candidates.add(entry);
        }
        if (candidates.isEmpty()) return null;
        double total = 0;
        for (StickerEntry entry : candidates) total += Math.max(0.1,entry.weight);
        double value = ThreadLocalRandom.current().nextDouble(total);
        double current = 0;
        for (StickerEntry entry : candidates) {
            current += Math.max(0.1,entry.weight);
            if (value <= current) return entry;
        }
        return candidates.get(candidates.size() - 1);
    }

    public StickerEntry get(String id) {
        if (id == null) return null;
        for (StickerEntry entry : entries) if (id.equals(entry.id)) return entry;
        return null;
    }

    public synchronized StickerEntry findByHash(String hash) {
        if (hash == null || hash.trim().isEmpty()) return null;
        for (StickerEntry entry : entries) {
            if (hash.equals(entry.hash)) return entry;
        }
        return null;
    }

    public synchronized StickerEntry findByFileUnique(String fileUnique) {
        if (fileUnique == null || fileUnique.trim().isEmpty()) return null;
        for (StickerEntry entry : entries) {
            if (fileUnique.equals(entry.fileUnique)) return entry;
        }
        return null;
    }

    public synchronized void touch(StickerEntry entry,String fileUnique) {
        if (entry == null) return;
        entry.lastSeenAt = System.currentTimeMillis();
        entry.hitCount++;
        if (entry.fileUnique == null || entry.fileUnique.trim().isEmpty()) {
            entry.fileUnique = fileUnique == null ? "" : fileUnique;
        }
        save();
    }

    public List<StickerEntry> all() {
        return new ArrayList<>(entries);
    }

    public Set<String> availableTags() {
        return new TreeSet<>(availableTags);
    }

    public int size() {
        return entries.size();
    }

    public List<String> normalizeTags(List<String> tags) {
        List<String> result = new ArrayList<>();
        if (tags == null) return result;
        Pattern pattern = Pattern.compile(plugin.getConfig().getString(
                "tagPattern","^[a-z][a-z0-9_]{1,31}$"));
        for (String tag : tags) {
            String value = normalizeTag(tag);
            if (value.isEmpty() || !pattern.matcher(value).matches() || result.contains(value)) continue;
            result.add(value);
        }
        Collections.sort(result);
        return result;
    }

    public String normalizeTag(String tag) {
        if (tag == null) return "";
        String value = tag.trim().toLowerCase()
                .replace(' ','_')
                .replace('-','_')
                .replaceAll("[^a-z0-9_]","")
                .replaceAll("_+","_")
                .replaceAll("^_+|_+$","");
        return value;
    }

    public static String toFileUri(File file) {
        String path = file.getAbsolutePath().replace("\\","/");
        if (!path.startsWith("/")) path = "/"+path;
        return "file://"+path;
    }

    public static File fromFileUri(String uri) {
        if (uri == null || uri.trim().isEmpty()) return null;
        try {
            if (uri.startsWith("file://")) return new File(new URI(uri));
        } catch (Exception ignored) {
        }
        return null;
    }

    private void rebuildTags() {
        availableTags.clear();
        for (StickerEntry entry : entries) {
            if (!entry.enabled) continue;
            availableTags.addAll(entry.tags);
        }
    }

    private boolean containsAny(List<String> source,List<String> wanted) {
        for (String tag : wanted) if (source.contains(tag)) return true;
        return false;
    }

    private String newId() {
        String time = new java.text.SimpleDateFormat("yyyyMMddHHmmss",java.util.Locale.CHINA)
                .format(new java.util.Date());
        for (int i = 0; i < 100; i++) {
            String id = "sticker-"+time+"-"+String.format("%04d",ThreadLocalRandom.current().nextInt(10000));
            if (get(id) == null) return id;
        }
        return "sticker-"+System.currentTimeMillis();
    }
}
