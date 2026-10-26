package org.moboxlab.mbb.trpg.script;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** 导入结果值对象：空值拒绝、告警不可变与计数。 */
public class ScriptImportResultTest {

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNullScript() {
        new ScriptImportResult(null, false, Collections.<ScriptIssue>emptyList());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNullWarnings() {
        new ScriptImportResult(new ScriptInfo(sampleScript(), ScriptSource.IMPORTED), false, null);
    }

    @Test
    public void keepsWarningsImmutable() {
        List<ScriptIssue> warnings = new ArrayList<ScriptIssue>();
        warnings.add(new ScriptIssue(ScriptErrorCodes.W_UNKNOWN_FIELD, "$.foo", "未知字段，已忽略：foo"));

        ScriptImportResult result = new ScriptImportResult(
                new ScriptInfo(sampleScript(), ScriptSource.IMPORTED), true, warnings);
        warnings.clear();

        assertEquals(1, result.warningCount());
        assertTrue(result.replaced());
        try {
            result.warnings().add(new ScriptIssue(ScriptErrorCodes.W_UNKNOWN_FIELD, "$.bar", "x"));
            fail("告警列表应不可变");
        } catch (UnsupportedOperationException expected) {
            assertFalse(result.warnings().isEmpty());
        }
    }

    @Test
    public void countsNodesFromScriptInfo() {
        ScriptInfo info = new ScriptInfo(sampleScript(), ScriptSource.IMPORTED);
        ScriptImportResult result = new ScriptImportResult(info, false,
                Collections.<ScriptIssue>emptyList());

        assertEquals("test-one", result.script().id());
        assertEquals(1, result.script().sceneCount());
        assertEquals(0, result.warningCount());
        assertFalse(result.replaced());
    }

    private static Script sampleScript() {
        Scene scene = new Scene("s1", "开场", "", "测试文本", "",
                Collections.<String>emptyList(), Collections.<String>emptyList(),
                Collections.<String>emptyList());
        return new Script(Script.SCHEMA_VERSION, "test-one", "测试剧本", "", "", Script.RULE_SET_D100,
                1, 2, 30, Collections.<String>emptyList(), Collections.<String>emptyList(),
                Arrays.asList(scene), Collections.<Clue>emptyList(),
                Collections.<Npc>emptyList(), Collections.<ScriptCheck>emptyList(),
                Collections.<ScriptTrigger>emptyList(), Collections.<ScriptEnding>emptyList());
    }
}
