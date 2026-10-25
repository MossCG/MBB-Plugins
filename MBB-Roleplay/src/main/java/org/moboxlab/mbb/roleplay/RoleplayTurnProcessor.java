package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Event.GroupMessageEvent;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 角色回合处理
 *
 * 负责单条与合批回合、延后回合、表情包等待窗口和同群串行收尾。
 */
public class RoleplayTurnProcessor {
    private final RoleplayService service;
    private final Map<String,PendingTurn> pendingTurnMap = new ConcurrentHashMap<>();
    private final Map<String,Integer> pendingGenerationMap = new ConcurrentHashMap<>();

    public RoleplayTurnProcessor(RoleplayService service) {
        this.service = service;
    }

    boolean processGroupMessage(GroupMessageEvent event) {
        RoleplayService.PreparedMessage prepared = service.prepareMessage(event);
        if (prepared == null) return false;
        return handleSingleTurn(prepared);
    }

    boolean processMessageBatch(long groupID,List<GroupMessageEvent> batch) {
        List<RoleplayService.PreparedMessage> messages = new ArrayList<>();
        for (GroupMessageEvent event : batch) {
            RoleplayService.PreparedMessage prepared = service.prepareMessage(event);
            if (prepared != null) messages.add(prepared);
        }
        if (messages.isEmpty()) return false;
        if (messages.size() == 1) {
            return handleSingleTurn(messages.get(0));
        }
        handleBatchTurn(groupID,messages);
        return false;
    }

    private boolean handleSingleTurn(RoleplayService.PreparedMessage prepared) {
        GroupMessageEvent event = prepared.event;
        long groupID = prepared.groupID;
        String content = service.mergeStickerEmotion(prepared.content,prepared.stickerEmotion);
        // 冷却或超频时直接跳过，不必再花一次路由调用
        if (service.rateLimited(groupID)) return false;
        boolean mediaOnly = prepared.hasImage
                && !service.hasMeaningfulText(event.getMessage());
        RoleplayReplyWillingnessService.Result willingness =
                service.replyWillingnessService().evaluate(groupID,prepared.signals,
                        content,mediaOnly,1);
        if ("enforce".equals(willingness.mode) && !willingness.allowed) return false;
        RoleplayRouteDecision decision = service.routeDecision(prepared.signals,event,groupID,
                prepared.selfID,content);
        if (decision == null || !decision.reply) return false;
        decision.chance = service.emotionService().adjustReplyChance(groupID,prepared.userID,
                decision.chance,prepared.direct,prepared.otherRoleBot);
        decision.chance = service.replyFeedbackService().adjustChance(groupID,decision.chance,
                prepared.direct,prepared.otherRoleBot);
        decision.chance = service.attentionService().adjustChance(groupID,prepared.attentionTopic,
                decision.chance,prepared.direct,prepared.otherRoleBot);
        if (decision.chance < 1.0 && Math.random() >= decision.chance) {
            service.plugin().getLogger().sendInfo("[角色] 群"+groupID+" 概率跳过 概率="
                    +decision.chance+" 原因="+decision.reason);
            return false;
        }
        if (prepared.otherRoleBot) {
            if (!service.hubService().botChainAllow(groupID)) return false;
        } else {
            service.hubService().botChainReset(groupID);
        }
        boolean defer = shouldDeferTurn(event,prepared.otherRoleBot,prepared.hasImage);
        //延后回合把限流判定留到真正生成回复时，避免这里刚登记的冷却把同一轮的延后执行挡掉
        if (defer) {
            if (service.rateLimited(groupID)) return false;
        } else if (!service.canReply(groupID)) {
            return false;
        }
        service.plugin().getLogger().sendInfo("[角色] 群"+groupID+" 决策 addressed="+decision.addressed
                +" 概率="+decision.chance
                +" 技能="+decision.skills
                +" 资料="+decision.materials
                +" 引用="+(decision.quoteRequired ? "是" : "否")
                +" 原因="+decision.reason);
        if (defer) {
            deferTurn(event,groupID,prepared.selfID,prepared.userName,prepared.relationship,
                    content,prepared.imageContext,prepared.otherRoleBot,decision,
                    prepared.emotionEvent);
            return true;
        }
        String turnKey = "m"+event.getMessageID();
        boolean multi = service.isMultiRoleAddress(event,content,prepared.selfID);
        RoleplayHubService.TurnResult turn = service.hubService().claimTurn(groupID,turnKey,
                service.hubService().turnScore(decision,prepared.direct),multi);
        if (!turn.granted) return false;
        if (turn.staggerMs > 0) service.sleepQuietly(turn.staggerMs);
        service.hubService().typingStart(groupID,turnKey);
        boolean handled = false;
        try {
            JSONObject result = service.reply(groupID,prepared.userID,prepared.userName,content,
                    prepared.otherRoleBot,prepared.relationship,prepared.imageContext,decision,
                    event.getMessageID(),prepared.selfID,event.getRaw().toJSONString(),
                    service.isOwnerUser(prepared.userID));
            if (result == null || !result.getBooleanValue("status")) {
                service.plugin().getLogger().sendWarn("[角色] 群"+groupID+" 回复生成失败，本轮不发送");
                return false;
            }
            if (!service.hubService().turnStillMine(groupID,turnKey)) {
                service.plugin().getLogger().sendInfo("[角色] 群"+groupID
                        +" 回合权已被更高优先级的角色接管，放弃本次发送");
                return false;
            }
            service.replyExecutor().processReplyResult(event,groupID,prepared.selfID,prepared.userName,prepared.relationship,
                    content,result,decision,prepared.emotionEvent);
            handled = true;
        } finally {
            service.hubService().typingStop(groupID,turnKey);
            // 成功回复后让租约自然过期，避免稍后才决定回复的另一个 bot 再补一遍
            if (!handled) service.hubService().turnRelease(groupID,turnKey);
        }
        return false;
    }

