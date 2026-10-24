package org.moboxlab.mbb.trpg.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.moboxlab.mbb.trpg.ai.AiTrigger;
import org.moboxlab.mbb.trpg.ai.AiTriggerSink;
import org.moboxlab.mbb.trpg.card.CardGenerator;
import org.moboxlab.mbb.trpg.config.TrpgConfig;
import org.moboxlab.mbb.trpg.present.TrpgLogReportSource;
import org.moboxlab.mbb.trpg.present.TrpgPresentConfig;
import org.moboxlab.mbb.trpg.present.TrpgPresenter;
import org.moboxlab.mbb.trpg.rule.D100RuleSet;
import org.moboxlab.mbb.trpg.rule.RuleRegistry;
import org.moboxlab.mbb.trpg.script.BuiltinScriptCatalog;
import org.moboxlab.mbb.trpg.script.Script;
import org.moboxlab.mbb.trpg.script.ScriptValidator;
import org.moboxlab.mbb.trpg.session.CooldownRegistry;
import org.moboxlab.mbb.trpg.session.ErrorDedupe;
import org.moboxlab.mbb.trpg.session.SessionLogService;
import org.moboxlab.mbb.trpg.session.SessionResult;
import org.moboxlab.mbb.trpg.session.SessionService;
import org.moboxlab.mbb.trpg.store.BlacklistService;
import org.moboxlab.mbb.trpg.store.CardData;
import org.moboxlab.mbb.trpg.store.JdbcStorageFake;
import org.moboxlab.mbb.trpg.store.SqlSessionStore;
import org.moboxlab.mbb.trpg.store.TrpgSchemaService;
import org.moboxlab.mbb.trpg.testutil.FakeClock;
import org.moboxlab.mbb.trpg.testutil.FakeCommandSender;
import org.moboxlab.mbb.trpg.testutil.FakeImageRenderer;
import org.moboxlab.mbb.trpg.testutil.FakeMessaging;
import org.moboxlab.mbb.trpg.testutil.FakeNameResolver;
import org.moboxlab.mbb.trpg.testutil.FakeRandom;
import org.moboxlab.mbb.trpg.testutil.TempDirs;

/** S8 指令层 B：KP 局内指令、库管理、黑名单与黑名单静默门禁。 */
public class TrpgKpCommandTest {

    private static final long GROUP = 7101L;
    private static final long KP = 2101L;
    private static final long PLAYER = 2102L;
    private static final long OTHER = 2103L;
    private static final long NOW = 1700000000000L;
    private static final String SKILL = "侦查";
    private static final long ACTION_GAP_MILLIS = 2000L;

    private File tempDir;
    private JdbcStorageFake storage;
    private SqlSessionStore store;
    private FakeClock clock;
    private FakeMessaging messaging;
    private FakeImageRenderer renderer;
    private FakeNameResolver names;
    private FakeRandom random;
    private TrpgConfig config;
    private BuiltinScriptCatalog catalog;
    private TrpgPresenter presenter;
    private SessionService sessions;
    private BlacklistService blacklist;
    private TrpgCommandService service;
    private TrpgCommand command;

    @Before
    public void setUp() {
        tempDir = TempDirs.create("trpg-kp-command");
        storage = new JdbcStorageFake(new File(tempDir, "trpg.db"));
        new TrpgSchemaService(storage, null).ensureSchema();
        store = new SqlSessionStore(storage, null);
        clock = new FakeClock(NOW);
        messaging = new FakeMessaging();
        renderer = new FakeImageRenderer();
        names = new FakeNameResolver().put(KP, "阿岚").put(PLAYER, "小满").put(OTHER, "老周");
        config = TrpgConfig.fromValues(new LinkedHashMap<String, String>());
        catalog = new BuiltinScriptCatalog(TrpgKpCommandTest.class.getClassLoader(),
                new ScriptValidator());
        RuleRegistry.register(new D100RuleSet());
        int[] faces = new int[400];
        for (int index = 0; index < faces.length; index++) {
            faces[index] = 3;
        }
        random = new FakeRandom(faces);
        presenter = new TrpgPresenter(TrpgPresentConfig.defaults(), messaging, renderer, names, catalog,
                new TrpgLogReportSource(store, catalog, names, config.retentionSessionDays()), null);
        sessions = new SessionService(store, catalog, clock, random, config, presenter, null);
        blacklist = new BlacklistService(store);
        service = new TrpgCommandService(sessions, new SessionLogService(store, config, clock, null),
                catalog, new CardGenerator(random), presenter, names, config, clock, null,
                new CooldownRegistry(), new ErrorDedupe(), blacklist);
        command = new TrpgCommand(service);
    }

