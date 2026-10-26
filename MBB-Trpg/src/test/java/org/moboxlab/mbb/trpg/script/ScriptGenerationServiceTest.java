package org.moboxlab.mbb.trpg.script;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.charset.Charset;

import org.moboxlab.mbb.trpg.ai.AiErrorTypes;
import org.moboxlab.mbb.trpg.ai.AiRequest;
import org.moboxlab.mbb.trpg.ai.AiResult;
import org.moboxlab.mbb.trpg.testutil.FakeAiClient;
import org.moboxlab.mbb.trpg.testutil.RecordingLogger;
import org.moboxlab.mbb.trpg.testutil.ScriptResources;
import org.moboxlab.mbb.trpg.testutil.TempDirs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** 剧本生成：成功落盘、错误映射、校验失败与拒绝存档、单槽位不排队。 */
public class ScriptGenerationServiceTest {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private File root;
    private ScriptLibrary library;
    private ScriptValidator validator;
    private ScriptRejectedStore rejected;
    private FakeAiClient ai;
    private ScriptGenerationService service;

    @Before
    public void setUp() {
        root = TempDirs.create("trpg-gen-test");
        validator = new ScriptValidator();
        RecordingLogger logger = new RecordingLogger();
        library = new ScriptLibrary(new BuiltinScriptCatalog(
                ScriptGenerationServiceTest.class.getClassLoader(), validator),
                new File(root, "scripts"), validator, 65536L, logger);
        rejected = new ScriptRejectedStore(new File(root, "rejected"), 20, logger);
        ai = new FakeAiClient();
        service = new ScriptGenerationService(library, validator, ai, rejected);
    }

    @After
    public void tearDown() {
        TempDirs.deleteRecursively(root);
    }

    @Test
    public void savesValidGeneratedScript() {
        ai.enqueue(AiResult.success(ScriptResources.example(), "stop", "{}", false, 1L));

        ScriptGenerationOutcome outcome = service.generate("雪山山庄", 4, 90, 88402L);

        assertTrue(outcome.success());
        assertNotNull(outcome.script());
        assertTrue(outcome.script().id().startsWith("gen-"));
        assertNotNull(library.get(outcome.script().id()));
        assertFalse(service.isRunning());
    }

    @Test
    public void stripsMarkdownFenceBeforeParsing() {
        String fenced = "```json\n" + ScriptResources.example() + "\n```";
        ai.enqueue(AiResult.success(fenced, "stop", "{}", false, 1L));

        assertTrue(service.generate("古宅", 3, 60, 1L).success());
    }

    @Test
    public void mapsDisabledErrorAndArchivesRaw() {
        ai.enqueue(AiResult.failure(AiErrorTypes.DISABLED, false, 1L));

        ScriptGenerationOutcome outcome = service.generate("古宅", 3, 60, 88402L);

        assertFalse(outcome.success());
        assertEquals(ScriptErrorCodes.E_AI_DISABLED, outcome.errorCode());
        assertFalse(outcome.savedFile().isEmpty());
        assertFalse(service.isRunning());
    }

    @Test
    public void invalidJsonIsOutputError() {
        ai.enqueue(AiResult.success("这不是 JSON", "stop", "{}", false, 1L));

        ScriptGenerationOutcome outcome = service.generate("古宅", 3, 60, 88402L);

        assertFalse(outcome.success());
        assertEquals(ScriptErrorCodes.E_AI_OUTPUT, outcome.errorCode());
        assertFalse(outcome.savedFile().isEmpty());
        assertTrue(library.list().size() == 1);
    }

    @Test
    public void semanticValidationFailureReportsErrors() {
        ai.enqueue(AiResult.success("{\"id\":\"gen-bad\",\"name\":\"\"}", "stop", "{}", false, 1L));

        ScriptGenerationOutcome outcome = service.generate("古宅", 3, 60, 88402L);

        assertFalse(outcome.success());
        assertEquals(ScriptErrorCodes.E_AI_VALIDATION, outcome.errorCode());
        assertTrue(outcome.errorCount() >= 1);
        assertTrue(outcome.errors().size() >= 1);
        assertNull(library.get("gen-bad"));
    }

