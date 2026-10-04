package org.moboxlab.mbb.sticker;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Event.PrivateMessageEvent;
import org.moboxlab.moboxbot.API.OneBot.MessageUtil;
import org.moboxlab.moboxbot.API.OneBot.OneBotClient;
import org.moboxlab.moboxbot.API.Plugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 私聊表情包收录服务
 */
public class StickerReceiveService {
    private final Plugin plugin;
    private final StickerLibrary library;
    private final StickerTagger tagger;
    private final Map<Long,Session> sessions = new ConcurrentHashMap<>();

    private static class Session {
        private final long expireAt;
        private int count;

        private Session(long expireAt) {
            this.expireAt = expireAt;
        }

        private boolean expired() {
            return System.currentTimeMillis() > expireAt;
        }
    }

    private static class SourceFile {
        private final File file;
        private final boolean temporary;

        private SourceFile(File file,boolean temporary) {
            this.file = file;
            this.temporary = temporary;
        }
    }

    public StickerReceiveService(Plugin plugin,StickerLibrary library,StickerTagger tagger) {
        this.plugin = plugin;
        this.library = library;
        this.tagger = tagger;
    }

    public void start(long userID) {
        int minutes = Math.max(1,plugin.getConfig().getInt("receiveTimeoutMinutes",10));
        sessions.put(userID,new Session(System.currentTimeMillis() + minutes * 60000L));
    }

    public void stop(long userID) {
        sessions.remove(userID);
    }

    public boolean active(long userID) {
        Session session = sessions.get(userID);
        if (session == null) return false;
        if (session.expired()) {
            sessions.remove(userID);
            return false;
        }
        return true;
    }

    public int count(long userID) {
        Session session = sessions.get(userID);
        return session == null ? 0 : session.count;
    }

    public boolean accept(long userID) {
        Session session = sessions.get(userID);
        if (session == null || session.expired()) {
            sessions.remove(userID);
            return false;
        }
        int max = Math.max(1,plugin.getConfig().getInt("receiveMaxImages",50));
        if (session.count >= max) return false;
        session.count++;
        return true;
    }

    public void handleImage(PrivateMessageEvent event,JSONObject data) {
        if (event == null || data == null || !active(event.getUserID())) return;
        if (!accept(event.getUserID())) {
            sendQuote(event.getUserID(),event.getMessageID(),"本次收录数量已达上限，请重新执行 /sticker receive。");
            return;
        }
        plugin.getServer().getPluginManager().runTask(plugin,() -> process(event,data));
    }

    private void process(PrivateMessageEvent event,JSONObject data) {
        try {
            SourceFile source = resolveSource(data);
            if (source == null || source.file == null || !source.file.exists()) {
                sendQuote(event.getUserID(),event.getMessageID(),"图片获取失败，请重新发送。");
                return;
            }
            File image = source.file;
            long maxBytes = Math.max(1,plugin.getConfig().getInt("maxFileSizeKB",8192)) * 1024L;
            if (image.length() > maxBytes) {
                if (source.temporary) deleteTemporary(image);
                sendQuote(event.getUserID(),event.getMessageID(),"图片超过大小限制："+maxBytes / 1024L+" KB。");
                return;
            }
            File stickerDir = new File(plugin.getDataFolder(),"stickers");
            if (!stickerDir.exists()) stickerDir.mkdirs();
            String extension = extension(image.getName());
            if (extension.isEmpty()) extension = "png";
            File target = new File(stickerDir,"sticker-"+System.currentTimeMillis()+"-"
                    +ThreadLocalRandom.current().nextInt(10000)+"."+extension);
            boolean copied = false;
            try {
                Files.copy(image.toPath(),target.toPath());
                copied = true;
            } finally {
                if (source.temporary) deleteTemporary(image);
                if (!copied && target.exists()) deleteTemporary(target);
            }
            String mime = mime(extension);
            JSONObject tagged = tagger.tag(target,mime);
            List<String> tags = toStringList(tagged.getJSONArray("tags"));
            String description = safe(tagged.getString("description"));
            StickerEntry entry = library.add(target,tags,"receive",description);
            sendQuote(event.getUserID(),event.getMessageID(),"已收录表情包：\n"
                    +"ID："+entry.id+"\n"
                    +"标签："+String.join(", ",entry.tags)+"\n"
                    +"描述："+(description.isEmpty() ? "无" : description)+"\n"
                    +"文件："+entry.file);
        } catch (Exception e) {
            plugin.getLogger().sendWarn("收录表情包失败："+e.getMessage());
            sendQuote(event.getUserID(),event.getMessageID(),"收录失败，请稍后再试。");
        }
    }

