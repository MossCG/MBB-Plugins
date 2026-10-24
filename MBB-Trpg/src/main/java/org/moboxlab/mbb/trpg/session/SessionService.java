package org.moboxlab.mbb.trpg.session;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;

import org.moboxlab.mbb.trpg.ai.AiFailureOutcome;
import org.moboxlab.mbb.trpg.ai.AiFailureRecorder;
import org.moboxlab.mbb.trpg.ai.AiToggle;
import org.moboxlab.mbb.trpg.ai.SessionView;
import org.moboxlab.mbb.trpg.ai.TrpgAiHostService;
import org.moboxlab.mbb.trpg.config.TrpgConfig;
import org.moboxlab.mbb.trpg.rule.RandomSource;
import org.moboxlab.mbb.trpg.rule.RuleRegistry;
import org.moboxlab.mbb.trpg.rule.RuleSet;
import org.moboxlab.mbb.trpg.script.Script;
import org.moboxlab.mbb.trpg.script.ScriptCatalog;
import org.moboxlab.mbb.trpg.script.ScriptInfo;
import org.moboxlab.mbb.trpg.script.ScriptSource;
import org.moboxlab.mbb.trpg.script.ScriptUsageIndex;
import org.moboxlab.mbb.trpg.script.Scene;
import org.moboxlab.mbb.trpg.store.CardData;
import org.moboxlab.mbb.trpg.store.LogRow;
import org.moboxlab.mbb.trpg.store.MemberRow;
import org.moboxlab.mbb.trpg.store.SessionRow;
import org.moboxlab.mbb.trpg.store.SessionStore;

import org.moboxlab.moboxbot.API.PluginLogger;

/**
 * 局面的唯一写入口（规格 §5）：四态迁移、群锁、统一判定顺序、收尾编排与启动恢复。
 *
 * <p>判定顺序固定为「是否群内 → 权限 → 状态 → 参数 → 冷却 → 执行」；黑名单在消息入口最前端，
 * 不进入本类。惰性过期（T5 / T6）在读取状态的同一临界区内先执行，且不受请求者权限影响。
 * 所有写操作在群锁内完成，事件在解锁后统一发布（临界区不含网络发送）。</p>
 *
 * <p>场景、邀请、接管、跳过与检定类迁移由 S7 / S8 在此之上落地。AI 子状态由
 * {@link #aiState(SessionActor, String)} 入口经 CAS 落库（开关、挂起时间戳与失败计数三列）；
 * 真实调用、排队、合并窗口与审计在 {@link TrpgAiHostService} 与 AiHostEngine。</p>
 *
 * <p>本类同时实现 {@link SessionView}（{@link #readSnapshot(long)}），供 AI 主持取只读快照；
 * 实现 {@link ScriptUsageIndex} 判断剧本是否被未归档局引用；实现 {@link AiFailureRecorder}
 * 记录 AI 连续失败与挂起。</p>
 */
public final class SessionService implements SessionView, ScriptUsageIndex, AiFailureRecorder {

    private static final int MILLIS_PER_SECOND = 1000;
    private static final int ACTION_MAX_CHARS = 200;
    private static final String ACTION_START = "start";
    private static final String ACTION_JOIN = "join";
    private static final String ACTION_LEAVE = "leave";
    private static final String ACTION_BEGIN = "begin";
    private static final String ACTION_STATUS = "status";
    private static final String ACTION_AI = "ai";
    private static final String ACTION_END = "end";
    private static final String ACTION_RESUME = "resume";
    private static final String ACTION_KP_LEFT = "kp-left";
    private static final String ACTION_PLAYER_ACTION = "player-action";
    private static final String EVENT_CREATE = "create";
    private static final String EVENT_JOIN = "join";
    private static final String EVENT_LEAVE = "leave";
    private static final String EVENT_BEGIN = "begin";
    private static final String EVENT_RESUME = "resume";
    private static final String EVENT_PLAYER_ACTION = "action";

    private final SessionStore store;
    private final ScriptCatalog catalog;
    private final Clock clock;
    private final TrpgConfig config;
    private final SessionEventPublisher publisher;
    private final PluginLogger logger;
    private final GroupLockRegistry locks;
    private final CooldownRegistry cooldowns;
    private final ErrorDedupe dedupe;
    private final SessionArchiver archiver;
    private final SessionRecoveryService recovery;
    private final SessionReader reader;
    /** 角色卡指令（pc / pc reroll / pc set）。 */
    private final SessionCardService cardService;
    /** 局内推进（场景、检定、对抗、理智、调整、邀请、接管、跳过、换剧本）。 */
    private final SessionPlayService playService;
    /** 检定、对抗与理智（S8 拆出，避免单类继续膨胀）。 */
    private final SessionCheckService checkService;
    /** AI 主持契约桩（S9）：v1 只判定全局开关与 /trpg ai 分支，零调用、零审计、不落库。 */
    private final TrpgAiHostService aiHost;

    /** 启动后尚未确认恢复的局（内存事实，不落库）；只包含进行中的局。 */
    private final Set<Long> pendingRecovery = ConcurrentHashMap.newKeySet();
    /** 结束二次确认的时间戳，key = 群号:局号:发起者（不同局与不同发起者互不合并）。 */
    private final Map<String, Long> confirmations = new ConcurrentHashMap<String, Long>();

    public SessionService(SessionStore store, ScriptCatalog catalog, Clock clock, RandomSource random,
                          TrpgConfig config, SessionEventPublisher publisher, PluginLogger logger) {
        this(store, catalog, clock, random, config, publisher, logger,
                new GroupLockRegistry(clock), new CooldownRegistry(), new ErrorDedupe());
    }

    /**
     * 生产装配：与其它指令服务共用同一份群锁、冷却与去重登记。
     *
     * <p>共用是为了让「同群同类错误 10 秒只回一次」（规范 §3.2）与各指令的冷却窗口
     * 跨 /trpg 与 /r 一起生效；测试可注入假登记。</p>
     */
    public SessionService(SessionStore store, ScriptCatalog catalog, Clock clock, RandomSource random,
                          TrpgConfig config, SessionEventPublisher publisher, PluginLogger logger,
                          GroupLockRegistry locks, CooldownRegistry cooldowns, ErrorDedupe dedupe) {
        this(store, catalog, clock, random, config, publisher, logger, locks, cooldowns, dedupe, null);
    }

