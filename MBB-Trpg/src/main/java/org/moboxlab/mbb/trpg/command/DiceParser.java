package org.moboxlab.mbb.trpg.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.moboxlab.mbb.trpg.rule.DiceExpression;

/**
 * /r 与 /roll 的表达式解析器。
 *
 * <p>v1 只支持「段相加」：每段是 {@code NdM} 或常数，用 {@code +} 连接，最多 {@value #MAX_SEGMENTS} 段。
 * 乘除、括号、负号一律不支持，解析失败返回 null 由指令层回「表达式不支持」。</p>
 */
public final class DiceParser {

    /** 最多段数。 */
    public static final int MAX_SEGMENTS = 3;
    /** 单段最多骰子数。 */
    public static final int MAX_DICE_COUNT = 20;
    /** 单段最多骰面数。 */
    public static final int MAX_DICE_SIDES = 1000;
    /** 常数段上限。 */
    public static final int MAX_CONSTANT = 999;

    private static final Pattern DICE_PATTERN = Pattern.compile("^(\\d{1,2})[dD](\\d{1,4})$");
    private static final Pattern CONSTANT_PATTERN = Pattern.compile("^\\d{1,3}$");

    private DiceParser() {
    }

    /**
     * 解析表达式。
     *
     * @param expression 玩家输入原样（参数已用空格拼接）
     * @return 解析结果，表达式为空或不合法返回 null
     */
    public static Parsed parse(String expression) {
        if (expression == null) {
            return null;
        }
        String trimmed = expression.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String[] parts = trimmed.split("\\+", -1);
        if (parts.length == 0 || parts.length > MAX_SEGMENTS) {
            return null;
        }
        List<DiceExpression> segments = new ArrayList<DiceExpression>();
        List<String> segmentTexts = new ArrayList<String>();
        for (String part : parts) {
            String token = part.trim();
            DiceExpression segment = parseSegment(token);
            if (segment == null) {
                return null;
            }
            segments.add(segment);
            // 常数段没有骰子可展示：留空串让回执跳过该行（§3.4 段行只列真正掷出的骰段）。
            segmentTexts.add(segment.count() > 0 ? token : "");
        }
        return new Parsed(trimmed, segments, segmentTexts);
    }

    private static DiceExpression parseSegment(String token) {
        if (token.isEmpty()) {
            return null;
        }
        Matcher dice = DICE_PATTERN.matcher(token);
        if (dice.matches()) {
            int count = Integer.parseInt(dice.group(1));
            int sides = Integer.parseInt(dice.group(2));
            if (count < 1 || count > MAX_DICE_COUNT || sides < 1 || sides > MAX_DICE_SIDES) {
                return null;
            }
            return DiceExpression.dice(count, sides, 0);
        }
        Matcher constant = CONSTANT_PATTERN.matcher(token);
        if (constant.matches()) {
            int value = Integer.parseInt(token);
            if (value > MAX_CONSTANT) {
                return null;
            }
            return DiceExpression.constant(value);
        }
        return null;
    }

    /** 解析结果：原始表达式（用于回执）与按书写顺序排列的段。 */
    public static final class Parsed {

        private final String expression;
        private final List<DiceExpression> segments;
        private final List<String> segmentTexts;

        private Parsed(String expression, List<DiceExpression> segments, List<String> segmentTexts) {
            this.expression = expression;
            this.segments = Collections.unmodifiableList(new ArrayList<DiceExpression>(segments));
            this.segmentTexts = Collections.unmodifiableList(new ArrayList<String>(segmentTexts));
        }

        /** 原始表达式，已去首尾空白。 */
        public String expression() {
            return expression;
        }

        /** 各段骰式。 */
        public List<DiceExpression> segments() {
            return segments;
        }

        /** 各段原样书写（保留玩家输入的大小写与形式，回执段行用）。 */
        public List<String> segmentTexts() {
            return segmentTexts;
        }

        /** 段数。 */
        public int size() {
            return segments.size();
        }
    }
}
