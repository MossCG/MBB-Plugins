package org.moboxlab.mbb.trpg.ai;

import org.moboxlab.mbb.trpg.session.ScheduledTask;

import java.util.ArrayList;
import java.util.List;

/** 合并窗口：一个群在 20 秒 / 5 条事件内累积的触发，结算时并成一次调用。 */
final class MergeWindow {

    final long groupId;
    final long sessionNo;
    long sceneVersion;
    final long openedAt;
    final List<Long> seqs = new ArrayList<Long>();
    final List<String> lines = new ArrayList<String>();
    long firstSeq;
    long lastSeq;
    ScheduledTask timer;

    MergeWindow(long groupId, long sessionNo, long sceneVersion, long openedAt) {
        this.groupId = groupId;
        this.sessionNo = sessionNo;
        this.sceneVersion = sceneVersion;
        this.openedAt = openedAt;
    }
}
