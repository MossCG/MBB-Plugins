package org.moboxlab.mbb.comfyui;

import org.moboxlab.moboxbot.API.Plugin;

/**
 * MBB-ComfyUI 插件
 */
public class ComfyUIPlugin extends Plugin {
    private ComfyUIConfig comfyConfig;
    private ComfyUIService service;

    @Override
    public void onLoad() {
        saveDefaultConfig();
        comfyConfig = ComfyUIConfig.load(this);
        getLogger().sendInfo("ComfyUI 配置已加载，地址："+comfyConfig.baseUrl);
    }

    @Override
    public void onEnable() {
        service = new ComfyUIService(this,comfyConfig);
        service.start();
        getServer().getPluginManager().registerService(this,service);
        getServer().getPluginManager().registerCommand(this,new ComfyUICommand(this,service));
        getLogger().sendInfo("MBB-ComfyUI 已启用！");
    }

    @Override
    public void onDisable() {
        if (service != null) service.stop();
        service = null;
        getLogger().sendInfo("MBB-ComfyUI 已停用！");
    }

    public ComfyUIConfig getComfyConfig() {
        return comfyConfig;
    }

    public ComfyUIService getService() {
        return service;
    }

    public void reloadConfig() {
        comfyConfig = ComfyUIConfig.load(this);
        if (service != null) service.reload(comfyConfig);
        getLogger().sendInfo("ComfyUI 配置已重载！");
    }
}
