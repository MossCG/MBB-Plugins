package org.moboxlab.mbb.trpg.rule;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;
import org.moboxlab.mbb.trpg.testutil.FakeRandom;

/** d100 规则集契约测试，覆盖规格 §4.3 数值口径与 §12 的 G1–G7 验收点。 */
public class D100RuleSetTest {

    private final D100RuleSet rules = new D100RuleSet();

    // ===== 基础信息与支持范围 =====

    @Test
    public void exposesBuiltinIdAndDisplayName() {
        assertEquals(RuleRegistry.BUILTIN_RULE_SET_ID, rules.id());
        assertEquals("d100", rules.id());
        assertFalse(rules.displayName().isEmpty());
    }

    @Test
    public void supportsOnlyD100CheckTypes() {
        assertTrue(rules.supports(CheckTypes.D100_SKILL));
        assertTrue(rules.supports(CheckTypes.D100_SAN));
        assertTrue(rules.supports(CheckTypes.D100_GROWTH));
        assertFalse(rules.supports("coc7.skill"));
        assertFalse(rules.supports(null));
    }

    // ===== G1 六档成功等级与阈值 =====

    @Test
    public void mapsSixDegreesByThreshold() throws Exception {
        assertResult(rules.resolve(skill(60), new FakeRandom(12)), 3, true);
        assertResult(rules.resolve(skill(60), new FakeRandom(30)), 2, true);
        assertResult(rules.resolve(skill(60), new FakeRandom(45)), 1, true);
        assertResult(rules.resolve(skill(60), new FakeRandom(61)), 0, false);
    }

    @Test
    public void difficultyRaisesTheMinimumSuccessLevel() throws Exception {
        assertResult(rules.resolve(skill(60, CheckDifficulty.REGULAR, 0, 0), new FakeRandom(60)), 1, true);
        assertResult(rules.resolve(skill(60, CheckDifficulty.HARD, 0, 0), new FakeRandom(40)), 1, false);
        assertResult(rules.resolve(skill(60, CheckDifficulty.HARD, 0, 0), new FakeRandom(30)), 2, true);
        assertResult(rules.resolve(skill(60, CheckDifficulty.EXTREME, 0, 0), new FakeRandom(12)), 3, true);
        assertResult(rules.resolve(skill(60, CheckDifficulty.EXTREME, 0, 0), new FakeRandom(30)), 2, false);
    }

    // ===== G2 大成功与大失败分界 =====

    @Test
    public void lowSkillFumblesOn96To100() throws Exception {
        assertResult(rules.resolve(skill(49), new FakeRandom(95)), 0, false);
        assertResult(rules.resolve(skill(49), new FakeRandom(96)), CheckResult.DEGREE_FUMBLE, false);
        assertResult(rules.resolve(skill(49), new FakeRandom(100)), CheckResult.DEGREE_FUMBLE, false);
    }

    @Test
    public void highSkillFumblesOnlyOn100() throws Exception {
        assertResult(rules.resolve(skill(50), new FakeRandom(96)), 0, false);
        assertResult(rules.resolve(skill(50), new FakeRandom(100)), CheckResult.DEGREE_FUMBLE, false);
        assertResult(rules.resolve(skill(99), new FakeRandom(100)), CheckResult.DEGREE_FUMBLE, false);
    }

    @Test
    public void faceOneIsCriticalEvenAtExtremeDifficultyAndZeroSkill() throws Exception {
        assertResult(rules.resolve(skill(60, CheckDifficulty.EXTREME, 0, 0), new FakeRandom(1)),
                CheckResult.DEGREE_CRITICAL, true);
        assertResult(rules.resolve(skill(0), new FakeRandom(1)), CheckResult.DEGREE_CRITICAL, true);
    }

    // ===== G3 奖惩骰与抵消 =====

    @Test
    public void bonusDicePickLowestTens() throws Exception {
        FakeRandom random = new FakeRandom(45, 2, 10);
        CheckResult result = rules.resolve(skill(60, CheckDifficulty.REGULAR, 2, 0), random);
        assertEquals(15, result.roll().normalized());
        assertEquals(1, result.roll().selectedTens());
        assertArrayEquals(new int[]{1, 9}, result.roll().bonusTens());
        assertEquals(Arrays.asList(100, 10, 10), random.requestedSides());
    }

