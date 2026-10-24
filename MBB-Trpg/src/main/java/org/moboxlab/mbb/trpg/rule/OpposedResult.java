package org.moboxlab.mbb.trpg.rule;

/** 对抗检定结果。 */
public final class OpposedResult {

    private final CheckResult left;
    private final CheckResult right;
    private final OpposedOutcome outcome;
    private final OpposedTieBreak decidedBy;

    public OpposedResult(CheckResult left, CheckResult right,
                         OpposedOutcome outcome, OpposedTieBreak decidedBy) {
        if (left == null || right == null) {
            throw new IllegalArgumentException("对抗双方结果都不能为空");
        }
        if (outcome == null || decidedBy == null) {
            throw new IllegalArgumentException("对抗结论与判定依据不能为空");
        }
        this.left = left;
        this.right = right;
        this.outcome = outcome;
        this.decidedBy = decidedBy;
    }

    public CheckResult left() {
        return left;
    }

    public CheckResult right() {
        return right;
    }

    public OpposedOutcome outcome() {
        return outcome;
    }

    public OpposedTieBreak decidedBy() {
        return decidedBy;
    }
}
