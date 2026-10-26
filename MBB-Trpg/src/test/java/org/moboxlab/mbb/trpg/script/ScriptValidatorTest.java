package org.moboxlab.mbb.trpg.script;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.junit.Before;
import org.moboxlab.mbb.trpg.testutil.ScriptResources;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** 剧本校验：覆盖 docs/scripts/README.md 的 18 条校验清单（来源冲突三项由导入与删除服务承担）。 */
public class ScriptValidatorTest {

    private ScriptValidator validator;
    private String exampleText;

    @Before
    public void setUp() {
        validator = new ScriptValidator();
        exampleText = ScriptResources.example();
    }

    @Test
    public void acceptsBuiltinExample() {
        ScriptValidationResult result = validator.validate(exampleText);

        assertTrue(result.errorCodes().toString(), result.ok());
        Script script = result.script();
        assertEquals("coc-example", script.id());
        assertEquals("雨夜古宅", script.name());
        assertEquals(4, script.scenes().size());
        assertEquals(4, script.clues().size());
        assertEquals(2, script.npcs().size());
        assertEquals(3, script.checks().size());
        assertEquals(3, script.triggers().size());
        assertEquals(3, script.endings().size());
        assertNotNull(script.clueById("clue-altar"));
        assertTrue(script.checkById("check-san").sanCheck());
    }

    @Test
    public void toleratesBomAndNormalizesCrlf() {
        JSONObject json = example();
        json.getJSONArray("scenes").getJSONObject(0)
                .put("summary", "第一行\r\n第二行");

        ScriptValidationResult result = validator.validate("\uFEFF" + json.toJSONString());

        assertTrue(result.errorCodes().toString(), result.ok());
        assertEquals("第一行\n第二行", result.script().sceneAt(0).summary());
    }

    @Test
    public void rejectsMalformedJson() {
        ScriptValidationResult result = validator.validate("{\"id\":");

        assertCode(result, ScriptErrorCodes.E_PARSE);
    }

    @Test
    public void rejectsOversizedContent() {
        byte[] content = new byte[ScriptValidator.MAX_CONTENT_BYTES + 1];
        Arrays.fill(content, (byte) ' ');

        ScriptValidationResult result = validator.validate(content);

        assertCode(result, ScriptErrorCodes.E_SIZE);
    }

    @Test
    public void rejectsUnsupportedSchemaVersion() {
        JSONObject json = example();
        json.put("schemaVersion", 2);

        assertCode(validate(json), ScriptErrorCodes.E_VERSION);
    }

    @Test
    public void rejectsUnsupportedRuleSet() {
        JSONObject json = example();
        json.put("ruleSet", "coc7");

        assertCode(validate(json), ScriptErrorCodes.E_RULESET);
    }

    @Test
    public void rejectsInvalidScriptId() {
        JSONObject json = example();
        json.put("id", "MyScript");

        assertCode(validate(json), ScriptErrorCodes.E_ID);
    }

    @Test
    public void rejectsReservedScriptId() {
        JSONObject json = example();
        json.put("id", "import");

        assertCode(validate(json), ScriptErrorCodes.E_RESERVED);
    }

    @Test
    public void rejectsGeneratedIdPrefix() {
        JSONObject json = example();
        json.put("id", "gen-20260101-0001");

        assertCode(validate(json), ScriptErrorCodes.E_ID);
    }

    @Test
    public void rejectsEmptyScenes() {
        JSONObject json = example();
        json.put("scenes", new JSONArray());

        assertCode(validate(json), ScriptErrorCodes.E_RANGE);
    }

    @Test
    public void rejectsDuplicateSceneTitle() {
        JSONObject json = example();
        JSONArray scenes = json.getJSONArray("scenes");
        scenes.getJSONObject(1).put("title", scenes.getJSONObject(0).getString("title"));

        assertCode(validate(json), ScriptErrorCodes.E_DUP);
    }

