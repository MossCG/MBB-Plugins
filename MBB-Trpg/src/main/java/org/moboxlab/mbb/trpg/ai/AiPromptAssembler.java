package org.moboxlab.mbb.trpg.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.moboxlab.mbb.trpg.present.TrpgMessageFormatter;
import org.moboxlab.mbb.trpg.script.Clue;
import org.moboxlab.mbb.trpg.script.Npc;
import org.moboxlab.mbb.trpg.script.Scene;
import org.moboxlab.mbb.trpg.script.Script;
import org.moboxlab.mbb.trpg.script.ScriptCatalog;
import org.moboxlab.mbb.trpg.session.MemberView;
import org.moboxlab.mbb.trpg.session.SessionSnapshot;
import org.moboxlab.mbb.trpg.store.CardData;

/**
 * AI 主持提示词装配（纯函数，无状态）。
 *
 * <p>提示词按三段输出，交给调用方原样放进请求：</p>
 * <ul>
 *   <li>稳定前缀：剧本核心设定与已登场 NPC 卡，一局内不变，适合缓存；</li>
 *   <li>可缓存中段：叙述风格与规则约束摘要，一局内不变；</li>
 *   <li>动态区：当前场景、在场角色公开状态、最近事件、线索、KP 最近叙述，
 *       以及固定系统提示；按优先级裁剪，动态区至少保留动态最小字数。</li>
 * </ul>
 *
 * <p>所有方法都容忍 null 入参，按空内容处理，不抛异常。素材按
 * {@link TrpgMessageFormatter} 的口径清洗与计字，保证与群消息一致。</p>
 */
public final class AiPromptAssembler {

    /** 提示词总预算默认值（字）。 */
    public static final int DEFAULT_PROMPT_BUDGET_CHARS = 6000;

    /** 动态区最少保留字数默认值（字）。 */
    public static final int DEFAULT_DYNAMIC_MIN_CHARS = 1500;

    /** 叙述正文上限默认值（字），与输出契约保持一致。 */
    public static final int DEFAULT_NARRATION_MAX_CHARS = 300;

    /** 最近事件最多保留的行数，避免旧事件挤占动态区。 */
    private static final int MAX_EVENT_LINES = 12;

    /** KP 最近叙述最多保留的行数。 */
    private static final int MAX_KP_LINES = 4;

    private static final String GENERIC_SETTING =
            "本局没有可用的剧本场景设定，按通用悬疑模板处理：只依据动态区给出的事实叙述，"
            + "不虚构世界观、人物、线索与数值。";

    /** 动态区固定的系统提示，任何预算下都不裁剪。 */
    private static final String SYSTEM_PROMPT = String.join("\n",
            "【系统提示（必须遵守）】",
            "你是本局 TRPG 的回合叙述员，只把系统已经确认的事实转写成有画面感的短叙述。",
            "不得修改或宣布任何数值（生命、理智、属性、技能等），不得代替 KP 下判定或宣布判定结果，",
            "不得推进回合、切换场景或替 KP 宣布剧情推进，不得复述系统播报与指令回执。",
            "叙述正文控制在 300 字以内；只输出叙述正文，可选地另起一行输出以「建议：」开头的行动建议（最多 3 条）。");

    /** 清洗与计字复用呈现层口径，保证提示词素材和群消息一致。 */
    private static final TrpgMessageFormatter FORMATTER = new TrpgMessageFormatter();

    private AiPromptAssembler() {
    }

    /**
     * 稳定前缀：剧本核心设定 + 已登场 NPC 卡。
     *
     * <p>已登场按当前场景序号判断（含当前场景）；剧本为 null 或没有场景时
     * 回退到通用悬疑模板说明。</p>
     */
    public static String stablePrefix(SessionSnapshot snapshot, Script script) {
        StringBuilder builder = new StringBuilder(1024);
        builder.append("【剧本核心设定】\n");
        if (script == null) {
            builder.append("剧本：通用悬疑模板（本局未绑定剧本）\n");
            builder.append(GENERIC_SETTING);
        } else {
            appendLine(builder, "剧本", text(script.name()));
            appendLine(builder, "简介", text(script.description()));
            appendLine(builder, "规则", text(script.ruleSet()));
            if (!script.tags().isEmpty()) {
                String tags = joinText(script.tags(), "、");
                if (!tags.isEmpty()) {
                    builder.append("题材：").append(tags).append('\n');
                }
            }
            if (script.scenes().isEmpty()) {
                builder.append("说明：").append(GENERIC_SETTING).append('\n');
            }
        }
        List<Npc> npcs = appearedNpcs(snapshot, script);
        if (!npcs.isEmpty()) {
            builder.append("\n【已登场 NPC】\n");
            for (Npc npc : npcs) {
                appendNpcCard(builder, npc);
            }
        }
        return builder.toString().trim();
    }

