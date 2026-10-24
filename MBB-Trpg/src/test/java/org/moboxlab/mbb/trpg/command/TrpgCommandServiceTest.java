package org.moboxlab.mbb.trpg.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.moboxlab.moboxbot.API.Command.CommandSender;
import org.moboxlab.mbb.trpg.card.CardGenerator;
import org.moboxlab.mbb.trpg.config.TrpgConfig;
import org.moboxlab.mbb.trpg.present.TrpgLogReportSource;
import org.moboxlab.mbb.trpg.present.TrpgPresentConfig;
import org.moboxlab.mbb.trpg.present.TrpgPresenter;
import org.moboxlab.mbb.trpg.rule.D100RuleSet;
import org.moboxlab.mbb.trpg.rule.RuleRegistry;
import org.moboxlab.mbb.trpg.script.BuiltinScriptCatalog;
import org.moboxlab.mbb.trpg.script.ScriptValidator;
import org.moboxlab.mbb.trpg.session.SessionLogService;
import org.moboxlab.mbb.trpg.session.SessionService;
import org.moboxlab.mbb.trpg.session.SessionState;
import org.moboxlab.mbb.trpg.store.JdbcStorageFake;
import org.moboxlab.mbb.trpg.store.SessionRow;
import org.moboxlab.mbb.trpg.store.SqlSessionStore;
import org.moboxlab.mbb.trpg.store.TrpgSchemaService;
import org.moboxlab.mbb.trpg.testutil.FakeClock;
import org.moboxlab.mbb.trpg.testutil.FakeCommandSender;
import org.moboxlab.mbb.trpg.testutil.FakeImageRenderer;
import org.moboxlab.mbb.trpg.testutil.FakeMessaging;
import org.moboxlab.mbb.trpg.testutil.FakeNameResolver;
import org.moboxlab.mbb.trpg.testutil.FakeRandom;
import org.moboxlab.mbb.trpg.testutil.TempDirs;

/** /trpg 指令层 A：生命周期、只读指令、别名与拒绝口径。 */
public class TrpgCommandServiceTest {

    private static final long GROUP = 7001L;
    private static final long KP = 2001L;
    private static final long PLAYER = 2002L;
    private static final long OTHER = 2003L;
    private static final long NOW = 1700000000000L;
    private static final int FACE = 3;
    private static final long DEDUPE_WINDOW_MILLIS = 11000L;

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
    private SessionLogService logs;
    private TrpgCommandService service;
    private TrpgCommand command;

