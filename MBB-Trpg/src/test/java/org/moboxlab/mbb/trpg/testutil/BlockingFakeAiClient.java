package org.moboxlab.mbb.trpg.testutil;

import org.moboxlab.mbb.trpg.ai.AiClient;
import org.moboxlab.mbb.trpg.ai.AiRequest;
import org.moboxlab.mbb.trpg.ai.AiResult;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 调用会阻塞在门闩上的假 AI 客户端。
 *
 * <p>用来制造「请求已占用并发槽位」的确定状态：先等 {@link #awaitEntered()}，
 * 再决定放行或断言并发行为。</p>
 */
public final class BlockingFakeAiClient implements AiClient {

    private final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final AtomicInteger calls = new AtomicInteger();
    private final AiResult result;

    /** 放行后返回一条成功叙述。 */
    public BlockingFakeAiClient() {
        this(AiResult.success("假 AI 叙述", "stop", "{}", false, 1L));
    }

    /** 放行后返回指定结果，例如 busy 失败。 */
    public BlockingFakeAiClient(AiResult result) {
        this.result = result;
    }

    @Override
    public AiResult chat(AiRequest request) {
        calls.incrementAndGet();
        entered.countDown();
        try {
            release.await(5L, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return AiResult.failure("interrupted", false, 0L);
        }
        return result;
    }

    /** 等第一次调用进入客户端，最多 3 秒。 */
    public boolean awaitEntered() throws InterruptedException {
        return entered.await(3L, TimeUnit.SECONDS);
    }

    /** 放行所有已进入与后续调用。 */
    public void releaseAll() {
        release.countDown();
    }

    /** 已发生的调用次数。 */
    public int calls() {
        return calls.get();
    }
}
