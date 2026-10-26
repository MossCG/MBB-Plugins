package org.moboxlab.mbb.trpg.session;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

/** 冷却登记必须有界，避免长期运行按用户数无限增长。 */
public class CooldownRegistryTest {

    @Test
    public void registryIsBounded() {
        CooldownRegistry registry = new CooldownRegistry();
        for (int index = 0; index < 5000; index++) {
            registry.mark("test",index,1L,index);
        }
        assertTrue("冷却缓存不应超过上限：" + registry.size(), registry.size() <= 4096);
    }
}
