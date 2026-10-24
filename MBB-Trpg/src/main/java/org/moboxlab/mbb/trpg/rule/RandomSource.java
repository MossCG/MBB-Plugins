package org.moboxlab.mbb.trpg.rule;

/**
 * 随机源端口。
 *
 * <p>规则层只通过本接口掷骰，测试可注入可记录骰面的实现，不做全局随机。</p>
 */
public interface RandomSource {

    /** 掷一次 {@code sides} 面骰，返回 1..sides。 */
    int roll(int sides);

    /** 掷一次 D10，返回 0..9。 */
    default int d10() {
        return roll(10) - 1;
    }
}
