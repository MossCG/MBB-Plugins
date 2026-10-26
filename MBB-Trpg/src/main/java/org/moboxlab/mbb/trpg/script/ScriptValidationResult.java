package org.moboxlab.mbb.trpg.script;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 剧本校验结果：通过时带解析好的 {@link Script}，失败时带错误列表。 */
public final class ScriptValidationResult {

    /** 回执里最多展示的错误条数，超出部分由调用方追加「另有 N 条」。 */
    public static final int MAX_REPORTED_ERRORS = 5;

    private final Script script;
    private final List<ScriptIssue> errors;
    private final List<ScriptIssue> warnings;

    private ScriptValidationResult(Script script, List<ScriptIssue> errors, List<ScriptIssue> warnings) {
        this.script = script;
        this.errors = Collections.unmodifiableList(new ArrayList<ScriptIssue>(errors));
        this.warnings = Collections.unmodifiableList(new ArrayList<ScriptIssue>(warnings));
    }

    public static ScriptValidationResult ok(Script script, List<ScriptIssue> warnings) {
        return new ScriptValidationResult(script, Collections.<ScriptIssue>emptyList(), warnings);
    }

    public static ScriptValidationResult failed(List<ScriptIssue> errors) {
        return new ScriptValidationResult(null, errors, Collections.<ScriptIssue>emptyList());
    }

    /** 校验是否通过；通过时 {@link #script()} 非 null 且没有错误。 */
    public boolean ok() {
        return script != null && errors.isEmpty();
    }

    /** 解析好的剧本；未通过时为 null。 */
    public Script script() {
        return script;
    }

    /** 全部错误（按校验顺序）；回执展示用 {@link #reportedErrors()}。 */
    public List<ScriptIssue> errors() {
        return errors;
    }

    /** 回执要展示的错误：最多 {@link #MAX_REPORTED_ERRORS} 条，按校验顺序取前几条。 */
    public List<ScriptIssue> reportedErrors() {
        if (errors.size() <= MAX_REPORTED_ERRORS) {
            return errors;
        }
        return Collections.unmodifiableList(new ArrayList<ScriptIssue>(
                errors.subList(0, MAX_REPORTED_ERRORS)));
    }

    /** 回执没展示的错误条数，用于「另有 N 条」；不超过上限时为 0。 */
    public int remainingErrorCount() {
        return errors.size() - reportedErrors().size();
    }

    /** 告警（W_ 开头，不阻断导入）。 */
    public List<ScriptIssue> warnings() {
        return warnings;
    }

    public int errorCount() {
        return errors.size();
    }

    public int warningCount() {
        return warnings.size();
    }

    /** 所有错误码，方便测试与日志按码断言。 */
    public List<String> errorCodes() {
        List<String> codes = new ArrayList<String>(errors.size());
        for (ScriptIssue issue : errors) {
            codes.add(issue.code());
        }
        return codes;
    }
}
