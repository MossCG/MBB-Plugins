package org.moboxlab.mbb.trpg.present;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.moboxlab.mbb.trpg.session.SessionState;

/**
 * 局面摘要视图（供 /trpg status 模板使用）。
 *
 * <p>指令层负责把会话快照与昵称拼成这个视图；模板只负责排版与截断。
 * 无局时用 {@link #none()}，模板回「无局」。</p>
 */
public final class TrpgStatusView {

    private final boolean active;
    private final SessionState state;
    private final String scriptName;
    private final String kpName;
    private final int currentPlayers;
    private final int maxPlayers;
    private final List<String> playerLabels;
    private final String sceneTitle;
    private final int sceneNo;
    private final int sceneTotal;
    private final int clueUnlocked;
    private final int clueTotal;
    private final String aiLine;
    private final boolean recovered;
    private final long lastActivityAt;

    private TrpgStatusView(boolean active, SessionState state, String scriptName, String kpName,
            int currentPlayers, int maxPlayers, List<String> playerLabels, String sceneTitle,
            int sceneNo, int sceneTotal, int clueUnlocked, int clueTotal, String aiLine,
            boolean recovered, long lastActivityAt) {
        this.active = active;
        this.state = state == null ? SessionState.NONE : state;
        this.scriptName = text(scriptName);
        this.kpName = text(kpName);
        this.currentPlayers = currentPlayers;
        this.maxPlayers = maxPlayers;
        this.playerLabels = playerLabels == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(playerLabels));
        this.sceneTitle = text(sceneTitle);
        this.sceneNo = sceneNo;
        this.sceneTotal = sceneTotal;
        this.clueUnlocked = clueUnlocked;
        this.clueTotal = clueTotal;
        this.aiLine = text(aiLine);
        this.recovered = recovered;
        this.lastActivityAt = lastActivityAt;
    }

    /** 无局视图。 */
    public static TrpgStatusView none() {
        return new TrpgStatusView(false, SessionState.NONE, "", "", 0, 0, null, "", 0, 0, 0, 0,
                "关", true, 0L);
    }

    /** 有局视图。 */
    public static TrpgStatusView of(SessionState state, String scriptName, String kpName,
            int currentPlayers, int maxPlayers, List<String> playerLabels, String sceneTitle,
            int sceneNo, int sceneTotal, int clueUnlocked, int clueTotal, String aiLine,
            boolean recovered, long lastActivityAt) {
        return new TrpgStatusView(true, state, scriptName, kpName, currentPlayers, maxPlayers,
                playerLabels, sceneTitle, sceneNo, sceneTotal, clueUnlocked, clueTotal, aiLine,
                recovered, lastActivityAt);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    public boolean active() {
        return active;
    }

    public SessionState state() {
        return state;
    }

    public String scriptName() {
        return scriptName;
    }

    public String kpName() {
        return kpName;
    }

    public int currentPlayers() {
        return currentPlayers;
    }

    public int maxPlayers() {
        return maxPlayers;
    }

    public List<String> playerLabels() {
        return playerLabels;
    }

    public String sceneTitle() {
        return sceneTitle;
    }

    public int sceneNo() {
        return sceneNo;
    }

    public int sceneTotal() {
        return sceneTotal;
    }

    public int clueUnlocked() {
        return clueUnlocked;
    }

    public int clueTotal() {
        return clueTotal;
    }

    public String aiLine() {
        return aiLine;
    }

    /** 重启后是否已由 KP 确认恢复；false 时模板多一行提示。 */
    public boolean recovered() {
        return recovered;
    }

    public long lastActivityAt() {
        return lastActivityAt;
    }
}
