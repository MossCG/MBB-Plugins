package org.moboxlab.mbb.trpg.store;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.PluginLogger;
import org.moboxlab.moboxbot.API.Storage.StorageService;
import org.moboxlab.mbb.trpg.session.SessionState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link SessionStore} 的 SQLite 实现，插件全部存储 SQL 的唯一落点。
 *
 * <p>职责边界：只做行对象与 SQL 参数的互转，不校验业务状态、不加锁、不发消息；
 * 主程序 StorageService 的返回码原样透传（update -1 / 0，insert 0，query 部分行），
 * 由 SessionService 等调用方按状态机决定重读、拒绝还是下轮重试。读取时把库中的
 * 状态文本转成 {@link SessionState}，无法识别的文本按 {@link SessionState#NONE}
 * 处理并告警。</p>
 *
 * <p>字符截断在写入层兜底：日志 content 最多 2000 字符，审计 preview 最多 240 字符。</p>
 */
public final class SqlSessionStore implements SessionStore {

    /** 日志内容最大字符数（规格 §7.4）。 */
    static final int LOG_CONTENT_LIMIT = 2000;
    /** 审计摘要最大字符数（规格 §7.3）。 */
    static final int TRACE_PREVIEW_LIMIT = 240;

    private static final String ACTIVE_STATES = "'RECRUITING','RUNNING'";

    private static final String SQL_FIND_ACTIVE_BY_GROUP = "SELECT * FROM "
            + TrpgSchemaService.TABLE_SESSION + " WHERE groupID = ? AND state IN (" + ACTIVE_STATES
            + ") ORDER BY id DESC LIMIT 1";
    private static final String SQL_FIND_SESSION = "SELECT * FROM "
            + TrpgSchemaService.TABLE_SESSION + " WHERE id = ?";
    private static final String SQL_FIND_LATEST_ARCHIVED_BY_GROUP = "SELECT * FROM "
            + TrpgSchemaService.TABLE_SESSION
            + " WHERE groupID = ? AND state = 'ENDED' AND endedAt >= ? ORDER BY id DESC LIMIT 1";
    private static final String SQL_LOAD_ACTIVE_SESSIONS = "SELECT * FROM "
            + TrpgSchemaService.TABLE_SESSION + " WHERE state IN (" + ACTIVE_STATES
            + ") ORDER BY id ASC";
    private static final String SQL_LIST_UNSETTLED = "SELECT * FROM "
            + TrpgSchemaService.TABLE_SESSION
            + " WHERE state = 'ENDED' AND growthSettled = 0 ORDER BY id ASC";
    private static final String SQL_LIST_EXPIRED = "SELECT id FROM "
            + TrpgSchemaService.TABLE_SESSION
            + " WHERE state = 'ENDED' AND growthSettled = 1 AND endedAt < ? ORDER BY id ASC";

    private static final String SQL_INSERT_SESSION = "INSERT INTO "
            + TrpgSchemaService.TABLE_SESSION
            + " (groupID, state, kpUserID, scriptID, ruleSetID, sceneIndex, sceneTitle,"
            + " startedAt, endedAt, endReason, summary, lastActivityAt, aiEnabled,"
            + " aiSuspendedUntil, aiFailStreak, growthSettled, createTime, updateTime)"
            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    private static final String SQL_CAS_TRANSITION = "UPDATE " + TrpgSchemaService.TABLE_SESSION
            + " SET state = ?, startedAt = ?, endedAt = ?, endReason = ?, growthSettled = ?,"
            + " summary = ?, updateTime = ? WHERE id = ? AND state = ?";
    private static final String SQL_CAS_SCENE = "UPDATE " + TrpgSchemaService.TABLE_SESSION
            + " SET sceneIndex = ?, sceneTitle = ?, updateTime = ? WHERE id = ? AND state = ?";
    private static final String SQL_CAS_SCRIPT = "UPDATE " + TrpgSchemaService.TABLE_SESSION
            + " SET scriptID = ?, updateTime = ? WHERE id = ? AND state = ?";
    private static final String SQL_CAS_AI_STATE = "UPDATE " + TrpgSchemaService.TABLE_SESSION
            + " SET aiEnabled = ?, aiSuspendedUntil = ?, aiFailStreak = ?, updateTime = ?"
            + " WHERE id = ? AND state = ?";
    private static final String SQL_TOUCH_ACTIVITY = "UPDATE " + TrpgSchemaService.TABLE_SESSION
            + " SET lastActivityAt = ?, updateTime = ? WHERE id = ?";
    private static final String SQL_SETTLE_SUMMARY = "UPDATE " + TrpgSchemaService.TABLE_SESSION
            + " SET summary = ?, updateTime = ? WHERE id = ?";

    private static final String SQL_LOAD_MEMBERS = "SELECT * FROM "
            + TrpgSchemaService.TABLE_MEMBER + " WHERE sessionID = ? ORDER BY id ASC";
    private static final String SQL_INSERT_MEMBER = "INSERT INTO "
            + TrpgSchemaService.TABLE_MEMBER
            + " (sessionID, userID, cardJSON, successSkills, joinedAt, leftAt, takenOver,"
            + " madnessUntil, createTime, updateTime) VALUES (?,?,?,?,?,?,?,?,?,?)";
    private static final String SQL_UPDATE_MEMBER = "UPDATE " + TrpgSchemaService.TABLE_MEMBER
            + " SET cardJSON = ?, successSkills = ?, leftAt = ?, takenOver = ?,"
            + " madnessUntil = ?, updateTime = ? WHERE id = ?";
    private static final String SQL_SET_SUCCESS_SKILLS = "UPDATE " + TrpgSchemaService.TABLE_MEMBER
            + " SET successSkills = ?, updateTime = ? WHERE sessionID = ? AND userID = ?";

    private static final String SQL_INSERT_LOG = "INSERT INTO "
            + TrpgSchemaService.TABLE_TEXT_LOG
            + " (sessionID, seq, eventType, actorID, content, createTime)"
            + " VALUES (?,?,?,?,?,?)";
    private static final String SQL_MAX_LOG_SEQ = "SELECT COALESCE(MAX(seq), 0) AS maxSeq FROM "
            + TrpgSchemaService.TABLE_TEXT_LOG + " WHERE sessionID = ?";
    private static final String SQL_LIST_LOGS = "SELECT * FROM "
            + TrpgSchemaService.TABLE_TEXT_LOG
            + " WHERE sessionID = ? ORDER BY seq DESC LIMIT ? OFFSET ?";
    private static final String SQL_LIST_CLUE_KEYS = "SELECT DISTINCT content FROM "
            + TrpgSchemaService.TABLE_TEXT_LOG
            + " WHERE sessionID = ? AND eventType = 'clue' ORDER BY content ASC";

    private static final String SQL_INSERT_BLACKLIST = "INSERT INTO "
            + TrpgSchemaService.TABLE_BLACKLIST
            + " (groupID, userID, operatorID, reason, createTime) VALUES (?,?,?,?,?)";
    private static final String SQL_DELETE_BLACKLIST = "DELETE FROM "
            + TrpgSchemaService.TABLE_BLACKLIST + " WHERE groupID = ? AND userID = ?";
    private static final String SQL_LIST_BLACKLIST = "SELECT * FROM "
            + TrpgSchemaService.TABLE_BLACKLIST + " WHERE groupID = ? ORDER BY id ASC";
    private static final String SQL_FIND_BLACKLIST = "SELECT id FROM "
            + TrpgSchemaService.TABLE_BLACKLIST + " WHERE groupID = ? AND userID = ? LIMIT 1";

    private static final String SQL_INSERT_TRACE = "INSERT INTO "
            + TrpgSchemaService.TABLE_AI_TRACE
            + " (groupID, sessionID, eventSeq, triggerType, resultStatus, degradeReason,"
            + " errorType, attempts, elapsedMs, finishReason, usageJSON, cached, preview, createTime)"
            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    private static final String SQL_LIST_TRACE_RECENT = "SELECT * FROM "
            + TrpgSchemaService.TABLE_AI_TRACE
            + " WHERE groupID = ? AND sessionID = ? ORDER BY id DESC LIMIT ?";
    private static final String SQL_LIST_TRACE_RECENT_BY_GROUP = "SELECT * FROM "
            + TrpgSchemaService.TABLE_AI_TRACE + " WHERE groupID = ? ORDER BY id DESC LIMIT ?";
    private static final String SQL_DELETE_TRACE_BEFORE = "DELETE FROM "
            + TrpgSchemaService.TABLE_AI_TRACE + " WHERE createTime < ?";
    private static final String SQL_DELETE_TRACE_OVERFLOW = "DELETE FROM "
            + TrpgSchemaService.TABLE_AI_TRACE
            + " WHERE id < (SELECT id FROM " + TrpgSchemaService.TABLE_AI_TRACE
            + " ORDER BY id DESC LIMIT 1 OFFSET ?)";

    private static final String SQL_DELETE_LOGS = "DELETE FROM "
            + TrpgSchemaService.TABLE_TEXT_LOG + " WHERE sessionID = ?";
    private static final String SQL_DELETE_MEMBERS = "DELETE FROM "
            + TrpgSchemaService.TABLE_MEMBER + " WHERE sessionID = ?";
    private static final String SQL_DELETE_SESSION = "DELETE FROM "
            + TrpgSchemaService.TABLE_SESSION + " WHERE id = ? AND state = 'ENDED'";

    private final StorageService storage;
    private final PluginLogger logger;

    public SqlSessionStore(StorageService storage, PluginLogger logger) {
        if (storage == null) {
            throw new IllegalArgumentException("存储服务不能为空");
        }
        this.storage = storage;
        this.logger = logger;
        for (String sql : sqlStatements()) {
            if (!TrpgSchemaService.isPluginScopedSql(sql)) {
                warn("SQL 越界（必须含 plugin_ 且不得含 bot_）：" + sql);
            }
        }
    }

    /** 全部 SQL 文本，供测试与边界自检（规格 K1）。 */
    public static List<String> sqlStatements() {
        List<String> all = new ArrayList<String>();
        all.add(SQL_FIND_ACTIVE_BY_GROUP);
        all.add(SQL_FIND_SESSION);
        all.add(SQL_LOAD_ACTIVE_SESSIONS);
        all.add(SQL_LIST_UNSETTLED);
        all.add(SQL_LIST_EXPIRED);
        all.add(SQL_INSERT_SESSION);
        all.add(SQL_CAS_TRANSITION);
        all.add(SQL_CAS_SCENE);
        all.add(SQL_CAS_SCRIPT);
        all.add(SQL_CAS_AI_STATE);
        all.add(SQL_TOUCH_ACTIVITY);
        all.add(SQL_SETTLE_SUMMARY);
        all.add(SQL_LOAD_MEMBERS);
        all.add(SQL_INSERT_MEMBER);
        all.add(SQL_UPDATE_MEMBER);
        all.add(SQL_SET_SUCCESS_SKILLS);
        all.add(SQL_INSERT_LOG);
        all.add(SQL_MAX_LOG_SEQ);
        all.add(SQL_LIST_LOGS);
        all.add(SQL_LIST_CLUE_KEYS);
        all.add(SQL_INSERT_BLACKLIST);
        all.add(SQL_DELETE_BLACKLIST);
        all.add(SQL_LIST_BLACKLIST);
        all.add(SQL_FIND_BLACKLIST);
        all.add(SQL_INSERT_TRACE);
        all.add(SQL_LIST_TRACE_RECENT);
        all.add(SQL_LIST_TRACE_RECENT_BY_GROUP);
        all.add(SQL_DELETE_TRACE_BEFORE);
        all.add(SQL_DELETE_TRACE_OVERFLOW);
        all.add(SQL_DELETE_LOGS);
        all.add(SQL_DELETE_MEMBERS);
        all.add(SQL_DELETE_SESSION);
        return Collections.unmodifiableList(all);
    }

    @Override
    public SessionRow findActiveByGroup(long groupId) {
        return readSession(storage.queryOne(SQL_FIND_ACTIVE_BY_GROUP, groupId));
    }

    @Override
    public SessionRow findSession(long sessionId) {
        return readSession(storage.queryOne(SQL_FIND_SESSION, sessionId));
    }

    @Override
    public SessionRow findLatestArchivedByGroup(long groupId, long endedAfter) {
        return readSession(storage.queryOne(SQL_FIND_LATEST_ARCHIVED_BY_GROUP,
                Long.valueOf(groupId), Long.valueOf(endedAfter)));
    }

    @Override
    public long insertSession(SessionRow row) {
        if (row == null) {
            warn("insertSession 收到空行，跳过");
            return 0L;
        }
        return storage.insert(SQL_INSERT_SESSION,
                row.groupId(), row.state().name(), row.kpUserId(), row.scriptId(), row.ruleSetId(),
                row.sceneIndex(), row.sceneTitle(), row.startedAt(), row.endedAt(), row.endReason(),
                row.summary(), row.lastActivityAt(), flag(row.aiEnabled()), row.aiSuspendedUntil(),
                row.aiFailStreak(), flag(row.growthSettled()), row.createTime(), row.updateTime());
    }

    @Override
    public int casTransition(long sessionId, SessionState expected, SessionState target,
                             long startedAt, long endedAt, String endReason, boolean growthSettled,
                             String summary, long updateTime) {
        return storage.update(SQL_CAS_TRANSITION, stateText(target), startedAt, endedAt,
                endReason == null ? "" : endReason, flag(growthSettled),
                summary == null ? "" : summary, updateTime, sessionId, stateText(expected));
    }

    @Override
    public int casScene(long sessionId, SessionState expected, int sceneIndex, String sceneTitle,
                        long updateTime) {
        return storage.update(SQL_CAS_SCENE, sceneIndex, sceneTitle == null ? "" : sceneTitle,
                updateTime, sessionId, stateText(expected));
    }

    @Override
    public int casScript(long sessionId, SessionState expected, String scriptId, long updateTime) {
        return storage.update(SQL_CAS_SCRIPT, scriptId == null ? "" : scriptId, updateTime,
                sessionId, stateText(expected));
    }

    @Override
    public int casAiState(long sessionId, SessionState expected, boolean aiEnabled,
                          long aiSuspendedUntil, int aiFailStreak, long updateTime) {
        return storage.update(SQL_CAS_AI_STATE, flag(aiEnabled), aiSuspendedUntil, aiFailStreak,
                updateTime, sessionId, stateText(expected));
    }

    @Override
    public int touchActivity(long sessionId, long lastActivityAt, long updateTime) {
        return storage.update(SQL_TOUCH_ACTIVITY, lastActivityAt, updateTime, sessionId);
    }

    @Override
    public List<SessionRow> loadActiveSessions() {
        return readSessions(SQL_LOAD_ACTIVE_SESSIONS);
    }

    @Override
    public List<MemberRow> loadMembers(long sessionId) {
        List<MemberRow> members = new ArrayList<MemberRow>();
        for (JSONObject row : rows(storage.query(SQL_LOAD_MEMBERS, sessionId))) {
            MemberRow member = readMember(row);
            if (member != null) {
                members.add(member);
            }
        }
        return members;
    }

    @Override
    public long insertMember(MemberRow row) {
        if (row == null) {
            warn("insertMember 收到空行，跳过");
            return 0L;
        }
        return storage.insert(SQL_INSERT_MEMBER, row.sessionId(), row.userId(), row.cardJson(),
                row.successSkillsJson(), row.joinedAt(), row.leftAt(), flag(row.takenOver()),
                row.madnessUntil(), row.createTime(), row.updateTime());
    }

    @Override
    public int updateMember(MemberRow row) {
        if (row == null || row.id() <= 0) {
            warn("updateMember 缺少成员主键，跳过");
            return 0;
        }
        return storage.update(SQL_UPDATE_MEMBER, row.cardJson(), row.successSkillsJson(),
                row.leftAt(), flag(row.takenOver()), row.madnessUntil(), row.updateTime(), row.id());
    }

    @Override
    public int appendSuccessSkills(long sessionId, long userId, List<String> skills, long updateTime) {
        List<String> safe = skills == null ? new ArrayList<String>() : skills;
        return storage.update(SQL_SET_SUCCESS_SKILLS, JSON.toJSONString(safe), updateTime,
                sessionId, userId);
    }

    @Override
    public List<SessionRow> listUnsettledSessions() {
        return readSessions(SQL_LIST_UNSETTLED);
    }

    @Override
    public int settleSummary(long sessionId, String summary, long updateTime) {
        return storage.update(SQL_SETTLE_SUMMARY, summary == null ? "" : summary, updateTime,
                sessionId);
    }

    @Override
    public int appendLog(LogRow row) {
        if (row == null) {
            warn("appendLog 收到空行，跳过");
            return 0;
        }
        long id = storage.insert(SQL_INSERT_LOG, row.sessionId(), row.seq(), row.eventType(),
                row.actorId(), truncate(row.content(), LOG_CONTENT_LIMIT), row.createTime());
        return id > 0L ? 1 : 0;
    }

    @Override
    public long maxLogSeq(long sessionId) {
        JSONObject row = storage.queryOne(SQL_MAX_LOG_SEQ, sessionId);
        return row == null ? 0L : longOf(row, "maxSeq");
    }

    @Override
    public List<LogRow> listLogs(long sessionId, int limit, int offset) {
        List<LogRow> logs = new ArrayList<LogRow>();
        for (JSONObject row : rows(storage.query(SQL_LIST_LOGS, sessionId,
                Math.max(0, limit), Math.max(0, offset)))) {
            Long id = row.getLong("id");
            logs.add(new LogRow(id == null ? 0L : id.longValue(), longOf(row, "sessionID"),
                    longOf(row, "seq"), stringOf(row, "eventType"), longOf(row, "actorID"),
                    stringOf(row, "content"), longOf(row, "createTime")));
        }
        return logs;
    }

    @Override
    public List<String> listClueKeys(long sessionId) {
        List<String> keys = new ArrayList<String>();
        for (JSONObject row : rows(storage.query(SQL_LIST_CLUE_KEYS, sessionId))) {
            String content = stringOf(row, "content");
            if (!content.isEmpty()) {
                keys.add(content);
            }
        }
        return keys;
    }

    @Override
    public int insertBlacklist(BlacklistRow row) {
        if (row == null) {
            warn("insertBlacklist 收到空行，跳过");
            return 0;
        }
        long id = storage.insert(SQL_INSERT_BLACKLIST, row.groupId(), row.userId(),
                row.operatorId(), row.reason(), row.createTime());
        return id > 0L ? 1 : 0;
    }

    @Override
    public int deleteBlacklist(long groupId, long userId) {
        return storage.update(SQL_DELETE_BLACKLIST, groupId, userId);
    }

    @Override
    public List<BlacklistRow> listBlacklist(long groupId) {
        List<BlacklistRow> entries = new ArrayList<BlacklistRow>();
        for (JSONObject row : rows(storage.query(SQL_LIST_BLACKLIST, groupId))) {
            Long id = row.getLong("id");
            entries.add(new BlacklistRow(id == null ? 0L : id.longValue(), longOf(row, "groupID"),
                    longOf(row, "userID"), longOf(row, "operatorID"), stringOf(row, "reason"),
                    longOf(row, "createTime")));
        }
        return entries;
    }

    @Override
    public boolean isBlacklisted(long groupId, long userId) {
        return storage.queryOne(SQL_FIND_BLACKLIST, groupId, userId) != null;
    }

    @Override
    public List<Long> listExpiredSessionIds(long endedBefore) {
        List<Long> ids = new ArrayList<Long>();
        for (JSONObject row : rows(storage.query(SQL_LIST_EXPIRED, endedBefore))) {
            Long id = row.getLong("id");
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    @Override
    public int deleteLogs(long sessionId) {
        return storage.update(SQL_DELETE_LOGS, sessionId);
    }

    @Override
    public int deleteMembers(long sessionId) {
        return storage.update(SQL_DELETE_MEMBERS, sessionId);
    }

    @Override
    public int deleteSession(long sessionId) {
        return storage.update(SQL_DELETE_SESSION, sessionId);
    }

    @Override
    public long appendAiTrace(AiTraceRow row) {
        if (row == null) {
            warn("appendAiTrace 收到空行，跳过");
            return 0L;
        }
        return storage.insert(SQL_INSERT_TRACE, row.groupId(), row.sessionId(), row.eventSeq(),
                row.triggerType(), row.resultStatus(), row.degradeReason(), row.errorType(),
                row.attempts(), row.elapsedMs(), row.finishReason(), row.usageJson(),
                flag(row.cached()), truncate(row.preview(), TRACE_PREVIEW_LIMIT), row.createTime());
    }

    @Override
    public List<AiTraceRow> listAiTrace(long groupId, long sessionId, int limit) {
        List<AiTraceRow> traces = new ArrayList<AiTraceRow>();
        if (limit <= 0) {
            return traces;
        }
        List<JSONObject> raw = sessionId > 0L
                ? storage.query(SQL_LIST_TRACE_RECENT, groupId, sessionId, limit)
                : storage.query(SQL_LIST_TRACE_RECENT_BY_GROUP, groupId, limit);
        for (JSONObject row : rows(raw)) {
            Long id = row.getLong("id");
            traces.add(new AiTraceRow(id == null ? 0L : id.longValue(), longOf(row, "groupID"),
                    longOf(row, "sessionID"), longOf(row, "eventSeq"), stringOf(row, "triggerType"),
                    stringOf(row, "resultStatus"), stringOf(row, "degradeReason"),
                    stringOf(row, "errorType"), intOf(row, "attempts"), longOf(row, "elapsedMs"),
                    stringOf(row, "finishReason"), stringOf(row, "usageJSON"),
                    boolOf(row, "cached"), stringOf(row, "preview"), longOf(row, "createTime")));
        }
        return traces;
    }

    @Override
    public int deleteAiTraceBefore(long createTime) {
        return storage.update(SQL_DELETE_TRACE_BEFORE, createTime);
    }

    @Override
    public int deleteAiTraceOverflow(int keepRows) {
        if (keepRows <= 0) {
            // 保留行数非法时宁可不动，避免把审计一次删空
            return 0;
        }
        return storage.update(SQL_DELETE_TRACE_OVERFLOW, keepRows - 1);
    }

    /** 截断到指定字符数，不把代理对切成半个字符。 */
    static String truncate(String text, int limit) {
        if (text == null) {
            return "";
        }
        if (limit <= 0 || text.length() <= limit) {
            return text;
        }
        int end = limit;
        if (Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }

    /** 布尔落库统一 0 / 1，避免依赖驱动对 Boolean 的绑定。 */
    private static int flag(boolean value) {
        return value ? 1 : 0;
    }

    private static String stateText(SessionState state) {
        return state == null ? SessionState.NONE.name() : state.name();
    }

    private static List<JSONObject> rows(List<JSONObject> raw) {
        return raw == null ? new ArrayList<JSONObject>() : raw;
    }

    private SessionRow readSession(JSONObject row) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        Long id = row.getLong("id");
        if (id == null) {
            warn("局面行缺少主键，已跳过");
            return null;
        }
        SessionState state = SessionState.fromDb(row.getString("state"));
        if (state == SessionState.NONE) {
            warn("局面 " + id + " 的状态文本无法识别，按无局处理：" + row.getString("state"));
        }
        return SessionRow.builder()
                .id(id.longValue())
                .groupId(longOf(row, "groupID"))
                .state(state)
                .kpUserId(longOf(row, "kpUserID"))
                .scriptId(stringOf(row, "scriptID"))
                .ruleSetId(stringOf(row, "ruleSetID"))
                .sceneIndex(intOf(row, "sceneIndex"))
                .sceneTitle(stringOf(row, "sceneTitle"))
                .startedAt(longOf(row, "startedAt"))
                .endedAt(longOf(row, "endedAt"))
                .endReason(stringOf(row, "endReason"))
                .summary(stringOf(row, "summary"))
                .lastActivityAt(longOf(row, "lastActivityAt"))
                .aiEnabled(boolOf(row, "aiEnabled"))
                .aiSuspendedUntil(longOf(row, "aiSuspendedUntil"))
                .aiFailStreak(intOf(row, "aiFailStreak"))
                .growthSettled(boolOf(row, "growthSettled"))
                .createTime(longOf(row, "createTime"))
                .updateTime(longOf(row, "updateTime"))
                .build();
    }

    private List<SessionRow> readSessions(String sql, Object... params) {
        List<SessionRow> sessions = new ArrayList<SessionRow>();
        for (JSONObject row : rows(storage.query(sql, params))) {
            SessionRow session = readSession(row);
            if (session != null) {
                sessions.add(session);
            }
        }
        return sessions;
    }

    private MemberRow readMember(JSONObject row) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        Long id = row.getLong("id");
        if (id == null) {
            warn("成员行缺少主键，已跳过");
            return null;
        }
        MemberRow member = MemberRow.builder()
                .id(id.longValue())
                .sessionId(longOf(row, "sessionID"))
                .userId(longOf(row, "userID"))
                .cardJson(stringOf(row, "cardJSON"))
                .successSkillsJson(stringOf(row, "successSkills"))
                .joinedAt(longOf(row, "joinedAt"))
                .leftAt(longOf(row, "leftAt"))
                .takenOver(boolOf(row, "takenOver"))
                .madnessUntil(longOf(row, "madnessUntil"))
                .createTime(longOf(row, "createTime"))
                .updateTime(longOf(row, "updateTime"))
                .build();
        for (String warning : member.warnings()) {
            warn("成员 " + id + " 解析告警：" + warning);
        }
        return member;
    }

    private static long longOf(JSONObject row, String key) {
        Long value = row.getLong(key);
        return value == null ? 0L : value.longValue();
    }

    private static int intOf(JSONObject row, String key) {
        Integer value = row.getInteger(key);
        return value == null ? 0 : value.intValue();
    }

    private static boolean boolOf(JSONObject row, String key) {
        return intOf(row, key) != 0;
    }

    private static String stringOf(JSONObject row, String key) {
        String value = row.getString(key);
        return value == null ? "" : value;
    }

    private void warn(String message) {
        if (logger != null) {
            logger.sendWarn("[MBB-Trpg] " + message);
        }
    }
}
