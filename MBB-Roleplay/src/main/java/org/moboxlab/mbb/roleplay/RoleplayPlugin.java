package org.moboxlab.mbb.roleplay;

import org.moboxlab.moboxbot.API.Plugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * MBB-Roleplay 插件
 */
public class RoleplayPlugin extends Plugin {
    private static final String[] PERSONA_FILES =
            new String[]{"persona-aris.json","persona-momoi.json","persona-midori.json"};

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
        roleplayConfig = RoleplayConfig.load(this);
        persona = RoleplayPersona.load(this,roleplayConfig.personaFile);
    }

    @Override
    public void onEnable() {
        service = new RoleplayService(this,roleplayConfig,persona);
        service.init();
        getServer().getPluginManager().registerListener(this,new RoleplayListener(this,service));
        getServer().getPluginManager().registerCommand(this,new RoleplayCommand(this,service));
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
    }
}
