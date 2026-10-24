package org.moboxlab.mbb.trpg.session;

import org.junit.Test;
import org.moboxlab.mbb.trpg.ai.AiFailureOutcome;
import org.moboxlab.mbb.trpg.ai.TrpgAiHostService;
import org.moboxlab.mbb.trpg.config.TrpgConfig;
import org.moboxlab.mbb.trpg.rule.DefaultRandomSource;
import org.moboxlab.mbb.trpg.store.CardData;
import org.moboxlab.mbb.trpg.store.LogRow;
import org.moboxlab.mbb.trpg.store.MemberRow;
import org.moboxlab.mbb.trpg.store.SessionRow;
import org.moboxlab.mbb.trpg.testutil.FakeRandom;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link SessionService} 生命周期契约测试：T1–T6、判定顺序、群锁、二次确认、惰性过期与事件时机。
 */
public class SessionServiceTest extends SessionTestFixture {

    private static final long HOUR = 3600000L;
    private static final long DAY = 24L * HOUR;

    @Test
    public void startCreatesRecruitingSessionWithKpMemberAndCreateLog() {
        SessionResult result = start();
        assertTrue(result.detail(), result.ok());
        SessionSnapshot snapshot = result.snapshot();
        assertEquals(SessionState.RECRUITING, snapshot.state());
        assertEquals(KP, snapshot.kpUserId());
        assertEquals(SCRIPT_ID, snapshot.scriptId());
        assertEquals("d100", snapshot.ruleSetId());
        assertEquals(NOW, snapshot.lastActivityAt());
        assertEquals(1, snapshot.members().size());
        assertNotNull(snapshot.member(KP));
        assertTrue(snapshot.member(KP).inSession());
        assertEquals("空角色卡的技能值应为 0", 0, snapshot.member(KP).card().skill(SKILL));

        long sessionId = snapshot.sessionId();
        assertEquals(1, store.loadMembers(sessionId).size());
        List<String> types = eventTypes(sessionId);
        assertEquals(1, types.size());
        assertEquals("create", types.get(0));
        assertEquals(SCRIPT_ID, store.listLogs(sessionId, 10, 0).get(0).content());
    }

    @Test
    public void secondStartInSameGroupIsRejected() {
        startSession();
        SessionResult again = start();
        assertTrue(again.rejected());
        assertEquals(SessionReject.STATE_UNAVAILABLE, again.reject());
        assertEquals(1, store.loadActiveSessions().size());
    }

    @Test
    public void startWithUnknownScriptIsRejected() {
        SessionResult result = service.start(actor(KP), "no-such-script");
        assertTrue(result.rejected());
        assertEquals(SessionReject.BAD_ARGUMENT, result.reject());
        assertTrue(store.loadActiveSessions().isEmpty());
    }

    @Test
    public void startOutsideGroupIsRejectedBeforeTouchingStorage() {
        SessionResult result = service.start(new SessionActor(KP, SessionActor.PRIVATE_GROUP_ID, false),
                SCRIPT_ID);
        assertTrue(result.rejected());
        assertEquals(SessionReject.NOT_IN_GROUP, result.reject());
        assertTrue(store.loadActiveSessions().isEmpty());
    }

    @Test
    public void joinAddsMemberAndWritesJoinLog() {
        startSession();
        SessionResult joined = service.join(actor(PLAYER), cardWithSkill(SKILL, 40));
        assertTrue(joined.detail(), joined.ok());
        long sessionId = activeSessionId();
        assertEquals(2, store.loadMembers(sessionId).size());
        assertEquals(40, member(sessionId, PLAYER).card().skill(SKILL));
        List<String> types = eventTypes(sessionId);
        assertEquals(2, types.size());
        assertEquals("join", types.get(1));
    }

    @Test
    public void duplicateJoinIsRejectedAsMemberState() {
        startJoining();
        SessionResult again = service.join(actor(PLAYER), CardData.empty());
        assertTrue(again.rejected());
        assertEquals(SessionReject.MEMBER_STATE, again.reject());
        assertEquals(2, store.loadMembers(activeSessionId()).size());
    }

