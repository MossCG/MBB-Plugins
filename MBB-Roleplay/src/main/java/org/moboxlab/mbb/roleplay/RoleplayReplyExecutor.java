package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Event.GroupMessageEvent;
import org.moboxlab.moboxbot.API.OneBot.OneBotClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 回复执行
 *
 * 负责回复结果解析后的发送、分段、旧标签兼容、技能调用和动作校验。
 */
public class RoleplayReplyExecutor {
    private final RoleplayService service;

    public RoleplayReplyExecutor(RoleplayService service) {
        this.service = service;
    }
    void processReplyResult(GroupMessageEvent event,long groupID,long selfID,
                                    String userName,String relationship,String content,
                                    JSONObject result,RoleplayRouteDecision decision,
                                    RoleplayEmotionService.LocalEvent emotionEvent) {
        if (service.blacklistService().contains(groupID,event.getUserID())) {
            service.plugin().getLogger().sendInfo("[角色] 群"+groupID+" 跳过已拉黑用户的回复 "
                    +event.getUserID());
            return;
        }
        RoleplayReplyDraft draft = RoleplayReplyDraft.parse(result.getString("content"));
        if (draft.malformed) {
            service.plugin().getLogger().sendWarn("[角色] 群"+groupID+" 执行层结构化结果无法解析，本轮跳过发送："
                    +service.shortText(result.getString("content"),160));
            return;
        }
        if (draft.repaired) {
            service.plugin().getLogger().sendInfo("[角色] 群"+groupID+" 执行层结构化结果已容错修复"
                    +" 完整结构="+draft.structured+" 动作数="+draft.actions.size());
        } else if (!draft.structured) {
            service.plugin().getLogger().sendInfo("[角色] 群"+groupID+" 执行层未返回结构化结果，按纯文本处理");
        }
        String rawReply = service.safe(draft.text).trim();
        // 兼容旧标签：模型偶尔仍会输出 <reminder> / <remember> / <global_remember> / <sticker>，
        // 统一转成技能调用，和执行层直接给出的 actions 走同一条执行路径
        RoleplayService.ReminderMarkerResult reminderMarker = service.extractReminderMarker(rawReply);
        RoleplayService.GlobalRememberResult globalRemember = service.extractGlobalRemember(reminderMarker.reply);
        RoleplayService.RememberResult rememberResult = service.extractRemember(globalRemember.reply);
        RoleplayService.StickerMarkerResult stickerMarker = service.extractStickerMarker(rememberResult.reply);
        String beforeSanitize = stickerMarker.reply.trim();
        String reply = RoleplayReplySanitizer.clean(service.persona(),
                service.plugin().getServer().getBotName(),beforeSanitize);
        logSanitizeChange(groupID,beforeSanitize,reply);
        if (reminderMarker.requested) draft.addCall(legacyReminderCall(reminderMarker));
        if (rememberResult.requested) draft.addCall(legacyMemoryCall(rememberResult.memory));
        if (globalRemember.requested) draft.addCall(legacyGlobalMemoryCall(globalRemember.content));
        if (stickerMarker.requested) draft.addCall(legacyStickerCall(stickerMarker.tags));
        boolean sendText = !reply.isEmpty() && !"<SKIP>".equalsIgnoreCase(reply);
        if (sendText && service.config().repeatSuppressEnable && service.shouldSuppressRepeat(groupID,reply)) {
            service.plugin().getLogger().sendInfo("[角色] 群"+groupID+" 跳过重复回复："+service.shortText(reply,80));
            sendText = false;
        }
        if (sendText && service.speechCorpusService().tooSimilar(reply)) {
            service.plugin().getLogger().sendInfo("[语料] 群"+groupID+" 跳过高度相似台词："
                    +service.shortText(reply,80));
            sendText = false;
        }
        if (sendText) {
            RoleplayReplyReviewService.Result review = service.getReplyReviewService().review(
                    groupID,event.getUserID(),content,reply,decision);
            if (review.text != null && !review.text.trim().isEmpty()
                    && !review.text.trim().equals(reply)) {
                String beforeReviewClean = reply;
                reply = RoleplayReplySanitizer.clean(service.persona(),
                        service.plugin().getServer().getBotName(),review.text);
                logSanitizeChange(groupID,beforeReviewClean,reply);
                if (reply.isEmpty()) sendText = false;
            }
        }
        if (sendText) ensurePokeBackAction(draft,decision,reply);
        OneBotClient client = service.plugin().getServer().getOneBotClient();
        if (sendText && client != null) {
            if (!service.getPacingService().sleep(groupID,event.getUserID(),content,reply)) return;
            service.messageSender().sendReply(client,groupID,selfID,event.getUserID(),reply,
                    decision != null && decision.quoteRequired,draft.quote,event.getMessageID());
        }
        executeSkillCalls(decision,draft,groupID,event.getUserID(),event.getMessageID(),selfID,
                userName,relationship,content);
        service.emotionService().afterTurn(groupID,event.getUserID(),userName,relationship,content,
                emotionEvent,decision != null && "direct".equals(decision.addressed),
                decision != null && decision.otherRoleBot);
    }

