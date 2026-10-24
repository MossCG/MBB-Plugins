package org.moboxlab.mbb.trpg.present;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 群内拆分与私聊折叠（呈现规范 §6.1）。 */
public class TrpgMessageSplitterTest {

    private final TrpgMessageSplitter splitter = new TrpgMessageSplitter(new TrpgMessageFormatter());

    @Test
    public void shortMessageKeepsSinglePart() {
        List<String> parts = splitter.split("【提示】", "正文内容", 500, 3);
        assertEquals(1, parts.size());
        assertEquals("【提示】\n正文内容", parts.get(0));
    }

    @Test
    public void splitsByParagraphAndAddsContinuedTitle() {
        StringBuilder body = new StringBuilder();
        for (int index = 0; index < 6; index++) {
            body.append("第").append(index).append("段正文内容比较长一些用来撑满字数上限。\n\n");
        }
        List<String> parts = splitter.split("【战报】", body.toString(), 60, 3);
        assertTrue("应拆成多条，实际 " + parts.size(), parts.size() > 1);
        assertTrue(parts.size() <= 3);
        assertTrue("首条保留原标题", parts.get(0).startsWith("【战报】\n"));
        assertTrue("续页标题带序号", parts.get(1).contains(" · 续 2/"));
        assertTrue("续页标题仍在方括号内", parts.get(1).startsWith("【战报 · 续 2/"));
    }

    @Test
    public void foldsWhenPartsExhausted() {
        StringBuilder body = new StringBuilder();
        for (int index = 0; index < 40; index++) {
            body.append("很长的正文片段").append(index).append("。");
        }
        List<String> parts = splitter.split("【日志】", body.toString(), 50, 3);
        assertEquals(3, parts.size());
        String last = parts.get(parts.size() - 1);
        assertTrue("末条应有折叠提示，实际：" + last, last.contains(TrpgMessageSplitter.GROUP_FOLD_PREFIX));
        assertTrue(last.contains("发送 /trpg log 查看"));
    }

    @Test
    public void firstPartsFitWithinLimit() {
        StringBuilder body = new StringBuilder();
        for (int index = 0; index < 30; index++) {
            body.append("片段").append(index).append("，");
        }
        String text = body.toString();
        List<String> parts = splitter.split("【提示】", text, 40, 3);
        TrpgMessageFormatter formatter = new TrpgMessageFormatter();
        for (int index = 0; index < parts.size() - 1; index++) {
            String bodyPart = parts.get(index).substring(parts.get(index).indexOf('\n') + 1);
            assertTrue("第 " + (index + 1) + " 条正文应在上限内", formatter.count(bodyPart) <= 40);
        }
    }

    @Test
    public void privateFoldKeepsSingleMessage() {
        StringBuilder body = new StringBuilder();
        for (int index = 0; index < 30; index++) {
            body.append("角色卡字段内容").append(index).append("。");
        }
        String folded = splitter.fold("【角色卡】小满", body.toString(), 100);
        assertTrue(folded.startsWith("【角色卡】小满\n"));
        // 私聊折叠与群内同款提示（呈现规范 §5.2），不再自造「私聊不再展开」。
        assertTrue(folded.contains(TrpgMessageSplitter.GROUP_FOLD_PREFIX));
        assertTrue(folded.endsWith("发送 /trpg log 查看）"));
        TrpgMessageFormatter formatter = new TrpgMessageFormatter();
        String bodyPart = folded.substring(folded.indexOf('\n') + 1);
        int foldAt = bodyPart.indexOf("\n\n" + TrpgMessageSplitter.GROUP_FOLD_PREFIX);
        String limited = foldAt < 0 ? bodyPart : bodyPart.substring(0, foldAt);
        assertTrue("正文（不含折叠行）应在上限内", formatter.count(limited) <= 100);
    }

    @Test
    public void untitledMessageHasNoLeadingBlankLine() {
        List<String> parts = splitter.split("", "第一行正文", 500, 3);
        assertEquals(1, parts.size());
        assertEquals("第一行正文", parts.get(0));

        StringBuilder longBody = new StringBuilder();
        for (int index = 0; index < 40; index++) {
            longBody.append("无标题长正文").append(index).append("。");
        }
        for (String part : splitter.split("", longBody.toString(), 40, 3)) {
            assertFalse("无标题消息不应以换行开头：" + part, part.startsWith("\n"));
        }
        assertFalse("无标题折叠不应以换行开头",
                splitter.fold("", longBody.toString(), 60).startsWith("\n"));
    }

    @Test
    public void fitsCountsBodyOnly() {
        assertTrue(splitter.fits("短短正文", 10));
        assertFalse(splitter.fits("超过十个字符的正文内容比较长", 5));
    }

    @Test(expected = IllegalArgumentException.class)
    public void splitRejectsNullTitle() {
        splitter.split(null, "正文", 100, 3);
    }

    @Test
    public void continuedTitleWithoutBracketAppendsSuffix() {
        assertEquals("【提示 · 续 2/3】", TrpgMessageSplitter.continuedTitle("【提示】", 2, 3));
        assertEquals("提示 · 续 2/3", TrpgMessageSplitter.continuedTitle("提示", 2, 3));
    }
}