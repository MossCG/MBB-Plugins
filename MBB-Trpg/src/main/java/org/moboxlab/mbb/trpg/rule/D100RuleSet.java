package org.moboxlab.mbb.trpg.rule;

/**
 * 内置 d100 规则集（CoC 第七版口径）。
 *
 * <p>纯函数：所有掷骰都经 {@link RandomSource}，不写存档、不发消息。口径要点：</p>
 * <ul>
 *   <li>骰面 00+0 归一化为 100；骰面 01 是大成功，任何难度都成功；</li>
 *   <li>大失败线取原始技能值：低于 50 时 96..100，达到 50 时仅 100；</li>
 *   <li>成功等级阈值：极难 ≤⌊技能值/5⌋、困难 ≤⌊技能值/2⌋、常规 ≤技能值；</li>
 *   <li>奖励骰与惩罚骰一比一抵消，抵消后奖励取最小归一值、惩罚取最大归一值；</li>
 *   <li>对抗按成功等级、技能值、骰面、重掷一次的顺序判定；</li>
 *   <li>理智检定成功扣成功损失、失败扣失败损失、大失败取失败损失上限；单次损失 ≥5
 *       置理智硬直标记；</li>
 *   <li>成长检定掷 1D100 严格大于技能值才成长 1D10，封顶 99。</li>
 * </ul>
 */
public final class D100RuleSet implements RuleSet {

    /** 规则集 ID。 */
    public static final String ID = RuleRegistry.BUILTIN_RULE_SET_ID;

    /** 单次理智损失达到该值即触发临时性疯狂判定。 */
    private static final int SAN_TRIGGER_THRESHOLD = 5;

    /** 低于该技能值时，96..100 都算大失败。 */
    private static final int HIGH_FUMBLE_SKILL_BOUNDARY = 50;

    /** 高技能值下唯一的大失败骰面。 */
    private static final int HIGH_FUMBLE_FACE = 100;

    /** 低技能值下大失败骰面的下界。 */
    private static final int LOW_FUMBLE_FLOOR = 96;

    /** 技能值上限。 */
    private static final int MAX_SKILL_VALUE = 99;

    /** 等级标签，下标为等级 + 1。 */
    private static final String[] DEGREE_LABELS = {
            "大失败", "失败", "常规成功", "困难成功", "极难成功", "大成功"
    };

