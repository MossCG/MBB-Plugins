package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.PluginService;

import java.util.List;

/**
 * 回复候选复核与风格后处理
 *
 * 发送前统一处理角色口吻、AI 味、链接、长句简写、换行和分句；低于阈值或需要调整时使用模型给出的改写。
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
        return review(groupID,userID,content,reply,decision,
                Math.max(2,service.config().replyBeatMaxSegments));
    }

    public Result review(long groupID,long userID,String content,String reply,
                         RoleplayRouteDecision decision,int maxLines) {
        Result result = new Result();
        result.text = reply;
        if (!service.config().replyReviewEnable || service.safe(reply).trim().isEmpty()) return result;
        PluginService ai = service.plugin().getServer().getPluginManager().getService("MBB-AI");
        if (ai == null) return result;
        try {
            boolean proactive = service.config().styleProactiveEnable
                    && decision != null && "ambient".equals(decision.addressed);
            String styleTrigger = RoleplayStyleDetector.reason(service.config(),reply,proactive);
            boolean allowSentencePeriod = service.emotionService().allowSentencePeriod(groupID);
            String moodLabel = groupID <= 0 ? "私聊"
                    : service.emotionService().mood(groupID).label;
            JSONArray messages = new JSONArray();
            messages.add(service.message("system",
                    reviewPrompt(maxLines,moodLabel,allowSentencePeriod)));
            JSONObject input = new JSONObject(true);
            input.put("message",service.safe(content));
            input.put("reply",service.safe(reply));
            input.put("addressed",decision == null ? "" : service.safe(decision.addressed));
            input.put("speechRules",service.persona().speechRuleText(content));
            input.put("styleTrigger",styleTrigger);
            input.put("singleLimit",service.config().replySegmentMaxChars);
            input.put("maxLines",Math.max(1,maxLines));
            input.put("mood",moodLabel);
            input.put("allowSentencePeriod",allowSentencePeriod);
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
                    List<String> parts = RoleplayMessageSender.splitReply(rewrite,
                            service.config().replySegmentMaxChars,
                            service.config().replySplitPunctuation);
                    if (parts.size() > Math.max(1,maxLines)) {
                        rejectReason = "改写结果超过允许发送行数 "+Math.max(1,maxLines);
                    }
                }
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
            } else if (!result.text.equals(reply)) {
                service.plugin().getLogger().sendInfo("[复核] 群"+groupID
                        +" 后处理改写="+service.shortText(result.text,80));
            }
            return result;
        } catch (Exception e) {
            service.plugin().getLogger().sendWarn("[复核] 群"+groupID+" 复核异常，使用原文："
                    +service.safe(e.getMessage()));
            return result;
        }
    }

    private String reviewPrompt(int maxLines,String moodLabel,boolean allowSentencePeriod) {
        RoleplayPersona persona = service.persona();
        StringBuilder builder = new StringBuilder();
        builder.append("【任务】\n")
                .append("你是角色回复复核器兼风格后处理器，只输出 JSON，不要解释。\n\n")
                .append("【角色核心】\n").append(persona.coreText()).append("\n")
                .append("【角色说话方式】\n").append(persona.speechStyle).append("\n\n")
                .append("【检查项】\n")
                .append("检查回复是否像该角色、是否有明显 AI 味、是否总结或说教、是否有客服腔、是否重复啰嗦。\n")
                .append("检查是否与当前消息有关、是否像剧情台词、舞台动作或旁白。\n")
                .append("如正文包含模型凭空生成的网址、域名或短链接，应删除链接并保持句子通顺。\n")
                .append("任一单句超过 ").append(service.config().replySegmentMaxChars)
                .append(" 字时，应压缩简写、换行或分成短句，最多保留 ")
                .append(Math.max(1,maxLines)).append(" 行。\n")
                .append("每行必须能作为独立 QQ 消息发送，不要使用编号、项目符号或 Markdown。\n")
                .append("默认不要在句尾使用句号。需要断句时优先换行，把句尾或句中的“。”换成新的消息节拍。\n")
                .append("只有心情烦躁、低落或闹别扭时才允许保留句号；当前心情：")
                .append(moodLabel).append("，允许句号=")
                .append(allowSentencePeriod ? "是" : "否").append("。\n")
                .append("如果有 speechRules，只检查命中的场景规则，不要额外补口癖或剧情内容。\n\n")
                .append("【固定关系称呼】\n")
                .append("botOwner 固定是角色的妈妈。“妈妈”不是不符合角色风格的词，不能仅因出现“妈妈”就判定异常或改写。\n")
                .append("“妈妈、爸爸、姐姐、妹妹、哥哥、弟弟、老师、主人”等固定称呼必须原样保留，不能替换成近义词。\n\n")
                .append("【输出格式】\n")
                .append("{\"pass\":true,\"score\":0,\"reason\":\"\",\"rewrite\":\"\"}\n\n")
                .append("【改写约束】\n")
                .append("只有确实需要改写时才填 rewrite；需要改写时 pass 设为 false。\n")
                .append("改写必须保持原意、关键事实和角色语气，不要增加新事实。\n")
                .append("可以删除重复、填充词、书面连接词和空泛铺陈，做不丢信息的压缩简写。\n")
                .append("不要角色名前缀、括号动作、心理旁白、镜头描写或 Markdown。\n");
        return builder.toString();
    }
}
