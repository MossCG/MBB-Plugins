package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Plugin;
import org.moboxlab.moboxbot.API.PluginService;

/**
 * 风格层
 *
 * 只做去 AI 味改写：不改事实、不增删信息、不动表达动作。
 * 调用失败或改写结果不合理时，直接使用执行层原文。
 */
public class RoleplayStyler {
    private final Plugin plugin;

    public RoleplayStyler(Plugin plugin) {
        this.plugin = plugin;
    }

    public String polish(RoleplayConfig config,RoleplayPersona persona,long groupID,
                         String text,String trigger,String examples) {
        PluginService ai = plugin.getServer().getPluginManager().getService("MBB-AI");
        if (ai == null) return text;
        try {
            JSONArray messages = new JSONArray();
            messages.add(message("system",systemPrompt(config,persona,trigger,examples)));
            messages.add(message("user",text));
            JSONObject params = new JSONObject(true);
            String profile = config.styleProfile == null || config.styleProfile.trim().isEmpty()
                    ? config.aiProfile : config.styleProfile.trim();
            params.put("profile",profile);
            params.put("maxTokens",config.styleMaxTokens);
            params.put("temperature",0.6);
            params.put("reasoningEffort",config.styleReasoningEffort);
            params.put("sessionId","roleplay-style-"+groupID);
            params.put("messages",messages);
            long startTime = System.currentTimeMillis();
            JSONObject response = ai.call("chat",params);
            RoleplayAiLog.log(plugin.getLogger(),"风格",groupID,response,
                    System.currentTimeMillis() - startTime);
            if (response == null || !response.getBooleanValue("status")) return text;
            String polished = clean(response.getString("content"));
            if (polished.isEmpty()) return text;
            // 改写可以变短，但不允许明显变长，也不允许带回 Markdown
            if (polished.length() > text.length() * 1.2 + 10) return text;
            if (polished.contains("```")) return text;
            return polished;
        } catch (Exception e) {
            return text;
        }
    }

    private String systemPrompt(RoleplayConfig config,RoleplayPersona persona,
                                String trigger,String examples) {
        StringBuilder builder = new StringBuilder();
        builder.append("你是中文群聊台词润色器。把下面的台词改得更像真人在 QQ 群里随口说的话，去掉 AI 味。\n")
                .append("硬性要求：\n")
                .append("- 只输出改写后的台词，不要解释、不要 Markdown、不要加引号或前缀\n")
                .append("- 不改变事实和信息量，不新增也不删减内容\n")
                .append("- 保持角色语气，说话方式：")
                .append(persona.speechStyle == null || persona.speechStyle.isEmpty()
                        ? "自然口语" : persona.speechStyle)
                .append("\n");
        if (persona.catchphrases != null && !persona.catchphrases.isEmpty()) {
            builder.append("- 角色口癖：").append(String.join("、",persona.catchphrases))
                    .append("，低频自然使用，不要每句都带\n");
        }
        builder.append("- 去掉书面语连接词、总结式收尾、排比和破折号\n")
                .append("- 长度不超过原文，宁短不长\n")
                .append("- 允许省略主语、允许短句和不完整句\n")
                .append("触发原因：").append(trigger).append("\n");
        if (examples != null && !examples.trim().isEmpty()) {
            builder.append("台词示例，只学语气，不要照抄：\n").append(examples.trim()).append("\n");
        }
        return builder.toString();
    }

    /**
     * 去掉模型可能加上的引号、前缀和多余空行
     */
    private String clean(String content) {
        String value = content == null ? "" : content.trim();
        if (value.isEmpty()) return "";
        if (value.startsWith("改写：")) value = value.substring(3).trim();
        if (value.startsWith("改写:")) value = value.substring(3).trim();
        if (value.length() >= 2) {
            boolean quoted = (value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("“") && value.endsWith("”"));
            if (quoted) value = value.substring(1,value.length() - 1).trim();
        }
        return value.replace("\r","").trim();
    }

    private JSONObject message(String role,String content) {
        JSONObject message = new JSONObject(true);
        message.put("role",role);
        message.put("content",content);
        return message;
    }
}
