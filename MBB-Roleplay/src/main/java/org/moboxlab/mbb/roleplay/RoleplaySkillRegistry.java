package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 技能注册表
 *
 * 新增能力时注册一个技能即可，不需要再往执行层提示词里堆规则。
 * 技能分两段：状态类（PRE）在执行层之前跑，表达类（POST）由执行层决定后执行。
 */
public class RoleplaySkillRegistry {
    private final Map<String,RoleplaySkill> skills = new LinkedHashMap<>();
    private final RoleplayService service;

    public RoleplaySkillRegistry(RoleplayService service) {
        this.service = service;
        register(new ReminderSkill());
        register(new MemorySkill());
        register(new GlobalMemorySkill());
        register(new StickerSkill());
        register(new PokeBackSkill());
        register(new DrawSkill());
    }

    private void register(RoleplaySkill skill) {
        skills.put(skill.id(),skill);
    }

    public RoleplaySkill get(String id) {
        return id == null ? null : skills.get(id.trim());
    }

    /** 本轮开放的动作清单，执行层只能从中选 */
    public List<String> actionIds(RoleplayConfig config) {
        List<String> result = new ArrayList<>();
        for (RoleplaySkill skill : skills.values()) {
            if (skill.phase() != RoleplaySkill.Phase.POST) continue;
            if (!isEnabled(skill.id(),config)) continue;
            result.add(skill.id());
        }
        return result;
    }

    /** 技能清单，给路由层看 */
    public String catalogue(RoleplayConfig config) {
        StringBuilder builder = new StringBuilder();
        for (RoleplaySkill skill : skills.values()) {
            if (!isEnabled(skill.id(),config)) continue;
            builder.append("- ").append(skill.id()).append("：").append(skill.description()).append("\n");
        }
        return builder.toString();
    }

    /** 把执行层请求的动作转成提示词片段，未开放的动作会被忽略 */
    public String promptFragments(RoleplayConfig config,List<String> actionIds,RoleplaySkillContext context) {
        StringBuilder builder = new StringBuilder();
        for (String id : actionIds) {
            if (!isEnabled(id,config)) continue;
            RoleplaySkill skill = get(id);
            if (skill == null) continue;
            String fragment = skill.promptFragment(context);
            if (fragment == null || fragment.isEmpty()) continue;
            builder.append(fragment).append("\n");
        }
        return builder.toString();
    }

    private boolean isEnabled(String id,RoleplayConfig config) {
        if ("reminder".equals(id)) return config.reminderEnable;
        if ("memory".equals(id)) return config.activeMemory;
        if ("global-memory".equals(id)) return config.globalMemoryEnable;
        if ("sticker".equals(id)) {
            return service.plugin().getServer().getPluginManager().getService("MBB-Sticker") != null;
        }
        if ("poke-back".equals(id)) return config.pokeBackEnable;
        if ("draw".equals(id)) {
            return service.plugin().getServer().getPluginManager().getService("MBB-ComfyUI") != null;
        }
        return true;
    }

    /**
     * 定时提醒：模型自己判断要不要建、改、删提醒
     */
    private class ReminderSkill implements RoleplaySkill {
        @Override
        public String id() {
            return "reminder";
        }

        @Override
        public String name() {
            return "定时提醒";
        }

        @Override
        public String description() {
            return "创建、修改、删除当前用户的定时提醒";
        }

        @Override
        public List<String> triggers() {
            return Arrays.asList("提醒","叫我","闹钟","定时","记得");
        }

        @Override
        public RoleplaySkill.Phase phase() {
            return RoleplaySkill.Phase.POST;
        }

        @Override
        public boolean sideEffect() {
            return true;
        }

