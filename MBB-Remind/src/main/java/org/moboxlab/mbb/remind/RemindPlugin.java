package org.moboxlab.mbb.remind;

import org.moboxlab.moboxbot.API.Plugin;

/**
 * MBB-Remind 插件
 */
public class RemindPlugin extends Plugin {
    private int maxRemindSecond = 604800;

    @Override
    public void onLoad() {
        saveDefaultConfig();
    }

    @Override
    public void onEnable() {
        maxRemindSecond = getConfig().getInt("maxRemindSecond",604800);
        if (maxRemindSecond < 1) maxRemindSecond = 604800;
        getServer().getPluginManager().registerCommand(this,new RemindCommand(this,maxRemindSecond));
        getLogger().sendInfo("MBB-Remind 已启用！");
    }

    @Override
    public void onDisable() {
        getLogger().sendInfo("MBB-Remind 已停用！");
    }

    public int getMaxRemindSecond() {
        return maxRemindSecond;
    }
}
