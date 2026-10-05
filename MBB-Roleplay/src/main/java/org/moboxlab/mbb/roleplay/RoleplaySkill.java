package org.moboxlab.mbb.roleplay;

import java.util.List;

/**
 * 角色技能
 *
 * 技能分两段执行：状态类在执行层之前跑，结果作为事实喂给模型；
 * 表达类由执行层和正文一起决定，发送前执行。
 */
public interface RoleplaySkill {

    String id();

    String name();

    /** 给路由层看的一句话说明 */
    String description();

    /** 免费预筛关键词，命中才让路由层考虑这个技能 */
    List<String> triggers();

    Phase phase();

    /** 有副作用的技能失败必须回报给执行层，不能静默 */
    boolean sideEffect();

    /** 只在本轮选中时注入执行层的提示片段 */
    String promptFragment(RoleplaySkillContext context);

    RoleplaySkillResult execute(RoleplaySkillContext context);

    enum Phase {
        /** 状态类：执行层之前执行 */
        PRE,
        /** 表达类：执行层之后执行 */
        POST
    }
}
