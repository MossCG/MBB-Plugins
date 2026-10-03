package org.moboxlab.mbb.aiguard;

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
 * 风险审查记录图片
 */
public class GuardLogImageRenderer {
    private static final int WIDTH = 1280;
    private static final int PADDING = 40;
    private static final int GAP = 16;

    public static byte[] render(JSONArray events,int page,int pageSize) {
        if (events == null) events = new JSONArray();
        int count = events.size();
        int listTop = PADDING + 154;
        int cardHeight = 112;
        int listHeight = 74 + Math.max(1,count) * cardHeight;
        int height = listTop + listHeight + PADDING;

        BufferedImage image = new BufferedImage(WIDTH,height,BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            init(graphics,height);
            drawHeader(graphics,page,pageSize);
            drawSummary(graphics,events);
            drawList(graphics,events,listTop,listHeight,cardHeight);
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

    private static void drawHeader(Graphics2D graphics,int page,int pageSize) {
        drawText(graphics,"AI 风险审查记录",PADDING,PADDING + 30,new Color(180,83,9),Font.BOLD,30);
        drawText(graphics,"第 "+page+" 页 · 每页最多 "+pageSize+" 条",
                PADDING,PADDING + 58,new Color(120,113,108),Font.PLAIN,16);
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.CHINA).format(new Date());
        drawRightText(graphics,time,WIDTH - PADDING,PADDING + 28,new Color(120,113,108),Font.PLAIN,15);
        graphics.setColor(new Color(251,191,36));
        graphics.fillRoundRect(PADDING,PADDING + 76,96,5,5,5);
    }

    private static void drawSummary(Graphics2D graphics,JSONArray events) {
        int count = events.size();
        int alerts = 0;
        int safety = 0;
        int totalScore = 0;
        for (int i = 0; i < count; i++) {
            JSONObject event = events.getJSONObject(i);
            int score = event.getIntValue("score");
            totalScore += score;
            if ("alert".equalsIgnoreCase(safe(event.getString("action")))) alerts++;
            if (event.getBooleanValue("safety")) safety++;
        }
        int average = count <= 0 ? 0 : Math.round(totalScore / (float)count);
        int cardWidth = (WIDTH - PADDING * 2 - GAP * 3) / 4;
        int y = PADDING + 96;
        drawMetric(graphics,PADDING,y,cardWidth,"本页记录",String.valueOf(count),new Color(37,99,235));
        drawMetric(graphics,PADDING + (cardWidth + GAP),y,cardWidth,"告警动作",String.valueOf(alerts),new Color(220,38,38));
        drawMetric(graphics,PADDING + (cardWidth + GAP) * 2,y,cardWidth,"安全分支",String.valueOf(safety),new Color(217,119,6));
        drawMetric(graphics,PADDING + (cardWidth + GAP) * 3,y,cardWidth,"平均风险分",String.valueOf(average),scoreColor(average));
    }

    private static void drawList(Graphics2D graphics,JSONArray events,int top,int height,int cardHeight) {
        graphics.setColor(Color.WHITE);
        graphics.fillRoundRect(PADDING,top,WIDTH - PADDING * 2,height,18,18);
        graphics.setColor(new Color(250,231,180));
        graphics.drawRoundRect(PADDING,top,WIDTH - PADDING * 2,height,18,18);
        drawText(graphics,"事件明细",PADDING + 22,top + 38,new Color(68,64,60),Font.BOLD,21);

        int rowY = top + 62;
        if (events.isEmpty()) {
            drawText(graphics,"本页暂无风险审查记录。",PADDING + 22,rowY + 28,new Color(120,113,108),Font.PLAIN,17);
            return;
        }
        for (int i = 0; i < events.size(); i++) {
            drawEvent(graphics,events.getJSONObject(i),PADDING + 18,rowY,WIDTH - PADDING * 2 - 36,cardHeight - 8);
            rowY += cardHeight;
        }
    }

    private static void drawEvent(Graphics2D graphics,JSONObject event,int x,int y,int width,int height) {
        int score = event.getIntValue("score");
        graphics.setColor(score >= 70 ? new Color(254,242,242) : new Color(248,250,252));
        graphics.fillRoundRect(x,y,width,height,14,14);
        graphics.setColor(score >= 70 ? new Color(252,165,165) : new Color(226,232,240));
        graphics.drawRoundRect(x,y,width,height,14,14);

        String meta = "#"+event.getLongValue("id")
                +"  "+formatTime(event.getLongValue("messageTime"))
                +"  群 "+event.getLongValue("groupID")
                +"  用户 "+event.getLongValue("userID");
        drawText(graphics,fitText(graphics,meta,width - 150),x + 18,y + 28,new Color(68,64,60),Font.BOLD,16);
        drawRightText(graphics,String.valueOf(score),x + width - 20,y + 30,scoreColor(score),Font.BOLD,24);

        String detail = "分类："+safe(event.getString("categories"))
                +"    动作："+safe(event.getString("action"))
                +"    安全分支："+(event.getBooleanValue("safety") ? "是" : "否");
        drawText(graphics,fitText(graphics,detail,width - 36),x + 18,y + 54,new Color(71,85,105),Font.PLAIN,15);

        String reason = "原因："+safe(event.getString("reason"));
        drawText(graphics,fitText(graphics,reason,width - 36),x + 18,y + 78,new Color(68,64,60),Font.PLAIN,15);
    }

    private static void drawMetric(Graphics2D graphics,int x,int y,int width,String label,String value,Color color) {
        graphics.setColor(Color.WHITE);
        graphics.fillRoundRect(x,y,width,78,14,14);
        graphics.setColor(new Color(250,231,180));
        graphics.drawRoundRect(x,y,width,78,14,14);
        drawText(graphics,label,x + 16,y + 28,new Color(146,138,130),Font.PLAIN,14);
        drawText(graphics,value,x + 16,y + 61,color,Font.BOLD,24);
    }

    private static Color scoreColor(int score) {
        if (score >= 70) return new Color(220,38,38);
        if (score >= 35) return new Color(217,119,6);
        return new Color(22,163,74);
    }

    private static String formatTime(long time) {
        return new SimpleDateFormat("MM-dd HH:mm:ss",Locale.CHINA).format(new Date(time));
    }

    private static void drawText(Graphics2D graphics,String text,int x,int y,Color color,int style,int size) {
        graphics.setColor(color);
        graphics.setFont(new Font("Microsoft YaHei",style,size));
        graphics.drawString(text == null ? "" : text,x,y);
    }

    private static void drawRightText(Graphics2D graphics,String text,int right,int baseline,Color color,int style,int size) {
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
