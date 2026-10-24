package org.moboxlab.mbb.trpg.present;

/**
 * 检定/理智/对抗的呈现入参（不可变）：由指令层把规则层结果映射成这里需要的展示值。
 *
 * <p>呈现层不读规则对象，只认「骰面、门槛、等级、附注」这几项，避免规则口径变化
 * 牵动文案。</p>
 */
public final class TrpgCheckView {

    /** 检定结果等级：大失败。 */
    public static final int DEGREE_FUMBLE = -1;
    /** 检定结果等级：失败。 */
    public static final int DEGREE_FAILURE = 0;
    /** 检定结果等级：常规成功。 */
    public static final int DEGREE_REGULAR = 1;
    /** 检定结果等级：困难成功。 */
    public static final int DEGREE_HARD = 2;
    /** 检定结果等级：极难成功。 */
    public static final int DEGREE_EXTREME = 3;
    /** 检定结果等级：大成功。 */
    public static final int DEGREE_CRITICAL = 4;

    private final String rollerName;
    private final String skillLabel;
    private final int rolled;
    private final int target;
    private final int degree;
    private final String note;
    private final boolean sanCheck;
    private final int sanBefore;
    private final int sanAfter;

    private TrpgCheckView(String rollerName, String skillLabel, int rolled, int target, int degree,
                          String note, boolean sanCheck, int sanBefore, int sanAfter) {
        if (rollerName == null || rollerName.trim().isEmpty()) {
            throw new IllegalArgumentException("掷骰者不能为空");
        }
        if (skillLabel == null || skillLabel.trim().isEmpty()) {
            throw new IllegalArgumentException("技能名不能为空");
        }
        if (degree < DEGREE_FUMBLE || degree > DEGREE_CRITICAL) {
            throw new IllegalArgumentException("检定等级必须在 -1..4：" + degree);
        }
        this.rollerName = rollerName;
        this.skillLabel = skillLabel;
        this.rolled = rolled;
        this.target = target;
        this.degree = degree;
        this.note = note == null ? "" : note;
        this.sanCheck = sanCheck;
        this.sanBefore = sanBefore;
        this.sanAfter = sanAfter;
    }

    /** 技能检定。 */
    public static TrpgCheckView skill(String rollerName, String skillLabel, int rolled, int target,
                                      int degree, String note) {
        return new TrpgCheckView(rollerName, skillLabel, rolled, target, degree, note, false, 0, 0);
    }

    /** 理智检定（SAN 前后值与损失由损失值反推）。 */
    public static TrpgCheckView san(String rollerName, int rolled, int target, int degree,
                                    int sanBefore, int sanAfter) {
        return new TrpgCheckView(rollerName, "理智", rolled, target, degree, "", true,
                sanBefore, sanAfter);
    }

    public String rollerName() {
        return rollerName;
    }

    public String skillLabel() {
        return skillLabel;
    }

    public int rolled() {
        return rolled;
    }

    public int target() {
        return target;
    }

    public int degree() {
        return degree;
    }

    /** 附注（奖惩骰说明、失败后果），没有为空串。 */
    public String note() {
        return note;
    }

    public boolean sanCheck() {
        return sanCheck;
    }

    public int sanBefore() {
        return sanBefore;
    }

    public int sanAfter() {
        return sanAfter;
    }

    public int sanLoss() {
        return Math.max(0, sanBefore - sanAfter);
    }

    public boolean success() {
        return degree >= DEGREE_REGULAR;
    }

    /** 等级中文名。 */
    public String degreeLabel() {
        switch (degree) {
            case DEGREE_FUMBLE:
                return "大失败";
            case DEGREE_FAILURE:
                return "失败";
            case DEGREE_REGULAR:
                return "常规成功";
            case DEGREE_HARD:
                return "困难成功";
            case DEGREE_EXTREME:
                return "极难成功";
            default:
                return "大成功";
        }
    }
}
