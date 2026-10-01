package org.moboxlab.mbb.plugins;

import org.moboxlab.moboxbot.API.Plugin;

/**
 * MBB-Plugins 插件
 */
public class PluginsPlugin extends Plugin {
    @Override
    public void onEnable() {
        getServer().getPluginManager().registerCommand(this,new PluginsCommand(this));
        getLogger().sendInfo("MBB-Plugins 已启用！");
    }

    @Override
    public void onDisable() {
        getLogger().sendInfo("MBB-Plugins 已停用！");
    }
}
