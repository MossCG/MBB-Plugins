package org.moboxlab.mbb.trpg.script;

/** 剧本线索（不可变）；id 即 clueKey，解锁记录用 text_log 的 clue 事件承载。 */
public final class Clue {

    private final String id;
    private final String name;
    private final String lockedSummary;
    private final String unlockedText;

    public Clue(String id, String name, String lockedSummary, String unlockedText) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.lockedSummary = lockedSummary == null ? "" : lockedSummary;
        this.unlockedText = unlockedText == null ? "" : unlockedText;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    /** 未解锁时给 AI 的模糊提示；没有时为空串。 */
    public String lockedSummary() {
        return lockedSummary;
    }

    public String unlockedText() {
        return unlockedText;
    }
}
