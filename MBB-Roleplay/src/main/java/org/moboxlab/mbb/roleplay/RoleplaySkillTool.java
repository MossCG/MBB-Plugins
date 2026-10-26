package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * 把旧动作技能适配成原生工具。
 *
 * toolEnable 打开后，角色不再走独立 actions 协议，统一使用 tools/tool_calls。
 */
public class RoleplaySkillTool implements RoleplayTool {
    private final RoleplayService service;
    private final RoleplaySkill skill;
    private final JSONObject schema;

    private RoleplaySkillTool(RoleplayService service,RoleplaySkill skill,JSONObject schema) {
        this.service = service;
        this.skill = skill;
        this.schema = schema;
    }

    public static RoleplaySkillTool create(RoleplayService service,String id) {
        if (service == null || id == null) return null;
        RoleplaySkill skill = service.skillRegistry().get(id);
        if (skill == null) return null;
        return new RoleplaySkillTool(service,skill,schema(id,skill.description()));
    }

    @Override
    public String name() {
        return skill.id();
    }

    public String category() {
        return "action";
    }

    @Override
    public String description() {
        return skill.description();
    }

    @Override
    public JSONObject schema() {
        return schema;
    }

    @Override
    public boolean sideEffect() {
        return skill.sideEffect();
    }

    @Override
    public int maxCallsPerTurn() {
        if ("draw".equals(skill.id()) || "poke-back".equals(skill.id())) return 1;
        return 2;
    }

    @Override
    public RoleplayToolResult execute(RoleplayToolContext context,JSONObject args) {
        RoleplaySkillContext skillContext = new RoleplaySkillContext();
        skillContext.groupID = context.groupID;
        skillContext.userID = context.userID;
        skillContext.messageID = context.messageID;
        skillContext.selfID = context.selfID;
        skillContext.userName = context.userName;
        skillContext.relationship = context.relationship;
        skillContext.message = context.message;
        skillContext.args = args == null ? new JSONObject(true) : args;
        skillContext.state = service.state(context.groupID);
        RoleplaySkillResult result = skill.execute(skillContext);
        if (result == null) return RoleplayToolResult.failed(skill.id()+" 没有返回结果");
        if (!result.success) return RoleplayToolResult.failed(result.summary);
        if (!result.relevant) {
            return RoleplayToolResult.failed(skill.id()+" 没有被实际执行，缺少必要参数或触发条件");
        }
        RoleplayToolResult toolResult = RoleplayToolResult.ok(result.summary);
        toolResult.data.put("skill",skill.id());
        if ("sticker".equals(skill.id())) toolResult.data.put("canEndTurn",true);
        if (result.extraText != null && !result.extraText.isEmpty()) {
            toolResult.data.put("extraText",result.extraText);
        }
        return toolResult;
    }

    private static JSONObject schema(String id,String description) {
        if ("reminder".equals(id)) {
            JSONObject properties = new JSONObject(true);
            properties.put("action",property("string","create / edit / delete"));
            properties.put("id",property("integer","要修改或删除的提醒 ID"));
            properties.put("time",property("string","触发时间，格式 yyyy-MM-dd HH:mm:ss"));
            properties.put("task",property("string","提醒内容"));
            properties.put("target",property("string","user 提醒当前群友，self 提醒角色自己"));
            return function(id,description,properties,new String[0]);
        }
        if ("memory".equals(id) || "global-memory".equals(id)) {
            JSONObject properties = new JSONObject(true);
            properties.put("content",property("string","值得长期保留的事实或设定"));
            return function(id,description,properties,new String[]{"content"});
        }
        if ("sticker".equals(id)) {
            JSONObject properties = new JSONObject(true);
            properties.put("tags",property("string","可选，英文逗号分隔的表情标签；留空随机选择"));
            return function(id,description,properties,new String[0]);
        }
        if ("poke-back".equals(id)) {
            return function(id,description,new JSONObject(true),new String[0]);
        }
        if ("draw".equals(id)) {
            JSONObject brief = new JSONObject(true);
            brief.put("type","object");
            brief.put("additionalProperties",false);
            JSONObject briefProperties = new JSONObject(true);
            briefProperties.put("subject",property("string","画面主体"));
            briefProperties.put("characters",property("array","需要明确身份的角色名数组"));
            briefProperties.put("action",property("string","动作"));
            briefProperties.put("expression",property("string","表情"));
            briefProperties.put("outfit",property("string","服装"));
            briefProperties.put("scene",property("string","环境、背景物件、光线"));
            briefProperties.put("composition",property("string","构图和视角"));
            briefProperties.put("mustInclude",property("array","必须出现的元素"));
            briefProperties.put("mustExclude",property("array","禁止出现的元素"));
            brief.put("properties",briefProperties);
            JSONObject properties = new JSONObject(true);
            properties.put("brief",brief);
            properties.put("size",property("string","square / landscape / portrait / avatar"));
            properties.put("camera",property("string","selfie 或 photo，普通生图省略"));
            properties.put("prompt",property("string","兼容旧调用的自然语言画面描述"));
            return function(id,description,properties,new String[0]);
        }
        return function(id,description,new JSONObject(true),new String[0]);
    }

    private static JSONObject function(String name,String description,JSONObject properties,
                                       String[] required) {
        JSONObject function = new JSONObject(true);
        function.put("name",name);
        function.put("description",description);
        JSONObject parameters = new JSONObject(true);
        parameters.put("type","object");
        parameters.put("properties",properties == null ? new JSONObject(true) : properties);
        JSONArray requiredArray = new JSONArray();
        if (required != null) {
            for (String item : required) {
                if (item != null && !item.isEmpty()) requiredArray.add(item);
            }
        }
        parameters.put("required",requiredArray);
        parameters.put("additionalProperties",false);
        function.put("parameters",parameters);
        JSONObject schema = new JSONObject(true);
        schema.put("type","function");
        schema.put("function",function);
        return schema;
    }

    private static JSONObject property(String type,String description) {
        JSONObject property = new JSONObject(true);
        property.put("type",type);
        property.put("description",description);
        return property;
    }
}