    /**
     * 生产装配：额外注入 AI 主持契约桩（S9）。
     *
     * <p>桩为 null 时按配置新建一个 v1 桩；桩不持有 AI / 审计 / 定时器端口，
     * 因此「零调用、零审计、无定时器」是结构性的（§9.10）。</p>
     */
    public SessionService(SessionStore store, ScriptCatalog catalog, Clock clock, RandomSource random,
                          TrpgConfig config, SessionEventPublisher publisher, PluginLogger logger,
                          GroupLockRegistry locks, CooldownRegistry cooldowns, ErrorDedupe dedupe,
                          TrpgAiHostService aiHost) {
        if (store == null) {
            throw new IllegalArgumentException("存储端口不能为空");
        }
        if (catalog == null) {
            throw new IllegalArgumentException("剧本库不能为空");
        }
        if (clock == null) {
            throw new IllegalArgumentException("时间端口不能为空");
        }
        if (random == null) {
            throw new IllegalArgumentException("随机源不能为空");
        }
        if (config == null) {
            throw new IllegalArgumentException("配置不能为空");
        }
        if (locks == null || cooldowns == null || dedupe == null) {
            throw new IllegalArgumentException("并发登记不能为空");
        }
        this.store = store;
        this.catalog = catalog;
        this.clock = clock;
        this.config = config;
        this.publisher = publisher == null ? SessionEventPublisher.NONE : publisher;
        this.logger = logger;
        this.locks = locks;
        this.cooldowns = cooldowns;
        this.dedupe = dedupe;
        this.archiver = new SessionArchiver(store, new GrowthSettler(store, random, logger),
                new SummaryResolver(catalog), logger);
        this.recovery = new SessionRecoveryService(store, archiver, locks, clock, publisher, logger,
                pendingRecovery);
        this.reader = new SessionReader(store, archiver, locks, clock, config, publisher, logger,
                this::isRecovered);
        this.cardService = new SessionCardService(this, store, cooldowns, config);
        this.playService = new SessionPlayService(this, store, catalog, config);
        this.checkService = new SessionCheckService(this, store, catalog, random, config, logger);
        this.aiHost = aiHost == null ? new TrpgAiHostService(config) : aiHost;
    }

    /** 请求者不能为空：与构造函数一样用 IllegalArgumentException 表达契约违例。 */
    static SessionActor requireActor(SessionActor actor) {
        if (actor == null) {
            throw new IllegalArgumentException("请求者不能为空");
        }
        return actor;
    }

    /** 查看状态：任意身份可执行；读到过期局时先惰性归档。 */
    public SessionResult status(SessionActor actor) {
        if (!requireActor(actor).inGroup()) {
            return SessionResult.rejected(SessionReject.NOT_IN_GROUP, "请在群内使用");
        }
        return withGroupLock(actor, ACTION_STATUS, new LockedAction() {

            @Override
            public SessionResult run(Turn turn) {
                SessionRow row = turn.active();
                if (row == null) {
                    return SessionResult.ok(SessionSnapshots.none(turn.groupId), "本群暂无局");
                }
                SessionResult cooling = cooling(ACTION_STATUS, turn, config.defaultCooldownSeconds());
                if (cooling != null) {
                    return cooling;
                }
                cooldowns.mark(ACTION_STATUS, actor.userId(), turn.groupId, turn.now);
                return SessionResult.ok(turn.snapshot(row), "");
            }
        });
    }

    /** 开团（T1）：建局并插入 KP 成员行。 */
    public SessionResult start(SessionActor actor, final String scriptId) {
        if (!requireActor(actor).inGroup()) {
            return SessionResult.rejected(SessionReject.NOT_IN_GROUP, "请在群内使用");
        }
        return withGroupLock(actor, ACTION_START, new LockedAction() {

            @Override
            public SessionResult run(Turn turn) {
                SessionRow active = turn.active();
                if (active != null) {
                    return reject(turn, SessionReject.STATE_UNAVAILABLE, "本群已有未归档的局");
                }
                String targetScript = scriptId == null ? "" : scriptId.trim();
                if (targetScript.isEmpty()) {
                    targetScript = defaultScriptId();
                }
                // 规格 §8.6：解析剧本与插入局面行期间必须持剧本库读锁，
                // 防止「解析通过 → 删除/替换落盘 → 插入指向已消失剧本的局」。
                long now = turn.now;
                SessionRow row = SessionRow.builder()
                        .groupId(turn.groupId)
                        .state(SessionState.RECRUITING)
                        .kpUserId(actor.userId())
                        .scriptId(targetScript)
                        .ruleSetId(resolveRuleSetId())
                        .lastActivityAt(now)
                        .createTime(now)
                        .updateTime(now)
                        .build();
                long sessionId;
                Lock readLock = catalog.acquireReadLock();
                readLock.lock();
                try {
                    if (loadScript(targetScript) == null) {
                        return reject(turn, SessionReject.BAD_ARGUMENT, "剧本不存在：" + targetScript);
                    }
                    SessionResult cooling = cooling(ACTION_START, turn, config.startCooldownSeconds());
                    if (cooling != null) {
                        return cooling;
                    }
                    sessionId = store.insertSession(row);
                    if (sessionId == 0L) {
                        if (store.findActiveByGroup(turn.groupId) != null) {
                            return reject(turn, SessionReject.STATE_UNAVAILABLE, "本群已有未归档的局");
                        }
                        error("建局写入未生效：group=" + turn.groupId);
                        return SessionResult.storageFailure("建局写入未生效，请稍后重试");
                    }
                } finally {
                    readLock.unlock();
                }
                MemberRow kp = MemberRow.builder()
                        .sessionId(sessionId)
                        .userId(actor.userId())
                        .card(CardData.empty())
                        .successSkills(new ArrayList<String>())
                        .joinedAt(now)
                        .createTime(now)
                        .updateTime(now)
                        .build();
                if (store.insertMember(kp) == 0L) {
                    int cancelled = store.casTransition(sessionId, SessionState.RECRUITING,
                            SessionState.ENDED, 0L, now, "cancelled", false, "", now);
                    error("建局成员写入未生效，已取消：session=" + sessionId + "，cas=" + cancelled);
                    if (cancelled < 0) {
                        warn("建局取消未生效，留给启动恢复按无成员归档：session=" + sessionId);
                    }
                    return SessionResult.storageFailure("建局成员写入未生效，已取消本局");
                }
                appendLog(sessionId, EVENT_CREATE, actor.userId(), targetScript, now);
                cooldowns.mark(ACTION_START, actor.userId(), turn.groupId, now);
                SessionRow created = store.findSession(sessionId);
                return SessionResult.ok(turn.snapshot(created == null ? rebuild(row, sessionId) : created),
                        "已创建招募中的局：" + targetScript);
            }
        });
    }

