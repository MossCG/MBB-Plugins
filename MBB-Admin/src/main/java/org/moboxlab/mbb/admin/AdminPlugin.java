package org.moboxlab.mbb.admin;

import org.moboxlab.moboxbot.API.Plugin;

/**
 * MBB-Admin 插件
 */
public class AdminPlugin extends Plugin {
    @Override
    public void onEnable() {
        getServer().getPluginManager().registerCommand(this,new AdminCommand());
        getLogger().sendInfo("MBB-Admin 已启用！");
    }

    @Override
    public void onDisable() {
        getLogger().sendInfo("MBB-Admin 已停用！");
    }
}
