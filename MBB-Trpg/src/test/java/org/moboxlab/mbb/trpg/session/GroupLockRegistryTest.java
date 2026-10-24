package org.moboxlab.mbb.trpg.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;
import org.moboxlab.mbb.trpg.testutil.FakeClock;

/** 群锁注册表：互斥、重入、配对释放与空闲淘汰。 */
public class GroupLockRegistryTest {

    private static final long GROUP = 1001L;

    private FakeClock clock;
    private GroupLockRegistry locks;

    @Before
    public void setUp() {
        clock = new FakeClock();
        locks = new GroupLockRegistry(clock);
    }

    @Test
    public void secondThreadCannotEnterSameGroup() throws Exception {
        assertTrue(locks.tryLock(GROUP));
        final boolean[] acquired = new boolean[1];
        Thread other = new Thread(new Runnable() {

            @Override
            public void run() {
                acquired[0] = locks.tryLock(GROUP);
            }
        });
        other.start();
        other.join();
        assertFalse("同一群同时只允许一个线程进入", acquired[0]);
        assertTrue(locks.isHeldByCurrentThread(GROUP));
        locks.unlock(GROUP);
        assertFalse(locks.isLocked(GROUP));
    }

    @Test
    public void differentGroupsDoNotBlockEachOther() throws Exception {
        assertTrue(locks.tryLock(GROUP));
        final boolean[] acquired = new boolean[1];
        Thread other = new Thread(new Runnable() {

            @Override
            public void run() {
                acquired[0] = locks.tryLock(GROUP + 1L);
                if (acquired[0]) {
                    locks.unlock(GROUP + 1L);
                }
            }
        });
        other.start();
        other.join();
        assertTrue("不同群的锁互相独立", acquired[0]);
        locks.unlock(GROUP);
    }

    @Test
    public void reentrantLockRequiresMatchingUnlocks() {
        assertTrue(locks.tryLock(GROUP));
        assertTrue("同线程可重入", locks.tryLock(GROUP));
        locks.unlock(GROUP);
        assertTrue("还持有一层", locks.isHeldByCurrentThread(GROUP));
        locks.unlock(GROUP);
        assertFalse(locks.isHeldByCurrentThread(GROUP));
    }

    @Test
    public void unlockWithoutHoldIsRejected() {
        try {
            locks.unlock(GROUP);
            fail("未持有时释放必须抛异常");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("群锁"));
        }
    }

    @Test
    public void idleLockIsEvictedOnNextAcquire() {
        assertTrue(locks.tryLock(GROUP));
        locks.unlock(GROUP);
        assertEquals(1, locks.size());
        clock.advance(GroupLockRegistry.DEFAULT_IDLE_MILLIS + 1L);
        assertTrue(locks.tryLock(GROUP + 1L));
        assertFalse("空闲超时的锁被淘汰", locks.isLocked(GROUP));
        assertEquals(1, locks.size());
        locks.unlock(GROUP + 1L);
    }

    @Test
    public void heldLockIsNotEvictedEvenWhenOverCapacity() {
        GroupLockRegistry small = new GroupLockRegistry(1, 0L, clock);
        assertTrue(small.tryLock(GROUP));
        clock.advance(1L);
        assertTrue("持锁中的锁不会被淘汰", small.isHeldByCurrentThread(GROUP));
        small.unlock(GROUP);
        assertTrue(small.tryLock(GROUP + 1L));
        assertFalse(small.isLocked(GROUP));
        small.unlock(GROUP + 1L);
    }
}
