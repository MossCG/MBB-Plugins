package org.moboxlab.mbb.pighub;

import org.moboxlab.moboxbot.API.Plugin;

/**
 * MBB-PigHub 插件
 */
public class PigHubPlugin extends Plugin {
    @Override
    public void onLoad() {
        saveDefaultConfig();
    }

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerListener(this,new PigHubListener(this));
        getLogger().sendInfo("MBB-PigHub 已启用！");
    }

    @Override
    public void onDisable() {
        getLogger().sendInfo("MBB-PigHub 已停用！");
    }
}
