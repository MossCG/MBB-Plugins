package org.moboxlab.mbb.ai;

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
 * MBB-AI 状态与统计图片
 */
public class AIImageRenderer {
    private static final int WIDTH = 1000;
    private static final int PADDING = 36;
    private static final int GAP = 16;

    public static byte[] renderStatus(JSONObject status,JSONObject usage,String message) {
        if (status == null) return null;
        int height = 640;
        BufferedImage image = new BufferedImage(WIDTH,height,BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            initGraphics(graphics,height);
            drawHeader(graphics,"MBB-AI 服务状态",message);
            int cardWidth = (WIDTH - PADDING * 2 - GAP * 3) / 4;
            int cardY = PADDING + 76;
            drawMetric(graphics,PADDING,cardY,cardWidth,96,"服务状态",
                    status.getBooleanValue("enable") ? "启用" : "关闭","");
            drawMetric(graphics,PADDING + (cardWidth + GAP),cardY,cardWidth,96,"默认配置",
                    safe(status.getString("defaultProfile")),"");
            drawMetric(graphics,PADDING + (cardWidth + GAP) * 2,cardY,cardWidth,96,"最大并发",
                    String.valueOf(status.getIntValue("maxConcurrent")),"");
            drawMetric(graphics,PADDING + (cardWidth + GAP) * 3,cardY,cardWidth,96,"缓存秒数",
                    String.valueOf(status.getIntValue("cacheSecond")),"");

            int totalY = cardY + 126;
            JSONObject total = usage == null ? new JSONObject(true) : usage.getJSONObject("total");
            drawMetric(graphics,PADDING,totalY,cardWidth,96,"总请求",String.valueOf(value(total,"requests")),"");
            drawMetric(graphics,PADDING + (cardWidth + GAP),totalY,cardWidth,96,"总 Token",
                    formatNumber(value(total,"totalTokens")),"");
            drawMetric(graphics,PADDING + (cardWidth + GAP) * 2,totalY,cardWidth,96,"成功率",
                    formatPercent(total == null ? 0 : total.getDoubleValue("successRate")),"");
            drawMetric(graphics,PADDING + (cardWidth + GAP) * 3,totalY,cardWidth,96,"提示词缓存",
                    formatPercent(total == null ? 0 : total.getDoubleValue("promptCacheHitRate")),"");

            int profileY = totalY + 132;
            drawPanel(graphics,PADDING,profileY,WIDTH - PADDING * 2,250,"模型配置");
            JSONArray profiles = status.getJSONArray("profiles");
            int lineY = profileY + 64;
            if (profiles == null || profiles.isEmpty()) {
                drawText(graphics,"没有配置模型。",PADDING + 20,lineY,new Color(120,113,108),Font.PLAIN,17);
            } else {
                for (int i = 0; i < profiles.size() && i < 6; i++) {
                    JSONObject profile = profiles.getJSONObject(i);
                    String text = safe(profile.getString("name"))
                            +" | "+safe(profile.getString("model"))
                            +" | "+safe(profile.getString("baseUrl"));
                    drawText(graphics,fitText(graphics,text,WIDTH - PADDING * 2 - 40),
                            PADDING + 20,lineY,new Color(68,64,60),Font.BOLD,17);
                    lineY += 28;
                }
            }
        } finally {
            graphics.dispose();
        }
        return writePng(image);
    }

