package org.moboxlab.mbb.trpg.present;

/**
 * 日志回看的一行（/trpg log）：时间、发言人、事件内容。
 *
 * <p>只携带排版权需要的信息，日志清洗与截断由模板完成。</p>
 */
public final class TrpgLogEntry {

    private final long createTime;
    private final String actorName;
    private final String content;

    public TrpgLogEntry(long createTime, String actorName, String content) {
        this.createTime = createTime;
        this.actorName = actorName == null ? "" : actorName.trim();
        this.content = content == null ? "" : content;
    }

    public long createTime() {
        return createTime;
    }

    public String actorName() {
        return actorName;
    }

    public String content() {
        return content;
    }
}
