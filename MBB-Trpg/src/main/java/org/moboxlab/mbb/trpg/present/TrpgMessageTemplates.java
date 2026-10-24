package org.moboxlab.mbb.trpg.present;

import org.moboxlab.mbb.trpg.script.Clue;
import org.moboxlab.mbb.trpg.script.Script;
import org.moboxlab.mbb.trpg.script.ScriptGenerationOutcome;
import org.moboxlab.mbb.trpg.script.ScriptImportMode;
import org.moboxlab.mbb.trpg.script.ScriptImportResult;
import org.moboxlab.mbb.trpg.script.ScriptInfo;
import org.moboxlab.mbb.trpg.script.ScriptIssue;
import org.moboxlab.mbb.trpg.script.ScriptValidationResult;
import org.moboxlab.mbb.trpg.session.SessionSnapshot;
import org.moboxlab.mbb.trpg.session.SessionState;
import org.moboxlab.mbb.trpg.store.CardData;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 消息模板（纯函数）：输入值对象，输出未拆分的单个字符串。
 *
 * <p>模板只决定「说什么」，「怎么截断、要不要拆条」由 {@link TrpgMessageFormatter} 与
 * {@link TrpgMessageSplitter} 决定。正文硬上限按呈现规范 §3 的表格逐条给出。</p>
 */
public final class TrpgMessageTemplates {

    /** 分数与人数区间的连接符。 */
    private static final String SEPARATOR = " · ";
    private static final String ALIGN = "　";
    private static final TrpgMessageFormatter FORMATTER = new TrpgMessageFormatter();
    /** 时间显示格式（本地时区）。 */
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm";
    private static final String DATE_PATTERN = "yyyy-MM-dd";
    /*
     * 模板级正文上限，逐条对应呈现规范 §3 的表格（不含标题行）。
     * 发送前的拆分/折叠再按 config.groupMaxChars() / privateMaxChars() 处理。
     */
    private static final int LIMIT_RECRUIT = 300;
    private static final int LIMIT_JOINED = 120;
    private static final int LIMIT_LEFT = 120;
    private static final int LIMIT_INVITED = 120;
    private static final int LIMIT_TAKEN_OVER = 120;
    private static final int LIMIT_HOST = 100;
    private static final int LIMIT_BEGIN = 300;
    private static final int LIMIT_SCENE = 400;
    private static final int LIMIT_CLUE = 200;
    private static final int LIMIT_CHECK = 200;
    private static final int LIMIT_OPPOSED = 220;
    private static final int LIMIT_SAN = 200;
    private static final int LIMIT_GROWTH = 300;
    private static final int LIMIT_ADJUST = 160;
    private static final int LIMIT_SCRIPT_CHANGED = 200;
    private static final int LIMIT_TRIGGER = 200;
    private static final int LIMIT_AI_NARRATION = 300;
    private static final int LIMIT_RECOVERED = 200;
    private static final int LIMIT_NOTICE = 120;
    private static final int LIMIT_ERROR = 120;
    private static final int LIMIT_END_CONFIRM = 160;
    private static final int LIMIT_ENDED = 1200;
    private static final int LIMIT_REPORT_TEXT = 600;
    private static final int LIMIT_CARD = 600;
    private static final int LIMIT_KP_HINT = 900;
    private static final int LIMIT_SCRIPT_LIBRARY = 500;
    private static final int LIMIT_SCRIPT_IMPORT_GUIDE = 600;
    private static final int LIMIT_SCRIPT_IMPORT_ISSUE = 260;
    private static final int LIMIT_SCRIPT_GEN = 600;
    private static final int LIMIT_STATUS = 400;
    private static final int LIMIT_LOG = 900;
    private static final int LIMIT_HELP = 900;

    private TrpgMessageTemplates() {
    }

    /** 招募公告。 */
    public static String recruit(Script script, String scriptId, String kpName, String metaLine) {
        StringBuilder body = new StringBuilder();
        body.append(TrpgTitles.of(TrpgTitles.RECRUIT)).append("《")
                .append(scriptName(script)).append("》开始招募\n");
        body.append("KP：").append(kpName).append(ALIGN).append("剧本：内置 ").append(scriptId);
        String meta = metaLine == null || metaLine.trim().isEmpty()
                ? scriptMeta(script) : metaLine.trim();
        if (!meta.isEmpty()) {
            body.append('\n').append(meta);
        }
        String description = FORMATTER.clean(script == null ? "" : script.description());
        if (!description.isEmpty()) {
            body.append('\n').append(description);
        }
        body.append("\n\n发送 /trpg join 加入；发送 /trpg status 查看局面。");
        return limit(body.toString(), LIMIT_RECRUIT);
    }

    /** 剧本元信息行：标签 · 人数 · 时长。 */
    public static String scriptMeta(Script script) {
        if (script == null) {
            return "";
        }
        List<String> parts = new ArrayList<String>();
        for (String tag : script.tags()) {
            String value = tag == null ? "" : tag.trim();
            if (!value.isEmpty()) {
                parts.add(value);
            }
        }
        parts.add(script.minPlayers() + "～" + script.maxPlayers() + " 人");
        if (script.estimatedMinutes() > 0) {
            parts.add("约 " + script.estimatedMinutes() + " 分钟");
        }
        return join(parts, SEPARATOR);
    }

    /** 加入。 */
    public static String joined(String name, int current, int maxPlayers, String occupation) {
        StringBuilder body = new StringBuilder();
        body.append(TrpgTitles.of(TrpgTitles.JOIN, name)).append('\n');
        body.append(name).append(" 加入了本局，当前 ").append(current).append('/')
                .append(maxPlayers).append(" 人。");
        if (occupation != null && !occupation.trim().isEmpty()) {
            body.append("职业：").append(occupation.trim()).append('。');
        }
        return limit(body.toString(), LIMIT_JOINED);
    }