    private SourceFile resolveSource(JSONObject data) throws Exception {
        String url = safe(data.getString("url"));
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return download(url);
        }
        String file = safe(data.getString("file"));
        if (file.startsWith("file://")) {
            File local = StickerLibrary.fromFileUri(file);
            if (local != null && local.exists()) return new SourceFile(local,false);
        }
        if (!file.isEmpty()) {
            File local = new File(file);
            if (local.exists() && local.isFile()) return new SourceFile(local,false);
        }
        if (!file.isEmpty()) {
            JSONObject params = new JSONObject(true);
            params.put("file",file);
            OneBotClient client = plugin.getServer().getOneBotClient();
            JSONObject response = client == null ? null : client.callAction("get_image",params);
            JSONObject result = response == null ? null : response.getJSONObject("data");
            if (result != null) {
                String resolvedUrl = safe(result.getString("url"));
                if (resolvedUrl.startsWith("http://") || resolvedUrl.startsWith("https://")) {
                    return download(resolvedUrl);
                }
                File local = new File(safe(result.getString("file")));
                if (local.exists()) return new SourceFile(local,false);
            }
        }
        return null;
    }

    private SourceFile download(String url) throws Exception {
        long maxBytes = Math.max(1,plugin.getConfig().getInt("maxFileSizeKB",8192)) * 1024L;
        HttpURLConnection connection = null;
        InputStream input = null;
        FileOutputStream output = null;
        File temp = null;
        boolean success = false;
        try {
            connection = (HttpURLConnection)new URL(url).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("User-Agent","MoBoxBot/0.1");
            String extension = extensionFromContentType(connection.getContentType());
            if (extension.isEmpty()) extension = extension(new URL(url).getPath());
            if (extension.isEmpty()) extension = "png";
            input = connection.getInputStream();
            File dir = new File(plugin.getDataFolder(),"receive");
            if (!dir.exists()) dir.mkdirs();
            temp = new File(dir,"receive-"+System.currentTimeMillis()+"-"
                    +ThreadLocalRandom.current().nextInt(10000)+"."+extension);
            output = new FileOutputStream(temp);
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) > 0) {
                total += read;
                if (total > maxBytes) throw new IllegalArgumentException("图片超过大小限制");
                output.write(buffer,0,read);
            }
            output.close();
            output = null;
            success = true;
            return new SourceFile(temp,true);
        } finally {
            try { if (output != null) output.close(); } catch (Exception ignored) {}
            try { if (input != null) input.close(); } catch (Exception ignored) {}
            if (connection != null) connection.disconnect();
            if (!success) deleteTemporary(temp);
        }
    }

    private void deleteTemporary(File file) {
        if (file == null) return;
        try {
            Files.deleteIfExists(file.toPath());
        } catch (Exception e) {
            plugin.getLogger().sendWarn("清理表情包临时文件失败："+e.getMessage());
        }
    }

    private void sendQuote(long userID,long messageID,String text) {
        OneBotClient client = plugin.getServer().getOneBotClient();
        if (client == null) return;
        client.sendPrivateMessage(userID,MessageUtil.message(
                MessageUtil.reply(messageID),MessageUtil.text(text)));
    }

    private List<String> toStringList(JSONArray array) {
        List<String> result = new java.util.ArrayList<>();
        if (array == null) return result;
        for (Object item : array) {
            if (item != null) result.add(String.valueOf(item));
        }
        return result;
    }

    private String extension(String name) {
        if (name == null) return "";
        int index = name.lastIndexOf('.');
        if (index < 0) return "";
        String value = name.substring(index + 1).toLowerCase(Locale.ROOT);
        if ("jpg".equals(value) || "jpeg".equals(value) || "png".equals(value)
                || "gif".equals(value) || "webp".equals(value)) return value;
        return "";
    }

    private String extensionFromContentType(String contentType) {
        if (contentType == null) return "";
        String value = contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        if ("image/jpeg".equals(value)) return "jpg";
        if ("image/png".equals(value)) return "png";
        if ("image/gif".equals(value)) return "gif";
        if ("image/webp".equals(value)) return "webp";
        return "";
    }

    private String mime(String extension) {
        if ("jpg".equals(extension) || "jpeg".equals(extension)) return "image/jpeg";
        if ("gif".equals(extension)) return "image/gif";
        if ("webp".equals(extension)) return "image/webp";
        return "image/png";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
