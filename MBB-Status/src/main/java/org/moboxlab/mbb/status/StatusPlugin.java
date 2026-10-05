package org.moboxlab.mbb.status;

import org.moboxlab.moboxbot.API.Plugin;

/**
 * MBB-Status 插件
 */
public class StatusPlugin extends Plugin {
    @Override
    public void onLoad() {
        saveDefaultConfig();
    }

    @Override
    public void onEnable() {
        int refreshSecond = getConfig().getInt("refreshSecond",10);
        if (refreshSecond < 3) refreshSecond = 3;
        SystemStatusService.setGpuCommand(getConfig().getString("nvidiaSmiPath","nvidia-smi"));
        getServer().getPluginManager().runTaskTimer(this,SystemStatusService::sampleNetwork,3,refreshSecond);
        getServer().getPluginManager().registerCommand(this,new StatusCommand(this));
        getLogger().sendInfo("MBB-Status 已启用，采样间隔："+refreshSecond+" 秒！");
    }

    @Override
    public void onDisable() {
        getLogger().sendInfo("MBB-Status 已停用！");
    }
}
