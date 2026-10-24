package org.moboxlab.mbb.trpg.present;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.OneBot.MessageUtil;
import org.moboxlab.moboxbot.API.OneBot.OneBotClient;
import org.moboxlab.moboxbot.API.Server;
import org.moboxlab.mbb.trpg.session.Messaging;

/**
 * 生产发送端口：只走主程序 {@code OneBotClient}，成功判 {@code retcode == 0}。
 *
 * <p>异常一律吞成 false，由呈现层按失败口径回退，不让发送问题打断状态迁移。</p>
 */
public final class OneBotMessaging implements Messaging {

    private final Server server;

    public OneBotMessaging(Server server) {
        if (server == null) {
            throw new IllegalArgumentException("服务器不能为空");
        }
        this.server = server;
    }

    @Override
    public boolean sendGroupText(long groupId, String text) {
        OneBotClient client = server.getOneBotClient();
        if (client == null) {
            return false;
        }
        try {
            return accepted(client.sendGroupMessage(groupId, text(text)));
        } catch (RuntimeException error) {
            return false;
        }
    }

    @Override
    public boolean sendGroupAt(long groupId, long userId, String text) {
        OneBotClient client = server.getOneBotClient();
        if (client == null) {
            return false;
        }
        try {
            return accepted(client.sendGroupMessage(groupId, MessageUtil.message(
                    MessageUtil.at(userId), MessageUtil.text(" " + text))));
        } catch (RuntimeException error) {
            return false;
        }
    }

    @Override
    public boolean sendPrivateText(long userId, String text) {
        OneBotClient client = server.getOneBotClient();
        if (client == null) {
            return false;
        }
        try {
            return accepted(client.sendPrivateMessage(userId, text(text)));
        } catch (RuntimeException error) {
            return false;
        }
    }

    @Override
    public boolean sendGroupImage(long groupId, byte[] image) {
        OneBotClient client = server.getOneBotClient();
        if (client == null || image == null || image.length == 0) {
            return false;
        }
        try {
            return accepted(client.sendGroupMessage(groupId, image(image)));
        } catch (RuntimeException error) {
            return false;
        }
    }

    @Override
    public boolean sendPrivateImage(long userId, byte[] image) {
        OneBotClient client = server.getOneBotClient();
        if (client == null || image == null || image.length == 0) {
            return false;
        }
        try {
            return accepted(client.sendPrivateMessage(userId, image(image)));
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static JSONArray text(String value) {
        return MessageUtil.message(MessageUtil.text(value));
    }

    private static JSONArray image(byte[] value) {
        return MessageUtil.message(MessageUtil.image(
                org.moboxlab.moboxbot.API.Util.ImageUtil.toBase64Uri(value)));
    }

    private static boolean accepted(JSONObject response) {
        return response != null && response.getIntValue("retcode") == 0;
    }
}
