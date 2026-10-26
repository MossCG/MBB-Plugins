package org.moboxlab.mbb.trpg.ai;

import org.moboxlab.moboxbot.API.PluginLogger;
import org.moboxlab.mbb.trpg.config.TrpgConfig;
import org.moboxlab.mbb.trpg.script.ScriptCatalog;
import org.moboxlab.mbb.trpg.session.Clock;
import org.moboxlab.mbb.trpg.session.ScheduledTask;
import org.moboxlab.mbb.trpg.session.Scheduler;
import org.moboxlab.mbb.trpg.session.SessionSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 主持引擎：触发合并、排队与并发、调用、降级与审计的总装。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>只做编排与降级，不直接发群消息：发送由 {@link AiNarrationSink} 接线；</li>
 *   <li>连续失败阈值、挂起时间戳与失败计数清零由 SessionService 负责，引擎通过
 *       {@link AiFailureRecorder} 回调并只消费返回结果；</li>
 *   <li>提示词装配、叙述校验、局视图门禁与审计分别委托 {@link AiPromptBuilder}、
 *       {@link AiNarrationValidator}、{@link AiSessionGate} 与 {@link AiTraceWriter}。</li>
 * </ul>
 *
 * <p>调用必须异步：submit 只做入队，实际 AI 调用在守护线程池中执行，并用
 * Future.get(deadline) 做插件侧墙钟，不阻塞事件监听线程。</p>
 *
 * <p>背压分两层：每群串行队列按 {@code aiPerGroupQueue} 限长，全局工作线程池本身也用
 * 有界队列，池满时按 queue_full 降级，不按群无限堆线程。</p>
 */
public final class AiHostEngine {

    private static final String RESULT_SUCCESS = "success";
    private static final String RESULT_DEGRADED = "degraded";
    private static final String RESULT_SUSPENDED = "suspended";
    private static final String RESULT_STALE = "stale";
    private static final String RESULT_SKIPPED = "skipped";

    private static final String REASON_QUEUE_FULL = "queue_full";
    private static final String REASON_QUEUE_AGE = "queue_age";
    private static final String REASON_VERIFICATION = "verification";
    private static final String REASON_TRUNCATED = "truncated";
    private static final String REASON_UNAVAILABLE = "unavailable";
    private static final String REASON_TIMEOUT = "timeout";

    private final TrpgConfig config;
    private final AiClient client;
    private final Scheduler scheduler;
    private final Clock clock;
    private final AiNarrationSink sink;
    private final AiEngineLog log;
    private final AiTraceWriter traces;
    private final AiPromptBuilder prompts;
    private final AiNarrationValidator validator;
    private final AiSessionGate gate;
    private final AiFailureCounter failures;

    /** 全局并发许可，数量等于 config.aiMaxConcurrent()。 */
    private final Semaphore concurrency;
    /** 每群串行工作线程所在的有界工作池：池满时拒绝，转成 queue_full 降级。 */
    private final ExecutorService groupExecutor;
    /** 真正执行 AI 调用的线程池，容量等于全局并发上限。 */
    private final ExecutorService callExecutor;

    private final Object lock = new Object();
    private final Map<Long, GroupQueue> queues = new HashMap<Long, GroupQueue>();
    private final Map<Long, MergeWindow> windows = new HashMap<Long, MergeWindow>();
    private final Map<String, Long> dedupeExpiry = new HashMap<String, Long>();
    /** 本挂起周期已发过挂起提示的群，避免动作提示与触发提示各发一次（§9.5）。 */
    private final Set<Long> suspensionNotified = new LinkedHashSet<Long>();
    private int globalWaiting;
    private int inFlight;
    private boolean shutdown;

    /**
     * 创建引擎。
     *
     * <p>构造器只做依赖注入，不启动任何调用；失败计数端口与剧本目录通过
     * {@link #bindFailureRecorder(AiFailureRecorder)}、{@link #bindScriptCatalog(ScriptCatalog)} 后续绑定。</p>
     */
    public AiHostEngine(TrpgConfig config, AiClient client, SessionView view, TraceStore trace,
                        Scheduler scheduler, Clock clock, PluginLogger logger, AiNarrationSink sink) {
        this(config, client, view, trace, scheduler, clock, logger, sink, 0);
    }

