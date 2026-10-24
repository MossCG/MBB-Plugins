package org.moboxlab.mbb.trpg.session;

/** 已提交的定时任务句柄。 */
public interface ScheduledTask {

    void cancel();

    boolean isCancelled();
}
