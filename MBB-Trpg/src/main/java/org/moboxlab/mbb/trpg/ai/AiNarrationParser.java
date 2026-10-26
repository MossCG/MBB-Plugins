package org.moboxlab.mbb.trpg.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.moboxlab.mbb.trpg.present.TrpgMessageFormatter;

/**
 * AI 主持输出解析（纯函数，无状态）。
 *
 * <p>把模型原始输出收敛成可发送的叙述与可选建议行，并做防越权语义扫描：</p>
 * <ul>
 *   <li>清洗：剥 Markdown 代码围栏、行首列表记号与反引号，口径与
 *       {@link TrpgMessageFormatter#clean(String)} 一致；</li>
 *   <li>长度：叙述正文不超过 maxChars（默认 300，按码点计），超出截断并记 truncated；</li>
 *   <li>建议：只接受单独成行、以「建议：」开头的白名单行动，最多 3 条，
 *       非白名单行整行丢弃；</li>
 *   <li>防越权：命中数值变化、判定结论或替 KP 宣布推进时验证失败，返回拒绝原因。</li>
 * </ul>
 *
 * <p>本类只做文本判定，不发送任何消息；拒绝时 narration 为空，调用方据此丢弃重试。</p>
 */
public final class AiNarrationParser {

    /** 叙述正文默认上限（字）。 */
    public static final int DEFAULT_MAX_CHARS = 300;

    /** 建议行最多保留条数。 */
    public static final int MAX_SUGGESTIONS = 3;

    /** 建议行固定前缀。 */
    public static final String SUGGESTION_PREFIX = "建议：";

    /** 空输出或没有可发送的叙述。 */
    public static final String REASON_EMPTY = "空输出或没有可发送的叙述";

    /** 越权：修改或宣布数值。 */
    public static final String REASON_VALUE_CHANGE = "越权：修改或宣布数值";

    /** 越权：宣布判定结论。 */
    public static final String REASON_CHECK_VERDICT = "越权：宣布判定结论";

    /** 越权：替 KP 宣布推进。 */
    public static final String REASON_TURN_ADVANCE = "越权：替 KP 宣布推进";

    /**
     * 数值变化句式：数值类关键词后跟加减符号或变化动词，再跟数字。
     *
     * <p>只拦「数值变化」，不拦单独出现的 HP、SAN、理智等字样，避免误杀普通叙述。</p>
     */
    private static final Pattern VALUE_CHANGE = Pattern.compile(
            "(?:HP|MP|SAN|STR|CON|SIZ|DEX|APP|INT|POW|EDU|生命值?|理智值?|理智|魔法值?|"
            + "属性|技能|力量|敏捷|体质|智力|意志|教育|体型|外貌|幸运|体力)"
            + "\\s*(?:值)?\\s*"
            + "(?:[-+＋－]|减少|降低|下降|扣除|损失|失去|掉了|增加|提高|上升|恢复|回复|加|扣|减)"
            + "\\s*\\d",
            Pattern.CASE_INSENSITIVE);

    /** 判定结论句式：检定/判定/骰类词后紧跟成功、失败、通过等结论。 */
    private static final Pattern CHECK_VERDICT = Pattern.compile(
            "(?:检定|判定|投骰|掷骰|骰子|骰值|骰点|投掷|掷出|对抗)"
            + "\\s*(?:结果|的)?\\s*(?:为|是|：|:)?\\s*"
            + "(?:成功|失败|通过|未通过|不通过|大成功|大失败|极难成功|困难成功|普通成功|险胜|惨败|平手|胜出)"
            + "|(?:成功|失败|通过|未通过|大成功|大失败)"
            + "\\s*(?:了)?\\s*(?:通过)?\\s*(?:这次|这轮|本场)?\\s*(?:的)?\\s*(?:检定|判定)",
            Pattern.CASE_INSENSITIVE);

    /** 直接播报骰值/判定数值，属于替 KP 宣布结果。 */
    private static final Pattern ROLL_RESULT = Pattern.compile(
            "(?:骰值|骰点|骰子结果|检定结果|判定结果|投骰结果|掷骰结果|投掷结果)"
            + "\\s*(?:为|是|：|:|=)?\\s*\\d{1,3}"
            + "|d100\\s*[=:：]\\s*\\d{1,3}",
            Pattern.CASE_INSENSITIVE);

    /** 替 KP 宣布推进：进入下一场景/幕/回合，或直接宣布场景推进。 */
    private static final Pattern TURN_ADVANCE = Pattern.compile(
            "(?:进入|推进|切换|跳转|开始|开启)\\s*(?:到|至)?\\s*(?:下一|下个|接下来)\\s*(?:个)?\\s*(?:场景|幕|回合|轮)"
            + "|(?:下一|下个)\\s*(?:场景|幕|回合|轮)\\s*(?:开始|开启)"
            + "|(?:场景|幕|回合)\\s*(?:推进|前进)");

    /** 建议白名单动词：调查/观察、交谈/询问、移动/前往、等待/休息、使用/查看物品、尝试技能。 */
    private static final List<String> SUGGESTION_VERBS = Collections.unmodifiableList(Arrays.asList(
            "调查", "观察", "查看", "检查", "搜查", "搜索", "翻找", "寻找", "留意", "倾听", "聆听",
            "交谈", "询问", "对话", "搭话", "打听", "追问", "商量",
            "移动", "前往", "走向", "离开", "进入", "靠近", "上楼", "下楼", "返回", "退到",
            "等待", "休息", "停下", "驻足", "等",
            "使用", "拿出", "取出", "递给", "拿起",
            "尝试", "试着"));