    /**
     * 合批回复：模型可以返回多段，每段用 to 指回批内不同的消息
     */
    void processBatchReplyResult(long groupID,long selfID,List<RoleplayService.PreparedMessage> messages,
                                         RoleplayService.PreparedMessage primary,RoleplayRouteDecision decision,
                                         JSONObject result) {
        RoleplayReplyDraft draft = RoleplayReplyDraft.parse(result.getString("content"));
        if (draft.malformed) {
            service.plugin().getLogger().sendWarn("[角色] 群"+groupID+" 合批结构化结果无法解析，本轮跳过发送："
                    +service.shortText(result.getString("content"),160));
            return;
        }
        if (draft.repaired) {
            service.plugin().getLogger().sendInfo("[角色] 群"+groupID+" 合批结构化结果已容错修复"
                    +" 完整结构="+draft.structured+" 动作数="+draft.actions.size());
        } else if (!draft.structured) {
            service.plugin().getLogger().sendInfo("[角色] 群"+groupID+" 合批未返回结构化结果，按纯文本处理");
        }
        List<RoleplayReplyDraft.Segment> segments = draft.effectiveSegments();
        int limit = Math.min(segments.size(),service.config().batchMaxSegments);
        OneBotClient client = service.plugin().getServer().getOneBotClient();
        List<RoleplayService.PreparedMessage> responded = new ArrayList<>();
        int sent = 0;
        //整批最多发多少条消息：段数上限的两倍，避免段内换行把消息刷屏
        int messageBudget = Math.max(1,service.config().batchMaxSegments * 2);
        for (int i = 0; i < limit; i++) {
            RoleplayReplyDraft.Segment segment = segments.get(i);
            if (segment == null) continue;
            RoleplayService.PreparedMessage target = batchTarget(messages,segment.to);
            boolean quoteAllowed = true;
            if (target == null) {
                //序号越界或没给序号：正文按主消息处理，但不允许引用，避免挂到无关消息上
                if (segment.to != 0) {
                    service.plugin().getLogger().sendInfo("[角色] 群"+groupID+" 合批段落 to="+segment.to
                            +" 越界，引用已取消");
                }
                target = primary;
                quoteAllowed = false;
            } else if (target != primary && target.userID != primary.userID) {
                //引用目标与主消息不是同一个人，降级为不引用
                quoteAllowed = false;
                service.plugin().getLogger().sendInfo("[角色] 群"+groupID+" 合批引用降级：目标 "
                        +target.userID+" 与主消息 "+primary.userID+" 不是同一人");
            }
            if (service.blacklistService().contains(groupID,target.userID)) {
                service.plugin().getLogger().sendInfo("[角色] 群"+groupID+" 合批跳过已拉黑用户 "
                        +target.userID);
                continue;
            }
            String reply = extractSegmentReply(groupID,segment);
            //先抽取旧标签补齐 segment.actions，再复制成执行用草稿
            RoleplayReplyDraft segmentDraft = segmentDraft(segment);
            boolean sendText = !reply.isEmpty() && !"<SKIP>".equalsIgnoreCase(reply);
            if (sendText && service.config().repeatSuppressEnable && service.shouldSuppressRepeat(groupID,reply)) {
                service.plugin().getLogger().sendInfo("[角色] 群"+groupID+" 合批跳过重复回复："
                        +service.shortText(reply,80));
                sendText = false;
            }
            if (sendText && service.speechCorpusService().tooSimilar(reply)) {
                service.plugin().getLogger().sendInfo("[语料] 群"+groupID+" 合批跳过高度相似台词："
                        +service.shortText(reply,80));
                sendText = false;
            }
            if (sendText) {
                RoleplayReplyReviewService.Result review = service.getReplyReviewService().review(
                        groupID,target.userID,target.content,reply,decision,
                        Math.max(1,service.config().replyMaxSegments));
                if (review.text != null && !review.text.trim().isEmpty()
                        && !review.text.trim().equals(reply)) {
                    String beforeReviewClean = reply;
                    reply = RoleplayReplySanitizer.clean(service.persona(),
                            service.plugin().getServer().getBotName(),review.text);
                    logSanitizeChange(groupID,beforeReviewClean,reply);
                    sendText = !reply.isEmpty();
                }
            }
            if (sendText) {
                ensurePokeBackAction(segmentDraft,decision,reply);
                long quoteMessageID = target.event.getMessageID();
                boolean forceQuote = decision != null && decision.quoteRequired && target == primary;
                boolean quote = service.config().quoteReplyEnable && quoteAllowed
                        && (forceQuote || segment.quote) && quoteMessageID > 0;
                //和单回合保持一致：段内换行按多段发送，引用只挂在第一段上
                List<String> parts = service.messageSender().splitReply(reply);
                int partLimit = Math.min(parts.size(),Math.max(1,service.config().replyMaxSegments));
                for (int p = 0; p < partLimit && sent < messageBudget; p++) {
                    if (p > 0 && !service.sleepQuietly(250L)) break;
                    if (!service.messageSender().sendSingleMessage(client,groupID,selfID,target.userID,parts.get(p),
                            quote && p == 0,quoteMessageID)) break;
                    if (!responded.contains(target)) responded.add(target);
                    sent++;
                }
            }
            if (!segmentDraft.actions.isEmpty()) {
                executeSkillCalls(decision,segmentDraft,groupID,target.userID,
                        target.event.getMessageID(),selfID,target.userName,target.relationship,
                        target.content);
            }
            if (sent > 0 && i + 1 < limit && !service.sleepQuietly(250L)) break;
        }
        service.plugin().getLogger().sendInfo("[角色] 群"+groupID+" 合批回合回复 "+sent+" 条");
        for (RoleplayService.PreparedMessage message : responded) {
            service.emotionService().afterTurn(groupID,message.userID,message.userName,message.relationship,
                    message.content,message.emotionEvent,
                    decision != null && "direct".equals(decision.addressed),
                    decision != null && decision.otherRoleBot);
        }
    }

