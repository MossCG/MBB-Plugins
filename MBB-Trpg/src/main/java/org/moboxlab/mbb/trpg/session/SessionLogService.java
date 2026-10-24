package org.moboxlab.mbb.trpg.session;

import java.util.List;

import org.moboxlab.mbb.trpg.config.TrpgConfig;
import org.moboxlab.mbb.trpg.store.LogRow;
import org.moboxlab.mbb.trpg.store.MemberRow;
import org.moboxlab.mbb.trpg.store.SessionRow;
import org.moboxlab.mbb.trpg.store.SessionStore;
import org.moboxlab.moboxbot.API.PluginLogger;

/**
 * 日志回看只读路径（§6.1 log）：定位最近一局、校验参与者、按页取日志。
 *
 * <p>只读、不入群锁、不写库；冬眠期的勾选与分页都在本类完成，指令层只负责拼回执。
 * 判定顺序沿用「群内 → 权限 → 状态 → 参数 → 冷却」。</p>
 */
public final class SessionLogService {

    private static final long MILLIS_PER_SECOND = 1000L;
    private static final long MILLIS_PER_DAY = 24L * 60L * 60L * 1000L;
    private static final String ACTION_LOG = "log";
    private static final String KEY_STORAGE = "log-storage";
    private static final int FIRST_PAGE = 1;

    private final SessionStore store;
    private final TrpgConfig config;
    private final Clock clock;
    private final CooldownRegistry cooldowns;
    private final ErrorDedupe dedupe;
    private final PluginLogger logger;

    public SessionLogService(SessionStore store, TrpgConfig config, Clock clock, PluginLogger logger) {
        this(store, config, clock, new CooldownRegistry(), new ErrorDedupe(), logger);
    }

    /** 生产装配：与局面层、指令层共用同一份冷却与去重登记，让「同群同类错误 10 秒只回一次」跨指令生效。 */
    public SessionLogService(SessionStore store, TrpgConfig config, Clock clock, CooldownRegistry cooldowns,
            ErrorDedupe dedupe, PluginLogger logger) {
        if (store == null) {
            throw new IllegalArgumentException("存储不能为空");
        }
        if (config == null) {
            throw new IllegalArgumentException("插件配置不能为空");
        }
        if (clock == null) {
            throw new IllegalArgumentException("时间端口不能为空");
        }
        if (cooldowns == null || dedupe == null) {
            throw new IllegalArgumentException("冷却与去重不能为空");
        }
        this.store = store;
        this.config = config;
        this.clock = clock;
        this.cooldowns = cooldowns;
        this.dedupe = dedupe;
        this.logger = logger;
    }

    /**
     * 取本群最近一局的一页日志。
     *
     * @param rawPage 指令里的页码原文；空表示第 1 页，非法或越界按参数不合法拒绝
     */
    public LogPage page(SessionActor actor, String rawPage) {
        if (actor == null) {
            throw new IllegalArgumentException("请求者不能为空");
        }
        long now = clock.nowMillis();
        if (!actor.inGroup()) {
            return reject(actor.groupId(), SessionReject.NOT_IN_GROUP, "请在群内使用", now);
        }
        SessionRow row;
        try {
            row = store.findActiveByGroup(actor.groupId());
            if (row == null) {
                long windowStart = now - config.retentionSessionDays() * MILLIS_PER_DAY;
                row = store.findLatestArchivedByGroup(actor.groupId(), windowStart);
            }
        } catch (RuntimeException error) {
            warn("读取日志定位局面异常：group=" + actor.groupId() + "，原因=" + error);
            return storageFailure(actor.groupId(), now);
        }
        if (row == null) {
            return reject(actor.groupId(), SessionReject.STATE_UNAVAILABLE, "本群最近没有可回看的局", now);
        }
        List<MemberRow> members;
        try {
            members = store.loadMembers(row.id());
        } catch (RuntimeException error) {
            warn("读取日志成员异常：session=" + row.id() + "，原因=" + error);
            return storageFailure(actor.groupId(), now);
        }
        boolean participant = actor.userId() == row.kpUserId() || findMember(members, actor.userId()) != null;
        if (!participant) {
            return reject(actor.groupId(), SessionReject.NO_PERMISSION, "只有本局参与者能回看日志", now);
        }
        int page;
        try {
            page = rawPage == null || rawPage.trim().isEmpty() ? FIRST_PAGE : Integer.parseInt(rawPage.trim());
        } catch (NumberFormatException error) {
            return reject(actor.groupId(), SessionReject.BAD_ARGUMENT, "页码必须是正整数：" + rawPage, now);
        }
        if (page < FIRST_PAGE) {
            return reject(actor.groupId(), SessionReject.BAD_ARGUMENT, "页码必须是正整数：" + rawPage, now);
        }
        long total;
        List<LogRow> rows;
        try {
            total = store.maxLogSeq(row.id());
            int pageSize = Math.max(1, config.logPageSize());
            int totalPages = (int) Math.max(1L, (total + pageSize - 1L) / pageSize);
            if (page > totalPages) {
                return reject(actor.groupId(), SessionReject.BAD_ARGUMENT,
                        "页码超出范围：本局共 " + totalPages + " 页", now);
            }
            long remaining = cooldowns.remainingMillis(ACTION_LOG, actor.userId(), actor.groupId(), now,
                    config.defaultCooldownSeconds() * MILLIS_PER_SECOND);
            if (remaining > 0L) {
                long seconds = (remaining + MILLIS_PER_SECOND - 1L) / MILLIS_PER_SECOND;
                return reject(actor.groupId(), SessionReject.COOLING, "还有 " + seconds + " 秒冷却", now);
            }
            cooldowns.mark(ACTION_LOG, actor.userId(), actor.groupId(), now);
            rows = store.listLogs(row.id(), pageSize, (page - FIRST_PAGE) * pageSize);
            return LogPage.ok(row.id(), row.scriptId(), row.state(), page, pageSize, totalPages, total, rows);
        } catch (RuntimeException error) {
            warn("读取日志异常：session=" + row.id() + "，原因=" + error);
            return storageFailure(actor.groupId(), now);
        }
    }

    /** 存储异常同样按「同群同类错误 10 秒只回一次」收敛，避免异常风暴刷屏。 */
    private LogPage storageFailure(long groupId, long now) {
        boolean claimed = dedupe.claim(groupId, KEY_STORAGE, now,
                config.errorDedupeSeconds() * MILLIS_PER_SECOND);
        return LogPage.rejected(SessionReject.STATE_UNAVAILABLE, "日志读取异常，请稍后重试", !claimed);
    }

    private LogPage reject(long groupId, SessionReject reject, String detail, long now) {
        boolean claimed = dedupe.claim(groupId, reject, now,
                config.errorDedupeSeconds() * MILLIS_PER_SECOND);
        return LogPage.rejected(reject, detail, !claimed);
    }

    private static MemberRow findMember(List<MemberRow> members, long userId) {
        if (members == null) {
            return null;
        }
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
}
