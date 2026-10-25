package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.PluginService;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.util.Base64;

/**
 * 表情包候选拼图视觉选择。
 */
public class RoleplayStickerVisualSelector {
    private static final int CELL = 300;
    private static final int LABEL = 40;

    private final RoleplayService service;
    private volatile long retryAfter = 0L;

    public RoleplayStickerVisualSelector(RoleplayService service) {
        this.service = service;
    }

    public JSONObject select(long groupID,long userID,String tags,String context) {
        if (!service.config().stickerVisionEnable) return null;
        if (retryAfter > System.currentTimeMillis()) return null;
        PluginService sticker = service.plugin().getServer().getPluginManager()
                .getService("MBB-Sticker");
        PluginService ai = service.plugin().getServer().getPluginManager()
                .getService("MBB-AI");
        if (sticker == null || ai == null) return null;
        JSONObject params = new JSONObject(true);
        params.put("tags",tagArray(tags));
        params.put("groupID",groupID);
        params.put("userID",userID);
        params.put("limit",service.config().stickerVisionCandidates);
        JSONObject candidatesResult = sticker.call("candidates",params);
        JSONArray candidates = candidatesResult == null
                ? null : candidatesResult.getJSONArray("candidates");
        if (candidates == null || candidates.size() < 2) return null;
        String dataUri = montageDataUri(candidates);
        if (dataUri.isEmpty()) return null;
        JSONArray content = new JSONArray();
        JSONObject textPart = new JSONObject(true);
        textPart.put("type","text");
        textPart.put("text",selectionPrompt(tags,context,candidates));
        content.add(textPart);
        JSONObject imageUrl = new JSONObject(true);
        imageUrl.put("url",dataUri);
        JSONObject imagePart = new JSONObject(true);
        imagePart.put("type","image_url");
        imagePart.put("image_url",imageUrl);
        content.add(imagePart);
        JSONObject message = new JSONObject(true);
        message.put("role","user");
        message.put("content",content);
        JSONArray messages = new JSONArray();
        messages.add(message);
        JSONObject aiParams = new JSONObject(true);
        String profile = service.config().stickerVisionProfile == null
                || service.config().stickerVisionProfile.trim().isEmpty()
                ? service.config().aiProfile : service.config().stickerVisionProfile.trim();
        aiParams.put("profile",profile);
        aiParams.put("maxTokens",service.config().stickerVisionMaxTokens);
        aiParams.put("temperature",0.0);
        aiParams.put("reasoningEffort","none");
        aiParams.put("timeoutSeconds",service.config().stickerVisionTimeoutSecond);
        aiParams.put("retryCount",0);
        aiParams.put("sessionId","roleplay-sticker-vision-"+groupID);
        aiParams.put("messages",messages);
        RoleplayAiMetadata.tagTemplate(aiParams,"roleplay.sticker.select",
                RoleplayStickerVisualSelector.class);
        long start = System.currentTimeMillis();
        JSONObject response = ai.call("chat",aiParams);
        RoleplayAiLog.log(service.plugin().getLogger(),"表情选择",groupID,response,
                System.currentTimeMillis() - start);
        if (response == null || !response.getBooleanValue("status")) {
            markFailure();
            return null;
        }
        int index = parseIndex(response.getString("content"),candidates.size());
        if (index <= 0 || index > candidates.size()) {
            markFailure();
            return null;
        }
        JSONObject candidate = candidates.getJSONObject(index - 1);
        if (candidate == null) return null;
        JSONObject pickParams = new JSONObject(true);
        pickParams.put("id",candidate.getString("id"));
        pickParams.put("groupID",groupID);
        pickParams.put("userID",userID);
        JSONObject picked = sticker.call("pick",pickParams);
        if (picked == null || !picked.getBooleanValue("status")) {
            markFailure();
            return null;
        }
        retryAfter = 0L;
        service.plugin().getLogger().sendInfo("[表情包] 群"+groupID+" 视觉选择 #"+index
                +" "+service.shortText(service.safe(candidate.getString("description")),80));
        return picked;
    }