    /** 离开。 */
    public static String left(String name, int current, int maxPlayers, boolean cardKept) {
        StringBuilder body = new StringBuilder();
        body.append(TrpgTitles.of(TrpgTitles.LEAVE, name)).append('\n');
        body.append(name).append(" 离开了本局，当前 ").append(current).append('/')
                .append(maxPlayers).append(" 人。");
        if (cardKept) {
            body.append("角色卡已保留，招募期内可再次加入。");
        }
        return limit(body.toString(), LIMIT_LEFT);
    }

    /** 邀请（KP 拉人）。 */
    public static String invited(String name, int current, int maxPlayers) {
        return limit(TrpgTitles.of(TrpgTitles.INVITE, name) + "\n" + name + " 被邀请加入本局，当前 "
                + current + "/" + maxPlayers + " 人。", LIMIT_INVITED);
    }

    /** KP 接管。 */
    public static String takenOver(String oldKpName, String newKpName) {
        return limit(TrpgTitles.of(TrpgTitles.TAKE, newKpName) + "\nKP 已由 " + oldKpName
                + " 移交给 " + newKpName + "。", LIMIT_TAKEN_OVER);
    }

    /** 主持方式提示：正文由 AI 契约桩给出，v1 恒为关闭口径（§9.10）。 */
    public static String aiHost(String detail) {
        String text = detail == null ? "" : detail.trim();
        return limit(TrpgTitles.of(TrpgTitles.HOST) + "\n"
                + (text.isEmpty() ? "AI 主持保持关闭。" : text + "。"), LIMIT_HOST);
    }

    /** 开局公告。 */
    public static String begin(Script script, String kpName, List<String> playerNames,
                               String firstSceneTitle) {
        StringBuilder body = new StringBuilder();
        body.append(TrpgTitles.of(TrpgTitles.BEGIN)).append("《").append(scriptName(script))
                .append("》\n");
        body.append("KP：").append(kpName).append(ALIGN).append("玩家：")
                .append(join(playerNames, "、"));
        if (firstSceneTitle != null && !firstSceneTitle.trim().isEmpty()) {
            body.append('\n').append("第一幕：").append(firstSceneTitle.trim());
        }
        return limit(body.toString(), LIMIT_BEGIN);
    }

    /** 场景叙述（纯文本，图片降级时也用这条）。 */
    public static String scene(String sceneTitle, String narration) {
        String text = narration == null ? "" : narration;
        return limit(TrpgTitles.of(TrpgTitles.SCENE, sceneTitle) + "\n" + text, LIMIT_SCENE);
    }

    /** 情景卡降级正文：场景叙述 + 下一步提示。 */
    public static String sceneFallback(String sceneTitle, String narration) {
        return limit(scene(sceneTitle, narration) + "\n\n发送 /trpg status 查看局面。", LIMIT_SCENE);
    }

    /** 线索公开钩子（全文只私聊 KP）。 */
    public static String clue(Clue clue) {
        String name = clue == null ? "未知线索" : clue.name();
        String locked = clue == null ? "" : clue.lockedSummary();
        StringBuilder body = new StringBuilder();
        body.append(TrpgTitles.of(TrpgTitles.CLUE, name)).append('\n');
        if (!locked.trim().isEmpty()) {
            body.append(locked).append('\n');
        }
        body.append("线索全文已私聊 KP。");
        return limit(body.toString(), LIMIT_CLUE);
    }

    /** 技能检定。 */
    public static String check(TrpgCheckView view) {
        StringBuilder body = new StringBuilder();
        body.append(TrpgTitles.of(TrpgTitles.CHECK, view.skillLabel())).append(view.rollerName())
                .append('\n');
        body.append("1D100 = ").append(view.rolled()).append(" / ").append(view.target())
                .append(' ').append(degreeClause(view));
        String note = view.note().trim();
        if (!note.isEmpty()) {
            body.append('\n').append(note);
        }
        return limit(body.toString(), LIMIT_CHECK);
    }

    /** 理智检定。 */
    public static String san(TrpgCheckView view) {
        return san(view, false, false, 0L);
    }

    /**
     * 理智检定，附带疯狂结局。
     *
     * @param permanent    是否理智归零、永久疯狂并退场
     * @param maddened     是否临时性疯狂
     * @param madnessUntil 临时性疯狂的结束时间（毫秒）
     */
    public static String san(TrpgCheckView view, boolean permanent, boolean maddened,
                             long madnessUntil) {
        StringBuilder body = new StringBuilder();
        body.append(TrpgTitles.of(TrpgTitles.SAN, view.rollerName())).append('\n');
        body.append("1D100 = ").append(view.rolled()).append(" / ").append(view.target())
                .append(' ').append(degreeClause(view)).append('\n');
        body.append("SAN ").append(view.sanBefore()).append(" → ").append(view.sanAfter())
                .append("（损失 ").append(view.sanLoss()).append("）");
        if (permanent) {
            body.append("\n理智归零，该角色永久疯狂并退场。");
        } else if (maddened) {
            body.append("\n临时性疯狂，症状持续到 ").append(dateTime(madnessUntil)).append('。');
        }
        return limit(body.toString(), LIMIT_SAN);
    }

