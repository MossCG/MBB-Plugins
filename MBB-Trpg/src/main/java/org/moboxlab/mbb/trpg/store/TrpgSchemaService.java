package org.moboxlab.mbb.trpg.store;

import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.PluginLogger;
import org.moboxlab.moboxbot.API.Storage.StorageService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 建表、补列与 SQL 边界自检。
 *
 * <p>幂等：没有 schema 版本号，每次插件启用都全量执行一遍 CREATE TABLE / INDEX IF NOT EXISTS。
 * 失败处理：任何语句返回 -1 视为建表失败，插件禁用 TRPG 能力；绝不 DROP、绝不重建。
 * 补列：先用 PRAGMA table_info 读现有列，判重后再 ALTER TABLE ADD COLUMN，PRAGMA
 * 失败或返回空结果时不做盲改。</p>
 */
public final class TrpgSchemaService {

    /** 局面表。 */
    public static final String TABLE_SESSION = "plugin_mbb_trpg_session";
    /** 成员表。 */
    public static final String TABLE_MEMBER = "plugin_mbb_trpg_member";
    /** 文本日志表。 */
    public static final String TABLE_TEXT_LOG = "plugin_mbb_trpg_text_log";
    /** 黑名单表。 */
    public static final String TABLE_BLACKLIST = "plugin_mbb_trpg_blacklist";
    /** AI 审计表。 */
    public static final String TABLE_AI_TRACE = "plugin_mbb_trpg_ai_trace";

    /** 主键列名；主键无法通过 ALTER TABLE 补列。 */
    private static final String PRIMARY_KEY_COLUMN = "id";

    private static final List<String> CREATE_TABLE_SQL;
    private static final List<String> CREATE_INDEX_SQL;

    /**
     * 各表列定义，供补列使用。
     *
     * <p>冻结 DDL（规格 §7.2）里部分 NOT NULL 列没有 DEFAULT，写入时显式给值；
     * 但 SQLite 的 ALTER TABLE ADD COLUMN 对 NOT NULL 列强制要求常量默认值，
     * 迁移老库只能用 0 / '' 补齐。这是 §7.2 与 SQLite 语法的折中：新库以
     * CREATE TABLE 为准，迁移库补上的列会带默认值。</p>
     */
    private static final Map<String, Map<String, String>> COLUMNS;

