package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Plugin;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 角色设定
 */
public class RoleplayPersona {
    public String name = "角色";
    public String identity = "";
    public String worldview = "";
    public String school = "";
    public String club = "";
    public String personality = "";
    public String speechStyle = "";
    public String behavior = "";
    public List<String> catchphrases = new ArrayList<>();
    public List<String> aliases = new ArrayList<>();
    public List<String> memes = new ArrayList<>();
    public List<String> interests = new ArrayList<>();
    public List<String> dislikes = new ArrayList<>();
    public List<String> relationships = new ArrayList<>();
    public List<String> otherStudents = new ArrayList<>();
    public List<String> terminology = new ArrayList<>();
    public List<String> storyMemory = new ArrayList<>();

    public static RoleplayPersona load(Plugin plugin,String fileName) {
        RoleplayPersona persona = new RoleplayPersona();
        try {
            String text = new String(Files.readAllBytes(Paths.get(plugin.getDataFolder()+"/"+fileName)),StandardCharsets.UTF_8);
            JSONObject json = JSONObject.parseObject(text);
            if (json == null) throw new IllegalArgumentException("角色设定不是合法 JSON");
            persona.name = safe(json.getString("name"),"角色");
            persona.identity = safe(json.getString("identity"),"");
            persona.worldview = safe(json.getString("worldview"),"");
            persona.school = safe(json.getString("school"),"");
            persona.club = safe(json.getString("club"),"");
            persona.personality = safe(json.getString("personality"),"");
            persona.speechStyle = safe(json.getString("speechStyle"),"");
            persona.behavior = safe(json.getString("behavior"),"");
            persona.catchphrases = readList(json.getJSONArray("catchphrases"));
            persona.aliases = readList(json.getJSONArray("aliases"));
            persona.memes = readList(json.getJSONArray("memes"));
            if (persona.aliases.isEmpty()) {
                persona.aliases.add(persona.name);
                if (persona.name.contains("爱丽丝")) persona.aliases.add("爱丽丝");
            }
            persona.interests = readList(json.getJSONArray("interests"));
            persona.dislikes = readList(json.getJSONArray("dislikes"));
            persona.relationships = readList(json.getJSONArray("relationships"));
            persona.otherStudents = readList(json.getJSONArray("otherStudents"));
            persona.terminology = readList(json.getJSONArray("terminology"));
            persona.storyMemory = readList(json.getJSONArray("storyMemory"));
            mergeSharedStudents(plugin,persona);
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
                +(worldview.isEmpty() ? "" : "世界观："+worldview+"\n")
                +(school.isEmpty() ? "" : "学园："+school+"\n")
                +(club.isEmpty() ? "" : "社团："+club+"\n")
                +"性格："+personality+"\n"
                +"说话方式："+speechStyle+"\n"
                +(catchphrases.isEmpty() ? "" : "口癖："+String.join("、",catchphrases)+"\n")
                +(aliases.isEmpty() ? "" : "称呼："+String.join("、",aliases)+"\n")
                +(memes.isEmpty() ? "" : "了解的梗："+String.join("；",memes)+"\n")
                +(relationships.isEmpty() ? "" : "关系："+String.join("；",relationships)+"\n")
                +(otherStudents.isEmpty() ? "" : "了解的学生："+String.join("；",otherStudents)+"\n")
                +(terminology.isEmpty() ? "" : "专有名词："+String.join("；",terminology)+"\n")
                +(storyMemory.isEmpty() ? "" : "剧情记忆："+String.join("；",storyMemory)+"\n")
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

    private static void mergeSharedStudents(Plugin plugin,RoleplayPersona persona) {
        try {
            String path = plugin.getDataFolder()+"/students.json";
            if (!Files.exists(Paths.get(path))) return;
            String text = new String(Files.readAllBytes(Paths.get(path)),StandardCharsets.UTF_8);
            JSONObject json = JSONObject.parseObject(text);
            if (json == null) return;
            List<String> shared = readList(json.getJSONArray("students"));
            Set<String> names = new HashSet<>();
            for (String item : persona.otherStudents) names.add(studentKey(item));
            for (String item : shared) {
                String key = studentKey(item);
                if (key.isEmpty() || names.contains(key)) continue;
                persona.otherStudents.add(item);
                names.add(key);
            }
        } catch (Exception e) {
            plugin.getLogger().sendWarn("读取共享学生设定失败："+e.getMessage());
        }
    }

    private static String studentKey(String value) {
        if (value == null) return "";
        String text = value.trim();
        int index = text.indexOf('（');
        if (index < 0) index = text.indexOf(':');
        if (index < 0) index = text.indexOf('：');
        return index < 0 ? text : text.substring(0,index).trim();
    }

    private static String safe(String value,String defaultValue) {
        return value == null ? defaultValue : value;
    }
}
