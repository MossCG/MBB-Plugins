package org.moboxlab.mbb.trpg.rule;

/** d100 成长检定请求。 */
public final class D100GrowthCheck implements CheckRequest {

    private final int skillValue;

    public D100GrowthCheck(int skillValue) throws RuleException {
        if (skillValue < 0 || skillValue > 99) {
            throw new RuleException("技能值必须在 0..99 之间：" + skillValue);
        }
        this.skillValue = skillValue;
    }

    @Override
    public String type() {
        return CheckTypes.D100_GROWTH;
    }

    public int skillValue() {
        return skillValue;
    }
}
