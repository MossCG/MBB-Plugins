package org.moboxlab.mbb.trpg.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 局面事件（不可变）：服务层在群锁外发布，由呈现层消费（战报、恢复提示）。
 *
 * <p>S5 只产出两类事件；场景与检定事件归 S7，发送文案由 S6 决定。</p>
 */
public final class SessionEvent {

    /** 事件类型。 */
    public enum Type {
        /** 重启后某进行中的局已加载，提示玩家可继续（招募期不发）。 */
        RECOVERED,
        /** 本局已收尾，等待战报与后续动作。 */
        ENDED
    }

    private final Type type;
    private final SessionSnapshot snapshot;
    private final String endReason;
    private final List<GrowthOutcome> growth;

    private SessionEvent(Type type, SessionSnapshot snapshot, String endReason,
                         List<GrowthOutcome> growth) {
        this.type = type;
        this.snapshot = snapshot;
        this.endReason = endReason == null ? "" : endReason;
        this.growth = Collections.unmodifiableList(new ArrayList<GrowthOutcome>(growth));
    }

    /** 恢复提示事件。 */
    public static SessionEvent recovered(SessionSnapshot snapshot) {
        return new SessionEvent(Type.RECOVERED, snapshot, "", new ArrayList<GrowthOutcome>());
    }

    /** 收尾事件。 */
    public static SessionEvent ended(SessionSnapshot snapshot, String endReason,
                                     List<GrowthOutcome> growth) {
        return new SessionEvent(Type.ENDED, snapshot, endReason, growth);
    }

    public Type type() {
        return type;
    }

    public SessionSnapshot snapshot() {
        return snapshot;
    }

    public String endReason() {
        return endReason;
    }

    public List<GrowthOutcome> growth() {
        return growth;
    }

    @Override
    public String toString() {
        return "SessionEvent{type=" + type + ", endReason=" + endReason
                + ", sessionId=" + (snapshot == null ? 0L : snapshot.sessionId()) + "}";
    }
}
