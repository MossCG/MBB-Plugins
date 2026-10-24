package org.moboxlab.mbb.trpg.command;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import org.moboxlab.mbb.trpg.ai.AiTrigger;
import org.moboxlab.mbb.trpg.card.CardGenerator;
import org.moboxlab.mbb.trpg.present.TrpgMessageTemplates;
import org.moboxlab.mbb.trpg.script.Scene;
import org.moboxlab.mbb.trpg.script.Script;
import org.moboxlab.mbb.trpg.session.MemberView;
import org.moboxlab.mbb.trpg.session.SessionActor;
import org.moboxlab.mbb.trpg.session.SessionResult;
import org.moboxlab.mbb.trpg.session.SessionState;
import org.moboxlab.mbb.trpg.session.SessionSnapshot;
import org.moboxlab.mbb.trpg.store.CardData;

/**
 * 局面生命周期 handler：start / join / leave / begin / end / resume。
 *
 * <p>权限、状态、参数与冷却全部由 {@link org.moboxlab.mbb.trpg.session.SessionService}
 * 判定；本类只负责把参数归一化、调用服务、按冻结样例拼回执。</p>
 */
final class LifecycleCommandHandler {

    private LifecycleCommandHandler() {
    }

    static Map<TrpgSubcommand, TrpgSubcommandHandler> handlers() {
        Map<TrpgSubcommand, TrpgSubcommandHandler> map =
                new EnumMap<TrpgSubcommand, TrpgSubcommandHandler>(TrpgSubcommand.class);
        map.put(TrpgSubcommand.START, new TrpgSubcommandHandler() {
            @Override
            public void handle(TrpgCommandContext context, SessionActor actor, String[] params) {
                start(context, actor, params);
            }
        });
        map.put(TrpgSubcommand.JOIN, new TrpgSubcommandHandler() {
            @Override
            public void handle(TrpgCommandContext context, SessionActor actor, String[] params) {
                join(context, actor);
            }
        });
        map.put(TrpgSubcommand.LEAVE, new TrpgSubcommandHandler() {
            @Override
            public void handle(TrpgCommandContext context, SessionActor actor, String[] params) {
                leave(context, actor);
            }
        });
        map.put(TrpgSubcommand.BEGIN, new TrpgSubcommandHandler() {
            @Override
            public void handle(TrpgCommandContext context, SessionActor actor, String[] params) {
                begin(context, actor);
            }
        });
        map.put(TrpgSubcommand.END, new TrpgSubcommandHandler() {
            @Override
            public void handle(TrpgCommandContext context, SessionActor actor, String[] params) {
                end(context, actor);
            }
        });
        map.put(TrpgSubcommand.RESUME, new TrpgSubcommandHandler() {
            @Override
            public void handle(TrpgCommandContext context, SessionActor actor, String[] params) {
                resume(context, actor);
            }
        });
        return Collections.unmodifiableMap(map);
    }

    static void start(TrpgCommandContext context, SessionActor actor, String[] params) {
        String scriptId = params.length > 1 && params[1] != null ? params[1].trim() : "";
        SessionResult result = context.sessions().start(actor, scriptId);
        if (!context.accept(actor, result)) {
            return;
        }
        SessionSnapshot snapshot = result.snapshot();
        Script script = context.loadScript(snapshot.scriptId());
        context.reply(actor, TrpgMessageTemplates.recruit(script, snapshot.scriptId(),
                context.nameOf(actor.groupId(), snapshot.kpUserId()), null));
    }

    static void join(TrpgCommandContext context, SessionActor actor) {
        SessionSnapshot before = context.sessions().readSnapshot(actor.groupId());
        // 先看局面能否加入，避免被拒时白掷一副新角色卡（随机源不该被无效请求消耗）。
        boolean canCreateCard = before.active() && before.state() == SessionState.RECRUITING;
        MemberView existing = canCreateCard ? before.member(actor.userId()) : null;
        boolean rejoin = existing != null && !existing.inSession();
        CardData card = canCreateCard && !rejoin ? context.cards().generate(context.loadScript(before.scriptId())) : null;
        SessionResult result = context.sessions().join(actor, card);
        if (!context.accept(actor, result)) {
            return;
        }
        SessionSnapshot snapshot = result.snapshot();
        Script script = context.loadScript(snapshot.scriptId());
        String name = context.nameOf(actor.groupId(), actor.userId());
        CardData shown = card == null ? (existing == null ? null : existing.card()) : card;
        context.reply(actor, TrpgMessageTemplates.joined(name, TrpgCommandContext.insideCount(snapshot),
                script == null ? 0 : script.maxPlayers(), CardGenerator.professionLabel(shown)));
        if (card != null) {
            context.sendCardPrivately(actor, actor.userId(), name, card);
            context.presenter().clearPrivateFailures(actor.userId());
        }
    }

    static void leave(TrpgCommandContext context, SessionActor actor) {
        SessionResult result = context.sessions().leave(actor);
        if (!context.accept(actor, result)) {
            return;
        }
        SessionSnapshot snapshot = result.snapshot();
        Script script = context.loadScript(snapshot.scriptId());
        context.reply(actor, TrpgMessageTemplates.left(context.nameOf(actor.groupId(), actor.userId()),
                TrpgCommandContext.insideCount(snapshot), script == null ? 0 : script.maxPlayers(), true));
    }

    static void begin(TrpgCommandContext context, SessionActor actor) {
        SessionResult result = context.sessions().begin(actor);
        if (!context.accept(actor, result)) {
            return;
        }
        SessionSnapshot snapshot = result.snapshot();
        Script script = context.loadScript(snapshot.scriptId());
        Scene opening = script == null ? null : script.sceneAt(0);
        context.reply(actor, TrpgMessageTemplates.begin(script, context.nameOf(actor.groupId(), snapshot.kpUserId()),
                context.playerNames(snapshot, snapshot.kpUserId()),
                opening == null ? snapshot.sceneTitle() : opening.title()));
        context.presenter().sendScene(actor.groupId(), snapshot.sessionId(), context.sceneCard(snapshot, script));
        context.fireAiNow(AiTrigger.BEGIN, actor.groupId());
    }

    static void end(TrpgCommandContext context, SessionActor actor) {
        SessionResult result = context.sessions().end(actor);
        if (!context.accept(actor, result)) {
            return;
        }
        if (!result.confirmationPending()) {
            // 结束消息由 Presenter 消费 ENDED 事件发出，指令层不重复回执。
            return;
        }
        SessionSnapshot snapshot = result.snapshot();
        Script script = context.loadScript(snapshot.scriptId());
        String method = actor.userId() == snapshot.kpUserId() ? "KP 确认" : "群管理员强制";
        context.reply(actor, TrpgMessageTemplates.endConfirm(script, method, context.config().confirmWindowSeconds()));
    }

    static void resume(TrpgCommandContext context, SessionActor actor) {
        SessionResult result = context.sessions().resume(actor);
        if (!context.accept(actor, result)) {
            return;
        }
        context.reply(actor, TrpgMessageTemplates.notice(
                TrpgCommandContext.safeDetail(result, "已确认恢复，可以继续")));
    }
}
