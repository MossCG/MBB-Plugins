package org.moboxlab.mbb.trpg.session;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 插件内冷却登记（规格 §5.6）。
 *
 * <p>按（动作, 用户, 群）计数；框架层 {@code Command.cooldownSeconds()} 恒为 0，冷却全部在这里判。
 * 只保留最近一次触发时间，条目数上限与群内用户规模同量级，不做主动淘汰。</p>
 */
public final class CooldownRegistry {

    private final Map<String, Long> lastUse = new ConcurrentHashMap<String, Long>();

    /** 剩余冷却毫秒数；0 表示可以执行。 */
    public long remainingMillis(String action, long userId, long groupId, long now,
                                long cooldownMillis) {
        if (action == null || action.isEmpty()) {
            throw new IllegalArgumentException("动作不能为空");
        }
        if (cooldownMillis <= 0L) {
            return 0L;
        }
        Long last = lastUse.get(key(action, userId, groupId));
        if (last == null) {
            return 0L;
        }
        long elapsed = now - last.longValue();
        if (elapsed < 0L) {
            // 时钟回拨时按冷却处理，宁可多等一轮也不放开限流。
            return cooldownMillis;
        }
        return elapsed >= cooldownMillis ? 0L : cooldownMillis - elapsed;
    }

    /** 记录一次执行。 */
    public void mark(String action, long userId, long groupId, long now) {
        if (action == null || action.isEmpty()) {
            throw new IllegalArgumentException("动作不能为空");
        }
        lastUse.put(key(action, userId, groupId), Long.valueOf(now));
    }

    /** 清空登记（停用与测试用）。 */
    public void clear() {
        lastUse.clear();
    }

    private static String key(String action, long userId, long groupId) {
        return action + ":" + userId + ":" + groupId;
    }
}
