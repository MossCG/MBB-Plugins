package org.moboxlab.mbb.trpg.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 一次收尾（CAS 置 ENDED → 成长 → 摘要）的结果。 */
final class ArchiveResult {

    /** 收尾状态。 */
    enum Status {
        /** 状态、成长与摘要都已落库。 */
        DONE,
        /** 已置 ENDED，但成长或摘要未完成，保留 growthSettled=0 等启动补做。 */
        DEFERRED,
        /** CAS 返回 -1，状态未知，本步中止。 */
        FAILED
    }

    private final Status status;
    private final SessionSnapshot snapshot;
    private final String endReason;
    private final List<GrowthOutcome> growth;
    private final String detail;

    private ArchiveResult(Status status, SessionSnapshot snapshot, String endReason,
                          List<GrowthOutcome> growth, String detail) {
        this.status = status;
        this.snapshot = snapshot;
        this.endReason = endReason == null ? "" : endReason;
        this.growth = Collections.unmodifiableList(new ArrayList<GrowthOutcome>(growth));
        this.detail = detail == null ? "" : detail;
    }

    static ArchiveResult done(SessionSnapshot snapshot, String endReason, List<GrowthOutcome> growth) {
        return new ArchiveResult(Status.DONE, snapshot, endReason, growth, "");
    }

    static ArchiveResult deferred(SessionSnapshot snapshot, String endReason,
                                  List<GrowthOutcome> growth, String detail) {
        return new ArchiveResult(Status.DEFERRED, snapshot, endReason, growth, detail);
    }

    static ArchiveResult failed(String detail) {
        return new ArchiveResult(Status.FAILED, null, "", new ArrayList<GrowthOutcome>(), detail);
    }

    Status status() {
        return status;
    }

    SessionSnapshot snapshot() {
        return snapshot;
    }

    String endReason() {
        return endReason;
    }

    List<GrowthOutcome> growth() {
        return growth;
    }

    String detail() {
        return detail;
    }
}
