package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.PluginService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 角色情绪语义分析
 *
 * 负责本地规则判断、AI 语义分析、事件冷却、重复消息检测和数值变化限幅。
 */
public class RoleplayEmotionAnalyzer {
    private final RoleplayEmotionService service;
    private final Map<String,Long> eventCooldownMap = new ConcurrentHashMap<>();
    private final Map<String,Long> analyzeCooldownMap = new ConcurrentHashMap<>();
    private final Map<String,RecentMessage> recentMessageMap = new ConcurrentHashMap<>();

    private static class RecentMessage {
        private final String content;
        private final long time;

        private RecentMessage(String content,long time) {
            this.content = content;
            this.time = time;
        }
    }

    public RoleplayEmotionAnalyzer(RoleplayEmotionService service) {
        this.service = service;
    }

    public void afterTurn(long groupID,long userID,String userName,String relationship,
                          String content,RoleplayEmotionService.LocalEvent localEvent,boolean direct,boolean otherRoleBot) {
        if (!service.config().emotionEnable || !service.config().emotionAnalyzeEnable || otherRoleBot) return;
        if (groupID <= 0 || userID <= 0 || service.safe(content).trim().isEmpty()) return;
        if ("off".equals(service.config().emotionAnalyzeMode)) return;
        if (!shouldAnalyze(direct,localEvent)) return;
        String key = groupID+"-"+userID;
        long now = System.currentTimeMillis();
        Long last = analyzeCooldownMap.get(key);
        if (last != null && now - last < service.config().emotionAnalyzeCooldownSecond * 1000L) return;
        analyzeCooldownMap.put(key,now);
        service.roleplayService().submitBackground(
                () -> analyzeEmotion(groupID,userID,userName,relationship,content,localEvent));
    }

    void analyzeEmotion(long groupID,long userID,String userName,String relationship,
                                String content,RoleplayEmotionService.LocalEvent localEvent) {
        PluginService ai = service.plugin().getServer().getPluginManager().getService("MBB-AI");
        if (ai == null) return;
        JSONArray messages = new JSONArray();
        messages.add(message("system",analyzeSystemPrompt(groupID,userID,userName,relationship,
                content,localEvent)));
        messages.add(message("user","请分析这条消息对角色情绪和用户关系的影响。"));
        JSONObject params = new JSONObject(true);
        String profile = service.config().emotionAnalyzeProfile == null
                || service.config().emotionAnalyzeProfile.trim().isEmpty()
                ? service.config().aiProfile : service.config().emotionAnalyzeProfile.trim();
        params.put("profile",profile);
        params.put("maxTokens",service.config().emotionAnalyzeMaxTokens);
        params.put("temperature",0.0);
        params.put("reasoningEffort",service.config().emotionAnalyzeReasoningEffort);
        params.put("sessionId","roleplay-emotion-"+groupID+"-"+userID);
        params.put("messages",messages);
        RoleplayAiMetadata.tagTemplate(params,"roleplay.emotion.analyze",
                RoleplayEmotionAnalyzer.class);
        long startTime = System.currentTimeMillis();
        JSONObject response = ai.call("chat",params);
        RoleplayAiLog.log(service.plugin().getLogger(),"情绪分析",groupID,response,
                System.currentTimeMillis() - startTime);
        JSONObject parsed = parseJson(response == null ? "" : response.getString("content"));
        if (parsed == null) parsed = parseJson(response == null ? "" : response.getString("reasoningContent"));
        if (parsed == null) {
            service.plugin().getLogger().sendWarn("[情绪分析] 群"+groupID+" 用户"+userID
                    +" 没有返回合法 JSON");
            return;
        }
        String eventType = service.safe(parsed.getString("event")).trim();
        if (eventType.isEmpty()) eventType = "ai";
        String reason = service.safe(parsed.getString("reason")).trim();
        boolean persistReason = parsed.getBooleanValue("persistReason");
        JSONObject deltas = parsed.getJSONObject("delta");
        RoleplayEmotionService.LocalEvent event = event("ai".equals(eventType) ? "semantic" : eventType,
                reason, true,
                intDelta(deltas,"valence"),intDelta(deltas,"energy"),
                intDelta(deltas,"patience"),delta(deltas,"affinity"),
                delta(deltas,"trust"),delta(deltas,"annoyance"));
        event.persistReason = persistReason;
        synchronized (service) {
            RoleplayEmotionService.MoodState mood = service.mood(groupID);
            RoleplayEmotionService.RelationState relation = service.relation(groupID,userID);
            RoleplayEmotionService.LocalEvent applied = service.applyEvent(groupID,userID,0L,mood,relation,event,"ai");
            service.plugin().getLogger().sendInfo("[情绪分析] 群"+groupID+" 用户"+userID
                    +" 事件="+applied.type
                    +" 心情"+service.formatDelta(applied.valenceDelta)
                    +" 耐心"+service.formatDelta(applied.patienceDelta)
                    +" 好感"+service.formatDelta(applied.affinityDelta)
                    +" 信任"+service.formatDelta(applied.trustDelta)
                    +" 厌烦"+service.formatDelta(applied.annoyanceDelta)
                    +(reason.isEmpty() ? "" : " 原因="+reason));
        }
        //同一轮调用顺手更新群员个人印象，不额外增加 AI 调用
        service.memberService().applyAi(groupID,userID,userName,parsed.getJSONObject("member"));
    }

