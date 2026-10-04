package org.moboxlab.mbb.vision;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Plugin;
import org.moboxlab.moboxbot.API.Storage.StorageService;

import java.util.List;

/**
 * 识图缓存
 */
public class VisionCache {
    private static final String TABLE = "plugin_mbb_vision_cache";
    private final Plugin plugin;

    public VisionCache(Plugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        storage().update("CREATE TABLE IF NOT EXISTS `"+TABLE+"` ("
                + "`ID` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`cacheKey` TEXT NOT NULL DEFAULT '',"
                + "`fileUnique` TEXT NOT NULL DEFAULT '',"
                + "`sha256` TEXT NOT NULL DEFAULT '',"
                + "`kind` TEXT NOT NULL DEFAULT '',"
                + "`summary` TEXT NOT NULL DEFAULT '',"
                + "`ocr` TEXT NOT NULL DEFAULT '',"
                + "`emotionTags` TEXT NOT NULL DEFAULT '[]',"
                + "`visualTags` TEXT NOT NULL DEFAULT '[]',"
                + "`description` TEXT NOT NULL DEFAULT '',"
                + "`profile` TEXT NOT NULL DEFAULT '',"
                + "`model` TEXT NOT NULL DEFAULT '',"
                + "`referenceHash` TEXT NOT NULL DEFAULT '',"
                + "`promptVersion` INTEGER NOT NULL DEFAULT 0,"
                + "`createdAt` INTEGER NOT NULL DEFAULT 0,"
                + "`lastHitAt` INTEGER NOT NULL DEFAULT 0,"
                + "`hitCount` INTEGER NOT NULL DEFAULT 0"
                + ")");
        storage().update("CREATE UNIQUE INDEX IF NOT EXISTS `idx_mbb_vision_cache_key` ON `"+TABLE+"` (`cacheKey`)");
        storage().update("CREATE INDEX IF NOT EXISTS `idx_mbb_vision_cache_unique` ON `"+TABLE+"` (`fileUnique`)");
        storage().update("CREATE INDEX IF NOT EXISTS `idx_mbb_vision_cache_hash` ON `"+TABLE+"` (`sha256`)");
        ensureColumn("referenceHash","TEXT NOT NULL DEFAULT ''");
    }

    public JSONObject findByFileUnique(String fileUnique,String kind,int promptVersion,String referenceHash) {
        if (fileUnique == null || fileUnique.trim().isEmpty()) return null;
        return storage().queryOne("SELECT * FROM `"+TABLE+"` WHERE `fileUnique`=? AND `kind`=? AND `promptVersion`=? "
                        + "AND `referenceHash`=? "
                        + "ORDER BY `lastHitAt` DESC LIMIT 1",
                fileUnique.trim(),safe(kind),promptVersion,safe(referenceHash));
    }

    public JSONObject findByHash(String sha256,String kind,int promptVersion,String referenceHash) {
        if (sha256 == null || sha256.trim().isEmpty()) return null;
        return storage().queryOne("SELECT * FROM `"+TABLE+"` WHERE `sha256`=? AND `kind`=? AND `promptVersion`=? "
                        + "AND `referenceHash`=? "
                        + "ORDER BY `lastHitAt` DESC LIMIT 1",
                sha256.trim(),safe(kind),promptVersion,safe(referenceHash));
    }

