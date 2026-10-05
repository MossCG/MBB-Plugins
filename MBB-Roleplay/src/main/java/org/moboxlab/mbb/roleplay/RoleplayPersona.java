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
    public static class StudentProfile {
        public String name = "";
        public String school = "";
        public String club = "";
        public String appearance = "";
        public String description = "";
        public List<String> aliases = new ArrayList<>();
        public List<String> visualTags = new ArrayList<>();
    }

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
    public List<StudentProfile> studentProfiles = new ArrayList<>();
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

    public String visionReferenceText() {
        if (studentProfiles.isEmpty()) return "";
        StringBuilder builder = new StringBuilder("蔚蓝档案学生外貌参考，只用于判断图片中的候选角色，不能只凭单一发色确定：\n");
        int count = 0;
        for (StudentProfile profile : studentProfiles) {
            if (profile == null || profile.name.isEmpty()) continue;
            String appearance = safe(profile.appearance,"");
            String description = safe(profile.description,"");
            if (appearance.isEmpty() && description.isEmpty()) continue;
            String line = "- "+profile.name;
            if (!profile.school.isEmpty() || !profile.club.isEmpty()) {
                line += "（"+safe(profile.school,"")+"/"+safe(profile.club,"")+"）";
            }
            line += "：";
            if (!appearance.isEmpty()) line += appearance;
            if (!appearance.isEmpty() && !description.isEmpty()) line += "；";
            if (!description.isEmpty()) line += description;
            if (!profile.visualTags.isEmpty()) line += "；视觉标签："+String.join(",",profile.visualTags);
            line += "\n";
            if (builder.length()+line.length() > 32000) break;
            builder.append(line);
            count++;
        }
        return count == 0 ? "" : builder.toString();
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
            JSONArray shared = json.getJSONArray("students");
            if (shared == null) return;
            Set<String> names = new HashSet<>();
            for (String item : persona.otherStudents) names.add(studentKey(item));
            for (Object value : shared) {
                StudentProfile profile = parseStudentProfile(value);
                if (profile == null || profile.name.isEmpty()) continue;
                String key = studentKey(profile.name);
                if (key.isEmpty() || names.contains(key)) continue;
                persona.otherStudents.add(profileText(profile));
                persona.studentProfiles.add(profile);
                names.add(key);
            }
        } catch (Exception e) {
            plugin.getLogger().sendWarn("读取共享学生设定失败："+e.getMessage());
        }
    }

    private static StudentProfile parseStudentProfile(Object value) {
        if (value instanceof JSONObject) {
            JSONObject json = (JSONObject)value;
            StudentProfile profile = new StudentProfile();
            profile.name = safe(json.getString("name"),"");
            profile.school = safe(json.getString("school"),"");
            profile.club = safe(json.getString("club"),"");
            profile.appearance = safe(json.getString("appearance"),"");
            profile.description = safe(json.getString("description"),"");
            profile.aliases = readList(json.getJSONArray("aliases"));
            profile.visualTags = readList(json.getJSONArray("visualTags"));
            return profile;
        }
        String text = value == null ? "" : String.valueOf(value).trim();
        if (text.isEmpty()) return null;
        StudentProfile profile = new StudentProfile();
        int start = text.indexOf('（');
        int end = text.indexOf('）');
        int colon = text.indexOf('：');
        if (start >= 0) {
            profile.name = text.substring(0,start).trim();
            if (end > start) {
                String belong = text.substring(start+1,end).trim();
                int split = belong.indexOf('/');
                if (split >= 0) {
                    profile.school = belong.substring(0,split).trim();
                    profile.club = belong.substring(split+1).trim();
                } else {
                    profile.club = belong;
                }
            }
        } else if (colon >= 0) {
            profile.name = text.substring(0,colon).trim();
        } else {
            profile.name = text;
        }
        if (colon >= 0 && colon+1 < text.length()) {
            profile.description = text.substring(colon+1).trim();
        }
        return profile;
    }

    private static String profileText(StudentProfile profile) {
        StringBuilder builder = new StringBuilder(profile.name);
        if (!profile.school.isEmpty() || !profile.club.isEmpty()) {
            builder.append("（").append(safe(profile.school,"")).append("/")
                    .append(safe(profile.club,"")).append("）");
        }
        builder.append("：").append(safe(profile.description,""));
        if (!profile.appearance.isEmpty()) builder.append("；外貌：").append(profile.appearance);
        return builder.toString();
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
