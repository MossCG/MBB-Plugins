package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

/**
 * 表情包候选拼图、索引解析和默认配置测试。
 */
public class RoleplayStickerVisualSelectorTestMain {
    public static void main(String[] args) throws Exception {
        RoleplayConfig config = new RoleplayConfig();
        if (!config.stickerVisionEnable || config.stickerVisionCandidates != 6
                || config.stickerVisionMaxTokens != 600
                || config.stickerVisionTimeoutSecond != 45) {
            throw new AssertionError("表情包视觉选择默认配置不正确");
        }

        File directory = Files.createTempDirectory("mbb-sticker-visual-").toFile();
        JSONArray candidates = new JSONArray();
        candidates.add(candidate(write(directory,"one.png",Color.RED), "开心", "sticker-1"));
        candidates.add(candidate(write(directory,"two.png",Color.BLUE), "无语", "sticker-2"));
        String dataUri = RoleplayStickerVisualSelector.montageDataUri(candidates);
        if (dataUri == null || !dataUri.startsWith("data:image/png;base64,")
                || dataUri.length() < 2000) {
            throw new AssertionError("表情包候选没有生成有效拼图");
        }
        if (args != null && args.length > 0) {
            String encoded = dataUri.substring(dataUri.indexOf(',') + 1);
            Files.write(Paths.get(args[0]),Base64.getDecoder().decode(encoded));
        }

        if (RoleplayStickerVisualSelector.parseIndex("{\"index\":2,\"confidence\":0.9}",2) != 2
                || RoleplayStickerVisualSelector.parseIndex("{\"index\":0}",2) != 0
                || RoleplayStickerVisualSelector.parseIndex("not-json",2) != 0) {
            throw new AssertionError("表情包视觉选择索引解析错误");
        }
        if (!RoleplayStickerVisualSelector.montageDataUri(new JSONArray()).isEmpty()) {
            throw new AssertionError("空候选不应生成拼图");
        }
    }

    private static JSONObject candidate(String file,String description,String id) {
        JSONObject item = new JSONObject(true);
        item.put("id",id);
        item.put("file",file);
        item.put("description",description);
        JSONArray tags = new JSONArray();
        tags.add("happy");
        item.put("tags",tags);
        return item;
    }

    private static String write(File directory,String name,Color color) throws Exception {
        BufferedImage image = new BufferedImage(160,120,BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0,0,160,120);
        } finally {
            graphics.dispose();
        }
        File file = new File(directory,name);
        ImageIO.write(image,"png",file);
        return file.toURI().toString();
    }
}