    public void save(String fileUnique,String sha256,String kind,JSONObject data,
                     String profile,String model,String referenceHash,int promptVersion) {
        if (data == null || sha256 == null || sha256.trim().isEmpty()) return;
        String cacheKey = cacheKey(kind,sha256,promptVersion,referenceHash);
        long now = System.currentTimeMillis();
        JSONObject existing = storage().queryOne("SELECT `ID` FROM `"+TABLE+"` WHERE `cacheKey`=?",cacheKey);
        if (existing == null) {
            storage().insert("INSERT INTO `"+TABLE+"` "
                            + "(`cacheKey`,`fileUnique`,`sha256`,`kind`,`summary`,`ocr`,`emotionTags`,"
                            + "`visualTags`,`description`,`profile`,`model`,`referenceHash`,`promptVersion`,`createdAt`,"
                            + "`lastHitAt`,`hitCount`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    cacheKey,safe(fileUnique),sha256.trim(),safe(kind),
                    safe(data.getString("summary")),safe(data.getString("ocr")),
                    json(data.getJSONArray("emotionTags")),json(data.getJSONArray("visualTags")),
                    safe(data.getString("description")),safe(profile),safe(model),
                    safe(referenceHash),promptVersion,now,now,0);
        } else {
            storage().update("UPDATE `"+TABLE+"` SET `fileUnique`=?,`summary`=?,`ocr`=?,`emotionTags`=?,"
                            + "`visualTags`=?,`description`=?,`profile`=?,`model`=?,`referenceHash`=?,`createdAt`=? "
                            + "WHERE `cacheKey`=?",
                    safe(fileUnique),safe(data.getString("summary")),safe(data.getString("ocr")),
                    json(data.getJSONArray("emotionTags")),json(data.getJSONArray("visualTags")),
                    safe(data.getString("description")),safe(profile),safe(model),
                    safe(referenceHash),now,cacheKey);
        }
    }

    public void touch(JSONObject row) {
        if (row == null || row.getLongValue("ID") <= 0) return;
        storage().update("UPDATE `"+TABLE+"` SET `lastHitAt`=?,`hitCount`=`hitCount`+1 WHERE `ID`=?",
                System.currentTimeMillis(),row.getLongValue("ID"));
    }

    public int count() {
        JSONObject row = storage().queryOne("SELECT COUNT(*) AS `count` FROM `"+TABLE+"`");
        return row == null ? 0 : row.getIntValue("count");
    }

    public int clear() {
        return storage().update("DELETE FROM `"+TABLE+"`");
    }

    public JSONObject toResult(JSONObject row,boolean cached) {
        JSONObject result = new JSONObject(true);
        result.put("status",true);
        result.put("cached",cached);
        result.put("cacheKey",safe(row.getString("cacheKey")));
        result.put("fileUnique",safe(row.getString("fileUnique")));
        result.put("sha256",safe(row.getString("sha256")));
        result.put("kind",safe(row.getString("kind")));
        result.put("summary",safe(row.getString("summary")));
        result.put("ocr",safe(row.getString("ocr")));
        result.put("emotionTags",parseArray(row.getString("emotionTags")));
        result.put("visualTags",parseArray(row.getString("visualTags")));
        result.put("description",safe(row.getString("description")));
        result.put("profile",safe(row.getString("profile")));
        result.put("model",safe(row.getString("model")));
        result.put("referenceHash",safe(row.getString("referenceHash")));
        result.put("promptVersion",row.getIntValue("promptVersion"));
        result.put("hitCount",row.getIntValue("hitCount"));
        return result;
    }

    public JSONObject stats() {
        JSONObject result = new JSONObject(true);
        result.put("status",true);
        result.put("count",count());
        List<JSONObject> rows = storage().query("SELECT `kind`,COUNT(*) AS `count` FROM `"+TABLE+"` "
                + "GROUP BY `kind` ORDER BY `kind` ASC");
        JSONArray kinds = new JSONArray();
        if (rows != null) {
            for (JSONObject row : rows) {
                JSONObject item = new JSONObject(true);
                item.put("kind",safe(row.getString("kind")));
                item.put("count",row.getIntValue("count"));
                kinds.add(item);
            }
        }
        result.put("kinds",kinds);
        return result;
    }

    private String cacheKey(String kind,String sha256,int promptVersion,String referenceHash) {
        return safe(kind)+"|"+sha256.trim()+"|"+promptVersion+"|"+safe(referenceHash);
    }

    private void ensureColumn(String column,String definition) {
        List<JSONObject> columns = storage().query("PRAGMA table_info(`"+TABLE+"`)");
        if (columns != null) {
            for (JSONObject item : columns) {
                if (column.equalsIgnoreCase(safe(item.getString("name")))) return;
            }
        }
        storage().update("ALTER TABLE `"+TABLE+"` ADD COLUMN `"+column+"` "+definition);
    }

    private JSONArray parseArray(String text) {
        try {
            JSONArray array = JSONArray.parseArray(text);
            return array == null ? new JSONArray() : array;
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private String json(JSONArray array) {
        return array == null ? "[]" : array.toJSONString();
    }

    private StorageService storage() {
        return plugin.getServer().getStorage();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
