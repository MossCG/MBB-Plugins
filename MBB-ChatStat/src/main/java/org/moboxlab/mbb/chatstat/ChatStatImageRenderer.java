package org.moboxlab.mbb.chatstat;

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
import java.util.Date;
import java.util.Locale;

/**
 * 群聊统计图片
 */
public class ChatStatImageRenderer {
    private static final int WIDTH = 1000;
    private static final int PADDING = 36;
    private static final int GAP = 16;

    public static byte[] render(JSONObject stats) {
        if (stats == null) return null;
        JSONArray top = stats.getJSONArray("top");
        JSONArray recent = stats.getJSONArray("recent");
        int topRows = top == null ? 0 : Math.min(8,top.size());
        int recentRows = recent == null ? 0 : Math.min(12,recent.size());
        int topY = 250;
        int recentY = topY + 84 + Math.max(1,topRows) * 30;
        int height = recentY + 84 + Math.max(1,recentRows) * 28 + PADDING;

        BufferedImage image = new BufferedImage(WIDTH,height,BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            init(graphics,height);
            boolean group = "group".equals(stats.getString("scope"));
            String title = group
                    ? "群聊内容统计 - "+safe(stats.getString("groupName"))+"("+stats.getLongValue("groupID")+")"
                    : "用户群聊统计 - "+stats.getLongValue("userID");
            drawHeader(graphics,title,"最近 "+stats.getIntValue("days")+" 天");

            JSONObject summary = stats.getJSONObject("summary");
            int cardWidth = (WIDTH - PADDING * 2 - GAP * 3) / 4;
            int cardY = PADDING + 76;
            drawMetric(graphics,PADDING,cardY,cardWidth,96,"消息数",String.valueOf(value(summary,"messages")),"");
            drawMetric(graphics,PADDING + (cardWidth + GAP),cardY,cardWidth,96,
                    group ? "活跃人数" : "活跃群数",String.valueOf(group ? value(summary,"users") : value(summary,"groups")),"");
            drawMetric(graphics,PADDING + (cardWidth + GAP) * 2,cardY,cardWidth,96,"图片",String.valueOf(value(summary,"images")),"");
            drawMetric(graphics,PADDING + (cardWidth + GAP) * 3,cardY,cardWidth,96,"@ 次数",String.valueOf(value(summary,"ats")),"");

            drawPanel(graphics,PADDING,topY,WIDTH - PADDING * 2,84 + Math.max(1,topRows) * 30,
                    group ? "发言人排行" : "活跃群排行");
            int rowY = topY + 68;
            if (topRows == 0) {
                drawText(graphics,"暂无统计数据。",PADDING + 20,rowY,new Color(120,113,108),Font.PLAIN,16);
            } else {
                long max = 1L;
                for (int i = 0; i < topRows; i++) max = Math.max(max,value(top.getJSONObject(i),"messages"));
                for (int i = 0; i < topRows; i++) {
                    JSONObject item = top.getJSONObject(i);
                    drawTopRow(graphics,PADDING + 20,rowY,item,max);
                    rowY += 30;
                }
            }

            drawPanel(graphics,PADDING,recentY,WIDTH - PADDING * 2,84 + Math.max(1,recentRows) * 28,"最近消息");
            rowY = recentY + 68;
            if (recentRows == 0) {
                drawText(graphics,"暂无统计数据。",PADDING + 20,rowY,new Color(120,113,108),Font.PLAIN,16);
            } else {
                for (int i = 0; i < recentRows; i++) {
                    JSONObject item = recent.getJSONObject(i);
                    drawRecentRow(graphics,PADDING + 20,rowY,item,group);
                    rowY += 28;
                }
            }
        } finally {
            graphics.dispose();
        }
        return writePng(image);
    }

