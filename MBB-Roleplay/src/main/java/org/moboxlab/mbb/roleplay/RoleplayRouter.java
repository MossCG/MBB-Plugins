package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Plugin;
import org.moboxlab.moboxbot.API.PluginService;

import java.util.ArrayList;
import java.util.List;

/**
 * 路由层
 *
 * 判断这条消息要不要回复、要挂哪些技能、要带哪些资料。
 * 提示词刻意保持小：不带人设正文，路由不需要知道角色的口癖。
 *
 * 规则层有否决权：被直接艾特必须回复，冷却中必须沉默，路由层不能推翻。
 */
public class RoleplayRouter {
    private final Plugin plugin;
    private final RoleplayService service;

    public RoleplayRouter(Plugin plugin,RoleplayService service) {
        this.plugin = plugin;
        this.service = service;
    }

    /**
     * 调用路由模型，失败返回 null，由调用方回退到规则决策
     */
    public RoleplayRouteDecision route(RoleplayConfig config,RoleplayPersona persona,
                                       RoleplaySkillRegistry registry,RoleplayConversationState state,
                                       String content,String userName,String relationship,
                                       String recentContext,String emotionSummary,
                                       String addressingHint) {
        PluginService ai = plugin.getServer().getPluginManager().getService("MBB-AI");
        if (ai == null) return null;
        JSONObject response = callRoute(ai,config,persona,registry,state,content,userName,
                relationship,recentContext,emotionSummary,addressingHint,
                config.routerMaxTokens,"路由");
        RoleplayRouteDecision decision = parseResponse(response);
        if (decision != null) return decision;
        if (!shouldRetry(response)) return null;
        int retryMaxTokens = retryMaxTokens(config.routerMaxTokens);
        if (retryMaxTokens <= config.routerMaxTokens) return null;
        plugin.getLogger().sendWarn("[角色] 路由 群"+state.groupID+" 输出被截断，使用 "
                +retryMaxTokens+" Token 重试");
        response = callRoute(ai,config,persona,registry,state,content,userName,
                relationship,recentContext,emotionSummary,addressingHint,
                retryMaxTokens,"路由重试");
        return parseResponse(response);
    }

    private JSONObject callRoute(PluginService ai,RoleplayConfig config,RoleplayPersona persona,
                                 RoleplaySkillRegistry registry,RoleplayConversationState state,
                                 String content,String userName,String relationship,
                                 String recentContext,String emotionSummary,
                                 String addressingHint,int maxTokens,String tag) {
        JSONArray messages = new JSONArray();
        messages.add(message("system",systemPrompt(config,persona,registry,state)));
        messages.add(message("user",userPrompt(content,userName,relationship,recentContext,
                emotionSummary,addressingHint)));
        JSONObject params = new JSONObject(true);
        String profile = config.routerProfile == null || config.routerProfile.trim().isEmpty()
                ? config.aiProfile : config.routerProfile.trim();
        params.put("profile",profile);
        params.put("maxTokens",maxTokens);
        params.put("temperature",0.0);
        params.put("reasoningEffort",config.routerReasoningEffort);
        params.put("sessionId","roleplay-route-"+state.groupID);
        params.put("messages",messages);
        long startTime = System.currentTimeMillis();
        JSONObject response = ai.call("chat",params);
        RoleplayAiLog.log(plugin.getLogger(),tag,state.groupID,response,
                System.currentTimeMillis() - startTime);
        return response;
    }

    private RoleplayRouteDecision parseResponse(JSONObject response) {
        if (response == null || !response.getBooleanValue("status")) return null;
        RoleplayRouteDecision decision = parse(response.getString("content"));
        if (decision == null) decision = parse(response.getString("reasoningContent"));
        return decision;
    }

    private boolean shouldRetry(JSONObject response) {
        if (response == null || !response.getBooleanValue("status")) return false;
        return "length".equalsIgnoreCase(safe(response.getString("finishReason"),""));
    }

    private int retryMaxTokens(int current) {
        int retry = Math.max(current * 2,1600);
        return Math.min(retry,8000);
    }