    /**
     * 完整构造：{@code maxConcurrent} 为 MBB-AI 侧的全局并发上限，正数时与配置取较小值（§9.7）。
     *
     * @param maxConcurrent MBB-AI 启动时报告的并发数；0 或负数表示不额外限制
     */
    public AiHostEngine(TrpgConfig config, AiClient client, SessionView view, TraceStore trace,
                        Scheduler scheduler, Clock clock, PluginLogger logger, AiNarrationSink sink,
                        int maxConcurrent) {
        if (config == null) {
            throw new IllegalArgumentException("配置不能为空");
        }
        if (client == null) {
            throw new IllegalArgumentException("AI 客户端不能为空");
        }
        if (view == null) {
            throw new IllegalArgumentException("局视图不能为空");
        }
        if (scheduler == null) {
            throw new IllegalArgumentException("定时器不能为空");
        }
        if (clock == null) {
            throw new IllegalArgumentException("时钟不能为空");
        }
        if (sink == null) {
            throw new IllegalArgumentException("叙述发送端口不能为空");
        }
        this.config = config;
        this.client = client;
        this.scheduler = scheduler;
        this.clock = clock;
        this.sink = sink;
        AiEngineLog engineLog = new AiEngineLog(logger);
        this.log = engineLog;
        this.traces = new AiTraceWriter(trace, config, clock, engineLog);
        this.prompts = new AiPromptBuilder(config, view, engineLog);
        this.validator = new AiNarrationValidator(config, engineLog);
        this.gate = new AiSessionGate(view, clock, traces);
        this.failures = new AiFailureCounter(engineLog);
        int limit = config.aiMaxConcurrent();
        if (maxConcurrent > 0) {
            limit = Math.min(limit, maxConcurrent);
        }
        limit = Math.max(1, limit);
        this.concurrency = new Semaphore(limit);
        // 每群串行任务会阻塞在并发许可或墙钟上；固定大小加有界队列，池满即拒绝。
        int workers = Math.max(2, limit * 2);
        this.groupExecutor = new ThreadPoolExecutor(workers, workers, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(workerQueueCapacity(limit)),
                daemonFactory("mbb-trpg-ai-group"), new ThreadPoolExecutor.AbortPolicy());
        this.callExecutor = Executors.newFixedThreadPool(limit, daemonFactory("mbb-trpg-ai-call"));
    }

    /**
     * 全局工作池的等待位：与全局排队上限同量级，至少留 2 个位置。
     *
     * <p>不能直接用 {@code aiGlobalQueue}：请求在池里排队时仍占着每群队列与全局排队计数，
     * 等待位与排队上限相同就基本不会拒绝，等于没有背压。</p>
     */
    private int workerQueueCapacity(int limit) {
        return Math.max(2, Math.min(Math.max(1, config.aiGlobalQueue()), limit));
    }

    /** 绑定失败计数端口（由 SessionService 实现）。未绑定时引擎不记录失败、不清零。 */
    public void bindFailureRecorder(AiFailureRecorder recorder) {
        failures.bind(recorder);
    }

    /** 绑定剧本目录，用于按 snapshot.scriptId() 取剧本传给提示词装配。 */
    public void bindScriptCatalog(ScriptCatalog catalog) {
        prompts.bindScriptCatalog(catalog);
    }

    /**
     * 立即触发路径：入队并尽快执行。
     *
     * <p>全局关、本局 AI 关或本局挂起时直接丢弃（不调用、不发送、不审计）。
     * 可合并触发且未标记 immediate 的请求会并入合并窗口。</p>
     */
    public void submit(AiCallRequest request) {
        if (request == null || !accepting()) {
            return;
        }
        if (!allowsOrNotify(request.groupId())) {
            return;
        }
        if (!request.immediate() && request.trigger().mergeable()) {
            List<Long> seqs = request.eventSeqs();
            if (seqs.isEmpty()) {
                enqueue(request);
                return;
            }
            List<String> lines = request.recentEventLines();
            for (int index = 0; index < seqs.size(); index++) {
                String line = index < lines.size() ? lines.get(index) : "";
                addToWindow(request.groupId(), request.sessionNo(), request.sceneVersion(),
                        seqs.get(index).longValue(), line);
            }
            return;
        }
        enqueue(request);
    }

