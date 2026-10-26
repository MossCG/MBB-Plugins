package org.moboxlab.mbb.trpg.testutil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.moboxlab.mbb.trpg.script.ScriptException;
import org.moboxlab.mbb.trpg.script.ScriptImportMode;
import org.moboxlab.mbb.trpg.script.ScriptImportResult;
import org.moboxlab.mbb.trpg.script.ScriptImportService;

/** 导入暂存端口假件：只记会话与片段，不解析、不落盘。 */
public final class FakeScriptImportService implements ScriptImportService {

    private final Map<String, List<String>> fragments = new LinkedHashMap<String, List<String>>();
    private ScriptException appendFailure;

    /** 让下一次 append 抛指定异常（模拟暂存失败）。 */
    public void failNextAppend(ScriptException failure) {
        this.appendFailure = failure;
    }

    /** 直接标记某个会话有暂存（跳过 begin 的参数校验）。 */
    public void markPending(long groupId, long operatorId) {
        fragments.put(key(groupId, operatorId), new ArrayList<String>());
    }

    /** 某会话已追加的片段。 */
    public List<String> fragments(long groupId, long operatorId) {
        List<String> stored = fragments.get(key(groupId, operatorId));
        return stored == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(stored));
    }

    @Override
    public void begin(long groupId, long operatorId, ScriptImportMode mode) throws ScriptException {
        fragments.put(key(groupId, operatorId), new ArrayList<String>());
    }

    @Override
    public void append(long groupId, long operatorId, String fragment) throws ScriptException {
        if (appendFailure != null) {
            ScriptException failure = appendFailure;
            appendFailure = null;
            throw failure;
        }
        List<String> stored = fragments.get(key(groupId, operatorId));
        if (stored == null) {
            throw new ScriptException("E_IMPORT_STATE", "$", "没有待导入会话");
        }
        stored.add(fragment);
    }

    @Override
    public ScriptImportResult end(long groupId, long operatorId) throws ScriptException {
        fragments.remove(key(groupId, operatorId));
        return null;
    }

    @Override
    public void cancel(long groupId, long operatorId) throws ScriptException {
        fragments.remove(key(groupId, operatorId));
    }

    @Override
    public boolean hasPending(long groupId, long operatorId) {
        return fragments.containsKey(key(groupId, operatorId));
    }

    @Override
    public long pendingBytes(long groupId, long operatorId) {
        List<String> stored = fragments.get(key(groupId, operatorId));
        if (stored == null) {
            return 0L;
        }
        long total = 0L;
        for (String fragment : stored) {
            total += fragment == null ? 0L : fragment.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        }
        return total;
    }

    @Override
    public long maxBytes() {
        return 65536L;
    }

    private static String key(long groupId, long operatorId) {
        return groupId + ":" + operatorId;
    }
}
