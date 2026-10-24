package org.moboxlab.mbb.trpg;

import org.moboxlab.moboxbot.API.Plugin;
import org.moboxlab.moboxbot.API.PluginManager;
import org.moboxlab.moboxbot.API.PluginService;
import org.moboxlab.moboxbot.API.Storage.StorageService;
import org.moboxlab.mbb.trpg.ai.AiHostEngine;
import org.moboxlab.mbb.trpg.ai.MbbAiClient;
import org.moboxlab.mbb.trpg.ai.TraceStore;
import org.moboxlab.mbb.trpg.ai.TrpgAiHostService;
import org.moboxlab.mbb.trpg.ai.TrpgAiNarrationSink;
import org.moboxlab.mbb.trpg.card.CardGenerator;
import org.moboxlab.mbb.trpg.command.DiceCommand;
import org.moboxlab.mbb.trpg.command.TrpgCommand;
import org.moboxlab.mbb.trpg.command.TrpgCommandService;
import org.moboxlab.mbb.trpg.config.TrpgConfig;
import org.moboxlab.mbb.trpg.listener.TrpgListener;
import org.moboxlab.mbb.trpg.listener.TrpgNoticeListener;
import org.moboxlab.mbb.trpg.present.ImageUtilRenderer;
import org.moboxlab.mbb.trpg.present.OneBotMessaging;
import org.moboxlab.mbb.trpg.present.OneBotNameResolver;
import org.moboxlab.mbb.trpg.present.TrpgLogReportSource;
import org.moboxlab.mbb.trpg.present.TrpgNameResolver;
import org.moboxlab.mbb.trpg.present.TrpgPresentConfig;
import org.moboxlab.mbb.trpg.present.TrpgPresenter;
import org.moboxlab.mbb.trpg.rule.D100RuleSet;
import org.moboxlab.mbb.trpg.rule.DefaultRandomSource;
import org.moboxlab.mbb.trpg.rule.RandomSource;
import org.moboxlab.mbb.trpg.rule.RuleRegistry;
import org.moboxlab.mbb.trpg.rule.RuleSet;
import org.moboxlab.mbb.trpg.script.BuiltinScriptCatalog;
import org.moboxlab.mbb.trpg.script.ScriptAdmin;
import org.moboxlab.mbb.trpg.script.ScriptCatalog;
import org.moboxlab.mbb.trpg.script.ScriptGenerationService;
import org.moboxlab.mbb.trpg.script.ScriptImportSessions;
import org.moboxlab.mbb.trpg.script.ScriptLibrary;
import org.moboxlab.mbb.trpg.script.ScriptRejectedStore;
import org.moboxlab.mbb.trpg.script.ScriptValidator;
import org.moboxlab.mbb.trpg.session.Clock;
import org.moboxlab.mbb.trpg.session.CooldownRegistry;
import org.moboxlab.mbb.trpg.session.ErrorDedupe;
import org.moboxlab.mbb.trpg.session.GroupLockRegistry;
import org.moboxlab.mbb.trpg.session.PluginManagerScheduler;
import org.moboxlab.mbb.trpg.session.SessionLogService;
import org.moboxlab.mbb.trpg.session.ScheduledTask;
import org.moboxlab.mbb.trpg.session.SessionService;
import org.moboxlab.mbb.trpg.session.SessionSnapshot;
import org.moboxlab.mbb.trpg.session.SessionState;
import org.moboxlab.mbb.trpg.store.BlacklistService;
import org.moboxlab.mbb.trpg.store.SessionStore;
import org.moboxlab.mbb.trpg.store.SqlSessionStore;
import org.moboxlab.mbb.trpg.store.SqlTraceStore;
import org.moboxlab.mbb.trpg.store.TrpgRetentionService;
import org.moboxlab.mbb.trpg.store.TrpgSchemaService;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.LongSupplier;

import com.alibaba.fastjson.JSONObject;

/**
 * MBB-Trpg 插件主类。
 *
 * <p>当前职责：加载配置、注册内置规则集并选定生效规则集、建表并保持幂等，装配存储实现、
 * 审计端口、拉黑名单、会话状态机、呈现层与归档清理，按冻结顺序执行启动序列并排入每小时
 * 清理任务；随后注册 /trpg 与 /r 指令与群监听器。</p>
 *
 * <p>AI 主持按 §9.10 装配：读取 aiHost.* 配置、判定全局开关、承担 /trpg ai 的参数与状态分支；
 * 全局开启时装配真实调用、合并窗口与审计，关闭时保持零调用桩。</p>
 */