    @After
    public void tearDown() {
        if (storage != null) {
            storage.close();
        }
        TempDirs.deleteRecursively(tempDir);
    }

    @Test
    public void sceneSendsImageClueAndKpSecret() {
        startRunning();
        String title = script().sceneAt(1).title();

        assertTrue(run(kpSender(), "scene", title));

        assertEquals(1, messaging.sentTo("image").size());
        assertTrue(groupText(), groupText().contains("【线索"));
        String clueId = script().sceneAt(1).clueIds().get(0);
        String secret = script().clueById(clueId).unlockedText();
        assertFalse("线索全文不得发到群里：" + groupText(), groupText().contains(secret));
        assertTrue("线索全文应私聊 KP", privateTextTo(KP).contains(secret));
    }

    @Test
    public void playerCheckUsesMergedTriggerWithEventText() {
        startRunning();
        FakeTriggerSink triggers = new FakeTriggerSink();
        service.bindAiTriggers(triggers);

        assertTrue(run(playerSender(), "check", SKILL));

        assertEquals(0, triggers.immediateCount());
        assertEquals(1, triggers.mergedCount());
        assertTrue(triggers.lastMergedText().contains("检定"));
    }

    @Test
    public void checkRepliesFrozenCheckLayout() {
        startRunning();

        assertTrue(run(playerSender(), "check", SKILL));

        assertTrue(groupText(), groupText().contains("【检定 · " + SKILL + "】"));
        assertTrue(groupText(), groupText().contains("1D100 = "));
    }

    @Test
    public void opposedRepliesOpposedLayout() {
        startRunning();

        assertTrue(run(kpSender(), "opposed", mention(PLAYER), SKILL, "vs", "50"));

        assertTrue(groupText(), groupText().contains("【对抗 · " + SKILL + "】"));
        assertTrue(groupText(), groupText().contains("目标值"));
    }

    @Test
    public void sanRepliesSanLayout() {
        startRunning();

        assertTrue(run(kpSender(), "san", mention(PLAYER), "1/1D6"));

        assertTrue(groupText(), groupText().contains("【理智 · 小满】"));
        assertTrue(groupText(), groupText().contains("SAN "));
    }

    @Test
    public void adjustRepliesAdjustLayout() {
        startRunning();

        assertTrue(run(kpSender(), "adjust", mention(PLAYER), SKILL, "+5"));

        assertTrue(groupText(), groupText().contains("【调整 · 小满】"));
        assertTrue(groupText(), groupText().contains("→"));
    }

    @Test
    public void inviteTakeAndSkipReply() {
        startRunning();

        assertTrue(run(kpSender(), "invite", mention(OTHER)));
        assertTrue(groupText(), groupText().contains("【邀请 · 老周】"));
        clock.advance(ACTION_GAP_MILLIS);

        assertTrue(run(kpSender(), "take", mention(PLAYER)));
        // 词表把【接管】钉死为 KP 交接，代管玩家回合并入【提示】。
        assertTrue(groupText(), groupText().contains("【提示】"));
        assertTrue(groupText(), groupText().contains("已记录代管：小满"));
        clock.advance(ACTION_GAP_MILLIS);

        assertTrue(run(kpSender(), "skip", mention(PLAYER), "挂机太久"));
        assertTrue(groupText(), groupText().contains("已记录跳过：小满"));
    }

