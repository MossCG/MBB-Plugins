package org.moboxlab.mbb.version;

import org.moboxlab.moboxbot.API.Plugin;

/**
 * MBB-Version 插件
 */
public class VersionPlugin extends Plugin {
    @Override
    public void onEnable() {
        getServer().getPluginManager().registerCommand(this,new VersionCommand(this));
        getLogger().sendInfo("MBB-Version 已启用！");
    }

    @Override
    public void onDisable() {
        getLogger().sendInfo("MBB-Version 已停用！");
    }
}
