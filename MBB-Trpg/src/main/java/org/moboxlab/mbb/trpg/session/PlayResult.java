package org.moboxlab.mbb.trpg.session;

/**
 * 带明细的局面操作结果：统一承载拒绝与明细数据。
 *
 * <p>局面层既要回执文本（拒绝原因），又要给指令层结构化数据（掷骰明细、解锁线索、
 * 触发器提示），因此用一层包装，避免让 SessionResult 变成万能结构。</p>
 *
 * @param <T> 明细类型
 */
public final class PlayResult<T> {

    private final SessionResult result;
    private final T detail;

    private PlayResult(SessionResult result, T detail) {
        if (result == null) {
            throw new IllegalArgumentException("局面结果不能为空");
        }
        this.result = result;
        this.detail = detail;
    }

    /** 包装成功结果与明细。 */
    public static <T> PlayResult<T> of(SessionResult result, T detail) {
        return new PlayResult<T>(result, detail);
    }

    /** 包装拒绝或无明细的结果。 */
    public static <T> PlayResult<T> rejected(SessionResult result) {
        return new PlayResult<T>(result, null);
    }

    /** 局面结果。 */
    public SessionResult result() {
        return result;
    }

    /** 明细数据；被拒绝时为 null。 */
    public T detail() {
        return detail;
    }

    /** 是否成功并带明细。 */
    public boolean ok() {
        return result.ok() && detail != null;
    }
}