    @Test
    public void aiSwitchRepliesGlobalOffBranch() {
        startRunning();

        // 默认配置 aiHost.enabled=false：ai on 按「当前状态不可用」拒绝（§9.2 / 验收 I1）。
        assertTrue(run(kpSender(), "ai", "on"));
        assertTrue(groupText(), groupText().contains("【错误】"));
        assertTrue(groupText(), groupText().contains("未在配置中启用"));

        clock.advance(ACTION_GAP_MILLIS);
        assertTrue(run(kpSender(), "ai", "off"));
        assertTrue(groupText(), groupText().contains("【主持】"));
        assertTrue(groupText(), groupText().contains("本局 AI 主持保持关闭"));
    }

    @Test
    public void aiOnWithGlobalSwitchWritesStateAndStatusTurnsOn() {
        startRunning();
        Map<String, String> raw = new LinkedHashMap<String, String>();
        raw.put("aiHost.enabled", "true");
        TrpgConfig enabled = TrpgConfig.fromValues(raw);
        SessionService stubbed = new SessionService(store, catalog, clock, random, enabled, presenter, null);
        TrpgCommandService stubbedService = new TrpgCommandService(stubbed,
                new SessionLogService(store, enabled, clock, null), catalog,
                new CardGenerator(random), presenter, names, enabled, clock, null,
                new CooldownRegistry(), new ErrorDedupe(), blacklist);
        command = new TrpgCommand(stubbedService);

        assertTrue(run(kpSender(), "ai", "on"));
        assertTrue(groupText(), groupText().contains("【主持】"));
        assertTrue(groupText(), groupText().contains("已开启 AI 主持"));

        clock.advance(ACTION_GAP_MILLIS);
        assertTrue(run(kpSender(), "status"));
        assertTrue(groupText(), groupText().contains("AI 主持：开"));
    }

    @Test
    public void aiRejectsUnknownArgument() {
        startRunning();

        assertTrue(run(kpSender(), "ai", "也许"));

        assertTrue(groupText(), groupText().contains("AI 主持只支持 on 或 off"));
    }

    @Test
    public void scriptListNeedsBotAdmin() {
        assertTrue(run(kpSender(), "script", "list"));
        assertTrue(groupText(), groupText().contains("仅限管理员使用"));

        clock.advance(ACTION_GAP_MILLIS);
        assertTrue(run(kpSender().botAdmin(), "script", "list"));
        assertTrue(groupText(), groupText().contains("coc-example"));
    }

    @Test
    public void scriptShowSendsPrivatePage() {
        assertTrue(run(kpSender().botAdmin(), "script", "show", "coc-example"));
        assertEquals("剧本页应私聊发图", 1, messaging.sentTo("private-image").size());
        assertTrue(renderer.last().title().contains("剧本"));
    }

    @Test
    public void scriptIdSwitchesRecruitingSession() {
        startSession();

        assertTrue(run(kpSender(), "script", "coc-example"));

        assertTrue(groupText(), groupText().contains("本局剧本已切换为"));
    }

    @Test
    public void blacklistAddListAndRemove() {
        FakeCommandSender admin = FakeCommandSender.groupAdmin(GROUP, OTHER, "老周");
        assertTrue(run(admin, "blacklist", "add", mention(PLAYER), "刷屏"));
        assertTrue(groupText(), groupText().contains("已把 小满 加入本群黑名单。"));
        assertTrue(blacklist.contains(GROUP, PLAYER));

        clock.advance(ACTION_GAP_MILLIS);
        assertTrue(run(admin, "blacklist", "list"));
        assertTrue(groupText(), groupText().contains("本群黑名单（1 人）"));
        assertTrue(groupText(), groupText().contains("刷屏"));

        clock.advance(ACTION_GAP_MILLIS);
        assertTrue(run(admin, "blacklist", "remove", mention(PLAYER)));
        assertTrue(groupText(), groupText().contains("已把 小满 移出群"));
        assertFalse(blacklist.contains(GROUP, PLAYER));
    }

