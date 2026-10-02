package org.moboxlab.mbb.chatstat;

import org.moboxlab.moboxbot.API.Event.EventHandler;
import org.moboxlab.moboxbot.API.Event.EventPriority;
import org.moboxlab.moboxbot.API.Event.GroupMessageEvent;
import org.moboxlab.moboxbot.API.Event.Listener;

/**
 * 群消息采集
 */
public class ChatStatListener implements Listener {
    private final ChatStatPlugin plugin;
    private final ChatStatService statsService;

    public ChatStatListener(ChatStatPlugin plugin,ChatStatService statsService) {
        this.plugin = plugin;
        this.statsService = statsService;
    }

    @EventHandler(priority = EventPriority.MONITOR,ignoreCancelled = false)
    public void onGroupMessage(GroupMessageEvent event) {
        if (!plugin.getConfig().getBoolean("enable",true)) return;
        plugin.getServer().getPluginManager().runTask(plugin,() -> statsService.record(event));
    }
}
