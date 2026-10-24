package org.moboxlab.mbb.trpg.session;

import java.util.List;
import java.util.Locale;

import org.moboxlab.mbb.trpg.config.TrpgConfig;
import org.moboxlab.mbb.trpg.rule.CheckResult;
import org.moboxlab.mbb.trpg.rule.D100SanCheck;
import org.moboxlab.mbb.trpg.rule.D100SkillCheck;
import org.moboxlab.mbb.trpg.rule.DiceExpression;
import org.moboxlab.mbb.trpg.rule.DiceExpressions;
import org.moboxlab.mbb.trpg.rule.OpposedCheck;
import org.moboxlab.mbb.trpg.rule.OpposedOutcome;
import org.moboxlab.mbb.trpg.rule.OpposedResult;
import org.moboxlab.mbb.trpg.rule.OpposedTieBreak;
import org.moboxlab.mbb.trpg.rule.RandomSource;
import org.moboxlab.mbb.trpg.rule.RollTrace;
import org.moboxlab.mbb.trpg.rule.RuleException;
import org.moboxlab.mbb.trpg.rule.RuleRegistry;
import org.moboxlab.mbb.trpg.rule.RuleSet;
import org.moboxlab.mbb.trpg.script.Script;
import org.moboxlab.mbb.trpg.script.ScriptCatalog;
import org.moboxlab.mbb.trpg.script.ScriptCheck;
import org.moboxlab.mbb.trpg.script.ScriptTrigger;
import org.moboxlab.mbb.trpg.store.CardData;
import org.moboxlab.mbb.trpg.store.MemberRow;
import org.moboxlab.mbb.trpg.store.SessionRow;
import org.moboxlab.mbb.trpg.store.SessionStore;

import org.moboxlab.moboxbot.API.PluginLogger;

/**
 * 局内检定动作（规格 §6.1 的 KP 子集）：技能检定、对抗检定与理智检定。
 *
 * <p>与 {@link SessionPlayService} 一样走 {@link SessionService#withGroupLockValue}：
 * 判定顺序固定为 群内 → 权限 → 状态 → 参数 → 冷却 → 执行，写库在群锁内完成，
 * 事件在解锁后发布。这里只负责掷骰、落库与提示拼装，参数解析属于指令层。</p>
 */
final class SessionCheckService {

    private static final String ACTION_CHECK = "check";
    private static final String ACTION_OPPOSED = "opposed";
    private static final String ACTION_SAN = "san";

    private static final String EVENT_CHECK = "check";
    private static final String EVENT_OPPOSED = "opposed";
    private static final String EVENT_SAN = "san";

    /** 技能值合法的闭区间端点。 */
    private static final int SKILL_MIN = 0;
    private static final int SKILL_MAX = 99;
    /** 奖惩骰上限（规格 §4.2：各最多 2 颗）。 */
    private static final int MAX_DICE_COUNT = 2;
    /** 临时性疯狂的毫秒换算。 */
    private static final long MILLIS_PER_HOUR = 60L * 60L * 1000L;
    /** 临时性疯狂的骰面（1D10 小时）。 */
    private static final int MADNESS_DIE_FACES = 10;
    /** 对手按目标值出牌时的显示名。 */
    private static final String OPPOSED_VALUE_LABEL = "目标值";

    private final SessionService sessions;
    private final SessionStore store;
    private final ScriptCatalog catalog;
    private final RandomSource random;
    private final TrpgConfig config;
    private final PluginLogger logger;

    SessionCheckService(SessionService sessions, SessionStore store, ScriptCatalog catalog,
                        RandomSource random, TrpgConfig config, PluginLogger logger) {
        if (sessions == null) {
            throw new IllegalArgumentException("局面服务不能为空");
        }
        if (store == null) {
            throw new IllegalArgumentException("存储不能为空");
        }
        if (catalog == null) {
            throw new IllegalArgumentException("剧本库不能为空");
        }
        if (random == null) {
            throw new IllegalArgumentException("随机源不能为空");
        }
        if (config == null) {
            throw new IllegalArgumentException("配置不能为空");
        }
        this.sessions = sessions;
        this.store = store;
        this.catalog = catalog;
        this.random = random;
        this.config = config;
        this.logger = logger;
    }

