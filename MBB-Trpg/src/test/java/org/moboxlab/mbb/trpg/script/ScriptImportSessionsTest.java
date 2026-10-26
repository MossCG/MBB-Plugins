package org.moboxlab.mbb.trpg.script;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import org.moboxlab.mbb.trpg.testutil.RecordingLogger;
import org.moboxlab.mbb.trpg.testutil.ScriptResources;
import org.moboxlab.mbb.trpg.testutil.TempDirs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** 分段导入状态机：拼段、结束、取消、超时、分段与字节上限、拒绝存档。 */
public class ScriptImportSessionsTest {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final long GROUP = 123456L;
    private static final long USER = 88402L;

    private File root;
    private ScriptLibrary library;
    private ScriptValidator validator;
    private ScriptRejectedStore rejected;
    private AtomicLong now;
    private ScriptImportSessions sessions;

    @Before
    public void setUp() {
        root = TempDirs.create("trpg-import-test");
        validator = new ScriptValidator();
        RecordingLogger logger = new RecordingLogger();
        library = new ScriptLibrary(new BuiltinScriptCatalog(
                ScriptImportSessionsTest.class.getClassLoader(), validator),
                new File(root, "scripts"), validator, 65536L, logger);
        rejected = new ScriptRejectedStore(new File(root, "rejected"), 20, logger);
        now = new AtomicLong(1_700_000_000_000L);
        sessions = newSessions(65536L, 5 * 60 * 1000L, 30 * 60 * 1000L, 200);
    }

    @After
    public void tearDown() {
        TempDirs.deleteRecursively(root);
    }

    private ScriptImportSessions newSessions(long maxBytes, long idleMillis, long maxMillis,
                                             int maxSegments) {
        LongSupplier clock = new LongSupplier() {
            @Override
            public long getAsLong() {
                return now.get();
            }
        };
        return new ScriptImportSessions(library, validator, rejected, maxBytes, idleMillis,
                maxMillis, maxSegments, clock);
    }

    @Test
    public void importsInMultipleSegmentsAndSaves() throws ScriptException {
        String json = importableJson();
        sessions.begin(GROUP, USER, ScriptImportMode.CREATE);
        sessions.append(GROUP, USER, json.substring(0, 120));
        sessions.append(GROUP, USER, json.substring(120));

        ScriptImportResult result = sessions.end(GROUP, USER);

        assertNotNull(result);
        assertEquals("import-test-1", result.script().id());
        assertNotNull(library.get("import-test-1"));
        assertFalse(sessions.hasPending(GROUP, USER));
        assertEquals(0L, sessions.pendingBytes(GROUP, USER));
    }

    @Test
    public void parseErrorKeepsPendingUntilComplete() throws ScriptException {
        String json = importableJson();
        int cut = json.length() / 2;
        sessions.begin(GROUP, USER, ScriptImportMode.CREATE);
        sessions.append(GROUP, USER, json.substring(0, cut));

        try {
            sessions.end(GROUP, USER);
            fail("半截 JSON 应抛解析错误");
        } catch (ScriptValidationException expected) {
            assertEquals(ScriptErrorCodes.E_PARSE, expected.code());
        }
        assertTrue(sessions.hasPending(GROUP, USER));
        assertEquals((long) json.substring(0, cut).getBytes(UTF8).length,
                sessions.pendingBytes(GROUP, USER));

        sessions.append(GROUP, USER, json.substring(cut));
        assertNotNull(sessions.end(GROUP, USER));
        assertFalse(sessions.hasPending(GROUP, USER));
    }

    @Test
    public void semanticErrorClearsPendingAndSavesRejected() throws ScriptException {
        sessions.begin(GROUP, USER, ScriptImportMode.CREATE);
        sessions.append(GROUP, USER, "{\"id\":\"bad-import\",\"name\":\"\"}");

        try {
            sessions.end(GROUP, USER);
            fail("语义校验失败应抛异常");
        } catch (ScriptValidationException expected) {
            assertNotNull(expected.validation());
        }

        assertFalse(sessions.hasPending(GROUP, USER));
        assertNull(library.get("bad-import"));
        File[] saved = rejected.directory().listFiles();
        assertNotNull(saved);
        assertTrue(saved.length >= 1);
    }

