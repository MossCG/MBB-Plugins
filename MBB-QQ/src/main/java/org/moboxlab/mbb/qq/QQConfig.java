package org.moboxlab.mbb.qq;

import org.moboxlab.moboxbot.API.Plugin;

/**
 * MBB-QQ 配置。
 */
public class QQConfig {
    public boolean enable = true;
    public boolean readToolsEnable = true;
    public boolean interactionToolsEnable = true;
    public boolean mediaToolsEnable = true;
    public boolean adminToolsEnable = true;
    public boolean groupLeaveEnable = true;
    public boolean notifyOwnerOnGroupEvent = true;
    public boolean friendRequestRoleDecisionEnable = true;
    public int friendRequestRoleDecisionRetrySecond = 300;
    public boolean qzoneEnable = true;
    public int pokeCooldownSecond = 30;
    public int likeCooldownSecond = 86400;
    public int likeMaxTimes = 10;
    public int emojiLikeCooldownSecond = 10;
    public int recallWindowSecond = 120;
    public int groupCardCooldownSecond = 60;
    public int qzoneCooldownSecond = 300;
    public int qzoneContentMaxChars = 500;
    public int qzoneMaxImages = 9;
    public int qzoneDefaultUgcRight = 64;
    public int imageMaxBytes = 8 * 1024 * 1024;
    public int voiceMaxBytes = 8 * 1024 * 1024;
    public String imageExtensions = "jpg,jpeg,png,gif,webp";
    public String voiceExtensions = "silk,amr,mp3,wav,ogg,m4a,flac";
    public int auditKeepDays = 90;
    public int auditMaxRows = 10000;
    public int requestKeepDays = 30;

    public static QQConfig load(Plugin plugin) {
        QQConfig config = new QQConfig();
        config.enable = plugin.getConfig().getBoolean("enable",true);
        config.readToolsEnable = plugin.getConfig().getBoolean("readToolsEnable",true);
        config.interactionToolsEnable = plugin.getConfig().getBoolean("interactionToolsEnable",true);
        config.mediaToolsEnable = plugin.getConfig().getBoolean("mediaToolsEnable",true);
        config.adminToolsEnable = plugin.getConfig().getBoolean("adminToolsEnable",true);
        config.groupLeaveEnable = plugin.getConfig().getBoolean("groupLeaveEnable",true);
        config.notifyOwnerOnGroupEvent =
                plugin.getConfig().getBoolean("notifyOwnerOnGroupEvent",true);
        config.friendRequestRoleDecisionEnable =
                plugin.getConfig().getBoolean("friendRequestRoleDecisionEnable",true);
        config.friendRequestRoleDecisionRetrySecond =
                plugin.getConfig().getInt("friendRequestRoleDecisionRetrySecond",300);
        config.qzoneEnable = plugin.getConfig().getBoolean("qzoneEnable",true);
        config.pokeCooldownSecond = plugin.getConfig().getInt("pokeCooldownSecond",30);
        config.likeCooldownSecond = plugin.getConfig().getInt("likeCooldownSecond",86400);
        config.likeMaxTimes = plugin.getConfig().getInt("likeMaxTimes",10);
        config.emojiLikeCooldownSecond = plugin.getConfig().getInt("emojiLikeCooldownSecond",10);
        config.recallWindowSecond = plugin.getConfig().getInt("recallWindowSecond",120);
        config.groupCardCooldownSecond = plugin.getConfig().getInt("groupCardCooldownSecond",60);
        config.qzoneCooldownSecond = plugin.getConfig().getInt("qzoneCooldownSecond",300);
        config.qzoneContentMaxChars = plugin.getConfig().getInt("qzoneContentMaxChars",500);
        config.qzoneMaxImages = plugin.getConfig().getInt("qzoneMaxImages",9);
        config.qzoneDefaultUgcRight = plugin.getConfig().getInt("qzoneDefaultUgcRight",64);
        config.imageMaxBytes = plugin.getConfig().getInt("imageMaxBytes",8 * 1024 * 1024);
        config.voiceMaxBytes = plugin.getConfig().getInt("voiceMaxBytes",8 * 1024 * 1024);
        config.imageExtensions = plugin.getConfig().getString("imageExtensions","jpg,jpeg,png,gif,webp");
        config.voiceExtensions = plugin.getConfig().getString("voiceExtensions","silk,amr,mp3,wav,ogg,m4a,flac");
        config.auditKeepDays = plugin.getConfig().getInt("auditKeepDays",90);
        config.auditMaxRows = plugin.getConfig().getInt("auditMaxRows",10000);
        config.requestKeepDays = plugin.getConfig().getInt("requestKeepDays",30);
        clamp(config);
        return config;
    }

    private static void clamp(QQConfig config) {
        if (config.pokeCooldownSecond < 1) config.pokeCooldownSecond = 1;
        if (config.likeCooldownSecond < 1) config.likeCooldownSecond = 1;
        if (config.likeMaxTimes < 1) config.likeMaxTimes = 1;
        if (config.likeMaxTimes > 20) config.likeMaxTimes = 20;
        if (config.emojiLikeCooldownSecond < 1) config.emojiLikeCooldownSecond = 1;
        if (config.recallWindowSecond < 10) config.recallWindowSecond = 10;
        if (config.groupCardCooldownSecond < 10) config.groupCardCooldownSecond = 10;
        if (config.friendRequestRoleDecisionRetrySecond < 30) {
            config.friendRequestRoleDecisionRetrySecond = 30;
        }
        if (config.friendRequestRoleDecisionRetrySecond > 3600) {
            config.friendRequestRoleDecisionRetrySecond = 3600;
        }
        if (config.qzoneCooldownSecond < 30) config.qzoneCooldownSecond = 30;
        if (config.qzoneContentMaxChars < 10) config.qzoneContentMaxChars = 10;
        if (config.qzoneContentMaxChars > 2000) config.qzoneContentMaxChars = 2000;
        if (config.qzoneMaxImages < 1) config.qzoneMaxImages = 1;
        if (config.qzoneMaxImages > 9) config.qzoneMaxImages = 9;
        int right = config.qzoneDefaultUgcRight;
        if (right != 1 && right != 4 && right != 16 && right != 64 && right != 128) {
            config.qzoneDefaultUgcRight = 64;
        }
        if (config.imageMaxBytes < 1024) config.imageMaxBytes = 1024;
        if (config.imageMaxBytes > 20 * 1024 * 1024) config.imageMaxBytes = 20 * 1024 * 1024;
        if (config.voiceMaxBytes < 1024) config.voiceMaxBytes = 1024;
        if (config.voiceMaxBytes > 20 * 1024 * 1024) config.voiceMaxBytes = 20 * 1024 * 1024;
        if (config.imageExtensions == null) config.imageExtensions = "";
        if (config.voiceExtensions == null) config.voiceExtensions = "";
        if (config.auditKeepDays < 1) config.auditKeepDays = 1;
        if (config.auditMaxRows < 100) config.auditMaxRows = 100;
        if (config.auditMaxRows > 100000) config.auditMaxRows = 100000;
        if (config.requestKeepDays < 1) config.requestKeepDays = 1;
        if (config.requestKeepDays > 365) config.requestKeepDays = 365;
    }
}
