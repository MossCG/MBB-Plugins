package org.moboxlab.mbb.trpg.present;

import org.junit.Test;
import org.moboxlab.mbb.trpg.script.Clue;
import org.moboxlab.mbb.trpg.script.Scene;
import org.moboxlab.mbb.trpg.script.Script;
import org.moboxlab.mbb.trpg.script.ScriptEnding;
import org.moboxlab.mbb.trpg.session.SessionSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** 消息模板逐字对照呈现规范 §4 的冻结样例（J11）。 */
public class TrpgMessageTemplatesTest {

    /** 与冻结样例一致的剧本：标签、人数、时长与简介都按样例给。 */
    private static Script sampleScript() {
        ScriptEnding ending = new ScriptEnding("ending-escape", "雨夜脱身",
                "你们带着那封没寄出的信走进雨里，身后的灯一盏盏灭了。",
                Arrays.asList("clue-print", "clue-letter", "clue-shadow"));
        List<Scene> scenes = Arrays.asList(
                scene("scene-gate", "古宅门前"),
                scene("scene-hall", "门厅"),
                scene("scene-study", "书房"),
                scene("scene-cellar", "地窖"));
        List<Clue> clues = Arrays.asList(
                new Clue("clue-print", "泥脚印", "地上有一串湿脚印。", "脚印通向书房。"),
                new Clue("clue-letter", "未寄出的信", "书桌上压着一封信。", "信是宅主写给女儿的。"),
                new Clue("clue-shadow", "煤油灯下的影子", "墙上多了一道影子。", "影子不是任何人的。"),
                new Clue("clue-altar", "石制祭坛", "地窖中央有石台。", "石台上刻满名字。"));
        return new Script(1, "coc-example", "雨夜古宅", "MBB-Trpg",
                "老友的一封来信，把你们引到城郊那座没人敢住的宅子。",
                "d100", 2, 4, 60, Arrays.asList("1920 年代", "调查"),
                Collections.<String>emptyList(), scenes, clues,
                Collections.<org.moboxlab.mbb.trpg.script.Npc>emptyList(),
                Collections.<org.moboxlab.mbb.trpg.script.ScriptCheck>emptyList(),
                Collections.<org.moboxlab.mbb.trpg.script.ScriptTrigger>emptyList(),
                Collections.singletonList(ending));
    }

    private static Scene scene(String id, String title) {
        return new Scene(id, title, title + "概览", title + "的叙述。", "",
                Collections.<String>emptyList(), Collections.<String>emptyList(),
                Collections.<String>emptyList());
    }

    private static SessionSnapshot snapshot(String sceneTitle, int sceneIndex) {
        return SessionSnapshot.builder()
                .groupId(1001L).sessionId(7L).scriptId("coc-example").ruleSetId("d100")
                .sceneIndex(sceneIndex).sceneTitle(sceneTitle)
                .build();
    }

    /** 结束时间固定成 2026-10-22 21:30（本地时区，与模板格式化同源），配合样例的「2 小时 06 分」。 */
    private static long endedAt() {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(2026, Calendar.OCTOBER, 22, 21, 30, 0);
        return calendar.getTimeInMillis();
    }

    private static TrpgReport sampleReport() {
        return TrpgReport.builder()
                .scriptName("雨夜古宅")
                .endingTitle("雨夜脱身")
                .endingNarration("你们带着那封没寄出的信走进雨里，身后的灯一盏盏灭了。")
                .startedAt(endedAt() - 126L * 60L * 1000L)
                .endedAt(endedAt())
                .durationMinutes(126)
                .kpName("阿岚")
                .playerNames(Arrays.asList("小满", "老周"))
                .clueNames(Arrays.asList("泥脚印", "未寄出的信", "煤油灯下的影子"))
                .clueTotal(4)
                .checkTotal(5)
                .checkSuccess(3)
                .checkResults(Arrays.asList("聆听 成功", "侦察 成功", "理智 失败"))
                .growthLines(Arrays.asList("聆听 50 → 52", "侦察 40 → 41"))
                .retentionDays(30)
                .build();
    }

    @Test
    public void recruitMatchesFrozenSample() {
        String message = TrpgMessageTemplates.recruit(sampleScript(), "coc-example", "阿岚", null);
        String expected = "【开团】《雨夜古宅》开始招募\n"
                + "KP：阿岚　剧本：内置 coc-example\n"
                + "1920 年代 · 调查 · 2～4 人 · 约 60 分钟\n"
                + "老友的一封来信，把你们引到城郊那座没人敢住的宅子。\n"
                + "\n"
                + "发送 /trpg join 加入；发送 /trpg status 查看局面。";
        assertEquals(expected, message);
    }

