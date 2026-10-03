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
 * 角色记忆图片
 */
public class RoleplayMemoryImageRenderer {
    private static final int WIDTH = 1080;
    private static final int PADDING = 38;
    private static final int GAP = 16;

    private static class MemoryRow {
        private final JSONObject memory;
        private final List<String> lines;
        private final int height;

        private MemoryRow(JSONObject memory,List<String> lines) {
            this.memory = memory;
            this.lines = lines;
            this.height = 70 + Math.max(1,lines.size()) * 28;
        }
    }

    public static byte[] render(JSONObject data,int page,int pageSize) {
        if (data == null) return null;
        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 12;

        JSONArray memories = data.getJSONArray("memories");
        int total = memories == null ? 0 : memories.size();
        int totalPages = Math.max(1,(total + pageSize - 1) / pageSize);
        if (page > totalPages) page = totalPages;
        int start = (page - 1) * pageSize;
        int end = Math.min(total,start + pageSize);

        List<String> shortLines = wrapText(safe(data.getString("shortSummary")),54);
        List<MemoryRow> rows = new ArrayList<>();
        for (int i = start; i < end; i++) {
            JSONObject memory = memories.getJSONObject(i);
            if (memory == null) continue;
            rows.add(new MemoryRow(memory,wrapText(safe(memory.getString("content")),56)));
        }

        int headerTop = PADDING;
        int summaryTop = headerTop + 112;
        int summaryHeight = 78 + Math.max(1,shortLines.size()) * 28;
        int listTop = summaryTop + summaryHeight + 18;
        int listHeaderHeight = 76;
        int listHeight = listHeaderHeight + Math.max(1,rows.size()) * 0;
        for (MemoryRow row : rows) listHeight += row.height + GAP;
        if (rows.isEmpty()) listHeight += 72;
        int height = listTop + listHeight + PADDING;

        BufferedImage image = new BufferedImage(WIDTH,height,BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            init(graphics,height);
            drawHeader(graphics,data,page,totalPages,total);
            drawSummary(graphics,summaryTop,summaryHeight,shortLines);
            drawMemories(graphics,listTop,listHeight,listHeaderHeight,rows,page,totalPages,total);
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
        graphics.drawRoundRect(PADDING - 14,PADDING - 14,WIDTH - (PADDING - 14) * 2,height - (PADDING - 14) * 2,24,24);
    }

    private static void drawHeader(Graphics2D graphics,JSONObject data,int page,int totalPages,int total) {
        String role = safe(data.getString("role"));
        drawText(graphics,"角色记忆 - "+role,PADDING,PADDING + 30,new Color(180,83,9),Font.BOLD,29);
        String group = "群 "+data.getLongValue("groupID")
                +"  长期记忆 "+total+" 条  第 "+page+" / "+totalPages+" 页";
        drawText(graphics,group,PADDING,PADDING + 58,new Color(120,113,108),Font.PLAIN,16);
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.CHINA).format(new Date());
        drawRightText(graphics,time,WIDTH - PADDING,PADDING + 28,new Color(120,113,108),Font.PLAIN,15);
        graphics.setColor(new Color(251,191,36));
        graphics.fillRoundRect(PADDING,PADDING + 76,92,5,5,5);
    }

    private static void drawSummary(Graphics2D graphics,int top,int height,List<String> lines) {
        drawPanel(graphics,PADDING,top,WIDTH - PADDING * 2,height,"短期记忆");
        int lineY = top + 68;
        if (lines.isEmpty()) {
            drawText(graphics,"暂无短期记忆。",PADDING + 22,lineY,new Color(120,113,108),Font.PLAIN,16);
            return;
        }
        for (String line : lines) {
            drawText(graphics,line,PADDING + 22,lineY,new Color(68,64,60),Font.PLAIN,17);
            lineY += 28;
        }
    }

    private static void drawMemories(Graphics2D graphics,int top,int height,int headerHeight,
                                     List<MemoryRow> rows,int page,int totalPages,int total) {
        drawPanel(graphics,PADDING,top,WIDTH - PADDING * 2,height,"长期记忆");
        drawRightText(graphics,"第 "+page+" / "+totalPages+" 页 · 共 "+total+" 条",
                WIDTH - PADDING - 22,top + 38,new Color(146,138,130),Font.PLAIN,14);
        int rowY = top + headerHeight;
        if (rows.isEmpty()) {
            drawText(graphics,"暂无长期记忆。",PADDING + 22,rowY + 28,new Color(120,113,108),Font.PLAIN,16);
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            MemoryRow row = rows.get(i);
            drawMemoryCard(graphics,row,PADDING + 18,rowY,WIDTH - PADDING * 2 - 36);
            rowY += row.height + GAP;
        }
    }

    private static void drawMemoryCard(Graphics2D graphics,MemoryRow row,int x,int y,int width) {
        JSONObject memory = row.memory;
        graphics.setColor(new Color(255,255,255));
        graphics.fillRoundRect(x,y,width,row.height,14,14);
        graphics.setColor(new Color(250,231,180));
        graphics.drawRoundRect(x,y,width,row.height,14,14);

        String type = safe(memory.getString("type"));
        int importance = memory.getIntValue("importance");
        String title = "["+type+"]  重要度 "+importance+"/5";
        drawText(graphics,fitText(graphics,title,width - 36),x + 18,y + 30,
                new Color(180,83,9),Font.BOLD,17);

        long subjectID = memory.getLongValue("subjectID");
        String subject = subjectID > 0 ? "对象 QQ："+subjectID : "对象：群级记忆";
        drawText(graphics,subject,x + 18,y + 54,new Color(120,113,108),Font.PLAIN,14);

        int lineY = y + 78;
        for (String line : row.lines) {
            drawText(graphics,line,x + 18,lineY,new Color(68,64,60),Font.PLAIN,16);
            lineY += 28;
        }
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
            if (value.isEmpty()) {
                result.add("");
                continue;
            }
            while (value.length() > maxChars) {
                int cut = maxChars;
                int start = Math.max(0,maxChars - 12);
                for (int i = maxChars; i >= start; i--) {
                    char character = value.charAt(i);
                    if (character == '。' || character == '！' || character == '？'
                            || character == '!' || character == '?' || character == '；'
                            || character == ';' || character == '，' || character == ',') {
                        cut = i + 1;
                        break;
                    }
                }
                result.add(value.substring(0,cut).trim());
                value = value.substring(cut).trim();
            }
            if (!value.isEmpty()) result.add(value);
        }
        return result;
    }

    private static void drawText(Graphics2D graphics,String text,int x,int y,Color color,int style,int size) {
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
        String ellipsis = "...";
        int end = text.length();
        while (end > 0 && metrics.stringWidth(text.substring(0,end)+ellipsis) > maxWidth) {
            end--;
        }
        return end <= 0 ? ellipsis : text.substring(0,end)+ellipsis;
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "无" : value.trim();
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
