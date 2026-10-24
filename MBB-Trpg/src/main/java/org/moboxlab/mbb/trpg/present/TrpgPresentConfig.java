package org.moboxlab.mbb.trpg.present;

import org.moboxlab.mbb.trpg.config.TrpgConfig;

/**
 * 呈现层配置（不可变）：只做取值与范围收敛，不做业务判断。
 *
 * <p>取值来自 {@code presentation.*} 配置节；非法值回落到默认值，避免一条坏配置
 * 让整条消息失去上限保护。</p>
 */
public final class TrpgPresentConfig {

    /** 群内单条正文默认上限。 */
    public static final int DEFAULT_GROUP_MAX_CHARS = 500;
    /** 私聊单条正文默认上限。 */
    public static final int DEFAULT_PRIVATE_MAX_CHARS = 1200;
    /** 默认最多拆几条。 */
    public static final int DEFAULT_SPLIT_MAX = 3;
    /** 默认战报图最大行数。 */
    public static final int DEFAULT_IMAGE_MAX_LINES = 22;
    /** 拆分条数硬上限（规格 §6.1 冻结为 3）。 */
    public static final int SPLIT_MAX_LIMIT = 3;
    /** 正文上限下限，防止配置成 1 之后每条消息都被切碎。 */
    private static final int MIN_TEXT_LIMIT = 50;

    private final int groupMaxChars;
    private final int privateMaxChars;
    private final int splitMax;
    private final boolean sceneImage;
    private final boolean reportImage;
    private final int imageMaxLines;
    private final String style;

    /** 从插件配置读取，缺失或非法值回落默认。 */
    public TrpgPresentConfig(TrpgConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("插件配置不能为空");
        }
        this.groupMaxChars = atLeast(config.groupMaxChars(), MIN_TEXT_LIMIT, DEFAULT_GROUP_MAX_CHARS);
        this.privateMaxChars = atLeast(config.privateMaxChars(), MIN_TEXT_LIMIT,
                DEFAULT_PRIVATE_MAX_CHARS);
        this.splitMax = clamp(config.splitMax(), 1, SPLIT_MAX_LIMIT, DEFAULT_SPLIT_MAX);
        this.sceneImage = config.sceneImage();
        this.reportImage = config.reportImage();
        this.imageMaxLines = clamp(config.imageMaxLines(), 5, 60, DEFAULT_IMAGE_MAX_LINES);
        this.style = config.presentationStyle() == null ? "broadcast" : config.presentationStyle();
    }

    /** 测试与默认值用：全部取规格默认。 */
    public static TrpgPresentConfig defaults() {
        return new TrpgPresentConfig(DEFAULT_GROUP_MAX_CHARS, DEFAULT_PRIVATE_MAX_CHARS,
                DEFAULT_SPLIT_MAX, true, true, DEFAULT_IMAGE_MAX_LINES, "broadcast");
    }

    /** 全参构造：仅测试与装配层使用。 */
    TrpgPresentConfig(int groupMaxChars, int privateMaxChars, int splitMax, boolean sceneImage,
                      boolean reportImage, int imageMaxLines, String style) {
        this.groupMaxChars = atLeast(groupMaxChars, MIN_TEXT_LIMIT, DEFAULT_GROUP_MAX_CHARS);
        this.privateMaxChars = atLeast(privateMaxChars, MIN_TEXT_LIMIT, DEFAULT_PRIVATE_MAX_CHARS);
        this.splitMax = clamp(splitMax, 1, SPLIT_MAX_LIMIT, DEFAULT_SPLIT_MAX);
        this.sceneImage = sceneImage;
        this.reportImage = reportImage;
        this.imageMaxLines = clamp(imageMaxLines, 5, 60, DEFAULT_IMAGE_MAX_LINES);
        this.style = style == null ? "broadcast" : style;
    }

    private static int atLeast(int value, int min, int fallback) {
        if (value <= 0) {
            return fallback;
        }
        return Math.max(value, min);
    }

    private static int clamp(int value, int min, int max, int fallback) {
        if (value <= 0) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    /** 群内单条正文上限。 */
    public int groupMaxChars() {
        return groupMaxChars;
    }

    /** 私聊单条正文上限。 */
    public int privateMaxChars() {
        return privateMaxChars;
    }

    /** 单次最多拆几条。 */
    public int splitMax() {
        return splitMax;
    }

    /** 是否给进场景发情景卡。 */
    public boolean sceneImage() {
        return sceneImage;
    }

    /** 是否给局结束发战报图。 */
    public boolean reportImage() {
        return reportImage;
    }

    /** 战报图最大行数。 */
    public int imageMaxLines() {
        return imageMaxLines;
    }

    /** 版式名；只支持 broadcast，其他值按 broadcast 处理。 */
    public String style() {
        return style;
    }

    /** 版式是否为播报风（只有播报风有实现）。 */
    public boolean broadcastStyle() {
        return "broadcast".equals(style);
    }
}
