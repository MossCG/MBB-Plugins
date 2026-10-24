package org.moboxlab.mbb.trpg.store;

import org.moboxlab.mbb.trpg.ai.TraceStore;

import java.util.Collections;
import java.util.List;

/**
 * {@link TraceStore} 的 SQL 实现，把审计端口的四个动作转给 {@link SessionStore}。
 *
 * <p>写失败只返回 false，由调用方记日志，不阻断叙述流程。</p>
 */
public final class SqlTraceStore implements TraceStore {

    private final SessionStore store;

    public SqlTraceStore(SessionStore store) {
        if (store == null) {
            throw new IllegalArgumentException("存储不能为空");
        }
        this.store = store;
    }

    @Override
    public boolean record(AiTraceRow row) {
        if (row == null) {
            return false;
        }
        return store.appendAiTrace(row) > 0L;
    }

    @Override
    public List<AiTraceRow> recent(long groupId, long sessionId, int limit) {
        List<AiTraceRow> rows = store.listAiTrace(groupId, sessionId, limit);
        return rows == null ? Collections.<AiTraceRow>emptyList() : rows;
    }

    @Override
    public void deleteBefore(long createTime) {
        store.deleteAiTraceBefore(createTime);
    }

    @Override
    public void trimTo(int keepRows) {
        store.deleteAiTraceOverflow(keepRows);
    }
}
