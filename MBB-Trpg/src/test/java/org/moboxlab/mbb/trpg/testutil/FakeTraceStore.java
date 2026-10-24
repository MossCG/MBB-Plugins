package org.moboxlab.mbb.trpg.testutil;

import org.moboxlab.mbb.trpg.ai.TraceStore;
import org.moboxlab.mbb.trpg.store.AiTraceRow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 审计端口假件：记录全部写入行，可注入写入失败。 */
public final class FakeTraceStore implements TraceStore {

    private final List<AiTraceRow> rows = new ArrayList<AiTraceRow>();
    private int failNextRecord;

    @Override
    public boolean record(AiTraceRow row) {
        if (failNextRecord > 0) {
            failNextRecord--;
            return false;
        }
        if (row != null) {
            rows.add(row);
        }
        return true;
    }

    @Override
    public List<AiTraceRow> recent(long groupId, long sessionId, int limit) {
        List<AiTraceRow> result = new ArrayList<AiTraceRow>();
        for (int i = rows.size() - 1; i >= 0 && result.size() < limit; i--) {
            AiTraceRow row = rows.get(i);
            if (row.groupId() == groupId && row.sessionId() == sessionId) {
                result.add(row);
            }
        }
        return result;
    }

    @Override
    public void deleteBefore(long createTime) {
        List<AiTraceRow> kept = new ArrayList<AiTraceRow>();
        for (AiTraceRow row : rows) {
            if (row.createTime() >= createTime) {
                kept.add(row);
            }
        }
        rows.clear();
        rows.addAll(kept);
    }

    @Override
    public void trimTo(int keepRows) {
        while (rows.size() > Math.max(0, keepRows)) {
            rows.remove(0);
        }
    }

    /** 注入接下来的写入失败次数。 */
    public void failNextRecord(int times) {
        this.failNextRecord = times;
    }

    /** 全部写入行，按写入顺序。 */
    public List<AiTraceRow> rows() {
        return Collections.unmodifiableList(rows);
    }

    public void clear() {
        rows.clear();
    }
}