    /**
     * 稳定前缀的便利重载：剧本未解析出来时，按快照的剧本编号从目录补一次。
     *
     * <p>调用方已经持有 {@link ScriptCatalog} 时可直接传三参，省掉一次自己解析；
     * 目录为 null、编号为空或读取失败都按无剧本处理，退回通用模板，不抛异常。</p>
     */
    public static String stablePrefix(SessionSnapshot snapshot, Script script, ScriptCatalog catalog) {
        Script resolved = script;
        if (resolved == null && catalog != null && snapshot != null) {
            String scriptId = text(snapshot.scriptId());
            if (!scriptId.isEmpty()) {
                try {
                    resolved = catalog.get(scriptId);
                } catch (RuntimeException error) {
                    resolved = null;
                }
            }
        }
        return stablePrefix(snapshot, resolved);
    }

    /**
     * 可缓存中段：叙述风格与规则约束摘要，一局内稳定，可与稳定前缀一起缓存。
     */
    public static String cacheable(SessionSnapshot snapshot) {
        StringBuilder builder = new StringBuilder(768);
        builder.append("【风格与规则约束】\n");
        builder.append("- 叙述风格：短句为主，写清环境、声音、气味与角色可见的动作和表情；"
                + "不替玩家决定行动与台词。\n");
        builder.append("- 规则边界：判定、数值变化、线索解锁与场景推进都由系统与 KP 完成，"
                + "你只转述已经确认的事实。\n");
        builder.append("- 信息边界：NPC 的秘密、未解锁线索的原文、玩家数值都不得出现在玩家可见叙述里。\n");
        builder.append("- 建议格式：如给出行动建议，另起一行以「建议：」开头，最多 3 条；"
                + "只写行动方向，不写技能数值与结果。\n");
        String ruleSetId = snapshot == null ? "" : text(snapshot.ruleSetId());
        if (!ruleSetId.isEmpty()) {
            builder.append("- 本局规则系统：").append(ruleSetId).append("；结算由系统完成。\n");
        }
        builder.append("- 输出长度：叙述正文不超过 300 字。");
        return builder.toString();
    }

    /**
     * 动态区：当前场景、在场角色、最近事件、线索、KP 最近叙述与固定系统提示。
     *
     * <p>{@code budgetChars} 按「稳定前缀 + 可缓存中段 + 动态区」的总预算解释：
     * 动态区预算 = {@code max(dynamicMinChars, budgetChars - 前两段字数)}；
     * 素材按声明顺序逐块装入，放不下的块截断后停止，因此低优先级素材先被舍弃。
     * 固定系统提示不参与裁剪，任何预算下都会完整保留。</p>
     */
    public static String dynamic(SessionSnapshot snapshot, Script script,
                                 List<String> recentEventLines, int budgetChars, int dynamicMinChars) {
        int totalBudget = budgetChars > 0 ? budgetChars : DEFAULT_PROMPT_BUDGET_CHARS;
        int minChars = dynamicMinChars > 0 ? dynamicMinChars : 0;
        int prefixChars = FORMATTER.count(stablePrefix(snapshot, script))
                + FORMATTER.count(cacheable(snapshot));
        int budget = totalBudget - prefixChars;
        if (budget < minChars) {
            budget = minChars;
        }
        int systemChars = FORMATTER.count(SYSTEM_PROMPT);
        if (budget < systemChars) {
            budget = systemChars;
        }

        List<String> eventLines = new ArrayList<String>();
        List<String> kpLines = new ArrayList<String>();
        splitRecentEvents(recentEventLines, eventLines, kpLines);

        List<String> blocks = new ArrayList<String>();
        blocks.add(SYSTEM_PROMPT);
        blocks.add(currentSceneBlock(snapshot, script));
        blocks.add(membersBlock(snapshot));
        blocks.add(eventsBlock(eventLines));
        blocks.add(unlockedCluesBlock(snapshot, script));
        blocks.add(lockedCluesBlock(snapshot, script));
        blocks.add(kpNarrationBlock(kpLines));
        return assemble(blocks, budget);
    }

