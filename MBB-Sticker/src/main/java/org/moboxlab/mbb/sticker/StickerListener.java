package org.moboxlab.mbb.sticker;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Event.EventHandler;
import org.moboxlab.moboxbot.API.Event.EventPriority;
import org.moboxlab.moboxbot.API.Event.Listener;
import org.moboxlab.moboxbot.API.Event.PrivateMessageEvent;

/**
 * 私聊图片收录监听
 */
public class StickerListener implements Listener {
    private final StickerReceiveService receiveService;

    public StickerListener(StickerReceiveService receiveService) {
        this.receiveService = receiveService;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPrivateMessage(PrivateMessageEvent event) {
        if (event == null || !receiveService.active(event.getUserID())) return;
        JSONArray message = event.getMessage();
        if (message == null) return;
        for (int i = 0; i < message.size(); i++) {
            JSONObject segment = message.getJSONObject(i);
            if (segment == null || !"image".equals(segment.getString("type"))) continue;
            receiveService.handleImage(event,segment.getJSONObject("data"));
        }
    }
}
