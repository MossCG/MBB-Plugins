package org.moboxlab.mbb.trpg.ai;

import java.util.ArrayDeque;
import java.util.Deque;

/** 每群串行队列：同一群的请求按入队顺序排队，同一时刻只有一个工作线程。 */
final class GroupQueue {

    final long groupId;
    final Deque<PendingCall> waiting = new ArrayDeque<PendingCall>();
    boolean workerRunning;

    GroupQueue(long groupId) {
        this.groupId = groupId;
    }
}