    @Test
    public void blacklistedMemberGetsNoReply() {
        blacklist.add(GROUP, PLAYER, KP, "刷屏", NOW);
        startSession();
        messaging.clear();

        assertTrue(run(playerSender(), "status"));

        assertTrue("黑名单成员不应收到任何回执：" + messaging.sent(), messaging.sent().isEmpty());
    }

    @Test
    public void blacklistRequiresGroupAdmin() {
        assertTrue(run(playerSender(), "blacklist", "add", mention(OTHER)));

        assertTrue(groupText(), groupText().contains("只有群管理员能管理本群黑名单"));
        assertFalse(blacklist.contains(GROUP, OTHER));
    }

    @Test
    public void commandMetadataCoversAllSubcommands() {
        assertEquals("trpg", command.prefix().get(0));
        assertNotNull(command.usage());
        assertTrue(command.usage().size() >= TrpgSubcommand.values().length);
    }

    private void startSession() {
        SessionResult started = sessions.start(actor(KP), "coc-example");
        assertTrue("建局应成功：" + started.detail(), started.ok());
    }

    private void startRunning() {
        startSession();
        Map<String, Integer> skills = new LinkedHashMap<String, Integer>();
        skills.put(SKILL, 60);
        CardData card = CardData.empty().withSkills(skills);
        SessionResult joined = sessions.join(actor(PLAYER), card);
        assertTrue("加入应成功：" + joined.detail(), joined.ok());
        SessionResult begun = sessions.begin(actor(KP));
        assertTrue("开局应成功：" + begun.detail(), begun.ok());
        clock.advance(ACTION_GAP_MILLIS);
    }

    private Script script() {
        return catalog.get("coc-example");
    }

    private org.moboxlab.mbb.trpg.session.SessionActor actor(long userId) {
        return new org.moboxlab.mbb.trpg.session.SessionActor(userId, GROUP, false);
    }

    private FakeCommandSender kpSender() {
        return FakeCommandSender.group(GROUP, KP, "阿岚");
    }

    private FakeCommandSender playerSender() {
        return FakeCommandSender.group(GROUP, PLAYER, "小满");
    }

    private boolean run(FakeCommandSender sender, String... args) {
        messaging.clear();
        return command.execute(sender, args);
    }

    private static String mention(long userId) {
        return "<@" + userId + ">";
    }

    private String groupText() {
        return messaging.sentTo("group").isEmpty() ? ""
                : messaging.sentTo("group").get(messaging.sentTo("group").size() - 1).text();
    }

    private String privateTextTo(long userId) {
        StringBuilder text = new StringBuilder();
        for (FakeMessaging.SentMessage message : messaging.sentTo("private")) {
            if (message.targetId() == userId) {
                text.append(message.text()).append('\n');
            }
        }
        return text.toString();
    }

    /** 记录 AI 触发类型的假端口。 */
    private static final class FakeTriggerSink implements AiTriggerSink {

        private final java.util.List<AiTrigger> immediate = new java.util.ArrayList<AiTrigger>();
        private final java.util.List<AiTrigger> merged = new java.util.ArrayList<AiTrigger>();
        private final java.util.List<String> mergedTexts = new java.util.ArrayList<String>();

        @Override
        public void fire(AiTrigger trigger, long groupId, long eventSeq, String eventText) {
            merged.add(trigger);
            mergedTexts.add(eventText == null ? "" : eventText);
        }

        @Override
        public void fireNow(AiTrigger trigger, long groupId) {
            immediate.add(trigger);
        }

        @Override
        public void onEnabled(long sessionNo) {
        }

        int immediateCount() {
            return immediate.size();
        }

        int mergedCount() {
            return merged.size();
        }

        AiTrigger lastImmediate() {
            return immediate.isEmpty() ? null : immediate.get(immediate.size() - 1);
        }

        String lastMergedText() {
            return mergedTexts.isEmpty() ? "" : mergedTexts.get(mergedTexts.size() - 1);
        }
    }
}
