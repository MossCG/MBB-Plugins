package org.moboxlab.mbb.vision;

import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.OneBot.OneBotClient;
import org.moboxlab.moboxbot.API.Plugin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * 识图图片读取
 */
public class VisionImageSource {
    public static class ImageData {
        public byte[] bytes;
        public String mime = "image/png";
        public String sha256 = "";
        public String fileUnique = "";
    }

    public static ImageData load(Plugin plugin,JSONObject params) throws Exception {
        if (params == null) throw new IllegalArgumentException("缺少图片参数");
        long maxBytes = Math.max(1,plugin.getConfig().getInt("maxImageSizeKB",8192)) * 1024L;
        String fileUnique = safe(params.getString("fileUnique"));
        byte[] bytes = null;
        String mime = "";

        String fileUri = safe(params.getString("fileUri"));
        if (!fileUri.isEmpty()) {
            File file = fromFileUri(fileUri);
            if (file == null || !file.exists()) file = new File(fileUri);
            if (file.exists() && file.isFile()) {
                bytes = readFile(file,maxBytes);
                mime = mime(file.getName());
            }
        }
        String url = safe(params.getString("url"));
        if (bytes == null && (url.startsWith("http://") || url.startsWith("https://"))) {
            Downloaded downloaded = download(url,maxBytes);
            bytes = downloaded.bytes;
            mime = downloaded.mime;
        }
        String file = safe(params.getString("file"));
        if (bytes == null && !file.isEmpty()) {
            File local = new File(file);
            if (local.exists() && local.isFile()) {
                bytes = readFile(local,maxBytes);
                mime = mime(local.getName());
            }
        }
        if (bytes == null && !file.isEmpty()) {
            JSONObject actionParams = new JSONObject(true);
            actionParams.put("file",file);
            OneBotClient client = plugin.getServer().getOneBotClient();
            JSONObject response = client == null ? null : client.callAction("get_image",actionParams);
            JSONObject data = response == null ? null : response.getJSONObject("data");
            if (data != null) {
                String resolvedUrl = safe(data.getString("url"));
                if (resolvedUrl.startsWith("http://") || resolvedUrl.startsWith("https://")) {
                    Downloaded downloaded = download(resolvedUrl,maxBytes);
                    bytes = downloaded.bytes;
                    mime = downloaded.mime;
                } else {
                    File local = new File(safe(data.getString("file")));
                    if (local.exists() && local.isFile()) {
                        bytes = readFile(local,maxBytes);
                        mime = mime(local.getName());
                    }
                }
            }
        }
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("图片读取失败");
        if (mime == null || mime.trim().isEmpty()) mime = "image/png";

        ImageData result = new ImageData();
        result.bytes = bytes;
        result.mime = mime;
        result.sha256 = sha256(bytes);
        result.fileUnique = fileUnique;
        return result;
    }

    private static byte[] readFile(File file,long maxBytes) throws Exception {
        if (file.length() > maxBytes) throw new IllegalArgumentException("图片超过大小限制");
        return Files.readAllBytes(file.toPath());
    }

    private static Downloaded download(String url,long maxBytes) throws Exception {
        HttpURLConnection connection = null;
        InputStream input = null;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            connection = (HttpURLConnection)new URL(url).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("User-Agent","MoBoxBot/0.1");
            String mime = mimeFromContentType(connection.getContentType());
            input = connection.getInputStream();
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) > 0) {
                total += read;
                if (total > maxBytes) throw new IllegalArgumentException("图片超过大小限制");
                output.write(buffer,0,read);
            }
            if (mime.isEmpty()) mime = mime(new URL(url).getPath());
            Downloaded result = new Downloaded();
            result.bytes = output.toByteArray();
            result.mime = mime.isEmpty() ? "image/png" : mime;
            return result;
        } finally {
            try { if (input != null) input.close(); } catch (Exception ignored) {}
            if (connection != null) connection.disconnect();
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] value = digest.digest(bytes);
        StringBuilder builder = new StringBuilder();
        for (byte item : value) builder.append(String.format("%02x",item & 0xff));
        return builder.toString();
    }

    private static String mime(String name) {
        String value = safe(name).toLowerCase(Locale.ROOT);
        if (value.endsWith(".jpg") || value.endsWith(".jpeg")) return "image/jpeg";
        if (value.endsWith(".gif")) return "image/gif";
        if (value.endsWith(".webp")) return "image/webp";
        return "image/png";
    }

    private static String mimeFromContentType(String contentType) {
        if (contentType == null) return "";
        String value = contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("image/")) return value;
        return "";
    }

    private static File fromFileUri(String uri) {
        try {
            if (uri.startsWith("file://")) return new File(new URI(uri));
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static class Downloaded {
        private byte[] bytes;
        private String mime;
    }
}
