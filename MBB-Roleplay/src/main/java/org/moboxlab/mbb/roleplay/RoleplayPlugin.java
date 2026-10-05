package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Plugin;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * MBB-Roleplay 插件
 */
public class RoleplayPlugin extends Plugin {
    private static final String[] PERSONA_FILES =
            new String[]{"persona-aris.json","persona-momoi.json","persona-midori.json"};
    private static final String[] SPEECH_CORPUS_FILES =
            new String[]{"speech-corpus-aris.jsonl","speech-corpus-momoi.jsonl","speech-corpus-midori.jsonl"};

    private RoleplayConfig roleplayConfig;
    private RoleplayPersona persona;
    private RoleplayService service;

    @Override
    public void onLoad() {
        saveDefaultConfig();
        int repaired = RoleplayConfigMigrator.ensure(this);
        if (repaired > 0) {
            getLogger().sendInfo("Roleplay 配置已自动补全或迁移 "+repaired+" 项。");
        }
        saveDefaultPersonas();
        saveDefaultSpeechCorpus();
        roleplayConfig = RoleplayConfig.load(this);
        persona = RoleplayPersona.load(this,roleplayConfig.personaFile);
    }

    @Override
    public void onEnable() {
        service = new RoleplayService(this,roleplayConfig,persona);
        service.init();
        getServer().getPluginManager().registerListener(this,new RoleplayListener(this,service));
        getServer().getPluginManager().registerCommand(this,new RoleplayCommand(this,service));
        getServer().getPluginManager().registerCommand(this,new RoleplayReminderCommand(service.getReminderService()));
        getLogger().sendInfo("MBB-Roleplay 已启用，角色："+persona.name);
    }

    @Override
    public void onDisable() {
        getLogger().sendInfo("MBB-Roleplay 已停用！");
    }

    public RoleplayConfig getRoleplayConfig() {
        return roleplayConfig;
    }

    public void reloadRoleplay() {
        int repaired = RoleplayConfigMigrator.ensure(this);
        if (repaired > 0) {
            getLogger().sendInfo("Roleplay 配置重载时自动补全或迁移 "+repaired+" 项。");
        }
        roleplayConfig = RoleplayConfig.load(this);
        persona = RoleplayPersona.load(this,roleplayConfig.personaFile);
        if (service != null) service.reload(roleplayConfig,persona);
    }

    public int repairConfig() {
        int repaired = RoleplayConfigMigrator.ensure(this);
        reloadRoleplay();
        return repaired;
    }

    public String getPersonaFileName() {
        return roleplayConfig.personaFile;
    }

    public boolean switchPersona(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) return false;
        String name = fileName.trim();
        File file = new File(getDataFolder(),name);
        if (!file.exists()) return false;
        getConfig().set("personaFile",name);
        if (!getConfig().save()) return false;
        reloadRoleplay();
        return true;
    }

    public boolean resetPersona(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) return false;
        String name = fileName.trim();
        boolean builtIn = false;
        for (String item : PERSONA_FILES) {
            if (item.equals(name)) {
                builtIn = true;
                break;
            }
        }
        if (!builtIn) return false;
        try {
            byte[] bytes = readResource(name);
            if (bytes == null) return false;
            Files.write(Paths.get(new File(getDataFolder(),name).getAbsolutePath()),bytes);
            reloadRoleplay();
            return true;
        } catch (Exception e) {
            getLogger().sendException(e);
            return false;
        }
    }

    public boolean setOtherRoleBotReplyChance(double chance) {
        if (chance < 0) chance = 0;
        if (chance > 1) chance = 1;
        return setConfigValue("otherRoleBotReplyChance",String.valueOf(chance));
    }

    public boolean setMaxConsecutiveOtherRoleMessages(int count) {
        if (count < 1) count = 1;
        if (count > 10) count = 10;
        return setConfigValue("maxConsecutiveOtherRoleMessages",String.valueOf(count));
    }

    public boolean setOtherRoleBotQQs(String value) {
        return setConfigValue("otherRoleBotQQs",value == null ? "" : value.trim());
    }

    public boolean setOtherRoleBotNames(String value) {
        return setConfigValue("otherRoleBotNames",value == null ? "" : value.trim());
    }

    public boolean setGlobalMemoryLearnGroups(String value) {
        return setConfigValue("globalMemoryLearnGroups",value == null ? "" : value.trim());
    }

    public boolean setSpeechCorpusEnable(boolean enabled) {
        return setConfigValue("speechCorpusEnable",enabled ? "true" : "false");
    }

    private boolean setConfigValue(String key,String value) {
        RoleplayConfigMigrator.ensure(this);
        getConfig().set(key,value);
        if (!getConfig().save()) return false;
        reloadRoleplay();
        return true;
    }

    private void saveDefaultPersonas() {
        for (String name : PERSONA_FILES) {
            try {
                File file = new File(getDataFolder(),name);
                if (file.exists()) continue;
                byte[] bytes = readResource(name);
                if (bytes == null) {
                    getLogger().sendWarn("插件 JAR 里没有 "+name+"！");
                    continue;
                }
                Files.write(Paths.get(file.getAbsolutePath()),bytes);
            } catch (Exception e) {
                getLogger().sendException(e);
            }
        }
        saveDefaultStudents();
    }

    private void saveDefaultStudents() {
        try {
            File file = new File(getDataFolder(),"students.json");
            byte[] bytes = readResource("students.json");
            if (bytes == null) {
                getLogger().sendWarn("插件 JAR 里没有 students.json！");
                return;
            }
            if (!file.exists()) {
                Files.write(Paths.get(file.getAbsolutePath()),bytes);
                return;
            }
            String text = new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);
            JSONObject json = JSONObject.parseObject(text);
            int version = json == null ? 0 : json.getIntValue("version");
            if (version >= 4) return;
            File backup = new File(getDataFolder(),"students.json.bak-"+System.currentTimeMillis());
            Files.copy(file.toPath(),backup.toPath());
            Files.write(Paths.get(file.getAbsolutePath()),bytes);
            getLogger().sendInfo("共享学生图鉴已升级到结构化外貌版本，旧文件已备份："+backup.getName());
        } catch (Exception e) {
            getLogger().sendException(e);
        }
    }

    private void saveDefaultResource(String name) {
        try {
            File file = new File(getDataFolder(),name);
            if (file.exists()) return;
            byte[] bytes = readResource(name);
            if (bytes == null) {
                getLogger().sendWarn("插件 JAR 里没有 "+name+"！");
                return;
            }
            Files.write(Paths.get(file.getAbsolutePath()),bytes);
        } catch (Exception e) {
            getLogger().sendException(e);
        }
    }

    private void saveDefaultSpeechCorpus() {
        try {
            File directory = new File(getDataFolder(),"speech-corpus");
            if (!directory.exists()) directory.mkdirs();
            for (String name : SPEECH_CORPUS_FILES) {
                File file = new File(directory,name);
                if (file.exists()) continue;
                byte[] bytes = readResource("speech-corpus/"+name);
                if (bytes == null) {
                    getLogger().sendWarn("插件 JAR 里没有 speech-corpus/"+name+"！");
                    continue;
                }
                Files.write(Paths.get(file.getAbsolutePath()),bytes);
            }
        } catch (Exception e) {
            getLogger().sendException(e);
        }
    }
}
