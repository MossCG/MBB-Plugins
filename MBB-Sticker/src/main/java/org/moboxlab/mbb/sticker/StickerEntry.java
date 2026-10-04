package org.moboxlab.mbb.sticker;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 表情包条目
 */
public class StickerEntry {
    public String id = "";
    public String file = "";
    public List<String> tags = new ArrayList<>();
    public double weight = 1.0;
    public boolean enabled = true;
    public long addedAt = 0L;
    public String source = "receive";
    public String description = "";
    public String hash = "";
    public String fileUnique = "";
    public int visionVersion = 0;
    public long lastSeenAt = 0L;
    public int hitCount = 0;

    public static StickerEntry fromJson(JSONObject json) {
        StickerEntry entry = new StickerEntry();
        if (json == null) return entry;
        entry.id = safe(json.getString("id"));
        entry.file = safe(json.getString("file"));
        entry.weight = json.get("weight") == null ? 1.0 : json.getDoubleValue("weight");
        entry.enabled = json.get("enabled") == null || json.getBooleanValue("enabled");
        entry.addedAt = json.getLongValue("addedAt");
        entry.source = safe(json.getString("source"));
        entry.description = safe(json.getString("description"));
        entry.hash = safe(json.getString("hash"));
        entry.fileUnique = safe(json.getString("fileUnique"));
        entry.visionVersion = json.getIntValue("visionVersion");
        entry.lastSeenAt = json.getLongValue("lastSeenAt");
        entry.hitCount = json.getIntValue("hitCount");
        JSONArray tagArray = json.getJSONArray("tags");
        if (tagArray != null) {
            for (Object tag : tagArray) {
                if (tag != null && !String.valueOf(tag).trim().isEmpty()) {
                    entry.tags.add(String.valueOf(tag).trim());
                }
            }
        }
        return entry;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject(true);
        json.put("id",id);
        json.put("file",file);
        JSONArray tagArray = new JSONArray();
        tagArray.addAll(tags);
        json.put("tags",tagArray);
        json.put("weight",weight);
        json.put("enabled",enabled);
        json.put("addedAt",addedAt);
        json.put("source",source);
        json.put("description",description);
        json.put("hash",hash);
        json.put("fileUnique",fileUnique);
        json.put("visionVersion",visionVersion);
        json.put("lastSeenAt",lastSeenAt);
        json.put("hitCount",hitCount);
        return json;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
