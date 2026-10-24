package org.moboxlab.mbb.trpg.present;

import org.moboxlab.mbb.trpg.script.Scene;
import org.moboxlab.mbb.trpg.script.Script;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 图片行组装（纯函数）：情景卡、战报、脚本打印页。
 *
 * <p>主程序只提供 {@code ImageUtil.renderText(title, lines)}，插件不自绘；
 * 版式靠行约定表达，每行不超过 26 个全角字符，超出由 {@link #wrapForImage} 折行。</p>
 */
public final class TrpgImageTemplates {

    /** 单行全角字符上限（760px 下按主程序字号估算）。 */
    public static final int LINE_WIDTH = 26;
    /** 情景卡行数上限。 */
    public static final int SCENE_MAX_LINES = 14;
    /** 情景卡叙述最多几行。 */
    public static final int SCENE_NARRATION_LINES = 6;
    /** 名单折叠阈值：超过 3 行改「等 N 人」。 */
    public static final int NAME_FOLD_LINES = 3;
    /** 战报超限时的收尾行。 */
    public static final String OVERFLOW_LINE = "其余见 /trpg log";

    private TrpgImageTemplates() {
    }

    /** 按 26 全角字符折行；不切断代理对。 */
    public static List<String> wrapForImage(String text) {
        return wrapForImage(text, LINE_WIDTH);
    }

    /**
     * 按给定宽度折行，保留原有换行。
     *
     * <p>宽度按「全角 1 行、半角 0.5 行」折算：ASCII 记 1 个单位，其余字符记 2 个单位，
     * 一行的预算为 {@code width * 2} 个单位，避免中英混排时行被撑爆。</p>
     */
    public static List<String> wrapForImage(String text, int width) {
        List<String> lines = new ArrayList<String>();
        if (text == null || text.isEmpty()) {
            return Collections.unmodifiableList(lines);
        }
        int budget = Math.max(2, width * 2);
        for (String raw : text.split("\n", -1)) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            int offset = 0;
            while (offset < line.length()) {
                int used = 0;
                int end = offset;
                while (end < line.length()) {
                    int codePoint = line.codePointAt(end);
                    int units = codePoint < 128 ? 1 : 2;
                    if (used + units > budget) {
                        break;
                    }
                    used += units;
                    end += Character.charCount(codePoint);
                }
                if (end == offset) {
                    // 单字符就超预算（宽度设置过小时）也必须前进，避免死循环。
                    end = offset + Character.charCount(line.codePointAt(offset));
                }
                lines.add(line.substring(offset, end));
                offset = end;
            }
        }
        return Collections.unmodifiableList(lines);
    }

    /** 情景卡（≤14 行）。 */
    public static List<String> scene(TrpgSceneCard card) {
        if (card == null) {
            throw new IllegalArgumentException("情景卡数据不能为空");
        }
        List<String> lines = new ArrayList<String>();
        lines.add("【场景】" + card.sceneTitle() + " · 第 " + card.sceneNo() + '/'
                + card.sceneTotal() + " 幕");
        if (!card.presentNames().isEmpty()) {
            lines.add("【在场】" + join(card.presentNames(), "、"));
        }
        if (!card.clueNames().isEmpty()) {
            lines.add("【线索】" + join(card.clueNames(), "、"));
        }
        List<String> narration = wrapForImage(card.narration());
        int narrationLimit = Math.min(SCENE_NARRATION_LINES, narration.size());
        for (int index = 0; index < narrationLimit; index++) {
            lines.add(narration.get(index));
        }
        if (!card.nextSteps().isEmpty()) {
            lines.add("【下一步】" + join(card.nextSteps(), "；"));
        }
        List<String> wrapped = wrapAll(lines);
        if (wrapped.size() > SCENE_MAX_LINES) {
            return Collections.unmodifiableList(new ArrayList<String>(
                    wrapped.subList(0, SCENE_MAX_LINES)));
        }
        return wrapped;
    }

    /** 战报（≤maxLines 行，默认配置 22）。 */
    public static List<String> report(TrpgReport report) {
        return report(report, TrpgPresentConfig.DEFAULT_IMAGE_MAX_LINES);
    }

    /** 战报（≤maxLines 行）。 */
    public static List<String> report(TrpgReport report, int maxLines) {
        if (report == null) {
            throw new IllegalArgumentException("战报数据不能为空");
        }
        List<String> sections = new ArrayList<String>();
        sections.add("【局】《" + report.scriptName() + "》");
        sections.add("【时间】" + timeRange(report));
        sections.add("【KP】" + report.kpName());
        sections.add("【玩家】" + foldNames(report.playerNames(), "人"));
        sections.add("【结局】" + report.endingTitle());
        sections.add("【线索】" + clueLine(report));
        sections.add("【检定】" + checkLine(report));
        sections.add("【成长】" + (report.growthLines().isEmpty()
                ? "本局无涨点" : join(report.growthLines(), "、")));
        sections.add("【日志】/trpg log 可查看本局记录（保留 " + report.retentionDays() + " 天）");
        List<String> wrapped = wrapAll(sections);
        int limit = Math.max(4, maxLines);
        if (wrapped.size() <= limit) {
            return wrapped;
        }
        List<String> trimmed = new ArrayList<String>(wrapped.subList(0, limit - 1));
        trimmed.add(OVERFLOW_LINE);
        return Collections.unmodifiableList(trimmed);
    }

    /** 脚本打印页（私聊长输出）。 */
    public static List<String> scriptPage(Script script) {
        if (script == null) {
            throw new IllegalArgumentException("剧本不能为空");
        }
        // 首行标题由渲染器负责（与图片标题同名），这里不再重复。
        List<String> lines = new ArrayList<String>();
        lines.add("id：" + script.id() + "　作者："
                + (script.author().isEmpty() ? "未知" : script.author()));
        lines.add(TrpgMessageTemplates.scriptMeta(script));
        lines.add("场景 " + script.scenes().size() + " 个　线索 " + script.clues().size()
                + " 个　结局 " + script.endings().size() + " 个");
        int index = 1;
        for (Scene scene : script.scenes()) {
            lines.add("第 " + index + " 幕：" + scene.title());
            index++;
        }
        return wrapAll(lines);
    }

    private static String clueLine(TrpgReport report) {
        if (report.clueNames().isEmpty()) {
            return "无（0/" + report.clueTotal() + "）";
        }
        return foldNames(report.clueNames(), " / ", "条") + "（" + report.clueNames().size() + '/'
                + report.clueTotal() + "）";
    }

    private static String checkLine(TrpgReport report) {
        if (report.checkTotal() <= 0) {
            return "无独立检定";
        }
        if (report.checkResults().isEmpty()) {
            return "独立检定 " + report.checkTotal() + " 次";
        }
        return "独立检定 " + report.checkTotal() + " 次：" + join(report.checkResults(), "、");
    }

    /** 时间栏：同一天只写一次日期，跨天时两端都带日期。 */
    private static String timeRange(TrpgReport report) {
        String startDate = TrpgMessageTemplates.date(report.startedAt());
        String endDate = TrpgMessageTemplates.date(report.endedAt());
        String start = startDate + ' ' + clock(report.startedAt());
        String end = endDate.equals(startDate) ? clock(report.endedAt())
                : endDate + ' ' + clock(report.endedAt());
        return start + " — " + end + "（" + report.durationText() + "）";
    }

    private static String clock(long millis) {
        String dateTime = TrpgMessageTemplates.dateTime(millis);
        return dateTime.length() >= 16 ? dateTime.substring(11) : dateTime;
    }

    /** 名单超过 3 行时折成「前两人 等 N 人」。 */
    private static String foldNames(List<String> names, String unit) {
        return foldNames(names, "、", unit);
    }

    /** 名单按指定分隔符拼；超过 3 行时折成「前两项 等 N 单位」。 */
    private static String foldNames(List<String> names, String separator, String unit) {
        if (names.isEmpty()) {
            return "无";
        }
        String joined = join(names, separator);
        if (wrapForImage(joined).size() <= NAME_FOLD_LINES) {
            return joined;
        }
        int shown = Math.min(2, names.size());
        return join(names.subList(0, shown), separator) + " 等 " + names.size() + " " + unit;
    }

    private static List<String> wrapAll(List<String> lines) {
        List<String> wrapped = new ArrayList<String>();
        for (String line : lines) {
            if (line == null || line.isEmpty()) {
                continue;
            }
            wrapped.addAll(wrapForImage(line));
        }
        return Collections.unmodifiableList(wrapped);
    }

    private static String join(List<String> values, String separator) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null || value.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(separator);
            }
            builder.append(value);
        }
        return builder.toString();
    }
}