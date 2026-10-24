package org.moboxlab.mbb.trpg.command;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import org.moboxlab.mbb.trpg.ai.AiTrigger;
import org.moboxlab.mbb.trpg.present.TrpgCheckView;
import org.moboxlab.mbb.trpg.present.TrpgMessageTemplates;
import org.moboxlab.mbb.trpg.script.Clue;
import org.moboxlab.mbb.trpg.script.Script;
import org.moboxlab.mbb.trpg.session.PlayResult;
import org.moboxlab.mbb.trpg.session.SessionActor;
import org.moboxlab.mbb.trpg.session.SessionOutcomes;
import org.moboxlab.mbb.trpg.session.SessionResult;
import org.moboxlab.mbb.trpg.session.SessionSnapshot;
import org.moboxlab.mbb.trpg.session.SessionState;
import org.moboxlab.mbb.trpg.store.CardData;

/**
 * KP 局内 handler：scene / check / opposed / san / adjust / invite / take / skip / ai。
 *
 * <p>参数在这里归一成标量，权限、状态、参数范围与冷却由 {@link org.moboxlab.mbb.trpg.session.SessionService}
 * 判定，本类只按冻结样例拼回执；线索全文与触发器提示只私聊 KP，绝不发到群里。</p>
 */
final class KpCommandHandler {

    private static final String VALUE_OPPOSED_LABEL = "目标值";

    private KpCommandHandler() {
    }

    static Map<TrpgSubcommand, TrpgSubcommandHandler> handlers() {
        Map<TrpgSubcommand, TrpgSubcommandHandler> map =
                new EnumMap<TrpgSubcommand, TrpgSubcommandHandler>(TrpgSubcommand.class);
        map.put(TrpgSubcommand.SCENE, new TrpgSubcommandHandler() {
            @Override
            public void handle(TrpgCommandContext context, SessionActor actor, String[] params) {
                scene(context, actor, params);
            }
        });
        map.put(TrpgSubcommand.CHECK, new TrpgSubcommandHandler() {
            @Override
            public void handle(TrpgCommandContext context, SessionActor actor, String[] params) {
                check(context, actor, params);
            }
        });
        map.put(TrpgSubcommand.OPPOSED, new TrpgSubcommandHandler() {
            @Override
            public void handle(TrpgCommandContext context, SessionActor actor, String[] params) {
                opposed(context, actor, params);
            }
        });
        map.put(TrpgSubcommand.SAN, new TrpgSubcommandHandler() {
            @Override
            public void handle(TrpgCommandContext context, SessionActor actor, String[] params) {
                san(context, actor, params);
            }
        });
        map.put(TrpgSubcommand.ADJUST, new TrpgSubcommandHandler() {
            @Override
            public void handle(TrpgCommandContext context, SessionActor actor, String[] params) {
                adjust(context, actor, params);
            }
        });
        map.put(TrpgSubcommand.INVITE, new TrpgSubcommandHandler() {
            @Override
            public void handle(TrpgCommandContext context, SessionActor actor, String[] params) {
                invite(context, actor, params);
            }
        });
        map.put(TrpgSubcommand.TAKE, new TrpgSubcommandHandler() {
            @Override
            public void handle(TrpgCommandContext context, SessionActor actor, String[] params) {
                take(context, actor, params);
            }
        });
        map.put(TrpgSubcommand.SKIP, new TrpgSubcommandHandler() {
            @Override
            public void handle(TrpgCommandContext context, SessionActor actor, String[] params) {
                skip(context, actor, params);
            }
        });
        map.put(TrpgSubcommand.AI, new TrpgSubcommandHandler() {
            @Override
            public void handle(TrpgCommandContext context, SessionActor actor, String[] params) {
                ai(context, actor, params);
            }
        });
        return Collections.unmodifiableMap(map);
    }

    static void scene(TrpgCommandContext context, SessionActor actor, String[] params) {
        PlayResult<SessionOutcomes.Scene> played = context.sessions().scene(actor, joinFrom(params, 1));
        if (!context.accept(actor, played.result())) {
            return;
        }
        SessionOutcomes.Scene detail = played.detail();
        SessionSnapshot snapshot = played.result().snapshot();
        Script script = context.loadScript(snapshot.scriptId());
        context.presenter().sendScene(actor.groupId(), snapshot.sessionId(),
                context.sceneCard(snapshot, script));
        for (String clueKey : detail.newClueKeys()) {
            Clue clue = script == null ? null : script.clueById(clueKey);
            if (clue == null) {
                continue;
            }
            context.reply(actor, TrpgMessageTemplates.clue(clue));
            // 线索全文只私聊 KP，绝不回退到群内（§5.2 第 3 条）。
            context.presenter().sendPrivateSecret(actor.groupId(), snapshot.kpUserId(),
                    clue.unlockedText());
        }
        sendKpHint(context, snapshot.kpUserId(), detail.sceneTitle(), detail.triggerHint());
        // §9.9 触发枚举没有 clue：新线索随场景叙述一并交代，一次推进只发一条叙述。
        context.fireAiNow(AiTrigger.SCENE, actor.groupId());
    }

