package org.moboxlab.mbb.trpg.store;

import org.moboxlab.moboxbot.API.PluginLogger;
import org.moboxlab.mbb.trpg.session.Clock;
import org.moboxlab.mbb.trpg.session.ScheduledTask;
import org.moboxlab.mbb.trpg.session.Scheduler;

import java.util.Collections;
import java.util.List;

/**
 * 归档清理服务：删除过期已结算局与过期 AI 审计行。
 *
 * <p>清理顺序固定为 text_log → member → session，避免出现子表孤儿。子表 DELETE 返回
 * -1（语句未执行、状态未知）或删除 0 行但按库里内容预期应有行时，都中止本局、不删父行，
 * 留待下一轮重试；库里本来就没有该子表的行（例如一局从未写过日志，或上一轮已删过日志）
 * 时，0 行属于正常结果，继续往下走。父行 DELETE 返回 0 视为状态已变或已被删除，计入跳过。</p>
 *
 * <p>未结算（growthSettled = 0）的 ENDED 局永不清理；黑名单不随局清理。</p>
 */
public final class TrpgRetentionService {

    /** 局归档默认保留天数。 */
    public static final int DEFAULT_SESSION_DAYS = 30;
    /** 审计默认保留小时数。 */
    public static final int DEFAULT_TRACE_KEEP_HOURS = 168;
    /** 审计默认保留行数。 */
    public static final int DEFAULT_TRACE_KEEP_ROWS = 5000;
    /** 每小时执行一次。 */
    public static final long HOURLY_PERIOD_MILLIS = 60L * 60L * 1000L;

    private static final long HOUR_MILLIS = 60L * 60L * 1000L;
    private static final long DAY_MILLIS = 24L * HOUR_MILLIS;

    private final SessionStore store;
    private final PluginLogger logger;
    private final Clock clock;
    private final long sessionRetentionMillis;
    private final long traceRetentionMillis;
    private final int traceKeepRows;

    public TrpgRetentionService(SessionStore store, PluginLogger logger, Clock clock) {
        this(store, logger, clock, DEFAULT_SESSION_DAYS, DEFAULT_TRACE_KEEP_HOURS,
                DEFAULT_TRACE_KEEP_ROWS);
    }

    public TrpgRetentionService(SessionStore store, PluginLogger logger, Clock clock, int sessionDays,
                                int traceKeepHours, int traceMaxRows) {
        if (store == null) {
            throw new IllegalArgumentException("存储不能为空");
        }
        if (clock == null) {
            throw new IllegalArgumentException("时间端口不能为空");
        }
        this.store = store;
        this.logger = logger;
        this.clock = clock;
        this.sessionRetentionMillis = Math.max(1, sessionDays) * DAY_MILLIS;
        this.traceRetentionMillis = Math.max(1, traceKeepHours) * HOUR_MILLIS;
        this.traceKeepRows = Math.max(1, traceMaxRows);
    }

    /** 排入每小时一次的清理任务。 */
    public ScheduledTask scheduleHourly(Scheduler scheduler) {
        if (scheduler == null) {
            throw new IllegalArgumentException("定时器不能为空");
        }
        return scheduler.runTimer(new Runnable() {
            @Override
            public void run() {
                runOnce(clock.nowMillis());
            }
        }, HOURLY_PERIOD_MILLIS, HOURLY_PERIOD_MILLIS);
    }

