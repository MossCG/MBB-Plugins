package org.moboxlab.mbb.trpg.rule;

/** 规则层的参数或状态异常。 */
public class RuleException extends Exception {

    private static final long serialVersionUID = 1L;

    public RuleException(String message) {
        super(message);
    }

    public RuleException(String message, Throwable cause) {
        super(message, cause);
    }
}