    @Test
    public void joinAfterBeginIsRejected() {
        startRunning();
        SessionResult late = service.join(actor(OTHER), CardData.empty());
        assertTrue(late.rejected());
        assertEquals(SessionReject.STATE_UNAVAILABLE, late.reject());
    }

    @Test
    public void leaveKeepsCardAndMarksLeftAt() {
        startJoining();
        long sessionId = activeSessionId();
        setCard(sessionId, PLAYER, cardWithSkill(SKILL, 42));
        clock.advance(5000L);
        SessionResult left = service.leave(actor(PLAYER));
        assertTrue(left.detail(), left.ok());
        MemberRow member = member(sessionId, PLAYER);
        assertEquals(clock.nowMillis(), member.leftAt());
        assertEquals(42, member.card().skill(SKILL));
        assertFalse(left.snapshot().member(PLAYER).inSession());
        assertEquals("leave", eventTypes(sessionId).get(2));
    }

    @Test
    public void rejoinAfterLeaveClearsLeftAtAndKeepsCard() {
        startJoining();
        long sessionId = activeSessionId();
        setCard(sessionId, PLAYER, cardWithSkill(SKILL, 42));
        assertTrue(service.leave(actor(PLAYER)).ok());
        clock.advance(5000L);
        SessionResult rejoined = service.join(actor(PLAYER), CardData.empty());
        assertTrue(rejoined.detail(), rejoined.ok());
        MemberRow member = member(sessionId, PLAYER);
        assertEquals(0L, member.leftAt());
        assertEquals(42, member.card().skill(SKILL));
        assertTrue(rejoined.snapshot().member(PLAYER).inSession());
    }

    @Test
    public void kpCannotLeave() {
        startJoining();
        SessionResult left = service.leave(actor(KP));
        assertTrue(left.rejected());
        assertEquals(SessionReject.MEMBER_STATE, left.reject());
        assertTrue(left.detail().contains("KP"));
    }

    @Test
    public void leaveWithoutMembershipIsRejected() {
        startRunning();
        SessionResult left = service.leave(actor(OTHER));
        assertTrue(left.rejected());
        assertEquals(SessionReject.MEMBER_STATE, left.reject());
    }

    @Test
    public void beginRequiresKp() {
        startJoining();
        SessionResult begun = service.begin(actor(PLAYER));
        assertTrue(begun.rejected());
        assertEquals(SessionReject.NO_PERMISSION, begun.reject());
        assertEquals(SessionState.RECRUITING, row(activeSessionId()).state());
    }

    @Test
    public void beginRequiresAnotherMember() {
        startSession();
        SessionResult begun = service.begin(actor(KP));
        assertTrue(begun.rejected());
        assertEquals(SessionReject.STATE_UNAVAILABLE, begun.reject());
        assertTrue(begun.detail().contains("至少"));
    }

    @Test
    public void beginSwitchesToFirstSceneAndWritesBeginLog() {
        startJoining();
        SessionResult begun = service.begin(actor(KP));
        assertTrue(begun.detail(), begun.ok());
        assertEquals(SessionState.RUNNING, begun.snapshot().state());
        assertEquals(0, begun.snapshot().sceneIndex());
        assertEquals(firstSceneTitle(), begun.snapshot().sceneTitle());
        long sessionId = activeSessionId();
        assertEquals(NOW, row(sessionId).startedAt());
        assertEquals("begin", eventTypes(sessionId).get(2));
    }

    @Test
    public void endNeedsSecondConfirmationWithinWindow() {
        startRunning();
        long sessionId = activeSessionId();
        SessionResult first = service.end(actor(KP));
        assertTrue(first.ok());
        assertTrue(first.confirmationPending());
        assertEquals(SessionState.RUNNING, row(sessionId).state());
        assertEquals("首次结束只应提示确认，不写结束日志", 3, eventTypes(sessionId).size());
    }

