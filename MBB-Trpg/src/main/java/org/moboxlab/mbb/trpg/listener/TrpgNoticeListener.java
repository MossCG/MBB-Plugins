package org.moboxlab.mbb.trpg.listener;

import org.moboxlab.moboxbot.API.Event.EventHandler;
import org.moboxlab.moboxbot.API.Event.EventPriority;
import org.moboxlab.moboxbot.API.Event.Listener;
import org.moboxlab.moboxbot.API.Event.NoticeEvent;
import org.moboxlab.moboxbot.API.PluginLogger;
import org.moboxlab.mbb.trpg.session.SessionResult;
import org.moboxlab.mbb.trpg.session.SessionService;

/**
 * 群成员减少通知：KP 离群触发 T7（kp_left）。
 *
 * <p>只认 {@code group_decrease} 里的离群用户号，不猜其他成员身份；是否真的结束本局由
 * {@link SessionService#kpLeft} 判定，本监听器不写库。</p>
 */
public final class TrpgNoticeListener implements Listener {

    private static final String NOTICE_GROUP_DECREASE = "group_decrease";
    private static final String END_REASON_KP_LEFT = "kp_left";

    private final SessionService sessions;
    private final PluginLogger logger;

    public TrpgNoticeListener(SessionService sessions, PluginLogger logger) {
        if (sessions == null) {
            throw new IllegalArgumentException("局面服务不能为空");
        }
        this.sessions = sessions;
        this.logger = logger;
    }

    /** 离群通知入口。 */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onNotice(NoticeEvent event) {
        if (event == null || !NOTICE_GROUP_DECREASE.equals(event.getNoticeType())) {
            return;
        }
        long groupId = event.getGroupID();
        long userId = event.getUserID();
        if (groupId <= 0L || userId <= 0L) {
            return;
        }
        SessionResult result = sessions.kpLeft(groupId, userId);
        if (result == null) {
            return;
        }
        if (result.ok()) {
            // 只有真正归档时 endReason 才有值：离群的人不是 KP、或本群暂无局都只算空操作。
            if (END_REASON_KP_LEFT.equals(result.endReason()) && logger != null) {
                logger.sendInfo("[MBB-Trpg] KP 离群，本局已按 kp_left 归档：群=" + groupId
                        + "，用户=" + userId);
            }
        } else if (result.rejected() && !result.replySuppressed() && logger != null) {
            logger.sendWarn("[MBB-Trpg] KP 离群处理被拒绝：群=" + groupId + "，用户=" + userId
                    + "，原因=" + result.detail());
        }
    }
}
