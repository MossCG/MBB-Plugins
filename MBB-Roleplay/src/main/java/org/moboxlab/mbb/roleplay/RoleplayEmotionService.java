package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Plugin;
import org.moboxlab.moboxbot.API.PluginService;
import org.moboxlab.moboxbot.API.Storage.StorageService;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 角色情绪与用户关系
 *
 * 情绪是角色状态，不进入回复结构。群级情绪影响整体语气和参与意愿，
 * 群级用户关系保存单个用户的好感、信任、厌烦以及用户级情绪原因。
 */
public class RoleplayEmotionService {
    private static final String MOOD_TABLE = "plugin_mbb_roleplay_mood";
    private static final String RELATION_TABLE = "plugin_mbb_roleplay_relation";
    private static final String EVENT_TABLE = "plugin_mbb_roleplay_emotion_event";
    private static final long DAY_MILLIS = 86400000L;

    private final Plugin plugin;
    private volatile RoleplayConfig config;
    private volatile RoleplayPersona persona;
    private final Map<Long,MoodState> moodCache = new ConcurrentHashMap<>();
    private final Map<String,RelationState> relationCache = new ConcurrentHashMap<>();
    private final Map<String,Long> eventCooldownMap = new ConcurrentHashMap<>();
    private final Map<String,Long> analyzeCooldownMap = new ConcurrentHashMap<>();
    private final Map<String,RecentMessage> recentMessageMap = new ConcurrentHashMap<>();
    private final AtomicInteger eventWriteCount = new AtomicInteger();

    public static class MoodState {
        public long groupID;
        public int valence;
        public int energy;
        public int patience;
        public String label = "";
        public String reason = "";
        public long lastEventTime;
        public long lastDecayTime;
        public long updateTime;
    }

    public static class RelationState {
        public long groupID;
        public long userID;
        public double affinity;
        public double trust;
        public double annoyance;
        public String emotionLabel = "普通";
        public String emotionReason = "";
        public int reasonStrength;
        public String reasonSource = "";
        public long reasonSince;
        public long reasonExpire;
        public long lastInteraction;
        public long updateTime;
        public boolean provisionalInitial;
        public int dayKey;
        public double dayBaseAffinity;
        public double dayBaseTrust;
        public double dayBaseAnnoyance;
        public double dayDeltaAffinity;
        public double dayDeltaTrust;
        public double dayDeltaAnnoyance;
    }

    public static class LocalEvent {
        public String type = "";
        public String reason = "";
        public boolean significant;
        public boolean persistReason;
        public int valenceDelta;
        public int energyDelta;
        public int patienceDelta;
        public double affinityDelta;
        public double trustDelta;
        public double annoyanceDelta;

        private static LocalEvent none() {
            return new LocalEvent();
        }

        public boolean isPresent() {
            return type != null && !type.trim().isEmpty();
        }
    }

    private static class RecentMessage {
        private final String content;
        private final long time;

        private RecentMessage(String content,long time) {
            this.content = content;
            this.time = time;
        }
    }

    public RoleplayEmotionService(Plugin plugin,RoleplayConfig config,RoleplayPersona persona) {
        this.plugin = plugin;
        this.config = config;
        this.persona = persona;
    }

    public void init() {
        storage().update("CREATE TABLE IF NOT EXISTS `"+MOOD_TABLE+"` ("
                + "`ID` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`groupID` INTEGER NOT NULL DEFAULT 0 UNIQUE,"
                + "`valence` INTEGER NOT NULL DEFAULT 0,"
                + "`energy` INTEGER NOT NULL DEFAULT 0,"
                + "`patience` INTEGER NOT NULL DEFAULT 0,"
                + "`label` TEXT NOT NULL DEFAULT '',"
                + "`reason` TEXT NOT NULL DEFAULT '',"
                + "`lastEventTime` INTEGER NOT NULL DEFAULT 0,"
                + "`lastDecayTime` INTEGER NOT NULL DEFAULT 0,"
                + "`updateTime` INTEGER NOT NULL DEFAULT 0"
                + ")");
        storage().update("CREATE TABLE IF NOT EXISTS `"+RELATION_TABLE+"` ("
                + "`ID` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`groupID` INTEGER NOT NULL DEFAULT 0,"
                + "`userID` INTEGER NOT NULL DEFAULT 0,"
                + "`affinity` REAL NOT NULL DEFAULT 0,"
                + "`trust` REAL NOT NULL DEFAULT 0,"
                + "`annoyance` REAL NOT NULL DEFAULT 0,"
                + "`emotionLabel` TEXT NOT NULL DEFAULT '普通',"
                + "`emotionReason` TEXT NOT NULL DEFAULT '',"
                + "`reasonStrength` INTEGER NOT NULL DEFAULT 0,"
                + "`reasonSource` TEXT NOT NULL DEFAULT '',"
                + "`reasonSince` INTEGER NOT NULL DEFAULT 0,"
                + "`reasonExpire` INTEGER NOT NULL DEFAULT 0,"
                + "`lastInteraction` INTEGER NOT NULL DEFAULT 0,"
                + "`updateTime` INTEGER NOT NULL DEFAULT 0,"
                + "`dayKey` INTEGER NOT NULL DEFAULT 0,"
                + "`dayBaseAffinity` REAL NOT NULL DEFAULT 0,"
                + "`dayBaseTrust` REAL NOT NULL DEFAULT 0,"
                + "`dayBaseAnnoyance` REAL NOT NULL DEFAULT 0,"
                + "`dayDeltaAffinity` REAL NOT NULL DEFAULT 0,"
                + "`dayDeltaTrust` REAL NOT NULL DEFAULT 0,"
                + "`dayDeltaAnnoyance` REAL NOT NULL DEFAULT 0"
                + ")");
        storage().update("CREATE TABLE IF NOT EXISTS `"+EVENT_TABLE+"` ("
                + "`ID` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`groupID` INTEGER NOT NULL DEFAULT 0,"
                + "`userID` INTEGER NOT NULL DEFAULT 0,"
                + "`messageID` INTEGER NOT NULL DEFAULT 0,"
                + "`eventType` TEXT NOT NULL DEFAULT '',"
                + "`valenceDelta` INTEGER NOT NULL DEFAULT 0,"
                + "`energyDelta` INTEGER NOT NULL DEFAULT 0,"
                + "`patienceDelta` INTEGER NOT NULL DEFAULT 0,"
                + "`affinityDelta` REAL NOT NULL DEFAULT 0,"
                + "`trustDelta` REAL NOT NULL DEFAULT 0,"
                + "`annoyanceDelta` REAL NOT NULL DEFAULT 0,"
                + "`reason` TEXT NOT NULL DEFAULT '',"
                + "`source` TEXT NOT NULL DEFAULT '',"
                + "`createTime` INTEGER NOT NULL DEFAULT 0"
                + ")");
        ensureColumn(RELATION_TABLE,"dayKey","INTEGER NOT NULL DEFAULT 0");
        ensureColumn(RELATION_TABLE,"dayBaseAffinity","REAL NOT NULL DEFAULT 0");
        ensureColumn(RELATION_TABLE,"dayBaseTrust","REAL NOT NULL DEFAULT 0");
        ensureColumn(RELATION_TABLE,"dayBaseAnnoyance","REAL NOT NULL DEFAULT 0");
        ensureColumn(RELATION_TABLE,"dayDeltaAffinity","REAL NOT NULL DEFAULT 0");
        ensureColumn(RELATION_TABLE,"dayDeltaTrust","REAL NOT NULL DEFAULT 0");
        ensureColumn(RELATION_TABLE,"dayDeltaAnnoyance","REAL NOT NULL DEFAULT 0");
        storage().update("CREATE UNIQUE INDEX IF NOT EXISTS `idx_plugin_mbb_roleplay_relation_key` "
                + "ON `"+RELATION_TABLE+"` (`groupID`,`userID`)");
        storage().update("CREATE INDEX IF NOT EXISTS `idx_plugin_mbb_roleplay_emotion_event_group` "
                + "ON `"+EVENT_TABLE+"` (`groupID`,`createTime`)");
        storage().update("CREATE INDEX IF NOT EXISTS `idx_plugin_mbb_roleplay_emotion_event_user` "
                + "ON `"+EVENT_TABLE+"` (`groupID`,`userID`,`createTime`)");
        pruneEvents();
    }

