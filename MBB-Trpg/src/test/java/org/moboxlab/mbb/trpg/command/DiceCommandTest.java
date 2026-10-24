package org.moboxlab.mbb.trpg.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;

import org.junit.Before;
import org.junit.Test;
import org.moboxlab.moboxbot.API.Command.CommandPermission;
import org.moboxlab.mbb.trpg.config.TrpgConfig;
import org.moboxlab.mbb.trpg.present.TrpgPresentConfig;
import org.moboxlab.mbb.trpg.present.TrpgPresenter;
import org.moboxlab.mbb.trpg.present.TrpgReport;
import org.moboxlab.mbb.trpg.present.TrpgReportSource;
import org.moboxlab.mbb.trpg.script.Script;
import org.moboxlab.mbb.trpg.session.SessionEvent;
import org.moboxlab.mbb.trpg.testutil.FakeClock;
import org.moboxlab.mbb.trpg.testutil.FakeCommandSender;
import org.moboxlab.mbb.trpg.testutil.FakeImageRenderer;
import org.moboxlab.mbb.trpg.testutil.FakeMessaging;
import org.moboxlab.mbb.trpg.testutil.FakeNameResolver;
import org.moboxlab.mbb.trpg.testutil.FakeRandom;

/** /r 与 /roll：解析、求和、冷却、私聊与元信息。 */
public class DiceCommandTest {

    private static final long GROUP = 6001L;
    private static final long USER = 3001L;
    private static final long NOW = 1700000000000L;

    private FakeMessaging messaging;
    private FakeRandom random;
    private FakeClock clock;
    private DiceCommand command;

    @Before
    public void setUp() {
        messaging = new FakeMessaging();
        random = new FakeRandom();
        clock = new FakeClock(NOW);
        TrpgConfig config = TrpgConfig.fromValues(new LinkedHashMap<String, String>());
        TrpgPresenter presenter = new TrpgPresenter(TrpgPresentConfig.defaults(), messaging,
                new FakeImageRenderer(), new FakeNameResolver(), null, new TrpgReportSource() {

                    @Override
                    public TrpgReport report(SessionEvent event, Script script) {
                        return null;
                    }
                }, null);
        command = new DiceCommand(config, presenter, random, clock);
    }

    @Test
    public void rollsSingleDiceSegment() {
        random.enqueue(2, 3, 4);
        assertTrue(command.execute(groupSender(), new String[]{"3d6"}));
        String text = lastGroupText();
        assertTrue(text, text.contains("/r 3d6 = 9"));
    }

    @Test
    public void sumsDiceAndConstants() {
        random.enqueue(5);
        assertTrue(command.execute(groupSender(), new String[]{"1d100+5"}));
        String text = lastGroupText();
        assertTrue(text, text.contains("[r] 1d100 = 5"));
        assertTrue(text, text.contains("[r] 1d100+5 = 10"));
    }

    @Test
    public void joinsArgumentsBeforeParsing() {
        random.enqueue(2, 2, 2);
        assertTrue(command.execute(groupSender(), new String[]{"3d6", "+", "1"}));
        assertTrue(lastGroupText(), lastGroupText().contains("3d6+1 = 7"));
    }

    @Test
    public void rejectsUnsupportedExpressionWithoutRolling() {
        assertTrue(command.execute(groupSender(), new String[]{"3d6*2"}));
        String text = lastGroupText();
        assertTrue(text, text.contains("表达式不支持：3d6*2"));
        assertEquals(0, random.remaining());
    }

    @Test
    public void rejectsMissingExpression() {
        assertTrue(command.execute(groupSender(), new String[0]));
        assertTrue(lastGroupText(), lastGroupText().contains("缺少骰子表达式"));
    }

    @Test
    public void cooldownIsTwoSecondsPerUserAndChannel() {
        random.enqueue(1);
        assertTrue(command.execute(groupSender(), new String[]{"1d6"}));
        assertTrue(command.execute(groupSender(), new String[]{"1d6"}));
        assertTrue(lastGroupText(), lastGroupText().contains("掷骰还有"));
        assertFalse(lastGroupText(), lastGroupText().contains("1d6 = 1"));
        clock.advance(2000L);
        random.enqueue(2);
        assertTrue(command.execute(groupSender(), new String[]{"1d6"}));
        assertTrue(lastGroupText(), lastGroupText().contains("1d6 = 2"));
    }

    @Test
    public void privateChatIsAllowed() {
        random.enqueue(6);
        assertTrue(command.execute(FakeCommandSender.privateSender(USER, "小满"), new String[]{"1d6"}));
        assertEquals(1, messaging.sentTo("private").size());
        assertEquals(0, messaging.sentTo("group").size());
        assertTrue(lastPrivateText(), lastPrivateText().contains("1d6 = 6"));
    }

    @Test
    public void metadataMatchesFrozenPrefixes() {
        assertEquals(Arrays.asList("r", "roll"), command.prefix());
        assertEquals(CommandPermission.EVERYONE, command.permission());
        assertEquals(0, command.cooldownSeconds());
        assertFalse(command.usage().isEmpty());
        assertFalse(command.description().trim().isEmpty());
    }

    private FakeCommandSender groupSender() {
        return FakeCommandSender.group(GROUP, USER, "小满");
    }

    private String lastGroupText() {
        int size = messaging.sentTo("group").size();
        return size == 0 ? "" : messaging.sentTo("group").get(size - 1).text();
    }

    private String lastPrivateText() {
        int size = messaging.sentTo("private").size();
        return size == 0 ? "" : messaging.sentTo("private").get(size - 1).text();
    }
}
