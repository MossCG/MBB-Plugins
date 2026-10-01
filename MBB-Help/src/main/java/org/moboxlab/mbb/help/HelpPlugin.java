package org.moboxlab.mbb.help;

import org.moboxlab.moboxbot.API.Plugin;

/**
 * MBB-Help 插件
 */
public class HelpPlugin extends Plugin {
    @Override
    public void onLoad() {
        saveDefaultConfig();
    }

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerCommand(this,new HelpCommand(this));
        getLogger().sendInfo("MBB-Help 已启用！");
    }

    @Override
    public void onDisable() {
        getLogger().sendInfo("MBB-Help 已停用！");
    }
}