    public void reload(RoleplayConfig config,RoleplayPersona persona) {
        this.config = config;
        this.persona = persona;
        pruneEvents();
    }

    public void clearCache() {
        moodCache.clear();
        relationCache.clear();
        eventCooldownMap.clear();
        analyzeCooldownMap.clear();
        recentMessageMap.clear();
    }

    /**
     * 本地规则观察一条消息，并立即更新状态。
     */
    public synchronized LocalEvent observeMessage(long groupID,long userID,String userName,
                                                  String relationship,String content,long messageID,
                                                  boolean direct,boolean otherRoleBot) {
        if (!config.emotionEnable || groupID <= 0 || userID <= 0) return LocalEvent.none();
        MoodState mood = mood(groupID);
        RelationState relation = otherRoleBot ? null : relation(groupID,userID,relationship);
        String text = safe(content).trim();
        if (text.isEmpty()) return LocalEvent.none();

        LocalEvent event = null;
        if (otherRoleBot) {
            if (allowEvent(groupID,userID,"bot-interaction",60)) {
                event = event("bot-interaction","其他角色机器人接续发言",false,0,0,-1,0,0,1);
            }
        } else if (isImpersonation(text,userName)
                && allowEvent(groupID,userID,"impersonation",
                Math.max(300,config.emotionEventCooldownSecond))) {
            event = event("impersonation","他在冒名顶替你",true,-3,-1,-5,-0.8,-0.6,1.2);
            event.persistReason = true;
        } else if (isAttack(text)
                && allowEvent(groupID,userID,"attack",config.emotionEventCooldownSecond)) {
            event = event("attack","他刚才说了攻击性的话",true,-6,-2,-8,-0.6,-0.4,1.0);
            event.persistReason = true;
        } else if (isPraise(text)
                && allowEvent(groupID,userID,"praise",config.emotionEventCooldownSecond)) {
            event = event("praise","他刚才夸过你",true,4,2,2,0.4,0.3,-0.2);
            event.persistReason = true;
        } else if (isRepeatedMessage(groupID,userID,text)
                && allowEvent(groupID,userID,"spam",Math.max(30,config.emotionEventCooldownSecond))) {
            event = event("spam","他连续重复发送消息",false,-2,0,-3,0,0,0);
        } else if (direct && allowEvent(groupID,userID,"friendly",config.emotionEventCooldownSecond)) {
            event = event("friendly","他主动找你说话了",false,2,1,1,0,0,0);
        }
        if (event == null || !event.isPresent()) return LocalEvent.none();

        LocalEvent applied = applyEvent(groupID,userID,messageID,mood,relation,event,"rule");
        plugin.getLogger().sendInfo("[情绪] 群"+groupID+" 用户"+userID
                +" 事件="+event.type
                +" 心情"+formatDelta(applied.valenceDelta)
                +" 耐心"+formatDelta(applied.patienceDelta)
                +" 好感"+formatDelta(applied.affinityDelta)
                +" 信任"+formatDelta(applied.trustDelta)
                +" 厌烦"+formatDelta(applied.annoyanceDelta)
                +(event.reason.isEmpty() ? "" : " 原因="+event.reason));
        return applied;
    }

    /**
     * 戳一戳事件。规则层先更新，避免 AI 分析失败时完全没有反应。
     */
    public synchronized LocalEvent observePoke(long groupID,long userID,String userName) {
        if (!config.emotionEnable || groupID <= 0 || userID <= 0) return LocalEvent.none();
        if (!allowEvent(groupID,userID,"poke",Math.max(5,config.emotionEventCooldownSecond / 2))) {
            return LocalEvent.none();
        }
        MoodState mood = mood(groupID);
        RelationState relation = relation(groupID,userID);
        LocalEvent event = event("poke",userName+"戳了你一下",false,-1,0,-1,0,0,0);
        LocalEvent applied = applyEvent(groupID,userID,0L,mood,relation,event,"rule");
        plugin.getLogger().sendInfo("[情绪] 群"+groupID+" 用户"+userID
                +" 事件=poke 心情"+applied.valenceDelta
                +" 耐心"+applied.patienceDelta
                +" 好感"+formatDelta(applied.affinityDelta)
                +" 厌烦"+formatDelta(applied.annoyanceDelta)
                +" 原因="+event.reason);
        return applied;
    }

    /**
     * 回复后异步分析语义情绪原因。回复结构不携带任何情绪字段。
     */
    public void afterTurn(long groupID,long userID,String userName,String relationship,
                          String content,LocalEvent localEvent,boolean direct,boolean otherRoleBot) {
        if (!config.emotionEnable || !config.emotionAnalyzeEnable || otherRoleBot) return;
        if (groupID <= 0 || userID <= 0 || safe(content).trim().isEmpty()) return;
        if ("off".equals(config.emotionAnalyzeMode)) return;
        if (!shouldAnalyze(direct,localEvent)) return;
        String key = groupID+"-"+userID;
        long now = System.currentTimeMillis();
        Long last = analyzeCooldownMap.get(key);
        if (last != null && now - last < config.emotionAnalyzeCooldownSecond * 1000L) return;
        analyzeCooldownMap.put(key,now);
        plugin.getServer().getPluginManager().runTask(plugin,
                () -> analyzeEmotion(groupID,userID,userName,relationship,content,localEvent));
    }

    /**
     * 提示词里的完整情绪和关系摘要。
     */
    public String promptText(long groupID,long userID,boolean otherRoleBot) {
        if (!config.emotionEnable || groupID <= 0) return "";
        MoodState mood = mood(groupID);
        StringBuilder builder = new StringBuilder();
        builder.append("当前群情绪：").append(mood.label)
                .append("，心情").append(levelText(mood.valence))
                .append("，精力").append(levelText(mood.energy))
                .append("，耐心").append(levelText(mood.patience)).append("。\n");
        if (mood.reason != null && !mood.reason.isEmpty()) {
            builder.append("最近群情绪原因：").append(mood.reason).append("\n");
        }
        if (!otherRoleBot && userID > 0) {
            RelationState relation = relation(groupID,userID);
            builder.append("对当前发言者的关系：").append(relation.emotionLabel)
                    .append("，好感").append(levelText(relation.affinity))
                    .append("，信任").append(levelText(relation.trust))
                    .append("，厌烦").append(levelText(relation.annoyance)).append("。\n");
            if (relation.reasonStrength >= config.emotionReasonMinStrength
                    && relation.emotionReason != null && !relation.emotionReason.isEmpty()) {
                builder.append("对当前发言者的情绪原因：").append(relation.emotionReason).append("\n");
            }
            builder.append(isBotOwner(userID)
                    ? "亲密程度：这是 botOwner，角色把他当成妈妈，亲近、依赖、听劝，可以用撒娇和撒娇式抱怨，"
                    +"但不要往恋爱或性意味方向表达。\n"
                    : intimacyPrompt(relation)).append("\n");
        }
        builder.append("情绪只影响语气和参与意愿，不能取消被直接提及时的回复，也不能违反权限、安全或事实规则。\n");
        return builder.toString();
    }

    /**
     * 给路由层看的短摘要。
     */
    public String routerText(long groupID,long userID,boolean otherRoleBot) {
        if (!config.emotionEnable || groupID <= 0) return "";
        MoodState mood = mood(groupID);
        StringBuilder builder = new StringBuilder();
        builder.append("群情绪=").append(mood.label)
                .append("，心情=").append(levelText(mood.valence))
                .append("，精力=").append(levelText(mood.energy))
                .append("，耐心=").append(levelText(mood.patience));
        if (!otherRoleBot && userID > 0) {
            RelationState relation = relation(groupID,userID);
            builder.append("；对当前用户=").append(relation.emotionLabel)
                    .append("，好感=").append(levelText(relation.affinity))
                    .append("，厌烦=").append(levelText(relation.annoyance));
            if (relation.reasonStrength >= config.emotionReasonMinStrength
                    && relation.emotionReason != null && !relation.emotionReason.isEmpty()) {
                builder.append("；原因=").append(relation.emotionReason);
            }
        }
        return builder.toString();
    }

