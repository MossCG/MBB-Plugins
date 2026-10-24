package org.moboxlab.mbb.trpg.session;

import org.moboxlab.moboxbot.API.Plugin;
import org.moboxlab.moboxbot.API.PluginLogger;
import org.moboxlab.moboxbot.API.PluginManager;

/**
 * 把插件定时端口 {@link Scheduler} 适配到主程序 {@link PluginManager}。
 *
 * <p>主程序按秒调度，且没有单个任务的取消接口，所以毫秒换算成秒时向上取整，
 * {@link ScheduledTask#cancel()} 只置本地标记：适配器在每次执行前检查标记，
 * 已取消的任务直接返回，插件的业务动作不会再跑。</p>
 */
public final class PluginManagerScheduler implements Scheduler {

    private final Plugin plugin;
    private final PluginManager pluginManager;
    private final PluginLogger logger;

    public PluginManagerScheduler(Plugin plugin, PluginManager pluginManager, PluginLogger logger) {
        if (plugin == null || pluginManager == null) {
            throw new IllegalArgumentException("插件与插件管理器不能为空");
        }
        this.plugin = plugin;
        this.pluginManager = pluginManager;
        this.logger = logger;
    }

    @Override
    public ScheduledTask runLater(Runnable task, long delayMillis) {
        TaskHandle handle = new TaskHandle();
        pluginManager.runTaskLater(plugin, guard(task, handle), toSeconds(delayMillis, 0L));
        return handle;
    }

    @Override
    public ScheduledTask runTimer(Runnable task, long delayMillis, long periodMillis) {
        TaskHandle handle = new TaskHandle();
        pluginManager.runTaskTimer(plugin, guard(task, handle), toSeconds(delayMillis, 0L),
                toSeconds(periodMillis, 1L));
        return handle;
    }

    /** 毫秒向上取整成秒，最少不低于给定下限。 */
    static long toSeconds(long millis, long minimum) {
        if (millis <= 0L) {
            return minimum;
        }
        return Math.max(minimum, (millis + 999L) / 1000L);
    }

    private Runnable guard(final Runnable task, final TaskHandle handle) {
        return new Runnable() {
            @Override
            public void run() {
                if (handle.isCancelled()) {
                    return;
                }
                try {
                    task.run();
                } catch (RuntimeException e) {
                    if (logger != null) {
                        logger.sendWarn("[MBB-Trpg] 定时任务异常：" + e.getMessage());
                    }
                }
            }
        };
    }

    private static final class TaskHandle implements ScheduledTask {

        private volatile boolean cancelled;

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
