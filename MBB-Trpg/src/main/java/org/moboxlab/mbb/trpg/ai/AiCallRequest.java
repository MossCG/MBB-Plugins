package org.moboxlab.mbb.trpg.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一次 AI 主持调用请求（不可变）。
 *
 * <p>本地键 sessionKey 形如 {@code trpg-<群号>-<局号>}，用于审计、排队与去重；
 * 局号由 {@link #sessionNo()} 单独暴露，供审计表与 SessionService 使用。
 * 窗口首末序号用于去重键：局号|场景版本|窗口首序号|窗口末序号。</p>
 */
public final class AiCallRequest {

    private static final String SESSION_KEY_PREFIX = "trpg-";

    private final long groupId;
    private final long sessionNo;
    private final String sessionKey;
    private final AiTrigger trigger;
    private final List<Long> eventSeqs;
    private final long windowFirstSeq;
    private final long windowLastSeq;
    private final long sceneVersion;
    private final boolean immediate;
    private final List<String> recentEventLines;

    /**
     * 构造一次调用请求。
     *
     * @param groupId         群号
     * @param sessionNo       局号；无局时为 0
     * @param trigger         触发类型
     * @param eventSeqs       事件序号列表，按发生顺序；不传 null
     * @param windowFirstSeq  窗口首事件序号；单事件请求与末序号相同
     * @param windowLastSeq   窗口末事件序号
     * @param sceneVersion    事件所属场景版本
     * @param immediate       是否立即触发；KP / 管理员代掷必须为 true
     * @param recentEventLines 最近事件原文行，与 eventSeqs 对齐；可为 null
     */
    public AiCallRequest(long groupId, long sessionNo, AiTrigger trigger, List<Long> eventSeqs,
                         long windowFirstSeq, long windowLastSeq, long sceneVersion,
                         boolean immediate, List<String> recentEventLines) {
        if (groupId <= 0L) {
            throw new IllegalArgumentException("群号必须为正数");
        }
        if (trigger == null) {
            throw new IllegalArgumentException("触发类型不能为空");
        }
        this.groupId = groupId;
        this.sessionNo = sessionNo;
        this.sessionKey = SESSION_KEY_PREFIX + groupId + "-" + sessionNo;
        this.trigger = trigger;
        this.eventSeqs = Collections.unmodifiableList(eventSeqs == null
                ? new ArrayList<Long>() : new ArrayList<Long>(eventSeqs));
        this.windowFirstSeq = windowFirstSeq;
        this.windowLastSeq = windowLastSeq;
        this.sceneVersion = sceneVersion;
        this.immediate = immediate;
        this.recentEventLines = Collections.unmodifiableList(recentEventLines == null
                ? new ArrayList<String>() : new ArrayList<String>(recentEventLines));
    }

    public long groupId() {
        return groupId;
    }

    /** 局号（不是 sessionKey）；无局时为 0。 */
    public long sessionNo() {
        return sessionNo;
    }

    /** 本地键，形如 trpg-&lt;群号&gt;-&lt;局号&gt;。 */
    public String sessionKey() {
        return sessionKey;
    }

    public AiTrigger trigger() {
        return trigger;
    }

    public List<Long> eventSeqs() {
        return eventSeqs;
    }

    public long windowFirstSeq() {
        return windowFirstSeq;
    }

    public long windowLastSeq() {
        return windowLastSeq;
    }

    public long sceneVersion() {
        return sceneVersion;
    }

    public boolean immediate() {
        return immediate;
    }

    public List<String> recentEventLines() {
        return recentEventLines;
    }

    /** 审计事件序号：优先取窗口末序号。 */
    public long latestEventSeq() {
        if (windowLastSeq > 0L) {
            return windowLastSeq;
        }
        if (eventSeqs.isEmpty()) {
            return 0L;
        }
        return eventSeqs.get(eventSeqs.size() - 1).longValue();
    }

    /** 去重键：局号|场景版本|窗口首序号|窗口末序号。 */
    public String dedupeKey() {
        return sessionKey + "|" + sceneVersion + "|" + windowFirstSeq + "|" + windowLastSeq;
    }

    @Override
    public String toString() {
        return "AiCallRequest{group=" + groupId + ", session=" + sessionKey + ", trigger=" + trigger
                + ", seqs=" + eventSeqs + ", window=" + windowFirstSeq + "-" + windowLastSeq
                + ", sceneVersion=" + sceneVersion + ", immediate=" + immediate + '}';
    }
}
