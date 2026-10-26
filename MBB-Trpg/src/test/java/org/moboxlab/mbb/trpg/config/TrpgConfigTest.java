package org.moboxlab.mbb.trpg.config;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 配置读取验收：内置 44 项默认值必须与随包发布的 config.yml 完全一致，
 * 缺失回默认、未知键与非法值告警、presentation.style 只接受 broadcast。
 */
public class TrpgConfigTest {

    private static final String MARKER = "#MBB-Trpg 插件配置";

    /** config.yml 与内置默认值逐项一致（键、顺序、值）。 */
    @Test
    public void configYmlMatchesBuiltInDefaults() throws Exception {
        Map<String, String> fromFile = readConfigYml();
        Map<String, String> defaults = TrpgConfig.defaults();

        assertEquals("配置项数量", 44, defaults.size());
        assertEquals("config.yml 配置项数量", 44, fromFile.size());
        assertEquals("键集合与顺序必须一致",
                new ArrayList<String>(defaults.keySet()), new ArrayList<String>(fromFile.keySet()));
        for (Map.Entry<String, String> entry : defaults.entrySet()) {
            assertEquals("默认值不一致：" + entry.getKey(), entry.getValue(), fromFile.get(entry.getKey()));
        }
        assertEquals("keys() 数量", 44, TrpgConfig.keys().size());
    }

    /** 空配置全部回退默认值，类型化读取正确。 */
    @Test
    public void emptyValuesFallBackToDefaults() {
        TrpgConfig config = TrpgConfig.fromValues(new HashMap<String, String>());
        assertTrue("空配置不应有告警：" + config.warnings(), config.warnings().isEmpty());
        assertEquals("d100", config.ruleSetId());
        assertEquals(24, config.recruitExpireHours());
        assertEquals(30, config.startCooldownSeconds());
        assertEquals(2000, config.logMaxContentChars());
        assertEquals("broadcast", config.presentationStyle());
        assertEquals(65536L, config.scriptMaxBytes());
        assertFalse(config.aiHostEnabled());
        assertEquals(45, config.aiDeadlineSeconds());
        assertEquals(300, config.aiNarrationMaxChars());
        assertTrue(config.aiTraceEnabled());
        assertEquals(5000, config.aiTraceMaxRows());
    }

    /** 未知键告警但不影响其它配置。 */
    @Test
    public void unknownKeyIsWarnedAndIgnored() {
        Map<String, String> raw = new HashMap<String, String>();
        raw.put("session.recruitExpireHours", "12");
        raw.put("legacy.traceHours", "24");
        TrpgConfig config = TrpgConfig.fromValues(raw);
        assertEquals(12, config.recruitExpireHours());
        boolean warned = false;
        for (String warning : config.warnings()) {
            if (warning.contains("legacy.traceHours")) {
                warned = true;
            }
        }
        assertTrue("未知键应告警：" + config.warnings(), warned);
    }

    /** int 解析失败回退默认值并告警。 */
    @Test
    public void invalidNumberFallsBackToDefaultWithWarning() {
        Map<String, String> raw = new HashMap<String, String>();
        raw.put("session.recruitExpireHours", "abc");
        TrpgConfig config = TrpgConfig.fromValues(raw);
        assertEquals(24, config.recruitExpireHours());
        assertTrue("非法值应告警：" + config.warnings(), !config.warnings().isEmpty());
    }

    /** presentation.style 只接受 broadcast，其它值回退并告警。 */
    @Test
    public void presentationStyleFallsBackToBroadcast() {
        Map<String, String> raw = new HashMap<String, String>();
        raw.put("presentation.style", "verbose");
        TrpgConfig config = TrpgConfig.fromValues(raw);
        assertEquals("broadcast", config.presentationStyle());
        boolean warned = false;
        for (String warning : config.warnings()) {
            if (warning.contains("presentation.style")) {
                warned = true;
            }
        }
        assertTrue("非 broadcast 应告警：" + config.warnings(), warned);
    }

    /** raw() 只接受已知键。 */
    @Test
    public void rawRejectsUnknownKey() {
        TrpgConfig config = TrpgConfig.fromValues(new HashMap<String, String>());
        assertEquals("d100", config.raw("ruleset"));
        try {
            config.raw("not.exists");
            fail("未知键应抛 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    /** 从类路径里挑出本插件随包发布的 config.yml（其它 jar 也带同名资源，按首行标记区分）。 */
    private List<String> readConfigYmlLines() throws Exception {
        Enumeration<URL> resources = TrpgConfigTest.class.getClassLoader().getResources("config.yml");
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            List<String> lines = readLines(url.openStream());
            if (!lines.isEmpty() && lines.get(0).contains(MARKER)) {
                return lines;
            }
        }
        fail("插件 JAR 资源里找不到 config.yml（首行应含 " + MARKER + "）");
        return new ArrayList<String>();
    }

    private List<String> readLines(InputStream stream) throws Exception {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, Charset.forName("UTF-8")));
        try {
            List<String> lines = new ArrayList<String>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            return lines;
        } finally {
            reader.close();
        }
    }

    /** 按行读取随包 config.yml，与主程序 PluginConfig 的平面解析保持一致。 */
    private Map<String, String> readConfigYml() throws Exception {
        Map<String, String> values = new LinkedHashMap<String, String>();
        for (String current : readConfigYmlLines()) {
            String trimmed = current.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int colon = trimmed.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = trimmed.substring(0, colon).trim();
            String value = trimmed.substring(colon + 1).trim();
            int comment = value.indexOf('#');
            if (comment >= 0) {
                value = value.substring(0, comment).trim();
            }
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            values.put(key, value);
        }
        return values;
    }
}
