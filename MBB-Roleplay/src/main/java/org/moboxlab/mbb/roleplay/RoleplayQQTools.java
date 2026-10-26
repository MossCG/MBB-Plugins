package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.OneBot.OneBotClient;
import org.moboxlab.moboxbot.API.PluginService;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Roleplay 对 MBB-QQ 的工具适配。
 *
 * 所有群号、操作者和机器人自身 QQ 都由当前回合强制注入，模型不能切换操作目标所在的群。
 */
public class RoleplayQQTools {
    private static final int MEDIA_MAX_BYTES = 8 * 1024 * 1024;
    private static final List<String> IMAGE_EXTENSIONS = Arrays.asList(
            "jpg","jpeg","png","gif","webp");
    private static final List<String> VOICE_EXTENSIONS = Arrays.asList(
            "silk","amr","mp3","wav","ogg","m4a","flac");

    public static void register(RoleplayToolRegistry registry,RoleplayService service) {
        PluginService qq = qqService(service);
        if (qq == null) return;
        registry.register(new QQTool(service,qq,"qq_group_info","group_info",
                "读取当前群基本信息",false,2,noArgs()));
        registry.register(new QQTool(service,qq,"qq_member_info","member_info",
                "读取当前群某个成员的群名片、角色和入群时间",false,4,memberArgs()));
        registry.register(new QQTool(service,qq,"qq_remote_history","group_history",
                "读取当前群服务器历史消息，可补足本地数据库之外的上下文",false,3,historyArgs()));
        registry.register(new QQTool(service,qq,"qq_message_detail","message_detail",
                "按消息 ID 读取当前群消息原文和发送者",false,4,messageArgs()));
        registry.register(new QQTool(service,qq,"qq_group_notices","group_notices",
                "读取当前群公告",false,1,noArgs()));
        registry.register(new QQTool(service,qq,"qq_mark_read","mark_read",
                "把当前群标记为已读",true,1,noArgs()));
        registry.register(new QQTool(service,qq,"qq_poke","poke",
                "戳一戳当前用户或 owner 指定的成员",true,1,pokeArgs()));
        registry.register(new QQTool(service,qq,"qq_like","like",
                "给当前用户或 owner 点赞，受每日冷却限制",true,1,likeArgs()));
        registry.register(new QQTool(service,qq,"qq_emoji_like","emoji_like",
                "给当前群一条消息设置表情回应",true,1,emojiArgs()));
        registry.register(new QQTool(service,qq,"qq_recall_own","recall_own",
                "撤回机器人自己刚发送的消息",true,1,messageArgs()));
        registry.register(new QQTool(service,qq,"qq_send_image","send_image",
                "把角色私有工作区中的图片发送到当前群",true,1,mediaArgs()));
        registry.register(new QQTool(service,qq,"qq_send_voice","send_voice",
                "把角色私有工作区中的语音发送到当前群",true,1,mediaArgs()));
        registry.register(new QQTool(service,qq,"qq_set_own_group_card","set_own_group_card",
                "修改机器人在当前群的群名片，需要当前用户是 owner 或群管理员",true,1,cardArgs()));
        registry.register(new QQTool(service,qq,"qq_group_leave","group_leave",
                "让机器人退出当前群，只有 owner 明确要求时才可执行",true,1,noArgs()));
        registry.register(new QQTool(service,qq,"qq_qzone_post","qzone_post",
                "以机器人自己的账号发表 QQ 空间说说，仅 owner 可用",true,1,qzonePostArgs()));
        registry.register(new QQTool(service,qq,"qq_qzone_delete","qzone_delete",
                "删除机器人自己的 QQ 空间说说，仅 owner 可用",true,1,qzoneDeleteArgs()));
        registry.register(new QQTool(service,qq,"qq_qzone_posts","qzone_posts",
                "查看机器人本机记录的 Qzone 说说的 tid、正文和可见范围，仅 owner 可用",false,1,qzonePostsArgs()));
        registry.register(new QQTool(service,qq,"qq_pending_requests","pending_requests",
                "查看待处理的好友申请和群邀请，仅 owner 可用，不会自动响应",false,1,
                requestListArgs()));
        registry.register(new QQTool(service,qq,"qq_friend_request","friend_request",
                "同意或拒绝待处理好友申请，支持一次按 requestIDs 或 userID 批量处理，仅 owner 可用",true,3,
                friendRequestArgs(),
                "approve"));
        registry.register(new QQTool(service,qq,"qq_group_request","group_request",
                "同意或拒绝待处理群邀请，仅 owner 可用",true,1,groupRequestArgs(),
                "approve"));
        registry.register(new QQTool(service,qq,"qq_group_ban","group_ban",
                "禁言或解禁当前群成员，需要 owner 或当前群管理员权限",true,1,banArgs(),
                "userID","duration"));
        registry.register(new QQTool(service,qq,"qq_group_whole_ban","group_whole_ban",
                "开启或关闭当前群全员禁言，需要 owner 或当前群管理员权限",true,1,
                wholeBanArgs(),"enable"));
        registry.register(new QQTool(service,qq,"qq_delete_friend","delete_friend",
                "删除机器人好友，仅 owner 可用",true,1,friendTargetArgs(),"userID"));
        registry.register(new QQTool(service,qq,"qq_set_friend_remark","set_friend_remark",
                "设置机器人好友备注，仅 owner 可用",true,1,friendRemarkArgs(),
                "userID","remark"));
        registry.register(new QQTool(service,qq,"qq_set_avatar","set_avatar",
                "从角色私有工作区读取图片并设置 QQ 头像，仅 owner 可用",true,1,avatarArgs(),
                "path"));
        registry.register(new QQTool(service,qq,"qq_set_profile","set_profile",
                "设置机器人 QQ 资料卡昵称、性别和资料签名，仅 owner 可用",true,1,profileArgs(),
                "nickname"));
        registry.register(new QQTool(service,qq,"qq_set_signature","set_signature",
                "设置机器人 QQ 个性签名，仅 owner 可用",true,1,signatureArgs(),"longNick"));
    }