    /**
     * 并入 20 秒 / 5 条合并窗口。
     *
     * <p>第 5 条事件到达时立即结算并取消定时器；否则由 {@link #tick()} 或窗口定时器结算。</p>
     */
    public void submitMerged(long groupId, long sessionId, long sceneVersion, long eventSeq,
                             String actionText) {
        if (!accepting() || eventSeq <= 0L) {
            return;
        }
        if (!allowsOrNotify(groupId)) {
            return;
        }
        addToWindow(groupId, sessionId, sceneVersion, eventSeq, actionText);
    }

    /**
     * 结算到期合并窗口并清理超龄排队请求；由 Scheduler 每 1 秒调用一次。
     */
    public void tick() {
        if (!accepting()) {
            return;
        }
        long now = clock.nowMillis();
        if (!globallyEnabled()) {
            dropWindows();
            synchronized (lock) {
                suspensionNotified.clear();
            }
            return;
        }
        pruneSuspensionNotices();
        List<MergeWindow> due = new ArrayList<MergeWindow>();
        List<PendingCall> expired = new ArrayList<PendingCall>();
        synchronized (lock) {
            if (shutdown) {
                return;
            }
            long windowMillis = mergeWindowMillis();
            Iterator<Map.Entry<Long, MergeWindow>> windowIterator = windows.entrySet().iterator();
            while (windowIterator.hasNext()) {
                MergeWindow window = windowIterator.next().getValue();
                if (now - window.openedAt >= windowMillis) {
                    windowIterator.remove();
                    due.add(window);
                }
            }
            long maxAgeMillis = queueMaxAgeMillis();
            for (GroupQueue queue : queues.values()) {
                Iterator<PendingCall> iterator = queue.waiting.iterator();
                while (iterator.hasNext()) {
                    PendingCall pending = iterator.next();
                    if (now - pending.enqueuedAt > maxAgeMillis) {
                        iterator.remove();
                        globalWaiting--;
                        expired.add(pending);
                    }
                }
            }
        }
        for (MergeWindow window : due) {
            cancel(window.timer);
            enqueueMerged(window);
        }
        for (PendingCall pending : expired) {
            degradeQueueAge(pending, now - pending.enqueuedAt);
        }
    }

    /** 停止接受新请求并取消窗口定时器；在途调用自行收尾。 */
    public void shutdown() {
        List<MergeWindow> pendingWindows = new ArrayList<MergeWindow>();
        synchronized (lock) {
            if (shutdown) {
                return;
            }
            shutdown = true;
            pendingWindows.addAll(windows.values());
            windows.clear();
            queues.clear();
            dedupeExpiry.clear();
            suspensionNotified.clear();
            globalWaiting = 0;
            inFlight = 0;
        }
        for (MergeWindow window : pendingWindows) {
            cancel(window.timer);
        }
        groupExecutor.shutdownNow();
        callExecutor.shutdownNow();
    }

    /** 当前排队中与执行中的请求总数。 */
    public int pendingCount() {
        synchronized (lock) {
            return globalWaiting + inFlight;
        }
    }

    /** 指定群是否还有合并窗口未结算。 */
    public boolean windowOpen(long groupId) {
        synchronized (lock) {
            return windows.containsKey(Long.valueOf(groupId));
        }
    }

