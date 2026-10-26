package org.moboxlab.mbb.trpg.session;

import org.junit.Before;
import org.junit.Test;
import org.moboxlab.mbb.trpg.store.TrpgRetentionService;
import org.moboxlab.mbb.trpg.store.TrpgSchemaService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 千次连续指令长跑（规格 §12 J10）。
 *
 * <p>覆盖三件事：群锁缓存条数不超过「最近活跃群」上界并随空闲淘汰（§5.5）；1000 次连续
 * 只读 / 低风险指令后关键表行数不增长、活跃局唯一；两轮各 1000 次后堆占用趋于稳定，
 * 不随轮次持续单调增长。</p>
 *
 * <p>装配真 SQLite（{@link org.moboxlab.mbb.trpg.store.JdbcStorageFake}）+ 真
 * {@link SessionService} + 内存群锁；只走 status、pc 查看角色卡、log 回看、活动刷新这些
 * 不触发 QQ 发送的路径。每两次指令之间推进 61 秒假时钟：既越过 status / pc / log 的
 * 1 秒冷却，也越过活动刷新的 60 秒节流窗，让轮转到的写入路径都真实落到同一行上。</p>
 */
public class ThousandCommandsLoadTest extends SessionTestFixture {

    /** 每轮连续指令次数。 */
    private static final int COMMANDS_PER_ROUND = 1000;
    /** 每两次指令之间推进的假时间：大于 60 秒的活动节流窗。 */
    private static final long STEP_MILLIS = 61_000L;
    /** 两轮 1000 次之后的堆增长上限（32 MB）：GC 时机带来的噪声远大于真实泄漏，阈值刻意放宽。 */
    private static final long HEAP_GROWTH_LIMIT_BYTES = 32L * 1024L * 1024L;
    /** 每次测堆前连续 GC 采样次数，取最小值以压低 GC 时机抖动。 */
    private static final int HEAP_SAMPLE_ROUNDS = 4;
    /** 每次 GC 采样后的短暂等待，给后台回收线程留出收尾时间。 */
    private static final long HEAP_SAMPLE_PAUSE_MILLIS = 60L;
    /** 触达的陌生群数量：用于观察群锁缓存随「最近活跃群」增长而不是随指令次数增长。 */
    private static final int CHURN_GROUPS = 16;

    /** 日志回看是只读路径，冷却与去重独立一份，避免与局面服务的登记互相干扰。 */
    private SessionLogService logService;

    @Before
    public void setUpLogService() {
        logService = new SessionLogService(store, config, clock, new CooldownRegistry(), new ErrorDedupe(),
                null);
    }

    @Test
    public void thousandCommandsKeepOneGroupLockAndFlatTables() {
        startRunning();
        long sessionId = activeSessionId();
        assertEquals("开局后缓存里应只有本群一把锁", 1, locks.size());

        long sessionsBefore = countRows(TrpgSchemaService.TABLE_SESSION);
        long membersBefore = countRows(TrpgSchemaService.TABLE_MEMBER);
        long logsBefore = countRows(TrpgSchemaService.TABLE_TEXT_LOG);
        long tracesBefore = countRows(TrpgSchemaService.TABLE_AI_TRACE);

        runThousandCommands(sessionId, "第一轮");

        // 单群活跃时，群锁缓存的上界就是 1：指令次数增加不应让缓存变大。
        assertEquals("单群 1000 次指令后群锁缓存仍应恰好一把", 1, locks.size());
        assertFalse("指令返回后不得残留持锁", locks.isLocked(GROUP));

        // 活跃局唯一：不会因为长跑产生重复局或把同一局拆成两行。
        assertEquals("活跃局必须唯一", 1, store.loadActiveSessions().size());
        assertEquals(sessionId, store.findActiveByGroup(GROUP).id());
        assertEquals(SessionState.RUNNING, store.findActiveByGroup(GROUP).state());

        // 只读 / 低风险路径不写日志、不写审计、不新增成员：行数应逐项收敛。
        assertEquals("session 表不应新增行", sessionsBefore,
                countRows(TrpgSchemaService.TABLE_SESSION));
        assertEquals("member 表不应新增行", membersBefore,
                countRows(TrpgSchemaService.TABLE_MEMBER));
        assertEquals("text_log 表不因只读指令增长", logsBefore,
                countRows(TrpgSchemaService.TABLE_TEXT_LOG));
        assertEquals("ai_trace 表不因只读指令增长", tracesBefore,
                countRows(TrpgSchemaService.TABLE_AI_TRACE));

        // 保留策略：活跃局不是清理候选，日志与审计也不会被清理改动（行数保持上一步的收敛结果）。
        TrpgRetentionService.Result retention =
                new TrpgRetentionService(store, null, clock).runOnce(clock.nowMillis());
        assertEquals("活跃局不应进入清理候选", 0, retention.candidates());
        assertEquals(0, retention.purged());
        assertEquals(0, retention.traceExpiredRows());
        assertEquals(0, retention.traceTrimmedRows());
        assertFalse("保留策略本轮不应失败", retention.hasFailure());
        assertEquals(logsBefore, countRows(TrpgSchemaService.TABLE_TEXT_LOG));
        assertEquals(tracesBefore, countRows(TrpgSchemaService.TABLE_AI_TRACE));
    }