    @Test
    public void secondEndConfirmationFinishesAndSettlesSummary() {
        startRunning();
        long sessionId = activeSessionId();
        assertTrue(service.end(actor(KP)).confirmationPending());
        clock.advance(1000L);
        SessionResult ended = service.end(actor(KP));
        assertTrue(ended.detail(), ended.ok());
        assertEquals("finished", ended.endReason());
        assertFalse(ended.confirmationPending());
        SessionRow finished = row(sessionId);
        assertEquals(SessionState.ENDED, finished.state());
        assertEquals("finished", finished.endReason());
        assertTrue(finished.growthSettled());
        assertFalse(finished.summary().isEmpty());
        assertNull(store.findActiveByGroup(GROUP));

        List<SessionEvent> endedEvents = eventsOf(SessionEvent.Type.ENDED);
        assertEquals(1, endedEvents.size());
        assertEquals("finished", endedEvents.get(0).endReason());
    }

    @Test
    public void botAdminCannotEndSession() {
        startRunning();
        long sessionId = activeSessionId();
        SessionActor botOnly = new SessionActor(OTHER, GROUP, false, true);
        SessionResult denied = service.end(botOnly);
        assertTrue(denied.rejected());
        assertEquals(SessionReject.NO_PERMISSION, denied.reject());
        assertEquals(SessionState.RUNNING, row(sessionId).state());
    }

    @Test
    public void adminEndIsForcedAndSettlesGrowth() {
        startRunning();
        long sessionId = activeSessionId();
        assertTrue(service.end(admin(OTHER)).confirmationPending());
        clock.advance(1000L);
        SessionResult ended = service.end(admin(OTHER));
        assertTrue(ended.detail(), ended.ok());
        assertEquals("forced", ended.endReason());
        assertTrue(row(sessionId).growthSettled());
    }

    @Test
    public void cancellingRecruitingDoesNotSettleGrowth() {
        startJoining();
        long sessionId = activeSessionId();
        setSuccessSkills(sessionId, PLAYER, list(SKILL));
        assertTrue(service.end(actor(KP)).confirmationPending());
        clock.advance(1000L);
        SessionResult ended = service.end(actor(KP));
        assertTrue(ended.detail(), ended.ok());
        assertEquals("cancelled", ended.endReason());
        assertTrue(ended.growth().isEmpty());
        SessionRow cancelled = row(sessionId);
        assertEquals("cancelled", cancelled.endReason());
        assertTrue(cancelled.growthSettled());
        assertFalse(cancelled.summary().isEmpty());
        assertEquals("招募期取消不结算成长，成功技能应保留",
                1, member(sessionId, PLAYER).successSkills().size());
    }

    @Test
    public void endCasFailureKeepsSessionRunning() {
        startRunning();
        long sessionId = activeSessionId();
        storage.failUpdateContaining("SET state = ?, startedAt = ?", 1);
        assertTrue(service.end(actor(KP)).confirmationPending());
        clock.advance(1000L);

        SessionResult failed = service.end(actor(KP));
        assertTrue("CAS 返回 -1 时按存储失败上报", failed.storageFailed());
        SessionRow current = row(sessionId);
        assertEquals(SessionState.RUNNING, current.state());
        assertFalse(current.growthSettled());
        assertEquals("未结束的局不参与补做", 0, service.settlePending());
    }

    @Test
    public void growthWriteFailureDefersSettlementUntilNextStartup() {
        service = buildService(new FakeRandom(95, 7, 95, 7), collectingPublisher());
        startRunning();
        long sessionId = activeSessionId();
        setCard(sessionId, PLAYER, cardWithSkill(SKILL, 60));
        setSuccessSkills(sessionId, PLAYER, list(SKILL));
        storage.failUpdateContaining("SET cardJSON = ?", 1);

        assertTrue(service.end(actor(KP)).confirmationPending());
        clock.advance(1000L);
        SessionResult ended = service.end(actor(KP));
        assertTrue(ended.detail(), ended.ok());
        SessionRow archived = row(sessionId);
        assertEquals(SessionState.ENDED, archived.state());
        assertFalse("成长未写完不推进 growthSettled", archived.growthSettled());

        assertEquals(1, service.settlePending());
        assertTrue(row(sessionId).growthSettled());
        assertEquals(67, member(sessionId, PLAYER).card().skill(SKILL));
        assertTrue(member(sessionId, PLAYER).successSkills().isEmpty());
    }

