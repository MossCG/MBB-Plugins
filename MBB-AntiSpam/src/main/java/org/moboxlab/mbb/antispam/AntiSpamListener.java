package org.moboxlab.mbb.antispam;

import org.moboxlab.moboxbot.API.Event.EventHandler;
import org.moboxlab.moboxbot.API.Event.EventPriority;
import org.moboxlab.moboxbot.API.Event.GroupMessageEvent;
import org.moboxlab.moboxbot.API.Event.Listener;

/**
 * 群消息刷屏检测监听
 */
public class AntiSpamListener implements Listener {
    private final AntiSpamPlugin plugin;
    private final AntiSpamService spamService;

    public AntiSpamListener(AntiSpamPlugin plugin,AntiSpamService spamService) {
        this.plugin = plugin;
        this.spamService = spamService;
    }

    @EventHandler(priority = EventPriority.MONITOR,ignoreCancelled = false)
    public void onGroupMessage(GroupMessageEvent event) {
        if (!plugin.getAntiSpamConfig().enable) return;
        //统计与处置都不在事件线程里做，避免阻塞事件分发
        plugin.getServer().getPluginManager().runTask(plugin,() -> spamService.handle(event));
    }
}
