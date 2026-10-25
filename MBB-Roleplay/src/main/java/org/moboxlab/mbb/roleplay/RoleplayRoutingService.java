package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Event.GroupMessageEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 角色路由与发言指向
 *
 * 负责规则兜底、AI 路由、动作清单、情绪提示和艾特/引用目标说明。
 */
public class RoleplayRoutingService {
    private final RoleplayService service;

    public RoleplayRoutingService(RoleplayService service) {
        this.service = service;
    }

    RoleplayRouteDecision routeDecision(RoleplayDecisionEngine.Signals signals,
                                                GroupMessageEvent event,long groupID,long selfID,
                                                String content) {
        RoleplayRouteDecision ruleDecision = RoleplayDecisionEngine.decide(service.config(),signals);
        if (service.config().toolEnable) ruleDecision.actions = new ArrayList<>();
        ruleDecision.threadHint = signals.threadHint;
        if (!service.config().routerEnable) {
            traceDecision(ruleDecision,event,groupID,content,"rule");
            return ruleDecision;
        }
        RoleplayRouteDecision routed = service.router().route(service.config(),service.persona(),service.skillRegistry(),service.state(groupID),
                content,service.senderName(event),service.relationshipLabel(event,signals.otherRoleBot),
                service.contextService().recentContext(groupID),emotionRouterText(groupID,event.getUserID(),
                        signals.otherRoleBot),addressingHint(event,groupID,signals),
                signals.topicHint);
        if (routed == null) {
            service.plugin().getLogger().sendWarn("[角色] 群"+groupID+" 路由失败，回退规则决策");
            return ruleDecision;
        }
        if (signals.direct) {
            routed.reply = true;
            routed.addressed = "direct";
            routed.chance = RoleplayMultiRolePolicy.directChance(true,routed.chance);
            routed.confidence = Math.max(routed.confidence,routed.chance);
        }
        routed.quoteRequired = service.config().quoteReplyEnable
                && (signals.quotingSelf || signals.mentioningSelf);
        routed.otherRoleBot = signals.otherRoleBot;
        if (signals.otherRoleBot) {
            //机器人互聊必须服从规则层概率，AI 路由只能决定“想不想接”，不能把概率抬到 1.0
            routed.addressed = "ambient";
            routed.chance = Math.min(routed.chance,service.config().otherRoleBotReplyChance);
            routed.confidence = Math.min(routed.confidence,service.config().otherRoleBotReplyChance);
        }
        routed.actions = service.config().toolEnable
                ? new ArrayList<>() : service.skillRegistry().actionIds(service.config());
        routed.threadHint = signals.threadHint;
        traceDecision(routed,event,groupID,content,"ai");
        return routed;
    }

    private void traceDecision(RoleplayRouteDecision decision,GroupMessageEvent event,
                               long groupID,String content,String source) {
        if (decision == null) return;
        JSONObject trace = new JSONObject(true);
        trace.put("source",source);
        trace.put("addressed",service.safe(decision.addressed));
        trace.put("reply",decision.reply);
        trace.put("chance",decision.chance);
        trace.put("confidence",decision.confidence);
        trace.put("reason",service.shortText(decision.reason,240));
        trace.put("skills",decision.skills);
        trace.put("materials",decision.materials);
        trace.put("message",service.shortText(content,240));
        service.getTraceService().record(groupID,event == null ? 0L : event.getUserID(),
                event == null ? 0L : event.getMessageID(),"route",trace);
    }

    String emotionRouterText(long groupID,long userID,boolean otherRoleBot) {
        return service.emotionService().routerText(groupID,userID,otherRoleBot);
    }

    String addressingHint(GroupMessageEvent event,long groupID,
                                  RoleplayDecisionEngine.Signals signals) {
        if (signals == null) return "";
        List<String> parts = new ArrayList<>();
        parts.add("艾特目标："+atTargetInfo(event));
        parts.add("引用目标："+quotedTargetInfo(groupID,event));
        if (signals.mentioningSelf) parts.add("指向角色：艾特了角色本人");
        if (signals.quotingSelf) parts.add("指向角色：回复的是角色本人上一条消息");
        if (signals.direct && !signals.mentioningSelf) parts.add("指向角色：开头或句首叫了角色的名字");
        if (signals.addressedToOtherMember) parts.add("其他指向：艾特的是其他群成员，不是角色");
        if (signals.addressedToOtherRole) parts.add("其他指向：艾特的是另一个角色机器人");
        if (signals.threadHint != null && !signals.threadHint.trim().isEmpty()) {
            parts.add(signals.threadHint.trim());
        }
        if (parts.size() <= 2) parts.add("指向角色：未明确指向角色，需要结合上下文判断");
        return String.join("；",parts);
    }

    String atTargetInfo(GroupMessageEvent event) {
        if (event == null || event.getMessage() == null) return "无";
        List<String> targets = new ArrayList<>();
        JSONArray message = event.getMessage();
        for (int i = 0; i < message.size(); i++) {
            JSONObject segment = message.getJSONObject(i);
            if (segment == null || !"at".equals(segment.getString("type"))) continue;
            JSONObject data = segment.getJSONObject("data");
            long atID = RoleplayTextUtils.jsonPositiveLong(data,"qq");
            if (atID > 0) targets.add("QQ "+atID);
        }
        return targets.isEmpty() ? "无" : String.join("、",targets);
    }

    String quotedTargetInfo(long groupID,GroupMessageEvent event) {
        long quotedID = service.quotedMessageID(event);
        if (quotedID <= 0) return "无";
        JSONObject row = service.storage().queryOne(
                "SELECT `userID`,`userName` FROM `"+RoleplayService.MSG_TABLE+"` WHERE `groupID`=? AND `messageID`=? LIMIT 1",
                groupID,quotedID);
        if (row == null) return "未知（消息ID："+quotedID+"）";
        String name = service.safe(row.getString("userName"));
        long userID = row.getLongValue("userID");
        return (name.isEmpty() ? "未知" : name)+"（QQ："+userID+"）";
    }
}
