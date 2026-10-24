package org.moboxlab.mbb.trpg.rule;

/**
 * 规则集 SPI，编译期注册。
 *
 * <p>实现必须是纯函数：只读请求、只通过注入的 {@link RandomSource} 掷骰，
 * 不写存档、不发消息。</p>
 */
public interface RuleSet {

    /** 规则集唯一 ID，如 {@code d100}。 */
    String id();

    /** 展示名。 */
    String displayName();

    /** 是否支持该检定类型。 */
    boolean supports(String checkType);

    /** 结算单次检定。 */
    CheckResult resolve(CheckRequest request, RandomSource random) throws RuleException;

    /** 结算对抗检定。 */
    OpposedResult resolveOpposed(OpposedCheck check, RandomSource random) throws RuleException;
}