    static void check(TrpgCommandContext context, SessionActor actor, String[] params) {
        int index = 1;
        long targetUserId = 0L;
        String first = arg(params, index);
        // 只有提及形式才算「代谁掷」，纯数字留给技能名，避免 /trpg check 60 侦查 把 60 当玩家。
        if (isMentionToken(first)) {
            long parsed = TrpgCommandService.parseUserId(first);
            if (parsed <= 0L) {
                context.rejectError(actor, "check-target", "参数不对：无法识别玩家 " + first,
                        "用法：/trpg check <技能> 或 /trpg check <@玩家> <技能>");
                return;
            }
            targetUserId = parsed;
            index++;
        }
        String skill = arg(params, index);
        String diceKind = arg(params, index + 1);
        String diceCount = arg(params, index + 2);
        PlayResult<SessionOutcomes.Check> played = context.sessions()
                .check(actor, targetUserId, skill, diceKind, diceCount);
        if (!context.accept(actor, played.result())) {
            return;
        }
        SessionOutcomes.Check detail = played.detail();
        String rollerName = context.nameOf(actor.groupId(), detail.rollerUserId());
        context.reply(actor, TrpgMessageTemplates.check(TrpgCheckView.skill(rollerName,
                detail.skillLabel(), detail.rolled(), detail.target(), detail.degree(),
                detail.note())));
        sendKpHint(context, played.result().snapshot().kpUserId(), detail.skillLabel(),
                detail.triggerHint());
        // KP 或 BOT_ADMIN 代掷属于「立即触发」（§9.3），玩家自助检定才进合并窗口。
        if (actor.userId() == played.result().snapshot().kpUserId() || actor.botAdmin()) {
            context.fireAiNow(AiTrigger.CHECK, actor.groupId());
        } else {
            long eventSeq = played.result().snapshot().sceneVersion();
            context.fireAi(AiTrigger.CHECK, actor.groupId(), eventSeq,
                    rollerName + " 的" + detail.skillLabel() + "检定：" + detail.rolled() + "/"
                            + detail.target());
        }
    }

    static void opposed(TrpgCommandContext context, SessionActor actor, String[] params) {
        String leftToken = arg(params, 1);
        // 左侧必须是提及形式：纯数字在这里不是玩家号，按「缺少对抗双方」拒绝。
        long leftUserId = isMentionToken(leftToken) ? TrpgCommandService.parseUserId(leftToken) : -1L;
        String leftSkill = arg(params, 2);
        int index = "vs".equalsIgnoreCase(arg(params, 3)) ? 4 : 3;
        String rightToken = arg(params, index);
        // 纯数字在对抗右侧表示目标值（0～99），只有提及形式才按玩家解析，避免把「50」当成用户号。
        long rightUserId = isMentionToken(rightToken) ? TrpgCommandService.parseUserId(rightToken) : -1L;
        String rightSkill = rightUserId > 0L ? arg(params, index + 1) : "";
        int rightValue = rightUserId > 0L ? -1 : intValue(rightToken, -1);
        PlayResult<SessionOutcomes.Opposed> played = context.sessions()
                .opposed(actor, leftUserId, leftSkill, rightUserId, rightSkill, rightValue);
        if (!context.accept(actor, played.result())) {
            return;
        }
        SessionOutcomes.Opposed detail = played.detail();
        TrpgCheckView left = TrpgCheckView.skill(
                context.nameOf(actor.groupId(), detail.leftUserId()), detail.leftLabel(),
                detail.leftRolled(), detail.leftTarget(), detail.leftDegree(), "");
        TrpgCheckView right = TrpgCheckView.skill(rightName(context, actor.groupId(), detail),
                detail.rightLabel(), detail.rightRolled(), detail.rightTarget(), detail.rightDegree(),
                "");
        context.fireAiNow(AiTrigger.OPPOSED, actor.groupId());
        if (!detail.decided()) {
            context.reply(actor, TrpgMessageTemplates.opposedDraw(left, right, detail.tieBreakLabel()));
            return;
        }
        boolean leftWin = detail.winnerUserId() == detail.leftUserId();
        context.reply(actor, TrpgMessageTemplates.opposed(left, right, leftWin,
                detail.tieBreakLabel()));
    }

    static void san(TrpgCommandContext context, SessionActor actor, String[] params) {
        long targetUserId = TrpgCommandService.parseUserId(arg(params, 1));
        PlayResult<SessionOutcomes.San> played = context.sessions()
                .san(actor, targetUserId, arg(params, 2));
        if (!context.accept(actor, played.result())) {
            return;
        }
        SessionOutcomes.San detail = played.detail();
        String rollerName = context.nameOf(actor.groupId(), detail.rollerUserId());
        context.reply(actor, TrpgMessageTemplates.san(TrpgCheckView.san(
                rollerName, detail.rolled(), detail.target(), detail.degree(), detail.sanBefore(),
                detail.sanAfter()), detail.permanent(), detail.maddened(), detail.madnessUntil()));
        sendKpHint(context, played.result().snapshot().kpUserId(), "理智", detail.triggerHint());
        context.fireAiNow(AiTrigger.SAN, actor.groupId());
    }

