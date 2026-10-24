package org.moboxlab.mbb.trpg.rule;

/** d100 技能检定请求。技能值已含全部修正，范围为 0..99。 */
public final class D100SkillCheck implements CheckRequest {

    private final String skillId;
    private final String skillLabel;
    private final int skillValue;
    private final CheckDifficulty difficulty;
    private final int bonusDice;
    private final int penaltyDice;

    /** 默认常规难度、无奖励惩罚骰的检定。 */
    public D100SkillCheck(String skillId, String skillLabel, int skillValue) throws RuleException {
        this(skillId, skillLabel, skillValue, CheckDifficulty.REGULAR, 0, 0);
    }

    /** 默认常规难度、带奖励/惩罚骰的检定。 */
    public D100SkillCheck(String skillId, String skillLabel, int skillValue,
                          int bonusDice, int penaltyDice) throws RuleException {
        this(skillId, skillLabel, skillValue, CheckDifficulty.REGULAR, bonusDice, penaltyDice);
    }

    public D100SkillCheck(String skillId, String skillLabel, int skillValue,
                          CheckDifficulty difficulty, int bonusDice, int penaltyDice)
            throws RuleException {
        if (skillValue < 0 || skillValue > 99) {
            throw new RuleException("技能值必须在 0..99 之间：" + skillValue);
        }
        if (difficulty == null) {
            throw new RuleException("难度不能为空");
        }
        if (bonusDice < 0 || bonusDice > 2) {
            throw new RuleException("奖励骰数量必须在 0..2 之间：" + bonusDice);
        }
        if (penaltyDice < 0 || penaltyDice > 2) {
            throw new RuleException("惩罚骰数量必须在 0..2 之间：" + penaltyDice);
        }
        this.skillId = skillId;
        this.skillLabel = skillLabel;
        this.skillValue = skillValue;
        this.difficulty = difficulty;
        this.bonusDice = bonusDice;
        this.penaltyDice = penaltyDice;
    }

    @Override
    public String type() {
        return CheckTypes.D100_SKILL;
    }

    public String skillId() {
        return skillId;
    }

    public String skillLabel() {
        return skillLabel;
    }

    public int skillValue() {
        return skillValue;
    }

    public CheckDifficulty difficulty() {
        return difficulty;
    }

    public int bonusDice() {
        return bonusDice;
    }

    public int penaltyDice() {
        return penaltyDice;
    }
}
