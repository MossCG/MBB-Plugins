package org.moboxlab.mbb.trpg.present;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** 消息清洗与长度控制（呈现规范 §2.1）。 */
public class TrpgMessageFormatterTest {

    private final TrpgMessageFormatter formatter = new TrpgMessageFormatter();

    @Test
    public void cleansMarkdownAndEmoji() {
        String cleaned = formatter.clean("**重点** 与 __斜体__ 和 ~~删除~~ `code` | 表格 🎲🔥");
        assertEquals("重点 与 斜体 和 删除 code 表格", cleaned);
    }

    @Test
    public void stripsLinePrefixes() {
        String cleaned = formatter.clean("> 引用行\n## 标题行\n- 列表行\n+ 加号行");
        assertEquals("引用行\n标题行\n列表行\n加号行", cleaned);
    }

    @Test
    public void normalizesLineEndingsAndSpaces() {
        String cleaned = formatter.clean("第一行  \r\n\r\n第二行\t末尾\n\n\n\n第三行");
        assertEquals("第一行\n\n第二行 末尾\n\n第三行", cleaned);
    }

    @Test
    public void cleanNullReturnsEmpty() {
        assertEquals("", formatter.clean(null));
    }

    @Test
    public void countsCodePoints() {
        assertEquals(0, formatter.count(null));
        assertEquals(0, formatter.count(""));
        assertEquals(3, formatter.count("调查员"));
        // 星形符号属于 emoji 区间，但 count 只数码点，不做清洗。
        assertEquals(2, formatter.count("🎲🔥"));
    }

    @Test
    public void truncateKeepsEllipsis() {
        // 未超过上限时原样返回，超过才截断补省略号。
        assertEquals("调查员", formatter.truncate("调查员", 3));
        assertEquals("调…", formatter.truncate("调查员", 2));
        assertEquals("", formatter.truncate("调查员", 0));
        assertEquals("", formatter.truncate("调查员", -1));
        assertEquals("", formatter.truncate(null, 5));
    }

    @Test
    public void truncateDoesNotSplitSurrogatePair() {
        String text = "🎲🎲🎲🎲";
        String truncated = formatter.truncate(text, 3);
        assertEquals(3, formatter.count(truncated));
        assertEquals("🎲🎲…", truncated);
    }

    @Test
    public void takeDoesNotAppendEllipsis() {
        assertEquals("调查", formatter.take("调查员", 2));
        assertEquals("调查员", formatter.take("调查员", 9));
        assertEquals("", formatter.take(null, 3));
        assertEquals("", formatter.take("调查员", 0));
    }

    @Test
    public void cleanAndTruncateCombinesBoth() {
        assertEquals("重点 内容", formatter.cleanAndTruncate("**重点**  内容", 20));
        assertEquals("重点…", formatter.cleanAndTruncate("**重点内容**", 3));
    }
}
