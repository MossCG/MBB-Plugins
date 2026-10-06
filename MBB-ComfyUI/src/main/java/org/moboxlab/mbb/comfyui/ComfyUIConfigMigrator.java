package org.moboxlab.mbb.comfyui;

import org.moboxlab.moboxbot.API.Plugin;
import org.moboxlab.moboxbot.API.Util.PluginConfig;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * ComfyUI 配置迁移与缺失项补全
 */
public class ComfyUIConfigMigrator {
    private static final String CURRENT_VERSION = "2";
    private static final String LEGACY_NEGATIVE =
            "lowres, bad anatomy, bad hands, extra fingers, watermark, text, signature";
    private static final String LEGACY_SUFFIX =
            "detailed background, dynamic composition, cinematic lighting, sharp focus";
    private static final String DEFAULT_NEGATIVE = LEGACY_NEGATIVE
            + ", duplicate character, clone, twins, multiple views, split screen, extra person";
    private static final String DEFAULT_SUFFIX = LEGACY_SUFFIX
            + ", correct character count, single instance of each character, no duplicate character";

    private static class Entry {
        private final String key;
        private final String value;
        private final String comment;

        private Entry(String key,String value,String comment) {
            this.key = key;
            this.value = value;
            this.comment = comment;
        }
    }

    private static final List<Entry> DEFAULTS = new ArrayList<>();

    static {
        DEFAULTS.add(new Entry("configVersion",CURRENT_VERSION,"配置结构版本，插件升级时自动维护"));
        DEFAULTS.add(new Entry("negativePrompt",DEFAULT_NEGATIVE,
                "默认负向提示词，包含重复角色抑制"));
        DEFAULTS.add(new Entry("promptPrefix",
                "masterpiece, best quality, highly detailed, anime illustration",
                "正向提示词前缀，自动拼在角色 prompt 前面"));
        DEFAULTS.add(new Entry("promptSuffix",DEFAULT_SUFFIX,
                "正向提示词后缀，自动拼在角色 prompt 后面"));
    }

    public static int ensure(Plugin plugin) {
        if (plugin == null) return 0;
        PluginConfig config = plugin.getConfig();
        File file = new File(plugin.getDataFolder(),"config.yml");
        if (!file.exists()) return 0;
        int version = config.getInt("configVersion",0);
        boolean legacyNegative = LEGACY_NEGATIVE.equals(config.getString("negativePrompt",""));
        boolean legacySuffix = LEGACY_SUFFIX.equals(config.getString("promptSuffix",""));
        boolean legacyPromptMax = version < 2 && "1000".equals(config.getString("promptMaxChars",""));
        int changed = 0;
        List<Entry> missing = new ArrayList<>();
        for (Entry entry : DEFAULTS) {
            if (!config.contains(entry.key)) missing.add(entry);
        }
        if (!missing.isEmpty()) {
            if (appendEntries(file,missing)) {
                changed += missing.size();
                config.load();
            } else {
                plugin.getLogger().sendWarn("自动补全 ComfyUI 配置失败，请检查 config.yml 权限！");
            }
        }
        if (version < 2) {
            config.set("configVersion",CURRENT_VERSION);
            changed++;
        }
        if (legacyNegative) {
            config.set("negativePrompt",DEFAULT_NEGATIVE);
            changed++;
        }
        if (legacySuffix) {
            config.set("promptSuffix",DEFAULT_SUFFIX);
            changed++;
        }
        if (legacyPromptMax) {
            config.set("promptMaxChars","2000");
            changed++;
        }
        if (changed > 0) {
            if (config.save()) {
                config.load();
            } else {
                plugin.getLogger().sendWarn("保存 ComfyUI 配置迁移结果失败，请检查 config.yml 权限！");
            }
        }
        return changed;
    }

    private static boolean appendEntries(File file,List<Entry> entries) {
        try {
            List<String> lines = Files.exists(Paths.get(file.getAbsolutePath()))
                    ? Files.readAllLines(Paths.get(file.getAbsolutePath()),StandardCharsets.UTF_8)
                    : new ArrayList<String>();
            lines.add("");
            lines.add("#以下配置由插件自动补全");
            for (Entry entry : entries) {
                lines.add("#"+entry.comment);
                lines.add(entry.key+": "+PluginConfig.formatValue(entry.value));
            }
            StringBuilder builder = new StringBuilder();
            for (String line : lines) builder.append(line).append("\n");
            Files.write(Paths.get(file.getAbsolutePath()),builder.toString().getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
