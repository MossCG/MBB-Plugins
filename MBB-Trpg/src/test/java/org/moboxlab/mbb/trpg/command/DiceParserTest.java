package org.moboxlab.mbb.trpg.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** 骰子表达式解析：只接受 NdM 与加法，最多 3 段。 */
public class DiceParserTest {

    @Test
    public void parsesSingleDice() {
        DiceParser.Parsed parsed = DiceParser.parse("3d6");
        assertNotNull(parsed);
        assertEquals(1, parsed.size());
        assertEquals("3D6", parsed.segments().get(0).describe());
        assertEquals("3d6", parsed.expression());
    }

    @Test
    public void parsesConstantOnly() {
        DiceParser.Parsed parsed = DiceParser.parse("7");
        assertNotNull(parsed);
        assertEquals(1, parsed.size());
        assertEquals(0, parsed.segments().get(0).count());
        assertEquals(7, parsed.segments().get(0).modifier());
        assertEquals("7", parsed.segments().get(0).describe());
    }

    @Test
    public void parsesMultipleSegments() {
        DiceParser.Parsed parsed = DiceParser.parse("1d100+5");
        assertNotNull(parsed);
        assertEquals(2, parsed.size());
        assertEquals("1D100", parsed.segments().get(0).describe());
        assertEquals(0, parsed.segments().get(1).count());
        assertEquals(5, parsed.segments().get(1).modifier());
    }

    @Test
    public void keepsOriginalExpressionWithSpaces() {
        DiceParser.Parsed parsed = DiceParser.parse(" 1d100 + 5 ");
        assertNotNull(parsed);
        assertEquals("1d100 + 5", parsed.expression());
    }

    @Test
    public void rejectsExpressionWithMoreThanThreeSegments() {
        assertNull(DiceParser.parse("1d6+1d6+1d6+1d6"));
    }

    @Test
    public void rejectsMultiplicationAndSkillNames() {
        assertNull(DiceParser.parse("3d6*2"));
        assertNull(DiceParser.parse("侦查"));
        assertNull(DiceParser.parse("1d"));
        assertNull(DiceParser.parse("d6"));
    }

    @Test
    public void rejectsOutOfRangeDiceAndConstants() {
        assertNull(DiceParser.parse("21d6"));
        assertNull(DiceParser.parse("0d6"));
        assertNull(DiceParser.parse("1d1001"));
        assertNull(DiceParser.parse("1000"));
    }

    @Test
    public void rejectsNegativeAndEmptySegments() {
        assertNull(DiceParser.parse("-3d6"));
        assertNull(DiceParser.parse("3d6++2"));
        assertNull(DiceParser.parse("1d100+"));
    }

    @Test
    public void constantSegmentHasBlankText() {
        DiceParser.Parsed parsed = DiceParser.parse("1d100+5");
        assertNotNull(parsed);
        assertEquals("1d100", parsed.segmentTexts().get(0));
        assertEquals("", parsed.segmentTexts().get(1));
    }

    @Test
    public void rejectsEmptyInput() {
        assertNull(DiceParser.parse(""));
        assertNull(DiceParser.parse("   "));
        assertNull(DiceParser.parse(null));
    }
}
