package org.moboxlab.mbb.trpg.rule;

/** 检定结果。 */
public final class CheckResult {

    /** 大失败。 */
    public static final int DEGREE_FUMBLE = -1;

    /** 失败。 */
    public static final int DEGREE_FAILURE = 0;

    /** 常规成功。 */
    public static final int DEGREE_REGULAR = 1;

    /** 困难成功。 */
    public static final int DEGREE_HARD = 2;

    /** 极难成功。 */
    public static final int DEGREE_EXTREME = 3;

    /** 大成功。 */
    public static final int DEGREE_CRITICAL = 4;

    /** 等级标签，下标 = degree - DEGREE_FUMBLE。 */
    private static final String[] DEGREE_LABELS = {"大失败", "失败", "常规成功", "困难成功",
            "极难成功", "大成功"};

    private final boolean success;
    private final int degree;
    private final RollTrace roll;
    private final String summary;
    private final Integer sanLoss;
    private final boolean sanTriggerInt;
    private final int growthDelta;

    public CheckResult(boolean success, int degree, RollTrace roll, String summary,
                       Integer sanLoss, boolean sanTriggerInt, int growthDelta) {
        if (degree < DEGREE_FUMBLE || degree > DEGREE_CRITICAL) {
            throw new IllegalArgumentException("结果等级必须在 -1..4 之间：" + degree);
        }
        if (growthDelta < 0) {
            throw new IllegalArgumentException("成长值不能为负：" + growthDelta);
        }
        this.success = success;
        this.degree = degree;
        this.roll = roll;
        this.summary = summary;
        this.sanLoss = sanLoss;
        this.sanTriggerInt = sanTriggerInt;
        this.growthDelta = growthDelta;
    }

    /** 失败结果，供规则实现构造。 */
    public static CheckResult failure(RollTrace roll, String summary) {
        return new CheckResult(false, DEGREE_FAILURE, roll, summary, null, false, 0);
    }

    /**
     * 等级标签（与呈现层 TrpgCheckView 的六档一致）。
     *
     * @param degree -1..4
     * @return 中文标签；越界返回空串
     */
    public static String degreeLabel(int degree) {
        if (degree < DEGREE_FUMBLE || degree > DEGREE_CRITICAL) {
            return "";
        }
        return DEGREE_LABELS[degree - DEGREE_FUMBLE];
    }

    public boolean success() {
        return success;
    }

    public int degree() {
        return degree;
    }

    /** 掷骰轨迹；纯常量类检定可为 null。 */
    public RollTrace roll() {
        return roll;
    }

    public String summary() {
        return summary;
    }

    /** 理智损失；非 SAN 检定或未结算时为 null。 */
    public Integer sanLoss() {
        return sanLoss;
    }

    public boolean sanTriggerInt() {
        return sanTriggerInt;
    }

    public int growthDelta() {
        return growthDelta;
    }

    @Override
    public String toString() {
        return "CheckResult{success=" + success + ", degree=" + degree
                + ", roll=" + roll + ", summary=" + summary
                + ", sanLoss=" + sanLoss + ", sanTriggerInt=" + sanTriggerInt
                + ", growthDelta=" + growthDelta + '}';
    }
}