    /** 入局：招募期可加入；离场成员重进清离场标记，角色卡保留。 */
    public SessionResult join(SessionActor actor, final CardData card) {
        if (!requireActor(actor).inGroup()) {
            return SessionResult.rejected(SessionReject.NOT_IN_GROUP, "请在群内使用");
        }
        return withGroupLock(actor, ACTION_JOIN, new LockedAction() {

            @Override
            public SessionResult run(Turn turn) {
                SessionRow row = turn.active();
                if (row == null) {
                    return reject(turn, SessionReject.STATE_UNAVAILABLE, "本群暂无局");
                }
                if (row.state() != SessionState.RECRUITING) {
                    return reject(turn, SessionReject.STATE_UNAVAILABLE,
                            "本群已开局，进行中加入需由 KP 邀请");
                }
                Script script = loadScript(row.scriptId());
                if (script != null && insideCount(row, actor.userId()) >= script.maxPlayers()) {
                    return reject(turn, SessionReject.MEMBER_STATE,
                            "本局人数已满（" + script.maxPlayers() + " 人）");
                }
                MemberRow existing = turn.member(row, actor.userId());
                if (existing != null && existing.leftAt() <= 0L) {
                    return reject(turn, SessionReject.MEMBER_STATE, "你已在局内");
                }
                SessionResult cooling = cooling(ACTION_JOIN, turn, config.defaultCooldownSeconds());
                if (cooling != null) {
                    return cooling;
                }
                long now = turn.now;
                if (existing != null) {
                    // 重新入局同时清掉代管标记（§6.1）：代管只对被代管的本次在场有效。
                    int affected = store.updateMember(existing.toBuilder()
                            .leftAt(0L).takenOver(false).updateTime(now).build());
                    if (affected < 0) {
                        return SessionResult.storageFailure("重新入局写入未生效");
                    }
                    if (affected == 0) {
                        MemberRow again = turn.member(row, actor.userId());
                        if (again == null || again.leftAt() > 0L) {
                            return SessionResult.storageFailure("重新入局写入未生效");
                        }
                    }
                    appendLog(row.id(), EVENT_JOIN, actor.userId(), "", now);
                    cooldowns.mark(ACTION_JOIN, actor.userId(), turn.groupId, now);
                    return SessionResult.ok(turn.reload(row), "已重新入局，角色卡保留");
                }
                if (actor.userId() == row.kpUserId()) {
                    return reject(turn, SessionReject.MEMBER_STATE, "你已是本局 KP");
                }
                CardData actual = card == null ? CardData.empty() : card;
                MemberRow member = MemberRow.builder()
                        .sessionId(row.id())
                        .userId(actor.userId())
                        .card(actual)
                        .successSkills(new ArrayList<String>())
                        .joinedAt(now)
                        .createTime(now)
                        .updateTime(now)
                        .build();
                if (store.insertMember(member) == 0L) {
                    return SessionResult.storageFailure("入局写入未生效，请稍后重试");
                }
                appendLog(row.id(), EVENT_JOIN, actor.userId(), "", now);
                cooldowns.mark(ACTION_JOIN, actor.userId(), turn.groupId, now);
                return SessionResult.ok(turn.reload(row), "已入局");
            }
        });
    }

    /** 离场：保留角色卡与局籍；KP 不能离场。 */
    public SessionResult leave(SessionActor actor) {
        if (!requireActor(actor).inGroup()) {
            return SessionResult.rejected(SessionReject.NOT_IN_GROUP, "请在群内使用");
        }
        return withGroupLock(actor, ACTION_LEAVE, new LockedAction() {

            @Override
            public SessionResult run(Turn turn) {
                SessionRow row = turn.active();
                if (row == null) {
                    return reject(turn, SessionReject.STATE_UNAVAILABLE, "本群暂无局");
                }
                MemberRow member = turn.member(row, actor.userId());
                if (member == null || member.leftAt() > 0L) {
                    return reject(turn, SessionReject.MEMBER_STATE, "你不在局内");
                }
                if (actor.userId() == row.kpUserId()) {
                    return reject(turn, SessionReject.MEMBER_STATE,
                            "KP 不能离场，请结束本局或交接后再离开");
                }
                SessionResult cooling = cooling(ACTION_LEAVE, turn, config.defaultCooldownSeconds());
                if (cooling != null) {
                    return cooling;
                }
                long now = turn.now;
                int affected = store.updateMember(member.toBuilder().leftAt(now).updateTime(now).build());
                if (affected < 0) {
                    return SessionResult.storageFailure("离场写入未生效");
                }
                if (affected == 0) {
                    MemberRow again = turn.member(row, actor.userId());
                    if (again != null && again.leftAt() <= 0L) {
                        return SessionResult.storageFailure("离场写入未生效");
                    }
                }
                appendLog(row.id(), EVENT_LEAVE, actor.userId(), "", now);
                cooldowns.mark(ACTION_LEAVE, actor.userId(), turn.groupId, now);
                return SessionResult.ok(turn.reload(row), "已离场，角色卡保留");
            }
        });
    }

