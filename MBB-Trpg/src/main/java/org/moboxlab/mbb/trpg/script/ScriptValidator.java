package org.moboxlab.mbb.trpg.script;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.mbb.trpg.rule.DiceExpression;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.moboxlab.mbb.trpg.script.ScriptErrorCodes.E_BUILTIN;
import static org.moboxlab.mbb.trpg.script.ScriptErrorCodes.E_DUP;
import static org.moboxlab.mbb.trpg.script.ScriptErrorCodes.E_FIELD;
import static org.moboxlab.mbb.trpg.script.ScriptErrorCodes.E_ID;
import static org.moboxlab.mbb.trpg.script.ScriptErrorCodes.E_PARSE;
import static org.moboxlab.mbb.trpg.script.ScriptErrorCodes.E_RANGE;
import static org.moboxlab.mbb.trpg.script.ScriptErrorCodes.E_REF;
import static org.moboxlab.mbb.trpg.script.ScriptErrorCodes.E_RESERVED;
import static org.moboxlab.mbb.trpg.script.ScriptErrorCodes.E_RULESET;
import static org.moboxlab.mbb.trpg.script.ScriptErrorCodes.E_ROOT;
import static org.moboxlab.mbb.trpg.script.ScriptErrorCodes.E_SIZE;
import static org.moboxlab.mbb.trpg.script.ScriptErrorCodes.E_VERSION;
import static org.moboxlab.mbb.trpg.script.ScriptErrorCodes.W_UNKNOWN_FIELD;

/**
 * 剧本校验器：结构校验（对齐 script-v1.schema.json）+ 跨字段语义校验。
 *
 * <p>校验顺序按 docs/scripts/README.md：文件大小 → JSON 解析 → 顶层类型与必填 →
 * schemaVersion / ruleSet / id 格式与保留字 → 单字段类型与范围 → 全局唯一性 →
 * 引用存在性 → 跨字段语义。来源冲突（E_EXISTS / E_IN_USE）由导入与删除服务负责。</p>
 *
 * <p>校验是可重复调用的纯函数：不读文件、不写文件、不依赖插件状态；同一份内容多次校验结果一致。
 * 未知字段只记 W_UNKNOWN_FIELD 告警，不阻断。</p>
 */
public final class ScriptValidator {

    /** 单个剧本内容上限（字节）。 */
    public static final int MAX_CONTENT_BYTES = 65536;

    /** 节点 id 正则：小写字母或数字开头，可继续包含小写字母、数字与连字符，总长不超过 32。 */
    public static final String ELEMENT_ID_REGEX = "^[a-z0-9][a-z0-9-]{0,31}$";

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final Pattern ELEMENT_ID = Pattern.compile(ELEMENT_ID_REGEX);
    private static final Pattern CONSTANT_OR_DICE =
            Pattern.compile("^(\\d{1,3})$|^(\\d{1,2})[dD](\\d{1,4})(?:\\+(\\d{1,3}))?$");

    private static final Set<String> RESERVED_IDS = setOf(
            "list", "show", "import", "delete", "gen", "end", "cancel");

    private static final Set<String> TOP_FIELDS = setOf(
            "schemaVersion", "id", "name", "author", "description", "ruleSet", "minPlayers",
            "maxPlayers", "estimatedMinutes", "tags", "startingItems", "scenes", "clues", "npcs",
            "checks", "triggers", "endings");
    private static final Set<String> SCENE_FIELDS = setOf(
            "id", "title", "summary", "narration", "fallbackNarration", "clueIds", "npcIds",
            "checkIds");
    private static final Set<String> CLUE_FIELDS = setOf(
            "id", "name", "lockedSummary", "unlockedText");
    private static final Set<String> NPC_FIELDS = setOf("id", "name", "role", "public", "secret");
    private static final Set<String> CHECK_FIELDS = setOf(
            "id", "type", "skill", "difficulty", "bonusDice", "penaltyDice", "loss", "prompt",
            "successHint", "failureHint");
    private static final Set<String> TRIGGER_FIELDS = setOf(
            "id", "when", "sceneId", "clueId", "checkId", "result", "hint");
    private static final Set<String> ENDING_FIELDS = setOf(
            "id", "title", "requiredClueIds", "narration");

    private static final Set<String> CHECK_TYPES = setOf(
            ScriptCheck.TYPE_SKILL, ScriptCheck.TYPE_SAN);
    private static final Set<String> DIFFICULTIES = setOf(
            ScriptCheck.DIFFICULTY_REGULAR, ScriptCheck.DIFFICULTY_HARD,
            ScriptCheck.DIFFICULTY_EXTREME);
    private static final Set<String> TRIGGER_WHENS = setOf(
            ScriptTrigger.WHEN_SCENE_ENTER, ScriptTrigger.WHEN_CLUE_UNLOCKED,
            ScriptTrigger.WHEN_CHECK_RESOLVED);
    private static final Set<String> TRIGGER_RESULTS = setOf(
            ScriptTrigger.RESULT_SUCCESS, ScriptTrigger.RESULT_FAILURE,
            ScriptTrigger.RESULT_ANY);