    /** 对抗检定。 */
    public static String opposed(TrpgCheckView left, TrpgCheckView right, boolean leftWin,
                                 String tieBreakLabel) {
        StringBuilder body = new StringBuilder();
        body.append(TrpgTitles.of(TrpgTitles.OPPOSED, left.skillLabel())).append('\n');
        body.append(left.rollerName()).append("：1D100 = ").append(left.rolled()).append(" / ")
                .append(left.target()).append(' ').append(degreeClause(left)).append('\n');
        body.append(right.rollerName()).append("：1D100 = ").append(right.rolled()).append(" / ")
                .append(right.target()).append(' ').append(degreeClause(right)).append('\n');
        if (tieBreakLabel == null || tieBreakLabel.trim().isEmpty()) {
            body.append(leftWin ? left.rollerName() : right.rollerName()).append(" 胜。");
        } else {
            body.append(leftWin ? left.rollerName() : right.rollerName()).append(" 胜（")
                    .append(tieBreakLabel.trim()).append("）。");
        }
        return limit(body.toString(), LIMIT_OPPOSED);
    }

    /** 成长结算明细。 */
    public static String growth(List<String> lines) {
        StringBuilder body = new StringBuilder();
        body.append(TrpgTitles.of(TrpgTitles.GROWTH));
        if (lines == null || lines.isEmpty()) {
            body.append("\n本局没有技能涨点。");
        } else {
            for (String line : lines) {
                body.append('\n').append(line);
            }
        }
        return limit(body.toString(), LIMIT_GROWTH);
    }

    /** 数值调整。 */
    public static String adjust(String name, String item, String from, String to) {
        return limit(TrpgTitles.of(TrpgTitles.ADJUST, name) + "\n" + item + "：" + from + " → " + to + "。",
                LIMIT_ADJUST);
    }

    /**
     * KP 代管某一成员的行动。
     *
     * <p>词表里的【接管】冻结为 KP 交接（原 KP → 新 KP），代管玩家回合并入【提示】，
     * 避免同一个标题承载两种语义。</p>
     */
    public static String takeoverAction(String name) {
        return limit(TrpgTitles.of(TrpgTitles.NOTICE) + "\n已记录代管：" + name
                + "。KP 会代该成员掷骰。", LIMIT_NOTICE);
    }

    /** 跳过回执：只记日志，不改角色卡。 */
    public static String skipped(String name, String reason) {
        StringBuilder body = new StringBuilder();
        body.append(TrpgTitles.of(TrpgTitles.NOTICE)).append("\n已记录跳过：").append(name);
        String trimmed = reason == null ? "" : reason.trim();
        if (!trimmed.isEmpty()) {
            body.append("。原因：").append(trimmed);
        } else {
            body.append('。');
        }
        return limit(body.toString(), LIMIT_NOTICE);
    }

    /** 对抗无胜者（双方都失败或都大失败）。 */
    public static String opposedDraw(TrpgCheckView left, TrpgCheckView right, String reason) {
        StringBuilder body = new StringBuilder();
        body.append(TrpgTitles.of(TrpgTitles.OPPOSED, left.skillLabel())).append('\n');
        body.append(left.rollerName()).append("：1D100 = ").append(left.rolled()).append(" / ")
                .append(left.target()).append(' ').append(degreeClause(left)).append('\n');
        body.append(right.rollerName()).append("：1D100 = ").append(right.rolled()).append(" / ")
                .append(right.target()).append(' ').append(degreeClause(right)).append('\n');
        String trimmed = reason == null ? "" : reason.trim();
        body.append(trimmed.isEmpty() ? "双方均未成功，无胜者。" : "双方均未成功，无胜者（" + trimmed + "）。");
        return limit(body.toString(), LIMIT_OPPOSED);
    }

    /** 本局剧本已切换（仅招募期）。 */
    public static String scriptChanged(Script script) {
        String name = script == null ? "" : script.name();
        return limit(TrpgTitles.of(TrpgTitles.SCRIPT) + "\n本局剧本已切换为《" + name + "》。",
                LIMIT_SCRIPT_CHANGED);
    }

    /** KP 触发提示（只私聊 KP）。 */
    public static String trigger(String sceneTitle, String hint) {
        return limit(TrpgTitles.of(TrpgTitles.TRIGGER, sceneTitle) + "\n"
                + (hint == null ? "" : hint), LIMIT_TRIGGER);
    }

    /** AI 叙述（含建议动作与落款）。 */
    public static String aiNarration(String sceneTitle, String text, List<String> suggestions,
                                     String kpName, boolean degraded) {
        String body = FORMATTER.truncate(FORMATTER.clean(text), LIMIT_AI_NARRATION);
        StringBuilder message = new StringBuilder();
        // 冻结样例（规范 §4.3）：标题行是「【AI 叙述】」直接接场景名，不加分隔符。
        message.append(TrpgTitles.of(TrpgTitles.AI_NARRATION));
        if (sceneTitle != null && !sceneTitle.trim().isEmpty()) {
            message.append(sceneTitle.trim());
        }
        message.append('\n').append(body);
        if (suggestions != null && !suggestions.isEmpty()) {
            message.append("\n\n建议：").append(join(suggestions, ALIGN));
        }
        if (degraded) {
            // 降级款与正文接在同一行，对应呈现规范 §4.3 的冻结样例。
            message.append("—— 主持人 · ").append(kpName).append("（AI 暂不可用，本条为剧本原文）");
        } else {
            message.append("\n\n—— 主持人 · ").append(kpName).append("（AI 叙述）");
        }
        return message.toString();
    }

    /** AI 主持引擎输出（已含建议行）：标题加正文，正文已由解析器清洗与截断，落款注明 AI 参与。 */
    public static String aiNarrated(String narration, String kpName) {
        String name = kpName == null || kpName.trim().isEmpty() ? "KP" : kpName.trim();
        return TrpgTitles.of(TrpgTitles.AI_NARRATION) + "\n"
                + FORMATTER.truncate(FORMATTER.clean(narration), LIMIT_AI_NARRATION * 2)
                + "\n\n—— 主持人 · " + name + "（AI 叙述）";
    }

