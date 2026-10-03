package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Plugin;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 角色设定
 */
public class RoleplayPersona {
    public String name = "角色";
    public String identity = "";
    public String personality = "";
    public String speechStyle = "";
    public String behavior = "";
    public List<String> interests = new ArrayList<>();
    public List<String> dislikes = new ArrayList<>();

    public static RoleplayPersona load(Plugin plugin,String fileName) {
        RoleplayPersona persona = new RoleplayPersona();
        try {
            String text = new String(Files.readAllBytes(Paths.get(plugin.getDataFolder()+"/"+fileName)),StandardCharsets.UTF_8);
            JSONObject json = JSONObject.parseObject(text);
            if (json == null) throw new IllegalArgumentException("角色设定不是合法 JSON");
            persona.name = safe(json.getString("name"),"角色");
            persona.identity = safe(json.getString("identity"),"");
            persona.personality = safe(json.getString("personality"),"");
            persona.speechStyle = safe(json.getString("speechStyle"),"");
            persona.behavior = safe(json.getString("behavior"),"");
            persona.interests = readList(json.getJSONArray("interests"));
            persona.dislikes = readList(json.getJSONArray("dislikes"));
        } catch (Exception e) {
            plugin.getLogger().sendWarn("读取角色设定失败："+e.getMessage());
        }
        return persona;
    }

    public boolean matchesInterest(String content) {
        if (content == null) return false;
        String text = content.toLowerCase();
        for (String interest : interests) {
            if (interest != null && !interest.trim().isEmpty()
                    && text.contains(interest.toLowerCase())) return true;
        }
        return false;
    }

    public String description() {
        return "角色名："+name+"\n"
                +"身份："+identity+"\n"
                +"性格："+personality+"\n"
                +"说话方式："+speechStyle+"\n"
                +"行为规则："+behavior;
    }

    private static List<String> readList(JSONArray array) {
        List<String> result = new ArrayList<>();
        if (array == null) return result;
        for (Object value : array) {
            if (value != null && !String.valueOf(value).trim().isEmpty()) {
                result.add(String.valueOf(value).trim());
            }
        }
        return result;
    }

    private static String safe(String value,String defaultValue) {
        return value == null ? defaultValue : value;
    }
}
