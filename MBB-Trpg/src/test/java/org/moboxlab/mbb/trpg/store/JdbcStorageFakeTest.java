package org.moboxlab.mbb.trpg.store;

import com.alibaba.fastjson.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.moboxlab.mbb.trpg.testutil.TempDirs;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** 测试基座自身的契约：失败语义必须与主程序 StorageService 一致，否则上层测试结论不可信。 */
public class JdbcStorageFakeTest {

    private File dir;
    private JdbcStorageFake storage;

    @Before
    public void setUp() throws Exception {
        dir = TempDirs.create("mbb-trpg-fake");
        storage = new JdbcStorageFake(new File(dir, "fake.db"));
        storage.update("CREATE TABLE plugin_mbb_trpg_probe (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL)");
    }

    @After
    public void tearDown() {
        if (storage != null) {
            storage.close();
        }
        TempDirs.deleteRecursively(dir);
    }

    /** update 的 -1 与 0 必须可区分：-1 表示语句未执行，0 表示无匹配行。 */
    @Test
    public void updateDistinguishesNotExecutedFromNoMatch() {
        storage.failNextUpdate(1);
        assertEquals("注入失败应返回 -1", -1, storage.update("UPDATE plugin_mbb_trpg_probe SET name = ? WHERE id = ?", "x", 1L));
        assertEquals("无匹配行应返回 0", 0, storage.update("UPDATE plugin_mbb_trpg_probe SET name = ? WHERE id = ?", "x", 999L));

        long id = storage.insert("INSERT INTO plugin_mbb_trpg_probe (name) VALUES (?)", "a");
        assertEquals("插入应返回自增主键", 1L, id);
        assertEquals("命中一行应返回 1", 1, storage.update("UPDATE plugin_mbb_trpg_probe SET name = ? WHERE id = ?", "b", id));
    }

    /** insert 取键失败返回 0。 */
    @Test
    public void insertReturnsZeroWhenInjected() {
        storage.failNextInsert(1);
        assertEquals(0L, storage.insert("INSERT INTO plugin_mbb_trpg_probe (name) VALUES (?)", "a"));
        assertEquals("注入后仍可正常插入", 1L, storage.insert("INSERT INTO plugin_mbb_trpg_probe (name) VALUES (?)", "b"));
    }

    /** query 异常被吞成部分行，不向外抛。 */
    @Test
    public void queryReturnsPartialRowsInsteadOfThrowing() {
        storage.insert("INSERT INTO plugin_mbb_trpg_probe (name) VALUES (?)", "a");
        storage.insert("INSERT INTO plugin_mbb_trpg_probe (name) VALUES (?)", "b");
        storage.insert("INSERT INTO plugin_mbb_trpg_probe (name) VALUES (?)", "c");

        storage.partialNextQuery(2);
        List<JSONObject> rows = storage.query("SELECT id, name FROM plugin_mbb_trpg_probe ORDER BY id");
        assertEquals("只应返回已读到的两行", 2, rows.size());
        assertEquals("a", rows.get(0).getString("name"));

        assertEquals("下一次查询恢复完整结果", 3, storage.query("SELECT id FROM plugin_mbb_trpg_probe").size());
    }

    /** 注入的查询失败只返回空结果，不抛异常。 */
    @Test
    public void injectedQueryFailureReturnsEmptyList() {
        storage.failNextQuery(1);
        List<JSONObject> rows = storage.query("SELECT id FROM plugin_mbb_trpg_probe");
        assertTrue(rows.isEmpty());
        assertNull(storage.queryOne("SELECT id FROM plugin_mbb_trpg_probe"));
    }

    /** 坏 SQL 也只返回空结果，与主程序语义一致。 */
    @Test
    public void brokenQueryDoesNotThrow() {
        List<JSONObject> rows = storage.query("SELECT * FROM plugin_mbb_trpg_missing_table");
        assertTrue(rows.isEmpty());
    }

    /** 坏 SQL 只返回失败码，不抛异常，与主程序吞异常语义一致。 */
    @Test
    public void brokenStatementsReturnFailureCodesInsteadOfThrowing() {
        assertEquals("语句未执行应返回 -1", -1,
                storage.update("UPDATE plugin_mbb_trpg_missing SET name = ?", "x"));
        assertEquals("插入失败应返回 0", 0L,
                storage.insert("INSERT INTO plugin_mbb_trpg_missing (name) VALUES (?)", "x"));
    }

    /** 插件键值存储不参与测试，直接拒绝。 */
    @Test
    public void keyValueApiIsRejected() {
        try {
            storage.get(null, "k");
            fail("get 应拒绝");
        } catch (UnsupportedOperationException expected) {
            assertFalse(expected.getMessage().isEmpty());
        }
    }

}
