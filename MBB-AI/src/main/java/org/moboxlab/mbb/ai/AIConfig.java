package org.moboxlab.mbb.ai;

import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Plugin;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MBB-AI 配置
 */
public class AIConfig {
    public boolean enable = true;
    public String defaultProfile = "default";
    public int maxConcurrent = 2;
    public int cacheSecond = 300;
    public int retryCount = 1;
    public boolean logRequestContent = false;

    private final Map<String,AIProfile> profileMap = new LinkedHashMap<>();

    public static AIConfig load(Plugin plugin) {
        AIConfig config = new AIConfig();
        config.enable = plugin.getConfig().getBoolean("enable",true);
        config.defaultProfile = plugin.getConfig().getString("defaultProfile","default");
        config.maxConcurrent = plugin.getConfig().getInt("maxConcurrent",2);
        config.cacheSecond = plugin.getConfig().getInt("cacheSecond",300);
        config.retryCount = plugin.getConfig().getInt("retryCount",1);
        config.logRequestContent = plugin.getConfig().getBoolean("logRequestContent",false);
        if (config.maxConcurrent < 1) config.maxConcurrent = 1;
        if (config.cacheSecond < 0) config.cacheSecond = 0;
        if (config.retryCount < 0) config.retryCount = 0;
        if (config.defaultProfile == null || config.defaultProfile.trim().isEmpty()) {
            config.defaultProfile = "default";
        }
        config.loadProfiles(plugin);
        return config;
    }

    public AIProfile getProfile(String name) {
        String key = name == null || name.trim().isEmpty() ? defaultProfile : name.trim();
        return profileMap.get(key);
    }

    public List<String> getProfileNames() {
        return new ArrayList<>(profileMap.keySet());
    }

    public int getProfileCount() {
        return profileMap.size();
    }

    private void loadProfiles(Plugin plugin) {
        try {
            String path = plugin.getDataFolder()+"/profiles.json";
            String text = new String(Files.readAllBytes(Paths.get(path)),StandardCharsets.UTF_8);
            JSONObject root = JSONObject.parseObject(text);
            JSONObject profiles = root == null ? null : root.getJSONObject("profiles");
            if (profiles == null) {
                plugin.getLogger().sendWarn("profiles.json 缺少 profiles 节点！");
                return;
            }
            for (String key : profiles.keySet()) {
                JSONObject json = profiles.getJSONObject(key);
                if (json == null) continue;
                AIProfile profile = new AIProfile();
                profile.name = key;
                profile.provider = json.getString("provider") == null ? "openai" : json.getString("provider");
                profile.baseUrl = json.getString("baseUrl") == null ? "" : json.getString("baseUrl").trim();
                profile.apiKey = json.getString("apiKey") == null ? "" : json.getString("apiKey").trim();
                profile.model = json.getString("model") == null ? "" : json.getString("model").trim();
                profile.temperature = json.getDoubleValue("temperature");
                if (json.get("temperature") == null) profile.temperature = 0.7;
                profile.maxTokens = json.getIntValue("maxTokens");
                if (profile.maxTokens <= 0) profile.maxTokens = 1024;
                profile.timeoutSeconds = json.getIntValue("timeoutSeconds");
                if (profile.timeoutSeconds <= 0) profile.timeoutSeconds = 60;
                profile.reasoningEffort = json.getString("reasoningEffort") == null
                        ? "" : json.getString("reasoningEffort").trim();
                JSONObject headers = json.getJSONObject("headers");
                if (headers != null) {
                    for (String header : headers.keySet()) {
                        String value = headers.getString(header);
                        if (header == null || header.trim().isEmpty() || value == null) continue;
                        profile.headers.put(header.trim(),value);
                    }
                }
                profileMap.put(key,profile);
            }
        } catch (Exception e) {
            plugin.getLogger().sendWarn("读取 profiles.json 失败："+e.getMessage());
        }
    }
}