public class TrpgPlugin extends Plugin {

    private TrpgConfig trpgConfig;
    private boolean trpgEnabled;
    private String disabledReason = "";

    private RuleSet ruleSet;
    private RandomSource randomSource;
    private SessionStore sessionStore;
    private TraceStore traceStore;
    private BlacklistService blacklistService;
    private SessionService sessionService;
    private TrpgRetentionService retentionService;
    private ScheduledTask retentionTask;
    private AiHostEngine aiEngine;
    private ScheduledTask aiTickTask;
    private ScriptCatalog scriptCatalog;
    /** S10：内置 + 导入的组合剧本库；未启用时为 null。 */
    private ScriptLibrary scriptLibrary;
    private ScriptValidator scriptValidator;
    private ScriptAdmin scriptAdmin;
    /** 生成任务执行器：单线程守护，避免占用主程序调度池。 */
    private ExecutorService scriptExecutor;
    private TrpgPresenter presenter;
    /** 群昵称解析端口：呈现层与 AI 叙述落款共用。 */
    private TrpgNameResolver nameResolver;
    private SessionLogService sessionLogService;
    private TrpgCommandService commandService;
    /** AI 主持宿主服务；全局关闭时为零调用桩。 */
    private TrpgAiHostService aiHostService;
    private TrpgListener listener;
    private TrpgNoticeListener noticeListener;
    /** 指令层共用登记：同群同类错误 10 秒只回一次、冷却窗口跨指令生效。 */
    private CooldownRegistry cooldowns;
    private ErrorDedupe dedupe;

    @Override
    public void onLoad() {
        saveDefaultConfig();
        trpgConfig = TrpgConfig.load(getConfig());
        for (String warning : trpgConfig.warnings()) {
            getLogger().sendWarn("[MBB-Trpg] " + warning);
        }
        getLogger().sendInfo("[MBB-Trpg] 配置已加载，共 " + TrpgConfig.keys().size() + " 项，AI 主持"
                + (trpgConfig.aiHostEnabled() ? "已启用" : "未启用"));
    }

    @Override
    public void onEnable() {
        if (trpgConfig == null) {
            trpgConfig = TrpgConfig.load(getConfig());
        }
        // 规则集是编译期 SPI：先注册内置实现，再按配置里的 id 取用。
        // 注册与取用不依赖数据库，因此放在建表之前，建表失败时规则集依然可用。
        registerBuiltinRuleSets();
        if (getServer() == null) {
            trpgEnabled = false;
            disabledReason = "服务器未就绪";
            getLogger().sendError("[MBB-Trpg] 取不到服务器实例，TRPG 能力已禁用");
            return;
        }
        StorageService storage = getServer().getStorage();
        if (storage == null) {
            trpgEnabled = false;
            disabledReason = "存储服务不可用";
            getLogger().sendError("[MBB-Trpg] 取不到存储服务，TRPG 能力已禁用");
            return;
        }
        TrpgSchemaService schemaService = new TrpgSchemaService(storage, getLogger());
        boolean ready = schemaService.ensureSchema();
        if (!ready) {
            trpgEnabled = false;
            disabledReason = "数据表未就绪";
            getLogger().sendError("[MBB-Trpg] 建表失败，TRPG 能力已禁用；不会删除或重建已有表，"
                    + "请检查数据库与上方告警后重启插件");
            return;
        }
        if (!assembleScriptCatalog()) {
            return;
        }
        trpgEnabled = true;
        disabledReason = "";
        assembleServices(storage);
        // 后续切片装配位：呈现服务、检定与场景、指令与监听器。
        getLogger().sendInfo("[MBB-Trpg] 已启用，版本 " + getVersion() + "，数据表已就绪");
    }

