package org.moboxlab.mbb.trpg.script;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 一次导入结束后的结果：落盘后的剧本摘要、是否覆盖了旧剧本、以及校验阶段收集到的告警。 */
public final class ScriptImportResult {

    private final ScriptInfo script;
    private final boolean replaced;
    private final List<ScriptIssue> warnings;

    public ScriptImportResult(ScriptInfo script, boolean replaced, List<ScriptIssue> warnings) {
        if (script == null) {
            throw new IllegalArgumentException("剧本摘要不能为空");
        }
        if (warnings == null) {
            throw new IllegalArgumentException("告警列表不能为空");
        }
        this.script = script;
        this.replaced = replaced;
        this.warnings = Collections.unmodifiableList(new ArrayList<ScriptIssue>(warnings));
    }

    public ScriptInfo script() {
        return script;
    }

    /** 本次导入是否覆盖了已有导入剧本。 */
    public boolean replaced() {
        return replaced;
    }

    public List<ScriptIssue> warnings() {
        return warnings;
    }

    public int warningCount() {
        return warnings.size();
    }
}