    /** 建议里不允许出现的数字：只写行动方向，不写数值。 */
    private static final Pattern DIGITS = Pattern.compile("[0-9０-９]");

    /** 建议里不允许出现的结论词：不写判定结果。 */
    private static final Pattern RESULT_WORDS = Pattern.compile("(?:成功|失败|通过|大成功|大失败|胜出|惨败)");

    /** 清洗与计字复用呈现层口径。 */
    private static final TrpgMessageFormatter FORMATTER = new TrpgMessageFormatter();

    private AiNarrationParser() {
    }

    /**
     * 解析模型原始输出。
     *
     * @param raw      模型原始文本，可为 null
     * @param maxChars 叙述正文字数上限，&lt;=0 时取 {@link #DEFAULT_MAX_CHARS}
     * @return 解析结果，永不为 null；被拒绝时 narration 为空串
     */
    public static ParseResult parse(String raw, int maxChars) {
        int limit = maxChars > 0 ? maxChars : DEFAULT_MAX_CHARS;
        String cleaned = FORMATTER.clean(stripCodeFences(raw));

        List<String> narrationLines = new ArrayList<String>();
        List<String> suggestions = new ArrayList<String>();
        if (!cleaned.isEmpty()) {
            String[] lines = cleaned.split("\n", -1);
            for (String candidate : lines) {
                String line = candidate.trim();
                if (line.isEmpty()) {
                    continue;
                }
                String action = suggestionAction(line);
                if (action != null) {
                    if (suggestions.size() < MAX_SUGGESTIONS && validSuggestion(action)) {
                        suggestions.add(action);
                    }
                    continue;
                }
                narrationLines.add(line);
            }
        }

        StringBuilder narrationBuilder = new StringBuilder();
        for (String line : narrationLines) {
            if (narrationBuilder.length() > 0) {
                narrationBuilder.append('\n');
            }
            narrationBuilder.append(line);
        }
        String narration = narrationBuilder.toString().trim();
        if (narration.isEmpty()) {
            return reject(REASON_EMPTY);
        }
        String violation = detectViolation(narration);
        if (violation != null) {
            return reject(violation);
        }
        boolean truncated = false;
        if (FORMATTER.count(narration) > limit) {
            narration = FORMATTER.truncate(narration, limit);
            truncated = true;
            if (narration.isEmpty()) {
                return reject(REASON_EMPTY);
            }
        }
        return new ParseResult(true, narration, Collections.unmodifiableList(suggestions), truncated, "");
    }

    /** 提取「建议：」后的动作文本；不是建议行返回 null。 */
    private static String suggestionAction(String line) {
        if (line.startsWith(SUGGESTION_PREFIX)) {
            return line.substring(SUGGESTION_PREFIX.length()).trim();
        }
        if (line.startsWith("建议:")) {
            return line.substring("建议:".length()).trim();
        }
        return null;
    }

    /**
     * 建议白名单校验：必须落在六个大类内，且不写数值与判定结果。
     */
    private static boolean validSuggestion(String action) {
        if (action.isEmpty()) {
            return false;
        }
        if (DIGITS.matcher(action).find() || RESULT_WORDS.matcher(action).find()) {
            return false;
        }
        if (detectViolation(action) != null) {
            return false;
        }
        for (String verb : SUGGESTION_VERBS) {
            if (action.startsWith(verb)) {
                return true;
            }
        }
        return false;
    }

    /** 防越权语义扫描；命中返回拒绝原因，未命中返回 null。 */
    private static String detectViolation(String text) {
        if (VALUE_CHANGE.matcher(text).find()) {
            return REASON_VALUE_CHANGE;
        }
        if (CHECK_VERDICT.matcher(text).find() || ROLL_RESULT.matcher(text).find()) {
            return REASON_CHECK_VERDICT;
        }
        if (TURN_ADVANCE.matcher(text).find()) {
            return REASON_TURN_ADVANCE;
        }
        return null;
    }

    private static ParseResult reject(String reason) {
        return new ParseResult(false, "", Collections.<String>emptyList(), false, reason);
    }

    /**
     * 去掉 Markdown 代码围栏行（含语言标记），避免围栏语言名混进叙述。
     * 围栏内的正文保留，由后续清洗去掉反引号。
     */
    private static String stripCodeFences(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = raw.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder builder = new StringBuilder(normalized.length());
        String[] lines = normalized.split("\n", -1);
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                continue;
            }
            builder.append(line).append('\n');
        }
        return builder.toString();
    }

    /**
     * 解析结果（不可变）。
     *
     * <p>除访问器外，字段也声明为 public final，便于调用方直接读取；
     * 建议统一用访问器。</p>
     */
    public static final class ParseResult {

        /** 是否通过验证，可以发送。 */
        public final boolean valid;

        /** 叙述正文；valid=false 时为空串。 */
        public final String narration;

        /** 可选建议，已去掉「建议：」前缀，最多 3 条。 */
        public final List<String> suggestions;

        /** 叙述是否因超过 maxChars 被截断。 */
        public final boolean truncated;

        /** 拒绝原因；valid=true 时为空串。 */
        public final String rejectReason;

        private ParseResult(boolean valid, String narration, List<String> suggestions,
                            boolean truncated, String rejectReason) {
            this.valid = valid;
            this.narration = narration;
            this.suggestions = suggestions;
            this.truncated = truncated;
            this.rejectReason = rejectReason;
        }

        public boolean valid() {
            return valid;
        }

        public String narration() {
            return narration;
        }

        public List<String> suggestions() {
            return suggestions;
        }

        public boolean truncated() {
            return truncated;
        }

        public String rejectReason() {
            return rejectReason;
        }
    }
}
