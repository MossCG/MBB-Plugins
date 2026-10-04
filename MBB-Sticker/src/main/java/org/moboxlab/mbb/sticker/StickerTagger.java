package org.moboxlab.mbb.sticker;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Plugin;
import org.moboxlab.moboxbot.API.PluginService;

import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * 表情包识图打标签
 */
public class StickerTagger {
    private final Plugin plugin;
    private final StickerLibrary library;

    public StickerTagger(Plugin plugin,StickerLibrary library) {
        this.plugin = plugin;
        this.library = library;
    }

    public JSONObject tag(File file) {
        return tag(file,mime(file));
    }

    public JSONObject tag(File file,String mime) {
        JSONObject result = new JSONObject(true);
        try {
            PluginService ai = plugin.getServer().getPluginManager().getService("MBB-AI");
            if (ai == null) {
                result.put("tags",fallbackTags());
                result.put("description","");
                return result;
            }
            byte[] bytes = Files.readAllBytes(file.toPath());
            String dataUri = "data:"+mime+";base64,"+Base64.getEncoder().encodeToString(bytes);
            JSONArray content = new JSONArray();
            JSONObject text = new JSONObject(true);
            text.put("type","text");
            text.put("text",buildPrompt());
            content.add(text);
            JSONObject imageUrl = new JSONObject(true);
            imageUrl.put("url",dataUri);
            JSONObject image = new JSONObject(true);
            image.put("type","image_url");
            image.put("image_url",imageUrl);
            content.add(image);

            JSONObject message = new JSONObject(true);
            message.put("role","user");
            message.put("content",content);
            JSONArray messages = new JSONArray();
            messages.add(message);
            String profile = plugin.getConfig().getString("aiProfile","default");
            int maxTokens = Math.max(3000,plugin.getConfig().getInt("taggingMaxTokens",3000));
            JSONObject response = callAi(ai,profile,messages,maxTokens,
                    "sticker-tag-"+System.currentTimeMillis());
            if (isSuccess(response) && firstContent(response).isEmpty()
                    && "length".equalsIgnoreCase(safe(response.getString("finishReason")))) {
                response = callAi(ai,profile,messages,Math.min(8000,maxTokens * 2),
                        "sticker-tag-retry-"+System.currentTimeMillis());
            }
            if (response == null || !response.getBooleanValue("status")) {
                plugin.getLogger().sendWarn("表情包识图失败："
                        +safe(response == null ? "" : response.getString("message")));
                result.put("tags",fallbackTags());
                result.put("description","");
                return result;
            }
            JSONObject parsed = parseJson(firstContent(response));
            List<String> tags = library.normalizeTags(arrayToList(parsed == null ? null : parsed.getJSONArray("tags")));
            if (tags.isEmpty()) {
                tags = repairTags(ai,profile,firstContent(response));
            }
            if (tags.isEmpty()) {
                plugin.getLogger().sendWarn("表情包识图没有生成有效标签：finishReason="
                        +safe(response.getString("finishReason"))+" content="
                        +shortText(firstContent(response),240));
                tags.add("unlabeled");
            }
            JSONArray tagArray = new JSONArray();
            tagArray.addAll(tags);
            result.put("tags",tagArray);
            result.put("description",parsed == null ? "" : safe(parsed.getString("description")));
            return result;
        } catch (Exception e) {
            plugin.getLogger().sendWarn("表情包识图异常："+e.getMessage());
            result.put("tags",fallbackTags());
            result.put("description","");
            return result;
        }
    }

    private JSONObject callAi(PluginService ai,String profile,JSONArray messages,int maxTokens,
                              String sessionId) {
        JSONObject params = new JSONObject(true);
        params.put("profile",profile);
        params.put("maxTokens",maxTokens);
        params.put("temperature",0.1);
        params.put("sessionId",sessionId);
        params.put("messages",messages);
        return ai.call("chat",params);
    }

    private List<String> repairTags(PluginService ai,String profile,String rawContent) {
        if (rawContent == null || rawContent.trim().isEmpty()) return new java.util.ArrayList<>();
        JSONObject params = new JSONObject(true);
        params.put("profile",profile);
        params.put("maxTokens",800);
        params.put("temperature",0.0);
        params.put("prompt",buildRepairPrompt(rawContent));
        JSONObject response = ai.call("complete",params);
        if (response == null || !response.getBooleanValue("status")) return new java.util.ArrayList<>();
        JSONObject parsed = parseJson(response.getString("content"));
        return library.normalizeTags(arrayToList(parsed == null ? null : parsed.getJSONArray("tags")));
    }

    private boolean isSuccess(JSONObject response) {
        return response != null && response.getBooleanValue("status");
    }

    private String firstContent(JSONObject response) {
        if (response == null) return "";
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

    private JSONArray fallbackTags() {
        JSONArray tags = new JSONArray();
        tags.add("unlabeled");
        return tags;
    }

    private String buildPrompt() {
        StringBuilder builder = new StringBuilder();
        builder.append("You are tagging a sticker for a chat bot.\n")
                .append("Return 3 to 6 English snake_case tags describing the emotion, attitude, or chat usage of this sticker.\n")
                .append("Tags must help decide when to send this sticker in a conversation.\n")
                .append("Do not output appearance, hair, eyes, clothing, character identity, art style, object, composition, gender, or age tags.\n")
                .append("Tag rules:\n")
                .append("- lowercase English snake_case\n")
                .append("- only a-z, 0-9, and underscore\n")
                .append("- start with a letter, length 2 to 32\n")
                .append("- no spaces, Chinese, or special characters\n")
                .append("Good directions include: happy, sad, angry, surprised, shy, smug, confused, tired, crying, laughing, agree, refuse, greeting, goodnight, urging, comfort, teasing, celebrate, waiting, working, eating.\n")
                .append("description: a short Chinese description of the image content.\n")
                .append("Return only JSON: {\"tags\":[\"happy\",\"shy\"],\"description\":\"简短描述\"}");
        return builder.toString();
    }

    private String buildRepairPrompt(String rawContent) {
        return "下面是表情包识图模型的原始输出。\n"
                +"请把它整理成 3 到 6 个英文 snake_case 标签。\n"
                +"标签只能描述情绪、态度或聊天场景，不要描述外貌、服装、发色、画风、物体、性别或年龄。\n"
                +"如果原输出是中文，请翻译成英文。\n"
                +"不要解释，只返回 JSON：{\"tags\":[\"happy\",\"shy\"]}\n"
                +"原始输出：\n"
                +shortText(rawContent,1500);
    }

    private String mime(File file) {
        String name = file == null ? "" : file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".webp")) return "image/webp";
        return "image/png";
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

    private List<String> arrayToList(JSONArray array) {
        List<String> result = new java.util.ArrayList<>();
        if (array == null) return result;
        for (Object item : array) {
            if (item != null && !String.valueOf(item).trim().isEmpty()) {
                result.add(String.valueOf(item).trim());
            }
        }
        return result;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
