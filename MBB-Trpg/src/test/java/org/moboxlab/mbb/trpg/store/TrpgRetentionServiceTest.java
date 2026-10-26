package org.moboxlab.mbb.trpg.store;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.moboxlab.mbb.trpg.session.ScheduledTask;
import org.moboxlab.mbb.trpg.session.SessionState;
import org.moboxlab.mbb.trpg.testutil.FakeClock;
import org.moboxlab.mbb.trpg.testutil.FakeScheduler;
import org.moboxlab.mbb.trpg.testutil.TempDirs;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link TrpgRetentionService} 测试：真实 SQLite 上的归档清理语义。
 *
 * <p>覆盖 F5（先子表后父行、不留孤儿）、F6（未结算局永不清理）、F7（子表删除失败
 * 中止本局、下轮重试）与 F9（审计先按时间再按行数）。</p>
 */
public class TrpgRetentionServiceTest {

    private static final long HOUR = 60L * 60L * 1000L;
    private static final long DAY = 24L * HOUR;
    private static final long NOW = 1_700_000_000_000L;

    private File tempDir;
    private JdbcStorageFake storage;
    private SqlSessionStore store;
    private TrpgRetentionService service;
    private FakeClock clock;

    @Before
    public void setUp() {
        tempDir = TempDirs.create("mbb-trpg-retention");
        storage = new JdbcStorageFake(new File(tempDir, "trpg.db"));
        new TrpgSchemaService(storage, null).ensureSchema();
        store = new SqlSessionStore(storage, null);
        clock = new FakeClock(NOW);
        service = new TrpgRetentionService(store, null, clock);
    }

    @After
    public void tearDown() {
        storage.close();
        TempDirs.deleteRecursively(tempDir);
    }

    @Test
    public void purgesExpiredSessionChildrenBeforeParent() {
        long sessionId = endedSession(1L, NOW - 31L * DAY, true);
        store.appendLog(new LogRow(0L, sessionId, 1L, "action", 0L, "旧日志", NOW - 40L * DAY));
        store.insertMember(member(sessionId, 42L));

        TrpgRetentionService.Result result = service.runOnce(NOW);

        assertEquals(1, result.candidates());
        assertEquals(1, result.purged());
        assertEquals(0, result.aborted());
        assertEquals(0, result.skipped());
        assertFalse(result.hasFailure());
        assertNull("过期局主行应被删除", store.findSession(sessionId));
        assertEquals(0L, countRows(TrpgSchemaService.TABLE_TEXT_LOG, sessionId));
        assertEquals(0L, countRows(TrpgSchemaService.TABLE_MEMBER, sessionId));

        List<String> sql = storage.executedSql();
        int logs = indexOfDelete(sql, TrpgSchemaService.TABLE_TEXT_LOG);
        int members = indexOfDelete(sql, TrpgSchemaService.TABLE_MEMBER);
        int session = indexOfDelete(sql, TrpgSchemaService.TABLE_SESSION);
        assertTrue("必须先删日志", logs >= 0);
        assertTrue("日志要先于成员", logs < members);
        assertTrue("成员要先于主行", members < session);
    }

    @Test
    public void keepsYoungSessionsAndNeverPurgesUnsettledOnes() {
        long young = endedSession(1L, NOW - 29L * DAY, true);
        long unsettled = endedSession(2L, NOW - 400L * DAY, false);

        TrpgRetentionService.Result result = service.runOnce(NOW);

        assertEquals("未结算局不进入候选", 0, result.candidates());
        assertEquals(0, result.purged());
        assertNotNull("保留期内的局不动", store.findSession(young));
        assertNotNull("未结算局永不清理", store.findSession(unsettled));
    }

    @Test
    public void abortsWholeSessionWhenChildDeleteFailsAndRetriesNextRun() {
        long sessionId = endedSession(1L, NOW - 31L * DAY, true);
        store.appendLog(new LogRow(0L, sessionId, 1L, "action", 0L, "旧日志", NOW - 40L * DAY));
        store.insertMember(member(sessionId, 42L));

        storage.failNextUpdate(1);
        TrpgRetentionService.Result failed = service.runOnce(NOW);
        assertEquals(1, failed.aborted());
        assertEquals(0, failed.purged());
        assertTrue(failed.hasFailure());
        assertNotNull("子表失败时不得删除父行", store.findSession(sessionId));
        assertEquals("失败的那一步之后不能再删", 1L,
                countRows(TrpgSchemaService.TABLE_MEMBER, sessionId));

        TrpgRetentionService.Result retried = service.runOnce(NOW);
        assertEquals(0, retried.aborted());
        assertEquals(1, retried.purged());
        assertNull(store.findSession(sessionId));
    }

