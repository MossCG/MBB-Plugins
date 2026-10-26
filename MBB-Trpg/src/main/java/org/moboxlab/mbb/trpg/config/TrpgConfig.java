package org.moboxlab.mbb.trpg.config;

import org.moboxlab.moboxbot.API.Util.PluginConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 插件配置的只读快照。
 *
 * <p>内置 44 项默认值，键名与 config.yml 完全一致（点号分层）。加载时只读一次，
 * 缺失键回退默认值，未知键与解析失败写入告警列表，由插件启动时统一打印。</p>
 *
 * <p>本类不依赖主程序内部类，只消费 {@link PluginConfig} 的字符串读取接口。</p>
 */
public final class TrpgConfig {

    /** 支持的值类型，用于加载时归一化。 */
    private enum Type {
        TEXT, INTEGER, LONG, BOOL
    }

    private static final Map<String, String> DEFAULTS;
    private static final Map<String, Type> TYPES;
    private static final Map<String, int[]> RANGES;

    static {
        Map<String, String> defaults = new LinkedHashMap<String, String>();
        Map<String, Type> types = new LinkedHashMap<String, Type>();
        Map<String, int[]> ranges = new LinkedHashMap<String, int[]>();

        put(defaults, types, "ruleset", "d100", Type.TEXT);

        put(defaults, types, "session.recruitExpireHours", "24", Type.INTEGER);
        put(defaults, types, "session.runningExpireDays", "7", Type.INTEGER);
        put(defaults, types, "session.confirmWindowSeconds", "30", Type.INTEGER);

        put(defaults, types, "cooldown.startSeconds", "30", Type.INTEGER);
        put(defaults, types, "cooldown.diceSeconds", "2", Type.INTEGER);
        put(defaults, types, "cooldown.defaultSeconds", "1", Type.INTEGER);

        put(defaults, types, "errorDedupeSeconds", "10", Type.INTEGER);
        put(defaults, types, "activityThrottleSeconds", "60", Type.INTEGER);

        put(defaults, types, "log.maxContentChars", "2000", Type.INTEGER);
        put(defaults, types, "log.pageSize", "10", Type.INTEGER);

        put(defaults, types, "retention.sessionDays", "30", Type.INTEGER);

        put(defaults, types, "presentation.style", "broadcast", Type.TEXT);
        put(defaults, types, "presentation.groupMaxChars", "500", Type.INTEGER);
        put(defaults, types, "presentation.privateMaxChars", "1200", Type.INTEGER);
        put(defaults, types, "presentation.splitMax", "3", Type.INTEGER);
        put(defaults, types, "presentation.sceneImage", "true", Type.BOOL);
        put(defaults, types, "presentation.reportImage", "true", Type.BOOL);
        put(defaults, types, "presentation.imageMaxLines", "22", Type.INTEGER);

        put(defaults, types, "script.maxBytes", "65536", Type.LONG);
        put(defaults, types, "script.importIdleMinutes", "5", Type.INTEGER);
        put(defaults, types, "script.importMaxMinutes", "30", Type.INTEGER);
        put(defaults, types, "script.importMaxSegments", "200", Type.INTEGER);
        put(defaults, types, "script.rejectedMaxFiles", "20", Type.INTEGER);
        put(defaults, types, "script.genCooldownSeconds", "60", Type.INTEGER);

        put(defaults, types, "aiHost.enabled", "false", Type.BOOL);
        put(defaults, types, "aiHost.deadlineSeconds", "45", Type.INTEGER);
        put(defaults, types, "aiHost.providerTimeoutSeconds", "10", Type.INTEGER);
        put(defaults, types, "aiHost.retryCount", "0", Type.INTEGER);
        put(defaults, types, "aiHost.maxConcurrent", "2", Type.INTEGER);
        put(defaults, types, "aiHost.perGroupQueue", "3", Type.INTEGER);
        put(defaults, types, "aiHost.globalQueue", "4", Type.INTEGER);
        put(defaults, types, "aiHost.queueMaxAgeSeconds", "65", Type.INTEGER);
        put(defaults, types, "aiHost.mergeWindowSeconds", "20", Type.INTEGER);
        put(defaults, types, "aiHost.mergeWindowEvents", "5", Type.INTEGER);
        put(defaults, types, "aiHost.suspendFailStreak", "3", Type.INTEGER);
        put(defaults, types, "aiHost.suspendMinutes", "10", Type.INTEGER);
        put(defaults, types, "aiHost.dedupeCacheSeconds", "60", Type.INTEGER);
        put(defaults, types, "aiHost.promptBudgetChars", "6000", Type.INTEGER);
        put(defaults, types, "aiHost.dynamicMinChars", "1500", Type.INTEGER);
        put(defaults, types, "aiHost.narrationMaxChars", "300", Type.INTEGER);
        put(defaults, types, "aiHost.traceEnabled", "true", Type.BOOL);
        put(defaults, types, "aiHost.traceKeepHours", "168", Type.INTEGER);
        put(defaults, types, "aiHost.traceMaxRows", "5000", Type.INTEGER);

        range(ranges, "session.recruitExpireHours", 1, 720);
        range(ranges, "session.runningExpireDays", 1, 365);
        range(ranges, "session.confirmWindowSeconds", 5, 300);
        range(ranges, "cooldown.startSeconds", 0, 3600);
        range(ranges, "cooldown.diceSeconds", 0, 60);
        range(ranges, "cooldown.defaultSeconds", 0, 60);
        range(ranges, "errorDedupeSeconds", 1, 300);
        range(ranges, "activityThrottleSeconds", 0, 86400);
        range(ranges, "log.maxContentChars", 100, 10000);
        range(ranges, "log.pageSize", 1, 50);
        range(ranges, "retention.sessionDays", 1, 3650);
        range(ranges, "presentation.groupMaxChars", 100, 4000);
        range(ranges, "presentation.privateMaxChars", 100, 5000);
        range(ranges, "presentation.splitMax", 1, 5);
        range(ranges, "presentation.imageMaxLines", 0, 100);
        range(ranges, "script.maxBytes", 1024, 1048576);
        range(ranges, "script.importIdleMinutes", 1, 1440);
        range(ranges, "script.importMaxMinutes", 1, 10080);
        range(ranges, "script.importMaxSegments", 1, 1000);
        range(ranges, "script.rejectedMaxFiles", 0, 200);
        range(ranges, "script.genCooldownSeconds", 0, 3600);
        range(ranges, "aiHost.deadlineSeconds", 1, 300);
        range(ranges, "aiHost.providerTimeoutSeconds", 2, 120);
        range(ranges, "aiHost.retryCount", 0, 3);
        range(ranges, "aiHost.maxConcurrent", 1, 32);
        range(ranges, "aiHost.perGroupQueue", 1, 100);
        range(ranges, "aiHost.globalQueue", 1, 200);
        range(ranges, "aiHost.queueMaxAgeSeconds", 10, 600);
        range(ranges, "aiHost.mergeWindowSeconds", 1, 300);
        range(ranges, "aiHost.mergeWindowEvents", 1, 50);
        range(ranges, "aiHost.suspendFailStreak", 1, 20);
        range(ranges, "aiHost.suspendMinutes", 1, 1440);
        range(ranges, "aiHost.dedupeCacheSeconds", 0, 3600);
        range(ranges, "aiHost.promptBudgetChars", 1000, 100000);
        range(ranges, "aiHost.dynamicMinChars", 0, 100000);
        range(ranges, "aiHost.narrationMaxChars", 50, 2000);
        range(ranges, "aiHost.traceKeepHours", 1, 8760);
        range(ranges, "aiHost.traceMaxRows", 100, 100000);

        DEFAULTS = Collections.unmodifiableMap(defaults);
        TYPES = Collections.unmodifiableMap(types);
        RANGES = Collections.unmodifiableMap(ranges);
    }

