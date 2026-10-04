package org.moboxlab.mbb.vision;

import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Plugin;
import org.moboxlab.moboxbot.API.PluginService;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.security.MessageDigest;

/**
 * 识图公共服务
 */
public class VisionService implements PluginService {
    private final Plugin plugin;
    private final VisionCache cache;
    private final VisionAnalyzer analyzer;
    private final Map<String,Object> inflightLocks = new ConcurrentHashMap<>();

    public VisionService(Plugin plugin,VisionCache cache) {
        this.plugin = plugin;
        this.cache = cache;
        this.analyzer = new VisionAnalyzer(plugin);
    }

    @Override
    public String getName() {
        return "MBB-Vision";
    }

    @Override
    public JSONObject call(String action,JSONObject params) {
        if (action == null) return error("缺少动作名");
        if ("describe".equalsIgnoreCase(action)) return describe(params);
        if ("dataUri".equalsIgnoreCase(action)) return dataUri(params);
        if ("stats".equalsIgnoreCase(action)) return cache.stats();
        if ("clear".equalsIgnoreCase(action)) return clear();
        if ("reload".equalsIgnoreCase(action)) return reload();
        return error("不支持的动作："+action);
    }

    private JSONObject describe(JSONObject params) {
        if (!plugin.getConfig().getBoolean("enable",true)) return error("识图服务当前已关闭");
        if (params == null) return error("缺少识图参数");
        String kind = safe(params.getString("kind"));
        if (kind.isEmpty()) kind = "image";
        int promptVersion = Math.max(3,plugin.getConfig().getInt("promptVersion",3));
        boolean cacheEnable = plugin.getConfig().getBoolean("cacheEnable",true);
        boolean force = params.getBooleanValue("force");
        String fileUnique = safe(params.getString("fileUnique"));
        String reference = safe(params.getString("reference"));
        String referenceHash = hashReference(reference);

        if (cacheEnable && !force && !fileUnique.isEmpty()) {
            JSONObject row = cache.findByFileUnique(fileUnique,kind,promptVersion,referenceHash);
            if (row != null) {
                cache.touch(row);
                logHit(kind,row);
                return cache.toResult(row,true);
            }
        }

        VisionImageSource.ImageData image;
        try {
            image = VisionImageSource.load(plugin,params);
        } catch (Exception e) {
            return error("图片读取失败："+e.getMessage());
        }
        if (cacheEnable && !force) {
            JSONObject row = cache.findByHash(image.sha256,kind,promptVersion,referenceHash);
            if (row != null) {
                cache.touch(row);
                logHit(kind,row);
                return cache.toResult(row,true);
            }
        }
        String profile = safe(params.getString("profile"));
        if (profile.isEmpty()) profile = plugin.getConfig().getString("aiProfile","default");
        if (force) {
            return describeRemote(params,image,fileUnique,kind,profile,reference,promptVersion,false);
        }
        String lockKey = kind+"|"+image.sha256+"|"+promptVersion+"|"+referenceHash;
        Object lock = inflightLocks.get(lockKey);
        if (lock == null) {
            Object newLock = new Object();
            Object existing = inflightLocks.putIfAbsent(lockKey,newLock);
            lock = existing == null ? newLock : existing;
        }
        synchronized (lock) {
            try {
                if (cacheEnable) {
                    JSONObject row = cache.findByHash(image.sha256,kind,promptVersion,referenceHash);
                    if (row != null) {
                        cache.touch(row);
                        logHit(kind,row);
                        return cache.toResult(row,true);
                    }
                }
                return describeRemote(params,image,fileUnique,kind,profile,reference,promptVersion,cacheEnable);
            } finally {
                inflightLocks.remove(lockKey,lock);
            }
        }
    }

