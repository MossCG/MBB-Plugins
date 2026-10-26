package org.moboxlab.mbb.trpg.session;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;

import org.moboxlab.mbb.trpg.script.Script;
import org.moboxlab.mbb.trpg.script.ScriptCatalog;
import org.moboxlab.mbb.trpg.script.ScriptEnding;
import org.moboxlab.mbb.trpg.store.SessionRow;

/**
 * 结束摘要解析（规格 §5.3）：按结局声明顺序取第一个所需线索全部解锁的结局，
 * 输出「标题 + 换行 + 叙述」；无匹配或剧本缺失时用通用文本，并按 2000 字截断。
 *
 * <p>读取剧本时持运维读锁（锁序：先群锁后库读锁）。</p>
 */
final class SummaryResolver {

    /** 摘要字数上限。 */
    static final int SUMMARY_MAX_CHARS = 2000;
    /** 无匹配结局时的标题。 */
    static final String GENERIC_TITLE = "冒险告一段落";
    /** 无匹配结局时的叙述。 */
    static final String GENERIC_NARRATION = "本次冒险没有达成既定结局，但经历会被记住。";

    private final ScriptCatalog catalog;

    SummaryResolver(ScriptCatalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("剧本库不能为空");
        }
        this.catalog = catalog;
    }

    /** 解析摘要；永不返回 null。 */
    String resolve(SessionRow row, List<String> unlockedClues) {
        Script script = loadScript(row.scriptId());
        if (script == null) {
            return truncate(genericText());
        }
        Set<String> unlocked = new HashSet<String>(
                unlockedClues == null ? Collections.<String>emptyList() : unlockedClues);
        for (ScriptEnding ending : script.endings()) {
            if (unlocked.containsAll(ending.requiredClueIds())) {
                return truncate(ending.title() + "\n" + ending.narration());
            }
        }
        return truncate(genericText());
    }

    /** 通用摘要文本。 */
    static String genericText() {
        return GENERIC_TITLE + "\n" + GENERIC_NARRATION;
    }

    /** 按字符（码点）截断，避免拆开代理对。 */
    static String truncate(String text) {
        if (text == null) {
            return "";
        }
        if (text.codePointCount(0, text.length()) <= SUMMARY_MAX_CHARS) {
            return text;
        }
        int end = text.offsetByCodePoints(0, SUMMARY_MAX_CHARS);
        return text.substring(0, end);
    }

    private Script loadScript(String scriptId) {
        if (scriptId == null || scriptId.isEmpty()) {
            return null;
        }
        Lock readLock = catalog.acquireReadLock();
        readLock.lock();
        try {
            return catalog.get(scriptId);
        } finally {
            readLock.unlock();
        }
    }
}
