package org.moboxlab.mbb.trpg.present;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.moboxlab.mbb.trpg.script.Clue;
import org.moboxlab.mbb.trpg.script.Scene;
import org.moboxlab.mbb.trpg.script.Script;
import org.moboxlab.mbb.trpg.script.ScriptCatalog;
import org.moboxlab.mbb.trpg.script.ScriptInfo;
import org.moboxlab.mbb.trpg.session.GrowthOutcome;
import org.moboxlab.mbb.trpg.session.MemberView;
import org.moboxlab.mbb.trpg.session.SessionEvent;
import org.moboxlab.mbb.trpg.session.SessionSnapshot;
import org.moboxlab.mbb.trpg.session.SessionState;
import org.moboxlab.mbb.trpg.store.CardData;
import org.moboxlab.mbb.trpg.store.JdbcStorageFake;
import org.moboxlab.mbb.trpg.store.LogRow;
import org.moboxlab.mbb.trpg.store.SqlSessionStore;
import org.moboxlab.mbb.trpg.store.TrpgSchemaService;
import org.moboxlab.mbb.trpg.testutil.FakeNameResolver;
import org.moboxlab.mbb.trpg.testutil.TempDirs;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** 战报数据来源测试：快照、摘要、剧本与日志如何合成战报字段。 */
public class TrpgLogReportSourceTest {

    private File dir;
    private JdbcStorageFake storage;
    private SqlSessionStore store;
    private FakeNameResolver names;
    private StubCatalog catalog;

    @Before
    public void setUp() {
        dir = TempDirs.create("trpg-report-source");
        storage = new JdbcStorageFake(new File(dir, "trpg.db"));
        new TrpgSchemaService(storage, null).ensureSchema();
        store = new SqlSessionStore(storage, null);
        names = new FakeNameResolver();
        names.put(201L, "阿岚");
        names.put(202L, "小满");
        catalog = new StubCatalog(script());
    }

    @After
    public void tearDown() {
        storage.close();
        TempDirs.deleteRecursively(dir);
    }

    @Test
    public void reportFillsFieldsFromSnapshotScriptAndLogs() {
        long sessionId = 7L;
        appendLog(sessionId, 1L, "create");
        appendLog(sessionId, 2L, "check");
        appendLog(sessionId, 3L, "san");
        appendLog(sessionId, 4L, "opposed");
        appendLog(sessionId, 5L, "scene");
        SessionSnapshot snapshot = snapshot(sessionId,
                "雨夜脱身\n你们带着那封没寄出的信走进雨里。",
                Arrays.asList("clue-print", "clue-unknown"));
        List<GrowthOutcome> growth = Collections.singletonList(
                new GrowthOutcome(202L, "聆听", 50, 52, 3));

        TrpgReport report = source().report(
                SessionEvent.ended(snapshot, "finished", growth), null);

        assertEquals("雨夜古宅", report.scriptName());
        assertEquals("雨夜脱身", report.endingTitle());
        assertEquals("你们带着那封没寄出的信走进雨里。", report.endingNarration());
        assertEquals("阿岚", report.kpName());
        assertEquals(Collections.singletonList("小满"), report.playerNames());
        assertEquals(Arrays.asList("泥脚印", "clue-unknown"), report.clueNames());
        assertEquals(1, report.clueTotal());
        assertEquals(3, report.checkTotal());
        assertEquals(120, report.durationMinutes());
        assertEquals(Collections.singletonList("聆听 50 → 52"), report.growthLines());
        assertEquals(30, report.retentionDays());
    }

    @Test
    public void reportFallsBackWhenScriptAndSummaryMissing() {
        catalog.script = null;
        SessionSnapshot snapshot = snapshot(9L, "", Arrays.asList("clue-print"));

        TrpgReport report = source().report(SessionEvent.ended(snapshot, "cancelled",
                Collections.<GrowthOutcome>emptyList()), null);

        assertEquals("coc-example", report.scriptName());
        assertEquals("", report.endingTitle());
        assertEquals("", report.endingNarration());
        assertEquals(Collections.singletonList("clue-print"), report.clueNames());
        assertEquals(0, report.clueTotal());
        assertEquals(0, report.checkTotal());
        assertTrue(report.growthLines().isEmpty());
    }

