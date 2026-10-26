package org.moboxlab.mbb.trpg.ai;

/**
 * 一次 AI 失败计数提交的结果（规格 §9.5 / §9.6）。
 *
 * <p>由 {@code SessionService} 在群锁内 CAS 落库后返回：宿主持有它决定是否发挂起提示；
 * {@code available=false} 表示没有可写的活跃局或 CAS 未生效，调用方按「不计入失败」处理。</p>
 */
public final class AiFailureOutcome {

    private final boolean available;
    private final long sessionId;
    private final boolean suspendedNow;
    private final long suspendedUntil;
    private final int failStreak;

    private AiFailureOutcome(boolean available, long sessionId, boolean suspendedNow,
                             long suspendedUntil, int failStreak) {
        this.available = available;
        this.sessionId = sessionId;
        this.suspendedNow = suspendedNow;
        this.suspendedUntil = suspendedUntil;
        this.failStreak = failStreak;
    }

    /** 没有可写的活跃局 / 本群正忙 / CAS 未生效。 */
    public static AiFailureOutcome unavailable() {
        return new AiFailureOutcome(false, 0L, false, 0L, 0);
    }

    /** 写入成功。 */
    public static AiFailureOutcome of(long sessionId, boolean suspendedNow, long suspendedUntil,
                                      int failStreak) {
        return new AiFailureOutcome(true, sessionId, suspendedNow, suspendedUntil, failStreak);
    }

    /** 是否成功写入失败计数。 */
    public boolean available() {
        return available;
    }

    public long sessionId() {
        return sessionId;
    }

    /** 本次失败是否触发了挂起。 */
    public boolean suspendedNow() {
        return suspendedNow;
    }

    /** 挂起到期时间；未挂起时为原值或 0。 */
    public long suspendedUntil() {
        return suspendedUntil;
    }

    /** 写入后的连续失败次数（触发挂起时为 0）。 */
    public int failStreak() {
        return failStreak;
    }
}