    /**
     * 按总预算装配三段素材（§9.8）：动态段优先保留，超预算时先裁可缓存中段、再裁稳定前缀。
     *
     * <p>{@link #dynamic} 按「动态区至少 N 字」产出，长剧本时前缀与中段可能把总长顶出预算；
     * 本方法是请求前的最后一道收紧，保证三段合计不超过 {@code budgetChars}。</p>
     */
    public static AiPromptParts fit(SessionSnapshot snapshot, Script script,
                                    List<String> recentEventLines, int budgetChars, int dynamicMinChars) {
        int budget = budgetChars > 0 ? budgetChars : DEFAULT_PROMPT_BUDGET_CHARS;
        String prefix = stablePrefix(snapshot, script);
        String cache = cacheable(snapshot);
        String dynamic = dynamic(snapshot, script, recentEventLines, budget, dynamicMinChars);
        int dynamicChars = FORMATTER.count(dynamic);
        if (dynamicChars >= budget) {
            return new AiPromptParts("", "", FORMATTER.truncate(dynamic, budget));
        }
        int allowed = budget - dynamicChars;
        int prefixChars = FORMATTER.count(prefix);
        int cacheChars = FORMATTER.count(cache);
        if (prefixChars + cacheChars <= allowed) {
            return new AiPromptParts(prefix, cache, dynamic);
        }
        if (prefixChars >= allowed) {
            return new AiPromptParts(FORMATTER.truncate(prefix, allowed), "", dynamic);
        }
        return new AiPromptParts(prefix, FORMATTER.truncate(cache, allowed - prefixChars), dynamic);
    }

    private static void appendLine(StringBuilder builder, String label, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        builder.append(label).append("：").append(value).append('\n');
    }

    private static void appendNpcCard(StringBuilder builder, Npc npc) {
        String name = text(npc.name());
        if (name.isEmpty()) {
            name = text(npc.id());
        }
        if (name.isEmpty()) {
            name = "未命名 NPC";
        }
        builder.append("- ").append(name);
        String role = text(npc.role());
        if (!role.isEmpty()) {
            builder.append("（").append(role).append("）");
        }
        builder.append('\n');
        String publicText = text(npc.publicText());
        if (!publicText.isEmpty()) {
            builder.append("  公开形象：").append(publicText).append('\n');
        }
        String secret = text(npc.secret());
        if (!secret.isEmpty()) {
            builder.append("  秘密（仅上下文，不得在玩家可见叙述中说破）：").append(secret).append('\n');
        }
    }

    /** 已登场 NPC：当前场景及其之前所有场景引用过的 NPC，按首次出现顺序去重。 */
    private static List<Npc> appearedNpcs(SessionSnapshot snapshot, Script script) {
        List<Npc> npcs = new ArrayList<Npc>();
        if (script == null || script.scenes().isEmpty()) {
            return npcs;
        }
        int current = currentSceneIndex(snapshot, script);
        Set<String> seen = new LinkedHashSet<String>();
        List<Scene> scenes = script.scenes();
        for (int index = 0; index <= current; index++) {
            for (String npcId : scenes.get(index).npcIds()) {
                if (npcId == null || npcId.isEmpty() || !seen.add(npcId)) {
                    continue;
                }
                Npc npc = script.npcById(npcId);
                if (npc != null) {
                    npcs.add(npc);
                }
            }
        }
        return npcs;
    }

    /** 当前场景序号；越界时收敛到合法范围，快照为 null 时按第一幕处理。 */
    private static int currentSceneIndex(SessionSnapshot snapshot, Script script) {
        int sceneIndex = snapshot == null ? 0 : snapshot.sceneIndex();
        int sceneCount = script.scenes().size();
        if (sceneIndex < 0) {
            return 0;
        }
        if (sceneIndex >= sceneCount) {
            return sceneCount - 1;
        }
        return sceneIndex;
    }

    private static Scene currentScene(SessionSnapshot snapshot, Script script) {
        if (script == null || script.scenes().isEmpty()) {
            return null;
        }
        return script.scenes().get(currentSceneIndex(snapshot, script));
    }