    /**
     * 校验 UTF-8 字节内容（文件读取路径用这个，能精确判断 E_SIZE）。
     *
     * <p>字节先严格按 UTF-8 解码：非法字节序列不静默替换为 U+FFFD，而是直接回 E_PARSE。
     * 大小上限按原始字节判断，BOM 计入其中，与 {@link #validate(String)} 口径一致。</p>
     */
    public ScriptValidationResult validate(byte[] content) {
        return validate(content, false);
    }

    /**
     * 校验 AI 生成结果：与导入同一条规则链，唯一区别是允许 {@code gen-} 前缀的 id。
     *
     * <p>生成流程在写入前把 id 覆盖为 {@code gen-<时间戳>-<随机>}，该前缀只对生成路径开放；
     * 作者手工导入仍然是 E_ID。</p>
     */
    public ScriptValidationResult validateGenerated(byte[] content) {
        return validate(content, true);
    }

    private ScriptValidationResult validate(byte[] content, boolean generatedId) {
        if (content == null) {
            return single(E_PARSE, "$", "剧本内容为空");
        }
        if (content.length > MAX_CONTENT_BYTES) {
            return single(E_SIZE, "$", "剧本内容超过 " + MAX_CONTENT_BYTES
                    + " 字节：实际 " + content.length + " 字节");
        }
        String text;
        try {
            CharsetDecoder decoder = UTF8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            text = decoder.decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException e) {
            return single(E_PARSE, "$", "剧本内容不是合法的 UTF-8 编码");
        }
        return validate(text, generatedId);
    }

    /** 校验 JSON 文本；BOM 会被剥离，但大小上限仍按含 BOM 的原始字节判断。 */
    public ScriptValidationResult validate(String jsonText) {
        return validate(jsonText, false);
    }

    /** 校验 AI 生成的 JSON 文本，允许 {@code gen-} 前缀 id。 */
    public ScriptValidationResult validateGenerated(String jsonText) {
        return validate(jsonText, true);
    }

    private ScriptValidationResult validate(String jsonText, boolean generatedId) {
        if (jsonText == null) {
            return single(E_PARSE, "$", "剧本内容为空");
        }
        int bytes = jsonText.getBytes(UTF8).length;
        if (bytes > MAX_CONTENT_BYTES) {
            return single(E_SIZE, "$", "剧本内容超过 " + MAX_CONTENT_BYTES
                    + " 字节：实际 " + bytes + " 字节");
        }
        String text = stripBom(jsonText);
        Ctx ctx = new Ctx();
        Object root;
        try {
            root = JSON.parse(text);
        } catch (RuntimeException e) {
            ctx.error(E_PARSE, "$", "JSON 解析失败：" + firstLine(e.getMessage()));
            return ScriptValidationResult.failed(ctx.errors);
        }
        if (!(root instanceof JSONObject)) {
            ctx.error(E_ROOT, "$", "剧本顶层必须是 JSON 对象");
            return ScriptValidationResult.failed(ctx.errors);
        }
        JSONObject json = (JSONObject) root;
        warnUnknownFields(json, TOP_FIELDS, "$", ctx);
        Script script = buildScript(json, generatedId, ctx);
        if (script == null || ctx.hasErrors()) {
            return ScriptValidationResult.failed(ctx.errors);
        }
        checkUnique(script, ctx);
        if (ctx.hasErrors()) {
            return ScriptValidationResult.failed(ctx.errors);
        }
        checkReferences(script, ctx);
        if (ctx.hasErrors()) {
            return ScriptValidationResult.failed(ctx.errors);
        }
        return ScriptValidationResult.ok(script, ctx.warnings);
    }

    private static ScriptValidationResult single(String code, String path, String message) {
        return ScriptValidationResult.failed(Collections.singletonList(new ScriptIssue(code, path, message)));
    }

