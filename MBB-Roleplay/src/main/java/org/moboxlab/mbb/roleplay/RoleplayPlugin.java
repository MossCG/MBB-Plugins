package org.moboxlab.mbb.roleplay;

import org.moboxlab.moboxbot.API.Plugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * MBB-Roleplay 插件
 */
public class RoleplayPlugin extends Plugin {
    private static final String[] PERSONA_FILES =
            new String[]{"persona-aris.json"};
    private static final String[] KNOWLEDGE_EXAMPLE_FILES =
            new String[]{"example/_index.md","example/sample.md"};

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
        saveDefaultKnowledge();
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
        getServer().getPluginManager().registerService(this,new RoleplayPublicService(service));
        logPokeConfigWarning();
        getLogger().sendInfo("MBB-Roleplay 已启用，角色："+persona.name);
    }

    @Override
    public void onDisable() {
        if (service != null) service.shutdown();
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
        logPokeConfigWarning();
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
            //覆盖前先备份运行目录里的旧设定，避免用户自定义内容丢失
            File file = new File(getDataFolder(),name);
            if (file.exists()) {
                File backupDirectory = new File(getDataFolder(),"backup");
                backupDirectory.mkdirs();
                String backupName = "persona-backup-"
                        +new SimpleDateFormat("yyyyMMdd-HHmmss-SSS",Locale.CHINA).format(new Date())
                        +"-"+name;
                Files.copy(file.toPath(),new File(backupDirectory,backupName).toPath());
                getLogger().sendInfo("角色设定重置前已备份："+backupName);
            }
            Files.write(Paths.get(file.getAbsolutePath()),bytes);
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

    public boolean setKnowledgeEnable(boolean enabled) {
        return setConfigValue("knowledgeEnable",enabled ? "true" : "false");
    }

    private boolean setConfigValue(String key,String value) {
        RoleplayConfigMigrator.ensure(this);
        getConfig().set(key,value);
        if (!getConfig().save()) return false;
        reloadRoleplay();
        return true;
    }

    private void logPokeConfigWarning() {
        if (!roleplayConfig.pokeReplyEnable) return;
        if (!roleplayConfig.pokeBackEnable) {
            getLogger().sendWarn("戳一戳回复已启用，但 pokeBackEnable=false，角色不会戳回去；"
                    +"需要戳回去请把该配置改为 true。");
        }
        if (getServer().getPluginManager().isEnabled("MBB-Poke")) {
            getLogger().sendWarn("MBB-Poke 已启用，MBB-Roleplay 将跳过戳一戳事件，由 MBB-Poke 处理。");
        }
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

    private void saveDefaultKnowledge() {
        try {
            String directory = getConfig().getString("knowledgeDirectory","knowledge");
            if (directory == null || directory.trim().isEmpty()) directory = "knowledge";
            File root = new File(getDataFolder(),directory.trim());
            //只在知识库目录第一次创建时释放示例；用户删掉示例后不再自动补回
            if (root.exists()) return;
            root.mkdirs();
            for (String name : KNOWLEDGE_EXAMPLE_FILES) {
                File file = new File(root,name);
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                byte[] bytes = readResource("knowledge/"+name);
                if (bytes == null) {
                    getLogger().sendWarn("插件 JAR 里没有 knowledge/"+name+"！");
                    continue;
                }
                Files.write(Paths.get(file.getAbsolutePath()),bytes);
            }
        } catch (Exception e) {
            getLogger().sendException(e);
        }
    }

}
