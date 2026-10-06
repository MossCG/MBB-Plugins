package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Plugin;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public String appearance = "";
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
    public int emotionBaselineValence = 75;
    public int emotionBaselineEnergy = 75;
    public int emotionBaselinePatience = 75;

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
            persona.appearance = safe(json.getString("appearance"),"");
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
            JSONObject emotionBaseline = json.getJSONObject("emotionBaseline");
            if (emotionBaseline != null) {
                persona.emotionBaselineValence = clamp(emotionBaseline.getIntValue("valence"),75);
                persona.emotionBaselineEnergy = clamp(emotionBaseline.getIntValue("energy"),75);
                persona.emotionBaselinePatience = clamp(emotionBaseline.getIntValue("patience"),75);
            }
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
        return coreText()+appearanceText()+studentBriefText();
    }

    /**
     * 角色核心设定，不含外貌和学生名录
     * 这两项各自作为独立资料，由预算分配器决定要不要注入
     */
    public String coreText() {
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
                +(terminology.isEmpty() ? "" : "专有名词："+String.join("；",terminology)+"\n")
                +(storyMemory.isEmpty() ? "" : "剧情记忆："+String.join("；",storyMemory)+"\n")
                +"行为规则："+behavior+"\n";
    }

    /** 角色自己的外貌 */
    public String appearanceText() {
        return appearance.isEmpty() ? "" : "自己的外貌："+appearance+"\n";
    }

    /** 学生名录，只有一句话印象，完整外貌由 studentDetailText 按需提供 */
    public String studentBriefText() {
        return otherStudents.isEmpty() ? "" : "了解的学生："+String.join("；",otherStudents)+"\n";
    }

    /**
     * 按当前消息里出现的学生名或别名，取回完整设定（含外貌）。
     * 只在被问到具体学生时注入，避免把整份图鉴塞进每一条请求。
     */
    public String studentDetailText(String text) {
        if (text == null || text.isEmpty() || studentProfiles.isEmpty()) return "";
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (StudentProfile profile : studentProfiles) {
            if (profile == null || profile.name.isEmpty()) continue;
            if (!matchesStudent(profile,text)) continue;
            String line = "- "+studentDetail(profile)+"\n";
            if (builder.length()+line.length() > 4000) break;
            builder.append(line);
            count++;
            if (count >= 6) break;
        }
        if (count == 0) return "";
        return "被提到的学生详细设定（外貌、社团、性格、关系）：\n"+builder.toString();
    }

    private static boolean matchesStudent(StudentProfile profile,String text) {
        if (profile.name.length() >= 2 && text.contains(profile.name)) return true;
        for (String alias : profile.aliases) {
            if (alias.length() >= 2 && text.contains(alias)) return true;
        }
        return false;
    }

    public String visionReferenceText() {
        if (studentProfiles.isEmpty()) return "";
        StringBuilder builder = new StringBuilder("蔚蓝档案学生外貌参考，只用于判断图片中的候选角色，不能只凭单一发色确定：\n");
        int count = 0;
        if (!appearance.isEmpty()) {
            String selfLine = "- 本人："+name;
            if (!school.isEmpty() || !club.isEmpty()) {
                selfLine += "（"+school+"/"+club+"）";
            }
            selfLine += "："+appearance+"\n";
            builder.append(selfLine);
            count++;
        }
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
            if (builder.length()+line.length() > 64000) break;
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
            Map<String,Integer> index = new HashMap<>();
            for (int i=0;i<persona.otherStudents.size();i++) {
                String key = studentKey(persona.otherStudents.get(i));
                if (!key.isEmpty() && !index.containsKey(key)) index.put(key,i);
            }
            for (Object value : shared) {
                StudentProfile profile = parseStudentProfile(value);
                if (profile == null || profile.name.isEmpty()) continue;
                String key = studentKey(profile.name);
                if (key.isEmpty()) continue;
                String line = studentBrief(profile);
                Integer existing = index.get(key);
                if (existing != null) {
                    // 角色自带的学生条目比较简短，用共享图鉴的描述与别名覆盖它
                    persona.otherStudents.set(existing,line);
                } else {
                    persona.otherStudents.add(line);
                    index.put(key,persona.otherStudents.size()-1);
                }
                persona.studentProfiles.add(profile);
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

    // 常驻的学生名录只保留一句话印象，完整外貌在被问到时才注入
    private static String studentBrief(StudentProfile profile) {
        StringBuilder builder = new StringBuilder(profile.name);
        if (!profile.school.isEmpty() || !profile.club.isEmpty()) {
            builder.append("（").append(safe(profile.school,"")).append("/")
                    .append(safe(profile.club,"")).append("）");
        }
        builder.append("：").append(safe(profile.description,""));
        if (!profile.aliases.isEmpty()) builder.append("；别名：").append(String.join("、",profile.aliases));
        return builder.toString();
    }

    private static String studentDetail(StudentProfile profile) {
        StringBuilder builder = new StringBuilder(studentBrief(profile));
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

    private static int clamp(int value,int defaultValue) {
        if (value <= 0) return defaultValue;
        if (value > 100) return 100;
        return value;
    }
}
