package org.moboxlab.mbb.trpg.script;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 剧本结局（不可变）；按数组顺序取第一个所需线索全部解锁者。 */
public final class ScriptEnding {

    private final String id;
    private final String title;
    private final String narration;
    private final List<String> requiredClueIds;

    public ScriptEnding(String id, String title, String narration, List<String> requiredClueIds) {
        this.id = id == null ? "" : id;
        this.title = title == null ? "" : title;
        this.narration = narration == null ? "" : narration;
        this.requiredClueIds = Collections.unmodifiableList(new ArrayList<String>(requiredClueIds));
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String narration() {
        return narration;
    }

    /** 需要全部已解锁的线索 id；缺省为空数组。 */
    public List<String> requiredClueIds() {
        return requiredClueIds;
    }
}
