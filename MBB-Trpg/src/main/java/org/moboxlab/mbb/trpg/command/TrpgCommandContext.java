package org.moboxlab.mbb.trpg.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.moboxlab.moboxbot.API.PluginLogger;
import org.moboxlab.mbb.trpg.ai.AiTrigger;
import org.moboxlab.mbb.trpg.ai.AiTriggerSink;
import org.moboxlab.mbb.trpg.card.CardGenerator;
import org.moboxlab.mbb.trpg.config.TrpgConfig;
import org.moboxlab.mbb.trpg.present.TrpgLogEntry;
import org.moboxlab.mbb.trpg.present.TrpgMessageTemplates;
import org.moboxlab.mbb.trpg.present.TrpgNameResolver;
import org.moboxlab.mbb.trpg.present.TrpgPresenter;
import org.moboxlab.mbb.trpg.present.TrpgSceneCard;
import org.moboxlab.mbb.trpg.present.TrpgStatusView;
import org.moboxlab.mbb.trpg.script.Clue;
import org.moboxlab.mbb.trpg.script.Scene;
import org.moboxlab.mbb.trpg.script.Script;
import org.moboxlab.mbb.trpg.script.ScriptAdmin;
import org.moboxlab.mbb.trpg.script.ScriptCatalog;
import org.moboxlab.mbb.trpg.session.Clock;
import org.moboxlab.mbb.trpg.session.CooldownRegistry;
import org.moboxlab.mbb.trpg.session.ErrorDedupe;
import org.moboxlab.mbb.trpg.session.MemberView;
import org.moboxlab.mbb.trpg.session.SessionActor;
import org.moboxlab.mbb.trpg.session.SessionLogService;
import org.moboxlab.mbb.trpg.session.SessionReject;
import org.moboxlab.mbb.trpg.session.SessionResult;
import org.moboxlab.mbb.trpg.session.SessionService;
import org.moboxlab.mbb.trpg.session.SessionSnapshot;
import org.moboxlab.mbb.trpg.store.BlacklistService;
import org.moboxlab.mbb.trpg.store.CardData;
import org.moboxlab.mbb.trpg.store.LogRow;

/**
 * 指令处理器的上下文：持有各服务与纯展示工具，供各 handler 复用。
 *
 * <p>本类只做「拼参数 → 调服务 → 拼回执」，不写库、不持局面状态；权限、状态、参数与冷却
 * 判定都委托给 {@link SessionService} / {@link SessionLogService}，指令层只在服务已放行后
 * 才回报参数错误，以保持「群内 → 权限 → 状态 → 参数 → 冷却」的固定顺序。</p>
 */
public final class TrpgCommandContext {

    private static final long MILLIS_PER_SECOND = 1000L;
    private static final String HELP_NEXT = "发送 /trpg help 查看用法。";
    private static final String PRIVATE_NEXT = "私聊只支持 help 与 /roll。";
    private static final String RETRY_NEXT = "请稍后重试。";
    private static final String UNKNOWN_NEXT = "发送 /trpg help 查看可用指令。";

    private final SessionService sessions;
    private final SessionLogService logs;
    private final ScriptCatalog catalog;
    private final CardGenerator cards;
    private final TrpgPresenter presenter;
    private final TrpgNameResolver names;
    private final TrpgConfig config;
    private final Clock clock;
    private final PluginLogger logger;
    private final CooldownRegistry cooldowns;
    private final ErrorDedupe dedupe;
    private final BlacklistService blacklist;
    private final ScriptAdmin scriptAdmin;
    private AiTriggerSink aiTriggers;

    public TrpgCommandContext(SessionService sessions, SessionLogService logs, ScriptCatalog catalog,
                              CardGenerator cards, TrpgPresenter presenter, TrpgNameResolver names,
                              TrpgConfig config, Clock clock, PluginLogger logger,
                              CooldownRegistry cooldowns, ErrorDedupe dedupe) {
        this(sessions, logs, catalog, cards, presenter, names, config, clock, logger, cooldowns,
                dedupe, null);
    }

    /** 生产装配：额外注入拉黑名单服务，供 blacklist 指令与消息入口判定使用。 */
    public TrpgCommandContext(SessionService sessions, SessionLogService logs, ScriptCatalog catalog,
                              CardGenerator cards, TrpgPresenter presenter, TrpgNameResolver names,
                              TrpgConfig config, Clock clock, PluginLogger logger,
                              CooldownRegistry cooldowns, ErrorDedupe dedupe,
                              BlacklistService blacklist) {
        this(sessions, logs, catalog, cards, presenter, names, config, clock, logger, cooldowns,
                dedupe, blacklist, null);
    }

