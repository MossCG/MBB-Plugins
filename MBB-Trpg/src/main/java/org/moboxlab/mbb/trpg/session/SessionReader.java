package org.moboxlab.mbb.trpg.session;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongPredicate;

import org.moboxlab.mbb.trpg.config.TrpgConfig;
import org.moboxlab.mbb.trpg.store.MemberRow;
import org.moboxlab.mbb.trpg.store.SessionRow;
import org.moboxlab.mbb.trpg.store.SessionStore;
import org.moboxlab.moboxbot.API.PluginLogger;

/**
 * 局面只读路径：对外快照读取与局内活动刷新。
 *
 * <p>读状态时若命中过期局，先在群锁内惰性归档（T5 / T6）；群锁被占用时退化为只读，
 * 过期归档留给下一次请求。活动刷新按 §5.8 做 60 秒节流，只由消息入口在群内消息上调用。</p>
 */
final class SessionReader {

    private static final long MILLIS_PER_SECOND = 1000L;

    private final SessionStore store;
    private final SessionArchiver archiver;
    private final GroupLockRegistry locks;
    private final Clock clock;
    private final TrpgConfig config;
    private final SessionEventPublisher publisher;
    private final PluginLogger logger;
    private final LongPredicate recovered;

    SessionReader(SessionStore store, SessionArchiver archiver, GroupLockRegistry locks, Clock clock,
            TrpgConfig config, SessionEventPublisher publisher, PluginLogger logger,
            LongPredicate recovered) {
        if (store == null || archiver == null || locks == null || clock == null || config == null) {
            throw new IllegalArgumentException("读取依赖不能为空");
        }
        if (recovered == null) {
            throw new IllegalArgumentException("恢复状态判定不能为空");
        }
        this.store = store;
        this.archiver = archiver;
        this.locks = locks;
        this.clock = clock;
        this.config = config;
        this.publisher = publisher == null ? SessionEventPublisher.NONE : publisher;
        this.logger = logger;
        this.recovered = recovered;
    }

    /** 读取本群当前局面；无活跃局返回空快照。 */
    SessionSnapshot readSnapshot(long groupId) {
        if (groupId <= 0L) {
            return SessionSnapshots.none(groupId);
        }
        List<SessionEvent> events = new ArrayList<SessionEvent>();
        boolean locked = locks.tryLock(groupId);
        SessionSnapshot snapshot;
        try {
            SessionRow row = store.findActiveByGroup(groupId);
            if (locked && row != null) {
                row = expireWhileReading(row, events);
            }
            snapshot = row == null ? SessionSnapshots.none(groupId)
                    : archiver.snapshot(row, recovered.test(row.id()));
        } catch (RuntimeException error) {
            warn("读取局面异常：group=" + groupId + "，原因=" + error);
            snapshot = SessionSnapshots.none(groupId);
        } finally {
            if (locked) {
                locks.unlock(groupId);
            }
        }
        for (SessionEvent event : events) {
            publish(event);
        }
        return snapshot;
    }

    /** 刷新局内活动（§5.8）：仅本局成员在群内的消息调用，60 秒节流；不入群锁、不做网络发送。 */
    boolean touchActivity(long groupId, long userId) {
        if (groupId <= 0L || userId <= 0L) {
            return false;
        }
        try {
            SessionRow row = store.findActiveByGroup(groupId);
            if (row == null) {
                return false;
            }
            // 节流判定放在加载成员之前：被节流掉的消息不再付出一次成员表查询与角色卡解析。
            long now = clock.nowMillis();
            long throttle = config.activityThrottleSeconds() * MILLIS_PER_SECOND;
            long anchor = SessionArchiver.activityAnchor(row);
            if (now - anchor < throttle) {
                return false;
            }
            MemberRow member = findMember(store.loadMembers(row.id()), userId);
            if (member == null || member.leftAt() > 0L) {
                return false;
            }
            int affected = store.touchActivity(row.id(), now, now);
            if (affected < 0) {
                warn("活动时间写入未生效：session=" + row.id());
                return false;
            }
            if (affected == 0) {
                SessionRow current = store.findActiveByGroup(groupId);
                if (current == null || current.id() != row.id()) {
                    warn("活动时间无匹配行，本局已归档或切换：session=" + row.id());
                    return false;
                }
            }
            return true;
        } catch (RuntimeException error) {
            // 群消息入口每条都会走到这里，异常必须吞在读取层，不能抛回事件线程。
            warn("刷新局内活动异常：group=" + groupId + "，原因=" + error);
            return false;
        }
    }

    /** 只读路径上的惰性过期：最多连续归档 {@link SessionArchiver#maxExpiryRounds()} 次。 */
    private SessionRow expireWhileReading(SessionRow row, List<SessionEvent> events) {
        int rounds = 0;
        long now = clock.nowMillis();
        while (row != null && archiver.isExpired(row, now, config)) {
            if (++rounds > SessionArchiver.maxExpiryRounds()) {
                warn("读取时惰性过期反复失效：group=" + row.groupId());
                break;
            }
            ArchiveResult archived = archiver.archive(row, "expired",
                    row.state() == SessionState.RUNNING, now);
            if (archived.status() == ArchiveResult.Status.FAILED) {
                warn("读取时惰性过期归档未生效：session=" + row.id());
                break;
            }
            if (archived.snapshot() != null) {
                events.add(SessionEvent.ended(archived.snapshot(), archived.endReason(),
                        archived.growth()));
            }
            row = store.findActiveByGroup(row.groupId());
        }
        return row;
    }

    private void publish(SessionEvent event) {
        try {
            publisher.publish(event);
        } catch (RuntimeException error) {
            error("局面事件发布失败：type=" + event.type() + "，原因=" + error);
        }
    }

    private static MemberRow findMember(List<MemberRow> members, long userId) {
        for (MemberRow member : members) {
            if (member != null && member.userId() == userId) {
                return member;
            }
        }
        return null;
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