    @Test
    public void rejectsDuplicateNodeIdAcrossTypes() {
        JSONObject json = example();
        json.getJSONArray("npcs").getJSONObject(0).put("id", "scene-gate");

        assertCode(validate(json), ScriptErrorCodes.E_DUP);
    }

    @Test
    public void rejectsMissingSceneClueReference() {
        JSONObject json = example();
        json.getJSONArray("scenes").getJSONObject(0).put("clueIds",
                new JSONArray(Arrays.asList("clue-missing")));

        ScriptValidationResult result = validate(json);

        assertCode(result, ScriptErrorCodes.E_REF);
        assertTrue(firstError(result).path().startsWith("$.scenes[0].clueIds"));
    }

    @Test
    public void rejectsMissingEndingClueReference() {
        JSONObject json = example();
        json.getJSONArray("endings").getJSONObject(0).put("requiredClueIds",
                new JSONArray(Arrays.asList("clue-nowhere")));

        assertCode(validate(json), ScriptErrorCodes.E_REF);
    }

    @Test
    public void rejectsSceneTriggerWithoutSceneId() {
        JSONObject json = example();
        triggerByWhen(json, ScriptTrigger.WHEN_SCENE_ENTER).remove("sceneId");

        ScriptValidationResult result = validate(json);

        assertCode(result, ScriptErrorCodes.E_FIELD);
        assertTrue(firstError(result).path().endsWith(".sceneId"));
    }

    @Test
    public void rejectsTriggerWithMismatchedTarget() {
        JSONObject json = example();
        triggerByWhen(json, ScriptTrigger.WHEN_SCENE_ENTER).put("clueId", "clue-letter");

        assertCode(validate(json), ScriptErrorCodes.E_FIELD);
    }

    @Test
    public void rejectsSanLossWithoutSlash() {
        JSONObject json = example();
        checkById(json, "check-san").put("loss", "1");

        assertCode(validate(json), ScriptErrorCodes.E_FIELD);
    }

    @Test
    public void rejectsSkillCheckCarryingLoss() {
        JSONObject json = example();
        checkById(json, "check-listen").put("loss", "1/1D6");

        assertCode(validate(json), ScriptErrorCodes.E_FIELD);
    }

    @Test
    public void rejectsOverlongNarration() {
        JSONObject json = example();
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 601; i++) {
            longText.append("雨夜");
        }
        json.getJSONArray("scenes").getJSONObject(0).put("narration", longText.toString());

