package org.moboxlab.mbb.trpg.rule;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 生产用随机源：线程本地随机数，返回 1..sides。
 *
 * <p>规则层只认 {@link RandomSource}；本类不持有可变状态，可直接共享。</p>
 */
public final class DefaultRandomSource implements RandomSource {

    /** 共享实例。 */
    public static final DefaultRandomSource INSTANCE = new DefaultRandomSource();

    private DefaultRandomSource() {
    }

    @Override
    public int roll(int sides) {
        if (sides < 1) {
            throw new IllegalArgumentException("骰子面数必须大于 0：" + sides);
        }
        return ThreadLocalRandom.current().nextInt(sides) + 1;
    }
}