    @Test
    public void reportAcceptsPreloadedScript() {
        TrpgReport report = source().report(SessionEvent.ended(snapshot(11L, "结局\n叙述",
                Collections.<String>emptyList()), "finished",
                Collections.<GrowthOutcome>emptyList()), script());

        assertEquals("雨夜古宅", report.scriptName());
        assertEquals("结局", report.endingTitle());
    }

    @Test
    public void reportWithoutSnapshotUsesDefaults() {
        TrpgReport report = source().report(SessionEvent.ended(null, "finished",
                Collections.<GrowthOutcome>emptyList()), null);

        assertEquals("", report.scriptName());
        assertEquals(30, report.retentionDays());
    }

    @Test
    public void constructorRejectsMissingCollaborators() {
        assertRejected(null, catalog, names);
        assertRejected(store, null, names);
        assertRejected(store, catalog, null);
    }

    private void assertRejected(SqlSessionStore storePort, ScriptCatalog catalogPort,
                                FakeNameResolver namePort) {
        try {
            new TrpgLogReportSource(storePort, catalogPort, namePort, 30);
            fail("应当拒绝空依赖");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().endsWith("不能为空"));
        }
    }

    @Test
    public void reportRejectsNullEvent() {
        try {
            source().report(null, null);
            fail("应当拒绝空事件");
        } catch (IllegalArgumentException expected) {
            assertEquals("事件不能为空", expected.getMessage());
        }
    }

    private TrpgLogReportSource source() {
        return new TrpgLogReportSource(store, catalog, names, 30);
    }

    private void appendLog(long sessionId, long seq, String eventType) {
        assertTrue(store.appendLog(new LogRow(0L, sessionId, seq, eventType, 202L, "内容",
                1_700_000_000_000L)) >= 0);
    }

    private static SessionSnapshot snapshot(long sessionId, String summary, List<String> clues) {
        return SessionSnapshot.builder()
                .groupId(1001L)
                .sessionId(sessionId)
                .state(SessionState.ENDED)
                .kpUserId(201L)
                .scriptId("coc-example")
                .ruleSetId("d100")
                .startedAt(1_700_000_000_000L)
                .endedAt(1_700_007_200_000L)
                .summary(summary)
                .members(Arrays.asList(member(201L, 0L), member(202L, 0L), member(203L, 5L)))
                .unlockedClues(clues)
                .build();
    }

    private static MemberView member(long userId, long leftAt) {
        return new MemberView(userId, 1_700_000_000_000L, leftAt, false, 0L, CardData.empty(),
                Collections.<String>emptyList());
    }

    private static Script script() {
        List<Scene> scenes = new ArrayList<Scene>();
        scenes.add(new Scene("scene-study", "书房", "昏暗的书房", "灯芯爆了一下。",
                "房门在你们敲到第三下时才开。", Collections.<String>emptyList(),
                Collections.<String>emptyList(), Collections.<String>emptyList()));
        List<Clue> clues = new ArrayList<Clue>();
        clues.add(new Clue("clue-print", "泥脚印", "地上有泥", "泥脚印通向书架。"));
        return new Script(1, "coc-example", "雨夜古宅", "MBB-Trpg", "一座被暴雨困住的旧宅。",
                "d100", 2, 4, 60, Arrays.asList("1920 年代", "调查"),
                Collections.<String>emptyList(), scenes, clues,
                Collections.<org.moboxlab.mbb.trpg.script.Npc>emptyList(),
                Collections.<org.moboxlab.mbb.trpg.script.ScriptCheck>emptyList(),
                Collections.<org.moboxlab.mbb.trpg.script.ScriptTrigger>emptyList(),
                Collections.<org.moboxlab.mbb.trpg.script.ScriptEnding>emptyList());
    }

    /** 剧本库假件：只支持 get，其余方法在测试中不会走到。 */
    private static final class StubCatalog implements ScriptCatalog {
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        private Script script;

        StubCatalog(Script script) {
            this.script = script;
        }

        @Override
        public Script get(String id) {
            return "coc-example".equals(id) ? script : null;
        }

        @Override
        public ScriptInfo info(String id) {
            return null;
        }

        @Override
        public List<ScriptInfo> list() {
            return Collections.emptyList();
        }

        @Override
        public void delete(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void replace(String id, byte[] content) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Lock acquireReadLock() {
            return lock.readLock();
        }

        @Override
        public Lock acquireWriteLock() {
            return lock.writeLock();
        }
    }
}
