package org.moboxlab.mbb.sticker;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSON;
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
            boolean migrated = false;
            if (array != null) {
                for (Object item : array) {
                    if (!(item instanceof JSONObject)) continue;
                    StickerEntry entry = StickerEntry.fromJson((JSONObject)item);
                    if (entry.id.isEmpty() || entry.file.isEmpty()) continue;
                    //旧版本存的是 file:// 绝对路径，这里统一转成相对插件目录的路径
                    String stored = migrateStoredPath(entry.file);
                    if (!stored.equals(entry.file)) {
                        entry.file = stored;
                        migrated = true;
                    }
                    entries.add(entry);
                }
            }
            rebuildTags();
            if (migrated) {
                plugin.getLogger().sendInfo("[表情包] 已把绝对路径转换为相对路径，共 "+entries.size()+" 条");
                save();
            }
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
            Files.write(file.toPath(),JSON.toJSONString(root,true).getBytes(StandardCharsets.UTF_8));
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
        entry.file = storePath(file);
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
            File file = resolveFile(entry.file);
            if (file != null && file.exists()) Files.deleteIfExists(file.toPath());
        } catch (Exception e) {
            plugin.getLogger().sendWarn("删除表情包文件失败："+e.getMessage());
        }
        rebuildTags();
        save();
        return true;
    }

    public synchronized StickerEntry random(List<String> tags,Set<String> avoid) {
        List<StickerEntry> candidates = filterCandidates(tags,avoid);
        if (candidates.isEmpty()) return null;
        double penalty = usePenalty();
        double total = 0;
        for (StickerEntry entry : candidates) total += effectiveWeight(entry,penalty);
        double value = ThreadLocalRandom.current().nextDouble(total);
        double current = 0;
        for (StickerEntry entry : candidates) {
            current += effectiveWeight(entry,penalty);
            if (value <= current) return entry;
        }
        return candidates.get(candidates.size() - 1);
    }

    public synchronized List<StickerEntry> candidates(List<String> tags,Set<String> avoid,int limit) {
        List<StickerEntry> remaining = filterCandidates(tags,avoid);
        List<StickerEntry> result = new ArrayList<>();
        int target = Math.max(1,limit);
        double penalty = usePenalty();
        while (!remaining.isEmpty() && result.size() < target) {
            double total = 0;
            for (StickerEntry entry : remaining) total += effectiveWeight(entry,penalty);
            double value = ThreadLocalRandom.current().nextDouble(total);
            double current = 0;
            int selected = remaining.size() - 1;
            for (int i = 0; i < remaining.size(); i++) {
                current += effectiveWeight(remaining.get(i),penalty);
                if (value <= current) {
                    selected = i;
                    break;
                }
            }
            result.add(remaining.remove(selected));
        }
        return result;
    }

    private List<StickerEntry> filterCandidates(List<String> tags,Set<String> avoid) {
        List<StickerEntry> candidates = new ArrayList<>();
        List<String> wanted = normalizeTags(tags);
        for (StickerEntry entry : entries) {
            if (!entry.enabled) continue;
            if (avoid != null && avoid.contains(entry.id)) continue;
            if (!wanted.isEmpty() && !containsAny(entry.tags,wanted)) continue;
            candidates.add(entry);
        }
        return candidates;
    }

    private double usePenalty() {
        double penalty = 0.5;
        try {
            penalty = Double.parseDouble(plugin.getConfig().getString("usePenalty","0.5"));
        } catch (Exception ignored) {
        }
        if (penalty < 0) penalty = 0;
        return penalty;
    }

    /**
     * 发送次数越多权重越低，避免同一张表情包反复出现
     */
    private double effectiveWeight(StickerEntry entry,double penalty) {
        return Math.max(0.1,entry.weight) / (1.0 + Math.max(0,entry.useCount) * penalty);
    }

    public synchronized void markUsed(String id) {
        StickerEntry entry = get(id);
        if (entry == null) return;
        entry.useCount++;
        save();
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

    /**
     * 存盘用的路径：在插件目录内就存相对路径，目录外的文件仍然存 file:// 绝对路径。
     * 相对路径让整个插件文件夹可以随便搬，图片不会集体失效。
     */
    public String storePath(File file) {
        if (file == null) return "";
        try {
            File root = new File(plugin.getDataFolder()).getCanonicalFile();
            File target = file.getCanonicalFile();
            String rootPath = root.getPath();
            String targetPath = target.getPath();
            if (targetPath.startsWith(rootPath + File.separator)) {
                return targetPath.substring(rootPath.length() + 1).replace("\\","/");
            }
        } catch (Exception ignored) {
        }
        return toFileUri(file);
    }

    /**
     * 把存盘路径还原成真实文件，兼容旧版本的 file:// 绝对路径
     */
    public File resolveFile(String stored) {
        if (stored == null || stored.trim().isEmpty()) return null;
        String value = stored.trim();
        if (value.startsWith("file://")) return fromFileUri(value);
        return new File(plugin.getDataFolder(),value.replace("/",File.separator));
    }

    /**
     * 给外部（OneBot 图片段）用的绝对 file:// 地址
     */
    public String toUri(String stored) {
        File file = resolveFile(stored);
        return file == null ? "" : toFileUri(file);
    }

    /**
     * 旧数据迁移：绝对路径能定位到文件就转相对路径；
     * 如果插件目录已经搬过家，就按文件名到 stickers/ 里找一份。
     */
    private String migrateStoredPath(String stored) {
        if (stored == null || !stored.startsWith("file://")) return stored == null ? "" : stored;
        File file = fromFileUri(stored);
        if (file == null) return stored;
        if (!file.exists()) {
            File fallback = new File(new File(plugin.getDataFolder(),"stickers"),file.getName());
            if (fallback.exists()) file = fallback;
        }
        String relative = storePath(file);
        return relative.isEmpty() ? stored : relative;
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
