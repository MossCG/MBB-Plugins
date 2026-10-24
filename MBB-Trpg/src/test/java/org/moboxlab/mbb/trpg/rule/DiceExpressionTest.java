package org.moboxlab.mbb.trpg.rule;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.moboxlab.mbb.trpg.testutil.FakeRandom;

/** 骰点表达式契约测试。 */
public class DiceExpressionTest {

    @Test
    public void constantExpressionRollsItself() {
        DiceExpression expression = DiceExpression.constant(3);
        assertEquals(0, expression.count());
        assertEquals(3, expression.roll(new FakeRandom()));
        assertEquals(3, expression.maxValue());
        assertEquals("3", expression.describe());
    }

    @Test
    public void diceExpressionSumsFacesAndModifier() {
        DiceExpression expression = DiceExpression.dice(2, 6, 1);
        FakeRandom random = new FakeRandom(6, 3);
        assertEquals(10, expression.roll(random));
        assertEquals(13, expression.maxValue());
        assertEquals("2D6+1", expression.describe());
        assertEquals(2, expression.count());
        assertEquals(6, expression.sides());
        assertEquals(1, expression.modifier());
    }

    @Test
    public void negativeModifierIsDescribed() {
        DiceExpression expression = DiceExpression.dice(1, 6, -2);
        assertEquals("1D6-2", expression.describe());
        assertEquals(4, expression.maxValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeDiceCount() {
        DiceExpression.dice(-1, 6, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonPositiveSides() {
        DiceExpression.dice(1, 0, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNullRandom() {
        DiceExpression.dice(1, 6, 0).roll(null);
    }
}
