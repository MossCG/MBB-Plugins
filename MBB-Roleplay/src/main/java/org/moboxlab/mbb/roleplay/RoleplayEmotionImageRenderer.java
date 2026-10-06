package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 角色情绪与关系图片
 */
public class RoleplayEmotionImageRenderer {
    private static final int WIDTH = 1080;
    private static final int PADDING = 38;
    private static final int GAP = 14;

    private static class EventRow {
        private final JSONObject event;
        private final List<String> lines;
        private final int height;

        private EventRow(JSONObject event,List<String> lines) {
            this.event = event;
            this.lines = lines;
            this.height = 70 + Math.max(1,lines.size()) * 26;
        }
    }

    public static byte[] render(JSONObject data,int page,int pageSize) {
        if (data == null) return null;
        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 8;

        JSONObject mood = data.getJSONObject("mood");
        JSONObject relation = data.getJSONObject("relation");
        JSONArray events = data.getJSONArray("events");
        int total = events == null ? 0 : events.size();
        int totalPages = Math.max(1,(total + pageSize - 1) / pageSize);
        if (page > totalPages) page = totalPages;
        int start = (page - 1) * pageSize;
        int end = Math.min(total,start + pageSize);

        List<EventRow> rows = new ArrayList<>();
        for (int i = start; i < end; i++) {
            JSONObject event = events.getJSONObject(i);
            if (event == null) continue;
            rows.add(new EventRow(event,eventLines(event)));
        }

        int headerTop = PADDING;
        int moodTop = headerTop + 112;
        int moodHeight = 220;
        int relationTop = moodTop + moodHeight + GAP;
        int relationHeight = relation != null && relation.getLongValue("userID") > 0 ? 250 : 0;
        int listTop = relationTop + relationHeight + GAP;
        int listHeaderHeight = 74;
        int listHeight = listHeaderHeight;
        for (EventRow row : rows) listHeight += row.height + GAP;
        if (rows.isEmpty()) listHeight += 72;
        int height = listTop + listHeight + PADDING;

        BufferedImage image = new BufferedImage(WIDTH,height,BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            init(graphics,height);
            drawHeader(graphics,data,page,totalPages,total);
            drawMood(graphics,mood,moodTop,moodHeight);
            if (relationHeight > 0) drawRelation(graphics,relation,relationTop,relationHeight);
            drawEvents(graphics,rows,listTop,listHeight,listHeaderHeight,page,totalPages,total);
        } finally {
            graphics.dispose();
        }
        return writePng(image);
    }

