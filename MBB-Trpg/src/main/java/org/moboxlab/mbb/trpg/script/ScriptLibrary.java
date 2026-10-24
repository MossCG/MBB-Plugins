package org.moboxlab.mbb.trpg.script;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.moboxlab.moboxbot.API.PluginLogger;

/**
 * 剧本库：内置（只读）与导入（数据目录）的统一视图，落地 §8.1 与 §8.6。
 *
 * <p>内置剧本随 JAR 发布、只读；导入剧本放在 {@code <dataFolder>/scripts/<id>.json}，
 * 启动时全量校验并缓存，校验失败的导入文件只告警跳过，不阻断插件加载（内置失败才阻断，
 * 见 {@link BuiltinScriptCatalog}）。同 id 以内置为准，导入文件命中内置 id 时跳过。</p>
 *
 * <p>删除与替换整段持库级写锁：写锁内先做引用检查（{@link ScriptUsageIndex}），再改文件与缓存，
 * 以消除「检查后、插入前被删」的窗口。读取路径只持读锁；调用方锁序固定为「先群锁后库锁」。
 * 读写锁不覆盖网络发送。</p>
 */
public final class ScriptLibrary implements ScriptCatalog {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String JSON_SUFFIX = ".json";

    private final BuiltinScriptCatalog builtin;
    private final File scriptDir;
    private final ScriptValidator validator;
    private final long maxBytes;
    private final PluginLogger logger;
    private final Map<String, Script> imported = new ConcurrentHashMap<String, Script>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile ScriptUsageIndex usageIndex;
    /** 启动加载时被跳过的导入文件数，只用于列表页提示。 */
    private volatile int skippedFiles;

    /**
     * 建库并加载导入剧本。
     *
     * @param builtin   内置剧本库，非空
     * @param scriptDir 导入剧本目录，可为不存在（首次导入时创建）
     * @param validator 校验器，非空
     * @param maxBytes  单份内容上限（字节）
     * @param logger    插件日志，可为 null
     */
    public ScriptLibrary(BuiltinScriptCatalog builtin, File scriptDir, ScriptValidator validator,
                         long maxBytes, PluginLogger logger) {
        if (builtin == null) {
            throw new IllegalArgumentException("内置剧本库不能为空");
        }
        if (scriptDir == null) {
            throw new IllegalArgumentException("导入目录不能为空");
        }
        if (validator == null) {
            throw new IllegalArgumentException("校验器不能为空");
        }
        this.builtin = builtin;
        this.scriptDir = scriptDir;
        this.validator = validator;
        this.maxBytes = maxBytes <= 0L ? ScriptValidator.MAX_CONTENT_BYTES : maxBytes;
        this.logger = logger;
        loadImported();
    }

    /** 绑定未归档局引用索引；绑定前删除与替换按无引用处理。 */
    public void bindUsageIndex(ScriptUsageIndex index) {
        this.usageIndex = index;
    }

    /** 导入剧本数量。 */
    public int importedCount() {
        return imported.size();
    }

    /** 启动加载时被跳过（校验失败、内置冲突、文件名不一致、读失败）的文件数。 */
    @Override
    public int skippedFileCount() {
        return skippedFiles;
    }

    private void loadImported() {
        File[] files = scriptDir.listFiles();
        if (files == null) {
            return;
        }
        TreeSet<String> names = new TreeSet<String>();
        for (File file : files) {
            if (file != null && file.isFile() && file.getName().endsWith(JSON_SUFFIX)) {
                names.add(file.getName());
            }
        }
        for (String name : names) {
            String id = name.substring(0, name.length() - JSON_SUFFIX.length());
            File file = new File(scriptDir, name);
            try {
                byte[] content = Files.readAllBytes(file.toPath());
                ScriptValidationResult result = validator.validate(content);
                if (!result.ok()) {
                    skippedFiles++;
                    warn("导入剧本校验失败，已跳过：" + name + "：" + describe(result));
                    continue;
                }
                if (builtin.get(id) != null) {
                    skippedFiles++;
                    warn("导入剧本与内置 id 冲突，已跳过：" + name);
                    continue;
                }
                if (!id.equals(result.script().id())) {
                    skippedFiles++;
                    warn("导入剧本文件名与 id 不一致，已跳过：" + name + "（id="
                            + result.script().id() + "）");
                    continue;
                }
                imported.put(id, result.script());
            } catch (IOException error) {
                skippedFiles++;
                warn("读取导入剧本失败，已跳过：" + name + "：" + error);
            }
        }
    }

