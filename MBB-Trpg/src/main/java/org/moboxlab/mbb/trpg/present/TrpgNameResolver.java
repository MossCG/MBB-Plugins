package org.moboxlab.mbb.trpg.present;

/** 群昵称解析端口：呈现层只在这里取人名，取不到由实现给兜底名。 */
public interface TrpgNameResolver {

    /** 取成员在群内的显示名；取不到返回兜底文案，不返回 null。 */
    String name(long groupId, long userId);

    /** 兜底名：{@code 成员 <QQ 号>}。 */
    static String fallback(long userId) {
        return "成员 " + userId;
    }
}
