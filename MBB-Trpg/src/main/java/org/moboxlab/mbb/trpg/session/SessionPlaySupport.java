package org.moboxlab.mbb.trpg.session;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.moboxlab.mbb.trpg.script.Scene;
import org.moboxlab.mbb.trpg.script.Script;
import org.moboxlab.mbb.trpg.store.SessionRow;

/**
 * 局面动作共用的静态工具：剧本定位、场景序号、技能集合与提示拼装。
 *
 * <p>放在这里而不是各自的动作服务里，是因为检定与流程两侧都要用，
 * 且这些函数不持有状态，便于单独核对。</p>
 */
final class SessionPlaySupport {

    /** 提示拼装的分隔符。 */
    private static final String HINT_SEPARATOR = "；";

    private SessionPlaySupport() {
    }

    /** 取本局剧本；缺失返回 null（调用方按不可用处理）。 */
    static Script scriptOf(SessionService sessions, SessionRow row) {
        return sessions.loadScript(row.scriptId());
    }

    /** 取场景在本局剧本里的序号；找不到按 0 处理。 */
    static int indexOf(Script script, Scene scene) {
        List<Scene> scenes = script.scenes();
        for (int i = 0; i < scenes.size(); i++) {
            Scene candidate = scenes.get(i);
            if (candidate != null && candidate.id().equals(scene.id())) {
                return i;
            }
        }
        return 0;
    }

    /** 把技能名并入成长集合（去重保序）。 */
    static List<String> mergeSkill(List<String> existing, String skill) {
        LinkedHashSet<String> merged = new LinkedHashSet<String>();
        if (existing != null) {
            merged.addAll(existing);
        }
        merged.add(skill);
        return new ArrayList<String>(merged);
    }

    static void addHint(Set<String> hints, String hint) {
        if (hint == null) {
            return;
        }
        String value = hint.trim();
        if (!value.isEmpty()) {
            hints.add(value);
        }
    }

    static String joinHints(Set<String> hints) {
        StringBuilder text = new StringBuilder();
        for (String hint : hints) {
            if (text.length() > 0) {
                text.append(HINT_SEPARATOR);
            }
            text.append(hint);
        }
        return text.toString();
    }

    /** 解析整数文本（允许前置 + 号）；格式不符返回 null。 */
    static Integer parseInt(String text) {
        String value = text.trim();
        if (value.isEmpty()) {
            return null;
        }
        String body = value.startsWith("+") ? value.substring(1) : value;
        if (body.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(body);
        } catch (NumberFormatException error) {
            return null;
        }
    }

    static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}