    /** AI 不可用时的降级提示：不发叙述，只说明原因（原因统一翻成中文标签）。 */
    public static String aiDegraded(String reason) {
        String text = degradeLabel(reason);
        StringBuilder body = new StringBuilder();
        // 冻结子标题表不允许「降级」这类新词，提示放在正文里。
        body.append(TrpgTitles.of(TrpgTitles.AI_NARRATION))
                .append("\nAI 主持暂时不可用");
        if (!text.isEmpty()) {
            body.append("（").append(text).append("）");
        }
        body.append("，本回合由 KP 继续。");
        return limit(body.toString(), LIMIT_AI_NARRATION);
    }

    /** AI 连续失败后挂起提示（§9.6）：每个挂起周期只发一次。 */
    public static String aiSuspended() {
        return limit(TrpgTitles.of(TrpgTitles.AI_NARRATION)
                + "\nAI 主持已持续失败，暂时停用到本局结束或被重新开启。", LIMIT_NOTICE);
    }

    /** 引擎降级原因的内部码翻成群内可见的中文标签。 */
    static String degradeLabel(String reason) {
        String code = reason == null ? "" : reason.trim();
        if ("queue_full".equals(code)) {
            return "队列已满";
        }
        if ("queue_age".equals(code)) {
            return "队列等待超时";
        }
        if ("busy".equals(code)) {
            return "服务繁忙";
        }
        if ("timeout".equals(code)) {
            return "超时";
        }
        if ("network".equals(code)) {
            return "网络错误";
        }
        if (code.startsWith("http_") || "http".equals(code)) {
            return "服务端错误";
        }
        if ("verification".equals(code)) {
            return "输出校验失败";
        }
        if ("disabled".equals(code)) {
            return "服务未启用";
        }
        if ("unavailable".equals(code)) {
            return "服务不可用";
        }
        if ("interrupted".equals(code)) {
            return "已中断";
        }
        if ("params".equals(code)) {
            return "参数错误";
        }
        if ("unknown".equals(code)) {
            return "未知错误";
        }
        return "";
    }

    /** 重启恢复提示（招募期的局不发）。 */
    public static String recovered(Script script, SessionSnapshot snapshot, int playerCount) {
        String name = scriptName(script);
        StringBuilder body = new StringBuilder();
        body.append(TrpgTitles.of(TrpgTitles.RECOVER)).append("《").append(name).append("》已恢复\n");
        String sceneTitle = snapshot == null ? "" : snapshot.sceneTitle();
        int sceneNo = snapshot == null ? 1 : snapshot.sceneIndex() + 1;
        int sceneTotal = script == null || script.scenes().isEmpty() ? sceneNo : script.scenes().size();
        if (!sceneTitle.trim().isEmpty()) {
            body.append("当前场景：").append(sceneTitle).append("（第 ").append(sceneNo).append('/')
                    .append(sceneTotal).append(" 幕）").append(ALIGN);
        }
        body.append("已恢复 ").append(Math.max(0, playerCount)).append(" 名玩家。\n");
        body.append("本局在上次重启前正在进行，KP 可发送 /trpg resume 继续。\n\n");
        body.append("/trpg status 可以随时查看局面。");
        return limit(body.toString(), LIMIT_RECOVERED);
    }

    /** 私聊不可达时的群内折叠行：只发职业与关键数值（规范 §5.2 第 2 条）。 */
    public static String cardFolded(String name, String profession, CardData card) {
        CardData data = card == null ? CardData.empty() : card;
        StringBuilder body = new StringBuilder();
        body.append(TrpgTitles.of(TrpgTitles.CARD, name)).append("职业：")
                .append(profession == null || profession.trim().isEmpty() ? "冒险者" : profession.trim())
                .append('\n').append("STR ").append(data.attribute("STR"))
                .append(ALIGN).append("CON ").append(data.attribute("CON"))
                .append(ALIGN).append("SAN ").append(data.san())
                .append(ALIGN).append("HP ").append(data.hp());
        return limit(body.toString(), LIMIT_CARD);
    }

    /** 局面摘要（/trpg status）：无局、招募中、进行中三种形态。 */
    public static String status(TrpgStatusView view) {
        TrpgStatusView data = view == null ? TrpgStatusView.none() : view;
        if (!data.active()) {
            StringBuilder empty = new StringBuilder();
            empty.append(TrpgTitles.of(TrpgTitles.NOTICE)).append('\n');
            empty.append("本群暂无局。发送 /trpg start <剧本ID> 开团，或 /trpg help 查看指令。");
            return limit(empty.toString(), LIMIT_NOTICE);
        }
        String name = data.scriptName().isEmpty() ? "未命名剧本" : data.scriptName();
        StringBuilder body = new StringBuilder();
        if (data.state() == SessionState.RECRUITING) {
            body.append(TrpgTitles.of(TrpgTitles.STATUS)).append('《').append(name).append("》招募中\n");
            body.append("KP：").append(data.kpName()).append(ALIGN)
                    .append("已加入 ").append(data.currentPlayers()).append('/')
                    .append(data.maxPlayers()).append(" 人\n");
            body.append("当前成员：").append(playerSummary(data.playerLabels())).append('\n');
            body.append("发送 /trpg join 加入；KP 发送 /trpg begin 开局。");
            return limit(body.toString(), LIMIT_STATUS);
        }
        body.append(TrpgTitles.of(TrpgTitles.STATUS)).append('《').append(name).append("》进行中\n");
        if (!data.sceneTitle().isEmpty()) {
            body.append("当前场景：").append(data.sceneTitle()).append("（第 ")
                    .append(Math.max(1, data.sceneNo())).append('/')
                    .append(Math.max(Math.max(1, data.sceneNo()), data.sceneTotal()))
                    .append(" 幕）\n");
        }
        body.append("局内人数：").append(data.currentPlayers()).append('/').append(data.maxPlayers())
                .append(" 人").append(data.playerLabels().isEmpty() ? ""
                        : "（" + playerSummary(data.playerLabels()) + "）")
                .append('\n');
        body.append("已解锁线索 ").append(data.clueUnlocked()).append('/').append(data.clueTotal());
        if (data.lastActivityAt() > 0L) {
            body.append(ALIGN).append("最近活动：").append(dateTime(data.lastActivityAt()));
        }
        body.append('\n').append("AI 主持：").append(data.aiLine().isEmpty() ? "关" : data.aiLine());
        if (!data.recovered()) {
            body.append("\n本局在重启前正在进行，KP 可发送 /trpg resume 确认继续。");
        }
        return limit(body.toString(), LIMIT_STATUS);
    }