    @Test
    public void cancelClearsPending() throws ScriptException {
        sessions.begin(GROUP, USER, ScriptImportMode.CREATE);
        sessions.append(GROUP, USER, "{}");

        sessions.cancel(GROUP, USER);

        assertFalse(sessions.hasPending(GROUP, USER));
    }

    @Test
    public void endAndCancelWithoutPendingFail() {
        try {
            sessions.end(GROUP, USER);
            fail("没有暂存时结束应失败");
        } catch (ScriptException expected) {
            assertEquals(ScriptErrorCodes.E_IMPORT_STATE, expected.code());
        }
        try {
            sessions.cancel(GROUP, USER);
            fail("没有暂存时取消应失败");
        } catch (ScriptException expected) {
            assertEquals(ScriptErrorCodes.E_IMPORT_STATE, expected.code());
        }
    }

    @Test
    public void duplicateBeginIsRejected() throws ScriptException {
        sessions.begin(GROUP, USER, ScriptImportMode.CREATE);
        try {
            sessions.begin(GROUP, USER, ScriptImportMode.REPLACE);
            fail("重复开始应失败");
        } catch (ScriptException expected) {
            assertEquals(ScriptErrorCodes.E_IMPORT_STATE, expected.code());
        }
    }

    @Test
    public void idleTimeoutExpiresPending() throws ScriptException {
        sessions.begin(GROUP, USER, ScriptImportMode.CREATE);
        sessions.append(GROUP, USER, "{}");
        now.addAndGet(5 * 60 * 1000L);

        assertFalse(sessions.hasPending(GROUP, USER));
        try {
            sessions.append(GROUP, USER, "{}");
            fail("超时后追加应失败");
        } catch (ScriptException expected) {
            assertEquals(ScriptErrorCodes.E_IMPORT_STATE, expected.code());
        }
    }

    @Test
    public void maxMinutesExpiresEvenWithActivity() throws ScriptException {
        sessions.begin(GROUP, USER, ScriptImportMode.CREATE);
        sessions.append(GROUP, USER, "{}");
        // 每次追加都刷新空闲计时，但总时长从开始算，超过 30 分钟即失效。
        now.addAndGet(30 * 60 * 1000L);

        assertFalse(sessions.hasPending(GROUP, USER));
        try {
            sessions.append(GROUP, USER, "{}");
            fail("超过总时长后追加应失败");
        } catch (ScriptException expected) {
            assertEquals(ScriptErrorCodes.E_IMPORT_STATE, expected.code());
        }
    }

    @Test
    public void segmentLimitRejectsFurtherFragments() throws ScriptException {
        ScriptImportSessions limited = newSessions(65536L, 5 * 60 * 1000L, 30 * 60 * 1000L, 2);
        limited.begin(GROUP, USER, ScriptImportMode.CREATE);
        limited.append(GROUP, USER, "a");
        limited.append(GROUP, USER, "b");
        try {
            limited.append(GROUP, USER, "c");
            fail("超过分段上限应失败");
        } catch (ScriptException expected) {
            assertEquals(ScriptErrorCodes.E_SIZE, expected.code());
        }
        assertFalse(limited.hasPending(GROUP, USER));
    }

    @Test
    public void byteLimitRejectsOversizeContent() throws ScriptException {
        ScriptImportSessions limited = newSessions(16L, 5 * 60 * 1000L, 30 * 60 * 1000L, 200);
        limited.begin(GROUP, USER, ScriptImportMode.CREATE);
        limited.append(GROUP, USER, "0123456789");
        try {
            limited.append(GROUP, USER, "0123456789");
            fail("超过字节上限应失败");
        } catch (ScriptException expected) {
            assertEquals(ScriptErrorCodes.E_SIZE, expected.code());
        }
        assertFalse(limited.hasPending(GROUP, USER));
    }

    private static String importableJson() {
        // 内置库已含 coc-example，导入用例换一个不冲突的 id
        return ScriptResources.example().replace("coc-example", "import-test-1");
    }

    @Test
    public void detectsControlWordsWithSurroundingWhitespace() {
        assertTrue(ScriptImportSessions.isEndControl(" /TRPG script import END "));
        assertTrue(ScriptImportSessions.isCancelControl("/trpg script import cancel"));
        assertFalse(ScriptImportSessions.isEndControl("/trpg script import end 现在"));
        assertFalse(ScriptImportSessions.isCancelControl(null));
    }
}
