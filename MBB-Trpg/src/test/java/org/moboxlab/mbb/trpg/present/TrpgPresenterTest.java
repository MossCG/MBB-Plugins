package org.moboxlab.mbb.trpg.present;

import org.junit.Before;
import org.junit.Test;
import org.moboxlab.mbb.trpg.script.Clue;
import org.moboxlab.mbb.trpg.script.Scene;
import org.moboxlab.mbb.trpg.script.Script;
import org.moboxlab.mbb.trpg.session.GrowthOutcome;
import org.moboxlab.mbb.trpg.session.MemberView;
import org.moboxlab.mbb.trpg.session.SessionEvent;
import org.moboxlab.mbb.trpg.session.SessionSnapshot;
import org.moboxlab.mbb.trpg.session.SessionState;
import org.moboxlab.mbb.trpg.store.CardData;
import org.moboxlab.mbb.trpg.testutil.FakeImageRenderer;
import org.moboxlab.mbb.trpg.testutil.FakeMessaging;
import org.moboxlab.mbb.trpg.testutil.FakeNameResolver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** 呈现门面测试：模板选择、回退、私聊降级与拆分（J11/J13/J14）。 */
public class TrpgPresenterTest {

    private static final long GROUP = 1001L;

    private FakeMessaging messaging;
    private FakeImageRenderer renderer;
    private FakeNameResolver names;
    private StubReportSource reports;
    private TrpgPresenter presenter;

    @Before
    public void setUp() {
        messaging = new FakeMessaging();
        renderer = new FakeImageRenderer();
        names = new FakeNameResolver();
        reports = new StubReportSource(report());
        presenter = new TrpgPresenter(TrpgPresentConfig.defaults(), messaging, renderer, names,
                null, reports, null);
    }

    @Test
    public void rejectsNullCollaborators() {
        assertIllegalArgument(null, messaging, renderer, names, reports);
        assertIllegalArgument(TrpgPresentConfig.defaults(), null, renderer, names, reports);
        assertIllegalArgument(TrpgPresentConfig.defaults(), messaging, renderer, null, reports);
        assertIllegalArgument(TrpgPresentConfig.defaults(), messaging, renderer, names, null);
    }

    @Test
    public void rejectsNullEvent() {
        try {
            presenter.publish(null);
            fail("应当拒绝空事件");
        } catch (IllegalArgumentException expected) {
            assertEquals("事件不能为空", expected.getMessage());
        }
    }

    @Test
    public void endedEventSendsReportImageBeforeText() {
        presenter.publish(endedEvent());

        assertEquals(2, messaging.sent().size());
        assertEquals("image", messaging.sent().get(0).channel());
        assertEquals("group", messaging.sent().get(1).channel());
        assertEquals("战报 · 《雨夜古宅》", renderer.last().title());
        String text = messaging.sentTo("group").get(0).text();
        assertTrue(text.startsWith("【结束】《雨夜古宅》"));
        assertTrue(text.contains("战报图见上一条"));
        assertTrue(text.contains("保留 30 天"));
    }

    @Test
    public void endedEventFallsBackToReportTextWhenImageUnavailable() {
        renderer.returnsNull(true);

        presenter.publish(endedEvent());

        assertEquals(0, messaging.sentTo("image").size());
        String text = messaging.sentTo("group").get(0).text();
        assertTrue(text.startsWith("【战报】《雨夜古宅》"));
        assertTrue(text.contains("时长：2 小时 06 分"));
        assertTrue(text.contains("KP：阿岚"));
    }

    @Test
    public void endedEventFallsBackWhenRendererThrows() {
        renderer.failWith(new IllegalStateException("画布不可用"));

        presenter.publish(endedEvent());

        assertEquals(0, messaging.sentTo("image").size());
        assertTrue(messaging.sentTo("group").get(0).text().startsWith("【战报】"));
    }

    @Test
    public void endedEventWithoutReportImageSkipsImage() {
        presenter = presenter(new TrpgPresentConfig(500, 1200, 3, true, false, 22, "broadcast"));

        presenter.publish(endedEvent());

        assertEquals(0, renderer.renderCount());
        String text = messaging.sentTo("group").get(0).text();
        assertTrue(text.startsWith("【结束】"));
        assertFalse(text.contains("战报图见上一条"));
    }