    private String systemPrompt(RoleplayConfig config,RoleplayPersona persona,
                                RoleplaySkillRegistry registry,RoleplayConversationState state) {
        StringBuilder builder = new StringBuilder();
        builder.append("你是群聊消息路由器。判断这条消息是否需要当前角色回复，")
                .append("并选择要挂载的技能和资料。只输出 JSON，不要解释。\n");
        builder.append("角色：").append(persona.name);
        if (!persona.school.isEmpty() || !persona.club.isEmpty()) {
            builder.append("（").append(persona.school).append("/").append(persona.club).append("）");
        }
        builder.append("\n");
        if (!persona.interests.isEmpty()) {
            builder.append("角色兴趣：").append(String.join("、",persona.interests)).append("\n");
        }
        builder.append("可用技能：\n").append(registry.catalogue(config));
        builder.append("可选资料（常驻资料不需要点名）：\n").append(service.materialCatalogue());
        builder.append("对话状态：").append(stateSummary(state)).append("\n");
        builder.append("判断规则：\n")
                .append("- 先判断这句话是不是对角色说的：只有明确艾特或回复角色、叫角色名字、")
                .append("或延续角色参与的话题，才算对角色说\n")
                .append("- 明确艾特或回复其他群成员的句子，默认不是对角色说的，reply 用 false\n")
                .append("- 群里其他人之间的闲聊、互相点名、与角色无关的话题，reply 用 false\n")
                .append("- 有人直接艾特、回复或点名角色时，reply 必须为 true，addressed 为 direct\n")
                .append("- 话题明显符合角色兴趣，或角色刚参与过同一话题时，可以 reply 为 true\n")
                .append("- 普通闲聊、别人的私事、别人之间的对话、机器人之间无关的互动，")
                .append("reply 用 false，不要为了刷存在感而回复\n")
                .append("- 只有确实需要某个技能时才写进 skills，没有就留空数组\n")
                .append("- 只有确实需要某份资料时才写进 materials，没有就留空数组\n");
        builder.append("输出格式：{\"reply\":true,\"addressed\":\"direct|thread|ambient|none\",")
                .append("\"confidence\":0.8,\"skills\":[],\"materials\":[],\"reason\":\"一句话原因\"}");
        return builder.toString();
    }

    private String userPrompt(String content,String userName,String relationship,
                              String recentContext,String emotionSummary,String addressingHint) {
        StringBuilder builder = new StringBuilder();
        if (recentContext != null && !recentContext.trim().isEmpty()) {
            builder.append("最近群聊：\n").append(recentContext.trim()).append("\n\n");
        }
        builder.append("当前消息：\n").append(content == null ? "" : content.trim()).append("\n");
        builder.append("发送者：").append(userName == null ? "" : userName)
                .append("（关系：").append(relationship == null ? "朋友" : relationship).append("）");
        if (addressingHint != null && !addressingHint.trim().isEmpty()) {
            builder.append("\n发言指向：").append(addressingHint.trim());
        }
        if (emotionSummary != null && !emotionSummary.trim().isEmpty()) {
            builder.append("\n当前情绪与关系：").append(emotionSummary.trim());
        }
        return builder.toString();
    }

    private String stateSummary(RoleplayConversationState state) {
        StringBuilder builder = new StringBuilder();
        if (state.lastReplyTime > 0) {
            long seconds = (System.currentTimeMillis() - state.lastReplyTime) / 1000L;
            builder.append("角色 ").append(seconds).append(" 秒前说过话；");
        } else {
            builder.append("角色还没在本群说过话；");
        }
        if (state.otherRoleStreak > 0) {
            builder.append("另一个角色机器人已连续发言 ").append(state.otherRoleStreak).append(" 条；");
        }
        if (state.botStreak > 2) {
            builder.append("角色自己已连续发言 ").append(state.botStreak).append(" 条，注意别霸屏；");
        }
        return builder.toString();
    }

    private RoleplayRouteDecision parse(String content) {
        JSONObject json = parseJson(content);
        if (json == null) return null;
        RoleplayRouteDecision decision = new RoleplayRouteDecision();
        decision.reply = json.getBooleanValue("reply");
        decision.addressed = safe(json.getString("addressed"),"none");
        decision.confidence = json.getDoubleValue("confidence");
        decision.chance = 1.0;
        decision.reason = safe(json.getString("reason"),"路由层判断");
        decision.skills = readList(json.getJSONArray("skills"));
        decision.materials = readList(json.getJSONArray("materials"));
        return decision;
    }

    private List<String> readList(JSONArray array) {
        List<String> result = new ArrayList<>();
        if (array == null) return result;
        for (Object item : array) {
            if (item == null) continue;
            String value = String.valueOf(item).trim();
            if (!value.isEmpty() && !result.contains(value)) result.add(value);
        }
        return result;
    }

    private JSONObject parseJson(String content) {
        String raw = content == null ? "" : content.trim();
        if (raw.isEmpty()) return null;
        try {
            return JSON.parseObject(raw);
        } catch (Exception ignored) {
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        try {
            return JSON.parseObject(raw.substring(start,end + 1));
        } catch (Exception ignored) {
            return null;
        }
    }

    private JSONObject message(String role,String content) {
        JSONObject message = new JSONObject(true);
        message.put("role",role);
        message.put("content",content);
        return message;
    }

    private String safe(String value,String defaultValue) {
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }
}