    private void handleBatchTurn(long groupID,List<RoleplayService.PreparedMessage> messages) {
        RoleplayService.PreparedMessage primary = primaryMessage(messages);
        if (service.rateLimited(groupID)) return;
        String batchText = batchPromptText(messages);
        RoleplayContextService.TopicFatigue batchFatigue = service.contextService().topicFatigue(groupID,batchText);
        primary.signals.topicFatigued = batchFatigue.fatigued();
        primary.signals.topicHint = batchFatigue.hint();
        boolean mediaOnly = primary.hasImage
                && !service.hasMeaningfulText(primary.event.getMessage());
        RoleplayReplyWillingnessService.Result willingness =
                service.replyWillingnessService().evaluate(groupID,primary.signals,
                        batchText,mediaOnly,messages.size());
        if ("enforce".equals(willingness.mode) && !willingness.allowed) return;
        RoleplayRouteDecision decision = service.routeDecision(primary.signals,primary.event,groupID,
                primary.selfID,batchText);
        if (decision == null || !decision.reply) return;
        decision.chance = service.emotionService().adjustReplyChance(groupID,primary.userID,
                decision.chance,primary.direct,primary.otherRoleBot);
        decision.chance = service.replyFeedbackService().adjustChance(groupID,decision.chance,
                primary.direct,primary.otherRoleBot);
        decision.chance = service.attentionService().adjustChance(groupID,primary.attentionTopic,
                decision.chance,primary.direct,primary.otherRoleBot);
        if (decision.chance < 1.0 && Math.random() >= decision.chance) {
            service.plugin().getLogger().sendInfo("[角色] 群"+groupID+" 合批概率跳过 概率="
                    +decision.chance+" 原因="+decision.reason);
            return;
        }
        if (primary.otherRoleBot) {
            if (!service.hubService().botChainAllow(groupID)) return;
        } else {
            service.hubService().botChainReset(groupID);
        }
        if (!service.canReply(groupID)) {
            service.plugin().getLogger().sendInfo("[角色] 群"+groupID+" 合批回合被回复冷却拦下，本轮不回复");
            return;
        }
        service.plugin().getLogger().sendInfo("[角色] 群"+groupID+" 合批 "+messages.size()
                +" 条 最早="+batchOldestAgeSecond(messages)+"秒前 决策 addressed="+decision.addressed
                +" 概率="+decision.chance
                +" 技能="+decision.skills
                +" 资料="+decision.materials
                +" 原因="+decision.reason);
        String turnKey = "b"+messages.get(messages.size() - 1).event.getMessageID();
        boolean multi = service.isMultiRoleAddress(primary.event,batchText,primary.selfID);
        RoleplayHubService.TurnResult turn = service.hubService().claimTurn(groupID,turnKey,
                service.hubService().turnScore(decision,primary.direct),multi);
        if (!turn.granted) return;
        if (turn.staggerMs > 0) service.sleepQuietly(turn.staggerMs);
        service.hubService().typingStart(groupID,turnKey);
        boolean handled = false;
        try {
            JSONObject result = service.reply(groupID,primary.userID,primary.userName,batchText,
                    primary.otherRoleBot,primary.relationship,primary.imageContext,decision,
                    batchText,messages.size(),primary.event.getMessageID(),primary.selfID,
                    primary.event.getRaw().toJSONString(),service.isOwnerUser(primary.userID));
            if (result == null || !result.getBooleanValue("status")) {
                service.plugin().getLogger().sendWarn("[角色] 群"+groupID+" 合批回复生成失败，本轮不发送");
                return;
            }
            if (!service.hubService().turnStillMine(groupID,turnKey)) {
                service.plugin().getLogger().sendInfo("[角色] 群"+groupID
                        +" 回合权已被更高优先级的角色接管，放弃本次发送");
                return;
            }
            service.replyExecutor().processBatchReplyResult(groupID,primary.selfID,messages,primary,decision,result);
            handled = true;
        } finally {
            service.hubService().typingStop(groupID,turnKey);
            if (!handled) service.hubService().turnRelease(groupID,turnKey);
        }
    }

