package org.moboxlab.mbb.ai;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.lang.reflect.Method;

/**
 * 正文 DSML 工具调用兼容回归测试
 */
public class AIToolCallTextParserTestMain {
    public static void main(String[] args) {
        String content = "<｜DSML｜ calls>\n"
                +"<｜DSML ｜ invoke name=\"draw\">\n"
                +"<｜DSML｜ parameter name=\"brief\" string=\"false\">"
                +"{\"subject\":\"当前角色\",\"scene\":\"教室\",\"composition\":\"中景\"}"
                +"</｜DSML｜ parameter>\n"
                +"</｜DSML｜ invoke>\n"
                +"</｜DSML｜ calls>";
        if (!ToolCallTextParser.containsProtocolMarker(content)) {
            throw new AssertionError("应识别全角 DSML 协议标签");
        }
        JSONArray calls = ToolCallTextParser.parse(content);
        if (calls == null || calls.size() != 1) {
            throw new AssertionError("应解析出一个工具调用");
        }
        JSONObject call = calls.getJSONObject(0);
        JSONObject function = call.getJSONObject("function");
        if (function == null || !"draw".equals(function.getString("name"))) {
            throw new AssertionError("工具名应解析为 draw");
        }
        JSONObject arguments = JSONObject.parseObject(function.getString("arguments"));
        JSONObject brief = arguments.getJSONObject("brief");
        if (brief == null || !"当前角色".equals(brief.getString("subject"))) {
            throw new AssertionError("brief 参数应恢复为 JSON 对象");
        }

        String withText = "稍等，我去画\n"+content;
        if (!"稍等，我去画".equals(ToolCallTextParser.stripProtocolBlocks(withText))) {
            throw new AssertionError("协议块前的正常文字应保留");
        }
        if (ToolCallTextParser.parse("普通聊天内容").size() != 0) {
            throw new AssertionError("普通聊天不应被识别为工具调用");
        }
        String invokeOnly = "<｜DSML ｜ invoke name=\"draw\">"
                +"<｜DSML｜ parameter name=\"brief\" string=\"false\">{\"scene\":\"教室\"}"
                +"</｜DSML｜ parameter></｜DSML｜ invoke>";
        if (!ToolCallTextParser.containsProtocolMarker(invokeOnly)
                || ToolCallTextParser.parse(invokeOnly).size() != 1) {
            throw new AssertionError("缺少 calls 包装或竖线旁有空格时仍应识别工具调用");
        }

        try {
            JSONObject message = new JSONObject(true);
            message.put("content",content);
            JSONObject choice = new JSONObject(true);
            choice.put("message",message);
            choice.put("finish_reason","stop");
            JSONArray choices = new JSONArray();
            choices.add(choice);
            JSONObject response = new JSONObject(true);
            response.put("choices",choices);
            Method success = OpenAIProvider.class.getDeclaredMethod(
                    "success",JSONObject.class,AIProfile.class);
            success.setAccessible(true);
            JSONObject normalized = (JSONObject)success.invoke(null,response,new AIProfile());
            if (!normalized.getBooleanValue("status")
                    || normalized.getJSONArray("toolCalls") == null
                    || normalized.getJSONArray("toolCalls").isEmpty()
                    || !normalized.getString("content").isEmpty()) {
                throw new AssertionError("OpenAIProvider 应把 DSML 正文转换为标准 toolCalls");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