        assertCode(validate(json), ScriptErrorCodes.E_RANGE);
    }

    @Test
    public void rejectsNullRequiredField() {
        JSONObject json = example();
        json.put("name", null);

        assertCode(validate(json), ScriptErrorCodes.E_FIELD);
    }

    @Test
    public void warnsUnknownTopLevelField() {
        JSONObject json = example();
        json.put("foo", 1);

        ScriptValidationResult result = validator.validate(json.toJSONString());

        assertTrue(result.errorCodes().toString(), result.ok());
        assertTrue(result.warningCount() > 0);
        assertEquals("$.foo", result.warnings().get(0).path());
        assertEquals(ScriptErrorCodes.W_UNKNOWN_FIELD, result.warnings().get(0).code());
    }

    @Test
    public void warnsUnknownNestedField() {
        JSONObject json = example();
        json.getJSONArray("scenes").getJSONObject(0).put("bar", "x");

        ScriptValidationResult result = validator.validate(json.toJSONString());

        assertTrue(result.errorCodes().toString(), result.ok());
        assertEquals("$.scenes[0].bar", result.warnings().get(0).path());
    }

    @Test
    public void acceptsConstantSanLoss() {
        JSONObject json = example();
        checkById(json, "check-san").put("loss", "0/3");

        ScriptValidationResult result = validator.validate(json.toJSONString());

        assertTrue(result.errorCodes().toString(), result.ok());
        ScriptCheck check = result.script().checkById("check-san");
        assertEquals(0, check.successLoss().maxValue());
        assertEquals(3, check.failureLoss().maxValue());
    }

    @Test
    public void rejectsSanCheckCarryingSkill() {
        JSONObject json = example();
        checkById(json, "check-san").put("skill", "侦查");

        assertCode(validate(json), ScriptErrorCodes.E_FIELD);
    }

    @Test
    public void rejectsControlCharacterInText() {
        JSONObject json = example();
        json.getJSONArray("scenes").getJSONObject(0).put("summary", "坏\u0000字符");

        assertCode(validate(json), ScriptErrorCodes.E_FIELD);
    }

    @Test
    public void rejectsInvalidUtf8Bytes() {
        byte[] bad = new byte[] {'{', '"', 'a', '"', ':', '"', (byte) 0xFF, '"', '}'};

        ScriptValidationResult result = validator.validate(bad);

        assertCode(result, ScriptErrorCodes.E_PARSE);
    }

    @Test
    public void reportsIdFormatForTooLongNodeId() {
        StringBuilder longId = new StringBuilder();
        for (int i = 0; i < 33; i++) {
            longId.append('a');
        }
        JSONObject json = example();
        json.getJSONArray("scenes").getJSONObject(0).put("id", longId.toString());

        assertCode(validate(json), ScriptErrorCodes.E_ID);
    }

    @Test
    public void rejectsIsolatedCarriageReturn() {
        JSONObject json = example();
        json.getJSONArray("scenes").getJSONObject(0).put("summary", "第一行\r第二行");

        assertCode(validate(json), ScriptErrorCodes.E_FIELD);
    }

    @Test
    public void reportsAtMostFiveErrorsInReceipt() {
        JSONObject json = example();
        JSONArray scenes = json.getJSONArray("scenes");
        for (int i = 0; i < scenes.size(); i++) {
            scenes.getJSONObject(i).put("id", "Bad" + i);
        }
        JSONArray clues = json.getJSONArray("clues");
        for (int i = 0; i < clues.size(); i++) {
            clues.getJSONObject(i).put("id", "Bad" + i);
        }

        ScriptValidationResult result = validate(json);

        assertTrue("错误数应大于 5：" + result.errorCodes(), result.errorCount() > 5);
        assertEquals(ScriptValidationResult.MAX_REPORTED_ERRORS, result.reportedErrors().size());
        assertEquals(result.errorCount() - ScriptValidationResult.MAX_REPORTED_ERRORS,
                result.remainingErrorCount());
    }

    private ScriptValidationResult validate(JSONObject json) {
        ScriptValidationResult result = validator.validate(json.toJSONString());
        assertFalse("期望校验失败但通过了", result.ok());
        return result;
    }

    private JSONObject example() {
        return JSON.parseObject(exampleText);
    }

    private static void assertCode(ScriptValidationResult result, String expectedCode) {
        assertFalse("期望失败但通过了", result.ok());
        assertTrue("实际错误码：" + result.errorCodes(), result.errorCodes().contains(expectedCode));
        assertNotNull(firstError(result).message());
    }

    private static ScriptIssue firstError(ScriptValidationResult result) {
        return result.errors().get(0);
    }

    private static JSONObject triggerByWhen(JSONObject json, String when) {
        JSONArray triggers = json.getJSONArray("triggers");
        for (int i = 0; i < triggers.size(); i++) {
            JSONObject trigger = triggers.getJSONObject(i);
            if (when.equals(trigger.getString("when"))) {
                return trigger;
            }
        }
        fail("缺少 when=" + when + " 的触发器");
        return null;
    }

    private static JSONObject checkById(JSONObject json, String checkId) {
        JSONArray checks = json.getJSONArray("checks");
        for (int i = 0; i < checks.size(); i++) {
            JSONObject check = checks.getJSONObject(i);
            if (checkId.equals(check.getString("id"))) {
                return check;
            }
        }
        fail("缺少检定：" + checkId);
        return null;
    }

}
