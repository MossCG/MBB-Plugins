package org.moboxlab.mbb.poll;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内存投票服务，一个群同时只能有一个进行中的投票。
 */
public class PollService {
    private static final Map<Long,Poll> polls = new LinkedHashMap<>();

    public static synchronized String create(long groupID,long initiatorID,String question,List<String> options,long durationSeconds) {
        Poll current = polls.get(groupID);
        if (current != null && current.active) return "当前群已经有进行中的投票了！";
        Poll poll = new Poll();
        poll.groupID = groupID;
        poll.initiatorID = initiatorID;
        poll.question = question;
        poll.options = new ArrayList<>(options);
        poll.endTime = System.currentTimeMillis() + durationSeconds * 1000L;
        poll.active = true;
        polls.put(groupID,poll);
        return null;
    }

    public static synchronized String vote(long groupID,long userID,int index) {
        Poll poll = polls.get(groupID);
        if (poll == null || !poll.active) return "当前没有进行中的投票！";
        if (index < 1 || index > poll.options.size()) return "选项不存在，请重新选择！";
        poll.votes.put(userID,index - 1);
        return "投票成功："+(index)+". "+poll.options.get(index - 1);
    }

    public static synchronized Poll finish(long groupID) {
        Poll poll = polls.remove(groupID);
        if (poll == null) return null;
        poll.active = false;
        return poll;
    }

    public static synchronized void clear() {
        polls.clear();
    }

    public static class Poll {
        public long groupID;
        public long initiatorID;
        public String question;
        public List<String> options = new ArrayList<>();
        public Map<Long,Integer> votes = new LinkedHashMap<>();
        public long endTime;
        public boolean active = false;
    }
}
