package org.moboxlab.mbb.trpg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

/**
 * 构建冒烟测试。
 *
 * <p>只验证测试载体本身可用：JUnit 能从 Maven 依赖加载、主类能编译进 classpath、
 * 插件清单资源已随构建打包。业务契约测试由后续切片补充。</p>
 */
public class TrpgBuildSmokeTest {

    @Test
    public void pluginJsonShouldBePackagedAndValid() throws Exception {
        InputStream in = TrpgBuildSmokeTest.class.getResourceAsStream("/plugin.json");
        assertNotNull("插件清单资源应随构建复制到 classes 目录", in);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        try {
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
        } finally {
            in.close();
        }

        String text = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        JSONObject json = JSON.parseObject(text);
        assertEquals("MBB-Trpg", json.getString("name"));
        assertEquals("org.moboxlab.mbb.trpg.TrpgPlugin", json.getString("main"));
        assertTrue("插件版本应带 V 前缀", json.getString("version").startsWith("V"));
    }

    @Test
    public void pluginClassShouldBeLoadable() throws Exception {
        Class<?> pluginClass = Class.forName("org.moboxlab.mbb.trpg.TrpgPlugin");
        assertTrue(org.moboxlab.moboxbot.API.Plugin.class.isAssignableFrom(pluginClass));
    }
}
