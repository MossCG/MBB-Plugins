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
                || Math.abs(config.conversationContextCompactPercent - 0.70) > 0.0001
                || Math.abs(config.conversationContextHardCompactPercent - 0.85) > 0.0001
                || Math.abs(config.conversationContextKeepPercent - 0.20) > 0.0001
                || config.conversationAppendRecentMessages != 20
                || config.conversationBootstrapMessages != 50) {
            throw new AssertionError("追加式上下文默认配置不正确");
        }
        if (!"assistant".equals(RoleplayConversationContextService.normalizeRole("assistant"))
                || !"system".equals(RoleplayConversationContextService.normalizeRole("system"))
                || !"tool".equals(RoleplayConversationContextService.normalizeRole("tool"))
                || !"user".equals(RoleplayConversationContextService.normalizeRole("unknown"))) {
            throw new AssertionError("上下文 role 归一化不正确");
        }
        if (RoleplayConversationContextService.shouldCompact(749999,1000000,0.75)
                || !RoleplayConversationContextService.shouldCompact(750000,1000000,0.75)) {
            throw new AssertionError("1M 窗口压缩阈值判断不正确");
        }
        JSONArray duplicate = new JSONArray();
        duplicate.add("喜欢布丁");
        duplicate.add(" 喜欢布丁 ");
        duplicate.add("喜欢打游戏");
        JSONArray deduped = RoleplayConversationContextService.dedupeTextList(duplicate,8);
        if (deduped.size() != 2 || !"喜欢布丁".equals(deduped.getString(0))) {
            throw new AssertionError("压缩轮次内文本列表未正确去重");
        }
        if (!RoleplayMemoryDedupService.isFactLine("- 喜欢布丁")
                || !"喜欢布丁".equals(RoleplayMemoryDedupService.factText("- 喜欢布丁"))
                || RoleplayMemoryDedupService.similarity(
                "喜欢布丁","喜欢布丁") < 0.99
                || RoleplayMemoryDedupService.similarity(
                "喜欢布丁","喜欢黑咖啡") >= 0.92) {
            throw new AssertionError("记忆注入指纹和相似度判断不正确");
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
