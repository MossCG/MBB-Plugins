package org.moboxlab.mbb.roleplay;

import org.moboxlab.moboxbot.API.Event.EventHandler;
import org.moboxlab.moboxbot.API.Event.EventPriority;
import org.moboxlab.moboxbot.API.Event.GroupMessageEvent;
import org.moboxlab.moboxbot.API.Event.Listener;
import org.moboxlab.moboxbot.API.Event.NoticeEvent;

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

    @EventHandler(priority = EventPriority.NORMAL,ignoreCancelled = false)
    public void onNotice(NoticeEvent event) {
        if (!plugin.getRoleplayConfig().enable) return;
        if (!"notify".equals(event.getNoticeType()) && !"poke".equals(event.getNoticeType())) return;
        if (!"poke".equals(event.getSubType())) return;
        if (isPokePluginEnabled()) return;
        plugin.getServer().getPluginManager().runTask(plugin,() -> service.handlePoke(event));
    }

    /**
     * MBB-Poke 已经在处理戳一戳，两个插件同时响应会导致同一件事回两次
     */
    private boolean isPokePluginEnabled() {
        return plugin.getServer().getPluginManager().isEnabled("MBB-Poke");
    }
}
