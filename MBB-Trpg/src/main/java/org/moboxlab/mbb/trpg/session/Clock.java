package org.moboxlab.mbb.trpg.session;

/** 时间端口：惰性过期、冷却、二次确认窗口统一取这里的时间。 */
public interface Clock {

    long nowMillis();
}
