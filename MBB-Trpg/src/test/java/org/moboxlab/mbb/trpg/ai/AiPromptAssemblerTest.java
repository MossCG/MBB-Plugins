package org.moboxlab.mbb.trpg.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.moboxlab.mbb.trpg.present.TrpgMessageFormatter;
import org.moboxlab.mbb.trpg.script.Clue;
import org.moboxlab.mbb.trpg.script.Npc;
import org.moboxlab.mbb.trpg.script.Scene;
import org.moboxlab.mbb.trpg.script.Script;
import org.moboxlab.mbb.trpg.script.ScriptCheck;
import org.moboxlab.mbb.trpg.script.ScriptEnding;
import org.moboxlab.mbb.trpg.script.ScriptTrigger;
import org.moboxlab.mbb.trpg.session.MemberView;
import org.moboxlab.mbb.trpg.session.SessionSnapshot;
import org.moboxlab.mbb.trpg.session.SessionState;
import org.moboxlab.mbb.trpg.store.CardData;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** AI 主持提示词装配：三段结构、预算裁剪与信息边界。 */
public class AiPromptAssemblerTest {

    private static final String SCRIPT_ID = "script-rain-letter";

    private static final TrpgMessageFormatter FORMATTER = new TrpgMessageFormatter();

    private static final List<String> EMPTY_STRINGS = Collections.emptyList();

    @Test
    public void threeSectionsAreNonEmptyAndCarryExpectedBlocks() {
        SessionSnapshot snapshot = snapshot(0);
        Script script = script();

        String stable = AiPromptAssembler.stablePrefix(snapshot, script);
        String cacheable = AiPromptAssembler.cacheable(snapshot);
        String dynamic = AiPromptAssembler.dynamic(snapshot, script, events(),
                AiPromptAssembler.DEFAULT_PROMPT_BUDGET_CHARS,
                AiPromptAssembler.DEFAULT_DYNAMIC_MIN_CHARS);

        assertFalse(stable.isEmpty());
        assertFalse(cacheable.isEmpty());
        assertFalse(dynamic.isEmpty());

        // 稳定前缀：剧本核心设定与已登场 NPC 卡。
        assertTrue(stable.contains("【剧本核心设定】"));
        assertTrue(stable.contains("雨夜来信"));
        assertTrue(stable.contains("封闭宅邸里的失踪案"));
        assertTrue(stable.contains("【已登场 NPC】"));
        assertTrue(stable.contains("管家林伯"));
        // 可缓存中段：风格与规则约束摘要。
        assertTrue(cacheable.contains("【风格与规则约束】"));
        assertTrue(cacheable.contains("规则边界"));
        // 动态区：场景、在场角色、事件、线索、KP 叙述与固定系统提示。
        assertTrue(dynamic.contains("【系统提示（必须遵守）】"));
        assertTrue(dynamic.contains("回合叙述员"));
        assertTrue(dynamic.contains("【当前场景】"));
        assertTrue(dynamic.contains("【在场角色】"));
        assertTrue(dynamic.contains("【最近事件】"));
        assertTrue(dynamic.contains("【已解锁线索（可引用原文）】"));
        assertTrue(dynamic.contains("【未解锁线索（只给后果摘要，不得引用原文）】"));
        assertTrue(dynamic.contains("【KP 最近叙述】"));
        assertTrue(dynamic.contains("他抬起头，眼神里闪过一丝慌乱"));

        int total = chars(stable) + chars(cacheable) + chars(dynamic);
        assertTrue("三段总字数不应超过预算", total <= AiPromptAssembler.DEFAULT_PROMPT_BUDGET_CHARS);
    }

    @Test
    public void systemPromptSurvivesTinyBudgetAndLowerPriorityBlocksAreDropped() {
        SessionSnapshot snapshot = snapshot(0);
        Script script = script();
        String stable = AiPromptAssembler.stablePrefix(snapshot, script);
        String cacheable = AiPromptAssembler.cacheable(snapshot);
        int prefixChars = chars(stable) + chars(cacheable);

        // 总预算几乎被前两段吃光：动态区仍保留固定系统提示，低优先级素材让路。
        String dynamic = AiPromptAssembler.dynamic(snapshot, script, events(), prefixChars + 60, 1);

        assertTrue(dynamic.contains("【系统提示（必须遵守）】"));
        assertFalse(dynamic.contains("【当前场景】"));
        assertTrue("只保留系统提示时字数应远小于总预算", chars(dynamic) < 300);
    }

    @Test
    public void dynamicHonoursMinCharsEvenWhenTotalBudgetIsSmaller() {
        SessionSnapshot snapshot = snapshot(0);
        Script script = script();
        String stable = AiPromptAssembler.stablePrefix(snapshot, script);
        String cacheable = AiPromptAssembler.cacheable(snapshot);
        int prefixChars = chars(stable) + chars(cacheable);
        int tinyTotal = prefixChars + 100;

        String dynamic = AiPromptAssembler.dynamic(snapshot, script, events(), tinyTotal, 1000);

        // 动态区保底优先于总预算：实际字数接近保底，而不是被挤到只剩一百来字。
        assertTrue("动态区保底应优先于总预算", chars(dynamic) > tinyTotal - prefixChars + 500);
        assertTrue("动态区不应超过保底字数太多", chars(dynamic) <= 1000);
        assertTrue("保底时应装入当前场景", dynamic.contains("【当前场景】"));
        assertFalse("低优先级的线索应被裁掉", dynamic.contains("【已解锁线索（可引用原文）】"));
    }