    @Test
    public void endedEventSendFailureAddsCriticalNotice() {
        messaging.failNextGroupText(1);

        presenter.publish(endedEvent());

        List<FakeMessaging.SentMessage> groups = messaging.sentTo("group");
        assertEquals(1, groups.size());
        assertTrue(groups.get(0).text().contains("【提示】"));
        assertTrue(groups.get(0).text().contains(TrpgPresenter.CRITICAL_FAILURE_NOTICE));
    }

    @Test
    public void endedEventSwallowsReportSourceError() {
        reports.failWith(new IllegalStateException("战报来源炸了"));

        presenter.publish(endedEvent());

        assertEquals(0, messaging.sent().size());
    }

    @Test
    public void recoveredEventMentionsResume() {
        presenter.publish(SessionEvent.recovered(snapshot()));

        String text = messaging.sentTo("group").get(0).text();
        assertTrue(text.contains("【恢复】"));
        assertTrue(text.contains("已恢复 2 名玩家。"));
        assertTrue(text.contains("/trpg resume"));
    }

    @Test
    public void recoveredEventFallsBackWhenSendFails() {
        messaging.failNextGroupText(1);

        presenter.publish(SessionEvent.recovered(snapshot()));

        assertTrue(messaging.sentTo("group").get(0).text()
                .contains(TrpgPresenter.CRITICAL_FAILURE_NOTICE));
    }

    @Test
    public void privateSecretFailureNeverRevealsSecret() {
        String secret = "线索全文：地下室的镜子会移动。";
        messaging.failNextPrivateText(1);

        assertFalse(presenter.sendPrivateSecret(GROUP, 301L, secret));

        List<FakeMessaging.SentMessage> groups = messaging.sentTo("group");
        assertEquals(1, groups.size());
        assertEquals("【提示】\n" + TrpgPresenter.SECRET_UNREACHABLE_NOTICE, groups.get(0).text());
        assertFalse(groups.get(0).text().contains("镜子"));
        assertEquals(1, presenter.privateFailureStreak(301L));
    }

    @Test
    public void privateCardFailureNoticesGroupAndCountsStreak() {
        messaging.failNextPrivateText(1);

        assertFalse(presenter.sendPrivateCard(GROUP, 302L, "小满", "【角色卡 · 小满】\n力量 60"));

        String notice = messaging.sentTo("group").get(0).text();
        assertTrue(notice.contains("小满" + TrpgPresenter.CARD_UNREACHABLE_SUFFIX));
        assertEquals(1, presenter.privateFailureStreak(302L));
        assertFalse(presenter.privateDegraded(302L));

        messaging.failNextPrivateText(1);
        presenter.sendPrivateCard(GROUP, 302L, "小满", "【角色卡 · 小满】");
        assertEquals(2, presenter.privateFailureStreak(302L));
        assertTrue(presenter.privateDegraded(302L));

        assertTrue(presenter.sendPrivateCard(GROUP, 302L, "小满", "【角色卡 · 小满】"));
        assertEquals(0, presenter.privateFailureStreak(302L));
        assertFalse(presenter.privateDegraded(302L));
    }

    @Test
    public void privateCardFailureUsesFallbackName() {
        messaging.failNextPrivateText(1);

        presenter.sendPrivateCard(GROUP, 303L, "  ", "【角色卡】");

        assertTrue(messaging.sentTo("group").get(0).text()
                .contains(TrpgNameResolver.fallback(303L)));
    }

    @Test
    public void longGroupMessageSplitsIntoAtMostThreeParts() {
        presenter = presenter(new TrpgPresentConfig(50, 1200, 3, true, true, 22, "broadcast"));

        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            body.append("第").append(i).append("段正文，写满这一段以便触发拆分。\n\n");
        }
        presenter.sendGroup(GROUP, "【提示】\n" + body);

