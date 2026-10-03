package org.moboxlab.mbb.roleplay;

import org.moboxlab.moboxbot.API.Plugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * MBB-Roleplay 插件
 */
public class RoleplayPlugin extends Plugin {
    private RoleplayConfig roleplayConfig;
    private RoleplayPersona persona;
    private RoleplayService service;

    @Override
    public void onLoad() {
        saveDefaultConfig();
        saveDefaultPersona();
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
        roleplayConfig = RoleplayConfig.load(this);
        persona = RoleplayPersona.load(this,roleplayConfig.personaFile);
        if (service != null) service.reload(roleplayConfig,persona);
    }

    private void saveDefaultPersona() {
        try {
            File file = new File(getDataFolder(),"persona.json");
            if (file.exists()) return;
            byte[] bytes = readResource("persona.json");
            if (bytes == null) {
                getLogger().sendWarn("插件 JAR 里没有 persona.json！");
                return;
            }
            Files.write(Paths.get(file.getAbsolutePath()),bytes);
        } catch (Exception e) {
            getLogger().sendException(e);
        }
    }
}