    private static PluginService qqService(RoleplayService service) {
        if (service == null || service.plugin().getServer() == null
                || service.plugin().getServer().getPluginManager() == null) return null;
        return service.plugin().getServer().getPluginManager().getService("MBB-QQ");
    }

    private static JSONObject noArgs() {
        return new JSONObject(true);
    }

    private static JSONObject memberArgs() {
        JSONObject properties = new JSONObject(true);
        properties.put("userID",property("integer","目标 QQ，省略为当前发言者"));
        return properties;
    }

    private static JSONObject historyArgs() {
        JSONObject properties = new JSONObject(true);
        properties.put("count",property("integer","读取条数，1 到 50"));
        properties.put("messageSeq",property("string","起始消息序号，可省略"));
        return properties;
    }

    private static JSONObject messageArgs() {
        JSONObject properties = new JSONObject(true);
        properties.put("messageID",property("string","消息 ID"));
        return properties;
    }

    private static JSONObject pokeArgs() {
        JSONObject properties = new JSONObject(true);
        properties.put("userID",property("integer","目标 QQ，省略为当前发言者"));
        return properties;
    }

    private static JSONObject likeArgs() {
        JSONObject properties = new JSONObject(true);
        properties.put("userID",property("integer","目标 QQ，省略为当前发言者"));
        properties.put("times",property("integer","点赞次数，1 到 10"));
        return properties;
    }

    private static JSONObject emojiArgs() {
        JSONObject properties = new JSONObject(true);
        properties.put("messageID",property("string","消息 ID"));
        properties.put("emojiID",property("string","表情 ID，例如 128512"));
        return properties;
    }

    private static JSONObject mediaArgs() {
        JSONObject properties = new JSONObject(true);
        properties.put("path",property("string","角色工作区中的相对路径"));
        return properties;
    }

    private static JSONObject cardArgs() {
        JSONObject properties = new JSONObject(true);
        properties.put("card",property("string","新的群名片，最多 30 个字符"));
        return properties;
    }

