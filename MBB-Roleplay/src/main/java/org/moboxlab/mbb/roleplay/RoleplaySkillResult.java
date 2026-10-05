package org.moboxlab.mbb.roleplay;

/**
 * 技能执行结果
 *
 * 因为状态类技能先执行，模型拿到的是"已经发生的事实"，无法凭空声称技能成功。
 */
public class RoleplaySkillResult {
    public final String skillId;
    public boolean success = true;
    /** 是否真的用上了，没用上就不进提示词 */
    public boolean relevant = true;
    /** 给执行层看的事实描述，例如"已创建提醒 #12，明天 08:00 提醒开会" */
    public String summary = "";
    /** 需要额外发送的文本，例如提醒创建确认 */
    public String extraText = "";

    public RoleplaySkillResult(String skillId) {
        this.skillId = skillId;
    }

    public static RoleplaySkillResult ok(String skillId,String summary) {
        RoleplaySkillResult result = new RoleplaySkillResult(skillId);
        result.summary = summary == null ? "" : summary;
        return result;
    }

    public static RoleplaySkillResult failed(String skillId,String summary) {
        RoleplaySkillResult result = new RoleplaySkillResult(skillId);
        result.success = false;
        result.summary = summary == null ? "" : summary;
        return result;
    }

    public static RoleplaySkillResult ignored(String skillId) {
        RoleplaySkillResult result = new RoleplaySkillResult(skillId);
        result.relevant = false;
        return result;
    }
}
