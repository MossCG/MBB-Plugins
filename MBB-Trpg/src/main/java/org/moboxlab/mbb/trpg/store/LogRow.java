package org.moboxlab.mbb.trpg.store;

/** plugin_mbb_trpg_text_log 行对象（不可变）。 */
public final class LogRow {

    private final long id;
    private final long sessionId;
    private final long seq;
    private final String eventType;
    private final long actorId;
    private final String content;
    private final long createTime;

    public LogRow(long id, long sessionId, long seq, String eventType, long actorId,
                  String content, long createTime) {
        this.id = id;
        this.sessionId = sessionId;
        this.seq = seq;
        this.eventType = eventType == null ? "" : eventType;
        this.actorId = actorId;
        this.content = content == null ? "" : content;
        this.createTime = createTime;
    }

    public long id() {
        return id;
    }

    public long sessionId() {
        return sessionId;
    }

    public long seq() {
        return seq;
    }

    public String eventType() {
        return eventType;
    }

    public long actorId() {
        return actorId;
    }

    public String content() {
        return content;
    }

    public long createTime() {
        return createTime;
    }
}