    /** 生产装配：额外注入剧本管理三件套（S10 导入 / 生成 / 执行器）。 */
    public TrpgCommandContext(SessionService sessions, SessionLogService logs, ScriptCatalog catalog,
                              CardGenerator cards, TrpgPresenter presenter, TrpgNameResolver names,
                              TrpgConfig config, Clock clock, PluginLogger logger,
                              CooldownRegistry cooldowns, ErrorDedupe dedupe,
                              BlacklistService blacklist, ScriptAdmin scriptAdmin) {
        if (sessions == null) {
            throw new IllegalArgumentException("局面服务不能为空");
        }
        if (logs == null) {
            throw new IllegalArgumentException("日志服务不能为空");
        }
        if (catalog == null) {
            throw new IllegalArgumentException("剧本库不能为空");
        }
        if (cards == null) {
            throw new IllegalArgumentException("角色卡生成器不能为空");
        }
        if (presenter == null) {
            throw new IllegalArgumentException("呈现服务不能为空");
        }
        if (names == null) {
            throw new IllegalArgumentException("昵称解析器不能为空");
        }
        if (config == null) {
            throw new IllegalArgumentException("配置不能为空");
        }
        if (clock == null) {
            throw new IllegalArgumentException("时间端口不能为空");
        }
        if (cooldowns == null || dedupe == null) {
            throw new IllegalArgumentException("冷却与去重不能为空");
        }
        this.sessions = sessions;
        this.logs = logs;
        this.catalog = catalog;
        this.cards = cards;
        this.presenter = presenter;
        this.names = names;
        this.config = config;
        this.clock = clock;
        this.logger = logger;
        this.cooldowns = cooldowns;
        this.dedupe = dedupe;
        this.blacklist = blacklist;
        this.scriptAdmin = scriptAdmin;
    }

    /** 剧本管理三件套；未装配时为 null（script 库管理动作不可用）。 */
    public ScriptAdmin scriptAdmin() {
        return scriptAdmin;
    }

    /** 装配 AI 触发端口（v1.1）；未装配时为 null，所有触发静默跳过。 */
    public void bindAiTriggers(AiTriggerSink aiTriggers) {
        this.aiTriggers = aiTriggers;
    }

    /** AI 触发端口；未装配时为 null。 */
    public AiTriggerSink aiTriggers() {
        return aiTriggers;
    }

    /**
     * 事件落库后触发 AI 叙述；端口未装配或群号非法时静默跳过。
     *
     * @param trigger 触发类型
     * @param groupId 群号
     */
    public void fireAi(AiTrigger trigger, long groupId) {
        fireAi(trigger, groupId, 0L, "");
    }

    /** 带真实事件序号与文本的合并触发（§9.3）：玩家行动原文会进入提示词动态段。 */
    public void fireAi(AiTrigger trigger, long groupId, long eventSeq, String eventText) {
        if (aiTriggers != null) {
            aiTriggers.fire(trigger, groupId, eventSeq, eventText);
        }
    }

    /**
     * 立即触发 AI 叙述（覆盖可合并类型的默认窗口语义）。
     *
     * @param trigger 触发类型
     * @param groupId 群号
     */
    public void fireAiNow(AiTrigger trigger, long groupId) {
        if (aiTriggers != null) {
            aiTriggers.fireNow(trigger, groupId);
        }
    }

    /**
     * 通知 AI 端口本局开关已打开（清去重缓存）。
     *
     * @param sessionNo 局号
     */
    public void notifyAiEnabled(long sessionNo) {
        if (aiTriggers != null) {
            aiTriggers.onEnabled(sessionNo);
        }
    }

    /** 拉黑名单服务；未装配时为 null（黑名单指令不可用）。 */
    public BlacklistService blacklist() {
        return blacklist;
    }

    public SessionService sessions() {
        return sessions;
    }

    public SessionLogService logs() {
        return logs;
    }

    public ScriptCatalog catalog() {
        return catalog;
    }

    public CardGenerator cards() {
        return cards;
    }

    public TrpgPresenter presenter() {
        return presenter;
    }

    public TrpgNameResolver names() {
        return names;
    }

    public TrpgConfig config() {
        return config;
    }

    public Clock clock() {
        return clock;
    }

    /** 插件内冷却登记，剧本生成等动作在命令层按（动作, 用户, 群）判冷却。 */
    public CooldownRegistry cooldowns() {
        return cooldowns;
    }

    /** 回执统一经 Presenter：群内发群、私聊发私聊。 */
    public void reply(SessionActor actor, String message) {
        if (actor == null || message == null || message.trim().isEmpty()) {
            return;
        }
        if (actor.inGroup()) {
            presenter.sendGroup(actor.groupId(), message);
        } else {
            presenter.sendPrivate(actor.userId(), message);
        }
    }

