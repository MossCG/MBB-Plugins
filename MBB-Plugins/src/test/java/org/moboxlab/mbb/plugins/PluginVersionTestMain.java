package org.moboxlab.mbb.plugins;

/**
 * 插件版本比较回归测试。
 */
public class PluginVersionTestMain {
    public static void main(String[] args) {
        if (PluginsCommand.compareVersions(
                "V0.13.22.0.2348","V0.13.23.0.2351") >= 0) {
            throw new AssertionError("较旧插件版本应判定为可更新");
        }
        if (PluginsCommand.compareVersions(
                "V0.13.23.0.2351","V0.13.23.0.2351") != 0) {
            throw new AssertionError("相同插件版本应判定为一致");
        }
        if (PluginsCommand.compareVersions(
                "V0.14.0.0.0100","V0.13.99.0.2359") <= 0) {
            throw new AssertionError("插件版本必须按数字段比较");
        }
        if (PluginsCommand.compareVersions(
                "V1.0.0","V1.0.0.0.0001") >= 0) {
            throw new AssertionError("缺失版本段应按 0 处理");
        }
    }
}
