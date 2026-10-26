package org.moboxlab.mbb.trpg.ai;

import org.moboxlab.mbb.trpg.session.SessionSnapshot;

/**
 * 局面只读视图端口。
 *
 * <p>由 SessionService 实现在群锁内取快照；调用方拿到快照后在锁外使用。</p>
 */
public interface SessionView {

    /** 取本群当前局面快照；无局时 state 为 NONE。 */
    SessionSnapshot readSnapshot(long groupId);
}