    private static JSONObject qzonePostArgs() {
        JSONObject properties = new JSONObject(true);
        properties.put("content",property("string","说说正文"));
        properties.put("images",property("array","可选图片数组，元素是角色工作区相对路径"));
        properties.put("ugcRight",property("integer","可见范围：1 所有人，4 好友，16 部分好友，64 仅自己，128 部分好友不可见；省略时使用 qzoneUgcRight"));
        properties.put("targetUins",property("array","部分好友可见/不可见时的 QQ 数组"));
        return properties;
    }

    private static JSONObject qzoneDeleteArgs() {
        JSONObject properties = new JSONObject(true);
        properties.put("tid",property("string","send_qzone_msg 返回的说说 tid"));
        return properties;
    }

    private static JSONObject qzonePostsArgs() {
        JSONObject properties = new JSONObject(true);
        properties.put("limit",property("integer","最多读取多少条本机发布记录，1 到 50"));
        return properties;
    }

    private static JSONObject requestListArgs() {
        JSONObject properties = new JSONObject(true);
        properties.put("limit",property("integer","最多读取多少条待处理申请，1 到 50"));
        return properties;
    }

    private static JSONObject friendRequestArgs() {
        JSONObject properties = new JSONObject(true);
        properties.put("requestID",property("integer","pending_requests 返回的申请 ID"));
        properties.put("requestIDs",property("array","可选，一次处理多个 pending_requests 返回的申请 ID"));
        properties.put("userID",property("integer","可选，处理这个 QQ 的全部待处理好友申请"));
        properties.put("flag",property("string","申请 flag；通常用 requestID 即可"));
        properties.put("approve",property("boolean","true 同意，false 拒绝"));
        properties.put("remark",property("string","同意后设置的好友备注，最多 60 字"));
        return properties;
    }

    private static JSONObject groupRequestArgs() {
        JSONObject properties = new JSONObject(true);
        properties.put("requestID",property("integer","pending_requests 返回的申请 ID"));
        properties.put("flag",property("string","申请 flag；通常用 requestID 即可"));
        properties.put("approve",property("boolean","true 同意，false 拒绝"));
        properties.put("reason",property("string","拒绝理由，可省略"));
        return properties;
    }

    private static JSONObject banArgs() {
        JSONObject properties = new JSONObject(true);
        properties.put("userID",property("integer","目标 QQ"));
        properties.put("duration",property("integer","禁言秒数，0 表示解禁，最长 30 天"));
        return properties;
    }

    private static JSONObject wholeBanArgs() {
        JSONObject properties = new JSONObject(true);
        properties.put("enable",property("boolean","true 开启全员禁言，false 关闭"));
        return properties;
    }

    private static JSONObject friendTargetArgs() {
        JSONObject properties = new JSONObject(true);
        properties.put("userID",property("integer","目标好友 QQ"));
        return properties;
    }

    private static JSONObject friendRemarkArgs() {
        JSONObject properties = new JSONObject(true);
        properties.put("userID",property("integer","目标好友 QQ"));
        properties.put("remark",property("string","好友备注，最多 60 字"));
        return properties;
    }

    private static JSONObject avatarArgs() {
        JSONObject properties = new JSONObject(true);
        properties.put("path",property("string","角色工作区中的图片相对路径"));
        return properties;
    }

    private static JSONObject profileArgs() {
        JSONObject properties = new JSONObject(true);
        properties.put("nickname",property("string","新的 QQ 昵称，最多 30 字"));
        properties.put("personalNote",property("string","资料签名，最多 200 字"));
        properties.put("sex",property("integer","性别：0 未知，1 男，2 女"));
        return properties;
    }

    private static JSONObject signatureArgs() {
        JSONObject properties = new JSONObject(true);
        properties.put("longNick",property("string","新的个性签名，最多 200 字"));
        return properties;
    }

    private static JSONObject property(String type,String description) {
        JSONObject property = new JSONObject(true);
        property.put("type",type);
        property.put("description",description);
        return property;
    }