    @Before
    public void setUp() {
        tempDir = TempDirs.create("trpg-command");
        storage = new JdbcStorageFake(new File(tempDir, "trpg.db"));
        new TrpgSchemaService(storage, null).ensureSchema();
        store = new SqlSessionStore(storage, null);
        clock = new FakeClock(NOW);
        messaging = new FakeMessaging();
        renderer = new FakeImageRenderer();
        names = new FakeNameResolver().put(KP, "阿岚").put(PLAYER, "小满").put(OTHER, "老周");
        config = TrpgConfig.fromValues(new LinkedHashMap<String, String>());
        catalog = new BuiltinScriptCatalog(TrpgCommandServiceTest.class.getClassLoader(),
                new ScriptValidator());
        RuleRegistry.register(new D100RuleSet());
        int[] faces = new int[400];
        for (int index = 0; index < faces.length; index++) {
            faces[index] = FACE;
        }
        random = new FakeRandom(faces);
        presenter = new TrpgPresenter(TrpgPresentConfig.defaults(), messaging, renderer, names, catalog,
                new TrpgLogReportSource(store, catalog, names, config.retentionSessionDays()), null);
        sessions = new SessionService(store, catalog, clock, random, config, presenter, null);
        logs = new SessionLogService(store, config, clock, null);
        service = new TrpgCommandService(sessions, logs, catalog, new CardGenerator(random), presenter,
                names, config, clock, null);
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
    public void helpListsAllImplementedCommands() {
        assertTrue(run(kpSender(), "help"));
        // 指令多到超过群内单条上限时由拆分器续页，断言要看整段回执。
        String text = groupText();
        assertTrue(text, text.contains("【帮助】"));
        assertTrue(text, text.contains("start / 开团"));
        assertTrue(text, text.contains("pc reroll / 重掷"));
        assertTrue(text, text.contains("scene / 场景"));
        assertTrue(text, text.contains("blacklist / 黑名单"));
        assertFalse("全部子指令都已落地，帮助里不再有后续启用行：" + text,
                text.contains("后续版本启用："));
        assertTrue(text, text.contains("直接发送 /r 3d6"));
    }

    @Test
    public void helpWithSubcommandShowsItsUsage() {
        assertTrue(run(kpSender(), "help", "start"));
        String text = lastGroupText();
        assertTrue(text, text.contains("start / 开团 [剧本ID]"));
        assertTrue(text, text.contains("已启用"));
        clock.advance(2000L);
        assertTrue(run(kpSender(), "help", "scene"));
        assertTrue(lastGroupText(), lastGroupText().contains("scene / 场景 <标题>"));
        assertTrue(lastGroupText(), lastGroupText().contains("已启用"));
    }

    @Test
    public void unknownSubcommandIsRejected() {
        assertTrue(run(kpSender(), "nope"));
        String text = lastGroupText();
        assertTrue(text, text.contains("参数不对：不认识的子指令 nope"));
        assertTrue(text, text.contains("发送 /trpg help 查看可用指令。"));
    }

    @Test
    public void privateChatRejectsGroupOnlyCommands() {
        assertTrue(run(FakeCommandSender.privateSender(PLAYER, "小满"), "start"));
        assertTrue(lastPrivateText(), lastPrivateText().contains("请在群内使用"));
        assertEquals(0, messaging.sentTo("group").size());
    }

    @Test
    public void privateChatAllowsHelp() {
        assertTrue(run(FakeCommandSender.privateSender(PLAYER, "小满"), "help"));
        assertEquals(1, messaging.sentTo("private").size());
        assertTrue(lastPrivateText(), lastPrivateText().contains("帮助"));
    }

    @Test
    public void startPublishesRecruitMessage() {
        assertTrue(run(kpSender(), "start"));
        String text = lastGroupText();
        assertTrue(text, text.contains("【开团】《雨夜古宅》开始招募"));
        assertTrue(text, text.contains("KP：阿岚"));
        assertTrue(text, text.contains("剧本：内置 coc-example"));
        assertTrue(text, text.contains("入门 · 调查 · 封闭场景"));
        assertTrue(text, text.contains("发送 /trpg join 加入"));
    }

    @Test
    public void aliasBehavesLikePrimary() {
        assertTrue(run(kpSender(), "开团"));
        assertTrue(lastGroupText(), lastGroupText().contains("【开团】《雨夜古宅》开始招募"));
    }

    @Test
    public void secondStartIsRejected() {
        assertTrue(run(kpSender(), "start"));
        assertTrue(run(playerSender(), "start"));
        String text = lastGroupText();
        assertTrue(text, text.contains("【错误】"));
        assertTrue(text, text.contains("本群已有未归档的局"));
    }

    @Test
    public void startWithUnknownScriptIsRejected() {
        assertTrue(run(kpSender(), "start", "no-such-script"));
        assertTrue(lastGroupText(), lastGroupText().contains("剧本不存在：no-such-script"));
    }

    @Test
    public void joinSendsGroupNoticeAndPrivateCard() {
        startSession();
        assertTrue(run(playerSender(), "join"));
        String text = lastGroupText();
        assertTrue(text, text.contains("【加入 · 小满】"));
        assertTrue(text, text.contains("小满 加入了本局，当前 2/4 人"));
        assertTrue(text, text.contains("职业："));
        String card = privateTextTo(PLAYER);
        assertTrue(card, card.contains("【角色卡 · 小满】"));
        assertTrue(card, card.contains("STR"));
    }

    @Test
    public void joinAfterLeaveKeepsCard() {
        startSession();
        assertTrue(run(playerSender(), "join"));
        clock.advance(1000L);
        assertTrue(run(playerSender(), "leave"));
        assertTrue(lastGroupText(), lastGroupText().contains("【离开 · 小满】"));
        assertTrue(lastGroupText(), lastGroupText().contains("角色卡已保留"));
        clock.advance(1000L);
        assertTrue(run(playerSender(), "join"));
        assertTrue(lastGroupText(), lastGroupText().contains("【加入 · 小满】"));
    }

    @Test
    public void kpCannotLeave() {
        startSession();
        assertTrue(run(kpSender(), "leave"));
        assertTrue(lastGroupText(), lastGroupText().contains("KP 不能离场"));
    }

    @Test
    public void beginRequiresKpAndPublishesScene() {
        startSession();
        assertTrue(run(playerSender(), "join"));
        clock.advance(1000L);
        assertTrue(run(playerSender(), "begin"));
        assertTrue(lastGroupText(), lastGroupText().contains("只有 KP 能开局"));
        clock.advance(1000L);
        assertTrue(run(kpSender(), "begin"));
        String text = lastGroupText();
        assertTrue(text, text.contains("【开局】《雨夜古宅》"));
        assertTrue(text, text.contains("玩家：小满"));
        assertTrue(text, text.contains("第一幕：古宅门前"));
        assertEquals(1, renderer.renderCount());
        assertTrue(renderer.last().title(), renderer.last().title().contains("情景卡"));
    }

    @Test
    public void statusWithoutSessionReportsNone() {
        assertTrue(run(kpSender(), "status"));
        assertTrue(lastGroupText(), lastGroupText().contains("本群暂无局"));
    }

    @Test
    public void statusShowsRunningSession() {
        startSession();
        assertTrue(run(playerSender(), "join"));
        clock.advance(1000L);
        assertTrue(run(kpSender(), "begin"));
        clock.advance(1000L);
        assertTrue(run(kpSender(), "status"));
        String text = lastGroupText();
        assertTrue(text, text.contains("《雨夜古宅》进行中"));
        assertTrue(text, text.contains("当前场景：古宅门前（第 1/4 幕）"));
        assertTrue(text, text.contains("局内人数：2/4 人"));
        assertTrue(text, text.contains("已解锁线索 0/4"));
        assertTrue(text, text.contains("AI 主持：关"));
    }

    @Test
    public void statusRepeatsAreSuppressedByCooldown() {
        startSession();
        clock.advance(2000L);
        assertTrue(run(kpSender(), "status"));
        assertTrue(run(kpSender(), "status"));
        assertTrue(lastGroupText(), lastGroupText().contains("还有 1 秒冷却"));
    }

    @Test
    public void endNeedsKpOrAdminAndConfirmation() {
        startSession();
        assertTrue(run(playerSender(), "join"));
        clock.advance(1000L);
        assertTrue(run(kpSender(), "begin"));
        clock.advance(2000L);
        assertTrue(run(playerSender(), "end"));
        assertTrue(lastGroupText(), lastGroupText().contains("只有 KP 或群管理员能结束本局"));
        clock.advance(1000L);
        assertTrue(run(kpSender(), "end"));
        String confirm = lastGroupText();
        assertTrue(confirm, confirm.contains("【结束确认】《雨夜古宅》"));
        assertTrue(confirm, confirm.contains("确认方式：KP 确认。"));
        assertTrue(confirm, confirm.contains("30 秒内再次发送 /trpg end 生效。"));
        clock.advance(2000L);
        assertTrue(run(kpSender(), "end"));
        assertTrue(groupText(), groupText().contains("【结束】《雨夜古宅》"));
        assertTrue(groupText(), groupText().contains("结局「"));
        SessionRow row = store.findLatestArchivedByGroup(GROUP, 0L);
        assertNotNull(row);
        assertEquals(SessionState.ENDED, row.state());
        assertEquals("finished", row.endReason());
        assertTrue(row.growthSettled());
        assertEquals(2, renderer.renderCount());
    }

    @Test
    public void groupAdminForcesEndReason() {
        startSession();
        assertTrue(run(playerSender(), "join"));
        clock.advance(1000L);
        assertTrue(run(kpSender(), "begin"));
        clock.advance(2000L);
        FakeCommandSender admin = FakeCommandSender.groupAdmin(GROUP, OTHER, "老周");
        assertTrue(run(admin, "end"));
        assertTrue(lastGroupText(), lastGroupText().contains("确认方式：群管理员强制。"));
        clock.advance(2000L);
        assertTrue(run(admin, "end"));
        SessionRow row = store.findLatestArchivedByGroup(GROUP, 0L);
        assertEquals(SessionState.ENDED, row.state());
        assertEquals("forced", row.endReason());
    }

    @Test
    public void groupOwnerHasSameEndPermissionAsAdmin() {
        startSession();
        assertTrue(run(playerSender(), "join"));
        clock.advance(1000L);
        assertTrue(run(kpSender(), "begin"));
        clock.advance(2000L);
        FakeCommandSender owner = FakeCommandSender.groupOwner(GROUP, OTHER, "老周");
        assertTrue(run(owner, "end"));
        assertTrue(lastGroupText(), lastGroupText().contains("确认方式：群管理员强制。"));
        clock.advance(2000L);
        assertTrue(run(owner, "end"));
        SessionRow row = store.findLatestArchivedByGroup(GROUP, 0L);
        assertEquals(SessionState.ENDED, row.state());
        assertEquals("forced", row.endReason());
    }

    @Test
    public void recruitingEndIsCancelled() {
        startSession();
        clock.advance(2000L);
        assertTrue(run(kpSender(), "end"));
        clock.advance(2000L);
        assertTrue(run(kpSender(), "end"));
        SessionRow row = store.findLatestArchivedByGroup(GROUP, 0L);
        assertEquals(SessionState.ENDED, row.state());
        assertEquals("cancelled", row.endReason());
        assertTrue("招募期取消仍写摘要并推进 growthSettled", row.growthSettled());
    }

    @Test
    public void pcShowsOwnCardPrivately() {
        startSession();
        assertTrue(run(playerSender(), "join"));
        assertTrue(run(playerSender(), "pc"));
        assertTrue(lastPrivateText(), lastPrivateText().contains("【角色卡 · 小满】"));
    }

    @Test
    public void kpCanViewOthersCard() {
        startSession();
        assertTrue(run(playerSender(), "join"));
        clock.advance(1000L);
        assertTrue(run(kpSender(), "pc", "<@2002>"));
        assertTrue(lastPrivateText(), lastPrivateText().contains("【角色卡 · 小满】"));
    }

    @Test
    public void pcRejectsUnparsableTarget() {
        startSession();
        assertTrue(run(kpSender(), "pc", "abc"));
        assertTrue(lastGroupText(), lastGroupText().contains("无法识别玩家 abc"));
    }

    @Test
    public void pcRerollRegeneratesCard() {
        startSession();
        assertTrue(run(playerSender(), "join"));
        clock.advance(1000L);
        assertTrue(run(playerSender(), "pc", "reroll"));
        assertTrue(lastGroupText(), lastGroupText().contains("已重新生成角色卡"));
        assertTrue(lastPrivateText(), lastPrivateText().contains("【角色卡 · 小满】"));
    }

    @Test
    public void pcSetValidatesAttributes() {
        startSession();
        assertTrue(run(playerSender(), "join"));
        assertTrue(run(playerSender(), "pc", "set", "STR", "70"));
        assertTrue(lastGroupText(), lastGroupText().contains("已把 STR 调整为 70"));
        assertTrue(run(playerSender(), "pc", "set", "STR", "100"));
        assertTrue(lastGroupText(), lastGroupText().contains("微调值需在 15～90 之间：100"));
        clock.advance(DEDUPE_WINDOW_MILLIS);
        assertTrue(run(playerSender(), "pc", "set", "LUCK", "50"));
        assertTrue(lastGroupText(), lastGroupText().contains("微调只支持八属性"));
    }

    @Test
    public void pcSetRequiresTwoArguments() {
        startSession();
        assertTrue(run(playerSender(), "join"));
        assertTrue(run(playerSender(), "pc", "set", "STR"));
        assertTrue(lastGroupText(), lastGroupText().contains("微调需要 <字段> <值>"));
    }

    @Test
    public void logShowsFirstPage() {
        startSession();
        assertTrue(run(kpSender(), "log"));
        String text = lastGroupText();
        assertTrue(text, text.contains("【日志 · 1/1】"));
        assertTrue(text, text.contains("《雨夜古宅》共 1 条"));
        assertTrue(text, text.contains("coc-example"));
    }

    @Test
    public void logRejectsInvalidPage() {
        startSession();
        assertTrue(run(kpSender(), "log", "abc"));
        assertTrue(lastGroupText(), lastGroupText().contains("页码必须是正整数：abc"));
    }

    @Test
    public void logRejectsNonMember() {
        startSession();
        assertTrue(run(FakeCommandSender.group(GROUP, OTHER, "老周"), "log"));
        assertTrue(lastGroupText(), lastGroupText().contains("只有本局参与者能回看日志"));
    }

    @Test
    public void kpCommandWithoutSessionRepliesStateError() {
        assertTrue(run(kpSender(), "scene", "书房"));
        String text = lastGroupText();
        assertTrue(text, text.contains("【错误】"));
        assertTrue(text, text.contains("本群暂无局"));
    }

    @Test
    public void commandMetadataMatchesFrozenPrefix() {
        assertEquals(1, command.prefix().size());
        assertEquals("trpg", command.prefix().get(0));
        assertEquals(0, command.cooldownSeconds());
        List<String> usages = command.usage();
        assertTrue(usages.contains("/trpg start / 开团 [剧本ID]"));
        assertTrue(usages.contains("/trpg pc reroll / 重掷"));
    }

    @Test
    public void executeAcceptsUnknownSenderGracefully() {
        assertFalse(service.handle(null, new String[]{"help"}));
    }

    private void startSession() {
        assertTrue(run(kpSender(), "start"));
        clock.advance(1000L);
    }

    private boolean run(CommandSender sender, String... args) {
        return command.execute(sender, args);
    }

    private FakeCommandSender kpSender() {
        return FakeCommandSender.group(GROUP, KP, "阿岚");
    }

    private FakeCommandSender playerSender() {
        return FakeCommandSender.group(GROUP, PLAYER, "小满");
    }

    private String groupText() {
        StringBuilder builder = new StringBuilder();
        for (FakeMessaging.SentMessage message : messaging.sentTo("group")) {
            builder.append(message.text() == null ? "" : message.text()).append('\n');
        }
        return builder.toString();
    }

    private String lastGroupText() {
        List<FakeMessaging.SentMessage> messages = messaging.sentTo("group");
        return messages.isEmpty() ? "" : messages.get(messages.size() - 1).text();
    }

    private String lastPrivateText() {
        List<FakeMessaging.SentMessage> messages = messaging.sentTo("private");
        return messages.isEmpty() ? "" : messages.get(messages.size() - 1).text();
    }

    private String privateTextTo(long userId) {
        StringBuilder builder = new StringBuilder();
        for (FakeMessaging.SentMessage message : messaging.sentTo("private")) {
            if (message.targetId() == userId) {
                builder.append(message.text() == null ? "" : message.text()).append('\n');
            }
        }
        return builder.toString();
    }
}
