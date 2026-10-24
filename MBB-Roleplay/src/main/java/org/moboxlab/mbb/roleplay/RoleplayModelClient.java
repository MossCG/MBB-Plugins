package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * MBB-Model 本地小模型的 HTTP 客户端
 *
 * 只用 JDK 自带的 HttpURLConnection，不引入新依赖。
 * 服务地址来自配置，默认 127.0.0.1:8770。
 */
public class RoleplayModelClient {
    private final RoleplayService service;

    public RoleplayModelClient(RoleplayService service) {
        this.service = service;
    }

    JSONObject get(String path) {
        return request("GET",path,null);
    }

    JSONObject post(String path,JSONObject payload) {
        return request("POST",path,payload);
    }

    private JSONObject request(String method,String path,JSONObject payload) {
        String base = service.safe(service.config().modelServiceUrl);
        if (base.isEmpty()) return null;
        while (base.endsWith("/")) base = base.substring(0,base.length()-1);
        HttpURLConnection connection = null;
        try {
            URL url = new URL(base+path);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(Math.max(5,service.config().modelServiceTimeoutSecond) * 1000);
            connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
            connection.setRequestProperty("Accept","application/json");
            String token = service.safe(service.config().modelServiceToken);
            if (!token.isEmpty()) connection.setRequestProperty("Authorization","Bearer "+token);
            if (payload != null) {
                connection.setDoOutput(true);
                byte[] body = payload.toJSONString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(body.length);
                OutputStream output = connection.getOutputStream();
                try {
                    output.write(body);
                    output.flush();
                } finally {
                    output.close();
                }
            }
            int code = connection.getResponseCode();
            InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String text = read(stream);
            JSONObject json = text.isEmpty() ? new JSONObject(true) : JSONObject.parseObject(text);
            if (json == null) json = new JSONObject(true);
            json.put("httpCode",code);
            if (code >= 400) {
                JSONObject error = json.getJSONObject("error");
                json.put("status",false);
                json.put("message",error == null ? ("HTTP "+code) : service.safe(error.getString("message")));
            } else if (!json.containsKey("status")) {
                json.put("status",true);
            }
            return json;
        } catch (Exception e) {
            JSONObject result = new JSONObject(true);
            result.put("status",false);
            result.put("message","连接 MBB-Model 失败："+e.getMessage());
            return result;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String read(InputStream stream) {
        if (stream == null) return "";
        StringBuilder builder = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8));
            try {
                String line;
                while ((line = reader.readLine()) != null) builder.append(line);
            } finally {
                reader.close();
            }
        } catch (Exception ignored) {
        }
        return builder.toString();
    }
}
