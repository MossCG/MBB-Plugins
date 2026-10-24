package org.moboxlab.mbb.trpg.session;

import org.junit.Before;
import org.junit.Test;
import org.moboxlab.moboxbot.API.Command.BotCommand;
import org.moboxlab.moboxbot.API.Event.Listener;
import org.moboxlab.moboxbot.API.Plugin;
import org.moboxlab.moboxbot.API.PluginManager;
import org.moboxlab.moboxbot.API.PluginService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** {@link PluginManagerScheduler} 适配测试：秒级换算、取消标记与异常隔离。 */
public class PluginManagerSchedulerTest {

    private FakePluginManager pluginManager;
    private Plugin plugin;
    private PluginManagerScheduler scheduler;

    @Before
    public void setUp() {
        pluginManager = new FakePluginManager();
        plugin = new Plugin() {
        };
        scheduler = new PluginManagerScheduler(plugin, pluginManager, null);
    }

    @Test
    public void toSecondsRoundsUpAndHonoursMinimum() {
        assertEquals("整秒不变", 1L, PluginManagerScheduler.toSeconds(1000L, 0L));
        assertEquals("不足一秒向上取整", 1L, PluginManagerScheduler.toSeconds(1L, 0L));
        assertEquals("超过一秒向上取整", 2L, PluginManagerScheduler.toSeconds(1001L, 0L));
        assertEquals("零延迟取最小值", 1L, PluginManagerScheduler.toSeconds(0L, 1L));
        assertEquals("负数延迟取最小值", 5L, PluginManagerScheduler.toSeconds(-10L, 5L));
    }

    @Test
    public void runLaterConvertsMillisecondsToSeconds() {
        ScheduledTask task = scheduler.runLater(new Runnable() {
            @Override
            public void run() {
            }
        }, 1500L);

        assertEquals(1, pluginManager.laterDelays.size());
        assertEquals(2L, pluginManager.laterDelays.get(0).longValue());
        assertFalse(task.isCancelled());
    }

    @Test
    public void runTimerUsesAtLeastOneSecondPeriod() {
        scheduler.runTimer(new Runnable() {
            @Override
            public void run() {
            }
        }, 0L, 2500L);

        assertEquals(1, pluginManager.timers.size());
        String timer = pluginManager.timers.get(0);
        assertEquals("0:3", timer);
    }

    @Test
    public void cancelledTaskIsNotExecuted() {
        final int[] runs = new int[]{0};
        ScheduledTask task = scheduler.runTimer(new Runnable() {
            @Override
            public void run() {
                runs[0]++;
            }
        }, 1000L, 1000L);

        pluginManager.fireTimer(0);
        assertEquals(1, runs[0]);
        task.cancel();
        assertTrue(task.isCancelled());
        pluginManager.fireTimer(0);
        assertEquals("取消后不得再执行业务动作", 1, runs[0]);
    }

    @Test
    public void taskExceptionIsSwallowed() {
        scheduler.runLater(new Runnable() {
            @Override
            public void run() {
                throw new IllegalStateException("模拟任务异常");
            }
        }, 1000L);

        pluginManager.fireLater(0);
        assertEquals("异常应被适配器吞掉", 1, pluginManager.laterRuns);
    }

    /** 只记录调度调用的插件管理器假件。 */
    private static final class FakePluginManager implements PluginManager {

        private final List<Long> laterDelays = new ArrayList<Long>();
        private final List<String> timers = new ArrayList<String>();
        private final List<Runnable> laterTasks = new ArrayList<Runnable>();
        private final List<Runnable> timerTasks = new ArrayList<Runnable>();
        private int laterRuns;

        void fireLater(int index) {
            laterRuns++;
            laterTasks.get(index).run();
        }

        void fireTimer(int index) {
            timerTasks.get(index).run();
        }

        @Override
        public Plugin getPlugin(String name) {
            return null;
        }

        @Override
        public List<Plugin> getPlugins() {
            return Collections.emptyList();
        }

        @Override
        public boolean isEnabled(String name) {
            return false;
        }

        @Override
        public void registerListener(Plugin plugin, Listener listener) {
        }

        @Override
        public void registerCommand(Plugin plugin, BotCommand command) {
        }

        @Override
        public boolean registerService(Plugin plugin, PluginService service) {
            return false;
        }

        @Override
        public PluginService getService(String name) {
            return null;
        }

        @Override
        public void runTask(Plugin plugin, Runnable task) {
        }

        @Override
        public void runTaskLater(Plugin plugin, Runnable task, long delaySeconds) {
            laterDelays.add(delaySeconds);
            laterTasks.add(task);
        }

        @Override
        public void runTaskTimer(Plugin plugin, Runnable task, long delaySeconds, long periodSeconds) {
            timers.add(delaySeconds + ":" + periodSeconds);
            timerTasks.add(task);
        }

        @Override
        public boolean enablePlugin(String name) {
            return false;
        }

        @Override
        public boolean disablePlugin(String name) {
            return false;
        }

        @Override
        public boolean reloadPlugin(String name) {
            return false;
        }
    }
}
