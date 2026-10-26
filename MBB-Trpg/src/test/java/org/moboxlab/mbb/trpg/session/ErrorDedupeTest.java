package org.moboxlab.mbb.trpg.session;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

/** 错误去重登记必须有界。 */
public class ErrorDedupeTest {

    @Test
    public void registryIsBounded() {
        ErrorDedupe dedupe = new ErrorDedupe();
        for (int index = 0; index < 3000; index++) {
            dedupe.claim(index,"key-"+index,index,10L);
        }
        assertTrue("错误去重缓存不应超过上限：" + dedupe.size(), dedupe.size() <= 2048);
    }
}
