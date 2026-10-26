package org.moboxlab.mbb.trpg.script;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 分段导入状态机：开始 → 分段粘贴 → 结束 / 取消（§8.2）。
 *
 * <p>暂存只在内存，按（群号, 用户）隔离：群内按群号 + 用户，私聊群号为 0。同一会话同一用户
 * 同时只允许一个暂存；最后一段之后 {@code script.importIdleMinutes} 分钟无新内容、或从开始算起
 * {@code script.importMaxMinutes} 分钟即失效。片段按发送顺序直接拼接，不插入分隔符，不做 trim。</p>
 *
 * <p>结束时的分工：JSON 语法错误（E_PARSE）保留暂存，视为「还没传完」；其余校验或落盘失败
 * 结束暂存并把原文存到 imports/rejected/，由调用方展示错误码与前 5 条错误。</p>
 */
public final class ScriptImportSessions implements ScriptImportService {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String END_COMMAND = "/trpg script import end";
    private static final String CANCEL_COMMAND = "/trpg script import cancel";

    private final ScriptLibrary library;
    private final ScriptValidator validator;
    private final ScriptRejectedStore rejected;
    private final long maxBytes;
    private final long idleMillis;
    private final long maxMillis;
    private final int maxSegments;
    private final LongSupplier clock;
    private final Map<String, Pending> pending = new ConcurrentHashMap<String, Pending>();

    /**
     * @param maxBytes    单份内容上限（字节）
     * @param idleMillis  最后一段之后的空闲有效期（毫秒）
     * @param maxMillis   从开始算起的总有效期（毫秒）
     * @param maxSegments 分段数上限
     * @param clock       时间端口，测试可注入假时钟
     */
    public ScriptImportSessions(ScriptLibrary library, ScriptValidator validator,
                                ScriptRejectedStore rejected, long maxBytes, long idleMillis,
                                long maxMillis, int maxSegments, LongSupplier clock) {
        if (library == null) {
            throw new IllegalArgumentException("剧本库不能为空");
        }
        if (validator == null) {
            throw new IllegalArgumentException("校验器不能为空");
        }
        if (rejected == null) {
            throw new IllegalArgumentException("拒绝原文存档不能为空");
        }
        this.library = library;
        this.validator = validator;
        this.rejected = rejected;
        this.maxBytes = maxBytes <= 0L ? ScriptValidator.MAX_CONTENT_BYTES : maxBytes;
        this.idleMillis = idleMillis <= 0L ? 5 * 60 * 1000L : idleMillis;
        this.maxMillis = maxMillis <= 0L ? 30 * 60 * 1000L : maxMillis;
        this.maxSegments = maxSegments <= 0 ? 200 : maxSegments;
        this.clock = clock == null ? new LongSupplier() {
            @Override
            public long getAsLong() {
                return System.currentTimeMillis();
            }
        } : clock;
    }

    /** 是不是结束控制字：整条消息去首尾空白后恰好等于 {@code /trpg script import end}。 */
    public static boolean isEndControl(String raw) {
        return raw != null && END_COMMAND.equalsIgnoreCase(raw.trim());
    }

    /** 是不是取消控制字：整条消息去首尾空白后恰好等于 {@code /trpg script import cancel}。 */
    public static boolean isCancelControl(String raw) {
        return raw != null && CANCEL_COMMAND.equalsIgnoreCase(raw.trim());
    }

    @Override
    public synchronized void begin(long groupId, long operatorId, ScriptImportMode mode)
            throws ScriptException {
        if (mode == null) {
            throw new IllegalArgumentException("导入模式不能为空");
        }
        expireLocked();
        String key = key(groupId, operatorId);
        if (pending.containsKey(key)) {
            throw new ScriptException(ScriptErrorCodes.E_IMPORT_STATE, "$",
                    "已有一段导入在进行，请先发送 /trpg script import end 结束或 cancel 取消");
        }
        pending.put(key, new Pending(mode, clock.getAsLong()));
    }