    static {
        List<String> tables = new ArrayList<String>();
        tables.add("CREATE TABLE IF NOT EXISTS " + TABLE_SESSION + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "groupID INTEGER NOT NULL,"
                + "state TEXT NOT NULL,"
                + "kpUserID INTEGER NOT NULL,"
                + "scriptID TEXT NOT NULL DEFAULT '',"
                + "ruleSetID TEXT NOT NULL DEFAULT 'd100',"
                + "sceneIndex INTEGER NOT NULL DEFAULT 0,"
                + "sceneTitle TEXT NOT NULL DEFAULT '',"
                + "startedAt INTEGER NOT NULL DEFAULT 0,"
                + "endedAt INTEGER NOT NULL DEFAULT 0,"
                + "endReason TEXT NOT NULL DEFAULT '',"
                + "summary TEXT NOT NULL DEFAULT '',"
                + "lastActivityAt INTEGER NOT NULL DEFAULT 0,"
                + "aiEnabled INTEGER NOT NULL DEFAULT 0,"
                + "aiSuspendedUntil INTEGER NOT NULL DEFAULT 0,"
                + "aiFailStreak INTEGER NOT NULL DEFAULT 0,"
                + "growthSettled INTEGER NOT NULL DEFAULT 0,"
                + "createTime INTEGER NOT NULL,"
                + "updateTime INTEGER NOT NULL"
                + ")");
        tables.add("CREATE TABLE IF NOT EXISTS " + TABLE_MEMBER + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "sessionID INTEGER NOT NULL,"
                + "userID INTEGER NOT NULL,"
                + "cardJSON TEXT NOT NULL DEFAULT '{}',"
                + "successSkills TEXT NOT NULL DEFAULT '[]',"
                + "joinedAt INTEGER NOT NULL DEFAULT 0,"
                + "leftAt INTEGER NOT NULL DEFAULT 0,"
                + "takenOver INTEGER NOT NULL DEFAULT 0,"
                + "madnessUntil INTEGER NOT NULL DEFAULT 0,"
                + "createTime INTEGER NOT NULL,"
                + "updateTime INTEGER NOT NULL"
                + ")");
        tables.add("CREATE TABLE IF NOT EXISTS " + TABLE_TEXT_LOG + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "sessionID INTEGER NOT NULL,"
                + "seq INTEGER NOT NULL,"
                + "eventType TEXT NOT NULL,"
                + "actorID INTEGER NOT NULL DEFAULT 0,"
                + "content TEXT NOT NULL DEFAULT '',"
                + "createTime INTEGER NOT NULL"
                + ")");
        tables.add("CREATE TABLE IF NOT EXISTS " + TABLE_BLACKLIST + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "groupID INTEGER NOT NULL,"
                + "userID INTEGER NOT NULL,"
                + "operatorID INTEGER NOT NULL DEFAULT 0,"
                + "reason TEXT NOT NULL DEFAULT '',"
                + "createTime INTEGER NOT NULL"
                + ")");
        tables.add("CREATE TABLE IF NOT EXISTS " + TABLE_AI_TRACE + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "groupID INTEGER NOT NULL,"
                + "sessionID INTEGER NOT NULL DEFAULT 0,"
                + "eventSeq INTEGER NOT NULL DEFAULT 0,"
                + "triggerType TEXT NOT NULL,"
                + "resultStatus TEXT NOT NULL,"
                + "degradeReason TEXT NOT NULL DEFAULT '',"
                + "errorType TEXT NOT NULL DEFAULT '',"
                + "attempts INTEGER NOT NULL DEFAULT 1,"
                + "elapsedMs INTEGER NOT NULL DEFAULT 0,"
                + "finishReason TEXT NOT NULL DEFAULT '',"
                + "usageJSON TEXT NOT NULL DEFAULT '',"
                + "cached INTEGER NOT NULL DEFAULT 0,"
                + "preview TEXT NOT NULL DEFAULT '',"
                + "createTime INTEGER NOT NULL"
                + ")");
        CREATE_TABLE_SQL = Collections.unmodifiableList(tables);

        List<String> indexes = new ArrayList<String>();
        indexes.add("CREATE UNIQUE INDEX IF NOT EXISTS ux_trpg_session_active ON "
                + TABLE_SESSION + "(groupID) WHERE state IN ('RECRUITING','RUNNING')");
        indexes.add("CREATE INDEX IF NOT EXISTS ix_trpg_session_cleanup ON "
                + TABLE_SESSION + "(state, endedAt)");
        indexes.add("CREATE INDEX IF NOT EXISTS ix_trpg_session_group ON "
                + TABLE_SESSION + "(groupID, state, endedAt)");
        indexes.add("CREATE UNIQUE INDEX IF NOT EXISTS ux_trpg_member ON "
                + TABLE_MEMBER + "(sessionID, userID)");
        indexes.add("CREATE UNIQUE INDEX IF NOT EXISTS ux_trpg_log_seq ON "
                + TABLE_TEXT_LOG + "(sessionID, seq)");
        indexes.add("CREATE UNIQUE INDEX IF NOT EXISTS ux_trpg_blacklist ON "
                + TABLE_BLACKLIST + "(groupID, userID)");
        indexes.add("CREATE INDEX IF NOT EXISTS ix_trpg_trace_session ON "
                + TABLE_AI_TRACE + "(sessionID, id)");
        indexes.add("CREATE INDEX IF NOT EXISTS ix_trpg_trace_cleanup ON "
                + TABLE_AI_TRACE + "(createTime)");
        CREATE_INDEX_SQL = Collections.unmodifiableList(indexes);

        Map<String, Map<String, String>> columns = new LinkedHashMap<String, Map<String, String>>();
        columns.put(TABLE_SESSION, columns(
                "id", "INTEGER PRIMARY KEY AUTOINCREMENT",
                "groupID", "INTEGER NOT NULL DEFAULT 0",
                "state", "TEXT NOT NULL DEFAULT ''",
                "kpUserID", "INTEGER NOT NULL DEFAULT 0",
                "scriptID", "TEXT NOT NULL DEFAULT ''",
                "ruleSetID", "TEXT NOT NULL DEFAULT 'd100'",
                "sceneIndex", "INTEGER NOT NULL DEFAULT 0",
                "sceneTitle", "TEXT NOT NULL DEFAULT ''",
                "startedAt", "INTEGER NOT NULL DEFAULT 0",
                "endedAt", "INTEGER NOT NULL DEFAULT 0",
                "endReason", "TEXT NOT NULL DEFAULT ''",
                "summary", "TEXT NOT NULL DEFAULT ''",
                "lastActivityAt", "INTEGER NOT NULL DEFAULT 0",
                "aiEnabled", "INTEGER NOT NULL DEFAULT 0",
                "aiSuspendedUntil", "INTEGER NOT NULL DEFAULT 0",
                "aiFailStreak", "INTEGER NOT NULL DEFAULT 0",
                "growthSettled", "INTEGER NOT NULL DEFAULT 0",
                "createTime", "INTEGER NOT NULL DEFAULT 0",
                "updateTime", "INTEGER NOT NULL DEFAULT 0"));
        columns.put(TABLE_MEMBER, columns(
                "id", "INTEGER PRIMARY KEY AUTOINCREMENT",
                "sessionID", "INTEGER NOT NULL DEFAULT 0",
                "userID", "INTEGER NOT NULL DEFAULT 0",
                "cardJSON", "TEXT NOT NULL DEFAULT '{}'",
                "successSkills", "TEXT NOT NULL DEFAULT '[]'",
                "joinedAt", "INTEGER NOT NULL DEFAULT 0",
                "leftAt", "INTEGER NOT NULL DEFAULT 0",
                "takenOver", "INTEGER NOT NULL DEFAULT 0",
                "madnessUntil", "INTEGER NOT NULL DEFAULT 0",
                "createTime", "INTEGER NOT NULL DEFAULT 0",
                "updateTime", "INTEGER NOT NULL DEFAULT 0"));
        columns.put(TABLE_TEXT_LOG, columns(
                "id", "INTEGER PRIMARY KEY AUTOINCREMENT",
                "sessionID", "INTEGER NOT NULL DEFAULT 0",
                "seq", "INTEGER NOT NULL DEFAULT 0",
                "eventType", "TEXT NOT NULL DEFAULT ''",
                "actorID", "INTEGER NOT NULL DEFAULT 0",
                "content", "TEXT NOT NULL DEFAULT ''",
                "createTime", "INTEGER NOT NULL DEFAULT 0"));
        columns.put(TABLE_BLACKLIST, columns(
                "id", "INTEGER PRIMARY KEY AUTOINCREMENT",
                "groupID", "INTEGER NOT NULL DEFAULT 0",
                "userID", "INTEGER NOT NULL DEFAULT 0",
                "operatorID", "INTEGER NOT NULL DEFAULT 0",
                "reason", "TEXT NOT NULL DEFAULT ''",
                "createTime", "INTEGER NOT NULL DEFAULT 0"));
        columns.put(TABLE_AI_TRACE, columns(
                "id", "INTEGER PRIMARY KEY AUTOINCREMENT",
                "groupID", "INTEGER NOT NULL DEFAULT 0",
                "sessionID", "INTEGER NOT NULL DEFAULT 0",
                "eventSeq", "INTEGER NOT NULL DEFAULT 0",
                "triggerType", "TEXT NOT NULL DEFAULT ''",
                "resultStatus", "TEXT NOT NULL DEFAULT ''",
                "degradeReason", "TEXT NOT NULL DEFAULT ''",
                "errorType", "TEXT NOT NULL DEFAULT ''",
                "attempts", "INTEGER NOT NULL DEFAULT 1",
                "elapsedMs", "INTEGER NOT NULL DEFAULT 0",
                "finishReason", "TEXT NOT NULL DEFAULT ''",
                "usageJSON", "TEXT NOT NULL DEFAULT ''",
                "cached", "INTEGER NOT NULL DEFAULT 0",
                "preview", "TEXT NOT NULL DEFAULT ''",
                "createTime", "INTEGER NOT NULL DEFAULT 0"));
        COLUMNS = Collections.unmodifiableMap(columns);
    }

