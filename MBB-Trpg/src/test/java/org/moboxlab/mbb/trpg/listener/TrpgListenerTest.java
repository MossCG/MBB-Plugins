package org.moboxlab.mbb.trpg.listener;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.LinkedHashMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.moboxlab.moboxbot.API.Event.GroupMessageEvent;
import org.moboxlab.mbb.trpg.config.TrpgConfig;
import org.moboxlab.mbb.trpg.script.ScriptException;
import org.moboxlab.mbb.trpg.session.SessionResult;
import org.moboxlab.mbb.trpg.session.SessionService;
import org.moboxlab.mbb.trpg.session.SessionState;
import org.moboxlab.mbb.trpg.store.BlacklistService;
import org.moboxlab.mbb.trpg.store.JdbcStorageFake;
import org.moboxlab.mbb.trpg.store.SessionRow;
import org.moboxlab.mbb.trpg.store.SqlSessionStore;
import org.moboxlab.mbb.trpg.store.TrpgSchemaService;
import org.moboxlab.mbb.trpg.testutil.FakeClock;
import org.moboxlab.mbb.trpg.testutil.FakeRandom;
import org.moboxlab.mbb.trpg.testutil.FakeScriptImportService;
import org.moboxlab.mbb.trpg.testutil.RecordingLogger;
import org.moboxlab.mbb.trpg.testutil.TempDirs;
import org.moboxlab.mbb.trpg.script.BuiltinScriptCatalog;
import org.moboxlab.mbb.trpg.script.ScriptValidator;

/** 群消息入口：黑名单静默拦截、导入暂存吞消息与局内活动刷新。 */
public class TrpgListenerTest {

    private static final long GROUP = 9201L;
    private static final long KP = 3001L;
    private static final long PLAYER = 3002L;
    private static final long NOW = 1700000000000L;
    private static final long THROTTLE_MILLIS = 61000L;

    private File tempDir;
    private JdbcStorageFake storage;
    private SqlSessionStore store;
    private FakeClock clock;
    private TrpgConfig config;
    private SessionService sessions;
    private BlacklistService blacklist;
    private FakeScriptImportService imports;
    private RecordingLogger logger;
    private TrpgListener listener;

    @Before
    public void setUp() {
        tempDir = TempDirs.create("trpg-listener");
        storage = new JdbcStorageFake(new File(tempDir, "trpg.db"));
        new TrpgSchemaService(storage, null).ensureSchema();
        store = new SqlSessionStore(storage, null);
        clock = new FakeClock(NOW);
        config = TrpgConfig.fromValues(new LinkedHashMap<String, String>());
        BuiltinScriptCatalog catalog = new BuiltinScriptCatalog(
                TrpgListenerTest.class.getClassLoader(), new ScriptValidator());
        int[] faces = new int[200];
        for (int index = 0; index < faces.length; index++) {
            faces[index] = 3;
        }
        sessions = new SessionService(store, catalog, clock, new FakeRandom(faces), config, null, null);
        blacklist = new BlacklistService(store);
        imports = new FakeScriptImportService();
        logger = new RecordingLogger();
        listener = new TrpgListener(sessions, blacklist, imports, logger);
    }

    @After
    public void tearDown() {
        if (storage != null) {
            storage.close();
        }
        TempDirs.deleteRecursively(tempDir);
    }

    @Test
    public void blacklistedMessageIsCancelledSilently() {
        blacklist.add(GROUP, PLAYER, KP, "刷屏", NOW);

        GroupMessageEvent event = message(PLAYER, "/trpg status");
        listener.onGroupMessage(event);

        assertTrue("黑名单消息应被取消", event.isCancelled());
        assertTrue("黑名单不应产生日志：" + logger.warns(), logger.warns().isEmpty());
    }

    @Test
    public void pendingImportSwallowsMessage() throws ScriptException {
        imports.begin(GROUP, PLAYER, org.moboxlab.mbb.trpg.script.ScriptImportMode.CREATE);

        GroupMessageEvent event = message(PLAYER, "{\"id\":\"demo\"}");
        listener.onGroupMessage(event);

        assertTrue("导入暂存期间消息应被吞掉", event.isCancelled());
        assertEquals(1, imports.fragments(GROUP, PLAYER).size());
        assertEquals("{\"id\":\"demo\"}", imports.fragments(GROUP, PLAYER).get(0));
    }

    @Test
    public void importFailureSwallowsMessageAndLogs() throws ScriptException {
        imports.begin(GROUP, PLAYER, org.moboxlab.mbb.trpg.script.ScriptImportMode.CREATE);
        imports.failNextAppend(new ScriptException("E_IMPORT_STATE", "$", "暂存已超限"));

        GroupMessageEvent event = message(PLAYER, "{}");
        listener.onGroupMessage(event);

        assertTrue("暂存失败时消息应被吞掉，避免误触指令", event.isCancelled());
        assertTrue("暂存失败不应留下片段", imports.fragments(GROUP, PLAYER).isEmpty());
    }

