package org.moboxlab.mbb.pighub;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * PigHub 图片服务
 */
public class PigHubService {
    private static final Object lock = new Object();
    private static List<String> cache = new ArrayList<>();
    private static long cacheTime = 0L;
    private static String cacheUrl = "";

    public static String getRandomImage(String apiUrl,int cacheMinutes) {
        if (apiUrl == null || apiUrl.trim().isEmpty()) return null;
        long now = System.currentTimeMillis();
        long ttl = Math.max(1,cacheMinutes) * 60L * 1000L;
        synchronized (lock) {
            if (cache.isEmpty() || !apiUrl.equals(cacheUrl) || now - cacheTime > ttl) {
                List<String> list = fetch(apiUrl);
                if (list != null && !list.isEmpty()) {
                    cache = list;
                    cacheUrl = apiUrl;
                    cacheTime = now;
                }
            }
            if (cache.isEmpty()) return null;
            return cache.get(ThreadLocalRandom.current().nextInt(cache.size()));
        }
    }

    private static List<String> fetch(String apiUrl) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(apiUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            connection.setRequestProperty("Referer","https://pighub.top/");
            connection.setRequestProperty("Accept","application/json");
            int code = connection.getResponseCode();
            if (code != 200) return null;
            InputStream input = connection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
            reader.close();
            JSONObject json = JSONObject.parseObject(builder.toString());
            if (json == null) return null;
            JSONArray data = json.getJSONArray("data");
            if (data == null) return null;
            List<String> result = new ArrayList<>();
            for (Object item : data) {
                if (!(item instanceof JSONObject)) continue;
                String imageUrl = ((JSONObject) item).getString("image_url");
                if (imageUrl == null || imageUrl.isEmpty()) continue;
                if (imageUrl.startsWith("//")) imageUrl = "https:"+imageUrl;
                if (imageUrl.startsWith("/")) {
                    imageUrl = new URI("https","pighub.top",imageUrl,null).toString();
                }
                if (imageUrl.startsWith("http")) result.add(imageUrl);
            }
            return result;
        } catch (Exception e) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
