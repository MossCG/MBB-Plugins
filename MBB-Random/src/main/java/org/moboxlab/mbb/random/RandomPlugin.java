package org.moboxlab.mbb.random;

import org.moboxlab.moboxbot.API.Plugin;

/**
 * MBB-Random 插件
 */
public class RandomPlugin extends Plugin {
    @Override
    public void onEnable() {
        getServer().getPluginManager().registerCommand(this,new RandomCommand());
        getLogger().sendInfo("MBB-Random 已启用！");
    }

    @Override
    public void onDisable() {
        getLogger().sendInfo("MBB-Random 已停用！");
    }
}