    @Test
    public void endConfirmationWindowIsPerInitiator() {
        startRunning();
        long sessionId = activeSessionId();
        assertTrue(service.end(actor(KP)).confirmationPending());
        clock.advance(1000L);
        assertTrue("管理员的确认窗口与 KP 互不合并",
                service.end(admin(OTHER)).confirmationPending());
        clock.advance(1000L);

        SessionResult forced = service.end(admin(OTHER));
        assertTrue(forced.detail(), forced.ok());
        assertEquals("forced", forced.endReason());
        assertEquals(SessionState.ENDED, row(sessionId).state());
    }

    @Test
    public void confirmationExpiresAfterWindow() {
        startSession();
        assertTrue(service.end(actor(KP)).confirmationPending());
        clock.advance(31000L);
        SessionResult again = service.end(actor(KP));
        assertTrue(again.confirmationPending());
        assertEquals(SessionState.RECRUITING, row(activeSessionId()).state());
    }

    @Test
    public void startCooldownBlocksImmediateRestart() {
        startSession();
        assertTrue(service.end(actor(KP)).confirmationPending());
        clock.advance(1000L);
        assertTrue(service.end(actor(KP)).ok());
        SessionResult blocked = start();
        assertTrue(blocked.rejected());
        assertEquals(SessionReject.COOLING, blocked.reject());

        clock.advance(30001L);
        clock.advance(1000L);
        SessionResult allowed = start();
        assertTrue(allowed.detail(), allowed.ok());
    }

