package org.moboxlab.mbb.status;

import org.moboxlab.mbb.status.SystemStatusService.StatusInfo;

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
 * 运行状态图片绘制
 */
public class StatusImageRenderer {
    private static final int WIDTH = 980;
    private static final int PADDING = 36;
    private static final int GAP = 18;

    public static byte[] render(StatusInfo info,int refreshSecond) {
        if (info == null) return null;
        int cardWidth = (WIDTH - PADDING * 2 - GAP) / 2;
        int cardHeight = 118;
        int metricTop = PADDING + 76;
        int networkTop = metricTop + cardHeight * 3 + GAP * 2 + 18;
        int configTop = networkTop + 104;
        int configRows = 6;
        int configHeight = 72 + configRows * 30;
        int height = configTop + configHeight + PADDING;

        BufferedImage image = new BufferedImage(WIDTH,height,BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            graphics.setColor(new Color(255,252,245));
            graphics.fillRect(0,0,WIDTH,height);
            graphics.setColor(new Color(250,231,180));
            graphics.drawRoundRect(PADDING - 12,PADDING - 12,WIDTH - (PADDING - 12) * 2,height - (PADDING - 12) * 2,22,22);

            graphics.setColor(new Color(180,83,9));
            graphics.setFont(new Font("Microsoft YaHei",Font.BOLD,30));
            graphics.drawString("MoBoxBot 运行状态",PADDING,PADDING + 28);

            graphics.setColor(new Color(120,113,108));
            graphics.setFont(new Font("Microsoft YaHei",Font.PLAIN,16));
            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.CHINA).format(new Date());
            drawRightText(graphics,time,WIDTH - PADDING,PADDING + 26);
            graphics.setColor(new Color(251,191,36));
            graphics.fillRoundRect(PADDING,PADDING + 44,86,5,5,5);

            int firstY = metricTop;
            int secondY = metricTop + cardHeight + GAP;
            int thirdY = metricTop + (cardHeight + GAP) * 2;
            drawMetric(graphics,PADDING,firstY,cardWidth,cardHeight,
                    "CPU 占用","系统整体",info.cpuLoad);
            drawMetric(graphics,PADDING + cardWidth + GAP,firstY,cardWidth,cardHeight,
                    "物理内存",SystemStatusService.formatBytes(info.memoryUsed)+" / "+SystemStatusService.formatBytes(info.memoryTotal),info.memoryPercent);
            drawMetric(graphics,PADDING,secondY,cardWidth,cardHeight,
                    "磁盘占用",SystemStatusService.formatBytes(info.diskUsed)+" / "+SystemStatusService.formatBytes(info.diskTotal),info.diskPercent);
            drawMetric(graphics,PADDING + cardWidth + GAP,secondY,cardWidth,cardHeight,
                    "JVM 堆内存",SystemStatusService.formatBytes(info.jvmUsed)+" / "+SystemStatusService.formatBytes(info.jvmMax),info.jvmPercent);
            drawMetric(graphics,PADDING,thirdY,cardWidth,cardHeight,
                    "显卡占用",gpuDetail(info),info.gpuLoad);
            drawMetric(graphics,PADDING + cardWidth + GAP,thirdY,cardWidth,cardHeight,
                    "显存占用",gpuMemoryDetail(info),info.gpuMemoryPercent);

            drawNetwork(graphics,PADDING,networkTop,WIDTH - PADDING * 2,86,info);
            drawConfig(graphics,PADDING,configTop,WIDTH - PADDING * 2,configHeight,info,refreshSecond);
        } finally {
            graphics.dispose();
        }

        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image,"png",output);
            return output.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private static void drawMetric(Graphics2D graphics,int x,int y,int width,int height,
                                   String label,String detail,double percent) {
        graphics.setColor(new Color(255,255,255));
        graphics.fillRoundRect(x,y,width,height,16,16);
        graphics.setColor(new Color(250,231,180));
        graphics.drawRoundRect(x,y,width,height,16,16);

        graphics.setFont(new Font("Microsoft YaHei",Font.BOLD,20));
        graphics.setColor(new Color(68,64,60));
        graphics.drawString(label,x + 20,y + 32);

        graphics.setFont(new Font("Microsoft YaHei",Font.BOLD,24));
        graphics.setColor(percentColor(percent));
        drawRightText(graphics,percentText(percent),x + width - 20,y + 34);

        int barX = x + 20;
        int barY = y + 58;
        int barWidth = width - 40;
        int barHeight = 12;
        graphics.setColor(new Color(241,238,232));
        graphics.fillRoundRect(barX,barY,barWidth,barHeight,barHeight,barHeight);
        if (percent > 0) {
            int fill = (int)Math.round(barWidth * Math.min(100.0,percent) / 100.0);
            if (fill < barHeight) fill = barHeight;
            graphics.setColor(percentColor(percent));
            graphics.fillRoundRect(barX,barY,fill,barHeight,barHeight,barHeight);
        }

        graphics.setFont(new Font("Microsoft YaHei",Font.PLAIN,15));
        graphics.setColor(new Color(120,113,108));
        graphics.drawString(fitText(graphics,detail,barWidth),barX,y + 99);
    }

    private static void drawNetwork(Graphics2D graphics,int x,int y,int width,int height,StatusInfo info) {
        graphics.setColor(new Color(255,255,255));
        graphics.fillRoundRect(x,y,width,height,16,16);
        graphics.setColor(new Color(250,231,180));
        graphics.drawRoundRect(x,y,width,height,16,16);

        graphics.setFont(new Font("Microsoft YaHei",Font.BOLD,20));
        graphics.setColor(new Color(68,64,60));
        graphics.drawString("实时网络",x + 20,y + 32);

        graphics.setFont(new Font("Microsoft YaHei",Font.BOLD,22));
        graphics.setColor(new Color(37,99,235));
        graphics.drawString("↓ "+SystemStatusService.formatRate(info.networkInKBps),x + 20,y + 68);
        graphics.setColor(new Color(22,163,74));
        graphics.drawString("↑ "+SystemStatusService.formatRate(info.networkOutKBps),x + width / 2,y + 68);

        double maxRate = Math.max(1024.0,Math.max(info.networkInKBps,info.networkOutKBps) * 1.2);
        int barX = x + width - 290;
        int barWidth = 250;
        drawRateBar(graphics,barX,y + 48,barWidth,10,info.networkInKBps,maxRate,new Color(37,99,235));
        drawRateBar(graphics,barX,y + 68,barWidth,10,info.networkOutKBps,maxRate,new Color(22,163,74));
    }

    private static void drawRateBar(Graphics2D graphics,int x,int y,int width,int height,
                                    double value,double max,Color color) {
        graphics.setColor(new Color(241,238,232));
        graphics.fillRoundRect(x,y,width,height,height,height);
        if (value <= 0 || max <= 0) return;
        int fill = (int)Math.round(width * Math.min(1.0,value / max));
        if (fill < height) fill = height;
        graphics.setColor(color);
        graphics.fillRoundRect(x,y,fill,height,height,height);
    }

    private static void drawConfig(Graphics2D graphics,int x,int y,int width,int height,
                                   StatusInfo info,int refreshSecond) {
        graphics.setColor(new Color(255,255,255));
        graphics.fillRoundRect(x,y,width,height,16,16);
        graphics.setColor(new Color(250,231,180));
        graphics.drawRoundRect(x,y,width,height,16,16);

        graphics.setFont(new Font("Microsoft YaHei",Font.BOLD,20));
        graphics.setColor(new Color(68,64,60));
        graphics.drawString("服务器配置",x + 20,y + 34);

        int rowY = y + 68;
        int columnWidth = (width - 80) / 2;
        drawConfigRow(graphics,x + 20,rowY,"主机名",info.hostName);
        drawConfigRow(graphics,x + 40 + columnWidth,rowY,"操作系统",info.osName);
        drawConfigRow(graphics,x + 20,rowY + 30,"系统架构",info.osArch);
        drawConfigRow(graphics,x + 40 + columnWidth,rowY + 30,"CPU 核心",String.valueOf(info.availableProcessors));
        drawConfigRow(graphics,x + 20,rowY + 60,"处理器",info.cpuName);
        drawConfigRow(graphics,x + 40 + columnWidth,rowY + 60,"Java",info.javaVersion);
        drawConfigRow(graphics,x + 20,rowY + 90,"显卡",info.gpuCount > 0 ? info.gpuName : "未检测到");
        drawConfigRow(graphics,x + 40 + columnWidth,rowY + 90,"显卡温度",SystemStatusService.formatTemperature(info.gpuTemperature));
        drawConfigRow(graphics,x + 20,rowY + 120,"物理内存",SystemStatusService.formatBytes(info.memoryTotal));
        drawConfigRow(graphics,x + 40 + columnWidth,rowY + 120,"磁盘容量",SystemStatusService.formatBytes(info.diskTotal));
        drawConfigRow(graphics,x + 20,rowY + 150,"运行时长",info.uptime);
        drawConfigRow(graphics,x + 40 + columnWidth,rowY + 150,"采样间隔",refreshSecond+" 秒");
    }

    private static void drawConfigRow(Graphics2D graphics,int x,int y,String label,String value) {
        graphics.setFont(new Font("Microsoft YaHei",Font.PLAIN,15));
        graphics.setColor(new Color(146,138,130));
        graphics.drawString(label,x,y);
        graphics.setFont(new Font("Microsoft YaHei",Font.BOLD,15));
        graphics.setColor(new Color(68,64,60));
        graphics.drawString(fitText(graphics,value == null || value.isEmpty() ? "未知" : value,250),x + 76,y);
    }

    private static void drawRightText(Graphics2D graphics,String text,int right,int baseline) {
        int width = graphics.getFontMetrics().stringWidth(text);
        graphics.drawString(text,right - width,baseline);
    }

    private static String percentText(double percent) {
        return percent < 0 ? "不可用" : SystemStatusService.formatPercent(percent);
    }

    private static String gpuDetail(StatusInfo info) {
        if (info.gpuCount <= 0) return "未检测到显卡";
        return info.gpuCount > 1 ? info.gpuName+" 等 "+info.gpuCount+" 张" : info.gpuName;
    }

    private static String gpuMemoryDetail(StatusInfo info) {
        if (info.gpuCount <= 0) return "未检测到显卡";
        if (info.gpuMemoryTotal <= 0) return "显存信息不可用";
        return SystemStatusService.formatBytes(info.gpuMemoryUsed)+" / "
                +SystemStatusService.formatBytes(info.gpuMemoryTotal);
    }

    private static Color percentColor(double percent) {
        if (percent < 0) return new Color(120,113,108);
        if (percent >= 90) return new Color(220,38,38);
        if (percent >= 75) return new Color(217,119,6);
        return new Color(22,163,74);
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
}