    private void registerBuiltinRuleSets() {
        RuleRegistry.register(new D100RuleSet());
        randomSource = DefaultRandomSource.INSTANCE;
        ruleSet = RuleRegistry.getOrDefault(trpgConfig.ruleSetId());
        if (ruleSet == null) {
            getLogger().sendError("[MBB-Trpg] 规则集 " + trpgConfig.ruleSetId()
                    + " 未注册且内置 d100 缺失，检定不可用");
        } else if (ruleSet.id().equals(trpgConfig.ruleSetId())) {
            getLogger().sendInfo("[MBB-Trpg] 规则集 " + ruleSet.id() + "（" + ruleSet.displayName()
                    + "）已就绪");
        }
        // 回退告警由 RuleRegistry 统一记录，这里只负责打成插件日志，避免同一件事报两遍。
        for (String warning : RuleRegistry.warnings()) {
            getLogger().sendWarn("[MBB-Trpg] " + warning);
        }
    }

    /**
     * 加载并校验内置剧本库。
     *
     * <p>内置剧本随 JAR 分发，缺失或校验失败属于分发问题：此时把插件置为不可用，
     * 避免指令层对外暴露不可用剧本。导入剧本属于 S10，届时由剧本库组合视图负责。</p>
     *
     * @return 加载成功返回 true；失败时已设置禁用原因与错误日志
     */
    private boolean assembleScriptCatalog() {
        ScriptValidator validator = new ScriptValidator();
        BuiltinScriptCatalog builtin;
        try {
            builtin = new BuiltinScriptCatalog(TrpgPlugin.class.getClassLoader(), validator);
        } catch (RuntimeException e) {
            trpgEnabled = false;
            disabledReason = "内置剧本未就绪";
            scriptCatalog = null;
            getLogger().sendError("[MBB-Trpg] 内置剧本加载失败，TRPG 能力已禁用：" + e.getMessage());
            return false;
        }
        scriptValidator = validator;
        // S10：内置库与导入库组合成同一个剧本目录视图；导入文件放 <dataFolder>/scripts。
        ScriptLibrary library = new ScriptLibrary(builtin, new File(getDataFolder(), "scripts"),
                validator, trpgConfig.scriptMaxBytes(), getLogger());
        scriptLibrary = library;
        scriptCatalog = library;
        for (String warning : builtin.warnings()) {
            getLogger().sendWarn("[MBB-Trpg] 剧本校验告警：" + warning);
        }
        getLogger().sendInfo("[MBB-Trpg] 剧本库已加载，内置 " + builtin.size() + " 份，导入 "
                + library.importedCount() + " 份");
        return true;
    }

