package org.moboxlab.mbb.trpg.present;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** 冻结标题词表（规格 §2.3）与两级标题拼接。 */
public class TrpgTitlesTest {

    @Test
    public void allTitlesAreUniqueAndCount29() {
        assertEquals(29, TrpgTitles.ALL.size());
        Set<String> unique = new HashSet<String>(TrpgTitles.ALL);
        assertEquals(TrpgTitles.ALL.size(), unique.size());
    }

    @Test
    public void singleTitleWrapsInBrackets() {
        assertEquals("【开团】", TrpgTitles.of(TrpgTitles.RECRUIT));
    }

    @Test
    public void twoLevelTitleJoinsWithSeparator() {
        assertEquals("【场景 · 书房】", TrpgTitles.of(TrpgTitles.SCENE, " 书房 "));
    }

    @Test
    public void blankSubtitleFallsBackToSingleTitle() {
        assertEquals("【恢复】", TrpgTitles.of(TrpgTitles.RECOVER, "  "));
        assertEquals("【恢复】", TrpgTitles.of(TrpgTitles.RECOVER, null));
    }

    @Test
    public void allTitlesAppearInSpecOrder() {
        assertEquals(TrpgTitles.RECRUIT, TrpgTitles.ALL.get(0));
        assertEquals(TrpgTitles.HOST, TrpgTitles.ALL.get(25));
        assertEquals(TrpgTitles.LOG, TrpgTitles.ALL.get(TrpgTitles.ALL.size() - 1));
        assertTrue(TrpgTitles.ALL.contains(TrpgTitles.AI_NARRATION));
        assertTrue(TrpgTitles.ALL.contains(TrpgTitles.STATUS));
        assertTrue(TrpgTitles.ALL.contains(TrpgTitles.HELP));
    }
}
