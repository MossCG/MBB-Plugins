package org.moboxlab.mbb.comfyui;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.PluginLogger;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * ComfyUI HTTP 客户端
 */
public class ComfyUIClient {
    private final String baseUrl;
    private final int timeoutSecond;
    private final PluginLogger logger;

    public ComfyUIClient(String baseUrl,int timeoutSecond,PluginLogger logger) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.timeoutSecond = timeoutSecond;
        this.logger = logger;
    }

    public String queuePrompt(JSONObject workflow,String clientId) throws Exception {
        JSONObject body = new JSONObject(true);
        body.put("prompt",workflow);
        body.put("client_id",clientId);
        JSONObject response = postJson("/prompt",body);
        String promptId = response == null ? "" : response.getString("prompt_id");
        if (promptId == null || promptId.trim().isEmpty()) {
            throw new IllegalStateException("ComfyUI 没有返回 prompt_id："+safeJson(response));
        }
        return promptId;
    }

    public JSONObject systemStats() throws Exception {
        return getJson("/system_stats");
    }

    public JSONObject history(String promptId) throws Exception {
        return getJson("/history/"+urlEncode(promptId));
    }

    public byte[] viewImage(String filename,String subfolder,String type) throws Exception {
        StringBuilder path = new StringBuilder("/view?filename=");
        path.append(urlEncode(filename));
        if (subfolder != null && !subfolder.isEmpty()) {
            path.append("&subfolder=").append(urlEncode(subfolder));
        }
        if (type != null && !type.isEmpty()) {
            path.append("&type=").append(urlEncode(type));
        }
        return getBytes(path.toString());
    }

    private JSONObject postJson(String path,JSONObject body) throws Exception {
        HttpURLConnection connection = open(path,"POST");
        byte[] bytes = body.toJSONString().getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(bytes.length);
        OutputStream output = connection.getOutputStream();
        try {
            output.write(bytes);
        } finally {
            output.close();
        }
        return parseResponse(connection);
    }

    private JSONObject getJson(String path) throws Exception {
        HttpURLConnection connection = open(path,"GET");
        return parseResponse(connection);
    }

    private byte[] getBytes(String path) throws Exception {
        HttpURLConnection connection = open(path,"GET");
        int code = connection.getResponseCode();
        InputStream input = code >= 200 && code < 300
                ? connection.getInputStream() : connection.getErrorStream();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("ComfyUI 返回 HTTP "+code+"："+readText(input));
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int length;
        while ((length = input.read(buffer)) >= 0) {
            output.write(buffer,0,length);
        }
        input.close();
        connection.disconnect();
        return output.toByteArray();
    }

    private JSONObject parseResponse(HttpURLConnection connection) throws Exception {
        try {
            int code = connection.getResponseCode();
            InputStream input = code >= 200 && code < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String text = readText(input);
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("ComfyUI 返回 HTTP "+code+"："+text);
            }
            JSONObject json = JSON.parseObject(text);
            if (json == null) throw new IllegalStateException("ComfyUI 返回内容不是 JSON："+text);
            return json;
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection open(String path,String method) throws Exception {
        URL url = new URL(buildUrl(path));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(Math.min(30,timeoutSecond) * 1000);
        connection.setReadTimeout(timeoutSecond * 1000);
        connection.setRequestProperty("Accept","application/json");
        connection.setRequestProperty("User-Agent","MoBoxBot-MBB-ComfyUI/1.0");
        if ("POST".equals(method)) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type","application/json; charset=UTF-8");
        }
        return connection;
    }

    private String buildUrl(String path) {
        String base = baseUrl;
        while (base.endsWith("/")) base = base.substring(0,base.length() - 1);
        return base + path;
    }

    private String readText(InputStream input) throws Exception {
        if (input == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (builder.length() > 0) builder.append('\n');
            builder.append(line);
        }
        reader.close();
        return builder.toString();
    }

    private String urlEncode(String value) throws Exception {
        return URLEncoder.encode(value == null ? "" : value,"UTF-8").replace("+","%20");
    }

    private String safeJson(JSONObject json) {
        return json == null ? "" : json.toJSONString();
    }
}