    /** 开局（T2）：仅 KP；除 KP 外至少一名局内成员；剧本存在；切到剧本开场场景。 */
    public SessionResult begin(SessionActor actor) {
        if (!requireActor(actor).inGroup()) {
            return SessionResult.rejected(SessionReject.NOT_IN_GROUP, "请在群内使用");
        }
        return withGroupLock(actor, ACTION_BEGIN, new LockedAction() {

            @Override
            public SessionResult run(Turn turn) {
                SessionRow row = turn.active();
                if (row == null) {
                    return reject(turn, SessionReject.STATE_UNAVAILABLE, "本群暂无局");
                }
                if (actor.userId() != row.kpUserId()) {
                    return reject(turn, SessionReject.NO_PERMISSION, "只有 KP 能开局");
                }
                if (row.state() != SessionState.RECRUITING) {
                    return reject(turn, SessionReject.STATE_UNAVAILABLE, "本局不在招募阶段");
                }
                List<MemberRow> members = store.loadMembers(row.id());
                if (insideCountExcluding(members, row.kpUserId()) < 1) {
                    return reject(turn, SessionReject.STATE_UNAVAILABLE,
                            "还需要至少一名其他成员才能开局");
                }
                Script script = loadScript(row.scriptId());
                if (script == null) {
                    return reject(turn, SessionReject.STATE_UNAVAILABLE, "剧本不可用：" + row.scriptId());
                }
                SessionResult cooling = cooling(ACTION_BEGIN, turn, config.defaultCooldownSeconds());
                if (cooling != null) {
                    return cooling;
                }
                long now = turn.now;
                int cas = store.casTransition(row.id(), SessionState.RECRUITING, SessionState.RUNNING,
                        now, 0L, "", false, "", now);
                if (cas < 0) {
                    return SessionResult.storageFailure("开局写入未生效，请稍后重试");
                }
                if (cas == 0) {
                    SessionRow again = store.findSession(row.id());
                    if (again != null && again.state() != SessionState.RECRUITING) {
                        return reject(turn, SessionReject.STATE_UNAVAILABLE, "本局不在招募阶段");
                    }
                    return SessionResult.storageFailure("开局写入未生效，请稍后重试");
                }
                Scene opening = script.sceneAt(0);
                if (store.casScene(row.id(), SessionState.RUNNING, 0,
                        opening == null ? "" : opening.title(), now) < 0) {
                    warn("开局场景写入未生效：session=" + row.id());
                }
                appendLog(row.id(), EVENT_BEGIN, actor.userId(), "", now);
                cooldowns.mark(ACTION_BEGIN, actor.userId(), turn.groupId, now);
                return SessionResult.ok(turn.reload(row), "本局已开始");
            }
        });
    }

    /** 结束（T3 / T4）：KP 或管理员，同一发起者 30 秒内二次执行生效。 */
    public SessionResult end(SessionActor actor) {
        if (!requireActor(actor).inGroup()) {
            return SessionResult.rejected(SessionReject.NOT_IN_GROUP, "请在群内使用");
        }
        return withGroupLock(actor, ACTION_END, new LockedAction() {

            @Override
            public SessionResult run(Turn turn) {
                SessionRow row = turn.active();
                if (row == null) {
                    return reject(turn, SessionReject.STATE_UNAVAILABLE, "本群暂无局");
                }
                boolean kpActor = actor.userId() == row.kpUserId();
                if (!kpActor && !actor.groupAdmin()) {
                    return reject(turn, SessionReject.NO_PERMISSION, "只有 KP 或群管理员能结束本局");
                }
                SessionResult cooling = cooling(ACTION_END, turn, config.defaultCooldownSeconds());
                if (cooling != null) {
                    return cooling;
                }
                long window = config.confirmWindowSeconds() * (long) MILLIS_PER_SECOND;
                String key = turn.groupId + ":" + row.id() + ":" + actor.userId();
                Long first = confirmations.get(key);
                if (first == null || turn.now - first.longValue() > window) {
                    confirmations.put(key, Long.valueOf(turn.now));
                    // 首次确认也计一次冷却，避免 1 秒内连发两次 end 直接生效（矩阵 end 1s）。
                    cooldowns.mark(ACTION_END, actor.userId(), turn.groupId, turn.now);
                    return SessionResult.awaitingConfirmation(turn.snapshot(row),
                            "结束本局需要确认：请在 " + config.confirmWindowSeconds() + " 秒内再次执行");
                }
                confirmations.remove(key);
                String reason;
                if (!kpActor) {
                    reason = "forced";
                } else {
                    reason = row.state() == SessionState.RUNNING ? "finished" : "cancelled";
                }
                boolean settleGrowth = row.state() == SessionState.RUNNING;
                ArchiveResult archived = archiver.archive(row, reason, settleGrowth, turn.now);
                if (archived.status() == ArchiveResult.Status.FAILED) {
                    confirmations.put(key, Long.valueOf(turn.now));
                    return finish(turn, archived, "本局已结束");
                }
                cooldowns.mark(ACTION_END, actor.userId(), turn.groupId, turn.now);
                return finish(turn, archived, "本局已结束");
            }
        });
    }

    /** 恢复确认（T8）：仅 KP，且只在本机重启后未确认时生效。 */
    public SessionResult resume(SessionActor actor) {
        if (!requireActor(actor).inGroup()) {
            return SessionResult.rejected(SessionReject.NOT_IN_GROUP, "请在群内使用");
        }
        return withGroupLock(actor, ACTION_RESUME, new LockedAction() {

            @Override
            public SessionResult run(Turn turn) {
                SessionRow row = turn.active();
                if (row == null) {
                    return reject(turn, SessionReject.STATE_UNAVAILABLE, "本群暂无局");
                }
                if (actor.userId() != row.kpUserId()) {
                    return reject(turn, SessionReject.NO_PERMISSION, "只有 KP 能确认恢复");
                }
                if (!pendingRecovery.contains(Long.valueOf(row.id()))) {
                    return reject(turn, SessionReject.STATE_UNAVAILABLE, "本局无需恢复确认");
                }
                SessionResult cooling = cooling(ACTION_RESUME, turn, config.defaultCooldownSeconds());
                if (cooling != null) {
                    return cooling;
                }
                pendingRecovery.remove(Long.valueOf(row.id()));
                appendLog(row.id(), EVENT_RESUME, actor.userId(), "", turn.now);
                cooldowns.mark(ACTION_RESUME, actor.userId(), turn.groupId, turn.now);
                return SessionResult.ok(turn.reload(row), "已确认恢复，可以继续");
            }
        });
    }

