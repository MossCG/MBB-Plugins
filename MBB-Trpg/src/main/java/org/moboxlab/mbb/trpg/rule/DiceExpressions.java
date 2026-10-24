package org.moboxlab.mbb.trpg.rule;

/**
 * 表达式文本解析：把 /trpg san 与剧本里的损失写法转成 {@link DiceExpression}。
 *
 * <p>支持两种形态：常数（0～999）与 "NdM" 或 "NdM+K"（N 1～20、M 1～1000、K 0～999）。
 * 解析失败返回 null，由调用方按参数不合法拒绝。</p>
 */
public final class DiceExpressions {

    /** 常数上限。 */
    public static final int MAX_CONSTANT = 999;
    /** 骰数上限。 */
    public static final int MAX_DICE_COUNT = 20;
    /** 面数上限。 */
    public static final int MAX_DICE_SIDES = 1000;
    /** 加值上限。 */
    public static final int MAX_MODIFIER = 999;

    private DiceExpressions() {
    }

    /**
     * 解析表达式。
     *
     * @param text 表达式原文；允许首尾空白
     * @return 解析结果；文本为空或格式不支持时返回 null
     */
    public static DiceExpression parse(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        if (value.isEmpty()) {
            return null;
        }
        int diceAt = indexOfDice(value);
        if (diceAt < 0) {
            return constant(value);
        }
        String countText = value.substring(0, diceAt);
        String sidesText = value.substring(diceAt + 1);
        int modifier = 0;
        int plusAt = sidesText.indexOf('+');
        if (plusAt >= 0) {
            String modifierText = sidesText.substring(plusAt + 1);
            sidesText = sidesText.substring(0, plusAt);
            if (!digits(modifierText)) {
                return null;
            }
            modifier = Integer.parseInt(modifierText);
            if (modifier > MAX_MODIFIER) {
                return null;
            }
        }
        if (!digits(countText) || !digits(sidesText)) {
            return null;
        }
        int count = Integer.parseInt(countText);
        int sides = Integer.parseInt(sidesText);
        if (count < 1 || count > MAX_DICE_COUNT || sides < 1 || sides > MAX_DICE_SIDES) {
            return null;
        }
        return DiceExpression.dice(count, sides, modifier);
    }

    private static DiceExpression constant(String value) {
        if (!digits(value)) {
            return null;
        }
        int constant = Integer.parseInt(value);
        if (constant > MAX_CONSTANT) {
            return null;
        }
        return DiceExpression.constant(constant);
    }

    private static int indexOfDice(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == 'd' || c == 'D') {
                return i;
            }
        }
        return -1;
    }

    private static boolean digits(String value) {
        if (value.isEmpty() || value.length() > 4) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}