    /**
     * 非直接消息的情绪概率修正。
     */
    public synchronized double adjustReplyChance(long groupID,long userID,double chance,
                                                 boolean direct,boolean otherRoleBot) {
        if (!config.emotionEnable || direct) return clamp01(chance);
        MoodState mood = mood(groupID);
        double value = chance;
        if (mood.valence >= 70) {
            value += config.emotionPositiveReplyBonus;
        } else if (mood.valence < 35) {
            value -= config.emotionNegativeReplyPenalty * 0.6;
        }
        if (mood.patience < 30) value -= config.emotionNegativeReplyPenalty;
        if (mood.energy < 25) value -= config.emotionNegativeReplyPenalty * 0.4;
        if (!otherRoleBot && userID > 0) {
            RelationState relation = relation(groupID,userID);
            if (relation.affinity >= 80 && relation.annoyance < 60) {
                value += config.emotionAffinityReplyBonus;
            }
            if (relation.affinity < 40 || relation.annoyance >= 65) {
                value -= config.emotionAffinityReplyPenalty;
            }
        }
        return clamp01(value);
    }

    public synchronized JSONObject emotionStats(long groupID,long userID,int limit) {
        MoodState mood = mood(groupID);
        RelationState relation = relation(groupID,userID);
        JSONObject result = new JSONObject(true);
        result.put("role",persona.name);
        result.put("groupID",groupID);
        result.put("mood",moodJson(mood));
        result.put("relation",relationJson(relation));
        result.put("events",listEvents(groupID,userID,limit));
        return result;
    }

    public synchronized JSONObject moodStats(long groupID,int limit) {
        MoodState mood = mood(groupID);
        JSONObject result = new JSONObject(true);
        result.put("role",persona.name);
        result.put("groupID",groupID);
        result.put("mood",moodJson(mood));
        result.put("relation",new JSONObject(true));
        result.put("events",listEvents(groupID,0L,limit));
        return result;
    }

    public synchronized void resetMood(long groupID) {
        MoodState mood = mood(groupID);
        mood.valence = baselineValence();
        mood.energy = baselineEnergy();
        mood.patience = baselinePatience();
        mood.reason = "";
        mood.label = moodLabel(mood);
        mood.lastEventTime = 0L;
        mood.lastDecayTime = System.currentTimeMillis();
        mood.updateTime = System.currentTimeMillis();
        saveMood(mood);
        plugin.getLogger().sendInfo("[情绪] 群"+groupID+" 群情绪已重置");
    }

    public synchronized void resetRelation(long groupID,long userID) {
        RelationState relation = relation(groupID,userID);
        double oldAffinity = relation.affinity;
        double oldTrust = relation.trust;
        double oldAnnoyance = relation.annoyance;
        boolean owner = isBotOwner(userID);
        relation.affinity = owner ? 100 : clamp(config.initialAffinity,0,100);
        relation.trust = owner ? 100 : clamp(config.initialTrust,0,100);
        relation.annoyance = 0;
        relation.emotionLabel = owner ? "妈妈" : "普通";
        relation.emotionReason = "";
        relation.reasonStrength = 0;
        relation.reasonSource = "";
        relation.reasonSince = 0L;
        relation.reasonExpire = 0L;
        relation.lastInteraction = System.currentTimeMillis();
        relation.updateTime = System.currentTimeMillis();
        relation.dayKey = dayKey(relation.updateTime);
        relation.dayBaseAffinity = relation.affinity;
        relation.dayBaseTrust = relation.trust;
        relation.dayBaseAnnoyance = relation.annoyance;
        relation.dayDeltaAffinity = 0;
        relation.dayDeltaTrust = 0;
        relation.dayDeltaAnnoyance = 0;
        relation.provisionalInitial = false;
        saveRelation(relation);
        if (oldAffinity != relation.affinity || oldTrust != relation.trust
                || oldAnnoyance != relation.annoyance) {
            insertEvent(groupID,userID,0L,"command-reset",
                    0,0,0,relation.affinity - oldAffinity,
                    relation.trust - oldTrust,relation.annoyance - oldAnnoyance,
                    "管理员重置关系","command",relation.updateTime);
        }
        plugin.getLogger().sendInfo("[情绪] 群"+groupID+" 用户"+userID+" 关系已重置");
    }

    public synchronized void setRelationReason(long groupID,long userID,String reason) {
        RelationState relation = relation(groupID,userID);
        if (isBotOwner(userID)) {
            relation.emotionReason = "";
            relation.reasonStrength = 0;
            relation.reasonSource = "";
            relation.reasonSince = 0L;
            relation.reasonExpire = 0L;
            relation.emotionLabel = "妈妈";
            relation.provisionalInitial = false;
            relation.updateTime = System.currentTimeMillis();
            saveRelation(relation);
            return;
        }
        String value = trimReason(reason);
        relation.emotionReason = value;
        relation.reasonStrength = value.isEmpty() ? 0 : 100;
        relation.reasonSource = "owner";
        relation.reasonSince = System.currentTimeMillis();
        relation.reasonExpire = relation.reasonSince + config.emotionReasonDecayDays * DAY_MILLIS;
        relation.emotionLabel = labelForReason(value);
        relation.updateTime = System.currentTimeMillis();
        relation.provisionalInitial = false;
        saveRelation(relation);
    }

    public synchronized void setRelationAffinity(long groupID,long userID,double affinity) {
        RelationState relation = relation(groupID,userID);
        double oldAffinity = relation.affinity;
        relation.affinity = isBotOwner(userID) ? 100 : clampDouble(affinity,0,100);
        relation.emotionLabel = relationLabel(relation);
        relation.updateTime = System.currentTimeMillis();
        relation.dayKey = dayKey(relation.updateTime);
        relation.dayBaseAffinity = relation.affinity;
        relation.dayDeltaAffinity = 0;
        relation.provisionalInitial = false;
        saveRelation(relation);
        if (oldAffinity != relation.affinity) {
            insertEvent(groupID,userID,0L,"command-set-affinity",
                    0,0,0,relation.affinity - oldAffinity,0,0,
                    "管理员手动设置好感度","command",relation.updateTime);
        }
    }

    public synchronized double affinity(long groupID,long userID) {
        return relation(groupID,userID).affinity;
    }

    public synchronized int patience(long groupID) {
        return mood(groupID).patience;
    }

    public synchronized void clearRelationReason(long groupID,long userID) {
        RelationState relation = relation(groupID,userID);
        relation.emotionReason = "";
        relation.reasonStrength = 0;
        relation.reasonSource = "";
        relation.reasonSince = 0L;
        relation.reasonExpire = 0L;
        relation.emotionLabel = relationLabel(relation);
        relation.updateTime = System.currentTimeMillis();
        relation.provisionalInitial = false;
        saveRelation(relation);
    }

    public synchronized JSONObject exportState() {
        JSONObject root = new JSONObject(true);
        root.put("mood",exportMood());
        root.put("relations",exportRelations());
        root.put("events",exportEvents());
        return root;
    }

    public synchronized void restoreState(JSONObject state) {
        if (state == null) return;
        storage().update("DELETE FROM `"+MOOD_TABLE+"`");
        storage().update("DELETE FROM `"+RELATION_TABLE+"`");
        storage().update("DELETE FROM `"+EVENT_TABLE+"`");
        clearCache();
        JSONArray moods = state.getJSONArray("mood");
        if (moods != null) {
            for (Object object : moods) {
                if (!(object instanceof JSONObject)) continue;
                JSONObject json = (JSONObject) object;
                MoodState mood = moodFromJson(json);
                saveMood(mood);
                moodCache.put(mood.groupID,mood);
            }
        }
        JSONArray relations = state.getJSONArray("relations");
        if (relations != null) {
            for (Object object : relations) {
                if (!(object instanceof JSONObject)) continue;
                JSONObject json = (JSONObject) object;
                RelationState relation = relationFromJson(json);
                saveRelation(relation);
                relationCache.put(relationKey(relation.groupID,relation.userID),relation);
            }
        }
        JSONArray events = state.getJSONArray("events");
        if (events != null) {
            for (Object object : events) {
                if (!(object instanceof JSONObject)) continue;
                JSONObject json = (JSONObject) object;
                insertEvent(json.getLongValue("groupID"),json.getLongValue("userID"),
                        json.getLongValue("messageID"),json.getString("eventType"),
                        json.getIntValue("valenceDelta"),json.getIntValue("energyDelta"),
                        json.getIntValue("patienceDelta"),json.getDoubleValue("affinityDelta"),
                        json.getDoubleValue("trustDelta"),json.getDoubleValue("annoyanceDelta"),
                        json.getString("reason"),json.getString("source"),
                        json.getLongValue("createTime"));
            }
        }
    }

