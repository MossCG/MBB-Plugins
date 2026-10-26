package org.moboxlab.mbb.trpg.testutil;

import org.moboxlab.mbb.trpg.session.Clock;

/** 时间端口假件：时间由测试直接推进。 */
public final class FakeClock implements Clock {

    private long nowMillis;

    public FakeClock() {
        this(1700000000000L);
    }

    public FakeClock(long startMillis) {
        this.nowMillis = startMillis;
    }

    @Override
    public long nowMillis() {
        return nowMillis;
    }

    /** 直接设置当前时间。 */
    public void setNow(long nowMillis) {
        this.nowMillis = nowMillis;
    }

    /** 向前推进若干毫秒。 */
    public void advance(long millis) {
        if (millis < 0) {
            throw new IllegalArgumentException("时间只能向前推进：" + millis);
        }
        this.nowMillis += millis;
    }
}
