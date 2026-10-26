package org.moboxlab.mbb.trpg.present;

import org.junit.Test;
import org.moboxlab.mbb.trpg.config.TrpgConfig;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 呈现配置收敛（presentation.*）。 */
public class TrpgPresentConfigTest {

    @Test
    public void defaultsMatchSpec() {
        TrpgPresentConfig config = TrpgPresentConfig.defaults();
        assertEquals(500, config.groupMaxChars());
        assertEquals(1200, config.privateMaxChars());
        assertEquals(3, config.splitMax());
        assertTrue(config.sceneImage());
        assertTrue(config.reportImage());
        assertEquals(22, config.imageMaxLines());
        assertTrue(config.broadcastStyle());
    }

    @Test
    public void readsValuesFromPluginConfig() {
        Map<String, String> raw = new HashMap<String, String>();
        raw.put("presentation.groupMaxChars", "600");
        raw.put("presentation.privateMaxChars", "900");
        raw.put("presentation.splitMax", "2");
        raw.put("presentation.sceneImage", "false");
        raw.put("presentation.reportImage", "false");
        raw.put("presentation.imageMaxLines", "18");
        raw.put("presentation.style", "broadcast");
        TrpgPresentConfig config = new TrpgPresentConfig(TrpgConfig.fromValues(raw));
        assertEquals(600, config.groupMaxChars());
        assertEquals(900, config.privateMaxChars());
        assertEquals(2, config.splitMax());
        assertFalse(config.sceneImage());
        assertFalse(config.reportImage());
        assertEquals(18, config.imageMaxLines());
        assertTrue(config.broadcastStyle());
    }

    @Test
    public void clampsOutOfRangeValues() {
        Map<String, String> raw = new HashMap<String, String>();
        raw.put("presentation.groupMaxChars", "0");
        raw.put("presentation.privateMaxChars", "-5");
        raw.put("presentation.splitMax", "9");
        raw.put("presentation.imageMaxLines", "2");
        TrpgPresentConfig config = new TrpgPresentConfig(TrpgConfig.fromValues(raw));
        assertEquals(500, config.groupMaxChars());
        assertEquals(1200, config.privateMaxChars());
        assertEquals(3, config.splitMax());
        assertEquals(5, config.imageMaxLines());
    }

    @Test
    public void tinyLimitsAreRaisedToFloor() {
        TrpgPresentConfig config = new TrpgPresentConfig(10, 20, 0, true, true, 0, null);
        assertEquals(50, config.groupMaxChars());
        assertEquals(50, config.privateMaxChars());
        assertEquals(3, config.splitMax());
        assertEquals(22, config.imageMaxLines());
        assertTrue(config.broadcastStyle());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNullPluginConfig() {
        new TrpgPresentConfig(null);
    }
}
