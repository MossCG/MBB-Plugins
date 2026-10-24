package org.moboxlab.mbb.trpg.script;

/** 剧本触发（不可变）：只产出 KP 提示，不改变状态、不自动发消息。 */
public final class ScriptTrigger {

    public static final String WHEN_SCENE_ENTER = "scene-enter";
    public static final String WHEN_CLUE_UNLOCKED = "clue-unlocked";
    public static final String WHEN_CHECK_RESOLVED = "check-resolved";

    public static final String RESULT_SUCCESS = "success";
    public static final String RESULT_FAILURE = "failure";
    public static final String RESULT_ANY = "any";

    private final String id;
    private final String when;
    private final String sceneId;
    private final String clueId;
    private final String checkId;
    private final String result;
    private final String hint;

    public ScriptTrigger(String id, String when, String sceneId, String clueId, String checkId,
                         String result, String hint) {
        this.id = id == null ? "" : id;
        this.when = when == null ? "" : when;
        this.sceneId = sceneId == null ? "" : sceneId;
        this.clueId = clueId == null ? "" : clueId;
        this.checkId = checkId == null ? "" : checkId;
        this.result = result == null ? "" : result;
        this.hint = hint == null ? "" : hint;
    }

    public String id() {
        return id;
    }

    public String when() {
        return when;
    }

    /** when=scene-enter 时的目标场景；其他情况为空串。 */
    public String sceneId() {
        return sceneId;
    }

    /** when=clue-unlocked 时的目标线索；其他情况为空串。 */
    public String clueId() {
        return clueId;
    }

    /** when=check-resolved 时的目标检定；其他情况为空串。 */
    public String checkId() {
        return checkId;
    }

    /** success / failure / any；非 check-resolved 时为空串。 */
    public String result() {
        return result;
    }

    public String hint() {
        return hint;
    }
}
