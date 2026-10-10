package org.moboxlab.mbb.antispam;

import org.moboxlab.moboxbot.API.Plugin;

/**
 * MBB-AntiSpam 插件
 *
 * 检测群聊刷屏：连发、复读、超长文本与艾特刷屏。默认只记录和告警，
 * 撤回与禁言需要同时满足配置阈值，并且可以在群级别单独开关。
 */
public class AntiSpamPlugin extends Plugin {
    private AntiSpamConfig antiSpamConfig;
    private AntiSpamService spamService;

    @Override
    public void onLoad() {
        saveDefaultConfig();
        antiSpamConfig = AntiSpamConfig.load(this);
    }

    @Override
    public void onEnable() {
        spamService = new AntiSpamService(this,antiSpamConfig);
        spamService.init();
        getServer().getPluginManager().registerListener(this,new AntiSpamListener(this,spamService));
        getServer().getPluginManager().registerCommand(this,new AntiSpamCommand(this,spamService));
        getLogger().sendInfo("MBB-AntiSpam 已启用，默认动作："+antiSpamConfig.defaultAction);
    }

    @Override
    public void onDisable() {
        getLogger().sendInfo("MBB-AntiSpam 已停用！");
    }

    public AntiSpamConfig getAntiSpamConfig() {
        return antiSpamConfig;
    }

    public AntiSpamService getSpamService() {
        return spamService;
    }
}