    /** 清理某一局的去重缓存（ai on 时调用）。 */
    public void clearDedupe(long sessionNo) {
        String suffix = "-" + sessionNo;
        synchronized (lock) {
            Iterator<Map.Entry<String, Long>> iterator = dedupeExpiry.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Long> entry = iterator.next();
                String key = entry.getKey();
                String sessionKey = key.substring(0, key.indexOf('|'));
                if (sessionKey.endsWith(suffix)) {
                    iterator.remove();
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 入队与合并窗口
    // ------------------------------------------------------------------

    /** 入队；超限不排队直接 queue_full 降级，去重命中静默跳过。 */
    private void enqueue(AiCallRequest request) {
        if (request == null || !accepting()) {
            return;
        }
        boolean full = false;
        boolean accepted = false;
        boolean startWorker = false;
        long groupId = request.groupId();
        synchronized (lock) {
            if (shutdown) {
                return;
            }
            GroupQueue queue = queues.get(Long.valueOf(groupId));
            if (queue == null) {
                queue = new GroupQueue(groupId);
                queues.put(Long.valueOf(groupId), queue);
            }
            int perGroupLimit = Math.max(1, config.aiPerGroupQueue());
            int globalLimit = Math.max(1, config.aiGlobalQueue());
            if (queue.waiting.size() >= perGroupLimit || globalWaiting >= globalLimit) {
                full = true;
            } else if (!registerDedupeLocked(request)) {
                // 同键 60 秒内已调用或已入队，静默跳过。
            } else {
                queue.waiting.addLast(new PendingCall(request, clock.nowMillis()));
                globalWaiting++;
                accepted = true;
                if (!queue.workerRunning) {
                    queue.workerRunning = true;
                    startWorker = true;
                }
            }
        }
        if (full) {
            degradeQueueFull(request);
            return;
        }
        if (!accepted) {
            return;
        }
        if (startWorker) {
            startWorker(groupId);
        }
    }

    /**
     * 提交本群的工作线程；全局工作池已满时按 queue_full 降级本群积压，不抛给调用方。
     */
    private void startWorker(final long groupId) {
        try {
            groupExecutor.execute(new GroupWorker(groupId));
        } catch (RejectedExecutionException error) {
            log.warn("AI 工作线程提交被拒，群 " + groupId + "：" + error);
            rejectWorker(groupId);
        }
    }

    /** 全局工作池拒绝后的收尾：复位本群工作线程标记，把积压请求按 queue_full 降级。 */
    private void rejectWorker(long groupId) {
        List<PendingCall> rejected = new ArrayList<PendingCall>();
        synchronized (lock) {
            GroupQueue queue = queues.remove(Long.valueOf(groupId));
            if (queue == null) {
                return;
            }
            queue.workerRunning = false;
            while (!queue.waiting.isEmpty()) {
                PendingCall pending = queue.waiting.pollFirst();
                globalWaiting--;
                rejected.add(pending);
            }
        }
        for (PendingCall pending : rejected) {
            degradeQueueFull(pending.request);
        }
    }

    /** 每群串行工作线程：取队首、检查超龄与门禁、占全局并发槽位、执行。 */
    private final class GroupWorker implements Runnable {

        private final long groupId;

        GroupWorker(long groupId) {
            this.groupId = groupId;
        }

        @Override
        public void run() {
            while (true) {
                PendingCall pending = takeNext(groupId);
                if (pending == null) {
                    return;
                }
                try {
                    if (isExpired(pending)) {
                        degradeQueueAge(pending, clock.nowMillis() - pending.enqueuedAt);
                        continue;
                    }
                    if (!allowsOrNotify(groupId)) {
                        continue;
                    }
                    int slot = acquireSlot(pending);
                    if (slot < 0) {
                        return;
                    }
                    if (slot == 0) {
                        // 等并发槽位时已超龄：按超龄降级，不再调用（§9.7）。
                        degradeQueueAge(pending, clock.nowMillis() - pending.enqueuedAt);
                        continue;
                    }
                    try {
                        execute(pending);
                    } finally {
                        concurrency.release();
                    }
                } catch (RuntimeException error) {
                    log.warn("AI 调用线程异常，群 " + groupId + "：" + error);
                } finally {
                    finishOne();
                }
            }
        }
    }

    private PendingCall takeNext(long groupId) {
        synchronized (lock) {
            GroupQueue queue = queues.get(Long.valueOf(groupId));
            if (queue == null || queue.waiting.isEmpty()) {
                if (queue != null) {
                    queue.workerRunning = false;
                    // 空队列立即从表里摘掉，避免长跑按群累积。
                    queues.remove(Long.valueOf(groupId));
                }
                return null;
            }
            PendingCall pending = queue.waiting.pollFirst();
            globalWaiting--;
            inFlight++;
            return pending;
        }
    }

    private void finishOne() {
        synchronized (lock) {
            if (inFlight > 0) {
                inFlight--;
            }
        }
    }

    /**
     * 占全局并发槽位，最多等到本条请求超龄为止。
     *
     * @return 1 已获得；0 等待期间超龄；-1 线程被中断（插件停用）
     */
    private int acquireSlot(PendingCall pending) {
        long maxAge = queueMaxAgeMillis();
        long remaining = maxAge - (clock.nowMillis() - pending.enqueuedAt);
        if (remaining <= 0L) {
            return 0;
        }
        try {
            return concurrency.tryAcquire(remaining, TimeUnit.MILLISECONDS) ? 1 : 0;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    private void addToWindow(long groupId, long sessionNo, long sceneVersion, long eventSeq,
                             String actionText) {
        MergeWindow settled = null;
        synchronized (lock) {
            if (shutdown) {
                return;
            }
            MergeWindow window = windows.get(Long.valueOf(groupId));
            // 窗口键只认局号：同一局内场景推进不应打断玩家行动的合并（§9.3）。
            if (window != null && window.sessionNo != sessionNo) {
                windows.remove(Long.valueOf(groupId));
                settled = window;
                window = null;
            }
            if (window == null) {
                window = new MergeWindow(groupId, sessionNo, sceneVersion, clock.nowMillis());
                windows.put(Long.valueOf(groupId), window);
                window.timer = scheduler.runLater(new WindowTimer(window), mergeWindowMillis());
            }
            // 结算时用最新场景版本做 stale 判断，窗口期间场景推进不算过期。
            window.sceneVersion = sceneVersion;
            if (!window.seqs.contains(Long.valueOf(eventSeq))) {
                window.seqs.add(Long.valueOf(eventSeq));
                window.lines.add(actionText == null ? "" : actionText);
                if (window.firstSeq == 0L) {
                    window.firstSeq = eventSeq;
                }
                window.lastSeq = eventSeq;
            }
            if (window.seqs.size() >= Math.max(1, config.aiMergeWindowEvents())) {
                windows.remove(Long.valueOf(groupId));
                settled = window;
            }
        }
        if (settled != null) {
            cancel(settled.timer);
            enqueueMerged(settled);
        }
    }

    private void settleWindow(MergeWindow window) {
        synchronized (lock) {
            if (windows.get(Long.valueOf(window.groupId)) != window) {
                return;
            }
            windows.remove(Long.valueOf(window.groupId));
        }
        cancel(window.timer);
        enqueueMerged(window);
    }

    private void enqueueMerged(MergeWindow window) {
        AiCallRequest request = new AiCallRequest(window.groupId, window.sessionNo,
                AiTrigger.MERGED, new ArrayList<Long>(window.seqs),
                window.firstSeq, window.lastSeq, window.sceneVersion,
                false, new ArrayList<String>(window.lines));
        enqueue(request);
    }

    private void dropWindows() {
        List<MergeWindow> dropped = new ArrayList<MergeWindow>();
        synchronized (lock) {
            dropped.addAll(windows.values());
            windows.clear();
        }
        for (MergeWindow window : dropped) {
            cancel(window.timer);
        }
    }

    private final class WindowTimer implements Runnable {

        private final MergeWindow window;

        WindowTimer(MergeWindow window) {
            this.window = window;
        }

        @Override
        public void run() {
            settleWindow(window);
        }
    }

    // ------------------------------------------------------------------
    // 调用与结果处理
    // ------------------------------------------------------------------

    private void execute(PendingCall pending) {
        AiCallRequest request = pending.request;
        long startAt = clock.nowMillis();
        long deadlineAt = startAt + deadlineMillis();
        SessionSnapshot snapshot = gate.snapshot(request.groupId());
        if (!gate.enabled(snapshot) || gate.suspended(snapshot) || !gate.running(snapshot)) {
            return;
        }
        AiRequest prompt;
        try {
            prompt = prompts.buildRequest(request, snapshot);
        } catch (RuntimeException error) {
            log.warn("AI 提示词装配失败，群 " + request.groupId() + "：" + error);
            degrade(request, REASON_UNAVAILABLE, "", 0, 0L, null, "");
            return;
        }
        AiResult result = invoke(prompt, deadlineAt);
        int attempts = 1;
        if (result == null) {
            failTimeout(request, attempts, startAt);
            return;
        }
        if (!result.success()) {
            failResult(request, result, attempts, startAt);
            return;
        }
        AiNarrationValidation validation = validator.validate(result.content(), result.finishReason());
        if (!validation.valid) {
            if (deadlineAt - clock.nowMillis() > 0L) {
                result = invoke(prompt, deadlineAt);
                attempts = 2;
                if (result == null) {
                    failTimeout(request, attempts, startAt);
                    return;
                }
                if (!result.success()) {
                    failResult(request, result, attempts, startAt);
                    return;
                }
                validation = validator.validate(result.content(), result.finishReason());
            }
            if (!validation.valid) {
                // 墙钟已过时无法重试：只发生过 1 次调用，按 §9.5 不计失败。
                boolean counts = attempts > 1;
                failVerification(request, result, validation.rejectReason, attempts, startAt, counts);
                return;
            }
        }
        succeed(request, result, validation, attempts, startAt);
    }

    /** 异步调用并做插件侧墙钟；超时返回 null，中断与线程拒绝按不计数失败返回。 */
    private AiResult invoke(final AiRequest prompt, long deadlineAt) {
        long remaining = deadlineAt - clock.nowMillis();
        if (remaining <= 0L) {
            return null;
        }
        Future<AiResult> future;
        try {
            future = callExecutor.submit(new Callable<AiResult>() {
                @Override
                public AiResult call() {
                    return client.chat(prompt);
                }
            });
        } catch (RejectedExecutionException error) {
            return AiResult.failure(AiErrorTypes.DISABLED, false, 0L);
        }
        try {
            return future.get(remaining, TimeUnit.MILLISECONDS);
        } catch (TimeoutException error) {
            future.cancel(true);
            return null;
        } catch (InterruptedException error) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            return AiResult.failure(AiErrorTypes.INTERRUPTED, false, 0L);
        } catch (ExecutionException error) {
            log.warn("AI 调用异常：" + error.getCause());
            return AiResult.failure(AiErrorTypes.UNKNOWN, false, 0L);
        }
    }

    private void succeed(AiCallRequest request, AiResult result, AiNarrationValidation validation,
                         int attempts, long startAt) {
        SessionSnapshot latest = gate.snapshot(request.groupId());
        if (latest == null || !gate.enabled(latest)) {
            return;
        }
        if (!latest.active()) {
            traces.write(request, RESULT_SKIPPED, "", "", attempts, traces.elapsed(startAt),
                    result.finishReason(), result.usageJson(), result.cached(), "");
            return;
        }
        if (gate.suspended(latest)) {
            traces.write(request, RESULT_SKIPPED, "", "", attempts, traces.elapsed(startAt),
                    result.finishReason(), result.usageJson(), result.cached(), "");
            return;
        }
        if (latest.sessionId() != request.sessionNo()
                || latest.sceneVersion() != request.sceneVersion()) {
            traces.write(request, RESULT_STALE, "", "", attempts, traces.elapsed(startAt),
                    result.finishReason(), result.usageJson(), result.cached(), "");
            return;
        }
        failures.recordSuccess(request.groupId());
        send(request.groupId(), validation.text, false, "");
        traces.write(request, RESULT_SUCCESS, validation.truncated ? REASON_TRUNCATED : "",
                "", attempts, traces.elapsed(startAt), result.finishReason(), result.usageJson(),
                result.cached(), validation.text);
    }

    private void failResult(AiCallRequest request, AiResult result, int attempts, long startAt) {
        if (!gate.deliverable(request, attempts, startAt)) {
            return;
        }
        String errorType = result.errorType() == null ? AiErrorTypes.UNKNOWN : result.errorType();
        boolean counts = AiDegradeClassifier.countsAsFailure(errorType);
        String reason = AiDegradeClassifier.degradeReason(errorType, REASON_UNAVAILABLE);
        boolean suspendedNow = counts && failures.recordFailure(request.groupId());
        send(request.groupId(), prompts.fallbackNarration(request.groupId()), true, reason);
        traces.write(request, suspendedNow ? RESULT_SUSPENDED : RESULT_DEGRADED, reason, errorType,
                attempts, traces.elapsed(startAt), result.finishReason(), result.usageJson(),
                result.cached(), "");
        afterFailure(request.groupId(), suspendedNow);
    }

    private void failTimeout(AiCallRequest request, int attempts, long startAt) {
        if (!gate.deliverable(request, attempts, startAt)) {
            return;
        }
        boolean suspendedNow = failures.recordFailure(request.groupId());
        send(request.groupId(), prompts.fallbackNarration(request.groupId()), true, REASON_TIMEOUT);
        traces.write(request, suspendedNow ? RESULT_SUSPENDED : RESULT_DEGRADED, REASON_TIMEOUT,
                AiErrorTypes.TIMEOUT, attempts, traces.elapsed(startAt), "", "", false, "");
        afterFailure(request.groupId(), suspendedNow);
    }

    /**
     * 输出校验失败：中文越权原因只进日志，审计的 errorType 固定为 verification。
     *
     * <p>errorType 是审计的稳定取值，混入「越权：修改或宣布数值」这类文案会让按类型聚合失真。</p>
     */
    private void failVerification(AiCallRequest request, AiResult result, String rejectReason,
                                  int attempts, long startAt, boolean counts) {
        if (!gate.deliverable(request, attempts, startAt)) {
            return;
        }
        boolean suspendedNow = counts && failures.recordFailure(request.groupId());
        log.warn("AI 输出校验未通过，群 " + request.groupId() + "，原因："
                + (rejectReason == null ? "" : rejectReason));
        send(request.groupId(), prompts.fallbackNarration(request.groupId()), true,
                REASON_VERIFICATION);
        traces.write(request, suspendedNow ? RESULT_SUSPENDED : RESULT_DEGRADED, REASON_VERIFICATION,
                REASON_VERIFICATION, attempts, traces.elapsed(startAt), result.finishReason(),
                result.usageJson(), result.cached(), "");
        afterFailure(request.groupId(), suspendedNow);
    }

    private void degrade(AiCallRequest request, String reason, String errorType, int attempts,
                         long elapsedMs, AiResult result, String preview) {
        if (!gate.deliverable(request, attempts, clock.nowMillis() - elapsedMs)) {
            return;
        }
        send(request.groupId(), prompts.fallbackNarration(request.groupId()), true, reason);
        traces.write(request, RESULT_DEGRADED, reason, errorType, attempts, elapsedMs,
                result == null ? "" : result.finishReason(),
                result == null ? "" : result.usageJson(),
                result != null && result.cached(), preview);
    }

    private void degradeQueueFull(AiCallRequest request) {
        if (!gate.deliverableQueued(request)) {
            return;
        }
        send(request.groupId(), prompts.fallbackNarration(request.groupId()), true, REASON_QUEUE_FULL);
        traces.write(request, RESULT_DEGRADED, REASON_QUEUE_FULL, "", 0, 0L, "", "", false, "");
    }

    private void degradeQueueAge(PendingCall pending, long waitedMs) {
        if (!gate.deliverableQueued(pending.request)) {
            return;
        }
        send(pending.request.groupId(), prompts.fallbackNarration(pending.request.groupId()), true,
                REASON_QUEUE_AGE);
        traces.write(pending.request, RESULT_DEGRADED, REASON_QUEUE_AGE, "", 0, waitedMs,
                "", "", false, "");
    }

    /** 失败已发出后的挂起收尾：提示一次并清掉本群积压。 */
    private void afterFailure(long groupId, boolean suspendedNow) {
        if (suspendedNow) {
            notifySuspended(groupId);
            clearQueueOnSuspend(groupId);
        }
    }

    private void notifySuspended(long groupId) {
        // 挂起动作本身先占位，挂起后的首次触发就不会重复发同一条提示。
        synchronized (lock) {
            suspensionNotified.add(Long.valueOf(groupId));
        }
        try {
            sink.notifySuspended(groupId);
        } catch (RuntimeException error) {
            log.warn("AI 挂起提示发送失败，群 " + groupId + "：" + error);
        }
    }

    /** 清掉已不需要提示标记的群：局结束、本局 AI 关闭或挂起已解除后，下一轮挂起要能重新提示。 */
    private void pruneSuspensionNotices() {
        List<Long> groups = new ArrayList<Long>();
        synchronized (lock) {
            groups.addAll(suspensionNotified);
        }
        for (Long group : groups) {
            SessionSnapshot snapshot = gate.snapshot(group.longValue());
            if (!gate.enabled(snapshot) || !gate.suspended(snapshot)) {
                synchronized (lock) {
                    suspensionNotified.remove(group);
                }
            }
        }
    }

    /** 挂起后清空本群待发队列；被清项只记 skipped 审计，不发提示。 */
    private void clearQueueOnSuspend(long groupId) {
        List<PendingCall> cleared = new ArrayList<PendingCall>();
        synchronized (lock) {
            GroupQueue queue = queues.get(Long.valueOf(groupId));
            if (queue != null && !queue.waiting.isEmpty()) {
                cleared.addAll(queue.waiting);
                globalWaiting -= queue.waiting.size();
                queue.waiting.clear();
            }
        }
        for (PendingCall pending : cleared) {
            traces.write(pending.request, RESULT_SKIPPED, "", "", 0, 0L, "", "", false, "");
        }
    }

    private void send(long groupId, String narration, boolean degraded, String degradeReason) {
        try {
            sink.send(groupId, narration == null ? "" : narration, degraded,
                    degradeReason == null ? "" : degradeReason);
        } catch (RuntimeException error) {
            log.warn("AI 叙述发送失败，群 " + groupId + "：" + error);
        }
    }

    // ------------------------------------------------------------------
    // 去重与门禁
    // ------------------------------------------------------------------

    private boolean registerDedupeLocked(AiCallRequest request) {
        long now = clock.nowMillis();
        long cacheMillis = Math.max(0L, config.aiDedupeCacheSeconds()) * 1000L;
        Iterator<Map.Entry<String, Long>> iterator = dedupeExpiry.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (entry.getValue().longValue() <= now) {
                iterator.remove();
            }
        }
        String key = request.dedupeKey();
        Long expiry = dedupeExpiry.get(key);
        if (expiry != null && expiry.longValue() > now) {
            return false;
        }
        dedupeExpiry.put(key, Long.valueOf(now + cacheMillis));
        return true;
    }

    private boolean globallyEnabled() {
        return config.aiHostEnabled();
    }

    private boolean accepting() {
        synchronized (lock) {
            return !shutdown;
        }
    }

    /** 全局开关、本局 AI 开关、进行中与挂起状态四重门禁（只判定，不发提示）。 */
    private boolean allowsWithoutNotice(long groupId) {
        if (!globallyEnabled()) {
            return false;
        }
        SessionSnapshot snapshot = gate.snapshot(groupId);
        return gate.enabled(snapshot) && gate.running(snapshot) && !gate.suspended(snapshot);
    }

    /**
     * 触发入口门禁：挂起期间不执行，但每个挂起周期只回一次挂起提示（§9.5），
     * 避免 KP 的每条指令都静默消失。
     */
    private boolean allowsOrNotify(long groupId) {
        if (allowsWithoutNotice(groupId)) {
            // 挂起已解除：清掉标记，下一轮挂起周期重新允许提示。
            synchronized (lock) {
                suspensionNotified.remove(Long.valueOf(groupId));
            }
            return true;
        }
        if (!globallyEnabled()) {
            return false;
        }
        SessionSnapshot snapshot = gate.snapshot(groupId);
        if (!gate.enabled(snapshot) || !gate.suspended(snapshot)) {
            return false;
        }
        long until = snapshot.aiSuspendedUntil();
        if (until <= clock.nowMillis()) {
            return false;
        }
        boolean first;
        synchronized (lock) {
            first = suspensionNotified.add(Long.valueOf(groupId));
        }
        if (first) {
            notifySuspended(groupId);
        }
        return false;
    }

    private boolean isExpired(PendingCall pending) {
        return clock.nowMillis() - pending.enqueuedAt > queueMaxAgeMillis();
    }

    private long deadlineMillis() {
        return Math.max(1, config.aiDeadlineSeconds()) * 1000L;
    }

    private long mergeWindowMillis() {
        return Math.max(1, config.aiMergeWindowSeconds()) * 1000L;
    }

    private long queueMaxAgeMillis() {
        return Math.max(1, config.aiQueueMaxAgeSeconds()) * 1000L;
    }

    private void cancel(ScheduledTask task) {
        if (task == null) {
            return;
        }
        try {
            task.cancel();
        } catch (RuntimeException error) {
            log.warn("AI 合并窗口定时器取消失败：" + error);
        }
    }

    private static ThreadFactory daemonFactory(final String prefix) {
        final AtomicInteger counter = new AtomicInteger();
        return new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        };
    }
}
