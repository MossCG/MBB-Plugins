package org.moboxlab.mbb.trpg.rule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 生产随机源契约测试：只验证取值范围与单例，不验证随机分布。 */
public class DefaultRandomSourceTest {

    private static final int SAMPLES = 200;

    @Test
    public void exposesSingleInstance() {
        assertSame(DefaultRandomSource.INSTANCE, DefaultRandomSource.INSTANCE);
    }

    @Test
    public void percentileStaysInRange() {
        for (int i = 0; i < SAMPLES; i++) {
            int face = DefaultRandomSource.INSTANCE.roll(100);
            assertTrue("d100 骰面越界：" + face, face >= 1 && face <= 100);
        }
    }

    @Test
    public void tensDieStaysInDigits() {
        for (int i = 0; i < SAMPLES; i++) {
            int digit = DefaultRandomSource.INSTANCE.d10();
            assertTrue("十位骰面越界：" + digit, digit >= 0 && digit <= 9);
        }
    }

    @Test
    public void coversAllFacesOfSmallDice() {
        boolean[] seen = new boolean[6];
        for (int i = 0; i < SAMPLES; i++) {
            seen[DefaultRandomSource.INSTANCE.roll(6) - 1] = true;
        }
        for (int face = 0; face < seen.length; face++) {
            assertTrue("D6 未出现骰面 " + (face + 1), seen[face]);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsZeroSides() {
        DefaultRandomSource.INSTANCE.roll(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeSides() {
        DefaultRandomSource.INSTANCE.roll(-1);
    }

    @Test
    public void exposesNoExtraState() {
        assertEquals(DefaultRandomSource.class, DefaultRandomSource.INSTANCE.getClass());
    }
}