    private static String currentSceneBlock(SessionSnapshot snapshot, Script script) {
        Scene scene = currentScene(snapshot, script);
        StringBuilder builder = new StringBuilder("【当前场景】\n");
        if (scene == null) {
            builder.append("剧本未提供当前场景；只依据在场角色与最近事件客观叙述，不虚构场景与线索。");
            String title = snapshot == null ? "" : text(snapshot.sceneTitle());
            if (!title.isEmpty()) {
                builder.append("（快照标题：").append(title).append("）");
            }
            return builder.toString();
        }
        String title = text(scene.title());
        if (title.isEmpty() && snapshot != null) {
            title = text(snapshot.sceneTitle());
        }
        builder.append("第 ").append(currentSceneIndex(snapshot, script) + 1).append(" 幕");
        if (!title.isEmpty()) {
            builder.append("《").append(title).append("》");
        }
        builder.append('\n');
        String summary = text(scene.summary());
        if (!summary.isEmpty()) {
            builder.append("概要：").append(summary).append('\n');
        }
        String narration = text(scene.narration());
        if (!narration.isEmpty()) {
            builder.append("场景素材（供改写，不要逐字复述）：").append(narration);
        }
        return builder.toString().trim();
    }

    /**
     * 在场角色：只给代号与公开状态标签，不给任何数值，也不给 QQ 号。
     *
     * <p>局面里没有昵称字段，因此按加入顺序编匿名代号（玩家A、玩家B……），
     * 保证多次装配同一局时代号稳定，且不把可识别用户身份的数字送进模型。</p>
     */
    private static String membersBlock(SessionSnapshot snapshot) {
        StringBuilder builder = new StringBuilder("【在场角色】\n");
        List<MemberView> members = snapshot == null
                ? new ArrayList<MemberView>() : snapshot.members();
        boolean any = false;
        int index = 0;
        for (MemberView member : members) {
            if (member == null || !member.inSession()) {
                continue;
            }
            any = true;
            builder.append("- ").append(anonymousLabel(index)).append("：")
                    .append(joinText(publicStatusLabels(member), "、")).append('\n');
            index++;
        }
        if (!any) {
            builder.append("（暂无在场角色）");
        }
        return builder.toString().trim();
    }

    /** 匿名代号：前 26 位用字母，之后退回序号，避免出现 QQ 号等身份信息。 */
    private static String anonymousLabel(int index) {
        if (index < 26) {
            return "玩家" + (char) ('A' + index);
        }
        return "玩家" + (index + 1);
    }

    /** 由角色卡推导公开状态标签；具体 HP/SAN 数值只在内部用于阈值，不输出。 */
    private static List<String> publicStatusLabels(MemberView member) {
        List<String> labels = new ArrayList<String>();
        CardData card = member.card();
        int maxHp = (card.attribute("CON") + card.attribute("SIZ")) / 10;
        if (maxHp > 0) {
            if (card.hp() <= 0) {
                labels.add("昏迷");
            } else if (card.hp() * 2 < maxHp) {
                labels.add("重伤");
            } else if (card.hp() < maxHp) {
                labels.add("受伤");
            }
        }
        int maxSan = card.attribute("POW");
        if (maxSan > 0) {
            if (card.san() <= 0) {
                labels.add("精神崩溃");
            } else if (card.san() * 3 < maxSan) {
                labels.add("精神紧绷");
            }
        }
        if (member.madnessUntil() != 0L) {
            labels.add("陷入疯狂");
        }
        if (member.takenOver()) {
            labels.add("由他人代管");
        }
        if (labels.isEmpty()) {
            labels.add("状态未见异常");
        }
        return labels;
    }

    /** 最近事件与 KP 叙述分流；KP 行以 KP：/主持人：开头，其余都算事件。 */
    private static void splitRecentEvents(List<String> recentEventLines,
                                          List<String> eventLines, List<String> kpLines) {
        if (recentEventLines == null) {
            return;
        }
        for (String raw : recentEventLines) {
            String cleaned = text(raw);
            if (cleaned.isEmpty()) {
                continue;
            }
            String[] lines = cleaned.split("\n", -1);
            for (String candidate : lines) {
                String line = candidate.trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (isKpLine(line)) {
                    kpLines.add(line);
                } else {
                    eventLines.add(line);
                }
            }
        }
        trimHead(eventLines, MAX_EVENT_LINES);
        trimHead(kpLines, MAX_KP_LINES);
    }

