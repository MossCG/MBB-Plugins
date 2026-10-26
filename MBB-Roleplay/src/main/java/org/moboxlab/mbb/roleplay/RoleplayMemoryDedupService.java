package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Storage.StorageService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 记忆注入去重
 *
 * 同一轮提示词内按来源优先级去重；启用追加上下文时，在当前压缩轮次内
 * 记录已经注入过的记忆，避免相同事实每轮重复占用上下文。
 */
public class RoleplayMemoryDedupService {
    static final String TABLE = "plugin_mbb_roleplay_memory_injection";
    private static final double NEAR_DUPLICATE = 0.92;

    private static class Entry {
        String fingerprint;
        String normalized;
        String source;
        int priority;
    }

    public class Round {
        private final String channelType;
        private final long channelID;
        private final String roundKey;
        private final boolean persistent;
        private final List<Entry> entries = new ArrayList<>();
        private final Set<String> exact = ConcurrentHashMap.newKeySet();
        private int inputLines;
        private int suppressedLines;

        private Round(String channelType,long channelID,String roundKey,boolean persistent) {
            this.channelType = channelType;
            this.channelID = channelID;
            this.roundKey = roundKey;
            this.persistent = persistent;
        }

        public String filter(String source,String text,int priority) {
            if (text == null || text.trim().isEmpty()) return "";
            String[] lines = text.replace("\r\n","\n").replace('\r','\n').split("\n",-1);
            StringBuilder builder = new StringBuilder();
            StringBuilder pendingHead = new StringBuilder();
            boolean kept = false;
            for (String line : lines) {
                String value = line == null ? "" : line.trim();
                if (!isFactLine(value)) {
                    if (!value.isEmpty()) pendingHead.append(line).append("\n");
                    continue;
                }
                inputLines++;
                String fact = factText(value);
                String normalized = normalize(fact);
                if (normalized.isEmpty() || duplicate(normalized)) {
                    suppressedLines++;
                    continue;
                }
                Entry entry = new Entry();
                entry.fingerprint = RoleplayAiMetadata.fingerprint(normalized);
                entry.normalized = normalized;
                entry.source = source;
                entry.priority = priority;
                entries.add(entry);
                exact.add(entry.fingerprint);
                if (!kept && pendingHead.length() > 0) builder.append(pendingHead);
                builder.append(value).append("\n");
                kept = true;
                if (persistent) persist(entry);
            }
            return kept ? builder.toString().trim() : "";
        }

        private boolean duplicate(String normalized) {
            String fingerprint = RoleplayAiMetadata.fingerprint(normalized);
            if (exact.contains(fingerprint)) return true;
            for (Entry entry : entries) {
                if (entry.normalized.equals(normalized)) return true;
                if (entry.normalized.length() >= 6 && normalized.length() >= 6
                        && similarity(entry.normalized,normalized) >= NEAR_DUPLICATE) {
                    return true;
                }
            }
            return false;
        }

        private void persist(Entry entry) {
            storage().insert("INSERT OR IGNORE INTO `"+TABLE+"` "
                            +"(`profile`,`channelType`,`channelID`,`roundKey`,`fingerprint`,"
                            +"`normalizedText`,`source`,`priority`,`createTime`,`updateTime`) "
                            +"VALUES (?,?,?,?,?,?,?,?,?,?)",
                    service.getRoleName(),channelType,channelID,roundKey,entry.fingerprint,
                    entry.normalized,entry.source,entry.priority,
                    System.currentTimeMillis(),System.currentTimeMillis());
        }

        public int inputLines() {
            return inputLines;
        }

        public int suppressedLines() {
            return suppressedLines;
        }
    }

    private final RoleplayService service;

    public RoleplayMemoryDedupService(RoleplayService service) {
        this.service = service;
    }

    public void init() {
        storage().update("CREATE TABLE IF NOT EXISTS `"+TABLE+"` ("
                + "`ID` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`profile` TEXT NOT NULL DEFAULT '',"
                + "`channelType` TEXT NOT NULL DEFAULT '',"
                + "`channelID` INTEGER NOT NULL DEFAULT 0,"
                + "`roundKey` TEXT NOT NULL DEFAULT '',"
                + "`fingerprint` TEXT NOT NULL DEFAULT '',"
                + "`normalizedText` TEXT NOT NULL DEFAULT '',"
                + "`source` TEXT NOT NULL DEFAULT '',"
                + "`priority` INTEGER NOT NULL DEFAULT 0,"
                + "`createTime` INTEGER NOT NULL DEFAULT 0,"
                + "`updateTime` INTEGER NOT NULL DEFAULT 0,"
                + "UNIQUE(`profile`,`channelType`,`channelID`,`roundKey`,`fingerprint`)"
                + ")");
        storage().update("CREATE INDEX IF NOT EXISTS `idx_plugin_mbb_roleplay_memory_injection_round` "
                + "ON `"+TABLE+"` (`profile`,`channelType`,`channelID`,`roundKey`)");
        storage().update("DELETE FROM `"+TABLE+"` WHERE `updateTime`<?",
                System.currentTimeMillis() - 30L * 86400000L);
    }

    public Round begin(String channelType,long channelID) {
        if (channelID <= 0) return new Round(channelType,channelID,"inline",false);
        String roundKey = service.config().conversationAppendEnable
                ? service.conversationContextService().contextToken(channelType,channelID)
                : "inline";
        Round round = new Round(channelType,channelID,roundKey,
                service.config().conversationAppendEnable);
        if (!round.persistent) return round;
        List<JSONObject> rows = storage().query(
                "SELECT `fingerprint`,`normalizedText`,`source`,`priority` FROM `"+TABLE+"` "
                        +"WHERE `profile`=? AND `channelType`=? AND `channelID`=? AND `roundKey`=? "
                        +"ORDER BY `priority` DESC,`ID` ASC LIMIT 500",
                service.getRoleName(),channelType,channelID,roundKey);
        if (rows != null) {
            for (JSONObject row : rows) {
                Entry entry = new Entry();
                entry.fingerprint = service.safe(row.getString("fingerprint"));
                entry.normalized = service.safe(row.getString("normalizedText"));
                entry.source = service.safe(row.getString("source"));
                entry.priority = row.getIntValue("priority");
                round.entries.add(entry);
                round.exact.add(entry.fingerprint);
            }
        }
        return round;
    }

    static boolean isFactLine(String line) {
        String value = line == null ? "" : line.trim();
        return value.startsWith("- ") || value.startsWith("* ") || value.startsWith("• ");
    }

    static String factText(String line) {
        String value = line == null ? "" : line.trim();
        if (value.startsWith("- ") || value.startsWith("* ") || value.startsWith("• ")) {
            value = value.substring(2).trim();
        }
        return value;
    }

    static String normalize(String text) {
        return SpeechCorpusEntry.normalize(factText(text));
    }

    static double similarity(String left,String right) {
        Set<String> a = SpeechCorpusEntry.bigrams(normalize(left));
        Set<String> b = SpeechCorpusEntry.bigrams(normalize(right));
        if (a.isEmpty() || b.isEmpty()) return 0;
        int overlap = 0;
        for (String item : a) if (b.contains(item)) overlap++;
        return (double)overlap / Math.max(a.size(),b.size());
    }

    private StorageService storage() {
        return service.plugin().getServer().getStorage();
    }
}