    @Test
    public void penaltyDicePickHighestTens() throws Exception {
        FakeRandom random = new FakeRandom(45, 10);
        CheckResult result = rules.resolve(skill(60, CheckDifficulty.REGULAR, 0, 1), random);
        assertEquals(95, result.roll().normalized());
        assertEquals(9, result.roll().selectedTens());
        assertArrayEquals(new int[]{9}, result.roll().penaltyTens());
    }

    @Test
    public void bonusAndPenaltyCancelOneToOne() throws Exception {
        CheckResult cancelled = rules.resolve(skill(60, CheckDifficulty.REGULAR, 2, 2),
                new FakeRandom(45, 1, 2, 5, 6));
        assertEquals(45, cancelled.roll().normalized());
        assertEquals(4, cancelled.roll().selectedTens());
        assertArrayEquals(new int[]{0, 1}, cancelled.roll().bonusTens());
        assertArrayEquals(new int[]{4, 5}, cancelled.roll().penaltyTens());

        CheckResult netPenalty = rules.resolve(skill(60, CheckDifficulty.REGULAR, 1, 2),
                new FakeRandom(5, 1, 3, 10));
        assertEquals(25, netPenalty.roll().normalized());
        assertEquals(2, netPenalty.roll().selectedTens());
    }

    @Test
    public void zeroTensZeroUnitsNormalizesToOneHundred() throws Exception {
        CheckResult withBonus = rules.resolve(skill(60, CheckDifficulty.REGULAR, 1, 0),
                new FakeRandom(100, 10));
        assertEquals(90, withBonus.roll().normalized());
        assertEquals(9, withBonus.roll().selectedTens());

        CheckResult withPenalty = rules.resolve(skill(60, CheckDifficulty.REGULAR, 0, 1),
                new FakeRandom(100, 1));
        assertEquals(100, withPenalty.roll().normalized());
        assertEquals(0, withPenalty.roll().selectedTens());
    }

    @Test
    public void rollTraceKeepsUnitsAndBaseTens() throws Exception {
        CheckResult result = rules.resolve(skill(45), new FakeRandom(45));
        RollTrace trace = result.roll();
        assertEquals(5, trace.units());
        assertEquals(4, trace.baseTens());
        assertEquals(4, trace.selectedTens());
        assertEquals(45, trace.normalized());
        assertEquals(0, trace.bonusTens().length);
        assertEquals(0, trace.penaltyTens().length);
    }

    // ===== G4 对抗判定平局链 =====

    @Test
    public void opposedDecidedByLevel() throws Exception {
        OpposedResult result = rules.resolveOpposed(opposed(skill(60), skill(50)), new FakeRandom(10, 20));
        assertEquals(OpposedOutcome.LEFT_WIN, result.outcome());
        assertEquals(OpposedTieBreak.LEVEL, result.decidedBy());
    }

    @Test
    public void opposedDecidedBySkillWhenLevelsTie() throws Exception {
        OpposedResult result = rules.resolveOpposed(opposed(skill(60), skill(50)), new FakeRandom(20, 20));
        assertEquals(OpposedOutcome.LEFT_WIN, result.outcome());
        assertEquals(OpposedTieBreak.SKILL, result.decidedBy());
    }

    @Test
    public void opposedDecidedByLowerRoll() throws Exception {
        OpposedResult result = rules.resolveOpposed(opposed(skill(50), skill(50)), new FakeRandom(20, 15));
        assertEquals(OpposedOutcome.RIGHT_WIN, result.outcome());
        assertEquals(OpposedTieBreak.ROLL, result.decidedBy());
    }

    @Test
    public void opposedRerollsOnceOnSameRoll() throws Exception {
        FakeRandom random = new FakeRandom(20, 20, 30, 40);
        OpposedResult result = rules.resolveOpposed(opposed(skill(50), skill(50)), random);
        assertEquals(OpposedOutcome.LEFT_WIN, result.outcome());
        assertEquals(OpposedTieBreak.ROLL_TIE, result.decidedBy());
        assertEquals(Arrays.asList(100, 100, 100, 100), random.requestedSides());
    }

