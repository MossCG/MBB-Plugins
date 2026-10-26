package org.moboxlab.mbb.trpg.ai;

import org.moboxlab.mbb.trpg.store.AiTraceRow;

import java.util.List;

/**
 * AI 审计端口。
 *
 * <p>写失败只记日志，不阻断叙述流程；查询只读最近若干行。</p>
 */
public interface TraceStore {

    /** 写入审计行；失败返回 false，调用方只记日志。 */
    boolean record(AiTraceRow row);

    /** 读最近审计行（按 id 倒序）。 */
    List<AiTraceRow> recent(long groupId, long sessionId, int limit);

    /** 删除创建时间早于给定时点的审计行。 */
    void deleteBefore(long createTime);

    /** 只保留最新 keepRows 行。 */
    void trimTo(int keepRows);
}
