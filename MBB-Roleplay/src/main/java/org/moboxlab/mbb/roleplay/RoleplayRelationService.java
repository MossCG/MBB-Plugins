package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONObject;

import java.util.List;

/**
 * 角色用户关系
 *
 * 负责好感、信任、厌烦的日限幅、自然回落、默认关系和身份倍率。
 */
public class RoleplayRelationService {
    private final RoleplayEmotionService service;

    public RoleplayRelationService(RoleplayEmotionService service) {
        this.service = service;
    }

    double applyDailyAffinity(RoleplayEmotionService.RelationState relation,double delta,long now) {
        if (delta == 0) return 0;
        normalizeDay(relation,now);
        relation.dayDeltaAffinity += delta;
        double old = relation.affinity;
        double cap = Math.max(0.1,service.config().relationDailyMaxDelta);
        relation.affinity = service.clampDouble(relation.dayBaseAffinity
                + service.clampDouble(relation.dayDeltaAffinity,-cap,cap),0,100);
        return relation.affinity - old;
    }

    /**
     * 好感度按用户跨群共享，已有多个群时统一取最高值。
     */
    void normalizeSharedAffinity() {
        List<JSONObject> rows = service.storage().query(
                "SELECT `userID`,MAX(`affinity`) AS `maxAffinity` FROM `"
                        +RoleplayEmotionService.RELATION_TABLE+"` WHERE `groupID`>0 "
                        +"GROUP BY `userID` HAVING COUNT(*)>1");
        if (rows == null || rows.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (JSONObject row : rows) {
            double highest = row.getDoubleValue("maxAffinity");
            service.storage().update("UPDATE `"+RoleplayEmotionService.RELATION_TABLE+"` "
                            +"SET `affinity`=?,`dayBaseAffinity`=?,`dayDeltaAffinity`=0,`updateTime`=? "
                            +"WHERE `userID`=? AND `affinity`<?",
                    highest,highest,now,row.getLongValue("userID"),highest);
        }
    }

    void enforceSharedAffinity(RoleplayEmotionService.RelationState relation) {
        if (relation == null || relation.userID <= 0 || relation.groupID <= 0) return;
        if (isFullRelationUser(relation.userID)) return;
        JSONObject row = service.storage().queryOne(
                "SELECT MAX(`affinity`) AS `maxAffinity` FROM `"
                        +RoleplayEmotionService.RELATION_TABLE+"` WHERE `userID`=?",
                relation.userID);
        double highest = highestAffinity(relation.affinity,
                row == null ? 0.0 : row.getDoubleValue("maxAffinity"));
        long now = System.currentTimeMillis();
        if (highest > relation.affinity) {
            normalizeDay(relation,now);
            relation.affinity = highest;
            relation.dayBaseAffinity = highest;
            relation.dayDeltaAffinity = 0;
            relation.emotionLabel = service.relationLabel(relation);
            relation.updateTime = now;
            service.emotionStateRepository().saveRelation(relation);
        }
        service.storage().update("UPDATE `"+RoleplayEmotionService.RELATION_TABLE+"` "
                        +"SET `affinity`=?,`dayBaseAffinity`=?,`dayDeltaAffinity`=0,`updateTime`=? "
                        +"WHERE `userID`=? AND `affinity`<?",
                relation.affinity,relation.affinity,now,relation.userID,relation.affinity);
    }

    static double highestAffinity(double current,double candidate) {
        double safeCurrent = Double.isNaN(current) ? 0.0 : Math.max(0,Math.min(100,current));
        double safeCandidate = Double.isNaN(candidate) ? 0.0 : Math.max(0,Math.min(100,candidate));
        return Math.max(safeCurrent,safeCandidate);
    }

    double applyDailyTrust(RoleplayEmotionService.RelationState relation,double delta,long now) {
        if (delta == 0) return 0;
        normalizeDay(relation,now);
        relation.dayDeltaTrust += delta;
        double old = relation.trust;
        double cap = Math.max(0.1,service.config().relationDailyMaxDelta);
        relation.trust = service.clampDouble(relation.dayBaseTrust
                + service.clampDouble(relation.dayDeltaTrust,-cap,cap),0,100);
        return relation.trust - old;
    }

    double applyDailyAnnoyance(RoleplayEmotionService.RelationState relation,double delta,long now) {
        if (delta == 0) return 0;
        normalizeDay(relation,now);
        relation.dayDeltaAnnoyance += delta;
        double old = relation.annoyance;
        double cap = Math.max(0.1,service.config().relationDailyMaxDelta);
        relation.annoyance = service.clampDouble(relation.dayBaseAnnoyance
                + service.clampDouble(relation.dayDeltaAnnoyance,-cap,cap),0,100);
        return relation.annoyance - old;
    }

    void normalizeDay(RoleplayEmotionService.RelationState relation,long now) {
        int key = service.emotionStateRepository().dayKey(now);
        if (relation.dayKey == key) return;
        relation.dayKey = key;
        relation.dayBaseAffinity = relation.affinity;
        relation.dayBaseTrust = relation.trust;
        relation.dayBaseAnnoyance = relation.annoyance;
        relation.dayDeltaAffinity = 0;
        relation.dayDeltaTrust = 0;
        relation.dayDeltaAnnoyance = 0;
    }

    void decayRelation(RoleplayEmotionService.RelationState relation) {
        long now = System.currentTimeMillis();
        if (isFullRelationUser(relation.userID)) {
            relation.affinity = 100;
            relation.trust = 100;
            relation.annoyance = 0;
            relation.emotionLabel = isBotOwner(relation.userID) ? "妈妈" : "同伴";
            relation.emotionReason = "";
            relation.reasonStrength = 0;
            relation.reasonSource = "";
            relation.reasonSince = 0L;
            relation.reasonExpire = 0L;
            relation.provisionalInitial = false;
            relation.dayKey = service.emotionStateRepository().dayKey(now);
            relation.dayBaseAffinity = 100;
            relation.dayBaseTrust = 100;
            relation.dayBaseAnnoyance = 0;
            relation.dayDeltaAffinity = 0;
            relation.dayDeltaTrust = 0;
            relation.dayDeltaAnnoyance = 0;
            relation.updateTime = now;
            service.emotionStateRepository().saveRelation(relation);
            return;
        }
        if (relation.updateTime <= 0) {
            relation.updateTime = now;
            return;
        }
        long days = (now - relation.updateTime) / RoleplayEmotionService.DAY_MILLIS;
        if (days <= 0) return;
        int steps = (int) Math.min(60,days);
        double oldAffinity = relation.affinity;
        double oldTrust = relation.trust;
        double oldAnnoyance = relation.annoyance;
        relation.affinity = service.moveToward(relation.affinity,service.config().initialAffinity,steps);
        relation.trust = service.moveToward(relation.trust,service.config().initialTrust,steps);
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
        relation.emotionLabel = service.relationLabel(relation);
        relation.dayKey = service.emotionStateRepository().dayKey(now);
        relation.dayBaseAffinity = relation.affinity;
        relation.dayBaseTrust = relation.trust;
        relation.dayBaseAnnoyance = relation.annoyance;
        relation.dayDeltaAffinity = 0;
        relation.dayDeltaTrust = 0;
        relation.dayDeltaAnnoyance = 0;
        relation.updateTime = now;
        service.emotionStateRepository().saveRelation(relation);
        if (oldAffinity != relation.affinity || oldTrust != relation.trust
                || oldAnnoyance != relation.annoyance) {
            service.emotionStateRepository().insertEvent(relation.groupID,relation.userID,0L,"decay",
                    0,0,0,
                    relation.affinity - oldAffinity,relation.trust - oldTrust,
                    relation.annoyance - oldAnnoyance,
                    "长时间未互动，关系缓慢回落","decay",now);
        }
    }

    RoleplayEmotionService.RelationState defaultRelation(long groupID,long userID,String relationship) {
        RoleplayEmotionService.RelationState relation = new RoleplayEmotionService.RelationState();
        relation.groupID = groupID;
        relation.userID = userID;
        double multiplier = initialMultiplier(userID,relationship);
        boolean full = isFullRelationUser(userID);
        relation.affinity = full
                ? 100 : service.clamp((int) Math.round(service.config().initialAffinity * multiplier),0,100);
        relation.trust = full ? 100 : service.clamp((int) Math.round(service.config().initialTrust
                * Math.min(1.25,multiplier)),0,100);
        relation.annoyance = 0;
        relation.emotionLabel = service.relationLabel(relation);
        relation.lastInteraction = System.currentTimeMillis();
        relation.updateTime = relation.lastInteraction;
        relation.dayKey = service.emotionStateRepository().dayKey(relation.updateTime);
        relation.dayBaseAffinity = relation.affinity;
        relation.dayBaseTrust = relation.trust;
        relation.dayBaseAnnoyance = relation.annoyance;
        relation.provisionalInitial = service.safe(relationship).isEmpty();
        return relation;
    }

    double initialMultiplier(long userID,String relationship) {
        double multiplier = 1.0;
        if ("老师".equals(service.safe(relationship))) {
            multiplier *= service.config().initialGroupAdminMultiplier;
        }
        if (isBotAdmin(userID)) multiplier *= service.config().initialBotAdminMultiplier;
        if (isBotOwner(userID)) multiplier *= service.config().initialBotOwnerMultiplier;
        return Math.max(1.0,multiplier);
    }

    boolean isBotAdmin(long userID) {
        List<Long> admins = service.plugin().getServer().getAdminList();
        return admins != null && admins.contains(userID);
    }

    boolean isBotOwner(long userID) {
        List<Long> owners = service.plugin().getServer().getOwnerList();
        return owners != null && owners.contains(userID);
    }

    boolean isFullRelationUser(long userID) {
        return isBotOwner(userID) || isOtherRoleBotUser(userID);
    }

    boolean isOtherRoleBotUser(long userID) {
        if (userID <= 0 || service.config() == null || service.config().otherRoleBotQQs == null) return false;
        for (String item : service.config().otherRoleBotQQs.split(",")) {
            try {
                if (Long.parseLong(item.trim()) == userID) return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    String relationKey(long groupID,long userID) {
        return groupID+"-"+userID;
    }
}
