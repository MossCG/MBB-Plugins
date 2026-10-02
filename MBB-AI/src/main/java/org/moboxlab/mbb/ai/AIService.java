package org.moboxlab.mbb.ai;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.PluginService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI 公共服务实现
 */
public class AIService implements PluginService {
    private final AIPlugin plugin;
    private volatile AIConfig config;
    private volatile Semaphore semaphore;
    private final Map<String,CacheEntry> cache = new LinkedHashMap<String,CacheEntry>(16,0.75f,true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String,CacheEntry> eldest) {
            return size() > 100;
        }
    };

    private final AtomicLong requestCount = new AtomicLong();
    private final AtomicLong successCount = new AtomicLong();
    private final AtomicLong failureCount = new AtomicLong();
    private final AtomicLong promptTokens = new AtomicLong();
    private final AtomicLong completionTokens = new AtomicLong();
    private final AtomicLong totalTokens = new AtomicLong();

    private static class CacheEntry {
        private final long expireTime;
        private final JSONObject result;

        private CacheEntry(long expireTime,JSONObject result) {
            this.expireTime = expireTime;
            this.result = result;
        }
    }

    public AIService(AIPlugin plugin,AIConfig config) {
        this.plugin = plugin;
        reload(config);
    }

    @Override
    public String getName() {
        return "MBB-AI";
    }

    @Override
    public JSONObject call(String action,JSONObject params) {
        if (action == null) return error("缺少 AI 动作名！","action",false);
        if ("status".equalsIgnoreCase(action)) return status();
        if ("usage".equalsIgnoreCase(action)) return usage();
        if ("reload".equalsIgnoreCase(action)) return reloadAction();
        if (!config.enable) return error("AI 服务当前已关闭！","disabled",false);
        if ("chat".equalsIgnoreCase(action)) return chat(params);
        if ("complete".equalsIgnoreCase(action)) return complete(params);
        return error("不支持的 AI 动作："+action,"action",false);
    }

    public void reload(AIConfig newConfig) {
        this.config = newConfig;
        this.semaphore = new Semaphore(newConfig.maxConcurrent,true);
    }

    private JSONObject chat(JSONObject params) {
        if (params == null) return error("chat 参数不能为空！","params",false);
        JSONArray messages = params.getJSONArray("messages");
        if (messages == null || messages.isEmpty()) {
            String prompt = params.getString("prompt");
            if (prompt == null || prompt.trim().isEmpty()) return error("messages 不能为空！","params",false);
            messages = new JSONArray();
            messages.add(message("user",prompt));
        }
        String profileName = params.getString("profile");
        AIProfile profile = config.getProfile(profileName);
        if (profile == null) {
            return error("没有找到 AI 模型配置："+(profileName == null ? config.defaultProfile : profileName),"profile",false);
        }
        requestCount.incrementAndGet();
        String cacheKey = cacheKey(profile,messages,params);
        JSONObject cached = getCache(cacheKey);
        if (cached != null) {
            successCount.incrementAndGet();
            return cached;
        }

        boolean acquired = false;
        try {
            acquired = semaphore.tryAcquire(profile.timeoutSeconds,TimeUnit.SECONDS);
            if (!acquired) {
                failureCount.incrementAndGet();
                return error("AI 服务当前并发已满，请稍后再试！","busy",true);
            }
            JSONObject result = callWithRetry(profile,messages,params);
            if (result.getBooleanValue("status")) {
                successCount.incrementAndGet();
                result.put("action","chat");
                result.put("profile",profile.name);
                result.put("cached",false);
                readUsage(result.getJSONObject("usage"));
                putCache(cacheKey,result);
            } else {
                failureCount.incrementAndGet();
            }
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failureCount.incrementAndGet();
            return error("AI 服务等待并发许可时被中断！","interrupted",true);
        } finally {
            if (acquired) semaphore.release();
        }
    }

    private JSONObject complete(JSONObject params) {
        if (params == null) return error("complete 参数不能为空！","params",false);
        String prompt = params.getString("prompt");
        if (prompt == null || prompt.trim().isEmpty()) return error("prompt 不能为空！","params",false);
        JSONObject chatParams = new JSONObject(true);
        chatParams.put("profile",params.getString("profile"));
        chatParams.put("temperature",params.get("temperature"));
        chatParams.put("maxTokens",params.get("maxTokens"));
        JSONArray messages = new JSONArray();
        messages.add(message("user",prompt));
        chatParams.put("messages",messages);
        JSONObject result = chat(chatParams);
        if (result.getBooleanValue("status")) result.put("action","complete");
        return result;
    }

    private JSONObject callWithRetry(AIProfile profile,JSONArray messages,JSONObject params) {
        int attempts = config.retryCount + 1;
        JSONObject result = null;
        for (int i = 0; i < attempts; i++) {
            if (!"openai".equalsIgnoreCase(profile.provider)) {
                return error("暂不支持 AI 提供方："+profile.provider,"provider",false);
            }
            result = OpenAIProvider.chat(profile,messages,params,config.logRequestContent,plugin.getLogger());
            if (result.getBooleanValue("status")) return result;
            if (!result.getBooleanValue("retryable")) return result;
            if (i + 1 < attempts) {
                try {
                    Thread.sleep(300L * (i + 1));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return result == null ? error("AI 服务没有返回结果！","unknown",true) : result;
    }

    private JSONObject status() {
        JSONObject result = new JSONObject(true);
        result.put("status",true);
        result.put("enable",config.enable);
        result.put("defaultProfile",config.defaultProfile);
        result.put("maxConcurrent",config.maxConcurrent);
        result.put("cacheSecond",config.cacheSecond);
        result.put("retryCount",config.retryCount);
        result.put("logRequestContent",config.logRequestContent);
        JSONArray profiles = new JSONArray();
        for (String name : config.getProfileNames()) {
            AIProfile profile = config.getProfile(name);
            JSONObject item = new JSONObject(true);
            item.put("name",name);
            item.put("provider",profile.provider);
            item.put("model",profile.model);
            item.put("baseUrl",profile.baseUrl);
            profiles.add(item);
        }
        result.put("profiles",profiles);
        return result;
    }

    private JSONObject usage() {
        JSONObject result = new JSONObject(true);
        result.put("status",true);
        result.put("requests",requestCount.get());
        result.put("successes",successCount.get());
        result.put("failures",failureCount.get());
        result.put("promptTokens",promptTokens.get());
        result.put("completionTokens",completionTokens.get());
        result.put("totalTokens",totalTokens.get());
        result.put("cacheSize",cacheSize());
        return result;
    }

    private JSONObject reloadAction() {
        plugin.reloadConfig();
        JSONObject result = status();
        result.put("message","AI 配置已重载！");
        return result;
    }

    private void readUsage(JSONObject usage) {
        if (usage == null) return;
        promptTokens.addAndGet(usage.getLongValue("promptTokens"));
        completionTokens.addAndGet(usage.getLongValue("completionTokens"));
        totalTokens.addAndGet(usage.getLongValue("totalTokens"));
    }

    private JSONObject getCache(String key) {
        if (config.cacheSecond <= 0) return null;
        synchronized (cache) {
            CacheEntry entry = cache.get(key);
            if (entry == null) return null;
            if (entry.expireTime < System.currentTimeMillis()) {
                cache.remove(key);
                return null;
            }
            JSONObject result = new JSONObject(true);
            result.putAll(entry.result);
            result.put("cached",true);
            return result;
        }
    }

    private void putCache(String key,JSONObject result) {
        if (config.cacheSecond <= 0) return;
        JSONObject copy = new JSONObject(true);
        copy.putAll(result);
        synchronized (cache) {
            cache.put(key,new CacheEntry(System.currentTimeMillis() + config.cacheSecond * 1000L,copy));
        }
    }

    private int cacheSize() {
        synchronized (cache) {
            return cache.size();
        }
    }

    private String cacheKey(AIProfile profile,JSONArray messages,JSONObject params) {
        double temperature = params != null && params.get("temperature") != null
                ? params.getDoubleValue("temperature") : profile.temperature;
        int maxTokens = params != null && params.getIntValue("maxTokens") > 0
                ? params.getIntValue("maxTokens") : profile.maxTokens;
        String sessionId = params == null ? "" : params.getString("sessionId");
        return profile.name+"|"+(sessionId == null ? "" : sessionId)+"|"
                +temperature+"|"+maxTokens+"|"+messages.toJSONString();
    }

    private JSONObject message(String role,String content) {
        JSONObject message = new JSONObject(true);
        message.put("role",role);
        message.put("content",content);
        return message;
    }

    private JSONObject error(String message,String type,boolean retryable) {
        JSONObject result = new JSONObject(true);
        result.put("status",false);
        result.put("message",message);
        result.put("errorType",type);
        result.put("retryable",retryable);
        return result;
    }
}
