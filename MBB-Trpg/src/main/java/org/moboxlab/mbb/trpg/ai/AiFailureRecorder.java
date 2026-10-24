package org.moboxlab.mbb.trpg.ai;

/**
 * AI 调用失败/成功的计数端口。
 *
 * <p>连续失败阈值、挂起时间戳与失败计数清零都由 SessionService 负责写入；
 * 引擎只负责在计入失败的降级路径上调用 {@link #recordFailure(long)}，
 * 并在一次成功的叙述交付后调用 {@link #recordSuccess(long)}，然后消费返回值决定是否挂起。</p>
 */
public interface AiFailureRecorder {

    /**
     * 记录一次计入失败的 AI 调用失败。
     *
     * @param groupId 群号
     * @return 记录结果；available=false 表示当前没有可写的活跃局或 CAS 未生效，调用方按不计入失败处理
     */
    AiFailureOutcome recordFailure(long groupId);

    /**
     * 记录一次成功调用，清零本局连续失败计数。
     *
     * @param groupId 群号
     * @return 是否真的清零（false 表示没有活跃局）
     */
    boolean recordSuccess(long groupId);
}
