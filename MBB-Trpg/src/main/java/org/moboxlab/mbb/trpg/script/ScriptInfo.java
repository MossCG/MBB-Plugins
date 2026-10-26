package org.moboxlab.mbb.trpg.script;

/** /trpg script list 用的剧本摘要（不含正文）。 */
public final class ScriptInfo {

    private final String id;
    private final String name;
    private final ScriptSource source;
    private final int sceneCount;
    private final int clueCount;
    private final int npcCount;
    private final int checkCount;
    private final int triggerCount;
    private final int endingCount;

    public ScriptInfo(Script script, ScriptSource source) {
        this.id = script.id();
        this.name = script.name();
        this.source = source;
        this.sceneCount = script.scenes().size();
        this.clueCount = script.clues().size();
        this.npcCount = script.npcs().size();
        this.checkCount = script.checks().size();
        this.triggerCount = script.triggers().size();
        this.endingCount = script.endings().size();
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public ScriptSource source() {
        return source;
    }

    public int sceneCount() {
        return sceneCount;
    }

    public int clueCount() {
        return clueCount;
    }

    public int npcCount() {
        return npcCount;
    }

    public int checkCount() {
        return checkCount;
    }

    public int triggerCount() {
        return triggerCount;
    }

    public int endingCount() {
        return endingCount;
    }
}