    private void assembleServices(StorageService storage) {
        SqlSessionStore store = new SqlSessionStore(storage, getLogger());
        Clock clock = new Clock() {
            @Override
            public long nowMillis() {
                return System.currentTimeMillis();
            }
        };
        sessionStore = store;
        traceStore = new SqlTraceStore(store);
        blacklistService = new BlacklistService(store);
        nameResolver = new OneBotNameResolver(getServer());
        presenter = new TrpgPresenter(new TrpgPresentConfig(trpgConfig),
                new OneBotMessaging(getServer()), new ImageUtilRenderer(), nameResolver,
                scriptCatalog,
                new TrpgLogReportSource(store, scriptCatalog, nameResolver,
                        trpgConfig.retentionSessionDays()),
                getLogger());
        // 冷却与去重登记跨 /trpg、/r 共用：同群同类错误 10 秒只回一次（§3.2），冷却窗口也不分指令层。
        cooldowns = new CooldownRegistry();
        dedupe = new ErrorDedupe();
        // AI 主持先建桩：会话状态机在 /trpg ai 分支上只问桩，桩自身不持有 AI 与审计端口。
        aiHostService = new TrpgAiHostService(trpgConfig);
        sessionService = new SessionService(store, scriptCatalog, clock, randomSource, trpgConfig,
                presenter, getLogger(), new GroupLockRegistry(clock), cooldowns, dedupe,
                aiHostService);
        // 剧本删除/替换的引用保护：库先建、局面服务后建，因此在这里补绑引用索引（§8.6）。
        if (scriptLibrary != null) {
            scriptLibrary.bindUsageIndex(sessionService);
        }
        // 只读快照视图交给宿主服务，v1.1 的叙述链路复用它（§9.11）。
        aiHostService.bindSessionView(sessionService);
        getLogger().sendInfo("[MBB-Trpg] AI 主持全局开关："
                + (aiHostService.globallyEnabled()
                ? "开（装配真实调用与审计）" : "关（零调用桩）"));
        sessionLogService = new SessionLogService(store, trpgConfig, clock, cooldowns, dedupe, getLogger());
        ScriptImportSessions importSessions = assembleScriptAdmin();
        commandService = new TrpgCommandService(sessionService, sessionLogService, scriptCatalog,
                new CardGenerator(randomSource), presenter, nameResolver, trpgConfig, clock, getLogger(),
                cooldowns, dedupe, blacklistService, scriptAdmin);
        listener = new TrpgListener(sessionService, blacklistService, importSessions, presenter,
                getLogger(), trpgConfig, aiHostService);
        noticeListener = new TrpgNoticeListener(sessionService, getLogger());
        retentionService = new TrpgRetentionService(store, getLogger(), clock,
                trpgConfig.retentionSessionDays(), trpgConfig.aiTraceKeepHours(),
                trpgConfig.aiTraceMaxRows());

        // 启动顺序按规格 §5.7 / §6.5：建表 → 成长与摘要补做 → 清理 → 扫未归档局并重建内存局面。
        // 补做先于清理，避免已结算的局被当成过期数据；恢复只读不写库。
        try {
            int settled = sessionService.settlePending();
            if (settled > 0) {
                getLogger().sendInfo("[MBB-Trpg] 启动补做完成，结算 " + settled + " 局");
            }
        } catch (RuntimeException e) {
            getLogger().sendWarn("[MBB-Trpg] 启动补做异常，已跳过：" + e.getMessage());
        }
        try {
            TrpgRetentionService.Result result = retentionService.runOnce(clock.nowMillis());
            getLogger().sendInfo("[MBB-Trpg] 启动清理完成：" + result);
        } catch (RuntimeException e) {
            getLogger().sendWarn("[MBB-Trpg] 启动清理异常，已跳过：" + e.getMessage());
        }
        try {
            List<SessionSnapshot> recovered = sessionService.recover();
            int running = 0;
            for (SessionSnapshot snapshot : recovered) {
                if (snapshot.active() && snapshot.state() == SessionState.RUNNING) {
                    running++;
                }
            }
            getLogger().sendInfo("[MBB-Trpg] 已加载进行中的局 " + recovered.size()
                    + " 个，其中待确认恢复 " + running + " 个");
        } catch (RuntimeException e) {
            getLogger().sendWarn("[MBB-Trpg] 启动恢复异常，已跳过：" + e.getMessage());
        }
        PluginManager pluginManager = getServer() == null ? null : getServer().getPluginManager();
        if (pluginManager == null) {
            retentionTask = null;
            getLogger().sendWarn("[MBB-Trpg] 取不到插件管理器，归档清理与指令注册均不会生效");
            return;
        }
        registerCommands(pluginManager, clock);
        registerListeners(pluginManager);
        assembleAiHost(pluginManager, clock);
        try {
            retentionTask = retentionService.scheduleHourly(
                    new PluginManagerScheduler(this, pluginManager, getLogger()));
        } catch (RuntimeException e) {
            retentionTask = null;
            getLogger().sendWarn("[MBB-Trpg] 归档清理定时任务注册失败：" + e.getMessage());
        }
    }

    /**
     * 读 MBB-AI 启动时报告的全局并发上限；读不到返回 0（只用本插件配置的 2）。
     *
     * <p>对应规格 §9.7「v1.1 启动时读 AIService.status() 的 maxConcurrent，取较小值」。</p>
     */
    private int aiServiceMaxConcurrent(PluginManager pluginManager) {
        try {
            PluginService ai = pluginManager.getService(MbbAiClient.SERVICE_NAME);
            if (ai == null) {
                return 0;
            }
            JSONObject status = ai.call("status", new JSONObject(true));
            if (status == null || !status.getBooleanValue("status")) {
                return 0;
            }
            return status.getIntValue("maxConcurrent");
        } catch (RuntimeException e) {
            getLogger().sendWarn("[MBB-Trpg] 读取 MBB-AI 并发上限失败，按插件配置执行：" + e.getMessage());
            return 0;
        }
    }

