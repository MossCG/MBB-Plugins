package org.moboxlab.mbb.pighub;

import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Event.EventHandler;
import org.moboxlab.moboxbot.API.Event.EventPriority;
import org.moboxlab.moboxbot.API.Event.GroupMessageEvent;
import org.moboxlab.moboxbot.API.Event.Listener;
import org.moboxlab.moboxbot.API.Event.PrivateMessageEvent;
import org.moboxlab.moboxbot.API.OneBot.MessageUtil;

/**
 * 关键词触发猪猪图片
 */
public class PigHubListener implements Listener {
    private final PigHubPlugin plugin;

    public PigHubListener(PigHubPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onGroupMessage(GroupMessageEvent event) {
        handle(event.getRawMessage(),event.getUserID(),event.getGroupID(),event.getRaw().getLongValue("self_id"));
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPrivateMessage(PrivateMessageEvent event) {
        handle(event.getRawMessage(),event.getUserID(),0L,event.getRaw().getLongValue("self_id"));
    }

    private void handle(String rawMessage,long userID,long groupID,long selfID) {
        if (rawMessage == null || userID <= 0 || userID == selfID) return;
        String keyword = plugin.getConfig().getString("keyword","来只猪猪");
        if (keyword == null || keyword.isEmpty() || !rawMessage.contains(keyword)) return;
        plugin.getServer().getPluginManager().runTask(plugin,() -> sendPig(groupID,userID));
    }

    private void sendPig(long groupID,long userID) {
        String apiUrl = plugin.getConfig().getString("apiUrl","https://pighub.top/api/images?sort=2");
        int cacheMinutes = plugin.getConfig().getInt("cacheMinutes",10);
        String imageUrl = PigHubService.getRandomImage(apiUrl,cacheMinutes);
        if (imageUrl == null) {
            send(groupID,userID,MessageUtil.message(MessageUtil.text("猪猪跑了，稍后再试喵~")));
            return;
        }
        String replyText = plugin.getConfig().getString("replyText","猪猪来啦！");
        send(groupID,userID,MessageUtil.message(MessageUtil.text(replyText),MessageUtil.image(imageUrl)));
    }

    private void send(long groupID,long userID,com.alibaba.fastjson.JSONArray message) {
        if (groupID > 0) {
            plugin.getServer().getOneBotClient().sendGroupMessage(groupID,message);
        } else {
            plugin.getServer().getOneBotClient().sendPrivateMessage(userID,message);
        }
    }
}
