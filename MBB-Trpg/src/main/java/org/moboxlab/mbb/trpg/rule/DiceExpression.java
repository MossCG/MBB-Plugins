package org.moboxlab.mbb.trpg.rule;

/**
 * 骰点表达式：{数量}D{面数}{+/-修正}。
 *
 * <p>常量子用数量 0 表示，不参与掷骰。</p>
 */
public final class DiceExpression {

    private final int count;
    private final int sides;
    private final int modifier;

    private DiceExpression(int count, int sides, int modifier) {
        this.count = count;
        this.sides = sides;
        this.modifier = modifier;
    }

    /** 常量表达式，如 {@code 3}。 */
    public static DiceExpression constant(int value) {
        return new DiceExpression(0, 0, value);
    }

    /** 骰点表达式，如 {@code 1D6+2}。 */
    public static DiceExpression dice(int count, int sides, int modifier) {
        if (count < 0) {
            throw new IllegalArgumentException("骰子数量不能为负：" + count);
        }
        if (count > 0 && sides < 1) {
            throw new IllegalArgumentException("骰子面数必须大于 0：" + sides);
        }
        return new DiceExpression(count, sides, modifier);
    }

    /** 掷骰并返回结果。 */
    public int roll(RandomSource random) {
        if (random == null) {
            throw new IllegalArgumentException("随机源不能为空");
        }
        int total = modifier;
        for (int i = 0; i < count; i++) {
            total += random.roll(sides);
        }
        return total;
    }

    /** 最大可能值；大失败取理智损失上限时用，常量表达式即常数本身。 */
    public int maxValue() {
        return count == 0 ? modifier : count * sides + modifier;
    }

    public int count() {
        return count;
    }

    public int sides() {
        return sides;
    }

    public int modifier() {
        return modifier;
    }

    /** 展示用文本，如 {@code 1D6+2}、{@code 3}。 */
    public String describe() {
        if (count == 0) {
            return String.valueOf(modifier);
        }
        StringBuilder builder = new StringBuilder();
        builder.append(count).append('D').append(sides);
        if (modifier > 0) {
            builder.append('+').append(modifier);
        } else if (modifier < 0) {
            builder.append('-').append(-modifier);
        }
        return builder.toString();
    }

    @Override
    public String toString() {
        return describe();
    }
}