    private LocalEvent applyEvent(long groupID,long userID,long messageID,
                                  MoodState mood,RelationState relation,
                                  LocalEvent event,String source) {
        long now = System.currentTimeMillis();
        int valence = event.valenceDelta;
        int energy = event.energyDelta;
        int patience = event.patienceDelta;
        double rawAffinity = event.affinityDelta;
        double rawTrust = event.trustDelta;
        double rawAnnoyance = event.annoyanceDelta;
        double affinity = 0;
        double trust = 0;
        double annoyance = 0;
        if (relation != null) {
            String relationReason = event.reason == null ? "" : event.reason.trim();
            if (isBotOwner(userID)) {
                relation.affinity = 100;
                relation.trust = 100;
                relation.annoyance = 0;
                relation.emotionLabel = "妈妈";
                relation.emotionReason = "";
                relation.reasonStrength = 0;
                relation.reasonSource = "";
                relation.reasonSince = 0L;
                relation.reasonExpire = 0L;
                relation.provisionalInitial = false;
                relation.dayKey = dayKey(now);
                relation.dayBaseAffinity = 100;
                relation.dayBaseTrust = 100;
                relation.dayBaseAnnoyance = 0;
                relation.dayDeltaAffinity = 0;
                relation.dayDeltaTrust = 0;
                relation.dayDeltaAnnoyance = 0;
                relation.lastInteraction = now;
                relation.updateTime = now;
                saveRelation(relation);
            } else {
                if ((rawAffinity != 0 || rawTrust != 0 || rawAnnoyance != 0)
                        && relationReason.isEmpty()) {
                    plugin.getLogger().sendWarn("[情绪] 群"+groupID+" 用户"+userID
                            +" 关系数值变化缺少原因，已忽略关系变化");
                    rawAffinity = 0;
                    rawTrust = 0;
                    rawAnnoyance = 0;
                }
                affinity = applyDailyAffinity(relation,rawAffinity,now);
                trust = applyDailyTrust(relation,rawTrust,now);
                annoyance = applyDailyAnnoyance(relation,rawAnnoyance,now);
                relation.lastInteraction = now;
                relation.updateTime = now;
                if (event.persistReason && !relationReason.isEmpty()) {
                    String reason = trimReason(relationReason);
                    if (!reason.equals(relation.emotionReason)) {
                        relation.emotionReason = reason;
                        relation.reasonSince = now;
                    }
                    relation.reasonStrength = clamp(relation.reasonStrength
                            + Math.max(5,(int) Math.round(Math.abs(rawAffinity) * 20
                            + Math.abs(rawTrust) * 12 + Math.abs(rawAnnoyance) * 24)),0,100);
                    relation.reasonSource = source;
                    relation.reasonExpire = now + config.emotionReasonDecayDays * DAY_MILLIS;
                    relation.emotionLabel = labelForReason(relation.emotionReason);
                } else if (relation.reasonStrength > 0) {
                    relation.emotionLabel = labelForReason(relation.emotionReason);
                }
                saveRelation(relation);
            }
        }

        mood.valence = clamp(mood.valence + valence,0,100);
        mood.energy = clamp(mood.energy + energy,0,100);
        mood.patience = clamp(mood.patience + patience,0,100);
        mood.reason = event.reason == null ? "" : trimReason(event.reason);
        mood.lastEventTime = now;
        mood.lastDecayTime = now;
        mood.updateTime = now;
        mood.label = moodLabel(mood);
        saveMood(mood);

        insertEvent(groupID,userID,messageID,event.type,valence,energy,patience,
                rawAffinity,rawTrust,rawAnnoyance,event.reason,source,now);
        LocalEvent applied = new LocalEvent();
        applied.type = event.type;
        applied.reason = event.reason;
        applied.significant = event.significant;
        applied.persistReason = event.persistReason;
        applied.valenceDelta = valence;
        applied.energyDelta = energy;
        applied.patienceDelta = patience;
        applied.affinityDelta = affinity;
        applied.trustDelta = trust;
        applied.annoyanceDelta = annoyance;
        return applied;
    }

    private double applyDailyAffinity(RelationState relation,double delta,long now) {
        if (delta == 0) return 0;
        normalizeDay(relation,now);
        relation.dayDeltaAffinity += delta;
        double old = relation.affinity;
        double cap = Math.max(0.1,config.relationDailyMaxDelta);
        relation.affinity = clampDouble(relation.dayBaseAffinity
                + clampDouble(relation.dayDeltaAffinity,-cap,cap),0,100);
        return relation.affinity - old;
    }

    private double applyDailyTrust(RelationState relation,double delta,long now) {
        if (delta == 0) return 0;
        normalizeDay(relation,now);
        relation.dayDeltaTrust += delta;
        double old = relation.trust;
        double cap = Math.max(0.1,config.relationDailyMaxDelta);
        relation.trust = clampDouble(relation.dayBaseTrust
                + clampDouble(relation.dayDeltaTrust,-cap,cap),0,100);
        return relation.trust - old;
    }

    private double applyDailyAnnoyance(RelationState relation,double delta,long now) {
        if (delta == 0) return 0;
        normalizeDay(relation,now);
        relation.dayDeltaAnnoyance += delta;
        double old = relation.annoyance;
        double cap = Math.max(0.1,config.relationDailyMaxDelta);
        relation.annoyance = clampDouble(relation.dayBaseAnnoyance
                + clampDouble(relation.dayDeltaAnnoyance,-cap,cap),0,100);
        return relation.annoyance - old;
    }

    private void normalizeDay(RelationState relation,long now) {
        int key = dayKey(now);
        if (relation.dayKey == key) return;
        relation.dayKey = key;
        relation.dayBaseAffinity = relation.affinity;
        relation.dayBaseTrust = relation.trust;
        relation.dayBaseAnnoyance = relation.annoyance;
        relation.dayDeltaAffinity = 0;
        relation.dayDeltaTrust = 0;
        relation.dayDeltaAnnoyance = 0;
    }

