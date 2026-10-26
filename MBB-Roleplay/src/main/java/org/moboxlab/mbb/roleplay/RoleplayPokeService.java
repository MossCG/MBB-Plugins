package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Event.NoticeEvent;
import org.moboxlab.moboxbot.API.OneBot.OneBotClient;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 戳一戳处理
 *
 * 负责戳一戳事件过滤、情绪结算、短回复和回戳动作。
 */
public class RoleplayPokeService {
    private final RoleplayService service;
    private final AtomicLong pokeSequence = new AtomicLong(System.currentTimeMillis() * 1000L);

    public RoleplayPokeService(RoleplayService service) {
        this.service = service;
    }

    public void handlePoke(NoticeEvent event) {
        RoleplayConfig config = service.config();
        if (event == null || !config.enable || !config.pokeReplyEnable) return;
        long groupID = event.getGroupID();
        long userID = event.getUserID();
        long selfID = event.getRaw().getLongValue("self_id");
        if (groupID <= 0 || userID <= 0 || selfID <= 0) return;
        if (event.getTargetID() != selfID || userID == selfID) return;
        if (!service.isGroupEnabled(groupID)) return;
        if (service.blacklistService().contains(groupID,userID)) {
            service.plugin().getLogger().sendInfo("[角色] 群"+groupID+" 忽略黑名单用户 "
                    +userID+" 的戳一戳");
            return;
        }
        service.replyFeedbackService().observePoke(groupID,userID);
        long turnID = -pokeSequence.incrementAndGet();
        String eventRaw = event.getRaw().toJSONString();
        long finalTurnID = turnID;
        service.submitTurn(() -> runPokeTurn(groupID,userID,selfID,finalTurnID,eventRaw));
    }

    private void runPokeTurn(long groupID,long userID,long selfID,long turnID,String eventRaw) {
        if (!service.canReply(groupID)) return;
        RoleplayRouteDecision decision = new RoleplayRouteDecision();
        decision.addressed = "direct";
        decision.reply = true;
        decision.confidence = 1.0;
        decision.chance = 1.0;

        JSONObject member = pokeMember(groupID,userID);
        String userName = pokeMemberName(member,userID);
        String relationship = pokeRelationship(member,userID);
        RoleplayEmotionService.LocalEvent emotionEvent = service.emotionService()
                .observePoke(groupID,userID,userName);
        boolean pokeSpam = emotionEvent.isPresent() && "poke-spam".equals(emotionEvent.type);
        RoleplayConversationState pokeState = service.state(groupID);
        int pokeVariant = pokeState.pokeVariant++ % 6;
        double affinity = service.emotionService().affinity(groupID,userID);
        int patience = service.emotionService().patience(groupID);
        boolean allowPokeBack = service.config().pokeBackEnable;
        String hint;
        if (pokeSpam) {
            allowPokeBack = false;
            hint = "对方在很短时间里连续戳了你很多次，已经有点烦了：可以用一句短促的话让他别戳了，"
                    +"或者敷衍、装没反应；不要辱骂，也不要用攻击性语气。";
        } else if (patience < 35) {
            hint = pokeVariant % 3 == 0
                    ? "服务端心情不太好，用一句短促的吐槽回敬，不要长篇解释。"
                    : "服务端耐心偏低，可以简短回敬；不要连续重复同一句话。";
        } else if (affinity >= service.config().intimacyVeryCloseAffinity) {
            allowPokeBack = pokeVariant % 4 == 1;
            hint = "你和对方已经非常亲近，可以撒娇、害羞、假装嫌弃，或用亲昵的短句回应。";
        } else if (affinity >= service.config().intimacyCloseAffinity) {
            allowPokeBack = pokeVariant % 3 == 1;
            hint = "你和对方关系亲近，可以自然吐槽、反戳、装作不耐烦或小声抱怨。";
        } else {
            allowPokeBack = pokeVariant % 2 == 1;
            hint = "按当前关系选择回应方式，不要每次都戳回去，可以只吐槽、反问或假装没反应。";
        }
        if (!allowPokeBack) {
            hint += "这一轮不要戳回去，也不要输出 poke-back 动作。";
        } else {
            hint += "如果合适可以戳回去，但正文不要只机械重复“戳回去”，换一句角色化表达。";
        }
        hint += "本轮只回复一条短消息，最多 "+service.config().pokeReplyMaxChars
                +" 字，不要换行，不要分段，不要补充无关内容。";
        decision.reason = "被戳一戳："+hint;
        if (service.config().pokeBackEnable && allowPokeBack) decision.actions.add("poke-back");
        String content = "[戳一戳] "+userName+" 戳了你一下。本轮建议："+hint;
        JSONObject result = service.reply(groupID,userID,userName,content,false,relationship,null,
                decision,turnID,selfID,eventRaw,service.isOwnerUser(userID));
        if (result == null || !result.getBooleanValue("status")) {
            service.emotionService().afterTurn(groupID,userID,userName,relationship,content,
                    emotionEvent,true,false);
            return;
        }
        RoleplayReplyDraft draft = RoleplayReplyDraft.parse(result.getString("content"));
        if (draft.malformed) {
            service.plugin().getLogger().sendWarn("[角色] 戳一戳 群"+groupID+" 结构化结果无法解析，本轮跳过");
            return;
        }
        String text = service.safe(draft.text).trim();
        boolean sendText = !text.isEmpty() && !"<SKIP>".equalsIgnoreCase(text);
        if (sendText) {
            service.ensurePokeBackAction(draft,decision,text);
            text = service.messageSender().normalizeSentencePeriods(groupID,text);
            text = normalizePokeReply(text);
            sendText = !text.isEmpty();
        }
        OneBotClient client = service.plugin().getServer().getOneBotClient();
        boolean sent = false;
        if (sendText && client != null) {
            sent = service.messageSender().sendSingleMessage(client,groupID,selfID,userID,text,false,0L);
            if (sent && service.config().conversationAppendEnable) {
                service.conversationContextService().append("group",groupID,"assistant",text,
                        "group:"+turnID+":assistant","group:"+turnID);
            }
        }
        service.plugin().getLogger().sendInfo("[角色] 戳一戳 群"+groupID+" 用户"+userID
                +" 回复="+(sendText ? service.shortText(text,60) : "无"));
        service.executeSkillCalls(decision,draft,groupID,userID,0L,selfID,userName,relationship,content);
        service.emotionService().afterTurn(groupID,userID,userName,relationship,content,
                emotionEvent,true,false);
    }

