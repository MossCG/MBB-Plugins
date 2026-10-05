package org.moboxlab.mbb.ai;

import java.util.LinkedHashMap;
import java.util.Map;

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
    /** 思考强度，留空表示不向接口发送该字段 */
    public String reasoningEffort = "";
    /** 跳过 HTTPS 证书与主机名校验，仅在自建网关或代理环境使用 */
    public boolean insecureTls = false;
    public Map<String,String> headers = new LinkedHashMap<>();

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
