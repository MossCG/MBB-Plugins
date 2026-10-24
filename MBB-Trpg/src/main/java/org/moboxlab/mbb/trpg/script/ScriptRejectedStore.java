package org.moboxlab.mbb.trpg.script;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.moboxlab.moboxbot.API.PluginLogger;

/**
 * 被拒绝导入与生成失败的原文存档：{@code <dataFolder>/imports/rejected/}。
 *
 * <p>只用于排查：任何失败都不落剧本库，原文（空内容写占位记录）按时间戳命名保存，
 * 并按 {@code script.rejectedMaxFiles} 只保留最新若干份，超出的从最旧开始删除。</p>
 */
public final class ScriptRejectedStore {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String SUFFIX = ".json";
    private static final String EMPTY_PLACEHOLDER = "【原始内容为空】";

    private final File dir;
    private final int maxFiles;
    private final PluginLogger logger;
    private final Object lock = new Object();

    public ScriptRejectedStore(File dir, int maxFiles, PluginLogger logger) {
        if (dir == null) {
            throw new IllegalArgumentException("拒绝目录不能为空");
        }
        this.dir = dir;
        this.maxFiles = maxFiles;
        this.logger = logger;
    }

    /** 存档目录。 */
    public File directory() {
        return dir;
    }

    /**
     * 保存一份被拒绝的原文。
     *
     * @param content    原始文本；为 null 或空白时写一条占位记录
     * @param operatorId 发起人 QQ 号，只用于文件名与排查
     * @param note       失败原因摘要（错误码等），写进占位记录
     * @return 写入的文件；失败返回 null（只告警，不阻断回执）
     */
    public File save(String content, long operatorId, String note) {
        String text = content == null || content.trim().isEmpty()
                ? EMPTY_PLACEHOLDER + " " + (note == null ? "" : note)
                : content;
        synchronized (lock) {
            try {
                ensureDir();
                File target = new File(dir, fileName(operatorId));
                Files.write(target.toPath(), text.getBytes(UTF8));
                prune();
                return target;
            } catch (IOException error) {
                warn("保存拒绝原文失败：" + error);
                return null;
            }
        }
    }

    /** 文件名固定为 {@code <epoch毫秒>-<用户号>.json}；同一毫秒重复保存时顺延毫秒，保持格式不变。 */
    private String fileName(long operatorId) {
        long epoch = System.currentTimeMillis();
        File target = new File(dir, epoch + "-" + operatorId + SUFFIX);
        while (target.exists()) {
            epoch++;
            target = new File(dir, epoch + "-" + operatorId + SUFFIX);
        }
        return target.getName();
    }

    private void ensureDir() throws IOException {
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) {
            throw new IOException("创建拒绝目录失败：" + dir);
        }
    }

    private void prune() {
        if (maxFiles <= 0) {
            return;
        }
        List<File> files = files();
        if (files.size() <= maxFiles) {
            return;
        }
        for (int i = 0; i < files.size() - maxFiles; i++) {
            File file = files.get(i);
            if (!file.delete()) {
                warn("清理拒绝原文失败：" + file.getName());
            }
        }
    }

    private List<File> files() {
        File[] list = dir.listFiles();
        if (list == null) {
            return Collections.emptyList();
        }
        List<File> files = new ArrayList<File>(Arrays.asList(list));
        Collections.sort(files, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                return left.getName().compareTo(right.getName());
            }
        });
        return files;
    }

    private void warn(String message) {
        if (logger != null) {
            logger.sendWarn("[MBB-Trpg] " + message);
        }
    }
}
