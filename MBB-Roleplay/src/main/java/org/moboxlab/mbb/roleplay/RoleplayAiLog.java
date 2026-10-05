package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.PluginLogger;

/**
 * 角色扮演 AI 调用日志，输出格式与 MBB-Vision 的识图日志保持一致
 */
public class RoleplayAiLog {
    public static void log(PluginLogger logger,String tag,long groupID,
                           JSONObject response,long elapsedMillis) {
        if (logger == null) return;
        if (response == null) {
            logger.sendWarn("[角色] "+tag+" 群"+groupID+" 调用失败：没有返回结果");
            return;
        }
        if (!response.getBooleanValue("status")) {
            logger.sendWarn("[角色] "+tag+" 群"+groupID+" 调用失败："
                    +safe(response.getString("message"))+" 错误类型="+safe(response.getString("errorType"))
                    +" 耗时="+elapsedMillis+"ms");
            return;
        }
        JSONObject usage = response.getJSONObject("usage");
        int promptTokens = usage == null ? 0 : usage.getIntValue("promptTokens");
        int completionTokens = usage == null ? 0 : usage.getIntValue("completionTokens");
        int reasoningLength = safe(response.getString("reasoningContent")).length();
        logger.sendInfo("[角色] "+tag+" 群"+groupID
                +" profile="+safe(response.getString("profile"))
                +" model="+safe(response.getString("model"))
                +" 耗时="+elapsedMillis+"ms"
                +" finish="+safe(response.getString("finishReason"))
                +" token="+promptTokens+"/"+completionTokens
                +" 缓存="+(response.getBooleanValue("cached") ? "是" : "否")
                +(reasoningLength > 0 ? " 思考="+reasoningLength : "")
                +" 长度="+safe(response.getString("content")).length()
                +" 内容="+shortText(safe(response.getString("content")),80));
    }

    public static String shortText(String text,int limit) {
        String value = safe(text).replace("\n"," ").replace("\r"," ").trim();
        if (value.length() <= limit) return value;
        return value.substring(0,limit)+"...";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
