package org.moboxlab.mbb.trpg.session;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 同群同类错误去重（规格 §5.6）：10 秒窗口内同类拒绝只回一次。
 *
 * <p>只对「每群同类」计数，不同用户命中同类错误时同样合并，符合规格口径。</p>
 */
public final class ErrorDedupe {

    private final Map<String, Long> lastReply = new ConcurrentHashMap<String, Long>();

    /**
     * 申请一次回复机会。
     *
     * @return true 表示本次应回复；false 表示窗口内已回复过、本次静默
     */
    public boolean claim(long groupId, SessionReject reject, long now, long windowMillis) {
        if (reject == null) {
            return true;
        }
        return claim(groupId, reject.name(), now, windowMillis);
    }

    /**
     * 按任意同类键申请一次回复机会（指令层与局面层共用一个去重登记）。
     *
     * @param key 同类错误标识，例如 {@code "dice-unsupported"} 或拒绝枚举名
     * @return true 表示本次应回复；false 表示窗口内已回复过、本次静默
     */
    public boolean claim(long groupId, String key, long now, long windowMillis) {
        if (key == null || key.isEmpty()) {
            return true;
        }
        String dedupeKey = groupId + ":" + key;
        // 判定与登记必须在同一把锁内：两个并发请求否则可能都判定为「未回复过」。
        synchronized (lastReply) {
            Long last = lastReply.get(dedupeKey);
            if (last != null && windowMillis > 0L && now - last.longValue() < windowMillis) {
                return false;
            }
            lastReply.put(dedupeKey, Long.valueOf(now));
            return true;
        }
    }

    /** 清空登记（停用与测试用）。 */
    public void clear() {
        lastReply.clear();
    }
}