    private final StorageService storage;
    private final PluginLogger logger;
    private final List<String> warnings = new ArrayList<String>();

    public TrpgSchemaService(StorageService storage, PluginLogger logger) {
        this.storage = storage;
        this.logger = logger;
        if (storage == null) {
            warn("存储服务不可用，无法建表");
        }
        for (String sql : allSql()) {
            if (!isPluginScopedSql(sql)) {
                warn("SQL 越界（必须含 plugin_ 且不得含 bot_）：" + sql);
            }
        }
    }

    private static Map<String, String> columns(String... entries) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        for (int i = 0; i + 1 < entries.length; i += 2) {
            map.put(entries[i], entries[i + 1]);
        }
        return Collections.unmodifiableMap(map);
    }

    /** 五张表名。 */
    public static List<String> tableNames() {
        return Collections.unmodifiableList(Arrays.asList(TABLE_SESSION, TABLE_MEMBER,
                TABLE_TEXT_LOG, TABLE_BLACKLIST, TABLE_AI_TRACE));
    }

    /** 建表语句（只读，供测试与自检）。 */
    public static List<String> createTableSql() {
        return CREATE_TABLE_SQL;
    }

    /** 建索引语句（只读，供测试与自检）。 */
    public static List<String> createIndexSql() {
        return CREATE_INDEX_SQL;
    }

    /** 各表期望列与补列定义（只读，供测试与自检）。 */
    public static Map<String, Map<String, String>> expectedColumns() {
        return COLUMNS;
    }

    /**
     * 执行一次幂等建表与补列。
     *
     * @return true 表示五表八索引就绪；false 表示有语句未执行（插件应禁用 TRPG）
     */
    public boolean ensureSchema() {
        warnings.clear();
        if (storage == null) {
            warn("存储服务不可用，跳过建表");
            return false;
        }
        for (String sql : CREATE_TABLE_SQL) {
            if (!executeDdl(sql, "建表")) {
                return false;
            }
        }
        for (String table : tableNames()) {
            if (!ensureColumns(table)) {
                return false;
            }
        }
        for (String sql : CREATE_INDEX_SQL) {
            if (!executeDdl(sql, "建索引")) {
                return false;
            }
        }
        return true;
    }

    private boolean executeDdl(String sql, String action) {
        int result;
        try {
            result = storage.update(sql);
        } catch (RuntimeException e) {
            warn(action + "异常：" + e.getMessage());
            return false;
        }
        if (result == -1) {
            warn(action + "失败（返回 -1）：" + sql);
            return false;
        }
        return true;
    }

    private boolean ensureColumns(String table) {
        Map<String, String> expected = COLUMNS.get(table);
        if (expected == null) {
            return true;
        }
        Set<String> existing = readColumns(table);
        if (existing == null) {
            // 读失败或表结构为空：只告警，不做盲改（F4）
            return true;
        }
        if (!existing.containsAll(expected.keySet())) {
            // StorageService 的 query 失败会被吞成部分行，缺列可能只是读到一半；
            // 重读一次并合并结果，仍缺的才当成真的缺列，避免对已存在列发 ALTER
            Set<String> reread = readColumns(table);
            if (reread == null) {
                warn("表结构读取不完整，跳过补列（不做盲改）：" + table);
                return true;
            }
            existing.addAll(reread);
        }
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            if (existing.contains(entry.getKey())) {
                continue;
            }
            if (PRIMARY_KEY_COLUMN.equals(entry.getKey())) {
                // 主键列无法用 ALTER TABLE 补，缺 id 说明表已被外部破坏，只告警不阻断
                warn("表 " + table + " 缺少主键列 " + PRIMARY_KEY_COLUMN + "，无法补列");
                continue;
            }
            String sql = addColumnSql(table, entry.getKey(), entry.getValue());
            warn("表 " + table + " 缺少列 " + entry.getKey() + "，尝试补列");
            if (!executeDdl(sql, "补列")) {
                // 补列失败常见原因是列其实已存在（重复列）或数据库忙；表本身可用，
                // 不据此禁用整块 TRPG 能力，只保留告警等下次启用重试
                warn("补列失败，已跳过；本次不因补列失败禁用 TRPG：" + entry.getKey());
            }
        }
        return true;
    }

    /** 读 PRAGMA table_info；读失败或结果为空时返回 null，由调用方只告警。 */
    private Set<String> readColumns(String table) {
        List<JSONObject> rows;
        try {
            rows = storage.query("PRAGMA table_info(" + table + ")");
        } catch (RuntimeException e) {
            warn("读取表结构失败，跳过补列：" + table + "，" + e.getMessage());
            return null;
        }
        if (rows == null || rows.isEmpty()) {
            warn("表结构为空，跳过补列（不做盲改）：" + table);
            return null;
        }
        Set<String> existing = new LinkedHashSet<String>();
        for (JSONObject row : rows) {
            if (row == null) {
                continue;
            }
            String name = row.getString("name");
            if (name != null && !name.trim().isEmpty()) {
                existing.add(name);
            }
        }
        if (existing.isEmpty()) {
            warn("表结构缺少列名，跳过补列（不做盲改）：" + table);
            return null;
        }
        return existing;
    }

    /** 全部 SQL，用于边界自检；与真正可能执行的语句一一对应。 */
    public static List<String> allSql() {
        List<String> all = new ArrayList<String>();
        all.addAll(CREATE_TABLE_SQL);
        all.addAll(CREATE_INDEX_SQL);
        for (Map.Entry<String, Map<String, String>> table : COLUMNS.entrySet()) {
            for (Map.Entry<String, String> column : table.getValue().entrySet()) {
                if (PRIMARY_KEY_COLUMN.equals(column.getKey())) {
                    continue;
                }
                all.add(addColumnSql(table.getKey(), column.getKey(), column.getValue()));
            }
        }
        return all;
    }

    /** 补列语句：必须同时带列名与常量默认值。 */
    private static String addColumnSql(String table, String column, String definition) {
        return "ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition;
    }

    /** 插件 SQL 边界：必须含 plugin_，且不得含 bot_。 */
    public static boolean isPluginScopedSql(String sql) {
        if (sql == null) {
            return false;
        }
        String lower = sql.toLowerCase();
        return lower.contains("plugin_") && !lower.contains("bot_");
    }

    /** 本次 ensureSchema 的告警。 */
    public List<String> warnings() {
        return Collections.unmodifiableList(warnings);
    }

    private void warn(String message) {
        warnings.add(message);
        if (logger != null) {
            logger.sendWarn("[MBB-Trpg] " + message);
        }
    }
}