    private RoleplayService.PreparedMessage primaryMessage(List<RoleplayService.PreparedMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).direct) return messages.get(i);
        }
        return messages.get(messages.size() - 1);
    }

    private String batchPromptText(List<RoleplayService.PreparedMessage> messages) {
        StringBuilder builder = new StringBuilder();
        builder.append("以下 ").append(messages.size())
                .append(" 条消息在很短时间内连续到达，按时间顺序排列，可能来自不同的人")
                .append("（只回应这里列出的消息，最近群聊只是背景，不要接已经翻篇的旧话题）：\n");
        for (int i = 0; i < messages.size(); i++) {
            RoleplayService.PreparedMessage message = messages.get(i);
            builder.append("[").append(i + 1).append("] ")
                    .append(messageTimeText(message.event)).append(" ")
                    .append(message.userName).append("（QQ：").append(message.userID).append("）");
            if (message.otherRoleBot) builder.append("（另一个角色机器人）");
            if (message.direct) builder.append("（直接提到你）");
            builder.append("（艾特：").append(service.atTargetInfo(message.event))
                    .append("；引用：").append(service.quotedTargetInfo(message.groupID,message.event)).append("）");
            builder.append("：").append(service.safe(message.content).trim().replace("\n"," "));
            if (!message.stickerEmotion.isEmpty()
                    && i == lastMessageIndexOfUser(messages,message.userID)) {
                builder.append("（随后补了一个表情包，情绪：").append(message.stickerEmotion).append("）");
            }
            builder.append("\n");
        }
        return builder.toString();
    }

    private String messageTimeText(GroupMessageEvent event) {
        long time = service.eventTimeMillis(event);
        if (time <= 0) return "";
        return new SimpleDateFormat("HH:mm:ss",Locale.CHINA).format(new Date(time));
    }

    private long batchOldestAgeSecond(List<RoleplayService.PreparedMessage> messages) {
        long now = System.currentTimeMillis();
        long oldest = 0L;
        for (RoleplayService.PreparedMessage message : messages) {
            long time = service.eventTimeMillis(message.event);
            if (time <= 0) continue;
            long age = (now - time) / 1000L;
            if (age > oldest) oldest = age;
        }
        return oldest;
    }

    private int lastMessageIndexOfUser(List<RoleplayService.PreparedMessage> messages,long userID) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).userID == userID) return i;
        }
        return -1;
    }

    private boolean shouldDeferTurn(GroupMessageEvent event,boolean otherRoleBot,boolean hasImage) {
        return service.config().stickerAttachEnable
                && !otherRoleBot
                && !hasImage
                && !service.hasStickerContent(event.getMessage());
    }

    private void deferTurn(GroupMessageEvent event,long groupID,long selfID,String userName,
                           String relationship,String content,RoleplayService.RecentImage imageContext,
                           boolean otherRoleBot,RoleplayRouteDecision decision,
                           RoleplayEmotionService.LocalEvent emotionEvent) {
        String key = service.recentImageKey(groupID,event.getUserID());
        int generation = nextPendingGeneration(key);
        PendingTurn pending = new PendingTurn(event,selfID,userName,relationship,
                content,imageContext,generation,otherRoleBot,decision,emotionEvent);
        pending.stickerEmotion = service.recentStickerEmotion(groupID,event.getUserID());
        //结尾正好是角色名字或悬挂连接词时，判定为还没说完的半句，等同一用户补充
        pending.incomplete = service.looksIncomplete(content);
        pending.hardDeadline = System.currentTimeMillis() + service.config().stickerAttachMaxWaitSecond * 1000L;
        pendingTurnMap.put(key,pending);
        //事件驱动等待：表情包在等待前就已经附带时不再空等窗口
        int delay = pending.stickerEmotion.isEmpty() ? service.config().stickerAttachWindowSecond : 0;
        service.plugin().getServer().getPluginManager().runTaskLater(service.plugin(),
                () -> executePendingTurn(key,generation),delay);
    }

    void wakePendingTurnForUser(long groupID,GroupMessageEvent event) {
        if (!service.config().stickerAttachWaitUntilNextMessage) return;
        long userID = event.getUserID();
        String key = service.recentImageKey(groupID,userID);
        PendingTurn pending = pendingTurnMap.get(key);
        if (pending == null) return;
        if (pending.incomplete) {
            String text = service.extractContent(event.getMessage());
            if (text != null && !text.trim().isEmpty()) {
                pending.appendContent(text);
                //并入的这句仍然要写进消息流水，否则记忆整理看不到它
                service.recordMessage(event,text,false);
                if (event.getMessageID() > 0) {
                    if (service.mergedMessageIDs.size() > 500) service.mergedMessageIDs.clear();
                    service.mergedMessageIDs.add(event.getMessageID());
                }
                service.plugin().getLogger().sendInfo("[角色] 群"+groupID+" 合并同一用户的补充发言："
                        +service.shortText(text,40));
            }
        }
        service.plugin().getServer().getPluginManager().runTaskLater(service.plugin(),
                () -> executePendingTurn(key,pending.generation),0);
    }

    private void executePendingTurn(String key,int generation) {
        PendingTurn pending = pendingTurnMap.get(key);
        if (pending == null || pending.generation != generation) return;
        if (pending.stickerPending) {
            long now = System.currentTimeMillis();
            if (now < pending.hardDeadline) {
                service.plugin().getServer().getPluginManager().runTaskLater(service.plugin(),
                        () -> executePendingTurn(key,generation),1);
                return;
            }
            service.plugin().getLogger().sendWarn("[角色] 群"+pending.event.getGroupID()
                    +" 等待表情包识别超时，按无表情包继续。");
        }
        //只有真正摘掉待处理回合的那一次调用继续往下走，避免并发唤醒导致同一回合回两次
        if (!pendingTurnMap.remove(key,pending)) return;
        service.submitTurn(() -> runPendingTurn(pending));
    }

    private void runPendingTurn(PendingTurn pending) {
        long groupID = pending.event.getGroupID();
        try {
            if (service.blacklistService().contains(groupID,pending.event.getUserID())) {
                service.plugin().getLogger().sendInfo("[角色] 群"+groupID+" 跳过已拉黑用户的待处理回合 "
                        +pending.event.getUserID());
                return;
            }
            if (!service.canReply(groupID)) {
                service.plugin().getLogger().sendInfo("[角色] 群"+groupID
                        +" 待处理回合被回复冷却或频率限制拦下，本次不回复");
                return;
            }
            String content = service.mergeStickerEmotion(pending.content,pending.stickerEmotion);
            String turnKey = "m"+pending.event.getMessageID();
            boolean direct = "direct".equals(pending.decision.addressed);
            boolean multi = service.isMultiRoleAddress(pending.event,content,pending.selfID);
            RoleplayHubService.TurnResult turn = service.hubService().claimTurn(groupID,turnKey,
                    service.hubService().turnScore(pending.decision,direct),multi);
            if (!turn.granted) return;
            if (turn.staggerMs > 0) service.sleepQuietly(turn.staggerMs);
            service.hubService().typingStart(groupID,turnKey);
            boolean handled = false;
            try {
                JSONObject result = service.reply(groupID,pending.event.getUserID(),pending.userName,content,
                        pending.otherRoleBot,pending.relationship,pending.imageContext,pending.decision,
                        pending.event.getMessageID(),pending.selfID,
                        pending.event.getRaw().toJSONString(),service.isOwnerUser(pending.event.getUserID()));
                if (result == null || !result.getBooleanValue("status")) {
                    service.plugin().getLogger().sendWarn("[角色] 群"+groupID+" 待处理回合回复生成失败，本次不发送");
                    return;
                }
                if (!service.hubService().turnStillMine(groupID,turnKey)) {
                    service.plugin().getLogger().sendInfo("[角色] 群"+groupID
                            +" 回合权已被更高优先级的角色接管，放弃本次发送");
                    return;
                }
                service.replyExecutor().processReplyResult(pending.event,groupID,pending.selfID,pending.userName,
                        pending.relationship,content,result,pending.decision,pending.emotionEvent);
                handled = true;
            } finally {
                service.hubService().typingStop(groupID,turnKey);
                if (!handled) service.hubService().turnRelease(groupID,turnKey);
            }
        } finally {
            //延后回合到这里才算结束，释放同群串行位并接着处理缓冲里的下一批
            service.batchScheduler().finishTurn(groupID);
        }
    }

    void markStickerRecognitionPending(long groupID,long userID) {
        PendingTurn pending = pendingTurnMap.get(service.recentImageKey(groupID,userID));
        if (pending == null) return;
        pending.stickerPending = true;
    }

    void finishStickerRecognition(long groupID,long userID,String emotion) {
        String key = service.recentImageKey(groupID,userID);
        service.rememberStickerEmotion(groupID,userID,emotion);
        PendingTurn pending = pendingTurnMap.get(key);
        if (pending == null) return;
        if (emotion != null && !emotion.trim().isEmpty()) {
            pending.stickerEmotion = emotion.trim();
        }
        pending.stickerPending = false;
        executePendingTurn(key,pending.generation);
    }

    private synchronized int nextPendingGeneration(String key) {
        Integer current = pendingGenerationMap.get(key);
        int next = current == null ? 1 : current + 1;
        pendingGenerationMap.put(key,next);
        return next;
    }

    private static class PendingTurn {
        private final GroupMessageEvent event;
        private final long selfID;
        private final String userName;
        private final String relationship;
        private volatile String content;
        private final RoleplayService.RecentImage imageContext;
        private final int generation;
        private final boolean otherRoleBot;
        private final RoleplayRouteDecision decision;
        private final RoleplayEmotionService.LocalEvent emotionEvent;
        private volatile boolean incomplete = false;
        private volatile String stickerEmotion = "";
        private volatile boolean stickerPending = false;
        private volatile long hardDeadline = 0L;

        private PendingTurn(GroupMessageEvent event,long selfID,String userName,
                            String relationship,String content,RoleplayService.RecentImage imageContext,
                            int generation,boolean otherRoleBot,RoleplayRouteDecision decision,
                            RoleplayEmotionService.LocalEvent emotionEvent) {
            this.event = event;
            this.selfID = selfID;
            this.userName = userName;
            this.relationship = relationship;
            this.content = content;
            this.imageContext = imageContext;
            this.generation = generation;
            this.otherRoleBot = otherRoleBot;
            this.decision = decision;
            this.emotionEvent = emotionEvent;
        }

        /**
         * 同一用户接着说了一句：把内容并进同一个回合，一次回完
         */
        private void appendContent(String text) {
            if (text == null || text.trim().isEmpty()) return;
            String value = text.trim();
            this.content = this.content == null || this.content.trim().isEmpty()
                    ? value : this.content.trim()+" "+value;
        }
    }

    void clearRuntimeState() {
        pendingTurnMap.clear();
        pendingGenerationMap.clear();
    }
}
