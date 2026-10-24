package org.moboxlab.mbb.trpg.script;

/**
 * 剧本语义校验失败的受检异常：除错误码、JSON path 与消息外，还携带完整的校验结果。
 *
 * <p>继承 {@link ScriptException} 是为了让调用方既能按旧口径 catch 单个错误码，
 * 也能从中取出全部错误与告警，回执只展示 {@link ScriptValidationResult#reportedErrors()}。</p>
 */
public final class ScriptValidationException extends ScriptException {

    private static final long serialVersionUID = 1L;

    private final transient ScriptValidationResult validation;

    public ScriptValidationException(ScriptValidationResult validation) {
        super(codeOf(validation), pathOf(validation), messageOf(validation));
        this.validation = validation;
    }

    /** 完整的校验结果，含全部错误与告警。 */
    public ScriptValidationResult validation() {
        return validation;
    }

    private static ScriptValidationResult requireResult(ScriptValidationResult validation) {
        if (validation == null) {
            throw new IllegalArgumentException("校验结果不能为空");
        }
        return validation;
    }

    private static String codeOf(ScriptValidationResult validation) {
        ScriptIssue first = firstIssue(requireResult(validation));
        return first == null ? ScriptErrorCodes.E_PARSE : first.code();
    }

    private static String pathOf(ScriptValidationResult validation) {
        ScriptIssue first = firstIssue(requireResult(validation));
        return first == null ? "$" : first.path();
    }

    private static String messageOf(ScriptValidationResult validation) {
        ScriptIssue first = firstIssue(requireResult(validation));
        return first == null ? "剧本校验未通过" : first.message();
    }

    private static ScriptIssue firstIssue(ScriptValidationResult validation) {
        return validation.errors().isEmpty() ? null : validation.errors().get(0);
    }
}
