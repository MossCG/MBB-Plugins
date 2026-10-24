package org.moboxlab.mbb.trpg.ai;

import java.util.Arrays;

import org.junit.Test;
import org.moboxlab.mbb.trpg.present.TrpgMessageFormatter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** AI 主持输出解析：清洗、字数、建议白名单与防越权。 */
public class AiNarrationParserTest {

    private static final TrpgMessageFormatter FORMATTER = new TrpgMessageFormatter();

    @Test
    public void parsesPlainNarration() {
        AiNarrationParser.ParseResult result = AiNarrationParser.parse("雨声敲打着窗棂，灯影晃了一下。", 300);

        assertTrue(result.valid());
        assertEquals("雨声敲打着窗棂，灯影晃了一下。", result.narration());
        assertTrue(result.suggestions().isEmpty());
        assertFalse(result.truncated());
        assertEquals("", result.rejectReason());
        // 字段与访问器同值，方便调用方任选读取方式。
        assertEquals(result.narration(), result.narration);
    }

    @Test
    public void stripsCodeFencesAndListPrefixes() {
        AiNarrationParser.ParseResult result = AiNarrationParser.parse(
                "```text\n- 门缝里透出灯光。\n+ 地板在脚下发出轻响。\n```", 300);

        assertTrue(result.valid());
        assertEquals("门缝里透出灯光。\n地板在脚下发出轻响。", result.narration());
        assertFalse(result.narration().contains("```"));
        assertFalse(result.narration().contains("- "));
    }

    @Test
    public void keepsOnlyWhitelistedSuggestionsWithoutPrefix() {
        AiNarrationParser.ParseResult result = AiNarrationParser.parse(
                "雨声更密了。\n"
                        + "建议：调查书桌\n"
                        + "建议：询问管家\n"
                        + "- 建议：使用手电筒\n"
                        + "建议：尝试潜行\n"
                        + "建议：原地转圈\n"
                        + "建议：调查书桌需要 7 小时\n"
                        + "建议：观察窗外", 300);

        assertTrue(result.valid());
        assertEquals("雨声更密了。", result.narration());
        assertEquals(Arrays.asList("调查书桌", "询问管家", "使用手电筒"), result.suggestions());
        for (String suggestion : result.suggestions()) {
            assertFalse(suggestion.startsWith("建议"));
        }
        assertFalse(result.narration().contains("建议"));
    }

    @Test
    public void truncatesLongNarrationAtDefaultMaxChars() {
        String raw = repeat("雾", 350);
        AiNarrationParser.ParseResult result = AiNarrationParser.parse(raw, 300);

        assertTrue(result.valid());
        assertTrue(result.truncated());
        assertEquals(300, FORMATTER.count(result.narration()));
        assertTrue(result.narration().endsWith("…"));
    }

    @Test
    public void appliesCallerMaxChars() {
        AiNarrationParser.ParseResult result = AiNarrationParser.parse(repeat("雨", 20), 10);

        assertTrue(result.valid());
        assertTrue(result.truncated());
        assertEquals(10, FORMATTER.count(result.narration()));
        assertEquals("雨雨雨雨雨雨雨雨雨…", result.narration());
    }

    @Test
    public void keepsPlainMentionsOfHpAndSan() {
        AiNarrationParser.ParseResult result = AiNarrationParser.parse(
                "墙上用粉笔写着 HP 与 SAN 两个词，理智在这里显得格外珍贵。", 300);

        assertTrue("普通叙述里的 HP/SAN/理智字样不应误杀", result.valid());
        assertEquals("", result.rejectReason());
    }

    @Test
    public void rejectsValueChanges() {
        assertRejected("你感到一阵眩晕，HP-2。", AiNarrationParser.REASON_VALUE_CHANGE);
        assertRejected("你的 SAN 减 1。", AiNarrationParser.REASON_VALUE_CHANGE);
        assertRejected("生命值减少 5 点。", AiNarrationParser.REASON_VALUE_CHANGE);
        assertRejected("你的理智值降低 3。", AiNarrationParser.REASON_VALUE_CHANGE);
        assertRejected("力量增加 10。", AiNarrationParser.REASON_VALUE_CHANGE);
    }

    @Test
    public void rejectsCheckVerdicts() {
        assertRejected("侦查检定成功，你发现了脚印。", AiNarrationParser.REASON_CHECK_VERDICT);
        assertRejected("KP 宣布判定通过。", AiNarrationParser.REASON_CHECK_VERDICT);
        assertRejected("骰值为 42。", AiNarrationParser.REASON_CHECK_VERDICT);
    }

    @Test
    public void rejectsTurnAdvance() {
        assertRejected("现在进入下一幕。", AiNarrationParser.REASON_TURN_ADVANCE);
        assertRejected("场景推进，灯光熄灭。", AiNarrationParser.REASON_TURN_ADVANCE);
    }

    @Test
    public void rejectsEmptyOutput() {
        assertEquals(AiNarrationParser.REASON_EMPTY, rejectReasonOf(null));
        assertEquals(AiNarrationParser.REASON_EMPTY, rejectReasonOf(""));
        assertEquals(AiNarrationParser.REASON_EMPTY, rejectReasonOf("   \n\n  "));
        assertEquals(AiNarrationParser.REASON_EMPTY, rejectReasonOf("```\n```"));
    }

    @Test
    public void rejectsSuggestionOnlyOutput() {
        AiNarrationParser.ParseResult result = AiNarrationParser.parse(
                "建议：调查书桌\n建议：观察窗外", 300);

        assertFalse(result.valid());
        assertEquals(AiNarrationParser.REASON_EMPTY, result.rejectReason());
        assertEquals("", result.narration());
        assertTrue(result.suggestions().isEmpty());
    }

    @Test
    public void neverReturnsNull() {
        assertNotNull(AiNarrationParser.parse(null, 300));
        assertNotNull(AiNarrationParser.parse(null, 0));
    }

    private static void assertRejected(String raw, String expectedReason) {
        AiNarrationParser.ParseResult result = AiNarrationParser.parse(raw, 300);
        assertFalse("应拒绝：" + raw, result.valid());
        assertEquals(expectedReason, result.rejectReason());
        assertEquals("", result.narration());
        assertTrue(result.suggestions().isEmpty());
        assertFalse(result.truncated());
    }

    private static String rejectReasonOf(String raw) {
        return AiNarrationParser.parse(raw, 300).rejectReason();
    }

    private static String repeat(String text, int times) {
        StringBuilder builder = new StringBuilder(text.length() * times);
        for (int index = 0; index < times; index++) {
            builder.append(text);
        }
        return builder.toString();
    }
}
