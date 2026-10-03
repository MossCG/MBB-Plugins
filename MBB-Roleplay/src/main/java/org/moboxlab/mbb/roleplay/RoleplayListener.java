package org.moboxlab.mbb.roleplay;

import org.moboxlab.moboxbot.API.Event.EventHandler;
import org.moboxlab.moboxbot.API.Event.EventPriority;
import org.moboxlab.moboxbot.API.Event.GroupMessageEvent;
import org.moboxlab.moboxbot.API.Event.Listener;

/**
 * 群消息监听
 */
public class RoleplayListener implements Listener {
    private final RoleplayPlugin plugin;
    private final RoleplayService service;

    public RoleplayListener(RoleplayPlugin plugin,RoleplayService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR,ignoreCancelled = false)
    public void onGroupMessage(GroupMessageEvent event) {
        if (!plugin.getRoleplayConfig().enable) return;
        plugin.getServer().getPluginManager().runTask(plugin,() -> service.handle(event));
    }
}
