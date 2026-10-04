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
            JSONObject params = new JSONObject(true);
            params.put("profile",plugin.getConfig().getString("aiProfile","default"));
            params.put("maxTokens",plugin.getConfig().getInt("taggingMaxTokens",800));
            params.put("temperature",0.1);
            params.put("sessionId","sticker-tag-"+System.currentTimeMillis());
            params.put("messages",messages);
            JSONObject response = ai.call("chat",params);
            if (response == null || !response.getBooleanValue("status")) {
                plugin.getLogger().sendWarn("表情包识图失败："
                        +safe(response == null ? "" : response.getString("message")));
                result.put("tags",fallbackTags());
                result.put("description","");
                return result;
            }
            JSONObject parsed = parseJson(response.getString("content"));
            List<String> tags = library.normalizeTags(arrayToList(parsed == null ? null : parsed.getJSONArray("tags")));
            if (tags.isEmpty()) tags.add("unlabeled");
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

    private JSONArray fallbackTags() {
        JSONArray tags = new JSONArray();
        tags.add("unlabeled");
        return tags;
    }

    private String buildPrompt() {
        StringBuilder builder = new StringBuilder();
        builder.append("请判断这张表情包最适合在什么情绪或聊天场景发送。\n")
                .append("生成 3 到 6 个情绪/用途标签，标签必须能代表发送这张图时想表达的情绪、态度或使用场景。\n")
                .append("不要生成外貌、发色、眼睛、服装、角色身份、画风、物体、构图、性别、年龄等视觉描述标签。\n")
                .append("标签命名规则：\n")
                .append("- 小写英文 snake_case\n")
                .append("- 只能使用 a-z、0-9、下划线\n")
                .append("- 必须以字母开头，长度 2 到 32\n")
                .append("- 不要使用空格、中文或特殊符号\n")
                .append("可以参考这类方向：happy、sad、angry、surprised、shy、smug、confused、tired、crying、laughing、agree、refuse、greeting、goodnight、urging、comfort、teasing、celebrate、waiting、working、eating。\n")
                .append("优先复用这些已有标签：\n");
        int count = 0;
        for (String tag : library.availableTags()) {
            if (count > 0) builder.append(", ");
            builder.append(tag);
            count++;
            if (count >= 120) break;
        }
        if (count == 0) builder.append("暂无");
        builder.append("\n如果已有标签属于情绪/用途且合适，可以复用；如果是外貌、服装、画风或物体描述，不要复用。")
                .append("description 可以描述画面内容，但 tags 只能放情绪/用途标签。")
                .append("只返回 JSON：{\"tags\":[\"tag1\",\"tag2\"],\"description\":\"简短描述\"}");
        return builder.toString();
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