    /**
     * {@code /trpg ai on|off}（§6.1 / §6.3 / §9.2）：KP 可开可关，群管理员只能关，
     * 机器人管理员可开可关。
     *
     * <p>自 v1.1 起开关经 {@code casAiState} 落库（§9.2）：{@code on} 需全局已启用，
     * 同时清除挂起时间戳与失败计数；{@code off} 写 {@code aiEnabled=false} 并清零挂起，
     * 使挂起自然失效。参数解析在权限与状态判定之后，保持 §5.5 的判定顺序。</p>
     */
    public SessionResult aiState(SessionActor actor, final String rawWord) {
        if (!requireActor(actor).inGroup()) {
            return SessionResult.rejected(SessionReject.NOT_IN_GROUP, "请在群内使用");
        }
        return withGroupLock(actor, ACTION_AI, new LockedAction() {

            @Override
            public SessionResult run(Turn turn) {
                SessionRow row = turn.active();
                if (row == null) {
                    return reject(turn, SessionReject.STATE_UNAVAILABLE, "本群暂无局");
                }
                boolean kpActor = actor.userId() == row.kpUserId();
                Boolean parsed = TrpgAiHostService.parseWord(rawWord);
                if (parsed == null) {
                    if (!kpActor && !actor.botAdmin() && !actor.groupAdmin()) {
                        return reject(turn, SessionReject.NO_PERMISSION,
                                "只有 KP 或群管理员能调整 AI");
                    }
                    return reject(turn, SessionReject.BAD_ARGUMENT,
                            "参数不对：AI 主持只支持 on 或 off");
                }
                boolean enabling = parsed.booleanValue();
                boolean allowed = kpActor || actor.botAdmin() || (!enabling && actor.groupAdmin());
                if (!allowed) {
                    return reject(turn, SessionReject.NO_PERMISSION,
                            enabling ? "只有 KP 能开启 AI" : "只有 KP 或群管理员能关闭 AI");
                }
                if (row.state() != SessionState.RECRUITING && row.state() != SessionState.RUNNING) {
                    return reject(turn, SessionReject.STATE_UNAVAILABLE, "本局不在可调 AI 的状态");
                }
                SessionResult cooling = cooling(ACTION_AI, turn, config.defaultCooldownSeconds());
                if (cooling != null) {
                    return cooling;
                }
                AiToggle toggle = aiHost.toggle(rawWord);
                if (toggle == AiToggle.USAGE) {
                    return reject(turn, SessionReject.BAD_ARGUMENT,
                            "参数不对：AI 主持只支持 on 或 off");
                }
                if (!enabling) {
                    return switchAiOff(turn, row);
                }
                if (!aiHost.globallyEnabled()) {
                    return reject(turn, SessionReject.STATE_UNAVAILABLE,
                            TrpgAiHostService.DETAIL_GLOBAL_OFF);
                }
                boolean resumed = row.aiSuspendedUntil() > turn.now;
                int affected = store.casAiState(row.id(), row.state(), true, 0L, 0, turn.now);
                if (affected != 1) {
                    return reject(turn, SessionReject.STATE_UNAVAILABLE,
                            TrpgAiHostService.DETAIL_WRITE_FAILED);
                }
                cooldowns.mark(ACTION_AI, actor.userId(), turn.groupId, turn.now);
                return SessionResult.ok(turn.reload(row),
                        resumed ? TrpgAiHostService.DETAIL_RESUMED
                                : TrpgAiHostService.DETAIL_ENABLED);
            }
        });
    }

    /** 关闭 AI：写 {@code aiEnabled=false} 并清零挂起与失败计数（挂起自然失效）。 */
    private SessionResult switchAiOff(Turn turn, SessionRow row) {
        boolean alreadyOff = !row.aiEnabled() && row.aiSuspendedUntil() == 0L
                && row.aiFailStreak() == 0;
        if (alreadyOff) {
            cooldowns.mark(ACTION_AI, turn.actor.userId(), turn.groupId, turn.now);
            return SessionResult.ok(turn.snapshot(row), TrpgAiHostService.DETAIL_ALREADY_OFF);
        }
        int affected = store.casAiState(row.id(), row.state(), false, 0L, 0, turn.now);
        if (affected != 1) {
            return reject(turn, SessionReject.STATE_UNAVAILABLE,
                    TrpgAiHostService.DETAIL_WRITE_FAILED);
        }
        cooldowns.mark(ACTION_AI, turn.actor.userId(), turn.groupId, turn.now);
        return SessionResult.ok(turn.reload(row), TrpgAiHostService.DETAIL_OFF);
    }

    /** 局内成员自由文本行动（v1.1 §9.4）：只记日志并刷新活动时间，不推进回合。 */
    public SessionResult playerAction(SessionActor actor, final String text) {
        if (!requireActor(actor).inGroup()) {
            return SessionResult.rejected(SessionReject.NOT_IN_GROUP, "请在群内使用");
        }
        String raw = text == null ? "" : text.trim();
        if (raw.isEmpty()) {
            return SessionResult.rejected(SessionReject.BAD_ARGUMENT, "行动内容不能为空");
        }
        final String content = truncateByCodePoint(raw, ACTION_MAX_CHARS);
        return withGroupLock(actor, ACTION_PLAYER_ACTION, new LockedAction() {

            @Override
            public SessionResult run(Turn turn) {
                SessionRow row = turn.active();
                if (row == null) {
                    return reject(turn, SessionReject.STATE_UNAVAILABLE, "本群暂无局");
                }
                if (row.state() != SessionState.RUNNING) {
                    return reject(turn, SessionReject.STATE_UNAVAILABLE, "本局不在进行中");
                }
                if (!turn.snapshot(row).insideMember(actor.userId())) {
                    return reject(turn, SessionReject.NO_PERMISSION, "只有局内成员可以行动");
                }
                appendLog(row.id(), EVENT_PLAYER_ACTION, actor.userId(), content, turn.now);
                reader.touchActivity(turn.groupId, actor.userId());
                return SessionResult.ok(turn.reload(row), "");
            }
        });
    }

    /** 记录一次 AI 失败（§9.5 / §9.6）：群锁内读活跃局、累加失败计数，达到阈值即写挂起时间戳。
     *
     * <p>不计失败的情形（授权关闭、无进行中局、群锁被占、CAS 未生效）返回
     * {@link AiFailureOutcome#unavailable()}，调用方据此不发挂起提示。</p>
     */
    public AiFailureOutcome recordFailure(long groupId) {
        if (groupId <= 0L) {
            return AiFailureOutcome.unavailable();
        }
        if (!locks.tryLock(groupId)) {
            return AiFailureOutcome.unavailable();
        }
        try {
            long now = clock.nowMillis();
            SessionRow row = store.findActiveByGroup(groupId);
            if (row == null || row.state() != SessionState.RUNNING || !row.aiEnabled()) {
                return AiFailureOutcome.unavailable();
            }
            int streak = row.aiFailStreak() + 1;
            boolean suspend = streak >= config.aiSuspendFailStreak();
            long suspendedUntil = suspend
                    ? now + config.aiSuspendMinutes() * (long) MILLIS_PER_SECOND * 60L
                    : row.aiSuspendedUntil();
            int nextStreak = suspend ? 0 : streak;
            int affected = store.casAiState(row.id(), row.state(), true, suspendedUntil, nextStreak,
                    now);
            if (affected != 1) {
                warn("AI 失败计数未生效：session=" + row.id() + "，affected=" + affected);
                return AiFailureOutcome.unavailable();
            }
            return AiFailureOutcome.of(row.id(), suspend, suspendedUntil, nextStreak);
        } finally {
            locks.unlock(groupId);
        }
    }

