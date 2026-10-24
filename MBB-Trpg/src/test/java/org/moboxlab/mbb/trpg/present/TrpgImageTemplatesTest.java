package org.moboxlab.mbb.trpg.present;

import org.junit.Test;
import org.moboxlab.mbb.trpg.script.Clue;
import org.moboxlab.mbb.trpg.script.Scene;
import org.moboxlab.mbb.trpg.script.Script;
import org.moboxlab.mbb.trpg.script.ScriptEnding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 图片行版式（呈现规范 §7.3/§7.4）。 */
public class TrpgImageTemplatesTest {

    private static long endedAt() {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(2026, Calendar.OCTOBER, 22, 21, 30, 0);
        return calendar.getTimeInMillis();
    }

    private static TrpgReport report() {
        return TrpgReport.builder()
                .scriptName("雨夜古宅").endingTitle("雨夜脱身")
                .startedAt(endedAt() - 126L * 60L * 1000L).endedAt(endedAt())
                .durationMinutes(126).kpName("阿岚")
                .playerNames(Arrays.asList("小满", "老周"))
                .clueNames(Arrays.asList("泥脚印", "未寄出的信", "煤油灯下的影子"))
                .clueTotal(4).checkTotal(5).checkSuccess(3)
                .checkResults(Arrays.asList("聆听 成功", "侦察 成功", "理智 失败"))
                .growthLines(Arrays.asList("聆听 50 → 52", "侦察 40 → 41"))
                .retentionDays(30).build();
    }

    private static Script script() {
        List<Scene> scenes = Arrays.asList(
                new Scene("scene-gate", "古宅门前", "", "门前叙述。", "",
                        Collections.<String>emptyList(), Collections.<String>emptyList(),
                        Collections.<String>emptyList()),
                new Scene("scene-hall", "门厅", "", "门厅叙述。", "",
                        Collections.<String>emptyList(), Collections.<String>emptyList(),
                        Collections.<String>emptyList()));
        List<Clue> clues = Collections.singletonList(
                new Clue("clue-print", "泥脚印", "地上有脚印。", "脚印通向书房。"));
        List<ScriptEnding> endings = Collections.singletonList(
                new ScriptEnding("ending-escape", "雨夜脱身", "走进雨里。",
                        Collections.<String>emptyList()));
        return new Script(1, "coc-example", "雨夜古宅", "MBB-Trpg", "简介。", "d100",
                2, 4, 60, Arrays.asList("1920 年代", "调查"),
                Collections.<String>emptyList(), scenes, clues,
                Collections.<org.moboxlab.mbb.trpg.script.Npc>emptyList(),
                Collections.<org.moboxlab.mbb.trpg.script.ScriptCheck>emptyList(),
                Collections.<org.moboxlab.mbb.trpg.script.ScriptTrigger>emptyList(), endings);
    }

    private static TrpgSceneCard sceneCard(String narration, List<String> presentNames) {
        return new TrpgSceneCard("书房", 2, 4, presentNames,
                Arrays.asList("泥脚印", "未寄出的信"), narration,
                "书房纯文本叙述。", Arrays.asList("翻看书桌", "查看壁炉"));
    }

