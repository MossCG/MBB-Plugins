package org.moboxlab.mbb.trpg.session;

/**
 * 定时任务端口。
 *
 * <p>定时器不持久化：插件重启后未执行的任务直接丢弃，不补发。</p>
 */
public interface Scheduler {

    /** 延迟执行一次。 */
    ScheduledTask runLater(Runnable task, long delayMillis);

    /** 固定周期执行。 */
    ScheduledTask runTimer(Runnable task, long delayMillis, long periodMillis);
}