    @Test
    public void groupLockCacheStaysBoundedAndEvictsIdleGroups() {
        startRunning();
        long sessionId = activeSessionId();

        runThousandCommands(sessionId, "预热");
        assertEquals("单群长跑后缓存仍是一把", 1, locks.size());

        // 再触达一批没有活跃局的陌生群：缓存条数只随「最近触达过的群」增加，不随指令次数增加。
        int touched = 0;
        for (int i = 0; i < CHURN_GROUPS; i++) {
            long groupId = OTHER_GROUP + i;
            SessionResult result = service.status(new SessionActor(KP, groupId, false));
            assertTrue("陌生群 status 应返回空局面：" + result.detail(), result.ok());
            touched++;
            assertTrue("群锁缓存条数 " + locks.size() + " 超过最近触达群数上界 " + (touched + 1),
                    locks.size() <= touched + 1);
        }
        assertTrue("触达 " + CHURN_GROUPS + " 个群后仍应远低于默认容量上限",
                locks.size() <= GroupLockRegistry.DEFAULT_MAX_GROUPS);

        // 空闲超过默认淘汰时间后再次触达：旧锁全部被淘汰，只留刚用到的那把。
        // 注意容量上限（DEFAULT_MAX_GROUPS=256）分支本轮不覆盖：只验证空闲淘汰，见验收记录 J10。
        clock.advance(GroupLockRegistry.DEFAULT_IDLE_MILLIS + 1L);
        long wakeGroup = OTHER_GROUP + 999L;
        assertTrue("空闲后触达应正常返回空局面",
                service.status(new SessionActor(KP, wakeGroup, false)).ok());
        assertEquals("空闲锁应在下一次触达时被淘汰，只剩一把", 1, locks.size());
        assertFalse("旧群锁不应再处于持锁状态", locks.isLocked(GROUP));
        assertFalse("陌生群旧锁不应再处于持锁状态", locks.isLocked(OTHER_GROUP));

        // 淘汰后再跑一千次，缓存仍是一把：说明收敛不是一次性假象。
        runThousandCommands(sessionId, "淘汰后第二轮");
        assertEquals("淘汰后再次长跑，缓存不应重新膨胀", 1, locks.size());
        assertEquals("长跑后活跃局仍唯一", 1, store.loadActiveSessions().size());
    }

