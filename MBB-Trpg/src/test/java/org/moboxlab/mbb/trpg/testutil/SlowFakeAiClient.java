package org.moboxlab.mbb.trpg.testutil;

import org.moboxlab.mbb.trpg.ai.AiClient;
import org.moboxlab.mbb.trpg.ai.AiRequest;
import org.moboxlab.mbb.trpg.ai.AiResult;

/** 固定睡眠的假 AI 客户端，用于插件侧墙钟测试。 */
public final class SlowFakeAiClient implements AiClient {

    private final long sleepMillis;

    public SlowFakeAiClient(long sleepMillis) {
        this.sleepMillis = sleepMillis;
    }

    @Override
    public AiResult chat(AiRequest request) {
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return AiResult.failure("interrupted", false, 0L);
        }
        return AiResult.success("慢速叙述", "stop", "{}", false, 1L);
    }
}
