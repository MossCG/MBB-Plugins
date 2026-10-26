package org.moboxlab.mbb.trpg.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.moboxlab.mbb.trpg.present.TrpgMessageTemplates;
import org.moboxlab.mbb.trpg.script.Script;
import org.moboxlab.mbb.trpg.session.LogPage;
import org.moboxlab.mbb.trpg.session.MemberView;
import org.moboxlab.mbb.trpg.session.SessionActor;
import org.moboxlab.mbb.trpg.session.SessionReject;
import org.moboxlab.mbb.trpg.session.SessionResult;
import org.moboxlab.mbb.trpg.session.SessionSnapshot;
import org.moboxlab.mbb.trpg.session.SessionState;
import org.moboxlab.mbb.trpg.store.CardData;

/**
 * 只读与查卡 handler：status / help / pc / pc reroll / pc set / log。
 *
 * <p>参数错误一律在服务已放行（权限、状态通过）之后才回报，保持 §5.5 的判定顺序；
 * help 与其余查询共用 1 秒插件内冷却。</p>
 */
final class ReadOnlyCommandHandler {

    private static final String ACTION_HELP = "help";
    private static final String KEY_PC_TARGET = "cmd-pc-target";
    private static final String KEY_PC_SET = "cmd-pc-set";

    private ReadOnlyCommandHandler() {
    }

    static Map<TrpgSubcommand, TrpgSubcommandHandler> handlers() {
        Map<TrpgSubcommand, TrpgSubcommandHandler> map =
                new EnumMap<TrpgSubcommand, TrpgSubcommandHandler>(TrpgSubcommand.class);
        map.put(TrpgSubcommand.STATUS, new TrpgSubcommandHandler() {
            @Override
            public void handle(TrpgCommandContext context, SessionActor actor, String[] params) {
                status(context, actor);
            }
        });
        map.put(TrpgSubcommand.HELP, new TrpgSubcommandHandler() {
            @Override
            public void handle(TrpgCommandContext context, SessionActor actor, String[] params) {
                help(context, actor, params);
            }
        });
        map.put(TrpgSubcommand.PC, new TrpgSubcommandHandler() {
            @Override
            public void handle(TrpgCommandContext context, SessionActor actor, String[] params) {
                viewCard(context, actor, params);
            }
        });
        map.put(TrpgSubcommand.PC_REROLL, new TrpgSubcommandHandler() {
            @Override
            public void handle(TrpgCommandContext context, SessionActor actor, String[] params) {
                rerollCard(context, actor);
            }
        });
        map.put(TrpgSubcommand.PC_SET, new TrpgSubcommandHandler() {
            @Override
            public void handle(TrpgCommandContext context, SessionActor actor, String[] params) {
                setAttribute(context, actor, params);
            }
        });
        map.put(TrpgSubcommand.LOG, new TrpgSubcommandHandler() {
            @Override
            public void handle(TrpgCommandContext context, SessionActor actor, String[] params) {
                log(context, actor, params);
            }
        });
        return Collections.unmodifiableMap(map);
    }

    static void status(TrpgCommandContext context, SessionActor actor) {
        SessionResult result = context.sessions().status(actor);
        if (!context.accept(actor, result)) {
            return;
        }
        context.reply(actor, TrpgMessageTemplates.status(context.statusView(result.snapshot())));
    }

    static void help(TrpgCommandContext context, SessionActor actor, String[] params) {
        if (context.cooling(actor, ACTION_HELP, context.config().defaultCooldownSeconds())) {
            return;
        }
        List<String> lines = new ArrayList<String>();
        if (params.length > 1 && params[1] != null) {
            TrpgSubcommand single = TrpgSubcommand.resolve(params[1]);
            if (single != null) {
                lines.add(single.label() + (single.usage().isEmpty() ? "" : " " + single.usage()));
                lines.add(single.implemented() ? "已启用。" : "该指令将在后续版本启用。");
                context.mark(actor, ACTION_HELP);
                context.reply(actor, TrpgMessageTemplates.help(lines));
                return;
            }
        }
        List<String> upcoming = new ArrayList<String>();
        for (TrpgSubcommand item : TrpgSubcommand.values()) {
            if (!item.implemented()) {
                upcoming.add(item.label());
                continue;
            }
            lines.add(item.label() + (item.usage().isEmpty() ? "" : " " + item.usage()));
        }
        if (!upcoming.isEmpty()) {
            lines.add("后续版本启用：" + TrpgCommandContext.join(upcoming, "、"));
        }
        context.mark(actor, ACTION_HELP);
        context.reply(actor, TrpgMessageTemplates.help(lines));
    }