    @Override
    public Script get(String id) {
        if (id == null) {
            return null;
        }
        Script script = builtin.get(id);
        if (script != null) {
            return script;
        }
        return imported.get(id);
    }

    @Override
    public ScriptInfo info(String id) {
        if (id == null) {
            return null;
        }
        Script builtinScript = builtin.get(id);
        if (builtinScript != null) {
            return new ScriptInfo(builtinScript, ScriptSource.BUILTIN);
        }
        Script importedScript = imported.get(id);
        if (importedScript == null) {
            return null;
        }
        return new ScriptInfo(importedScript, ScriptSource.IMPORTED);
    }

    @Override
    public List<ScriptInfo> list() {
        Map<String, ScriptInfo> all = new TreeMap<String, ScriptInfo>();
        for (ScriptInfo info : builtin.list()) {
            all.put(info.id(), info);
        }
        for (Map.Entry<String, Script> entry : imported.entrySet()) {
            all.put(entry.getKey(), new ScriptInfo(entry.getValue(), ScriptSource.IMPORTED));
        }
        return Collections.unmodifiableList(new ArrayList<ScriptInfo>(all.values()));
    }

    @Override
    public void delete(String id) throws ScriptException {
        Lock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            if (id == null || id.trim().isEmpty()) {
                throw new ScriptException(ScriptErrorCodes.E_NOT_FOUND, "$.id", "剧本不存在：");
            }
            if (builtin.get(id) != null) {
                throw new ScriptException(ScriptErrorCodes.E_BUILTIN, "$.id", "内置剧本不可删除：" + id);
            }
            if (!imported.containsKey(id)) {
                throw new ScriptException(ScriptErrorCodes.E_NOT_FOUND, "$.id", "剧本不存在：" + id);
            }
            checkInUse(id);
            File file = new File(scriptDir, id + JSON_SUFFIX);
            if (!file.exists()) {
                throw new ScriptException(ScriptErrorCodes.E_NOT_FOUND, "$.id",
                        "剧本文件已不存在：" + id);
            }
            if (!file.delete()) {
                throw new ScriptException(ScriptErrorCodes.E_IO, "$.id", "删除剧本文件失败：" + id);
            }
            imported.remove(id);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void replace(String id, byte[] content) throws ScriptException {
        Lock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            if (id == null || id.trim().isEmpty()) {
                throw new ScriptException(ScriptErrorCodes.E_ID, "$.id", "剧本 id 不能为空");
            }
            if (builtin.get(id) != null) {
                throw new ScriptException(ScriptErrorCodes.E_BUILTIN, "$.id", "内置剧本不可覆盖：" + id);
            }
            if (content == null) {
                throw new ScriptException(ScriptErrorCodes.E_PARSE, "$", "剧本内容为空");
            }
            if (content.length > maxBytes) {
                throw new ScriptException(ScriptErrorCodes.E_SIZE, "$", "剧本内容超过 " + maxBytes
                        + " 字节：实际 " + content.length + " 字节");
            }
            ScriptValidationResult result = validator.validate(content);
            if (!result.ok()) {
                throw new ScriptValidationException(result);
            }
            if (!id.equals(result.script().id())) {
                throw new ScriptException(ScriptErrorCodes.E_ID, "$.id",
                        "剧本 id 与请求不一致：" + result.script().id());
            }
            checkInUse(id);
            writeAtomically(new File(scriptDir, id + JSON_SUFFIX), content);
            imported.put(id, result.script());
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 导入落盘：校验、来源冲突检查、原子写入与缓存更新在同一把写锁内完成。
     *
     * <p>新建模式在目标 id 已存在时回 E_EXISTS；替换模式在目标不存在时按新建处理
     * （见 docs/scripts/README.md「replace 与新建同一路径」），两种模式都受内置 id
     * 与未归档局引用限制。</p>
     *
     * @param content 原始 UTF-8 字节，非空
     * @param mode    新建或替换
     * @return 落盘结果（含是否覆盖与校验告警）
     * @throws ScriptException 校验、冲突、引用或读写失败
     */
    public ScriptImportResult save(byte[] content, ScriptImportMode mode) throws ScriptException {
        return save(content, mode, false);
    }

    /** AI 生成落盘：与导入同一条校验与写入链路，只放开 {@code gen-} 前缀 id。 */
    public ScriptImportResult saveGenerated(byte[] content) throws ScriptException {
        return save(content, ScriptImportMode.CREATE, true);
    }

    private ScriptImportResult save(byte[] content, ScriptImportMode mode, boolean generated)
            throws ScriptException {
        if (mode == null) {
            throw new IllegalArgumentException("导入模式不能为空");
        }
        Lock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            if (content == null) {
                throw new ScriptException(ScriptErrorCodes.E_PARSE, "$", "剧本内容为空");
            }
            if (content.length > maxBytes) {
                throw new ScriptException(ScriptErrorCodes.E_SIZE, "$", "剧本内容超过 " + maxBytes
                        + " 字节：实际 " + content.length + " 字节");
            }
            ScriptValidationResult result = generated
                    ? validator.validateGenerated(content)
                    : validator.validate(content);
            if (!result.ok()) {
                throw new ScriptValidationException(result);
            }
            Script script = result.script();
            String id = script.id();
            if (builtin.get(id) != null) {
                throw new ScriptException(ScriptErrorCodes.E_BUILTIN, "$.id", "内置剧本不可覆盖：" + id);
            }
            boolean exists = imported.containsKey(id);
            if (!mode.isReplace() && exists) {
                throw new ScriptException(ScriptErrorCodes.E_EXISTS, "$.id", "剧本已存在：" + id);
            }
            checkInUse(id);
            writeAtomically(new File(scriptDir, id + JSON_SUFFIX), content);
            imported.put(id, script);
            return new ScriptImportResult(new ScriptInfo(script, ScriptSource.IMPORTED),
                    mode.isReplace() && exists, result.warnings());
        } finally {
            writeLock.unlock();
        }
    }

    private void checkInUse(String id) throws ScriptException {
        ScriptUsageIndex index = usageIndex;
        if (index != null && index.isInUse(id)) {
            throw new ScriptException(ScriptErrorCodes.E_IN_USE, "$.id",
                    "剧本仍被未归档的局引用，请先结束这些局：" + id);
        }
    }

    private void writeAtomically(File target, byte[] content) throws ScriptException {
        File dir = target.getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs() && !dir.exists()) {
            throw new ScriptException(ScriptErrorCodes.E_IO, "$", "创建剧本目录失败：" + dir);
        }
        Path temp = null;
        try {
            temp = Files.createTempFile(dir == null ? new File(".").toPath() : dir.toPath(),
                    target.getName(), ".tmp");
            Files.write(temp, content);
            try {
                Files.move(temp, target.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException error) {
                // 部分文件系统（网络盘、FAT）不支持原子移动：退化为普通 move，并留下日志。
                logger.sendWarn("[MBB-Trpg] 当前文件系统不支持原子移动，退化为普通 move：" + target);
                Files.move(temp, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            temp = null;
        } catch (IOException error) {
            throw new ScriptException(ScriptErrorCodes.E_IO, "$", "写入剧本文件失败：" + target, error);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // 清理临时文件失败不影响主流程
                }
            }
        }
    }

    @Override
    public Lock acquireReadLock() {
        return lock.readLock();
    }

    @Override
    public Lock acquireWriteLock() {
        return lock.writeLock();
    }

    private static String describe(ScriptValidationResult result) {
        StringBuilder text = new StringBuilder();
        for (ScriptIssue issue : result.reportedErrors()) {
            if (text.length() > 0) {
                text.append("；");
            }
            text.append(issue.code()).append(" @").append(issue.path());
        }
        return text.toString();
    }

    private void warn(String message) {
        if (logger != null) {
            logger.sendWarn("[MBB-Trpg] " + message);
        }
    }
}
