package org.moboxlab.mbb.comfyui;

import org.moboxlab.moboxbot.API.Plugin;

/**
 * MBB-ComfyUI 配置
 */
public class ComfyUIConfig {
    public boolean enable = true;
    public String baseUrl = "http://192.168.10.10:8188";
    public int cooldownSecond = 600;
    public int timeoutSecond = 600;
    public int queueLimit = 3;
    public int pollIntervalSecond = 2;
    public String checkpoint = "NoobAI-XL-v1.1.safetensors";
    public String defaultPreset = "square";
    public int defaultWidth = 1280;
    public int defaultHeight = 1280;
    public int minWidth = 256;
    public int minHeight = 256;
    public int maxWidth = 1536;
    public int maxHeight = 1536;
    public int maxPixels = 2360000;
    public int sizeMultiple = 64;
    public int steps = 32;
    public double cfg = 5.5;
    public String sampler = "dpmpp_2m";
    public String scheduler = "karras";
    public double denoise = 1.0;
    public String negativePrompt = "lowres, bad anatomy, bad hands, extra fingers, watermark, text, signature";
    public String promptPrefix = "masterpiece, best quality, highly detailed, anime illustration";
    public String promptSuffix = "detailed background, dynamic composition, cinematic lighting, sharp focus";
    public int promptMinChars = 4;
    public int promptMaxChars = 1000;
    public String outputDirectory = "images";

    public static ComfyUIConfig load(Plugin plugin) {
        ComfyUIConfig config = new ComfyUIConfig();
        config.enable = plugin.getConfig().getBoolean("enable",true);
        config.baseUrl = plugin.getConfig().getString("baseUrl","http://192.168.10.10:8188");
        config.cooldownSecond = plugin.getConfig().getInt("cooldownSecond",600);
        config.timeoutSecond = plugin.getConfig().getInt("timeoutSecond",600);
        config.queueLimit = plugin.getConfig().getInt("queueLimit",3);
        config.pollIntervalSecond = plugin.getConfig().getInt("pollIntervalSecond",2);
        config.checkpoint = plugin.getConfig().getString("checkpoint","NoobAI-XL-v1.1.safetensors");
        config.defaultPreset = plugin.getConfig().getString("defaultPreset","square");
        config.defaultWidth = plugin.getConfig().getInt("defaultWidth",1280);
        config.defaultHeight = plugin.getConfig().getInt("defaultHeight",1280);
        config.minWidth = plugin.getConfig().getInt("minWidth",256);
        config.minHeight = plugin.getConfig().getInt("minHeight",256);
        config.maxWidth = plugin.getConfig().getInt("maxWidth",1536);
        config.maxHeight = plugin.getConfig().getInt("maxHeight",1536);
        config.maxPixels = plugin.getConfig().getInt("maxPixels",2360000);
        config.sizeMultiple = plugin.getConfig().getInt("sizeMultiple",64);
        config.steps = plugin.getConfig().getInt("steps",32);
        config.cfg = parseDouble(plugin.getConfig().getString("cfg","5.5"),5.5);
        config.sampler = plugin.getConfig().getString("sampler","dpmpp_2m");
        config.scheduler = plugin.getConfig().getString("scheduler","karras");
        config.denoise = parseDouble(plugin.getConfig().getString("denoise","1.0"),1.0);
        config.negativePrompt = plugin.getConfig().getString("negativePrompt",
                "lowres, bad anatomy, bad hands, extra fingers, watermark, text, signature");
        config.promptPrefix = plugin.getConfig().getString("promptPrefix",
                "masterpiece, best quality, highly detailed, anime illustration");
        config.promptSuffix = plugin.getConfig().getString("promptSuffix",
                "detailed background, dynamic composition, cinematic lighting, sharp focus");
        config.promptMinChars = plugin.getConfig().getInt("promptMinChars",4);
        config.promptMaxChars = plugin.getConfig().getInt("promptMaxChars",1000);
        config.outputDirectory = plugin.getConfig().getString("outputDirectory","images");
        if (config.baseUrl == null || config.baseUrl.trim().isEmpty()) {
            config.baseUrl = "http://192.168.10.10:8188";
        }
        if (config.checkpoint == null || config.checkpoint.trim().isEmpty()) {
            config.checkpoint = "NoobAI-XL-v1.1.safetensors";
        }
        if (config.defaultPreset == null || config.defaultPreset.trim().isEmpty()) {
            config.defaultPreset = "square";
        }
        if (config.sampler == null || config.sampler.trim().isEmpty()) config.sampler = "dpmpp_2m";
        if (config.scheduler == null || config.scheduler.trim().isEmpty()) config.scheduler = "karras";
        if (config.outputDirectory == null || config.outputDirectory.trim().isEmpty()) {
            config.outputDirectory = "images";
        }
        if (config.promptPrefix == null) config.promptPrefix = "";
        if (config.promptSuffix == null) config.promptSuffix = "";
        if (config.cooldownSecond < 0) config.cooldownSecond = 0;
        if (config.timeoutSecond < 30) config.timeoutSecond = 30;
        if (config.timeoutSecond > 1800) config.timeoutSecond = 1800;
        if (config.queueLimit < 1) config.queueLimit = 1;
        if (config.queueLimit > 20) config.queueLimit = 20;
        if (config.pollIntervalSecond < 1) config.pollIntervalSecond = 1;
        if (config.pollIntervalSecond > 10) config.pollIntervalSecond = 10;
        if (config.minWidth < 256) config.minWidth = 256;
        if (config.minHeight < 256) config.minHeight = 256;
        if (config.maxWidth < config.minWidth) config.maxWidth = config.minWidth;
        if (config.maxHeight < config.minHeight) config.maxHeight = config.minHeight;
        if (config.maxWidth > 2048) config.maxWidth = 2048;
        if (config.maxHeight > 2048) config.maxHeight = 2048;
        if (config.maxPixels < 262144) config.maxPixels = 262144;
        if (config.maxPixels > 4194304) config.maxPixels = 4194304;
        if (config.sizeMultiple < 8) config.sizeMultiple = 8;
        if (config.sizeMultiple > 128) config.sizeMultiple = 128;
        if (config.steps < 1) config.steps = 1;
        if (config.steps > 100) config.steps = 100;
        if (config.cfg < 0.1) config.cfg = 0.1;
        if (config.cfg > 30) config.cfg = 30;
        if (config.denoise < 0) config.denoise = 0;
        if (config.denoise > 1) config.denoise = 1;
        if (config.promptMinChars < 1) config.promptMinChars = 1;
        if (config.promptMaxChars < config.promptMinChars) config.promptMaxChars = config.promptMinChars;
        if (config.promptMaxChars > 4000) config.promptMaxChars = 4000;
        return config;
    }

    private static double parseDouble(String value,double defaultValue) {
        try {
            return Double.parseDouble(value == null ? "" : value.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
