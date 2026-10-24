package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.PluginService;

/**
 * 角色回复生成
 *
 * 负责回复消息装配、图片上下文合并、AI 调用和回复日志。
 */
public class RoleplayReplyGenerationService {
    private final RoleplayService service;

    public RoleplayReplyGenerationService(RoleplayService service) {
        this.service = service;
    }

    JSONObject reply(long groupID,long userID,String userName,String content,
                             boolean otherRoleBot,String relationship,RoleplayService.RecentImage imageContext,
                             RoleplayRouteDecision decision,long messageID,long selfID,
                             String eventRaw,boolean ownerUser) {
        return reply(groupID,userID,userName,content,otherRoleBot,relationship,imageContext,
                decision,null,0,messageID,selfID,eventRaw,ownerUser);
    }

    JSONObject reply(long groupID,long userID,String userName,String content,
                             boolean otherRoleBot,String relationship,RoleplayService.RecentImage imageContext,
                             RoleplayRouteDecision decision,String batchText,int batchCount,
                             long messageID,long selfID,String eventRaw,boolean ownerUser) {
        PluginService ai = service.plugin().getServer().getPluginManager().getService("MBB-AI");
        if (ai == null) return null;
        JSONArray messages = new JSONArray();
        String speechPrompt = service.speechPrompt(groupID,userID,content,otherRoleBot,decision);
        String systemPrompt = service.promptAssembler().buildSystemPrompt(
                groupID,userID,otherRoleBot,relationship,speechPrompt,content,decision,batchCount);
        messages.add(service.message("system",systemPrompt));
        String userText = "当前时间："+service.currentTimeText()+"\n";
        if (batchCount > 1) {
            userText += "最近群聊背景（只是旧上下文，不要优先回应这里）：\n"
                    +service.contextService().recentContext(groupID,messageID)+"\n\n"
                    +"本批连续消息（当前回合，按时间顺序，共 "+batchCount+" 条）：\n"+batchText+"\n"
                    +"这一批消息里最需要回应的人："+(userName == null ? "" : userName)
                    +"（QQ："+userID+"）\n"
                    +"当前关系："+relationship+"\n"
                    +"本批主消息指向："+addressingText(decision);
        } else {
            userText += "最近群聊背景（只是旧上下文，不要优先回应这里）：\n"
                    +service.contextService().recentContext(groupID,messageID)+"\n\n"
                    +"当前发言者："+(userName == null ? "" : userName)+"（QQ："+userID+"）\n"
                    +"当前关系："+relationship+"\n"
                    +"当前消息指向："+addressingText(decision)+"\n"
                    +"当前消息（必须优先回应）：\n"+content;
        }
        if (imageContext != null && !content.contains("[图片")) {
            String summary = service.imageSummary(imageContext);
            if (!summary.isEmpty()) {
                userText += "\n\n用户最近发送的图片：\n"+summary
                        +"\n请结合这张图片回答当前问题。";
            }
        }
        JSONObject userMessage = new JSONObject(true);
        userMessage.put("role","user");
        JSONObject imageData = imageContext == null ? null : service.imageData(imageContext);
        if (imageData != null && imageData.getBooleanValue("status")
                && !service.safe(imageData.getString("dataUri")).isEmpty()) {
            JSONArray contentArray = new JSONArray();
            JSONObject textPart = new JSONObject(true);
            textPart.put("type","text");
            textPart.put("text",userText);
            contentArray.add(textPart);
            JSONObject imageUrl = new JSONObject(true);
            imageUrl.put("url",imageData.getString("dataUri"));
            JSONObject imagePart = new JSONObject(true);
            imagePart.put("type","image_url");
            imagePart.put("image_url",imageUrl);
            contentArray.add(imagePart);
            userMessage.put("content",contentArray);
        } else {
            userMessage.put("content",userText);
        }
        String anchorInput = batchCount > 1
                ? service.safe(batchText)+"\n"+service.safe(content) : service.safe(content);
        String styleAnchor = service.promptAssembler().styleAnchorPrompt(anchorInput);
        if (!styleAnchor.isEmpty()) messages.add(service.message("system",styleAnchor));
        messages.add(userMessage);
        JSONObject params = new JSONObject(true);
        params.put("profile",service.config().aiProfile);
        params.put("maxTokens",imageContext == null
                ? service.config().replyMaxTokens
                : Math.max(service.config().replyMaxTokens,service.config().replyImageMaxTokens));
        params.put("reasoningEffort",service.config().replyReasoningEffort);
        params.put("sessionId","roleplay-"+groupID+"-"+service.contextToken(groupID));
        RoleplayToolContext toolContext = new RoleplayToolContext();
        toolContext.groupID = groupID;
        toolContext.userID = userID;
        toolContext.selfID = selfID;
        toolContext.messageID = messageID;
        toolContext.eventRaw = eventRaw == null ? "" : eventRaw;
        toolContext.ownerAuthorizedInternalNetwork = ownerUser;
        toolContext.userName = userName == null ? "" : userName;
        toolContext.relationship = relationship == null ? "" : relationship;
        toolContext.message = content == null ? "" : content;
        toolContext.channel = "group";
        toolContext.privacy = "group";
        long startTime = System.currentTimeMillis();
        JSONObject response = service.toolLoopService().run(groupID,toolContext,messages,params);
        long elapsed = System.currentTimeMillis() - startTime;
        RoleplayAiLog.log(service.plugin().getLogger(),"回复",groupID,response,elapsed);
        if (response != null) {
            JSONObject usage = response.getJSONObject("usage");
            JSONObject trace = new JSONObject(true);
            trace.put("message",service.shortText(content,240));
            trace.put("finishReason",service.safe(response.getString("finishReason")));
            trace.put("promptTokens",usage == null ? 0 : usage.getIntValue("promptTokens"));
            trace.put("completionTokens",usage == null ? 0 : usage.getIntValue("completionTokens"));
            trace.put("cachedPromptTokens",usage == null ? 0 : usage.getIntValue("cachedPromptTokens"));
            trace.put("elapsedMs",elapsed);
            trace.put("systemPromptChars",systemPrompt.length());
            trace.put("userPromptChars",userText.length());
            trace.put("reply",service.shortText(service.safe(response.getString("content")),240));
            JSONArray toolTrace = response.getJSONArray("toolTrace");
            if (toolTrace != null) trace.put("toolTrace",toolTrace);
            service.getTraceService().record(groupID,userID,0L,"reply",trace);
        }
        return response;
    }

    String addressingText(RoleplayRouteDecision decision) {
        String addressed = decision == null ? "none" : service.safe(decision.addressed);
        if ("direct".equals(addressed)) return "明确对角色说";
        if ("thread".equals(addressed)) return "延续角色参与的话题";
        if ("ambient".equals(addressed)) return "群聊氛围中可能接话";
        return "未明确指向角色";
    }
}
