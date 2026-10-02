package org.moboxlab.mbb.status;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 系统状态采集
 */
public class SystemStatusService {
    private static final long startTime = System.currentTimeMillis();
    private static final String cpuName = readCpuName();
    private static volatile double networkInKBps = -1;
    private static volatile double networkOutKBps = -1;
    private static long[] lastNetworkBytes = null;
    private static long lastNetworkTime = 0L;

    public static class StatusInfo {
        public double cpuLoad = -1;
        public long memoryUsed = 0L;
        public long memoryTotal = 0L;
        public double memoryPercent = -1;
        public long diskUsed = 0L;
        public long diskTotal = 0L;
        public double diskPercent = -1;
        public long jvmUsed = 0L;
        public long jvmMax = 0L;
        public double jvmPercent = -1;
        public double networkInKBps = -1;
        public double networkOutKBps = -1;
        public String uptime = "";
        public String cpuName = "";
        public String hostName = "";
        public String osName = "";
        public String osArch = "";
        public String javaVersion = "";
        public int availableProcessors = 0;
    }

    public static StatusInfo getStatusInfo() {
        StatusInfo info = new StatusInfo();
        info.cpuLoad = readCpuLoad();

        long[] memory = readMemoryUsage();
        info.memoryUsed = memory[0];
        info.memoryTotal = memory[1];
        info.memoryPercent = percent(memory[0],memory[1]);

        long[] disk = readDiskUsage();
        info.diskUsed = disk[0];
        info.diskTotal = disk[1];
        info.diskPercent = percent(disk[0],disk[1]);

        long[] jvm = readJvmUsage();
        info.jvmUsed = jvm[0];
        info.jvmMax = jvm[1];
        info.jvmPercent = percent(jvm[0],jvm[1]);

        info.networkInKBps = networkInKBps;
        info.networkOutKBps = networkOutKBps;
        info.uptime = readUptimeText();
        info.cpuName = cpuName;
        info.hostName = readHostName();
        info.osName = System.getProperty("os.name","未知")+" "+System.getProperty("os.version","");
        info.osArch = System.getProperty("os.arch","未知");
        info.javaVersion = System.getProperty("java.version","未知");
        info.availableProcessors = Runtime.getRuntime().availableProcessors();
        return info;
    }

    public static String getStatusText() {
        StatusInfo info = getStatusInfo();
        StringBuilder builder = new StringBuilder();
        builder.append("MoBoxBot 运行状态\n");
        builder.append("主机：").append(info.hostName).append("\n");
        builder.append("系统：").append(info.osName).append(" ").append(info.osArch).append("\n");
        builder.append("处理器：").append(info.cpuName).append("\n");
        builder.append("CPU 核心：").append(info.availableProcessors).append("\n");
        builder.append("CPU：").append(formatPercent(info.cpuLoad)).append("\n");
        builder.append("内存：").append(formatBytes(info.memoryUsed)).append(" / ").append(formatBytes(info.memoryTotal))
                .append("（").append(formatPercent(info.memoryPercent)).append("）\n");
        builder.append("硬盘：").append(formatBytes(info.diskUsed)).append(" / ").append(formatBytes(info.diskTotal))
                .append("（").append(formatPercent(info.diskPercent)).append("）\n");
        builder.append("JVM：").append(formatBytes(info.jvmUsed)).append(" / ").append(formatBytes(info.jvmMax))
                .append("（").append(formatPercent(info.jvmPercent)).append("）\n");
        builder.append("网络：").append(readNetworkText()).append("\n");
        builder.append("运行时长：").append(info.uptime);
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

    private static long[] readMemoryUsage() {
        try {
            java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
            if (bean instanceof com.sun.management.OperatingSystemMXBean) {
                com.sun.management.OperatingSystemMXBean os = (com.sun.management.OperatingSystemMXBean) bean;
                long total = os.getTotalPhysicalMemorySize();
                long free = os.getFreePhysicalMemorySize();
                return new long[]{Math.max(0,total - free),total};
            }
        } catch (Exception ignored) {
        }
        return new long[]{0L,0L};
    }

    private static long[] readDiskUsage() {
        File[] roots = File.listRoots();
        if (roots == null || roots.length == 0) return new long[]{0L,0L};
        long total = 0L;
        long usable = 0L;
        for (File root : roots) {
            try {
                total += root.getTotalSpace();
                usable += root.getUsableSpace();
            } catch (Exception ignored) {
            }
        }
        return new long[]{Math.max(0,total - usable),total};
    }

    private static long[] readJvmUsage() {
        Runtime runtime = Runtime.getRuntime();
        long total = runtime.totalMemory();
        long free = runtime.freeMemory();
        return new long[]{total - free,runtime.maxMemory()};
    }

    private static String readNetworkText() {
        if (networkInKBps < 0 || networkOutKBps < 0) return "采样中";
        return "↓ "+formatRate(networkInKBps)+" ↑ "+formatRate(networkOutKBps);
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

    private static String readHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            String computerName = System.getenv("COMPUTERNAME");
            if (computerName != null && !computerName.trim().isEmpty()) return computerName;
            String hostName = System.getenv("HOSTNAME");
            return hostName == null || hostName.trim().isEmpty() ? "未知主机" : hostName;
        }
    }

