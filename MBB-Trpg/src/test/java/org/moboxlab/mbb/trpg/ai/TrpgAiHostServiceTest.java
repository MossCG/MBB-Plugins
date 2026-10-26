package org.moboxlab.mbb.trpg.ai;

import org.junit.Test;
import org.moboxlab.mbb.trpg.config.TrpgConfig;
import org.moboxlab.mbb.trpg.session.SessionSnapshot;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * {@link TrpgAiHostService} 的 v1 契约：全局开关、参数解析、四分支与只读视图绑定（验收 I1/I4/I6/I7）。
 */
public class TrpgAiHostServiceTest {

    private static TrpgConfig config(boolean enabled) {
        Map<String, String> raw = new LinkedHashMap<String, String>();
        raw.put("aiHost.enabled", enabled ? "true" : "false");
        return TrpgConfig.fromValues(raw);
    }

    @Test
    public void parseWordAcceptsChineseAndEnglishWords() {
        assertEquals(Boolean.TRUE, TrpgAiHostService.parseWord("on"));
        assertEquals(Boolean.TRUE, TrpgAiHostService.parseWord("ON"));
        assertEquals(Boolean.TRUE, TrpgAiHostService.parseWord(" 开 "));
        assertEquals(Boolean.FALSE, TrpgAiHostService.parseWord("off"));
        assertEquals(Boolean.FALSE, TrpgAiHostService.parseWord("Off"));
        assertEquals(Boolean.FALSE, TrpgAiHostService.parseWord("关"));
    }

    @Test
    public void parseWordRejectsEverythingElse() {
        assertNull(TrpgAiHostService.parseWord(null));
        assertNull(TrpgAiHostService.parseWord(""));
        assertNull(TrpgAiHostService.parseWord("   "));
        assertNull(TrpgAiHostService.parseWord("也许"));
        assertNull(TrpgAiHostService.parseWord("onoff"));
    }

    @Test
    public void toggleWithoutWordIsUsage() {
        TrpgAiHostService host = new TrpgAiHostService(config(false));

        assertEquals(AiToggle.USAGE, host.toggle(null));
        assertEquals(AiToggle.USAGE, host.toggle("也许"));
        assertFalse(host.toggle(null).accepted());
    }

    @Test
    public void toggleOnIsAcknowledgedWhenGlobalSwitchIsOn() {
        TrpgAiHostService host = new TrpgAiHostService(config(true));

        assertEquals(AiToggle.ON_ACK, host.toggle("on"));
        assertTrue(host.toggle("on").accepted());
    }

    @Test
    public void toggleOffIsAlwaysAcknowledged() {
        TrpgAiHostService disabled = new TrpgAiHostService(config(false));
        TrpgAiHostService enabled = new TrpgAiHostService(config(true));

        assertEquals(AiToggle.OFF_ACK, disabled.toggle("off"));
        assertEquals(AiToggle.OFF_ACK, enabled.toggle("关"));
        assertTrue(disabled.toggle("off").accepted());
        assertTrue(enabled.toggle("off").accepted());
    }

    @Test
    public void globallyEnabledReflectsConfig() {
        assertFalse(new TrpgAiHostService(config(false)).globallyEnabled());
        assertTrue(new TrpgAiHostService(config(true)).globallyEnabled());
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorRejectsNullConfig() {
        new TrpgAiHostService(null);
    }

    @Test
    public void bindSessionViewExposesTheInjectedView() {
        TrpgAiHostService host = new TrpgAiHostService(config(false));
        assertNull(host.sessionView());

        SessionView view = new SessionView() {
            @Override
            public SessionSnapshot readSnapshot(long groupId) {
                return SessionSnapshot.none(groupId);
            }
        };
        host.bindSessionView(view);

        assertSame(view, host.sessionView());
    }
}
