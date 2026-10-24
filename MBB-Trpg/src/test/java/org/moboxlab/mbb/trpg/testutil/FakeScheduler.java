package org.moboxlab.mbb.trpg.testutil;

import org.moboxlab.mbb.trpg.session.ScheduledTask;
import org.moboxlab.mbb.trpg.session.Scheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/** 定时任务假件：任务只存在内存里，由测试调用 advance 手动触发。 */
public final class FakeScheduler implements Scheduler {

    private final List<Task> tasks = new ArrayList<Task>();
    private long nowMillis;

    public FakeScheduler() {
        this(0L);
    }

    public FakeScheduler(long startMillis) {
        this.nowMillis = startMillis;
    }

    @Override
    public ScheduledTask runLater(Runnable task, long delayMillis) {
        return add(task, delayMillis, 0L);
    }

    @Override
    public ScheduledTask runTimer(Runnable task, long delayMillis, long periodMillis) {
        if (periodMillis <= 0) {
            throw new IllegalArgumentException("固定周期必须大于 0：" + periodMillis);
        }
        return add(task, delayMillis, periodMillis);
    }

    /** 推进时间并执行到期任务；返回执行次数。 */
    public int advance(long millis) {
        if (millis < 0) {
            throw new IllegalArgumentException("时间只能向前推进：" + millis);
        }
        nowMillis += millis;
        int executed = 0;
        for (Task task : new ArrayList<Task>(tasks)) {
            if (task.cancelled || task.dueAt > nowMillis) {
                continue;
            }
            executed++;
            if (task.periodMillis > 0) {
                task.dueAt = nowMillis + task.periodMillis;
            } else {
                tasks.remove(task);
            }
            task.runnable.run();
        }
        return executed;
    }

    /** 未取消的任务数量。 */
    public int pendingCount() {
        int count = 0;
        for (Task task : tasks) {
            if (!task.cancelled) {
                count++;
            }
        }
        return count;
    }

    /** 当前任务列表（只读快照）。 */
    public List<ScheduledTask> tasks() {
        List<ScheduledTask> result = new ArrayList<ScheduledTask>();
        result.addAll(tasks);
        return Collections.unmodifiableList(result);
    }

    /** 全部取消，用于模拟插件停用。 */
    public void cancelAll() {
        Iterator<Task> iterator = tasks.iterator();
        while (iterator.hasNext()) {
            Task task = iterator.next();
            task.cancelled = true;
            iterator.remove();
        }
    }

    private Task add(Runnable runnable, long delayMillis, long periodMillis) {
        if (runnable == null) {
            throw new IllegalArgumentException("定时任务不能为空");
        }
        Task task = new Task(runnable, nowMillis + Math.max(0L, delayMillis), periodMillis);
        tasks.add(task);
        return task;
    }

    private static final class Task implements ScheduledTask {

        private final Runnable runnable;
        private final long periodMillis;
        private long dueAt;
        private boolean cancelled;

        Task(Runnable runnable, long dueAt, long periodMillis) {
            this.runnable = runnable;
            this.dueAt = dueAt;
            this.periodMillis = periodMillis;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