    private static String readCpuName() {
        String os = System.getProperty("os.name","").toLowerCase();
        if (os.contains("win")) {
            String windowsName = readWindowsCpuName();
            if (windowsName != null && !windowsName.isEmpty()) return windowsName;
        }
        String linuxName = readLinuxCpuName();
        if (linuxName != null && !linuxName.isEmpty()) return linuxName;
        String identifier = System.getenv("PROCESSOR_IDENTIFIER");
        return identifier == null || identifier.trim().isEmpty() ? "未知处理器" : identifier.trim();
    }

    private static String readWindowsCpuName() {
        try {
            Process process = new ProcessBuilder(
                    "powershell",
                    "-NoProfile",
                    "-Command",
                    "(Get-CimInstance Win32_Processor | Select-Object -First 1 -ExpandProperty Name)")
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(3,TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "";
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8));
            String line = reader.readLine();
            reader.close();
            return line == null ? "" : line.trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static String readLinuxCpuName() {
        try {
            List<String> lines = Files.readAllLines(Paths.get("/proc/cpuinfo"),StandardCharsets.UTF_8);
            for (String line : lines) {
                if (!line.toLowerCase().startsWith("model name")) continue;
                int index = line.indexOf(':');
                if (index >= 0) return line.substring(index + 1).trim();
            }
        } catch (Exception ignored) {
        }
        return "";
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
            if (!process.waitFor(3,TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            StringBuilder output = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) output.append(line);
            reader.close();
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

    static double percent(long used,long total) {
        if (total <= 0) return -1;
        return used * 100.0 / total;
    }

    static String formatBytes(long bytes) {
        if (bytes <= 0L) return "0 B";
        if (bytes < 1024L) return bytes+" B";
        double kb = bytes / 1024.0;
        if (kb < 1024.0) return String.format(Locale.US,"%.1f KB",kb);
        double mb = kb / 1024.0;
        if (mb < 1024.0) return String.format(Locale.US,"%.1f MB",mb);
        double gb = mb / 1024.0;
        return String.format(Locale.US,"%.2f GB",gb);
    }

    static String formatRate(double kbPerSecond) {
        if (kbPerSecond < 0) return "采样中";
        if (kbPerSecond < 1024.0) return String.format(Locale.US,"%.1f KB/s",kbPerSecond);
        return String.format(Locale.US,"%.2f MB/s",kbPerSecond / 1024.0);
    }

    static String formatPercent(double percent) {
        if (percent < 0) return "不可用";
        return String.format(Locale.US,"%.1f%%",percent);
    }
}
