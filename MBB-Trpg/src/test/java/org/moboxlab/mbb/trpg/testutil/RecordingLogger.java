package org.moboxlab.mbb.trpg.testutil;

import org.moboxlab.moboxbot.API.PluginLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 记录日志的假件：只收集告警文本，不落主程序日志。 */
public final class RecordingLogger extends PluginLogger {

    private final List<String> infos = new ArrayList<String>();
    private final List<String> warns = new ArrayList<String>();
    private final List<String> errors = new ArrayList<String>();

    public RecordingLogger() {
        super("test", null);
    }

    @Override
    public void sendInfo(String message) {
        infos.add(message);
    }

    @Override
    public void sendWarn(String message) {
        warns.add(message);
    }

    @Override
    public void sendError(String message) {
        errors.add(message);
    }

    /** 收集到的普通日志，按顺序。 */
    public List<String> infos() {
        return Collections.unmodifiableList(infos);
    }

    /** 收集到的告警，按顺序。 */
    public List<String> warns() {
        return Collections.unmodifiableList(warns);
    }

    /** 收集到的错误，按顺序。 */
    public List<String> errors() {
        return Collections.unmodifiableList(errors);
    }
}