    @Override
    public synchronized void append(long groupId, long operatorId, String fragment)
            throws ScriptException {
        String key = key(groupId, operatorId);
        Pending entry = pending.get(key);
        if (entry == null) {
            throw new ScriptException(ScriptErrorCodes.E_IMPORT_STATE, "$",
                    "没有待导入的剧本，请先发送 /trpg script import 开始");
        }
        if (expired(entry)) {
            pending.remove(key);
            throw new ScriptException(ScriptErrorCodes.E_IMPORT_STATE, "$",
                    "暂存已超时失效，请重新发送 /trpg script import 开始");
        }
        if (entry.segments >= maxSegments) {
            pending.remove(key);
            throw new ScriptException(ScriptErrorCodes.E_SIZE, "$",
                    "分段数超过上限 " + maxSegments + " 段，请重新开始导入");
        }
        String text = fragment == null ? "" : fragment;
        long length = entry.bytes + text.getBytes(UTF8).length;
        if (length > maxBytes) {
            pending.remove(key);
            throw new ScriptException(ScriptErrorCodes.E_SIZE, "$",
                    "剧本内容超过 " + maxBytes + " 字节，请重新开始导入");
        }
        entry.content.append(text);
        entry.bytes = length;
        entry.segments++;
        entry.lastAt = clock.getAsLong();
    }

    @Override
    public synchronized ScriptImportResult end(long groupId, long operatorId)
            throws ScriptException {
        String key = key(groupId, operatorId);
        Pending entry = pending.get(key);
        if (entry == null) {
            throw new ScriptException(ScriptErrorCodes.E_IMPORT_STATE, "$",
                    "没有待导入的剧本，请先发送 /trpg script import 开始");
        }
        if (expired(entry)) {
            pending.remove(key);
            throw new ScriptException(ScriptErrorCodes.E_IMPORT_STATE, "$",
                    "暂存已超时失效，请重新发送 /trpg script import 开始");
        }
        String raw = entry.content.toString();
        if (entry.bytes > maxBytes) {
            throw new ScriptException(ScriptErrorCodes.E_SIZE, "$",
                    "剧本内容超过 " + maxBytes + " 字节");
        }
        ScriptValidationResult result = validator.validate(raw);
        if (!result.ok() && containsParseError(result)) {
            // 多半还没传完：保留暂存，让管理员继续发送或取消
            throw new ScriptValidationException(result);
        }
        byte[] content = raw.getBytes(UTF8);
        try {
            ScriptImportResult saved = library.save(content, entry.mode);
            pending.remove(key);
            return saved;
        } catch (ScriptValidationException error) {
            pending.remove(key);
            rejected.save(raw, operatorId, error.code());
            throw error;
        } catch (ScriptException error) {
            pending.remove(key);
            rejected.save(raw, operatorId, error.code());
            throw error;
        }
    }

    @Override
    public synchronized void cancel(long groupId, long operatorId) throws ScriptException {
        String key = key(groupId, operatorId);
        if (pending.remove(key) == null) {
            throw new ScriptException(ScriptErrorCodes.E_IMPORT_STATE, "$",
                    "没有待导入的剧本，无需取消");
        }
    }

    @Override
    public synchronized boolean hasPending(long groupId, long operatorId) {
        expireLocked();
        return pending.containsKey(key(groupId, operatorId));
    }

    /** 当前暂存的字节数；没有暂存返回 0。 */
    public synchronized long pendingBytes(long groupId, long operatorId) {
        Pending entry = pending.get(key(groupId, operatorId));
        return entry == null ? 0L : entry.bytes;
    }

    /** 暂存上限，供回执展示。 */
    public long maxBytes() {
        return maxBytes;
    }

    private void expireLocked() {
        Iterator<Map.Entry<String, Pending>> iterator = pending.entrySet().iterator();
        List<String> expiredKeys = new ArrayList<String>();
        while (iterator.hasNext()) {
            Map.Entry<String, Pending> item = iterator.next();
            if (expired(item.getValue())) {
                expiredKeys.add(item.getKey());
            }
        }
        for (String key : expiredKeys) {
            pending.remove(key);
        }
    }

    private boolean expired(Pending entry) {
        long now = clock.getAsLong();
        return now - entry.lastAt >= idleMillis || now - entry.startedAt >= maxMillis;
    }

    private static boolean containsParseError(ScriptValidationResult result) {
        for (ScriptIssue issue : result.errors()) {
            if (ScriptErrorCodes.E_PARSE.equals(issue.code())) {
                return true;
            }
        }
        return false;
    }

    private static String key(long groupId, long operatorId) {
        return groupId + ":" + operatorId;
    }

    /** 一个待导入暂存。只在 synchronized 内读写。 */
    private static final class Pending {
        private final ScriptImportMode mode;
        private final long startedAt;
        private final StringBuilder content = new StringBuilder();
        private long bytes;
        private int segments;
        private long lastAt;

        private Pending(ScriptImportMode mode, long startedAt) {
            this.mode = mode;
            this.startedAt = startedAt;
            this.lastAt = startedAt;
        }
    }
}