    @Test
    public void twoThousandCommandsKeepHeapUsageStable() {
        startRunning();
        long sessionId = activeSessionId();

        long sessionsBefore = countRows(TrpgSchemaService.TABLE_SESSION);
        long membersBefore = countRows(TrpgSchemaService.TABLE_MEMBER);
        long logsBefore = countRows(TrpgSchemaService.TABLE_TEXT_LOG);
        long tracesBefore = countRows(TrpgSchemaService.TABLE_AI_TRACE);

        runThousandCommands(sessionId, "堆观测第一轮");
        long usedAfterFirstRound = settledUsedHeapBytes();

        runThousandCommands(sessionId, "堆观测第二轮");
        long usedAfterSecondRound = settledUsedHeapBytes();

        // 两轮之间只多出固定数量的锁、冷却与去重条目，堆占用不应继续单调增长。
        // 阈值 32 MB 远大于 GC 噪声与测试侧的 SQL 记录开销，避免在共享 JVM 里出现 flaky。
        long growth = usedAfterSecondRound - usedAfterFirstRound;
        assertTrue("第二轮堆占用增长 " + growth + " 字节，超过 32 MB 上限", growth <= HEAP_GROWTH_LIMIT_BYTES);

        // 两轮之后局面与行数仍保持收敛，没有被长跑拖出重复行。
        assertEquals("活跃局必须唯一", 1, store.loadActiveSessions().size());
        assertEquals(sessionId, store.findActiveByGroup(GROUP).id());
        assertEquals(1, locks.size());
        assertEquals(sessionsBefore, countRows(TrpgSchemaService.TABLE_SESSION));
        assertEquals(membersBefore, countRows(TrpgSchemaService.TABLE_MEMBER));
        assertEquals(logsBefore, countRows(TrpgSchemaService.TABLE_TEXT_LOG));
        assertEquals(tracesBefore, countRows(TrpgSchemaService.TABLE_AI_TRACE));
    }

    /**
     * 连续执行一轮指令，逐次断言成功；全部走不发 QQ 消息的只读 / 低风险路径。
     *
     * <p>四条路径轮转，每条各执行 250 次：status（群锁 + 冷却）、pc 查看角色卡
     * （群锁 + 冷却，只读）、log 回看（只读，独立冷却）、touchActivity（只更新同一行的
     * 活动时间）。每次推进 61 秒假时钟，既越过 1 秒冷却，也越过 60 秒活动节流窗。</p>
     */
    private void runThousandCommands(long sessionId, String label) {
        for (int i = 0; i < COMMANDS_PER_ROUND; i++) {
            clock.advance(STEP_MILLIS);
            int sequence = i + 1;
            switch (i % 4) {
                case 0: {
                    SessionResult status = service.status(actor(PLAYER));
                    assertTrue(label + "第 " + sequence + " 次 status 失败：" + status.detail(),
                            status.ok());
                    assertEquals(label + "第 " + sequence + " 次 status 应指向唯一活跃局", sessionId,
                            status.snapshot().sessionId());
                    break;
                }
                case 1: {
                    SessionResult card = service.viewCard(actor(PLAYER), PLAYER);
                    assertTrue(label + "第 " + sequence + " 次 pc 查看角色卡失败：" + card.detail(),
                            card.ok());
                    assertEquals(label + "第 " + sequence + " 次 pc 应指向唯一活跃局", sessionId,
                            card.snapshot().sessionId());
                    break;
                }
                case 2: {
                    LogPage page = logService.page(actor(PLAYER), "1");
                    assertTrue(label + "第 " + sequence + " 次 log 回看失败：" + page.detail(),
                            page.ok());
                    assertEquals(label + "第 " + sequence + " 次 log 应定位同一局", sessionId,
                            page.sessionId());
                    break;
                }
                default: {
                    // 61 秒推进大于 60 秒节流窗，每次命中都应真正写一次活动时间（只更新行、不新增行）。
                    assertTrue(label + "第 " + sequence + " 次活动刷新未写入",
                            service.touchActivity(GROUP, PLAYER));
                    break;
                }
            }
        }
    }

    /** 行数统计：测试库与生产共用同一套表名常量。 */
    private long countRows(String table) {
        return storage.count("SELECT COUNT(*) AS c FROM " + table);
    }

    /**
     * 测当前已用堆：连续多次 GC 后取最小值。
     *
     * <p>单次 {@code Runtime} 采样很容易把待回收垃圾算进去，取多次最小值可显著降低 GC 时机
     * 抖动；仍然只做粗测，因此断言阈值放宽到 32 MB。</p>
     */
    private static long settledUsedHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        long minimum = Long.MAX_VALUE;
        for (int i = 0; i < HEAP_SAMPLE_ROUNDS; i++) {
            System.gc();
            try {
                Thread.sleep(HEAP_SAMPLE_PAUSE_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
            long used = runtime.totalMemory() - runtime.freeMemory();
            if (used < minimum) {
                minimum = used;
            }
        }
        return minimum == Long.MAX_VALUE ? runtime.totalMemory() - runtime.freeMemory() : minimum;
    }
}
