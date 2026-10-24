package org.moboxlab.mbb.trpg.store;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.moboxlab.mbb.trpg.session.SessionState;
import org.moboxlab.mbb.trpg.testutil.TempDirs;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link SqlSessionStore} 契约测试：连真实 SQLite，一处一个临时库。
 *
 * <p>覆盖主程序 StorageService 的返回码语义（-1 与 0 分开、query 只回部分行不崩）、
 * 唯一约束、字符截断与全部 SQL 的插件作用域。</p>
 */
public class SqlSessionStoreTest {

    private File dbFile;
    private File tempDir;
    private JdbcStorageFake storage;
    private SqlSessionStore store;

    @Before
    public void setUp() {
        tempDir = TempDirs.create("mbb-trpg-store");
        dbFile = new File(tempDir, "trpg.db");
        storage = new JdbcStorageFake(dbFile);
        new TrpgSchemaService(storage, null).ensureSchema();
        store = new SqlSessionStore(storage, null);
    }

    @After
    public void tearDown() {
        storage.close();
        TempDirs.deleteRecursively(tempDir);
    }

    @Test
    public void allSqlStayInsidePluginScope() {
        List<String> statements = SqlSessionStore.sqlStatements();
        assertTrue("SQL 语句数量应覆盖全部存储动作", statements.size() >= 30);
        for (String sql : statements) {
            assertTrue("SQL 必须带 plugin_ 前缀且不得触碰主程序表：" + sql,
                    TrpgSchemaService.isPluginScopedSql(sql));
        }
    }

    @Test
    public void insertSessionWritesEveryColumnAndReadsBack() {
        SessionRow row = fullSession(7L, SessionState.RUNNING);
        long id = store.insertSession(row);
        assertTrue("插入局应返回自增主键", id > 0L);

        SessionRow loaded = store.findSession(id);
        assertNotNull("按主键应能读回", loaded);
        assertEquals(id, loaded.id());
        assertEquals(7L, loaded.groupId());
        assertEquals(SessionState.RUNNING, loaded.state());
        assertEquals(9001L, loaded.kpUserId());
        assertEquals("script-a", loaded.scriptId());
        assertEquals("coc7", loaded.ruleSetId());
        assertEquals(2, loaded.sceneIndex());
        assertEquals("第二幕", loaded.sceneTitle());
        assertEquals(1000L, loaded.startedAt());
        assertEquals(0L, loaded.endedAt());
        assertEquals("", loaded.endReason());
        assertEquals("", loaded.summary());
        assertEquals(1500L, loaded.lastActivityAt());
        assertFalse("关闭的 AI 状态应原样读回", loaded.aiEnabled());
        assertEquals(3000L, loaded.aiSuspendedUntil());
        assertEquals(2, loaded.aiFailStreak());
        assertTrue(loaded.growthSettled());
        assertEquals(500L, loaded.createTime());
        assertEquals(600L, loaded.updateTime());
    }

    @Test
    public void activeUniqueIndexRejectsSecondActiveSessionInSameGroup() {
        long first = store.insertSession(fullSession(7L, SessionState.RUNNING));
        assertTrue(first > 0L);
        long second = store.insertSession(fullSession(7L, SessionState.RECRUITING));
        assertEquals("同群第二个活跃局必须被部分唯一索引拦下", 0L, second);
        long otherGroup = store.insertSession(fullSession(8L, SessionState.RUNNING));
        assertTrue("不同群不受影响", otherGroup > 0L);
    }

    @Test
    public void findActiveByGroupIgnoresEndedSessions() {
        long id = store.insertSession(fullSession(7L, SessionState.RUNNING));
        SessionRow active = store.findActiveByGroup(7L);
        assertNotNull(active);
        assertEquals(id, active.id());

        store.casTransition(id, SessionState.RUNNING, SessionState.ENDED, 1000L, 2000L, "正常结束",
                true, "落幕", 2000L);
        assertNull("结束后不应再被当作活跃局", store.findActiveByGroup(7L));
    }