    private final Map<String, String> values;
    private final List<String> warnings;

    private TrpgConfig(Map<String, String> values, List<String> warnings) {
        this.values = Collections.unmodifiableMap(values);
        this.warnings = Collections.unmodifiableList(warnings);
    }

    private static void put(Map<String, String> defaults, Map<String, Type> types,
                            String key, String value, Type type) {
        defaults.put(key, value);
        types.put(key, type);
    }

    private static void range(Map<String, int[]> ranges, String key, int min, int max) {
        ranges.put(key, new int[]{min,max});
    }

    /** 全部默认值，键顺序与 config.yml 一致。 */
    public static Map<String, String> defaults() {
        return DEFAULTS;
    }

    /** 全部配置键。 */
    public static Set<String> keys() {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(DEFAULTS.keySet()));
    }

    /** 从主程序配置读取；缺失回默认，未知键与非法值进告警。 */
    public static TrpgConfig load(PluginConfig pluginConfig) {
        Map<String, String> raw = new LinkedHashMap<String, String>();
        if (pluginConfig != null) {
            for (String key : pluginConfig.keys()) {
                raw.put(key, pluginConfig.getString(key, ""));
            }
        }
        return fromValues(raw);
    }

    /** 从原始键值构造，供测试与无主程序环境的装配使用。 */
    public static TrpgConfig fromValues(Map<String, String> raw) {
        Map<String, String> normalized = new LinkedHashMap<String, String>();
        List<String> warnings = new ArrayList<String>();
        if (raw != null) {
            for (Map.Entry<String, String> entry : raw.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                if (!DEFAULTS.containsKey(entry.getKey())) {
                    warnings.add("未知配置项：" + entry.getKey() + "，已忽略");
                }
            }
        }
        for (Map.Entry<String, String> entry : DEFAULTS.entrySet()) {
            String key = entry.getKey();
            String provided = raw == null ? null : raw.get(key);
            if (provided == null || provided.trim().isEmpty()) {
                normalized.put(key, entry.getValue());
                continue;
            }
            String trimmed = provided.trim();
            if (isValid(trimmed, TYPES.get(key))) {
                normalized.put(key, trimmed);
            } else {
                warnings.add("配置项 " + key + " 的值 " + trimmed + " 非法，回退默认值 "
                        + entry.getValue());
                normalized.put(key, entry.getValue());
            }
        }

        String style = normalized.get("presentation.style");
        if (!"broadcast".equals(style)) {
            warnings.add("presentation.style 只支持 broadcast，当前为 " + style + "，已回退 broadcast");
            normalized.put("presentation.style", "broadcast");
        }
        for (Map.Entry<String, int[]> entry : RANGES.entrySet()) {
            String key = entry.getKey();
            long value;
            try {
                value = TYPES.get(key) == Type.LONG
                        ? Long.parseLong(normalized.get(key)) : Integer.parseInt(normalized.get(key));
            } catch (NumberFormatException ignored) {
                continue;
            }
            int[] bounds = entry.getValue();
            if (value < bounds[0] || value > bounds[1]) {
                String fallback = DEFAULTS.get(key);
                warnings.add("配置项 " + key + " 超出范围 " + bounds[0] + ".." + bounds[1]
                        + "，当前为 " + value + "，已回退默认值 " + fallback);
                normalized.put(key,fallback);
            }
        }
        int promptBudget = Integer.parseInt(normalized.get("aiHost.promptBudgetChars"));
        int dynamicMin = Integer.parseInt(normalized.get("aiHost.dynamicMinChars"));
        if (dynamicMin > promptBudget) {
            String fallback = DEFAULTS.get("aiHost.dynamicMinChars");
            warnings.add("配置项 aiHost.dynamicMinChars 不能大于 aiHost.promptBudgetChars，已回退默认值 "
                    + fallback);
            normalized.put("aiHost.dynamicMinChars",fallback);
        }
        return new TrpgConfig(normalized, warnings);
    }

    private static boolean isValid(String value, Type type) {
        if (type == null) {
            return true;
        }
        try {
            switch (type) {
                case INTEGER:
                    Integer.parseInt(value);
                    return true;
                case LONG:
                    Long.parseLong(value);
                    return true;
                case BOOL:
                    return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
                case TEXT:
                default:
                    return true;
            }
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** 加载期收集到的告警，按发现顺序排列。 */
    public List<String> warnings() {
        return warnings;
    }

    /** 原始字符串值；键必须在内置默认值列表中。 */
    public String raw(String key) {
        String value = values.get(key);
        if (value == null) {
            throw new IllegalArgumentException("未知配置键：" + key);
        }
        return value;
    }

    private int intValue(String key) {
        return Integer.parseInt(values.get(key));
    }

    private long longValue(String key) {
        return Long.parseLong(values.get(key));
    }

    private boolean boolValue(String key) {
        return Boolean.parseBoolean(values.get(key));
    }

    public String ruleSetId() {
        return values.get("ruleset");
    }

    public int recruitExpireHours() {
        return intValue("session.recruitExpireHours");
    }

    public int runningExpireDays() {
        return intValue("session.runningExpireDays");
    }

    public int confirmWindowSeconds() {
        return intValue("session.confirmWindowSeconds");
    }

    public int startCooldownSeconds() {
        return intValue("cooldown.startSeconds");
    }

    public int diceCooldownSeconds() {
        return intValue("cooldown.diceSeconds");
    }

    public int defaultCooldownSeconds() {
        return intValue("cooldown.defaultSeconds");
    }

    public int errorDedupeSeconds() {
        return intValue("errorDedupeSeconds");
    }

    public int activityThrottleSeconds() {
        return intValue("activityThrottleSeconds");
    }

    public int logMaxContentChars() {
        return intValue("log.maxContentChars");
    }

    public int logPageSize() {
        return intValue("log.pageSize");
    }

    public int retentionSessionDays() {
        return intValue("retention.sessionDays");
    }

    public String presentationStyle() {
        return values.get("presentation.style");
    }

    public int groupMaxChars() {
        return intValue("presentation.groupMaxChars");
    }

    public int privateMaxChars() {
        return intValue("presentation.privateMaxChars");
    }

    public int splitMax() {
        return intValue("presentation.splitMax");
    }

    public boolean sceneImage() {
        return boolValue("presentation.sceneImage");
    }

    public boolean reportImage() {
        return boolValue("presentation.reportImage");
    }

    public int imageMaxLines() {
        return intValue("presentation.imageMaxLines");
    }

    public long scriptMaxBytes() {
        return longValue("script.maxBytes");
    }

    public int scriptImportIdleMinutes() {
        return intValue("script.importIdleMinutes");
    }

    public int scriptImportMaxMinutes() {
        return intValue("script.importMaxMinutes");
    }

    public int scriptImportMaxSegments() {
        return intValue("script.importMaxSegments");
    }

    public int scriptRejectedMaxFiles() {
        return intValue("script.rejectedMaxFiles");
    }

    public int scriptGenCooldownSeconds() {
        return intValue("script.genCooldownSeconds");
    }

    public boolean aiHostEnabled() {
        return boolValue("aiHost.enabled");
    }

    public int aiDeadlineSeconds() {
        return intValue("aiHost.deadlineSeconds");
    }

    public int aiProviderTimeoutSeconds() {
        return intValue("aiHost.providerTimeoutSeconds");
    }

    public int aiRetryCount() {
        return intValue("aiHost.retryCount");
    }

    public int aiMaxConcurrent() {
        return intValue("aiHost.maxConcurrent");
    }

    public int aiPerGroupQueue() {
        return intValue("aiHost.perGroupQueue");
    }

    public int aiGlobalQueue() {
        return intValue("aiHost.globalQueue");
    }

    public int aiQueueMaxAgeSeconds() {
        return intValue("aiHost.queueMaxAgeSeconds");
    }

    public int aiMergeWindowSeconds() {
        return intValue("aiHost.mergeWindowSeconds");
    }

    public int aiMergeWindowEvents() {
        return intValue("aiHost.mergeWindowEvents");
    }

    public int aiSuspendFailStreak() {
        return intValue("aiHost.suspendFailStreak");
    }

    public int aiSuspendMinutes() {
        return intValue("aiHost.suspendMinutes");
    }

    public int aiDedupeCacheSeconds() {
        return intValue("aiHost.dedupeCacheSeconds");
    }

    public int aiPromptBudgetChars() {
        return intValue("aiHost.promptBudgetChars");
    }

    public int aiDynamicMinChars() {
        return intValue("aiHost.dynamicMinChars");
    }

    public int aiNarrationMaxChars() {
        return intValue("aiHost.narrationMaxChars");
    }

    public boolean aiTraceEnabled() {
        return boolValue("aiHost.traceEnabled");
    }

    public int aiTraceKeepHours() {
        return intValue("aiHost.traceKeepHours");
    }

    public int aiTraceMaxRows() {
        return intValue("aiHost.traceMaxRows");
    }
}
