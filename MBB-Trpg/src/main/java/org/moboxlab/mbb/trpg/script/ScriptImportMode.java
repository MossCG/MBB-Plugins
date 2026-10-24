package org.moboxlab.mbb.trpg.script;

/** 剧本导入模式：新建要求目标 id 不存在，替换要求目标 id 已存在（否则按新建处理）。 */
public enum ScriptImportMode {

    /** 新建导入：目标 id 已存在时回 E_EXISTS。 */
    CREATE("新建"),

    /** 替换导入：目标 id 已存在时覆盖，仍受引用检查限制。 */
    REPLACE("替换");

    private final String label;

    ScriptImportMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isReplace() {
        return this == REPLACE;
    }
}