    private static void init(Graphics2D graphics,int height) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setColor(new Color(255,252,245));
        graphics.fillRect(0,0,WIDTH,height);
        graphics.setColor(new Color(250,231,180));
        graphics.drawRoundRect(PADDING - 14,PADDING - 14,WIDTH - (PADDING - 14) * 2,
                height - (PADDING - 14) * 2,24,24);
    }

    private static void drawHeader(Graphics2D graphics,JSONObject data,int page,
                                   int totalPages,int total) {
        String role = safe(data.getString("role"));
        drawText(graphics,"角色情绪 - "+role,PADDING,PADDING + 30,
                new Color(180,83,9),Font.BOLD,29);
        drawText(graphics,"群 "+data.getLongValue("groupID")
                +"  情绪事件 "+total+" 条  第 "+page+" / "+totalPages+" 页",
                PADDING,PADDING + 58,new Color(120,113,108),Font.PLAIN,16);
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.CHINA).format(new Date());
        drawRightText(graphics,time,WIDTH - PADDING,PADDING + 28,
                new Color(120,113,108),Font.PLAIN,15);
        graphics.setColor(new Color(251,191,36));
        graphics.fillRoundRect(PADDING,PADDING + 76,92,5,5,5);
    }

    private static void drawMood(Graphics2D graphics,JSONObject mood,int top,int height) {
        drawPanel(graphics,PADDING,top,WIDTH - PADDING * 2,height,"群级情绪");
        String label = safe(mood == null ? null : mood.getString("label"));
        drawText(graphics,"当前："+label,PADDING + 22,top + 72,new Color(68,64,60),Font.BOLD,20);
        int x = PADDING + 22;
        int y = top + 96;
        drawBar(graphics,x,y,260,"心情",mood == null ? 0 : mood.getIntValue("valence"),
                new Color(244,114,182));
        drawBar(graphics,x + 320,y,260,"精力",mood == null ? 0 : mood.getIntValue("energy"),
                new Color(56,189,248));
        drawBar(graphics,x + 640,y,260,"耐心",mood == null ? 0 : mood.getIntValue("patience"),
                new Color(34,197,94));
        String reason = safe(mood == null ? null : mood.getString("reason"));
        List<String> lines = wrapText(reason,58);
        int lineY = y + 72;
        if (lines.isEmpty()) lines.add("暂无群级情绪原因。");
        for (String line : lines) {
            drawText(graphics,line,PADDING + 22,lineY,new Color(120,113,108),Font.PLAIN,16);
            lineY += 26;
        }
    }

    private static void drawRelation(Graphics2D graphics,JSONObject relation,int top,int height) {
        drawPanel(graphics,PADDING,top,WIDTH - PADDING * 2,height,"用户关系");
        long userID = relation.getLongValue("userID");
        String label = safe(relation.getString("emotionLabel"));
        drawText(graphics,"QQ "+userID+"  当前态度："+label,
                PADDING + 22,top + 72,new Color(68,64,60),Font.BOLD,20);
        int x = PADDING + 22;
        int y = top + 96;
        drawBar(graphics,x,y,260,"好感",relation.getIntValue("affinity"),new Color(251,191,36));
        drawBar(graphics,x + 320,y,260,"信任",relation.getIntValue("trust"),
                new Color(56,189,248));
        drawBar(graphics,x + 640,y,260,"厌烦",relation.getIntValue("annoyance"),
                new Color(239,68,68));
        String reason = safe(relation.getString("emotionReason"));
        List<String> lines = wrapText(reason,58);
        int lineY = y + 72;
        if (lines.isEmpty()) lines.add("暂无用户级情绪原因。");
        for (String line : lines) {
            drawText(graphics,line,PADDING + 22,lineY,new Color(120,113,108),Font.PLAIN,16);
            lineY += 26;
        }
        drawText(graphics,"原因强度 "+relation.getIntValue("reasonStrength")
                +"/100  来源 "+safe(relation.getString("reasonSource")),
                PADDING + 22,top + height - 22,new Color(146,138,130),Font.PLAIN,14);
    }

    private static void drawEvents(Graphics2D graphics,List<EventRow> rows,int top,int height,
                                   int headerHeight,int page,int totalPages,int total) {
        drawPanel(graphics,PADDING,top,WIDTH - PADDING * 2,height,"情绪事件");
        drawRightText(graphics,"第 "+page+" / "+totalPages+" 页 · 共 "+total+" 条",
                WIDTH - PADDING - 22,top + 38,new Color(146,138,130),Font.PLAIN,14);
        int rowY = top + headerHeight;
        if (rows.isEmpty()) {
            drawText(graphics,"暂无情绪事件。",PADDING + 22,rowY + 28,
                    new Color(120,113,108),Font.PLAIN,16);
            return;
        }
        for (EventRow row : rows) {
            drawEventCard(graphics,row,PADDING + 18,rowY,WIDTH - PADDING * 2 - 36);
            rowY += row.height + GAP;
        }
    }

    private static void drawEventCard(Graphics2D graphics,EventRow row,int x,int y,int width) {
        JSONObject event = row.event;
        graphics.setColor(Color.WHITE);
        graphics.fillRoundRect(x,y,width,row.height,14,14);
        graphics.setColor(new Color(250,231,180));
        graphics.drawRoundRect(x,y,width,row.height,14,14);
        String type = safe(event.getString("eventType"));
        String time = new SimpleDateFormat("MM-dd HH:mm",Locale.CHINA)
                .format(new Date(event.getLongValue("createTime")));
        drawText(graphics,"["+type+"]",x + 18,y + 28,new Color(180,83,9),Font.BOLD,16);
        drawRightText(graphics,time,x + width - 18,y + 28,
                new Color(146,138,130),Font.PLAIN,14);
        int lineY = y + 52;
        for (String line : row.lines) {
            drawText(graphics,line,x + 18,lineY,new Color(68,64,60),Font.PLAIN,15);
            lineY += 26;
        }
    }

    private static List<String> eventLines(JSONObject event) {
        List<String> result = new ArrayList<>();
        String reason = safe(event.getString("reason"));
        if (!reason.isEmpty()) result.addAll(wrapText(reason,62));
        StringBuilder change = new StringBuilder("变化：");
        appendChange(change,"心情",event.getIntValue("valenceDelta"));
        appendChange(change,"精力",event.getIntValue("energyDelta"));
        appendChange(change,"耐心",event.getIntValue("patienceDelta"));
        appendChange(change,"好感",event.getIntValue("affinityDelta"));
        appendChange(change,"信任",event.getIntValue("trustDelta"));
        appendChange(change,"厌烦",event.getIntValue("annoyanceDelta"));
        if (change.length() == 3) change.append("无");
        result.add(change.toString());
        return result;
    }

    private static void appendChange(StringBuilder builder,String name,int value) {
        if (value == 0) return;
        if (builder.length() > 3) builder.append("  ");
        builder.append(name).append(value > 0 ? "+" : "").append(value);
    }

    private static void drawBar(Graphics2D graphics,int x,int y,int width,String label,
                                int value,Color color) {
        int safeValue = Math.max(0,Math.min(100,value));
        drawText(graphics,label,x,y,new Color(68,64,60),Font.PLAIN,15);
        graphics.setColor(new Color(241,235,226));
        graphics.fillRoundRect(x,y + 12,width,16,8,8);
        graphics.setColor(color);
        graphics.fillRoundRect(x,y + 12,Math.max(4,width * safeValue / 100),16,8,8);
        drawRightText(graphics,safeValue+"/100",x + width,y + 25,
                new Color(120,113,108),Font.PLAIN,13);
    }

    private static void drawPanel(Graphics2D graphics,int x,int y,int width,int height,String title) {
        graphics.setColor(Color.WHITE);
        graphics.fillRoundRect(x,y,width,height,16,16);
        graphics.setColor(new Color(250,231,180));
        graphics.drawRoundRect(x,y,width,height,16,16);
        drawText(graphics,title,x + 20,y + 38,new Color(68,64,60),Font.BOLD,21);
    }

    private static List<String> wrapText(String text,int maxChars) {
        List<String> result = new ArrayList<>();
        if (text == null) return result;
        String normalized = text.replace("\r","").trim();
        if (normalized.isEmpty()) return result;
        String[] paragraphs = normalized.split("\n");
        for (String paragraph : paragraphs) {
            String value = paragraph.trim();
            if (value.isEmpty()) continue;
            while (value.length() > maxChars) {
                result.add(value.substring(0,maxChars).trim());
                value = value.substring(maxChars).trim();
            }
            if (!value.isEmpty()) result.add(value);
        }
        return result;
    }

    private static void drawText(Graphics2D graphics,String text,int x,int y,
                                 Color color,int style,int size) {
        graphics.setColor(color);
        graphics.setFont(new Font("Microsoft YaHei",style,size));
        graphics.drawString(text == null ? "" : text,x,y);
    }

    private static void drawRightText(Graphics2D graphics,String text,int right,int baseline,
                                      Color color,int style,int size) {
        graphics.setColor(color);
        graphics.setFont(new Font("Microsoft YaHei",style,size));
        String value = text == null ? "" : text;
        graphics.drawString(value,right - graphics.getFontMetrics().stringWidth(value),baseline);
    }

    private static String fitText(Graphics2D graphics,String text,int maxWidth) {
        if (text == null) return "";
        FontMetrics metrics = graphics.getFontMetrics();
        if (metrics.stringWidth(text) <= maxWidth) return text;
        int end = text.length();
        while (end > 0 && metrics.stringWidth(text.substring(0,end)+"...") > maxWidth) end--;
        return end <= 0 ? "..." : text.substring(0,end)+"...";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static byte[] writePng(BufferedImage image) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image,"png",output);
            return output.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}