    /**
     * 装配 v1.1 AI 主持引擎（§9）：MBB-AI 客户端、叙述发送端口、失败计数与剧本目录。
     *
     * <p>全局开关关闭时保持 v1 桩：零调用、零审计、不注册定时器（§9.10）。</p>
     */
    private void assembleAiHost(PluginManager pluginManager, Clock clock) {
        if (!trpgConfig.aiHostEnabled()) {
            getLogger().sendInfo("[MBB-Trpg] AI 主持未启用：保持零调用桩");
            return;
        }
        try {
            PluginManagerScheduler scheduler = new PluginManagerScheduler(this, pluginManager,
                    getLogger());
            int aiLimit = aiServiceMaxConcurrent(pluginManager);
            AiHostEngine engine = new AiHostEngine(trpgConfig, new MbbAiClient(this, trpgConfig), sessionService,
                    traceStore, scheduler, clock, getLogger(),
                    new TrpgAiNarrationSink(presenter, sessionService, nameResolver), aiLimit);
            engine.bindFailureRecorder(sessionService);
            engine.bindScriptCatalog(scriptCatalog);
            aiHostService.bindEngine(engine);
            if (commandService != null) {
                commandService.bindAiTriggers(aiHostService);
            }
            aiEngine = engine;
            aiTickTask = scheduler.runTimer(new Runnable() {
                @Override
                public void run() {
                    aiHostService.tick();
                }
            }, 1000L, 1000L);
            getLogger().sendInfo("[MBB-Trpg] AI 主持已启用（每 1 秒结算合并窗口）");
        } catch (RuntimeException e) {
            aiEngine = null;
            aiTickTask = null;
            getLogger().sendWarn("[MBB-Trpg] AI 主持装配失败，保持零调用桩：" + e.getMessage());
        }
    }

