package org.moboxlab.mbb.trpg.session;

import java.util.ArrayList;
import java.util.List;

import org.moboxlab.mbb.trpg.config.TrpgConfig;
import org.moboxlab.mbb.trpg.rule.RuleRegistry;
import org.moboxlab.mbb.trpg.store.MemberRow;
import org.moboxlab.mbb.trpg.store.SessionRow;
import org.moboxlab.mbb.trpg.store.SessionStore;

import org.moboxlab.moboxbot.API.PluginLogger;

/**
 * 收尾编排与快照构造（规格 §5.3 / §5.8）。
 *
 * <p>收尾顺序冻结：CAS 置 ENDED（endedAt、endReason、growthSettled=0、空摘要）→ 逐成员成长 →
 * 写摘要并把 growthSettled 置 1。CAS 返回 -1 中止本步、不推进 growthSettled、下轮重试；
 * 返回 0 视为前置不匹配，回读后按新状态处理。所有调用都必须已在群锁内。</p>
 */
final class SessionArchiver {

    private static final long MILLIS_PER_HOUR = 60L * 60L * 1000L;
    private static final long MILLIS_PER_DAY = 24L * MILLIS_PER_HOUR;

    private final SessionStore store;
    private final GrowthSettler growthSettler;
    private final SummaryResolver summaryResolver;
    private final PluginLogger logger;

    SessionArchiver(SessionStore store, GrowthSettler growthSettler, SummaryResolver summaryResolver,
                    PluginLogger logger) {
        if (store == null) {
            throw new IllegalArgumentException("存储端口不能为空");
        }
        if (growthSettler == null) {
            throw new IllegalArgumentException("成长结算器不能为空");
        }
        if (summaryResolver == null) {
            throw new IllegalArgumentException("摘要解析器不能为空");
        }
        this.store = store;
        this.growthSettler = growthSettler;
        this.summaryResolver = summaryResolver;
        this.logger = logger;
    }

    /**
     * 构造局面快照（成员、线索与情景版本一起读）。
     *
     * <p>未知 ruleSetID 在快照里回退内置 d100（§5.7「不阻断」），库内原值保持不变。</p>
     */
    SessionSnapshot snapshot(SessionRow row, boolean recovered) {
        SessionRow effective = effectiveRuleSet(row);
        List<MemberRow> members = store.loadMembers(effective.id());
        List<String> clues = store.listClueKeys(effective.id());
        return SessionSnapshots.of(effective, members, clues, recovered, store.maxLogSeq(effective.id()));
    }

    /** 库里的规则集未注册时按内置 d100 投影。 */
    private SessionRow effectiveRuleSet(SessionRow row) {
        if (row == null || RuleRegistry.get(row.ruleSetId()) != null) {
            return row;
        }
        return row.toBuilder().ruleSetId(RuleRegistry.BUILTIN_RULE_SET_ID).build();
    }

    /** 是否已到惰性过期（§5.8）：招募期按招募超时、进行中按无活动超时。 */
    boolean isExpired(SessionRow row, long now, TrpgConfig config) {
        if (row == null || row.state() == null || !row.state().active()) {
            return false;
        }
        long anchor = activityAnchor(row);
        if (row.state() == SessionState.RECRUITING) {
            return now - anchor > config.recruitExpireHours() * MILLIS_PER_HOUR;
        }
        if (row.state() == SessionState.RUNNING) {
            return now - anchor > config.runningExpireDays() * MILLIS_PER_DAY;
        }
        return false;
    }

    /** 局内活动锚点：最后活动时间，缺失时回退建局时间。 */
    static long activityAnchor(SessionRow row) {
        return row.lastActivityAt() > 0L ? row.lastActivityAt() : row.createTime();
    }

    /**
     * 执行一次收尾。
     *
     * @param settleGrowth 进行中结束传 true（结算成长）；招募期结束传 false
     */
    ArchiveResult archive(SessionRow row, String endReason, boolean settleGrowth, long now) {
        int cas = store.casTransition(row.id(), row.state(), SessionState.ENDED, row.startedAt(),
                now, endReason, false, "", now);
        if (cas < 0) {
            warn("归档状态写入未生效，本步中止：session=" + row.id());
            return ArchiveResult.failed("归档状态写入未生效");
        }
        SessionRow ended = store.findSession(row.id());
        if (ended == null) {
            warn("归档后回读不到局面行：session=" + row.id());
            return ArchiveResult.failed("归档后回读不到局面行");
        }
        if (cas == 0 && ended.state() != SessionState.ENDED) {
            warn("归档前置状态不匹配：session=" + row.id() + "，state=" + ended.state());
            return ArchiveResult.failed("归档前置状态不匹配");
        }
        return finish(ended, settleGrowth, now, ended.endReason().isEmpty() ? endReason : ended.endReason());
    }

    /**
     * 对已处于 ENDED 的局补齐成长与摘要（启动补做与收尾共用）。
     *
     * <p>已结算则直接返回快照；成长失败保留 growthSettled=0，等下一轮。</p>
     */
    ArchiveResult finish(SessionRow ended, boolean settleGrowth, long now, String endReason) {
        if (ended.growthSettled()) {
            return ArchiveResult.done(snapshot(ended, true), endReason,
                    new ArrayList<GrowthOutcome>());
        }
        List<GrowthOutcome> outcomes = new ArrayList<GrowthOutcome>();
        if (settleGrowth) {
            GrowthSettler.Result settled = growthSettler.settle(ended, store.loadMembers(ended.id()), now);
            outcomes.addAll(settled.outcomes());
            if (!settled.ok()) {
                warn("成长结算未完成，保留 growthSettled=0 待补做：session=" + ended.id());
                return ArchiveResult.deferred(snapshot(ended, true), endReason, outcomes,
                        "成长结算未完成，待启动补做");
            }
        }
        String summary = summaryResolver.resolve(ended, store.listClueKeys(ended.id()));
        int cas = store.casTransition(ended.id(), SessionState.ENDED, SessionState.ENDED,
                ended.startedAt(), ended.endedAt(), ended.endReason(), true, summary, now);
        if (cas < 0) {
            warn("摘要写入未生效，保留 growthSettled=0 待补做：session=" + ended.id());
            return ArchiveResult.deferred(snapshot(ended, true), endReason, outcomes,
                    "摘要写入未生效，待启动补做");
        }
        SessionRow settled = store.findSession(ended.id());
        if (settled == null) {
            settled = ended;
        }
        if (!settled.growthSettled()) {
            // cas==0 表示前置状态不匹配：回读确认未结算后按 deferred 交给下一轮
            warn("摘要写入前置不匹配，保留 growthSettled=0 待补做：session=" + ended.id());
            return ArchiveResult.deferred(snapshot(settled, true), endReason, outcomes,
                    "摘要写入前置不匹配，待启动补做");
        }
        return ArchiveResult.done(snapshot(settled, true), endReason, outcomes);
    }

    /** 一次过期扫描里最多连续归档几局（同群唯一约束下正常为 1）。 */
    static int maxExpiryRounds() {
        return 3;
    }

    private void warn(String message) {
        if (logger != null) {
            logger.sendWarn("[MBB-Trpg] " + message);
        }
    }
}
