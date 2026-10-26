package org.moboxlab.mbb.comfyui;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.OneBot.MessageUtil;
import org.moboxlab.moboxbot.API.OneBot.OneBotClient;
import org.moboxlab.moboxbot.API.Plugin;
import org.moboxlab.moboxbot.API.PluginService;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ComfyUI 生图服务
 */
public class ComfyUIService implements PluginService {
    private final Plugin plugin;
    private volatile ComfyUIConfig config;
    private volatile ComfyUIClient client;
    private final BlockingQueue<ComfyUIJob> queue;
    private final Set<Long> busyGroups = ConcurrentHashMap.newKeySet();
    private final AtomicLong sequence = new AtomicLong(0L);
    private final String clientId = UUID.randomUUID().toString();
    private final Random random = new Random();
    private volatile JSONObject deviceInfo = new JSONObject(true);
    private volatile boolean running = false;
    private ExecutorService worker;

    private static class ComfyUIJob {
        private final long taskID;
        private final long groupID;
        private final long userID;
        private final long messageID;
        private final String prompt;
        private final String negativePrompt;
        private final String expectedTags;
        private final String forbiddenTags;
        private final int width;
        private final int height;
        private final String cameraMode;
        private final String requestContext;

        private ComfyUIJob(long taskID,long groupID,long userID,long messageID,
                           String prompt,String negativePrompt,
                           String expectedTags,String forbiddenTags,
                           int width,int height,String cameraMode,String requestContext) {
            this.taskID = taskID;
            this.groupID = groupID;
            this.userID = userID;
            this.messageID = messageID;
            this.prompt = prompt;
            this.negativePrompt = negativePrompt;
            this.expectedTags = expectedTags;
            this.forbiddenTags = forbiddenTags;
            this.width = width;
            this.height = height;
            this.cameraMode = cameraMode;
            this.requestContext = requestContext;
        }
    }

    private static class SizeResult {
        private final int width;
        private final int height;
        private final String error;

        private SizeResult(int width,int height,String error) {
            this.width = width;
            this.height = height;
            this.error = error;
        }

        private boolean success() {
            return error == null || error.isEmpty();
        }
    }

    public ComfyUIService(Plugin plugin,ComfyUIConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.client = new ComfyUIClient(config.baseUrl,config.timeoutSecond,plugin.getLogger());
        this.queue = new ArrayBlockingQueue<>(config.queueLimit);
    }

