package org.moboxlab.mbb.ai;

/**
 * 单个 AI 模型配置
 */
public class AIProfile {
    public String name = "";
    public String provider = "openai";
    public String baseUrl = "";
    public String apiKey = "";
    public String model = "";
    public double temperature = 0.7;
    public int maxTokens = 1024;
    public int timeoutSeconds = 60;

    public String resolveApiKey() {
        if (apiKey == null) return "";
        String value = apiKey.trim();
        if (!value.startsWith("env:")) return value;
        String envName = value.substring(4).trim();
        if (envName.isEmpty()) return "";
        String envValue = System.getenv(envName);
        return envValue == null ? "" : envValue.trim();
    }
}