        @Override
        public String promptFragment(RoleplaySkillContext context) {
            return "需要创建提醒时输出 {\"type\":\"reminder\",\"args\":{\"action\":\"create\","
                    +"\"time\":\"yyyy-MM-dd HH:mm:ss\",\"task\":\"要提醒的内容\",\"target\":\"user\"}}；"
                    +"删除用 {\"action\":\"delete\",\"id\":12}；修改用 {\"action\":\"edit\",\"id\":12,"
                    +"\"time\":\"yyyy-MM-dd HH:mm:ss\",\"task\":\"新的提醒内容\"}。"
                    +"time 必须使用当前时区，target 用 self 表示提醒自己，用 user 表示提醒当前群友。"
                    +"没有明确 ID 时不要删除或修改。";
        }

        @Override
        public RoleplaySkillResult execute(RoleplaySkillContext context) {
            String action = context.arg("action");
            if (action.isEmpty()) action = "create";
            String text = service.getReminderService().executeAiAction(context.groupID,context.userID,
                    context.userName,context.relationship,action,context.argLong("id"),
                    context.arg("time"),context.arg("task"),context.arg("target"),context.message);
            if (text == null || text.isEmpty()) {
                return RoleplaySkillResult.failed(id(),"提醒操作没有返回结果");
            }
            // 提醒确认带艾特，走提醒服务自己的发送入口
            service.getReminderService().sendAt(context.groupID,context.userID,
                    RoleplayReminderService.REMINDER_MARKER+text);
            return RoleplaySkillResult.ok(id(),text);
        }
    }

    /**
     * 长期记忆：角色认为有值得记住的内容
     */
    private class MemorySkill implements RoleplaySkill {
        @Override
        public String id() {
            return "memory";
        }

        @Override
        public String name() {
            return "长期记忆";
        }

        @Override
        public String description() {
            return "记住值得长期保留的人物信息、群梗或自己的重要行为";
        }

        @Override
        public List<String> triggers() {
            return Arrays.asList("记住","记一下","记忆");
        }

        @Override
        public RoleplaySkill.Phase phase() {
            return RoleplaySkill.Phase.POST;
        }

        @Override
        public boolean sideEffect() {
            return true;
        }

        @Override
        public String promptFragment(RoleplaySkillContext context) {
            return "如果当前内容出现了值得长期记忆的新人物信息、稳定偏好、重要事件或你自己的重要承诺，"
                    +"输出 {\"type\":\"memory\",\"args\":{\"content\":\"要记住的内容\"}}；"
                    +"没有长期价值时不要输出，也不要解释这个动作。";
        }

        @Override
        public RoleplaySkillResult execute(RoleplaySkillContext context) {
            String content = context.arg("content");
            if (content.isEmpty()) return RoleplaySkillResult.ignored(id());
            if (service.isInducedMemory(context.message) && !service.isOwnerUser(context.userID)) {
                service.plugin().getLogger().sendWarn("[记忆] 群"+context.groupID+" 用户"+context.userID
                        +" 非 owner 诱导记忆，已忽略");
                return RoleplaySkillResult.failed(id(),"非 owner 诱导的记忆请求被忽略");
            }
            service.rememberNow(context.groupID,content);
            return RoleplaySkillResult.ok(id(),"已触发一次记忆整理");
        }
    }

    /**
     * 全局永久记忆：所有群共享
     */
    private class GlobalMemorySkill implements RoleplaySkill {
        @Override
        public String id() {
            return "global-memory";
        }

        @Override
        public String name() {
            return "永久记忆";
        }

        @Override
        public String description() {
            return "记录所有群共享、不绑定具体用户的说话方式、习惯、知识或群梗";
        }

        @Override
        public List<String> triggers() {
            return Arrays.asList("永久记忆","全局记忆");
        }

        @Override
        public RoleplaySkill.Phase phase() {
            return RoleplaySkill.Phase.POST;
        }

        @Override
        public boolean sideEffect() {
            return true;
        }