    @Test
    public void opposedRerollKeepsBonusDiceOfBothSides() throws Exception {
        // 十位骰面 1..10 对应十位 0..9，面 2 即十位 1。首轮双方都取到 15 平局，
        // 重掷时左侧仍是十位 1（15），右侧十位 2（25），由骰面低者胜。
        FakeRandom random = new FakeRandom(45, 2, 45, 2, 45, 2, 45, 3);
        OpposedResult result = rules.resolveOpposed(
                opposed(skill(60, CheckDifficulty.REGULAR, 1, 0), skill(60, CheckDifficulty.REGULAR, 1, 0)),
                random);
        assertEquals(OpposedOutcome.LEFT_WIN, result.outcome());
        assertEquals(OpposedTieBreak.ROLL_TIE, result.decidedBy());
        assertEquals(Arrays.asList(100, 10, 100, 10, 100, 10, 100, 10), random.requestedSides());
    }

    @Test
    public void opposedNoneWhenRerollStillTies() throws Exception {
        OpposedResult result = rules.resolveOpposed(opposed(skill(50), skill(50)),
                new FakeRandom(20, 20, 30, 30));
        assertEquals(OpposedOutcome.NONE, result.outcome());
        assertEquals(OpposedTieBreak.ROLL_TIE, result.decidedBy());
    }

    @Test
    public void opposedBothFailIsNone() throws Exception {
        OpposedResult result = rules.resolveOpposed(opposed(skill(50), skill(50)), new FakeRandom(90, 95));
        assertEquals(OpposedOutcome.NONE, result.outcome());
        assertEquals(OpposedTieBreak.BOTH_FAIL, result.decidedBy());
    }

    @Test
    public void opposedBothFumbleIsNone() throws Exception {
        OpposedResult result = rules.resolveOpposed(opposed(skill(49), skill(49)), new FakeRandom(96, 100));
        assertEquals(OpposedOutcome.NONE, result.outcome());
        assertEquals(OpposedTieBreak.BOTH_FUMBLE, result.decidedBy());
    }

    @Test
    public void opposedSuccessBeatsFailureEvenWithLowerDegree() throws Exception {
        OpposedResult result = rules.resolveOpposed(
                opposed(skill(60, CheckDifficulty.HARD, 0, 0), skill(60, CheckDifficulty.EXTREME, 0, 0)),
                new FakeRandom(10, 20));
        assertEquals(OpposedOutcome.LEFT_WIN, result.outcome());
        assertEquals(OpposedTieBreak.LEVEL, result.decidedBy());
    }

    // ===== G5 理智检定 =====

    @Test
    public void sanSuccessUsesSuccessLoss() throws Exception {
        FakeRandom random = new FakeRandom(40, 2);
        CheckResult result = rules.resolve(san(60, DiceExpression.dice(1, 3, 0), DiceExpression.dice(1, 6, 0)), random);
        assertTrue(result.success());
        assertEquals(1, result.degree());
        assertEquals(Integer.valueOf(2), result.sanLoss());
        assertFalse(result.sanTriggerInt());
        assertEquals(Arrays.asList(100, 3), random.requestedSides());
    }

    @Test
    public void sanFailureUsesFailureLoss() throws Exception {
        FakeRandom random = new FakeRandom(70, 4);
        CheckResult result = rules.resolve(san(60, DiceExpression.dice(1, 3, 0), DiceExpression.dice(1, 6, 0)), random);
        assertFalse(result.success());
        assertEquals(CheckResult.DEGREE_FAILURE, result.degree());
        assertEquals(Integer.valueOf(4), result.sanLoss());
        assertFalse(result.sanTriggerInt());
    }

    @Test
    public void sanFumbleUsesMaxFailureLossAndTriggers() throws Exception {
        FakeRandom random = new FakeRandom(100);
        CheckResult result = rules.resolve(san(60, DiceExpression.dice(1, 3, 0), DiceExpression.dice(1, 6, 0)), random);
        assertFalse(result.success());
        assertEquals(CheckResult.DEGREE_FUMBLE, result.degree());
        assertEquals(Integer.valueOf(6), result.sanLoss());
        assertTrue(result.sanTriggerInt());
        assertEquals(Arrays.asList(100), random.requestedSides());
    }

