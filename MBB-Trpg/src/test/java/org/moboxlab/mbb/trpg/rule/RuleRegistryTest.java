package org.moboxlab.mbb.trpg.rule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** 规则集注册表契约测试，覆盖 G7 的未知规则集回退与告警。 */
public class RuleRegistryTest {

    private static final String CUSTOM_ID = "test.custom";

    /** 每个用例都从「内置已注册、无告警」的干净状态开始，避免静态注册表跨用例污染。 */
    @Before
    public void setUp() {
        RuleRegistry.unregister(CUSTOM_ID);
        RuleRegistry.register(new D100RuleSet());
        RuleRegistry.clearWarnings();
    }

    @After
    public void cleanup() {
        RuleRegistry.unregister(D100RuleSet.ID);
        RuleRegistry.unregister(CUSTOM_ID);
        RuleRegistry.clearWarnings();
    }

    @Test
    public void registersAndLooksUpById() {
        D100RuleSet ruleSet = new D100RuleSet();
        RuleRegistry.register(ruleSet);
        assertSame(ruleSet, RuleRegistry.get("d100"));
        assertTrue(RuleRegistry.get("d100") instanceof D100RuleSet);
    }

    @Test
    public void sameIdOverwritesPreviousRegistration() {
        D100RuleSet later = new D100RuleSet();
        RuleRegistry.register(later);
        assertSame(later, RuleRegistry.get("d100"));
    }

    @Test
    public void unknownRuleSetFallsBackToBuiltinAndWarns() {
        D100RuleSet builtin = new D100RuleSet();
        RuleRegistry.register(builtin);
        RuleRegistry.clearWarnings();

        RuleSet resolved = RuleRegistry.getOrDefault("coc7x");
        assertSame(builtin, resolved);
        assertEquals(1, RuleRegistry.warnings().size());
        assertTrue(RuleRegistry.warnings().get(0).contains("coc7x"));

        assertSame(builtin, RuleRegistry.getOrDefault("d100"));
        assertEquals(1, RuleRegistry.warnings().size());
    }

    @Test
    public void getDoesNotFallBack() {
        RuleRegistry.register(new D100RuleSet());
        assertNull(RuleRegistry.get("coc7x"));
        assertNull(RuleRegistry.get(null));
    }

    @Test
    public void unknownRuleSetFallsBackToNullWhenBuiltinMissing() {
        RuleRegistry.unregister(D100RuleSet.ID);
        RuleRegistry.clearWarnings();
        assertNull(RuleRegistry.getOrDefault("coc7x"));
        assertEquals(1, RuleRegistry.warnings().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNullRuleSet() {
        RuleRegistry.register(null);
    }
}
