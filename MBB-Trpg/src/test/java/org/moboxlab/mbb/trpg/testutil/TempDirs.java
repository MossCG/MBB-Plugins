package org.moboxlab.mbb.trpg.testutil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/** 临时目录工具：测试统一用这里创建与清理临时目录。 */
public final class TempDirs {

    private TempDirs() {
    }

    /** 创建一个临时目录。 */
    public static File create(String prefix) {
        try {
            return Files.createTempDirectory(prefix).toFile();
        } catch (IOException e) {
            throw new IllegalStateException("无法创建临时目录：" + prefix, e);
        }
    }

    /** 尽力递归删除目录；失败不抛异常（测试收尾用）。 */
    public static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        if (!file.delete()) {
            file.deleteOnExit();
        }
    }
}
