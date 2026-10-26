package org.moboxlab.mbb.trpg.store;

import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Plugin;
import org.moboxlab.moboxbot.API.Storage.StorageService;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 测试用存储实现：直连一个真实的 SQLite 文件库。
 *
 * <p>复刻主程序 StorageService 的失败语义：update 返回 -1 表示语句未执行、0 表示无匹配行；
 * insert 返回 0 表示没插入或取键失败；query 异常时只返回已读到的部分行；异常不向外抛。
 * 另提供故障注入，用于验证插件的降级路径。</p>
 *
 * <p>只实现插件真正使用的方法；插件键值存储（get/set/remove）直接拒绝。</p>
 */
public final class JdbcStorageFake implements StorageService {

    private final File dbFile;
    private Connection connection;
    private final List<String> executedSql = new ArrayList<String>();
    private int failNextUpdate;
    private String failUpdateFragment = "";
    private int failUpdateFragmentTimes;
    private int failNextInsert;
    private int failNextQuery;
    private int partialQueryRows;

    public JdbcStorageFake(File dbFile) {
        this.dbFile = dbFile;
        try {
            Class.forName("org.sqlite.JDBC");
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("找不到 SQLite 驱动：org.sqlite.JDBC", e);
        } catch (SQLException e) {
            throw new IllegalStateException("打开测试数据库失败：" + dbFile.getAbsolutePath(), e);
        }
    }

    /** 让接下来的若干次 update 返回 -1（语句未执行）。 */
    public void failNextUpdate(int times) {
        this.failNextUpdate = times;
    }

    /**
     * 让接下来若干次「SQL 文本包含指定片段」的 update 返回 -1。
     *
     * <p>用于只打中某类语句（例如只让 CAS 迁移失败、或只让成员写回失败）。</p>
     */
    public void failUpdateContaining(String fragment, int times) {
        if (fragment == null || fragment.isEmpty()) {
            throw new IllegalArgumentException("SQL 片段不能为空");
        }
        this.failUpdateFragment = fragment;
        this.failUpdateFragmentTimes = times;
    }

    /** 让接下来的若干次 insert 返回 0（没插入或取键失败）。 */
    public void failNextInsert(int times) {
        this.failNextInsert = times;
    }

    /** 让接下来的若干次 query 返回空结果（模拟驱动异常被主程序吞掉）。 */
    public void failNextQuery(int times) {
        this.failNextQuery = times;
    }

    /** 让下一次 query 只返回前 rows 行（模拟异常被吞掉、只读到部分行）。 */
    public void partialNextQuery(int rows) {
        this.partialQueryRows = rows;
    }

    /** 已执行过的 SQL 文本，按执行顺序。 */
    public List<String> executedSql() {
        return Collections.unmodifiableList(executedSql);
    }

    /** 直接拿连接做断言，例如 PRAGMA 检查。 */
    public Connection connection() {
        return connection;
    }

    @Override
    public String get(Plugin plugin, String key) {
        throw new UnsupportedOperationException("测试实现不支持插件键值读取");
    }

    @Override
    public void set(Plugin plugin, String key, String value) {
        throw new UnsupportedOperationException("测试实现不支持插件键值写入");
    }

    @Override
    public void remove(Plugin plugin, String key) {
        throw new UnsupportedOperationException("测试实现不支持插件键值删除");
    }

    @Override
    public List<JSONObject> query(String sql, Object... params) {
        executedSql.add(sql);
        List<JSONObject> rows = new ArrayList<JSONObject>();
        if (failNextQuery > 0) {
            failNextQuery--;
            return rows;
        }
        try {
            PreparedStatement statement = connection.prepareStatement(sql);
            try {
                bind(statement, params);
                ResultSet resultSet = statement.executeQuery();
                try {
                    ResultSetMetaData meta = resultSet.getMetaData();
                    int columns = meta.getColumnCount();
                    while (resultSet.next()) {
                        if (partialQueryRows > 0 && rows.size() >= partialQueryRows) {
                            break;
                        }
                        JSONObject row = new JSONObject();
                        for (int i = 1; i <= columns; i++) {
                            row.put(meta.getColumnLabel(i), readValue(resultSet, i));
                        }
                        rows.add(row);
                    }
                } finally {
                    resultSet.close();
                }
            } finally {
                statement.close();
            }
        } catch (SQLException e) {
            // 主程序把异常吞成已读到的部分行，这里保持一致
            return rows;
        } finally {
            if (partialQueryRows > 0) {
                partialQueryRows = 0;
            }
        }
        return rows;
    }

    @Override
    public JSONObject queryOne(String sql, Object... params) {
        List<JSONObject> rows = query(sql, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public int update(String sql, Object... params) {
        executedSql.add(sql);
        if (failUpdateFragmentTimes > 0 && sql.contains(failUpdateFragment)) {
            failUpdateFragmentTimes--;
            return -1;
        }
        if (failNextUpdate > 0) {
            failNextUpdate--;
            return -1;
        }
        try {
            PreparedStatement statement = connection.prepareStatement(sql);
            try {
                bind(statement, params);
                return statement.executeUpdate();
            } finally {
                statement.close();
            }
        } catch (SQLException e) {
            // 主程序把语句失败吞成 -1，这里保持一致，避免测试把生产会降级的情况判成崩溃
            return -1;
        }
    }

    @Override
    public long insert(String sql, Object... params) {
        executedSql.add(sql);
        if (failNextInsert > 0) {
            failNextInsert--;
            return 0L;
        }
        try {
            PreparedStatement statement = connection.prepareStatement(sql);
            try {
                bind(statement, params);
                int affected = statement.executeUpdate();
                if (affected == 0) {
                    return 0L;
                }
            } finally {
                statement.close();
            }
            Statement idStatement = connection.createStatement();
            try {
                ResultSet resultSet = idStatement.executeQuery("SELECT last_insert_rowid()");
                try {
                    return resultSet.next() ? resultSet.getLong(1) : 0L;
                } finally {
                    resultSet.close();
                }
            } finally {
                idStatement.close();
            }
        } catch (SQLException e) {
            // 主程序把插入失败吞成 0，这里保持一致
            return 0L;
        }
    }

    /** 统计一行结果，供测试断言。 */
    public long count(String sql, Object... params) {
        JSONObject row = queryOne(sql, params);
        if (row == null || row.isEmpty()) {
            return 0L;
        }
        Object value = row.values().iterator().next();
        return value == null ? 0L : ((Number) value).longValue();
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // 关闭失败不影响测试结论
            }
            connection = null;
        }
    }

    private static void bind(PreparedStatement statement, Object[] params) throws SQLException {
        if (params == null) {
            return;
        }
        for (int i = 0; i < params.length; i++) {
            Object value = params[i];
            int index = i + 1;
            if (value == null) {
                statement.setNull(index, Types.NULL);
            } else if (value instanceof Integer) {
                statement.setInt(index, ((Integer) value).intValue());
            } else if (value instanceof Long) {
                statement.setLong(index, ((Long) value).longValue());
            } else if (value instanceof Boolean) {
                statement.setInt(index, ((Boolean) value).booleanValue() ? 1 : 0);
            } else if (value instanceof Number) {
                statement.setDouble(index, ((Number) value).doubleValue());
            } else {
                statement.setString(index, String.valueOf(value));
            }
        }
    }

    private static Object readValue(ResultSet resultSet, int index) throws SQLException {
        Object value = resultSet.getObject(index);
        if (value instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) value).getTime();
        }
        return value;
    }
}
