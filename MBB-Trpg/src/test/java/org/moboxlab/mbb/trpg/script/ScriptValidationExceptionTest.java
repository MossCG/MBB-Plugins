package org.moboxlab.mbb.trpg.script;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/** 校验异常：携带完整校验结果，首个错误作为异常的错误码、路径与文案。 */
public class ScriptValidationExceptionTest {

    @Test
    public void carriesValidationResultAndFirstIssue() {
        List<ScriptIssue> errors = Arrays.asList(
                new ScriptIssue(ScriptErrorCodes.E_DUP, "$.scenes[1].id", "节点 id 重复：gate"),
                new ScriptIssue(ScriptErrorCodes.E_REF, "$.scenes[0].clueIds[0]", "引用的线索 id 不存在"));
        ScriptValidationResult validation = ScriptValidationResult.failed(errors);

        ScriptValidationException exception = new ScriptValidationException(validation);

        assertSame(validation, exception.validation());
        assertEquals(ScriptErrorCodes.E_DUP, exception.code());
        assertEquals("$.scenes[1].id", exception.path());
        assertEquals("节点 id 重复：gate", exception.getMessage());
    }

    @Test
    public void fallsBackWhenNoErrorsCollected() {
        ScriptValidationException exception = new ScriptValidationException(
                ScriptValidationResult.failed(Collections.<ScriptIssue>emptyList()));

        assertEquals(ScriptErrorCodes.E_PARSE, exception.code());
        assertEquals("$", exception.path());
        assertEquals("剧本校验未通过", exception.getMessage());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNullValidation() {
        new ScriptValidationException(null);
    }
}
