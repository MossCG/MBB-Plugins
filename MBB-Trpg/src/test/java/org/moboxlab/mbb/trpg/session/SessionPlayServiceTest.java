package org.moboxlab.mbb.trpg.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.moboxlab.mbb.trpg.script.Clue;
import org.moboxlab.mbb.trpg.script.Scene;
import org.moboxlab.mbb.trpg.store.CardData;
import org.moboxlab.mbb.trpg.store.LogRow;
import org.moboxlab.mbb.trpg.store.MemberRow;
import org.moboxlab.mbb.trpg.testutil.FakeRandom;

/** 局内推进：场景、检定、理智、对抗、调整、邀请、接管、跳过与换剧本。 */
public class SessionPlayServiceTest extends SessionTestFixture {

    @Test
    public void sceneAdvancesAndUnlocksClues() {
        startRunning();
        long sessionId = activeSessionId();
        Scene second = script().sceneAt(1);

        PlayResult<SessionOutcomes.Scene> played = service.scene(actor(KP), second.title());

        assertTrue("推进场景应成功：" + played.result().detail(), played.ok());
        assertEquals(1, played.detail().sceneIndex());
        assertFalse("第二个场景应解锁新线索", played.detail().newClueKeys().isEmpty());
        SessionSnapshot snapshot = service.readSnapshot(GROUP);
        assertEquals(second.title(), snapshot.sceneTitle());
        assertTrue(snapshot.unlockedClues().containsAll(played.detail().newClueKeys()));
        assertTrue("场景事件应落库", hasLog(sessionId, "scene", second.title()));
        for (String clueKey : played.detail().newClueKeys()) {
            assertTrue("线索解锁应落库：" + clueKey, hasLog(sessionId, "clue", clueKey));
        }
    }

    @Test
    public void sceneRejectsNonKp() {
        startRunning();
        PlayResult<SessionOutcomes.Scene> played = service.scene(actor(PLAYER),
                script().sceneAt(1).title());
        assertFalse(played.ok());
        assertEquals(SessionReject.NO_PERMISSION, played.result().reject());
    }

    @Test
    public void sceneRejectsUnknownTitle() {
        startRunning();
        PlayResult<SessionOutcomes.Scene> played = service.scene(actor(KP), "不存在的场景");
        assertFalse(played.ok());
        assertEquals(SessionReject.BAD_ARGUMENT, played.result().reject());
        assertTrue(played.result().detail(), played.result().detail().contains("剧本里没有该场景"));
    }

    @Test
    public void checkSuccessMarksGrowthSkill() {
        startRunning();
        long sessionId = activeSessionId();
        setCard(sessionId, PLAYER, cardWithSkill(SKILL, 60));
        service = buildService(new FakeRandom(30), collectingPublisher());

        PlayResult<SessionOutcomes.Check> played = service.check(actor(PLAYER), 0L, SKILL, "", "");

        assertTrue("检定应成功：" + played.result().detail(), played.ok());
        assertEquals(30, played.detail().rolled());
        assertEquals(60, played.detail().target());
        assertTrue(played.detail().success());
        MemberRow updated = member(sessionId, PLAYER);
        assertNotNull(updated);
        assertTrue("成功技能应记入成长集合", updated.successSkills().contains(SKILL));
        assertTrue("检定事件应落库", hasLog(sessionId, "check", null));
    }

    @Test
    public void checkFumbleDoesNotMarkGrowth() {
        startRunning();
        long sessionId = activeSessionId();
        setCard(sessionId, PLAYER, cardWithSkill(SKILL, 60));
        service = buildService(new FakeRandom(100), collectingPublisher());

        PlayResult<SessionOutcomes.Check> played = service.check(actor(PLAYER), 0L, SKILL, "", "");

        assertTrue(played.ok());
        assertFalse("大失败不应记成长", member(sessionId, PLAYER).successSkills().contains(SKILL));
    }

    @Test
    public void kpRollingForPlayerIsNoted() {
        startRunning();
        setCard(activeSessionId(), PLAYER, cardWithSkill(SKILL, 60));
        service = buildService(new FakeRandom(30), collectingPublisher());

        PlayResult<SessionOutcomes.Check> played = service.check(actor(KP), PLAYER, SKILL, "", "");

        assertTrue(played.ok());
        assertEquals(PLAYER, played.detail().rollerUserId());
        assertTrue(played.detail().note(), played.detail().note().contains("KP 代掷"));
    }

