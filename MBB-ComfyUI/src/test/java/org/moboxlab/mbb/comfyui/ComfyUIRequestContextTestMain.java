package org.moboxlab.mbb.comfyui;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * 生图请求上下文保存与裁剪回归测试
 */
public class ComfyUIRequestContextTestMain {
    public static void main(String[] args) {
        JSONObject input = new JSONObject(true);
        input.put("message",repeat("画小桃",300));
        input.put("userName","测试用户");
        input.put("targetNames",array("才羽桃井","才羽绿","角色三","角色四","角色五",
                "角色六","角色七","角色八","角色九"));
        JSONObject brief = new JSONObject(true);
        brief.put("subject","才羽桃井");
        brief.put("scene",repeat("教室",200));
        brief.put("characters",array("才羽桃井"));
        input.put("brief",brief);
        JSONObject normalized = ComfyUIService.normalizeRequestContext(input);
        if (normalized.getString("message").length() > 800) {
            throw new AssertionError("请求原话应裁剪到 800 字符以内");
        }
        if (normalized.getJSONArray("targetNames").size() != 8) {
            throw new AssertionError("目标角色最多保留 8 个");
        }
        JSONObject normalizedBrief = normalized.getJSONObject("brief");
        if (normalizedBrief == null || normalizedBrief.getString("scene").length() > 240) {
            throw new AssertionError("brief 场景应保存并裁剪到 240 字符以内");
        }
    }

    private static JSONArray array(String... values) {
        JSONArray result = new JSONArray();
        for (String value : values) result.add(value);
        return result;
    }

    private static String repeat(String value,int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) builder.append(value);
        return builder.toString();
    }
}
