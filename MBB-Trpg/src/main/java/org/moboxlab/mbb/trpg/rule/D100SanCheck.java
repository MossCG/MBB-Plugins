package org.moboxlab.mbb.trpg.rule;

/** d100 理智检定请求。 */
public final class D100SanCheck implements CheckRequest {

    private final int currentSan;
    private final DiceExpression successLoss;
    private final DiceExpression failureLoss;

    public D100SanCheck(int currentSan, DiceExpression successLoss, DiceExpression failureLoss)
            throws RuleException {
        if (currentSan < 0 || currentSan > 99) {
            throw new RuleException("当前理智必须在 0..99 之间：" + currentSan);
        }
        if (successLoss == null || failureLoss == null) {
            throw new RuleException("理智损失表达式不能为空");
        }
        this.currentSan = currentSan;
        this.successLoss = successLoss;
        this.failureLoss = failureLoss;
    }

    @Override
    public String type() {
        return CheckTypes.D100_SAN;
    }

    public int currentSan() {
        return currentSan;
    }

    public DiceExpression successLoss() {
        return successLoss;
    }

    public DiceExpression failureLoss() {
        return failureLoss;
    }
}
