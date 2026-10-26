package org.moboxlab.mbb.trpg.session;

/** 局面事件发布端口：服务层在群锁外调用，实现方负责异步发送（S6 接线）。 */
public interface SessionEventPublisher {

    /** 无消费者的空实现（测试与尚未接线的场景）。 */
    SessionEventPublisher NONE = new SessionEventPublisher() {

        @Override
        public void publish(SessionEvent event) {
            // 空实现：不消费任何事件。
        }
    };

    /** 发布一条事件；实现方不得抛异常打断服务层流程。 */
    void publish(SessionEvent event);
}
