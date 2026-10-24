package org.moboxlab.mbb.trpg.rule;

/** 对抗检定请求。 */
public final class OpposedCheck {

    private final D100SkillCheck left;
    private final D100SkillCheck right;

    public OpposedCheck(D100SkillCheck left, D100SkillCheck right) {
        if (left == null || right == null) {
            throw new IllegalArgumentException("对抗双方都不能为空");
        }
        this.left = left;
        this.right = right;
    }

    public D100SkillCheck left() {
        return left;
    }

    public D100SkillCheck right() {
        return right;
    }
}
