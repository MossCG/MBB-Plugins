package org.moboxlab.mbb.trpg.session;

/**
 * 发送端口：包装主程序的群发、私聊与图片发送。
 *
 * <p>只在群锁外调用；返回值表示发送是否成功，失败由调用方决定是否重试或回退。</p>
 */
public interface Messaging {

    boolean sendGroupText(long groupId, String text);

    /** 群内发送并 @ 指定成员（用于异步任务把结果只回给发起人）。 */
    boolean sendGroupAt(long groupId, long userId, String text);

    boolean sendPrivateText(long userId, String text);

    boolean sendGroupImage(long groupId, byte[] image);

    boolean sendPrivateImage(long userId, byte[] image);
}
