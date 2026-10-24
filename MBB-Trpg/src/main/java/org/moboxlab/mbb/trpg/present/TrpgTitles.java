package org.moboxlab.mbb.trpg.present;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 冻结标题词表（规格 §2.3）：所有群内消息标题只能取这里的词，新增词先改规格。
 *
 * <p>标题层级最多两级，用 {@code " · "} 分隔；续页用 {@link #continued}。</p>
 */
public final class TrpgTitles {

    /** 招募分区。 */
    public static final String RECRUIT = "开团";
    public static final String JOIN = "加入";
    public static final String LEAVE = "离开";
    public static final String INVITE = "邀请";
    public static final String TAKE = "接管";
    /** 开局分区。 */
    public static final String BEGIN = "开局";
    public static final String SCENE = "场景";
    public static final String SCRIPT = "剧本";
    /** 玩法分区。 */
    public static final String CHECK = "检定";
    public static final String OPPOSED = "对抗";
    public static final String SAN = "理智";
    public static final String GROWTH = "成长";
    public static final String ADJUST = "调整";
    /** 剧情分区。 */
    public static final String CLUE = "线索";
    public static final String TRIGGER = "触发";
    public static final String AI_NARRATION = "AI 叙述";
    /** 收尾分区。 */
    public static final String END = "结束";
    public static final String END_CONFIRM = "结束确认";
    public static final String REPORT = "战报";
    /** 系统分区。 */
    public static final String NOTICE = "提示";
    public static final String ERROR = "错误";
    public static final String CARD = "角色卡";
    public static final String KP_HINT = "KP 提示";
    public static final String SCRIPT_LIBRARY = "剧本库";
    public static final String RECOVER = "恢复";
    public static final String HOST = "主持";
    /** 查询回复（S7 新增，已同步规格标题表）。 */
    public static final String STATUS = "局面";
    public static final String HELP = "帮助";
    public static final String LOG = "日志";

    /** 词表全集（顺序与规格表一致）。 */
    public static final List<String> ALL = Collections.unmodifiableList(Arrays.asList(
            RECRUIT, JOIN, LEAVE, INVITE, TAKE,
            BEGIN, SCENE, SCRIPT,
            CHECK, OPPOSED, SAN, GROWTH, ADJUST,
            CLUE, TRIGGER, AI_NARRATION,
            END, END_CONFIRM, REPORT,
            NOTICE, ERROR, CARD, KP_HINT, SCRIPT_LIBRARY, RECOVER, HOST,
            STATUS, HELP, LOG));

    private TrpgTitles() {
    }

    /** 一级标题：{@code 【标题】}。 */
    public static String of(String title) {
        return "【" + title + "】";
    }

    /** 两级标题：{@code 【标题 · 子标题】}。 */
    public static String of(String title, String subtitle) {
        if (subtitle == null || subtitle.trim().isEmpty()) {
            return of(title);
        }
        return "【" + title + " · " + subtitle.trim() + "】";
    }

}
