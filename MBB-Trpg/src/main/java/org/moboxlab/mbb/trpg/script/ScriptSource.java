package org.moboxlab.mbb.trpg.script;

/** 剧本来源：内置（随 JAR 发布、只读）或导入（数据目录）。 */
public enum ScriptSource {

    BUILTIN("内置"),
    IMPORTED("导入");

    private final String label;

    ScriptSource(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