    /** 成长掷 1D10 的档位：十位骰 0..9 加一得 1..10。 */
    private static final int GROWTH_DIE_OFFSET = 1;
    private static final int GROWTH_DIE_FACES = 10;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "d100（CoC 第七版口径）";
    }

    @Override
    public boolean supports(String checkType) {
        return CheckTypes.D100_SKILL.equals(checkType)
                || CheckTypes.D100_SAN.equals(checkType)
                || CheckTypes.D100_GROWTH.equals(checkType);
    }

    @Override
    public CheckResult resolve(CheckRequest request, RandomSource random) throws RuleException {
        if (request == null) {
            throw new RuleException("检定请求不能为空");
        }
        if (random == null) {
            throw new RuleException("随机源不能为空");
        }
        if (!supports(request.type())) {
            throw new RuleException("d100 规则集不支持检定类型：" + request.type());
        }
        if (request instanceof D100SkillCheck) {
            return resolveSkill((D100SkillCheck) request, random);
        }
        if (request instanceof D100SanCheck) {
            return resolveSan((D100SanCheck) request, random);
        }
        if (request instanceof D100GrowthCheck) {
            return resolveGrowth((D100GrowthCheck) request, random);
        }
        throw new RuleException("无法识别的 d100 检定请求：" + request.getClass().getName());
    }

    @Override
    public OpposedResult resolveOpposed(OpposedCheck check, RandomSource random) throws RuleException {
        if (check == null) {
            throw new RuleException("对抗检定不能为空");
        }
        if (random == null) {
            throw new RuleException("随机源不能为空");
        }
        return resolveOpposed(check.left(), check.right(), random);
    }

    private OpposedResult resolveOpposed(D100SkillCheck leftCheck, D100SkillCheck rightCheck,
                                         RandomSource random) throws RuleException {
        CheckResult left = resolveSkill(leftCheck, random);
        CheckResult right = resolveSkill(rightCheck, random);
        if (left.degree() == CheckResult.DEGREE_FUMBLE && right.degree() == CheckResult.DEGREE_FUMBLE) {
            return new OpposedResult(left, right, OpposedOutcome.NONE, OpposedTieBreak.BOTH_FUMBLE);
        }
        if (!left.success() && !right.success()) {
            return new OpposedResult(left, right, OpposedOutcome.NONE, OpposedTieBreak.BOTH_FAIL);
        }
        if (left.success() != right.success()) {
            return new OpposedResult(left, right,
                    left.success() ? OpposedOutcome.LEFT_WIN : OpposedOutcome.RIGHT_WIN,
                    OpposedTieBreak.LEVEL);
        }
        if (left.degree() != right.degree()) {
            return new OpposedResult(left, right,
                    left.degree() > right.degree() ? OpposedOutcome.LEFT_WIN : OpposedOutcome.RIGHT_WIN,
                    OpposedTieBreak.LEVEL);
        }
        if (leftCheck.skillValue() != rightCheck.skillValue()) {
            return new OpposedResult(left, right,
                    leftCheck.skillValue() > rightCheck.skillValue()
                            ? OpposedOutcome.LEFT_WIN : OpposedOutcome.RIGHT_WIN,
                    OpposedTieBreak.SKILL);
        }
        int leftRoll = left.roll().normalized();
        int rightRoll = right.roll().normalized();
        if (leftRoll != rightRoll) {
            return new OpposedResult(left, right,
                    leftRoll < rightRoll ? OpposedOutcome.LEFT_WIN : OpposedOutcome.RIGHT_WIN,
                    OpposedTieBreak.ROLL);
        }
        // 重掷一次：仍按双方原奖惩骰掷完整的 d100，只比较归一化骰面。
        int leftReroll = rollD100(leftCheck.bonusDice(), leftCheck.penaltyDice(), random).normalized();
        int rightReroll = rollD100(rightCheck.bonusDice(), rightCheck.penaltyDice(), random).normalized();
        if (leftReroll == rightReroll) {
            return new OpposedResult(left, right, OpposedOutcome.NONE, OpposedTieBreak.ROLL_TIE);
        }
        return new OpposedResult(left, right,
                leftReroll < rightReroll ? OpposedOutcome.LEFT_WIN : OpposedOutcome.RIGHT_WIN,
                OpposedTieBreak.ROLL_TIE);
    }

    private CheckResult resolveSkill(D100SkillCheck check, RandomSource random) throws RuleException {
        RollTrace roll = rollD100(check.bonusDice(), check.penaltyDice(), random);
        int degree = degreeOf(roll.normalized(), check.skillValue());
        boolean success = isSuccess(degree, check.difficulty());
        String summary = skillSummary(check, degree, success, roll.normalized());
        return new CheckResult(success, degree, roll, summary, null, false, 0);
    }

    /**
     * 理智检定：大失败取失败损失上限，成功扣 X，失败扣 Y；单次损失 ≥5 置硬直标记。
     *
     * <p>理智归零后的永久疯狂退场由上层用 {@code currentSan} 与 {@code sanLoss} 判定，
     * 本规则集只回报扣点，不改角色状态。</p>
     */
    private CheckResult resolveSan(D100SanCheck check, RandomSource random) throws RuleException {
        RollTrace roll = rollD100(0, 0, random);
        int degree = degreeOf(roll.normalized(), check.currentSan());
        int loss;
        if (degree == CheckResult.DEGREE_FUMBLE) {
            loss = check.failureLoss().maxValue();
        } else if (degree >= CheckResult.DEGREE_REGULAR) {
            loss = check.successLoss().roll(random);
        } else {
            loss = check.failureLoss().roll(random);
        }
        boolean trigger = loss >= SAN_TRIGGER_THRESHOLD;
        String summary = "理智检定" + degreeLabel(degree) + "（掷出 " + roll.normalized()
                + "），损失 " + loss;
        return new CheckResult(degree >= CheckResult.DEGREE_REGULAR, degree, roll, summary,
                Integer.valueOf(loss), trigger, 0);
    }

    private CheckResult resolveGrowth(D100GrowthCheck check, RandomSource random) throws RuleException {
        int rolled = rollPercentile(random);
        int tens = tensOf(rolled);
        RollTrace roll = new RollTrace(unitsOf(rolled), tens, new int[0], new int[0], tens, rolled);
        boolean success = rolled > check.skillValue();
        int delta = 0;
        if (success) {
            int gain = rollGrowthDie(random);
            int cap = MAX_SKILL_VALUE - check.skillValue();
            delta = Math.min(gain, Math.max(0, cap));
        }
        int degree = success ? CheckResult.DEGREE_REGULAR : CheckResult.DEGREE_FAILURE;
        String summary;
        if (!success) {
            summary = "成长检定失败（掷出 " + rolled + " ≤ " + check.skillValue() + "）";
        } else if (delta == 0) {
            summary = "成长检定成功（掷出 " + rolled + "），技能值已封顶";
        } else {
            summary = "成长检定成功（掷出 " + rolled + "），技能值 +" + delta;
        }
        return new CheckResult(success, degree, roll, summary, null, false, delta);
    }

    /** 掷一次 d100 并选择奖惩骰后的十位；抵消后的奖励取最小归一值，惩罚取最大归一值。 */
    private RollTrace rollD100(int bonusDice, int penaltyDice, RandomSource random) throws RuleException {
        int base = rollPercentile(random);
        int units = unitsOf(base);
        int baseTens = tensOf(base);
        int[] bonusTens = new int[bonusDice];
        for (int i = 0; i < bonusDice; i++) {
            bonusTens[i] = rollTensDie(random);
        }
        int[] penaltyTens = new int[penaltyDice];
        for (int i = 0; i < penaltyDice; i++) {
            penaltyTens[i] = rollTensDie(random);
        }
        // 十位骰先全部掷出再抵消：被抵消的骰面仍进 RollTrace，便于战报复盘原始骰面。
        int cancelled = Math.min(bonusDice, penaltyDice);
        int remainingBonus = bonusDice - cancelled;
        int remainingPenalty = penaltyDice - cancelled;
        int selectedTens = baseTens;
        int selected = base;
        if (remainingBonus > 0) {
            for (int i = 0; i < remainingBonus; i++) {
                int candidate = normalize(bonusTens[i], units);
                if (candidate < selected) {
                    selected = candidate;
                    selectedTens = bonusTens[i];
                }
            }
        } else if (remainingPenalty > 0) {
            for (int i = 0; i < remainingPenalty; i++) {
                int candidate = normalize(penaltyTens[i], units);
                if (candidate > selected) {
                    selected = candidate;
                    selectedTens = penaltyTens[i];
                }
            }
        }
        return new RollTrace(units, baseTens, bonusTens, penaltyTens, selectedTens, selected);
    }

    private static int rollPercentile(RandomSource random) throws RuleException {
        int face = random.roll(100);
        if (face < 1 || face > 100) {
            throw new RuleException("d100 骰面必须在 1..100 之间，实际：" + face);
        }
        return face;
    }

    /** 成长收益 1..10：十位骰 0..9 加一，等价 1D10。 */
    private static int rollGrowthDie(RandomSource random) throws RuleException {
        int gain = rollTensDie(random) + GROWTH_DIE_OFFSET;
        if (gain < 1 || gain > GROWTH_DIE_FACES) {
            throw new RuleException("成长骰面必须在 1..10 之间，实际：" + gain);
        }
        return gain;
    }

    private static int rollTensDie(RandomSource random) throws RuleException {
        int digit = random.d10();
        if (digit < 0 || digit > 9) {
            throw new RuleException("十位骰面必须在 0..9 之间，实际：" + digit);
        }
        return digit;
    }

    private static int unitsOf(int percentile) {
        return percentile % 10;
    }

    private static int tensOf(int percentile) {
        return (percentile % 100) / 10;
    }

    /** 十位 0 且个位 0 归一化为 100，其余为十位 ×10 + 个位。 */
    private static int normalize(int tensDigit, int units) {
        if (tensDigit == 0 && units == 0) {
            return 100;
        }
        return tensDigit * 10 + units;
    }

    /** 按原始技能值判定等级：01 大成功，随后大失败，再按难度阈值逐档。 */
    private static int degreeOf(int normalized, int skillValue) {
        if (normalized == 1) {
            return CheckResult.DEGREE_CRITICAL;
        }
        if (isFumble(normalized, skillValue)) {
            return CheckResult.DEGREE_FUMBLE;
        }
        if (normalized <= successThreshold(skillValue, CheckDifficulty.EXTREME)) {
            return CheckResult.DEGREE_EXTREME;
        }
        if (normalized <= successThreshold(skillValue, CheckDifficulty.HARD)) {
            return CheckResult.DEGREE_HARD;
        }
        if (normalized <= successThreshold(skillValue, CheckDifficulty.REGULAR)) {
            return CheckResult.DEGREE_REGULAR;
        }
        return CheckResult.DEGREE_FAILURE;
    }

    private static boolean isFumble(int normalized, int skillValue) {
        if (skillValue < HIGH_FUMBLE_SKILL_BOUNDARY) {
            return normalized >= LOW_FUMBLE_FLOOR;
        }
        return normalized == HIGH_FUMBLE_FACE;
    }

    /** 难度只抬高算成功的最低档；大成功必成功、大失败必失败。 */
    private static boolean isSuccess(int degree, CheckDifficulty difficulty) {
        if (degree == CheckResult.DEGREE_CRITICAL) {
            return true;
        }
        if (degree == CheckResult.DEGREE_FUMBLE) {
            return false;
        }
        switch (difficulty) {
            case EXTREME:
                return degree >= CheckResult.DEGREE_EXTREME;
            case HARD:
                return degree >= CheckResult.DEGREE_HARD;
            default:
                return degree >= CheckResult.DEGREE_REGULAR;
        }
    }

    /** 摘要只陈述事实：达成难度用「≤ 本次阈值」，未达成用「> 本次阈值」，不写「成功」误导。 */
    private static String skillSummary(D100SkillCheck check, int degree, boolean success, int normalized) {
        String label = degreeLabel(degree);
        if (degree == CheckResult.DEGREE_CRITICAL || degree == CheckResult.DEGREE_FUMBLE) {
            return label + "（掷出 " + normalized + "）";
        }
        int threshold = successThreshold(check.skillValue(), check.difficulty());
        return success
                ? label + "（掷出 " + normalized + " ≤ " + threshold + "）"
                : label + "（掷出 " + normalized + " > " + threshold + "）";
    }

    /** 各难度「算成功」的骰面上限，也是摘要里的比较阈值。 */
    private static int successThreshold(int skillValue, CheckDifficulty difficulty) {
        switch (difficulty) {
            case EXTREME:
                return skillValue / 5;
            case HARD:
                return skillValue / 2;
            default:
                return skillValue;
        }
    }

    private static String degreeLabel(int degree) {
        return DEGREE_LABELS[degree + 1];
    }
}