    /**
     * 装配 S10 剧本管理三件套：分段导入暂存、AI 生成与生成执行器。
     *
     * <p>任一步失败只让 script 的库管理动作回「暂不可用」，不影响其它 TRPG 能力。</p>
     *
     * @return 导入暂存服务，供消息入口拦截分段；装配失败返回 null
     */
    private ScriptImportSessions assembleScriptAdmin() {
        if (scriptLibrary == null || scriptValidator == null) {
            return null;
        }
        try {
            ScriptRejectedStore rejected = new ScriptRejectedStore(
                    new File(getDataFolder(), "imports/rejected"),
                    trpgConfig.scriptRejectedMaxFiles(), getLogger());
            ScriptImportSessions imports = new ScriptImportSessions(scriptLibrary, scriptValidator,
                    rejected, trpgConfig.scriptMaxBytes(),
                    trpgConfig.scriptImportIdleMinutes() * 60_000L,
                    trpgConfig.scriptImportMaxMinutes() * 60_000L,
                    trpgConfig.scriptImportMaxSegments(), new LongSupplier() {
                        @Override
                        public long getAsLong() {
                            return System.currentTimeMillis();
                        }
                    });
            ScriptGenerationService generation = new ScriptGenerationService(scriptLibrary,
                    scriptValidator, new MbbAiClient(this, trpgConfig), rejected);
            scriptExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "MBB-Trpg-剧本生成");
                    thread.setDaemon(true);
                    return thread;
                }
            });
            scriptAdmin = new ScriptAdmin(imports, generation, scriptExecutor);
            getLogger().sendInfo("[MBB-Trpg] 剧本管理已装配（分段导入 + AI 生成）");
            return imports;
        } catch (RuntimeException e) {
            scriptAdmin = null;
            getLogger().sendWarn("[MBB-Trpg] 剧本管理装配失败，script 库管理动作不可用："
                    + e.getMessage());
            return null;
        }
    }

    /** 注册 /trpg 与 /r；指令层只做解析与委托，所有回执经呈现层发出。 */
    private void registerCommands(PluginManager pluginManager, Clock clock) {
        try {
            pluginManager.registerCommand(this, new TrpgCommand(commandService));
        } catch (RuntimeException e) {
            getLogger().sendError("[MBB-Trpg] /trpg 指令注册失败，该指令不可用：" + e.getMessage());
        }
        try {
            pluginManager.registerCommand(this, new DiceCommand(trpgConfig, presenter, randomSource, clock,
                    cooldowns, dedupe));
        } catch (RuntimeException e) {
            getLogger().sendError("[MBB-Trpg] /r 指令注册失败，该指令不可用：" + e.getMessage());
        }
        if (commandService != null) {
            getLogger().sendInfo("[MBB-Trpg] 指令已注册：/trpg（跑团）、/r 与 /roll（掷骰）");
        }
    }

    /** 注册群消息与离群通知监听器；任一失败只影响该监听器。 */
    private void registerListeners(PluginManager pluginManager) {
        try {
            pluginManager.registerListener(this, listener);
        } catch (RuntimeException e) {
            getLogger().sendError("[MBB-Trpg] 群消息监听器注册失败，黑名单与局内活动刷新不可用："
                    + e.getMessage());
        }
        try {
            pluginManager.registerListener(this, noticeListener);
        } catch (RuntimeException e) {
            getLogger().sendError("[MBB-Trpg] 离群通知监听器注册失败，KP 离群不会自动收尾："
                    + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (retentionTask != null) {
            retentionTask.cancel();
            retentionTask = null;
        }
        if (aiTickTask != null) {
            aiTickTask.cancel();
            aiTickTask = null;
        }
        if (aiHostService != null) {
            aiHostService.shutdown();
        }
        aiEngine = null;
        if (scriptExecutor != null) {
            scriptExecutor.shutdownNow();
            scriptExecutor = null;
        }
        ruleSet = null;
        randomSource = null;
        sessionStore = null;
        traceStore = null;
        blacklistService = null;
        sessionService = null;
        retentionService = null;
        scriptCatalog = null;
        scriptLibrary = null;
        scriptValidator = null;
        scriptAdmin = null;
        presenter = null;
        sessionLogService = null;
        commandService = null;
        aiHostService = null;
        listener = null;
        noticeListener = null;
        cooldowns = null;
        dedupe = null;
        getLogger().sendInfo("[MBB-Trpg] 已停用");
    }

    /** 配置快照；未加载时为 null。 */
    public TrpgConfig trpgConfig() {
        return trpgConfig;
    }

    /** TRPG 能力是否可用（建表失败时为 false）。 */
    public boolean trpgEnabled() {
        return trpgEnabled;
    }

    /** TRPG 被禁用的原因；可用时为空串。 */
    public String disabledReason() {
        return disabledReason;
    }

    /** 当前生效的规则集；注册完成后即有值，插件停用后为 null。 */
    public RuleSet ruleSet() {
        return ruleSet;
    }

    /** 生产随机源；注册完成后即有值，插件停用后为 null。 */
    public RandomSource randomSource() {
        return randomSource;
    }

    /** 会话存储；未启用时为 null。 */
    public SessionStore sessionStore() {
        return sessionStore;
    }

    /** AI 审计端口；未启用时为 null。 */
    public TraceStore traceStore() {
        return traceStore;
    }

    /** 拉黑名单服务；未启用时为 null。 */
    public BlacklistService blacklistService() {
        return blacklistService;
    }

    /** 会话状态机；未启用时为 null。 */
    public SessionService sessionService() {
        return sessionService;
    }

    /** 归档清理服务；未启用时为 null。 */
    public TrpgRetentionService retentionService() {
        return retentionService;
    }

    /** 剧本库（内置只读）；未启用时为 null。 */
    public ScriptCatalog scriptCatalog() {
        return scriptCatalog;
    }

    /** 呈现层门面（唯一消息出口）；未启用时为 null。 */
    public TrpgPresenter presenter() {
        return presenter;
    }

    /** 日志回看服务；未启用时为 null。 */
    public SessionLogService sessionLogService() {
        return sessionLogService;
    }

    /** 指令服务；未启用时为 null。 */
    public TrpgCommandService commandService() {
        return commandService;
    }

    /** AI 主持宿主服务；装配失败时为未启用桩。 */
    public TrpgAiHostService aiHostService() {
        return aiHostService;
    }

    /** 群消息监听器；未启用时为 null。 */
    public TrpgListener listener() {
        return listener;
    }

    /** 离群通知监听器；未启用时为 null。 */
    public TrpgNoticeListener noticeListener() {
        return noticeListener;
    }
}