    private void analyzeEmotion(long groupID,long userID,String userName,String relationship,
                                String content,LocalEvent localEvent) {
        PluginService ai = plugin.getServer().getPluginManager().getService("MBB-AI");
        if (ai == null) return;
        JSONArray messages = new JSONArray();
        messages.add(message("system",analyzeSystemPrompt(groupID,userID,userName,relationship,
                content,localEvent)));
        messages.add(message("user","请分析这条消息对角色情绪和用户关系的影响。"));
        JSONObject params = new JSONObject(true);
        String profile = config.emotionAnalyzeProfile == null
                || config.emotionAnalyzeProfile.trim().isEmpty()
                ? config.aiProfile : config.emotionAnalyzeProfile.trim();
        params.put("profile",profile);
        params.put("maxTokens",config.emotionAnalyzeMaxTokens);
        params.put("temperature",0.0);
        params.put("reasoningEffort",config.emotionAnalyzeReasoningEffort);
        params.put("sessionId","roleplay-emotion-"+groupID+"-"+userID);
        params.put("messages",messages);
        long startTime = System.currentTimeMillis();
        JSONObject response = ai.call("chat",params);
        RoleplayAiLog.log(plugin.getLogger(),"情绪分析",groupID,response,
                System.currentTimeMillis() - startTime);
        JSONObject parsed = parseJson(response == null ? "" : response.getString("content"));
        if (parsed == null) parsed = parseJson(response == null ? "" : response.getString("reasoningContent"));
        if (parsed == null) {
            plugin.getLogger().sendWarn("[情绪分析] 群"+groupID+" 用户"+userID
                    +" 没有返回合法 JSON");
            return;
        }
        String eventType = safe(parsed.getString("event")).trim();
        if (eventType.isEmpty()) eventType = "ai";
        String reason = safe(parsed.getString("reason")).trim();
        boolean persistReason = parsed.getBooleanValue("persistReason");
        JSONObject deltas = parsed.getJSONObject("delta");
        LocalEvent event = event("ai".equals(eventType) ? "semantic" : eventType,
                reason, true,
                intDelta(deltas,"valence"),intDelta(deltas,"energy"),
                intDelta(deltas,"patience"),delta(deltas,"affinity"),
                delta(deltas,"trust"),delta(deltas,"annoyance"));
        event.persistReason = persistReason;
        synchronized (this) {
            MoodState mood = mood(groupID);
            RelationState relation = relation(groupID,userID);
            LocalEvent applied = applyEvent(groupID,userID,0L,mood,relation,event,"ai");
            plugin.getLogger().sendInfo("[情绪分析] 群"+groupID+" 用户"+userID
                    +" 事件="+applied.type
                    +" 心情"+formatDelta(applied.valenceDelta)
                    +" 耐心"+formatDelta(applied.patienceDelta)
                    +" 好感"+formatDelta(applied.affinityDelta)
                    +" 信任"+formatDelta(applied.trustDelta)
                    +" 厌烦"+formatDelta(applied.annoyanceDelta)
                    +(reason.isEmpty() ? "" : " 原因="+reason));
        }
    }

    private String analyzeSystemPrompt(long groupID,long userID,String userName,String relationship,
                                       String content,LocalEvent localEvent) {
        MoodState mood = mood(groupID);
        RelationState relation = relation(groupID,userID);
        StringBuilder builder = new StringBuilder();
        builder.append("你是角色情绪分析器，只输出 JSON，不要解释。\n");
        builder.append("角色：").append(persona.name).append("\n");
        if (persona.personality != null && !persona.personality.isEmpty()) {
            builder.append("性格：").append(persona.personality).append("\n");
        }
        builder.append("当前群情绪：").append(mood.label)
                .append("，心情").append(levelText(mood.valence))
                .append("，精力").append(levelText(mood.energy))
                .append("，耐心").append(levelText(mood.patience)).append("\n");
        builder.append("当前用户：").append(safe(userName)).append("（QQ：").append(userID)
                .append("，关系：").append(safe(relationship)).append("）\n");
        builder.append("当前关系：").append(relation.emotionLabel)
                .append("，好感").append(levelText(relation.affinity))
                .append("，信任").append(levelText(relation.trust))
                .append("，厌烦").append(levelText(relation.annoyance)).append("\n");
        if (relation.emotionReason != null && !relation.emotionReason.isEmpty()) {
            builder.append("已有情绪原因：").append(relation.emotionReason)
                    .append("，强度 ").append(relation.reasonStrength).append("\n");
        }
        if (localEvent != null && localEvent.isPresent()) {
            builder.append("本地规则初步判断：").append(localEvent.type)
                    .append("，原因：").append(localEvent.reason).append("\n");
        }
        builder.append("当前消息：").append(limitText(safe(content),1200)).append("\n");
        builder.append("判断原则：\n")
                .append("- 普通闲聊不要把临时心情写成长期原因\n")
                .append("- 冒名顶替、欺骗、辱骂、反复挑衅、持续骚扰可以写入用户级原因\n")
                .append("- 正面互动可以提升好感和信任，但原因要短\n")
                .append("- 原因只描述当前用户做了什么，不记录隐私信息\n")
                .append("- 原因最多 ").append(config.emotionReasonMaxChars).append(" 个字符\n")
                .append("- 单项数值变化使用小数，普通事件建议 0.1 到 0.5\n")
                .append("- 单项数值变化绝对值不超过 ").append(config.emotionAnalyzeMaxDelta).append("\n")
                .append("- 来源：“他在冒名顶替我”“他刚才夸过我”“他反复戳我”\n");
        builder.append("输出格式：{\"event\":\"impersonation|attack|praise|friendly|neutral\",")
                .append("\"reason\":\"\",\"persistReason\":false,")
                .append("\"delta\":{\"valence\":0,\"energy\":0,\"patience\":0,")
                .append("\"affinity\":0.0,\"trust\":0.0,\"annoyance\":0.0}}");
        return builder.toString();
    }

    private boolean shouldAnalyze(boolean direct,LocalEvent localEvent) {
        if ("direct".equals(config.emotionAnalyzeMode)) return direct;
        if ("significant".equals(config.emotionAnalyzeMode)) {
            if (localEvent != null && localEvent.isPresent() && localEvent.significant) return true;
            return direct;
        }
        return false;
    }

