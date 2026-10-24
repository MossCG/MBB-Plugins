package org.moboxlab.mbb.trpg.store;

import com.alibaba.fastjson.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.moboxlab.mbb.trpg.testutil.TempDirs;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 建表与补列的验收测试（规格 F1、F2、F3、F4、K1）。
 *
 * <p>全部用例连真实的临时 SQLite 文件库，不做 mock：建表、索引、补列的语义只有真库能证明。</p>
 */
public class TrpgSchemaServiceTest {

    private File dir;
    private JdbcStorageFake storage;
    private TrpgSchemaService service;

    @Before
    public void setUp() throws Exception {
        dir = TempDirs.create("mbb-trpg-schema");
        storage = new JdbcStorageFake(new File(dir, "trpg.db"));
        service = new TrpgSchemaService(storage, null);
    }

    @After
    public void tearDown() {
        if (storage != null) {
            storage.close();
        }
        TempDirs.deleteRecursively(dir);
    }

    /** F1：连续两次 ensureSchema 结果一致，五表八索引都在，且无报错。 */
    @Test
    public void f1SchemaIsIdempotent() {
        assertTrue("首次建表应成功", service.ensureSchema());
        List<JSONObject> first = schemaObjects();
        assertEquals("表数量", 5, countType(first, "table"));
        assertEquals("索引数量", 8, countType(first, "index"));
        assertTrue("首次建表不应有告警：" + service.warnings(), service.warnings().isEmpty());

        assertTrue("第二次建表应成功", service.ensureSchema());
        List<JSONObject> second = schemaObjects();
        assertEquals("第二次表数量", 5, countType(second, "table"));
        assertEquals("第二次索引数量", 8, countType(second, "index"));
        assertTrue("第二次建表不应有告警：" + service.warnings(), service.warnings().isEmpty());
    }

    /** F2：删掉一列后启动，PRAGMA 判重补回该列，已有数据不丢。 */
    @Test
    public void f2RestoresDroppedColumnWithoutLosingData() {
        assertTrue(service.ensureSchema());
        storage.insert("INSERT INTO plugin_mbb_trpg_session "
                        + "(groupID, state, kpUserID, createTime, updateTime) VALUES (?,?,?,?,?)",
                1001L, "RECRUITING", 7L, 111L, 222L);

        storage.update("ALTER TABLE plugin_mbb_trpg_session DROP COLUMN sceneTitle");
        assertFalse("列应已被删掉", columnExists("plugin_mbb_trpg_session", "sceneTitle"));

        assertTrue("补列后 ensureSchema 应成功", service.ensureSchema());
        assertTrue("sceneTitle 应被补回", columnExists("plugin_mbb_trpg_session", "sceneTitle"));

        JSONObject row = storage.queryOne(
                "SELECT groupID, sceneTitle FROM plugin_mbb_trpg_session WHERE groupID = ?", 1001L);
        assertNotNull("原有数据行不应丢失", row);
        assertEquals(1001L, row.getLongValue("groupID"));
        assertEquals("补列后新列取常量默认值", "", row.getString("sceneTitle"));
        assertTrue("补列应留告警：" + service.warnings(),
                containsWarning(service.warnings(), "缺少列"));
    }

    /** F3：建表失败（update 返回 -1）时返回 false，不 DROP、不重建，已有表与数据保留。 */
    @Test
    public void f3CreateFailureDisablesWithoutDropping() {
        assertTrue(service.ensureSchema());
        storage.insert("INSERT INTO plugin_mbb_trpg_session "
                        + "(groupID, state, kpUserID, createTime, updateTime) VALUES (?,?,?,?,?)",
                2002L, "RUNNING", 8L, 10L, 20L);

        storage.failNextUpdate(1);
        TrpgSchemaService failing = new TrpgSchemaService(storage, null);
        assertFalse("建表返回 -1 时应判定失败", failing.ensureSchema());
        assertFalse("失败必须留告警", failing.warnings().isEmpty());

        assertEquals("已有数据必须保留", 1L,
                storage.count("SELECT COUNT(*) FROM plugin_mbb_trpg_session WHERE groupID = ?", 2002L));
        assertEquals("表必须还在", 5, countType(schemaObjects(), "table"));
        for (String sql : storage.executedSql()) {
            assertFalse("建表中不得出现 DROP：" + sql, sql.toLowerCase().contains("drop "));
        }
    }

    /** F4：PRAGMA 读不到列（异常被吞成空结果）时只告警并跳过补列，不做盲改。 */
    @Test
    public void f4PragmaFailureSkipsAlterInsteadOfBlindChange() {
        storage.failNextQuery(1);
        assertTrue("PRAGMA 失败不应判定建表失败", service.ensureSchema());
        assertTrue("应告警跳过补列：" + service.warnings(),
                containsWarning(service.warnings(), "表结构为空"));
        for (String sql : storage.executedSql()) {
            assertFalse("不得盲改表结构：" + sql, sql.toUpperCase().startsWith("ALTER TABLE"));
        }
        assertEquals("五张表仍应建成", 5, countType(schemaObjects(), "table"));
    }

