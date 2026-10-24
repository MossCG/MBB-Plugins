package org.moboxlab.mbb.trpg.testutil;

import org.moboxlab.mbb.trpg.ai.SessionView;
import org.moboxlab.mbb.trpg.session.SessionSnapshot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 可修改快照的局视图假件：支持单群覆盖，也支持多群并存。 */
public final class FakeSessionView implements SessionView {

    private final Map<Long, SessionSnapshot> snapshots =
            new ConcurrentHashMap<Long, SessionSnapshot>();

    public FakeSessionView() {
    }

    public FakeSessionView(SessionSnapshot snapshot) {
        set(snapshot);
    }

    /** 覆盖一个群的快照；同一群重复设置时后写覆盖前写。 */
    public void set(SessionSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        snapshots.put(Long.valueOf(snapshot.groupId()), snapshot);
    }

    /** 清掉所有群的快照。 */
    public void clear() {
        snapshots.clear();
    }

    @Override
    public SessionSnapshot readSnapshot(long groupId) {
        SessionSnapshot snapshot = snapshots.get(Long.valueOf(groupId));
        if (snapshot == null) {
            return SessionSnapshot.none(groupId);
        }
        return snapshot;
    }
}
