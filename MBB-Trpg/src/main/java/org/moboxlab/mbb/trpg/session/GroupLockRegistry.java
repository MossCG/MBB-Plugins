package org.moboxlab.mbb.trpg.session;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 每群一把可重入锁的注册表（规格 §5.5）。
 *
 * <p>临界区只包「读状态 → 校验 → 迁移 → 落库」，不含网络发送；入口用 {@link #tryLock(long)}，
 * 拿不到即视为「本群正在处理上一条」，不排队阻塞。锁按群号懒创建，空闲超时且无人持有时淘汰；
 * 容量上限只用于触发更积极的淘汰：当所有锁都有人持有时允许暂时超出，安全性优先于容量精度。</p>
 *
 * <p>并发约定：注册表本身可能被不同群的线程同时访问，因此登记、淘汰与持锁计数都在同一个
 * 监视器内完成；锁对象一旦交给某个线程，就不会在持有期间被淘汰。</p>
 */
public final class GroupLockRegistry {

    /** 默认群锁容量上限。 */
    public static final int DEFAULT_MAX_GROUPS = 256;
    /** 默认空闲淘汰时间（30 分钟）。 */
    public static final long DEFAULT_IDLE_MILLIS = 30L * 60L * 1000L;

    private final int maxGroups;
    private final long idleMillis;
    private final Clock clock;
    private final Map<Long, Entry> entries = new ConcurrentHashMap<Long, Entry>();
    /** 保护登记、淘汰与持锁计数的监视器；锁对象本身仍是独立的 ReentrantLock。 */
    private final Object monitor = new Object();

    public GroupLockRegistry(Clock clock) {
        this(DEFAULT_MAX_GROUPS, DEFAULT_IDLE_MILLIS, clock);
    }

    public GroupLockRegistry(int maxGroups, long idleMillis, Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("时间端口不能为空");
        }
        if (maxGroups <= 0) {
            throw new IllegalArgumentException("群锁容量必须大于 0：" + maxGroups);
        }
        if (idleMillis < 0) {
            throw new IllegalArgumentException("空闲淘汰时间不能为负：" + idleMillis);
        }
        this.maxGroups = maxGroups;
        this.idleMillis = idleMillis;
        this.clock = clock;
    }

    /**
     * 尝试进入本群临界区。
     *
     * @return 拿到锁返回 true；本群已有线程持锁或正被处理时返回 false
     */
    public boolean tryLock(long groupId) {
        synchronized (monitor) {
            long now = clock.nowMillis();
            evict(now);
            Long key = Long.valueOf(groupId);
            Entry entry = entries.get(key);
            if (entry == null) {
                entry = new Entry();
                entries.put(key, entry);
            }
            if (!entry.lock.tryLock()) {
                return false;
            }
            entry.holders++;
            entry.lastUsed = now;
            return true;
        }
    }

    /** 释放本群临界区；未持有时抛 {@link IllegalStateException}（不得静默吞掉）。 */
    public void unlock(long groupId) {
        synchronized (monitor) {
            Entry entry = entries.get(Long.valueOf(groupId));
            if (entry == null || !entry.lock.isHeldByCurrentThread()) {
                throw new IllegalStateException("群锁未由当前线程持有：" + groupId);
            }
            entry.lock.unlock();
            entry.holders--;
            entry.lastUsed = clock.nowMillis();
        }
    }

    /** 本群是否正被某个线程持锁（测试与诊断用）。 */
    public boolean isLocked(long groupId) {
        Entry entry = entries.get(Long.valueOf(groupId));
        return entry != null && entry.lock.isLocked();
    }

    /** 本群锁是否由当前线程持有（测试用）。 */
    public boolean isHeldByCurrentThread(long groupId) {
        Entry entry = entries.get(Long.valueOf(groupId));
        return entry != null && entry.lock.isHeldByCurrentThread();
    }

    /** 当前缓存了多少把群锁。 */
    public int size() {
        return entries.size();
    }

    /**
     * 清空缓存（停用与测试用）。
     *
     * <p>不强制解除仍持有的锁：调用方必须保证此刻没有线程持有或即将获取群锁，
     * 否则同一群可能被分配到两把不同的锁，互斥随之失效。</p>
     */
    public void clear() {
        synchronized (monitor) {
            entries.clear();
        }
    }

    /** 淘汰无人持有且空闲超时（或容量已满）的锁；必须在 monitor 内调用。 */
    private void evict(long now) {
        boolean overCapacity = entries.size() >= maxGroups;
        for (Map.Entry<Long, Entry> current : entries.entrySet()) {
            Entry entry = current.getValue();
            if (entry.holders > 0 || entry.lock.isLocked()) {
                continue;
            }
            if (overCapacity || now - entry.lastUsed >= idleMillis) {
                entries.remove(current.getKey(), entry);
            }
        }
    }

    private static final class Entry {

        private final ReentrantLock lock = new ReentrantLock();
        private long lastUsed;
        private int holders;
    }
}
