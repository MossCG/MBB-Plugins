package org.moboxlab.mbb.trpg.present;

import org.moboxlab.mbb.trpg.script.Script;
import org.moboxlab.mbb.trpg.script.ScriptCatalog;
import org.moboxlab.mbb.trpg.session.MemberView;
import org.moboxlab.mbb.trpg.session.Messaging;
import org.moboxlab.mbb.trpg.session.SessionEvent;
import org.moboxlab.mbb.trpg.session.SessionEventPublisher;
import org.moboxlab.mbb.trpg.session.SessionSnapshot;
import org.moboxlab.moboxbot.API.PluginLogger;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 呈现层门面：模板 → 格式化 → 拆分/折叠 → 图片 → 发送 → 失败回退。
 *
 * <p>唯一消息出口：指令层与事件消费都经过这里；发送失败只记日志并按规格回退，
 * 不在本类重试，也不在锁内调用（调用方保证只在群锁外调用）。</p>
 */
public final class TrpgPresenter implements SessionEventPublisher {

    /** 关键消息（开团/开局/结束）发送失败时补的一行提示。 */
    public static final String CRITICAL_FAILURE_NOTICE =
            "上一条消息发送失败，可发送 /trpg status 查看当前局面。";
    /** 角色卡私聊不可达时的群内提示后缀。 */
    public static final String CARD_UNREACHABLE_SUFFIX =
            " 的私聊不可达，角色卡全文未发出，可在 QQ 设置里放开临时会话后重发 /trpg pc。";
    /** 秘密信息私聊不可达时的群内提示（不回退正文）。 */
    public static final String SECRET_UNREACHABLE_NOTICE =
            "私聊消息未能送达，本条内容未在群内展示，可稍后重试。";
    /** 连续私聊失败达到该次数后，后续私聊改群内折叠。 */
    public static final int PRIVATE_FAILURE_LIMIT = 2;
    /** 连续失败后角色卡改在群内发折叠行时的提示前缀。 */
    public static final String CARD_FOLDED_SUFFIX =
            " 的私聊不可达，本局角色卡改为群内折叠（只列职业与关键数值）：";

    private static final String LOG_PREFIX = "[MBB-Trpg] ";

    private final TrpgPresentConfig config;
    private final Messaging messaging;
    private final TrpgImageRenderer renderer;
    private final TrpgNameResolver names;
    private final ScriptCatalog catalog;
    private final TrpgReportSource reports;
    private final PluginLogger logger;
    private final TrpgMessageFormatter formatter = new TrpgMessageFormatter();
    private final TrpgMessageSplitter splitter = new TrpgMessageSplitter(formatter);
    private final ConcurrentMap<Long, Integer> privateFailures = new ConcurrentHashMap<Long, Integer>();

    public TrpgPresenter(TrpgPresentConfig config, Messaging messaging, TrpgImageRenderer renderer,
                         TrpgNameResolver names, ScriptCatalog catalog, TrpgReportSource reports,
                         PluginLogger logger) {
        if (config == null) {
            throw new IllegalArgumentException("呈现配置不能为空");
        }
        if (messaging == null) {
            throw new IllegalArgumentException("发送端口不能为空");
        }
        if (names == null) {
            throw new IllegalArgumentException("昵称解析器不能为空");
        }
        if (reports == null) {
            throw new IllegalArgumentException("战报来源不能为空");
        }
        this.config = config;
        this.messaging = messaging;
        this.renderer = renderer;
        this.names = names;
        this.catalog = catalog;
        this.reports = reports;
        this.logger = logger;
    }

