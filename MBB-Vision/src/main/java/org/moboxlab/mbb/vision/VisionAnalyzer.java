package org.moboxlab.mbb.vision;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Plugin;
import org.moboxlab.moboxbot.API.PluginService;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 图片理解
 */
public class VisionAnalyzer {
    private final Plugin plugin;

    public VisionAnalyzer(Plugin plugin) {
        this.plugin = plugin;
    }

    public JSONObject analyze(PluginService ai,String profile,int maxTokens,
                              VisionImageSource.ImageData image,String kind,String context,
                              String reference) {
        JSONObject result = new JSONObject(true);
        try {
            String dataUri = "data:"+image.mime+";base64,"
                    +Base64.getEncoder().encodeToString(image.bytes);
            JSONArray content = new JSONArray();
            JSONObject text = new JSONObject(true);
            text.put("type","text");
            text.put("text",buildPrompt(kind,context,reference));
            content.add(text);
            JSONObject imageUrl = new JSONObject(true);
            imageUrl.put("url",dataUri);
            JSONObject imageObject = new JSONObject(true);
            imageObject.put("type","image_url");
            imageObject.put("image_url",imageUrl);
            content.add(imageObject);

            JSONObject message = new JSONObject(true);
            message.put("role","user");
            message.put("content",content);
            JSONArray messages = new JSONArray();
            messages.add(message);
            int tokenLimit = Math.max(2000,maxTokens);
            JSONObject response = callAi(ai,profile,messages,tokenLimit,kind);
            JSONObject parsed = parseResponse(response);
            if ((parsed == null || !isSuccess(response))
                    && "length".equalsIgnoreCase(safe(response == null ? "" : response.getString("finishReason")))) {
                response = callAi(ai,profile,messages,Math.min(20000,tokenLimit * 2),kind);
                parsed = parseResponse(response);
            }
            if (response == null || !response.getBooleanValue("status")) {
                result.put("status",false);
                result.put("message",response == null ? "识图没有返回结果" : response.getString("message"));
                return result;
            }
            String raw = firstContent(response);
            if (parsed == null) parsed = repairResult(ai,profile,raw,kind);
            if (parsed == null) {
                plugin.getLogger().sendWarn("[识图] 模型没有返回合法 JSON，finishReason="
                        +safe(response.getString("finishReason"))+" raw="+shortText(raw,300));
                result.put("status",false);
                result.put("message","模型没有返回合法 JSON");
                return result;
            }
            JSONArray emotionTags = normalizeArray(parsed.getJSONArray("emotionTags"));
            if (emotionTags.isEmpty() && "sticker".equalsIgnoreCase(kind)) {
                emotionTags = repairTags(ai,profile,raw);
            }
            result.put("status",true);
            result.put("summary",safe(parsed.getString("summary")));
            result.put("ocr",safe(parsed.getString("ocr")));
            result.put("description",safe(parsed.getString("description")));
            result.put("scene",safe(parsed.getString("scene")));
            result.put("emotionTags",emotionTags);
            result.put("visualTags",normalizeArray(parsed.getJSONArray("visualTags")));
            result.put("profile",profile);
            result.put("model",safe(response.getString("model")));
            result.put("finishReason",safe(response.getString("finishReason")));
            return result;
        } catch (Exception e) {
            plugin.getLogger().sendWarn("识图异常："+e.getMessage());
            result.put("status",false);
            result.put("message",e.getMessage());
            return result;
        }
    }

    private JSONObject callAi(PluginService ai,String profile,JSONArray messages,int maxTokens,String kind) {
        JSONObject params = new JSONObject(true);
        params.put("profile",profile);
        params.put("maxTokens",maxTokens);
        params.put("temperature",0.1);
        params.put("sessionId","vision-"+kind+"-"+System.currentTimeMillis());
        params.put("messages",messages);
        return ai.call("chat",params);
    }

    private JSONArray repairTags(PluginService ai,String profile,String rawContent) {
        if (rawContent == null || rawContent.trim().isEmpty()) return new JSONArray();
        JSONObject params = new JSONObject(true);
        params.put("profile",profile);
        params.put("maxTokens",800);
        params.put("temperature",0.0);
        params.put("prompt","Convert the following image recognition output into 3 to 6 English snake_case "
                +"emotion or chat usage tags. Do not describe appearance. Return only JSON: "
                +"{\"emotionTags\":[\"happy\",\"shy\"]}\n"+shortText(rawContent,1500));
        JSONObject response = ai.call("complete",params);
        if (response == null || !response.getBooleanValue("status")) return new JSONArray();
        JSONObject parsed = parseJson(response.getString("content"));
        return normalizeArray(parsed == null ? null : parsed.getJSONArray("emotionTags"));
    }

    private JSONObject repairResult(PluginService ai,String profile,String rawContent,String kind) {
        if (rawContent == null || rawContent.trim().isEmpty()) return null;
        JSONObject params = new JSONObject(true);
        params.put("profile",profile);
        params.put("maxTokens",2500);
        params.put("temperature",0.0);
        params.put("prompt","Convert the following model output into only JSON. Do not explain or repeat reasoning. "
                +"Use this schema: {\"summary\":\"Chinese description\",\"ocr\":\"text or empty\","
                +"\"emotionTags\":[\"happy\"],\"visualTags\":[\"cat\"],\"scene\":\"group_chat\","
                +"\"description\":\"Chinese description\"}. Kind: "+safe(kind)
                +"\nOutput:\n"+shortText(rawContent,3000));
        JSONObject response = ai.call("complete",params);
        if (response == null || !response.getBooleanValue("status")) return null;
        return parseJson(response.getString("content"));
    }

