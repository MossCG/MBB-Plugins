package org.moboxlab.mbb.trpg.script;

import java.util.Collections;
import java.util.List;

/**
 * 一次剧本生成的结果（不可变），供指令层组装回执与日志。
 *
 * <p>失败时 {@link #errorCode()} 取值见 {@link ScriptErrorCodes} 的 {@code E_AI_*}；
 * {@link #errors()} 是校验失败时前 5 条错误（其他失败为空）；
 * 原文已存档时 {@link #savedFile()} 非空，供回执展示文件名。</p>
 */
public final class ScriptGenerationOutcome {

    private final boolean success;
    private final ScriptInfo script;
    private final int sceneCount;
    private final String errorCode;
    private final String savedFile;
    private final List<ScriptIssue> errors;
    private final int errorCount;
    private final long elapsedMs;

    private ScriptGenerationOutcome(boolean success, ScriptInfo script, int sceneCount,
                                    String errorCode, String savedFile, List<ScriptIssue> errors,
                                    int errorCount, long elapsedMs) {
        this.success = success;
        this.script = script;
        this.sceneCount = sceneCount;
        this.errorCode = errorCode == null ? "" : errorCode;
        this.savedFile = savedFile == null ? "" : savedFile;
        this.errors = errors == null ? Collections.<ScriptIssue>emptyList() : errors;
        this.errorCount = errorCount;
        this.elapsedMs = elapsedMs;
    }

    /** 生成并保存成功。 */
    public static ScriptGenerationOutcome success(ScriptInfo script, int sceneCount,
                                                  long elapsedMs) {
        return new ScriptGenerationOutcome(true, script, sceneCount, "", "",
                Collections.<ScriptIssue>emptyList(), 0, elapsedMs);
    }

    /** 生成失败；savedFile、errors 可为空。 */
    public static ScriptGenerationOutcome failure(String errorCode, String savedFile,
                                                  List<ScriptIssue> errors, int errorCount,
                                                  long elapsedMs) {
        return new ScriptGenerationOutcome(false, null, 0, errorCode, savedFile, errors,
                errorCount, elapsedMs);
    }

    public boolean success() {
        return success;
    }

    public ScriptInfo script() {
        return script;
    }

    public int sceneCount() {
        return sceneCount;
    }

    public String errorCode() {
        return errorCode;
    }

    public String savedFile() {
        return savedFile;
    }

    /** 校验失败时的前 5 条错误。 */
    public List<ScriptIssue> errors() {
        return errors;
    }

    /** 校验失败的完整错误条数。 */
    public int errorCount() {
        return errorCount;
    }

    public long elapsedMs() {
        return elapsedMs;
    }
}
