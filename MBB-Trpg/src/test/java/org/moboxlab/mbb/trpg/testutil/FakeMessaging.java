package org.moboxlab.mbb.trpg.testutil;

import org.moboxlab.mbb.trpg.session.Messaging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 发送端口假件：记录全部发送内容，可注入连续失败。 */
public final class FakeMessaging implements Messaging {

    /** 一次发送记录。 */
    public static final class SentMessage {
        private final String channel;
        private final long targetId;
        private final String text;
        private final byte[] image;

        SentMessage(String channel, long targetId, String text, byte[] image) {
            this.channel = channel;
            this.targetId = targetId;
            this.text = text;
            this.image = image;
        }

        public String channel() {
            return channel;
        }

        public long targetId() {
            return targetId;
        }

        public String text() {
            return text;
        }

        public byte[] image() {
            return image;
        }
    }

    private final List<SentMessage> sent = new ArrayList<SentMessage>();
    private int failNextGroupText;
    private int failNextPrivateText;
    private int failNextGroupImage;
    private int failNextPrivateImage;
    private int failNextGroupAt;

    @Override
    public boolean sendGroupAt(long groupId, long userId, String text) {
        if (failNextGroupAt > 0) {
            failNextGroupAt--;
            return false;
        }
        sent.add(new SentMessage("group-at:" + userId, groupId, text, null));
        return true;
    }

    /** 注入接下来的群内 @ 发送失败次数。 */
    public void failNextGroupAt(int times) {
        this.failNextGroupAt = times;
    }

    @Override
    public boolean sendGroupText(long groupId, String text) {
        if (failNextGroupText > 0) {
            failNextGroupText--;
            return false;
        }
        sent.add(new SentMessage("group", groupId, text, null));
        return true;
    }

    @Override
    public boolean sendPrivateText(long userId, String text) {
        if (failNextPrivateText > 0) {
            failNextPrivateText--;
            return false;
        }
        sent.add(new SentMessage("private", userId, text, null));
        return true;
    }

    @Override
    public boolean sendGroupImage(long groupId, byte[] image) {
        if (failNextGroupImage > 0) {
            failNextGroupImage--;
            return false;
        }
        sent.add(new SentMessage("image", groupId, null, image));
        return true;
    }

    @Override
    public boolean sendPrivateImage(long userId, byte[] image) {
        if (failNextPrivateImage > 0) {
            failNextPrivateImage--;
            return false;
        }
        sent.add(new SentMessage("private-image", userId, null, image));
        return true;
    }

    /** 注入接下来的群文本发送失败次数。 */
    public void failNextGroupText(int times) {
        this.failNextGroupText = times;
    }

    /** 注入接下来的私聊文本发送失败次数。 */
    public void failNextPrivateText(int times) {
        this.failNextPrivateText = times;
    }

    /** 注入接下来的群图片发送失败次数。 */
    public void failNextGroupImage(int times) {
        this.failNextGroupImage = times;
    }

    /** 注入接下来的私聊图片发送失败次数。 */
    public void failNextPrivateImage(int times) {
        this.failNextPrivateImage = times;
    }

    /** 全部发送记录，按发送顺序。 */
    public List<SentMessage> sent() {
        return Collections.unmodifiableList(sent);
    }

    /** 某频道的发送记录。 */
    public List<SentMessage> sentTo(String channel) {
        List<SentMessage> result = new ArrayList<SentMessage>();
        for (SentMessage message : sent) {
            if (message.channel().equals(channel)) {
                result.add(message);
            }
        }
        return result;
    }

    /** 清空记录，保留失败注入设置。 */
    public void clear() {
        sent.clear();
    }
}
