package org.moboxlab.mbb.trpg.script;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 内置剧本库：JAR 资源 {@code scripts/index.txt} 列出 id，逐个读取 {@code scripts/<id>.json}
 * 并校验后缓存。
 *
 * <p>内置剧本只读：删除回 E_BUILTIN；替换与新建属导入路径，S10 落地前统一回 E_IMPORT_STATE。
 * 同 id 以内置为准（导入侧禁止使用内置 id，见 docs/scripts/README.md）。</p>
 *
 * <p>加载是启动时的一次性动作：任一内置剧本缺失或校验失败都视为分发问题，直接抛
 * {@link IllegalStateException}，由装配层把插件置为不可用并记错误日志。</p>
 */
public final class BuiltinScriptCatalog implements ScriptCatalog {

    private static final String INDEX_RESOURCE = "scripts/index.txt";
    private static final String SCRIPT_RESOURCE_PREFIX = "scripts/";
    private static final String SCRIPT_RESOURCE_SUFFIX = ".json";
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final Map<String, Script> scripts;
    private final List<String> warnings;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * 加载全部内置剧本。
     *
     * @param loader    资源加载器，生产用插件的类加载器
     * @param validator 校验器
     * @throws IllegalStateException 索引缺失、剧本缺失、id 重复或校验失败时抛出
     */
    public BuiltinScriptCatalog(ClassLoader loader, ScriptValidator validator) {
        if (loader == null) {
            throw new IllegalArgumentException("资源加载器不能为空");
        }
        if (validator == null) {
            throw new IllegalArgumentException("校验器不能为空");
        }
        List<String> ids = readIndex(loader);
        Map<String, Script> loaded = new LinkedHashMap<String, Script>();
        List<String> collectedWarnings = new ArrayList<String>();
        for (String id : ids) {
            Script script = loadOne(loader, validator, id, collectedWarnings);
            loaded.put(id, script);
        }
        this.scripts = Collections.unmodifiableMap(loaded);
        this.warnings = Collections.unmodifiableList(collectedWarnings);
    }

    private static List<String> readIndex(ClassLoader loader) {
        String text = readResource(loader, INDEX_RESOURCE, true);
        TreeSet<String> ids = new TreeSet<String>();
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (!ids.add(trimmed)) {
                throw new IllegalStateException("内置剧本索引存在重复 id：" + trimmed);
            }
        }
        if (ids.isEmpty()) {
            throw new IllegalStateException("内置剧本索引为空：" + INDEX_RESOURCE);
        }
        return new ArrayList<String>(ids);
    }

    private static Script loadOne(ClassLoader loader, ScriptValidator validator, String id,
                                  List<String> warnings) {
        String resource = SCRIPT_RESOURCE_PREFIX + id + SCRIPT_RESOURCE_SUFFIX;
        String text = readResource(loader, resource, false);
        ScriptValidationResult result = validator.validate(text);
        if (!result.ok()) {
            StringBuilder detail = new StringBuilder();
            for (ScriptIssue issue : result.errors()) {
                if (detail.length() > 0) {
                    detail.append("；");
                }
                detail.append(issue.code()).append(" @").append(issue.path());
            }
            throw new IllegalStateException("内置剧本校验失败：" + id + "：" + detail);
        }
        for (ScriptIssue warning : result.warnings()) {
            warnings.add(resource + "：" + warning.code() + " @"
                    + warning.path() + " " + warning.message());
        }
        return result.script();
    }

    private static String readResource(ClassLoader loader, String resource, boolean required) {
        InputStream in = loader.getResourceAsStream(resource);
        if (in == null) {
            if (required) {
                throw new IllegalStateException("缺少内置资源：" + resource);
            }
            throw new IllegalStateException("内置剧本索引指向不存在的资源：" + resource);
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), UTF8);
        } catch (IOException e) {
            throw new IllegalStateException("读取内置资源失败：" + resource, e);
        } finally {
            closeQuietly(in);
        }
    }

    private static void closeQuietly(InputStream in) {
        try {
            in.close();
        } catch (IOException ignored) {
            // 关闭失败不影响已读完的内容
        }
    }

    @Override
    public Script get(String id) {
        if (id == null) {
            return null;
        }
        return scripts.get(id);
    }

    @Override
    public ScriptInfo info(String id) {
        Script script = get(id);
        if (script == null) {
            return null;
        }
        return new ScriptInfo(script, ScriptSource.BUILTIN);
    }

    @Override
    public List<ScriptInfo> list() {
        List<ScriptInfo> infos = new ArrayList<ScriptInfo>(scripts.size());
        for (Script script : scripts.values()) {
            infos.add(new ScriptInfo(script, ScriptSource.BUILTIN));
        }
        return Collections.unmodifiableList(infos);
    }

    @Override
    public void delete(String id) throws ScriptException {
        if (scripts.containsKey(id)) {
            throw new ScriptException(ScriptErrorCodes.E_BUILTIN, "$.id",
                    "内置剧本不可删除：" + id);
        }
        throw new ScriptException(ScriptErrorCodes.E_NOT_FOUND, "$.id", "剧本不存在：" + id);
    }

    @Override
    public void replace(String id, byte[] content) throws ScriptException {
        throw new ScriptException(ScriptErrorCodes.E_IMPORT_STATE, "$.id",
                "剧本导入与替换尚未启用：" + id);
    }

    @Override
    public Lock acquireReadLock() {
        return lock.readLock();
    }

    @Override
    public Lock acquireWriteLock() {
        return lock.writeLock();
    }

    /** 加载期收集到的校验告警，供装配层打成插件日志。 */
    public List<String> warnings() {
        return warnings;
    }

    /** 内置剧本数量。 */
    public int size() {
        return scripts.size();
    }
}