    @Test
    public void casTransitionSeparatesMinusOneFromZero() {
        long id = store.insertSession(fullSession(7L, SessionState.RECRUITING));

        assertEquals("状态不匹配应返回 0", 0,
                store.casTransition(id, SessionState.RUNNING, SessionState.ENDED, 0L, 0L, "", false, "",
                        10L));
        storage.failNextUpdate(1);
        assertEquals("语句未执行应返回 -1", -1,
                store.casTransition(id, SessionState.RECRUITING, SessionState.RUNNING, 100L, 0L, "",
                        false, "", 20L));
        assertEquals("注入失败不应改变状态", SessionState.RECRUITING, store.findSession(id).state());

        assertEquals("正常转移应返回 1", 1,
                store.casTransition(id, SessionState.RECRUITING, SessionState.RUNNING, 100L, 0L, "",
                        false, "", 30L));
        SessionRow running = store.findSession(id);
        assertEquals(SessionState.RUNNING, running.state());
        assertEquals(100L, running.startedAt());
        assertEquals(30L, running.updateTime());
    }

    @Test
    public void sceneAndAiStateAndActivityAndSummaryArePersisted() {
        long id = store.insertSession(fullSession(7L, SessionState.RUNNING));

        assertEquals(1, store.casScene(id, SessionState.RUNNING, 5, "终幕", 400L));
        assertEquals(5, store.findSession(id).sceneIndex());
        assertEquals("终幕", store.findSession(id).sceneTitle());

        assertEquals(1, store.casAiState(id, SessionState.RUNNING, true, 0L, 0, 500L));
        SessionRow aiRow = store.findSession(id);
        assertTrue(aiRow.aiEnabled());
        assertEquals(0L, aiRow.aiSuspendedUntil());
        assertEquals(0, aiRow.aiFailStreak());

        assertEquals(1, store.touchActivity(id, 700L, 800L));
        assertEquals(700L, store.findSession(id).lastActivityAt());

        assertEquals(1, store.settleSummary(id, "收束完毕", 900L));
        assertEquals("收束完毕", store.findSession(id).summary());
    }

    @Test
    public void loadActiveAndUnsettledAndExpiredSessions() {
        long recruiting = store.insertSession(fullSession(1L, SessionState.RECRUITING));
        long running = store.insertSession(fullSession(2L, SessionState.RUNNING));
        long endedSettled = store.insertSession(fullSession(3L, SessionState.RUNNING));
        store.casTransition(endedSettled, SessionState.RUNNING, SessionState.ENDED, 0L, 5000L, "结束",
                true, "", 5000L);
        long endedUnsettled = store.insertSession(fullSession(4L, SessionState.RUNNING));
        store.casTransition(endedUnsettled, SessionState.RUNNING, SessionState.ENDED, 0L, 6000L,
                "结束", false, "", 6000L);
        long endedSettledLater = store.insertSession(fullSession(5L, SessionState.RUNNING));
        store.casTransition(endedSettledLater, SessionState.RUNNING, SessionState.ENDED, 0L, 6500L,
                "结束", true, "", 6500L);

        List<SessionRow> active = store.loadActiveSessions();
        assertEquals(2, active.size());
        assertEquals(recruiting, active.get(0).id());
        assertEquals(running, active.get(1).id());

        List<SessionRow> unsettled = store.listUnsettledSessions();
        assertEquals("只有未结算的 ENDED 局需要补结算", 1, unsettled.size());
        assertEquals(endedUnsettled, unsettled.get(0).id());

        List<Long> expired = store.listExpiredSessionIds(7000L);
        assertEquals(2, expired.size());
        assertEquals(endedSettled, expired.get(0).longValue());
        assertEquals("按 id 升序", endedSettledLater, expired.get(1).longValue());
        assertTrue("未结算局永远不进入清理候选",
                store.listExpiredSessionIds(7000L).indexOf(endedUnsettled) < 0);
        assertEquals("冻结 SQL 不带 LIMIT：候选统一返回全部",
                2, store.listExpiredSessionIds(Long.MAX_VALUE).size());
    }

