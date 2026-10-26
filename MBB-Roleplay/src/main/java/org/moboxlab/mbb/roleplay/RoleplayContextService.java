package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 最近上下文与话题疲劳
 *
 * 负责最近发言、最近群聊、话题键和话题疲劳统计，避免这些查询逻辑继续堆在 RoleplayService。
 */
public class RoleplayContextService {
    public static class TopicFatigue {
        private final String key;
        private final int count;
        private final int selfCount;
        private final String hint;
        private final boolean fatigued;

        private TopicFatigue() {
            this("",0,0,"",false);
        }

        private TopicFatigue(String key,int count,int selfCount,String hint,boolean fatigued) {
            this.key = key == null ? "" : key;
            this.count = count;
            this.selfCount = selfCount;
            this.hint = hint == null ? "" : hint;
            this.fatigued = fatigued;
        }

        public String key() { return key; }
        public int count() { return count; }
        public int selfCount() { return selfCount; }
        public String hint() { return hint; }
        public boolean fatigued() { return fatigued; }
    }

    private static final Set<String> TOPIC_STOPWORDS = new LinkedHashSet<>();

    static {
        Collections.addAll(TOPIC_STOPWORDS,
                "我们","你们","他们","这个","那个","什么","就是","不是","没有","一个",
                "今天","明天","昨天","现在","已经","还是","可以","不能","怎么","为什么",
                "这样","那样","一下","有点","自己","大家","老师","群友","感觉","而且",
                "但是","因为","所以","如果","然后","真的","哈哈","不是","没有","不是");
    }

    private final RoleplayService service;

    public RoleplayContextService(RoleplayService service) {
        this.service = service;
    }