    @Test
    public void lowSanFumblesOn96() throws Exception {
        CheckResult result = rules.resolve(san(30, DiceExpression.constant(0), DiceExpression.dice(1, 10, 0)),
                new FakeRandom(96));
        assertEquals(CheckResult.DEGREE_FUMBLE, result.degree());
        assertEquals(Integer.valueOf(10), result.sanLoss());
    }

    @Test
    public void sanTriggerThresholdIsFive() throws Exception {
        CheckResult five = rules.resolve(san(60, DiceExpression.constant(5), DiceExpression.dice(1, 6, 0)),
                new FakeRandom(10));
        assertEquals(Integer.valueOf(5), five.sanLoss());
        assertTrue(five.sanTriggerInt());

        CheckResult four = rules.resolve(san(60, DiceExpression.constant(4), DiceExpression.dice(1, 6, 0)),
                new FakeRandom(10));
        assertEquals(Integer.valueOf(4), four.sanLoss());
        assertFalse(four.sanTriggerInt());
    }

    // ===== G6 成长检定 =====

    @Test
    public void growthGainsOnlyWhenStrictlyGreater() throws Exception {
        CheckResult success = rules.resolve(new D100GrowthCheck(40), new FakeRandom(41, 7));
        assertTrue(success.success());
        assertEquals(CheckResult.DEGREE_REGULAR, success.degree());
        assertEquals(7, success.growthDelta());
        assertEquals(41, success.roll().normalized());

        FakeRandom equalRandom = new FakeRandom(40);
        CheckResult equal = rules.resolve(new D100GrowthCheck(40), equalRandom);
        assertFalse(equal.success());
        assertEquals(0, equal.growthDelta());
        assertEquals(0, equalRandom.remaining());
    }

    @Test
    public void growthCapsAtNinetyNine() throws Exception {
        CheckResult capped = rules.resolve(new D100GrowthCheck(95), new FakeRandom(99, 10));
        assertTrue(capped.success());
        assertEquals(4, capped.growthDelta());

        CheckResult atMax = rules.resolve(new D100GrowthCheck(99), new FakeRandom(100, 5));
        assertTrue(atMax.success());
        assertEquals(0, atMax.growthDelta());
    }

    // ===== 参数与异常 =====

    @Test(expected = RuleException.class)
    public void rejectsNullRequest() throws Exception {
        rules.resolve(null, new FakeRandom(1));
    }

    @Test(expected = RuleException.class)
    public void rejectsNullRandom() throws Exception {
        rules.resolve(skill(50), null);
    }

    @Test(expected = RuleException.class)
    public void rejectsUnsupportedCheckType() throws Exception {
        rules.resolve(new CheckRequest() {
            @Override
            public String type() {
                return "coc7.skill";
            }
        }, new FakeRandom(1));
    }

    @Test(expected = RuleException.class)
    public void rejectsUnknownRequestImplementation() throws Exception {
        rules.resolve(new CheckRequest() {
            @Override
            public String type() {
                return CheckTypes.D100_SKILL;
            }
        }, new FakeRandom(1));
    }

    @Test(expected = RuleException.class)
    public void rejectsNullOpposedCheck() throws Exception {
        rules.resolveOpposed(null, new FakeRandom(1, 1));
    }

    // ===== 辅助 =====

    private static D100SkillCheck skill(int value) throws RuleException {
        return new D100SkillCheck("spot", "侦察", value);
    }

    private static D100SkillCheck skill(int value, CheckDifficulty difficulty, int bonusDice, int penaltyDice)
            throws RuleException {
        return new D100SkillCheck("spot", "侦察", value, difficulty, bonusDice, penaltyDice);
    }

    private static D100SanCheck san(int currentSan, DiceExpression successLoss, DiceExpression failureLoss)
            throws RuleException {
        return new D100SanCheck(currentSan, successLoss, failureLoss);
    }

    private static OpposedCheck opposed(D100SkillCheck left, D100SkillCheck right) {
        return new OpposedCheck(left, right);
    }

    private static void assertResult(CheckResult result, int degree, boolean success) {
        assertEquals("等级不符，摘要：" + result.summary(), degree, result.degree());
        assertEquals("成功标记不符，摘要：" + result.summary(), success, result.success());
    }
}
