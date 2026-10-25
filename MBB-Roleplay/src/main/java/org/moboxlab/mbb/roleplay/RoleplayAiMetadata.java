package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 调用点与 Prompt 版本标记
 */
public class RoleplayAiMetadata {
    private static final Map<Class<?>,String> TEMPLATE_VERSIONS = new ConcurrentHashMap<>();

    public static void tag(JSONObject params,String taskName,String promptVersion) {
        if (params == null) return;
        String task = taskName == null ? "" : taskName.trim();
        if (!task.isEmpty()) params.put("taskName",task);
        String version = promptVersion == null ? "" : promptVersion.trim();
        if (!version.isEmpty()) params.put("promptVersion",version);
    }

    /**
     * 辅助任务用构建类字节指纹代表提示词模板版本。
     *
     * 动态用户内容、情绪、时间和记忆每轮都会变化，不能直接拿来哈希；读取提示词构建类本身
     * 可以保持版本稳定，同时在真正修改该任务的提示词代码后产生新指纹。
     */
    public static void tagTemplate(JSONObject params,String taskName,Class<?> owner) {
        tag(params,taskName,templateVersion(owner));
    }

    public static String templateVersion(Class<?> owner) {
        if (owner == null) return "";
        String cached = TEMPLATE_VERSIONS.get(owner);
        if (cached != null) return cached;
        String version = classFingerprint(owner);
        if (version.isEmpty()) version = fingerprint(owner.getName());
        String previous = TEMPLATE_VERSIONS.putIfAbsent(owner,version);
        return previous == null ? version : previous;
    }

    private static String classFingerprint(Class<?> owner) {
        String simpleName = owner.getSimpleName();
        if (simpleName.isEmpty()) {
            String name = owner.getName();
            simpleName = name.substring(name.lastIndexOf('.') + 1);
        }
        InputStream input = null;
        try {
            input = owner.getResourceAsStream(simpleName + ".class");
            if (input == null) return "";
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) > 0) {
                output.write(buffer,0,read);
            }
            byte[] bytes = output.toByteArray();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(bytes));
        } catch (Exception e) {
            return "";
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static String fingerprint(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((text == null ? "" : text)
                    .getBytes(StandardCharsets.UTF_8));
            return toHex(bytes);
        } catch (Exception e) {
            return "";
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < Math.min(8,bytes.length); i++) {
            result.append(String.format("%02x",bytes[i] & 0xff));
        }
        return result.toString();
    }
}
