package org.moboxlab.mbb.roleplay;

import java.util.ArrayList;
import java.util.List;

/**
 * 一轮消息的路由决策结果
 * 目前由规则决策器产出，后续替换为 AI 路由层时保持同一结构，便于逐层切换
 */
public class RoleplayRouteDecision {
    /** 被叫到的方式：direct 艾特或引用 / name 点名 / thread 延续对话 / ambient 群内话题 / none 不参与 */
    public String addressed = "none";
    public boolean reply = false;
    public double confidence = 0;
    public double chance = 0;
    public String reason = "";
    public boolean otherRoleBot = false;
    /** 由规则置位，表达层不能取消 */
    public boolean quoteRequired = false;
    /** 本轮允许的表达类动作，执行层只能从中选 */
    public List<String> actions = new ArrayList<>();
    /** 本轮要注入的资料 id，形如 students.detail:角色名 */
    public List<String> materials = new ArrayList<>();
    /** 路由层点名要挂的技能 id，目前用于日志与后续 PRE 技能 */
    public List<String> skills = new ArrayList<>();
}
