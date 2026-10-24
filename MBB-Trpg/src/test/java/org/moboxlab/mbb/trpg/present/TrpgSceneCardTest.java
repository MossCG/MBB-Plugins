package org.moboxlab.mbb.trpg.present;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/** 情景卡降级正文的优先级（呈现规范 §7.4）。 */
public class TrpgSceneCardTest {

    @Test
    public void fallbackTextPrefersSceneNarration() {
        TrpgSceneCard card = new TrpgSceneCard("书房", 2, 4,
                Collections.<String>emptyList(), Collections.<String>emptyList(),
                "灯芯爆了一下。", "剧本原文兜底。", Collections.<String>emptyList());
        assertEquals("灯芯爆了一下。", card.fallbackText());
    }

    @Test
    public void fallbackTextUsesScriptNarrationWhenSceneNarrationMissing() {
        TrpgSceneCard card = new TrpgSceneCard("书房", 2, 4,
                Collections.<String>emptyList(), Collections.<String>emptyList(),
                "  ", "剧本原文兜底。", Collections.<String>emptyList());
        assertEquals("剧本原文兜底。", card.fallbackText());
    }

    @Test
    public void rejectsBlankTitle() {
        try {
            new TrpgSceneCard("  ", 1, 1, Collections.<String>emptyList(),
                    Collections.<String>emptyList(), "", "", Collections.<String>emptyList());
            fail("应当拒绝空场景标题");
        } catch (IllegalArgumentException expected) {
            assertEquals("场景标题不能为空", expected.getMessage());
        }
    }
}