    @Test
    public void insertMemberRespectsUniquePairAndUpdatePersists() {
        long sessionId = store.insertSession(fullSession(7L, SessionState.RUNNING));
        MemberRow member = MemberRow.builder()
                .sessionId(sessionId).userId(42L).cardJson("{\"name\":\"调查员\"}")
                .successSkills(Arrays.asList("侦查")).joinedAt(100L).leftAt(0L)
                .takenOver(false).madnessUntil(0L).createTime(100L).updateTime(100L).build();
        long id = store.insertMember(member);
        assertTrue(id > 0L);
        assertEquals("同一局同一成员只能有一行", 0L, store.insertMember(member));
        assertTrue("不同局同一成员应允许", store.insertMember(MemberRow.builder()
                .sessionId(store.insertSession(fullSession(8L, SessionState.RUNNING))).userId(42L)
                .cardJson("{}").successSkillsJson("[]").joinedAt(100L).leftAt(0L).takenOver(false)
                .madnessUntil(0L).createTime(100L).updateTime(100L).build()) > 0L);

        List<MemberRow> members = store.loadMembers(sessionId);
        assertEquals(1, members.size());
        MemberRow loaded = members.get(0);
        assertEquals(42L, loaded.userId());
        assertEquals("[\"侦查\"]", loaded.successSkillsJson());
        assertEquals("侦查", loaded.successSkills().get(0));

        MemberRow changed = MemberRow.builder()
                .id(loaded.id()).sessionId(sessionId).userId(42L)
                .cardJson("{\"name\":\"调查员\",\"hp\":9}").successSkillsJson("[\"侦查\",\"聆听\"]")
                .joinedAt(100L).leftAt(200L).takenOver(true).madnessUntil(300L)
                .createTime(100L).updateTime(400L).build();
        assertEquals(1, store.updateMember(changed));
        MemberRow after = store.loadMembers(sessionId).get(0);
        assertEquals(200L, after.leftAt());
        assertTrue(after.takenOver());
        assertEquals(300L, after.madnessUntil());
        assertTrue(after.cardJson().contains("hp"));
        assertEquals(2, after.successSkills().size());

        assertEquals(1, store.appendSuccessSkills(sessionId, 42L, Arrays.asList("图书馆"), 500L));
        assertEquals("[\"图书馆\"]", store.loadMembers(sessionId).get(0).successSkillsJson());
        assertEquals("成员不存在时应返回 0", 0,
                store.appendSuccessSkills(sessionId, 999L, new ArrayList<String>(), 500L));
    }

    @Test
    public void logsKeepSeqUniqueAndListNewestFirst() {
        long sessionId = store.insertSession(fullSession(7L, SessionState.RUNNING));
        assertEquals(0L, store.maxLogSeq(sessionId));

        assertEquals(1, store.appendLog(new LogRow(0L, sessionId, 1L, "action", 42L, "第一句", 10L)));
        assertEquals(1, store.appendLog(new LogRow(0L, sessionId, 2L, "speech", 0L, "第二句", 11L)));
        assertEquals(1, store.appendLog(new LogRow(0L, sessionId, 3L, "clue", 0L, "书房密道", 12L)));
        assertEquals("seq 应取最大值", 3L, store.maxLogSeq(sessionId));
        assertEquals("seq 重复应插入失败返回 0", 0,
                store.appendLog(new LogRow(0L, sessionId, 2L, "action", 0L, "重复", 13L)));

        List<LogRow> page = store.listLogs(sessionId, 2, 0);
        assertEquals(2, page.size());
        assertEquals("按 seq 倒序", 3L, page.get(0).seq());
        assertEquals(2L, page.get(1).seq());
        List<LogRow> secondPage = store.listLogs(sessionId, 2, 2);
        assertEquals(1, secondPage.size());
        assertEquals(1L, secondPage.get(0).seq());
        assertEquals("第一句", secondPage.get(0).content());
    }

