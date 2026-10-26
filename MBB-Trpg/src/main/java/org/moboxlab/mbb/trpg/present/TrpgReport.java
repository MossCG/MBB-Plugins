package org.moboxlab.mbb.trpg.present;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 战报数据（不可变）：图与纯文本回退共用同一份字段，保证两者口径一致。
 */
public final class TrpgReport {

    private final String scriptName;
    private final String endingTitle;
    private final String endingNarration;
    private final long startedAt;
    private final long endedAt;
    private final int durationMinutes;
    private final String kpName;
    private final List<String> playerNames;
    private final List<String> clueNames;
    private final int clueTotal;
    /** 检定成功数未知（日志里还没有可解析的结果）。 */
    public static final int CHECK_SUCCESS_UNKNOWN = -1;

    private final int checkTotal;
    private final int checkSuccess;
    private final List<String> checkResults;
    private final List<String> growthLines;
    private final int retentionDays;

    private TrpgReport(Builder builder) {
        this.scriptName = emptyIfNull(builder.scriptName);
        this.endingTitle = emptyIfNull(builder.endingTitle);
        this.endingNarration = emptyIfNull(builder.endingNarration);
        this.startedAt = builder.startedAt;
        this.endedAt = builder.endedAt;
        this.durationMinutes = Math.max(0, builder.durationMinutes);
        this.kpName = emptyIfNull(builder.kpName);
        this.playerNames = copy(builder.playerNames);
        this.clueNames = copy(builder.clueNames);
        this.clueTotal = Math.max(0, builder.clueTotal);
        this.checkTotal = Math.max(0, builder.checkTotal);
        // 允许 -1 透传：呈现层据此不输出「（成功 N）」，避免把未知写成成功 0。
        this.checkSuccess = Math.max(CHECK_SUCCESS_UNKNOWN, builder.checkSuccess);
        this.checkResults = copy(builder.checkResults);
        this.growthLines = copy(builder.growthLines);
        this.retentionDays = Math.max(1, builder.retentionDays);
    }

    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    private static List<String> copy(List<String> values) {
        return Collections.unmodifiableList(values == null
                ? new ArrayList<String>() : new ArrayList<String>(values));
    }

    public static Builder builder() {
        return new Builder();
    }

    public String scriptName() {
        return scriptName;
    }

    public String endingTitle() {
        return endingTitle;
    }

    public String endingNarration() {
        return endingNarration;
    }

    public long startedAt() {
        return startedAt;
    }

    public long endedAt() {
        return endedAt;
    }

    public int durationMinutes() {
        return durationMinutes;
    }

    public String kpName() {
        return kpName;
    }

    public List<String> playerNames() {
        return playerNames;
    }

    public List<String> clueNames() {
        return clueNames;
    }

    public int clueTotal() {
        return clueTotal;
    }

    public int checkTotal() {
        return checkTotal;
    }

    /** 检定成功数；{@link #CHECK_SUCCESS_UNKNOWN} 表示日志尚未提供可解析结果。 */
    public int checkSuccess() {
        return checkSuccess;
    }

    /** 检定明细（形如「聆听 成功」），可能为空。 */
    public List<String> checkResults() {
        return checkResults;
    }

    /** 成长明细（形如「聆听 50 → 52」）。 */
    public List<String> growthLines() {
        return growthLines;
    }

    public int retentionDays() {
        return retentionDays;
    }

    /** 时长文案：不足一小时写分钟，超过写「N 小时 MM 分」。 */
    public String durationText() {
        if (durationMinutes < 60) {
            return durationMinutes + " 分钟";
        }
        int hours = durationMinutes / 60;
        int minutes = durationMinutes % 60;
        return hours + " 小时 " + twoDigits(minutes) + " 分";
    }

    private static String twoDigits(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    /** 卡片标题行：{@code 战报 · 《剧本名》}。 */
    public String cardTitle() {
        return "战报 · 《" + scriptName + "》";
    }

    public static final class Builder {

        private String scriptName = "";
        private String endingTitle = "";
        private String endingNarration = "";
        private long startedAt;
        private long endedAt;
        private int durationMinutes;
        private String kpName = "";
        private List<String> playerNames = new ArrayList<String>();
        private List<String> clueNames = new ArrayList<String>();
        private int clueTotal;
        private int checkTotal;
        private int checkSuccess = CHECK_SUCCESS_UNKNOWN;
        private List<String> checkResults = new ArrayList<String>();
        private List<String> growthLines = new ArrayList<String>();
        private int retentionDays = 30;

        public Builder scriptName(String value) {
            this.scriptName = value;
            return this;
        }

        public Builder endingTitle(String value) {
            this.endingTitle = value;
            return this;
        }

        public Builder endingNarration(String value) {
            this.endingNarration = value;
            return this;
        }

        public Builder startedAt(long value) {
            this.startedAt = value;
            return this;
        }

        public Builder endedAt(long value) {
            this.endedAt = value;
            return this;
        }

        public Builder durationMinutes(int value) {
            this.durationMinutes = value;
            return this;
        }

        public Builder kpName(String value) {
            this.kpName = value;
            return this;
        }

        public Builder playerNames(List<String> value) {
            this.playerNames = value;
            return this;
        }

        public Builder clueNames(List<String> value) {
            this.clueNames = value;
            return this;
        }

        public Builder clueTotal(int value) {
            this.clueTotal = value;
            return this;
        }

        public Builder checkTotal(int value) {
            this.checkTotal = value;
            return this;
        }

        public Builder checkSuccess(int value) {
            this.checkSuccess = value;
            return this;
        }

        public Builder checkResults(List<String> value) {
            this.checkResults = value;
            return this;
        }

        public Builder growthLines(List<String> value) {
            this.growthLines = value;
            return this;
        }

        public Builder retentionDays(int value) {
            this.retentionDays = value;
            return this;
        }

        public TrpgReport build() {
            return new TrpgReport(this);
        }
    }
}