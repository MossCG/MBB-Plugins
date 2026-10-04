package org.moboxlab.mbb.sticker;

import org.moboxlab.moboxbot.API.Plugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * MBB-Sticker 插件
 */
public class StickerPlugin extends Plugin {
    private StickerLibrary library;
    private StickerService service;
    private StickerReceiveService receiveService;

    @Override
    public void onLoad() {
        saveDefaultConfig();
        saveDefaultData();
    }

    @Override
    public void onEnable() {
        library = new StickerLibrary(this);
        library.load();
        StickerTagger tagger = new StickerTagger(this,library);
        service = new StickerService(this,library);
        receiveService = new StickerReceiveService(this,library,tagger);
        getServer().getPluginManager().registerListener(this,new StickerListener(receiveService));
        getServer().getPluginManager().registerCommand(this,
                new StickerCommand(this,library,service,receiveService));
        getServer().getPluginManager().registerService(this,service);
        getLogger().sendInfo("MBB-Sticker 已启用，表情包数量："+library.size());
    }

    @Override
    public void onDisable() {
        if (library != null) library.save();
        getLogger().sendInfo("MBB-Sticker 已停用！");
    }

    public StickerLibrary getLibrary() {
        return library;
    }

    public StickerService getService() {
        return service;
    }

    private void saveDefaultData() {
        try {
            File file = new File(getDataFolder(),"stickers.json");
            if (file.exists()) return;
            byte[] bytes = readResource("stickers.json");
            if (bytes == null) return;
            Files.write(Paths.get(file.getAbsolutePath()),bytes);
        } catch (Exception e) {
            getLogger().sendException(e);
        }
    }
}
