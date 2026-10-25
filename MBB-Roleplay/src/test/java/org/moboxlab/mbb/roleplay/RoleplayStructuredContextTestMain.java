package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * 结构化上下文清洗、渲染、兼容回退与配置默认值测试。
 */
public class RoleplayStructuredContextTestMain {
    public static void main(String[] args) {
        RoleplayConfig config = new RoleplayConfig();
        if (!config.structuredContextEnable || config.structuredContextMaxChars != 1800
                || config.structuredContextItemMaxChars != 160) {
            throw new AssertionError("结构化上下文默认配置不正确");
        }

        JSONObject raw = new JSONObject(true);
        JSONArray topics = new JSONArray();
        topics.add("箱子风道");
        topics.add("箱子风道");
        topics.add(" ");
        topics.add("硬盘温度");
        raw.put("topics",topics);
        raw.put("background","正在讨论机箱散热");
        JSONArray progress = new JSONArray();
        progress.add("已经确认侧面进风方向反了");
        raw.put("progress",progress);
        raw.put("pending",new JSONArray());
        JSONArray agreements = new JSONArray();
        agreements.add("- 下次先看显卡尾气");
        raw.put("agreements",agreements);

        RoleplayStructuredContext context = RoleplayStructuredContext.parse(raw,80);
        JSONObject normalized = context.toJson();
        if (normalized.getJSONArray("topics").size() != 2
                || normalized.getJSONArray("background").size() != 1
                || !"下次先看显卡尾气".equals(normalized.getJSONArray("agreements").getString(0))) {
            throw new AssertionError("结构化上下文没有正确去重或清洗");
        }
        String rendered = context.render(600);
        if (!rendered.contains("【当前话题】") || !rendered.contains("【进展与结论】")
                || !rendered.contains("【对话约定】")) {
            throw new AssertionError("结构化上下文没有按固定分区渲染");
        }
        String limited = context.render(160);
        if (limited.length() > 160 || limited.isEmpty()) {
            throw new AssertionError("结构化上下文没有遵守字符上限");
        }

        RoleplayStructuredContext restored = RoleplayStructuredContext.fromJson(
                JSONObject.toJSONString(normalized),80);
        if (restored.isEmpty() || !context.render(600).equals(restored.render(600))) {
            throw new AssertionError("结构化上下文 JSON 往返后内容发生变化");
        }

        RoleplayStructuredContext fallback = RoleplayStructuredContext.fallback(
                "昨天在调机箱风道。硬盘温度还是偏高！今天先看显卡位置。",80);
        JSONArray background = fallback.toJson().getJSONArray("background");
        if (background == null || background.isEmpty() || fallback.isEmpty()) {
            throw new AssertionError("旧短期摘要没有兼容回退为结构化背景");
        }
    }
}
