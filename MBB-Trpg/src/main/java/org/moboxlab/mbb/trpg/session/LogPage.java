package org.moboxlab.mbb.trpg.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.moboxlab.mbb.trpg.store.LogRow;

/**
 * 日志回看的一页结果（§6.1 log）。
 *
 * <p>成功时带定位信息与当页日志行；失败时带固定拒绝类别与原因，页字段取安全默认值。</p>
 */
public final class LogPage {

    private final SessionReject reject;
    private final String detail;
    private final boolean suppressed;
    private final long sessionId;
    private final String scriptId;
    private final SessionState state;
    private final int page;
    private final int pageSize;
    private final int totalPages;
    private final long total;
    private final List<LogRow> rows;

    private LogPage(SessionReject reject, String detail, boolean suppressed, long sessionId,
            String scriptId, SessionState state, int page, int pageSize, int totalPages, long total,
            List<LogRow> rows) {
        this.reject = reject;
        this.detail = detail;
        this.suppressed = suppressed;
        this.sessionId = sessionId;
        this.scriptId = scriptId;
        this.state = state;
        this.page = page;
        this.pageSize = pageSize;
        this.totalPages = totalPages;
        this.total = total;
        this.rows = rows == null ? Collections.<LogRow>emptyList()
                : Collections.unmodifiableList(new ArrayList<LogRow>(rows));
    }

    static LogPage ok(long sessionId, String scriptId, SessionState state, int page, int pageSize,
            int totalPages, long total, List<LogRow> rows) {
        return new LogPage(null, "", false, sessionId, scriptId, state, page, pageSize, totalPages,
                total, rows);
    }

    static LogPage rejected(SessionReject reject, String detail, boolean suppressed) {
        return new LogPage(reject, detail, suppressed, 0L, "", SessionState.NONE, 1, 0, 1, 0L, null);
    }

    public boolean ok() {
        return reject == null;
    }

    public SessionReject reject() {
        return reject;
    }

    public String detail() {
        return detail;
    }

    /** 同类错误 10 秒去重命中时为 true，调用方应保持静默。 */
    public boolean replySuppressed() {
        return suppressed;
    }

    public long sessionId() {
        return sessionId;
    }

    public String scriptId() {
        return scriptId;
    }

    public SessionState state() {
        return state;
    }

    public int page() {
        return page;
    }

    public int pageSize() {
        return pageSize;
    }

    public int totalPages() {
        return totalPages;
    }

    public long total() {
        return total;
    }

    public List<LogRow> rows() {
        return rows;
    }
}
