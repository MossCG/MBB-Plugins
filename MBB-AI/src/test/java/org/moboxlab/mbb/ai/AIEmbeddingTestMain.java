package org.moboxlab.mbb.ai;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * OpenAI 兼容 embedding 端点与响应解析测试。
 */
public class AIEmbeddingTestMain {
    public static void main(String[] args) {
        String chat = OpenAIProvider.buildEndpoint(
                "https://example.test/v1/chat/completions","chat/completions");
        String embeddings = OpenAIProvider.buildEndpoint(
                "https://example.test/v1/chat/completions","embeddings");
        if (!"https://example.test/v1/chat/completions".equals(chat)
                || !"https://example.test/v1/embeddings".equals(embeddings)) {
            throw new AssertionError("embedding 端点推导不正确");
        }

        JSONObject response = new JSONObject(true);
        response.put("model","embedding-test");
        JSONArray data = new JSONArray();
        JSONObject first = new JSONObject(true);
        first.put("index",0);
        JSONArray vector = new JSONArray();
        vector.add(0.1);
        vector.add(-0.2);
        vector.add(0.3);
        first.put("embedding",vector);
        data.add(first);
        response.put("data",data);
        JSONObject usage = new JSONObject(true);
        usage.put("prompt_tokens",3);
        usage.put("total_tokens",3);
        response.put("usage",usage);

        AIProfile profile = new AIProfile();
        profile.model = "fallback-model";
        JSONObject parsed = OpenAIProvider.embeddingSuccess(response,profile);
        if (!parsed.getBooleanValue("status")
                || !"embedding-test".equals(parsed.getString("model"))
                || parsed.getJSONArray("embeddings").size() != 1
                || parsed.getJSONArray("embeddings").getJSONObject(0)
                .getJSONArray("embedding").size() != 3
                || parsed.getJSONObject("usage").getIntValue("promptTokens") != 3) {
            throw new AssertionError("embedding 响应解析不完整");
        }
    }
}