    /**
     * 按批内序号取回目标消息，序号越界时返回 null 由调用方回退到主消息
     */
    private RoleplayService.PreparedMessage batchTarget(List<RoleplayService.PreparedMessage> messages,int to) {
        if (to <= 0 || to > messages.size()) return null;
        return messages.get(to - 1);
    }

    private RoleplayReplyDraft segmentDraft(RoleplayReplyDraft.Segment segment) {
        RoleplayReplyDraft draft = new RoleplayReplyDraft();
        draft.text = segment.text;
        draft.quote = segment.quote;
        draft.actions.addAll(segment.actions);
        return draft;
    }

    /**
     * 合批分段同样兼容 <remember> / <reminder> / <sticker> 这类旧标签
     */
    private String extractSegmentReply(long groupID,RoleplayReplyDraft.Segment segment) {
        String rawReply = service.safe(segment.text).trim();
        RoleplayService.ReminderMarkerResult reminderMarker = service.extractReminderMarker(rawReply);
        RoleplayService.GlobalRememberResult globalRemember = service.extractGlobalRemember(reminderMarker.reply);
        RoleplayService.RememberResult rememberResult = service.extractRemember(globalRemember.reply);
        RoleplayService.StickerMarkerResult stickerMarker = service.extractStickerMarker(rememberResult.reply);
        if (reminderMarker.requested) addSegmentCall(segment,legacyReminderCall(reminderMarker));
        if (rememberResult.requested) addSegmentCall(segment,legacyMemoryCall(rememberResult.memory));
        if (globalRemember.requested) {
            addSegmentCall(segment,legacyGlobalMemoryCall(globalRemember.content));
        }
        if (stickerMarker.requested) addSegmentCall(segment,legacyStickerCall(stickerMarker.tags));
        String beforeSanitize = stickerMarker.reply.trim();
        String cleaned = RoleplayReplySanitizer.clean(service.persona(),
                service.plugin().getServer().getBotName(),beforeSanitize);
        logSanitizeChange(groupID,beforeSanitize,cleaned);
        return cleaned;
    }

    private void addSegmentCall(RoleplayReplyDraft.Segment segment,RoleplaySkillCall call) {
        if (segment == null || call == null || call.type.isEmpty()) return;
        for (RoleplaySkillCall existing : segment.actions) {
            if (existing.type.equals(call.type)) return;
        }
        segment.actions.add(call);
    }

    private void logSanitizeChange(long groupID,String before,String after) {
        if (service.safe(before).equals(service.safe(after))) return;
        service.plugin().getLogger().sendInfo("[角色] 群"+groupID+" 去舞台化 原文="
                +service.shortText(before,60)+" 清洗="+service.shortText(after,60));
    }

