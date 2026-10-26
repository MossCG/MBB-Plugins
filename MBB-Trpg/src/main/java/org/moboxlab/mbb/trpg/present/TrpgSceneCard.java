package org.moboxlab.mbb.trpg.present;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 情景卡入参（不可变）：进入场景时由指令层组装，呈现层只负责排成行与降级文本。
 */
public final class TrpgSceneCard {

    private final String sceneTitle;
    private final int sceneNo;
    private final int sceneTotal;
    private final List<String> presentNames;
    private final List<String> clueNames;
    private final String narration;
    private final String fallbackNarration;
    private final List<String> nextSteps;

    public TrpgSceneCard(String sceneTitle, int sceneNo, int sceneTotal, List<String> presentNames,
                         List<String> clueNames, String narration, String fallbackNarration,
                         List<String> nextSteps) {
        if (sceneTitle == null || sceneTitle.trim().isEmpty()) {
            throw new IllegalArgumentException("场景标题不能为空");
        }
        this.sceneTitle = sceneTitle;
        this.sceneNo = Math.max(1, sceneNo);
        this.sceneTotal = Math.max(this.sceneNo, sceneTotal);
        this.presentNames = copy(presentNames);
        this.clueNames = copy(clueNames);
        this.narration = narration == null ? "" : narration;
        this.fallbackNarration = fallbackNarration == null ? "" : fallbackNarration;
        this.nextSteps = copy(nextSteps);
    }

    private static List<String> copy(List<String> values) {
        return Collections.unmodifiableList(values == null
                ? new ArrayList<String>() : new ArrayList<String>(values));
    }

    public String sceneTitle() {
        return sceneTitle;
    }

    public int sceneNo() {
        return sceneNo;
    }

    public int sceneTotal() {
        return sceneTotal;
    }

    public List<String> presentNames() {
        return presentNames;
    }

    public List<String> clueNames() {
        return clueNames;
    }

    public String narration() {
        return narration;
    }

    /** AI 关闭或不可用时用于降级的剧本原文。 */
    public String fallbackNarration() {
        return fallbackNarration;
    }

    public List<String> nextSteps() {
        return nextSteps;
    }

    /**
     * 降级正文：优先当前场景叙述（AI 关闭时也应展示本幕内容），
     * 叙述为空时才用剧本给出的人工兜底原文（规范 §7.4）。
     */
    public String fallbackText() {
        return narration.trim().isEmpty() ? fallbackNarration : narration;
    }
}