    /** 日志回看（/trpg log）：按 seq 倒序的一页，页脚给翻页方式。 */
    public static String logPage(String scriptName, int page, int totalPages, long total,
            List<TrpgLogEntry> entries) {
        String name = scriptName == null || scriptName.trim().isEmpty() ? "未知剧本"
                : scriptName.trim();
        int safePage = Math.max(1, page);
        int safeTotalPages = Math.max(1, totalPages);
        StringBuilder body = new StringBuilder();
        body.append(TrpgTitles.of(TrpgTitles.LOG,
                safePage + "/" + safeTotalPages)).append('\n');
        body.append('《').append(name).append("》共 ").append(Math.max(0L, total))
                .append(" 条，按时间倒序。" );
        if (entries == null || entries.isEmpty()) {
            body.append("\n本局暂无日志。");
            return limit(body.toString(), LIMIT_LOG);
        }
        for (TrpgLogEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            String actor = entry.actorName().isEmpty() ? "系统" : entry.actorName();
            body.append('\n').append(dateTime(entry.createTime())).append(ALIGN)
                    .append(actor).append('：')
                    .append(FORMATTER.clean(entry.content()));
        }
        if (safePage < safeTotalPages) {
            body.append("\n发送 /trpg log ").append(safePage + 1).append(" 查看下一页。");
        } else if (safePage > 1) {
            body.append("\n发送 /trpg log ").append(safePage - 1).append(" 查看上一页。");
        }
        return limit(body.toString(), LIMIT_LOG);
    }

    /** 指令帮助：标题固定，正文由指令层按注册表逐行给出。 */
    public static String help(List<String> lines) {
        StringBuilder body = new StringBuilder();
        body.append(TrpgTitles.of(TrpgTitles.HELP)).append('\n');
        body.append("指令前缀 /trpg，主名与中文别名等价。\n");
        if (lines != null) {
            for (String line : lines) {
                if (line != null && !line.trim().isEmpty()) {
                    body.append(line.trim()).append('\n');
                }
            }
        }
        body.append("直接发送 /r 3d6 可以掷骰。");
        return limit(body.toString(), LIMIT_HELP);
    }

    private static String playerSummary(List<String> labels) {
        if (labels == null || labels.isEmpty()) {
            return "无";
        }
        return join(labels, "、");
    }

    /** 一次性提示。 */
    public static String notice(String text) {
        return limit(TrpgTitles.of(TrpgTitles.NOTICE) + "\n" + (text == null ? "" : text), LIMIT_NOTICE);
    }

    /** 固定错误：原因 + 下一步怎么做。 */
    public static String error(String reason, String next) {
        StringBuilder body = new StringBuilder();
        body.append(TrpgTitles.of(TrpgTitles.ERROR)).append('\n').append(reason);
        if (next != null && !next.trim().isEmpty()) {
            body.append('\n').append(next.trim());
        }
        return limit(body.toString(), LIMIT_ERROR);
    }

    /** 结束确认。 */
    public static String endConfirm(Script script, String method, int confirmSeconds) {
        StringBuilder body = new StringBuilder();
        body.append(TrpgTitles.of(TrpgTitles.END_CONFIRM)).append("《").append(scriptName(script))
                .append("》\n");
        body.append("确认方式：").append(method).append("。\n");
        body.append(confirmSeconds).append(" 秒内再次发送 /trpg end 生效。");
        return limit(body.toString(), LIMIT_END_CONFIRM);
    }

    /** 局结束（先战报图，再这条文字）。 */
    public static String ended(TrpgReport report, boolean reportImageSent) {
        StringBuilder body = new StringBuilder();
        body.append(TrpgTitles.of(TrpgTitles.END)).append("《").append(report.scriptName())
                .append("》· 结局「").append(report.endingTitle()).append("」\n");
        String narration = FORMATTER.clean(report.endingNarration());
        if (!narration.isEmpty()) {
            body.append(narration).append('\n');
        }
        body.append('\n');
        body.append("时间 ").append(report.durationText())
                .append(SEPARATOR).append("线索 ").append(report.clueNames().size()).append('/')
                .append(report.clueTotal())
                .append(SEPARATOR).append("检定 ").append(report.checkTotal()).append(" 次");
        // 成功数未知（-1，例如尚未解析检定日志）时不输出括号，避免写成错误的「成功 0」。
        boolean successKnown = report.checkSuccess() >= 0;
        if (report.checkTotal() > 0 && successKnown) {
            body.append("（成功 ").append(report.checkSuccess()).append('）');
        }
        // 冻结样例（规范 §4.4）：成功数括号后紧接「· 成长」，无空格。
        body.append(report.checkTotal() > 0 && successKnown ? "· 成长 " : SEPARATOR + "成长 ")
                .append(report.growthLines().size()).append(" 人次\n");
        body.append(reportImageSent
                ? "战报图见上一条；文字明细发送 /trpg log 查看（保留 " + report.retentionDays() + " 天）。"
                : "文字明细发送 /trpg log 查看（保留 " + report.retentionDays() + " 天）。");
        return limit(body.toString(), LIMIT_ENDED);
    }