    static void viewCard(TrpgCommandContext context, SessionActor actor, String[] params) {
        long targetUserId = actor.userId();
        if (params.length > 1) {
            long parsed = TrpgCommandService.parseUserId(params[1]);
            if (parsed <= 0L) {
                // 先让服务判权限与状态，再回参数错误（B4）。
                if (!context.accept(actor, context.sessions().cardGate(actor))) {
                    return;
                }
                String raw = params[1] == null ? "" : params[1].trim();
                context.rejectError(actor, KEY_PC_TARGET, "参数不对：无法识别玩家 " + raw,
                        "用法：/trpg pc [@玩家]");
                return;
            }
            targetUserId = parsed;
        }
        SessionResult result = context.sessions().viewCard(actor, targetUserId);
        if (!context.accept(actor, result)) {
            return;
        }
        SessionSnapshot snapshot = result.snapshot();
        MemberView target = snapshot.member(targetUserId);
        if (target == null) {
            context.reply(actor, TrpgMessageTemplates.error("角色卡不可用", "请确认该玩家在本局内。"));
            return;
        }
        context.sendCardPrivately(actor, actor.userId(), context.nameOf(actor.groupId(), targetUserId),
                target.card());
    }

    static void rerollCard(TrpgCommandContext context, SessionActor actor) {
        SessionSnapshot before = context.sessions().readSnapshot(actor.groupId());
        boolean canGenerate = before.active() && before.state() == SessionState.RECRUITING
                && before.insideMember(actor.userId());
        CardData generated = canGenerate ? context.cards().generate(context.loadScript(before.scriptId())) : null;
        SessionResult result = context.sessions().rerollCard(actor, generated);
        if (!context.accept(actor, result)) {
            return;
        }
        SessionSnapshot snapshot = result.snapshot();
        MemberView member = snapshot.member(actor.userId());
        CardData actual = member == null ? generated : member.card();
        String name = context.nameOf(actor.groupId(), actor.userId());
        context.reply(actor, TrpgMessageTemplates.notice(
                TrpgCommandContext.safeDetail(result, "已重新生成角色卡")));
        context.sendCardPrivately(actor, actor.userId(), name, actual);
    }

    static void setAttribute(TrpgCommandContext context, SessionActor actor, String[] params) {
        if (params.length < 4) {
            if (!context.accept(actor, context.sessions().cardActionGate(actor))) {
                return;
            }
            context.rejectError(actor, KEY_PC_SET, "参数不对：微调需要 <字段> <值>",
                    "用法：/trpg pc set STR 70（八属性 15～90）。");
            return;
        }
        SessionResult result = context.sessions().setAttribute(actor, params[2], params[3]);
        if (!context.accept(actor, result)) {
            return;
        }
        context.reply(actor, TrpgMessageTemplates.notice(
                TrpgCommandContext.safeDetail(result, "已调整角色卡")));
    }

    static void log(TrpgCommandContext context, SessionActor actor, String[] params) {
        String rawPage = params.length > 1 && params[1] != null ? params[1].trim() : "";
        LogPage page = context.logs().page(actor, rawPage);
        if (!page.ok()) {
            if (page.replySuppressed()) {
                return;
            }
            SessionReject reject = page.reject();
            if (reject == SessionReject.COOLING) {
                context.reply(actor, TrpgMessageTemplates.notice(page.detail()));
            } else {
                context.reply(actor, TrpgMessageTemplates.error(page.detail(),
                        TrpgCommandContext.nextHint(reject)));
            }
            return;
        }
        Script script = context.loadScript(page.scriptId());
        context.reply(actor, TrpgMessageTemplates.logPage(script == null ? page.scriptId() : script.name(),
                page.page(), page.totalPages(), page.total(), context.logEntries(actor, page.rows())));
    }
}
