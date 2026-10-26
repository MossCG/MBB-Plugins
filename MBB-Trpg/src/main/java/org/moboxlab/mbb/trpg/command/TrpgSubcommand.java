package org.moboxlab.mbb.trpg.command;

import java.util.Locale;

/**
 * /trpg 的子指令表。
 *
 * <p>主名与中文别名等价，集中在本枚举定义，指令层只按这里识别；未注册进本表的词一律按参数错误拒绝。
 * {@code implemented} 标记该子指令是否已在当前版本落地，未落地的指令仍被识别但回「后续版本启用」，
 * 避免玩家把「切片未到」误当参数写错。</p>
 */
public enum TrpgSubcommand {

    START("start", "开团", "[剧本ID]", true, true),
    JOIN("join", "加入", "", true, true),
    LEAVE("leave", "离开", "", true, true),
    BEGIN("begin", "开局", "", true, true),
    END("end", "结束", "", true, true),
    RESUME("resume", "恢复", "", true, true),
    STATUS("status", "状态", "", true, true),
    HELP("help", "帮助", "", false, true),
    PC("pc", "角色卡", "[@玩家]", true, true),
    PC_REROLL("pc reroll", "重掷", "", true, true),
    PC_SET("pc set", "微调", "<字段> <值>", true, true),
    LOG("log", "日志", "[页码]", true, true),
    SCENE("scene", "场景", "<标题>", true, true),
    CHECK("check", "检定", "[@玩家] <技能> [bp/pp [次数]]", true, true),
    OPPOSED("opposed", "对抗", "<@A> <技能> vs <@B> <技能或目标值>", true, true),
    SAN("san", "理智", "<@玩家> <X/Y>", true, true),
    ADJUST("adjust", "调整", "<@玩家> <技能> <±值>", true, true),
    INVITE("invite", "邀请", "<QQ>", true, true),
    TAKE("take", "接管", "<@玩家>", true, true),
    SKIP("skip", "跳过", "<@玩家> [原因]", true, true),
    SCRIPT("script", "剧本", "[list|show|import|delete|gen] ...", false, true),
    BLACKLIST("blacklist", "黑名单", "add|remove|list（私聊 remove <群号> <QQ>）", false, true),
    AI("ai", "AI 主持", "on|off", true, true);

    private final String primary;
    private final String alias;
    private final String usage;
    private final boolean groupOnly;
    private final boolean implemented;

    TrpgSubcommand(String primary, String alias, String usage, boolean groupOnly, boolean implemented) {
        this.primary = primary;
        this.alias = alias;
        this.usage = usage;
        this.groupOnly = groupOnly;
        this.implemented = implemented;
    }

    /** 主名（小写英文）。 */
    public String primary() {
        return primary;
    }

    /** 中文别名，没有别名时为空串。 */
    public String alias() {
        return alias;
    }

    /** 是否有中文别名。 */
    public boolean hasAlias() {
        return !alias.isEmpty();
    }

    /** 参数提示，没有参数时为空串。 */
    public String usage() {
        return usage;
    }

    /** 是否只允许群内使用。 */
    public boolean groupOnly() {
        return groupOnly;
    }

    /** 是否已在当前版本落地。 */
    public boolean implemented() {
        return implemented;
    }

    /** 展示用名称，如 {@code start / 开团}。 */
    public String label() {
        return hasAlias() ? primary + " / " + alias : primary;
    }

    /**
     * 解析 {@code pc} 后面的修饰词（主名与中文别名等价）。
     *
     * <p>{@code /trpg 重掷}、{@code /trpg pc 重掷}、{@code /trpg pc reroll} 三种写法
     * 都归一化成 {@code pc reroll} 形态，保证别名行为与主名完全一致（B7）。</p>
     */
    static TrpgSubcommand resolvePcModifier(String token) {
        if (token == null) {
            return null;
        }
        String value = token.trim();
        if ("reroll".equalsIgnoreCase(value) || "重掷".equals(value)) {
            return PC_REROLL;
        }
        if ("set".equalsIgnoreCase(value) || "微调".equals(value)) {
            return PC_SET;
        }
        return null;
    }

    /**
     * 按主名或中文别名匹配子指令。
     *
     * <p>英文主名忽略大小写，中文别名按原样比较；前后空白忽略。</p>
     *
     * @param token 玩家输入的第一个词
     * @return 匹配到的子指令，空或未知返回 null
     */
    public static TrpgSubcommand resolve(String token) {
        if (token == null) {
            return null;
        }
        String normalized = token.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        for (TrpgSubcommand item : values()) {
            if (item.primary.equalsIgnoreCase(lower)) {
                return item;
            }
            if (item.hasAlias() && item.alias.equals(normalized)) {
                return item;
            }
        }
        return null;
    }
}