    @Test
    public void endControlFinishesImport() throws ScriptException {
        imports.begin(GROUP, PLAYER, org.moboxlab.mbb.trpg.script.ScriptImportMode.CREATE);

        GroupMessageEvent event = message(PLAYER, " /trpg script import END ");
        listener.onGroupMessage(event);

        assertTrue("控制字应被消费", event.isCancelled());
        assertFalse("结束后不应再有暂存", imports.hasPending(GROUP, PLAYER));
        assertTrue("控制字不应作为片段追加", imports.fragments(GROUP, PLAYER).isEmpty());
    }

    @Test
    public void cancelControlCancelsImport() throws ScriptException {
        imports.begin(GROUP, PLAYER, org.moboxlab.mbb.trpg.script.ScriptImportMode.CREATE);

        GroupMessageEvent event = message(PLAYER, "/trpg script import cancel");
        listener.onGroupMessage(event);

        assertTrue(event.isCancelled());
        assertFalse(imports.hasPending(GROUP, PLAYER));
    }

    @Test
    public void controlWordWithTrailingTextIsTreatedAsFragment() throws ScriptException {
        imports.begin(GROUP, PLAYER, org.moboxlab.mbb.trpg.script.ScriptImportMode.CREATE);

        GroupMessageEvent event = message(PLAYER, "/trpg script import end 现在");
        listener.onGroupMessage(event);

        assertTrue(event.isCancelled());
        assertTrue("不是纯控制字，应按片段追加", imports.hasPending(GROUP, PLAYER));
        assertEquals(1, imports.fragments(GROUP, PLAYER).size());
    }

    @Test
    public void privateMessageUsesPrivatePending() throws ScriptException {
        long privateGroup = org.moboxlab.mbb.trpg.session.SessionActor.PRIVATE_GROUP_ID;
        imports.begin(privateGroup, PLAYER, org.moboxlab.mbb.trpg.script.ScriptImportMode.CREATE);
        org.moboxlab.moboxbot.API.Event.PrivateMessageEvent event =
                new org.moboxlab.moboxbot.API.Event.PrivateMessageEvent();
        event.setUserID(PLAYER);
        event.setRawMessage("{\"id\":\"demo\"}");

        listener.onPrivateMessage(event);

        assertTrue(event.isCancelled());
        assertEquals(1, imports.fragments(privateGroup, PLAYER).size());
    }

    @Test
    public void messageRefreshesActivityAfterThrottle() {
        SessionResult started = sessions.start(new org.moboxlab.mbb.trpg.session.SessionActor(KP, GROUP,
                false), "coc-example");
        assertTrue(started.ok());
        SessionResult joined = sessions.join(new org.moboxlab.mbb.trpg.session.SessionActor(PLAYER,
                GROUP, false), org.moboxlab.mbb.trpg.store.CardData.empty());
        assertTrue("玩家应在局内：" + joined.detail(), joined.ok());
        long sessionId = started.snapshot().sessionId();
        long before = store.findSession(sessionId).lastActivityAt();

        clock.advance(THROTTLE_MILLIS);
        GroupMessageEvent event = message(PLAYER, "随便聊聊");
        listener.onGroupMessage(event);

        assertFalse(event.isCancelled());
        assertTrue("局内活动应刷新", store.findSession(sessionId).lastActivityAt() > before);
    }

    @Test
    public void zeroIdsAreIgnored() {
        GroupMessageEvent event = new GroupMessageEvent();
        event.setGroupID(0L);
        event.setUserID(PLAYER);
        listener.onGroupMessage(event);
        assertFalse(event.isCancelled());

        GroupMessageEvent noUser = new GroupMessageEvent();
        noUser.setGroupID(GROUP);
        noUser.setUserID(0L);
        listener.onGroupMessage(noUser);
        assertFalse(noUser.isCancelled());
    }

    @Test
    public void nullEventIsIgnored() {
        listener.onGroupMessage(null);
        assertTrue(logger.warns().isEmpty());
    }

    @Test
    public void listenerWithoutImportsDoesNotSwallow() {
        TrpgListener bare = new TrpgListener(sessions, null, null, null);
        GroupMessageEvent event = message(PLAYER, "{}");
        bare.onGroupMessage(event);
        assertFalse(event.isCancelled());
    }

    @Test
    public void constructorRejectsNullSessionService() {
        try {
            new TrpgListener(null, blacklist, imports, logger);
            org.junit.Assert.fail("应当拒绝空局面服务");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("局面服务不能为空"));
        }
    }

    @Test
    public void activityTouchOnlyAffectsActiveSession() {
        GroupMessageEvent event = message(PLAYER, "群里随便聊");
        listener.onGroupMessage(event);
        assertFalse(event.isCancelled());
        assertEquals(0, store.loadActiveSessions().size());
    }

    @Test
    public void recruitingSessionStillCountsAsActive() {
        SessionResult started = sessions.start(new org.moboxlab.mbb.trpg.session.SessionActor(KP, GROUP,
                false), "coc-example");
        assertTrue(started.ok());
        SessionRow row = store.findSession(started.snapshot().sessionId());
        assertEquals(SessionState.RECRUITING, row.state());
    }

    private GroupMessageEvent message(long userId, String raw) {
        GroupMessageEvent event = new GroupMessageEvent();
        event.setGroupID(GROUP);
        event.setUserID(userId);
        event.setRawMessage(raw);
        return event;
    }
}