    /** F4：PRAGMA 只读到部分行时重读合并，不得对已存在列发 ALTER，也不得因此禁用 TRPG。 */
    @Test
    public void f4PartialPragmaRowsDoNotTriggerAlterOrDisable() {
        assertTrue(service.ensureSchema());
        int before = storage.executedSql().size();

        storage.partialNextQuery(3);
        assertTrue("部分行不应判定建表失败", service.ensureSchema());
        for (int i = before; i < storage.executedSql().size(); i++) {
            String sql = storage.executedSql().get(i);
            assertFalse("不得对已存在列补列：" + sql,
                    sql.toUpperCase().startsWith("ALTER TABLE"));
        }
        assertEquals("五张表仍应建成", 5, countType(schemaObjects(), "table"));
    }

    /** 补列 SQL 必须同时带列名与常量默认值，且不包含主键（主键无法补）。 */
    @Test
    public void allSqlAltersCarryColumnNameAndDefault() {
        int alterCount = 0;
        for (String sql : TrpgSchemaService.allSql()) {
            if (!sql.toUpperCase().startsWith("ALTER TABLE")) {
                continue;
            }
            alterCount++;
            assertTrue("补列必须带列名与类型：" + sql, sql.matches(
                    "ALTER TABLE plugin_mbb_trpg_\\w+ ADD COLUMN \\w+ (INTEGER|TEXT).*"));
            assertFalse("主键无法补列：" + sql, sql.contains("AUTOINCREMENT"));
        }
        int expected = 0;
        for (Map<String, String> columns : TrpgSchemaService.expectedColumns().values()) {
            expected += columns.size() - 1;
        }
        assertEquals("每个非主键列一条补列语句", expected, alterCount);
    }

    /** 活跃局唯一索引在真库上确实生效（同群不能有两条未归档局）。 */
    @Test
    public void activeSessionUniqueIndexIsEnforced() {
        assertTrue(service.ensureSchema());
        assertEquals("第一条未归档局应写入成功", 1L,
                storage.insert("INSERT INTO plugin_mbb_trpg_session "
                                + "(groupID, state, kpUserID, createTime, updateTime) VALUES (?,?,?,?,?)",
                        3003L, "RECRUITING", 9L, 1L, 1L));
        // 唯一索引冲突在主程序语义下被吞成插入失败（0），不会抛异常
        assertEquals("同群第二条未归档局应被唯一索引挡下", 0L,
                storage.insert("INSERT INTO plugin_mbb_trpg_session "
                                + "(groupID, state, kpUserID, createTime, updateTime) VALUES (?,?,?,?,?)",
                        3003L, "RUNNING", 9L, 2L, 2L));
        assertEquals("冲突后仍只有一条未归档局", 1L,
                storage.count("SELECT COUNT(*) FROM plugin_mbb_trpg_session "
                        + "WHERE groupID = ? AND state IN ('RECRUITING','RUNNING')", 3003L));
        // 索引只约束未归档局：已结束的同群局可以并存
        assertTrue("已结束局不受唯一索引限制",
                storage.insert("INSERT INTO plugin_mbb_trpg_session "
                                + "(groupID, state, kpUserID, endedAt, createTime, updateTime) "
                                + "VALUES (?,?,?,?,?,?)",
                        3003L, "ENDED", 9L, 5L, 3L, 3L) > 0L);
    }

    /** K1：全部 SQL 只碰 plugin_ 前缀表，不含 bot_，且不含删除表结构。 */
    @Test
    public void k1AllSqlStayInPluginScope() {
        List<String> all = TrpgSchemaService.allSql();
        assertFalse("应能取到 SQL", all.isEmpty());
        for (String sql : all) {
            String lower = sql.toLowerCase();
            assertTrue("SQL 必须含 plugin_：" + sql, lower.contains("plugin_"));
            assertFalse("SQL 不得含 bot_：" + sql, lower.contains("bot_"));
            assertFalse("SQL 不得删表：" + sql, lower.startsWith("drop "));
        }
        assertEquals("五张表", 5, TrpgSchemaService.tableNames().size());
        assertEquals("八个索引", 8, TrpgSchemaService.createIndexSql().size());
        assertEquals("五条建表语句", 5, TrpgSchemaService.createTableSql().size());
        for (String table : TrpgSchemaService.tableNames()) {
            assertTrue("表名必须以 plugin_mbb_trpg_ 开头：" + table,
                    table.startsWith("plugin_mbb_trpg_"));
        }
    }

    private List<JSONObject> schemaObjects() {
        // 表名以 plugin_mbb_trpg_ 开头，索引名以 ux_trpg_/ix_trpg_ 开头，统一用 trpg 过滤
        return storage.query("SELECT type, name FROM sqlite_master "
                + "WHERE type IN ('table','index') AND name LIKE '%trpg%'");
    }

    private int countType(List<JSONObject> rows, String type) {
        int count = 0;
        for (JSONObject row : rows) {
            if (row != null && type.equals(row.getString("type"))) {
                count++;
            }
        }
        return count;
    }

    private boolean columnExists(String table, String column) {
        List<JSONObject> rows = storage.query("PRAGMA table_info(" + table + ")");
        for (JSONObject row : rows) {
            if (row != null && column.equals(row.getString("name"))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsWarning(List<String> warnings, String keyword) {
        for (String warning : warnings) {
            if (warning.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

}