    @Override
    public void publish(SessionEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("事件不能为空");
        }
        try {
            if (event.type() == SessionEvent.Type.ENDED) {
                presentEnded(event);
            } else {
                presentRecovered(event);
            }
        } catch (RuntimeException error) {
            warn("呈现事件失败，已忽略：事件=" + event.type() + "，原因=" + error.getMessage());
        }
    }

    private void presentEnded(SessionEvent event) {
        SessionSnapshot snapshot = event.snapshot();
        long groupId = snapshot.groupId();
        long sessionId = snapshot.sessionId();
        Script script = script(snapshot.scriptId());
        TrpgReport report = reports.report(event, script);
        boolean imageSent = false;
        boolean imageTried = false;
        if (config.reportImage()) {
            imageTried = true;
            imageSent = sendReportImage(groupId, sessionId, report);
        }
        // 规格 §7.4：画图失败时只发一条【战报】纯文本；开关关闭时仍发【结束】，不提战报图。
        String message = imageTried && !imageSent
                ? TrpgMessageTemplates.reportText(report)
                : TrpgMessageTemplates.ended(report, imageSent);
        boolean sent = sendGroup(groupId, message);
        if (!sent) {
            sendCriticalFailureNotice(groupId);
        }
    }

    private void presentRecovered(SessionEvent event) {
        SessionSnapshot snapshot = event.snapshot();
        Script script = script(snapshot.scriptId());
        int playerCount = 0;
        for (MemberView member : snapshot.members()) {
            if (member.inSession()) {
                playerCount++;
            }
        }
        if (!sendGroup(snapshot.groupId(),
                TrpgMessageTemplates.recovered(script, snapshot, playerCount))) {
            sendCriticalFailureNotice(snapshot.groupId());
        }
    }

    /** 补一行关键消息失败提示（不重发正文）。 */
    public boolean sendCriticalFailureNotice(long groupId) {
        String message = TrpgMessageTemplates.notice(CRITICAL_FAILURE_NOTICE);
        return splitAndSendGroup(groupId, message, false);
    }

    /**
     * 群内发一条消息：超长按标题+正文拆成至多 {@code splitMax} 条，折叠尾注指向 /trpg log。
     *
     * <p>规格 §5.4：开团与开局属于关键消息，发送失败时补一行【提示】（结束与恢复由事件处理自己补）。</p>
     */
    public boolean sendGroup(long groupId, String message) {
        boolean sent = splitAndSendGroup(groupId, message, true);
        if (!sent && isCriticalTitle(message)) {
            sendCriticalFailureNotice(groupId);
        }
        return sent;
    }

    private boolean isCriticalTitle(String message) {
        String title = titleOf(formatter.clean(message));
        return title.equals(TrpgTitles.of(TrpgTitles.RECRUIT))
                || title.equals(TrpgTitles.of(TrpgTitles.BEGIN));
    }

    /**
     * 群内发一条消息并 @ 指定成员：用于异步任务把结果只回给发起人。
     *
     * <p>超长只折叠，不拆分；@ 只出现在消息开头一次。</p>
     */
    public boolean sendGroupAt(long groupId, long userId, String message) {
        String clean = formatter.clean(message);
        if (clean.isEmpty()) {
            return false;
        }
        String folded = splitter.fold(titleOf(clean), bodyOf(clean), config.groupMaxChars());
        boolean sent = messaging.sendGroupAt(groupId, userId, folded);
        if (!sent) {
            warn("群消息发送失败（@）：群=" + groupId + "，对象=" + userId);
        }
        return sent;
    }

    private boolean splitAndSendGroup(long groupId, String message, boolean allowSplit) {
        String clean = formatter.clean(message);
        if (clean.isEmpty()) {
            // 空内容不发也算「没发出去」，避免调用方把空消息当成已送达。
            return false;
        }
        String title = titleOf(clean);
        String body = bodyOf(clean);
        List<String> parts = Collections.singletonList(clean);
        if (allowSplit) {
            parts = splitter.split(title, body, config.groupMaxChars(), config.splitMax());
        }
        boolean sent = true;
        int index = 0;
        for (String part : parts) {
            index++;
            if (!messaging.sendGroupText(groupId, part)) {
                sent = false;
                warn("群消息发送失败：群=" + groupId + "，第 " + index + "/" + parts.size() + " 条");
            }
        }
        return sent;
    }

    /** 私聊发一条消息：不拆分，超长只折叠。 */
    public boolean sendPrivate(long userId, String message) {
        String clean = formatter.clean(message);
        if (clean.isEmpty()) {
            return false;
        }
        String folded = splitter.fold(titleOf(clean), bodyOf(clean), config.privateMaxChars());
        boolean sent = messaging.sendPrivateText(userId, folded);
        notePrivateResult(userId, sent);
        if (!sent) {
            warn("私聊发送失败：对象=" + userId);
        }
        return sent;
    }

    /** 私聊发角色卡全文；不可达时群内补一行提示，并累计失败次数。 */
    public boolean sendPrivateCard(long groupId, long userId, String name, String message) {
        return sendPrivateCard(groupId, userId, name, message, null);
    }

    /**
     * 私聊发角色卡全文；不可达时群内补一行提示，并累计失败次数。
     *
     * <p>规格 §5.2：同一成员连续失败达到 {@link #PRIVATE_FAILURE_LIMIT} 次后，本局后续角色卡
     * 不再尝试私聊，改在群内只发 {@code foldedSummary} 一行（职业与关键数值），不发全文；
     * {@code foldedSummary} 为空时退回普通不可达提示。计数由入局流程调用
     * {@link #clearPrivateFailures(long)} 重置，局结束不再提示。</p>
     */
    public boolean sendPrivateCard(long groupId, long userId, String name, String message,
                                   String foldedSummary) {
        String owner = name == null || name.trim().isEmpty()
                ? TrpgNameResolver.fallback(userId) : name.trim();
        if (privateDegraded(userId) && foldedSummary != null && !foldedSummary.trim().isEmpty()) {
            return splitAndSendGroup(groupId,
                    TrpgMessageTemplates.notice(owner + CARD_FOLDED_SUFFIX + foldedSummary.trim()), false);
        }
        if (sendPrivate(userId, message)) {
            return true;
        }
        splitAndSendGroup(groupId, TrpgMessageTemplates.notice(owner + CARD_UNREACHABLE_SUFFIX), false);
        return false;
    }

    /** 私聊发秘密信息（线索全文、KP 提示、NPC 秘密）；失败只群内提示，绝不回退正文。 */
    public boolean sendPrivateSecret(long groupId, long userId, String message) {
        if (sendPrivate(userId, message)) {
            return true;
        }
        warn("秘密信息私聊失败，未回退群内：对象=" + userId);
        splitAndSendGroup(groupId, TrpgMessageTemplates.notice(SECRET_UNREACHABLE_NOTICE), false);
        return false;
    }

    /** 发情景卡：图片优先，关闭或失败时回退纯文本（局号未知）。 */
    public boolean sendScene(long groupId, TrpgSceneCard card) {
        return sendScene(groupId, 0L, card);
    }

    /** 发情景卡：图片优先，关闭或失败时回退纯文本。 */
    public boolean sendScene(long groupId, long sessionId, TrpgSceneCard card) {
        if (card == null) {
            throw new IllegalArgumentException("情景卡不能为空");
        }
        if (config.sceneImage()) {
            byte[] image = render("情景卡", "情景卡 · " + card.sceneTitle(), sessionId,
                    TrpgImageTemplates.scene(card));
            if (image != null) {
                if (messaging.sendGroupImage(groupId, image)) {
                    return true;
                }
                warn("情景卡图片发送失败，回退纯文本：群=" + groupId + "，场景=" + card.sceneTitle());
            }
        }
        return sendGroup(groupId,
                TrpgMessageTemplates.sceneFallback(card.sceneTitle(), card.fallbackText()));
    }

    /** 群内发战报图；渲染不可用或发送失败返回 false，由调用方改发纯文本。 */
    public boolean sendReportImage(long groupId, long sessionId, TrpgReport report) {
        if (report == null) {
            throw new IllegalArgumentException("战报不能为空");
        }
        byte[] image = render("战报", report.cardTitle(), sessionId,
                TrpgImageTemplates.report(report, config.imageMaxLines()));
        if (image == null) {
            return false;
        }
        if (messaging.sendGroupImage(groupId, image)) {
            return true;
        }
        warn("战报图片发送失败，改发纯文本：局=" + sessionId);
        return false;
    }

    /** 私聊发剧本打印页；图片不可用或失败时回退纯文本（仍私聊）。 */
    public boolean sendScriptPage(long groupId, long userId, Script script) {
        if (script == null) {
            throw new IllegalArgumentException("剧本不能为空");
        }
        byte[] image = render("剧本页", "剧本 · 《" + script.name() + "》", 0L,
                TrpgImageTemplates.scriptPage(script));
        if (image != null) {
            if (messaging.sendPrivateImage(userId, image)) {
                notePrivateResult(userId, true);
                return true;
            }
            warn("剧本页图片发送失败，回退纯文本：对象=" + userId);
        }
        return sendPrivate(userId, TrpgMessageTemplates.scriptDetail(script));
    }

    /** 发一条【提示】。 */
    public boolean sendNotice(long groupId, String text) {
        return splitAndSendGroup(groupId, TrpgMessageTemplates.notice(text), false);
    }

    /** 该成员当前连续私聊失败次数。 */
    public int privateFailureStreak(long userId) {
        Integer value = privateFailures.get(userId);
        return value == null ? 0 : value;
    }

    /** 该成员是否已改为「群内折叠」模式（连续失败达到上限）。 */
    public boolean privateDegraded(long userId) {
        return privateFailureStreak(userId) >= PRIVATE_FAILURE_LIMIT;
    }

    /** 清空私聊失败计数（成员重新入局时调用）。 */
    public void clearPrivateFailures(long userId) {
        privateFailures.remove(userId);
    }

    private void notePrivateResult(long userId, boolean sent) {
        if (sent) {
            privateFailures.remove(userId);
            return;
        }
        // 并发下可能同时失败同一成员，用原子累加避免丢次数；超过阈值后不再增长。
        privateFailures.compute(userId, new java.util.function.BiFunction<Long, Integer, Integer>() {
            @Override
            public Integer apply(Long key, Integer current) {
                return current == null ? 1 : Math.min(current + 1, PRIVATE_FAILURE_LIMIT);
            }
        });
    }

    /**
     * 渲染图片；渲染器未装配、返回 null 或抛异常都按规范 §7.4 记 WARN 后回退纯文本。
     *
     * <p>局号未知时传 0，告警里不写「局=0」。</p>
     */
    private byte[] render(String kind, String subject, long sessionId, List<String> lines) {
        String context = "，类型=" + kind + (sessionId > 0L ? "，局=" + sessionId : "")
                + "，主体=" + subject;
        if (renderer == null) {
            warn("图片渲染不可用，回退纯文本" + context);
            return null;
        }
        try {
            byte[] image = renderer.render(subject, lines);
            if (image == null) {
                warn("图片渲染返回空，回退纯文本" + context);
            }
            return image;
        } catch (RuntimeException error) {
            warn("图片渲染失败，回退纯文本" + context + "，原因=" + error.getMessage());
            return null;
        }
    }

    private Script script(String scriptId) {
        if (catalog == null || scriptId == null || scriptId.trim().isEmpty()) {
            return null;
        }
        return catalog.get(scriptId);
    }

    private static String titleOf(String text) {
        int newline = text.indexOf('\n');
        String first = newline < 0 ? text : text.substring(0, newline);
        return first.startsWith("【") ? first.trim() : "";
    }

    private static String bodyOf(String text) {
        if (titleOf(text).isEmpty()) {
            return text;
        }
        int newline = text.indexOf('\n');
        return newline < 0 ? "" : text.substring(newline + 1);
    }

    private void warn(String message) {
        if (logger != null) {
            logger.sendWarn(LOG_PREFIX + message);
        }
    }
}