    @Test
    public void wrapsAtLineWidth() {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < 30; index++) {
            text.append("字");
        }
        List<String> lines = TrpgImageTemplates.wrapForImage(text.toString());
        assertEquals(2, lines.size());
        assertEquals(26, lines.get(0).codePointCount(0, lines.get(0).length()));
        assertEquals(4, lines.get(1).codePointCount(0, lines.get(1).length()));
    }

    @Test
    public void wrapKeepsExistingLineBreaksAndSkipsEmpty() {
        List<String> lines = TrpgImageTemplates.wrapForImage("第一行\n\n第二行");
        assertEquals(Arrays.asList("第一行", "第二行"), lines);
        assertTrue(TrpgImageTemplates.wrapForImage(null).isEmpty());
        assertTrue(TrpgImageTemplates.wrapForImage("").isEmpty());
    }

    @Test
    public void wrapDoesNotSplitSurrogatePairs() {
        String text = "🎲🎲🎲";
        List<String> lines = TrpgImageTemplates.wrapForImage(text, 2);
        assertEquals(2, lines.size());
        assertEquals(2, lines.get(0).codePointCount(0, lines.get(0).length()));
        assertEquals(1, lines.get(1).codePointCount(0, lines.get(1).length()));
    }

    @Test
    public void sceneCardHasHeaderAndSections() {
        List<String> lines = TrpgImageTemplates.scene(
                sceneCard("书房里只有一盏煤油灯。", Arrays.asList("小满", "老周")));
        assertEquals("【场景】书房 · 第 2/4 幕", lines.get(0));
        assertTrue(lines.contains("【在场】小满、老周"));
        assertTrue(lines.contains("【线索】泥脚印、未寄出的信"));
        assertTrue(lines.contains("书房里只有一盏煤油灯。"));
        assertTrue(lines.contains("【下一步】翻看书桌；查看壁炉"));
        assertTrue("行数应不超过 14，实际 " + lines.size(), lines.size() <= TrpgImageTemplates.SCENE_MAX_LINES);
        for (String line : lines) {
            assertTrue("每行不超过 26 全角字符宽：" + line,
                    displayWidth(line) <= TrpgImageTemplates.LINE_WIDTH * 2);
        }
    }

    @Test
    public void sceneCardSkipsEmptySections() {
        TrpgSceneCard card = new TrpgSceneCard("书房", 2, 4,
                Collections.<String>emptyList(), Collections.<String>emptyList(),
                "", "", Collections.<String>emptyList());
        List<String> lines = TrpgImageTemplates.scene(card);
        assertEquals(Collections.singletonList("【场景】书房 · 第 2/4 幕"), lines);
    }

    @Test
    public void sceneCardCapsTotalLines() {
        List<String> people = new ArrayList<String>();
        for (int index = 0; index < 40; index++) {
            people.add("成员" + index);
        }
        StringBuilder narration = new StringBuilder();
        for (int index = 0; index < 200; index++) {
            narration.append("叙述");
        }
        List<String> lines = TrpgImageTemplates.scene(
                sceneCard(narration.toString(), people));
        assertTrue("应截到 14 行以内，实际 " + lines.size(),
                lines.size() <= TrpgImageTemplates.SCENE_MAX_LINES);
    }

    @Test(expected = IllegalArgumentException.class)
    public void sceneRejectsNullCard() {
        TrpgImageTemplates.scene(null);
    }

    @Test
    public void reportHasNineFrozenSections() {
        List<String> lines = TrpgImageTemplates.report(report());
        assertTrue(lines.size() >= 9);
        String flat = flat(lines);
        assertEquals("【局】《雨夜古宅》", lines.get(0));
        assertTrue(flat.contains("【时间】2026-10-22 19:24 — 21:30（2 小时 06 分）"));
        assertTrue(flat.contains("【KP】阿岚"));
        assertTrue(flat.contains("【玩家】小满、老周"));
        assertTrue(flat.contains("【结局】雨夜脱身"));
        assertTrue(flat.contains("【线索】泥脚印 / 未寄出的信 / 煤油灯下的影子（3/4）"));
        assertTrue(flat.contains("【检定】独立检定 5 次：聆听 成功、侦察 成功、理智 失败"));
        assertTrue(flat.contains("【成长】聆听 50 → 52、侦察 40 → 41"));
        assertTrue(flat.contains("【日志】/trpg log 可查看本局记录（保留 30 天）"));
        for (String line : lines) {
            assertTrue("每行不超过 26 全角字符宽：" + line,
                    displayWidth(line) <= TrpgImageTemplates.LINE_WIDTH * 2);
        }
    }

    @Test
    public void reportWithoutChecksOrClues() {
        TrpgReport empty = TrpgReport.builder().scriptName("雨夜古宅").endingTitle("雨夜脱身")
                .kpName("阿岚").clueTotal(2).retentionDays(7).build();
        List<String> lines = TrpgImageTemplates.report(empty);
        String flat = flat(lines);
        assertTrue(flat.contains("【线索】无（0/2）"));
        assertTrue(flat.contains("【检定】无独立检定"));
        assertTrue(flat.contains("【玩家】无"));
        assertTrue(flat.contains("【成长】本局无涨点"));
        assertTrue(flat.contains("【日志】/trpg log 可查看本局记录（保留 7 天）"));
    }

    @Test
    public void reportOverflowEndsWithPointerLine() {
        List<String> lines = TrpgImageTemplates.report(report(), 6);
        assertEquals(6, lines.size());
        assertEquals(TrpgImageTemplates.OVERFLOW_LINE, lines.get(lines.size() - 1));
        assertFalse(lines.contains("【日志】/trpg log 可查看本局记录（保留 30 天）"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void reportRejectsNull() {
        TrpgImageTemplates.report(null);
    }

    @Test
    public void scriptPageListsScenes() {
        List<String> lines = TrpgImageTemplates.scriptPage(script());
        // 首行标题由渲染器负责，图片正文从 id 行开始。
        assertEquals("id：coc-example　作者：MBB-Trpg", lines.get(0));
        String flat = flat(lines);
        assertTrue(flat.contains("1920 年代 · 调查 · 2～4 人 · 约 60 分钟"));
        assertTrue(flat.contains("场景 2 个　线索 1 个　结局 1 个"));
        assertTrue(flat.contains("第 1 幕：古宅门前"));
        assertTrue(flat.contains("第 2 幕：门厅"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void scriptPageRejectsNull() {
        TrpgImageTemplates.scriptPage(null);
    }
    /** 把所有行拼回去：wrapForImage 只折行不丢字，拼回后可与冻结样例逐字比对。 */
    private static String flat(List<String> lines) {
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            builder.append(line);
        }
        return builder.toString();
    }

    @Test
    public void reportFoldsLongClueLists() {
        List<String> clues = new ArrayList<String>();
        for (int index = 0; index < 12; index++) {
            clues.add("长线索名称" + index);
        }
        TrpgReport report = TrpgReport.builder().scriptName("雨夜古宅").endingTitle("雨夜脱身")
                .kpName("阿岚").clueNames(clues).clueTotal(12).build();
        String flat = flat(TrpgImageTemplates.report(report));
        assertTrue("线索超过 3 行应折叠：" + flat, flat.contains("等 12 条"));
    }

    @Test
    public void reportShowsBothDatesWhenEndingNextDay() {
        long start = 1_700_000_000_000L;
        long end = start + 30L * 60L * 60L * 1000L;
        TrpgReport report = TrpgReport.builder().scriptName("雨夜古宅").endingTitle("雨夜脱身")
                .startedAt(start).endedAt(end).durationMinutes(1800).build();
        String flat = flat(TrpgImageTemplates.report(report));
        assertTrue("跨天时应带结束日期：" + flat,
                flat.contains(TrpgMessageTemplates.date(start) + " ")
                        && flat.contains(TrpgMessageTemplates.date(end)));
    }

    @Test
    public void wrappingCountsHalfWidthCharactersAsHalf() {
        StringBuilder ascii = new StringBuilder();
        for (int index = 0; index < 60; index++) {
            ascii.append('a');
        }
        // 半角每行可放 52 个字符（26 全角 × 2 单位），60 个字符只需 2 行。
        assertEquals(2, TrpgImageTemplates.wrapForImage(ascii.toString(), 26).size());
        StringBuilder fullWidth = new StringBuilder();
        for (int index = 0; index < 60; index++) {
            fullWidth.append('中');
        }
        assertEquals(3, TrpgImageTemplates.wrapForImage(fullWidth.toString(), 26).size());
    }

    @Test
    public void scriptPageLeavesTitleToRenderer() {
        List<String> lines = TrpgImageTemplates.scriptPage(script());
        assertTrue("图片正文不再重复标题行", !lines.get(0).startsWith("剧本 · 《"));
    }

    /** 全角记 2 单位、半角记 1 单位，与图片渲染的折行口径一致。 */
    private static int displayWidth(String line) {
        int width = 0;
        for (int index = 0; index < line.length(); ) {
            int codePoint = line.codePointAt(index);
            width += codePoint < 0x80 ? 1 : 2;
            index += Character.charCount(codePoint);
        }
        return width;
    }
}
