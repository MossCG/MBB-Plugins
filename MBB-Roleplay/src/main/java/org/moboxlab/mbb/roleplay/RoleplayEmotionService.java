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
    static final String MOOD_TABLE = "plugin_mbb_roleplay_mood";
    static final String RELATION_TABLE = "plugin_mbb_roleplay_relation";
    static final String EVENT_TABLE = "plugin_mbb_roleplay_emotion_event";
    static final long DAY_MILLIS = 86400000L;

    private final Plugin plugin;
    private final RoleplayService service;
    private final RoleplayMemberService memberService;
    private final RoleplayEmotionStateRepository emotionStateRepository;
    private final RoleplayEmotionPromptService emotionPromptService;
    private final RoleplayEmotionAnalyzer emotionAnalyzer;
    private final RoleplayRelationService relationService;
    private final RoleplayMoodService moodService;
    private volatile RoleplayConfig config;
    private volatile RoleplayPersona persona;
    final Map<Long,MoodState> moodCache = new ConcurrentHashMap<>();
    final Map<String,RelationState> relationCache = new ConcurrentHashMap<>();

    //短时间内的连续戳一戳计数，key 是 群号-用户号
    private final Map<String,long[]> pokeStreakMap = new ConcurrentHashMap<>();




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

    public RoleplayEmotionService(Plugin plugin,RoleplayConfig config,RoleplayPersona persona,
                                  RoleplayService service,RoleplayMemberService memberService) {
        this.plugin = plugin;
        this.config = config;
        this.persona = persona;
        this.service = service;
        this.memberService = memberService;
        this.emotionStateRepository = new RoleplayEmotionStateRepository(this);
        this.emotionPromptService = new RoleplayEmotionPromptService(this);
        this.emotionAnalyzer = new RoleplayEmotionAnalyzer(this);
        this.relationService = new RoleplayRelationService(this);
        this.moodService = new RoleplayMoodService(this);
    }



    public void init() {
        emotionStateRepository.init();
    }

    public synchronized JSONObject exportState() {
        return emotionStateRepository.exportState();
    }

    public synchronized void restoreState(JSONObject state) {
        emotionStateRepository.restoreState(state);
    }

    RoleplayService roleplayService() {
        return service;
    }

    RoleplayMemberService memberService() {
        return memberService;
    }

    RoleplayPersona persona() {
        return persona;
    }

    org.moboxlab.moboxbot.API.Plugin plugin() {
        return plugin;
    }

    RoleplayConfig config() {
        return config;
    }


    public String promptText(long groupID,long userID,boolean otherRoleBot) {
        return emotionPromptService.promptText(groupID,userID,otherRoleBot);
    }

    public String routerText(long groupID,long userID,boolean otherRoleBot) {
        return emotionPromptService.routerText(groupID,userID,otherRoleBot);
    }

    int baselineValence() {
        return emotionPromptService.baselineValence();
    }

    int baselineEnergy() {
        return emotionPromptService.baselineEnergy();
    }

    int baselinePatience() {
        return emotionPromptService.baselinePatience();
    }

    String moodLabel(MoodState mood) {
        return emotionPromptService.moodLabel(mood);
    }

    /**
     * 正常心情下句尾不带句号；只有烦躁、低落或闹别扭时允许保留。
     */
    boolean allowSentencePeriod(long groupID) {
        if (!config.emotionEnable || groupID <= 0) return false;
        MoodState mood = mood(groupID);
        return (mood.patience <= 30 && mood.valence < 55)
                || mood.valence <= 35
                || mood.patience <= 45;
    }

    String relationLabel(RelationState relation) {
        return emotionPromptService.relationLabel(relation);
    }

    String labelForReason(String reason) {
        return emotionPromptService.labelForReason(reason);
    }

    String levelText(double value) {
        return emotionPromptService.levelText(value);
    }

    String formatDelta(double value) {
        return emotionPromptService.formatDelta(value);
    }


    public void afterTurn(long groupID,long userID,String userName,String relationship,
                          String content,LocalEvent localEvent,boolean direct,boolean otherRoleBot) {
        emotionAnalyzer.afterTurn(groupID,userID,userName,relationship,content,localEvent,direct,otherRoleBot);
    }

    boolean allowEvent(long groupID,long userID,String type,int seconds) {
        return emotionAnalyzer.allowEvent(groupID,userID,type,seconds);
    }

    LocalEvent event(String type,String reason,boolean significant,
                     int valence,int energy,int patience,
                     double affinity,double trust,double annoyance) {
        return emotionAnalyzer.event(type,reason,significant,valence,energy,patience,
                affinity,trust,annoyance);
    }

    boolean isImpersonation(String text,String userName) {
        return emotionAnalyzer.isImpersonation(text,userName);
    }

    boolean isAttack(String text) {
        return emotionAnalyzer.isAttack(text);
    }

    boolean isPraise(String text) {
        return emotionAnalyzer.isPraise(text);
    }

    boolean isRepeatedMessage(long groupID,long userID,String content) {
        return emotionAnalyzer.isRepeatedMessage(groupID,userID,content);
    }


    boolean isBotOwner(long userID) {
        return relationService.isBotOwner(userID);
    }

    boolean isOtherRoleBotUser(long userID) {
        return relationService.isOtherRoleBotUser(userID);
    }

    boolean isFullRelationUser(long userID) {
        return relationService.isFullRelationUser(userID);
    }

    String relationKey(long groupID,long userID) {
        return relationService.relationKey(groupID,userID);
    }

    RoleplayEmotionStateRepository emotionStateRepository() {
        return emotionStateRepository;
    }

    public void reload(RoleplayConfig config,RoleplayPersona persona) {
        this.config = config;
        this.persona = persona;
        emotionStateRepository.pruneEvents();
    }

    public void clearCache() {
        moodCache.clear();
        relationCache.clear();
        emotionAnalyzer.clearCache();


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
                event = event("bot-interaction","其他角色机器人接续发言",false,0,0,-1,0,0,0.2);
            }
        } else if (isImpersonation(text,userName)
                && allowEvent(groupID,userID,"impersonation",
                Math.max(300,config.emotionEventCooldownSecond))) {
            event = event("impersonation","他在冒名顶替你",true,-3,-1,-5,-0.3,-0.25,0.5);
            event.persistReason = true;
        } else if (isAttack(text)
                && allowEvent(groupID,userID,"attack",config.emotionEventCooldownSecond)) {
            event = event("attack","他刚才说了攻击性的话",true,-6,-2,-8,-0.25,-0.2,0.4);
            event.persistReason = true;
        } else if (isPraise(text)
                && allowEvent(groupID,userID,"praise",config.emotionEventCooldownSecond)) {
            event = event("praise","他刚才夸过你",true,4,2,2,0.35,0.25,-0.1);
            event.persistReason = true;
        } else if (isRepeatedMessage(groupID,userID,text)
                && allowEvent(groupID,userID,"spam",Math.max(30,config.emotionEventCooldownSecond))) {
            event = event("spam","他连续重复发送消息",false,-2,0,-3,0,0,0.1);
        } else if (direct && allowEvent(groupID,userID,"friendly",config.emotionEventCooldownSecond)) {
            event = event("friendly","他主动找你说话了",false,2,1,1,0.15,0.1,0);
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
     * 戳一戳是主动互动的加分项：单独一次只给正反馈；
     * 只有短时间内连续戳很多次才升级成骚扰，才开始涨厌烦。
     */
    public synchronized LocalEvent observePoke(long groupID,long userID,String userName) {
        if (!config.emotionEnable || groupID <= 0 || userID <= 0) return LocalEvent.none();
        int streak = bumpPokeStreak(groupID,userID);
        boolean spam = streak >= Math.max(2,config.pokeStreakThreshold);
        String type = spam ? "poke-spam" : "poke";
        if (!allowEvent(groupID,userID,type,spam
                ? Math.max(30,config.pokeStreakWindowSecond)
                : Math.max(5,config.emotionEventCooldownSecond / 6))) {
            return LocalEvent.none();
        }
        MoodState mood = mood(groupID);
        RelationState relation = relation(groupID,userID);
        LocalEvent event = spam
                ? event(type,userName+"在短时间内连续戳了你 "+streak+" 次",true,-3,-1,-2,-0.1,-0.05,0.35)
                : event(type,userName+"戳了你一下，是主动来找你互动",false,2,1,0,0.15,0.1,0);
        LocalEvent applied = applyEvent(groupID,userID,0L,mood,relation,event,"rule");
        plugin.getLogger().sendInfo("[情绪] 群"+groupID+" 用户"+userID
                +" 事件="+type+" 连戳="+streak
                +" 心情"+formatDelta(applied.valenceDelta)
                +" 耐心"+applied.patienceDelta
                +" 好感"+formatDelta(applied.affinityDelta)
                +" 厌烦"+formatDelta(applied.annoyanceDelta)
                +" 原因="+event.reason);
        return applied;
    }

    /**
     * 统计短时间内的连续戳一戳次数，超过窗口自动重新计数
     */
    private int bumpPokeStreak(long groupID,long userID) {
        String key = groupID+"-"+userID;
        long now = System.currentTimeMillis();
        long window = Math.max(10,config.pokeStreakWindowSecond) * 1000L;
        long[] state = pokeStreakMap.get(key);
        if (state == null || now - state[0] > window) {
            if (pokeStreakMap.size() > 2000) pokeStreakMap.clear();
            state = new long[]{now,0L};
            pokeStreakMap.put(key,state);
        }
        state[1]++;
        return (int)state[1];
    }

    /**
     * 回复后异步分析语义情绪原因。回复结构不携带任何情绪字段。
     */

    /**
     * 提示词里的完整情绪和关系摘要。
     */

    /**
     * 给路由层看的短摘要。
     */

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
        result.put("mood",emotionStateRepository.moodJson(mood));
        result.put("relation",emotionStateRepository.relationJson(relation));
        result.put("events",emotionStateRepository.listEvents(groupID,userID,limit));
        return result;
    }

    public synchronized JSONObject moodStats(long groupID,int limit) {
        MoodState mood = mood(groupID);
        JSONObject result = new JSONObject(true);
        result.put("role",persona.name);
        result.put("groupID",groupID);
        result.put("mood",emotionStateRepository.moodJson(mood));
        result.put("relation",new JSONObject(true));
        result.put("events",emotionStateRepository.listEvents(groupID,0L,limit));
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
        emotionStateRepository.saveMood(mood);
        plugin.getLogger().sendInfo("[情绪] 群"+groupID+" 群情绪已重置");
    }

    public synchronized void resetRelation(long groupID,long userID) {
        RelationState relation = relation(groupID,userID);
        double oldAffinity = relation.affinity;
        double oldTrust = relation.trust;
        double oldAnnoyance = relation.annoyance;
        boolean owner = isBotOwner(userID);
        boolean otherBot = isOtherRoleBotUser(userID);
        boolean full = owner || otherBot;
        relation.affinity = full ? 100 : clamp(config.initialAffinity,0,100);
        relation.trust = full ? 100 : clamp(config.initialTrust,0,100);
        relation.annoyance = 0;
        relation.emotionLabel = owner ? "妈妈" : (otherBot ? "同伴" : "普通");
        relation.emotionReason = "";
        relation.reasonStrength = 0;
        relation.reasonSource = "";
        relation.reasonSince = 0L;
        relation.reasonExpire = 0L;
        relation.lastInteraction = System.currentTimeMillis();
        relation.updateTime = System.currentTimeMillis();
        relation.dayKey = emotionStateRepository.dayKey(relation.updateTime);
        relation.dayBaseAffinity = relation.affinity;
        relation.dayBaseTrust = relation.trust;
        relation.dayBaseAnnoyance = relation.annoyance;
        relation.dayDeltaAffinity = 0;
        relation.dayDeltaTrust = 0;
        relation.dayDeltaAnnoyance = 0;
        relation.provisionalInitial = false;
        emotionStateRepository.saveRelation(relation);
        if (oldAffinity != relation.affinity || oldTrust != relation.trust
                || oldAnnoyance != relation.annoyance) {
            emotionStateRepository.insertEvent(groupID,userID,0L,"command-reset",
                    0,0,0,relation.affinity - oldAffinity,
                    relation.trust - oldTrust,relation.annoyance - oldAnnoyance,
                    "管理员重置关系","command",relation.updateTime);
        }
        plugin.getLogger().sendInfo("[情绪] 群"+groupID+" 用户"+userID+" 关系已重置");
    }

    public synchronized void setRelationReason(long groupID,long userID,String reason) {
        RelationState relation = relation(groupID,userID);
        if (relationService.isFullRelationUser(userID)) {
            relation.emotionReason = "";
            relation.reasonStrength = 0;
            relation.reasonSource = "";
            relation.reasonSince = 0L;
            relation.reasonExpire = 0L;
            relation.emotionLabel = isBotOwner(userID) ? "妈妈" : "同伴";
            relation.provisionalInitial = false;
            relation.updateTime = System.currentTimeMillis();
            emotionStateRepository.saveRelation(relation);
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
        emotionStateRepository.saveRelation(relation);
    }

    public synchronized void setRelationAffinity(long groupID,long userID,double affinity) {
        RelationState relation = relation(groupID,userID);
        double oldAffinity = relation.affinity;
        relation.affinity = relationService.isFullRelationUser(userID) ? 100 : clampDouble(affinity,0,100);
        relation.emotionLabel = relationLabel(relation);
        relation.updateTime = System.currentTimeMillis();
        relation.dayKey = emotionStateRepository.dayKey(relation.updateTime);
        relation.dayBaseAffinity = relation.affinity;
        relation.dayDeltaAffinity = 0;
        relation.provisionalInitial = false;
        emotionStateRepository.saveRelation(relation);
        if (oldAffinity != relation.affinity) {
            emotionStateRepository.insertEvent(groupID,userID,0L,"command-set-affinity",
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

    public synchronized int valence(long groupID) {
        return mood(groupID).valence;
    }

    public synchronized double annoyance(long groupID,long userID) {
        return relation(groupID,userID).annoyance;
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
        emotionStateRepository.saveRelation(relation);
    }



    LocalEvent applyEvent(long groupID,long userID,long messageID,
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
            if (relationService.isFullRelationUser(userID)) {
                relation.affinity = 100;
                relation.trust = 100;
                relation.annoyance = 0;
                relation.emotionLabel = isBotOwner(userID) ? "妈妈" : "同伴";
                relation.emotionReason = "";
                relation.reasonStrength = 0;
                relation.reasonSource = "";
                relation.reasonSince = 0L;
                relation.reasonExpire = 0L;
                relation.provisionalInitial = false;
                relation.dayKey = emotionStateRepository.dayKey(now);
                relation.dayBaseAffinity = 100;
                relation.dayBaseTrust = 100;
                relation.dayBaseAnnoyance = 0;
                relation.dayDeltaAffinity = 0;
                relation.dayDeltaTrust = 0;
                relation.dayDeltaAnnoyance = 0;
                relation.lastInteraction = now;
                relation.updateTime = now;
                emotionStateRepository.saveRelation(relation);
            } else {
                if ((rawAffinity != 0 || rawTrust != 0 || rawAnnoyance != 0)
                        && relationReason.isEmpty()) {
                    plugin.getLogger().sendWarn("[情绪] 群"+groupID+" 用户"+userID
                            +" 关系数值变化缺少原因，已忽略关系变化");
                    rawAffinity = 0;
                    rawTrust = 0;
                    rawAnnoyance = 0;
                }
                affinity = relationService.applyDailyAffinity(relation,rawAffinity,now);
                trust = relationService.applyDailyTrust(relation,rawTrust,now);
                annoyance = relationService.applyDailyAnnoyance(relation,rawAnnoyance,now);
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
                emotionStateRepository.saveRelation(relation);
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
        emotionStateRepository.saveMood(mood);

        emotionStateRepository.insertEvent(groupID,userID,messageID,event.type,valence,energy,patience,
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














    synchronized MoodState mood(long groupID) {
        MoodState state = moodCache.get(groupID);
        if (state == null) {
            JSONObject row = storage().queryOne("SELECT * FROM `"+MOOD_TABLE+"` WHERE `groupID`=?",
                    groupID);
            state = row == null ? moodService.defaultMood(groupID) : emotionStateRepository.moodFromJson(row);
            MoodState previous = moodCache.putIfAbsent(groupID,state);
            if (previous != null) state = previous;
        }
        moodService.decayMood(state);
        return state;
    }

    synchronized RelationState relation(long groupID,long userID) {
        return relation(groupID,userID,"");
    }

    synchronized RelationState relation(long groupID,long userID,String relationship) {
        String key = relationKey(groupID,userID);
        RelationState state = relationCache.get(key);
        if (state == null) {
            JSONObject row = storage().queryOne("SELECT * FROM `"+RELATION_TABLE
                    +"` WHERE `groupID`=? AND `userID`=?",groupID,userID);
            state = row == null ? relationService.defaultRelation(groupID,userID,relationship) : emotionStateRepository.relationFromJson(row);
            RelationState previous = relationCache.putIfAbsent(key,state);
            if (previous != null) state = previous;
            if (row == null && !state.provisionalInitial) emotionStateRepository.saveRelation(state);
        }
        if (state.provisionalInitial && !safe(relationship).isEmpty()) {
            RelationState initialized = relationService.defaultRelation(groupID,userID,relationship);
            state.affinity = initialized.affinity;
            state.trust = initialized.trust;
            state.annoyance = initialized.annoyance;
            state.emotionLabel = relationLabel(state);
            state.provisionalInitial = false;
            emotionStateRepository.saveRelation(state);
        }
        relationService.decayRelation(state);
        return state;
    }



    int moveToward(int value,int target,int steps) {
        if (value == target) return value;
        for (int i = 0; i < steps; i++) {
            if (value < target) value++;
            else if (value > target) value--;
        }
        return value;
    }




























    /**
     * 厌烦时的语气约束。
     * 厌烦的表现是冷淡、敷衍、回避，不是攻击性，避免模型把厌烦演成骂人。
     */



    int clamp(int value,int min,int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    double clampDouble(double value,double min,double max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private double clamp01(double value) {
        if (value < 0) return 0;
        if (value > 1) return 1;
        return value;
    }






    boolean matchesRoleName(String value) {
        String text = safe(value).trim();
        if (text.isEmpty()) return false;
        for (String alias : roleAliases()) {
            if (!alias.isEmpty() && alias.equalsIgnoreCase(text)) return true;
        }
        return false;
    }

    List<String> roleAliases() {
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

    String trimReason(String reason) {
        String value = safe(reason).trim();
        if (value.isEmpty()) return "";
        int max = Math.max(20,config.emotionReasonMaxChars);
        return value.length() > max ? value.substring(0,max) : value;
    }




    double moveToward(double value,double target,int steps) {
        if (value == target) return value;
        double step = Math.copySign(1.0,value < target ? 1.0 : -1.0);
        for (int i = 0; i < steps; i++) {
            if (value == target) break;
            value += step;
        }
        return value;
    }

    String safe(String value) {
        return value == null ? "" : value;
    }

    StorageService storage() {
        return plugin.getServer().getStorage();
    }
}