    @Test
    public void skippedWhenParentRowIsGoneButChildrenRemain() {
        long sessionId = endedSession(1L, NOW - 31L * DAY, true);
        SessionStore toggling = new ToggleStore(store) {
            @Override
            public int deleteSession(long ignored) {
                return 0;
            }
        };

        TrpgRetentionService.Result result = new TrpgRetentionService(toggling, null, clock).runOnce(NOW);

        assertEquals(1, result.skipped());
        assertEquals(0, result.purged());
        assertFalse(result.hasFailure());
        assertNotNull("父行没删掉就要留着，等下一轮", store.findSession(sessionId));
    }

    @Test
    public void traceKeepsByTimeFirstThenByRowCount() {
        TrpgRetentionService custom = new TrpgRetentionService(store, null, clock, 30, 168, 5);
        for (int i = 0; i < 2; i++) {
            store.appendAiTrace(trace(1L, NOW - 169L * HOUR));
        }
        for (int i = 0; i < 6; i++) {
            store.appendAiTrace(trace(1L, NOW - 1L * HOUR));
        }

        TrpgRetentionService.Result result = custom.runOnce(NOW);

        assertEquals("先删过期两行", 2, result.traceExpiredRows());
        assertEquals("再截到最新五行", 1, result.traceTrimmedRows());
        assertEquals(5, store.listAiTrace(1L, 0L, 100).size());
    }

    @Test
    public void traceUnderKeepRowsIsNotDeleted() {
        TrpgRetentionService custom = new TrpgRetentionService(store, null, clock, 30, 168, 5000);
        for (int i = 0; i < 3; i++) {
            store.appendAiTrace(trace(1L, NOW - i * 1000L));
        }

        TrpgRetentionService.Result result = custom.runOnce(NOW);

        assertEquals(0, result.traceTrimmedRows());
        assertEquals(3, store.listAiTrace(1L, 0L, 10).size());
    }

    @Test
    public void traceFailureIsReportedButDoesNotStopSessionCleanup() {
        long sessionId = endedSession(1L, NOW - 31L * DAY, true);
        SessionStore failing = new ToggleStore(store) {
            @Override
            public int deleteAiTraceBefore(long createTime) {
                return -1;
            }
        };

        TrpgRetentionService.Result result = new TrpgRetentionService(failing, null, clock).runOnce(NOW);

        assertTrue("审计失败应被标记", result.hasFailure());
        assertTrue("审计删除失败应记为 -1", result.traceExpiredRows() < 0);
        assertEquals("审计失败不影响局清理", 1, result.purged());
        assertNull(store.findSession(sessionId));
    }

    @Test
    public void abortsWhenChildDeleteReturnsZeroButLogsExpected() {
        long sessionId = endedSession(1L, NOW - 31L * DAY, true);
        store.appendLog(new LogRow(0L, sessionId, 1L, "action", 0L, "旧日志", NOW - 40L * DAY));
        SessionStore zeroButExpected = new ToggleStore(store) {
            @Override
            public int deleteLogs(long ignored) {
                return 0;
            }
        };

        TrpgRetentionService.Result result =
                new TrpgRetentionService(zeroButExpected, null, clock).runOnce(NOW);

        assertEquals("预期有日志却删除 0 行，必须中止该局", 1, result.aborted());
        assertEquals(0, result.purged());
        assertTrue(result.hasFailure());
        assertNotNull("中止时不得删除父行", store.findSession(sessionId));
        assertEquals("日志仍在，等下一轮", 1L, countRows(TrpgSchemaService.TABLE_TEXT_LOG, sessionId));
    }

    @Test
    public void abortsWhenMemberDeleteReturnsZeroButMembersExpected() {
        long sessionId = endedSession(1L, NOW - 31L * DAY, true);
        store.insertMember(member(sessionId, 42L));
        SessionStore zeroButExpected = new ToggleStore(store) {
            @Override
            public int deleteMembers(long ignored) {
                return 0;
            }
        };

        TrpgRetentionService.Result result =
                new TrpgRetentionService(zeroButExpected, null, clock).runOnce(NOW);

        assertEquals(1, result.aborted());
        assertEquals(0, result.purged());
        assertNotNull(store.findSession(sessionId));
        assertEquals(1L, countRows(TrpgSchemaService.TABLE_MEMBER, sessionId));
    }

