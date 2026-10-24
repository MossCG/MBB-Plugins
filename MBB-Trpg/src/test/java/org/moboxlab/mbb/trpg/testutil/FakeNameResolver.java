package org.moboxlab.mbb.trpg.testutil;

import org.moboxlab.mbb.trpg.present.TrpgNameResolver;

import java.util.HashMap;
import java.util.Map;

/** 昵称解析假件：按用户号查表，未登记的按「成员 <用户号>」回退。 */
public final class FakeNameResolver implements TrpgNameResolver {

    private final Map<Long, String> names = new HashMap<Long, String>();

    /** 登记一个昵称。 */
    public FakeNameResolver put(long userId, String name) {
        names.put(userId, name);
        return this;
    }

    @Override
    public String name(long groupId, long userId) {
        String name = names.get(userId);
        return name == null || name.trim().isEmpty() ? TrpgNameResolver.fallback(userId) : name;
    }
}
