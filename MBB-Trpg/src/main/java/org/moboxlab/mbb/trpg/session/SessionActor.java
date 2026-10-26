package org.moboxlab.mbb.trpg.session;

/**
 * 请求者身份（不可变）。
 *
 * <p>群内请求带群号，私聊请求群号为 0；权限位来自框架层，服务层只做状态相关判定，
 * 不自行解析管理员名单。</p>
 */
public final class SessionActor {

    /** 私聊请求的群号占位值。 */
    public static final long PRIVATE_GROUP_ID = 0L;

    private final long userId;
    private final long groupId;
    private final boolean groupAdmin;
    private final boolean botAdmin;

    public SessionActor(long userId, long groupId, boolean groupAdmin) {
        this(userId, groupId, groupAdmin, false);
    }

    public SessionActor(long userId, long groupId, boolean groupAdmin, boolean botAdmin) {
        if (userId <= 0L) {
            throw new IllegalArgumentException("用户号必须大于 0：" + userId);
        }
        if (groupId < 0L) {
            throw new IllegalArgumentException("群号不能为负：" + groupId);
        }
        this.userId = userId;
        this.groupId = groupId;
        this.groupAdmin = groupAdmin;
        this.botAdmin = botAdmin;
    }

    public long userId() {
        return userId;
    }

    public long groupId() {
        return groupId;
    }

    public boolean groupAdmin() {
        return groupAdmin;
    }

    public boolean botAdmin() {
        return botAdmin;
    }

    /** 是否来自群聊；私聊返回 false。 */
    public boolean inGroup() {
        return groupId > PRIVATE_GROUP_ID;
    }

    @Override
    public String toString() {
        return "SessionActor{userId=" + userId + ", groupId=" + groupId
                + ", groupAdmin=" + groupAdmin + ", botAdmin=" + botAdmin + "}";
    }
}
