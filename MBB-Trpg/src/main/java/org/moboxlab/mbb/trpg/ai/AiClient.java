package org.moboxlab.mbb.trpg.ai;

/**
 * AI 调用端口，适配 MBB-AI 的 chat 能力。
 *
 * <p>同步阻塞；由插件任务线程调用并自行设置墙钟。失败通过 {@link AiResult}
 * 的 errorType / retryable 表达，不抛异常给上层。</p>
 */
public interface AiClient {

    AiResult chat(AiRequest request);
}
