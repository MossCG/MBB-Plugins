package org.moboxlab.mbb.trpg.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 局面操作结果（不可变）：成功、固定拒绝或存储异常，附带执行后快照与结算产物。 */
public final class SessionResult {

    /** 结果大类。 */
    public enum Status {
        /** 操作已生效。 */
        OK,
        /** 按状态固定拒绝；{@link #reject()} 给出类别。 */
        REJECTED,
        /** 存储返回 -1 或异常，本步中止；调用方提示稍后重试。 */
        STORAGE
    }

    private final Status status;
    private final SessionReject reject;
    private final String detail;
    private final SessionSnapshot snapshot;
    private final String endReason;
    private final List<GrowthOutcome> growth;
    private final boolean replySuppressed;
    private final boolean confirmationPending;

    private SessionResult(Status status, SessionReject reject, String detail,
                          SessionSnapshot snapshot, String endReason, List<GrowthOutcome> growth,
                          boolean replySuppressed, boolean confirmationPending) {
        this.status = status;
        this.reject = reject;
        this.detail = detail == null ? "" : detail;
        this.snapshot = snapshot;
        this.endReason = endReason == null ? "" : endReason;
        this.growth = Collections.unmodifiableList(new ArrayList<GrowthOutcome>(growth));
        this.replySuppressed = replySuppressed;
        this.confirmationPending = confirmationPending;
    }

    /** 操作成功。 */
    public static SessionResult ok(SessionSnapshot snapshot, String detail) {
        return new SessionResult(Status.OK, null, detail, snapshot, "", new ArrayList<GrowthOutcome>(),
                false, false);
    }

    /** 结束了二次确认的第一次：等待同一发起者再次执行。 */
    public static SessionResult awaitingConfirmation(SessionSnapshot snapshot, String detail) {
        return new SessionResult(Status.OK, null, detail, snapshot, "", new ArrayList<GrowthOutcome>(),
                false, true);
    }

    /** 本局已收尾（正常结束、过期归档或 KP 离场）。 */
    public static SessionResult ended(SessionSnapshot snapshot, String endReason,
                                      List<GrowthOutcome> growth) {
        List<GrowthOutcome> outcomes = growth == null ? new ArrayList<GrowthOutcome>() : growth;
        return new SessionResult(Status.OK, null, "", snapshot, endReason, outcomes, false, false);
    }

    /** 固定拒绝。 */
    public static SessionResult rejected(SessionReject reject, String detail) {
        if (reject == null) {
            throw new IllegalArgumentException("拒绝类别不能为空");
        }
        return new SessionResult(Status.REJECTED, reject, detail, null, "",
                new ArrayList<GrowthOutcome>(), false, false);
    }

    /** 存储异常：本步中止。 */
    public static SessionResult storageFailure(String detail) {
        return new SessionResult(Status.STORAGE, null, detail, null, "",
                new ArrayList<GrowthOutcome>(), false, false);
    }

    /** 标记本次回复被同类错误去重抑制。 */
    public SessionResult withSuppressed(boolean suppressed) {
        return new SessionResult(status, reject, detail, snapshot, endReason, growth,
                suppressed, confirmationPending);
    }

    public Status status() {
        return status;
    }

    public boolean ok() {
        return status == Status.OK;
    }

    public boolean rejected() {
        return status == Status.REJECTED;
    }

    public boolean storageFailed() {
        return status == Status.STORAGE;
    }

    /** 拒绝类别；非拒绝时为 null。 */
    public SessionReject reject() {
        return reject;
    }

    /** 附加说明（中文，可直接进日志或回执）。 */
    public String detail() {
        return detail;
    }

    /** 执行后快照；拒绝与存储异常时为 null。 */
    public SessionSnapshot snapshot() {
        return snapshot;
    }

    /** 收尾原因；未收尾时为空串。 */
    public String endReason() {
        return endReason;
    }

    /** 本次收尾产生的成长结算明细。 */
    public List<GrowthOutcome> growth() {
        return growth;
    }

    /** 本次回复是否应静默（同群同类错误 10 秒去重）。 */
    public boolean replySuppressed() {
        return replySuppressed;
    }

    /** 是否处于二次确认等待状态。 */
    public boolean confirmationPending() {
        return confirmationPending;
    }

    @Override
    public String toString() {
        return "SessionResult{status=" + status + ", reject=" + reject + ", detail=" + detail
                + ", endReason=" + endReason + ", suppressed=" + replySuppressed
                + ", confirming=" + confirmationPending + "}";
    }
}
