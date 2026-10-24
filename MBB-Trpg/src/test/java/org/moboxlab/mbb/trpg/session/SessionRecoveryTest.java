package org.moboxlab.mbb.trpg.session;

import org.junit.Test;
import org.moboxlab.mbb.trpg.rule.DefaultRandomSource;
import org.moboxlab.mbb.trpg.store.LogRow;
import org.moboxlab.mbb.trpg.store.MemberRow;
import org.moboxlab.mbb.trpg.store.SessionRow;
import org.moboxlab.mbb.trpg.testutil.FakeRandom;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link SessionService} 启动恢复与补做契约测试：§5.7 恢复序列、T8 恢复确认、F8 未结算补做。
 */
public class SessionRecoveryTest extends SessionTestFixture {

    @Test
    public void recoverLoadsRunningSessionAsPendingRecovery() {
        startRunning();
        long sessionId = activeSessionId();
        events.clear();
        service = buildService(DefaultRandomSource.INSTANCE,
                collectingPublisher());

        List<SessionSnapshot> loaded = service.recover();
        assertEquals(1, loaded.size());
        SessionSnapshot snapshot = loaded.get(0);
        assertEquals(sessionId, snapshot.sessionId());
        assertEquals(SessionState.RUNNING, snapshot.state());
        assertFalse("重启后需 KP 确认恢复", snapshot.recovered());
        assertEquals(1, events.size());
        assertEquals(SessionEvent.Type.RECOVERED, events.get(0).type());
        assertEquals(sessionId, events.get(0).snapshot().sessionId());

        assertFalse(service.readSnapshot(GROUP).recovered());
    }

    @Test
    public void recoverDoesNotPublishRecoveryPromptForRecruitingSession() {
        startSession();
        events.clear();
        service = buildService(DefaultRandomSource.INSTANCE,
                collectingPublisher());

        List<SessionSnapshot> loaded = service.recover();
        assertEquals(1, loaded.size());
        assertEquals(SessionState.RECRUITING, loaded.get(0).state());
        assertTrue("招募期的局无需确认恢复", loaded.get(0).recovered());
        assertTrue("招募期的局不发恢复提示", events.isEmpty());
    }

    @Test
    public void recoverArchivesSessionWithoutMembers() {
        startSession();
        long sessionId = activeSessionId();
        assertEquals(1, store.deleteMembers(sessionId));
        events.clear();
        service = buildService(DefaultRandomSource.INSTANCE,
                collectingPublisher());

        assertTrue(service.recover().isEmpty());
        SessionRow archived = row(sessionId);
        assertEquals(SessionState.ENDED, archived.state());
        assertEquals("cancelled", archived.endReason());
        assertTrue(archived.growthSettled());
        assertTrue(events.isEmpty());
    }

    @Test
    public void resumeRequiresKpAndPendingRecovery() {
        startRunning();
        long sessionId = activeSessionId();
        service = buildService(DefaultRandomSource.INSTANCE,
                collectingPublisher());
        service.recover();

        SessionResult playerResume = service.resume(actor(PLAYER));
        assertTrue(playerResume.rejected());
        assertEquals(SessionReject.NO_PERMISSION, playerResume.reject());
        assertFalse(service.readSnapshot(GROUP).recovered());

        SessionResult kpResume = service.resume(actor(KP));
        assertTrue(kpResume.detail(), kpResume.ok());
        assertTrue(service.readSnapshot(GROUP).recovered());
        assertEquals("resume", lastEventType(sessionId));

        SessionResult again = service.resume(actor(KP));
        assertTrue(again.rejected());
        assertEquals(SessionReject.STATE_UNAVAILABLE, again.reject());
    }

    @Test
    public void resumeWithoutRecoveryIsRejected() {
        startRunning();
        service = buildService(DefaultRandomSource.INSTANCE,
                collectingPublisher());
        SessionResult result = service.resume(actor(KP));
        assertTrue(result.rejected());
        assertEquals(SessionReject.STATE_UNAVAILABLE, result.reject());
    }

