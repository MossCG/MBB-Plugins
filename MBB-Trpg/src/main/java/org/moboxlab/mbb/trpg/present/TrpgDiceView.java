package org.moboxlab.mbb.trpg.present;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * /r 与 /roll 的回执数据：玩家输入的表达式、各段骰点与合计。
 *
 * <p>单段只回一行（{@code /r 3d6+2 = 14}）；多段每段一行（{@code [r] 3d6 = 9}），末行为合计。</p>
 */
public final class TrpgDiceView {

    private final String commandPrefix;
    private final String expression;
    private final List<String> segmentTexts;
    private final List<Integer> segmentValues;
    private final int total;

    public TrpgDiceView(String commandPrefix, String expression, List<String> segmentTexts,
                        List<Integer> segmentValues, int total) {
        this.commandPrefix = commandPrefix == null || commandPrefix.trim().isEmpty()
                ? "r" : commandPrefix.trim();
        this.expression = expression == null ? "" : expression.trim();
        this.segmentTexts = Collections.unmodifiableList(segmentTexts == null
                ? new ArrayList<String>() : new ArrayList<String>(segmentTexts));
        this.segmentValues = Collections.unmodifiableList(segmentValues == null
                ? new ArrayList<Integer>() : new ArrayList<Integer>(segmentValues));
        this.total = total;
    }

    /** 玩家实际使用的前缀（{@code r} 或 {@code roll}）。 */
    public String commandPrefix() {
        return commandPrefix;
    }

    /** 原始表达式，已去首尾空白。 */
    public String expression() {
        return expression;
    }

    /** 各段的原样书写（例如 {@code 3d6}、{@code 常量}）。 */
    public List<String> segmentTexts() {
        return segmentTexts;
    }

    /** 各段的掷点结果，与 {@link #segmentTexts()} 一一对应。 */
    public List<Integer> segmentValues() {
        return segmentValues;
    }

    /** 合计。 */
    public int total() {
        return total;
    }

    /** 段数。 */
    public int segmentCount() {
        return segmentTexts.size();
    }
}