    @Test
    public void logContentIsTruncatedToTwoThousandChars() {
        long sessionId = store.insertSession(fullSession(7L, SessionState.RUNNING));
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 2100; i++) {
            builder.append('中');
        }
        store.appendLog(new LogRow(0L, sessionId, 1L, "narration", 0L, builder.toString(), 10L));
        String stored = store.listLogs(sessionId, 1, 0).get(0).content();
        assertEquals("超长日志必须截到 2000 字符", 2000, stored.length());
        assertEquals("截断不能切坏代理对", 2000, stored.codePointCount(0, stored.length()));
    }

    @Test
    public void clueKeysAreDistinctAndSorted() {
        long sessionId = store.insertSession(fullSession(7L, SessionState.RUNNING));
        store.appendLog(new LogRow(0L, sessionId, 1L, "clue", 0L, "书房密道", 10L));
        store.appendLog(new LogRow(0L, sessionId, 2L, "clue", 0L, "书房密道", 11L));
        store.appendLog(new LogRow(0L, sessionId, 3L, "clue", 0L, "地窖钥匙", 12L));
        store.appendLog(new LogRow(0L, sessionId, 4L, "action", 0L, "不是线索", 13L));
        List<String> keys = store.listClueKeys(sessionId);
        assertEquals(2, keys.size());
        assertEquals("按内容升序", "书房密道", keys.get(0));
        assertEquals("地窖钥匙", keys.get(1));
    }

    @Test
    public void blacklistRowsAreUniquePerGroupAndUser() {
        assertFalse(store.isBlacklisted(7L, 42L));
        assertEquals(1, store.insertBlacklist(new BlacklistRow(0L, 7L, 42L, 1L, "刷屏", 10L)));
        assertEquals("重复拉黑不应产生第二行", 0,
                store.insertBlacklist(new BlacklistRow(0L, 7L, 42L, 2L, "刷屏", 20L)));
        assertFalse("同一用户在其他群不生效", store.isBlacklisted(8L, 42L));
        assertEquals(1, store.insertBlacklist(new BlacklistRow(0L, 8L, 42L, 1L, "刷屏", 30L)));

        List<BlacklistRow> rows = store.listBlacklist(7L);
        assertEquals(1, rows.size());
        assertEquals("刷屏", rows.get(0).reason());
        assertEquals(1L, rows.get(0).operatorId());

        assertEquals(1, store.deleteBlacklist(7L, 42L));
        assertFalse(store.isBlacklisted(7L, 42L));
        assertEquals("重复解除应返回 0", 0, store.deleteBlacklist(7L, 42L));
    }

    @Test
    public void traceRowsAreWrittenListedTrimmedAndExpired() {
        long sessionId = store.insertSession(fullSession(7L, SessionState.RUNNING));
        StringBuilder preview = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            preview.append('述');
        }
        long id = store.appendAiTrace(trace(sessionId, 1L, preview.toString(), 1000L));
        assertTrue(id > 0L);
        store.appendAiTrace(trace(sessionId, 2L, "第二行", 2000L));
        store.appendAiTrace(trace(sessionId, 3L, "第三行", 3000L));

        List<AiTraceRow> recent = store.listAiTrace(7L, sessionId, 10);
        assertEquals(3, recent.size());
        assertEquals("按 id 倒序", 3L, recent.get(0).eventSeq());
        assertEquals(240, recent.get(2).preview().length());

        assertEquals(2, store.listAiTrace(7L, sessionId, 2).size());
        assertEquals(3, store.listAiTrace(7L, 0L, 10).size());
        assertEquals(0, store.listAiTrace(7L, sessionId, 0).size());

        assertEquals("早于 2000 的审计应删除两行", 2, store.deleteAiTraceBefore(3000L));
        assertEquals(1, store.listAiTrace(7L, 0L, 10).size());
    }

    @Test
    public void traceOverflowKeepsNewestRowsAndNeverDeletesAll() {
        long sessionId = store.insertSession(fullSession(7L, SessionState.RUNNING));
        for (int i = 1; i <= 8; i++) {
            store.appendAiTrace(trace(sessionId, i, "行" + i, 1000L + i));
        }
        assertEquals("保留 5 行应删掉 3 行", 3, store.deleteAiTraceOverflow(5));
        List<AiTraceRow> rest = store.listAiTrace(7L, sessionId, 10);
        assertEquals(5, rest.size());
        assertEquals("留下的是最新 5 行", 8L, rest.get(0).eventSeq());
        assertEquals(4L, rest.get(4).eventSeq());

        assertEquals("行数不足时不误删", 0, store.deleteAiTraceOverflow(5000));
        assertEquals("非法保留行数不得清空审计", 0, store.deleteAiTraceOverflow(0));
        assertEquals(5, store.listAiTrace(7L, sessionId, 10).size());
    }

    @Test
    public void partialQueryReturnsWhatItReadWithoutDeleting() {
        long sessionId = store.insertSession(fullSession(7L, SessionState.RUNNING));
        store.insertMember(member(sessionId, 1L));
        store.insertMember(member(sessionId, 2L));

        storage.partialNextQuery(1);
        List<MemberRow> partial = store.loadMembers(sessionId);
        assertEquals("只读到一行时不应崩", 1, partial.size());
        assertEquals("读失败不能删数据", 2L,
                storage.count("SELECT COUNT(*) AS c FROM " + TrpgSchemaService.TABLE_MEMBER
                        + " WHERE sessionID = ?", sessionId));
    }

    @Test
    public void deleteSubTablesAndSessionOrderIsIndependent() {
        long sessionId = store.insertSession(fullSession(7L, SessionState.RUNNING));
        store.appendLog(new LogRow(0L, sessionId, 1L, "action", 0L, "一句", 10L));
        store.insertMember(member(sessionId, 42L));
        store.casTransition(sessionId, SessionState.RUNNING, SessionState.ENDED, 0L, 2000L, "结束",
                true, "", 2000L);

        assertEquals(1, store.deleteLogs(sessionId));
        assertEquals("重复删除返回 0", 0, store.deleteLogs(sessionId));
        assertEquals(1, store.deleteMembers(sessionId));
        assertEquals("状态已不是 ENDED 前先删本行", 1, store.deleteSession(sessionId));
        assertNull(store.findSession(sessionId));
        assertEquals("已被删除的局再删返回 0", 0, store.deleteSession(sessionId));
    }

    @Test
    public void deleteSessionOnlyWorksForEndedRows() {
        long sessionId = store.insertSession(fullSession(7L, SessionState.RUNNING));
        assertEquals("活跃局不能被清理语句删掉", 0, store.deleteSession(sessionId));
        assertNotNull(store.findSession(sessionId));
    }

    private SessionRow fullSession(long groupId, SessionState state) {
        return SessionRow.builder()
                .groupId(groupId).state(state).kpUserId(9001L).scriptId("script-a")
                .ruleSetId("coc7").sceneIndex(2).sceneTitle("第二幕").startedAt(1000L).endedAt(0L)
                .endReason("").summary("").lastActivityAt(1500L).aiEnabled(false)
                .aiSuspendedUntil(3000L).aiFailStreak(2).growthSettled(true)
                .createTime(500L).updateTime(600L).build();
    }

    private MemberRow member(long sessionId, long userId) {
        return MemberRow.builder().sessionId(sessionId).userId(userId).cardJson("{}")
                .successSkillsJson("[]").joinedAt(10L).leftAt(0L).takenOver(false).madnessUntil(0L)
                .createTime(10L).updateTime(10L).build();
    }

    private AiTraceRow trace(long sessionId, long eventSeq, String preview, long createTime) {
        return new AiTraceRow(0L, 7L, sessionId, eventSeq, "action", "ok", "", "", 1, 120L,
                "stop", "{}", false, preview, createTime);
    }

}