        @Override
        public String promptFragment(RoleplaySkillContext context) {
            return "如果出现了值得所有群共享、且不绑定具体用户的说话方式、语气、生活习惯、知识或注意事项，"
                    +"输出 {\"type\":\"global-memory\",\"args\":{\"content\":\"要永久记住的内容\"}}；"
                    +"不要记录个人隐私或用户专属信息。";
        }

        @Override
        public RoleplaySkillResult execute(RoleplaySkillContext context) {
            String content = context.arg("content");
            if (content.isEmpty()) return RoleplaySkillResult.ignored(id());
            if (service.isInducedMemory(context.message) && !service.isOwnerUser(context.userID)) {
                service.plugin().getLogger().sendWarn("[记忆] 群"+context.groupID+" 用户"+context.userID
                        +" 非 owner 诱导全局记忆，已忽略");
                return RoleplaySkillResult.failed(id(),"非 owner 诱导的全局记忆请求被忽略");
            }
            if (!service.getGlobalMemoryService().isLearnGroup(context.groupID)) {
                service.plugin().getLogger().sendWarn("[永久记忆] 群"+context.groupID
                        +" 不在学习白名单，忽略");
                return RoleplaySkillResult.failed(id(),"当前群不在永久记忆学习白名单");
            }
            service.getGlobalMemoryService().save("note",content,3,context.groupID,context.userID);
            return RoleplaySkillResult.ok(id(),"已写入全局永久记忆");
        }
    }

    /**
     * 表情包：可选表达，不是每句话都带
     */
    private class StickerSkill implements RoleplaySkill {
        @Override
        public String id() {
            return "sticker";
        }

        @Override
        public String name() {
            return "表情包";
        }

        @Override
        public String description() {
            return "按当前真实标签集发送一张匹配表情包";
        }

        @Override
        public List<String> triggers() {
            return new ArrayList<>();
        }

        @Override
        public RoleplaySkill.Phase phase() {
            return RoleplaySkill.Phase.POST;
        }

        @Override
        public boolean sideEffect() {
            return false;
        }

        @Override
        public String promptFragment(RoleplaySkillContext context) {
            return service.stickerPrompt();
        }

        @Override
        public RoleplaySkillResult execute(RoleplaySkillContext context) {
            String tags = context.arg("tags");
            if (tags.isEmpty()) return RoleplaySkillResult.ignored(id());
            service.sendStickerMessage(context.groupID,context.userID,tags);
            return RoleplaySkillResult.ok(id(),"已发送表情包");
        }
    }

    /**
     * 戳回去：被戳时的表达动作
     */
    private class PokeBackSkill implements RoleplaySkill {
        @Override
        public String id() {
            return "poke-back";
        }

        @Override
        public String name() {
            return "戳回去";
        }

        @Override
        public String description() {
            return "被人戳一戳时戳回去";
        }

        @Override
        public List<String> triggers() {
            return new ArrayList<>();
        }

        @Override
        public RoleplaySkill.Phase phase() {
            return RoleplaySkill.Phase.POST;
        }

        @Override
        public boolean sideEffect() {
            return false;
        }

        @Override
        public String promptFragment(RoleplaySkillContext context) {
            return "poke-back 表示戳回去。被人戳、想回敬对方，或正文里说出“戳回去”“回戳”“戳你”时，"
                    +"必须同时输出 {\"type\":\"poke-back\"}，不能只在 text 里表达。";
        }

        @Override
        public RoleplaySkillResult execute(RoleplaySkillContext context) {
            boolean success = service.getActionService().pokeBack(service.config(),
                    context.groupID,context.userID);
            return success
                    ? RoleplaySkillResult.ok(id(),"已戳回去")
                    : RoleplaySkillResult.failed(id(),"戳回去被冷却或权限跳过");
        }
    }

    /**
     * 生图：调用 MBB-ComfyUI，角色只提供 prompt 和尺寸
     */
    private class DrawSkill implements RoleplaySkill {
        @Override
        public String id() {
            return "draw";
        }

        @Override
        public String name() {
            return "生图";
        }

