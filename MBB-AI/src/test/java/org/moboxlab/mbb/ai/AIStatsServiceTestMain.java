package org.moboxlab.mbb.ai;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Plugin;
import org.moboxlab.moboxbot.API.Server;
import org.moboxlab.moboxbot.API.Storage.StorageService;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 调用点统计与统计图回归测试。
 */
public class AIStatsServiceTestMain {
    public static void main(String[] args) throws Exception {
        Class.forName("org.sqlite.JDBC");
        Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try {
            MemoryStorage storage = new MemoryStorage(connection);
            AIStatsService service = new AIStatsService(new TestPlugin(storage));
            service.init();

            AIProfile profile = new AIProfile();
            profile.name = "default";
            profile.model = "deepseek-v4.1-flash";
            JSONObject replyUsage = usage(100,20,120,50);
            service.record(profile,"chat","roleplay.reply","a1b2c3d4e5f60708",
                    true,false,1200L,replyUsage);
            service.record(profile,"chat","roleplay.reply","a1b2c3d4e5f60708",
                    true,false,1800L,replyUsage);
            service.record(profile,"chat","roleplay.router","1122334455667788",
                    false,false,400L,usage(40,0,40,0));

            JSONObject summary = service.summary(7);
            JSONObject total = summary.getJSONObject("total");
            assertNumber(total,"requests",3);
            assertNumber(total,"promptTokens",240);
            assertNumber(total,"cachedPromptTokens",100);
            assertDouble(total,"promptCacheHitRate",100.0 * 100.0 / 240.0);

            JSONArray tasks = summary.getJSONArray("tasks");
            if (tasks == null || tasks.size() != 2) {
                throw new AssertionError("调用点统计应聚合为两个任务版本");
            }
            JSONObject reply = findTask(tasks,"roleplay.reply");
            assertNumber(reply,"requests",2);
            assertNumber(reply,"totalTokens",240);
            assertDouble(reply,"promptCacheHitRate",50.0);
            assertNumber(reply,"averageLatencyMs",1500);
            JSONObject router = findTask(tasks,"roleplay.router");
            assertNumber(router,"failures",1);
            assertDouble(router,"successRate",0.0);

            byte[] imageBytes = AIImageRenderer.renderUsage(summary,7);
            if (imageBytes == null || imageBytes.length < 1000) {
                throw new AssertionError("调用点统计图没有正常生成");
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null || image.getWidth() != 1000 || image.getHeight() < 900) {
                throw new AssertionError("调用点统计图尺寸异常");
            }
            if (args != null && args.length > 0) {
                Files.write(Paths.get(args[0]),imageBytes);
            }
        } finally {
            connection.close();
        }
    }

    private static JSONObject usage(long prompt,long completion,long total,long cached) {
        JSONObject usage = new JSONObject(true);
        usage.put("promptTokens",prompt);
        usage.put("completionTokens",completion);
        usage.put("totalTokens",total);
        usage.put("cachedPromptTokens",cached);
        return usage;
    }

    private static JSONObject findTask(JSONArray tasks,String name) {
        for (int i = 0; i < tasks.size(); i++) {
            JSONObject item = tasks.getJSONObject(i);
            if (name.equals(item.getString("name"))) return item;
        }
        throw new AssertionError("未找到调用点："+name);
    }

    private static void assertNumber(JSONObject item,String key,long expected) {
        long actual = item.getLongValue(key);
        if (actual != expected) {
            throw new AssertionError(key+" 期望 "+expected+"，实际 "+actual);
        }
    }

    private static void assertDouble(JSONObject item,String key,double expected) {
        double actual = item.getDoubleValue(key);
        if (Math.abs(actual - expected) > 0.0001) {
            throw new AssertionError(key+" 期望 "+expected+"，实际 "+actual);
        }
    }

    private static class TestPlugin extends Plugin {
        private final Server server;

        TestPlugin(StorageService storage) {
            server = (Server)Proxy.newProxyInstance(
                    Server.class.getClassLoader(),
                    new Class<?>[]{Server.class},
                    (proxy,method,args) -> "getStorage".equals(method.getName()) ? storage : null);
        }

        @Override
        public Server getServer() {
            return server;
        }
    }

    private static class MemoryStorage implements StorageService {
        private final Connection connection;

        MemoryStorage(Connection connection) {
            this.connection = connection;
        }

        @Override
        public String get(Plugin plugin,String key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(Plugin plugin,String key,String value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void remove(Plugin plugin,String key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<JSONObject> query(String sql,Object... params) {
            List<JSONObject> result = new ArrayList<>();
            try {
                PreparedStatement statement = connection.prepareStatement(sql);
                try {
                    bind(statement,params);
                    ResultSet set = statement.executeQuery();
                    try {
                        ResultSetMetaData meta = set.getMetaData();
                        while (set.next()) {
                            JSONObject row = new JSONObject(true);
                            for (int i = 1; i <= meta.getColumnCount(); i++) {
                                row.put(meta.getColumnLabel(i),set.getObject(i));
                            }
                            result.add(row);
                        }
                    } finally {
                        set.close();
                    }
                } finally {
                    statement.close();
                }
                return result;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public JSONObject queryOne(String sql,Object... params) {
            List<JSONObject> rows = query(sql,params);
            return rows.isEmpty() ? null : rows.get(0);
        }

        @Override
        public int update(String sql,Object... params) {
            try {
                PreparedStatement statement = connection.prepareStatement(sql);
                try {
                    bind(statement,params);
                    return statement.executeUpdate();
                } finally {
                    statement.close();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public long insert(String sql,Object... params) {
            try {
                PreparedStatement statement = connection.prepareStatement(
                        sql,Statement.RETURN_GENERATED_KEYS);
                try {
                    bind(statement,params);
                    if (statement.executeUpdate() <= 0) return 0L;
                    ResultSet keys = statement.getGeneratedKeys();
                    try {
                        return keys.next() ? keys.getLong(1) : 0L;
                    } finally {
                        keys.close();
                    }
                } finally {
                    statement.close();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private void bind(PreparedStatement statement,Object... params) throws Exception {
            if (params == null) return;
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1,params[i]);
            }
        }
    }
}
