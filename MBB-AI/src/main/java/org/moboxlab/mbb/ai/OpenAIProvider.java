package org.moboxlab.mbb.ai;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.PluginLogger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * OpenAI 兼容协议提供方
 */
public class OpenAIProvider {
    public static JSONObject chat(AIProfile profile,JSONArray messages,JSONObject params,
                                  boolean logRequestContent,PluginLogger logger) {
        if (profile == null) return error("没有找到 AI 模型配置！","profile");
        if (profile.baseUrl == null || profile.baseUrl.trim().isEmpty()) {
            return error("模型配置缺少 baseUrl！","config");
        }
        if (profile.model == null || profile.model.trim().isEmpty()) {
            return error("模型配置缺少 model！","config");
        }
        if (messages == null || messages.isEmpty()) {
            return error("messages 不能为空！","params");
        }

        JSONObject request = new JSONObject(true);
        request.put("model",profile.model);
        request.put("messages",messages);
        double temperature = params != null && params.get("temperature") != null
                ? params.getDoubleValue("temperature") : profile.temperature;
        int maxTokens = params != null && params.getIntValue("maxTokens") > 0
                ? params.getIntValue("maxTokens") : profile.maxTokens;
        request.put("temperature",temperature);
        request.put("max_tokens",maxTokens);

        String apiKey = profile.resolveApiKey();
        if (apiKey.isEmpty()) return error("模型配置缺少 apiKey！","config");

        if (logRequestContent) {
            logger.sendInfo("AI 请求：profile="+profile.name+" model="+profile.model
                    +" messages="+messages.toJSONString());
        }

        HttpURLConnection connection = null;
        try {
            URL url = new URL(buildEndpoint(profile.baseUrl));
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(profile.timeoutSeconds * 1000);
            connection.setReadTimeout(profile.timeoutSeconds * 1000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type","application/json; charset=UTF-8");
            connection.setRequestProperty("Accept","application/json");
            connection.setRequestProperty("Authorization","Bearer "+apiKey);
            connection.setRequestProperty("User-Agent","MoBoxBot/0.1 (+https://github.com/MossCG/MoBoxBot)");
            for (String header : profile.headers.keySet()) {
                String value = profile.headers.get(header);
                if (header == null || header.trim().isEmpty() || value == null) continue;
                connection.setRequestProperty(header.trim(),value);
            }
            String sessionId = params == null ? null : params.getString("sessionId");
            if (sessionId != null && !sessionId.trim().isEmpty()) {
                connection.setRequestProperty("x-opencode-session",sessionId.trim());
            }

            byte[] body = request.toJSONString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            OutputStream output = connection.getOutputStream();
            try {
                output.write(body);
            } finally {
                output.close();
            }

            int code = connection.getResponseCode();
            String response = read(connection,code);
            JSONObject json = parse(response);
            if (code < 200 || code >= 300) {
                return httpError(code,json,response);
            }
            if (json == null) return error("AI 返回内容不是合法 JSON！","response",true);
            return success(json,profile);
        } catch (java.net.SocketTimeoutException e) {
            return error("AI 服务请求超时！","timeout",true);
        } catch (Exception e) {
            return error("AI 服务请求失败："+e.getMessage(),"network",true);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static JSONObject success(JSONObject json,AIProfile profile) {
        JSONArray choices = json.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            return error("AI 返回内容缺少 choices！","response",false);
        }
        JSONObject choice = choices.getJSONObject(0);
        JSONObject message = choice == null ? null : choice.getJSONObject("message");
        String content = readContent(message == null ? null : message.get("content"));
        String reasoningContent = readContent(message == null ? null : message.get("reasoning_content"));
        JSONObject result = new JSONObject(true);
        result.put("status",true);
        result.put("content",content);
        result.put("reasoningContent",reasoningContent);
        result.put("finishReason",choice == null ? "" : choice.getString("finish_reason"));
        result.put("model",json.getString("model") == null ? profile.model : json.getString("model"));
        result.put("usage",readUsage(json.getJSONObject("usage")));
        return result;
    }

    private static JSONObject httpError(int code,JSONObject json,String response) {
        String message = "AI 服务返回 HTTP "+code;
        if (response != null && response.contains("error code: 1010")) {
            return error("请求被 Cloudflare 拦截，请检查 API 地址和请求头！","cloudflare_1010",false);
        }
        if (json != null) {
            JSONObject error = json.getJSONObject("error");
            if (error != null && error.getString("message") != null) message = error.getString("message");
        } else if (response != null && !response.trim().isEmpty()) {
            message = response.trim();
            if (message.length() > 300) message = message.substring(0,300)+"...";
        }
        boolean retryable = code == 429 || code >= 500;
        return error(message,"http_"+code,retryable);
    }

    private static JSONObject readUsage(JSONObject usage) {
        JSONObject result = new JSONObject(true);
        if (usage == null) {
            result.put("promptTokens",0);
            result.put("completionTokens",0);
            result.put("totalTokens",0);
            return result;
        }
        result.put("promptTokens",usage.getIntValue("prompt_tokens"));
        result.put("completionTokens",usage.getIntValue("completion_tokens"));
        result.put("totalTokens",usage.getIntValue("total_tokens"));
        return result;
    }

    private static String readContent(Object content) {
        if (content == null) return "";
        if (content instanceof String) return String.valueOf(content);
        if (content instanceof JSONArray) {
            StringBuilder builder = new StringBuilder();
            JSONArray array = (JSONArray) content;
            for (Object item : array) {
                if (!(item instanceof JSONObject)) continue;
                JSONObject object = (JSONObject) item;
                if ("text".equals(object.getString("type"))) {
                    builder.append(object.getString("text"));
                } else if ("image_url".equals(object.getString("type"))) {
                    builder.append("[图片]");
                }
            }
            return builder.toString();
        }
        return String.valueOf(content);
    }

    private static JSONObject parse(String response) {
        try {
            return JSONObject.parseObject(response);
        } catch (Exception e) {
            return null;
        }
    }

    private static String read(HttpURLConnection connection,int code) throws Exception {
        InputStream input = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
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

    private static String buildEndpoint(String baseUrl) {
        String base = baseUrl.trim();
        while (base.endsWith("/")) base = base.substring(0,base.length() - 1);
        if (base.endsWith("/chat/completions")) return base;
        return base+"/chat/completions";
    }

    private static JSONObject error(String message,String type) {
        return error(message,type,false);
    }

    private static JSONObject error(String message,String type,boolean retryable) {
        JSONObject result = new JSONObject(true);
        result.put("status",false);
        result.put("message",message);
        result.put("errorType",type);
        result.put("retryable",retryable);
        return result;
    }
}
