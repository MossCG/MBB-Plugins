package org.moboxlab.mbb.trpg.rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 规则集注册表。
 *
 * <p>未知规则集回退内置 {@code d100} 并留下告警，由装配层启动时统一打印日志。</p>
 */
public final class RuleRegistry {

    /** 内置规则集 ID。 */
    public static final String BUILTIN_RULE_SET_ID = "d100";

    /** 告警条数上限，避免未知规则集反复回退时无限增长。 */
    private static final int MAX_WARNINGS = 20;

    private static final Map<String, RuleSet> RULE_SETS = new LinkedHashMap<String, RuleSet>();
    private static final Set<String> WARNINGS = new LinkedHashSet<String>();

    private RuleRegistry() {
    }

    /** 注册规则集；同 ID 覆盖。 */
    public static synchronized void register(RuleSet ruleSet) {
        if (ruleSet == null || isBlank(ruleSet.id())) {
            throw new IllegalArgumentException("规则集与规则集 ID 不能为空");
        }
        RULE_SETS.put(ruleSet.id(), ruleSet);
    }

    /** 注销规则集，主要供测试使用。 */
    public static synchronized void unregister(String ruleSetId) {
        RULE_SETS.remove(ruleSetId);
    }

    public static synchronized RuleSet get(String ruleSetId) {
        return ruleSetId == null ? null : RULE_SETS.get(ruleSetId);
    }

    /** 取规则集；未知 ID 回退内置 d100 并记告警，内置也未注册时返回 null。 */
    public static synchronized RuleSet getOrDefault(String ruleSetId) {
        RuleSet ruleSet = get(ruleSetId);
        if (ruleSet != null) {
            return ruleSet;
        }
        if (WARNINGS.size() < MAX_WARNINGS) {
            WARNINGS.add("未知规则集 " + ruleSetId + "，回退内置 " + BUILTIN_RULE_SET_ID);
        }
        return get(BUILTIN_RULE_SET_ID);
    }

    /** 已记录的告警，按发生顺序。 */
    public static synchronized List<String> warnings() {
        return Collections.unmodifiableList(new ArrayList<String>(WARNINGS));
    }

    public static synchronized void clearWarnings() {
        WARNINGS.clear();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
