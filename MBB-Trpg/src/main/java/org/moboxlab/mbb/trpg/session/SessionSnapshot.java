package org.moboxlab.mbb.trpg.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 局面只读快照（不可变）：呈现层、指令层与后续切片只读它，不直接读存储行。
 *
 * <p>{@code recovered=false} 表示本机重启后该局尚未确认恢复（内存事实，不落库）；
 * {@code sceneVersion} 是最新日志序号，重启后由日志重算。</p>
 */
public final class SessionSnapshot {

    private final long groupId;
    private final long sessionId;
    private final SessionState state;
    private final long kpUserId;
    private final String scriptId;
    private final String ruleSetId;
    private final int sceneIndex;
    private final String sceneTitle;
    private final long startedAt;
    private final long endedAt;
    private final String endReason;
    private final String summary;
    private final long lastActivityAt;
    private final boolean aiEnabled;
    private final long aiSuspendedUntil;
    private final int aiFailStreak;
    private final boolean growthSettled;
    private final boolean recovered;
    private final long sceneVersion;
    private final List<MemberView> members;
    private final List<String> unlockedClues;

    private SessionSnapshot(Builder builder) {
        this.groupId = builder.groupId;
        this.sessionId = builder.sessionId;
        this.state = builder.state;
        this.kpUserId = builder.kpUserId;
        this.scriptId = builder.scriptId;
        this.ruleSetId = builder.ruleSetId;
        this.sceneIndex = builder.sceneIndex;
        this.sceneTitle = builder.sceneTitle;
        this.startedAt = builder.startedAt;
        this.endedAt = builder.endedAt;
        this.endReason = builder.endReason;
        this.summary = builder.summary;
        this.lastActivityAt = builder.lastActivityAt;
        this.aiEnabled = builder.aiEnabled;
        this.aiSuspendedUntil = builder.aiSuspendedUntil;
        this.aiFailStreak = builder.aiFailStreak;
        this.growthSettled = builder.growthSettled;
        this.recovered = builder.recovered;
        this.sceneVersion = builder.sceneVersion;
        this.members = Collections.unmodifiableList(new ArrayList<MemberView>(builder.members));
        this.unlockedClues = Collections.unmodifiableList(new ArrayList<String>(builder.unlockedClues));
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 本群无活跃局的空快照。 */
    public static SessionSnapshot none(long groupId) {
        return builder().groupId(groupId).state(SessionState.NONE).recovered(true).build();
    }

    public long groupId() {
        return groupId;
    }

    public long sessionId() {
        return sessionId;
    }

    public SessionState state() {
        return state;
    }

    public long kpUserId() {
        return kpUserId;
    }

    public String scriptId() {
        return scriptId;
    }

    public String ruleSetId() {
        return ruleSetId;
    }

    public int sceneIndex() {
        return sceneIndex;
    }

    public String sceneTitle() {
        return sceneTitle;
    }

    public long startedAt() {
        return startedAt;
    }

    public long endedAt() {
        return endedAt;
    }

    public String endReason() {
        return endReason;
    }

    public String summary() {
        return summary;
    }

    public long lastActivityAt() {
        return lastActivityAt;
    }

    public boolean aiEnabled() {
        return aiEnabled;
    }

    public long aiSuspendedUntil() {
        return aiSuspendedUntil;
    }

    public int aiFailStreak() {
        return aiFailStreak;
    }

    public boolean growthSettled() {
        return growthSettled;
    }

    /** 本机重启后是否已确认恢复；新局与已确认的局都是 true。 */
    public boolean recovered() {
        return recovered;
    }

    /** 情景版本（最新日志序号）。 */
    public long sceneVersion() {
        return sceneVersion;
    }

    public List<MemberView> members() {
        return members;
    }

    /** 已解锁线索 key（按首次解锁顺序）。 */
    public List<String> unlockedClues() {
        return unlockedClues;
    }

    /** 是否处于活跃状态（招募中或进行中）。 */
    public boolean active() {
        return state != null && state.active();
    }

    /** 按用户号找局内成员；不在局内返回 null。 */
    public MemberView member(long userId) {
        for (MemberView member : members) {
            if (member.userId() == userId) {
                return member;
            }
        }
        return null;
    }

    /** 是否仍是局内成员（入局且未离场）。 */
    public boolean insideMember(long userId) {
        MemberView member = member(userId);
        return member != null && member.inSession();
    }

    public static final class Builder {

        private long groupId;
        private long sessionId;
        private SessionState state = SessionState.NONE;
        private long kpUserId;
        private String scriptId = "";
        private String ruleSetId = "";
        private int sceneIndex;
        private String sceneTitle = "";
        private long startedAt;
        private long endedAt;
        private String endReason = "";
        private String summary = "";
        private long lastActivityAt;
        private boolean aiEnabled;
        private long aiSuspendedUntil;
        private int aiFailStreak;
        private boolean growthSettled;
        private boolean recovered = true;
        private long sceneVersion;
        private List<MemberView> members = new ArrayList<MemberView>();
        private List<String> unlockedClues = new ArrayList<String>();

        public Builder groupId(long value) {
            this.groupId = value;
            return this;
        }

        public Builder sessionId(long value) {
            this.sessionId = value;
            return this;
        }

        public Builder state(SessionState value) {
            this.state = value == null ? SessionState.NONE : value;
            return this;
        }

        public Builder kpUserId(long value) {
            this.kpUserId = value;
            return this;
        }

        public Builder scriptId(String value) {
            this.scriptId = value == null ? "" : value;
            return this;
        }

        public Builder ruleSetId(String value) {
            this.ruleSetId = value == null ? "" : value;
            return this;
        }

        public Builder sceneIndex(int value) {
            this.sceneIndex = value;
            return this;
        }

        public Builder sceneTitle(String value) {
            this.sceneTitle = value == null ? "" : value;
            return this;
        }

        public Builder startedAt(long value) {
            this.startedAt = value;
            return this;
        }

        public Builder endedAt(long value) {
            this.endedAt = value;
            return this;
        }

        public Builder endReason(String value) {
            this.endReason = value == null ? "" : value;
            return this;
        }

        public Builder summary(String value) {
            this.summary = value == null ? "" : value;
            return this;
        }

        public Builder lastActivityAt(long value) {
            this.lastActivityAt = value;
            return this;
        }

        public Builder aiEnabled(boolean value) {
            this.aiEnabled = value;
            return this;
        }

        public Builder aiSuspendedUntil(long value) {
            this.aiSuspendedUntil = value;
            return this;
        }

        public Builder aiFailStreak(int value) {
            this.aiFailStreak = value;
            return this;
        }

        public Builder growthSettled(boolean value) {
            this.growthSettled = value;
            return this;
        }

        public Builder recovered(boolean value) {
            this.recovered = value;
            return this;
        }

        public Builder sceneVersion(long value) {
            this.sceneVersion = value;
            return this;
        }

        public Builder members(List<MemberView> value) {
            this.members = value == null ? new ArrayList<MemberView>() : value;
            return this;
        }

        public Builder unlockedClues(List<String> value) {
            this.unlockedClues = value == null ? new ArrayList<String>() : value;
            return this;
        }

        public SessionSnapshot build() {
            return new SessionSnapshot(this);
        }
    }

}