        @Override
        public String description() {
            return "调用 ComfyUI 按当前群生成一张图片";
        }

        @Override
        public List<String> triggers() {
            return Arrays.asList("画","生图","图片","画一张","生成图","来张图");
        }

        @Override
        public RoleplaySkill.Phase phase() {
            return RoleplaySkill.Phase.POST;
        }

        @Override
        public boolean sideEffect() {
            return true;
        }

        @Override
        public String promptFragment(RoleplaySkillContext context) {
            org.moboxlab.moboxbot.API.PluginService comfy =
                    service.plugin().getServer().getPluginManager().getService("MBB-ComfyUI");
            if (comfy == null) return "";
            JSONObject params = new JSONObject(true);
            params.put("groupID",context.groupID);
            JSONObject status = comfy.call("status",params);
            long remaining = status.getLongValue("cooldownRemaining");
            boolean busy = status.getBooleanValue("busy");
            return "draw 表示调用 ComfyUI 生图。只有用户明确要求生图时才调用；"
                    +"如果用户只给出主体，没给背景、动作、风格、构图或尺寸，可以自己补全这些细节并写入 prompt，"
                    +"不要每一项都追问；只有主体不明确、可能违规或用户要求变化时再追问。"
                    +"prompt 要写得具体细腻：主体 + 完整角色名 + 外貌特征 + 服装装备 + 动作 + 表情 + "
                    +"背景 + 构图镜头 + 风格 + 光线 + 质量词。"
                    +"如果涉及《蔚蓝档案》角色，必须写完整姓名和特征，例如“天童爱丽丝、才羽桃井、才羽绿、"
                    +"砂狼白子、小鸟游星野”等，不要只写小桃、小绿这种简称，避免生成偏差。"
                    +"每个角色在画面中只能出现一次；如果画双人，必须明确只有两名不同角色，"
                    +"不要使用 twins、clone、duplicate character、multiple views、split screen 等容易复制的标签，"
                    +"不要分身、克隆、多视角、分屏或重复同一角色。"
                    +"prompt 使用逗号分隔的英文 booru 风格标签为主，必要时保留中文全名；"
                    +"控制在 300 到 800 字符以内，不要写成自然语言段落，不要重复。"
                    +"当前群生图冷却剩余 "+remaining+" 秒，"
                    +"当前群任务中："+(busy ? "是" : "否")+"。冷却中或有任务时不要调用 draw，"
                    +"直接用角色语气说明还需要等多久。尺寸上限 "
                    +status.getIntValue("maxWidth")+"x"+status.getIntValue("maxHeight")
                    +"，默认 "+status.getIntValue("defaultWidth")+"x"+status.getIntValue("defaultHeight")
                    +"，可用 size：square / landscape / portrait / avatar。"
                    +"调用格式：{\"type\":\"draw\",\"args\":{\"prompt\":\"...\",\"size\":\"square\"}}；"
                    +"角色只提供 prompt 和尺寸，不要控制模型、steps、cfg、sampler、seed。";
        }

        @Override
        public RoleplaySkillResult execute(RoleplaySkillContext context) {
            org.moboxlab.moboxbot.API.PluginService comfy =
                    service.plugin().getServer().getPluginManager().getService("MBB-ComfyUI");
            if (comfy == null) return RoleplaySkillResult.failed(id(),"MBB-ComfyUI 未启用");
            JSONObject params = new JSONObject(true);
            if (context.args != null) params.putAll(context.args);
            params.put("groupID",context.groupID);
            params.put("userID",context.userID);
            params.put("messageID",context.messageID);
            JSONObject result = comfy.call("generate",params);
            if (result == null || !result.getBooleanValue("status")) {
                return RoleplaySkillResult.failed(id(),
                        result == null ? "生图服务没有返回结果" : result.getString("message"));
            }
            return RoleplaySkillResult.ok(id(),"已提交生图任务 "+result.getString("taskID"));
        }
    }
}
