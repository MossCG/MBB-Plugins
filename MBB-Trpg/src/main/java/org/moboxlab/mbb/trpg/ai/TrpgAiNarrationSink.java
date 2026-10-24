package org.moboxlab.mbb.trpg.ai;

import java.util.Collections;

import org.moboxlab.mbb.trpg.present.TrpgMessageTemplates;
import org.moboxlab.mbb.trpg.present.TrpgNameResolver;
import org.moboxlab.mbb.trpg.present.TrpgPresenter;
import org.moboxlab.mbb.trpg.session.SessionSnapshot;

/**
 * AI 叙述发送端口（§9.7）：把引擎产出的叙述或降级提示发到发起群。
 *
 * <p>发送在引擎工作线程里发生，不持有群锁；发送失败只由呈现层记录，不回流引擎。
 * 叙述落款需要 KP 名，因此本类只读局面快照与人名解析端口，不改任何状态。</p>
 */
public final class TrpgAiNarrationSink implements AiNarrationSink {

    private final TrpgPresenter presenter;
    private final SessionView sessionView;
    private final TrpgNameResolver names;

    /**
     * 生产装配：需要局面视图与姓名解析来给叙述落款（§9.7）。
     */
    public TrpgAiNarrationSink(TrpgPresenter presenter, SessionView sessionView,
                               TrpgNameResolver names) {
        if (presenter == null) {
            throw new IllegalArgumentException("呈现层不能为空");
        }
        this.presenter = presenter;
        this.sessionView = sessionView;
        this.names = names;
    }

    @Override
    public void send(long groupId, String narration, boolean degraded, String degradeReason) {
        if (degraded) {
            String template = narration == null ? "" : narration.trim();
            if (!template.isEmpty()) {
                // §9.5：降级时先发剧本模板叙述，再发「暂时不可用」提示。
                presenter.sendGroup(groupId, TrpgMessageTemplates.aiNarration(null, template,
                        Collections.<String>emptyList(), kpName(groupId), true));
            }
            presenter.sendGroup(groupId, TrpgMessageTemplates.aiDegraded(degradeReason));
            return;
        }
        presenter.sendGroup(groupId, TrpgMessageTemplates.aiNarrated(narration, kpName(groupId)));
    }

    @Override
    public void notifySuspended(long groupId) {
        presenter.sendGroup(groupId, TrpgMessageTemplates.aiSuspended());
    }

    private String kpName(long groupId) {
        if (sessionView == null || names == null) {
            return "KP";
        }
        SessionSnapshot snapshot = sessionView.readSnapshot(groupId);
        if (snapshot == null || snapshot.kpUserId() <= 0L) {
            return "KP";
        }
        return names.name(groupId, snapshot.kpUserId());
    }
}
