package org.moboxlab.mbb.trpg.store;

import org.moboxlab.mbb.trpg.session.SessionState;

/**
 * plugin_mbb_trpg_session 行对象（不可变）。
 *
 * <p>只承载库中字段，不含业务语义；状态文本在读取时转换为 {@link SessionState}。</p>
 */
public final class SessionRow {

    private final long id;
    private final long groupId;
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
    private final long createTime;
    private final long updateTime;

    private SessionRow(Builder builder) {
        this.id = builder.id;
        this.groupId = builder.groupId;
        this.state = builder.state == null ? SessionState.NONE : builder.state;
        this.kpUserId = builder.kpUserId;
        this.scriptId = builder.scriptId == null ? "" : builder.scriptId;
        this.ruleSetId = builder.ruleSetId == null ? "" : builder.ruleSetId;
        this.sceneIndex = builder.sceneIndex;
        this.sceneTitle = builder.sceneTitle == null ? "" : builder.sceneTitle;
        this.startedAt = builder.startedAt;
        this.endedAt = builder.endedAt;
        this.endReason = builder.endReason == null ? "" : builder.endReason;
        this.summary = builder.summary == null ? "" : builder.summary;
        this.lastActivityAt = builder.lastActivityAt;
        this.aiEnabled = builder.aiEnabled;
        this.aiSuspendedUntil = builder.aiSuspendedUntil;
        this.aiFailStreak = builder.aiFailStreak;
        this.growthSettled = builder.growthSettled;
        this.createTime = builder.createTime;
        this.updateTime = builder.updateTime;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 以本行为基础复制出一个新构造器，便于 CAS 与局部更新。 */
    public Builder toBuilder() {
        return new Builder()
                .id(id).groupId(groupId).state(state).kpUserId(kpUserId).scriptId(scriptId)
                .ruleSetId(ruleSetId).sceneIndex(sceneIndex).sceneTitle(sceneTitle)
                .startedAt(startedAt).endedAt(endedAt).endReason(endReason).summary(summary)
                .lastActivityAt(lastActivityAt).aiEnabled(aiEnabled)
                .aiSuspendedUntil(aiSuspendedUntil).aiFailStreak(aiFailStreak)
                .growthSettled(growthSettled).createTime(createTime).updateTime(updateTime);
    }

    public long id() {
        return id;
    }

    public long groupId() {
        return groupId;
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

    public long createTime() {
        return createTime;
    }

    public long updateTime() {
        return updateTime;
    }

    /** 行构造器。 */
    public static final class Builder {

        private long id;
        private long groupId;
        private SessionState state = SessionState.NONE;
        private long kpUserId;
        private String scriptId = "";
        private String ruleSetId = "d100";
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
        private long createTime;
        private long updateTime;

        public Builder id(long value) {
            this.id = value;
            return this;
        }

        public Builder groupId(long value) {
            this.groupId = value;
            return this;
        }

        public Builder state(SessionState value) {
            this.state = value;
            return this;
        }

        public Builder kpUserId(long value) {
            this.kpUserId = value;
            return this;
        }

        public Builder scriptId(String value) {
            this.scriptId = value;
            return this;
        }

        public Builder ruleSetId(String value) {
            this.ruleSetId = value;
            return this;
        }

        public Builder sceneIndex(int value) {
            this.sceneIndex = value;
            return this;
        }

        public Builder sceneTitle(String value) {
            this.sceneTitle = value;
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
            this.endReason = value;
            return this;
        }

        public Builder summary(String value) {
            this.summary = value;
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

        public Builder createTime(long value) {
            this.createTime = value;
            return this;
        }

        public Builder updateTime(long value) {
            this.updateTime = value;
            return this;
        }

        public SessionRow build() {
            return new SessionRow(this);
        }
    }
}