    @Test
    public void requestCarriesThemeAndLimits() {
        ai.enqueue(AiResult.success(ScriptResources.example(), "stop", "{}", false, 1L));

        service.generate("雪山山庄", 4, 90, 1L);

        AiRequest request = ai.requests().get(0);
        assertNotNull(request);
        assertTrue(request.dynamic().contains("雪山山庄"));
        assertTrue(request.dynamic().contains("4"));
        assertTrue(request.dynamic().contains("90"));
        assertTrue(request.maxTokens() > 0);
        assertEquals(90, request.timeoutSeconds());
    }

    @Test
    public void timeoutAndGenericFailureMapToCodes() {
        ai.enqueue(AiResult.failure(AiErrorTypes.TIMEOUT, true, 1L));
        ScriptGenerationOutcome timedOut = service.generate("古宅", 3, 60, 88402L);
        assertFalse(timedOut.success());
        assertEquals(ScriptErrorCodes.E_AI_TIMEOUT, timedOut.errorCode());

        ai.enqueue(AiResult.failure(AiErrorTypes.UNKNOWN, false, 1L));
        ScriptGenerationOutcome failed = service.generate("古宅", 3, 60, 88402L);
        assertFalse(failed.success());
        assertEquals(ScriptErrorCodes.E_AI_FAILED, failed.errorCode());
        assertFalse(service.isRunning());
    }

    @Test
    public void emptyOutputIsRecordedAsPlaceholder() {
        ai.enqueue(AiResult.success("", "stop", "{}", false, 1L));

        ScriptGenerationOutcome outcome = service.generate("古宅", 3, 60, 88402L);

        assertFalse(outcome.success());
        assertEquals(ScriptErrorCodes.E_AI_OUTPUT, outcome.errorCode());
        assertFalse("空原文也应留下占位记录", outcome.savedFile().isEmpty());
    }

    @Test
    public void generatedIdFollowsFormatAndAuthorDefaults() {
        ai.enqueue(AiResult.success(ScriptResources.example(), "stop", "{}", false, 1L));

        ScriptGenerationOutcome outcome = service.generate("古宅", 3, 60, 1L);

        assertTrue(outcome.success());
        assertTrue("生成 id 形如 gen-<14位时间>-<4位36进制>：" + outcome.script().id(),
                outcome.script().id().matches("gen-\\d{14}-[0-9a-z]{4}"));
        Script saved = library.get(outcome.script().id());
        assertNotNull(saved);
        assertFalse("author 缺省时应补占位", saved.author().trim().isEmpty());
    }

    @Test
    public void concurrentCallWhileRunningIsBusy() {
        final ScriptGenerationService[] holder = new ScriptGenerationService[1];
        final ScriptGenerationOutcome[] inner = new ScriptGenerationOutcome[1];
        org.moboxlab.mbb.trpg.ai.AiClient reentrant = new org.moboxlab.mbb.trpg.ai.AiClient() {
            @Override
            public AiResult chat(AiRequest request) {
                inner[0] = holder[0].generate("古宅", 3, 60, 1L);
                return AiResult.success(ScriptResources.example(), "stop", "{}", false, 1L);
            }
        };
        ScriptGenerationService busy = new ScriptGenerationService(library, validator, reentrant,
                rejected);
        holder[0] = busy;

        ScriptGenerationOutcome first = busy.generate("古宅", 3, 60, 1L);

        assertTrue(first.success());
        assertNotNull(inner[0]);
        assertFalse(inner[0].success());
        assertEquals(ScriptErrorCodes.E_AI_BUSY, inner[0].errorCode());
        assertFalse(busy.isRunning());
    }
}
