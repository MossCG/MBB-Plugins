package org.moboxlab.mbb.aiguard;

import org.moboxlab.moboxbot.API.Event.EventHandler;
import org.moboxlab.moboxbot.API.Event.EventPriority;
import org.moboxlab.moboxbot.API.Event.GroupMessageEvent;
import org.moboxlab.moboxbot.API.Event.Listener;

/**
 * 群消息实时审查监听
 */
public class GuardListener implements Listener {
    private final GuardPlugin plugin;
    private final GuardService guardService;

    public GuardListener(GuardPlugin plugin,GuardService guardService) {
        this.plugin = plugin;
        this.guardService = guardService;
    }

    @EventHandler(priority = EventPriority.MONITOR,ignoreCancelled = false)
    public void onGroupMessage(GroupMessageEvent event) {
        if (!plugin.getGuardConfig().enable) return;
        plugin.getServer().getPluginManager().runTask(plugin,() -> guardService.handle(event));
    }
}
