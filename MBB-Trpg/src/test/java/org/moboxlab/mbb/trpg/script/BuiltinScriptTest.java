package org.moboxlab.mbb.trpg.script;

import org.moboxlab.mbb.trpg.testutil.ScriptResources;

import com.alibaba.fastjson.JSON;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** 内置样本与随包资源的结构验收（S4 验收 H1 的资源侧、K4 的素材结构）。 */
public class BuiltinScriptTest {

    private Script script;
    private ScriptCatalog catalog;

    @Before
    public void setUp() throws IOException {
        ScriptValidationResult result = new ScriptValidator()
                .validate(ScriptResources.read("scripts/coc-example.json"));
        assertTrue(result.errorCodes().toString(), result.ok());
        script = result.script();
        catalog = new BuiltinScriptCatalog(BuiltinScriptTest.class.getClassLoader(),
                new ScriptValidator());
    }

    @Test
    public void exampleIsCataloguedAsBuiltin() {
        assertEquals(1, catalog.list().size());
        ScriptInfo info = catalog.info("coc-example");
        assertNotNull(info);
        assertEquals(ScriptSource.BUILTIN, info.source());
        assertEquals("雨夜古宅", info.name());
        assertEquals(4, info.sceneCount());
        assertEquals(4, info.clueCount());
        assertEquals(2, info.npcCount());
        assertEquals(3, info.checkCount());
        assertEquals(3, info.triggerCount());
        assertEquals(3, info.endingCount());
    }

    @Test
    public void schemaAndExampleResourcesAreShipped() throws IOException {
        String schema = ScriptResources.read("script-v1.schema.json");
        assertTrue(schema.contains("schemaVersion"));
        String index = ScriptResources.read("scripts/index.txt");
        assertEquals("coc-example", index.trim());
    }

    @Test
    public void scenesFormALinearChain() {
        List<Scene> scenes = script.scenes();
        assertEquals(Arrays.asList("scene-gate", "scene-hall", "scene-study", "scene-cellar"),
                Arrays.asList(scenes.get(0).id(), scenes.get(1).id(),
                        scenes.get(2).id(), scenes.get(3).id()));
        for (Scene scene : scenes) {
            assertFalse(scene.narration().trim().isEmpty());
        }
        // fallbackNarration 是可选的兜底叙述，示例里只在开头场景给出。
        assertEquals("scene-gate", scenes.get(0).id());
        assertFalse(scenes.get(0).fallbackNarration().trim().isEmpty());
        assertNotNull(script.sceneByTitle(" 书房 "));
        assertEquals("scene-study", script.sceneByTitle(" 书房 ").id());
    }

    @Test
    public void everyTriggerKindIsCovered() {
        Set<String> kinds = new HashSet<String>();
        for (ScriptTrigger trigger : script.triggers()) {
            kinds.add(trigger.when());
        }
        assertEquals(new HashSet<String>(Arrays.asList(ScriptTrigger.WHEN_SCENE_ENTER,
                ScriptTrigger.WHEN_CLUE_UNLOCKED, ScriptTrigger.WHEN_CHECK_RESOLVED)), kinds);
    }

    @Test
    public void cluesAndNpcsCarryHiddenMaterial() {
        for (Clue clue : script.clues()) {
            assertFalse(clue.lockedSummary().trim().isEmpty());
            assertFalse(clue.unlockedText().trim().isEmpty());
        }
        for (Npc npc : script.npcs()) {
            assertFalse(npc.publicText().trim().isEmpty());
            assertFalse(npc.secret().trim().isEmpty());
        }
    }

    @Test
    public void endingsAreOrderedFromRichestToFallback() {
        List<ScriptEnding> endings = script.endings();
        assertEquals(4, endings.get(0).requiredClueIds().size());
        assertEquals(2, endings.get(1).requiredClueIds().size());
        assertTrue(endings.get(2).requiredClueIds().isEmpty());
    }

    @Test
    public void sanCheckUsesTwoSidedLoss() {
        ScriptCheck san = script.checkById("check-san");
        assertTrue(san.sanCheck());
        assertEquals(ScriptCheck.DIFFICULTY_REGULAR, san.difficulty());
        assertEquals(1, san.successLoss().maxValue());
        assertEquals(6, san.failureLoss().maxValue());
        assertEquals(ScriptCheck.DIFFICULTY_HARD, script.checkById("check-spot").difficulty());
    }

    /** 校验资源能被 fastjson 解析，避免示例文件编码坏掉时只报校验错误。 */
    @Test
    public void exampleResourceIsValidJsonObject() throws IOException {
        assertNotNull(JSON.parseObject(ScriptResources.read("scripts/coc-example.json")));
    }
}
