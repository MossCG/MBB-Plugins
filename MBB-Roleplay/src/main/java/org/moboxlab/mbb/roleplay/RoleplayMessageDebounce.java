package org.moboxlab.mbb.roleplay;

/**
 * 自适应消息防抖延迟计算。
 *
 * 只负责算下一段等待时间，是否取消旧任务、如何保证同群串行仍由批次调度器处理。
 */
public class RoleplayMessageDebounce {
    public static long delayMillis(String mode,int batchSize,boolean groupActive,
                                   boolean sameUser,boolean direct,boolean initialMediaOnly,
                                   int windowSecond) {
        String normalized = mode == null ? "" : mode.trim().toLowerCase();
        if ("off".equals(normalized) || "none".equals(normalized)) return 0L;
        long base;
        if ("fast".equals(normalized)) {
            base = 450L;
        } else if ("steady".equals(normalized)) {
            base = 2000L;
        } else {
            base = 1100L;
        }
        if (!groupActive) base += 350L;
        if (batchSize > 1) {
            base += sameUser ? 220L : 140L;
        }
        if (initialMediaOnly) base += 250L;
        if (direct) base = Math.min(base,800L);
        long windowMs = Math.max(0,windowSecond) * 1000L;
        if (windowMs <= 0) return 0L;
        long result = Math.min(base,windowMs);
        return Math.max(250L,result);
    }
}
