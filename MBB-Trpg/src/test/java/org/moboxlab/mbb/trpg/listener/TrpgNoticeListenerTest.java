package org.moboxlab.mbb.trpg.listener;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.LinkedHashMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.moboxlab.moboxbot.API.Event.NoticeEvent;
import org.moboxlab.mbb.trpg.config.TrpgConfig;
import org.moboxlab.mbb.trpg.script.BuiltinScriptCatalog;
import org.moboxlab.mbb.trpg.script.ScriptValidator;
import org.moboxlab.mbb.trpg.session.SessionResult;
import org.moboxlab.mbb.trpg.session.SessionService;
import org.moboxlab.mbb.trpg.session.SessionState;
import org.moboxlab.mbb.trpg.store.JdbcStorageFake;
import org.moboxlab.mbb.trpg.store.SqlSessionStore;
import org.moboxlab.mbb.trpg.store.TrpgSchemaService;
import org.moboxlab.mbb.trpg.testutil.FakeClock;
import org.moboxlab.mbb.trpg.testutil.FakeRandom;
import org.moboxlab.mbb.trpg.testutil.RecordingLogger;
import org.moboxlab.mbb.trpg.testutil.TempDirs;

/** 离群通知：KP 离群触发 T7（kp_left），其他人离群不动本局。 */
public class TrpgNoticeListenerTest {

    private static final long GROUP = 9301L;
    private static final long KP = 3101L;
    private static final long PLAYER = 3102L;
    private static final long NOW = 1700000000000L;

    private File tempDir;
    private JdbcStorageFake storage;
    private SqlSessionStore store;
    private FakeClock clock;
    private SessionService sessions;
    private RecordingLogger logger;
    private TrpgNoticeListener listener;

    @Before
    public void setUp() {
        tempDir = TempDirs.create("trpg-notice");
        storage = new JdbcStorageFake(new File(tempDir, "trpg.db"));
        new TrpgSchemaService(storage, null).ensureSchema();
        store = new SqlSessionStore(storage, null);
        clock = new FakeClock(NOW);
        TrpgConfig config = TrpgConfig.fromValues(new LinkedHashMap<String, String>());
        BuiltinScriptCatalog catalog = new BuiltinScriptCatalog(
                TrpgNoticeListenerTest.class.getClassLoader(), new ScriptValidator());
        int[] faces = new int[200];
        for (int index = 0; index < faces.length; index++) {
            faces[index] = 3;
        }
        sessions = new SessionService(store, catalog, clock, new FakeRandom(faces), config, null, null);
        logger = new RecordingLogger();
        listener = new TrpgNoticeListener(sessions, logger);
    }

    @After
    public void tearDown() {
        if (storage != null) {
            storage.close();
        }
        TempDirs.deleteRecursively(tempDir);
    }

    @Test
    public void kpLeavingArchivesSession() {
        long sessionId = startRunning();

        listener.onNotice(notice("group_decrease", KP));

        assertEquals(SessionState.ENDED, store.findSession(sessionId).state());
        assertEquals("kp_left", store.findSession(sessionId).endReason());
        assertTrue(logger.infos().get(0), logger.infos().get(0).contains("kp_left"));
    }

    @Test
    public void otherMemberLeavingKeepsSessionRunning() {
        long sessionId = startRunning();

        listener.onNotice(notice("group_decrease", PLAYER));

        assertEquals(SessionState.RUNNING, store.findSession(sessionId).state());
        assertTrue("非 KP 离群不应打归档日志：" + logger.infos(), logger.infos().isEmpty());
    }

    @Test
    public void otherNoticeTypesAreIgnored() {
        long sessionId = startRunning();

        listener.onNotice(notice("group_increase", KP));

        assertEquals(SessionState.RUNNING, store.findSession(sessionId).state());
        assertTrue(logger.infos().isEmpty());
    }

    @Test
    public void zeroIdsAreIgnored() {
        long sessionId = startRunning();

        NoticeEvent event = notice("group_decrease", KP);
        event.setGroupID(0L);
        listener.onNotice(event);
        assertEquals(SessionState.RUNNING, store.findSession(sessionId).state());

        NoticeEvent noUser = notice("group_decrease", KP);
        noUser.setUserID(0L);
        listener.onNotice(noUser);
        assertEquals(SessionState.RUNNING, store.findSession(sessionId).state());
    }

    @Test
    public void nullEventIsIgnored() {
        listener.onNotice(null);
        assertTrue(logger.warns().isEmpty());
    }

    @Test
    public void constructorRejectsNullSessionService() {
        try {
            new TrpgNoticeListener(null, logger);
            org.junit.Assert.fail("应当拒绝空局面服务");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("局面服务不能为空"));
        }
    }

    @Test
    public void kpLeavingRecruitingSessionAlsoArchives() {
        SessionResult started = sessions.start(actor(KP), "coc-example");
        assertTrue(started.ok());
        long sessionId = started.snapshot().sessionId();
        assertNotNull(store.findSession(sessionId));

        listener.onNotice(notice("group_decrease", KP));

        assertEquals(SessionState.ENDED, store.findSession(sessionId).state());
        // 招募期 KP 离群按 T7 记 cancelled（§5.3），进行中才是 kp_left。
        assertEquals("cancelled", store.findSession(sessionId).endReason());
    }

    private long startRunning() {
        SessionResult started = sessions.start(actor(KP), "coc-example");
        assertTrue("建局应成功：" + started.detail(), started.ok());
        SessionResult joined = sessions.join(actor(PLAYER), null);
        assertTrue("加入应成功：" + joined.detail(), joined.ok());
        clock.advance(2000L);
        SessionResult begun = sessions.begin(actor(KP));
        assertTrue("开局应成功：" + begun.detail(), begun.ok());
        clock.advance(2000L);
        return started.snapshot().sessionId();
    }

    private org.moboxlab.mbb.trpg.session.SessionActor actor(long userId) {
        return new org.moboxlab.mbb.trpg.session.SessionActor(userId, GROUP, false);
    }

    private NoticeEvent notice(String type, long userId) {
        NoticeEvent event = new NoticeEvent();
        event.setNoticeType(type);
        event.setGroupID(GROUP);
        event.setUserID(userId);
        return event;
    }
}
