package org.moboxlab.mbb.trpg.ai;

import org.moboxlab.mbb.trpg.session.Clock;
import org.moboxlab.mbb.trpg.session.SessionSnapshot;
import org.moboxlab.mbb.trpg.session.SessionState;

/** 局视图门禁：开关、进行中、挂起判定与投递前快照比对。 */
final class AiSessionGate {

    private final SessionView view;
    private final Clock clock;
    private final AiTraceWriter traces;

    AiSessionGate(SessionView view, Clock clock, AiTraceWriter traces) {
        this.view = view;
        this.clock = clock;
        this.traces = traces;
    }

    SessionSnapshot snapshot(long groupId) {
        return view.readSnapshot(groupId);
    }

    /** 本局 AI 开关：快照为空视为关闭。 */
    boolean enabled(SessionSnapshot snapshot) {
        return snapshot != null && snapshot.aiEnabled();
    }

    /** 只在进行中调用（§9.3）；招募期、已结束与挂起局一律不调。 */
    boolean running(SessionSnapshot snapshot) {
        return snapshot != null && snapshot.state() == SessionState.RUNNING;
    }

    /** 本局是否处于 AI 挂起期。 */
    boolean suspended(SessionSnapshot snapshot) {
        return snapshot != null && snapshot.aiSuspendedUntil() > clock.nowMillis();
    }

    /** 队列阶段的门禁：还没开始调用，审计里的耗时固定为 0。 */
    boolean deliverableQueued(AiCallRequest request) {
        SessionSnapshot current = snapshot(request.groupId());
        if (current == null || !enabled(current)) {
            return false;
        }
        if (!current.active() || suspended(current)) {
            traces.write(request, "skipped", "", "", 0, 0L, "", "", false, "");
            return false;
        }
        if (current.sessionId() != request.sessionNo()
                || current.sceneVersion() != request.sceneVersion()) {
            traces.write(request, "stale", "", "", 0, 0L, "", "", false, "");
            return false;
        }
        return true;
    }

    /**
     * 投递门禁（§9.6）：本局 AI 关时静默丢弃（零审计），局已结束、挂起、换局或换场景时只记审计。
     *
     * @return true 表示可以发送叙述或降级提示
     */
    boolean deliverable(AiCallRequest request, int attempts, long startAt) {
        SessionSnapshot current = snapshot(request.groupId());
        if (current == null || !enabled(current)) {
            return false;
        }
        if (!current.active() || suspended(current)) {
            traces.write(request, "skipped", "", "", attempts, traces.elapsed(startAt),
                    "", "", false, "");
            return false;
        }
        if (current.sessionId() != request.sessionNo()
                || current.sceneVersion() != request.sceneVersion()) {
            traces.write(request, "stale", "", "", attempts, traces.elapsed(startAt),
                    "", "", false, "");
            return false;
        }
        return true;
    }
}