    private static JSONObject schema(String name,String description,JSONObject properties,
                                     String... required) {
        JSONObject function = new JSONObject(true);
        function.put("name",name);
        function.put("description",description);
        JSONObject parameters = new JSONObject(true);
        parameters.put("type","object");
        parameters.put("properties",properties == null ? new JSONObject(true) : properties);
        JSONArray requiredArray = new JSONArray();
        if (required != null) {
            for (String item : required) {
                if (item != null && !item.trim().isEmpty()) requiredArray.add(item.trim());
            }
        }
        parameters.put("required",requiredArray);
        parameters.put("additionalProperties",false);
        function.put("parameters",parameters);
        JSONObject schema = new JSONObject(true);
        schema.put("type","function");
        schema.put("function",function);
        return schema;
    }

    private static class QQTool implements RoleplayTool {
        private final RoleplayService service;
        private final PluginService qq;
        private final String name;
        private final String action;
        private final String description;
        private final boolean sideEffect;
        private final int maxCalls;
        private final JSONObject schema;

        private QQTool(RoleplayService service,PluginService qq,String name,String action,
                       String description,boolean sideEffect,int maxCalls,JSONObject properties,
                       String... required) {
            this.service = service;
            this.qq = qq;
            this.name = name;
            this.action = action;
            this.description = description;
            this.sideEffect = sideEffect;
            this.maxCalls = maxCalls;
            this.schema = RoleplayQQTools.schema(name,description,properties,required);
        }

        @Override
        public String name() { return name; }

        @Override
        public String category() { return "qq"; }

        @Override
        public String description() { return description; }

        @Override
        public JSONObject schema() { return schema; }

        @Override
        public boolean sideEffect() { return sideEffect; }

        @Override
        public int maxCallsPerTurn() { return maxCalls; }

