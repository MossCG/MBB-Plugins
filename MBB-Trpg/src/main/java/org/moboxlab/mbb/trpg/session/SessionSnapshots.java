package org.moboxlab.mbb.trpg.session;

import java.util.ArrayList;
import java.util.List;

import org.moboxlab.mbb.trpg.store.MemberRow;
import org.moboxlab.mbb.trpg.store.SessionRow;

/** 存储行到只读快照的映射（无状态工具）。 */
public final class SessionSnapshots {

    private SessionSnapshots() {
    }

    /** 由存储行、成员行、已解锁线索与恢复标记构造快照。 */
    public static SessionSnapshot of(SessionRow row, List<MemberRow> members, List<String> unlockedClues,
                                     boolean recovered, long sceneVersion) {
        if (row == null) {
            throw new IllegalArgumentException("局面行不能为空");
        }
        List<MemberView> views = new ArrayList<MemberView>();
        if (members != null) {
            for (MemberRow member : members) {
                if (member != null) {
                    views.add(MemberView.of(member));
                }
            }
        }
        return SessionSnapshot.builder()
                .groupId(row.groupId())
                .sessionId(row.id())
                .state(row.state())
                .kpUserId(row.kpUserId())
                .scriptId(row.scriptId())
                .ruleSetId(row.ruleSetId())
                .sceneIndex(row.sceneIndex())
                .sceneTitle(row.sceneTitle())
                .startedAt(row.startedAt())
                .endedAt(row.endedAt())
                .endReason(row.endReason())
                .summary(row.summary())
                .lastActivityAt(row.lastActivityAt())
                .aiEnabled(row.aiEnabled())
                .aiSuspendedUntil(row.aiSuspendedUntil())
                .aiFailStreak(row.aiFailStreak())
                .growthSettled(row.growthSettled())
                .recovered(recovered)
                .sceneVersion(sceneVersion)
                .members(views)
                .unlockedClues(unlockedClues)
                .build();
    }

    /** 无活跃局的空快照。 */
    public static SessionSnapshot none(long groupId) {
        return SessionSnapshot.none(groupId);
    }
}
