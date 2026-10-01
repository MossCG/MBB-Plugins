package org.moboxlab.mbb.poll;

import org.moboxlab.moboxbot.API.Plugin;

/**
 * MBB-Poll 插件
 */
public class PollPlugin extends Plugin {
    @Override
    public void onEnable() {
        getServer().getPluginManager().registerCommand(this,new PollCommand(this));
        getServer().getPluginManager().registerCommand(this,new VoteCommand());
        getLogger().sendInfo("MBB-Poll 已启用！");
    }

    @Override
    public void onDisable() {
        PollService.clear();
        getLogger().sendInfo("MBB-Poll 已停用！");
    }
}
