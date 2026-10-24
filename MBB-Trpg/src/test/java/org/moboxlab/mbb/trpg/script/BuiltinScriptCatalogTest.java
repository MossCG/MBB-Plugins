package org.moboxlab.mbb.trpg.script;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** 内置剧本库：资源加载、只读语义与读写锁骨架。 */
public class BuiltinScriptCatalogTest {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private BuiltinScriptCatalog catalog;

    @Before
    public void setUp() {
        catalog = new BuiltinScriptCatalog(BuiltinScriptCatalogTest.class.getClassLoader(),
                new ScriptValidator());
    }

    @Test
    public void loadsBuiltinExample() {
        assertEquals(1, catalog.size());
        Script script = catalog.get("coc-example");
        assertNotNull(script);
        assertEquals("雨夜古宅", script.name());
        assertNull(catalog.get("no-such-script"));
        assertNull(catalog.get(null));
        assertNull(catalog.info("no-such-script"));
    }

    @Test
    public void listsInfosSortedBySource() {
        assertEquals(1, catalog.list().size());
        ScriptInfo info = catalog.list().get(0);
        assertEquals("coc-example", info.id());
        assertEquals(ScriptSource.BUILTIN, info.source());
    }

    @Test
    public void refusesToDeleteBuiltin() {
        try {
            catalog.delete("coc-example");
            fail("内置剧本不应允许删除");
        } catch (ScriptException e) {
            assertEquals(ScriptErrorCodes.E_BUILTIN, e.code());
            assertEquals("$.id", e.path());
        }
    }

    @Test
    public void reportsUnknownDeleteAsNotFound() {
        try {
            catalog.delete("no-such-script");
            fail("删除未知剧本应报 E_NOT_FOUND");
        } catch (ScriptException e) {
            assertEquals(ScriptErrorCodes.E_NOT_FOUND, e.code());
        }
    }

    @Test
    public void replaceIsNotEnabledUntilImportSlice() {
        try {
            catalog.replace("new-one", minimalScript("new-one").getBytes(UTF8));
            fail("替换导入尚未启用");
        } catch (ScriptException e) {
            assertEquals(ScriptErrorCodes.E_IMPORT_STATE, e.code());
        }
    }

    @Test
    public void readAndWriteLocksAreUsable() {
        Lock readLock = catalog.acquireReadLock();
        assertNotNull(readLock);
        readLock.lock();
        try {
            assertNotNull(catalog.get("coc-example"));
        } finally {
            readLock.unlock();
        }
        Lock writeLock = catalog.acquireWriteLock();
        writeLock.lock();
        try {
            assertNotNull(catalog.get("coc-example"));
        } finally {
            writeLock.unlock();
        }
    }

    @Test
    public void loadsFromProvidedClasspath() {
        Map<String, String> resources = new HashMap<String, String>();
        resources.put("scripts/index.txt", "test-one\n");
        resources.put("scripts/test-one.json", minimalScript("test-one"));

        BuiltinScriptCatalog custom = new BuiltinScriptCatalog(loaderOf(resources),
                new ScriptValidator());

        assertEquals(1, custom.size());
        assertEquals("测试剧本", custom.get("test-one").name());
        assertTrue(custom.warnings().isEmpty());
    }

    @Test
    public void collectsValidationWarnings() {
        Map<String, String> resources = new HashMap<String, String>();
        resources.put("scripts/index.txt", "test-one\n");
        resources.put("scripts/test-one.json",
                minimalScript("test-one").replace("\"ruleSet\":\"d100\",",
                        "\"ruleSet\":\"d100\",\"foo\":1,"));

        BuiltinScriptCatalog custom = new BuiltinScriptCatalog(loaderOf(resources),
                new ScriptValidator());

        assertEquals(1, custom.warnings().size());
        assertTrue(custom.warnings().get(0).contains(ScriptErrorCodes.W_UNKNOWN_FIELD));
    }

    @Test(expected = IllegalStateException.class)
    public void failsWhenIndexIsMissing() {
        new BuiltinScriptCatalog(loaderOf(new HashMap<String, String>()), new ScriptValidator());
    }

    @Test(expected = IllegalStateException.class)
    public void failsWhenScriptFileIsMissing() {
        Map<String, String> resources = new HashMap<String, String>();
        resources.put("scripts/index.txt", "test-one\n");

        new BuiltinScriptCatalog(loaderOf(resources), new ScriptValidator());
    }

    @Test(expected = IllegalStateException.class)
    public void failsWhenIndexRepeatsAnId() {
        Map<String, String> resources = new HashMap<String, String>();
        resources.put("scripts/index.txt", "test-one\ntest-one\n");
        resources.put("scripts/test-one.json", minimalScript("test-one"));

        new BuiltinScriptCatalog(loaderOf(resources), new ScriptValidator());
    }

    @Test(expected = IllegalStateException.class)
    public void failsWhenBuiltinScriptIsInvalid() {
        Map<String, String> resources = new HashMap<String, String>();
        resources.put("scripts/index.txt", "test-one\n");
        resources.put("scripts/test-one.json", "{\"schemaVersion\": 2}");

        new BuiltinScriptCatalog(loaderOf(resources), new ScriptValidator());
    }

    private static String minimalScript(String id) {
        return "{\"schemaVersion\":1,\"id\":\"" + id + "\",\"name\":\"测试剧本\","
                + "\"ruleSet\":\"d100\",\"minPlayers\":1,\"maxPlayers\":2,"
                + "\"estimatedMinutes\":30,"
                + "\"scenes\":[{\"id\":\"s1\",\"title\":\"开场\","
                + "\"narration\":\"测试文本\"}],\"endings\":[]}";
    }

    private static ClassLoader loaderOf(final Map<String, String> resources) {
        return new ClassLoader() {
            @Override
            public InputStream getResourceAsStream(String name) {
                String text = resources.get(name);
                if (text == null) {
                    return null;
                }
                return new ByteArrayInputStream(text.getBytes(UTF8));
            }
        };
    }
}
