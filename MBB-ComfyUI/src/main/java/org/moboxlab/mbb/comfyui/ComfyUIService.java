package org.moboxlab.mbb.comfyui;

import com.alibaba.fastjson.JSONArray;
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
    private volatile boolean running = false;
    private ExecutorService worker;

    private static class ComfyUIJob {
        private final long taskID;
        private final long groupID;
        private final long userID;
        private final long messageID;
        private final String prompt;
        private final int width;
        private final int height;

        private ComfyUIJob(long taskID,long groupID,long userID,long messageID,
                           String prompt,int width,int height) {
            this.taskID = taskID;
            this.groupID = groupID;
            this.userID = userID;
            this.messageID = messageID;
            this.prompt = prompt;
            this.width = width;
            this.height = height;
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
    }

    public void stop() {
        running = false;
        if (worker != null) worker.shutdownNow();
    }

    public void reload(ComfyUIConfig newConfig) {
        this.config = newConfig;
        this.client = new ComfyUIClient(newConfig.baseUrl,newConfig.timeoutSecond,plugin.getLogger());
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
        if (prompt.length() < config.promptMinChars) {
            return error("生图需求还不够明确，请先补充主体和风格。","prompt");
        }
        if (prompt.length() > config.promptMaxChars) {
            return error("生图 prompt 过长，最多 "+config.promptMaxChars+" 个字符。","prompt");
        }
        SizeResult size = resolveSize(params);
        if (!size.success()) return error(size.error,"size");
        long remaining = cooldownRemaining(groupID);
        if (remaining > 0) {
            return error("当前群生图冷却中，剩余 "+remaining+" 秒。","cooldown");
        }
        long taskID = sequence.incrementAndGet();
        ComfyUIJob job = new ComfyUIJob(taskID,groupID,userID,messageID,
                prompt,size.width,size.height);
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
        plugin.getLogger().sendInfo("[ComfyUI] 已加入队列 群"+groupID
                +" 用户"+userID+" 尺寸="+size.width+"x"+size.height
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
                +" 用户"+job.userID+" 尺寸="+job.width+"x"+job.height);
        JSONObject workflow = buildWorkflow(job);
        String promptId = client.queuePrompt(workflow,clientId);
        plugin.getLogger().sendInfo("[ComfyUI] 已提交 promptId="+promptId
                +" 模型="+config.checkpoint);
        JSONObject image = waitForImage(promptId);
        String filename = image.getString("filename");
        String subfolder = image.getString("subfolder");
        String type = image.getString("type");
        File file = downloadImage(job,promptId,filename,subfolder,type);
        sendImage(job.groupID,file);
        markCooldown(job.groupID);
        callbackSuccess(job,promptId,filename,file);
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
            metadata.put("negativePrompt",config.negativePrompt);
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
            String name = imageFile.getName();
            if (name.toLowerCase().endsWith(".png")) {
                name = name.substring(0,name.length() - 4);
            }
            File metadataFile = new File(imageFile.getParentFile(),name+".json");
            Files.write(Paths.get(metadataFile.getAbsolutePath()),
                    metadata.toJSONString().getBytes(StandardCharsets.UTF_8));
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
                "text",job.prompt,
                "clip",link(1,1))));
        workflow.put("3",node("CLIPTextEncode",inputs(
                "text",config.negativePrompt,
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

    private SizeResult resolveSize(JSONObject params) {
        int width = params.getIntValue("width");
        int height = params.getIntValue("height");
        String preset = safe(params.getString("size"));
        if (preset.isEmpty()) preset = config.defaultPreset;
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

    private void callbackSuccess(ComfyUIJob job,String promptId,String filename,File file) {
        PluginService roleplay = plugin.getServer().getPluginManager().getService("MBB-Roleplay");
        if (roleplay == null) return;
        JSONObject params = new JSONObject(true);
        params.put("groupID",job.groupID);
        params.put("userID",job.userID);
        params.put("messageID",job.messageID);
        params.put("prompt",job.prompt);
        params.put("promptId",promptId);
        params.put("imageName",filename);
        params.put("imagePath",file.getAbsolutePath());
        params.put("imageUri",fileUri(file));
        params.put("model",config.checkpoint);
        roleplay.call("notify-draw-complete",params);
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
        roleplay.call("notify-draw-failed",params);
    }

    private String fileUri(File file) {
        return "file:///"+file.getAbsolutePath().replace("\\","/");
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

    private String describe(Throwable error) {
        if (error == null) return "未知错误";
        String message = error.getMessage();
        return error.getClass().getSimpleName()+(message == null ? "" : ": "+message);
    }
}
