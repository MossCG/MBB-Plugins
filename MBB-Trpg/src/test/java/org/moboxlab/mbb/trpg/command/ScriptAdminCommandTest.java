package org.moboxlab.mbb.trpg.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.concurrent.Executor;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.moboxlab.mbb.trpg.ai.AiResult;
import org.moboxlab.mbb.trpg.config.TrpgConfig;
import org.moboxlab.mbb.trpg.present.TrpgLogReportSource;
import org.moboxlab.mbb.trpg.present.TrpgPresentConfig;
import org.moboxlab.mbb.trpg.present.TrpgPresenter;
import org.moboxlab.mbb.trpg.rule.D100RuleSet;
import org.moboxlab.mbb.trpg.rule.RuleRegistry;
import org.moboxlab.mbb.trpg.card.CardGenerator;
import org.moboxlab.mbb.trpg.script.BuiltinScriptCatalog;
import org.moboxlab.mbb.trpg.script.ScriptAdmin;
import org.moboxlab.mbb.trpg.script.ScriptGenerationService;
import org.moboxlab.mbb.trpg.script.ScriptImportMode;
import org.moboxlab.mbb.trpg.script.ScriptImportSessions;
import org.moboxlab.mbb.trpg.script.ScriptLibrary;
import org.moboxlab.mbb.trpg.script.ScriptRejectedStore;
import org.moboxlab.mbb.trpg.script.ScriptValidator;
import org.moboxlab.mbb.trpg.session.CooldownRegistry;
import org.moboxlab.mbb.trpg.session.ErrorDedupe;
import org.moboxlab.mbb.trpg.session.SessionActor;
import org.moboxlab.mbb.trpg.session.SessionLogService;
import org.moboxlab.mbb.trpg.session.SessionResult;
import org.moboxlab.mbb.trpg.session.SessionService;
import org.moboxlab.mbb.trpg.store.BlacklistService;
import org.moboxlab.mbb.trpg.store.JdbcStorageFake;
import org.moboxlab.mbb.trpg.store.SqlSessionStore;
import org.moboxlab.mbb.trpg.store.TrpgSchemaService;
import org.moboxlab.mbb.trpg.testutil.FakeAiClient;
import org.moboxlab.mbb.trpg.testutil.FakeClock;
import org.moboxlab.mbb.trpg.testutil.FakeCommandSender;
import org.moboxlab.mbb.trpg.testutil.FakeImageRenderer;
import org.moboxlab.mbb.trpg.testutil.FakeMessaging;
import org.moboxlab.mbb.trpg.testutil.FakeNameResolver;
import org.moboxlab.mbb.trpg.testutil.FakeRandom;
import org.moboxlab.mbb.trpg.testutil.RecordingLogger;
import org.moboxlab.mbb.trpg.testutil.ScriptResources;
import org.moboxlab.mbb.trpg.testutil.TempDirs;

/** S10 库管理指令：分段导入的起始/结束、删除保护、生成参数与冷却。 */
public class ScriptAdminCommandTest {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final long GROUP = 7201L;
    private static final long KP = 2201L;
    private static final long OTHER = 2202L;
    private static final long NOW = 1700000000000L;

    private File tempDir;
    private JdbcStorageFake storage;
    private FakeClock clock;
    private FakeMessaging messaging;
    private FakeAiClient ai;
    private ScriptLibrary library;
    private ScriptImportSessions imports;
    private ScriptAdmin admin;
    private SessionService sessions;
    private TrpgCommand command;

