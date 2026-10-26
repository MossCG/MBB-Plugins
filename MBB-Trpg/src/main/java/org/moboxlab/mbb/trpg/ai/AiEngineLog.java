package org.moboxlab.mbb.trpg.ai;

import org.moboxlab.moboxbot.API.PluginLogger;

/** 引擎日志出口：统一加插件前缀，未注入日志器时静默丢弃。 */
final class AiEngineLog {

    private static final String PREFIX = "[MBB-Trpg] ";

    private final PluginLogger logger;

    AiEngineLog(PluginLogger logger) {
        this.logger = logger;
    }

    /** 打一条告警；日志器为空时什么也不做。 */
    void warn(String message) {
        if (logger != null) {
            logger.sendWarn(PREFIX + message);
        }
    }
}