    public static byte[] renderUsage(JSONObject usage,int days) {
        if (usage == null) return null;
        JSONArray daily = usage.getJSONArray("daily");
        JSONArray profiles = usage.getJSONArray("profiles");
        int rowCount = profiles == null ? 0 : Math.min(6,profiles.size());
        int height = 760 + rowCount * 28;
        BufferedImage image = new BufferedImage(WIDTH,height,BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            initGraphics(graphics,height);
            drawHeader(graphics,"MBB-AI 使用统计","最近 "+days+" 天");

            JSONObject total = usage.getJSONObject("total");
            JSONObject today = usage.getJSONObject("today");
            int cardWidth = (WIDTH - PADDING * 2 - GAP * 4) / 5;
            int cardY = PADDING + 76;
            drawMetric(graphics,PADDING,cardY,cardWidth,96,"总请求",String.valueOf(value(total,"requests")),"");
            drawMetric(graphics,PADDING + (cardWidth + GAP),cardY,cardWidth,96,"成功率",
                    formatPercent(total == null ? 0 : total.getDoubleValue("successRate")),"");
            drawMetric(graphics,PADDING + (cardWidth + GAP) * 2,cardY,cardWidth,96,"总 Token",
                    formatNumber(value(total,"totalTokens")),"");
            drawMetric(graphics,PADDING + (cardWidth + GAP) * 3,cardY,cardWidth,96,"平均耗时",
                    formatMillis(total == null ? 0 : total.getLongValue("averageLatencyMs")),"");
            drawMetric(graphics,PADDING + (cardWidth + GAP) * 4,cardY,cardWidth,96,"提示词缓存",
                    formatPercent(total == null ? 0 : total.getDoubleValue("promptCacheHitRate")),"");

            int todayY = cardY + 124;
            drawText(graphics,"今日：请求 "+value(today,"requests")
                    +" | Token "+formatNumber(value(today,"totalTokens"))
                    +" | 成功 "+value(today,"successes")
                    +" | 失败 "+value(today,"failures")
                    +" | 服务端缓存 "+formatNumber(value(today,"cachedPromptTokens"))+" token"
                    +" | 本地缓存 "+value(today,"cachedHits")+" 次",
                    PADDING,todayY,new Color(120,113,108),Font.PLAIN,16);

            int chartY = todayY + 24;
            int chartPanelHeight = 260;
            drawChart(graphics,PADDING,chartY,WIDTH - PADDING * 2,chartPanelHeight,daily);

            int tableY = chartY + chartPanelHeight + 28;
            drawPanel(graphics,PADDING,tableY,WIDTH - PADDING * 2,112 + rowCount * 28,"模型配置统计");
            int rowY = tableY + 66;
            drawTableHeader(graphics,PADDING + 20,rowY);
            rowY += 26;
            if (profiles == null || profiles.isEmpty()) {
                drawText(graphics,"暂无统计数据。",PADDING + 20,rowY,new Color(120,113,108),Font.PLAIN,16);
            } else {
                for (int i = 0; i < rowCount; i++) {
                    JSONObject item = profiles.getJSONObject(i);
                    drawTableRow(graphics,PADDING + 20,rowY,item);
                    rowY += 28;
                }
            }
        } finally {
            graphics.dispose();
        }
        return writePng(image);
    }