        List<FakeMessaging.SentMessage> groups = messaging.sentTo("group");
        assertTrue(groups.size() >= 2);
        assertTrue(groups.size() <= 3);
        assertTrue(groups.get(groups.size() - 1).text().contains("本条还有"));
    }

    @Test
    public void sceneImageIsSentWhenEnabled() {
        TrpgSceneCard card = sceneCard();

        assertTrue(presenter.sendScene(GROUP, card));

        assertEquals(1, messaging.sentTo("image").size());
        assertEquals("【场景】书房 · 第 2/4 幕", renderer.last().line(0));
        assertEquals(0, messaging.sentTo("group").size());
    }

    @Test
    public void sceneFallsBackToTextWhenImageDisabled() {
        presenter = presenter(new TrpgPresentConfig(500, 1200, 3, false, true, 22, "broadcast"));

        assertTrue(presenter.sendScene(GROUP, sceneCard()));

        assertEquals(0, renderer.renderCount());
        String text = messaging.sentTo("group").get(0).text();
        assertTrue(text.contains("【场景 · 书房】"));
        // 回退正文优先当前场景叙述（规范 §7.4），剧本原文只在叙述缺失时兜底。
        assertTrue(text.contains("灯芯爆了一下，书架后传来轻轻的摩擦声。"));
        assertTrue(text.contains("发送 /trpg status 查看局面。"));
    }

    @Test
    public void sceneFallsBackToTextWhenImageSendFails() {
        messaging.failNextGroupImage(1);

        assertTrue(presenter.sendScene(GROUP, sceneCard()));

        assertTrue(messaging.sentTo("group").get(0).text().contains("【场景 · 书房】"));
    }

    @Test
    public void sceneFallsBackToScriptNarrationWhenNarrationMissing() {
        presenter = presenter(new TrpgPresentConfig(500, 1200, 3, false, true, 22, "broadcast"));
        TrpgSceneCard card = new TrpgSceneCard("书房", 2, 4, Collections.<String>emptyList(),
                Collections.<String>emptyList(), "",
                "房门在你们敲到第三下时才开。", Collections.<String>emptyList());

        assertTrue(presenter.sendScene(GROUP, card));

        String text = messaging.sentTo("group").get(0).text();
        assertTrue(text.contains("房门在你们敲到第三下时才开。"));
    }

    @Test
    public void sceneRejectsNullCard() {
        try {
            presenter.sendScene(GROUP, null);
            fail("应当拒绝空情景卡");
        } catch (IllegalArgumentException expected) {
            assertEquals("情景卡不能为空", expected.getMessage());
        }
    }

    @Test
    public void scriptPageFallsBackToPrivateTextWithoutRenderer() {
        presenter = new TrpgPresenter(TrpgPresentConfig.defaults(), messaging, null, names,
                null, reports, null);

        assertTrue(presenter.sendScriptPage(GROUP, 401L, script()));

        assertEquals(0, messaging.sentTo("private-image").size());
        String text = messaging.sentTo("private").get(0).text();
        assertTrue(text.contains("剧本"));
        assertTrue(text.contains("雨夜古宅"));
        assertEquals(0, presenter.privateFailureStreak(401L));
    }

    @Test
    public void scriptPageUsesPrivateImage() {
        assertTrue(presenter.sendScriptPage(GROUP, 402L, script()));

        assertEquals(1, messaging.sentTo("private-image").size());
        assertEquals("剧本 · 《雨夜古宅》", renderer.last().title());
    }

    @Test
    public void reportImageReportsAvailabilityToCaller() {
        assertTrue(presenter.sendReportImage(GROUP, 7L, report()));

        presenter = new TrpgPresenter(TrpgPresentConfig.defaults(), messaging, null, names,
                null, reports, null);
        assertFalse(presenter.sendReportImage(GROUP, 7L, report()));
    }

    @Test
    public void noticeIsSentAsSingleGroupMessage() {
        assertTrue(presenter.sendNotice(GROUP, "本群 TRPG 已暂停。"));

        assertEquals(1, messaging.sentTo("group").size());
        assertEquals("【提示】\n本群 TRPG 已暂停。", messaging.sentTo("group").get(0).text());
    }

    @Test
    public void cardDegradesToGroupFoldAfterRepeatedPrivateFailures() {
        messaging.failNextPrivateText(2);

        assertFalse(presenter.sendPrivateCard(GROUP, 305L, "小满", "【角色卡 · 小满】\n力量 60"));
        assertFalse(presenter.sendPrivateCard(GROUP, 305L, "小满", "【角色卡 · 小满】\n力量 60"));
        assertTrue(presenter.privateDegraded(305L));
        int privateBefore = messaging.sentTo("private").size();
        int groupBefore = messaging.sentTo("group").size();

        assertTrue(presenter.sendPrivateCard(GROUP, 305L, "小满",
                "【角色卡 · 小满】\n力量 60\n技能：聆听 60", "职业：记者　聆听 60　侦查 50"));

        // 降级后不再尝试私聊，只在群内发一行折叠摘要，不发角色卡全文。
        assertEquals(privateBefore, messaging.sentTo("private").size());
        assertEquals(groupBefore + 1, messaging.sentTo("group").size());
        String folded = messaging.sentTo("group").get(groupBefore).text();
        assertTrue(folded.contains(TrpgPresenter.CARD_FOLDED_SUFFIX));
        assertTrue(folded.contains("职业：记者"));
        assertFalse(folded.contains("【角色卡"));
    }

    @Test
    public void emptyMessageIsNotReportedAsSent() {
        assertFalse(presenter.sendGroup(GROUP, "   "));
        assertFalse(presenter.sendPrivate(306L, ""));
        assertTrue(messaging.sent().isEmpty());

        // 模板会为正文补标题行，所以 notice 传空白仍有标题可发，不算空消息。
        assertTrue(presenter.sendNotice(GROUP, " "));
        assertEquals(1, messaging.sentTo("group").size());
        assertEquals("【提示】", messaging.sentTo("group").get(0).text());
    }

    @Test
    public void scriptPageFallsBackToPrivateTextWhenImageFails() {
        messaging.failNextPrivateImage(1);

        assertTrue(presenter.sendScriptPage(GROUP, 403L, script()));

        assertEquals(0, messaging.sentTo("private-image").size());
        assertTrue(messaging.sentTo("private").get(0).text().contains("剧本"));
    }

    private void assertIllegalArgument(TrpgPresentConfig config, FakeMessaging out,
                                       FakeImageRenderer rendererPort, FakeNameResolver namePort,
                                       StubReportSource reportPort) {
        try {
            new TrpgPresenter(config, out, rendererPort, namePort, null, reportPort, null);
            fail("应当拒绝空依赖");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().endsWith("不能为空"));
        }
    }

    private TrpgPresenter presenter(TrpgPresentConfig config) {
        renderer.clear();
        messaging.clear();
        return new TrpgPresenter(config, messaging, renderer, names, null, reports, null);
    }

    private static SessionEvent endedEvent() {
        return SessionEvent.ended(snapshot(), "finished", Collections.<GrowthOutcome>emptyList());
    }

    private static SessionSnapshot snapshot() {
        return SessionSnapshot.builder()
                .groupId(GROUP)
                .sessionId(7L)
                .state(SessionState.RUNNING)
                .kpUserId(201L)
                .scriptId("coc-example")
                .ruleSetId("d100")
                .sceneIndex(1)
                .sceneTitle("书房")
                .startedAt(1_700_000_000_000L)
                .endedAt(1_700_007_560_000L)
                .endReason("finished")
                .members(Arrays.asList(member(201L, 0L), member(202L, 0L), member(203L, 9L)))
                .unlockedClues(Arrays.asList("clue-print", "clue-letter"))
                .build();
    }

    private static MemberView member(long userId, long leftAt) {
        return new MemberView(userId, 1_700_000_000_000L, leftAt, false, 0L, CardData.empty(),
                Collections.<String>emptyList());
    }

    private static TrpgReport report() {
        return TrpgReport.builder()
                .scriptName("雨夜古宅")
                .endingTitle("雨夜脱身")
                .endingNarration("你们带着那封没寄出的信走进雨里。")
                .startedAt(1_700_000_000_000L)
                .endedAt(1_700_007_560_000L)
                .durationMinutes(126)
                .kpName("阿岚")
                .playerNames(Arrays.asList("小满", "老周"))
                .clueNames(Arrays.asList("泥脚印", "未寄出的信", "煤油灯下的影子"))
                .clueTotal(4)
                .checkTotal(3)
                .checkSuccess(2)
                .checkResults(Arrays.asList("聆听 成功", "侦察 成功", "理智 失败"))
                .growthLines(Arrays.asList("聆听 50 → 52"))
                .retentionDays(30)
                .build();
    }

    private static TrpgSceneCard sceneCard() {
        List<String> present = new ArrayList<String>();
        present.add("小满");
        present.add("老周");
        List<String> clues = new ArrayList<String>();
        clues.add("泥脚印");
        List<String> steps = new ArrayList<String>();
        steps.add("询问周福来意");
        return new TrpgSceneCard("书房", 2, 4, present, clues,
                "灯芯爆了一下，书架后传来轻轻的摩擦声。",
                "房门在你们敲到第三下时才开。周福站在门内，没有让开。", steps);
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

    /** 战报来源假件：返回固定战报，可注入异常。 */
    private static final class StubReportSource implements TrpgReportSource {
        private final TrpgReport report;
        private RuntimeException failure;

        StubReportSource(TrpgReport report) {
            this.report = report;
        }

        void failWith(RuntimeException error) {
            this.failure = error;
        }

        @Override
        public TrpgReport report(SessionEvent event, Script script) {
            if (failure != null) {
                throw failure;
            }
            return report;
        }
    }
}