    /** 战报纯文本（图失败时的回退，字段与图一致）。 */
    public static String reportText(TrpgReport report) {
        StringBuilder body = new StringBuilder();
        body.append(TrpgTitles.of(TrpgTitles.REPORT)).append("《").append(report.scriptName())
                .append("》· 结局「").append(report.endingTitle()).append("」\n");
        body.append("结束时间：").append(dateTime(report.endedAt())).append(ALIGN)
                .append("时长：").append(report.durationText()).append('\n');
        body.append("KP：").append(report.kpName()).append(ALIGN).append("玩家：")
                .append(join(report.playerNames(), "、")).append('\n');
        body.append("线索：");
        if (report.clueNames().isEmpty()) {
            body.append("无（0/").append(report.clueTotal()).append("）");
        } else {
            body.append(join(report.clueNames(), "、")).append("（")
                    .append(report.clueNames().size()).append('/').append(report.clueTotal())
                    .append("）");
        }
        body.append('\n').append("检定：");
        if (report.checkResults().isEmpty()) {
            body.append("无独立检定");
        } else {
            body.append(join(report.checkResults(), "、")).append("（共 ")
                    .append(report.checkTotal()).append(" 次）");
        }
        body.append('\n').append("成长：");
        body.append(report.growthLines().isEmpty()
                ? "本局无涨点" : join(report.growthLines(), "、"));
        body.append('\n').append("日志：/trpg log 可查看本局记录（保留 ")
                .append(report.retentionDays()).append(" 天）");
        return limit(body.toString(), LIMIT_REPORT_TEXT);
    }

    /** 角色卡全文（只私聊本人）。 */
    public static String card(String name, CardData card) {
        CardData data = card == null ? CardData.empty() : card;
        StringBuilder body = new StringBuilder();
        body.append(TrpgTitles.of(TrpgTitles.CARD, name)).append('\n');
        List<String> attributes = CardData.ATTRIBUTES;
        for (int index = 0; index < attributes.size(); index += 4) {
            List<String> lineParts = new ArrayList<String>();
            for (int inner = index; inner < Math.min(index + 4, attributes.size()); inner++) {
                String key = attributes.get(inner);
                lineParts.add(key + " " + data.attribute(key));
            }
            body.append(join(lineParts, ALIGN)).append('\n');
        }
        body.append("SAN ").append(data.san()).append(ALIGN).append("HP ").append(data.hp())
                .append(ALIGN).append("MP ").append(data.mp()).append('\n');
        Map<String, Integer> skills = data.skills();
        if (skills.isEmpty()) {
            body.append("技能：无");
        } else {
            List<String> skillParts = new ArrayList<String>();
            for (Map.Entry<String, Integer> entry : skills.entrySet()) {
                skillParts.add(entry.getKey() + " " + entry.getValue());
            }
            body.append("技能：").append(join(skillParts, "、"));
        }
        body.append('\n').append("物品：")
                .append(data.items().isEmpty() ? "无" : join(data.items(), "、"));
        return limit(body.toString(), LIMIT_CARD);
    }

    /** KP 提示（只私聊 KP）：场景、触发、线索全文、下一场景。 */
    public static String kpHint(String sceneTitle, String triggerHint, List<String> clueTexts,
                                String nextSceneTitle) {
        StringBuilder body = new StringBuilder();
        body.append(TrpgTitles.of(TrpgTitles.KP_HINT, sceneTitle)).append('\n');
        if (triggerHint != null && !triggerHint.trim().isEmpty()) {
            body.append("触发：").append(triggerHint.trim()).append('\n');
        }
        if (clueTexts != null && !clueTexts.isEmpty()) {
            body.append("线索全文：\n");
            for (String text : clueTexts) {
                body.append(text).append('\n');
            }
        }
        if (nextSceneTitle != null && !nextSceneTitle.trim().isEmpty()) {
            body.append("下一场景：").append(nextSceneTitle.trim());
        }
        return limit(body.toString(), LIMIT_KP_HINT);
    }

    /** 剧本库列表（每页 10 条）。 */
    public static String scriptList(List<String> entries, int page, int totalPages) {
        return scriptList(entries, page, totalPages, 0);
    }

    /** 剧本库列表（每页 10 条），skippedFiles 为启动时被跳过的文件数。 */
    public static String scriptList(List<String> entries, int page, int totalPages, int skippedFiles) {
        StringBuilder body = new StringBuilder();
        body.append(TrpgTitles.of(TrpgTitles.SCRIPT_LIBRARY)).append("第 ").append(page).append('/')
                .append(Math.max(1, totalPages)).append(" 页");
        for (String entry : entries) {
            body.append('\n').append(entry);
        }
        if (skippedFiles > 0) {
            body.append("\n\n有 ").append(skippedFiles)
                    .append(" 个剧本文件加载失败已被跳过，详见插件日志。");
        }
        body.append("\n\n发送 /trpg script show <id> 查看详情。");
        return limit(body.toString(), LIMIT_SCRIPT_LIBRARY);
    }

    /** 剧本详情（私聊）。 */
    public static String scriptDetail(Script script) {
        if (script == null) {
            return TrpgTitles.of(TrpgTitles.SCRIPT) + "\n剧本不存在。";
        }
        StringBuilder body = new StringBuilder();
        body.append(TrpgTitles.of(TrpgTitles.SCRIPT, script.name())).append('\n');
        body.append("id：").append(script.id()).append(ALIGN).append("作者：")
                .append(script.author().isEmpty() ? "未知" : script.author()).append('\n');
        body.append(scriptMeta(script)).append('\n');
        body.append("场景 ").append(script.scenes().size()).append(" 个")
                .append(ALIGN).append("线索 ").append(script.clues().size()).append(" 个")
                .append(ALIGN).append("结局 ").append(script.endings().size()).append(" 个");
        if (!script.description().isEmpty()) {
            body.append('\n').append(script.description());
        }
        return limit(body.toString(), LIMIT_SCRIPT_LIBRARY);
    }