    private JSONObject parseResponse(JSONObject response) {
        if (response == null) return null;
        JSONObject parsed = parseJson(response.getString("content"));
        if (parsed == null) parsed = parseJson(response.getString("reasoningContent"));
        return parsed;
    }

    private boolean isSuccess(JSONObject response) {
        return response != null && response.getBooleanValue("status");
    }

    private String buildPrompt(String kind,String context,String reference) {
        StringBuilder builder = new StringBuilder();
        if ("sticker".equalsIgnoreCase(kind)) {
            builder.append("You are describing a chat sticker for a roleplay system.\n")
                    .append("Return only JSON with this schema:\n")
                    .append("{\"summary\":\"short Chinese emotion description\",\"ocr\":\"recognized text or empty\",")
                    .append("\"emotionTags\":[\"happy\"],\"visualTags\":[\"cat\"],\"scene\":\"group_chat\",")
                    .append("\"description\":\"short Chinese emotion description\"}\n")
                    .append("Rules:\n")
                    .append("- summary and description should focus on the emotion or chat usage, in concise Chinese.\n")
                    .append("- Do not over-describe appearance or make a long analysis.\n")
                    .append("- emotionTags use 3 to 6 English snake_case emotion, attitude, or chat usage tags.\n")
                    .append("- visualTags use 3 to 8 English snake_case visual tags.\n");
        } else {
            builder.append("You are describing an image for a chat bot roleplay system.\n")
                    .append("Return only JSON with this schema:\n")
                    .append("{\"summary\":\"rich Chinese description\",\"ocr\":\"recognized text or empty\",")
                    .append("\"emotionTags\":[\"curious\"],\"visualTags\":[\"cat\"],\"scene\":\"group_chat\",")
                    .append("\"description\":\"rich Chinese description\"}\n")
                    .append("Rules:\n")
                    .append("- summary and description use 2 to 4 complete Chinese sentences.\n")
                    .append("- Describe main subjects, actions, environment, mood, important objects, and notable details.\n")
                    .append("- Include visible text in ocr when present.\n")
                    .append("- Do not guess identities, locations, or facts that are not visible.\n")
                    .append("- emotionTags use 3 to 6 English snake_case emotion or chat usage tags.\n")
                    .append("- visualTags use 3 to 8 English snake_case visual tags.\n");
        }
        builder.append("- Do not describe the bot itself or add explanations.\n")
                .append("- Do not use Markdown.\n");
        if (context != null && !context.trim().isEmpty()) {
            builder.append("Conversation context:\n").append(shortText(context,500));
        }
        if (reference != null && !reference.trim().isEmpty()) {
            builder.append("\nKnown Blue Archive student appearance reference:\n")
                    .append(limitText(reference,32000))
                    .append("\nUse the reference only as candidate guidance. Do not force a match, "
                            + "and do not rely on hair color alone.");
        }
        return builder.toString();
    }

    private JSONObject parseJson(String content) {
        if (content == null) return null;
        String text = content.trim();
        text = text.replaceAll("(?s)```[a-zA-Z0-9_-]*\\s*","").replace("```","").trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        try {
            return JSONObject.parseObject(text.substring(start,end + 1));
        } catch (Exception e) {
            return null;
        }
    }

    private JSONArray normalizeArray(JSONArray array) {
        JSONArray result = new JSONArray();
        if (array == null) return result;
        Pattern pattern = Pattern.compile(plugin.getConfig().getString(
                "tagPattern","^[a-z][a-z0-9_]{1,31}$"));
        List<String> values = new ArrayList<>();
        for (Object item : array) {
            String value = normalizeTag(item == null ? "" : String.valueOf(item));
            if (value.isEmpty() || !pattern.matcher(value).matches() || values.contains(value)) continue;
            values.add(value);
        }
        result.addAll(values);
        return result;
    }

    private String normalizeTag(String tag) {
        if (tag == null) return "";
        return tag.trim().toLowerCase()
                .replace(' ','_')
                .replace('-','_')
                .replaceAll("[^a-z0-9_]","")
                .replaceAll("_+","_")
                .replaceAll("^_+|_+$","");
    }

    private String firstContent(JSONObject response) {
        String content = safe(response.getString("content"));
        if (!content.isEmpty()) return content;
        return safe(response.getString("reasoningContent"));
    }

    private String shortText(String value,int maxLength) {
        if (value == null) return "";
        String text = value.replace("\r"," ").replace("\n"," ").trim();
        if (text.length() <= maxLength) return text;
        return text.substring(0,maxLength)+"...";
    }

    private String limitText(String value,int maxLength) {
        if (value == null) return "";
        String text = value.replace("\r","").trim();
        if (text.length() <= maxLength) return text;
        return text.substring(0,maxLength)+"...";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
