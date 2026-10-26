package org.moboxlab.mbb.trpg.ai;

import java.util.Collections;
import java.util.Locale;

import org.moboxlab.mbb.trpg.config.TrpgConfig;
import org.moboxlab.mbb.trpg.session.SessionSnapshot;

/**
 * AI 主持服务（规格 §9）：全局开关、{@code /trpg ai} 判定与真实调用门面。
 *
 * <p>本类不直接写局面也不发消息：触发交给 {@link AiHostEngine}（队列、合并窗口、降级、审计），
 * 发送交给 {@link AiNarrationSink}，失败计数交给 {@link AiFailureRecorder}。
 * AI 子状态（开关、失败计数、挂起）统一经 {@code SessionService} 在群锁内 CAS 落库。</p>
 */
public final class TrpgAiHostService implements AiTriggerSink {

    /** {@code /trpg ai on} 已开启。 */
    public static final String DETAIL_ENABLED = "已开启 AI 主持";

    /** {@code /trpg ai on} 清除挂起后开启。 */
    public static final String DETAIL_RESUMED = "已清除挂起并重新开启 AI 主持";

    /** {@code /trpg ai off} 已关闭。 */
    public static final String DETAIL_OFF = "已关闭 AI 主持";

    /** {@code /trpg ai off} 本来就关着。 */
    public static final String DETAIL_ALREADY_OFF = "本局 AI 主持保持关闭";

    /** 全局未启用时的拒绝说明（§9.2：不新增拒绝类别，走「当前状态不可用」）。 */
    public static final String DETAIL_GLOBAL_OFF = "当前状态不可用：AI 主持未在配置中启用";

    /** 写库失败时的拒绝说明。 */
    public static final String DETAIL_WRITE_FAILED = "状态写入失败，请重试";

    private final TrpgConfig config;
    private SessionView sessionView;
    private AiHostEngine engine;

    public TrpgAiHostService(TrpgConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("配置不能为空");
        }
        this.config = config;
    }

    /** 全局开关：只在插件加载时读取配置（§9.2），运行期不再重读。 */
    public boolean globallyEnabled() {
        return config.aiHostEnabled();
    }

    /** 解析开关词：on / 开 为开启，off / 关 为关闭，其余返回 null。 */
    public static Boolean parseWord(String rawWord) {
        String word = rawWord == null ? "" : rawWord.trim().toLowerCase(Locale.ROOT);
        if ("on".equals(word) || "开".equals(word)) {
            return Boolean.TRUE;
        }
        if ("off".equals(word) || "关".equals(word)) {
            return Boolean.FALSE;
        }
        return null;
    }

    /** 判定 {@code /trpg ai} 的参数分支；权限与局面状态由局面服务先行判定。 */
    public AiToggle toggle(String rawWord) {
        Boolean parsed = parseWord(rawWord);
        if (parsed == null) {
            return AiToggle.USAGE;
        }
        return parsed.booleanValue() ? AiToggle.ON_ACK : AiToggle.OFF_ACK;
    }

    /**
     * 绑定局面只读视图（§9.11 的测试缝）：提示词装配时通过它取快照。
     *
     * <p>允许重复绑定，最后一次生效。</p>
     */
    public void bindSessionView(SessionView view) {
        this.sessionView = view;
    }

    /** 绑定引擎；未装配时为桩（v1 语义：零调用）。 */
    public void bindEngine(AiHostEngine engine) {
        this.engine = engine;
    }

    /** 已绑定的局面只读视图；未装配时为 null。 */
    public SessionView sessionView() {
        return sessionView;
    }

    /** 读本群快照；未绑定视图时返回空局面。 */
    public SessionSnapshot snapshot(long groupId) {
        if (sessionView == null) {
            return SessionSnapshot.none(groupId);
        }
        return sessionView.readSnapshot(groupId);
    }

    @Override
    public void fire(AiTrigger trigger, long groupId, long eventSeq, String eventText) {
        if (engine == null || trigger == null || groupId <= 0L) {
            return;
        }
        SessionSnapshot snapshot = snapshot(groupId);
        long seq = eventSeq > 0L ? eventSeq : snapshot.sceneVersion();
        if (seq <= 0L) {
            return;
        }
        AiCallRequest request = new AiCallRequest(groupId, snapshot.sessionId(), trigger,
                Collections.singletonList(Long.valueOf(seq)), seq, seq, snapshot.sceneVersion(),
                false, Collections.singletonList(eventText == null ? "" : eventText));
        engine.submit(request);
    }

    @Override
    public void fireNow(AiTrigger trigger, long groupId) {
        fire(trigger, groupId, true);
    }

    private void fire(AiTrigger trigger, long groupId, boolean immediate) {
        if (engine == null || trigger == null || groupId <= 0L) {
            return;
        }
        SessionSnapshot snapshot = snapshot(groupId);
        long seq = snapshot.sceneVersion();
        AiCallRequest request = new AiCallRequest(groupId, snapshot.sessionId(), trigger,
                Collections.singletonList(Long.valueOf(seq)), seq, seq, seq,
                immediate, Collections.<String>emptyList());
        engine.submit(request);
    }

    /** 开关被重新打开时清掉本局去重缓存（§9.2）。 */
    @Override
    public void onEnabled(long sessionNo) {
        if (engine != null) {
            engine.clearDedupe(sessionNo);
        }
    }

    /** 每秒 tick：结算到期合并窗口、清理超龄队列（由插件调度器驱动）。 */
    public void tick() {
        if (engine != null) {
            engine.tick();
        }
    }

    /** 插件停用：停止接受新请求。 */
    public void shutdown() {
        if (engine != null) {
            engine.shutdown();
        }
    }
}