    /** 分段导入指引（开始导入时回一条）。 */
    public static String scriptImportGuide(ScriptImportMode mode, long limitBytes,
                                           int idleMinutes, int maxMinutes) {
        ScriptImportMode selected = mode == null ? ScriptImportMode.CREATE : mode;
        StringBuilder body = new StringBuilder();
        body.append(TrpgTitles.of(TrpgTitles.SCRIPT, "导入")).append('\n');
        body.append("本次为").append(selected.label()).append("导入。\n");
        body.append("把剧本 JSON 分条发给我：可以一条整段，也可以切成多条，切点不限，不必带指令前缀。\n");
        body.append("导入结束前，你发的其他内容只会当片段接收，不会执行也不会回复。\n");
        body.append("总量上限 ").append(kilobytes(limitBytes)).append("；")
                .append(idleMinutes).append(" 分钟没有新内容或从开始算超过 ")
                .append(maxMinutes).append(" 分钟会自动放弃。\n");
        body.append("发 /trpg script import end 结束并保存；发 /trpg script import cancel 取消。");
        return limit(body.toString(), LIMIT_SCRIPT_IMPORT_GUIDE);
    }

    /** 分段导入「还不能解析」：保留暂存，提示当前字节数与前 5 条错误里的第一条。 */
    public static String scriptImportIssue(long receivedBytes, long limitBytes, ScriptIssue issue) {
        StringBuilder body = new StringBuilder();
        body.append(TrpgTitles.of(TrpgTitles.SCRIPT, "导入")).append('\n');
        body.append("已收到 ").append(kilobytes(receivedBytes)).append(" / ")
                .append(kilobytes(limitBytes)).append("，当前内容还不能解析：\n");
        body.append(issueText(issue)).append('\n');
        body.append("可继续发送剩余内容后再次 /trpg script import end，或 /trpg script import cancel 取消。");
        return limit(body.toString(), LIMIT_SCRIPT_IMPORT_ISSUE);
    }

    /** 分段导入校验未通过：本次导入已结束，展示前 5 条错误。 */
    public static String scriptImportRejected(ScriptValidationResult result) {
        StringBuilder body = new StringBuilder();
        body.append(TrpgTitles.of(TrpgTitles.SCRIPT, "导入")).append('\n');
        body.append("校验未通过，本次导入已结束：\n");
        appendIssues(body, result == null ? null : result.reportedErrors());
        if (result != null && result.remainingErrorCount() > 0) {
            body.append("另有 ").append(result.remainingErrorCount()).append(" 条错误未展示。\n");
        }
        body.append("原文已存入数据目录 imports/rejected/，修正后重新发送 /trpg script import 导入。");
        return limit(body.toString(), LIMIT_SCRIPT_LIBRARY);
    }

    /** 分段导入成功。 */
    public static String scriptImportSuccess(ScriptImportResult result) {
        if (result == null || result.script() == null) {
            return TrpgTitles.of(TrpgTitles.SCRIPT, "导入") + "\n导入成功，但没有返回剧本摘要。";
        }
        ScriptInfo info = result.script();
        StringBuilder body = new StringBuilder();
        body.append(TrpgTitles.of(TrpgTitles.SCRIPT, info.name())).append('\n');
        body.append("id：").append(info.id()).append(ALIGN)
                .append("场景 ").append(info.sceneCount()).append(" 个").append(ALIGN)
                .append("线索 ").append(info.clueCount()).append(" 个").append(ALIGN)
                .append("结局 ").append(info.endingCount()).append(" 个\n");
        body.append(result.replaced() ? "已替换原剧本。" : "已存入剧本库。");
        if (result.warningCount() > 0) {
            body.append("告警 ").append(result.warningCount()).append(" 条（不阻断导入）。");
        }
        body.append('\n').append("发送 /trpg start ").append(info.id()).append(" 开团，")
                .append("/trpg script show ").append(info.id()).append(" 查看详情。");
        return limit(body.toString(), LIMIT_SCRIPT_LIBRARY);
    }

    /** 分段导入已取消。 */
    public static String scriptImportCancelled() {
        return limit(TrpgTitles.of(TrpgTitles.SCRIPT, "导入") + "\n已取消导入，未写入剧本库。",
                LIMIT_NOTICE);
    }

    /** 剧本生成已受理（异步任务回执）。 */
    public static String scriptGenStarted(String theme, int players, int minutes) {
        StringBuilder body = new StringBuilder();
        body.append(TrpgTitles.of(TrpgTitles.NOTICE)).append('\n');
        body.append("已开始生成剧本《").append(theme == null ? "" : theme.trim()).append("》");
        if (players > 0) {
            body.append("（").append(players).append(" 人");
            if (minutes > 0) {
                body.append(SEPARATOR).append("约 ").append(minutes).append(" 分钟");
            }
            body.append("）");
        }
        body.append("，完成后会 @ 发起人；期间可以继续游戏。");
        return limit(body.toString(), LIMIT_NOTICE);
    }