    /**
     * 统一处理一个局面结果。
     *
     * @return 成功（或待确认）返回 true；已回执拒绝 / 失败返回 false
     */
    public boolean accept(SessionActor actor, SessionResult result) {
        if (result == null) {
            reply(actor, TrpgMessageTemplates.error("存储写入失败", RETRY_NEXT));
            return false;
        }
        if (result.replySuppressed()) {
            return false;
        }
        if (result.ok() || result.confirmationPending()) {
            return true;
        }
        if (result.rejected()) {
            SessionReject reject = result.reject();
            if (reject == SessionReject.COOLING) {
                reply(actor, TrpgMessageTemplates.notice(safeDetail(result, "还有 1 秒冷却")));
            } else {
                reply(actor, TrpgMessageTemplates.error(safeDetail(result, reject.label()),
                        nextHint(reject)));
            }
            return false;
        }
        reply(actor, TrpgMessageTemplates.error(safeDetail(result, "存储写入失败"), RETRY_NEXT));
        return false;
    }

    /** 指令层自身的错误回执：走与局面层同一个去重登记，10 秒内同键只回一次。 */
    public void rejectError(SessionActor actor, String key, String reason, String next) {
        if (claim(actor, key)) {
            reply(actor, TrpgMessageTemplates.error(reason, next));
        }
    }

    /** 指令层自身的提示回执：同样去重。 */
    public void rejectNotice(SessionActor actor, String key, String message) {
        if (claim(actor, key)) {
            reply(actor, TrpgMessageTemplates.notice(message));
        }
    }

    /** 冷却中则回执并返回 true；调用方直接结束。冷却按（动作, 用户, 群）计。 */
    public boolean cooling(SessionActor actor, String action, int seconds) {
        long now = clock.nowMillis();
        long remaining = cooldowns.remainingMillis(action, actor.userId(), actor.groupId(), now,
                seconds * MILLIS_PER_SECOND);
        if (remaining <= 0L) {
            return false;
        }
        long remainSeconds = Math.max(1L, (remaining + MILLIS_PER_SECOND - 1L) / MILLIS_PER_SECOND);
        reply(actor, TrpgMessageTemplates.notice("还有 " + remainSeconds + " 秒冷却"));
        return true;
    }

    /** 记录一次冷却起点。 */
    public void mark(SessionActor actor, String action) {
        cooldowns.mark(action, actor.userId(), actor.groupId(), clock.nowMillis());
    }

    /** 解析昵称，失败时用兜底名并告警。 */
    public String nameOf(long groupId, long userId) {
        try {
            return names.name(groupId, userId);
        } catch (RuntimeException error) {
            warn("解析昵称失败，使用兜底名：" + error.getMessage());
            return TrpgNameResolver.fallback(userId);
        }
    }

    /** 持剧本库读锁读取剧本，失败或缺失返回 null。 */
    public Script loadScript(String scriptId) {
        if (scriptId == null || scriptId.trim().isEmpty()) {
            return null;
        }
        java.util.concurrent.locks.Lock lock = catalog.acquireReadLock();
        lock.lock();
        try {
            return catalog.get(scriptId);
        } catch (RuntimeException error) {
            warn("读取剧本失败：" + scriptId + "，原因：" + error.getMessage());
            return null;
        } finally {
            lock.unlock();
        }
    }

    /** 私聊发送角色卡全文，失败时由 Presenter 按 §5.2 回退群内折叠。 */
    public void sendCardPrivately(SessionActor actor, long toUserId, String name, CardData card) {
        CardData actual = card == null ? CardData.empty() : card;
        presenter.sendPrivateCard(actor.groupId(), toUserId, name,
                TrpgMessageTemplates.card(name, actual),
                TrpgMessageTemplates.cardFolded(name, CardGenerator.professionLabel(actual), actual));
    }

    /** 把局面快照投影成状态回执视图。 */
    public TrpgStatusView statusView(SessionSnapshot snapshot) {
        if (snapshot == null || !snapshot.active()) {
            return TrpgStatusView.none();
        }
        Script script = loadScript(snapshot.scriptId());
        List<String> labels = new ArrayList<String>();
        int inside = 0;
        for (MemberView member : snapshot.members()) {
            if (!member.inSession()) {
                continue;
            }
            inside++;
            String label = nameOf(snapshot.groupId(), member.userId());
            labels.add(member.takenOver() ? label + "（代管中）" : label);
        }
        int sceneTotal = script == null ? 0 : script.scenes().size();
        int clueTotal = script == null ? 0 : script.clues().size();
        // v1 的 AI 契约桩不写 aiEnabled（§9.10），因此这一行在 v1 恒为「关」。
        String aiLine;
        if (!snapshot.aiEnabled()) {
            aiLine = "关";
        } else if (snapshot.aiSuspendedUntil() > clock.nowMillis()) {
            aiLine = "挂起至 " + TrpgMessageTemplates.dateTime(snapshot.aiSuspendedUntil());
        } else {
            aiLine = "开";
        }
        return TrpgStatusView.of(snapshot.state(),
                script == null ? snapshot.scriptId() : script.name(),
                nameOf(snapshot.groupId(), snapshot.kpUserId()),
                inside, script == null ? 0 : script.maxPlayers(), labels,
                snapshot.sceneTitle(), snapshot.sceneIndex() + 1, Math.max(sceneTotal, 1),
                snapshot.unlockedClues().size(), clueTotal, aiLine, snapshot.recovered(),
                snapshot.lastActivityAt());
    }