    @Test
    public void childDeleteOfZeroWithNothingToDeleteIsNotFailure() {
        long sessionId = endedSession(1L, NOW - 31L * DAY, true);

        TrpgRetentionService.Result result = service.runOnce(NOW);

        assertEquals("没有子表行的局照常清理", 1, result.purged());
        assertEquals(0, result.aborted());
        assertFalse(result.hasFailure());
        assertNull(store.findSession(sessionId));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNullClock() {
        new TrpgRetentionService(store, null, null);
    }

    @Test
    public void scheduleHourlyRunsOncePerHour() {
        long sessionId = endedSession(1L, NOW - 31L * DAY, true);
        FakeScheduler scheduler = new FakeScheduler(0L);
        ScheduledTask task = service.scheduleHourly(scheduler);

        assertEquals(1, scheduler.pendingCount());
        assertEquals("首次延迟应为一小时", 0, scheduler.advance(TrpgRetentionService.HOURLY_PERIOD_MILLIS - 1L));
        assertEquals(1, scheduler.advance(1L));
        assertNull("任务按注入的 Clock 判定过期局", store.findSession(sessionId));
        assertEquals("一秒后仍在下一小时", 0, scheduler.advance(1000L));
        task.cancel();
        assertTrue(task.isCancelled());
        scheduler.cancelAll();
        assertEquals(0, scheduler.pendingCount());
    }

    @Test
    public void emptyRunReturnsZeroes() {
        TrpgRetentionService.Result result = service.runOnce(NOW);
        assertEquals(0, result.candidates());
        assertEquals(0, result.purged());
        assertEquals(0, result.aborted());
        assertEquals(0, result.skipped());
        assertEquals(0, result.traceExpiredRows());
        assertEquals(0, result.traceTrimmedRows());
        assertFalse(result.hasFailure());
    }

    private long endedSession(long groupId, long endedAt, boolean settled) {
        long id = store.insertSession(SessionRow.builder()
                .groupId(groupId).state(SessionState.RUNNING).kpUserId(1L).scriptId("s")
                .ruleSetId("coc7").sceneIndex(0).sceneTitle("").startedAt(endedAt - DAY)
                .endedAt(0L).endReason("").summary("").lastActivityAt(endedAt)
                .aiEnabled(true).aiSuspendedUntil(0L).aiFailStreak(0).growthSettled(false)
                .createTime(endedAt - DAY).updateTime(endedAt - DAY).build());
        store.casTransition(id, SessionState.RUNNING, SessionState.ENDED, endedAt - DAY, endedAt,
                "结束", settled, "", endedAt);
        return id;
    }

    private MemberRow member(long sessionId, long userId) {
        return MemberRow.builder().sessionId(sessionId).userId(userId).cardJson("{}")
                .successSkillsJson("[]").joinedAt(0L).leftAt(0L).takenOver(false).madnessUntil(0L)
                .createTime(0L).updateTime(0L).build();
    }

    private AiTraceRow trace(long groupId, long createTime) {
        return new AiTraceRow(0L, groupId, 0L, 0L, "action", "ok", "", "", 1, 10L, "stop", "{}",
                false, "摘要", createTime);
    }

    private long countRows(String table, long sessionId) {
        return storage.count("SELECT COUNT(*) AS c FROM " + table + " WHERE sessionID = ?",
                sessionId);
    }

    private static int indexOfDelete(List<String> sql, String table) {
        for (int i = 0; i < sql.size(); i++) {
            String text = sql.get(i);
            if (text.startsWith("DELETE FROM " + table)) {
                return i;
            }
        }
        return -1;
    }


    /** 全部方法转给真实存储，子类只覆盖要观察的那一个。 */
    private static class ToggleStore implements SessionStore {

        private final SessionStore delegate;

        ToggleStore(SessionStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public SessionRow findActiveByGroup(long groupId) {
            return delegate.findActiveByGroup(groupId);
        }

        @Override
        public SessionRow findSession(long sessionId) {
            return delegate.findSession(sessionId);
        }

        @Override
        public SessionRow findLatestArchivedByGroup(long groupId, long endedAfter) {
            return delegate.findLatestArchivedByGroup(groupId, endedAfter);
        }

        @Override
        public long insertSession(SessionRow row) {
            return delegate.insertSession(row);
        }

        @Override
        public int casTransition(long sessionId, SessionState expected, SessionState target,
                                 long startedAt, long endedAt, String endReason,
                                 boolean growthSettled, String summary, long updateTime) {
            return delegate.casTransition(sessionId, expected, target, startedAt, endedAt, endReason,
                    growthSettled, summary, updateTime);
        }

        @Override
        public int casScene(long sessionId, SessionState expected, int sceneIndex, String sceneTitle,
                            long updateTime) {
            return delegate.casScene(sessionId, expected, sceneIndex, sceneTitle, updateTime);
        }

        @Override
        public int casAiState(long sessionId, SessionState expected, boolean aiEnabled,
                              long aiSuspendedUntil, int aiFailStreak, long updateTime) {
            return delegate.casAiState(sessionId, expected, aiEnabled, aiSuspendedUntil, aiFailStreak,
                    updateTime);
        }

        @Override
        public int casScript(long sessionId, SessionState expected, String scriptId,
                             long updateTime) {
            return delegate.casScript(sessionId, expected, scriptId, updateTime);
        }

        @Override
        public int touchActivity(long sessionId, long lastActivityAt, long updateTime) {
            return delegate.touchActivity(sessionId, lastActivityAt, updateTime);
        }

        @Override
        public List<SessionRow> loadActiveSessions() {
            return delegate.loadActiveSessions();
        }

        @Override
        public List<MemberRow> loadMembers(long sessionId) {
            return delegate.loadMembers(sessionId);
        }

        @Override
        public long insertMember(MemberRow row) {
            return delegate.insertMember(row);
        }

        @Override
        public int updateMember(MemberRow row) {
            return delegate.updateMember(row);
        }

        @Override
        public int appendSuccessSkills(long sessionId, long userId, List<String> skills,
                                       long updateTime) {
            return delegate.appendSuccessSkills(sessionId, userId, skills, updateTime);
        }

        @Override
        public List<SessionRow> listUnsettledSessions() {
            return delegate.listUnsettledSessions();
        }

        @Override
        public int settleSummary(long sessionId, String summary, long updateTime) {
            return delegate.settleSummary(sessionId, summary, updateTime);
        }

        @Override
        public int appendLog(LogRow row) {
            return delegate.appendLog(row);
        }

        @Override
        public long maxLogSeq(long sessionId) {
            return delegate.maxLogSeq(sessionId);
        }

        @Override
        public List<LogRow> listLogs(long sessionId, int limit, int offset) {
            return delegate.listLogs(sessionId, limit, offset);
        }

        @Override
        public List<String> listClueKeys(long sessionId) {
            return delegate.listClueKeys(sessionId);
        }

        @Override
        public int insertBlacklist(BlacklistRow row) {
            return delegate.insertBlacklist(row);
        }

        @Override
        public int deleteBlacklist(long groupId, long userId) {
            return delegate.deleteBlacklist(groupId, userId);
        }

        @Override
        public List<BlacklistRow> listBlacklist(long groupId) {
            return delegate.listBlacklist(groupId);
        }

        @Override
        public boolean isBlacklisted(long groupId, long userId) {
            return delegate.isBlacklisted(groupId, userId);
        }

        @Override
        public List<Long> listExpiredSessionIds(long endedBefore) {
            return delegate.listExpiredSessionIds(endedBefore);
        }

        @Override
        public int deleteLogs(long sessionId) {
            return delegate.deleteLogs(sessionId);
        }

        @Override
        public int deleteMembers(long sessionId) {
            return delegate.deleteMembers(sessionId);
        }

        @Override
        public int deleteSession(long sessionId) {
            return delegate.deleteSession(sessionId);
        }

        @Override
        public long appendAiTrace(AiTraceRow row) {
            return delegate.appendAiTrace(row);
        }

        @Override
        public List<AiTraceRow> listAiTrace(long groupId, long sessionId, int limit) {
            return delegate.listAiTrace(groupId, sessionId, limit);
        }

        @Override
        public int deleteAiTraceBefore(long createTime) {
            return delegate.deleteAiTraceBefore(createTime);
        }

        @Override
        public int deleteAiTraceOverflow(int keepRows) {
            return delegate.deleteAiTraceOverflow(keepRows);
        }
    }
}