    static int parseIndex(String content,int count) {
        if (content == null || count <= 0) return 0;
        String text = content.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            try {
                JSONObject parsed = JSONObject.parseObject(text.substring(start,end + 1));
                int index = parsed.getIntValue("index");
                return index >= 1 && index <= count ? index : 0;
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    static String montageDataUri(JSONArray candidates) {
        if (candidates == null || candidates.isEmpty()) return "";
        int count = Math.min(9,candidates.size());
        int columns = Math.min(3,count);
        int rows = (count + columns - 1) / columns;
        int width = columns * CELL;
        int height = rows * (CELL + LABEL);
        BufferedImage image = new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(248,246,240));
            graphics.fillRect(0,0,width,height);
            int readable = 0;
            for (int i = 0; i < count; i++) {
                JSONObject candidate = candidates.getJSONObject(i);
                int x = (i % columns) * CELL;
                int y = (i / columns) * (CELL + LABEL);
                BufferedImage item = read(candidate == null ? "" : candidate.getString("file"));
                if (item != null) readable++;
                graphics.setColor(Color.WHITE);
                graphics.fillRect(x + 5,y + 5,CELL - 10,CELL - 10);
                if (item != null) {
                    drawFit(graphics,item,x + 10,y + 10,CELL - 20,CELL - 20);
                } else {
                    graphics.setColor(new Color(170,160,150));
                    graphics.setFont(new Font("Microsoft YaHei",Font.PLAIN,16));
                    graphics.drawString("图片不可读",x + 90,y + CELL / 2);
                }
                graphics.setColor(new Color(32,32,32));
                graphics.fillRect(x + 8,y + 8,44,34);
                graphics.setColor(Color.WHITE);
                graphics.setFont(new Font("Arial",Font.BOLD,24));
                graphics.drawString(String.valueOf(i + 1),x + 18,y + 33);
                graphics.setColor(new Color(80,76,72));
                graphics.setFont(new Font("Microsoft YaHei",Font.PLAIN,15));
                String label = candidate == null ? "" : serviceText(candidate.getString("description"));
                graphics.drawString(shortText(label,28),x + 10,y + CELL + 26);
            }
            if (readable == 0) return "";
        } finally {
            graphics.dispose();
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image,"png",output);
            return "data:image/png;base64,"
                    +Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }

    private String selectionPrompt(String tags,String context,JSONArray candidates) {
        StringBuilder builder = new StringBuilder();
        builder.append("你要从编号表情包中选出最适合当前 QQ 群聊天的一条。\n")
                .append("只根据画面、文字、情绪和聊天语境选择；不要选无关或重复的。\n")
                .append("当前标签：").append(service.safe(tags)).append("\n")
                .append("聊天语境：").append(service.shortText(service.safe(context),1200)).append("\n")
                .append("候选：\n");
        for (int i = 0; i < Math.min(9,candidates.size()); i++) {
            JSONObject item = candidates.getJSONObject(i);
            builder.append(i + 1).append(". 标签=").append(service.safe(item.getString("tags")))
                    .append(" 描述=").append(service.shortText(
                            service.safe(item.getString("description")),100)).append("\n");
        }
        builder.append("只输出 JSON：{\"index\":1,\"confidence\":0.8}。index 必须是 1 到 ")
                .append(Math.min(9,candidates.size())).append("。");
        return builder.toString();
    }

    private JSONArray tagArray(String tags) {
        JSONArray result = new JSONArray();
        for (String item : service.safe(tags).split("[,，]")) {
            if (!item.trim().isEmpty()) result.add(item.trim());
        }
        return result;
    }

    private static BufferedImage read(String fileUri) {
        if (fileUri == null || !fileUri.startsWith("file:")) return null;
        try {
            return ImageIO.read(new File(new URI(fileUri)));
        } catch (Exception e) {
            return null;
        }
    }

    private static void drawFit(Graphics2D graphics,BufferedImage image,
                                int x,int y,int width,int height) {
        double scale = Math.min(width * 1.0 / image.getWidth(),height * 1.0 / image.getHeight());
        int drawWidth = Math.max(1,(int)Math.round(image.getWidth() * scale));
        int drawHeight = Math.max(1,(int)Math.round(image.getHeight() * scale));
        int drawX = x + (width - drawWidth) / 2;
        int drawY = y + (height - drawHeight) / 2;
        graphics.drawImage(image,drawX,drawY,drawWidth,drawHeight,null);
    }

    private static String serviceText(String value) {
        return value == null ? "" : value.replace("\n"," ").trim();
    }

    private static String shortText(String value,int maxChars) {
        String text = serviceText(value);
        return text.length() <= maxChars ? text : text.substring(0,maxChars)+"...";
    }

    private void markFailure() {
        retryAfter = System.currentTimeMillis() + 300000L;
    }
}
