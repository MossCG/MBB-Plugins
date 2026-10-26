package org.moboxlab.mbb.trpg.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.moboxlab.mbb.trpg.rule.CheckResult;
import org.moboxlab.mbb.trpg.rule.D100GrowthCheck;
import org.moboxlab.mbb.trpg.rule.RandomSource;
import org.moboxlab.mbb.trpg.rule.RuleException;
import org.moboxlab.mbb.trpg.rule.RuleRegistry;
import org.moboxlab.mbb.trpg.rule.RuleSet;
import org.moboxlab.mbb.trpg.store.CardData;
import org.moboxlab.mbb.trpg.store.MemberRow;
import org.moboxlab.mbb.trpg.store.SessionRow;
import org.moboxlab.mbb.trpg.store.SessionStore;

import org.moboxlab.moboxbot.API.PluginLogger;

/**
 * 收尾成长结算（规格 §5.3）：对每个成员待结算的成长技能逐项掷成长检定，
 * 把新技能值与清空后的成长列表写回同一条成员行。
 *
 * <p>已写入的成员不回滚；全部成员写成功后由调用方把 growthSettled 置 1。
 * 任一成员失败即整体判失败，保留其余成员的成长列表，等下一轮（启动补做）重试。</p>
 */
final class GrowthSettler {

    private static final int SKILL_MIN = 0;
    private static final int SKILL_MAX = 99;

    private final SessionStore store;
    private final RandomSource random;
    private final PluginLogger logger;

    GrowthSettler(SessionStore store, RandomSource random, PluginLogger logger) {
        if (store == null) {
            throw new IllegalArgumentException("存储端口不能为空");
        }
        if (random == null) {
            throw new IllegalArgumentException("随机源不能为空");
        }
        this.store = store;
        this.random = random;
        this.logger = logger;
    }

    /** 结算一局的全体成员成长；返回是否全部成功与已产生的明细。 */
    Result settle(SessionRow row, List<MemberRow> members, long now) {
        List<GrowthOutcome> outcomes = new ArrayList<GrowthOutcome>();
        RuleSet ruleSet = RuleRegistry.getOrDefault(row.ruleSetId());
        if (ruleSet == null) {
            warn("无法结算成长，规则集不可用：session=" + row.id() + "，ruleSet=" + row.ruleSetId());
            return Result.failed(outcomes);
        }
        if (members == null) {
            return Result.ok(outcomes);
        }
        for (MemberRow member : members) {
            if (member == null) {
                continue;
            }
            List<String> pending = member.successSkills();
            if (pending.isEmpty()) {
                continue;
            }
            CardData card = member.card();
            if (!member.warnings().isEmpty()) {
                warn("成员数据解析告警：session=" + row.id() + "，user=" + member.userId()
                        + "，原因=" + member.warnings().get(0));
            }
            Map<String, Integer> skills = new HashMap<String, Integer>(card.skills());
            List<GrowthOutcome> memberOutcomes = new ArrayList<GrowthOutcome>();
            boolean failed = false;
            for (String skillId : pending) {
                if (skillId == null || skillId.isEmpty()) {
                    continue;
                }
                int from = clamp(card.skill(skillId));
                int rolled;
                try {
                    CheckResult result = ruleSet.resolve(new D100GrowthCheck(from), random);
                    rolled = result.growthDelta();
                } catch (RuleException error) {
                    warn("成长检定失败，保留待补做：session=" + row.id() + "，user="
                            + member.userId() + "，skill=" + skillId + "，原因=" + error.getMessage());
                    failed = true;
                    break;
                }
                int to = Math.min(SKILL_MAX, from + rolled);
                skills.put(skillId, Integer.valueOf(to));
                memberOutcomes.add(new GrowthOutcome(member.userId(), skillId, from, to, rolled));
            }
            if (failed) {
                return Result.failed(outcomes);
            }
            int affected = store.updateMember(withSettlement(member, card, skills, now));
            if (affected <= 0) {
                warn("成长写回未生效，保留待补做：session=" + row.id() + "，user="
                        + member.userId() + "，affected=" + affected);
                return Result.failed(outcomes);
            }
            outcomes.addAll(memberOutcomes);
        }
        return Result.ok(outcomes);
    }

    /** 写回新技能值并清空成长列表，其余字段原样保留。 */
    private MemberRow withSettlement(MemberRow member, CardData card, Map<String, Integer> skills,
                                     long now) {
        return member.toBuilder()
                .card(card.withSkills(skills))
                .successSkills(Collections.<String>emptyList())
                .updateTime(now)
                .build();
    }

    private static int clamp(int value) {
        if (value < SKILL_MIN) {
            return SKILL_MIN;
        }
        return value > SKILL_MAX ? SKILL_MAX : value;
    }

    private void warn(String message) {
        if (logger != null) {
            logger.sendWarn("[MBB-Trpg] " + message);
        }
    }

    /** 一次结算的结果。 */
    static final class Result {

        private final boolean ok;
        private final List<GrowthOutcome> outcomes;

        private Result(boolean ok, List<GrowthOutcome> outcomes) {
            this.ok = ok;
            this.outcomes = Collections.unmodifiableList(new ArrayList<GrowthOutcome>(outcomes));
        }

        static Result ok(List<GrowthOutcome> outcomes) {
            return new Result(true, outcomes);
        }

        static Result failed(List<GrowthOutcome> outcomes) {
            return new Result(false, outcomes);
        }

        boolean ok() {
            return ok;
        }

        List<GrowthOutcome> outcomes() {
            return outcomes;
        }
    }
}
