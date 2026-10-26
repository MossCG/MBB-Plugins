package org.moboxlab.mbb.trpg.script;

import org.moboxlab.mbb.trpg.ai.AiRequest;

/**
 * 剧本生成的提示词装配（§docs/scripts/README.md「AI 生成流程」）。
 *
 * <p>系统提示词内置 v1 schema 摘要与硬约束，用户消息只放题材、人数与时长。
 * 生成结果最终仍以 {@link ScriptValidator#validateGenerated} 为准，提示词只是第一道闸。</p>
 */
final class ScriptGenerationPrompts {

    /** 生成请求的触发类型，用于日志与错误归类。 */
    static final String TRIGGER_TYPE = "trpg-script-gen";

    private static final String SYSTEM = String.join("\n",
            "你是单人跑团（TRPG）剧本作者。根据管理员的题材写一份可玩的剧本 JSON。",
            "",
            "只输出一个 JSON 对象：不要 Markdown 代码围栏，不要注释，不要任何解释文字。",
            "全部使用简体中文；public 与 narration 不得剧透 secret。",
            "",
            "顶层字段（除注明可选外都必填）：",
            "schemaVersion: 整数 1；id: \"gen-placeholder\"；name: 1-40 字；",
            "author: 1-40 字；description: 1-300 字；ruleSet: \"d100\"；",
            "minPlayers / maxPlayers: 1-20 的整数且 max>=min；estimatedMinutes: 15-600；",
            "tags: 最多 8 个短词（可选）；startingItems: 最多 20 个短句（可选）；",
            "scenes: 3-5 个；clues: 3-6 个；npcs: 1-3 个；checks: 2-4 个；triggers: 1-3 个；endings: 1-3 个。",
            "",
            "节点 id 用 ^[a-z0-9][a-z0-9-]{0,31}$，全剧本内唯一，所有引用必须指向存在的 id。",
            "scene: id、title(1-40 字，唯一)、summary(可选)、narration(1-1200 字)、",
            "  clueIds / npcIds / checkIds 为 id 数组（可选）。",
            "clue: id、name(1-40 字)、lockedSummary(可选)、unlockedText(1-800 字)。",
            "npc: id、name、role、public(1-500 字)、secret(可选，1-500 字，不对玩家展示)。",
            "check: id、type 为 \"d100.skill\" 或 \"d100.san\"；",
            "  d100.skill 必填 skill(技能名)，可选 difficulty(regular/hard/extreme)、bonusDice/penaltyDice(0-2)；",
            "  d100.san 必填 loss(形如 \"1/1d6\")，禁止 skill；prompt/successHint/failureHint 可选。",
            "trigger: id、when 为 scene-enter/clue-unlocked/check-resolved；",
            "  when=scene-enter 必填 sceneId；when=clue-unlocked 必填 clueId；when=check-resolved 必填 checkId 且可选 result(success/failure/any)；",
            "  hint 必填(1-200 字)。",
            "ending: id、title(1-40 字)、narration(1-1200 字)、requiredClueIds(可选，id 数组)。",
            "",
            "剧本要能按 scenes 顺序单次跑完，最后一个场景之后能进入 ending；不要数组里放 null。");

    private static final String USER_TEMPLATE = String.join("\n",
            "题材：%s",
            "玩家人数：%d 人（minPlayers 与 maxPlayers 都写 %d）",
            "预计时长：%d 分钟",
            "",
            "请直接输出剧本 JSON。");

    private ScriptGenerationPrompts() {
    }

    /** 装配一次生成请求：90 秒超时、6000 输出 token、低温度。 */
    static AiRequest build(String theme, int players, int minutes) {
        String dynamic = String.format(USER_TEMPLATE, theme == null ? "" : theme.trim(),
                players, players, minutes);
        return new AiRequest(TRIGGER_TYPE, SYSTEM, "", dynamic, 90, 6000, 0.3D);
    }
}
