package org.moboxlab.mbb.trpg.script;

/** 剧本校验问题：错误码、JSON path 与中文消息（错误与告警共用一种形状）。 */
public final class ScriptIssue {

    private final String code;
    private final String path;
    private final String message;

    public ScriptIssue(String code, String path, String message) {
        this.code = code == null ? "" : code;
        this.path = path == null ? "" : path;
        this.message = message == null ? "" : message;
    }

    public String code() {
        return code;
    }

    public String path() {
        return path;
    }

    public String message() {
        return message;
    }

    /** 告警以 W_ 开头，不阻断导入。 */
    public boolean warning() {
        return code.startsWith("W_");
    }

    @Override
    public String toString() {
        return code + " @" + path + "：" + message;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ScriptIssue)) {
            return false;
        }
        ScriptIssue issue = (ScriptIssue) other;
        return code.equals(issue.code) && path.equals(issue.path) && message.equals(issue.message);
    }

    @Override
    public int hashCode() {
        int result = code.hashCode();
        result = 31 * result + path.hashCode();
        result = 31 * result + message.hashCode();
        return result;
    }
}
