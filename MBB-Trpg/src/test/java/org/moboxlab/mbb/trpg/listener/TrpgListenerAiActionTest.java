package org.moboxlab.mbb.trpg.listener;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.moboxlab.moboxbot.API.Event.GroupMessageEvent;
import org.moboxlab.mbb.trpg.ai.AiTrigger;
import org.moboxlab.mbb.trpg.ai.AiTriggerSink;
import org.moboxlab.mbb.trpg.config.TrpgConfig;
import org.moboxlab.mbb.trpg.script.BuiltinScriptCatalog;
import org.moboxlab.mbb.trpg.script.ScriptValidator;
import org.moboxlab.mbb.trpg.session.SessionActor;
import org.moboxlab.mbb.trpg.session.SessionService;
import org.moboxlab.mbb.trpg.store.BlacklistService;
import org.moboxlab.mbb.trpg.store.CardData;
import org.moboxlab.mbb.trpg.store.JdbcStorageFake;
import org.moboxlab.mbb.trpg.store.SqlSessionStore;
import org.moboxlab.mbb.trpg.store.TrpgSchemaService;
import org.moboxlab.mbb.trpg.testutil.FakeClock;
import org.moboxlab.mbb.trpg.testutil.FakeRandom;
import org.moboxlab.mbb.trpg.testutil.FakeScriptImportService;
import org.moboxlab.mbb.trpg.testutil.RecordingLogger;
import org.moboxlab.mbb.trpg.testutil.TempDirs;

/** §9.4：局内玩家自由文本行动进合并窗口，KP 自由文本不识别。 */
public class TrpgListenerAiActionTest {

    private static final long GROUP = 9301L;
    private static final long KP = 3101L;
    private static final long PLAYER = 3102L;
    private static final long OUTSIDER = 3103L;
    private static final long NOW = 1700000000000L;

    private File tempDir;
    private JdbcStorageFake storage;
    private SqlSessionStore store;
    private FakeClock clock;
    private SessionService sessions;
    private RecordingLogger logger;
    private FakeTriggerSink triggers;
    private TrpgListener listener;

    @Before
    public void setUp() {
        tempDir = TempDirs.create("trpg-listener-ai");
        storage = new JdbcStorageFake(new File(tempDir, "trpg.db"));
        new TrpgSchemaService(storage, null).ensureSchema();
        store = new SqlSessionStore(storage, null);
        clock = new FakeClock(NOW);
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("aiHost.enabled", "true");
        TrpgConfig config = TrpgConfig.fromValues(values);
        int[] faces = new int[200];
        for (int index = 0; index < faces.length; index++) {
            faces[index] = 3;
        }
        BuiltinScriptCatalog catalog = new BuiltinScriptCatalog(
                TrpgListenerAiActionTest.class.getClassLoader(), new ScriptValidator());
        sessions = new SessionService(store, catalog, clock, new FakeRandom(faces), config, null, null);
        logger = new RecordingLogger();
        triggers = new FakeTriggerSink();
        listener = new TrpgListener(sessions, new BlacklistService(store),
                new FakeScriptImportService(), null, logger, config, triggers);

        assertTrue(sessions.start(actor(KP), "coc-example").ok());
        assertTrue(sessions.join(actor(PLAYER), CardData.empty()).ok());
        assertTrue("开局应成功", sessions.begin(actor(KP)).ok());
        assertTrue("本局 AI 主持应开启", sessions.aiState(actor(KP), "on").ok());
    }

    @After
    public void tearDown() {
        if (storage != null) {
            storage.close();
        }
        TempDirs.deleteRecursively(tempDir);
    }

    @Test
    public void playerFreeTextIsConsumedAndFiresWindow() {
        GroupMessageEvent event = message(PLAYER, "我推开门，观察屋内");

        listener.onGroupMessage(event);

        assertTrue("玩家自由文本应被消费，避免进入指令分发", event.isCancelled());
        assertEquals(1, triggers.fired.size());
        assertEquals(AiTrigger.PLAYER_ACTION, triggers.fired.get(0));
    }

    @Test
    public void kpFreeTextIsNotConsumed() {
        GroupMessageEvent event = message(KP, "KP 的叙述不该触发 AI");

        listener.onGroupMessage(event);

        assertFalse("KP 自由文本不应被识别为玩家行动", event.isCancelled());
        assertTrue("KP 自由文本不应触发 AI", triggers.fired.isEmpty());
    }

    @Test
    public void outsiderFreeTextIsNotConsumed() {
        GroupMessageEvent event = message(OUTSIDER, "路人围观");

        listener.onGroupMessage(event);

        assertFalse("非局内成员不触发", event.isCancelled());
        assertTrue(triggers.fired.isEmpty());
    }

    @Test
    public void commandAndEmptyTextAreNotConsumed() {
        GroupMessageEvent command = message(PLAYER, "/trpg status");
        listener.onGroupMessage(command);
        assertFalse("指令不归监听器消费", command.isCancelled());

        GroupMessageEvent blank = message(PLAYER, "   ");
        listener.onGroupMessage(blank);
        assertFalse(blank.isCancelled());

        assertTrue(triggers.fired.isEmpty());
    }

    @Test
    public void otherPluginPrefixesAreNotConsumed() {
        String[] commands = {"#帮助", "!roll 1d100", ".trpg status", "！签到"};
        for (String command : commands) {
            GroupMessageEvent event = message(PLAYER, command);
            listener.onGroupMessage(event);
            assertFalse("其它插件前缀不应被吞：" + command, event.isCancelled());
        }
        assertTrue(triggers.fired.isEmpty());
    }

    @Test
    public void trpgPrefixIsCommandButTrpgWordInsideTextIsAction() {
        GroupMessageEvent command = message(PLAYER, "trpg 真好玩");
        listener.onGroupMessage(command);
        assertFalse("trpg 开头的指令不归监听器消费", command.isCancelled());

        GroupMessageEvent action = message(PLAYER, "trpg真好玩，我们继续往前走");
        listener.onGroupMessage(action);
        assertTrue("trpg 连写的普通文本仍应算玩家行动", action.isCancelled());
        assertEquals(1, triggers.fired.size());
        assertEquals(AiTrigger.PLAYER_ACTION, triggers.fired.get(0));
    }

    private SessionActor actor(long userId) {
        return new SessionActor(userId, GROUP, false);
    }

    private GroupMessageEvent message(long userId, String raw) {
        GroupMessageEvent event = new GroupMessageEvent();
        event.setGroupID(GROUP);
        event.setUserID(userId);
        event.setRawMessage(raw);
        return event;
    }

    /** 记录触发类型与群号的替身。 */
    private static final class FakeTriggerSink implements AiTriggerSink {

        private final List<AiTrigger> fired = new ArrayList<AiTrigger>();

        @Override
        public void fire(AiTrigger trigger, long groupId, long eventSeq, String eventText) {
            fired.add(trigger);
        }

        @Override
        public void fireNow(AiTrigger trigger, long groupId) {
            fired.add(trigger);
        }

        @Override
        public void onEnabled(long sessionNo) {
        }
    }
}