    private static boolean isKpLine(String line) {
        return line.startsWith("KP：") || line.startsWith("KP:")
                || line.startsWith("主持人：") || line.startsWith("主持人:");
    }

    /** 只保留末尾 max 行，事件列表优先保留最近的。 */
    private static void trimHead(List<String> lines, int max) {
        while (lines.size() > max) {
            lines.remove(0);
        }
    }

    private static String eventsBlock(List<String> eventLines) {
        if (eventLines.isEmpty()) {
            return "【最近事件】\n（本回合暂无新事件）";
        }
        StringBuilder builder = new StringBuilder("【最近事件】\n");
        for (String line : eventLines) {
            builder.append("- ").append(line).append('\n');
        }
        return builder.toString().trim();
    }

    private static String unlockedCluesBlock(SessionSnapshot snapshot, Script script) {
        if (script == null || snapshot == null || snapshot.unlockedClues().isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder("【已解锁线索（可引用原文）】\n");
        Set<String> seen = new LinkedHashSet<String>();
        boolean any = false;
        for (String key : snapshot.unlockedClues()) {
            if (key == null || key.isEmpty() || !seen.add(key)) {
                continue;
            }
            Clue clue = script.clueById(key);
            if (clue == null) {
                continue;
            }
            any = true;
            builder.append("- ").append(clueTitle(clue));
            String unlockedText = text(clue.unlockedText());
            if (!unlockedText.isEmpty()) {
                builder.append("：").append(unlockedText);
            }
            builder.append('\n');
        }
        return any ? builder.toString().trim() : "";
    }

    private static String lockedCluesBlock(SessionSnapshot snapshot, Script script) {
        if (script == null) {
            return "";
        }
        Set<String> unlocked = new LinkedHashSet<String>();
        if (snapshot != null) {
            for (String key : snapshot.unlockedClues()) {
                if (key != null && !key.isEmpty()) {
                    unlocked.add(key);
                }
            }
        }
        StringBuilder builder = new StringBuilder("【未解锁线索（只给后果摘要，不得引用原文）】\n");
        boolean any = false;
        for (Clue clue : script.clues()) {
            if (clue == null || unlocked.contains(clue.id())) {
                continue;
            }
            String summary = text(clue.lockedSummary());
            if (summary.isEmpty()) {
                continue;
            }
            any = true;
            builder.append("- ").append(clueTitle(clue)).append("：").append(summary).append('\n');
        }
        return any ? builder.toString().trim() : "";
    }

    private static String kpNarrationBlock(List<String> kpLines) {
        if (kpLines.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder("【KP 最近叙述】\n");
        for (String line : kpLines) {
            builder.append("- ").append(line).append('\n');
        }
        return builder.toString().trim();
    }

    private static String clueTitle(Clue clue) {
        String name = text(clue.name());
        if (!name.isEmpty()) {
            return name;
        }
        String id = text(clue.id());
        return id.isEmpty() ? "未命名线索" : id;
    }

    /**
     * 逐块装入动态区素材；放不下的块截断后停止，保证总字数不超预算。
     */
    private static String assemble(List<String> blocks, int budget) {
        StringBuilder builder = new StringBuilder(2048);
        int used = 0;
        for (String block : blocks) {
            if (block == null || block.isEmpty()) {
                continue;
            }
            int separator = builder.length() == 0 ? 0 : 2;
            int remain = budget - used - separator;
            int blockChars = FORMATTER.count(block);
            if (blockChars <= remain) {
                if (separator > 0) {
                    builder.append("\n\n");
                }
                builder.append(block);
                used += separator + blockChars;
                continue;
            }
            if (remain > 2) {
                if (separator > 0) {
                    builder.append("\n\n");
                }
                builder.append(FORMATTER.truncate(block, remain));
            }
            break;
        }
        return builder.toString().trim();
    }

    private static String joinText(List<String> values, String separator) {
        StringBuilder builder = new StringBuilder();
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String cleaned = text(value);
            if (cleaned.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(separator);
            }
            builder.append(cleaned);
        }
        return builder.toString();
    }

    private static String text(String value) {
        return FORMATTER.clean(value);
    }
}
