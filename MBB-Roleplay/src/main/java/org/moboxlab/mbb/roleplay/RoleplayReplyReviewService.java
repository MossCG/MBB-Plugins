package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.PluginService;

/**
 * 回复候选复核
 *
 * 发送前检查回复是否像角色、是否有 AI 味、是否重复或说教；低于阈值时使用模型给出的改写。
 */
public class RoleplayReplyReviewService {
    public static class Result {
        public boolean pass;
        public int score = 100;
        public String text = "";
        public String reason = "";
    }

    private final RoleplayService service;

    public RoleplayReplyReviewService(RoleplayService service) {
        this.service = service;
    }

    public Result review(long groupID,long userID,String content,String reply,
                         RoleplayRouteDecision decision) {
        Result result = new Result();
        result.text = reply;
        if (!service.config().replyReviewEnable || service.safe(reply).trim().isEmpty()) return result;
        PluginService ai = service.plugin().getServer().getPluginManager().getService("MBB-AI");
        if (ai == null) return result;
        try {
            JSONArray messages = new JSONArray();
            messages.add(service.message("system",reviewPrompt()));
            JSONObject input = new JSONObject(true);
            input.put("message",service.safe(content));
            input.put("reply",service.safe(reply));
            input.put("addressed",decision == null ? "" : service.safe(decision.addressed));
            input.put("speechRules",service.persona().speechRuleText(content));
            messages.add(service.message("user",input.toJSONString()));
            JSONObject params = new JSONObject(true);
            String profile = service.safe(service.config().replyReviewProfile).isEmpty()
                    ? service.config().aiProfile : service.config().replyReviewProfile.trim();
            params.put("profile",profile);
            params.put("maxTokens",service.config().replyReviewMaxTokens);
            params.put("temperature",0.1);
            params.put("reasoningEffort",service.config().replyReviewReasoningEffort);
            params.put("timeoutSeconds",service.config().replyReviewTimeoutSecond);
            params.put("sessionId","roleplay-review-"
                    +Integer.toHexString(service.persona().name.hashCode()));
            params.put("messages",messages);
            long startTime = System.currentTimeMillis();
            JSONObject response = ai.call("chat",params);
            RoleplayAiLog.log(service.plugin().getLogger(),"回复复核",groupID,response,
                    System.currentTimeMillis() - startTime);
            JSONObject parsed = service.parseJson(response == null ? "" : response.getString("content"));
            if (parsed == null) parsed = service.parseJson(response == null ? "" : response.getString("reasoningContent"));
            if (parsed == null) return result;
            result.score = parsed.getIntValue("score");
            result.pass = parsed.getBooleanValue("pass")
                    && result.score >= service.config().replyReviewMinScore;
            result.reason = service.shortText(service.safe(parsed.getString("reason")),120);
            String rewrite = service.safe(parsed.getString("rewrite")).trim();
            if (!rewrite.isEmpty() && !"<SKIP>".equalsIgnoreCase(rewrite)) {
                String rejectReason = RoleplayReplyTextValidator.rejectReason(reply,rewrite);
                if (rejectReason.isEmpty()) {
                    result.text = rewrite;
                } else {
                    service.plugin().getLogger().sendWarn("[复核] 群"+groupID+" 丢弃改写："
                            +rejectReason+" 原文="+service.shortText(reply,80)
                            +" 改写="+service.shortText(rewrite,80));
                }
            }
            if (!result.pass) {
                service.plugin().getLogger().sendInfo("[复核] 群"+groupID+" 分数="+result.score
                        +" 原因="+result.reason+" 改写="+service.shortText(result.text,80));
            }
            return result;
        } catch (Exception e) {
            service.plugin().getLogger().sendWarn("[复核] 群"+groupID+" 复核异常，使用原文："
                    +service.safe(e.getMessage()));
            return result;
        }
    }

    private String reviewPrompt() {
        RoleplayPersona persona = service.persona();
        return "你是角色回复复核器，只输出 JSON，不要解释。\n"
                +"角色核心：\n"+persona.coreText()+"\n"
                +"角色说话方式：\n"+persona.speechStyle+"\n"
                +"检查回复是否像该角色、是否有明显 AI 味、是否总结/说教/客服腔、是否重复啰嗦、"
                +"是否与当前消息有关、是否超过普通 QQ 聊天长度，以及是否像剧情台词、舞台动作或旁白。\n"
                +"如果有 speechRules，只检查命中的场景规则，不要额外补口癖或剧情内容。\n"
                +"输出格式：{\"pass\":true,\"score\":0,\"reason\":\"\",\"rewrite\":\"\"}。\n"
                +"只有确实需要改写时才填 rewrite；改写必须保持原意和角色语气，不要增加新事实，"
                +"不要角色名前缀、括号动作、心理旁白、镜头描写或 Markdown。";
    }
}
