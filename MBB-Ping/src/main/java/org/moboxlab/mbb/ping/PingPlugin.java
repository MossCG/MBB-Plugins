package org.moboxlab.mbb.ping;

import org.moboxlab.moboxbot.API.Plugin;

/**
 * MBB-Ping 插件
 */
public class PingPlugin extends Plugin {
    @Override
    public void onEnable() {
        getServer().getPluginManager().registerCommand(this,new PingCommand());
        getLogger().sendInfo("MBB-Ping 已启用！");
    }

    @Override
    public void onDisable() {
        getLogger().sendInfo("MBB-Ping 已停用！");
    }
}
