package org.moboxlab.mbb.trpg.testutil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

/** 测试用的随包资源读取工具：剧本校验、内置库与示例剧本用例共用。 */
public final class ScriptResources {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private ScriptResources() {
    }

    /** 按类路径读取资源并解码为 UTF-8 文本；资源不存在时抛 IllegalStateException。 */
    public static String read(String path) {
        InputStream in = ScriptResources.class.getClassLoader().getResourceAsStream(path);
        if (in == null) {
            throw new IllegalStateException("测试资源不存在：" + path);
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), UTF8);
        } catch (IOException e) {
            throw new IllegalStateException("读取测试资源失败：" + path, e);
        } finally {
            closeQuietly(in);
        }
    }

    /** 随包示例剧本的文本内容。 */
    public static String example() {
        return read("scripts/coc-example.json");
    }

    private static void closeQuietly(InputStream in) {
        try {
            in.close();
        } catch (IOException ignored) {
            // 测试工具，关闭失败无需处理
        }
    }
}