    @Test
    public void checkRejectsSkillMissingOnCard() {
        startRunning();
        setCard(activeSessionId(), PLAYER, cardWithSkill(SKILL, 60));
        service = buildService(new FakeRandom(30), collectingPublisher());

        PlayResult<SessionOutcomes.Check> played = service.check(actor(PLAYER), 0L, "格斗", "", "");

        assertFalse(played.ok());
        assertEquals(SessionReject.BAD_ARGUMENT, played.result().reject());
        assertTrue(played.result().detail(), played.result().detail().contains("角色卡上没有技能"));
    }

    @Test
    public void bonusDiceNoteExplainsTensChoice() {
        startRunning();
        setCard(activeSessionId(), PLAYER, cardWithSkill(SKILL, 60));
        // 百分骰 45（十位 4、个位 5），奖励骰十位面 2 → 十位 1，取较小值 1 → 15。
        service = buildService(new FakeRandom(45, 2), collectingPublisher());

        PlayResult<SessionOutcomes.Check> played = service.check(actor(PLAYER), 0L, SKILL, "bp", "1");

        assertTrue(played.ok());
        assertEquals(15, played.detail().rolled());
        assertTrue(played.detail().note(), played.detail().note().contains("奖励骰"));
    }

    @Test
    public void sanLossReducesSan() {
        startRunning();
        long sessionId = activeSessionId();
        setCard(sessionId, PLAYER, CardData.empty().withSan(50));
        // 百分骰 10 ≤ 50：成功，成功损失为常数 1。
        service = buildService(new FakeRandom(10), collectingPublisher());

        PlayResult<SessionOutcomes.San> played = service.san(actor(KP), PLAYER, "1/1D6");

        assertTrue("理智检定应成功：" + played.result().detail(), played.ok());
        assertEquals(50, played.detail().sanBefore());
        assertEquals(49, played.detail().sanAfter());
        assertEquals(1, played.detail().loss());
        assertFalse(played.detail().permanent());
        assertEquals(49, member(sessionId, PLAYER).card().san());
        assertTrue(hasLog(sessionId, "san", null));
    }

    @Test
    public void sanZeroMeansPermanentMadnessAndLeavesSession() {
        startRunning();
        long sessionId = activeSessionId();
        setCard(sessionId, PLAYER, CardData.empty().withSan(5));
        // 百分骰 90 > 5：失败；失败损失 1D6 面 6 → 6 点，理智归零。
        service = buildService(new FakeRandom(90, 6), collectingPublisher());

        PlayResult<SessionOutcomes.San> played = service.san(actor(KP), PLAYER, "1/1D6");

        assertTrue("理智检定应执行：" + played.result().detail(), played.ok());
        assertTrue(played.detail().permanent());
        assertEquals(0, played.detail().sanAfter());
        MemberRow updated = member(sessionId, PLAYER);
        assertEquals(0, updated.card().san());
        assertTrue("理智归零应退场", updated.leftAt() > 0L);
        assertFalse(service.readSnapshot(GROUP).insideMember(PLAYER));
    }

    @Test
    public void opposedPicksWinner() {
        startRunning();
        long sessionId = activeSessionId();
        setCard(sessionId, PLAYER, cardWithSkill(SKILL, 70));
        // 左 20（成功）、右 80（失败）。
        service = buildService(new FakeRandom(20, 80), collectingPublisher());

        PlayResult<SessionOutcomes.Opposed> played = service.opposed(actor(KP), PLAYER, SKILL, 0L, "",
                60);

        assertTrue("对抗应成功：" + played.result().detail(), played.ok());
        assertTrue(played.detail().decided());
        assertEquals(PLAYER, played.detail().winnerUserId());
        assertEquals("目标值", played.detail().rightLabel());
        assertTrue(hasLog(sessionId, "opposed", null));
    }

    @Test
    public void adjustClampsSkillAndLogs() {
        startRunning();
        long sessionId = activeSessionId();
        setCard(sessionId, PLAYER, cardWithSkill(SKILL, 60));

        PlayResult<SessionOutcomes.Adjust> played = service.adjust(actor(KP), PLAYER, SKILL, "+5");

        assertTrue("调整应成功：" + played.result().detail(), played.ok());
        assertEquals(60, played.detail().from());
        assertEquals(65, played.detail().to());
        assertEquals(65, member(sessionId, PLAYER).card().skill(SKILL));
        assertTrue(hasLog(sessionId, "adjust", null));
    }

