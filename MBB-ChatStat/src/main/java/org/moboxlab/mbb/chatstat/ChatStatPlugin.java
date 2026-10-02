package org.moboxlab.mbb.chatstat;

import org.moboxlab.moboxbot.API.Plugin;

/**
 * MBB-ChatStat 插件
 */
public class ChatStatPlugin extends Plugin {
    private ChatStatService statsService;

    @Override
    public void onLoad() {
        saveDefaultConfig();
    }

    @Override
    public void onEnable() {
        statsService = new ChatStatService(this);
        statsService.init();
        getServer().getPluginManager().registerListener(this,new ChatStatListener(this,statsService));
        getServer().getPluginManager().registerCommand(this,new ChatStatCommand(this,statsService));
        getLogger().sendInfo("MBB-ChatStat 已启用！");
    }

    @Override
    public void onDisable() {
        getLogger().sendInfo("MBB-ChatStat 已停用！");
    }
}