    public String recentRoleReplyText(long groupID) {
        JSONArray replies = recentRoleRepliesForPrompt(groupID,true);
        if (replies.isEmpty()) return "暂无。";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < replies.size(); i++) {
            builder.append("- [自己] ").append(service.safe(replies.getJSONObject(i).getString("content")))
                    .append("\n");
        }
        return builder.toString();
    }

    public String recentOtherRoleReplyText(long groupID) {
        JSONArray replies = recentRoleRepliesForPrompt(groupID,false);
        if (replies.isEmpty()) return "暂无。";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < replies.size(); i++) {
            JSONObject row = replies.getJSONObject(i);
            String name = service.safe(row.getString("userName"));
            builder.append("- [其他角色机器人] ")
                    .append(name.isEmpty() ? "未命名" : name)
                    .append("（QQ：").append(row.getLongValue("userID")).append("）：")
                    .append(service.safe(row.getString("content"))).append("\n");
        }
        return builder.toString();
    }

    private JSONArray recentRoleRepliesForPrompt(long groupID,boolean self) {
        JSONArray result = new JSONArray();
        List<JSONObject> rows = service.storage().query(
                "SELECT `userName`,`content`,`isBot`,`userID` FROM `"+RoleplayService.MSG_TABLE+"` "
                        + "WHERE `groupID`=? ORDER BY `messageTime` DESC,`ID` DESC LIMIT ?",
                groupID,Math.max(20,service.config().recentReplyCheckCount * 3));
        if (rows == null || rows.isEmpty()) return result;
        List<JSONObject> selected = new ArrayList<>();
        for (JSONObject row : rows) {
            boolean isSelf = row.getIntValue("isBot") == 1;
            boolean isOtherRole = !isSelf && service.isRoleParticipantUser(row.getLongValue("userID"));
            if (self ? isSelf : isOtherRole) selected.add(row);
            if (selected.size() >= service.config().recentReplyCheckCount) break;
        }
        for (int i = selected.size() - 1; i >= 0; i--) {
            JSONObject row = selected.get(i);
            JSONObject item = new JSONObject(true);
            item.put("userName",row.getString("userName"));
            item.put("userID",row.getLongValue("userID"));
            item.put("content",row.getString("content"));
            result.add(item);
        }
        return result;
    }

    public JSONArray recentRoleReplies(long groupID) {
        JSONArray result = new JSONArray();
        List<JSONObject> rows = service.storage().query(
                "SELECT `userName`,`content`,`isBot`,`userID` FROM `"+RoleplayService.MSG_TABLE+"` "
                        + "WHERE `groupID`=? AND (`isBot`=1 OR `userID` NOT IN "
                        + "(SELECT `userID` FROM `plugin_mbb_roleplay_blacklist` WHERE `groupID`=?)) "
                        + "ORDER BY `messageTime` DESC,`ID` DESC LIMIT ?",
                groupID,groupID,Math.max(20,service.config().recentReplyCheckCount * 3));
        if (rows == null || rows.isEmpty()) return result;
        List<JSONObject> selected = new ArrayList<>();
        for (JSONObject row : rows) {
            if (row.getIntValue("isBot") == 1
                    || service.isRoleParticipantUser(row.getLongValue("userID"))) {
                selected.add(row);
                if (selected.size() >= service.config().recentReplyCheckCount) break;
            }
        }
        for (int i = selected.size() - 1; i >= 0; i--) {
            JSONObject row = selected.get(i);
            JSONObject item = new JSONObject(true);
            item.put("content",row.getString("content"));
            result.add(item);
        }
        return result;
    }

    public String recentContext(long groupID) {
        return recentContext(groupID,0L,service.config().shortContextMessages);
    }

    public String recentContext(long groupID,long excludeMessageID) {
        return recentContext(groupID,excludeMessageID,service.config().shortContextMessages);
    }

    public String recentContext(long groupID,long excludeMessageID,int limit) {
        int capped = Math.max(1,Math.min(500,limit));
        List<JSONObject> rows;
        if (excludeMessageID > 0) {
            rows = service.storage().query(
                    "SELECT `messageID`,`userID`,`userName`,`content`,`isBot`,`messageTime` FROM `"
                            +RoleplayService.MSG_TABLE+"` WHERE `groupID`=? "
                            +"AND `messageID`<>? AND `userID` NOT IN "
                            +"(SELECT `userID` FROM `plugin_mbb_roleplay_blacklist` WHERE `groupID`=?) "
                            +"ORDER BY `messageTime` DESC LIMIT ?",
                    groupID,excludeMessageID,groupID,capped);
        } else {
            rows = service.storage().query(
                    "SELECT `messageID`,`userID`,`userName`,`content`,`isBot`,`messageTime` FROM `"
                            +RoleplayService.MSG_TABLE+"` WHERE `groupID`=? "
                            +"AND `userID` NOT IN (SELECT `userID` FROM `plugin_mbb_roleplay_blacklist` "
                            +"WHERE `groupID`=?) ORDER BY `messageTime` DESC LIMIT ?",
                    groupID,groupID,capped);
        }
        if (rows == null || rows.isEmpty()) return "无";
        List<JSONObject> selected = new ArrayList<>();
        int maxChars = Math.max(2000,service.config().shortContextMaxChars);
        int used = 0;
        for (JSONObject row : rows) {
            int length = service.safe(row.getString("content")).length()+80;
            if (!selected.isEmpty() && used + length > maxChars) break;
            selected.add(row);
            used += length;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = selected.size() - 1; i >= 0; i--) {
            JSONObject row = selected.get(i);
            builder.append(contextIdentity(row))
                    .append(" ").append(service.safe(row.getString("userName")))
                    .append("（QQ：").append(row.getLongValue("userID")).append("）：")
                    .append(service.safe(row.getString("content"))).append("\n");
        }
        return builder.toString();
    }

    private String contextIdentity(JSONObject row) {
        if (row == null) return "[群友]";
        if (row.getIntValue("isBot") == 1) return "[自己]";
        if (service.isRoleParticipantUser(row.getLongValue("userID"))) return "[其他角色机器人]";
        return "[群友]";
    }

    public void recordTopic(long groupID,String content,boolean self) {
        if (!service.config().topicFatigueEnable) return;
        String key = topicKeyForMessage(groupID,content);
        if (key.isEmpty()) return;
        long now = System.currentTimeMillis();
        JSONObject row = service.storage().queryOne(
                "SELECT `ID`,`selfCount` FROM `"+RoleplayService.TOPIC_TABLE+"` WHERE `groupID`=? AND `topicKey`=?",
                groupID,key);
        if (row == null) {
            service.storage().insert("INSERT INTO `"+RoleplayService.TOPIC_TABLE+"` "
                            + "(`groupID`,`topicKey`,`lastTime`,`count`,`selfCount`,`lastSelfTime`,`updateTime`) "
                            + "VALUES (?,?,?,?,?,?,?)",
                    groupID,key,now,1,self ? 1 : 0,self ? now : 0L,now);
        } else {
            service.storage().update("UPDATE `"+RoleplayService.TOPIC_TABLE+"` SET `lastTime`=?,`count`=`count`+1,"
                            + "`selfCount`=`selfCount`+?,`lastSelfTime`=?,`updateTime`=? WHERE `ID`=?",
                    now,self ? 1 : 0,self ? now : 0L,now,row.getLongValue("ID"));
        }
    }

    public TopicFatigue topicFatigue(long groupID,String content) {
        if (!service.config().topicFatigueEnable) return new TopicFatigue();
        String key = topicKeyForMessage(groupID,content);
        if (key.isEmpty()) return new TopicFatigue();
        JSONObject row = service.storage().queryOne(
                "SELECT `lastTime`,`count`,`selfCount` FROM `"+RoleplayService.TOPIC_TABLE+"` "
                        + "WHERE `groupID`=? AND `topicKey`=?",
                groupID,key);
        if (row == null) return new TopicFatigue(key,0,0,"",false);
        long now = System.currentTimeMillis();
        long window = Math.max(1,service.config().topicFatigueWindowMinute) * 60000L;
        if (now - row.getLongValue("lastTime") > window) {
            return new TopicFatigue(key,row.getIntValue("count"),row.getIntValue("selfCount"),"",false);
        }
        int count = row.getIntValue("count");
        int selfCount = row.getIntValue("selfCount");
        boolean fatigued = count >= service.config().topicFatigueMinCount
                && selfCount >= service.config().topicFatigueSelfCount;
        String hint = "";
        if (fatigued || count >= service.config().topicFatigueMinCount) {
            hint = "话题疲劳：话题“"+service.shortText(key,20)+"”最近"
                    +service.config().topicFatigueWindowMinute+"分钟内出现"+count+"次，角色自己参与"
                    +selfCount+"次。"
                    +(fatigued ? "除非有明显新信息或直接提问，否则不要主动展开、复述或接着重复这个话题。"
                    : "减少主动接话，不要主动延长这个话题。");
        }
        return new TopicFatigue(key,count,selfCount,hint,fatigued);
    }

    private String topicKeyForMessage(long groupID,String content) {
        String normalized = SpeechCorpusEntry.normalize(content == null ? "" : content);
        if (normalized.length() < 2) return "";
        List<JSONObject> rows = service.storage().query(
                "SELECT `content` FROM `"+RoleplayService.MSG_TABLE+"` WHERE `groupID`=? "
                        + "ORDER BY `messageTime` DESC,`ID` DESC LIMIT ?",
                groupID,Math.max(20,service.config().shortContextMessages / 2));
        String best = "";
        int bestScore = 0;
        int maxLength = Math.min(6,normalized.length());
        for (int len = 2; len <= maxLength; len++) {
            for (int start = 0; start + len <= normalized.length(); start++) {
                String candidate = normalized.substring(start,start + len);
                if (TOPIC_STOPWORDS.contains(candidate)) continue;
                int count = 0;
                if (rows != null) {
                    for (JSONObject row : rows) {
                        if (SpeechCorpusEntry.normalize(row.getString("content")).contains(candidate)) count++;
                    }
                }
                int score = count * len;
                if (count >= 2 && score > bestScore) {
                    best = candidate;
                    bestScore = score;
                }
            }
        }
        if (!best.isEmpty()) return best;
        return normalized.length() > 12 ? normalized.substring(0,12) : normalized;
    }
}
