package org.moboxlab.mbb.trpg.ai;

import org.moboxlab.mbb.trpg.config.TrpgConfig;
import org.moboxlab.mbb.trpg.script.Scene;
import org.moboxlab.mbb.trpg.script.Script;
import org.moboxlab.mbb.trpg.script.ScriptCatalog;
import org.moboxlab.mbb.trpg.session.SessionSnapshot;

/** 提示词装配与模板叙述：按当前场景取剧本装配请求，取不到剧本时退回空串。 */
final class AiPromptBuilder {

    private final TrpgConfig config;
    private final SessionView view;
    private final AiEngineLog log;
    private volatile ScriptCatalog catalog;

    AiPromptBuilder(TrpgConfig config, SessionView view, AiEngineLog log) {
        this.config = config;
        this.view = view;
        this.log = log;
    }

    /** 绑定剧本目录，用于按 {@code snapshot.scriptId()} 取剧本；未绑定时按无剧本装配。 */
    void bindScriptCatalog(ScriptCatalog catalog) {
        this.catalog = catalog;
    }

    /** 装配一次 AI 调用请求；读剧本失败只告警，退化为无剧本装配。 */
    AiRequest buildRequest(AiCallRequest request, SessionSnapshot snapshot) {
        Script script = null;
        ScriptCatalog current = catalog;
        if (current != null) {
            try {
                script = current.get(snapshot.scriptId());
            } catch (RuntimeException error) {
                log.warn("读取剧本失败，剧本 " + snapshot.scriptId() + "：" + error);
            }
        }
        AiPromptParts parts = AiPromptAssembler.fit(snapshot, script, request.recentEventLines(),
                config.aiPromptBudgetChars(), config.aiDynamicMinChars());
        return new AiRequest(request.trigger().traceName(), parts.stablePrefix(), parts.cacheable(),
                parts.dynamic(), config.aiProviderTimeoutSeconds(), 0, 0.7D, request.sessionKey());
    }

    /**
     * 取当前场景的模板叙述（§9.5）：优先 {@code fallbackNarration}，没有则退回 {@code narration}。
     *
     * <p>降级时必须先发模板叙述再发提示；取不到剧本时返回空串，由发送端口只发提示。</p>
     */
    String fallbackNarration(long groupId) {
        ScriptCatalog current = catalog;
        if (current == null) {
            return "";
        }
        try {
            SessionSnapshot snapshot = view.readSnapshot(groupId);
            if (snapshot == null) {
                return "";
            }
            Script script = current.get(snapshot.scriptId());
            if (script == null || script.scenes().isEmpty()) {
                return "";
            }
            int index = snapshot.sceneIndex();
            if (index < 0 || index >= script.scenes().size()) {
                return "";
            }
            Scene scene = script.scenes().get(index);
            String text = scene.fallbackNarration();
            return text.isEmpty() ? scene.narration() : text;
        } catch (RuntimeException error) {
            log.warn("读取模板叙述失败，群 " + groupId + "：" + error);
            return "";
        }
    }
}