    static void adjust(TrpgCommandContext context, SessionActor actor, String[] params) {
        long targetUserId = TrpgCommandService.parseUserId(arg(params, 1));
        PlayResult<SessionOutcomes.Adjust> played = context.sessions()
                .adjust(actor, targetUserId, arg(params, 2), arg(params, 3));
        if (!context.accept(actor, played.result())) {
            return;
        }
        SessionOutcomes.Adjust detail = played.detail();
        context.reply(actor, TrpgMessageTemplates.adjust(
                context.nameOf(actor.groupId(), detail.targetUserId()), detail.skill(),
                String.valueOf(detail.from()), String.valueOf(detail.to())));
    }

    static void invite(TrpgCommandContext context, SessionActor actor, String[] params) {
        long targetUserId = TrpgCommandService.parseUserId(arg(params, 1));
        // 先看局面能否邀请，避免被拒时白掷一副新角色卡。
        SessionSnapshot before = context.sessions().readSnapshot(actor.groupId());
        boolean canCreate = before.active() && before.state() == SessionState.RUNNING
                && before.kpUserId() == actor.userId() && targetUserId > 0L
                && before.member(targetUserId) == null;
        CardData card = canCreate ? context.cards().generate(context.loadScript(before.scriptId()))
                : null;
        SessionResult result = context.sessions().invite(actor, targetUserId, card);
        if (!context.accept(actor, result)) {
            return;
        }
        SessionSnapshot snapshot = result.snapshot();
        Script script = context.loadScript(snapshot.scriptId());
        String name = targetUserId > 0L ? context.nameOf(actor.groupId(), targetUserId) : "";
        context.reply(actor, TrpgMessageTemplates.invited(name,
                TrpgCommandContext.insideCount(snapshot), script == null ? 0 : script.maxPlayers()));
        if (card != null) {
            context.sendCardPrivately(actor, targetUserId, name, card);
        }
    }

    static void take(TrpgCommandContext context, SessionActor actor, String[] params) {
        long targetUserId = TrpgCommandService.parseUserId(arg(params, 1));
        SessionResult result = context.sessions().takeover(actor, targetUserId);
        if (!context.accept(actor, result)) {
            return;
        }
        context.reply(actor, TrpgMessageTemplates.takeoverAction(
                targetUserId > 0L ? context.nameOf(actor.groupId(), targetUserId) : ""));
    }

    static void skip(TrpgCommandContext context, SessionActor actor, String[] params) {
        long targetUserId = TrpgCommandService.parseUserId(arg(params, 1));
        SessionResult result = context.sessions().skip(actor, targetUserId, joinFrom(params, 2));
        if (!context.accept(actor, result)) {
            return;
        }
        context.reply(actor, TrpgMessageTemplates.skipped(
                targetUserId > 0L ? context.nameOf(actor.groupId(), targetUserId) : "",
                joinFrom(params, 2)));
    }

    static void ai(TrpgCommandContext context, SessionActor actor, String[] params) {
        String word = arg(params, 1);
        // 开关词的解析放在局面服务里，保证「权限优先于参数」的判定顺序（B4）。
        SessionResult result = context.sessions().aiState(actor, word);
        if (!context.accept(actor, result)) {
            return;
        }
        // v1.1 起开关真实落库：打开时清掉本局去重缓存（§9.2）。
        context.reply(actor, TrpgMessageTemplates.aiHost(result.detail()));
        SessionSnapshot after = result.snapshot();
        if (after != null && after.aiEnabled()) {
            context.notifyAiEnabled(after.sessionId());
        }
    }

    /** 触发器提示只私聊 KP；发送失败不重试，也不回退到群内。 */
    private static void sendKpHint(TrpgCommandContext context, long kpUserId, String subject,
                                   String hint) {
        if (kpUserId <= 0L || hint == null || hint.trim().isEmpty()) {
            return;
        }
        context.presenter().sendPrivate(kpUserId,
                TrpgMessageTemplates.trigger(subject == null ? "" : subject, hint));
    }

    private static String rightName(TrpgCommandContext context, long groupId,
                                    SessionOutcomes.Opposed detail) {
        if (detail.rightUserId() > 0L) {
            return context.nameOf(groupId, detail.rightUserId());
        }
        return VALUE_OPPOSED_LABEL;
    }

    /** 参数是否是提及形式的用户标识（不含纯数字）。 */
    private static boolean isMentionToken(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        return token.startsWith("<@") || token.startsWith("[CQ:at");
    }

    private static String arg(String[] params, int index) {
        if (params == null || index < 0 || index >= params.length || params[index] == null) {
            return "";
        }
        return params[index].trim();
    }

    private static String joinFrom(String[] params, int from) {
        StringBuilder text = new StringBuilder();
        if (params == null) {
            return "";
        }
        for (int index = from; index < params.length; index++) {
            String value = arg(params, index);
            if (value.isEmpty()) {
                continue;
            }
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(value);
        }
        return text.toString();
    }

    private static int intValue(String token, int fallback) {
        if (token == null || token.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(token.trim());
        } catch (NumberFormatException error) {
            return fallback;
        }
    }
}