    private String normalizePokeReply(String text) {
        String value = service.safe(text).replaceAll("\\s+"," ").trim();
        int maxChars = Math.max(8,service.config().pokeReplyMaxChars);
        if (value.length() <= maxChars) return value;
        int cut = service.messageSender().findSplitCut(value,maxChars,true);
        if (cut <= 0) cut = service.messageSender().findSplitCut(value,maxChars,false);
        if (cut <= 0) cut = maxChars;
        String result = value.substring(0,cut).trim();
        if (cut < value.length() && service.messageSender().isWeakSplitPunctuation(value.charAt(cut - 1))) {
            result = service.messageSender().trimWeakSplitPunctuation(result);
        }
        if (result.isEmpty()) result = value.substring(0,maxChars).trim();
        return result;
    }

    private JSONObject pokeMember(long groupID,long userID) {
        OneBotClient client = service.plugin().getServer().getOneBotClient();
        if (client == null) return null;
        try {
            JSONObject response = client.getGroupMemberInfo(groupID,userID);
            return response == null ? null : response.getJSONObject("data");
        } catch (Exception e) {
            return null;
        }
    }

    private String pokeMemberName(JSONObject member,long userID) {
        if (member != null) {
            String card = service.safe(member.getString("card")).trim();
            if (!card.isEmpty()) return card;
            String nickname = service.safe(member.getString("nickname")).trim();
            if (!nickname.isEmpty()) return nickname;
        }
        return String.valueOf(userID);
    }

    private String pokeRelationship(JSONObject member,long userID) {
        if (service.isOwner(userID)) return "妈妈";
        String role = member == null ? "" : service.safe(member.getString("role")).trim();
        if ("owner".equals(role) || "admin".equals(role)) return "老师";
        return "朋友";
    }
}
