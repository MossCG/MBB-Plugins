package org.moboxlab.mbb.status;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * 系统状态采集
 */
public class SystemStatusService {
    private static final long startTime = System.currentTimeMillis();
    private static volatile double networkInKBps = -1;
    private static volatile double networkOutKBps = -1;
    private static long[] lastNetworkBytes = null;
    private static long lastNetworkTime = 0L;

    public static String getStatusText() {
        StringBuilder builder = new StringBuilder();
        builder.append("MoBoxBot 运行状态\n");
        builder.append("CPU：").append(formatPercent(readCpuLoad())).append("\n");
        builder.append("内存：").append(readMemoryText()).append("\n");
        builder.append("硬盘：").append(readDiskText()).append("\n");
        builder.append("网络：").append(readNetworkText()).append("\n");
        builder.append("JVM：").append(readJvmText()).append("\n");
        builder.append("运行时长：").append(readUptimeText());
        return builder.toString();
    }

    /**
     * 网络采样，建议由插件定时任务周期调用
     */
    public static void sampleNetwork() {
        long[] current = readNetworkBytes();
        long now = System.currentTimeMillis();
        if (current == null) {
            networkInKBps = -1;
            networkOutKBps = -1;
            return;
        }
        if (lastNetworkBytes != null && lastNetworkTime > 0) {
            long deltaTime = now - lastNetworkTime;
            if (deltaTime > 0) {
                networkInKBps = Math.max(0,(current[0] - lastNetworkBytes[0]) * 1000.0 / deltaTime / 1024.0);
                networkOutKBps = Math.max(0,(current[1] - lastNetworkBytes[1]) * 1000.0 / deltaTime / 1024.0);
            }
        }
        lastNetworkBytes = current;
        lastNetworkTime = now;
    }

    private static double readCpuLoad() {
        try {
            java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
            if (bean instanceof com.sun.management.OperatingSystemMXBean) {
                double load = ((com.sun.management.OperatingSystemMXBean) bean).getSystemCpuLoad();
                if (load >= 0) return load * 100.0;
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private static String readMemoryText() {
        try {
            java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
            if (bean instanceof com.sun.management.OperatingSystemMXBean) {
                com.sun.management.OperatingSystemMXBean os = (com.sun.management.OperatingSystemMXBean) bean;
                long total = os.getTotalPhysicalMemorySize();
                long free = os.getFreePhysicalMemorySize();
                long used = total - free;
                return formatBytes(used)+" / "+formatBytes(total)+"（"+formatPercent(used*100.0/total)+"）";
            }
        } catch (Exception ignored) {
        }
        return "不可用";
    }

    private static String readDiskText() {
        File[] roots = File.listRoots();
        if (roots == null || roots.length == 0) return "不可用";
        long total = 0L;
        long usable = 0L;
        for (File root : roots) {
            try {
                total += root.getTotalSpace();
                usable += root.getUsableSpace();
            } catch (Exception ignored) {
            }
        }
        if (total <= 0) return "不可用";
        long used = total - usable;
        return formatBytes(used)+" / "+formatBytes(total)+"（"+formatPercent(used*100.0/total)+"）";
    }

    private static String readNetworkText() {
        if (networkInKBps < 0 || networkOutKBps < 0) return "采样中";
        return "↓ "+formatRate(networkInKBps)+" ↑ "+formatRate(networkOutKBps);
    }

    private static String readJvmText() {
        Runtime runtime = Runtime.getRuntime();
        long total = runtime.totalMemory();
        long free = runtime.freeMemory();
        long used = total - free;
        long max = runtime.maxMemory();
        return formatBytes(used)+" / "+formatBytes(max)+"（"+formatPercent(used*100.0/max)+"）";
    }

    private static String readUptimeText() {
        long seconds = (System.currentTimeMillis() - startTime) / 1000L;
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        if (days > 0) return days+"天 "+hours+"小时 "+minutes+"分";
        if (hours > 0) return hours+"小时 "+minutes+"分";
        return minutes+"分";
    }

    private static long[] readNetworkBytes() {
        String os = System.getProperty("os.name","").toLowerCase();
        if (os.contains("win")) return readWindowsNetworkBytes();
        return readLinuxNetworkBytes();
    }

    private static long[] readWindowsNetworkBytes() {
        try {
            Process process = new ProcessBuilder(
                    "powershell",
                    "-NoProfile",
                    "-Command",
                    "Get-NetAdapterStatistics | Select-Object ReceivedBytes,SentBytes | ConvertTo-Json -Compress")
                    .redirectErrorStream(true)
                    .start();
            StringBuilder output = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) output.append(line);
            reader.close();
            process.waitFor();
            String json = output.toString().trim();
            if (json.isEmpty()) return null;
            long received = 0L;
            long sent = 0L;
            if (json.startsWith("[")) {
                JSONArray array = JSONArray.parseArray(json);
                for (Object item : array) {
                    JSONObject object = (JSONObject) item;
                    received += object.getLongValue("ReceivedBytes");
                    sent += object.getLongValue("SentBytes");
                }
            } else {
                JSONObject object = JSONObject.parseObject(json);
                received = object.getLongValue("ReceivedBytes");
                sent = object.getLongValue("SentBytes");
            }
            return new long[]{received,sent};
        } catch (Exception e) {
            return null;
        }
    }

    private static long[] readLinuxNetworkBytes() {
        try {
            List<String> lines = Files.readAllLines(Paths.get("/proc/net/dev"),StandardCharsets.UTF_8);
            long received = 0L;
            long sent = 0L;
            for (String line : lines) {
                if (!line.contains(":")) continue;
                String[] parts = line.split(":");
                if (parts.length < 2) continue;
                String name = parts[0].trim();
                if ("lo".equals(name)) continue;
                String[] values = parts[1].trim().split("\\s+");
                if (values.length < 9) continue;
                received += Long.parseLong(values[0]);
                sent += Long.parseLong(values[8]);
            }
            return new long[]{received,sent};
        } catch (Exception e) {
            return null;
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes+" B";
        double kb = bytes / 1024.0;
        if (kb < 1024.0) return String.format("%.1f KB",kb);
        double mb = kb / 1024.0;
        if (mb < 1024.0) return String.format("%.1f MB",mb);
        double gb = mb / 1024.0;
        return String.format("%.2f GB",gb);
    }

    private static String formatRate(double kbPerSecond) {
        if (kbPerSecond < 1024.0) return String.format("%.1f KB/s",kbPerSecond);
        return String.format("%.2f MB/s",kbPerSecond / 1024.0);
    }

    private static String formatPercent(double percent) {
        if (percent < 0) return "不可用";
        return String.format("%.1f%%",percent);
    }
}