    @Test
    public void membersUseAnonymousLabelsAndPublicStatusOnly() {
        SessionSnapshot snapshot = snapshot(0);
        String dynamic = AiPromptAssembler.dynamic(snapshot, script(), events(),
                AiPromptAssembler.DEFAULT_PROMPT_BUDGET_CHARS,
                AiPromptAssembler.DEFAULT_DYNAMIC_MIN_CHARS);

        assertTrue(dynamic.contains("玩家A"));
        assertTrue(dynamic.contains("玩家B"));
        assertFalse("提示词不得出现玩家 QQ 号", dynamic.contains("1001"));
        assertFalse("提示词不得出现玩家 QQ 号", dynamic.contains("1002"));
        // 3/11 生命对应重伤，SAN 归零对应精神崩溃；只给标签，不给数值。
        assertTrue(dynamic.contains("重伤"));
        assertTrue(dynamic.contains("精神崩溃"));
        assertFalse(dynamic.contains("HP"));
        assertFalse(dynamic.contains("SAN"));
        assertFalse(dynamic.contains("生命值"));
        assertFalse(dynamic.contains("理智值"));
        assertFalse(dynamic.contains("CON"));
        assertFalse(dynamic.contains("POW"));
    }

    @Test
    public void unlockedClueShowsOriginalTextWhileLockedClueOnlyShowsSummary() {
        String dynamic = AiPromptAssembler.dynamic(snapshot(0), script(), events(),
                AiPromptAssembler.DEFAULT_PROMPT_BUDGET_CHARS,
                AiPromptAssembler.DEFAULT_DYNAMIC_MIN_CHARS);

        assertTrue(dynamic.contains("抽屉里躺着半张车票，终点站被涂掉了。"));
        assertTrue(dynamic.contains("衣柜后面有东西在等。"));
        assertFalse("未解锁线索不得泄露原文", dynamic.contains("衣柜暗格里藏着带血的凶器。"));
    }

    @Test
    public void appearedNpcsStopAtCurrentScene() {
        String firstScene = AiPromptAssembler.stablePrefix(snapshot(0), script());
        assertTrue(firstScene.contains("管家林伯"));

        String secondScene = AiPromptAssembler.stablePrefix(snapshot(1), script());
        assertTrue(secondScene.contains("女仆阿棠"));

        // 秘密只进上下文并标注不得说破，不阻止写入前缀。
        assertTrue(firstScene.contains("不得在玩家可见叙述中说破"));
    }

    @Test
    public void scriptNullFallsBackToGenericTemplate() {
        String stable = AiPromptAssembler.stablePrefix(snapshot(0), null);
        assertTrue(stable.contains("通用悬疑模板"));
        assertFalse(stable.isEmpty());

        String dynamic = AiPromptAssembler.dynamic(snapshot(0), null, events(),
                AiPromptAssembler.DEFAULT_PROMPT_BUDGET_CHARS,
                AiPromptAssembler.DEFAULT_DYNAMIC_MIN_CHARS);
        assertTrue(dynamic.contains("剧本未提供当前场景"));
        assertTrue(dynamic.contains("【系统提示（必须遵守）】"));
    }

    @Test
    public void nullInputsAreTolerated() {
        assertFalse(AiPromptAssembler.stablePrefix(null, null).isEmpty());
        assertTrue(AiPromptAssembler.stablePrefix(null, null).contains("通用悬疑模板"));
        assertFalse(AiPromptAssembler.cacheable(null).isEmpty());
        assertFalse(AiPromptAssembler.dynamic(null, null, null, 0, 0).isEmpty());
        assertTrue(AiPromptAssembler.dynamic(null, null, null, 0, 0)
                .contains("【系统提示（必须遵守）】"));
    }

    @Test
    public void threeArgOverloadAcceptsNullCatalog() {
        String stable = AiPromptAssembler.stablePrefix(snapshot(0), null, null);
        assertTrue(stable.contains("通用悬疑模板"));
    }

    @Test
    public void emptyScriptScenesFallBackToGenericScene() {
        Script noScenes = new Script(Script.SCHEMA_VERSION, SCRIPT_ID, "空剧本", "", "没有场景",
                Script.RULE_SET_D100, 1, 2, 60, EMPTY_STRINGS, EMPTY_STRINGS,
                Collections.<Scene>emptyList(), Collections.<Clue>emptyList(),
                Collections.<Npc>emptyList(), Collections.<ScriptCheck>emptyList(),
                Collections.<ScriptTrigger>emptyList(), Collections.<ScriptEnding>emptyList());

        String stable = AiPromptAssembler.stablePrefix(snapshot(0), noScenes);
        assertTrue(stable.contains("通用悬疑模板"));

        String dynamic = AiPromptAssembler.dynamic(snapshot(0), noScenes, events(),
                AiPromptAssembler.DEFAULT_PROMPT_BUDGET_CHARS,
                AiPromptAssembler.DEFAULT_DYNAMIC_MIN_CHARS);
        assertTrue(dynamic.contains("剧本未提供当前场景"));
    }

