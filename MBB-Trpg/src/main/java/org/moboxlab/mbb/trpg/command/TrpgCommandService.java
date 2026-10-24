package org.moboxlab.mbb.trpg.command;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.moboxlab.moboxbot.API.Command.CommandPermission;
import org.moboxlab.moboxbot.API.Command.CommandSender;
import org.moboxlab.moboxbot.API.PluginLogger;
import org.moboxlab.mbb.trpg.card.CardGenerator;
import org.moboxlab.mbb.trpg.config.TrpgConfig;
import org.moboxlab.mbb.trpg.present.TrpgNameResolver;
import org.moboxlab.mbb.trpg.present.TrpgMessageTemplates;
import org.moboxlab.mbb.trpg.present.TrpgPresenter;
import org.moboxlab.mbb.trpg.script.ScriptAdmin;
import org.moboxlab.mbb.trpg.script.ScriptCatalog;
import org.moboxlab.mbb.trpg.session.Clock;
import org.moboxlab.mbb.trpg.session.CooldownRegistry;
import org.moboxlab.mbb.trpg.session.ErrorDedupe;
import org.moboxlab.mbb.trpg.session.SessionActor;
import org.moboxlab.mbb.trpg.session.SessionLogService;
import org.moboxlab.mbb.trpg.session.SessionService;
import org.moboxlab.mbb.trpg.store.BlacklistService;

/**
 * /trpg 指令的分发门面。
 *
 * <p>只做「识别子指令 → 归一名 → 交给 handler」：权限、状态、参数与冷却由 handler 调用的服务判定，
 * 指令层不写库、不持状态。所有回执都经 {@link TrpgPresenter} 发出，保证插件只有一条消息出口。</p>
 *
 * <p>新增指令只需实现 {@link TrpgSubcommandHandler} 并在构造或 {@link #register} 中登记，
 * 不需要改本类的分发结构。</p>
 */
public final class TrpgCommandService {

    private static final String UNKNOWN_NEXT = "发送 /trpg help 查看可用指令。";
    /** 私聊门禁的下一步提示；指令层其它入口共用同一文案，避免两处维护。 */
    public static final String PRIVATE_NEXT = "私聊只支持 help、/roll 与 script 库管理。";
    /** 指令层同类错误的键（与局面层共用去重窗口）。 */
    private static final String KEY_UNKNOWN = "cmd-unknown";
    private static final String KEY_PRIVATE = "cmd-private";
    private static final String KEY_UPCOMING = "cmd-upcoming";

    private final TrpgCommandContext context;
    private final Map<TrpgSubcommand, TrpgSubcommandHandler> handlers;

    public TrpgCommandService(SessionService sessions, SessionLogService logs, ScriptCatalog catalog,
                              CardGenerator cards, TrpgPresenter presenter,
                              TrpgNameResolver names,
                              TrpgConfig config, Clock clock, PluginLogger logger) {
        this(sessions, logs, catalog, cards, presenter, names, config, clock, logger,
                new CooldownRegistry(), new ErrorDedupe());
    }

    /** 生产装配：与局面层、日志层共用同一份冷却与去重登记。 */
    public TrpgCommandService(SessionService sessions, SessionLogService logs, ScriptCatalog catalog,
                              CardGenerator cards, TrpgPresenter presenter,
                              TrpgNameResolver names,
                              TrpgConfig config, Clock clock, PluginLogger logger,
                              CooldownRegistry cooldowns, ErrorDedupe dedupe) {
        this(sessions, logs, catalog, cards, presenter, names, config, clock, logger, cooldowns,
                dedupe, null);
    }

