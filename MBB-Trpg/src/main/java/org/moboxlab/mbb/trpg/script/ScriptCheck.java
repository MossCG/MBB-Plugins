package org.moboxlab.mbb.trpg.script;

import org.moboxlab.mbb.trpg.rule.DiceExpression;

/**
 * 剧本里的建议检定（不可变）。
 *
 * <p>只是 KP 与 AI 的参考，不会被自动掷骰或自动扣 SAN；发起仍由 KP 经 /trpg check、/trpg san 完成。</p>
 */
public final class ScriptCheck {

    public static final String TYPE_SKILL = "d100.skill";
    public static final String TYPE_SAN = "d100.san";

    public static final String DIFFICULTY_REGULAR = "regular";
    public static final String DIFFICULTY_HARD = "hard";
    public static final String DIFFICULTY_EXTREME = "extreme";

    private final String id;
    private final String type;
    private final String skill;
    private final String difficulty;
    private final int bonusDice;
    private final int penaltyDice;
    private final DiceExpression successLoss;
    private final DiceExpression failureLoss;
    private final String prompt;
    private final String successHint;
    private final String failureHint;

    public ScriptCheck(String id, String type, String skill, String difficulty, int bonusDice,
                       int penaltyDice, DiceExpression successLoss, DiceExpression failureLoss,
                       String prompt, String successHint, String failureHint) {
        this.id = id == null ? "" : id;
        this.type = type == null ? "" : type;
        this.skill = skill == null ? "" : skill;
        this.difficulty = difficulty == null ? "" : difficulty;
        this.bonusDice = bonusDice;
        this.penaltyDice = penaltyDice;
        this.successLoss = successLoss;
        this.failureLoss = failureLoss;
        this.prompt = prompt == null ? "" : prompt;
        this.successHint = successHint == null ? "" : successHint;
        this.failureHint = failureHint == null ? "" : failureHint;
    }

    public String id() {
        return id;
    }

    public String type() {
        return type;
    }

    public boolean skillCheck() {
        return TYPE_SKILL.equals(type);
    }

    public boolean sanCheck() {
        return TYPE_SAN.equals(type);
    }

    /** 技能展示名；SAN 检定为空串。 */
    public String skill() {
        return skill;
    }

    /** regular / hard / extreme；缺省为 regular。 */
    public String difficulty() {
        return difficulty;
    }

    public int bonusDice() {
        return bonusDice;
    }

    public int penaltyDice() {
        return penaltyDice;
    }

    /** SAN 成功时的损失表达式；技能检定或未填写时为 null。 */
    public DiceExpression successLoss() {
        return successLoss;
    }

    /** SAN 失败时的损失表达式；技能检定或未填写时为 null。 */
    public DiceExpression failureLoss() {
        return failureLoss;
    }

    public String prompt() {
        return prompt;
    }

    public String successHint() {
        return successHint;
    }

    public String failureHint() {
        return failureHint;
    }
}