    @Test
    public void fitKeepsThreePartsWithinTotalBudget() {
        AiPromptParts parts = AiPromptAssembler.fit(snapshot(0), script(), events(), 800, 400);
        int total = chars(parts.stablePrefix()) + chars(parts.cacheable())
                + chars(parts.dynamic());
        assertTrue("三段合计 " + total + " 应不超过 800", total <= 800);
        assertFalse(parts.dynamic().isEmpty());
    }

    @Test
    public void fitTruncatesDynamicWhenBudgetIsSmallerThanMinimum() {
        AiPromptParts parts = AiPromptAssembler.fit(snapshot(0), script(), events(), 300, 2000);
        int total = chars(parts.stablePrefix()) + chars(parts.cacheable())
                + chars(parts.dynamic());
        assertTrue("三段合计 " + total + " 应不超过 300", total <= 300);
    }

    private static SessionSnapshot snapshot(int sceneIndex) {
        return SessionSnapshot.builder()
                .groupId(100L)
                .sessionId(7L)
                .state(SessionState.RUNNING)
                .kpUserId(9L)
                .scriptId(SCRIPT_ID)
                .ruleSetId(Script.RULE_SET_D100)
                .sceneIndex(sceneIndex)
                .sceneTitle("雨夜门厅")
                .members(Arrays.asList(hurtMember(1001L), madMember(1002L)))
                .unlockedClues(Arrays.asList("c1"))
                .build();
    }

    private static Script script() {
        String longScene = repeat("潮湿的空气里混着铁锈味，角落的落地钟停在三点十七分。", 20);
        List<Scene> scenes = Arrays.asList(
                new Scene("s1", "雨夜门厅", "门厅的壁灯忽明忽暗。", longScene, "",
                        Arrays.asList("c1"), Arrays.asList("n1"), EMPTY_STRINGS),
                new Scene("s2", "地下室", "地下室的台阶又湿又滑。", longScene, "",
                        Arrays.asList("c2"), Arrays.asList("n2"), EMPTY_STRINGS));
        List<Clue> clues = Arrays.asList(
                new Clue("c1", "半张车票", "一张被撕掉一半的旧车票。",
                        "抽屉里躺着半张车票，终点站被涂掉了。"),
                new Clue("c2", "衣柜暗格", "衣柜后面有东西在等。",
                        "衣柜暗格里藏着带血的凶器。"));
        List<Npc> npcs = Arrays.asList(
                new Npc("n1", "管家林伯", "管家", "总是低着头，手指关节发白。",
                        "他就是三十年前那桩旧案的凶手。"),
                new Npc("n2", "女仆阿棠", "女仆", "说话声音很轻，不敢看人。",
                        "她每晚都会去地下室。"));
        return new Script(Script.SCHEMA_VERSION, SCRIPT_ID, "雨夜来信", "测试作者",
                "封闭宅邸里的失踪案。", Script.RULE_SET_D100, 1, 4, 180,
                Arrays.asList("悬疑", "封闭"), Arrays.asList("手电筒"), scenes, clues, npcs,
                Collections.<ScriptCheck>emptyList(), Collections.<ScriptTrigger>emptyList(),
                Collections.<ScriptEnding>emptyList());
    }

    private static List<String> events() {
        List<String> lines = new ArrayList<String>();
        for (int index = 0; index < 30; index++) {
            lines.add("玩家推开第 " + index + " 扇侧门，冷风顺着走廊灌进来。");
        }
        lines.add("KP：他抬起头，眼神里闪过一丝慌乱，随即又低下头去。");
        lines.add("KP：窗外传来树枝刮过玻璃的声响，没有人说话。");
        return lines;
    }

    private static MemberView hurtMember(long userId) {
        return new MemberView(userId, 1L, 0L, false, 0L, card(3, 0), EMPTY_STRINGS);
    }

    private static MemberView madMember(long userId) {
        return new MemberView(userId, 1L, 0L, true, System.currentTimeMillis() + 600000L,
                card(9, 40), EMPTY_STRINGS);
    }

    private static CardData card(int hp, int san) {
        Map<String, Integer> attrs = new LinkedHashMap<String, Integer>();
        attrs.put("CON", 50);
        attrs.put("SIZ", 60);
        attrs.put("POW", 60);
        Map<String, Integer> skills = new LinkedHashMap<String, Integer>();
        skills.put("侦查", 45);
        return new CardData(attrs, skills, san, hp, 10, EMPTY_STRINGS, EMPTY_STRINGS);
    }

    private static String repeat(String text, int times) {
        StringBuilder builder = new StringBuilder(text.length() * times);
        for (int index = 0; index < times; index++) {
            builder.append(text);
        }
        return builder.toString();
    }

    private static int chars(String text) {
        return text == null ? 0 : FORMATTER.count(text);
    }
}
