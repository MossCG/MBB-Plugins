package org.moboxlab.mbb.vision;

import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Plugin;
import org.moboxlab.moboxbot.API.PluginService;

import java.util.Base64;

/**
 * 识图公共服务
 */
public class VisionService implements PluginService {
    private final Plugin plugin;
    private final VisionCache cache;
    private final VisionAnalyzer analyzer;

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
        int promptVersion = Math.max(2,plugin.getConfig().getInt("promptVersion",2));
        boolean cacheEnable = plugin.getConfig().getBoolean("cacheEnable",true);
        boolean force = params.getBooleanValue("force");
        String fileUnique = safe(params.getString("fileUnique"));

        if (cacheEnable && !force && !fileUnique.isEmpty()) {
            JSONObject row = cache.findByFileUnique(fileUnique,kind,promptVersion);
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
            JSONObject row = cache.findByHash(image.sha256,kind,promptVersion);
            if (row != null) {
                cache.touch(row);
                logHit(kind,row);
                return cache.toResult(row,true);
            }
        }

        PluginService ai = plugin.getServer().getPluginManager().getService("MBB-AI");
        if (ai == null) return error("MBB-AI 未启用");
        String profile = safe(params.getString("profile"));
        if (profile.isEmpty()) profile = plugin.getConfig().getString("aiProfile","default");
        int maxTokens = Math.max(4000,plugin.getConfig().getInt("maxTokens",4000));
        JSONObject result = analyzer.analyze(ai,profile,maxTokens,image,kind,
                safe(params.getString("context")));
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
                    safe(result.getString("model")),promptVersion);
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