        @Override
        public RoleplayToolResult execute(RoleplayToolContext context,JSONObject args) {
            JSONObject params = new JSONObject(true);
            params.put("groupID",context.groupID);
            params.put("operatorID",context.userID);
            params.put("selfID",context.selfID);
            params.put("targetID",args.getLongValue("userID") > 0
                    ? args.getLongValue("userID") : context.userID);
            params.put("userID",args.getLongValue("userID"));
            params.put("messageID",service.safe(args.getString("messageID")));
            params.put("messageSeq",service.safe(args.getString("messageSeq")));
            params.put("count",args.getIntValue("count"));
            params.put("emojiID",service.safe(args.getString("emojiID")));
            params.put("times",args.getIntValue("times"));
            params.put("card",service.safe(args.getString("card")));
            params.put("content",service.safe(args.getString("content")));
            int ugcRight = args.getIntValue("ugcRight");
            if (ugcRight == 0 && "qzone_post".equals(action)) {
                ugcRight = service.config().qzoneUgcRight;
            }
            params.put("ugcRight",ugcRight);
            params.put("targetUins",args.getJSONArray("targetUins"));
            params.put("tid",service.safe(args.getString("tid")));
            params.put("limit",args.getIntValue("limit"));
            params.put("requestID",args.getLongValue("requestID"));
            params.put("requestIDs",args.getJSONArray("requestIDs"));
            params.put("flag",service.safe(args.getString("flag")));
            params.put("approve",args.getBooleanValue("approve"));
            params.put("reason",service.safe(args.getString("reason")));
            params.put("remark",service.safe(args.getString("remark")));
            params.put("duration",args.getIntValue("duration"));
            params.put("enable",args.getBooleanValue("enable"));
            params.put("nickname",service.safe(args.getString("nickname")));
            params.put("personalNote",service.safe(args.getString("personalNote")));
            params.put("sex",args.getIntValue("sex"));
            params.put("longNick",service.safe(args.getString("longNick")));
            params.put("tempBlock",args.getBooleanValue("tempBlock"));
            params.put("tempBothDelete",args.getBooleanValue("tempBothDelete"));
            if ("send_image".equals(action) || "send_voice".equals(action)) {
                String path = service.safe(args.getString("path")).trim();
                if (!validExtension(path,"send_image".equals(action))) {
                    return RoleplayToolResult.failed("文件扩展名不在允许范围内");
                }
                try {
                    params.put("file","base64://" + service.toolLoopService().registry()
                            .workspace().readBase64(
                            path,MEDIA_MAX_BYTES));
                } catch (Exception e) {
                    return RoleplayToolResult.failed(e.getMessage());
                }
            }
            if ("set_avatar".equals(action)) {
                String path = service.safe(args.getString("path")).trim();
                if (!validExtension(path,true)) {
                    return RoleplayToolResult.failed("头像扩展名不在允许范围内");
                }
                try {
                    params.put("file","base64://" + service.toolLoopService().registry()
                            .workspace().readBase64(path,MEDIA_MAX_BYTES));
                } catch (Exception e) {
                    return RoleplayToolResult.failed(e.getMessage());
                }
            }
            if ("qzone_post".equals(action)) {
                JSONArray images = args.getJSONArray("images");
                JSONArray encoded = new JSONArray();
                long totalBytes = 0L;
                if (images != null) {
                    for (Object object : images) {
                        String path = service.safe(String.valueOf(object)).trim();
                        if (!validExtension(path,true)) {
                            return RoleplayToolResult.failed("Qzone 图片扩展名不允许："+path);
                        }
                        try {
                            String base64 = service.toolLoopService().registry().workspace()
                                    .readBase64(path,MEDIA_MAX_BYTES);
                            totalBytes += base64.length() * 3L / 4L;
                            if (totalBytes > 16L * 1024L * 1024L) {
                                return RoleplayToolResult.failed("Qzone 图片总大小超过 16MB");
                            }
                            encoded.add("base64://" + base64);
                        } catch (Exception e) {
                            return RoleplayToolResult.failed(e.getMessage());
                        }
                    }
                }
                params.put("images",encoded);
            }
            JSONObject response = qq.call(action,params);
            long targetID = args.getLongValue("userID");
            if (targetID <= 0 && targetsMember(action)) targetID = context.userID;
            if (response == null || !response.getBooleanValue("status")) {
                RoleplayToolResult failed = RoleplayToolResult.failed(response == null
                        ? "MBB-QQ 没有返回结果" : response.getString("message"));
                failed.data.put("groupID",context.groupID);
                failed.data.put("operatorQQ",context.userID);
                if (targetID > 0) failed.data.put("targetQQ",targetID);
                if (response != null) {
                    JSONObject data = response.getJSONObject("data");
                    if (data != null) failed.data.putAll(data);
                    if (response.getJSONArray("items") != null) {
                        failed.data.put("items",response.getJSONArray("items"));
                    }
                }
                return failed;
            }
            String summary = response.getString("message") == null
                    ? "QQ 操作已完成" : response.getString("message");
            if (targetID > 0 && !summary.contains(String.valueOf(targetID))) {
                summary += "，目标QQ："+targetID;
            }
            RoleplayToolResult result = RoleplayToolResult.ok(summary);
            JSONObject data = response.getJSONObject("data");
            if (data != null) result.data = data;
            result.data.put("groupID",context.groupID);
            result.data.put("operatorQQ",context.userID);
            if (targetID > 0) result.data.put("targetQQ",targetID);
            if (response.getJSONArray("posts") != null) {
                result.data.put("posts",response.getJSONArray("posts"));
            }
            if (response.getJSONArray("items") != null) {
                result.data.put("items",response.getJSONArray("items"));
            }
            if (response.getJSONArray("requests") != null) {
                result.data.put("requests",response.getJSONArray("requests"));
            }
            return result;
        }

        private boolean targetsMember(String action) {
            return "member_info".equals(action) || "poke".equals(action)
                    || "like".equals(action) || "group_ban".equals(action)
                    || "delete_friend".equals(action) || "set_friend_remark".equals(action);
        }

        private boolean validExtension(String path,boolean image) {
            if (path == null || path.trim().isEmpty()) return false;
            String name = new File(path.replace("\\","/")).getName().toLowerCase(Locale.ROOT);
            int dot = name.lastIndexOf('.');
            if (dot < 0 || dot + 1 >= name.length()) return false;
            String extension = name.substring(dot + 1);
            return (image ? IMAGE_EXTENSIONS : VOICE_EXTENSIONS).contains(extension);
        }
    }
}