    /** 记录一次 AI 成功（§9.5）：群锁内把连续失败计数清零，挂起时间戳保持不变。 */
    public boolean recordSuccess(long groupId) {
        if (groupId <= 0L) {
            return false;
        }
        if (!locks.tryLock(groupId)) {
            return false;
        }
        try {
            long now = clock.nowMillis();
            SessionRow row = store.findActiveByGroup(groupId);
            if (row == null || row.state() != SessionState.RUNNING) {
                return false;
            }
            int affected = store.casAiState(row.id(), row.state(), row.aiEnabled(),
                    row.aiSuspendedUntil(), 0, now);
            if (affected != 1) {
                warn("AI 成功计数未生效：session=" + row.id() + "，affected=" + affected);
                return false;
            }
            return true;
        } finally {
            locks.unlock(groupId);
        }
    }

    /** KP 离群（T7）：招募期按 cancelled、进行中按 kp_left 归档。 */
    public SessionResult kpLeft(final long groupId, final long kpUserId) {
        if (groupId <= 0L || kpUserId <= 0L) {
            return SessionResult.rejected(SessionReject.BAD_ARGUMENT, "群号与用户号必须大于 0");
        }
        SessionActor actor = new SessionActor(kpUserId, groupId, false);
        return withGroupLock(actor, ACTION_KP_LEFT, new LockedAction() {

            @Override
            public SessionResult run(Turn turn) {
                SessionRow row = turn.active();
                if (row == null) {
                    return SessionResult.ok(SessionSnapshots.none(groupId), "本群暂无局");
                }
                if (row.kpUserId() != kpUserId) {
                    return SessionResult.ok(turn.snapshot(row), "KP 未变更");
                }
                boolean settleGrowth = row.state() == SessionState.RUNNING;
                // 招募期离群按取消归档、进行中才记 kp_left（§7.7 endReason 口径）。
                String endReason = settleGrowth ? END_REASON_KP_LEFT : END_REASON_CANCELLED;
                return finish(turn, archiver.archive(row, endReason, settleGrowth, turn.now),
                        "KP 已离群");
            }
        });
    }

    /**
     * 角色卡指令的前置放行判定：只判群内、局面存在、请求者在本局、非 KP。
     *
     * <p>供指令层在回报「参数不对」之前调用，保证 §5.5 的判定顺序（状态与权限先于参数）；
     * 放行时不执行动作，因此不写冷却。</p>
     */
    public SessionResult cardGate(SessionActor actor) {
        return cardGate(actor, false);
    }

    /**
     * 角色卡动作的前置放行判定：在 {@link #cardGate(SessionActor)} 之上再拒掉 KP。
     *
     * <p>pc reroll / pc set 对 KP 属于成员状态不满足，必须排在参数错误之前（§5.5）。</p>
     */
    public SessionResult cardActionGate(SessionActor actor) {
        return cardGate(actor, true);
    }

    private SessionResult cardGate(SessionActor actor, boolean rejectKp) {
        if (!requireActor(actor).inGroup()) {
            return SessionResult.rejected(SessionReject.NOT_IN_GROUP, "请在群内使用");
        }
        return cardService.gate(actor, rejectKp);
    }

    /** 查看角色卡（§6.2）：本局成员可看自己（含离场），KP 可看任意成员。 */
    public SessionResult viewCard(SessionActor actor, long targetUserId) {
        return cardService.viewCard(actor, targetUserId);
    }

    /**
     * 重掷角色卡（§6.1 pc reroll）：仅招募期、玩家本人；新卡由指令层生成后传入。
     *
     * <p>按 §7.5，重掷把 SAN 重置为 POW 属于生成侧职责，本方法只整卡替换并落库。</p>
     */
    public SessionResult rerollCard(SessionActor actor, CardData card) {
        return cardService.rerollCard(actor, card);
    }

    /**
     * 微调角色卡（§6.1 pc set）：仅招募期、玩家本人；只接受八属性、每项 15–90 的整数。
     *
     * <p>改 POW 不联动 SAN；SAN / HP / MP 与技能、物品都不在这里改（§7.5）。</p>
     */
    public SessionResult setAttribute(SessionActor actor, String field, String rawValue) {
        return cardService.setAttribute(actor, field, rawValue);
    }

    /** 推进场景（§6.1 scene）：仅 KP、仅进行中，同时解锁本场景尚未解锁的线索。 */
    public PlayResult<SessionOutcomes.Scene> scene(SessionActor actor, String title) {
        return playService.scene(actor, title);
    }

    /** 技能检定（§6.1 check）：本人或 KP 代掷，成功技能记入成长集合。 */
    public PlayResult<SessionOutcomes.Check> check(SessionActor actor, long targetUserId,
                                                  String skill, String diceKind, String diceCount) {
        return checkService.check(actor, targetUserId, skill, diceKind, diceCount);
    }

    /** 对抗检定（§6.1 opposed）：右侧可以是玩家技能或 0～99 的目标值。 */
    public PlayResult<SessionOutcomes.Opposed> opposed(SessionActor actor, long leftUserId,
                                                      String leftSkill, long rightUserId,
                                                      String rightSkill, int rightValue) {
        return checkService.opposed(actor, leftUserId, leftSkill, rightUserId, rightSkill, rightValue);
    }

    /** 理智检定（§6.1 san）：仅 KP、仅进行中；损失达阈值追 INT 检定。 */
    public PlayResult<SessionOutcomes.San> san(SessionActor actor, long targetUserId, String lossSpec) {
        return checkService.san(actor, targetUserId, lossSpec);
    }

    /** 技能调整（§6.1 adjust）：仅 KP，招募期与进行中都可执行。 */
    public PlayResult<SessionOutcomes.Adjust> adjust(SessionActor actor, long targetUserId,
                                                    String skill, String delta) {
        return playService.adjust(actor, targetUserId, skill, delta);
    }

    /** 进行中邀请入局（§6.1 invite）：仅 KP；离场成员重进清离场与代管标记。 */
    public SessionResult invite(SessionActor actor, long targetUserId, CardData card) {
        return playService.invite(actor, targetUserId, card);
    }