    /**
     * 生产装配（含黑名单）：黑名单命中时静默丢弃群内指令，不产生任何回执。
     */
    public TrpgCommandService(SessionService sessions, SessionLogService logs, ScriptCatalog catalog,
                              CardGenerator cards, TrpgPresenter presenter,
                              TrpgNameResolver names,
                              TrpgConfig config, Clock clock, PluginLogger logger,
                              CooldownRegistry cooldowns, ErrorDedupe dedupe,
                              BlacklistService blacklist) {
        this(sessions, logs, catalog, cards, presenter, names, config, clock, logger, cooldowns,
                dedupe, blacklist, null);
    }

    /**
     * 生产装配（含黑名单与剧本管理三件套）：S10 之后由插件装配层传入导入 / 生成 / 执行器。
     */
    public TrpgCommandService(SessionService sessions, SessionLogService logs, ScriptCatalog catalog,
                              CardGenerator cards, TrpgPresenter presenter,
                              TrpgNameResolver names,
                              TrpgConfig config, Clock clock, PluginLogger logger,
                              CooldownRegistry cooldowns, ErrorDedupe dedupe,
                              BlacklistService blacklist, ScriptAdmin scriptAdmin) {
        this.context = new TrpgCommandContext(sessions, logs, catalog, cards, presenter, names, config,
                clock, logger, cooldowns, dedupe, blacklist, scriptAdmin);
        Map<TrpgSubcommand, TrpgSubcommandHandler> registered =
                new EnumMap<TrpgSubcommand, TrpgSubcommandHandler>(TrpgSubcommand.class);
        registered.putAll(LifecycleCommandHandler.handlers());
        registered.putAll(ReadOnlyCommandHandler.handlers());
        registered.putAll(KpCommandHandler.handlers());
        registered.putAll(AdminCommandHandler.handlers());
        this.handlers = registered;
    }

    /**
     * 注册或替换一个子指令处理器（后续切片追加场面推进指令时使用）。
     *
     * @throws IllegalArgumentException 子指令或处理器为空
     */
    public void register(TrpgSubcommand subcommand, TrpgSubcommandHandler handler) {
        if (subcommand == null || handler == null) {
            throw new IllegalArgumentException("子指令与处理器不能为空");
        }
        handlers.put(subcommand, handler);
    }

    /** 指令上下文（装配与测试用）。 */
    public TrpgCommandContext context() {
        return context;
    }

    /** 装配 AI 触发端口（v1.1）：装配失败或全局关闭时不调用，触发静默跳过。 */
    public void bindAiTriggers(org.moboxlab.mbb.trpg.ai.AiTriggerSink aiTriggers) {
        context.bindAiTriggers(aiTriggers);
    }

    /**
     * 处理一条 /trpg 指令。
     *
     * @param sender 指令来源
     * @param args   子指令与参数（不含前缀）
     * @return 是否已处理（未知来源返回 false）
     */
    public boolean handle(CommandSender sender, String[] args) {
        if (sender == null) {
            return false;
        }
        SessionActor actor = actorOf(sender);
        if (actor.inGroup() && context.blacklist() != null
                && context.blacklist().contains(actor.groupId(), actor.userId())) {
            // 黑名单命中：静默丢弃，不处理、不刷新局内活动、不产生任何回执（§5.4）。
            return true;
        }
        if (actor.inGroup()) {
            // 局内活动节流由 SessionService 内部按 60 秒窗口判定，这里忽略返回值。
            context.sessions().touchActivity(actor.groupId(), actor.userId());
        }
        String[] params = args == null ? new String[0] : args;
        if (params.length == 0 || params[0] == null || params[0].trim().isEmpty()) {
            runHelp(actor, params);
            return true;
        }
        Resolved resolved = resolve(params);
        if (resolved == null) {
            context.rejectError(actor, KEY_UNKNOWN, "参数不对：不认识的子指令 " + params[0].trim(),
                    UNKNOWN_NEXT);
            return true;
        }
        TrpgSubcommand subcommand = resolved.subcommand();
        if (!actor.inGroup() && subcommand.groupOnly()) {
            context.rejectError(actor, KEY_PRIVATE, "请在群内使用", PRIVATE_NEXT);
            return true;
        }
        TrpgSubcommandHandler handler = handlers.get(subcommand);
        if (!subcommand.implemented() || handler == null) {
            context.rejectNotice(actor, KEY_UPCOMING, "该指令将在后续版本启用：" + subcommand.label());
            return true;
        }
        handler.handle(context, actor, resolved.params());
        return true;
    }

