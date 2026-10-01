package org.moboxlab.mbb.welcome;

import org.moboxlab.moboxbot.API.Plugin;

/**
 * MBB-Welcome 插件
 */
public class WelcomePlugin extends Plugin {
    @Override
    public void onLoad() {
        saveDefaultConfig();
    }

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerListener(this,new WelcomeListener(this));
        getServer().getPluginManager().registerCommand(this,new WelcomeCommand(this));
        getLogger().sendInfo("MBB-Welcome 已启用！");
    }

    @Override
    public void onDisable() {
        getLogger().sendInfo("MBB-Welcome 已停用！");
    }
}
