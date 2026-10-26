package org.moboxlab.mbb.trpg.rule;

import java.util.Arrays;

/**
 * 掷骰轨迹，供展示与排查。
 *
 * <p>十位数字每项为 0..9；个位为 0..9；归一化结果 1..100。</p>
 */
public final class RollTrace {

    private final int units;
    private final int baseTens;
    private final int[] bonusTens;
    private final int[] penaltyTens;
    private final int selectedTens;
    private final int normalized;

    public RollTrace(int units, int baseTens, int[] bonusTens, int[] penaltyTens,
                     int selectedTens, int normalized) {
        checkDigit(units, "个位");
        checkDigit(baseTens, "基础十位");
        checkDigit(selectedTens, "选中十位");
        if (normalized < 1 || normalized > 100) {
            throw new IllegalArgumentException("归一化结果必须在 1..100 之间：" + normalized);
        }
        if (bonusTens == null || penaltyTens == null) {
            throw new IllegalArgumentException("奖励骰与惩罚骰数组不能为空");
        }
        for (int digit : bonusTens) {
            checkDigit(digit, "奖励骰十位");
        }
        for (int digit : penaltyTens) {
            checkDigit(digit, "惩罚骰十位");
        }
        this.units = units;
        this.baseTens = baseTens;
        this.bonusTens = bonusTens.clone();
        this.penaltyTens = penaltyTens.clone();
        this.selectedTens = selectedTens;
        this.normalized = normalized;
    }

    private static void checkDigit(int digit, String name) {
        if (digit < 0 || digit > 9) {
            throw new IllegalArgumentException(name + "必须在 0..9 之间：" + digit);
        }
    }

    public int units() {
        return units;
    }

    public int baseTens() {
        return baseTens;
    }

    public int[] bonusTens() {
        return bonusTens.clone();
    }

    public int[] penaltyTens() {
        return penaltyTens.clone();
    }

    public int selectedTens() {
        return selectedTens;
    }

    public int normalized() {
        return normalized;
    }

    @Override
    public String toString() {
        return "RollTrace{units=" + units + ", baseTens=" + baseTens
                + ", bonusTens=" + Arrays.toString(bonusTens)
                + ", penaltyTens=" + Arrays.toString(penaltyTens)
                + ", selectedTens=" + selectedTens + ", normalized=" + normalized + '}';
    }
}
