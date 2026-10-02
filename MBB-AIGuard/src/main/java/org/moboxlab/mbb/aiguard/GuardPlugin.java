package org.moboxlab.mbb.aiguard;

import org.moboxlab.moboxbot.API.Plugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * MBB-AIGuard 插件
 */
public class GuardPlugin extends Plugin {
    private GuardConfig guardConfig;
    private GuardRuleEngine ruleEngine;
    private GuardService guardService;

    @Override
    public void onLoad() {
        saveDefaultConfig();
        saveDefaultRules();
        guardConfig = GuardConfig.load(this);
        ruleEngine = new GuardRuleEngine(this);
        ruleEngine.load();
    }

    @Override
    public void onEnable() {
        guardService = new GuardService(this,guardConfig,ruleEngine);
        guardService.init();
        getServer().getPluginManager().registerListener(this,new GuardListener(this,guardService));
        getServer().getPluginManager().registerCommand(this,new GuardCommand(this,guardService));
        getLogger().sendInfo("MBB-AIGuard 已启用，规则数："+guardService.ruleCount());
    }

    @Override
    public void onDisable() {
        getLogger().sendInfo("MBB-AIGuard 已停用！");
    }

    public GuardConfig getGuardConfig() {
        return guardConfig;
    }

    private void saveDefaultRules() {
        try {
            File file = new File(getDataFolder(),"rules.json");
            if (file.exists()) return;
            byte[] bytes = readResource("rules.json");
            if (bytes == null) {
                getLogger().sendWarn("插件 JAR 里没有 rules.json！");
                return;
            }
            Files.write(Paths.get(file.getAbsolutePath()),bytes);
        } catch (Exception e) {
            getLogger().sendException(e);
        }
    }
}
