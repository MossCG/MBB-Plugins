package org.moboxlab.mbb.trpg.testutil;

import org.moboxlab.mbb.trpg.ai.AiNarrationSink;

import java.util.ArrayList;
import java.util.List;

/** 记录发送内容的叙述端口假件。 */
public final class FakeNarrationSink implements AiNarrationSink {

    private final List<String> narrations = new ArrayList<String>();
    private final List<Boolean> degradedFlags = new ArrayList<Boolean>();
    private final List<String> reasons = new ArrayList<String>();
    private int suspendedNotifications;

    @Override
    public synchronized void send(long groupId, String narration, boolean degraded,
                                  String degradeReason) {
        narrations.add(narration);
        degradedFlags.add(Boolean.valueOf(degraded));
        reasons.add(degradeReason);
    }

    @Override
    public synchronized void notifySuspended(long groupId) {
        suspendedNotifications++;
    }

    /** 已发送的叙述正文，按顺序。 */
    public synchronized List<String> narrations() {
        return new ArrayList<String>(narrations);
    }

    /** 每条叙述是否为降级叙述，与 {@link #narrations()} 同序。 */
    public synchronized List<Boolean> degradedFlags() {
        return new ArrayList<Boolean>(degradedFlags);
    }

    /** 每条降级叙述的降级原因，成功叙述为空串。 */
    public synchronized List<String> reasons() {
        return new ArrayList<String>(reasons);
    }

    /** 挂起提示发送次数。 */
    public synchronized int suspendedNotifications() {
        return suspendedNotifications;
    }
}