    private static void drawTopRow(Graphics2D graphics,int x,int y,JSONObject item,long max) {
        long messages = value(item,"messages");
        String name = safe(item.getString("name"));
        drawText(graphics,fitText(graphics,name,220),x,y,new Color(68,64,60),Font.BOLD,15);
        drawText(graphics,String.valueOf(messages),x + 240,y,new Color(37,99,235),Font.BOLD,15);
        int barX = x + 300;
        int barWidth = 560;
        graphics.setColor(new Color(241,238,232));
        graphics.fillRoundRect(barX,y - 12,barWidth,10,10,10);
        int fill = (int)Math.round(barWidth * messages / (double)Math.max(1,max));
        graphics.setColor(new Color(37,99,235));
        graphics.fillRoundRect(barX,y - 12,Math.max(4,fill),10,10,10);
    }

    private static void drawRecentRow(Graphics2D graphics,int x,int y,JSONObject item,boolean group) {
        String time = new SimpleDateFormat("HH:mm",Locale.CHINA).format(new Date(item.getLongValue("messageTime")));
        String prefix = time+"  ";
        if (!group) prefix += safe(item.getString("groupName"))+"  ";
        prefix += safe(item.getString("userName"))+"：";
        String content = safe(item.getString("content"));
        drawText(graphics,fitText(graphics,prefix+content,WIDTH - PADDING * 2 - 40),
                x,y,new Color(68,64,60),Font.PLAIN,15);
    }

    private static void init(Graphics2D graphics,int height) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setColor(new Color(255,252,245));
        graphics.fillRect(0,0,WIDTH,height);
        graphics.setColor(new Color(250,231,180));
        graphics.drawRoundRect(PADDING - 12,PADDING - 12,WIDTH - (PADDING - 12) * 2,height - (PADDING - 12) * 2,22,22);
    }

    private static void drawHeader(Graphics2D graphics,String title,String subtitle) {
        drawText(graphics,title,PADDING,PADDING + 28,new Color(180,83,9),Font.BOLD,28);
        graphics.setFont(new Font("Microsoft YaHei",Font.PLAIN,15));
        graphics.setColor(new Color(120,113,108));
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.CHINA).format(new Date());
        String text = subtitle+"  "+time;
        int width = graphics.getFontMetrics().stringWidth(text);
        graphics.drawString(text,WIDTH - PADDING - width,PADDING + 26);
        graphics.setColor(new Color(251,191,36));
        graphics.fillRoundRect(PADDING,PADDING + 44,86,5,5,5);
    }

    private static void drawMetric(Graphics2D graphics,int x,int y,int width,int height,
                                   String label,String value,String detail) {
        graphics.setColor(Color.WHITE);
        graphics.fillRoundRect(x,y,width,height,14,14);
        graphics.setColor(new Color(250,231,180));
        graphics.drawRoundRect(x,y,width,height,14,14);
        drawText(graphics,label,x + 16,y + 30,new Color(146,138,130),Font.PLAIN,15);
        drawText(graphics,value,x + 16,y + 64,new Color(180,83,9),Font.BOLD,23);
        if (detail != null && !detail.isEmpty()) {
            drawText(graphics,detail,x + 16,y + 84,new Color(120,113,108),Font.PLAIN,13);
        }
    }

    private static void drawPanel(Graphics2D graphics,int x,int y,int width,int height,String title) {
        graphics.setColor(Color.WHITE);
        graphics.fillRoundRect(x,y,width,height,16,16);
        graphics.setColor(new Color(250,231,180));
        graphics.drawRoundRect(x,y,width,height,16,16);
        drawText(graphics,title,x + 20,y + 36,new Color(68,64,60),Font.BOLD,20);
    }

    private static void drawText(Graphics2D graphics,String text,int x,int y,Color color,int style,int size) {
        graphics.setColor(color);
        graphics.setFont(new Font("Microsoft YaHei",style,size));
        graphics.drawString(text == null ? "" : text,x,y);
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

    private static long value(JSONObject row,String key) {
        return row == null ? 0L : row.getLongValue(key);
    }

    private static String safe(String value) {
        return value == null || value.isEmpty() ? "未知" : value;
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