    private void runHelp(SessionActor actor, String[] params) {
        TrpgSubcommandHandler help = handlers.get(TrpgSubcommand.HELP);
        if (help == null) {
            context.reply(actor, TrpgMessageTemplates.notice("帮助暂不可用"));
            return;
        }
        help.handle(context, actor, params);
    }

    /**
     * 识别子指令并归一参数：{@code pc} 的修饰词（reroll / 重掷 / set / 微调）统一补成
     * {@code pc <主名> ...} 形态，让别名与主名走同一条 handler。
     */
    static Resolved resolve(String[] params) {
        TrpgSubcommand subcommand = TrpgSubcommand.resolve(params[0]);
        if (subcommand == null) {
            return null;
        }
        TrpgSubcommand modifier = TrpgSubcommand.resolvePcModifier(params[0]);
        if (modifier != null) {
            return new Resolved(modifier, canonical(modifier, params, 1));
        }
        if (subcommand == TrpgSubcommand.PC && params.length > 1) {
            TrpgSubcommand word = TrpgSubcommand.resolvePcModifier(params[1]);
            if (word != null) {
                return new Resolved(word, canonical(word, params, 2));
            }
        }
        return new Resolved(subcommand, params);
    }

    private static String[] canonical(TrpgSubcommand modifier, String[] params, int skip) {
        List<String> list = new ArrayList<String>();
        list.add("pc");
        list.add(modifier == TrpgSubcommand.PC_REROLL ? "reroll" : "set");
        for (int index = skip; index < params.length; index++) {
            if (params[index] != null) {
                list.add(params[index]);
            }
        }
        return list.toArray(new String[0]);
    }

    private static SessionActor actorOf(CommandSender sender) {
        boolean groupAdmin = sender.hasPermission(CommandPermission.GROUP_ADMIN)
                || sender.hasPermission(CommandPermission.GROUP_OWNER);
        boolean botAdmin = sender.hasPermission(CommandPermission.BOT_ADMIN)
                || sender.hasPermission(CommandPermission.OWNER);
        return new SessionActor(sender.getUserID(),
                sender.isGroup() ? sender.getGroupID() : SessionActor.PRIVATE_GROUP_ID,
                groupAdmin, botAdmin);
    }

    /**
     * 从 {@code <@123>}、{@code <@!123>}、{@code [CQ:at,qq=123]} 或纯数字里取用户号，
     * 失败返回 -1。
     */
    static long parseUserId(String token) {
        if (token == null) {
            return -1L;
        }
        String value = token.trim();
        if (value.isEmpty()) {
            return -1L;
        }
        if (value.startsWith("[CQ:at,") && value.endsWith("]")) {
            String body = value.substring("[CQ:at,".length(), value.length() - 1);
            for (String part : body.split(",")) {
                String pair = part.trim();
                if (pair.startsWith("qq=")) {
                    value = pair.substring("qq=".length()).trim();
                    break;
                }
            }
        } else if (value.startsWith("<@") && value.endsWith(">")) {
            value = value.substring(2, value.length() - 1);
            if (value.startsWith("!")) {
                value = value.substring(1);
            }
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException error) {
            return -1L;
        }
    }

    /** 归一化后的子指令与参数。 */
    static final class Resolved {

        private final TrpgSubcommand subcommand;
        private final String[] params;

        Resolved(TrpgSubcommand subcommand, String[] params) {
            this.subcommand = subcommand;
            this.params = params;
        }

        TrpgSubcommand subcommand() {
            return subcommand;
        }

        String[] params() {
            return params;
        }
    }
}
