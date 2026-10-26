package org.moboxlab.mbb.trpg.store;

/** plugin_mbb_trpg_blacklist 行对象（不可变）。 */
public final class BlacklistRow {

    private final long id;
    private final long groupId;
    private final long userId;
    private final long operatorId;
    private final String reason;
    private final long createTime;

    public BlacklistRow(long id, long groupId, long userId, long operatorId,
                        String reason, long createTime) {
        this.id = id;
        this.groupId = groupId;
        this.userId = userId;
        this.operatorId = operatorId;
        this.reason = reason == null ? "" : reason;
        this.createTime = createTime;
    }

    public long id() {
        return id;
    }

    public long groupId() {
        return groupId;
    }

    public long userId() {
        return userId;
    }

    public long operatorId() {
        return operatorId;
    }

    public String reason() {
        return reason;
    }

    public long createTime() {
        return createTime;
    }
}