    /** 剧本生成结果（成功或失败，异步任务回执）。 */
    public static String scriptGenResult(ScriptGenerationOutcome outcome) {
        if (outcome == null) {
            return error("生成没有返回结果。", "请稍后重试，或改用手工导入。");
        }
        if (outcome.success() && outcome.script() != null) {
            ScriptInfo info = outcome.script();
            StringBuilder body = new StringBuilder();
            body.append(TrpgTitles.of(TrpgTitles.SCRIPT, info.name())).append('\n');
            body.append("id：").append(info.id()).append(ALIGN)
                    .append("场景 ").append(info.sceneCount()).append(" 个").append(ALIGN)
                    .append("线索 ").append(info.clueCount()).append(" 个").append(ALIGN)
                    .append("结局 ").append(info.endingCount()).append(" 个\n");
            body.append("已存入剧本库，发送 /trpg start ").append(info.id()).append(" 开团。");
            return limit(body.toString(), LIMIT_SCRIPT_GEN);
        }
        StringBuilder body = new StringBuilder();
        body.append(TrpgTitles.of(TrpgTitles.ERROR)).append('\n');
        body.append("剧本生成失败：").append(outcome.errorCode()).append("，")
                .append(genReason(outcome.errorCode()));
        if (!outcome.errors().isEmpty()) {
            body.append("：\n");
            appendIssues(body, outcome.errors());
            if (outcome.errorCount() > outcome.errors().size()) {
                body.append("另有 ").append(outcome.errorCount() - outcome.errors().size())
                        .append(" 条错误未展示。\n");
            }
        } else {
            body.append("。\n");
        }
        if (!outcome.savedFile().isEmpty()) {
            body.append("原始返回已存入 imports/rejected/").append(outcome.savedFile()).append('\n');
        }
        body.append("可换题材重试，或把修正后的 JSON 按 /trpg script import 流程手工导入。");
        return limit(body.toString(), LIMIT_SCRIPT_GEN);
    }

    /** 删除剧本成功。 */
    public static String scriptDeleted(ScriptInfo info) {
        String name = info == null ? "" : info.name();
        String id = info == null ? "" : info.id();
        return limit(TrpgTitles.of(TrpgTitles.SCRIPT, "删除") + "\n已删除《" + name + "》（" + id + "）。",
                LIMIT_NOTICE);
    }

    private static void appendIssues(StringBuilder body, List<ScriptIssue> issues) {
        if (issues == null) {
            return;
        }
        for (ScriptIssue issue : issues) {
            // 表现规格：行间不加序号符号，错误逐行平铺。
            body.append(issueText(issue)).append('\n');
        }
    }

    private static String issueText(ScriptIssue issue) {
        if (issue == null) {
            return "E_PARSE：内容还不是完整 JSON";
        }
        return issue.code() + " @" + issue.path() + "：" + issue.message();
    }

    private static String genReason(String code) {
        if ("E_AI_DISABLED".equals(code)) {
            return "AI 服务未开启或参数不合法";
        }
        if ("E_AI_BUSY".equals(code)) {
            return "已有生成任务在跑，请稍后再试";
        }
        if ("E_AI_TIMEOUT".equals(code)) {
            return "AI 调用超时";
        }
        if ("E_AI_OUTPUT".equals(code)) {
            return "AI 返回的不是合法 JSON";
        }
        if ("E_AI_VALIDATION".equals(code)) {
            return "生成内容未通过校验";
        }
        return "AI 调用失败";
    }

    private static String kilobytes(long bytes) {
        if (bytes <= 0L) {
            return "0 KB";
        }
        double value = bytes / 1024.0D;
        return String.format(Locale.ROOT, "%.1f KB", value);
    }

    /**
     * /r 与 /roll 回复：不套标题（规范 §3.4）。
     *
     * <p>单段只回一行 {@code /r 3d6+2 = 14}；多段每条骰段一行 {@code [r] 3d6 = 9}，
     * 末行合计 {@code [r] 3d6+2 = 14}（常数段不单独列行）。</p>
     */
    public static String dice(TrpgDiceView view) {        if (view == null) {
            return "";
        }
        StringBuilder body = new StringBuilder();
        if (view.segmentCount() > 1) {
            for (int index = 0; index < view.segmentCount(); index++) {
                String text = view.segmentTexts().get(index);
                if (text == null || text.trim().isEmpty()) {
                    continue;
                }
                body.append("[r] ").append(text).append(" = ")
                        .append(view.segmentValues().get(index)).append('\n');
            }
            body.append("[r] ").append(view.expression()).append(" = ").append(view.total());
            return body.toString();
        }
        body.append('/').append(view.commandPrefix()).append(' ').append(view.expression())
                .append(" = ").append(view.total());
        return body.toString();
    }

    /** 表达式解析失败：不套标题的一行。 */
    public static String diceUnsupported(String expression) {
        return "表达式不支持：" + expression + "（只支持 NdM 与加法，最多 3 段）";
    }

    /** 检定等级子句，例如 {@code → 常规成功}、{@code → 大成功（01）}。 */
    public static String degreeClause(TrpgCheckView view) {
        if (view.degree() == TrpgCheckView.DEGREE_CRITICAL) {
            return "→ 大成功（" + twoDigits(view.rolled()) + "）";
        }
        return "→ " + view.degreeLabel();
    }

    /** 时间格式：{@code yyyy-MM-dd HH:mm}。 */
    public static String dateTime(long millis) {
        if (millis <= 0L) {
            return "未知";
        }
        return new SimpleDateFormat(DATE_TIME_PATTERN, Locale.CHINA).format(new Date(millis));
    }

    /** 日期格式：{@code yyyy-MM-dd}。 */
    public static String date(long millis) {
        if (millis <= 0L) {
            return "未知";
        }
        return new SimpleDateFormat(DATE_PATTERN, Locale.CHINA).format(new Date(millis));
    }

    /** 收敛到 §3 表格的正文上限：保留标题行，只截正文。 */
    private static String limit(String message, int limit) {
        int newline = message.indexOf('\n');
        if (newline < 0) {
            return message;
        }
        String title = message.substring(0, newline);
        String body = message.substring(newline + 1);
        return title + "\n" + FORMATTER.truncate(body, limit);
    }

    private static String twoDigits(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    private static String scriptName(Script script) {
        return script == null || script.name().isEmpty() ? "未知剧本" : script.name();
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