    private static void initGraphics(Graphics2D graphics,int height) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setColor(new Color(255,252,245));
        graphics.fillRect(0,0,WIDTH,height);
        graphics.setColor(new Color(250,231,180));
        graphics.drawRoundRect(PADDING - 12,PADDING - 12,WIDTH - (PADDING - 12) * 2,height - (PADDING - 12) * 2,22,22);
    }

    private static void drawHeader(Graphics2D graphics,String title,String subtitle) {
        graphics.setColor(new Color(180,83,9));
        graphics.setFont(new Font("Microsoft YaHei",Font.BOLD,30));
        graphics.drawString(title,PADDING,PADDING + 28);
        graphics.setColor(new Color(120,113,108));
        graphics.setFont(new Font("Microsoft YaHei",Font.PLAIN,15));
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.CHINA).format(new Date());
        String text = subtitle == null || subtitle.isEmpty() ? time : subtitle+"  "+time;
        drawRightText(graphics,text,WIDTH - PADDING,PADDING + 26);
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
        drawText(graphics,fitText(graphics,value,width - 32),x + 16,y + 64,
                new Color(180,83,9),Font.BOLD,23);
        if (detail != null && !detail.isEmpty()) {
            drawText(graphics,fitText(graphics,detail,width - 32),x + 16,y + 84,
                    new Color(120,113,108),Font.PLAIN,13);
        }
    }

    private static void drawPanel(Graphics2D graphics,int x,int y,int width,int height,String title) {
        graphics.setColor(Color.WHITE);
        graphics.fillRoundRect(x,y,width,height,16,16);
        graphics.setColor(new Color(250,231,180));
        graphics.drawRoundRect(x,y,width,height,16,16);
        drawText(graphics,title,x + 20,y + 36,new Color(68,64,60),Font.BOLD,20);
    }

    private static void drawChart(Graphics2D graphics,int x,int y,int width,int height,JSONArray daily) {
        drawPanel(graphics,x,y,width,height,"最近 7 天 Token 趋势");
        drawChartLegend(graphics,x + width - 350,y + 36);
        int chartX = x + 24;
        int chartY = y + 86;
        int chartWidth = width - 48;
        int chartHeight = height - 116;
        graphics.setColor(new Color(241,238,232));
        graphics.drawLine(chartX,chartY + chartHeight,chartX + chartWidth,chartY + chartHeight);
        if (daily == null || daily.isEmpty()) return;

        long max = 0L;
        for (int i = 0; i < daily.size(); i++) {
            JSONObject item = daily.getJSONObject(i);
            max = Math.max(max,value(item,"totalTokens"));
            max = Math.max(max,value(item,"cachedPromptTokens"));
        }
        if (max <= 0) max = 1L;
        int count = daily.size();
        int slot = chartWidth / Math.max(1,count);
        int barGap = 10;
        int barWidth = Math.max(14,(slot - barGap - 24) / 2);
        int groupWidth = barWidth * 2 + barGap;
        for (int i = 0; i < count; i++) {
            JSONObject item = daily.getJSONObject(i);
            long tokens = value(item,"totalTokens");
            long cachedTokens = value(item,"cachedPromptTokens");
            int totalHeight = (int)Math.round(chartHeight * tokens / (double)max);
            int cachedHeight = (int)Math.round(chartHeight * cachedTokens / (double)max);
            int groupX = chartX + i * slot + (slot - groupWidth) / 2;
            int totalX = groupX;
            int cachedX = groupX + barWidth + barGap;
            int totalY = chartY + chartHeight - totalHeight;
            int cachedY = chartY + chartHeight - cachedHeight;

            graphics.setColor(new Color(37,99,235));
            graphics.fillRoundRect(totalX,totalY,barWidth,Math.max(2,totalHeight),7,7);
            graphics.setColor(new Color(245,158,11));
            graphics.fillRoundRect(cachedX,cachedY,barWidth,Math.max(2,cachedHeight),7,7);

            graphics.setFont(new Font("Microsoft YaHei",Font.BOLD,11));
            graphics.setColor(new Color(37,99,235));
            String totalText = formatMillion(tokens);
            int totalTextWidth = graphics.getFontMetrics().stringWidth(totalText);
            graphics.drawString(totalText,totalX + (barWidth - totalTextWidth) / 2,totalY - 8);
            graphics.setColor(new Color(180,83,9));
            String cachedText = formatMillion(cachedTokens);
            int cachedTextWidth = graphics.getFontMetrics().stringWidth(cachedText);
            graphics.drawString(cachedText,cachedX + (barWidth - cachedTextWidth) / 2,cachedY - 8);

            String date = safe(item.getString("date"));
            if (date.length() >= 10) date = date.substring(5);
            graphics.setColor(new Color(120,113,108));
            graphics.setFont(new Font("Microsoft YaHei",Font.PLAIN,13));
            int textWidth = graphics.getFontMetrics().stringWidth(date);
            graphics.drawString(date,groupX + (groupWidth - textWidth) / 2,chartY + chartHeight + 22);
        }
    }

    private static void drawChartLegend(Graphics2D graphics,int x,int baseline) {
        graphics.setFont(new Font("Microsoft YaHei",Font.PLAIN,12));
        graphics.setColor(new Color(37,99,235));
        graphics.fillRoundRect(x,baseline - 10,10,10,3,3);
        graphics.setColor(new Color(120,113,108));
        graphics.drawString("总 Token",x + 16,baseline);
        graphics.setColor(new Color(245,158,11));
        graphics.fillRoundRect(x + 82,baseline - 10,10,10,3,3);
        graphics.setColor(new Color(120,113,108));
        graphics.drawString("缓存命中",x + 98,baseline);
        graphics.setColor(new Color(146,138,130));
        graphics.drawString("单位：百万 Token（M）",x + 166,baseline + 20);
    }

    private static void drawTableHeader(Graphics2D graphics,int x,int y) {
        graphics.setColor(new Color(146,138,130));
        graphics.setFont(new Font("Microsoft YaHei",Font.PLAIN,14));
        graphics.drawString("配置",x,y);
        graphics.drawString("请求",x + 260,y);
        graphics.drawString("Token",x + 400,y);
        graphics.drawString("成功率",x + 580,y);
    }

    private static void drawTableRow(Graphics2D graphics,int x,int y,JSONObject item) {
        graphics.setColor(new Color(68,64,60));
        graphics.setFont(new Font("Microsoft YaHei",Font.BOLD,15));
        graphics.drawString(fitText(graphics,safe(item.getString("name")),230),x,y);
        graphics.setFont(new Font("Microsoft YaHei",Font.PLAIN,15));
        graphics.drawString(String.valueOf(value(item,"requests")),x + 260,y);
        graphics.drawString(formatNumber(value(item,"totalTokens")),x + 400,y);
        graphics.drawString(formatPercent(item.getDoubleValue("successRate")),x + 580,y);
    }

    private static void drawText(Graphics2D graphics,String text,int x,int y,Color color,int style,int size) {
        graphics.setColor(color);
        graphics.setFont(new Font("Microsoft YaHei",style,size));
        graphics.drawString(text == null ? "" : text,x,y);
    }

    private static void drawRightText(Graphics2D graphics,String text,int right,int baseline) {
        int width = graphics.getFontMetrics().stringWidth(text == null ? "" : text);
        graphics.drawString(text == null ? "" : text,right - width,baseline);
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

    private static String formatNumber(long value) {
        return String.format(Locale.US,"%,d",value);
    }

    private static String formatMillion(long value) {
        return String.format(Locale.US,"%.1fM",value / 1_000_000.0);
    }

    private static String formatPercent(double value) {
        return String.format(Locale.US,"%.1f%%",value);
    }

    private static String formatMillis(long value) {
        if (value < 1000) return value+" ms";
        return String.format(Locale.US,"%.2f s",value / 1000.0);
    }

    private static String safe(String value) {
        return value == null || value.isEmpty() ? "未配置" : value;
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
