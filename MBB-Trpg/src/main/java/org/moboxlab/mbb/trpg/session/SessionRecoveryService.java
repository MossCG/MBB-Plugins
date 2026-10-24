package org.moboxlab.mbb.trpg.session;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.moboxlab.mbb.trpg.rule.RuleRegistry;
import org.moboxlab.mbb.trpg.store.SessionRow;
import org.moboxlab.mbb.trpg.store.SessionStore;
import org.moboxlab.moboxbot.API.PluginLogger;

/**
 * 启动期恢复与补做（§5.3 / §5.7）：扫描未归档局、逐局加载成员、重建内存局面，并把未结算的 ENDED 局补做成长与摘要。
 *
 * <p>本类只做启动期一次性工作，操作过程中取群锁；事件在释放群锁后发布，不参与网络发送。
 * 恢复只读不写库（除按规则归档的异常局），不预载日志。</p>
 */
final class SessionRecoveryService {

    private final SessionStore store;
    private final SessionArchiver archiver;
    private final GroupLockRegistry locks;
    private final Clock clock;
    private final SessionEventPublisher publisher;
    private final PluginLogger logger;
    private final Set<Long> pendingRecovery;

    SessionRecoveryService(SessionStore store, SessionArchiver archiver, GroupLockRegistry locks,
            Clock clock, SessionEventPublisher publisher, PluginLogger logger,
            Set<Long> pendingRecovery) {
        if (store == null || archiver == null || locks == null || clock == null) {
            throw new IllegalArgumentException("恢复依赖不能为空");
        }
        if (pendingRecovery == null) {
            throw new IllegalArgumentException("待确认恢复集合不能为空");
        }
        this.store = store;
        this.archiver = archiver;
        this.locks = locks;
        this.clock = clock;
        this.publisher = publisher == null ? SessionEventPublisher.NONE : publisher;
        this.logger = logger;
        this.pendingRecovery = pendingRecovery;
    }

    /**
     * 启动恢复（§5.7）：扫未归档局、逐局加载成员、重建内存局面。
     *
     * <p>只有进行中的局把 {@code recovered} 置 false 并发布 {@link SessionEvent.Type#RECOVERED}；
     * 招募期的局没有需要确认的进行中上下文，仍按已确认处理。成员行为 0 的局按 T4 cancelled
     * 归档并告警；规则集未知回退 d100。逐局 try/catch，单局异常不影响其余局。</p>
     *
     * @return 已加载的局面快照
     */
    List<SessionSnapshot> recover() {
        List<SessionSnapshot> loaded = new ArrayList<SessionSnapshot>();
        List<SessionEvent> events = new ArrayList<SessionEvent>();
        for (SessionRow candidate : store.loadActiveSessions()) {
            if (!locks.tryLock(candidate.groupId())) {
                warn("恢复时群锁被占用，跳过：group=" + candidate.groupId());
                continue;
            }
            try {
                SessionRow current = store.findActiveByGroup(candidate.groupId());
                if (current == null) {
                    continue;
                }
                if (store.loadMembers(current.id()).isEmpty()) {
                    warn("活跃局没有成员行，按取消归档：session=" + current.id());
                    ArchiveResult archived = archiver.archive(current, "cancelled", false, clock.nowMillis());
                    if (archived.status() == ArchiveResult.Status.FAILED) {
                        warn("取消无成员局失败：session=" + current.id() + "，原因=" + archived.detail());
                    }
                    continue;
                }
                if (RuleRegistry.get(current.ruleSetId()) == null) {
                    warn("规则集未知，回退 d100：session=" + current.id() + "，ruleSet=" + current.ruleSetId());
                }
                boolean running = current.state() == SessionState.RUNNING;
                if (running) {
                    pendingRecovery.add(Long.valueOf(current.id()));
                }
                SessionSnapshot snapshot = archiver.snapshot(current, !running);
                loaded.add(snapshot);
                if (running) {
                    events.add(SessionEvent.recovered(snapshot));
                }
            } catch (RuntimeException error) {
                warn("恢复异常，跳过本局：group=" + candidate.groupId() + "，原因=" + error);
            } finally {
                locks.unlock(candidate.groupId());
            }
        }
        for (SessionEvent event : events) {
            publish(event);
        }
        return loaded;
    }

    /**
     * 启动补做（§5.3 / §5.7）：对未结算的 ENDED 局补做成长与摘要。
     *
     * <p>不发布事件；-1 中止当前局并记日志，下一轮再试。招募期收尾的局成功技能为空，
     * 成长结算为空操作。</p>
     *
     * @return 成功补做的局数
     */
    int settlePending() {
        int settled = 0;
        for (SessionRow candidate : store.listUnsettledSessions()) {
            if (!locks.tryLock(candidate.groupId())) {
                warn("补做时群锁被占用，跳过：group=" + candidate.groupId());
                continue;
            }
            try {
                SessionRow current = store.findSession(candidate.id());
                if (current == null || current.state() != SessionState.ENDED || current.growthSettled()) {
                    continue;
                }
                ArchiveResult archived = archiver.finish(current, true, clock.nowMillis(),
                        current.endReason());
                if (archived.status() != ArchiveResult.Status.DONE) {
                    warn("补做未完成，下轮重试：session=" + current.id() + "，原因=" + archived.detail());
                    continue;
                }
                settled++;
            } catch (RuntimeException error) {
                warn("补做异常，下轮重试：session=" + candidate.id() + "，原因=" + error);
            } finally {
                locks.unlock(candidate.groupId());
            }
        }
        return settled;
    }

    private void publish(SessionEvent event) {
        try {
            publisher.publish(event);
        } catch (RuntimeException error) {
            error("局面事件发布失败：type=" + event.type() + "，原因=" + error);
        }
    }

    private void warn(String message) {
        if (logger != null) {
            logger.sendWarn("[MBB-Trpg] " + message);
        }
    }

    private void error(String message) {
        if (logger != null) {
            logger.sendError("[MBB-Trpg] " + message);
        }
    }
}
