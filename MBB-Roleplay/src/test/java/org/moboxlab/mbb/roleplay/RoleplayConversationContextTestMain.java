package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * 追加式会话上下文默认配置和内容清洗测试。
 */
public class RoleplayConversationContextTestMain {
    public static void main(String[] args) {
        RoleplayConfig config = new RoleplayConfig();
        if (!config.conversationAppendEnable
                || config.conversationContextTokenLimit != 1000000
                || Math.abs(config.conversationContextCompactPercent - 0.75) > 0.0001
                || Math.abs(config.conversationContextKeepPercent - 0.20) > 0.0001
                || config.conversationAppendRecentMessages != 20) {
            throw new AssertionError("追加式上下文默认配置不正确");
        }
        if (!"assistant".equals(RoleplayConversationContextService.normalizeRole("assistant"))
                || !"system".equals(RoleplayConversationContextService.normalizeRole("system"))
                || !"user".equals(RoleplayConversationContextService.normalizeRole("tool"))) {
            throw new AssertionError("上下文 role 归一化不正确");
        }
        if (RoleplayConversationContextService.shouldCompact(749999,1000000,0.75)
                || !RoleplayConversationContextService.shouldCompact(750000,1000000,0.75)) {
            throw new AssertionError("1M 窗口压缩阈值判断不正确");
        }

        JSONArray content = new JSONArray();
        JSONObject text = new JSONObject(true);
        text.put("type","text");
        text.put("text","看图");
        content.add(text);
        JSONObject image = new JSONObject(true);
        image.put("type","image_url");
        image.put("image_url","data:image/png;base64,AAAA");
        content.add(image);
        JSONArray cleaned = (JSONArray)RoleplayConversationContextService.sanitizeContentForStorage(content);
        if (cleaned.size() != 2 || !"text".equals(cleaned.getJSONObject(1).getString("type"))
                || !String.valueOf(cleaned.getJSONObject(1).getString("text")).contains("图片")) {
            throw new AssertionError("图片 data URI 不应写入会话历史");
        }
    }
}
