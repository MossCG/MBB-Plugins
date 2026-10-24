package org.moboxlab.mbb.trpg.ai;

/**
 * 三段提示词素材（§9.8）：稳定前缀、可缓存中段、动态段。
 *
 * <p>由 {@link AiPromptAssembler#fit} 产出，保证三段字数之和不超过预算。</p>
 */
public final class AiPromptParts {

    private final String stablePrefix;
    private final String cacheable;
    private final String dynamic;

    public AiPromptParts(String stablePrefix, String cacheable, String dynamic) {
        this.stablePrefix = stablePrefix == null ? "" : stablePrefix;
        this.cacheable = cacheable == null ? "" : cacheable;
        this.dynamic = dynamic == null ? "" : dynamic;
    }

    /** 稳定前缀：剧本核心设定与已登场 NPC 卡。 */
    public String stablePrefix() {
        return stablePrefix;
    }

    /** 可缓存中段：风格与规则约束摘要。 */
    public String cacheable() {
        return cacheable;
    }

    /** 动态段：本轮场景、事件、线索与固定系统提示。 */
    public String dynamic() {
        return dynamic;
    }

    /** 三段总字数（按呈现层口径计）。 */
    public int totalChars(org.moboxlab.mbb.trpg.present.TrpgMessageFormatter formatter) {
        return formatter.count(stablePrefix) + formatter.count(cacheable) + formatter.count(dynamic);
    }
}