    /** 把局面快照投影成情景卡。 */
    public TrpgSceneCard sceneCard(SessionSnapshot snapshot, Script script) {
        Scene scene = script == null ? null : script.sceneAt(snapshot.sceneIndex());
        List<String> present = new ArrayList<String>();
        for (MemberView member : snapshot.members()) {
            if (member.inSession()) {
                present.add(nameOf(snapshot.groupId(), member.userId()));
            }
        }
        List<String> clues = new ArrayList<String>();
        for (String key : snapshot.unlockedClues()) {
            Clue clue = script == null ? null : script.clueById(key);
            clues.add(clue == null ? key : clue.name());
        }
        String title = scene == null ? snapshot.sceneTitle() : scene.title();
        String narration = scene == null ? "" : scene.narration();
        String fallback = scene == null ? "" : scene.fallbackNarration();
        return new TrpgSceneCard(title, snapshot.sceneIndex() + 1,
                script == null ? Math.max(1, snapshot.sceneIndex() + 1) : Math.max(1, script.scenes().size()),
                present, clues, narration, fallback, Collections.<String>emptyList());
    }

    /** 局内成员昵称（可排除 KP）。 */
    public List<String> playerNames(SessionSnapshot snapshot, long excludedUserId) {
        List<String> playerNames = new ArrayList<String>();
        for (MemberView member : snapshot.members()) {
            if (member.inSession() && member.userId() != excludedUserId) {
                playerNames.add(nameOf(snapshot.groupId(), member.userId()));
            }
        }
        return playerNames;
    }

    /** 局内在场人数。 */
    public static int insideCount(SessionSnapshot snapshot) {
        int count = 0;
        if (snapshot == null) {
            return 0;
        }
        for (MemberView member : snapshot.members()) {
            if (member.inSession()) {
                count++;
            }
        }
        return count;
    }

    /** 日志页行：时间 + 发言人 + 正文。 */
    public List<TrpgLogEntry> logEntries(SessionActor actor, List<LogRow> rows) {
        List<TrpgLogEntry> entries = new ArrayList<TrpgLogEntry>();
        for (LogRow row : rows) {
            String actorName = row.actorId() <= 0L ? "系统" : nameOf(actor.groupId(), row.actorId());
            entries.add(new TrpgLogEntry(row.createTime(), actorName, row.content()));
        }
        return entries;
    }

    /** 拒绝分类对应的下一步提示。 */
    public static String nextHint(SessionReject reject) {
        if (reject == null) {
            return "";
        }
        switch (reject) {
            case BAD_ARGUMENT:
                return HELP_NEXT;
            case NOT_IN_GROUP:
                return PRIVATE_NEXT;
            case NO_PERMISSION:
                return "发送 /trpg help 查看指令权限。";
            default:
                return "";
        }
    }

    /** 取结果里的说明，空则用兜底文案。 */
    public static String safeDetail(SessionResult result, String fallback) {
        String detail = result == null ? null : result.detail();
        return detail == null || detail.trim().isEmpty() ? fallback : detail;
    }

    /** 用分隔符拼接非空项。 */
    public static String join(List<String> items, String separator) {
        StringBuilder builder = new StringBuilder();
        for (String item : items) {
            if (item == null || item.trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(separator);
            }
            builder.append(item.trim());
        }
        return builder.toString();
    }

    /** 告警（logger 可为 null）。 */
    public void warn(String message) {
        if (logger != null) {
            logger.sendWarn("[MBB-Trpg] " + message);
        }
    }

    /** 同类错误去重：同一群同一键在窗口内只放行一次。 */
    private boolean claim(SessionActor actor, String key) {
        long groupId = actor.inGroup() ? actor.groupId() : SessionActor.PRIVATE_GROUP_ID;
        return dedupe.claim(groupId, key, clock.nowMillis(),
                config.errorDedupeSeconds() * MILLIS_PER_SECOND);
    }
}
