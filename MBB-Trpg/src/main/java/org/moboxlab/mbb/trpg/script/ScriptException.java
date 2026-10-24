package org.moboxlab.mbb.trpg.script;

/**
 * 剧本操作失败的受检异常：带错误码、JSON path 与中文消息。
 *
 * <p>错误码口径见 docs/scripts/README.md 的「校验规则与错误模型」，
 * 指令层据此映射回执文案，不再自行编造错误码。</p>
 */
public class ScriptException extends Exception {

    private static final long serialVersionUID = 1L;

    private final String code;
    private final String path;

    public ScriptException(String code, String path, String message) {
        super(message == null ? "" : message);
        this.code = code == null ? "" : code;
        this.path = path == null ? "" : path;
    }

    public ScriptException(String code, String path, String message, Throwable cause) {
        super(message == null ? "" : message, cause);
        this.code = code == null ? "" : code;
        this.path = path == null ? "" : path;
    }

    /** 错误码，例如 E_PARSE、E_REF。 */
    public String code() {
        return code;
    }

    /** 出错的 JSON path，整体失败时可能是 "$"。 */
    public String path() {
        return path;
    }

    @Override
    public String toString() {
        return code + " @" + path + "：" + getMessage();
    }
}