    private JSONObject describeRemote(JSONObject params,VisionImageSource.ImageData image,
                                      String fileUnique,String kind,String profile,
                                      String reference,int promptVersion,boolean cacheEnable) {
        PluginService ai = plugin.getServer().getPluginManager().getService("MBB-AI");
        if (ai == null) return error("MBB-AI 未启用");
        int maxTokens = plugin.getConfig().getInt("maxTokens",4000);
        if ("image".equalsIgnoreCase(kind)) {
            maxTokens = Math.max(12000,maxTokens);
        } else {
            maxTokens = Math.max(4000,maxTokens);
        }
        JSONObject result = analyzer.analyze(ai,profile,maxTokens,image,kind,
                safe(params.getString("context")),reference);
        if (result == null || !result.getBooleanValue("status")) {
            return error(result == null ? "识图失败" : safe(result.getString("message")));
        }
        result.put("sha256",image.sha256);
        result.put("fileUnique",fileUnique);
        result.put("kind",kind);
        result.put("cached",false);
        result.put("promptVersion",promptVersion);
        if (cacheEnable) {
            cache.save(fileUnique,image.sha256,kind,result,profile,
                    safe(result.getString("model")),hashReference(reference),promptVersion);
        }
        logResult(kind,result);
        return result;
    }

    private JSONObject clear() {
        int count = cache.clear();
        JSONObject result = new JSONObject(true);
        result.put("status",true);
        result.put("count",count);
        result.put("message","识图缓存已清空");
        return result;
    }

    private JSONObject dataUri(JSONObject params) {
        if (params == null) return error("缺少图片参数");
        try {
            VisionImageSource.ImageData image = VisionImageSource.load(plugin,params);
            JSONObject result = new JSONObject(true);
            result.put("status",true);
            result.put("sha256",image.sha256);
            result.put("fileUnique",safe(params.getString("fileUnique")));
            result.put("mime",image.mime);
            result.put("dataUri","data:"+image.mime+";base64,"
                    +Base64.getEncoder().encodeToString(image.bytes));
            return result;
        } catch (Exception e) {
            return error("图片读取失败："+e.getMessage());
        }
    }

    private JSONObject reload() {
        plugin.getConfig().load();
        return cache.stats();
    }

    private void logHit(String kind,JSONObject row) {
        plugin.getLogger().sendInfo("[识图] 命中缓存 kind="+kind+" hash="
                +shortHash(safe(row.getString("sha256")))+" fileUnique="
                +safe(row.getString("fileUnique"))+" 描述="
                +shortText(safe(row.getString("summary")),80)+" 标签="
                +safe(row.getString("emotionTags")));
    }

    private void logResult(String kind,JSONObject result) {
        plugin.getLogger().sendInfo("[识图] AI识别 kind="+kind+" hash="
                +shortHash(safe(result.getString("sha256")))+" fileUnique="
                +safe(result.getString("fileUnique"))+" 描述="
                +shortText(safe(result.getString("summary")),80)+" 标签="
                +safe(result.getString("emotionTags"))+" OCR="
                +shortText(safe(result.getString("ocr")),60));
    }

    private String shortHash(String hash) {
        if (hash == null || hash.length() <= 12) return safe(hash);
        return hash.substring(0,12);
    }

    private String hashReference(String reference) {
        String value = safe(reference);
        if (value.isEmpty()) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes("UTF-8"));
            StringBuilder builder = new StringBuilder();
            for (byte item : bytes) builder.append(String.format("%02x",item & 0xff));
            return builder.substring(0,16);
        } catch (Exception e) {
            return String.valueOf(value.hashCode());
        }
    }

    private String shortText(String value,int maxLength) {
        if (value == null) return "";
        String text = value.replace("\r"," ").replace("\n"," ").trim();
        if (text.length() <= maxLength) return text;
        return text.substring(0,maxLength)+"...";
    }

    private JSONObject error(String message) {
        JSONObject result = new JSONObject(true);
        result.put("status",false);
        result.put("message",message);
        return result;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
