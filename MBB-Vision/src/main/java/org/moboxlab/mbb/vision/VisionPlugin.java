package org.moboxlab.mbb.vision;

import org.moboxlab.moboxbot.API.Plugin;

/**
 * MBB-Vision 插件
 */
public class VisionPlugin extends Plugin {
    private VisionCache cache;
    private VisionService service;

    @Override
    public void onLoad() {
        saveDefaultConfig();
    }

    @Override
    public void onEnable() {
        cache = new VisionCache(this);
        cache.init();
        service = new VisionService(this,cache);
        getServer().getPluginManager().registerService(this,service);
        getServer().getPluginManager().registerCommand(this,new VisionCommand(this,cache,service));
        getLogger().sendInfo("MBB-Vision 已启用，缓存数量："+cache.count());
    }

    @Override
    public void onDisable() {
        getLogger().sendInfo("MBB-Vision 已停用！");
    }

    public VisionCache getCache() {
        return cache;
    }

    public VisionService getService() {
        return service;
    }
}
