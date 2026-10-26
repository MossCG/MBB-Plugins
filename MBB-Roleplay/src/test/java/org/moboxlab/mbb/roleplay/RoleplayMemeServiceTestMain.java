package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * 梗Wiki响应裁剪与风险标记测试
 */
public class RoleplayMemeServiceTestMain {
    public static void main(String[] args) {
        RoleplayConfig config = new RoleplayConfig();
        if (!config.memeSearchEnable || config.memeSearchMaxResults != 3
                || config.memeSearchTimeoutSecond != 10 || config.memeSearchCacheSecond != 600) {
            throw new AssertionError("梗检索默认配置不正确");
        }
        JSONArray source = new JSONArray();
        source.add(item("example","示例梗","一个普通梗的摘要",new String[]{"谐音梗"}));
        source.add(item("open-box","开盒相关","<script>alert(1)</script>涉及开盒和挂人的词条",
                new String[]{"开盒","网暴"}));
        JSONObject page = new JSONObject(true);
        page.put("items",source);
        page.put("has_more",true);
        JSONObject normalized = RoleplayMemeService.normalizePage(page,"示例梗","谐音梗",2);
        JSONArray items = normalized.getJSONArray("items");
        if (items == null || items.size() != 2 || !normalized.getBooleanValue("hasMore")) {
            throw new AssertionError("梗Wiki响应应保留词条和分页标记");
        }
        JSONObject first = items.getJSONObject(0);
        if (!first.getString("sourceUrl").startsWith("https://cnmeme.wiki/entry/")) {
            throw new AssertionError("梗Wiki结果必须带来源链接");
        }
        JSONObject sensitive = items.getJSONObject(1);
        if (!sensitive.getBooleanValue("sensitive")
                || sensitive.getString("summary").contains("<")) {
            throw new AssertionError("高风险词条应标记，且只保留纯文本摘要");
        }
    }

    private static JSONObject item(String slug,String title,String summary,String[] tags) {
        JSONObject item = new JSONObject(true);
        item.put("slug",slug);
        item.put("title",title);
        item.put("summary",summary);
        JSONArray values = new JSONArray();
        for (String tag : tags) values.add(tag);
        item.put("tags",values);
        item.put("updated_at","2026-10-27T00:00:00Z");
        return item;
    }
}