    String analyzeSystemPrompt(long groupID,long userID,String userName,String relationship,
                                       String content,RoleplayEmotionService.LocalEvent localEvent) {
        RoleplayEmotionService.MoodState mood = service.mood(groupID);
        RoleplayEmotionService.RelationState relation = service.relation(groupID,userID);
        StringBuilder builder = new StringBuilder();
        builder.append("你是角色情绪分析器，只输出 JSON，不要解释。\n");
        builder.append("角色：").append(service.persona().name).append("\n");
        if (service.persona().personality != null && !service.persona().personality.isEmpty()) {
            builder.append("性格：").append(service.persona().personality).append("\n");
        }
        builder.append("当前群情绪：").append(mood.label)
                .append("，心情").append(service.levelText(mood.valence))
                .append("，精力").append(service.levelText(mood.energy))
                .append("，耐心").append(service.levelText(mood.patience)).append("\n");
        builder.append("当前用户：").append(service.safe(userName)).append("（QQ：").append(userID)
                .append("，关系：").append(service.safe(relationship)).append("）\n");
        builder.append("当前关系：").append(relation.emotionLabel)
                .append("，好感").append(service.levelText(relation.affinity))
                .append("，信任").append(service.levelText(relation.trust))
                .append("，厌烦").append(service.levelText(relation.annoyance)).append("\n");
        if (relation.emotionReason != null && !relation.emotionReason.isEmpty()) {
            builder.append("已有情绪原因：").append(relation.emotionReason)
                    .append("，强度 ").append(relation.reasonStrength).append("\n");
        }
        //把已有印象一起给模型，让它做增量修订而不是每次从零重写
        String existingProfile = service.memberService().promptText(groupID,userID);
        if (!existingProfile.isEmpty()) {
            builder.append("该群员已有的印象（在此基础上补充或修正，没有新信息就把对应字段留空）：\n")
                    .append(existingProfile);
        }
        if (localEvent != null && localEvent.isPresent()) {
            builder.append("本地规则初步判断：").append(localEvent.type)
                    .append("，原因：").append(localEvent.reason).append("\n");
        }
        builder.append("当前消息：").append(limitText(service.safe(content),1200)).append("\n");
        builder.append("判断原则：\n")
                .append("- 普通闲聊不要把临时心情写成长期原因\n")
                .append("- 冒名顶替、欺骗、辱骂、反复挑衅、持续骚扰可以写入用户级原因\n")
                .append("- 正面互动可以提升好感和信任，但原因要短\n")
                .append("- 原因只描述当前用户做了什么，不记录隐私信息\n")
                .append("- 原因最多 ").append(service.config().emotionReasonMaxChars).append(" 个字符\n")
                .append("- 单项数值变化必须用小数：提升建议 0.1 到 ")
                .append(service.config().emotionAnalyzeMaxDelta).append("，降低不超过 ")
                .append(service.config().emotionAnalyzeMaxDecreaseDelta).append("\n")
                .append("- 关系数值降得比升得慢：除非是明确的欺骗、辱骂或持续骚扰，否则不要给负值\n")
                .append("- 来源：“他在冒名顶替我”“他刚才夸过我”“他反复戳我”\n");
        builder.append("同时给这个群员维护一份个人印象，字段含义：\n")
                .append("- alias：只填写一个简短称呼；不确定就留空，不要把“别叫老师”之类说明句写进称呼\n")
                .append("- likes / dislikes：他明确说过的稳定喜好与不喜欢，不记录一次夸奖、一次玩笑或临时情绪\n")
                .append("- notes：跨天仍成立的相处方式，例如喜欢被吐槽或需要认真反馈；"
                        +"一次戳一戳、一次打招呼、一次玩笑不要写成长期相处方式\n")
                .append("- 只写他自己说过或明确表现出来的新信息；上面已有的印象不要原样重复，"
                        +"没有新信息就把对应字段留空字符串，也不要猜\n")
                .append("- 不确定是否长期成立的信息宁可不写；不能把角色自己说的话当成他的偏好\n")
                .append("- 发现旧印象不准时可以改写得更准确，但不要因为一次玩笑就推翻长期印象\n")
                .append("- 单项不超过 ").append(service.config().memberFieldMaxChars).append(" 个字符\n")
                .append("- 不记录真实姓名、住址、电话、账号等现实隐私\n");
        builder.append("输出格式：{\"event\":\"impersonation|attack|praise|friendly|neutral\",")
                .append("\"reason\":\"\",\"persistReason\":false,")
                .append("\"delta\":{\"valence\":0,\"energy\":0,\"patience\":0,")
                .append("\"affinity\":0.0,\"trust\":0.0,\"annoyance\":0.0},")
                .append("\"member\":{\"alias\":\"\",\"likes\":\"\",\"dislikes\":\"\",\"notes\":\"\"}}");
        return builder.toString();
    }

