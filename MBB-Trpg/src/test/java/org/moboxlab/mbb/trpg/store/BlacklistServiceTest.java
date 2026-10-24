package org.moboxlab.mbb.trpg.store;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.moboxlab.mbb.trpg.testutil.TempDirs;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** {@link BlacklistService} 语义测试：重复拉黑收敛为一行、名单不随局清理。 */
public class BlacklistServiceTest {

    private File tempDir;
    private JdbcStorageFake storage;
    private SqlSessionStore store;
    private BlacklistService service;

    @Before
    public void setUp() {
        tempDir = TempDirs.create("mbb-trpg-blacklist");
        storage = new JdbcStorageFake(new File(tempDir, "trpg.db"));
        new TrpgSchemaService(storage, null).ensureSchema();
        store = new SqlSessionStore(storage, null);
        service = new BlacklistService(store);
    }

    @After
    public void tearDown() {
        storage.close();
        TempDirs.deleteRecursively(tempDir);
    }

    @Test
    public void addIsIdempotentAndReportsSuccessForRepeat() {
        assertTrue(service.add(7L, 42L, 1L, "刷屏", 100L));
        assertTrue("重复拉黑也应视为成功", service.add(7L, 42L, 2L, "再次举报", 200L));

        List<BlacklistRow> rows = service.list(7L);
        assertEquals("唯一约束应把重复拉黑收敛为一行", 1, rows.size());
        assertEquals("保留首次写入的原因", "刷屏", rows.get(0).reason());
        assertTrue(service.contains(7L, 42L));
        assertFalse("其他群不受影响", service.contains(8L, 42L));
    }

    @Test
    public void addFallsBackToReadBackWhenInsertReturnsZero() {
        storage.failNextInsert(1);
        assertFalse("插入失败且回读为空时应返回失败", service.add(7L, 42L, 1L, "刷屏", 100L));

        assertTrue(service.add(7L, 42L, 1L, "刷屏", 100L));
        storage.failNextInsert(1);
        assertTrue("插入返回 0 但行已存在时按成功处理", service.add(7L, 42L, 1L, "刷屏", 200L));
        assertEquals(1, service.list(7L).size());
    }

    @Test
    public void blankReasonIsStoredAsEmptyString() {
        assertTrue(service.add(7L, 42L, 1L, null, 100L));
        assertEquals("", service.list(7L).get(0).reason());
    }

    @Test
    public void removeIsIdempotent() {
        service.add(7L, 42L, 1L, "刷屏", 100L);
        assertTrue(service.remove(7L, 42L));
        assertFalse("重复解除返回失败", service.remove(7L, 42L));
        assertFalse(service.contains(7L, 42L));
        assertTrue("解除后可以重新拉黑", service.add(7L, 42L, 1L, "再犯", 300L));
        assertEquals(1, service.list(7L).size());
    }

    @Test
    public void emptyListIsEmptyNotNil() {
        assertTrue(service.list(9L).isEmpty());
    }

}
