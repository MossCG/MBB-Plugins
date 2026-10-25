package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 角色回复频率与重复抑制
 *
 * 负责消息计数、回复冷却、每小时上限、连续对话窗口和重复回复检测。
 */
public class RoleplayRateLimitService {
    private final RoleplayService service;
    private final Map<Long,Integer> messageCountMap = new ConcurrentHashMap<>();
    private final Map<Long,long[]> replyRateMap = new ConcurrentHashMap<>();

    public RoleplayRateLimitService(RoleplayService service) {
        this.service = service;
    }

    int countMessage(long groupID) {
        Integer count = messageCountMap.get(groupID);
        count = count == null ? 0 : count;
        count++;
        messageCountMap.put(groupID,count);
        return count;
    }

    boolean rateLimited(long groupID) {
        return rateLimited(groupID,false);
    }

    boolean rateLimited(long groupID,boolean bypassCooldown) {
        synchronized (service.groupQueueLock) {
            long now = System.currentTimeMillis();
            RoleplayConversationState state = service.state(groupID);
            if (!bypassCooldown && state.lastReplyTime > 0
                    && now - state.lastReplyTime < service.config().replyCooldownSecond * 1000L) {
                return true;
            }
            long hour = now / 3600000L;
            long[] rate = replyRateMap.get(groupID);
            return rate != null && rate[0] == hour && rate[1] >= service.config().maxRepliesPerHour;
        }
    }

    boolean canReply(long groupID) {
        return canReply(groupID,false);
    }

    boolean canReply(long groupID,boolean bypassCooldown) {
        //冷却判定与时间戳写入必须原子，否则并发回合会同时读到旧值、各回一次
        synchronized (service.groupQueueLock) {
            long now = System.currentTimeMillis();
            RoleplayConversationState state = service.state(groupID);
            if (!bypassCooldown && state.lastReplyTime > 0
                    && now - state.lastReplyTime < service.config().replyCooldownSecond * 1000L) {
                return false;
            }
            long hour = now / 3600000L;
            long[] rate = replyRateMap.get(groupID);
            if (rate == null || rate[0] != hour) {
                rate = new long[]{hour,1};
                replyRateMap.put(groupID,rate);
            } else {
                if (rate[1] >= service.config().maxRepliesPerHour) return false;
                rate[1]++;
            }
            state.lastReplyTime = now;
            return true;
        }
    }

    boolean shouldSuppressRepeat(long groupID,String reply) {
        if (reply == null || reply.trim().length() < service.config().repeatCheckMinChars) return false;
        JSONArray recent = service.contextService().recentRoleReplies(groupID);
        if (recent.isEmpty()) return false;
        String candidate = service.normalizeForSimilarity(reply);
        if (candidate.length() < service.config().repeatCheckMinChars) return false;
        String opening = service.openingOf(candidate);
        int openingCount = 0;
        int fillerCount = 0;
        boolean fillerOpening = candidate.startsWith("嗯");
        for (int i = 0; i < recent.size(); i++) {
            String old = service.normalizeForSimilarity(recent.getJSONObject(i).getString("content"));
            if (old.isEmpty()) continue;
            if (opening.length() >= 2 && old.startsWith(opening)) openingCount++;
            if (fillerOpening && old.startsWith("嗯")) fillerCount++;
            if (service.similarity(candidate,old) >= service.config().repeatSimilarityThreshold) return true;
            //字面相似度抓不住“同一组信息换个尾巴”的复读，这种会留下很长的公共片段
            if (service.longestCommonRun(candidate,old) >= service.config().repeatCommonRunMinChars) return true;
        }
        return (opening.length() >= 2 && openingCount >= service.config().repeatOpeningLimit)
                || (fillerOpening && fillerCount >= Math.max(1,service.config().repeatOpeningLimit - 1));
    }

    boolean isGroupActive(long groupID) {
        RoleplayConversationState state = service.state(groupID);
        return state.lastReplyTime > 0
                && System.currentTimeMillis() - state.lastReplyTime
                <= service.config().conversationWindowSecond * 1000L;
    }

    int updateOtherRoleMessageStreak(long groupID,boolean otherRoleBot) {
        RoleplayConversationState state = service.state(groupID);
        if (!otherRoleBot) {
            state.otherRoleStreak = 0;
            state.botStreak = 0;
            return 0;
        }
        state.otherRoleStreak++;
        return state.otherRoleStreak;
    }

    boolean justRepliedToSameUser(long groupID,long userID) {
        if (service.config().splitMessageSuppressSecond <= 0) return false;
        RoleplayConversationState state = service.state(groupID);
        return state.lastReplyTime > 0 && state.lastReplyUser == userID
                && System.currentTimeMillis() - state.lastReplyTime
                <= service.config().splitMessageSuppressSecond * 1000L;
    }

    boolean isContinuation(long groupID,long userID) {
        RoleplayConversationState state = service.state(groupID);
        return state.lastReplyTime > 0 && state.lastReplyUser == userID
                && System.currentTimeMillis() - state.lastReplyTime
                <= service.config().conversationWindowSecond * 1000L;
    }

    void clearRuntimeState() {
        messageCountMap.clear();
        replyRateMap.clear();
    }

    void resetGroup(long groupID) {
        messageCountMap.put(groupID,0);
        replyRateMap.remove(groupID);
    }
}
