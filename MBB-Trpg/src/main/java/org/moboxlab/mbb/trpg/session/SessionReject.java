package org.moboxlab.mbb.trpg.session;

/** 固定拒绝类别（规格 §5.4）；措辞由呈现层决定，服务层只给类别。 */
public enum SessionReject {

    /** 请在群内使用。 */
    NOT_IN_GROUP("请在群内使用"),
    /** 无权限。 */
    NO_PERMISSION("无权限"),
    /** 当前状态不可用（含本群暂无局）。 */
    STATE_UNAVAILABLE("当前状态不可用"),
    /** 参数不合法。 */
    BAD_ARGUMENT("参数不合法"),
    /** 冷却中。 */
    COOLING("冷却中"),
    /** 并发占用（本群正在处理上一条）。 */
    BUSY("本群正在处理上一条"),
    /** 成员状态不满足（未入局 / 已在局 / 已离场 / 非 KP）。 */
    MEMBER_STATE("成员状态不满足");

    private final String label;

    SessionReject(String label) {
        this.label = label;
    }

    /** 中文类别名，供呈现层组句。 */
    public String label() {
        return label;
    }
}
