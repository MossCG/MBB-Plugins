package org.moboxlab.mbb.trpg.session;

/**
 * 局面状态。
 *
 * <p>四态：无局、招募中、进行中、已结束。已结束是归档态，不占用每群一局的名额；
 * 无局不落库，只用于内存与快照表达。</p>
 */
public enum SessionState {

    NONE("无局"),
    RECRUITING("招募中"),
    RUNNING("进行中"),
    ENDED("已结束");

    private final String label;

    SessionState(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 是否占用每群一局的名额。 */
    public boolean active() {
        return this == RECRUITING || this == RUNNING;
    }

    /**
     * 解析库中状态文本。
     *
     * <p>未知值返回 {@link #NONE}，由调用方告警；库中不应出现 NONE。</p>
     */
    public static SessionState fromDb(String value) {
        if (value == null) {
            return NONE;
        }
        String trimmed = value.trim();
        for (SessionState state : values()) {
            if (state.name().equalsIgnoreCase(trimmed)) {
                return state;
            }
        }
        return NONE;
    }
}
