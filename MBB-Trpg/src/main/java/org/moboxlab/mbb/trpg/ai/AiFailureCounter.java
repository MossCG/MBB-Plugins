package org.moboxlab.mbb.trpg.ai;

/**
 * 连续失败计数的转发端口：把记账动作交给注入的 {@link AiFailureRecorder}，异常只打日志。
 *
 * <p>未绑定端口时引擎既不记失败也不清零；挂起判定仍由计数实现方负责。</p>
 */
final class AiFailureCounter {

    private final AiEngineLog log;
    private volatile AiFailureRecorder recorder;

    AiFailureCounter(AiEngineLog log) {
        this.log = log;
    }

    void bind(AiFailureRecorder recorder) {
        this.recorder = recorder;
    }

    /** 记录一次计入失败的失败；返回是否已触发挂起。 */
    boolean recordFailure(long groupId) {
        AiFailureRecorder current = recorder;
        if (current == null) {
            return false;
        }
        try {
            AiFailureOutcome outcome = current.recordFailure(groupId);
            return outcome != null && outcome.available() && outcome.suspendedNow();
        } catch (RuntimeException error) {
            log.warn("AI 失败计数写入异常，群 " + groupId + "：" + error);
            return false;
        }
    }

    /** 清零连续失败计数；失败只打日志。 */
    void recordSuccess(long groupId) {
        AiFailureRecorder current = recorder;
        if (current == null) {
            return;
        }
        try {
            current.recordSuccess(groupId);
        } catch (RuntimeException error) {
            log.warn("AI 失败计数清零异常，群 " + groupId + "：" + error);
        }
    }
}
