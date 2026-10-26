package org.moboxlab.mbb.trpg.store;

import java.util.Collections;
import java.util.List;

/**
 * 群级拉黑名单服务：唯一实现直连 {@link SessionStore}。
 *
 * <p>拉黑是群内约束，不占群锁、不改变局面状态；重复拉黑靠唯一约束收敛为一行，
 * 名单永久保留，不随局清理。指令入口见 command 包的 AdminCommandHandler，
 * 消息入口的静默拦截见 listener 包的 TrpgListener。</p>
 */
public final class BlacklistService {

    private final SessionStore store;

    public BlacklistService(SessionStore store) {
        if (store == null) {
            throw new IllegalArgumentException("存储不能为空");
        }
        this.store = store;
    }

    /**
     * 拉黑一名成员。
     *
     * @return true 表示该成员最终在名单里（含本就已在名单的重复拉黑）；
     *         false 表示写入失败，调用方按失败提示
     */
    public boolean add(long groupId, long userId, long operatorId, String reason, long now) {
        BlacklistRow row = new BlacklistRow(0L, groupId, userId, operatorId,
                reason == null ? "" : reason, now);
        int inserted = store.insertBlacklist(row);
        if (inserted > 0) {
            return true;
        }
        // 插入返回 0 无法区分"没写进去"和"唯一约束冲突"，回读一次确认
        return store.isBlacklisted(groupId, userId);
    }

    /**
     * 解除拉黑。
     *
     * @return true 只在确实删掉一行时为真；false 表示没有删到行——成员本就不在名单（幂等），
     *         或主程序语句未执行（-1）。两者对调用方都是「该成员已不在名单」的同一结果，
     *         需要区分时再读一次 {@link #contains}
     */
    public boolean remove(long groupId, long userId) {
        return store.deleteBlacklist(groupId, userId) > 0;
    }

    /** 群内名单，按写入顺序。 */
    public List<BlacklistRow> list(long groupId) {
        List<BlacklistRow> rows = store.listBlacklist(groupId);
        return rows == null ? Collections.<BlacklistRow>emptyList() : rows;
    }

    /** 是否在名单内。 */
    public boolean contains(long groupId, long userId) {
        return store.isBlacklisted(groupId, userId);
    }
}