    /** KP 代管（§6.1 take）：标记由 KP 代掷，目标重新加入时清除。 */
    public SessionResult takeover(SessionActor actor, long targetUserId) {
        return playService.takeover(actor, targetUserId);
    }

    /** 跳过（§6.1 skip）：只写日志，不改角色卡。 */
    public SessionResult skip(SessionActor actor, long targetUserId, String reason) {
        return playService.skip(actor, targetUserId, reason);
    }

    /** 换本局剧本（§6.1 script）：仅 KP、仅招募期。 */
    public SessionResult changeScript(SessionActor actor, String scriptId) {
        return playService.changeScript(actor, scriptId);
    }

    /**
     * 该剧本是否被未归档局引用（§8.6 删除/替换前的保护）。
     *
     * <p>以库为准查询未归档局，不依赖内存缓存，因此重启恢复的局也能挡住删除；
     * 查询失败时按「被引用」处理——宁可拒绝删除，也不让进行中的局丢剧本。</p>
     */
    @Override
    public boolean isInUse(String scriptId) {
        if (scriptId == null || scriptId.trim().isEmpty()) {
            return false;
        }
        List<SessionRow> rows;
        try {
            rows = store.loadActiveSessions();
        } catch (RuntimeException error) {
            warn("引用检查查询失败，按被引用处理：剧本=" + scriptId + "，原因=" + error.getMessage());
            return true;
        }
        if (rows == null) {
            return false;
        }
        for (int i = 0; i < rows.size(); i++) {
            SessionRow row = rows.get(i);
            if (row == null || !scriptId.equals(row.scriptId())) {
                continue;
            }
            if (row.state() == SessionState.RECRUITING || row.state() == SessionState.RUNNING) {
                return true;
            }
        }
        return false;
    }

    /** 读取本群当前局面；无活跃局返回空快照（读状态会先惰性归档过期局）。 */
    public SessionSnapshot readSnapshot(long groupId) {
        return reader.readSnapshot(groupId);
    }

    /** 刷新局内活动（§5.8），返回是否真正写了活动时间。 */
    public boolean touchActivity(long groupId, long userId) {
        return reader.touchActivity(groupId, userId);
    }

    /** 启动恢复（§5.7）：加载未归档局并重建内存局面，返回已加载快照。 */
    public List<SessionSnapshot> recover() {
        return recovery.recover();
    }

    /** 启动补做（§5.3 / §5.7）：对未结算的 ENDED 局补做成长与摘要，返回成功局数。 */
    public int settlePending() {
        return recovery.settlePending();
    }

    /** 执行一段需要群锁的动作。 */
    SessionResult withGroupLock(SessionActor actor, String action, LockedAction body) {
        long groupId = actor.groupId();
        long now = clock.nowMillis();
        if (!locks.tryLock(groupId)) {
            return rejectRaw(groupId, SessionReject.BUSY, "本群正在处理上一条", now);
        }
        Turn turn = new Turn(actor, now);
        SessionResult result;
        try {
            result = body.run(turn);
        } catch (StorageAbort abort) {
            warn("局面操作中止：" + abort.getMessage());
            result = SessionResult.storageFailure(abort.getMessage());
        } catch (RuntimeException error) {
            error("局面操作异常：" + action + "：group=" + groupId + "，原因=" + error);
            result = SessionResult.storageFailure("局面操作异常，请稍后重试");
        } finally {
            locks.unlock(groupId);
        }
        for (SessionEvent event : turn.events) {
            publish(event);
        }
        return result;
    }

    /**
     * 与 {@link #withGroupLock} 同构，但返回带明细的 {@link PlayResult}。
     *
     * <p>判定顺序、群锁生命周期与事件发布时机完全一致：明细在锁内算好，事件在解锁后发布。</p>
     */
    <T> PlayResult<T> withGroupLockValue(SessionActor actor, String action, LockedOutcome<T> body) {
        long groupId = actor.groupId();
        long now = clock.nowMillis();
        if (!locks.tryLock(groupId)) {
            return PlayResult.rejected(rejectRaw(groupId, SessionReject.BUSY, "本群正在处理上一条", now));
        }
        Turn turn = new Turn(actor, now);
        PlayResult<T> result;
        try {
            result = body.run(turn);
        } catch (StorageAbort abort) {
            warn("局面操作中止：" + abort.getMessage());
            result = PlayResult.rejected(SessionResult.storageFailure(abort.getMessage()));
        } catch (RuntimeException error) {
            error("局面操作异常：" + action + "：group=" + groupId + "，原因=" + error);
            result = PlayResult.rejected(SessionResult.storageFailure("局面操作异常，请稍后重试"));
        } finally {
            locks.unlock(groupId);
        }
        for (SessionEvent event : turn.events) {
            publish(event);
        }
        if (result == null) {
            return PlayResult.rejected(SessionResult.storageFailure("局面操作未返回结果，请稍后重试"));
        }
        return result;
    }

    /** 成功执行后登记冷却（按动作、请求者与群号）。 */
    void markCooldown(String action, Turn turn) {
        cooldowns.mark(action, turn.actor.userId(), turn.groupId, turn.now);
    }

    /** 收尾结果转成对外结果，并登记战报事件。 */
    private SessionResult finish(Turn turn, ArchiveResult archived, String detail) {
        if (archived.status() == ArchiveResult.Status.FAILED) {
            return SessionResult.storageFailure("本局收尾未生效，请稍后重试");
        }
        turn.events.add(SessionEvent.ended(archived.snapshot(), archived.endReason(), archived.growth()));
        if (archived.status() == ArchiveResult.Status.DEFERRED) {
            warn("本局已结束但结算未完成，待启动补做：" + archived.detail());
        }
        return SessionResult.ended(archived.snapshot(), archived.endReason(), archived.growth());
    }

