package org.moboxlab.mbb.trpg.ai;

/** 排队中的一次调用：请求与入队时刻，超龄判定用后者。 */
final class PendingCall {

    final AiCallRequest request;
    final long enqueuedAt;

    PendingCall(AiCallRequest request, long enqueuedAt) {
        this.request = request;
        this.enqueuedAt = enqueuedAt;
    }
}
