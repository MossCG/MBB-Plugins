package org.moboxlab.mbb.trpg.testutil;

import org.moboxlab.mbb.trpg.ai.AiClient;
import org.moboxlab.mbb.trpg.ai.AiRequest;
import org.moboxlab.mbb.trpg.ai.AiResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** AI 调用端口假件：按队列返回预设结果，队列空时返回默认成功结果。 */
public final class FakeAiClient implements AiClient {

    private final Deque<AiResult> program = new ArrayDeque<AiResult>();
    private final List<AiRequest> requests = new ArrayList<AiRequest>();
    private String defaultContent = "假 AI 叙述";

    /** 排入下一个返回结果。 */
    public FakeAiClient enqueue(AiResult result) {
        if (result != null) {
            program.addLast(result);
        }
        return this;
    }

    /** 设置队列空时的默认成功文本。 */
    public void setDefaultContent(String content) {
        this.defaultContent = content == null ? "" : content;
    }

    @Override
    public AiResult chat(AiRequest request) {
        requests.add(request);
        AiResult next = program.pollFirst();
        if (next != null) {
            return next;
        }
        return AiResult.success(defaultContent, "stop", "{}", false, 1L);
    }

    /** 全部收到的请求，按调用顺序。 */
    public List<AiRequest> requests() {
        return new ArrayList<AiRequest>(requests);
    }

    /** 排队中尚未返回的结果数量。 */
    public int queuedCount() {
        return program.size();
    }

    public void clear() {
        requests.clear();
        program.clear();
    }
}
