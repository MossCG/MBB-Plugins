package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Plugin;
import org.moboxlab.moboxbot.API.PluginService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 表达候选向量检索。
 *
 * embedding 由 MBB-AI 的 embed 动作生成；服务不可用时由调用方保留原有文本顺序回退。
 */
public class RoleplayExpressionVectorService {
    private static class CacheEntry {
        final long expireTime;
        final EmbeddingResult result;

        CacheEntry(long expireTime,EmbeddingResult result) {
            this.expireTime = expireTime;
            this.result = result;
        }
    }

    private static class Scored {
        final JSONObject item;
        final double score;

        Scored(JSONObject item,double score) {
            this.item = item;
            this.score = score;
        }
    }

    public static class EmbeddingResult {
        public final boolean success;
        public final String model;
        public final JSONArray embedding;
        public final String message;

        private EmbeddingResult(boolean success,String model,JSONArray embedding,String message) {
            this.success = success;
            this.model = model == null ? "" : model;
            this.embedding = embedding;
            this.message = message == null ? "" : message;
        }
    }

    private final Plugin plugin;
    private volatile RoleplayConfig config;
    private volatile long embedRetryAfter = 0L;
    private final Map<String,CacheEntry> queryCache = new ConcurrentHashMap<>();

    public RoleplayExpressionVectorService(Plugin plugin,RoleplayConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void reload(RoleplayConfig config) {
        this.config = config;
        queryCache.clear();
    }

    public EmbeddingResult embed(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new EmbeddingResult(false,"",null,"输入为空");
        }
        long now = System.currentTimeMillis();
        if (embedRetryAfter > now) {
            return new EmbeddingResult(false,"",null,"embedding 服务失败冷却中");
        }
        PluginService ai = plugin.getServer().getPluginManager().getService("MBB-AI");
        if (ai == null) {
            markFailure();
            return new EmbeddingResult(false,"",null,"MBB-AI 未启用");
        }
        JSONObject params = new JSONObject(true);
        String profile = config.expressionEmbeddingProfile == null
                || config.expressionEmbeddingProfile.trim().isEmpty()
                ? config.aiProfile : config.expressionEmbeddingProfile.trim();
        params.put("profile",profile);
        params.put("input",text.trim());
        params.put("timeoutSeconds",config.expressionEmbeddingTimeoutSecond);
        params.put("retryCount",0);
        RoleplayAiMetadata.tag(params,"roleplay.expression.embed","expression-vector-v1");
        JSONObject result = ai.call("embed",params);
        if (result == null || !result.getBooleanValue("status")) {
            markFailure();
            return new EmbeddingResult(false,"",null,
                    result == null ? "embedding 无返回" : result.getString("message"));
        }
        JSONArray entries = result.getJSONArray("embeddings");
        if (entries == null || entries.isEmpty()) {
            markFailure();
            return new EmbeddingResult(false,"",null,"embedding 结果为空");
        }
        JSONObject first = entries.getJSONObject(0);
        JSONArray embedding = first == null ? null : first.getJSONArray("embedding");
        if (embedding == null || embedding.isEmpty()) {
            markFailure();
            return new EmbeddingResult(false,"",null,"embedding 向量为空");
        }
        embedRetryAfter = 0L;
        return new EmbeddingResult(true,result.getString("model"),embedding,"");
    }

    public List<JSONObject> rank(List<JSONObject> candidates,String query) {
        if (candidates == null || candidates.size() < 2) return candidates;
        if (!config.expressionVectorEnable || query == null || query.trim().length() < 2) {
            return candidates;
        }
        boolean hasVector = false;
        for (JSONObject item : candidates) {
            JSONArray vector = item.getJSONArray("embedding");
            if (vector != null && !vector.isEmpty()
                    && !safe(item.getString("embeddingModel")).isEmpty()) {
                hasVector = true;
                break;
            }
        }
        if (!hasVector) return candidates;
        EmbeddingResult queryResult = queryEmbedding(query);
        if (!queryResult.success) return candidates;
        List<Scored> scored = new ArrayList<>();
        for (JSONObject item : candidates) {
            if (!queryResult.model.equals(safe(item.getString("embeddingModel")))) continue;
            JSONArray vector = item.getJSONArray("embedding");
            if (vector == null || vector.isEmpty()) continue;
            double cosine = cosine(queryResult.embedding,vector);
            double lexical = lexicalSimilarity(query,item.getString("content"));
            double score = cosine * 0.8 + lexical * 0.2;
            if (score >= config.expressionVectorMinScore) scored.add(new Scored(item,score));
        }
        if (scored.isEmpty()) return candidates;
        Collections.sort(scored,new Comparator<Scored>() {
            @Override
            public int compare(Scored left,Scored right) {
                return Double.compare(right.score,left.score);
            }
        });
        List<JSONObject> result = new ArrayList<>();
        int limit = Math.min(Math.max(1,config.expressionVectorTopK),scored.size());
        for (int i = 0; i < limit; i++) {
            JSONObject copy = new JSONObject(true);
            copy.putAll(scored.get(i).item);
            copy.put("vectorScore",scored.get(i).score);
            result.add(copy);
        }
        return result;
    }

    public JSONArray parseVector(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        try {
            JSONArray values = JSONArray.parseArray(text);
            return values == null || values.isEmpty() ? null : values;
        } catch (Exception e) {
            return null;
        }
    }

    private EmbeddingResult queryEmbedding(String query) {
        String key = safe(query).trim().toLowerCase(Locale.ROOT);
        if (key.length() > 500) key = key.substring(0,500);
        if (key.isEmpty()) return new EmbeddingResult(false,"",null,"查询为空");
        long now = System.currentTimeMillis();
        CacheEntry cached = queryCache.get(key);
        if (cached != null && cached.expireTime > now) return cached.result;
        EmbeddingResult result = embed(query);
        long expire = now + Math.max(1,config.expressionVectorQueryCacheMinute) * 60000L;
        if (queryCache.size() > 200) queryCache.clear();
        queryCache.put(key,new CacheEntry(expire,result));
        return result;
    }

    static double cosine(JSONArray left,JSONArray right) {
        int size = Math.min(left.size(),right.size());
        if (size <= 0) return 0.0;
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < size; i++) {
            double a = left.getDoubleValue(i);
            double b = right.getDoubleValue(i);
            dot += a * b;
            leftNorm += a * a;
            rightNorm += b * b;
        }
        if (leftNorm <= 0 || rightNorm <= 0) return 0.0;
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    static double lexicalSimilarity(String query,String content) {
        Set<String> a = bigrams(query);
        Set<String> b = bigrams(content);
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        int overlap = 0;
        for (String token : a) {
            if (b.contains(token)) overlap++;
        }
        return overlap * 2.0 / (a.size() + b.size());
    }

    private static Set<String> bigrams(String text) {
        Set<String> result = new LinkedHashSet<>();
        String value = safe(text).replaceAll("\\s+","").toLowerCase(Locale.ROOT);
        for (int i = 0; i + 1 < value.length(); i++) {
            result.add(value.substring(i,i + 2));
        }
        return result;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private void markFailure() {
        embedRetryAfter = System.currentTimeMillis() + 300000L;
    }
}
