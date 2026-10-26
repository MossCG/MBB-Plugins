package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 梗Wiki公开只读 API。
 *
 * 固定访问 cnmeme.wiki，不接受模型传入任意 URL；只返回精简摘要和来源链接。
 */
public class RoleplayMemeService {
    private static final String BASE_URL = "https://cnmeme.wiki";
    private static final String SEARCH_PATH = "/api/v1/public/memes";
    private static final int MAX_RESPONSE_BYTES = 524288;

    private final RoleplayService service;
    private final Map<String,CacheEntry> cache = new LinkedHashMap<String,CacheEntry>(16,0.75f,true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String,CacheEntry> eldest) {
            return size() > 100;
        }
    };

    public RoleplayMemeService(RoleplayService service) {
        this.service = service;
    }

    public JSONObject search(String query,String tag,int limit) throws Exception {
        if (!service.config().memeSearchEnable) {
            throw new IllegalArgumentException("梗检索未启用");
        }
        String text = service.safe(query).trim();
        String tagText = service.safe(tag).trim();
        if (text.isEmpty() && tagText.isEmpty()) throw new IllegalArgumentException("查询词和标签不能同时为空");
        if (text.length() > 80) throw new IllegalArgumentException("查询词最多 80 字符");
        if (tagText.length() > 40) throw new IllegalArgumentException("标签最多 40 字符");
        if (isShortAsciiQuery(text)) {
            throw new IllegalArgumentException("一到两个字母的缩写请先用 slang_search 查询本地词集");
        }
        int max = Math.max(1,Math.min(5,limit <= 0
                ? service.config().memeSearchMaxResults : limit));
        String key = text.toLowerCase(Locale.ROOT)+"|"+tagText.toLowerCase(Locale.ROOT)+"|"+max;
        JSONObject cached = cached(key);
        if (cached != null) {
            JSONObject copy = new JSONObject(true);
            copy.putAll(cached);
            copy.put("cached",true);
            return copy;
        }
        StringBuilder url = new StringBuilder(BASE_URL).append(SEARCH_PATH).append("?limit=").append(max);
        if (!text.isEmpty()) url.append("&q=").append(URLEncoder.encode(text,"UTF-8"));
        if (!tagText.isEmpty()) url.append("&tag=").append(URLEncoder.encode(tagText,"UTF-8"));
        JSONObject page = request(url.toString());
        JSONObject result = normalizePage(page,text,tagText,max);
        result.put("cached",false);
        putCache(key,result);
        return result;
    }

    static JSONObject normalizePage(JSONObject page,String query,String tag,int limit) {
        JSONObject result = new JSONObject(true);
        JSONArray items = new JSONArray();
        JSONArray source = page == null ? null : page.getJSONArray("items");
        int max = Math.max(1,Math.min(5,limit));
        if (source != null) {
            for (Object value : source) {
                if (items.size() >= max) break;
                if (!(value instanceof JSONObject)) continue;
                JSONObject item = (JSONObject)value;
                String slug = safe(item.getString("slug"));
                String title = safe(item.getString("title"));
                if (slug.isEmpty() || title.isEmpty()) continue;
                JSONObject entry = new JSONObject(true);
                entry.put("slug",slug);
                entry.put("title",title);
                entry.put("summary",truncate(plainSummary(item.getString("summary")),500));
                entry.put("tags",safeArray(item.getJSONArray("tags"),8,40));
                entry.put("updatedAt",safe(item.getString("updated_at")));
                entry.put("sourceUrl",BASE_URL+"/entry/"+encode(slug));
                entry.put("sensitive",sensitive(entry.getString("summary"),entry.getJSONArray("tags")));
                items.add(entry);
            }
        }
        result.put("query",safe(query));
        result.put("tag",safe(tag));
        result.put("items",items);
        result.put("hasMore",page != null && page.getBooleanValue("has_more"));
        result.put("source","梗Wiki");
        result.put("licenseNote","公开只读摘要；使用时应保留来源链接，素材授权需另行确认");
        return result;
    }

    private JSONObject request(String rawUrl) throws Exception {
        URL url = new URL(rawUrl);
        if (!"https".equalsIgnoreCase(url.getProtocol())
                || !"cnmeme.wiki".equalsIgnoreCase(url.getHost())) {
            throw new IllegalArgumentException("梗检索只允许访问 cnmeme.wiki");
        }
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("GET");
        int timeout = Math.max(3,service.config().memeSearchTimeoutSecond) * 1000;
        connection.setConnectTimeout(timeout);
        connection.setReadTimeout(timeout);
        connection.setRequestProperty("User-Agent","MoBoxBot/0.1 (+https://github.com/MossCG/MoBoxBot)");
        connection.setRequestProperty("Accept","application/json");
        connection.setRequestProperty("Accept-Encoding","identity");
        try {
            int code = connection.getResponseCode();
            if (code != 200) {
                String retry = connection.getHeaderField("Retry-After");
                throw new IllegalStateException("梗Wiki API HTTP "+code
                        +(retry == null || retry.trim().isEmpty() ? "" : "，Retry-After="+retry));
            }
            byte[] bytes = readLimited(connection.getInputStream(),MAX_RESPONSE_BYTES);
            JSONObject json = JSONObject.parseObject(new String(bytes,StandardCharsets.UTF_8));
            if (json == null) throw new IllegalStateException("梗Wiki API 返回了无效 JSON");
            return json;
        } finally {
            connection.disconnect();
        }
    }

    private JSONObject cached(String key) {
        int seconds = Math.max(0,service.config().memeSearchCacheSecond);
        if (seconds <= 0) return null;
        synchronized (cache) {
            CacheEntry entry = cache.get(key);
            if (entry == null) return null;
            if (entry.expireAt < System.currentTimeMillis()) {
                cache.remove(key);
                return null;
            }
            return entry.value;
        }
    }

    private void putCache(String key,JSONObject value) {
        int seconds = Math.max(0,service.config().memeSearchCacheSecond);
        if (seconds <= 0 || value == null) return;
        JSONObject copy = new JSONObject(true);
        copy.putAll(value);
        synchronized (cache) {
            cache.put(key,new CacheEntry(System.currentTimeMillis()+seconds*1000L,copy));
        }
    }

    private byte[] readLimited(InputStream input,int maxBytes) throws Exception {
        if (input == null) throw new IllegalStateException("梗Wiki API 没有响应正文");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read == 0) continue;
            total += read;
            if (total > maxBytes) throw new IllegalStateException("梗Wiki API 响应超过大小限制");
            output.write(buffer,0,read);
        }
        return output.toByteArray();
    }

    private static JSONArray safeArray(JSONArray source,int maxItems,int maxChars) {
        JSONArray result = new JSONArray();
        if (source == null) return result;
        for (Object value : source) {
            if (result.size() >= maxItems) break;
            String text = truncate(safe(value == null ? "" : String.valueOf(value)),maxChars);
            if (!text.isEmpty() && !result.contains(text)) result.add(text);
        }
        return result;
    }

    private static boolean sensitive(String summary,JSONArray tags) {
        String text = (safe(summary)+" "+(tags == null ? "" : tags.toJSONString())).toLowerCase(Locale.ROOT);
        return text.contains("开盒") || text.contains("挂人") || text.contains("黑称")
                || text.contains("地狱笑话") || text.contains("网暴") || text.contains("人肉");
    }

    private static boolean isShortAsciiQuery(String text) {
        return text.matches("(?i)[a-z0-9]{1,2}");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(safe(value),"UTF-8");
        } catch (Exception e) {
            return safe(value);
        }
    }

    private static String truncate(String value,int maxChars) {
        String text = safe(value).trim();
        return text.length() <= maxChars ? text : text.substring(0,maxChars);
    }

    private static String plainSummary(String value) {
        return safe(value).replaceAll("(?is)<[^>]+>"," ")
                .replaceAll("\\s+"," ").trim();
    }

    private static class CacheEntry {
        private final long expireAt;
        private final JSONObject value;

        private CacheEntry(long expireAt,JSONObject value) {
            this.expireAt = expireAt;
            this.value = value;
        }
    }
}