    SessionResult cooling(String action, Turn turn, int cooldownSeconds) {
        long remaining = cooldowns.remainingMillis(action, turn.actor.userId(), turn.groupId,
                turn.now, cooldownSeconds * (long) MILLIS_PER_SECOND);
        if (remaining <= 0L) {
            return null;
        }
        long seconds = (remaining + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND;
        return reject(turn, SessionReject.COOLING, "还有 " + seconds + " 秒冷却");
    }

    SessionResult reject(Turn turn, SessionReject reject, String detail) {
        return rejectRaw(turn.groupId, reject, detail, turn.now);
    }

    private SessionResult rejectRaw(long groupId, SessionReject reject, String detail, long now) {
        boolean claimed = dedupe.claim(groupId, reject, now,
                config.errorDedupeSeconds() * (long) MILLIS_PER_SECOND);
        return SessionResult.rejected(reject, detail).withSuppressed(!claimed);
    }

    private void publish(SessionEvent event) {
        try {
            publisher.publish(event);
        } catch (RuntimeException error) {
            error("局面事件发布失败：type=" + event.type() + "，原因=" + error);
        }
    }

    private String resolveRuleSetId() {
        RuleSet ruleSet = RuleRegistry.getOrDefault(config.ruleSetId());
        return ruleSet == null ? RuleRegistry.BUILTIN_RULE_SET_ID : ruleSet.id();
    }

    /** 读取剧本（持剧本库读锁）；未知返回 null。 */
    Script loadScript(String scriptId) {
        if (scriptId == null || scriptId.isEmpty()) {
            return null;
        }
        Lock readLock = catalog.acquireReadLock();
        readLock.lock();
        try {
            return catalog.get(scriptId);
        } finally {
            readLock.unlock();
        }
    }

    private String defaultScriptId() {
        Lock readLock = catalog.acquireReadLock();
        readLock.lock();
        try {
            List<ScriptInfo> infos = catalog.list();
            if (infos == null || infos.isEmpty()) {
                return "";
            }
            for (ScriptInfo info : infos) {
                if (info.source() == ScriptSource.BUILTIN) {
                    return info.id();
                }
            }
            return infos.get(0).id();
        } finally {
            readLock.unlock();
        }
    }

    private boolean isRecovered(long sessionId) {
        return !pendingRecovery.contains(Long.valueOf(sessionId));
    }

    /** 追加一条事件日志（seq 在群锁内取 MAX(seq)+1，写失败只告警不阻断）。 */
    void appendLog(long sessionId, String eventType, long actorId, String content, long now) {
        long seq = store.maxLogSeq(sessionId) + 1L;
        int affected = store.appendLog(new LogRow(0L, sessionId, seq, eventType, actorId, content, now));
        if (affected != 1) {
            warn("事件日志写入未生效：session=" + sessionId + "，seq=" + seq + "，type=" + eventType);
        }
    }

    private static MemberRow findMember(List<MemberRow> members, long userId) {
        for (MemberRow member : members) {
            if (member != null && member.userId() == userId) {
                return member;
            }
        }
        return null;
    }

    /** 本局在场人数（可排除某人，供人数上限与回执使用）。 */
    int insideCount(SessionRow row, long excludedUserId) {
        if (row == null) {
            return 0;
        }
        return insideCountExcluding(store.loadMembers(row.id()), excludedUserId);
    }

    private static int insideCountExcluding(List<MemberRow> members, long excludedUserId) {
        int count = 0;
        for (MemberRow member : members) {
            if (member != null && member.userId() != excludedUserId && member.leftAt() <= 0L) {
                count++;
            }
        }
        return count;
    }

    private static SessionRow rebuild(SessionRow row, long sessionId) {
        return row.toBuilder().id(sessionId).build();
    }

    private void warn(String message) {
        if (logger != null) {
            logger.sendWarn("[MBB-Trpg] " + message);
        }
    }

    private void error(String message) {
        if (logger != null) {
            logger.sendError("[MBB-Trpg] " + message);
        }
    }

    /** 进行中 KP 离群的归档原因。 */
    static final String END_REASON_KP_LEFT = "kp_left";

    /** 招募期取消的归档原因。 */
    static final String END_REASON_CANCELLED = "cancelled";

    /** 群锁内执行的动作。 */
    interface LockedAction {

        SessionResult run(Turn turn);
    }

    /** 群锁内执行并返回明细的动作。 */
    interface LockedOutcome<T> {

        PlayResult<T> run(Turn turn);
    }

    /** 一次请求的群锁上下文：持有当次时间、请求者与待发布事件。 */
    final class Turn {

        final SessionActor actor;
        final long now;
        final long groupId;
        final List<SessionEvent> events = new ArrayList<SessionEvent>();

        Turn(SessionActor actor, long now) {
            this.actor = actor;
            this.now = now;
            this.groupId = actor.groupId();
        }

        /** 读本群活跃局；已过期则先惰性归档，返回归档后的活跃局（无则 null）。 */
        SessionRow active() {
            SessionRow row = store.findActiveByGroup(groupId);
            int rounds = 0;
            while (row != null && archiver.isExpired(row, now, config)) {
                if (++rounds > SessionArchiver.maxExpiryRounds()) {
                    throw new StorageAbort("惰性过期反复失效：group=" + groupId);
                }
                boolean settleGrowth = row.state() == SessionState.RUNNING;
                ArchiveResult archived = archiver.archive(row, "expired", settleGrowth, now);
                if (archived.status() == ArchiveResult.Status.FAILED) {
                    throw new StorageAbort("惰性过期归档未生效：session=" + row.id());
                }
                if (archived.snapshot() != null) {
                    events.add(SessionEvent.ended(archived.snapshot(), archived.endReason(),
                            archived.growth()));
                }
                row = store.findActiveByGroup(groupId);
            }
            return row;
        }

        /** 写后回读：重读本局行再构造快照，避免拿陈旧行对外。 */
        SessionSnapshot reload(SessionRow row) {
            if (row == null) {
                return SessionSnapshots.none(groupId);
            }
            SessionRow current = store.findSession(row.id());
            return snapshot(current == null ? row : current);
        }

        /** 读取本局成员行（不存在返回 null）。 */
        MemberRow member(SessionRow row, long userId) {
            return findMember(store.loadMembers(row.id()), userId);
        }

        SessionSnapshot snapshot(SessionRow row) {
            if (row == null) {
                return SessionSnapshots.none(groupId);
            }
            return archiver.snapshot(row, isRecovered(row.id()));
        }
    }

    /** 存储前置失败：中止本步，转为对外存储异常。 */
    private static final class StorageAbort extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private StorageAbort(String message) {
            super(message);
        }
    }

    /**
     * 按 Unicode 码点截断，避免把 emoji 等代理对劈成半个字符写进日志与提示词。
     *
     * @param text     原文
     * @param maxChars 最多保留的码点数
     * @return 截断后的文本
     */
    static String truncateByCodePoint(String text, int maxChars) {
        if (text == null || maxChars < 0) {
            return "";
        }
        if (text.codePointCount(0, text.length()) <= maxChars) {
            return text;
        }
        int end = text.offsetByCodePoints(0, maxChars);
        return text.substring(0, end);
    }
}