    @Test
    public void scriptMetaJoinsTagsPlayersAndDuration() {
        assertEquals("1920 年代 · 调查 · 2～4 人 · 约 60 分钟",
                TrpgMessageTemplates.scriptMeta(sampleScript()));
        assertEquals("", TrpgMessageTemplates.scriptMeta(null));
    }

    @Test
    public void checkMatchesFrozenSample() {
        TrpgCheckView view = TrpgCheckView.skill("小满", "聆听", 34, 50,
                TrpgCheckView.DEGREE_REGULAR, "奖励骰 1：十位取 2、7 中较小值。");
        String expected = "【检定 · 聆听】小满\n"
                + "1D100 = 34 / 50 → 常规成功\n"
                + "奖励骰 1：十位取 2、7 中较小值。";
        assertEquals(expected, TrpgMessageTemplates.check(view));
    }

    @Test
    public void criticalCheckWritesTwoDigitRoll() {
        TrpgCheckView view = TrpgCheckView.skill("小满", "聆听", 1, 50,
                TrpgCheckView.DEGREE_CRITICAL, "");
        assertEquals("【检定 · 聆听】小满\n1D100 = 1 / 50 → 大成功（01）",
                TrpgMessageTemplates.check(view));
    }

    @Test
    public void sanTemplateShowsLoss() {
        TrpgCheckView view = TrpgCheckView.san("小满", 78, 60, TrpgCheckView.DEGREE_FAILURE, 12, 7);
        String expected = "【理智 · 小满】\n"
                + "1D100 = 78 / 60 → 失败\n"
                + "SAN 12 → 7（损失 5）";
        assertEquals(expected, TrpgMessageTemplates.san(view));
    }

    @Test
    public void aiNarrationNormalMatchesFrozenSample() {
        String message = TrpgMessageTemplates.aiNarration("古宅门前",
                "雨点砸在门环上。老管家周福把门开了一条缝，灯光只够照出他的半张脸，他没有让你们进去的意思。",
                Arrays.asList("询问周福来意", "观察门廊的地面", "等待雨停"), "阿岚", false);
        String expected = "【AI 叙述】古宅门前\n"
                + "雨点砸在门环上。老管家周福把门开了一条缝，灯光只够照出他的半张脸，他没有让你们进去的意思。\n"
                + "\n"
                + "建议：询问周福来意　观察门廊的地面　等待雨停\n"
                + "\n"
                + "—— 主持人 · 阿岚（AI 叙述）";
        assertEquals(expected, message);
    }

    @Test
    public void aiNarrationDegradedMatchesFrozenSample() {
        String message = TrpgMessageTemplates.aiNarration("古宅门前",
                "房门在你们敲到第三下时才开。周福站在门内，没有让开。",
                null, "阿岚", true);
        String expected = "【AI 叙述】古宅门前\n"
                + "房门在你们敲到第三下时才开。周福站在门内，没有让开。"
                + "—— 主持人 · 阿岚（AI 暂不可用，本条为剧本原文）";
        assertEquals(expected, message);
    }

    @Test
    public void endedMatchesFrozenSample() {
        String message = TrpgMessageTemplates.ended(sampleReport(), true);
        String expected = "【结束】《雨夜古宅》· 结局「雨夜脱身」\n"
                + "你们带着那封没寄出的信走进雨里，身后的灯一盏盏灭了。\n"
                + "\n"
                + "时间 2 小时 06 分 · 线索 3/4 · 检定 5 次（成功 3）· 成长 2 人次\n"
                + "战报图见上一条；文字明细发送 /trpg log 查看（保留 30 天）。";
        assertEquals(expected, message);
    }

    @Test
    public void endedWithoutImageOmitsImageLine() {
        String message = TrpgMessageTemplates.ended(sampleReport(), false);
        assertTrue(message.endsWith("文字明细发送 /trpg log 查看（保留 30 天）。"));
    }

    @Test
    public void reportTextMatchesFrozenSample() {
        String message = TrpgMessageTemplates.reportText(sampleReport());
        String expected = "【战报】《雨夜古宅》· 结局「雨夜脱身」\n"
                + "结束时间：2026-10-22 21:30　时长：2 小时 06 分\n"
                + "KP：阿岚　玩家：小满、老周\n"
                + "线索：泥脚印、未寄出的信、煤油灯下的影子（3/4）\n"
                + "检定：聆听 成功、侦察 成功、理智 失败（共 5 次）\n"
                + "成长：聆听 50 → 52、侦察 40 → 41\n"
                + "日志：/trpg log 可查看本局记录（保留 30 天）";
        assertEquals(expected, message);
    }

    @Test
    public void reportTextWithoutChecksOrClues() {
        TrpgReport report = TrpgReport.builder().scriptName("雨夜古宅").endingTitle("雨夜脱身")
                .clueTotal(4).retentionDays(30).build();
        String message = TrpgMessageTemplates.reportText(report);
        assertTrue(message.contains("线索：无（0/4）"));
        assertTrue(message.contains("检定：无独立检定"));
        assertTrue(message.contains("成长：本局无涨点"));
    }