    /** 执行一轮清理；不抛异常，结果通过 {@link Result} 返回。 */
    public Result runOnce(long now) {
        List<Long> expired;
        try {
            expired = store.listExpiredSessionIds(now - sessionRetentionMillis);
        } catch (RuntimeException e) {
            warn("查询过期局失败：" + e.getMessage());
            expired = Collections.emptyList();
        }
        if (expired == null) {
            expired = Collections.emptyList();
        }

        int purged = 0;
        int aborted = 0;
        int skipped = 0;
        for (Long id : expired) {
            if (id == null) {
                continue;
            }
            int outcome = purgeSession(id.longValue());
            if (outcome < 0) {
                aborted++;
            } else if (outcome == 0) {
                skipped++;
            } else {
                purged++;
            }
        }

        boolean traceFailed = false;
        int traceExpired = 0;
        try {
            traceExpired = store.deleteAiTraceBefore(now - traceRetentionMillis);
            if (traceExpired < 0) {
                traceFailed = true;
            }
        } catch (RuntimeException e) {
            traceExpired = -1;
            traceFailed = true;
            warn("清理过期审计失败：" + e.getMessage());
        }
        int traceTrimmed = 0;
        try {
            traceTrimmed = store.deleteAiTraceOverflow(traceKeepRows);
            if (traceTrimmed < 0) {
                traceFailed = true;
            }
        } catch (RuntimeException e) {
            traceTrimmed = -1;
            traceFailed = true;
            warn("截断超量审计失败：" + e.getMessage());
        }

        int candidates = expired.size();
        if (purged > 0 || traceExpired > 0 || traceTrimmed > 0) {
            info("清理完成：候选局 " + candidates + "，已清理 " + purged + "，跳过 " + skipped
                    + "，中止 " + aborted + "；过期审计 " + traceExpired + " 行，超量审计 "
                    + traceTrimmed + " 行");
        }
        if (aborted > 0 || traceFailed) {
            warn("本轮清理有失败项，将在下一轮重试：候选局 " + candidates + "，中止 " + aborted
                    + "，审计" + (traceFailed ? "失败" : "正常"));
        }
        return new Result(candidates, purged, aborted, skipped, traceExpired, traceTrimmed);
    }

    /** 返回 1 已清理、0 跳过、-1 中止。 */
    private int purgeSession(long sessionId) {
        try {
            long lastSeq = store.maxLogSeq(sessionId);
            int logs = store.deleteLogs(sessionId);
            if (logs < 0) {
                warn("删除局 " + sessionId + " 的日志失败，本轮中止该局，下一轮重试");
                return -1;
            }
            if (logs == 0 && lastSeq > 0L) {
                warn("局 " + sessionId + " 预期有日志却删除 0 行，本轮中止该局，下一轮重试");
                return -1;
            }

            List<MemberRow> members = store.loadMembers(sessionId);
            boolean membersExpected = members != null && !members.isEmpty();
            int memberRows = store.deleteMembers(sessionId);
            if (memberRows < 0) {
                warn("删除局 " + sessionId + " 的成员失败，本轮中止该局，下一轮重试");
                return -1;
            }
            if (memberRows == 0 && membersExpected) {
                warn("局 " + sessionId + " 预期有成员却删除 0 行，本轮中止该局，下一轮重试");
                return -1;
            }

            int session = store.deleteSession(sessionId);
            if (session < 0) {
                warn("删除局 " + sessionId + " 的主行失败，下一轮重试");
                return -1;
            }
            return session > 0 ? 1 : 0;
        } catch (RuntimeException e) {
            warn("清理局 " + sessionId + " 时异常：" + e.getMessage());
            return -1;
        }
    }

    private void info(String message) {
        if (logger != null) {
            logger.sendInfo("[MBB-Trpg] " + message);
        }
    }

    private void warn(String message) {
        if (logger != null) {
            logger.sendWarn("[MBB-Trpg] " + message);
        }
    }

    /** 一轮清理的结果快照。 */
    public static final class Result {

        private final int candidates;
        private final int purged;
        private final int aborted;
        private final int skipped;
        private final int traceExpiredRows;
        private final int traceTrimmedRows;

        Result(int candidates, int purged, int aborted, int skipped, int traceExpiredRows,
               int traceTrimmedRows) {
            this.candidates = candidates;
            this.purged = purged;
            this.aborted = aborted;
            this.skipped = skipped;
            this.traceExpiredRows = traceExpiredRows;
            this.traceTrimmedRows = traceTrimmedRows;
        }

        public int candidates() {
            return candidates;
        }

        public int purged() {
            return purged;
        }

        public int aborted() {
            return aborted;
        }

        public int skipped() {
            return skipped;
        }

        public int traceExpiredRows() {
            return traceExpiredRows;
        }

        public int traceTrimmedRows() {
            return traceTrimmedRows;
        }

        public boolean hasFailure() {
            return aborted > 0 || traceExpiredRows < 0 || traceTrimmedRows < 0;
        }

        @Override
        public String toString() {
            return "Result{candidates=" + candidates + ", purged=" + purged + ", aborted=" + aborted
                    + ", skipped=" + skipped + ", traceExpiredRows=" + traceExpiredRows
                    + ", traceTrimmedRows=" + traceTrimmedRows + "}";
        }
    }
}