    private boolean isImpersonation(String text,String userName) {
        String name = safe(userName).trim();
        if (matchesRoleName(name)) return true;
        for (String alias : roleAliases()) {
            if (alias.isEmpty()) continue;
            if (text.contains("我是"+alias) || text.contains("我才是"+alias)
                    || text.contains("冒充"+alias) || text.contains("冒名"+alias)
                    || text.contains("我就是"+alias)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAttack(String text) {
        String[] words = new String[]{"傻子","蠢货","滚开","滚蛋","闭嘴","垃圾","讨厌你",
                "烦人","恶心","死开","妈的","贱人","去死","废物","臭女人","笨蛋"};
        for (String word : words) {
            if (text.contains(word)) return true;
        }
        return false;
    }

    private boolean isPraise(String text) {
        String[] words = new String[]{"厉害","可爱","漂亮","喜欢你","谢谢","好棒","不错",
                "辛苦了","真聪明","最强","好厉害","很棒"};
        for (String word : words) {
            if (text.contains(word)) return true;
        }
        return false;
    }

    private boolean isRepeatedMessage(long groupID,long userID,String content) {
        String key = groupID+"-"+userID;
        long now = System.currentTimeMillis();
        RecentMessage previous = recentMessageMap.get(key);
        recentMessageMap.put(key,new RecentMessage(content,now));
        if (previous == null) return false;
        return content.equals(previous.content) && now - previous.time <= 60000L
                && content.length() >= 4;
    }

    private boolean allowEvent(long groupID,long userID,String type,int seconds) {
        if (seconds <= 0) return true;
        String key = groupID+"-"+userID+"-"+type;
        long now = System.currentTimeMillis();
        Long last = eventCooldownMap.get(key);
        if (last != null && now - last < seconds * 1000L) return false;
        eventCooldownMap.put(key,now);
        return true;
    }

    private LocalEvent event(String type,String reason,boolean significant,
                             int valence,int energy,int patience,
                             double affinity,double trust,double annoyance) {
        LocalEvent event = new LocalEvent();
        event.type = type;
        event.reason = trimReason(reason);
        event.significant = significant;
        event.valenceDelta = valence;
        event.energyDelta = energy;
        event.patienceDelta = patience;
        event.affinityDelta = affinity;
        event.trustDelta = trust;
        event.annoyanceDelta = annoyance;
        return event;
    }

    private synchronized MoodState mood(long groupID) {
        MoodState state = moodCache.get(groupID);
        if (state == null) {
            JSONObject row = storage().queryOne("SELECT * FROM `"+MOOD_TABLE+"` WHERE `groupID`=?",
                    groupID);
            state = row == null ? defaultMood(groupID) : moodFromJson(row);
            MoodState previous = moodCache.putIfAbsent(groupID,state);
            if (previous != null) state = previous;
        }
        decayMood(state);
        return state;
    }

    private synchronized RelationState relation(long groupID,long userID) {
        return relation(groupID,userID,"");
    }

    private synchronized RelationState relation(long groupID,long userID,String relationship) {
        String key = relationKey(groupID,userID);
        RelationState state = relationCache.get(key);
        if (state == null) {
            JSONObject row = storage().queryOne("SELECT * FROM `"+RELATION_TABLE
                    +"` WHERE `groupID`=? AND `userID`=?",groupID,userID);
            state = row == null ? defaultRelation(groupID,userID,relationship) : relationFromJson(row);
            RelationState previous = relationCache.putIfAbsent(key,state);
            if (previous != null) state = previous;
            if (row == null && !state.provisionalInitial) saveRelation(state);
        }
        if (state.provisionalInitial && !safe(relationship).isEmpty()) {
            RelationState initialized = defaultRelation(groupID,userID,relationship);
            state.affinity = initialized.affinity;
            state.trust = initialized.trust;
            state.annoyance = initialized.annoyance;
            state.emotionLabel = relationLabel(state);
            state.provisionalInitial = false;
            saveRelation(state);
        }
        decayRelation(state);
        return state;
    }

    private void decayMood(MoodState mood) {
        long now = System.currentTimeMillis();
        if (mood.lastDecayTime <= 0) {
            mood.lastDecayTime = now;
            return;
        }
        long interval = Math.max(1,config.emotionDecayMinute) * 60000L;
        long steps = (now - mood.lastDecayTime) / interval;
        if (steps <= 0) return;
        int count = (int) Math.min(120,steps);
        mood.valence = moveToward(mood.valence,baselineValence(),count);
        mood.energy = moveToward(mood.energy,baselineEnergy(),count);
        mood.patience = moveToward(mood.patience,baselinePatience(),count);
        mood.label = moodLabel(mood);
        if (now - mood.lastEventTime > 7200000L) mood.reason = "";
        mood.lastDecayTime += steps * interval;
        mood.updateTime = now;
        saveMood(mood);
    }

    private void decayRelation(RelationState relation) {
        long now = System.currentTimeMillis();
        if (isBotOwner(relation.userID)) {
            relation.affinity = 100;
            relation.trust = 100;
            relation.annoyance = 0;
            relation.emotionLabel = "妈妈";
            relation.emotionReason = "";
            relation.reasonStrength = 0;
            relation.reasonSource = "";
            relation.reasonSince = 0L;
            relation.reasonExpire = 0L;
            relation.provisionalInitial = false;
            relation.dayKey = dayKey(now);
            relation.dayBaseAffinity = 100;
            relation.dayBaseTrust = 100;
            relation.dayBaseAnnoyance = 0;
            relation.dayDeltaAffinity = 0;
            relation.dayDeltaTrust = 0;
            relation.dayDeltaAnnoyance = 0;
            relation.updateTime = now;
            saveRelation(relation);
            return;
        }
        if (relation.updateTime <= 0) {
            relation.updateTime = now;
            return;
        }
        long days = (now - relation.updateTime) / DAY_MILLIS;
        if (days <= 0) return;
        int steps = (int) Math.min(60,days);
        double oldAffinity = relation.affinity;
        double oldTrust = relation.trust;
        double oldAnnoyance = relation.annoyance;
        relation.affinity = moveToward(relation.affinity,config.initialAffinity,steps);
        relation.trust = moveToward(relation.trust,config.initialTrust,steps);
        relation.annoyance = Math.max(0,relation.annoyance - steps);
        if (relation.reasonStrength > 0) {
            relation.reasonStrength = Math.max(0,relation.reasonStrength - steps * 2);
        }
        if (relation.reasonExpire > 0 && now > relation.reasonExpire) {
            relation.reasonStrength = 0;
        }
        if (relation.reasonStrength <= 0) {
            relation.emotionReason = "";
            relation.reasonSource = "";
            relation.reasonSince = 0L;
            relation.reasonExpire = 0L;
        }
        relation.emotionLabel = relationLabel(relation);
        relation.dayKey = dayKey(now);
        relation.dayBaseAffinity = relation.affinity;
        relation.dayBaseTrust = relation.trust;
        relation.dayBaseAnnoyance = relation.annoyance;
        relation.dayDeltaAffinity = 0;
        relation.dayDeltaTrust = 0;
        relation.dayDeltaAnnoyance = 0;
        relation.updateTime = now;
        saveRelation(relation);
        if (oldAffinity != relation.affinity || oldTrust != relation.trust
                || oldAnnoyance != relation.annoyance) {
            insertEvent(relation.groupID,relation.userID,0L,"decay",
                    0,0,0,
                    relation.affinity - oldAffinity,relation.trust - oldTrust,
                    relation.annoyance - oldAnnoyance,
                    "长时间未互动，关系缓慢回落","decay",now);
        }
    }

    private int moveToward(int value,int target,int steps) {
        if (value == target) return value;
        for (int i = 0; i < steps; i++) {
            if (value < target) value++;
            else if (value > target) value--;
        }
        return value;
    }

    private MoodState defaultMood(long groupID) {
        MoodState mood = new MoodState();
        mood.groupID = groupID;
        mood.valence = baselineValence();
        mood.energy = baselineEnergy();
        mood.patience = baselinePatience();
        mood.label = moodLabel(mood);
        mood.lastDecayTime = System.currentTimeMillis();
        mood.updateTime = mood.lastDecayTime;
        return mood;
    }

    private RelationState defaultRelation(long groupID,long userID,String relationship) {
        RelationState relation = new RelationState();
        relation.groupID = groupID;
        relation.userID = userID;
        double multiplier = initialMultiplier(userID,relationship);
        relation.affinity = isBotOwner(userID)
                ? 100 : clamp((int) Math.round(config.initialAffinity * multiplier),0,100);
        relation.trust = isBotOwner(userID) ? 100 : clamp((int) Math.round(config.initialTrust
                * Math.min(1.25,multiplier)),0,100);
        relation.annoyance = 0;
        relation.emotionLabel = relationLabel(relation);
        relation.lastInteraction = System.currentTimeMillis();
        relation.updateTime = relation.lastInteraction;
        relation.dayKey = dayKey(relation.updateTime);
        relation.dayBaseAffinity = relation.affinity;
        relation.dayBaseTrust = relation.trust;
        relation.dayBaseAnnoyance = relation.annoyance;
        relation.provisionalInitial = safe(relationship).isEmpty();
        return relation;
    }

    private double initialMultiplier(long userID,String relationship) {
        double multiplier = 1.0;
        if ("老师".equals(safe(relationship))) {
            multiplier *= config.initialGroupAdminMultiplier;
        }
        if (isBotAdmin(userID)) multiplier *= config.initialBotAdminMultiplier;
        if (isBotOwner(userID)) multiplier *= config.initialBotOwnerMultiplier;
        return Math.max(1.0,multiplier);
    }

    private boolean isBotAdmin(long userID) {
        List<Long> admins = plugin.getServer().getAdminList();
        return admins != null && admins.contains(userID);
    }

    private boolean isBotOwner(long userID) {
        List<Long> owners = plugin.getServer().getOwnerList();
        return owners != null && owners.contains(userID);
    }

    private MoodState moodFromJson(JSONObject json) {
        MoodState mood = new MoodState();
        mood.groupID = json.getLongValue("groupID");
        mood.valence = clamp(json.getIntValue("valence"),0,100);
        mood.energy = clamp(json.getIntValue("energy"),0,100);
        mood.patience = clamp(json.getIntValue("patience"),0,100);
        mood.label = safe(json.getString("label"));
        mood.reason = safe(json.getString("reason"));
        mood.lastEventTime = json.getLongValue("lastEventTime");
        mood.lastDecayTime = json.getLongValue("lastDecayTime");
        mood.updateTime = json.getLongValue("updateTime");
        if (mood.label.isEmpty()) mood.label = moodLabel(mood);
        return mood;
    }

    private RelationState relationFromJson(JSONObject json) {
        RelationState relation = new RelationState();
        relation.groupID = json.getLongValue("groupID");
        relation.userID = json.getLongValue("userID");
        relation.affinity = clampDouble(json.getDoubleValue("affinity"),0,100);
        relation.trust = clampDouble(json.getDoubleValue("trust"),0,100);
        relation.annoyance = clampDouble(json.getDoubleValue("annoyance"),0,100);
        relation.emotionLabel = safe(json.getString("emotionLabel"));
        relation.emotionReason = safe(json.getString("emotionReason"));
        relation.reasonStrength = clamp(json.getIntValue("reasonStrength"),0,100);
        relation.reasonSource = safe(json.getString("reasonSource"));
        relation.reasonSince = json.getLongValue("reasonSince");
        relation.reasonExpire = json.getLongValue("reasonExpire");
        relation.lastInteraction = json.getLongValue("lastInteraction");
        relation.updateTime = json.getLongValue("updateTime");
        relation.dayKey = json.getIntValue("dayKey");
        relation.dayBaseAffinity = json.getDoubleValue("dayBaseAffinity");
        relation.dayBaseTrust = json.getDoubleValue("dayBaseTrust");
        relation.dayBaseAnnoyance = json.getDoubleValue("dayBaseAnnoyance");
        relation.dayDeltaAffinity = json.getDoubleValue("dayDeltaAffinity");
        relation.dayDeltaTrust = json.getDoubleValue("dayDeltaTrust");
        relation.dayDeltaAnnoyance = json.getDoubleValue("dayDeltaAnnoyance");
        if (relation.emotionLabel.isEmpty()) relation.emotionLabel = relationLabel(relation);
        return relation;
    }

    private JSONObject moodJson(MoodState mood) {
        JSONObject json = new JSONObject(true);
        json.put("groupID",mood.groupID);
        json.put("valence",mood.valence);
        json.put("energy",mood.energy);
        json.put("patience",mood.patience);
        json.put("label",mood.label);
        json.put("reason",mood.reason);
        json.put("lastEventTime",mood.lastEventTime);
        json.put("updateTime",mood.updateTime);
        return json;
    }

    private JSONObject relationJson(RelationState relation) {
        JSONObject json = new JSONObject(true);
        json.put("groupID",relation.groupID);
        json.put("userID",relation.userID);
        json.put("affinity",relation.affinity);
        json.put("trust",relation.trust);
        json.put("annoyance",relation.annoyance);
        json.put("emotionLabel",relation.emotionLabel);
        json.put("emotionReason",relation.emotionReason);
        json.put("reasonStrength",relation.reasonStrength);
        json.put("reasonSource",relation.reasonSource);
        json.put("reasonSince",relation.reasonSince);
        json.put("reasonExpire",relation.reasonExpire);
        json.put("lastInteraction",relation.lastInteraction);
        json.put("updateTime",relation.updateTime);
        json.put("dayKey",relation.dayKey);
        json.put("dayBaseAffinity",relation.dayBaseAffinity);
        json.put("dayBaseTrust",relation.dayBaseTrust);
        json.put("dayBaseAnnoyance",relation.dayBaseAnnoyance);
        json.put("dayDeltaAffinity",relation.dayDeltaAffinity);
        json.put("dayDeltaTrust",relation.dayDeltaTrust);
        json.put("dayDeltaAnnoyance",relation.dayDeltaAnnoyance);
        return json;
    }

    private JSONArray exportMood() {
        JSONArray result = new JSONArray();
        List<JSONObject> rows = storage().query("SELECT * FROM `"+MOOD_TABLE+"` ORDER BY `groupID` ASC");
        if (rows == null) return result;
        for (JSONObject row : rows) result.add(moodJson(moodFromJson(row)));
        return result;
    }

    private JSONArray exportRelations() {
        JSONArray result = new JSONArray();
        List<JSONObject> rows = storage().query("SELECT * FROM `"+RELATION_TABLE
                +"` ORDER BY `groupID` ASC,`userID` ASC");
        if (rows == null) return result;
        for (JSONObject row : rows) result.add(relationJson(relationFromJson(row)));
        return result;
    }

    private JSONArray exportEvents() {
        JSONArray result = new JSONArray();
        List<JSONObject> rows = storage().query("SELECT * FROM `"+EVENT_TABLE+"` ORDER BY `ID` ASC");
        if (rows == null) return result;
        for (JSONObject row : rows) {
            JSONObject item = new JSONObject(true);
            item.put("groupID",row.getLongValue("groupID"));
            item.put("userID",row.getLongValue("userID"));
            item.put("messageID",row.getLongValue("messageID"));
            item.put("eventType",row.getString("eventType"));
            item.put("valenceDelta",row.getIntValue("valenceDelta"));
            item.put("energyDelta",row.getIntValue("energyDelta"));
            item.put("patienceDelta",row.getIntValue("patienceDelta"));
            item.put("affinityDelta",row.getDoubleValue("affinityDelta"));
            item.put("trustDelta",row.getDoubleValue("trustDelta"));
            item.put("annoyanceDelta",row.getDoubleValue("annoyanceDelta"));
            item.put("reason",row.getString("reason"));
            item.put("source",row.getString("source"));
            item.put("createTime",row.getLongValue("createTime"));
            result.add(item);
        }
        return result;
    }

    private JSONArray listEvents(long groupID,long userID,int limit) {
        if (limit < 1) limit = 12;
        if (limit > 100) limit = 100;
        List<JSONObject> rows;
        if (userID > 0) {
            rows = storage().query("SELECT * FROM `"+EVENT_TABLE
                            +"` WHERE `groupID`=? AND `userID`=? ORDER BY `ID` DESC LIMIT ?",
                    groupID,userID,limit);
        } else {
            rows = storage().query("SELECT * FROM `"+EVENT_TABLE
                    +"` WHERE `groupID`=? ORDER BY `ID` DESC LIMIT ?",groupID,limit);
        }
        JSONArray result = new JSONArray();
        if (rows == null) return result;
        for (int i = rows.size() - 1; i >= 0; i--) {
            JSONObject row = rows.get(i);
            JSONObject item = new JSONObject(true);
            item.put("groupID",row.getLongValue("groupID"));
            item.put("userID",row.getLongValue("userID"));
            item.put("messageID",row.getLongValue("messageID"));
            item.put("eventType",row.getString("eventType"));
            item.put("valenceDelta",row.getIntValue("valenceDelta"));
            item.put("energyDelta",row.getIntValue("energyDelta"));
            item.put("patienceDelta",row.getIntValue("patienceDelta"));
            item.put("affinityDelta",row.getDoubleValue("affinityDelta"));
            item.put("trustDelta",row.getDoubleValue("trustDelta"));
            item.put("annoyanceDelta",row.getDoubleValue("annoyanceDelta"));
            item.put("reason",row.getString("reason"));
            item.put("source",row.getString("source"));
            item.put("createTime",row.getLongValue("createTime"));
            result.add(item);
        }
        return result;
    }

    private void saveMood(MoodState mood) {
        storage().insert("INSERT OR REPLACE INTO `"+MOOD_TABLE+"` "
                        + "(`groupID`,`valence`,`energy`,`patience`,`label`,`reason`,"
                        + "`lastEventTime`,`lastDecayTime`,`updateTime`) VALUES (?,?,?,?,?,?,?,?,?)",
                mood.groupID,mood.valence,mood.energy,mood.patience,mood.label,mood.reason,
                mood.lastEventTime,mood.lastDecayTime,mood.updateTime);
    }

    private void saveRelation(RelationState relation) {
        storage().insert("INSERT OR REPLACE INTO `"+RELATION_TABLE+"` "
                        + "(`groupID`,`userID`,`affinity`,`trust`,`annoyance`,`emotionLabel`,"
                        + "`emotionReason`,`reasonStrength`,`reasonSource`,`reasonSince`,"
                        + "`reasonExpire`,`lastInteraction`,`updateTime`,`dayKey`,"
                        + "`dayBaseAffinity`,`dayBaseTrust`,`dayBaseAnnoyance`,"
                        + "`dayDeltaAffinity`,`dayDeltaTrust`,`dayDeltaAnnoyance`) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                relation.groupID,relation.userID,relation.affinity,relation.trust,
                relation.annoyance,relation.emotionLabel,relation.emotionReason,
                relation.reasonStrength,relation.reasonSource,relation.reasonSince,
                relation.reasonExpire,relation.lastInteraction,relation.updateTime,
                relation.dayKey,relation.dayBaseAffinity,relation.dayBaseTrust,
                relation.dayBaseAnnoyance,relation.dayDeltaAffinity,relation.dayDeltaTrust,
                relation.dayDeltaAnnoyance);
    }

    private void insertEvent(long groupID,long userID,long messageID,String eventType,
                             int valence,int energy,int patience,double affinity,double trust,
                             double annoyance,String reason,String source,long createTime) {
        long time = createTime <= 0 ? System.currentTimeMillis() : createTime;
        storage().insert("INSERT INTO `"+EVENT_TABLE+"` "
                        + "(`groupID`,`userID`,`messageID`,`eventType`,`valenceDelta`,`energyDelta`,"
                        + "`patienceDelta`,`affinityDelta`,`trustDelta`,`annoyanceDelta`,"
                        + "`reason`,`source`,`createTime`) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                groupID,userID,messageID,safe(eventType),valence,energy,patience,affinity,
                trust,annoyance,trimReason(reason),safe(source),time);
        if (eventWriteCount.incrementAndGet() % 200 == 0) pruneEvents();
    }

    private void pruneEvents() {
        long cutoff = System.currentTimeMillis() - config.emotionEventRetentionDays * DAY_MILLIS;
        storage().update("DELETE FROM `"+EVENT_TABLE+"` WHERE `createTime`<?",cutoff);
    }

    private void ensureColumn(String table,String column,String definition) {
        List<JSONObject> columns = storage().query("PRAGMA table_info(`"+table+"`)");
        if (columns != null) {
            for (JSONObject item : columns) {
                if (column.equalsIgnoreCase(item.getString("name"))) return;
            }
        }
        storage().update("ALTER TABLE `"+table+"` ADD COLUMN `"+column+"` "+definition);
    }

    private int baselineValence() {
        return clamp(persona == null ? 65 : persona.emotionBaselineValence,0,100);
    }

    private int baselineEnergy() {
        return clamp(persona == null ? 65 : persona.emotionBaselineEnergy,0,100);
    }

    private int baselinePatience() {
        return clamp(persona == null ? 65 : persona.emotionBaselinePatience,0,100);
    }

    private String moodLabel(MoodState mood) {
        if (mood.patience <= 30 && mood.valence < 55) return "烦躁";
        if (mood.valence >= 75 && mood.energy >= 65) return "开心";
        if (mood.energy <= 25) return "困倦";
        if (mood.valence <= 35) return "低落";
        if (mood.patience <= 45) return "闹别扭";
        return "平静";
    }

    private String relationLabel(RelationState relation) {
        if (isBotOwner(relation.userID)) return "妈妈";
        if (relation.annoyance >= 70) return "讨厌";
        if (relation.affinity >= 85 && relation.annoyance < 40) return "亲近";
        if (relation.trust >= 75 && relation.annoyance < 50) return "信任";
        if (relation.affinity < 40) return "疏远";
        return "普通";
    }

    private String labelForReason(String reason) {
        String text = safe(reason);
        if (text.contains("讨厌") || text.contains("冒名") || text.contains("冒充")
                || text.contains("攻击") || text.contains("骚扰")) {
            return "讨厌";
        }
        if (text.contains("喜欢") || text.contains("亲近") || text.contains("关心")) {
            return "亲近";
        }
        if (text.contains("信任")) return "信任";
        return "在意";
    }

    private String intimacyPrompt(RelationState relation) {
        if (relation.affinity >= config.intimacyVeryCloseAffinity) {
            return "亲密程度：非常亲近。对方提出抱抱、牵手、贴贴、靠肩、摸头、膝枕等日常亲密举动时，"
                    +"可以自然接受、害羞回应或反过来撒娇，不要本能拒绝；只有露骨性内容或现实隐私需要明确挡住。";
        }
        if (relation.affinity >= config.intimacyCloseAffinity) {
            return "亲密程度：关系亲近。可以接受轻微亲密和暧昧互动，例如靠近、牵手、抱一下、摸头，"
                    +"可以害羞、别扭或吐槽，但不要冷冰冰地拒绝。";
        }
        if (relation.affinity >= 60) {
            return "亲密程度：熟悉。可以接住普通玩笑和轻微暧昧，但明显亲密举动要先表现出害羞、犹豫或转移话题。";
        }
        return "亲密程度：还不算亲近。亲密举动要保持距离，先害羞、吐槽或转移话题，不要表现成恋人式亲近。";
    }

    private String levelText(double value) {
        if (value >= 80) return "很高";
        if (value >= 65) return "较高";
        if (value >= 45) return "中等";
        if (value >= 30) return "偏低";
        return "很低";
    }

    private String formatDelta(double value) {
        if (Math.abs(value) < 0.001) return "0";
        String number = Math.abs(value - Math.rint(value)) < 0.001
                ? String.valueOf((long) Math.rint(value))
                : String.format(Locale.CHINA,"%.1f",value);
        return value > 0 ? "+"+number : number;
    }

    private int clamp(int value,int min,int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private double clampDouble(double value,double min,double max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private double clamp01(double value) {
        if (value < 0) return 0;
        if (value > 1) return 1;
        return value;
    }

    private double delta(JSONObject delta,String key) {
        if (delta == null) return 0;
        double value = delta.getDoubleValue(key);
        if (value > config.emotionAnalyzeMaxDelta) value = config.emotionAnalyzeMaxDelta;
        if (value < -config.emotionAnalyzeMaxDelta) value = -config.emotionAnalyzeMaxDelta;
        return value;
    }

    private int intDelta(JSONObject delta,String key) {
        if (delta == null) return 0;
        return delta.getIntValue(key);
    }

    private JSONObject parseJson(String content) {
        String raw = safe(content).trim();
        if (raw.isEmpty()) return null;
        try {
            return JSON.parseObject(raw);
        } catch (Exception ignored) {
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        try {
            return JSON.parseObject(raw.substring(start,end + 1));
        } catch (Exception ignored) {
            return null;
        }
    }

    private JSONObject message(String role,String content) {
        JSONObject message = new JSONObject(true);
        message.put("role",role);
        message.put("content",content);
        return message;
    }

    private String relationKey(long groupID,long userID) {
        return groupID+"-"+userID;
    }

    private boolean matchesRoleName(String value) {
        String text = safe(value).trim();
        if (text.isEmpty()) return false;
        for (String alias : roleAliases()) {
            if (!alias.isEmpty() && alias.equalsIgnoreCase(text)) return true;
        }
        return false;
    }

    private List<String> roleAliases() {
        List<String> result = new ArrayList<>();
        if (persona == null) return result;
        if (persona.name != null && !persona.name.isEmpty()) result.add(persona.name);
        for (String alias : persona.aliases) {
            if (alias != null && !alias.trim().isEmpty() && !result.contains(alias.trim())) {
                result.add(alias.trim());
            }
        }
        return result;
    }

    private String trimReason(String reason) {
        String value = safe(reason).trim();
        if (value.isEmpty()) return "";
        int max = Math.max(20,config.emotionReasonMaxChars);
        return value.length() > max ? value.substring(0,max) : value;
    }

    private String limitText(String value,int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0,max)+"...";
    }

    private long dayStart(long time) {
        TimeZone zone = TimeZone.getTimeZone(config.timeZone == null
                || config.timeZone.trim().isEmpty() ? "Asia/Shanghai" : config.timeZone.trim());
        Calendar calendar = Calendar.getInstance(zone,Locale.CHINA);
        calendar.setTime(new Date(time));
        calendar.set(Calendar.HOUR_OF_DAY,0);
        calendar.set(Calendar.MINUTE,0);
        calendar.set(Calendar.SECOND,0);
        calendar.set(Calendar.MILLISECOND,0);
        return calendar.getTimeInMillis();
    }

    private int dayKey(long time) {
        TimeZone zone = TimeZone.getTimeZone(config.timeZone == null
                || config.timeZone.trim().isEmpty() ? "Asia/Shanghai" : config.timeZone.trim());
        Calendar calendar = Calendar.getInstance(zone,Locale.CHINA);
        calendar.setTime(new Date(time));
        return calendar.get(Calendar.YEAR) * 10000
                + (calendar.get(Calendar.MONTH) + 1) * 100
                + calendar.get(Calendar.DAY_OF_MONTH);
    }

    private double moveToward(double value,double target,int steps) {
        if (value == target) return value;
        double step = Math.copySign(1.0,value < target ? 1.0 : -1.0);
        for (int i = 0; i < steps; i++) {
            if (value == target) break;
            value += step;
        }
        return value;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private StorageService storage() {
        return plugin.getServer().getStorage();
    }
}
