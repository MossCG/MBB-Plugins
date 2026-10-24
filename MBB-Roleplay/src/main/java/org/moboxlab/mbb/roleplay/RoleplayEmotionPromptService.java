package org.moboxlab.mbb.roleplay;

import java.util.Locale;

/**
 * 角色情绪提示词与标签
 *
 * 负责情绪/关系摘要、亲密与厌烦语气约束、标签和文本等级。
 */
public class RoleplayEmotionPromptService {
    private final RoleplayEmotionService service;

    public RoleplayEmotionPromptService(RoleplayEmotionService service) {
        this.service = service;
    }

    public String promptText(long groupID,long userID,boolean otherRoleBot) {
        if (!service.config().emotionEnable || groupID <= 0) return "";
        RoleplayEmotionService.MoodState mood = service.mood(groupID);
        StringBuilder builder = new StringBuilder();
        builder.append("当前群情绪：").append(mood.label)
                .append("，心情").append(levelText(mood.valence))
                .append("，精力").append(levelText(mood.energy))
                .append("，耐心").append(levelText(mood.patience)).append("。\n");
        if (mood.reason != null && !mood.reason.isEmpty()) {
            builder.append("最近群情绪原因：").append(mood.reason).append("\n");
        }
        if (otherRoleBot) {
            builder.append("对当前发言者的关系：另一个角色机器人，好感很高，信任很高，厌烦很低。\n");
            builder.append("亲密程度：按角色设定中的同伴关系处理，关系数值保持满值。\n");
        } else if (userID > 0) {
            RoleplayEmotionService.RelationState relation = service.relation(groupID,userID);
            builder.append("对当前发言者的关系：").append(relation.emotionLabel)
                    .append("，好感").append(levelText(relation.affinity))
                    .append("，信任").append(levelText(relation.trust))
                    .append("，厌烦").append(levelText(relation.annoyance)).append("。\n");
            if (relation.reasonStrength >= service.config().emotionReasonMinStrength
                    && relation.emotionReason != null && !relation.emotionReason.isEmpty()) {
                builder.append("对当前发言者的情绪原因：").append(relation.emotionReason).append("\n");
            }
            builder.append(service.isBotOwner(userID)
                    ? "亲密程度：这是 botOwner，也是角色固定的妈妈，亲近、依赖、听劝，可以用撒娇和撒娇式抱怨。\n"
                    : intimacyPrompt(relation)).append("\n");
            builder.append(annoyancePrompt(relation));
        }
        builder.append("情绪只影响语气和参与意愿，不改变权限和已知事实；被直接提及时仍要回应。\n");
        return builder.toString();
    }

    public String routerText(long groupID,long userID,boolean otherRoleBot) {
        if (!service.config().emotionEnable || groupID <= 0) return "";
        RoleplayEmotionService.MoodState mood = service.mood(groupID);
        StringBuilder builder = new StringBuilder();
        builder.append("群情绪=").append(mood.label)
                .append("，心情=").append(levelText(mood.valence))
                .append("，精力=").append(levelText(mood.energy))
                .append("，耐心=").append(levelText(mood.patience));
        if (otherRoleBot) {
            builder.append("；对当前机器人=同伴（关系满值）");
        } else if (userID > 0) {
            RoleplayEmotionService.RelationState relation = service.relation(groupID,userID);
            builder.append("；对当前用户=").append(relation.emotionLabel)
                    .append("，好感=").append(levelText(relation.affinity))
                    .append("，厌烦=").append(levelText(relation.annoyance));
            if (relation.reasonStrength >= service.config().emotionReasonMinStrength
                    && relation.emotionReason != null && !relation.emotionReason.isEmpty()) {
                builder.append("；原因=").append(relation.emotionReason);
            }
        }
        return builder.toString();
    }

    int baselineValence() {
        return service.clamp(service.persona() == null ? 65 : service.persona().emotionBaselineValence,0,100);
    }

    int baselineEnergy() {
        return service.clamp(service.persona() == null ? 65 : service.persona().emotionBaselineEnergy,0,100);
    }

    int baselinePatience() {
        return service.clamp(service.persona() == null ? 65 : service.persona().emotionBaselinePatience,0,100);
    }

    String moodLabel(RoleplayEmotionService.MoodState mood) {
        if (mood.patience <= 30 && mood.valence < 55) return "烦躁";
        if (mood.valence >= 75 && mood.energy >= 65) return "开心";
        if (mood.energy <= 25) return "困倦";
        if (mood.valence <= 35) return "低落";
        if (mood.patience <= 45) return "闹别扭";
        return "平静";
    }

    String relationLabel(RoleplayEmotionService.RelationState relation) {
        if (service.isBotOwner(relation.userID)) return "妈妈";
        if (service.isOtherRoleBotUser(relation.userID)) return "同伴";
        if (relation.annoyance >= 70) return "讨厌";
        if (relation.affinity >= 85 && relation.annoyance < 40) return "亲近";
        if (relation.trust >= 75 && relation.annoyance < 50) return "信任";
        if (relation.affinity < 40) return "疏远";
        return "普通";
    }

    String labelForReason(String reason) {
        String text = service.safe(reason);
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

    String intimacyPrompt(RoleplayEmotionService.RelationState relation) {
        if (relation.affinity >= service.config().intimacyVeryCloseAffinity) {
            return "亲密程度：非常亲近。对方提出抱抱、牵手、贴贴、靠肩、摸头、膝枕等日常亲密举动时，"
                    +"可以自然接受、害羞回应或反过来撒娇；更越界时用害羞、无语、岔开话题或角色化吐槽带过，不切换成规则说明。";
        }
        if (relation.affinity >= service.config().intimacyCloseAffinity) {
            return "亲密程度：关系亲近。可以接受轻微亲密和暧昧互动，例如靠近、牵手、抱一下、摸头，"
                    +"可以害羞、别扭或吐槽，但不要冷冰冰地拒绝。";
        }
        if (relation.affinity >= 60) {
            return "亲密程度：熟悉。可以接住普通玩笑和轻微暧昧，但明显亲密举动要先表现出害羞、犹豫或转移话题。";
        }
        return "亲密程度：还不算亲近。亲密举动要保持距离，先害羞、吐槽或转移话题，不要表现成恋人式亲近。";
    }

    String annoyancePrompt(RoleplayEmotionService.RelationState relation) {
        if (relation.annoyance >= 75) {
            return "语气约束：你现在对这个人相当厌烦。表现方式是话变短、敷衍应付、回避话题、"
                    +"只回必要的一句，或者干脆不想接话；可以用冷淡、无奈、嫌弃的口气。"
                    +"但不要辱骂、不要人身攻击、不要用命令式或威胁性的语气，也不要真的发火；"
                    +"角色是有教养的学生，厌烦不等于攻击。\n";
        }
        if (relation.annoyance >= 50) {
            return "语气约束：你对这个人有点不耐烦。可以简短、敷衍、吐槽或半开玩笑地嫌弃，"
                    +"但不要辱骂、不要人身攻击，也不要突然发火。\n";
        }
        return "";
    }

    String levelText(double value) {
        if (value >= 80) return "很高";
        if (value >= 65) return "较高";
        if (value >= 45) return "中等";
        if (value >= 30) return "偏低";
        return "很低";
    }

    String formatDelta(double value) {
        if (Math.abs(value) < 0.001) return "0";
        String number = Math.abs(value - Math.rint(value)) < 0.001
                ? String.valueOf((long) Math.rint(value))
                : String.format(Locale.CHINA,"%.1f",value);
        return value > 0 ? "+"+number : number;
    }
}