    boolean shouldAnalyze(boolean direct,RoleplayEmotionService.LocalEvent localEvent) {
        if ("direct".equals(service.config().emotionAnalyzeMode)) return direct;
        if ("significant".equals(service.config().emotionAnalyzeMode)) {
            if (localEvent != null && localEvent.isPresent() && localEvent.significant) return true;
            return direct;
        }
        return false;
    }

    boolean isImpersonation(String text,String userName) {
        String name = service.safe(userName).trim();
        if (service.matchesRoleName(name)) return true;
        for (String alias : service.roleAliases()) {
            if (alias.isEmpty()) continue;
            if (text.contains("我是"+alias) || text.contains("我才是"+alias)
                    || text.contains("冒充"+alias) || text.contains("冒名"+alias)
                    || text.contains("我就是"+alias)) {
                return true;
            }
        }
        return false;
    }

    boolean isAttack(String text) {
        String[] words = new String[]{"傻子","蠢货","滚开","滚蛋","闭嘴","垃圾","讨厌你",
                "烦人","恶心","死开","妈的","贱人","去死","废物","臭女人","笨蛋"};
        for (String word : words) {
            if (text.contains(word)) return true;
        }
        return false;
    }

    boolean isPraise(String text) {
        String[] words = new String[]{"厉害","可爱","漂亮","喜欢你","谢谢","好棒","不错",
                "辛苦了","真聪明","最强","好厉害","很棒"};
        for (String word : words) {
            if (text.contains(word)) return true;
        }
        return false;
    }

    boolean isRepeatedMessage(long groupID,long userID,String content) {
        String key = groupID+"-"+userID;
        long now = System.currentTimeMillis();
        RecentMessage previous = recentMessageMap.get(key);
        recentMessageMap.put(key,new RecentMessage(content,now));
        if (previous == null) return false;
        return content.equals(previous.content) && now - previous.time <= 60000L
                && content.length() >= 4;
    }

    boolean allowEvent(long groupID,long userID,String type,int seconds) {
        if (seconds <= 0) return true;
        String key = groupID+"-"+userID+"-"+type;
        long now = System.currentTimeMillis();
        Long last = eventCooldownMap.get(key);
        if (last != null && now - last < seconds * 1000L) return false;
        eventCooldownMap.put(key,now);
        return true;
    }

    RoleplayEmotionService.LocalEvent event(String type,String reason,boolean significant,
                             int valence,int energy,int patience,
                             double affinity,double trust,double annoyance) {
        RoleplayEmotionService.LocalEvent event = new RoleplayEmotionService.LocalEvent();
        event.type = type;
        event.reason = service.trimReason(reason);
        event.significant = significant;
        event.valenceDelta = valence;
        event.energyDelta = energy;
        event.patienceDelta = patience;
        event.affinityDelta = affinity;
        event.trustDelta = trust;
        event.annoyanceDelta = annoyance;
        return event;
    }

    double delta(JSONObject delta,String key) {
        if (delta == null) return 0;
        double value = delta.getDoubleValue(key);
        if (value > service.config().emotionAnalyzeMaxDelta) value = service.config().emotionAnalyzeMaxDelta;
        //降低的幅度单独限得更小，保证关系数值跌得比涨得慢
        if (value < -service.config().emotionAnalyzeMaxDecreaseDelta) {
            value = -service.config().emotionAnalyzeMaxDecreaseDelta;
        }
        return value;
    }

    int intDelta(JSONObject delta,String key) {
        if (delta == null) return 0;
        return delta.getIntValue(key);
    }

    JSONObject parseJson(String content) {
        String raw = service.safe(content).trim();
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

    JSONObject message(String role,String content) {
        JSONObject message = new JSONObject(true);
        message.put("role",role);
        message.put("content",content);
        return message;
    }

    String limitText(String value,int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0,max)+"...";
    }

    void clearCache() {
        eventCooldownMap.clear();
        analyzeCooldownMap.clear();
        recentMessageMap.clear();
    }
}
