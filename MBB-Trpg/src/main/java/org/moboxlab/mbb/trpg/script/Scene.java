package org.moboxlab.mbb.trpg.script;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 剧本场景（不可变）；数组顺序即场景顺序，第一个是开场。 */
public final class Scene {

    private final String id;
    private final String title;
    private final String summary;
    private final String narration;
    private final String fallbackNarration;
    private final List<String> clueIds;
    private final List<String> npcIds;
    private final List<String> checkIds;

    public Scene(String id, String title, String summary, String narration, String fallbackNarration,
                 List<String> clueIds, List<String> npcIds, List<String> checkIds) {
        this.id = id == null ? "" : id;
        this.title = title == null ? "" : title;
        this.summary = summary == null ? "" : summary;
        this.narration = narration == null ? "" : narration;
        this.fallbackNarration = fallbackNarration == null ? "" : fallbackNarration;
        this.clueIds = Collections.unmodifiableList(new ArrayList<String>(clueIds));
        this.npcIds = Collections.unmodifiableList(new ArrayList<String>(npcIds));
        this.checkIds = Collections.unmodifiableList(new ArrayList<String>(checkIds));
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    /** 一行概括；没有时为空串。 */
    public String summary() {
        return summary;
    }

    public String narration() {
        return narration;
    }

    /** AI 主持失败时的替补叙述；没有时为空串，调用方回退到 {@link #narration()}。 */
    public String fallbackNarration() {
        return fallbackNarration;
    }

    public List<String> clueIds() {
        return clueIds;
    }

    public List<String> npcIds() {
        return npcIds;
    }

    public List<String> checkIds() {
        return checkIds;
    }
}
