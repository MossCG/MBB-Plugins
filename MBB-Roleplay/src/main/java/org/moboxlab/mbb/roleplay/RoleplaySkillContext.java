package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONObject;

/**
 * 技能执行上下文
 */
public class RoleplaySkillContext {
    public long groupID;
    public long userID;
    public long messageID;
    public long selfID;
    public String userName = "";
    public String relationship = "";
    /** 当前消息文本，戳一戳事件里是描述文本 */
    public String message = "";
    /** 执行层给出的参数，PRE 技能由路由层填充，POST 技能由执行层填充 */
    public JSONObject args = new JSONObject(true);
    public RoleplayConversationState state;

    public String arg(String key) {
        String value = args == null ? null : args.getString(key);
        return value == null ? "" : value.trim();
    }

    public long argLong(String key) {
        return args == null ? 0L : args.getLongValue(key);
    }
}