    @Test
    public void settlePendingFinishesEndedSessionThatMissedSettlement() {
        startRunning();
        long sessionId = activeSessionId();
        setCard(sessionId, PLAYER, cardWithSkill(SKILL, 60));
        setSuccessSkills(sessionId, PLAYER, list(SKILL));
        assertEquals(1, store.casTransition(sessionId, SessionState.RUNNING, SessionState.ENDED,
                row(sessionId).startedAt(), clock.nowMillis(), "finished", false, "", clock.nowMillis()));
        service = buildService(new FakeRandom(95, 7), collectingPublisher());

        assertEquals(1, service.settlePending());
        SessionRow settled = row(sessionId);
        assertEquals(SessionState.ENDED, settled.state());
        assertTrue(settled.growthSettled());
        assertFalse(settled.summary().isEmpty());
        assertEquals(67, member(sessionId, PLAYER).card().skill(SKILL));
        assertTrue(member(sessionId, PLAYER).successSkills().isEmpty());
    }

    @Test
    public void settlePendingSkipsAlreadySettledSessions() {
        startRunning();
        assertTrue(service.end(actor(KP)).confirmationPending());
        clock.advance(1000L);
        assertTrue(service.end(actor(KP)).ok());
        service = buildService(new FakeRandom(), collectingPublisher());
        assertEquals(0, service.settlePending());
    }

    @Test
    public void settlePendingFallsBackToBuiltinRuleSet() {
        long sessionId = insertSession(GROUP, SessionState.ENDED, "coc7x");
        MemberRow kp = member(sessionId, KP);
        assertEquals(1, store.updateMember(MemberRow.builder()
                .id(kp.id())
                .sessionId(kp.sessionId())
                .userId(kp.userId())
                .card(cardWithSkill(SKILL, 60))
                .successSkills(list(SKILL))
                .joinedAt(kp.joinedAt())
                .leftAt(kp.leftAt())
                .takenOver(kp.takenOver())
                .madnessUntil(kp.madnessUntil())
                .createTime(kp.createTime())
                .updateTime(clock.nowMillis())
                .build()));
        service = buildService(new FakeRandom(95, 7), collectingPublisher());

        assertEquals(1, service.settlePending());
        assertTrue(row(sessionId).growthSettled());
        assertEquals(67, member(sessionId, KP).card().skill(SKILL));
    }

    @Test
    public void recoverFallsBackToBuiltinRuleSetForUnknownIds() {
        long sessionId = insertSession(GROUP, SessionState.RUNNING, "coc7x");
        service = buildService(DefaultRandomSource.INSTANCE,
                collectingPublisher());

        List<SessionSnapshot> loaded = service.recover();
        assertEquals(1, loaded.size());
        assertEquals("d100", loaded.get(0).ruleSetId());
        assertEquals("d100", service.readSnapshot(GROUP).ruleSetId());
        // 回退只作用在运行时与内存快照，库内原值保持不变，便于排查配置写错。
        assertEquals("coc7x", row(sessionId).ruleSetId());
        assertEquals(SessionState.RUNNING, loaded.get(0).state());
    }

    @Test
    public void recoverLoadsEveryGroup() {
        startRunning();
        long otherSessionId = insertSession(OTHER_GROUP, SessionState.RUNNING, "d100");
        service = buildService(DefaultRandomSource.INSTANCE,
                collectingPublisher());

        List<SessionSnapshot> loaded = service.recover();
        assertEquals(2, loaded.size());
        List<Long> ids = new ArrayList<Long>();
        for (SessionSnapshot snapshot : loaded) {
            ids.add(Long.valueOf(snapshot.sessionId()));
        }
        assertTrue(ids.contains(Long.valueOf(activeSessionId())));
        assertTrue(ids.contains(Long.valueOf(otherSessionId)));
    }

    @Test
    public void recoverReportsSceneVersionFromLogs() {
        startRunning();
        long sessionId = activeSessionId();
        unlockClue(sessionId, ending(2).requiredClueIds().isEmpty()
                ? "clue-gate-ledger" : ending(2).requiredClueIds().get(0));
        service = buildService(DefaultRandomSource.INSTANCE,
                collectingPublisher());
        List<SessionSnapshot> loaded = service.recover();
        assertEquals(1, loaded.size());
        assertEquals(store.maxLogSeq(sessionId), loaded.get(0).sceneVersion());
    }

    private String lastEventType(long sessionId) {
        List<LogRow> logs = store.listLogs(sessionId, 10, 0);
        assertNotNull(logs);
        assertFalse(logs.isEmpty());
        return logs.get(0).eventType();
    }
}
