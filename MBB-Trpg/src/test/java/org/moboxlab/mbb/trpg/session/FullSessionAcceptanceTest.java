package org.moboxlab.mbb.trpg.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.moboxlab.mbb.trpg.store.SqlTraceStore;
import org.moboxlab.mbb.trpg.testutil.FakeRandom;

/**
 * I2 整局回归：AI 全局关闭时，按剧本从开团跑到收尾，行为与纯工具模式一致。
 *
 * <p>验收要点（docs/MBB-Trpg.md 第 12 节 I2）：`SessionService` 层的 B/C/G/H 代表动作走完整一局；
 * AI 提示数 0；{@code TraceStore} 行数 0。定时器注册属插件装配层，本测试不覆盖（验收记录里
 * I2/I5 因此标「部分」），这里只断言整局没有任何 AI 审计行或 AI 事件。</p>
 */
public class FullSessionAcceptanceTest extends SessionTestFixture {

    @Test
    public void fullSessionRunsWithoutAi() {
        // B–C：开团、入局、开局（角色卡带 SAN，供后面理智检定使用）。
        startSession();
        long sessionId = activeSessionId();
        assertTrue(service.join(actor(PLAYER), cardWithSkill(SKILL, 60).withSan(50)).ok());
        assertTrue(service.begin(actor(KP)).ok());

        // G：一次成功检定，记下成长待结算（固定骰面：检定 D100=30、理智 D100=30、收尾成长 D10=5）。
        // 这里换一份带固定骰面的 service：仅本测试用，冷却/去重状态随之重置，不影响下面的一次性断言。
        service = buildService(new FakeRandom(30, 30, 5), collectingPublisher());
        PlayResult<SessionOutcomes.Check> check = service.check(actor(PLAYER), 0L, SKILL, "", "");
        assertTrue("检定应成功：" + check.result().detail(), check.ok());
        assertTrue("成功技能应进入成长集合",
                member(sessionId, PLAYER).successSkills().contains(SKILL));

        // G：理智检定按 X/Y 扣点（由 KP 发起、指定目标玩家）。
        PlayResult<SessionOutcomes.San> san = service.san(actor(KP), PLAYER, "1/1d3");
        assertTrue("理智检定应成功：" + san.result().detail(), san.ok());

        // H：推进场景与线索。
        PlayResult<SessionOutcomes.Scene> scene = service.scene(actor(KP),
                script().sceneAt(1).title());
        assertTrue("推进场景应成功：" + scene.result().detail(), scene.ok());
        assertFalse("第二个场景应解锁线索", scene.detail().newClueKeys().isEmpty());

        // H：手动调整一条属性。
        PlayResult<SessionOutcomes.Adjust> adjust = service.adjust(actor(KP), PLAYER, "理智", "60");
        assertTrue("调整应成功：" + adjust.result().detail(), adjust.ok());

        // C：二次确认后收尾，进行中的局结算成长。
        assertTrue(service.end(actor(KP)).confirmationPending());
        clock.advance(1000L);
        SessionResult ended = service.end(actor(KP));
        assertTrue("结束应成功：" + ended.detail(), ended.ok());
        assertEquals("finished", ended.endReason());
        assertEquals(SessionState.ENDED, ended.snapshot().state());

        // C5：结算幂等，重复触发不再涨点。
        int before = member(sessionId, PLAYER).cardJson().length();
        assertEquals("已结算的局不应再次进入补做", 0, service.settlePending());
        assertEquals("重复触发不得改写成员行", before, member(sessionId, PLAYER).cardJson().length());

        // I2：没有任何 AI 审计行。
        SqlTraceStore traceStore = new SqlTraceStore(store);
        assertTrue("AI 关闭时不得写入 ai_trace",
                traceStore.recent(GROUP, sessionId, 10).isEmpty());

        // I2：事件里应有收尾事件；恢复事件不在本测试范围（没有重启加载流程，归 E1/E2 覆盖）。
        List<SessionEvent.Type> types = new ArrayList<SessionEvent.Type>();
        for (SessionEvent event : events) {
            types.add(event.type());
        }
        assertTrue("整局应发布收尾事件：" + types, types.contains(SessionEvent.Type.ENDED));

        // 审计与日志按保留策略收敛：收尾后活跃局为空，日志可读。
        assertNotNull("收尾后仍应能读到已归档局的日志", store.listLogs(sessionId, 200, 0));
        assertFalse("收尾后日志应保留", store.listLogs(sessionId, 200, 0).isEmpty());
        assertNull("收尾后本群不再有活跃局", store.findActiveByGroup(GROUP));
    }
}
