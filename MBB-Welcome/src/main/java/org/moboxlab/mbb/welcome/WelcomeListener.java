package org.moboxlab.mbb.welcome;

import org.moboxlab.moboxbot.API.Event.EventHandler;
import org.moboxlab.moboxbot.API.Event.EventPriority;
import org.moboxlab.moboxbot.API.Event.Listener;
import org.moboxlab.moboxbot.API.Event.NoticeEvent;
import org.moboxlab.moboxbot.API.OneBot.MessageUtil;

/**
 * 进群退群事件监听
 */
public class WelcomeListener implements Listener {
    private final WelcomePlugin plugin;

    public WelcomeListener(WelcomePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onNotice(NoticeEvent event) {
        String type = event.getNoticeType();
        if (!"group_increase".equals(type) && !"group_decrease".equals(type)) return;
        long groupID = event.getGroupID();
        long userID = event.getUserID();
        if (groupID <= 0 || userID <= 0) return;
        if (!enabled(groupID)) return;
        if ("group_increase".equals(type)) {
            String text = plugin.getConfig().getString("welcomeText","欢迎新猫猫进群，贴贴喵~");
            plugin.getServer().getOneBotClient().sendGroupMessage(
                    groupID,
                    MessageUtil.message(MessageUtil.at(userID),MessageUtil.text(" "+text)));
        } else {
            String text = plugin.getConfig().getString("leaveText","有猫猫离开了，喵呜~");
            plugin.getServer().getOneBotClient().sendGroupMessage(
                    groupID,
                    MessageUtil.message(MessageUtil.text(text)));
        }
    }

    private boolean enabled(long groupID) {
        String value = plugin.getServer().getStorage().get(plugin,"welcome_enabled_"+groupID);
        return "true".equalsIgnoreCase(value);
    }
}