    @Before
    public void setUp() {
        tempDir = TempDirs.create("trpg-script-admin");
        storage = new JdbcStorageFake(new File(tempDir, "trpg.db"));
        new TrpgSchemaService(storage, null).ensureSchema();
        SqlSessionStore store = new SqlSessionStore(storage, null);
        clock = new FakeClock(NOW);
        messaging = new FakeMessaging();
        FakeImageRenderer renderer = new FakeImageRenderer();
        FakeNameResolver names = new FakeNameResolver().put(KP, "阿岚").put(OTHER, "老周");
        TrpgConfig config = TrpgConfig.fromValues(new LinkedHashMap<String, String>());
        RecordingLogger logger = new RecordingLogger();
        ScriptValidator validator = new ScriptValidator();
        BuiltinScriptCatalog builtin = new BuiltinScriptCatalog(
                ScriptAdminCommandTest.class.getClassLoader(), validator);
        library = new ScriptLibrary(builtin, new File(tempDir, "scripts"), validator, 65536L, logger);
        ScriptRejectedStore rejected = new ScriptRejectedStore(new File(tempDir, "rejected"), 20, logger);
        imports = new ScriptImportSessions(library, validator, rejected, 65536L,
                5 * 60 * 1000L, 30 * 60 * 1000L, 200,
                new java.util.function.LongSupplier() {
                    @Override
                    public long getAsLong() {
                        return clock.nowMillis();
                    }
                });
        ai = new FakeAiClient();
        ScriptGenerationService generation = new ScriptGenerationService(library, validator, ai, rejected);
        Executor direct = new Executor() {
            @Override
            public void execute(Runnable task) {
                task.run();
            }
        };
        admin = new ScriptAdmin(imports, generation, direct);
        RuleRegistry.register(new D100RuleSet());
        int[] faces = new int[200];
        for (int index = 0; index < faces.length; index++) {
            faces[index] = 3;
        }
        FakeRandom random = new FakeRandom(faces);
        TrpgPresenter presenter = new TrpgPresenter(TrpgPresentConfig.defaults(), messaging, renderer,
                names, library, new TrpgLogReportSource(store, library, names,
                        config.retentionSessionDays()), null);
        sessions = new SessionService(store, library, clock, random, config, presenter, null);
        library.bindUsageIndex(sessions);
        TrpgCommandService service = new TrpgCommandService(sessions,
                new SessionLogService(store, config, clock, null), library, new CardGenerator(random),
                presenter, names, config, clock, null, new CooldownRegistry(), new ErrorDedupe(),
                new BlacklistService(store), admin);
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
    public void importBeginRepliesGuideAndStartsPending() {
        assertTrue(run(kpSender(), "script", "import"));

        String text = groupText();
        assertTrue(text, text.contains("【剧本 · 导入】"));
        assertTrue(text, text.contains("/trpg script import end"));
        assertTrue(imports.hasPending(GROUP, KP));
    }

    @Test
    public void importReplaceUsesReplaceMode() {
        assertTrue(run(kpSender(), "script", "import", "replace"));

        assertTrue(groupText(), groupText().contains("替换导入"));
        assertTrue(imports.hasPending(GROUP, KP));
    }

    @Test
    public void importRejectsUnknownAction() {
        assertTrue(run(kpSender(), "script", "import", "foo"));

        assertTrue(groupText(), groupText().contains("导入只支持 replace"));
        assertFalse(imports.hasPending(GROUP, KP));
    }

    @Test
    public void importWhilePendingOnlyRepliesNotice() {
        assertTrue(run(kpSender(), "script", "import"));
        assertTrue(run(kpSender(), "script", "import"));

        assertTrue(groupText(), groupText().contains("已有一段导入在进行"));
    }

    @Test
    public void importEndWithoutPendingIsRejected() {
        assertTrue(run(kpSender(), "script", "import", "end"));

        assertTrue(groupText(), groupText().contains("E_IMPORT_STATE"));
    }

    @Test
    public void scriptLibraryActionsRequireBotAdmin() {
        FakeCommandSender player = FakeCommandSender.group(GROUP, OTHER, "老周");
        assertTrue(run(player, "script", "import"));

        assertTrue(groupText(), groupText().contains("管理员"));
        assertFalse(imports.hasPending(GROUP, OTHER));
    }

    @Test
    public void deleteBuiltinScriptIsRejected() {
        assertTrue(run(kpSender(), "script", "delete", "coc-example"));

        assertTrue(groupText(), groupText().contains("内置剧本不能删除"));
        assertNotNull(library.get("coc-example"));
    }

    @Test
    public void deleteMissingScriptIsRejected() {
        assertTrue(run(kpSender(), "script", "delete", "no-such"));

        assertTrue(groupText(), groupText().contains("剧本不存在"));
    }

    @Test
    public void deleteInUseScriptIsRejected() throws Exception {
        importScript("in-use-1");
        SessionResult started = sessions.start(new SessionActor(KP, GROUP, false), "in-use-1");
        assertTrue("建局应成功：" + started.detail(), started.ok());

        assertTrue(run(kpSender(), "script", "delete", "in-use-1"));

        assertTrue(groupText(), groupText().contains("正被未归档的局引用"));
        assertNotNull(library.get("in-use-1"));
    }

    @Test
    public void deleteImportedScriptSucceeds() throws Exception {
        importScript("deletable-1");

        assertTrue(run(kpSender(), "script", "delete", "deletable-1"));

        assertTrue(groupText(), groupText().contains("已删除"));
        assertNull(library.get("deletable-1"));
    }

    @Test
    public void genRejectsMissingTheme() {
        assertTrue(run(kpSender(), "script", "gen"));

        assertTrue(groupText(), groupText().contains("缺少题材"));
    }

    @Test
    public void genRejectsOutOfRangePlayers() {
        assertTrue(run(kpSender(), "script", "gen", "雪山山庄", "21"));

        assertTrue(groupText(), groupText().contains("人数需在 1-20 之间"));
    }

    @Test
    public void genRejectsOutOfRangeMinutes() {
        assertTrue(run(kpSender(), "script", "gen", "雪山山庄", "4", "5"));

        assertTrue(groupText(), groupText().contains("时长需在 15-600 分钟之间"));
    }

    @Test
    public void genNotifiesAndDeliversToOriginator() {
        ai.enqueue(AiResult.success(ScriptResources.example(), "stop", "{}", false, 1L));

        assertTrue(run(kpSender(), "script", "gen", "雪山山庄", "4", "90"));

        assertTrue(groupText(), groupText().contains("已开始生成剧本《雪山山庄》"));
        assertFalse("异步结果不应发给全员", messaging.sentTo("group-at:" + KP).isEmpty());
        assertTrue(messaging.sentTo("group-at:" + KP).get(0).text().contains("剧本"));
        assertTrue(library.get("gen-snow") != null || library.list().size() > 1);
    }

    @Test
    public void genSecondCallIsCoolingDown() {
        ai.enqueue(AiResult.success(ScriptResources.example(), "stop", "{}", false, 1L));
        assertTrue(run(kpSender(), "script", "gen", "雪山山庄"));

        assertTrue(run(kpSender(), "script", "gen", "雪山山庄"));

        assertTrue(groupText(), groupText().contains("生成冷却中"));
    }

    private void importScript(String scriptId) throws Exception {
        String json = ScriptResources.example().replace("coc-example", scriptId);
        imports.begin(GROUP, KP, ScriptImportMode.CREATE);
        imports.append(GROUP, KP, json.substring(0, json.length() / 2));
        imports.append(GROUP, KP, json.substring(json.length() / 2));
        imports.end(GROUP, KP);
        assertTrue("导入应写入剧本库", library.get(scriptId) != null);
        assertFalse(imports.hasPending(GROUP, KP));
    }

    private FakeCommandSender kpSender() {
        FakeCommandSender sender = FakeCommandSender.group(GROUP, KP, "阿岚");
        return sender.botAdmin();
    }

    private boolean run(FakeCommandSender sender, String... args) {
        messaging.clear();
        return command.execute(sender, args);
    }

    private String groupText() {
        return messaging.sentTo("group").isEmpty() ? ""
                : messaging.sentTo("group").get(messaging.sentTo("group").size() - 1).text();
    }
}