    public void start() {
        running = true;
        worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable,"MBB-ComfyUI-Worker");
            thread.setDaemon(true);
            return thread;
        });
        worker.submit(this::workerLoop);
        plugin.getLogger().sendInfo("[ComfyUI] 队列已启动，地址："+config.baseUrl);
        refreshDeviceInfo();
    }

    public void stop() {
        running = false;
        if (worker != null) worker.shutdownNow();
    }

    public void reload(ComfyUIConfig newConfig) {
        this.config = newConfig;
        this.client = new ComfyUIClient(newConfig.baseUrl,newConfig.timeoutSecond,plugin.getLogger());
        refreshDeviceInfo();
    }

    private void refreshDeviceInfo() {
        try {
            JSONObject stats = client.systemStats();
            JSONArray devices = stats == null ? null : stats.getJSONArray("devices");
            if (devices == null || devices.isEmpty()) return;
            JSONObject device = devices.getJSONObject(0);
            if (device == null) return;
            JSONObject info = new JSONObject(true);
            info.put("name",device.getString("name"));
            info.put("vramTotal",device.getLongValue("vram_total"));
            info.put("vramFree",device.getLongValue("vram_free"));
            deviceInfo = info;
            String name = safe(info.getString("name"));
            plugin.getLogger().sendInfo("[ComfyUI] 当前设备："+name
                    +" 显存="+formatBytes(info.getLongValue("vramFree"))
                    +"/"+formatBytes(info.getLongValue("vramTotal")));
            if (name.toLowerCase(Locale.ROOT).contains("cpu")) {
                plugin.getLogger().sendWarn("[ComfyUI] 当前设备名包含 CPU，请检查 ComfyUI 是否以 GPU 模式启动。");
            }
        } catch (Exception e) {
            plugin.getLogger().sendWarn("[ComfyUI] 读取设备信息失败："+describe(e));
        }
    }

    public boolean clearCooldown(long groupID) {
        if (groupID <= 0) return false;
        plugin.getServer().getStorage().remove(plugin,"draw.cooldown."+groupID);
        plugin.getLogger().sendInfo("[ComfyUI] 已清除群"+groupID+" 的生图冷却。");
        return true;
    }

    @Override
    public String getName() {
        return "MBB-ComfyUI";
    }

    @Override
    public JSONObject call(String action,JSONObject params) {
        if (action == null) return error("缺少动作名！","action");
        if ("status".equalsIgnoreCase(action)) return status(params);
        if ("generate".equalsIgnoreCase(action)) return generate(params);
        if ("reload".equalsIgnoreCase(action)) return status(params);
        return error("不支持的动作："+action,"action");
    }

    private JSONObject status(JSONObject params) {
        long groupID = params == null ? 0L : params.getLongValue("groupID");
        long remaining = cooldownRemaining(groupID);
        boolean busy = groupID > 0 && busyGroups.contains(groupID);
        boolean queueFull = queue.size() >= config.queueLimit;
        JSONObject result = new JSONObject(true);
        result.put("status",true);
        result.put("enable",config.enable);
        result.put("groupID",groupID);
        result.put("ready",config.enable && remaining <= 0 && !busy && !queueFull);
        result.put("cooldownRemaining",remaining);
        result.put("busy",busy);
        result.put("queueSize",queue.size());
        result.put("queueLimit",config.queueLimit);
        result.put("queueFull",queueFull);
        result.put("defaultWidth",config.defaultWidth);
        result.put("defaultHeight",config.defaultHeight);
        result.put("maxWidth",config.maxWidth);
        result.put("maxHeight",config.maxHeight);
        result.put("maxPixels",config.maxPixels);
        result.put("checkpoint",config.checkpoint);
        result.put("presets","square,landscape,portrait,avatar");
        result.put("device",safe(deviceInfo.getString("name")));
        result.put("vramTotal",deviceInfo.getLongValue("vramTotal"));
        result.put("vramFree",deviceInfo.getLongValue("vramFree"));
        return result;
    }

    private JSONObject generate(JSONObject params) {
        if (!config.enable) return error("生图服务当前已关闭！","disabled");
        if (params == null) return error("缺少生图参数！","params");
        long groupID = params.getLongValue("groupID");
        long userID = params.getLongValue("userID");
        long messageID = params.getLongValue("messageID");
        if (groupID <= 0) return error("生图任务缺少群号！","params");
        if (userID <= 0) return error("生图任务缺少用户号！","params");
        String prompt = safe(params.getString("prompt")).trim();
        String negativePrompt = sanitizeNegativePrompt(params.getString("negativePrompt"));
        String expectedTags = sanitizeTags(params.getString("expectedTags"));
        String forbiddenTags = sanitizeTags(params.getString("forbiddenTags"));
        String requestContext = normalizeRequestContext(params.getJSONObject("requestContext")).toJSONString();
        if (prompt.length() < config.promptMinChars) {
            return error("生图需求还不够明确，请先补充主体和风格。","prompt");
        }
        if (prompt.length() > config.promptMaxChars) {
            String original = prompt;
            prompt = truncatePrompt(prompt,config.promptMaxChars);
            plugin.getLogger().sendWarn("[ComfyUI] prompt 超长，已从 "+original.length()
                    +" 字符截断到 "+prompt.length()+" 字符。");
        }
        String cameraMode = normalizeCameraMode(params.getString("camera"));
        SizeResult size = resolveSize(params,cameraMode);
        if (!size.success()) return error(size.error,"size");
        long remaining = cooldownRemaining(groupID);
        if (remaining > 0) {
            return error("当前群生图冷却中，剩余 "+remaining+" 秒。","cooldown");
        }
        long taskID = sequence.incrementAndGet();
        ComfyUIJob job = new ComfyUIJob(taskID,groupID,userID,messageID,
                prompt,negativePrompt,expectedTags,forbiddenTags,size.width,size.height,
                cameraMode,requestContext);
        if (!busyGroups.add(groupID)) {
            return error("当前群已经有生图任务在执行。","busy");
        }
        if (!queue.offer(job)) {
            busyGroups.remove(groupID);
            return error("生图队列已满，请稍后再试。","queue_full");
        }
        JSONObject result = new JSONObject(true);
        result.put("status",true);
        result.put("taskID","comfyui-"+taskID);
        result.put("message","已加入生图队列。");
        result.put("width",size.width);
        result.put("height",size.height);
        if (!cameraMode.isEmpty()) result.put("camera",cameraMode);
        plugin.getLogger().sendInfo("[ComfyUI] 已加入队列 群"+groupID
                +" 用户"+userID+" 尺寸="+size.width+"x"+size.height
                +(cameraMode.isEmpty() ? "" : " 相机="+cameraMode)
                +" prompt="+shortText(prompt,120));
        return result;
    }

    private void workerLoop() {
        while (running) {
            ComfyUIJob job;
            try {
                job = queue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            try {
                processJob(job);
            } catch (Exception e) {
                plugin.getLogger().sendWarn("[ComfyUI] 群"+job.groupID+" 生图失败："
                        +describe(e));
                callbackFail(job,describe(e));
            } finally {
                busyGroups.remove(job.groupID);
            }
        }
    }

    private void processJob(ComfyUIJob job) throws Exception {
        long startTime = System.currentTimeMillis();
        plugin.getLogger().sendInfo("[ComfyUI] 开始生成 群"+job.groupID
                +" 用户"+job.userID+" 尺寸="+job.width+"x"+job.height
                +" 设备="+safe(deviceInfo.getString("name")));
        JSONObject workflow = buildWorkflow(job);
        String promptId = client.queuePrompt(workflow,clientId);
        plugin.getLogger().sendInfo("[ComfyUI] 已提交 promptId="+promptId
                +" 模型="+config.checkpoint);
        JSONObject image = waitForImage(promptId);
        String filename = image.getString("filename");
        String subfolder = image.getString("subfolder");
        String type = image.getString("type");
        File file = downloadImage(job,promptId,filename,subfolder,type);
        checkIdentity(job,file);
        String notifyText = prepareDrawNotify(job,file);
        sendImage(job.groupID,file);
        sendDrawNotify(job,notifyText);
        markCooldown(job.groupID);
        plugin.getLogger().sendInfo("[ComfyUI] 生成完成 群"+job.groupID
                +" 耗时="+(System.currentTimeMillis() - startTime)+"ms 文件="+file.getAbsolutePath());
    }

    private JSONObject waitForImage(String promptId) throws Exception {
        long deadline = System.currentTimeMillis() + config.timeoutSecond * 1000L;
        while (System.currentTimeMillis() < deadline) {
            JSONObject history = client.history(promptId);
            JSONObject entry = history == null ? null : history.getJSONObject(promptId);
            if (entry != null) {
                JSONObject image = findImage(entry.getJSONObject("outputs"));
                if (image != null) return image;
                JSONObject status = entry.getJSONObject("status");
                if (status != null && status.getBooleanValue("completed")) {
                    throw new IllegalStateException("ComfyUI 已完成但没有输出图片："+entry.toJSONString());
                }
            }
            Thread.sleep(config.pollIntervalSecond * 1000L);
        }
        throw new IllegalStateException("ComfyUI 生图超时（"+config.timeoutSecond+" 秒）");
    }

    private JSONObject findImage(JSONObject outputs) {
        if (outputs == null) return null;
        for (String key : outputs.keySet()) {
            JSONObject output = outputs.getJSONObject(key);
            if (output == null) continue;
            JSONArray images = output.getJSONArray("images");
            if (images == null || images.isEmpty()) continue;
            JSONObject image = images.getJSONObject(0);
            if (image != null) return image;
        }
        return null;
    }

    private File downloadImage(ComfyUIJob job,String promptId,String filename,
                               String subfolder,String type)
            throws Exception {
        byte[] bytes = client.viewImage(filename,subfolder,type);
        File directory = new File(plugin.getDataFolder(),config.outputDirectory);
        if (!directory.exists()) directory.mkdirs();
        String time = new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.CHINA).format(new Date());
        String shortId = promptId.length() > 8 ? promptId.substring(0,8) : promptId;
        File file = new File(directory,"comfyui-"+time+"-"+shortId+".png");
        Files.write(Paths.get(file.getAbsolutePath()),bytes);
        writeMetadata(file,job,promptId,filename,subfolder,type);
        return file;
    }

    private void writeMetadata(File imageFile,ComfyUIJob job,String promptId,
                               String filename,String subfolder,String type) {
        try {
            JSONObject metadata = new JSONObject(true);
            metadata.put("createTime",System.currentTimeMillis());
            metadata.put("promptId",promptId);
            metadata.put("groupID",job.groupID);
            metadata.put("userID",job.userID);
            metadata.put("messageID",job.messageID);
            metadata.put("prompt",job.prompt);
            metadata.put("cameraMode",job.cameraMode);
            metadata.put("effectivePrompt",buildPositivePrompt(job.prompt,job.cameraMode));
            metadata.put("promptPrefix",config.promptPrefix);
            metadata.put("promptSuffix",config.promptSuffix);
            metadata.put("negativePrompt",effectiveNegativePrompt(job));
            metadata.put("characterNegativePrompt",job.negativePrompt);
            metadata.put("detailBoostEnable",config.detailBoostEnable);
            metadata.put("detailPositive",config.detailPositive);
            metadata.put("detailNegative",config.detailNegative);
            metadata.put("width",job.width);
            metadata.put("height",job.height);
            metadata.put("checkpoint",config.checkpoint);
            metadata.put("steps",config.steps);
            metadata.put("cfg",config.cfg);
            metadata.put("sampler",config.sampler);
            metadata.put("scheduler",config.scheduler);
            metadata.put("remoteFilename",filename);
            metadata.put("remoteSubfolder",subfolder);
            metadata.put("remoteType",type);
            metadata.put("imageFile",imageFile.getName());
            metadata.put("baseUrl",config.baseUrl);
            metadata.put("device",safe(deviceInfo.getString("name")));
            metadata.put("vramTotal",deviceInfo.getLongValue("vramTotal"));
            metadata.put("vramFree",deviceInfo.getLongValue("vramFree"));
            String name = imageFile.getName();
            if (name.toLowerCase().endsWith(".png")) {
                name = name.substring(0,name.length() - 4);
            }
            File metadataFile = new File(imageFile.getParentFile(),name+".json");
            Files.write(Paths.get(metadataFile.getAbsolutePath()),
                    JSON.toJSONString(metadata,true).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            plugin.getLogger().sendWarn("[ComfyUI] 图片元数据备份失败："+describe(e));
        }
    }

    private void sendImage(long groupID,File file) {
        OneBotClient client = plugin.getServer().getOneBotClient();
        if (client == null) throw new IllegalStateException("OneBot 客户端未就绪");
        JSONArray message = MessageUtil.message(MessageUtil.image(fileUri(file)));
        JSONObject response = client.sendGroupMessage(groupID,message);
        if (response == null || response.getIntValue("retcode") != 0) {
            throw new IllegalStateException("图片发送失败："+safeJson(response));
        }
    }

    private JSONObject buildWorkflow(ComfyUIJob job) {
        int seed = random.nextInt(Integer.MAX_VALUE);
        JSONObject workflow = new JSONObject(true);
        workflow.put("1",node("CheckpointLoaderSimple",inputs(
                "ckpt_name",config.checkpoint)));
        workflow.put("2",node("CLIPTextEncode",inputs(
                "text",buildPositivePrompt(job.prompt,job.cameraMode),
                "clip",link(1,1))));
        workflow.put("3",node("CLIPTextEncode",inputs(
                "text",effectiveNegativePrompt(job),
                "clip",link(1,1))));
        workflow.put("4",node("EmptyLatentImage",inputs(
                "width",job.width,
                "height",job.height,
                "batch_size",1)));
        workflow.put("5",node("KSampler",inputs(
                "model",link(1,0),
                "seed",seed,
                "steps",config.steps,
                "cfg",config.cfg,
                "sampler_name",config.sampler,
                "scheduler",config.scheduler,
                "positive",link(2,0),
                "negative",link(3,0),
                "latent_image",link(4,0),
                "denoise",config.denoise)));
        workflow.put("6",node("VAEDecode",inputs(
                "samples",link(5,0),
                "vae",link(1,2))));
        workflow.put("7",node("SaveImage",inputs(
                "filename_prefix","MoBoxBot",
                "images",link(6,0))));
        return workflow;
    }

    private String buildPositivePrompt(String prompt,String cameraMode) {
        StringBuilder builder = new StringBuilder();
        appendPrompt(builder,config.promptPrefix);
        appendPrompt(builder,cameraPromptPrefix(cameraMode));
        appendPrompt(builder,sanitizePrompt(prompt));
        if (config.detailBoostEnable) appendPrompt(builder,config.detailPositive);
        appendPrompt(builder,cameraPromptSuffix(cameraMode));
        appendPrompt(builder,config.promptSuffix);
        return builder.toString();
    }

    private String effectiveNegativePrompt(ComfyUIJob job) {
        String base = safe(config.negativePrompt).trim();
        String extra = job == null ? "" : safe(job.negativePrompt).trim();
        String detail = config.detailBoostEnable ? safe(config.detailNegative).trim() : "";
        if (!detail.isEmpty()) {
            extra = extra.isEmpty() ? detail : extra+", "+detail;
        }
        if (base.isEmpty()) return extra;
        if (extra.isEmpty()) return base;
        return base+", "+extra;
    }

    private String sanitizeNegativePrompt(String prompt) {
        String value = safe(prompt);
        StringBuilder builder = new StringBuilder();
        Set<String> seen = new HashSet<>();
        for (String part : value.split(",")) {
            String text = part == null ? "" : part.trim();
            if (text.isEmpty()) continue;
            String lower = text.toLowerCase(Locale.ROOT);
            if (seen.contains(lower)) continue;
            seen.add(lower);
            if (builder.length() > 0) builder.append(", ");
            builder.append(text);
        }
        if (builder.length() > 1200) return builder.substring(0,1200);
        return builder.toString();
    }

    private String sanitizeTags(String value) {
        String text = safe(value);
        StringBuilder builder = new StringBuilder();
        Set<String> seen = new HashSet<>();
        for (String part : text.split(",")) {
            String item = part == null ? "" : part.trim();
            if (item.isEmpty()) continue;
            String lower = item.toLowerCase(Locale.ROOT);
            if (seen.contains(lower)) continue;
            seen.add(lower);
            if (builder.length() > 0) builder.append(", ");
            builder.append(item);
        }
        return builder.toString();
    }

    private void checkIdentity(ComfyUIJob job,File file) {
        if (!config.identityCheckEnable || safe(job.expectedTags).isEmpty()) return;
        PluginService vision = plugin.getServer().getPluginManager().getService("MBB-Vision");
        if (vision == null) return;
        try {
            JSONObject params = new JSONObject(true);
            params.put("kind","image");
            params.put("file",file.getAbsolutePath());
            params.put("fileUnique","comfyui-"+job.taskID);
            params.put("context",job.prompt);
            params.put("reference",job.expectedTags);
            params.put("force",true);
            if (!safe(config.identityCheckProfile).isEmpty()) {
                params.put("profile",config.identityCheckProfile.trim());
            }
            JSONObject result = vision.call("describe",params);
            if (result == null || !result.getBooleanValue("status")) {
                plugin.getLogger().sendWarn("[ComfyUI] 身份校验失败："
                        +(result == null ? "没有返回结果" : safe(result.getString("message"))));
                return;
            }
            String summary = safe(result.getString("summary"));
            String ocr = safe(result.getString("ocr"));
            String tags = safe(result.getString("emotionTags"));
            String text = (summary+" "+ocr+" "+tags).toLowerCase(Locale.ROOT);
            String matched = "";
            for (String item : safe(job.forbiddenTags).split(",")) {
                String value = item == null ? "" : item.trim().toLowerCase(Locale.ROOT);
                if (value.isEmpty()) continue;
                if (text.contains(value)) {
                    matched = value;
                    break;
                }
            }
            if (!matched.isEmpty()) {
                plugin.getLogger().sendWarn("[ComfyUI] 身份校验疑似不符 群"+job.groupID
                        +" 命中禁止特征="+matched+" 描述="+shortText(summary,120));
            } else {
                plugin.getLogger().sendInfo("[ComfyUI] 身份校验通过 群"+job.groupID
                        +" 描述="+shortText(summary,100));
            }
        } catch (Exception e) {
            plugin.getLogger().sendWarn("[ComfyUI] 身份校验异常："+describe(e));
        }
    }

    private String normalizeCameraMode(String value) {
        String mode = safe(value).trim().toLowerCase(Locale.ROOT);
        if ("selfie".equals(mode) || "photo".equals(mode)) return mode;
        return "";
    }

    private String cameraPromptPrefix(String cameraMode) {
        if ("selfie".equals(cameraMode)) {
            return "smartphone selfie, front camera photo, holding smartphone, "
                    +"arm extended, looking at camera, casual candid snapshot";
        }
        if ("photo".equals(cameraMode)) {
            return "first person view, point of view, immersive perspective, candid snapshot";
        }
        return "";
    }

    private String cameraPromptSuffix(String cameraMode) {
        if ("selfie".equals(cameraMode)) {
            return "natural phone camera perspective, casual framing, no screenshot UI";
        }
        if ("photo".equals(cameraMode)) {
            return "natural first person perspective, realistic framing";
        }
        return "";
    }

    private String sanitizePrompt(String prompt) {
        String value = safe(prompt);
        String[] parts = value.split(",");
        StringBuilder builder = new StringBuilder();
        Set<String> seen = new HashSet<>();
        String countTag = "";
        for (String part : parts) {
            String text = part == null ? "" : part.trim();
            if (text.isEmpty()) continue;
            String lower = text.toLowerCase(Locale.ROOT);
            if ("twins".equals(lower) || "clone".equals(lower) || "cloned".equals(lower)
                    || "duplicate character".equals(lower) || "multiple views".equals(lower)
                    || "split screen".equals(lower) || "extra person".equals(lower)) {
                continue;
            }
            if ("masterpiece".equals(lower) || "best quality".equals(lower)
                    || "highres".equals(lower) || "high resolution".equals(lower)
                    || "highly detailed".equals(lower) || "anime illustration".equals(lower)
                    || "detailed".equals(lower)) {
                continue;
            }
            if ("two girls".equals(lower) || "2girls".equals(lower)) {
                countTag = "2girls";
                continue;
            }
            if ("one girl".equals(lower) || "1girl".equals(lower)) {
                countTag = "1girl";
                continue;
            }
            if (seen.contains(lower)) continue;
            seen.add(lower);
            if (builder.length() > 0) builder.append(", ");
            builder.append(text);
        }
        if (countTag.isEmpty()) return builder.toString();
        if (builder.length() == 0) return countTag;
        return countTag+", "+builder.toString();
    }

    private void appendPrompt(StringBuilder builder,String value) {
        String text = safe(value).trim();
        if (text.isEmpty()) return;
        if (builder.length() > 0) builder.append(", ");
        builder.append(text);
    }

    private JSONObject node(String classType,JSONObject inputs) {
        JSONObject node = new JSONObject(true);
        node.put("class_type",classType);
        node.put("inputs",inputs);
        return node;
    }

    private JSONObject inputs(Object... values) {
        JSONObject result = new JSONObject(true);
        for (int i = 0; i + 1 < values.length; i += 2) {
            result.put(String.valueOf(values[i]),values[i + 1]);
        }
        return result;
    }

    private JSONArray link(int nodeID,int outputIndex) {
        JSONArray link = new JSONArray();
        link.add(String.valueOf(nodeID));
        link.add(outputIndex);
        return link;
    }

    private SizeResult resolveSize(JSONObject params,String cameraMode) {
        int width = params.getIntValue("width");
        int height = params.getIntValue("height");
        String preset = safe(params.getString("size"));
        if (preset.isEmpty()) {
            if ("selfie".equals(cameraMode)) {
                preset = "portrait";
            } else if ("photo".equals(cameraMode)) {
                preset = "landscape";
            } else {
                preset = config.defaultPreset;
            }
        }
        if (width <= 0 || height <= 0) {
            if ("landscape".equalsIgnoreCase(preset)) {
                width = 1536;
                height = 1024;
            } else if ("portrait".equalsIgnoreCase(preset)) {
                width = 1024;
                height = 1536;
            } else if ("avatar".equalsIgnoreCase(preset)) {
                width = 1024;
                height = 1024;
            } else {
                width = config.defaultWidth;
                height = config.defaultHeight;
            }
        }
        width = normalize(width,config.minWidth,config.maxWidth);
        height = normalize(height,config.minHeight,config.maxHeight);
        if (width > config.maxWidth || height > config.maxHeight) {
            return new SizeResult(0,0,"尺寸超过上限，最大 "
                    +config.maxWidth+"x"+config.maxHeight+"。");
        }
        if ((long)width * height > config.maxPixels) {
            return new SizeResult(0,0,"尺寸像素超过上限，最多 "+config.maxPixels+"。");
        }
        return new SizeResult(width,height,"");
    }

    private int normalize(int value,int min,int max) {
        if (value < min) value = min;
        if (value > max) return value;
        int multiple = config.sizeMultiple;
        return Math.max(min,(value / multiple) * multiple);
    }

    private long cooldownRemaining(long groupID) {
        if (groupID <= 0 || config.cooldownSecond <= 0) return 0L;
        String value = plugin.getServer().getStorage().get(plugin,"draw.cooldown."+groupID);
        if (value == null || value.trim().isEmpty()) return 0L;
        long last;
        try {
            last = Long.parseLong(value.trim());
        } catch (Exception e) {
            return 0L;
        }
        long elapsed = System.currentTimeMillis() - last;
        long remain = config.cooldownSecond * 1000L - elapsed;
        return remain <= 0 ? 0L : (remain + 999L) / 1000L;
    }

    private void markCooldown(long groupID) {
        plugin.getServer().getStorage().set(plugin,"draw.cooldown."+groupID,
                String.valueOf(System.currentTimeMillis()));
    }

    private String prepareDrawNotify(ComfyUIJob job,File imageFile) {
        PluginService roleplay = plugin.getServer().getPluginManager().getService("MBB-Roleplay");
        if (roleplay == null) return "画好啦，快看看。";
        JSONObject params = new JSONObject(true);
        params.put("groupID",job.groupID);
        params.put("userID",job.userID);
        params.put("messageID",job.messageID);
        params.put("prompt",job.prompt);
        params.put("model",config.checkpoint);
        JSONObject requestContext = jobRequestContext(job);
        if (!requestContext.isEmpty()) params.put("requestContext",requestContext);
        if (imageFile != null) params.put("imageFile",imageFile.getAbsolutePath());
        JSONObject result = roleplay.call("prepare-draw-complete",params);
        if (result == null || !result.getBooleanValue("status")) return "画好啦，快看看。";
        String text = result.getString("text");
        return text == null || text.trim().isEmpty() ? "画好啦，快看看。" : text.trim();
    }

    private void sendDrawNotify(ComfyUIJob job,String text) {
        PluginService roleplay = plugin.getServer().getPluginManager().getService("MBB-Roleplay");
        if (roleplay != null) {
            JSONObject params = new JSONObject(true);
            params.put("groupID",job.groupID);
            params.put("userID",job.userID);
            params.put("text",text);
            roleplay.call("send-draw-notify",params);
            return;
        }
        sendFallbackNotify(job.groupID,job.userID,text);
    }

    private void sendFallbackNotify(long groupID,long userID,String text) {
        OneBotClient client = plugin.getServer().getOneBotClient();
        if (client == null) return;
        String value = text == null || text.trim().isEmpty() ? "画好啦，快看看。" : text.trim();
        JSONArray message = MessageUtil.message(MessageUtil.at(userID),
                MessageUtil.text(" "+value));
        client.sendGroupMessage(groupID,message);
    }

    private void callbackFail(ComfyUIJob job,String reason) {
        PluginService roleplay = plugin.getServer().getPluginManager().getService("MBB-Roleplay");
        if (roleplay == null) return;
        JSONObject params = new JSONObject(true);
        params.put("groupID",job.groupID);
        params.put("userID",job.userID);
        params.put("messageID",job.messageID);
        params.put("prompt",job.prompt);
        params.put("reason",reason);
        JSONObject requestContext = jobRequestContext(job);
        if (!requestContext.isEmpty()) params.put("requestContext",requestContext);
        roleplay.call("notify-draw-failed",params);
    }

    private JSONObject jobRequestContext(ComfyUIJob job) {
        if (job == null || safe(job.requestContext).trim().isEmpty()) return new JSONObject(true);
        try {
            JSONObject value = JSON.parseObject(job.requestContext);
            return value == null ? new JSONObject(true) : value;
        } catch (Exception e) {
            return new JSONObject(true);
        }
    }

    private String fileUri(File file) {
        return "file:///"+file.getAbsolutePath().replace("\\","/");
    }

    static JSONObject normalizeRequestContext(JSONObject input) {
        JSONObject result = new JSONObject(true);
        if (input == null) return result;
        putText(result,"message",input.getString("message"),800);
        putText(result,"userName",input.getString("userName"),80);
        putText(result,"relationship",input.getString("relationship"),40);
        putText(result,"size",input.getString("size"),20);
        putText(result,"camera",input.getString("camera"),20);
        result.put("targetNames",textArray(input.getJSONArray("targetNames"),8,80));
        result.put("targetAliases",textArray(input.getJSONArray("targetAliases"),16,80));
        long requestedAt = input.getLongValue("requestedAt");
        if (requestedAt > 0) result.put("requestedAt",requestedAt);
        JSONObject brief = normalizeDrawBrief(input.getJSONObject("brief"));
        if (!brief.isEmpty()) result.put("brief",brief);
        return result;
    }

    private static JSONObject normalizeDrawBrief(JSONObject input) {
        JSONObject result = new JSONObject(true);
        if (input == null) return result;
        putText(result,"subject",input.getString("subject"),160);
        putText(result,"action",input.getString("action"),160);
        putText(result,"expression",input.getString("expression"),120);
        putText(result,"outfit",input.getString("outfit"),160);
        putText(result,"scene",input.getString("scene"),240);
        putText(result,"composition",input.getString("composition"),160);
        putText(result,"raw",input.getString("raw"),500);
        result.put("characters",textArray(input.getJSONArray("characters"),8,80));
        result.put("mustInclude",textArray(input.getJSONArray("mustInclude"),8,80));
        result.put("mustExclude",textArray(input.getJSONArray("mustExclude"),8,80));
        return result;
    }

    private static void putText(JSONObject result,String key,String value,int maxChars) {
        String text = value == null ? "" : value.trim();
        if (!text.isEmpty()) result.put(key,shorten(text,maxChars));
    }

    private static JSONArray textArray(JSONArray values,int maxItems,int maxChars) {
        JSONArray result = new JSONArray();
        if (values == null) return result;
        for (Object value : values) {
            if (result.size() >= maxItems) break;
            String text = value == null ? "" : String.valueOf(value).trim();
            if (text.isEmpty()) continue;
            String shortened = shorten(text,maxChars);
            if (!result.contains(shortened)) result.add(shortened);
        }
        return result;
    }

    private static String shorten(String value,int maxChars) {
        String text = value == null ? "" : value;
        if (maxChars <= 0) return "";
        return text.length() <= maxChars ? text : text.substring(0,maxChars);
    }

    private JSONObject error(String message,String type) {
        JSONObject result = new JSONObject(true);
        result.put("status",false);
        result.put("message",message);
        result.put("errorType",type);
        return result;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String safeJson(JSONObject json) {
        return json == null ? "" : json.toJSONString();
    }

    private String shortText(String text,int maxChars) {
        String value = safe(text).replace("\n"," ").trim();
        return value.length() <= maxChars ? value : value.substring(0,maxChars)+"...";
    }

    private String truncatePrompt(String prompt,int maxChars) {
        if (prompt == null || prompt.length() <= maxChars) return prompt == null ? "" : prompt;
        String value = prompt.substring(0,maxChars);
        int comma = value.lastIndexOf(',');
        if (comma >= maxChars / 2) value = value.substring(0,comma);
        return value.trim();
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0) return "0";
        double value = bytes / 1024.0 / 1024.0 / 1024.0;
        return String.format(Locale.ROOT,"%.1fGB",value);
    }

    private String describe(Throwable error) {
        if (error == null) return "未知错误";
        String message = error.getMessage();
        return error.getClass().getSimpleName()+(message == null ? "" : ": "+message);
    }
}