    @Test
    public void busyWhenAnotherThreadHoldsTheGroupLock() throws Exception {
        final CountDownLatch held = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(new Runnable() {
            @Override
            public void run() {
                locks.tryLock(GROUP);
                held.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    locks.unlock(GROUP);
                }
            }
        });
        holder.start();
        assertTrue(held.await(5, TimeUnit.SECONDS));
        try {
            SessionResult result = start();
            assertTrue(result.rejected());
            assertEquals(SessionReject.BUSY, result.reject());
            assertTrue(store.loadActiveSessions().isEmpty());
        } finally {
            release.countDown();
            holder.join(5000L);
        }
    }

    @Test
    public void lazyExpiryCancelsRecruitingAfter24Hours() {
        startSession();
        long sessionId = activeSessionId();
        clock.advance(25L * HOUR);
        SessionResult status = service.status(actor(PLAYER));
        assertTrue(status.detail(), status.ok());
        assertEquals(SessionState.NONE, status.snapshot().state());
        SessionRow expired = row(sessionId);
        assertEquals(SessionState.ENDED, expired.state());
        assertEquals("expired", expired.endReason());
        assertTrue(expired.growthSettled());
        assertEquals(1, eventsOf(SessionEvent.Type.ENDED).size());
    }

    @Test
    public void lazyExpiryFinishesRunningAfter7DaysAndSettlesGrowth() {
        startRunning();
        long sessionId = activeSessionId();
        setCard(sessionId, PLAYER, cardWithSkill(SKILL, 60));
        setSuccessSkills(sessionId, PLAYER, list(SKILL));
        service = buildService(new FakeRandom(95, 7), collectingPublisher());

        clock.advance(8L * DAY);
        SessionResult status = service.status(actor(OTHER));
        assertTrue(status.detail(), status.ok());
        assertEquals(SessionState.NONE, status.snapshot().state());

        SessionRow expired = row(sessionId);
        assertEquals("expired", expired.endReason());
        assertTrue(expired.growthSettled());
        assertEquals(67, member(sessionId, PLAYER).card().skill(SKILL));
        assertTrue("成长结算后应清空成功技能", member(sessionId, PLAYER).successSkills().isEmpty());
        List<SessionEvent> endedEvents = eventsOf(SessionEvent.Type.ENDED);
        assertEquals(1, endedEvents.size());
        assertEquals(1, endedEvents.get(0).growth().size());
        GrowthOutcome outcome = endedEvents.get(0).growth().get(0);
        assertEquals(PLAYER, outcome.userId());
        assertEquals(60, outcome.from());
        assertEquals(67, outcome.to());
        assertEquals(7, outcome.rolled());
        assertEquals(7, outcome.delta());
    }

    @Test
    public void kpLeftArchivesRunningSession() {
        startRunning();
        long sessionId = activeSessionId();
        SessionResult result = service.kpLeft(GROUP, KP);
        assertTrue(result.detail(), result.ok());
        assertEquals("kp_left", result.endReason());
        SessionRow archived = row(sessionId);
        assertEquals(SessionState.ENDED, archived.state());
        assertEquals("kp_left", archived.endReason());
        assertTrue(archived.growthSettled());
        assertNull(store.findActiveByGroup(GROUP));
    }

    @Test
    public void kpLeftIgnoresOtherUsers() {
        startRunning();
        SessionResult result = service.kpLeft(GROUP, PLAYER);
        assertTrue(result.ok());
        assertEquals("", result.endReason());
        assertTrue(result.detail().contains("KP"));
        assertNotNull(store.findActiveByGroup(GROUP));
    }

    @Test
    public void eventsArePublishedAfterTheGroupLockIsReleased() {
        final List<Boolean> lockedAtPublish = new ArrayList<Boolean>();
        service = buildService(DefaultRandomSource.INSTANCE,
                new SessionEventPublisher() {
                    @Override
                    public void publish(SessionEvent event) {
                        events.add(event);
                        lockedAtPublish.add(Boolean.valueOf(locks.isLocked(GROUP)));
                    }
                });
        startRunning();
        assertTrue(service.end(actor(KP)).confirmationPending());
        clock.advance(1000L);
        assertTrue(service.end(actor(KP)).ok());
        assertFalse("结束事件必须在锁外发布", lockedAtPublish.isEmpty());
        for (Boolean locked : lockedAtPublish) {
            assertFalse("发布事件时不应持有群锁", locked.booleanValue());
        }
    }

    @Test
    public void touchActivityIsThrottledAndOnlyCountsMembers() {
        startRunning();
        clock.advance(120000L);
        assertTrue(service.touchActivity(GROUP, PLAYER));
        assertFalse("节流窗口内不重复写", service.touchActivity(GROUP, PLAYER));
        assertFalse("非局内成员不计活动", service.touchActivity(GROUP, OTHER));
        assertFalse("局号或用户号非法直接返回 false", service.touchActivity(0L, PLAYER));
        clock.advance(61000L);
        assertTrue(service.touchActivity(GROUP, PLAYER));
        assertEquals(clock.nowMillis(), row(activeSessionId()).lastActivityAt());
    }

    @Test
    public void touchActivityIgnoresLeftMembers() {
        startJoining();
        service.leave(actor(PLAYER));
        clock.advance(120000L);
        assertFalse(service.touchActivity(GROUP, PLAYER));
    }

    @Test
    public void aiToggleFollowsPermissionMatrix() {
        startJoining();
        SessionResult playerOn = service.aiState(actor(PLAYER), "on");
        assertTrue(playerOn.rejected());
        assertEquals(SessionReject.NO_PERMISSION, playerOn.reject());

        SessionResult adminOn = service.aiState(admin(OTHER), "on");
        assertTrue("群管理员不能开 AI", adminOn.rejected());
        assertEquals(SessionReject.NO_PERMISSION, adminOn.reject());

        // 全局开关关闭时 ai on 按「当前状态不可用」拒绝，且不写 aiEnabled（§9.2）。
        SessionResult kpOn = service.aiState(actor(KP), "on");
        assertTrue(kpOn.rejected());
        assertEquals(SessionReject.STATE_UNAVAILABLE, kpOn.reject());
        assertTrue(kpOn.detail(), kpOn.detail().contains("未在配置中启用"));
        assertFalse("全局关不写 aiEnabled", row(activeSessionId()).aiEnabled());

        clock.advance(2000L);
        SessionResult adminOff = service.aiState(admin(OTHER), "off");
        assertTrue(adminOff.detail(), adminOff.ok());
        assertTrue(adminOff.detail(), adminOff.detail().contains("保持关闭"));
        assertFalse(row(activeSessionId()).aiEnabled());

        clock.advance(2000L);
        SessionResult kpOff = service.aiState(botAdmin(OTHER), "off");
        assertTrue(kpOff.ok());
        assertFalse(row(activeSessionId()).aiEnabled());
    }

    @Test
    public void aiOnWithGlobalSwitchWritesStateAndClearsSuspension() {
        startRunning();
        Map<String, String> raw = new LinkedHashMap<String, String>();
        raw.put("aiHost.enabled", "true");
        TrpgConfig enabled = TrpgConfig.fromValues(raw);
        SessionService stubbed = new SessionService(store, catalog, clock, DefaultRandomSource.INSTANCE,
                enabled, collectingPublisher(), null, locks, new CooldownRegistry(), new ErrorDedupe(),
                new TrpgAiHostService(enabled));
        store.casAiState(activeSessionId(), SessionState.RUNNING, false,
                clock.nowMillis() + 600000L, 2, clock.nowMillis());

        SessionResult result = stubbed.aiState(actor(KP), "on");

        assertTrue(result.detail(), result.ok());
        assertEquals(TrpgAiHostService.DETAIL_RESUMED, result.detail());
        assertTrue("ai on 写 aiEnabled", row(activeSessionId()).aiEnabled());
        assertEquals(0L, row(activeSessionId()).aiSuspendedUntil());
        assertEquals(0, row(activeSessionId()).aiFailStreak());

        clock.advance(2000L);
        SessionResult off = stubbed.aiState(actor(KP), "off");
        assertTrue(off.ok());
        assertEquals(TrpgAiHostService.DETAIL_OFF, off.detail());
        assertFalse(row(activeSessionId()).aiEnabled());
    }

    @Test
    public void aiOnWithoutSuspensionKeepsEnabledWordingEvenWithStreak() {
        startRunning();
        Map<String, String> raw = new LinkedHashMap<String, String>();
        raw.put("aiHost.enabled", "true");
        TrpgConfig enabled = TrpgConfig.fromValues(raw);
        SessionService stubbed = new SessionService(store, catalog, clock, DefaultRandomSource.INSTANCE,
                enabled, collectingPublisher(), null, locks, new CooldownRegistry(), new ErrorDedupe(),
                new TrpgAiHostService(enabled));
        // 未挂起、但已累计 1 次失败：不应说「已清除挂起」。
        store.casAiState(activeSessionId(), SessionState.RUNNING, false, 0L, 1, clock.nowMillis());

        SessionResult result = stubbed.aiState(actor(KP), "on");

        assertTrue(result.detail(), result.ok());
        assertEquals(TrpgAiHostService.DETAIL_ENABLED, result.detail());
        assertEquals(0, row(activeSessionId()).aiFailStreak());
    }

    @Test
    public void playerActionTruncatesByCodePointWithoutSplittingEmoji() {
        String emoji = "\uD83D\uDE00";
        String longText = emoji + emoji + emoji;
        assertEquals(emoji + emoji, SessionService.truncateByCodePoint(longText, 2));
        assertEquals(longText, SessionService.truncateByCodePoint(longText, 3));
        assertEquals("", SessionService.truncateByCodePoint(null, 2));
    }

    @Test
    public void recordFailureSuspendsAtThresholdAndSuccessClearsStreak() {
        startRunning();
        Map<String, String> raw = new LinkedHashMap<String, String>();
        raw.put("aiHost.enabled", "true");
        TrpgConfig enabled = TrpgConfig.fromValues(raw);
        SessionService stubbed = new SessionService(store, catalog, clock, DefaultRandomSource.INSTANCE,
                enabled, collectingPublisher(), null, locks, new CooldownRegistry(), new ErrorDedupe(),
                new TrpgAiHostService(enabled));
        stubbed.aiState(actor(KP), "on");
        store.casAiState(activeSessionId(), SessionState.RUNNING, true, 0L, 0, clock.nowMillis());

        AiFailureOutcome first = stubbed.recordFailure(GROUP);
        assertTrue(first.available());
        assertEquals(1, first.failStreak());
        assertFalse(first.suspendedNow());

        assertTrue(stubbed.recordSuccess(GROUP));
        assertEquals(0, row(activeSessionId()).aiFailStreak());

        AiFailureOutcome second = stubbed.recordFailure(GROUP);
        assertEquals(1, second.failStreak());
        AiFailureOutcome third = stubbed.recordFailure(GROUP);
        assertEquals(2, third.failStreak());
        assertFalse(third.suspendedNow());
        AiFailureOutcome fourth = stubbed.recordFailure(GROUP);
        assertTrue(fourth.suspendedNow());
        assertTrue(fourth.suspendedUntil() > clock.nowMillis());
        assertEquals(0, row(activeSessionId()).aiFailStreak());
    }

    @Test
    public void aiToggleWithoutSessionIsRejected() {
        SessionResult result = service.aiState(actor(KP), "on");
        assertTrue(result.rejected());
        assertEquals(SessionReject.STATE_UNAVAILABLE, result.reject());
    }

    @Test
    public void statusWithoutSessionReturnsEmptySnapshot() {
        SessionResult status = service.status(actor(PLAYER));
        assertTrue(status.ok());
        assertEquals(SessionState.NONE, status.snapshot().state());
        assertTrue(status.snapshot().members().isEmpty());
    }

    @Test
    public void readSnapshotReflectsActiveSessionAndOtherGroupsAreEmpty() {
        startSession();
        SessionSnapshot snapshot = service.readSnapshot(GROUP);
        assertEquals(SessionState.RECRUITING, snapshot.state());
        assertTrue(snapshot.recovered());
        assertEquals(SessionState.NONE, service.readSnapshot(OTHER_GROUP).state());
        assertEquals(SessionState.NONE, service.readSnapshot(0L).state());
    }

    @Test
    public void summaryUsesFirstEndingWhoseCluesAreAllUnlocked() {
        startRunning();
        long sessionId = activeSessionId();
        assertEquals(2, ending(1).requiredClueIds().size());
        assertEquals(4, ending(0).requiredClueIds().size());
        for (String clueKey : ending(1).requiredClueIds()) {
            unlockClue(sessionId, clueKey);
            clock.advance(1L);
        }
        assertTrue(service.end(actor(KP)).confirmationPending());
        clock.advance(1000L);
        SessionResult ended = service.end(actor(KP));
        assertTrue(ended.detail(), ended.ok());
        String summary = row(sessionId).summary();
        assertTrue("摘要应命中第二个结局：" + summary, summary.startsWith(ending(1).title()));
        assertFalse(summary.length() > SummaryResolver.SUMMARY_MAX_CHARS);
    }

    @Test
    public void summaryFallsBackToEndingWithoutRequiredClues() {
        startRunning();
        long sessionId = activeSessionId();
        assertTrue(service.end(actor(KP)).confirmationPending());
        clock.advance(1000L);
        assertTrue(service.end(actor(KP)).ok());
        String summary = row(sessionId).summary();
        assertEquals(0, ending(2).requiredClueIds().size());
        assertTrue("没有解锁线索时应命中不要求线索的结局：" + summary,
                summary.startsWith(ending(2).title()));
    }

    @Test
    public void endWithoutSessionIsRejected() {
        SessionResult result = service.end(actor(KP));
        assertTrue(result.rejected());
        assertEquals(SessionReject.STATE_UNAVAILABLE, result.reject());
    }

    @Test
    public void rejectionDedupeSuppressesRepeatedSameError() {
        startSession();
        SessionResult first = service.status(actor(PLAYER));
        assertTrue(first.ok());
        clock.advance(2000L);
        SessionResult second = service.status(actor(PLAYER));
        assertTrue(second.ok());
        SessionResult blocked = service.start(actor(KP), SCRIPT_ID);
        assertTrue(blocked.rejected());
        assertFalse("首次同类拒绝应正常回复", blocked.replySuppressed());
        clock.advance(2000L);
        SessionResult blockedAgain = service.start(actor(KP), SCRIPT_ID);
        assertTrue(blockedAgain.rejected());
        assertTrue("10 秒窗口内同类拒绝应抑制回复", blockedAgain.replySuppressed());
    }

    /** 日志按 seq 升序返回，便于按发生顺序断言。 */
    private List<String> eventTypes(long sessionId) {
        List<LogRow> logs = store.listLogs(sessionId, 50, 0);
        List<String> types = new ArrayList<String>();
        for (int index = logs.size() - 1; index >= 0; index--) {
            types.add(logs.get(index).eventType());
        }
        return types;
    }

    private List<SessionEvent> eventsOf(SessionEvent.Type type) {
        List<SessionEvent> matched = new ArrayList<SessionEvent>();
        for (SessionEvent event : events) {
            if (event.type() == type) {
                matched.add(event);
            }
        }
        return matched;
    }
}
