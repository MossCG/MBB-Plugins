package org.moboxlab.mbb.leave;

import org.moboxlab.moboxbot.API.Plugin;

/**
 * MBB-Leave 插件
 */
public class LeavePlugin extends Plugin {
    @Override
    public void onLoad() {
        saveDefaultConfig();
    }

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerCommand(this,new LeaveCommand(this));
        getLogger().sendInfo("MBB-Leave 已启用！");
    }

    @Override
    public void onDisable() {
        getLogger().sendInfo("MBB-Leave 已停用！");
    }
}
