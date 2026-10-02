package org.moboxlab.mbb.ai;

import org.moboxlab.moboxbot.API.Plugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * MBB-AI 插件
 */
public class AIPlugin extends Plugin {
    private AIConfig aiConfig;
    private AIService service;

    @Override
    public void onLoad() {
        saveDefaultConfig();
        saveDefaultProfiles();
        aiConfig = AIConfig.load(this);
        getLogger().sendInfo("AI 配置已加载，模型配置数："+aiConfig.getProfileCount());
    }

    @Override
    public void onEnable() {
        service = new AIService(this,aiConfig);
        getServer().getPluginManager().registerService(this,service);
        getServer().getPluginManager().registerCommand(this,new AICommand(this));
        getLogger().sendInfo("MBB-AI 已启用！");
    }

    @Override
    public void onDisable() {
        service = null;
        getLogger().sendInfo("MBB-AI 已停用！");
    }

    public AIService getService() {
        return service;
    }

    public AIConfig getAIConfig() {
        return aiConfig;
    }

    public void reloadConfig() {
        aiConfig = AIConfig.load(this);
        if (service != null) service.reload(aiConfig);
        getLogger().sendInfo("AI 配置已重载！");
    }

    private void saveDefaultProfiles() {
        try {
            File file = new File(getDataFolder(),"profiles.json");
            if (file.exists()) return;
            byte[] bytes = readResource("profiles.json");
            if (bytes == null) {
                getLogger().sendWarn("插件 JAR 里没有 profiles.json！");
                return;
            }
            Files.write(Paths.get(file.getAbsolutePath()),bytes);
        } catch (Exception e) {
            getLogger().sendException(e);
        }
    }
}
