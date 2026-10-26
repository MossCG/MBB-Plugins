package org.moboxlab.mbb.trpg.session;

/** 单次成长结算结果（不可变）：某成员某个技能从旧值涨到新值。 */
public final class GrowthOutcome {

    private final long userId;
    private final String skillId;
    private final int from;
    private final int to;
    private final int rolled;
    private final int delta;

    public GrowthOutcome(long userId, String skillId, int from, int to, int rolled) {
        if (skillId == null || skillId.isEmpty()) {
            throw new IllegalArgumentException("技能不能为空");
        }
        if (from < 0 || from > 99) {
            throw new IllegalArgumentException("旧技能值必须在 0..99：" + from);
        }
        if (to < 0 || to > 99) {
            throw new IllegalArgumentException("新技能值必须在 0..99：" + to);
        }
        if (rolled < 0 || rolled > 10) {
            throw new IllegalArgumentException("成长涨幅必须在 0..10：" + rolled);
        }
        this.userId = userId;
        this.skillId = skillId;
        this.from = from;
        this.to = to;
        this.rolled = rolled;
        this.delta = to - from;
    }

    public long userId() {
        return userId;
    }

    public String skillId() {
        return skillId;
    }

    public int from() {
        return from;
    }

    public int to() {
        return to;
    }

    /** 成长检定掷出的增长量（0 表示未涨点，10 为封顶后的实际涨幅）。 */
    public int rolled() {
        return rolled;
    }

    /** 实际增长量；受 99 上限截断后可能小于 {@link #rolled()}。 */
    public int delta() {
        return delta;
    }

    @Override
    public String toString() {
        return "GrowthOutcome{userId=" + userId + ", skill=" + skillId + ", " + from + "->" + to
                + ", rolled=" + rolled + "}";
    }
}