    @Test
    public void adjustRejectsOutOfRange() {
        startRunning();
        setCard(activeSessionId(), PLAYER, cardWithSkill(SKILL, 95));

        PlayResult<SessionOutcomes.Adjust> played = service.adjust(actor(KP), PLAYER, SKILL, "+10");

        assertFalse(played.ok());
        assertEquals(SessionReject.BAD_ARGUMENT, played.result().reject());
        assertTrue(played.result().detail(), played.result().detail().contains("技能值需在 0～99"));
    }

    @Test
    public void inviteAddsNewMemberDuringRunning() {
        startRunning();
        long sessionId = activeSessionId();

        SessionResult result = service.invite(actor(KP), OTHER, CardData.empty());

        assertTrue("邀请应成功：" + result.detail(), result.ok());
        assertNotNull(member(sessionId, OTHER));
        assertTrue(hasLog(sessionId, "invite", String.valueOf(OTHER)));
    }

    @Test
    public void takeoverMarksMember() {
        startRunning();
        long sessionId = activeSessionId();

        SessionResult result = service.takeover(actor(KP), PLAYER);

        assertTrue("接管应成功：" + result.detail(), result.ok());
        assertTrue(member(sessionId, PLAYER).takenOver());
        assertTrue(hasLog(sessionId, "take", null));
    }

    @Test
    public void skipRecordsReason() {
        startRunning();
        long sessionId = activeSessionId();

        SessionResult result = service.skip(actor(KP), PLAYER, "挂机太久");

        assertTrue("跳过应成功：" + result.detail(), result.ok());
        // 日志要能定位到被跳过的人，所以带上用户号与原因。
        String content = skipLogContent(sessionId);
        assertTrue("跳过日志应带用户号：" + content, content.startsWith(String.valueOf(PLAYER)));
        assertTrue("跳过日志应带原因：" + content, content.contains("挂机太久"));
    }

    @Test
    public void changeScriptOnlyInRecruiting() {
        startSession();
        long sessionId = activeSessionId();

        SessionResult result = service.changeScript(actor(KP), SCRIPT_ID);

        assertTrue("招募期换剧本应成功：" + result.detail(), result.ok());
        assertEquals(SCRIPT_ID, row(sessionId).scriptId());
    }

    @Test
    public void changeScriptRejectedAfterBegin() {
        startRunning();

        SessionResult result = service.changeScript(actor(KP), SCRIPT_ID);

        assertFalse(result.ok());
        assertEquals(SessionReject.STATE_UNAVAILABLE, result.reject());
        assertTrue(result.detail(), result.detail().contains("开局后不能更换剧本"));
    }

    @Test
    public void checkRejectsKpAsRoller() {
        startRunning();
        PlayResult<SessionOutcomes.Check> played = service.check(actor(KP), 0L, SKILL, "", "");
        assertFalse(played.ok());
        assertEquals(SessionReject.MEMBER_STATE, played.result().reject());
        assertTrue(played.result().detail(), played.result().detail().contains("KP 没有角色卡"));
    }

    @Test
    public void clueOutcomeCarriesUnlockedClueName() {
        startRunning();
        Scene second = script().sceneAt(1);
        PlayResult<SessionOutcomes.Scene> played = service.scene(actor(KP), second.title());
        assertTrue(played.ok());
        for (String clueKey : played.detail().newClueKeys()) {
            Clue clue = script().clueById(clueKey);
            assertNotNull("剧本应定义该线索：" + clueKey, clue);
            assertFalse(clue.unlockedText().trim().isEmpty());
        }
    }

    private String skipLogContent(long sessionId) {
        for (LogRow row : store.listLogs(sessionId, 200, 0)) {
            if ("skip".equals(row.eventType())) {
                return row.content();
            }
        }
        return "";
    }

    private boolean hasLog(long sessionId, String eventType, String content) {
        List<LogRow> rows = store.listLogs(sessionId, 200, 0);
        for (LogRow row : rows) {
            if (!eventType.equals(row.eventType())) {
                continue;
            }
            if (content == null || content.equals(row.content())) {
                return true;
            }
        }
        return false;
    }
}
