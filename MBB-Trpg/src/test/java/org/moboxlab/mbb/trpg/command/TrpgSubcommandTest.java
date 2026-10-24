package org.moboxlab.mbb.trpg.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 子指令表：主名、中文别名与未落地标记。 */
public class TrpgSubcommandTest {

    @Test
    public void resolvesEveryPrimaryName() {
        assertEquals(TrpgSubcommand.START, TrpgSubcommand.resolve("start"));
        assertEquals(TrpgSubcommand.JOIN, TrpgSubcommand.resolve("join"));
        assertEquals(TrpgSubcommand.LEAVE, TrpgSubcommand.resolve("leave"));
        assertEquals(TrpgSubcommand.BEGIN, TrpgSubcommand.resolve("begin"));
        assertEquals(TrpgSubcommand.END, TrpgSubcommand.resolve("end"));
        assertEquals(TrpgSubcommand.RESUME, TrpgSubcommand.resolve("resume"));
        assertEquals(TrpgSubcommand.STATUS, TrpgSubcommand.resolve("status"));
        assertEquals(TrpgSubcommand.HELP, TrpgSubcommand.resolve("help"));
        assertEquals(TrpgSubcommand.PC, TrpgSubcommand.resolve("pc"));
        assertEquals(TrpgSubcommand.PC_REROLL, TrpgSubcommand.resolve("pc reroll"));
        assertEquals(TrpgSubcommand.PC_SET, TrpgSubcommand.resolve("pc set"));
        assertEquals(TrpgSubcommand.LOG, TrpgSubcommand.resolve("log"));
        assertEquals(TrpgSubcommand.SCENE, TrpgSubcommand.resolve("scene"));
        assertEquals(TrpgSubcommand.CHECK, TrpgSubcommand.resolve("check"));
        assertEquals(TrpgSubcommand.OPPOSED, TrpgSubcommand.resolve("opposed"));
        assertEquals(TrpgSubcommand.SAN, TrpgSubcommand.resolve("san"));
        assertEquals(TrpgSubcommand.ADJUST, TrpgSubcommand.resolve("adjust"));
        assertEquals(TrpgSubcommand.INVITE, TrpgSubcommand.resolve("invite"));
        assertEquals(TrpgSubcommand.TAKE, TrpgSubcommand.resolve("take"));
        assertEquals(TrpgSubcommand.SKIP, TrpgSubcommand.resolve("skip"));
        assertEquals(TrpgSubcommand.SCRIPT, TrpgSubcommand.resolve("script"));
        assertEquals(TrpgSubcommand.BLACKLIST, TrpgSubcommand.resolve("blacklist"));
        assertEquals(TrpgSubcommand.AI, TrpgSubcommand.resolve("ai"));
    }

    @Test
    public void resolvesChineseAliases() {
        assertEquals(TrpgSubcommand.START, TrpgSubcommand.resolve("开团"));
        assertEquals(TrpgSubcommand.JOIN, TrpgSubcommand.resolve("加入"));
        assertEquals(TrpgSubcommand.LEAVE, TrpgSubcommand.resolve("离开"));
        assertEquals(TrpgSubcommand.BEGIN, TrpgSubcommand.resolve("开局"));
        assertEquals(TrpgSubcommand.END, TrpgSubcommand.resolve("结束"));
        assertEquals(TrpgSubcommand.RESUME, TrpgSubcommand.resolve("恢复"));
        assertEquals(TrpgSubcommand.STATUS, TrpgSubcommand.resolve("状态"));
        assertEquals(TrpgSubcommand.HELP, TrpgSubcommand.resolve("帮助"));
        assertEquals(TrpgSubcommand.PC, TrpgSubcommand.resolve("角色卡"));
        assertEquals(TrpgSubcommand.PC_REROLL, TrpgSubcommand.resolve("重掷"));
        assertEquals(TrpgSubcommand.PC_SET, TrpgSubcommand.resolve("微调"));
        assertEquals(TrpgSubcommand.LOG, TrpgSubcommand.resolve("日志"));
        assertEquals(TrpgSubcommand.SCENE, TrpgSubcommand.resolve("场景"));
        assertEquals(TrpgSubcommand.CHECK, TrpgSubcommand.resolve("检定"));
        assertEquals(TrpgSubcommand.OPPOSED, TrpgSubcommand.resolve("对抗"));
        assertEquals(TrpgSubcommand.SAN, TrpgSubcommand.resolve("理智"));
        assertEquals(TrpgSubcommand.ADJUST, TrpgSubcommand.resolve("调整"));
        assertEquals(TrpgSubcommand.INVITE, TrpgSubcommand.resolve("邀请"));
        assertEquals(TrpgSubcommand.TAKE, TrpgSubcommand.resolve("接管"));
        assertEquals(TrpgSubcommand.SKIP, TrpgSubcommand.resolve("跳过"));
        assertEquals(TrpgSubcommand.SCRIPT, TrpgSubcommand.resolve("剧本"));
        assertEquals(TrpgSubcommand.BLACKLIST, TrpgSubcommand.resolve("黑名单"));
        assertEquals(TrpgSubcommand.AI, TrpgSubcommand.resolve("AI 主持"));
    }

    @Test
    public void englishIsCaseInsensitiveAndTrimmed() {
        assertEquals(TrpgSubcommand.STATUS, TrpgSubcommand.resolve("STATUS"));
        assertEquals(TrpgSubcommand.PC_REROLL, TrpgSubcommand.resolve("  pc reroll  "));
    }

    @Test
    public void unknownTokensAreRejected() {
        assertNull(TrpgSubcommand.resolve("nope"));
        assertNull(TrpgSubcommand.resolve(""));
        assertNull(TrpgSubcommand.resolve("   "));
        assertNull(TrpgSubcommand.resolve(null));
        // 中文别名按原样比较，缺空格的变体不识别，避免误伤其他指令。
        assertNull(TrpgSubcommand.resolve("AI主持"));
    }

    @Test
    public void labelsCombinePrimaryAndAlias() {
        assertEquals("start / 开团", TrpgSubcommand.START.label());
        assertEquals("pc reroll / 重掷", TrpgSubcommand.PC_REROLL.label());
    }

    @Test
    public void implementedFlagsMatchSliceBoundary() {
        for (TrpgSubcommand subcommand : TrpgSubcommand.values()) {
            assertTrue("S8 之后全部子指令都应启用：" + subcommand.label(),
                    subcommand.implemented());
        }
        assertTrue(TrpgSubcommand.START.implemented());
        assertTrue(TrpgSubcommand.SCENE.implemented());
        assertTrue(TrpgSubcommand.BLACKLIST.implemented());
        assertTrue(TrpgSubcommand.AI.implemented());
    }

    @Test
    public void privateChatOnlyAllowsHelpAndScriptLibrary() {
        assertFalse(TrpgSubcommand.HELP.groupOnly());
        assertFalse(TrpgSubcommand.SCRIPT.groupOnly());
        assertFalse(TrpgSubcommand.BLACKLIST.groupOnly());
        assertTrue(TrpgSubcommand.START.groupOnly());
        assertTrue(TrpgSubcommand.LOG.groupOnly());
    }
}
