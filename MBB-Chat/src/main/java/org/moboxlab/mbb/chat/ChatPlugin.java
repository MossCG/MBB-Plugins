package org.moboxlab.mbb.chat;

import org.moboxlab.moboxbot.API.Plugin;

/**
 * MBB-Chat 插件
 */
public class ChatPlugin extends Plugin {
    @Override
    public void onLoad() {
        saveDefaultConfig();
    }

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerCommand(this,new ChatCommand(this));
        getLogger().sendInfo("MBB-Chat 已启用！");
    }

    @Override
    public void onDisable() {
        getLogger().sendInfo("MBB-Chat 已停用！");
    }
}