    private static String stripBom(String text) {
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            return text.substring(1);
        }
        return text;
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "未知原因";
        }
        int index = message.indexOf('\n');
        return index < 0 ? message : message.substring(0, index);
    }

    // ===== 顶层与节点 =====

    private Script buildScript(JSONObject json, boolean generatedId, Ctx ctx) {
        int before = ctx.errors.size();

        Integer schemaVersion = requiredInt(json, "schemaVersion", "$.schemaVersion", ctx);
        String id = requiredText(json, "id", "$.id", 1, 32, ctx);
        String name = requiredText(json, "name", "$.name", 1, 40, ctx);
        String author = optionalText(json, "author", "$.author", 1, 40, ctx);
        String description = optionalText(json, "description", "$.description", 1, 300, ctx);
        String ruleSet = requiredText(json, "ruleSet", "$.ruleSet", 1, 16, ctx);
        Integer minPlayers = requiredIntRange(json, "minPlayers", "$.minPlayers", 1, 20, ctx);
        Integer maxPlayers = requiredIntRange(json, "maxPlayers", "$.maxPlayers", 1, 20, ctx);
        Integer estimatedMinutes =
                requiredIntRange(json, "estimatedMinutes", "$.estimatedMinutes", 15, 600, ctx);
        List<String> tags = optionalTextArray(json, "tags", "$.tags", 8, 1, 16, ctx);
        List<String> startingItems =
                optionalTextArray(json, "startingItems", "$.startingItems", 20, 1, 30, ctx);

        if (schemaVersion != null && schemaVersion.intValue() != Script.SCHEMA_VERSION) {
            ctx.error(E_VERSION, "$.schemaVersion", "schemaVersion 必须是 "
                    + Script.SCHEMA_VERSION + "，当前为 " + schemaVersion);
        }
        if (ruleSet != null && !Script.RULE_SET_D100.equals(ruleSet)) {
            ctx.error(E_RULESET, "$.ruleSet", "ruleSet 只接受 "
                    + Script.RULE_SET_D100 + "，当前为 " + ruleSet);
        }
        if (id != null) {
            checkScriptId(id, generatedId, ctx);
        }
        if (minPlayers != null && maxPlayers != null && maxPlayers.intValue() < minPlayers.intValue()) {
            ctx.error(E_RANGE, "$.maxPlayers", "maxPlayers 不得小于 minPlayers："
                    + maxPlayers + " < " + minPlayers);
        }

        List<Scene> scenes = parseScenes(json, ctx);
        List<Clue> clues = parseClues(json, ctx);
        List<Npc> npcs = parseNpcs(json, ctx);
        List<ScriptCheck> checks = parseChecks(json, ctx);
        List<ScriptTrigger> triggers = parseTriggers(json, ctx);
        List<ScriptEnding> endings = parseEndings(json, ctx);

        if (ctx.errors.size() > before) {
            return null;
        }
        return new Script(schemaVersion, id, name, author, description, ruleSet,
                minPlayers, maxPlayers, estimatedMinutes, tags, startingItems, scenes, clues, npcs,
                checks, triggers, endings);
    }

    private void checkScriptId(String id, boolean generatedId, Ctx ctx) {
        if (!ELEMENT_ID.matcher(id).matches()) {
            ctx.error(E_ID, "$.id", "剧本 id 必须匹配 " + ELEMENT_ID_REGEX + "：" + id);
            return;
        }
        if (!generatedId && id.startsWith("gen-")) {
            ctx.error(E_ID, "$.id", "剧本 id 不得以 gen- 开头（该前缀保留给生成剧本）：" + id);
        }
        if (RESERVED_IDS.contains(id.toLowerCase())) {
            ctx.error(E_RESERVED, "$.id", "剧本 id 不得使用保留字：" + id);
        }
    }

    private List<Scene> parseScenes(JSONObject json, Ctx ctx) {
        List<Scene> scenes = new ArrayList<Scene>();
        JSONArray array = arrayValue(json, "scenes", "$.scenes", true, 1, 20, ctx);
        if (array == null) {
            return scenes;
        }
        for (int i = 0; i < array.size(); i++) {
            String path = "$.scenes[" + i + "]";
            JSONObject node = objectValue(array.get(i), path, ctx);
            if (node == null) {
                continue;
            }
            warnUnknownFields(node, SCENE_FIELDS, path, ctx);
            int before = ctx.errors.size();
            String sceneId = requiredElementId(node, "id", path + ".id", ctx);
            String title = requiredText(node, "title", path + ".title", 1, 40, ctx);
            String summary = optionalText(node, "summary", path + ".summary", 1, 200, ctx);
            String narration = requiredText(node, "narration", path + ".narration", 1, 1200, ctx);
            String fallback = optionalText(node, "fallbackNarration",
                    path + ".fallbackNarration", 1, 1200, ctx);
            List<String> clueIds = optionalIdArray(node, "clueIds", path + ".clueIds", ctx);
            List<String> npcIds = optionalIdArray(node, "npcIds", path + ".npcIds", ctx);
            List<String> checkIds = optionalIdArray(node, "checkIds", path + ".checkIds", ctx);
            if (ctx.errors.size() > before) {
                continue;
            }
            scenes.add(new Scene(sceneId, title, summary, narration, fallback,
                    clueIds, npcIds, checkIds));
        }
        return scenes;
    }

    private List<Clue> parseClues(JSONObject json, Ctx ctx) {
        List<Clue> clues = new ArrayList<Clue>();
        JSONArray array = arrayValue(json, "clues", "$.clues", false, 0, 60, ctx);
        if (array == null) {
            return clues;
        }
        for (int i = 0; i < array.size(); i++) {
            String path = "$.clues[" + i + "]";
            JSONObject node = objectValue(array.get(i), path, ctx);
            if (node == null) {
                continue;
            }
            warnUnknownFields(node, CLUE_FIELDS, path, ctx);
            int before = ctx.errors.size();
            String clueId = requiredElementId(node, "id", path + ".id", ctx);
            String name = requiredText(node, "name", path + ".name", 1, 40, ctx);
            String lockedSummary = optionalText(node, "lockedSummary",
                    path + ".lockedSummary", 1, 120, ctx);
            String unlockedText = requiredText(node, "unlockedText",
                    path + ".unlockedText", 1, 800, ctx);
            if (ctx.errors.size() > before) {
                continue;
            }
            clues.add(new Clue(clueId, name, lockedSummary, unlockedText));
        }
        return clues;
    }

    private List<Npc> parseNpcs(JSONObject json, Ctx ctx) {
        List<Npc> npcs = new ArrayList<Npc>();
        JSONArray array = arrayValue(json, "npcs", "$.npcs", false, 0, 20, ctx);
        if (array == null) {
            return npcs;
        }
        for (int i = 0; i < array.size(); i++) {
            String path = "$.npcs[" + i + "]";
            JSONObject node = objectValue(array.get(i), path, ctx);
            if (node == null) {
                continue;
            }
            warnUnknownFields(node, NPC_FIELDS, path, ctx);
            int before = ctx.errors.size();
            String npcId = requiredElementId(node, "id", path + ".id", ctx);
            String name = requiredText(node, "name", path + ".name", 1, 40, ctx);
            String role = requiredText(node, "role", path + ".role", 1, 40, ctx);
            String publicText = requiredText(node, "public", path + ".public", 1, 500, ctx);
            String secret = optionalText(node, "secret", path + ".secret", 1, 500, ctx);
            if (ctx.errors.size() > before) {
                continue;
            }
            npcs.add(new Npc(npcId, name, role, publicText, secret));
        }
        return npcs;
    }

    private List<ScriptCheck> parseChecks(JSONObject json, Ctx ctx) {
        List<ScriptCheck> checks = new ArrayList<ScriptCheck>();
        JSONArray array = arrayValue(json, "checks", "$.checks", false, 0, 40, ctx);
        if (array == null) {
            return checks;
        }
        for (int i = 0; i < array.size(); i++) {
            String path = "$.checks[" + i + "]";
            JSONObject node = objectValue(array.get(i), path, ctx);
            if (node == null) {
                continue;
            }
            warnUnknownFields(node, CHECK_FIELDS, path, ctx);
            int before = ctx.errors.size();
            String checkId = requiredElementId(node, "id", path + ".id", ctx);
            String type = requiredText(node, "type", path + ".type", 1, 20, ctx);
            String skill = optionalText(node, "skill", path + ".skill", 1, 20, ctx);
            String difficulty = optionalText(node, "difficulty", path + ".difficulty", 1, 20, ctx);
            Integer bonusDice = optionalIntRange(node, "bonusDice", path + ".bonusDice", 0, 2, ctx);
            Integer penaltyDice =
                    optionalIntRange(node, "penaltyDice", path + ".penaltyDice", 0, 2, ctx);
            String loss = optionalText(node, "loss", path + ".loss", 1, 64, ctx);
            String prompt = optionalText(node, "prompt", path + ".prompt", 1, 200, ctx);
            String successHint = optionalText(node, "successHint",
                    path + ".successHint", 1, 200, ctx);
            String failureHint = optionalText(node, "failureHint",
                    path + ".failureHint", 1, 200, ctx);

            if (type != null && !CHECK_TYPES.contains(type)) {
                ctx.error(E_FIELD, path + ".type", "type 只接受 "
                        + ScriptCheck.TYPE_SKILL + " 或 " + ScriptCheck.TYPE_SAN + "："
                        + type);
            }
            if (difficulty != null && !difficulty.isEmpty() && !DIFFICULTIES.contains(difficulty)) {
                ctx.error(E_FIELD, path + ".difficulty", "difficulty 只接受 regular / hard / extreme："
                        + difficulty);
            }
            DiceExpression successLoss = null;
            DiceExpression failureLoss = null;
            if (ScriptCheck.TYPE_SKILL.equals(type)) {
                if (skill != null && skill.isEmpty()) {
                    ctx.error(E_FIELD, path + ".skill", "d100.skill 必须填写 skill");
                }
                if (loss != null && !loss.isEmpty()) {
                    ctx.error(E_FIELD, path + ".loss", "d100.skill 不得出现 loss");
                }
            } else if (ScriptCheck.TYPE_SAN.equals(type)) {
                if (skill != null && !skill.isEmpty()) {
                    ctx.error(E_FIELD, path + ".skill", "d100.san 不得出现 skill");
                }
                if (loss != null && loss.isEmpty()) {
                    ctx.error(E_FIELD, path + ".loss", "d100.san 必须填写 loss，形如 X/Y");
                } else if (loss != null) {
                    String[] parts = loss.split("/", -1);
                    if (parts.length != 2) {
                        ctx.error(E_FIELD, path + ".loss", "loss 必须形如 X/Y：" + loss);
                    } else {
                        successLoss = parseDiceExpression(parts[0].trim(),
                                path + ".loss", ctx);
                        failureLoss = parseDiceExpression(parts[1].trim(),
                                path + ".loss", ctx);
                    }
                }
            }
            if (ctx.errors.size() > before) {
                continue;
            }
            checks.add(new ScriptCheck(checkId, type, skill,
                    difficulty == null || difficulty.isEmpty()
                            ? ScriptCheck.DIFFICULTY_REGULAR : difficulty,
                    bonusDice == null ? 0 : bonusDice, penaltyDice == null ? 0 : penaltyDice,
                    successLoss, failureLoss, prompt, successHint, failureHint));
        }
        return checks;
    }

    private List<ScriptTrigger> parseTriggers(JSONObject json, Ctx ctx) {
        List<ScriptTrigger> triggers = new ArrayList<ScriptTrigger>();
        JSONArray array = arrayValue(json, "triggers", "$.triggers", false, 0, 40, ctx);
        if (array == null) {
            return triggers;
        }
        for (int i = 0; i < array.size(); i++) {
            String path = "$.triggers[" + i + "]";
            JSONObject node = objectValue(array.get(i), path, ctx);
            if (node == null) {
                continue;
            }
            warnUnknownFields(node, TRIGGER_FIELDS, path, ctx);
            int before = ctx.errors.size();
            String triggerId = requiredElementId(node, "id", path + ".id", ctx);
            String when = requiredText(node, "when", path + ".when", 1, 20, ctx);
            String sceneId = optionalElementId(node, "sceneId", path + ".sceneId", ctx);
            String clueId = optionalElementId(node, "clueId", path + ".clueId", ctx);
            String checkId = optionalElementId(node, "checkId", path + ".checkId", ctx);
            String result = optionalText(node, "result", path + ".result", 1, 20, ctx);
            String hint = requiredText(node, "hint", path + ".hint", 1, 200, ctx);

            if (when != null && !TRIGGER_WHENS.contains(when)) {
                ctx.error(E_FIELD, path + ".when", "when 只接受 scene-enter / clue-unlocked / "
                        + "check-resolved：" + when);
            } else if (when != null) {
                if (ScriptTrigger.WHEN_SCENE_ENTER.equals(when)) {
                    requireOnly(node, path, "sceneId", sceneId, ctx);
                    forbidField(path, ".clueId", clueId, ctx);
                    forbidField(path, ".checkId", checkId, ctx);
                    forbidField(path, ".result", result, ctx);
                } else if (ScriptTrigger.WHEN_CLUE_UNLOCKED.equals(when)) {
                    requireOnly(node, path, "clueId", clueId, ctx);
                    forbidField(path, ".sceneId", sceneId, ctx);
                    forbidField(path, ".checkId", checkId, ctx);
                    forbidField(path, ".result", result, ctx);
                } else {
                    requireOnly(node, path, "checkId", checkId, ctx);
                    forbidField(path, ".sceneId", sceneId, ctx);
                    forbidField(path, ".clueId", clueId, ctx);
                    if (result != null && !result.isEmpty()
                            && !TRIGGER_RESULTS.contains(result)) {
                        ctx.error(E_FIELD, path + ".result",
                                "result 只接受 success / failure / any：" + result);
                    }
                }
            }
            if (ctx.errors.size() > before) {
                continue;
            }
            String effectiveResult = result == null || result.isEmpty()
                    ? ScriptTrigger.RESULT_ANY : result;
            if (!ScriptTrigger.WHEN_CHECK_RESOLVED.equals(when)) {
                effectiveResult = "";
            }
            triggers.add(new ScriptTrigger(triggerId, when, sceneId, clueId, checkId,
                    effectiveResult, hint));
        }
        return triggers;
    }

    private List<ScriptEnding> parseEndings(JSONObject json, Ctx ctx) {
        List<ScriptEnding> endings = new ArrayList<ScriptEnding>();
        JSONArray array = arrayValue(json, "endings", "$.endings", false, 0, 6, ctx);
        if (array == null) {
            return endings;
        }
        for (int i = 0; i < array.size(); i++) {
            String path = "$.endings[" + i + "]";
            JSONObject node = objectValue(array.get(i), path, ctx);
            if (node == null) {
                continue;
            }
            warnUnknownFields(node, ENDING_FIELDS, path, ctx);
            int before = ctx.errors.size();
            String endingId = requiredElementId(node, "id", path + ".id", ctx);
            String title = requiredText(node, "title", path + ".title", 1, 40, ctx);
            String narration = requiredText(node, "narration", path + ".narration", 1, 1200, ctx);
            List<String> requiredClueIds = optionalIdArray(node, "requiredClueIds",
                    path + ".requiredClueIds", ctx);
            if (ctx.errors.size() > before) {
                continue;
            }
            endings.add(new ScriptEnding(endingId, title, narration, requiredClueIds));
        }
        return endings;
    }

    private void requireOnly(JSONObject node, String path, String field, String value, Ctx ctx) {
        if (value == null || value.isEmpty()) {
            ctx.error(E_FIELD, path + "." + field, "when=" + node.getString("when")
                    + " 时必须填写 " + field);
        }
    }

    private void forbidField(String path, String fieldSuffix, String value, Ctx ctx) {
        if (value != null && !value.isEmpty()) {
            ctx.error(E_FIELD, path + fieldSuffix, "该字段与 when 不匹配，必须省略");
        }
    }

    // ===== 全局唯一性与引用 =====

    private void checkUnique(Script script, Ctx ctx) {
        Map<String, String> seenIds = new HashMap<String, String>();
        collectUniqueIds(seenIds, script.scenes(), new Function<Scene, String>() {
            @Override
            public String apply(Scene scene) {
                return scene.id();
            }
        }, "$.scenes", ctx);
        collectUniqueIds(seenIds, script.clues(), new Function<Clue, String>() {
            @Override
            public String apply(Clue clue) {
                return clue.id();
            }
        }, "$.clues", ctx);
        collectUniqueIds(seenIds, script.npcs(), new Function<Npc, String>() {
            @Override
            public String apply(Npc npc) {
                return npc.id();
            }
        }, "$.npcs", ctx);
        collectUniqueIds(seenIds, script.checks(), new Function<ScriptCheck, String>() {
            @Override
            public String apply(ScriptCheck check) {
                return check.id();
            }
        }, "$.checks", ctx);
        collectUniqueIds(seenIds, script.triggers(), new Function<ScriptTrigger, String>() {
            @Override
            public String apply(ScriptTrigger trigger) {
                return trigger.id();
            }
        }, "$.triggers", ctx);
        collectUniqueIds(seenIds, script.endings(), new Function<ScriptEnding, String>() {
            @Override
            public String apply(ScriptEnding ending) {
                return ending.id();
            }
        }, "$.endings", ctx);

        Map<String, String> seenTitles = new HashMap<String, String>();
        for (int i = 0; i < script.scenes().size(); i++) {
            String title = script.scenes().get(i).title().trim();
            String path = "$.scenes[" + i + "].title";
            if (seenTitles.containsKey(title)) {
                ctx.error(E_DUP, path, "场景标题重复：" + title + "（首次出现在 "
                        + seenTitles.get(title) + "）");
            } else {
                seenTitles.put(title, path);
            }
        }
    }

    /** 按数组逐项登记节点 id，重复即回 E_DUP。 */
    private <T> void collectUniqueIds(Map<String, String> seen, List<T> nodes,
                                     Function<T, String> idOf, String arrayPath, Ctx ctx) {
        for (int i = 0; i < nodes.size(); i++) {
            addUniqueId(seen, idOf.apply(nodes.get(i)), arrayPath + "[" + i + "].id", ctx);
        }
    }

    private void addUniqueId(Map<String, String> seen, String id, String path, Ctx ctx) {
        if (seen.containsKey(id)) {
            ctx.error(E_DUP, path, "节点 id 重复：" + id + "（首次出现在 " + seen.get(id) + "）");
            return;
        }
        seen.put(id, path);
    }

    private void checkReferences(Script script, Ctx ctx) {
        for (int i = 0; i < script.scenes().size(); i++) {
            Scene scene = script.scenes().get(i);
            checkRefs(script, scene.clueIds(), ScriptRefType.CLUE,
                    "$.scenes[" + i + "].clueIds", ctx);
            checkRefs(script, scene.npcIds(), ScriptRefType.NPC,
                    "$.scenes[" + i + "].npcIds", ctx);
            checkRefs(script, scene.checkIds(), ScriptRefType.CHECK,
                    "$.scenes[" + i + "].checkIds", ctx);
        }
        for (int i = 0; i < script.triggers().size(); i++) {
            ScriptTrigger trigger = script.triggers().get(i);
            String base = "$.triggers[" + i + "]";
            if (ScriptTrigger.WHEN_SCENE_ENTER.equals(trigger.when())) {
                checkRef(script, trigger.sceneId(), ScriptRefType.SCENE, base + ".sceneId", ctx);
            } else if (ScriptTrigger.WHEN_CLUE_UNLOCKED.equals(trigger.when())) {
                checkRef(script, trigger.clueId(), ScriptRefType.CLUE, base + ".clueId", ctx);
            } else if (ScriptTrigger.WHEN_CHECK_RESOLVED.equals(trigger.when())) {
                checkRef(script, trigger.checkId(), ScriptRefType.CHECK, base + ".checkId", ctx);
            }
        }
        for (int i = 0; i < script.endings().size(); i++) {
            checkRefs(script, script.endings().get(i).requiredClueIds(), ScriptRefType.CLUE,
                    "$.endings[" + i + "].requiredClueIds", ctx);
        }
    }

    private void checkRefs(Script script, List<String> ids, ScriptRefType type, String path,
                           Ctx ctx) {
        for (int j = 0; j < ids.size(); j++) {
            checkRef(script, ids.get(j), type, path + "[" + j + "]", ctx);
        }
    }

    private void checkRef(Script script, String id, ScriptRefType type, String path, Ctx ctx) {
        if (!type.exists(script, id)) {
            ctx.error(E_REF, path, "引用的" + type.label() + " id 不存在：" + id);
        }
    }

    // ===== 字段读取与基础校验 =====

    private Integer requiredInt(JSONObject json, String key, String path, Ctx ctx) {
        if (!json.containsKey(key) || json.get(key) == null) {
            ctx.error(E_FIELD, path, "缺少必填字段：" + key);
            return null;
        }
        Integer value = asInt(json.get(key));
        if (value == null) {
            ctx.error(E_FIELD, path, "字段类型应为整数：" + key);
        }
        return value;
    }

    private Integer requiredIntRange(JSONObject json, String key, String path, int min, int max,
                                     Ctx ctx) {
        Integer value = requiredInt(json, key, path, ctx);
        if (value != null) {
            checkIntRange(value.intValue(), min, max, path, ctx);
        }
        return value;
    }

    private Integer optionalIntRange(JSONObject json, String key, String path, int min, int max,
                                     Ctx ctx) {
        if (!json.containsKey(key) || json.get(key) == null) {
            return null;
        }
        Integer value = asInt(json.get(key));
        if (value == null) {
            ctx.error(E_FIELD, path, "字段类型应为整数：" + key);
            return null;
        }
        checkIntRange(value.intValue(), min, max, path, ctx);
        return value;
    }

    private void checkIntRange(int value, int min, int max, String path, Ctx ctx) {
        if (value < min || value > max) {
            ctx.error(E_RANGE, path, "数值必须在 " + min + " 到 " + max + " 之间：实际 " + value);
        }
    }

    private static Integer asInt(Object value) {
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Long) {
            long longValue = ((Long) value).longValue();
            if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                return Integer.valueOf((int) longValue);
            }
        }
        return null;
    }

    private String requiredText(JSONObject json, String key, String path, int min, int max,
                                Ctx ctx) {
        if (!json.containsKey(key) || json.get(key) == null) {
            ctx.error(E_FIELD, path, "缺少必填字段：" + key);
            return null;
        }
        return optionalText(json, key, path, min, max, ctx);
    }

    /**
     * 读取可选文本字段：缺省返回空串；类型错误、含控制字符或超出长度范围返回 null。
     *
     * <p>返回值有三种语义：null 表示已记错误；空串表示字段缺省；其余为原文
     * （仅把 CRLF 统一为 LF）。调用方必须先判 null，再判空串。</p>
     *
     * <p>长度与「去首尾空白后非空」都按 trim 后的文本判断，但返回的是原文。</p>
     */
    private String optionalText(JSONObject json, String key, String path, int min, int max,
                                Ctx ctx) {
        if (!json.containsKey(key)) {
            return "";
        }
        Object raw = json.get(key);
        if (raw == null) {
            ctx.error(E_FIELD, path, "字段不允许为 null：" + key);
            return null;
        }
        if (!(raw instanceof String)) {
            ctx.error(E_FIELD, path, "字段类型应为字符串：" + key);
            return null;
        }
        String text = ((String) raw).replace("\r\n", "\n");
        if (!checkControlChars(text, path, ctx)) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            ctx.error(E_RANGE, path, "文本去首尾空白后不能为空：" + key);
            return null;
        }
        if (trimmed.length() < min || trimmed.length() > max) {
            ctx.error(E_RANGE, path, "文本长度必须在 " + min + " 到 " + max + " 字符之间：实际 "
                    + trimmed.length() + "（" + key + "）");
            return null;
        }
        return text;
    }

    private boolean checkControlChars(String text, String path, Ctx ctx) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x20 && c != '\n' && c != '\t') {
                ctx.error(E_FIELD, path, "文本含有不允许的控制字符：U+"
                        + String.format("%04X", (int) c));
                return false;
            }
        }
        return true;
    }

    private String requiredElementId(JSONObject json, String key, String path, Ctx ctx) {
        String value = optionalElementId(json, key, path, ctx);
        if (value == null) {
            return null;
        }
        if (value.isEmpty()) {
            ctx.error(E_FIELD, path, "缺少必填字段：" + key);
            return null;
        }
        return value;
    }

    private String optionalElementId(JSONObject json, String key, String path, Ctx ctx) {
        return idText(json, key, path, ctx);
    }

    /**
     * 读取节点 id：缺省返回空串；类型或格式不合法返回 null 并记错。
     *
     * <p>格式错误一律回 E_ID（不先按长度报 E_RANGE），错误码与 docs/scripts/README.md 一致。</p>
     */
    private String idText(JSONObject json, String key, String path, Ctx ctx) {
        if (!json.containsKey(key)) {
            return "";
        }
        Object raw = json.get(key);
        if (raw == null) {
            ctx.error(E_FIELD, path, "字段不允许为 null：" + key);
            return null;
        }
        if (!(raw instanceof String)) {
            ctx.error(E_FIELD, path, "字段类型应为字符串：" + key);
            return null;
        }
        String value = (String) raw;
        if (value.isEmpty()) {
            return "";
        }
        if (!ELEMENT_ID.matcher(value).matches()) {
            ctx.error(E_ID, path, "id 必须匹配 " + ELEMENT_ID_REGEX + "：" + value);
            return null;
        }
        return value;
    }

    private List<String> optionalIdArray(JSONObject json, String key, String path, Ctx ctx) {
        List<String> ids = new ArrayList<String>();
        JSONArray array = arrayValue(json, key, path, false, 0, Integer.MAX_VALUE, ctx);
        if (array == null) {
            return ids;
        }
        Set<String> seen = new LinkedHashSet<String>();
        for (int i = 0; i < array.size(); i++) {
            Object raw = array.get(i);
            String itemPath = path + "[" + i + "]";
            if (!(raw instanceof String)) {
                ctx.error(E_FIELD, itemPath, "数组元素应为字符串");
                continue;
            }
            String value = ((String) raw).replace("\r\n", "\n");
            if (!ELEMENT_ID.matcher(value).matches()) {
                ctx.error(E_ID, itemPath, "id 必须匹配 " + ELEMENT_ID_REGEX + "：" + value);
                continue;
            }
            if (!seen.add(value)) {
                ctx.error(E_DUP, itemPath, "数组内 id 重复：" + value);
                continue;
            }
            ids.add(value);
        }
        return ids;
    }

    private List<String> optionalTextArray(JSONObject json, String key, String path, int maxItems,
                                           int minLength, int maxLength, Ctx ctx) {
        List<String> values = new ArrayList<String>();
        JSONArray array = arrayValue(json, key, path, false, 0, maxItems, ctx);
        if (array == null) {
            return values;
        }
        Set<String> seen = new LinkedHashSet<String>();
        for (int i = 0; i < array.size(); i++) {
            Object raw = array.get(i);
            String itemPath = path + "[" + i + "]";
            if (!(raw instanceof String)) {
                ctx.error(E_FIELD, itemPath, "数组元素应为字符串");
                continue;
            }
            String text = ((String) raw).replace("\r\n", "\n");
            if (!checkControlChars(text, itemPath, ctx)) {
                continue;
            }
            String trimmed = text.trim();
            if (trimmed.isEmpty()) {
                ctx.error(E_RANGE, itemPath, "数组元素去首尾空白后不能为空");
                continue;
            }
            if (trimmed.length() < minLength || trimmed.length() > maxLength) {
                ctx.error(E_RANGE, itemPath, "数组元素长度必须在 " + minLength + " 到 "
                        + maxLength + " 字符之间：实际 " + trimmed.length());
                continue;
            }
            if (!seen.add(trimmed)) {
                ctx.error(E_DUP, itemPath, "数组元素重复：" + trimmed);
                continue;
            }
            values.add(text);
        }
        return values;
    }

    /** 读取 JSON 数组：required=false 且缺省返回空表；类型错误或数量越界返回 null。 */
    private JSONArray arrayValue(JSONObject json, String key, String path, boolean required,
                                 int minItems, int maxItems, Ctx ctx) {
        if (!json.containsKey(key)) {
            if (required) {
                ctx.error(E_FIELD, path, "缺少必填字段：" + key);
                return null;
            }
            return new JSONArray();
        }
        Object raw = json.get(key);
        if (raw == null) {
            ctx.error(E_FIELD, path, "字段不允许为 null：" + key);
            return null;
        }
        if (!(raw instanceof JSONArray)) {
            ctx.error(E_FIELD, path, "字段类型应为数组：" + key);
            return null;
        }
        JSONArray array = (JSONArray) raw;
        if (array.size() < minItems || array.size() > maxItems) {
            ctx.error(E_RANGE, path, "数组元素数量必须在 " + minItems + " 到 " + maxItems
                    + " 之间：实际 " + array.size() + "（" + key + "）");
            return null;
        }
        return array;
    }

    private JSONObject objectValue(Object raw, String path, Ctx ctx) {
        if (raw instanceof JSONObject) {
            return (JSONObject) raw;
        }
        ctx.error(E_FIELD, path, "数组元素应为对象");
        return null;
    }

    private void warnUnknownFields(JSONObject json, Set<String> allowed, String path, Ctx ctx) {
        for (String key : json.keySet()) {
            if (!allowed.contains(key)) {
                ctx.warn(W_UNKNOWN_FIELD, path + "." + key, "未知字段，已忽略：" + key);
            }
        }
    }

    /** 解析 SAN 损失里的一侧表达式：常数 0..999 或 NdM+K（N 1..20、M 1..1000、K 0..999）。 */
    private DiceExpression parseDiceExpression(String raw, String path, Ctx ctx) {
        String text = raw == null ? "" : raw.trim();
        Matcher matcher = CONSTANT_OR_DICE.matcher(text);
        if (!matcher.matches()) {
            ctx.error(E_FIELD, path, "损失表达式必须形如 1D6 或 1D6+2：" + text);
            return null;
        }
        if (matcher.group(1) != null) {
            // 正则已把常数封顶到 3 位，最大值 999，无需再判上界。
            return DiceExpression.constant(Integer.parseInt(matcher.group(1)));
        }
        int count = Integer.parseInt(matcher.group(2));
        int sides = Integer.parseInt(matcher.group(3));
        int modifier = matcher.group(4) == null ? 0 : Integer.parseInt(matcher.group(4));
        if (count < 1 || count > 20 || sides < 1 || sides > 1000) {
            ctx.error(E_RANGE, path, "损失表达式范围越界：N 1..20、M 1..1000、K 0..999：" + text);
            return null;
        }
        return DiceExpression.dice(count, sides, modifier);
    }

    private static Set<String> setOf(String... values) {
        return Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(values)));
    }

    /** 校验过程中的收集器：错误与告警分开存放。 */
    private static final class Ctx {

        private final List<ScriptIssue> errors = new ArrayList<ScriptIssue>();
        private final List<ScriptIssue> warnings = new ArrayList<ScriptIssue>();

        private void error(String code, String path, String message) {
            errors.add(new ScriptIssue(code, path, message));
        }

        private void warn(String code, String path, String message) {
            warnings.add(new ScriptIssue(code, path, message));
        }

        private boolean hasErrors() {
            return !errors.isEmpty();
        }
    }
}
