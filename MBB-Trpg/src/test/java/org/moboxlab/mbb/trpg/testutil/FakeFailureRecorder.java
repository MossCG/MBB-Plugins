package org.moboxlab.mbb.trpg.testutil;

import org.moboxlab.mbb.trpg.ai.AiFailureOutcome;
import org.moboxlab.mbb.trpg.ai.AiFailureRecorder;

/** 失败计数端口假件：连续失败达到阈值后返回已挂起，成功调用清零。 */
public final class FakeFailureRecorder implements AiFailureRecorder {

    private final int suspendAfter;
    private final long sessionNo;
    private int failures;
    private boolean suspended;

    public FakeFailureRecorder(int suspendAfter) {
        this(suspendAfter, 7L);
    }

    public FakeFailureRecorder(int suspendAfter, long sessionNo) {
        this.suspendAfter = suspendAfter;
        this.sessionNo = sessionNo;
    }

    @Override
    public synchronized AiFailureOutcome recordFailure(long groupId) {
        failures++;
        if (failures >= suspendAfter) {
            suspended = true;
        }
        return AiFailureOutcome.of(sessionNo, suspended, suspended ? 600_000L : 0L, failures);
    }

    @Override
    public synchronized boolean recordSuccess(long groupId) {
        failures = 0;
        return true;
    }

    /** 当前连续失败次数。 */
    public synchronized int failures() {
        return failures;
    }

    /** 是否已进入挂起状态。 */
    public synchronized boolean suspended() {
        return suspended;
    }
}