    @Test
    public void recoveredMatchesFrozenSample() {
        String message = TrpgMessageTemplates.recovered(sampleScript(), snapshot("书房", 1), 2);
        String expected = "【恢复】《雨夜古宅》已恢复\n"
                + "当前场景：书房（第 2/4 幕）　已恢复 2 名玩家。\n"
                + "本局在上次重启前正在进行，KP 可发送 /trpg resume 继续。\n"
                + "\n"
                + "/trpg status 可以随时查看局面。";
        assertEquals(expected, message);
    }

    @Test
    public void recoveredWithoutScriptFallsBackToUnknownName() {
        String message = TrpgMessageTemplates.recovered(null, snapshot("", 0), 0);
        assertTrue(message.startsWith("【恢复】《未知剧本》已恢复\n"));
        assertTrue(message.contains("已恢复 0 名玩家。"));
    }

    @Test
    public void growthLinesAndEmptyGrowth() {
        assertEquals("【成长】\n聆听 50 → 52", TrpgMessageTemplates.growth(
                Collections.singletonList("聆听 50 → 52")));
        assertEquals("【成长】\n本局没有技能涨点。",
                TrpgMessageTemplates.growth(new ArrayList<String>()));
    }

    @Test
    public void noticeAndErrorTemplates() {
        assertEquals("【提示】\n请稍后再试。", TrpgMessageTemplates.notice("请稍后再试。"));
        assertEquals("【错误】\n本群已有进行中的局。\n发送 /trpg status 查看当前局面。",
                TrpgMessageTemplates.error("本群已有进行中的局。", "发送 /trpg status 查看当前局面。"));
    }

    @Test
    public void dateTimeFormatsInLocalZone() {
        assertEquals("2026-10-22 21:30", TrpgMessageTemplates.dateTime(endedAt()));
        assertEquals("2026-10-22", TrpgMessageTemplates.date(endedAt()));
        assertEquals("未知", TrpgMessageTemplates.dateTime(0L));
        assertEquals("未知", TrpgMessageTemplates.date(-1L));
    }

    @Test
    public void truncatesTemplatesToTheirBodyLimits() {
        StringBuilder longText = new StringBuilder();
        for (int index = 0; index < 300; index++) {
            longText.append("很长的正文");
        }
        assertBodyWithin(TrpgMessageTemplates.scene("书房", longText.toString()), 400);
        assertBodyWithin(TrpgMessageTemplates.sceneFallback("书房", longText.toString()), 400);
        assertBodyWithin(TrpgMessageTemplates.notice(longText.toString()), 120);
        assertBodyWithin(TrpgMessageTemplates.error(longText.toString(), longText.toString()), 120);
        assertBodyWithin(TrpgMessageTemplates.clue(new Clue("clue-print", "泥脚印",
                longText.toString(), "全文")), 200);
        assertBodyWithin(TrpgMessageTemplates.adjust("小满", "HP", longText.toString(),
                longText.toString()), 160);
        assertBodyWithin(TrpgMessageTemplates.trigger("书房", longText.toString()), 200);
        assertBodyWithin(TrpgMessageTemplates.growth(Collections.singletonList(
                longText.toString())), 300);
        assertBodyWithin(TrpgMessageTemplates.recovered(sampleScript(), snapshot("书房", 1), 2), 200);
    }

    @Test
    public void endedOmitsSuccessCountWhenUnknown() {
        TrpgReport report = TrpgReport.builder().scriptName("雨夜古宅").endingTitle("雨夜脱身")
                .durationMinutes(10).checkTotal(3).build();
        String message = TrpgMessageTemplates.ended(report, false);
        assertTrue(message.contains("检定 3 次"));
        assertTrue("成功数未知时不应写括号：" + message, !message.contains("（成功"));
    }

    @Test
    public void endedPrintsKnownSuccessCount() {
        TrpgReport report = TrpgReport.builder().scriptName("雨夜古宅").endingTitle("雨夜脱身")
                .durationMinutes(10).checkTotal(3).checkSuccess(2)
                .growthLines(Collections.singletonList("聆听 50 → 52")).build();
        String message = TrpgMessageTemplates.ended(report, false);
        assertTrue(message.contains("检定 3 次（成功 2）· 成长 1 人次"));
    }

    private static void assertBodyWithin(String message, int limit) {
        int newline = message.indexOf('\n');
        String body = newline < 0 ? "" : message.substring(newline + 1);
        assertTrue("正文应截到 " + limit + " 字，实际 " + body.length() + "：" + body,
                new TrpgMessageFormatter().count(body) <= limit);
    }
}
