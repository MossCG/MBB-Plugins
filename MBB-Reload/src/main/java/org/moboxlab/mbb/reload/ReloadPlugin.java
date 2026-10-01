package org.moboxlab.mbb.reload;

import org.moboxlab.moboxbot.API.Plugin;

/**
 * MBB-Reload 插件
 */
public class ReloadPlugin extends Plugin {
    @Override
    public void onEnable() {
        getServer().getPluginManager().registerCommand(this,new ReloadCommand());
        getLogger().sendInfo("MBB-Reload 已启用！");
    }

    @Override
    public void onDisable() {
        getLogger().sendInfo("MBB-Reload 已停用！");
    }
}
