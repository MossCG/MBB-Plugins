package org.moboxlab.mbb.trpg;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 包边界验收（规格 K2）：插件源码只允许依赖主程序的 {@code org.moboxlab.moboxbot.API}。
 *
 * <p>直接扫源码文本，不依赖编译期信息：即使某个类暂时没人调用，也不许把它写进 import。</p>
 */
public class SourceBoundaryTest {

    private static final String ALLOWED_PREFIX = "org.moboxlab.moboxbot.API.";

    @Test
    public void mainSourcesOnlyImportApiPackage() throws Exception {
        File sourceRoot = locateMainSourceRoot();
        List<File> files = new ArrayList<File>();
        collect(sourceRoot, files);
        assertTrue("应扫描到主源码文件，实际：" + files.size(), files.size() >= 30);

        List<String> violations = new ArrayList<String>();
        for (File file : files) {
            int lineNumber = 0;
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), Charset.forName("UTF-8")));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    String trimmed = line.trim();
                    if (!trimmed.contains("org.moboxlab.moboxbot")) {
                        continue;
                    }
                    if (!trimmed.startsWith("import ")) {
                        continue;
                    }
                    if (!trimmed.startsWith("import " + ALLOWED_PREFIX)) {
                        violations.add(file.getName() + ":" + lineNumber + " " + trimmed);
                    }
                }
            } finally {
                reader.close();
            }
        }
        assertTrue("只允许 import org.moboxlab.moboxbot.API.*，越界项：" + violations, violations.isEmpty());
    }

    @Test
    public void mainSourcesAvoidForbiddenInternalHooks() throws Exception {
        File sourceRoot = locateMainSourceRoot();
        List<File> files = new ArrayList<File>();
        collect(sourceRoot, files);
        List<String> violations = new ArrayList<String>();
        String[] forbidden = {"BasicInfo", "StorageServiceImpl", "SqlExecutor", "OneBotClientImpl"};
        for (File file : files) {
            int lineNumber = 0;
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), Charset.forName("UTF-8")));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    for (String keyword : forbidden) {
                        if (line.contains(keyword)) {
                            violations.add(file.getName() + ":" + lineNumber + " 引用了 " + keyword);
                        }
                    }
                }
            } finally {
                reader.close();
            }
        }
        assertTrue("不得触碰主程序内部实现：" + violations, violations.isEmpty());
    }

    @Test
    public void packageLayoutStaysInDeclaredPath() {
        File sourceRoot = locateMainSourceRoot();
        File pluginJson = new File(new File(sourceRoot.getParentFile(), "resources"), "plugin.json");
        assertTrue("随包资源里必须有 plugin.json：" + pluginJson.getAbsolutePath(), pluginJson.isFile());
    }

    /** MBB-Trpg/src/main/java；兼容从仓库根或插件目录运行测试。 */
    private File locateMainSourceRoot() {
        List<File> candidates = new ArrayList<File>();
        candidates.add(new File("src/main/java"));
        candidates.add(new File("MBB-Trpg/src/main/java"));
        File current = new File(System.getProperty("user.dir", ".")).getAbsoluteFile();
        while (current != null) {
            candidates.add(new File(current, "src/main/java"));
            candidates.add(new File(current, "MBB-Trpg/src/main/java"));
            current = current.getParentFile();
        }
        for (File candidate : candidates) {
            if (candidate.isDirectory()) {
                return candidate;
            }
        }
        fail("找不到主源码目录 src/main/java");
        return null;
    }

    private void collect(File root, List<File> files) {
        File[] children = root.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collect(child, files);
            } else if (child.getName().endsWith(".java")) {
                files.add(child);
            }
        }
    }
}
