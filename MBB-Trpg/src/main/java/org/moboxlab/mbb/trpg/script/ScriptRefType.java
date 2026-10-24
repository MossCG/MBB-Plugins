package org.moboxlab.mbb.trpg.script;

/**
 * 剧本内可被其他节点引用的类型：引用存在性检查与错误文案共用一处判别。
 *
 * <p>取值与 {@link ScriptTrigger#when()} 的目标字段一一对应：场景、线索、NPC、检定。</p>
 */
enum ScriptRefType {

    /** 场景：被 scene-enter 触发与场景链引用。 */
    SCENE("场景") {
        @Override
        boolean exists(Script script, String id) {
            return script.sceneById(id) != null;
        }
    },

    /** 线索：被 clue-unlocked 触发、场景与结局引用。 */
    CLUE("线索") {
        @Override
        boolean exists(Script script, String id) {
            return script.clueById(id) != null;
        }
    },

    /** NPC：被场景引用，供私聊秘密与公开钩子使用。 */
    NPC("NPC") {
        @Override
        boolean exists(Script script, String id) {
            return script.npcById(id) != null;
        }
    },

    /** 检定：被 check-resolved 触发与场景引用。 */
    CHECK("检定") {
        @Override
        boolean exists(Script script, String id) {
            return script.checkById(id) != null;
        }
    };

    private final String label;

    ScriptRefType(String label) {
        this.label = label;
    }

    /** 中文类型名，用于错误文案。 */
    String label() {
        return label;
    }

    /** 目标 id 在剧本里是否存在。 */
    abstract boolean exists(Script script, String id);
}
