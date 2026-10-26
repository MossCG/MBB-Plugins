package org.moboxlab.mbb.trpg.store;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.moboxlab.mbb.trpg.testutil.TempDirs;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** {@link SqlTraceStore} 测试：把审计端口转给存储，写失败只返回 false。 */
public class SqlTraceStoreTest {

    private File tempDir;
    private JdbcStorageFake storage;
    private SqlSessionStore store;
    private SqlTraceStore traceStore;

    @Before
    public void setUp() {
        tempDir = TempDirs.create("mbb-trpg-trace");
        storage = new JdbcStorageFake(new File(tempDir, "trpg.db"));
        new TrpgSchemaService(storage, null).ensureSchema();
        store = new SqlSessionStore(storage, null);
        traceStore = new SqlTraceStore(store);
    }

    @After
    public void tearDown() {
        storage.close();
        TempDirs.deleteRecursively(tempDir);
    }

    @Test
    public void recordWritesAndReportsFailure() {
        assertTrue(traceStore.record(trace(1L, 0L, 1000L)));
        storage.failNextInsert(1);
        assertFalse("写入失败应只返回 false", traceStore.record(trace(1L, 0L, 2000L)));
        assertFalse("空行不写库", traceStore.record(null));
        assertEquals(1, traceStore.recent(1L, 0L, 10).size());
    }

    @Test
    public void recentPassesGroupAndSessionFilters() {
        traceStore.record(trace(1L, 10L, 1000L));
        traceStore.record(trace(1L, 20L, 2000L));
        traceStore.record(trace(2L, 10L, 3000L));

        assertEquals(1, traceStore.recent(1L, 10L, 10).size());
        assertEquals("sessionId 为 0 时只按群过滤", 2, traceStore.recent(1L, 0L, 10).size());
        assertEquals(1, traceStore.recent(1L, 0L, 1).size());
        assertEquals(0, traceStore.recent(3L, 0L, 10).size());
        assertTrue(traceStore.recent(1L, 10L, 0).isEmpty());
    }

    @Test
    public void deleteBeforeAndTrimForwardToStore() {
        for (int i = 0; i < 6; i++) {
            traceStore.record(trace(1L, 0L, 1000L + i));
        }
        traceStore.deleteBefore(1003L);
        List<AiTraceRow> left = traceStore.recent(1L, 0L, 10);
        assertEquals(3, left.size());
        assertEquals(1005L, left.get(0).createTime());

        traceStore.trimTo(2);
        assertEquals(2, traceStore.recent(1L, 0L, 10).size());
    }

    private AiTraceRow trace(long groupId, long sessionId, long createTime) {
        return new AiTraceRow(0L, groupId, sessionId, 0L, "action", "ok", "", "", 1, 10L, "stop",
                "{}", false, "摘要", createTime);
    }

}
