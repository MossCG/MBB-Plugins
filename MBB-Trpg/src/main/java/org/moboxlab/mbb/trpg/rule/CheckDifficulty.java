package org.moboxlab.mbb.trpg.rule;

/** d100 检定的难度等级。 */
public enum CheckDifficulty {

    REGULAR("常规"),
    HARD("困难"),
    EXTREME("极难");

    private final String label;

    CheckDifficulty(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
