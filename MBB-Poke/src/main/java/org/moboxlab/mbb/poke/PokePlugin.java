package org.moboxlab.mbb.poke;

import org.moboxlab.moboxbot.API.Plugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * MBB-Poke 插件
 */
public class PokePlugin extends Plugin {
    @Override
    public void onLoad() {
        saveDefaultConfig();
        saveDefaultReplyFile();
    }

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerListener(this,new PokeListener(this));
        getLogger().sendInfo("MBB-Poke 已启用！");
    }

    @Override
    public void onDisable() {
        getLogger().sendInfo("MBB-Poke 已停用！");
    }

    private void saveDefaultReplyFile() {
        String path = getDataFolder()+"/replies.yml";
        if (new File(path).exists()) return;
        try {
            new File(getDataFolder()).mkdirs();
            byte[] bytes = readResource("replies.yml");
            if (bytes == null) {
                getLogger().sendWarn("插件 JAR 里没有 replies.yml，已使用内置默认回复！");
                return;
            }
            Files.write(Paths.get(path),bytes);
        } catch (Exception e) {
            getLogger().sendException(e);
        }
    }
}