    /** 技能检定（§6.1 check）：KP 可代掷，成功则把技能记入成长集合。 */
    PlayResult<SessionOutcomes.Check> check(SessionActor actor, final long targetUserId,
                                            final String rawSkill, final String rawDiceKind,
                                            final String rawDiceCount) {
        final SessionActor request = SessionService.requireActor(actor);
        if (!request.inGroup()) {
            return PlayResult.rejected(SessionResult.rejected(SessionReject.NOT_IN_GROUP, "请在群内使用"));
        }
        if (targetUserId < 0L) {
            return PlayResult.rejected(SessionResult.rejected(SessionReject.BAD_ARGUMENT,
                    "参数不对：无法识别玩家"));
        }
        return sessions.withGroupLockValue(request, ACTION_CHECK,
                new SessionService.LockedOutcome<SessionOutcomes.Check>() {

                    @Override
                    public PlayResult<SessionOutcomes.Check> run(SessionService.Turn turn) {
                        return checkInLock(turn, targetUserId, rawSkill, rawDiceKind, rawDiceCount);
                    }
                });
    }

    private PlayResult<SessionOutcomes.Check> checkInLock(SessionService.Turn turn,
                                                          long targetUserId, String rawSkill,
                                                          String rawDiceKind, String rawDiceCount) {
        SessionRow row = turn.active();
        if (row == null) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.STATE_UNAVAILABLE,
                    "本群暂无局"));
        }
        long rollerUserId = targetUserId == 0L ? turn.actor.userId() : targetUserId;
        boolean kpRolled = rollerUserId != turn.actor.userId();
        if (turn.actor.userId() != row.kpUserId() && kpRolled) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.NO_PERMISSION,
                    "只有 KP 或本人能发起检定"));
        }
        if (row.state() != SessionState.RUNNING) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.STATE_UNAVAILABLE,
                    "本局尚未开局"));
        }
        if (rollerUserId == row.kpUserId()) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.MEMBER_STATE,
                    "KP 没有角色卡"));
        }
        MemberRow member = turn.member(row, rollerUserId);
        if (member == null || member.leftAt() > 0L) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.MEMBER_STATE,
                    kpRolled ? "目标不在本局内" : "你不在本局内"));
        }
        String skill = rawSkill == null ? "" : rawSkill.trim();
        if (skill.isEmpty()) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.BAD_ARGUMENT,
                    "参数不对：缺少技能名"));
        }
        CardData card = member.card();
        Integer stored = card.skills().get(skill);
        if (stored == null) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.BAD_ARGUMENT,
                    "角色卡上没有技能：" + skill));
        }
        int skillValue = SessionPlaySupport.clamp(stored.intValue(), SKILL_MIN, SKILL_MAX);
        String countText = rawDiceCount == null ? "" : rawDiceCount.trim();
        int diceCount = 1;
        if (!countText.isEmpty()) {
            Integer parsedCount = SessionPlaySupport.parseInt(countText);
            if (parsedCount == null) {
                return PlayResult.rejected(sessions.reject(turn, SessionReject.BAD_ARGUMENT,
                        "参数不对：奖惩骰次数必须是整数：" + countText));
            }
            diceCount = parsedCount.intValue();
        }
        if (diceCount < 1 || diceCount > MAX_DICE_COUNT) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.BAD_ARGUMENT,
                    "参数不对：奖惩骰 1～" + MAX_DICE_COUNT + " 颗"));
        }
        int bonusDice = 0;
        int penaltyDice = 0;
        String diceKind = rawDiceKind == null ? "" : rawDiceKind.trim().toLowerCase(Locale.ROOT);
        if (diceKind.isEmpty()) {
            diceCount = 0;
        } else if ("bp".equals(diceKind) || "b".equals(diceKind)) {
            bonusDice = diceCount;
        } else if ("pp".equals(diceKind) || "p".equals(diceKind)) {
            penaltyDice = diceCount;
        } else {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.BAD_ARGUMENT,
                    "参数不对：奖惩骰只支持 bp 或 pp"));
        }
        SessionResult cooling = sessions.cooling(ACTION_CHECK, turn,
                config.defaultCooldownSeconds());
        if (cooling != null) {
            return PlayResult.rejected(cooling);
        }
        D100SkillCheck request;
        try {
            request = new D100SkillCheck(skill, skill, skillValue, bonusDice, penaltyDice);
        } catch (RuleException e) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.BAD_ARGUMENT,
                    "参数不对：技能值无法检定（" + e.getMessage() + "）"));
        }
        RuleSet ruleSet = RuleRegistry.getOrDefault(row.ruleSetId());
        if (ruleSet == null) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.STATE_UNAVAILABLE,
                    "本局规则集不可用"));
        }
        CheckResult result;
        try {
            result = ruleSet.resolve(request, random);
        } catch (RuleException e) {
            return PlayResult.rejected(SessionResult.storageFailure("检定解析失败，请稍后重试"));
        }
        if (result.success() && !recordGrowth(row, member, skill, turn.now)) {
            return PlayResult.rejected(SessionResult.storageFailure("成长记录未生效，请稍后重试"));
        }
        sessions.appendLog(row.id(), EVENT_CHECK, turn.actor.userId(),
                skill + " " + CheckResult.degreeLabel(result.degree()), turn.now);
        sessions.markCooldown(ACTION_CHECK, turn);
        StringBuilder note = new StringBuilder();
        String diceNote = diceNote(request, result.roll());
        if (!diceNote.isEmpty()) {
            note.append(diceNote);
        }
        if (kpRolled) {
            if (note.length() > 0) {
                note.append(' ');
            }
            note.append("（KP 代掷）");
        }
        String hints = checkHint(SessionPlaySupport.scriptOf(sessions, row), skill, result.success());
        SessionResult ok = SessionResult.ok(turn.reload(row), "");
        return PlayResult.of(ok, new SessionOutcomes.Check(ok, rollerUserId, skill,
                result.roll().normalized(), skillValue, result.degree(), note.toString(), hints));
    }

    /** 理智检定（§6.1 san）：损失达 5 追 INT 检定，失败则临时疯狂；归零永久疯狂退场。 */
    PlayResult<SessionOutcomes.San> san(SessionActor actor, final long targetUserId,
                                        final String rawLossSpec) {
        final SessionActor request = SessionService.requireActor(actor);
        if (!request.inGroup()) {
            return PlayResult.rejected(SessionResult.rejected(SessionReject.NOT_IN_GROUP, "请在群内使用"));
        }
        return sessions.withGroupLockValue(request, ACTION_SAN,
                new SessionService.LockedOutcome<SessionOutcomes.San>() {

                    @Override
                    public PlayResult<SessionOutcomes.San> run(SessionService.Turn turn) {
                        return sanInLock(turn, targetUserId, rawLossSpec);
                    }
                });
    }

    private PlayResult<SessionOutcomes.San> sanInLock(SessionService.Turn turn, long targetUserId,
                                                      String rawLossSpec) {
        SessionRow row = turn.active();
        if (row == null) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.STATE_UNAVAILABLE,
                    "本群暂无局"));
        }
        if (row.kpUserId() != turn.actor.userId()) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.NO_PERMISSION,
                    "只有 KP 能发起理智检定"));
        }
        if (row.state() != SessionState.RUNNING) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.STATE_UNAVAILABLE,
                    "本局尚未开局"));
        }
        if (targetUserId <= 0L) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.BAD_ARGUMENT,
                    "参数不对：缺少目标玩家"));
        }
        if (targetUserId == row.kpUserId()) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.MEMBER_STATE,
                    "KP 没有角色卡"));
        }
        MemberRow member = turn.member(row, targetUserId);
        if (member == null || member.leftAt() > 0L) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.MEMBER_STATE,
                    "目标不在本局内"));
        }
        String spec = rawLossSpec == null ? "" : rawLossSpec.trim();
        int slash = spec.indexOf('/');
        if (slash <= 0 || slash == spec.length() - 1) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.BAD_ARGUMENT,
                    "参数不对：理智损失写成 成功损失/失败损失，例如 1/1D6"));
        }
        DiceExpression successLoss = DiceExpressions.parse(spec.substring(0, slash));
        DiceExpression failureLoss = DiceExpressions.parse(spec.substring(slash + 1));
        if (successLoss == null || failureLoss == null) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.BAD_ARGUMENT,
                    "参数不对：理智损失只支持常数或 NdM+K"));
        }
        SessionResult cooling = sessions.cooling(ACTION_SAN, turn, config.defaultCooldownSeconds());
        if (cooling != null) {
            return PlayResult.rejected(cooling);
        }
        CardData card = member.card();
        int sanBefore = card.san();
        D100SanCheck request;
        try {
            request = new D100SanCheck(SessionPlaySupport.clamp(sanBefore, SKILL_MIN, SKILL_MAX),
                    successLoss, failureLoss);
        } catch (RuleException e) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.BAD_ARGUMENT,
                    "参数不对：理智值无法检定（" + e.getMessage() + "）"));
        }
        RuleSet ruleSet = RuleRegistry.getOrDefault(row.ruleSetId());
        if (ruleSet == null) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.STATE_UNAVAILABLE,
                    "本局规则集不可用"));
        }
        CheckResult result;
        try {
            result = ruleSet.resolve(request, random);
        } catch (RuleException e) {
            return PlayResult.rejected(SessionResult.storageFailure("理智检定解析失败，请稍后重试"));
        }
        int loss = result.sanLoss() == null ? 0 : result.sanLoss().intValue();
        int sanAfter = Math.max(0, sanBefore - loss);
        boolean permanent = sanAfter == 0;
        boolean maddened = false;
        long madnessUntil = member.madnessUntil();
        long leftAt = member.leftAt();
        if (permanent) {
            // 永久疯狂即退场，不再保留临时疯狂的结束时间，避免状态自相矛盾。
            leftAt = turn.now;
            madnessUntil = 0L;
        } else if (result.sanTriggerInt() && intCheckFailed(card, row.ruleSetId())) {
            maddened = true;
            long hours = random.roll(MADNESS_DIE_FACES);
            madnessUntil = turn.now + hours * MILLIS_PER_HOUR;
        }
        MemberRow updated = member.toBuilder()
                .card(card.withSan(sanAfter))
                .madnessUntil(madnessUntil)
                .leftAt(leftAt)
                .updateTime(turn.now)
                .build();
        int affected = store.updateMember(updated);
        if (affected < 0) {
            return PlayResult.rejected(SessionResult.storageFailure("理智状态未写入，请稍后重试"));
        }
        if (affected == 0) {
            return PlayResult.rejected(SessionResult.storageFailure("理智状态未写入，请稍后重试"));
        }
        sessions.appendLog(row.id(), EVENT_SAN, turn.actor.userId(),
                "理智 " + CheckResult.degreeLabel(result.degree()), turn.now);
        sessions.markCooldown(ACTION_SAN, turn);
        String hints = checkHint(SessionPlaySupport.scriptOf(sessions, row), "", result.success());
        SessionResult ok = SessionResult.ok(turn.reload(row), "");
        return PlayResult.of(ok, new SessionOutcomes.San(ok, targetUserId,
                result.roll().normalized(), sanBefore, result.degree(), sanBefore, sanAfter, loss,
                maddened, permanent, madnessUntil, hints));
    }

    /** 对抗检定（§6.1 opposed）：对手可以是玩家技能，也可以是 0～99 的目标值。 */
    PlayResult<SessionOutcomes.Opposed> opposed(SessionActor actor, final long leftUserId,
                                                final String rawLeftSkill, final long rightUserId,
                                                final String rawRightSkill,
                                                final int rightValue) {
        final SessionActor request = SessionService.requireActor(actor);
        if (!request.inGroup()) {
            return PlayResult.rejected(SessionResult.rejected(SessionReject.NOT_IN_GROUP, "请在群内使用"));
        }
        return sessions.withGroupLockValue(request, ACTION_OPPOSED,
                new SessionService.LockedOutcome<SessionOutcomes.Opposed>() {

                    @Override
                    public PlayResult<SessionOutcomes.Opposed> run(SessionService.Turn turn) {
                        return opposedInLock(turn, leftUserId, rawLeftSkill, rightUserId,
                                rawRightSkill, rightValue);
                    }
                });
    }

    private PlayResult<SessionOutcomes.Opposed> opposedInLock(SessionService.Turn turn,
                                                              long leftUserId, String rawLeftSkill,
                                                              long rightUserId, String rawRightSkill,
                                                              int rightValue) {
        SessionRow row = turn.active();
        if (row == null) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.STATE_UNAVAILABLE,
                    "本群暂无局"));
        }
        if (row.kpUserId() != turn.actor.userId()) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.NO_PERMISSION,
                    "只有 KP 能发起对抗检定"));
        }
        if (row.state() != SessionState.RUNNING) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.STATE_UNAVAILABLE,
                    "本局尚未开局"));
        }
        if (leftUserId <= 0L) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.BAD_ARGUMENT,
                    "参数不对：缺少对抗双方"));
        }
        String leftSkill = rawLeftSkill == null ? "" : rawLeftSkill.trim();
        if (leftSkill.isEmpty()) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.BAD_ARGUMENT,
                    "参数不对：缺少左侧技能名"));
        }
        if (leftUserId == row.kpUserId()) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.MEMBER_STATE,
                    "KP 没有角色卡"));
        }
        MemberRow left = turn.member(row, leftUserId);
        if (left == null || left.leftAt() > 0L) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.MEMBER_STATE,
                    "左侧玩家不在本局内"));
        }
        Integer leftStored = left.card().skills().get(leftSkill);
        if (leftStored == null) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.BAD_ARGUMENT,
                    "角色卡上没有技能：" + leftSkill));
        }
        String rightLabel;
        int rightTarget;
        if (rightUserId > 0L) {
            if (rightUserId == row.kpUserId()) {
                return PlayResult.rejected(sessions.reject(turn, SessionReject.MEMBER_STATE,
                        "KP 没有角色卡"));
            }
            MemberRow right = turn.member(row, rightUserId);
            if (right == null || right.leftAt() > 0L) {
                return PlayResult.rejected(sessions.reject(turn, SessionReject.MEMBER_STATE,
                        "右侧玩家不在本局内"));
            }
            String rightSkill = rawRightSkill == null ? "" : rawRightSkill.trim();
            if (rightSkill.isEmpty()) {
                return PlayResult.rejected(sessions.reject(turn, SessionReject.BAD_ARGUMENT,
                        "参数不对：缺少右侧技能名"));
            }
            Integer rightStored = right.card().skills().get(rightSkill);
            if (rightStored == null) {
                return PlayResult.rejected(sessions.reject(turn, SessionReject.BAD_ARGUMENT,
                        "角色卡上没有技能：" + rightSkill));
            }
            rightLabel = rightSkill;
            rightTarget = SessionPlaySupport.clamp(rightStored.intValue(), SKILL_MIN, SKILL_MAX);
        } else {
            if (rightValue < SKILL_MIN || rightValue > SKILL_MAX) {
                return PlayResult.rejected(sessions.reject(turn, SessionReject.BAD_ARGUMENT,
                        "参数不对：目标值需在 0～99 之间"));
            }
            rightLabel = OPPOSED_VALUE_LABEL;
            rightTarget = rightValue;
        }
        SessionResult cooling = sessions.cooling(ACTION_OPPOSED, turn,
                config.defaultCooldownSeconds());
        if (cooling != null) {
            return PlayResult.rejected(cooling);
        }
        D100SkillCheck leftRequest;
        D100SkillCheck rightRequest;
        try {
            leftRequest = new D100SkillCheck(leftSkill, leftSkill,
                    SessionPlaySupport.clamp(leftStored.intValue(), SKILL_MIN, SKILL_MAX));
            rightRequest = new D100SkillCheck(rightLabel, rightLabel, rightTarget);
        } catch (RuleException e) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.BAD_ARGUMENT,
                    "参数不对：技能值无法检定（" + e.getMessage() + "）"));
        }
        RuleSet ruleSet = RuleRegistry.getOrDefault(row.ruleSetId());
        if (ruleSet == null) {
            return PlayResult.rejected(sessions.reject(turn, SessionReject.STATE_UNAVAILABLE,
                    "本局规则集不可用"));
        }
        OpposedResult result;
        try {
            result = ruleSet.resolveOpposed(new OpposedCheck(leftRequest, rightRequest), random);
        } catch (RuleException e) {
            return PlayResult.rejected(SessionResult.storageFailure("对抗检定解析失败，请稍后重试"));
        }
        long winner = 0L;
        if (result.outcome() == OpposedOutcome.LEFT_WIN) {
            winner = leftUserId;
        } else if (result.outcome() == OpposedOutcome.RIGHT_WIN) {
            winner = rightUserId;
        }
        if (winner > 0L) {
            // 对抗胜出方同样计入成长候选（§7.6：成功或作为对抗胜出方使用该技能）。
            MemberRow winnerMember = winner == leftUserId ? left : turn.member(row, winner);
            String winnerSkill = winner == leftUserId ? leftSkill : rightLabel;
            if (winnerMember != null && !recordGrowth(row, winnerMember, winnerSkill, turn.now)) {
                return PlayResult.rejected(SessionResult.storageFailure("成长记录未生效，请稍后重试"));
            }
        }
        sessions.appendLog(row.id(), EVENT_OPPOSED, turn.actor.userId(),
                leftSkill + " vs " + rightLabel + " "
                        + CheckResult.degreeLabel(result.left().degree()), turn.now);
        sessions.markCooldown(ACTION_OPPOSED, turn);
        String hints = checkHint(SessionPlaySupport.scriptOf(sessions, row), leftSkill,
                result.left().success());
        SessionResult ok = SessionResult.ok(turn.reload(row), "");
        return PlayResult.of(ok, new SessionOutcomes.Opposed(ok, leftUserId,
                rightUserId > 0L ? rightUserId : 0L, leftSkill, rightLabel,
                result.left().roll().normalized(), result.right().roll().normalized(),
                SessionPlaySupport.clamp(leftStored.intValue(), SKILL_MIN, SKILL_MAX), rightTarget,
                result.left().degree(), result.right().degree(), winner,
                tieBreakLabel(result.decidedBy()), hints));
    }

    /** 把技能记入成长候选（检定成功或对抗胜出，§7.6）；写入失败返回 false。 */
    private boolean recordGrowth(SessionRow row, MemberRow member, String skill, long now) {
        List<String> merged = SessionPlaySupport.mergeSkill(member.successSkills(), skill);
        int affected = store.appendSuccessSkills(row.id(), member.userId(), merged, now);
        if (affected < 0) {
            return false;
        }
        if (affected == 0) {
            warn("成长技能写入未生效：session=" + row.id() + "，user=" + member.userId());
        }
        return true;
    }

    /** 奖惩骰说明：把被取用的十位骰写清楚，便于桌面上核对。 */
    private static String diceNote(D100SkillCheck request, RollTrace trace) {
        int bonus = request.bonusDice();
        int penalty = request.penaltyDice();
        if (bonus <= 0 && penalty <= 0) {
            return "";
        }
        if (bonus > 0 && penalty > 0) {
            return "奖励骰与惩罚骰各 " + Math.min(bonus, penalty) + " 颗已抵消。";
        }
        if (bonus > 0) {
            return "奖励骰 " + bonus + "：十位取 " + tensList(trace.baseTens(), trace.bonusTens())
                    + " 中较小值。";
        }
        return "惩罚骰 " + penalty + "：十位取 " + tensList(trace.baseTens(), trace.penaltyTens())
                + " 中较大值。";
    }

    private static String tensList(int baseTens, int[] dice) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < dice.length; i++) {
            if (i > 0) {
                text.append('、');
            }
            text.append(dice[i]);
        }
        return String.valueOf(baseTens) + (text.length() == 0 ? "" : "、" + text);
    }

    private boolean intCheckFailed(CardData card, String ruleSetId) {
        int intValue = SessionPlaySupport.clamp(card.attribute("INT"), SKILL_MIN, SKILL_MAX);
        try {
            D100SkillCheck request = new D100SkillCheck("INT", "INT", intValue);
            RuleSet ruleSet = RuleRegistry.getOrDefault(ruleSetId);
            if (ruleSet == null) {
                return true;
            }
            return !ruleSet.resolve(request, random).success();
        } catch (RuleException e) {
            warn("临时疯狂判定失败，按未通过处理：" + e.getMessage());
            return true;
        }
    }

    private static String checkHint(Script script, String skill, boolean success) {
        if (script == null) {
            return "";
        }
        java.util.LinkedHashSet<String> hints = new java.util.LinkedHashSet<String>();
        ScriptCheck matched = matchCheck(script, skill);
        if (matched != null) {
            SessionPlaySupport.addHint(hints, success ? matched.successHint() : matched.failureHint());
            for (ScriptTrigger trigger : script.triggers()) {
                if (trigger == null || !ScriptTrigger.WHEN_CHECK_RESOLVED.equals(trigger.when())) {
                    continue;
                }
                if (!matched.id().equals(trigger.checkId())) {
                    continue;
                }
                String result = trigger.result();
                boolean usable = ScriptTrigger.RESULT_ANY.equals(result)
                        || (ScriptTrigger.RESULT_SUCCESS.equals(result) && success)
                        || (ScriptTrigger.RESULT_FAILURE.equals(result) && !success);
                if (usable) {
                    SessionPlaySupport.addHint(hints, trigger.hint());
                }
            }
        }
        return SessionPlaySupport.joinHints(hints);
    }

    /** 按技能名匹配剧本里的独立检定；SAN 检定没有技能名，取第一条理智检定。 */
    private static ScriptCheck matchCheck(Script script, String skill) {
        List<ScriptCheck> checks = script.checks();
        for (ScriptCheck check : checks) {
            if (check == null) {
                continue;
            }
            if (ScriptCheck.TYPE_SKILL.equals(check.type())) {
                if (skill != null && skill.equals(check.skill())) {
                    return check;
                }
            }
        }
        if (skill != null && !skill.isEmpty()) {
            return null;
        }
        for (ScriptCheck check : checks) {
            if (check != null && ScriptCheck.TYPE_SAN.equals(check.type())) {
                return check;
            }
        }
        return null;
    }

    private static String tieBreakLabel(OpposedTieBreak tieBreak) {
        if (tieBreak == null) {
            return "";
        }
        switch (tieBreak) {
            case LEVEL:
                return "成功等级";
            case SKILL:
                return "技能值";
            case ROLL:
                return "骰面";
            case ROLL_TIE:
                return "重掷";
            case BOTH_FAIL:
                return "双方失败";
            case BOTH_FUMBLE:
                return "双方大失败";
            default:
                return "";
        }
    }

    private void warn(String message) {
        if (logger != null) {
            logger.sendWarn("[MBB-Trpg] " + message);
        }
    }
}
