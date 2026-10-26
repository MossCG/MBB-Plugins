package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 生图回调目标角色约束回归测试
 */
public class RoleplayDrawNotifyContextTestMain {
    public static void main(String[] args) {
        List<RoleplayPersona.StudentProfile> profiles = new ArrayList<>();
        profiles.add(profile("才羽桃井",Arrays.asList("小桃")));
        profiles.add(profile("才羽绿",Arrays.asList("小绿")));

        String wrong = RoleplayDrawNotifyService.wrongTargetMention(
                "小绿画好啦",Arrays.asList("才羽桃井"),Arrays.asList("小桃"),
                profiles,"才羽桃井",Arrays.asList("小桃"));
        if (!Arrays.asList("才羽绿","小绿").contains(wrong)) {
            throw new AssertionError("回调提到非目标角色时应识别为错误引用");
        }
        String allowed = RoleplayDrawNotifyService.wrongTargetMention(
                "小桃画好啦",Arrays.asList("才羽桃井"),Arrays.asList("小桃"),
                profiles,"才羽桃井",Arrays.asList("小桃"));
        if (!allowed.isEmpty()) {
            throw new AssertionError("目标角色别名应被允许");
        }

        JSONObject context = new JSONObject(true);
        JSONArray names = new JSONArray();
        names.add("才羽桃井");
        context.put("targetNames",names);
        String fallback = RoleplayDrawNotifyService.fallbackNotifyText(true,context);
        if (!fallback.contains("才羽桃井") || fallback.contains("小绿")) {
            throw new AssertionError("无 AI 或校验失败时兜底文案应使用请求目标角色");
        }
    }

    private static RoleplayPersona.StudentProfile profile(String name,List<String> aliases) {
        RoleplayPersona.StudentProfile profile = new RoleplayPersona.StudentProfile();
        profile.name = name;
        profile.aliases = aliases;
        return profile;
    }
}
