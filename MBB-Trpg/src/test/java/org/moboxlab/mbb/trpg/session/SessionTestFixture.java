package org.moboxlab.mbb.trpg.session;

import org.junit.After;
import org.junit.Before;
import org.moboxlab.mbb.trpg.config.TrpgConfig;
import org.moboxlab.mbb.trpg.rule.D100RuleSet;
import org.moboxlab.mbb.trpg.rule.DefaultRandomSource;
import org.moboxlab.mbb.trpg.rule.RandomSource;
import org.moboxlab.mbb.trpg.rule.RuleRegistry;
import org.moboxlab.mbb.trpg.script.BuiltinScriptCatalog;
import org.moboxlab.mbb.trpg.script.Script;
import org.moboxlab.mbb.trpg.script.ScriptValidator;
import org.moboxlab.mbb.trpg.script.ScriptEnding;
import org.moboxlab.mbb.trpg.store.CardData;
import org.moboxlab.mbb.trpg.store.JdbcStorageFake;
import org.moboxlab.mbb.trpg.store.LogRow;
import org.moboxlab.mbb.trpg.store.MemberRow;
import org.moboxlab.mbb.trpg.store.SessionRow;
import org.moboxlab.mbb.trpg.store.SqlSessionStore;
import org.moboxlab.mbb.trpg.store.TrpgSchemaService;
import org.moboxlab.mbb.trpg.testutil.FakeClock;
import org.moboxlab.mbb.trpg.testutil.TempDirs;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 局面试句的公共基座：真 SQLite、内置示例剧本、可控时钟、内存群锁。
 *
 * <p>不是测试类（类名不带 Test），由各测试类在 {@code setUp} 里调用 {@link #baseSetUp(String)}。</p>
 */
abstract class SessionTestFixture {

    static final long GROUP = 9001L;
    static final long OTHER_GROUP = 9002L;
    static final long KP = 1001L;
    static final long PLAYER = 1002L;
    static final long OTHER = 1003L;
    static final long NOW = 1700000000000L;
    static final String SCRIPT_ID = "coc-example";
    static final String SKILL = "侦查";

    File tempDir;
    JdbcStorageFake storage;
    SqlSessionStore store;
    FakeClock clock;
    BuiltinScriptCatalog catalog;
    TrpgConfig config;
    GroupLockRegistry locks;
    final List<SessionEvent> events = new ArrayList<SessionEvent>();
    SessionService service;

    @Before
    public void baseSetUp() {
        baseSetUp("mbb-trpg-session");
    }

    void baseSetUp(String prefix) {
        tempDir = TempDirs.create(prefix);
        storage = new JdbcStorageFake(new File(tempDir, "trpg.db"));
        new TrpgSchemaService(storage, null).ensureSchema();
        store = new SqlSessionStore(storage, null);
        clock = new FakeClock(NOW);
        catalog = new BuiltinScriptCatalog(SessionTestFixture.class.getClassLoader(), new ScriptValidator());
        config = TrpgConfig.fromValues(new LinkedHashMap<String, String>());
        RuleRegistry.register(new D100RuleSet());
        locks = new GroupLockRegistry(clock);
        events.clear();
        service = buildService(DefaultRandomSource.INSTANCE, collectingPublisher());
    }

    @After
    public void baseTearDown() {
        if (storage != null) {
            storage.close();
        }
        TempDirs.deleteRecursively(tempDir);
    }

    SessionService buildService(RandomSource random, SessionEventPublisher publisher) {
        return new SessionService(store, catalog, clock, random, config, publisher, null,
                locks, new CooldownRegistry(), new ErrorDedupe());
    }

    SessionEventPublisher collectingPublisher() {
        return new SessionEventPublisher() {
            @Override
            public void publish(SessionEvent event) {
                events.add(event);
            }
        };
    }

    SessionActor actor(long userId) {
        return new SessionActor(userId, GROUP, false);
    }

    SessionActor admin(long userId) {
        return new SessionActor(userId, GROUP, true);
    }

    SessionActor botAdmin(long userId) {
        return new SessionActor(userId, GROUP, true, true);
    }

    SessionResult start() {
        return service.start(actor(KP), SCRIPT_ID);
    }

    void startSession() {
        SessionResult result = start();
        assertTrue("建局应成功：" + result.detail(), result.ok());
        assertEquals(SessionState.RECRUITING, result.snapshot().state());
    }

    void startJoining() {
        startSession();
        SessionResult joined = service.join(actor(PLAYER), CardData.empty());
        assertTrue("入局应成功：" + joined.detail(), joined.ok());
    }

    void startRunning() {
        startJoining();
        SessionResult begun = service.begin(actor(KP));
        assertTrue("开局应成功：" + begun.detail(), begun.ok());
        assertEquals(SessionState.RUNNING, begun.snapshot().state());
    }

    long activeSessionId() {
        SessionRow row = store.findActiveByGroup(GROUP);
        return row == null ? 0L : row.id();
    }

    SessionRow row(long sessionId) {
        return store.findSession(sessionId);
    }

    MemberRow member(long sessionId, long userId) {
        for (MemberRow candidate : store.loadMembers(sessionId)) {
            if (candidate.userId() == userId) {
                return candidate;
            }
        }
        return null;
    }

    /** 直接改成员的成功技能列表，模拟检定成功后待结算的成长。 */
    void setSuccessSkills(long sessionId, long userId, List<String> skills) {
        MemberRow current = member(sessionId, userId);
        assertNotNull("成员行应存在：" + userId, current);
        MemberRow.Builder builder = MemberRow.builder()
                .id(current.id())
                .sessionId(current.sessionId())
                .userId(current.userId())
                .cardJson(current.cardJson())
                .successSkills(skills)
                .joinedAt(current.joinedAt())
                .leftAt(current.leftAt())
                .takenOver(current.takenOver())
                .madnessUntil(current.madnessUntil())
                .createTime(current.createTime())
                .updateTime(clock.nowMillis());
        assertEquals(1, store.updateMember(builder.build()));
    }

    /** 直接改成员的八属性与技能，模拟角色卡上的技能值。 */
    void setCard(long sessionId, long userId, CardData card) {
        MemberRow current = member(sessionId, userId);
        assertNotNull("成员行应存在：" + userId, current);
        MemberRow.Builder builder = MemberRow.builder()
                .id(current.id())
                .sessionId(current.sessionId())
                .userId(current.userId())
                .card(card)
                .successSkills(current.successSkills())
                .joinedAt(current.joinedAt())
                .leftAt(current.leftAt())
                .takenOver(current.takenOver())
                .madnessUntil(current.madnessUntil())
                .createTime(current.createTime())
                .updateTime(clock.nowMillis());
        assertEquals(1, store.updateMember(builder.build()));
    }

    /** 写入一条线索解锁事件（eventType=clue，content=线索 id）。 */
    void unlockClue(long sessionId, String clueKey) {
        long seq = store.maxLogSeq(sessionId) + 1L;
        assertEquals(1, store.appendLog(new LogRow(0L, sessionId, seq, "clue", 0L, clueKey,
                clock.nowMillis())));
    }

    /** 直接插入一条指定状态与规则集的局面行，并补一行 KP 成员，供恢复与补做测试使用。 */
    long insertSession(long groupId, SessionState state, String ruleSetId) {
        long now = clock.nowMillis();
        SessionRow row = SessionRow.builder()
                .groupId(groupId)
                .state(state)
                .kpUserId(KP)
                .scriptId(SCRIPT_ID)
                .ruleSetId(ruleSetId)
                .lastActivityAt(now)
                .createTime(now)
                .updateTime(now)
                .build();
        long sessionId = store.insertSession(row);
        assertTrue("直插局面应成功", sessionId > 0L);
        assertTrue("直插 KP 成员应成功", store.insertMember(MemberRow.builder()
                .sessionId(sessionId)
                .userId(KP)
                .card(CardData.empty())
                .successSkills(new ArrayList<String>())
                .joinedAt(now)
                .createTime(now)
                .updateTime(now)
                .build()) > 0L);
        return sessionId;
    }

    Script script() {
        Script script = catalog.get(SCRIPT_ID);
        assertNotNull("内置示例剧本应可加载", script);
        return script;
    }

    String firstSceneTitle() {
        return script().sceneAt(0).title();
    }

    /** 指定第几个结局（从 0 起）。 */
    ScriptEnding ending(int index) {
        return script().endings().get(index);
    }

    /** 构造一份只有技能值的角色卡。 */
    CardData cardWithSkill(String skillId, int value) {
        LinkedHashMap<String, Integer> skills = new LinkedHashMap<String, Integer>();
        skills.put(skillId, Integer.valueOf(value));
        return CardData.empty().withSkills(skills);
    }

    List<String> list(String... values) {
        return new ArrayList<String>(Arrays.asList(values));
    }
}
