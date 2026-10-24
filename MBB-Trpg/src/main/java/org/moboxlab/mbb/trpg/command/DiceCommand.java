package org.moboxlab.mbb.trpg.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.moboxlab.moboxbot.API.Command.BotCommand;
import org.moboxlab.moboxbot.API.Command.CommandPermission;
import org.moboxlab.moboxbot.API.Command.CommandSender;
import org.moboxlab.mbb.trpg.config.TrpgConfig;
import org.moboxlab.mbb.trpg.present.TrpgDiceView;
import org.moboxlab.mbb.trpg.present.TrpgMessageTemplates;
import org.moboxlab.mbb.trpg.present.TrpgPresenter;
import org.moboxlab.mbb.trpg.rule.DiceExpression;
import org.moboxlab.mbb.trpg.rule.RandomSource;
import org.moboxlab.mbb.trpg.session.Clock;
import org.moboxlab.mbb.trpg.session.CooldownRegistry;
import org.moboxlab.mbb.trpg.session.ErrorDedupe;
import org.moboxlab.mbb.trpg.session.SessionActor;

/**
 * /r 与 /roll：段相加表达式，不落库、不能掷技能检定，群内外均可使用。
 *
 * <p>回执同样经 {@link TrpgPresenter} 发出，保证插件只有一条消息出口；冷却按（用户, 频道）计
 * 2 秒，与 /trpg 的插件内冷却分开。解析失败与空表达式属于「同类错误」，与局面层共用同一个
 * {@link ErrorDedupe}，10 秒内只回一次（规范 §3.4）。</p>
 */
public final class DiceCommand extends BotCommand {

    private static final String ACTION_DICE = "dice";
    /** 同类错误键：缺少表达式。 */
    private static final String KEY_EMPTY = "dice-empty";
    /** 同类错误键：表达式不支持。 */
    private static final String KEY_UNSUPPORTED = "dice-unsupported";
    private static final long MILLIS_PER_SECOND = 1000L;
    private static final String DEFAULT_PREFIX = "r";

    private final TrpgConfig config;
    private final TrpgPresenter presenter;
    private final RandomSource random;
    private final Clock clock;
    private final CooldownRegistry cooldowns;
    private final ErrorDedupe dedupe;

    public DiceCommand(TrpgConfig config, TrpgPresenter presenter, RandomSource random, Clock clock) {
        this(config, presenter, random, clock, new CooldownRegistry(), new ErrorDedupe());
    }

    /** 生产装配注入与 /trpg 共用的冷却与去重登记。 */
    public DiceCommand(TrpgConfig config, TrpgPresenter presenter, RandomSource random, Clock clock,
                       CooldownRegistry cooldowns, ErrorDedupe dedupe) {
        if (config == null) {
            throw new IllegalArgumentException("配置不能为空");
        }
        if (presenter == null) {
            throw new IllegalArgumentException("呈现服务不能为空");
        }
        if (random == null) {
            throw new IllegalArgumentException("随机源不能为空");
        }
        if (clock == null) {
            throw new IllegalArgumentException("时间端口不能为空");
        }
        if (cooldowns == null || dedupe == null) {
            throw new IllegalArgumentException("冷却与去重不能为空");
        }
        this.config = config;
        this.presenter = presenter;
        this.random = random;
        this.clock = clock;
        this.cooldowns = cooldowns;
        this.dedupe = dedupe;
    }

    @Override
    public List<String> prefix() {
        return Arrays.asList("r", "roll");
    }

    @Override
    public CommandPermission permission() {
        return CommandPermission.EVERYONE;
    }

    @Override
    public int cooldownSeconds() {
        return 0;
    }

    @Override
    public String description() {
        return "掷骰（支持 NdM 与加法，最多 3 段）";
    }

    @Override
    public List<String> usage() {
        return Arrays.asList("/r 3d6", "/r 1d100+5", "/roll 1d10+2");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (sender == null) {
            return false;
        }
        long userId = sender.getUserID();
        long groupId = sender.isGroup() ? sender.getGroupID() : SessionActor.PRIVATE_GROUP_ID;
        long now = clock.nowMillis();
        long window = config.errorDedupeSeconds() * MILLIS_PER_SECOND;
        String expression = joinArgs(args);
        if (expression.isEmpty()) {
            if (dedupe.claim(groupId, KEY_EMPTY, now, window)) {
                reply(sender, TrpgMessageTemplates.error("参数不对：缺少骰子表达式",
                        "用法：/r 3d6、/r 1d100+5，最多 3 段。"));
            }
            return true;
        }
        DiceParser.Parsed parsed = DiceParser.parse(expression);
        if (parsed == null) {
            if (dedupe.claim(groupId, KEY_UNSUPPORTED, now, window)) {
                reply(sender, TrpgMessageTemplates.diceUnsupported(expression));
            }
            return true;
        }
        long remaining = cooldowns.remainingMillis(ACTION_DICE, userId, groupId, now,
                config.diceCooldownSeconds() * MILLIS_PER_SECOND);
        if (remaining > 0L) {
            reply(sender, TrpgMessageTemplates.notice("掷骰还有 " + seconds(remaining) + " 秒冷却"));
            return true;
        }
        List<Integer> values = new ArrayList<Integer>();
        int total = 0;
        for (DiceExpression segment : parsed.segments()) {
            int value = segment.roll(random);
            total += value;
            values.add(Integer.valueOf(value));
        }
        cooldowns.mark(ACTION_DICE, userId, groupId, now);
        reply(sender, TrpgMessageTemplates.dice(new TrpgDiceView(prefixOf(sender), parsed.expression(),
                parsed.segmentTexts(), values, total)));
        return true;
    }

    private void reply(CommandSender sender, String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }
        if (sender.isGroup()) {
            presenter.sendGroup(sender.getGroupID(), message);
        } else {
            presenter.sendPrivate(sender.getUserID(), message);
        }
    }

    /** 回执按玩家实际用的前缀回显（/r 或 /roll）。 */
    private static String prefixOf(CommandSender sender) {
        String prefix = sender.getCommandPrefix();
        if (prefix == null) {
            return DEFAULT_PREFIX;
        }
        String trimmed = prefix.trim();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        return trimmed.isEmpty() ? DEFAULT_PREFIX : trimmed;
    }

    private static long seconds(long remainingMillis) {
        return Math.max(1L, (remainingMillis + MILLIS_PER_SECOND - 1L) / MILLIS_PER_SECOND);
    }

    private static String joinArgs(String[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String arg : args) {
            if (arg == null) {
                continue;
            }
            builder.append(arg.trim());
        }
        return builder.toString().trim();
    }
}