    void executeSkillCalls(RoleplayRouteDecision decision,RoleplayReplyDraft draft,
                                   long groupID,long userID,long messageID,long selfID,
                                   String userName,String relationship,String content) {
        if (draft == null || draft.actions.isEmpty()) return;
        List<String> allowed = decision == null ? service.skillRegistry().actionIds(service.config()) : decision.actions;
        for (RoleplaySkillCall call : draft.actions) {
            RoleplaySkill skill = service.skillRegistry().get(call.type);
            if (skill == null) {
                service.plugin().getLogger().sendWarn("[角色] 群"+groupID+" 执行层请求了未注册的技能："+call.type);
                continue;
            }
            if (!isActionAllowed(allowed,skill.id())) {
                service.plugin().getLogger().sendWarn("[角色] 群"+groupID+" 执行层请求了未开放的技能："
                        +skill.id()+" 当前开放="+canonicalActions(allowed));
                continue;
            }
            RoleplaySkillContext context = new RoleplaySkillContext();
            context.groupID = groupID;
            context.userID = userID;
            context.messageID = messageID;
            context.selfID = selfID;
            context.userName = userName == null ? "" : userName;
            context.relationship = relationship == null ? "" : relationship;
            context.message = content == null ? "" : content;
            context.args = call.args == null ? new JSONObject(true) : call.args;
            context.state = service.state(groupID);
            RoleplaySkillResult result = skill.execute(context);
            service.plugin().getLogger().sendInfo("[角色] 技能 "+skill.id()+" 群"+groupID+" 用户"+userID
                    +" 结果="+(result.success ? "成功" : "失败")
                    +" 摘要="+service.shortText(result.summary,80));
        }
    }

    private boolean isActionAllowed(List<String> allowed,String skillID) {
        if (allowed == null || skillID == null) return false;
        String target = canonicalAction(skillID);
        for (String item : allowed) {
            if (target.equals(canonicalAction(item))) return true;
        }
        return false;
    }

    void ensurePokeBackAction(RoleplayReplyDraft draft,RoleplayRouteDecision decision,
                                      String text) {
        if (draft == null || text == null || text.trim().isEmpty()) return;
        if (draft.hasAction("poke-back")) return;
        List<String> allowed = decision == null ? service.skillRegistry().actionIds(service.config()) : decision.actions;
        if (!isActionAllowed(allowed,"poke-back")) return;
        String value = text.trim();
        if (value.contains("不戳") || value.contains("别戳") || value.contains("不回了")) return;
        if (value.contains("戳回去") || value.contains("回戳") || value.contains("戳你")) {
            draft.addCall(new RoleplaySkillCall("poke-back"));
        }
    }

    private List<String> canonicalActions(List<String> actions) {
        List<String> result = new ArrayList<>();
        if (actions == null) return result;
        for (String item : actions) {
            String value = canonicalAction(item);
            if (!value.isEmpty() && !result.contains(value)) result.add(value);
        }
        return result;
    }

    private String canonicalAction(String action) {
        if (action == null) return "";
        String value = action.trim().toLowerCase(Locale.ROOT);
        if ("poke".equals(value) || "poke_back".equals(value) || "poke-back".equals(value)) {
            return "poke-back";
        }
        return value;
    }

    private RoleplaySkillCall legacyReminderCall(RoleplayService.ReminderMarkerResult marker) {
        RoleplaySkillCall call = new RoleplaySkillCall("reminder");
        call.args.put("action",marker.action);
        if (marker.id > 0) call.args.put("id",marker.id);
        if (marker.time != null && !marker.time.isEmpty()) call.args.put("time",marker.time);
        if (marker.task != null && !marker.task.isEmpty()) call.args.put("task",marker.task);
        if (marker.target != null && !marker.target.isEmpty()) call.args.put("target",marker.target);
        return call;
    }

    private RoleplaySkillCall legacyMemoryCall(String content) {
        RoleplaySkillCall call = new RoleplaySkillCall("memory");
        call.args.put("content",content == null ? "" : content);
        return call;
    }

    private RoleplaySkillCall legacyGlobalMemoryCall(String content) {
        RoleplaySkillCall call = new RoleplaySkillCall("global-memory");
        call.args.put("content",content == null ? "" : content);
        return call;
    }

    private RoleplaySkillCall legacyStickerCall(String tags) {
        RoleplaySkillCall call = new RoleplaySkillCall("sticker");
        call.args.put("tags",tags == null ? "" : tags);
        return call;
    }
}
