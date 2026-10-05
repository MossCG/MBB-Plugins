package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONObject;

/**
 * 执行层请求的一次技能调用
 */
public class RoleplaySkillCall {
    public String type = "";
    public JSONObject args = new JSONObject(true);

    public RoleplaySkillCall(String type) {
        this.type = type == null ? "" : type.trim();
    }
}
