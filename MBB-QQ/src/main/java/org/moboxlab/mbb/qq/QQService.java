package org.moboxlab.mbb.qq;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Event.NoticeEvent;
import org.moboxlab.moboxbot.API.OneBot.MessageUtil;
import org.moboxlab.moboxbot.API.OneBot.OneBotClient;
import org.moboxlab.moboxbot.API.Plugin;
import org.moboxlab.moboxbot.API.PluginService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QQ 扩展动作服务。
 *
 * 只暴露经过选择和高层封装的 OneBot 动作，不向调用方暴露凭据、rkey、原始包或进程控制。
 */
public class QQService implements PluginService {
    private static final String AUDIT_TABLE = "plugin_mbb_qq_audit";
    private static final String QZONE_TABLE = "plugin_mbb_qq_qzone_post";
    private static final String REQUEST_TABLE = "plugin_mbb_qq_request";
    private final Plugin plugin;
    private volatile QQConfig config;
    private final Set<Long> friendDecisionRunning = ConcurrentHashMap.newKeySet();

    public QQService(Plugin plugin,QQConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    @Override
    public String getName() {
        return "MBB-QQ";
    }

    public void reload(QQConfig config) {
        this.config = config;
    }

    public void init() {
        storage().update("CREATE TABLE IF NOT EXISTS `"+AUDIT_TABLE+"` ("
                + "`ID` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`action` TEXT NOT NULL DEFAULT '',"
                + "`groupID` INTEGER NOT NULL DEFAULT 0,"
                + "`operatorID` INTEGER NOT NULL DEFAULT 0,"
                + "`targetID` INTEGER NOT NULL DEFAULT 0,"
                + "`summary` TEXT NOT NULL DEFAULT '',"
                + "`success` INTEGER NOT NULL DEFAULT 0,"
                + "`createTime` INTEGER NOT NULL DEFAULT 0"
                + ")");
        storage().update("CREATE INDEX IF NOT EXISTS `idx_mbb_qq_audit_group` "
                + "ON `"+AUDIT_TABLE+"` (`groupID`,`createTime`)");
        storage().update("CREATE TABLE IF NOT EXISTS `"+QZONE_TABLE+"` ("
                + "`ID` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`tid` TEXT NOT NULL DEFAULT '',"
                + "`content` TEXT NOT NULL DEFAULT '',"
                + "`images` TEXT NOT NULL DEFAULT '',"
                + "`ugcRight` INTEGER NOT NULL DEFAULT 64,"
                + "`createTime` INTEGER NOT NULL DEFAULT 0"
                + ")");
        storage().update("CREATE TABLE IF NOT EXISTS `"+REQUEST_TABLE+"` ("
                + "`ID` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`requestType` TEXT NOT NULL DEFAULT '',"
                + "`subType` TEXT NOT NULL DEFAULT '',"
                + "`userID` INTEGER NOT NULL DEFAULT 0,"
                + "`groupID` INTEGER NOT NULL DEFAULT 0,"
                + "`comment` TEXT NOT NULL DEFAULT '',"
                + "`flag` TEXT NOT NULL DEFAULT '',"
                + "`status` TEXT NOT NULL DEFAULT 'pending',"
                + "`decisionSource` TEXT NOT NULL DEFAULT '',"
                + "`decisionReason` TEXT NOT NULL DEFAULT '',"
                + "`createTime` INTEGER NOT NULL DEFAULT 0,"
                + "`updateTime` INTEGER NOT NULL DEFAULT 0"
                + ")");
        storage().update("CREATE UNIQUE INDEX IF NOT EXISTS `idx_mbb_qq_request_flag` "
                + "ON `"+REQUEST_TABLE+"` (`requestType`,`flag`)");
        storage().update("CREATE INDEX IF NOT EXISTS `idx_mbb_qq_request_status` "
                + "ON `"+REQUEST_TABLE+"` (`status`,`updateTime`)");
        ensureRequestColumn("decisionSource","TEXT NOT NULL DEFAULT ''");
        ensureRequestColumn("decisionReason","TEXT NOT NULL DEFAULT ''");
        cleanupAudit();
        cleanupRequests();
    }

    @Override
    public JSONObject call(String action,JSONObject params) {
        if (!config.enable) return error("MBB-QQ 当前已关闭");
        String name = action == null ? "" : action.trim();
        JSONObject input = params == null ? new JSONObject(true) : params;
        try {
            if ("group_info".equalsIgnoreCase(name)) return groupInfo(input);
            if ("member_info".equalsIgnoreCase(name)) return memberInfo(input);
            if ("group_history".equalsIgnoreCase(name)) return groupHistory(input);
            if ("message_detail".equalsIgnoreCase(name)) return messageDetail(input);
            if ("group_notices".equalsIgnoreCase(name)) return groupNotices(input);
            if ("mark_read".equalsIgnoreCase(name)) return markRead(input);
            if ("poke".equalsIgnoreCase(name)) return poke(input);
            if ("like".equalsIgnoreCase(name)) return like(input);
            if ("emoji_like".equalsIgnoreCase(name)) return emojiLike(input);
            if ("recall_own".equalsIgnoreCase(name)) return recallOwn(input);
            if ("send_image".equalsIgnoreCase(name)) return sendImage(input);
            if ("send_voice".equalsIgnoreCase(name)) return sendVoice(input);
            if ("set_own_group_card".equalsIgnoreCase(name)) return setOwnGroupCard(input);
            if ("group_leave".equalsIgnoreCase(name)) return groupLeave(input);
            if ("qzone_post".equalsIgnoreCase(name)) return qzonePost(input);
            if ("qzone_delete".equalsIgnoreCase(name)) return qzoneDelete(input);
            if ("qzone_posts".equalsIgnoreCase(name)) return qzonePosts(input);
            if ("pending_requests".equalsIgnoreCase(name)) return pendingRequests(input);
            if ("friend_request".equalsIgnoreCase(name)) return friendRequest(input);
            if ("group_request".equalsIgnoreCase(name)) return groupRequest(input);
            if ("group_ban".equalsIgnoreCase(name)) return groupBan(input);
            if ("group_whole_ban".equalsIgnoreCase(name)) return groupWholeBan(input);
            if ("delete_friend".equalsIgnoreCase(name)) return deleteFriend(input);
            if ("set_friend_remark".equalsIgnoreCase(name)) return setFriendRemark(input);
            if ("set_avatar".equalsIgnoreCase(name)) return setAvatar(input);
            if ("set_profile".equalsIgnoreCase(name)) return setProfile(input);
            if ("set_signature".equalsIgnoreCase(name)) return setSignature(input);
            if ("audit".equalsIgnoreCase(name)) return auditList(input);
            return error("不支持的 QQ 动作："+name);
        } catch (Exception e) {
            plugin.getLogger().sendWarn("[QQ] "+name+" 执行失败："+safe(e.getMessage()));
            return error(name+" 执行失败："+safe(e.getMessage()));
        }
    }

    private JSONObject groupInfo(JSONObject input) {
        if (!config.readToolsEnable) return error("QQ 只读工具未启用");
        long groupID = positive(input,"groupID");
        if (groupID <= 0) return error("缺少 groupID");
        JSONObject params = new JSONObject(true);
        params.put("group_id",String.valueOf(groupID));
        return action("group_info","get_group_info",params,input,0,0,"读取群信息");
    }

    private JSONObject memberInfo(JSONObject input) {
        if (!config.readToolsEnable) return error("QQ 只读工具未启用");
        long groupID = positive(input,"groupID");
        long userID = positive(input,"targetID");
        if (groupID <= 0 || userID <= 0) return error("缺少群号或 QQ");
        JSONObject params = new JSONObject(true);
        params.put("group_id",String.valueOf(groupID));
        params.put("user_id",String.valueOf(userID));
        params.put("no_cache",false);
        return action("member_info","get_group_member_info",params,input,groupID,userID,"读取群成员信息");
    }

    private JSONObject groupHistory(JSONObject input) {
        if (!config.readToolsEnable) return error("QQ 只读工具未启用");
        long groupID = positive(input,"groupID");
        int count = clamp(input.getIntValue("count"),1,50);
        if (groupID <= 0) return error("缺少群号");
        JSONObject params = new JSONObject(true);
        params.put("group_id",String.valueOf(groupID));
        String seq = safe(input.getString("messageSeq"));
        if (!seq.isEmpty()) params.put("message_seq",seq);
        params.put("count",count);
        params.put("reverse_order",false);
        params.put("disable_get_url",false);
        params.put("parse_mult_msg",true);
        return action("group_history","get_group_msg_history",params,input,groupID,0,
                "读取群历史 "+count+" 条");
    }

    private JSONObject messageDetail(JSONObject input) {
        if (!config.readToolsEnable) return error("QQ 只读工具未启用");
        String messageID = safe(input.getString("messageID"));
        if (messageID.isEmpty()) return error("缺少 messageID");
        long groupID = positive(input,"groupID");
        if (groupID > 0) {
            JSONObject detail = getMessage(messageID);
            if (detail == null || !sameGroup(detail,groupID)) {
                return error("消息不属于当前群");
            }
        }
        JSONObject params = new JSONObject(true);
        params.put("message_id",messageID);
        return action("message_detail","get_msg",params,input,groupID,0,"读取消息详情");
    }

    private JSONObject groupNotices(JSONObject input) {
        if (!config.readToolsEnable) return error("QQ 只读工具未启用");
        long groupID = positive(input,"groupID");
        if (groupID <= 0) return error("缺少群号");
        JSONObject params = new JSONObject(true);
        params.put("group_id",String.valueOf(groupID));
        return action("group_notices","get_group_notice",params,input,groupID,0,"读取群公告");
    }

    private JSONObject markRead(JSONObject input) {
        if (!config.readToolsEnable) return error("QQ 只读工具未启用");
        long groupID = positive(input,"groupID");
        if (groupID <= 0) return error("缺少群号");
        JSONObject params = new JSONObject(true);
        params.put("group_id",String.valueOf(groupID));
        return action("mark_read","mark_group_msg_as_read",params,input,groupID,0,"标记群消息已读");
    }

    private JSONObject poke(JSONObject input) {
        if (!config.interactionToolsEnable) return error("QQ 互动工具未启用");
        long groupID = positive(input,"groupID");
        long operatorID = positive(input,"operatorID");
        long targetID = positive(input,"targetID");
        if (groupID <= 0 || operatorID <= 0 || targetID <= 0) return error("缺少群号或目标 QQ");
        if (targetID != operatorID && !isOwnerOrAdmin(groupID,operatorID,input)) {
            return error("只能戳自己或由 owner/管理员指定目标");
        }
        if (!cooldown("poke:"+groupID+":"+targetID,config.pokeCooldownSecond)) {
            return error("戳一戳冷却中，请稍后再说");
        }
        JSONObject params = new JSONObject(true);
        params.put("group_id",String.valueOf(groupID));
        params.put("user_id",String.valueOf(operatorID));
        params.put("target_id",String.valueOf(targetID));
        JSONObject result = action("poke","group_poke",params,input,groupID,targetID,
                "戳一戳 "+targetID);
        if (result.getBooleanValue("status")) mark("poke:"+groupID+":"+targetID);
        return result;
    }

    private JSONObject like(JSONObject input) {
        if (!config.interactionToolsEnable) return error("QQ 互动工具未启用");
        long operatorID = positive(input,"operatorID");
        long targetID = positive(input,"targetID");
        int times = clamp(input.getIntValue("times"),1,config.likeMaxTimes);
        if (operatorID <= 0 || targetID <= 0) return error("缺少 QQ");
        if (targetID != operatorID && !isOwner(operatorID)) {
            return error("只能给自己或 owner 指定目标点赞");
        }
        if (!cooldown("like:"+targetID,config.likeCooldownSecond)) {
            return error("今天已经给该用户点过赞了");
        }
        JSONObject params = new JSONObject(true);
        params.put("user_id",String.valueOf(targetID));
        params.put("times",times);
        JSONObject result = action("like","send_like",params,input,0,targetID,
                "点赞 "+targetID+" x"+times);
        if (result.getBooleanValue("status")) mark("like:"+targetID);
        return result;
    }

    private JSONObject emojiLike(JSONObject input) {
        if (!config.interactionToolsEnable) return error("QQ 互动工具未启用");
        long groupID = positive(input,"groupID");
        String messageID = safe(input.getString("messageID"));
        String emojiID = safe(input.getString("emojiID"));
        if (groupID <= 0 || messageID.isEmpty() || emojiID.isEmpty()) {
            return error("缺少群号、消息 ID 或表情 ID");
        }
        if (!cooldown("emoji:"+messageID,config.emojiLikeCooldownSecond)) {
            return error("这条消息刚刚回应过，请稍后再试");
        }
        JSONObject detail = getMessage(messageID);
        if (detail == null || !sameGroup(detail,groupID)) {
            return error("消息不属于当前群");
        }
        JSONObject params = new JSONObject(true);
        params.put("message_id",messageID);
        params.put("emoji_id",emojiID);
        params.put("set",true);
        JSONObject result = action("emoji_like","set_msg_emoji_like",params,input,groupID,0,
                "表情回应 "+messageID);
        if (result.getBooleanValue("status")) mark("emoji:"+messageID);
        return result;
    }

    private JSONObject recallOwn(JSONObject input) {
        if (!config.interactionToolsEnable) return error("QQ 互动工具未启用");
        long groupID = positive(input,"groupID");
        String messageID = safe(input.getString("messageID"));
        if (groupID <= 0 || messageID.isEmpty()) return error("缺少群号或消息 ID");
        JSONObject detail = getMessage(messageID);
        if (detail == null || !sameGroup(detail,groupID)) return error("消息不属于当前群");
        long senderID = detail.getLongValue("user_id");
        long selfID = positive(input,"selfID");
        if (senderID <= 0 || senderID != selfID) return error("只能撤回机器人自己发送的消息");
        long time = detail.getLongValue("time") * 1000L;
        if (time > 0 && System.currentTimeMillis() - time > config.recallWindowSecond * 1000L) {
            return error("消息已超过可撤回时间");
        }
        JSONObject params = new JSONObject(true);
        params.put("message_id",messageID);
        return action("recall_own","delete_msg",params,input,groupID,0,"撤回自己的消息");
    }

    private JSONObject sendImage(JSONObject input) {
        if (!config.mediaToolsEnable) return error("QQ 媒体工具未启用");
        long groupID = positive(input,"groupID");
        String file = safe(input.getString("file"));
        if (groupID <= 0 || file.isEmpty()) return error("缺少群号或图片");
        if (!file.startsWith("base64://") && !validFileExtension(file,config.imageExtensions)) {
            return error("图片扩展名不在允许范围内");
        }
        if (!withinBase64Limit(file,config.imageMaxBytes)) return error("图片超过大小限制");
        JSONObject sent = client().sendGroupMessage(groupID,
                MessageUtil.message(MessageUtil.image(file)));
        return wrapSend("send_image",input,groupID,0,"发送图片",sent);
    }

    private JSONObject sendVoice(JSONObject input) {
        if (!config.mediaToolsEnable) return error("QQ 媒体工具未启用");
        long groupID = positive(input,"groupID");
        String file = safe(input.getString("file"));
        if (groupID <= 0 || file.isEmpty()) return error("缺少群号或语音");
        if (!file.startsWith("base64://") && !validFileExtension(file,config.voiceExtensions)) {
            return error("语音扩展名不在允许范围内");
        }
        if (!withinBase64Limit(file,config.voiceMaxBytes)) return error("语音超过大小限制");
        JSONObject sent = client().sendGroupMessage(groupID,
                MessageUtil.message(MessageUtil.record(file)));
        return wrapSend("send_voice",input,groupID,0,"发送语音",sent);
    }

    private JSONObject setOwnGroupCard(JSONObject input) {
        if (!config.interactionToolsEnable) return error("QQ 互动工具未启用");
        long groupID = positive(input,"groupID");
        long selfID = positive(input,"selfID");
        long operatorID = positive(input,"operatorID");
        String card = safe(input.getString("card"));
        if (groupID <= 0 || selfID <= 0) return error("缺少群号或机器人 QQ");
        if (!isOwnerOrAdmin(groupID,operatorID,input)) {
            return error("修改机器人群名片需要 owner 或群管理员权限");
        }
        if (card.length() > 30) return error("群名片最多 30 个字符");
        if (!cooldown("card:"+groupID+":"+selfID,config.groupCardCooldownSecond)) {
            return error("群名片修改冷却中");
        }
        JSONObject params = new JSONObject(true);
        params.put("group_id",String.valueOf(groupID));
        params.put("user_id",String.valueOf(selfID));
        params.put("card",card);
        JSONObject result = action("set_own_group_card","set_group_card",params,input,
                groupID,selfID,"修改自己的群名片");
        if (result.getBooleanValue("status")) mark("card:"+groupID+":"+selfID);
        return result;
    }

    private JSONObject groupLeave(JSONObject input) {
        if (!config.adminToolsEnable) return error("QQ 管理工具未启用");
        if (!config.groupLeaveEnable) return error("退群功能未启用");
        long groupID = positive(input,"groupID");
        long operatorID = positive(input,"operatorID");
        if (groupID <= 0) return error("缺少群号");
        if (!isOwner(operatorID)) {
            plugin.getLogger().sendWarn("[QQ] 拒绝非 owner 退群请求 group="+groupID
                    +" operator="+operatorID);
            return error("退群仅 owner 可以要求执行");
        }
        JSONObject params = new JSONObject(true);
        params.put("group_id",String.valueOf(groupID));
        return action("group_leave","set_group_leave",params,input,groupID,0,"退出群聊");
    }

    public void handleNotice(NoticeEvent event) {
        if (!config.enable || !config.notifyOwnerOnGroupEvent || event == null) return;
        String noticeType = safe(event.getNoticeType());
        String subType = safe(event.getSubType());
        long selfID = event.getRaw().getLongValue("self_id");
        if (selfID <= 0 || event.getUserID() != selfID) return;
        long groupID = event.getGroupID();
        if (groupID <= 0) return;
        String message = "";
        if ("group_decrease".equalsIgnoreCase(noticeType)) {
            long operatorID = event.getOperatorID();
            if ("kick_me".equalsIgnoreCase(subType) || "kick".equalsIgnoreCase(subType)) {
                message = "[MBB-QQ] 机器人 "+selfID+" 已被移出群 "+groupID
                        +(operatorID > 0 ? "，操作者 QQ："+operatorID : "");
            } else {
                message = "[MBB-QQ] 机器人 "+selfID+" 已退出群 "+groupID;
            }
        } else if ("group_ban".equalsIgnoreCase(noticeType)
                && "ban".equalsIgnoreCase(subType)) {
            long duration = event.getRaw().getLongValue("duration");
            long operatorID = event.getOperatorID();
            message = "[MBB-QQ] 机器人 "+selfID+" 在群 "+groupID+" 被禁言"
                    +(duration > 0 ? " "+duration+" 秒" : "")
                    +(operatorID > 0 ? "，操作者 QQ："+operatorID : "");
        }
        if (!message.isEmpty()) notifyOwners(message);
    }

    private void notifyOwners(String message) {
        List<Long> owners = plugin.getServer().getOwnerList();
        if (owners == null || owners.isEmpty() || client() == null) return;
        for (Long ownerID : owners) {
            if (ownerID == null || ownerID <= 0) continue;
            JSONObject response = client().sendPrivateMessage(ownerID,
                    MessageUtil.message(MessageUtil.text(message)));
            if (response == null || response.getIntValue("retcode") != 0) {
                plugin.getLogger().sendWarn("[QQ] 群事件通知 owner 失败 owner="+ownerID);
            }
        }
    }

    private JSONObject qzonePost(JSONObject input) {
        if (!config.qzoneEnable) return error("Qzone 工具未启用");
        long operatorID = positive(input,"operatorID");
        if (!isOwner(operatorID)) return error("管理 Qzone 仅 owner 可用");
        String content = safe(input.getString("content")).trim();
        if (content.isEmpty()) return error("说说内容不能为空");
        if (content.length() > config.qzoneContentMaxChars) {
            return error("说说内容最多 "+config.qzoneContentMaxChars+" 个字符");
        }
        JSONArray images = input.getJSONArray("images");
        if (images != null && images.size() > config.qzoneMaxImages) {
            return error("Qzone 图片最多 "+config.qzoneMaxImages+" 张");
        }
        long imageBytes = 0L;
        if (images != null) {
            for (Object object : images) {
                String image = safe(String.valueOf(object));
                if (!withinBase64Limit(image,config.imageMaxBytes)) {
                    return error("Qzone 单张图片超过大小限制");
                }
                if (image.startsWith("base64://")) {
                    imageBytes += Math.max(0,image.length() - "base64://".length()) * 3L / 4L;
                }
            }
            if (imageBytes > 16L * 1024L * 1024L) return error("Qzone 图片总大小超过 16MB");
        }
        if (!cooldown("qzone:post",config.qzoneCooldownSecond)) {
            return error("Qzone 发布冷却中");
        }
        int right = input.getIntValue("ugcRight");
        if (right == 0) right = config.qzoneDefaultUgcRight;
        if (!validUgcRight(right)) return error("Qzone 可见范围不合法");
        JSONObject params = new JSONObject(true);
        params.put("content",content);
        if (images != null) params.put("images",images);
        params.put("ugc_right",right);
        JSONArray targets = input.getJSONArray("targetUins");
        if (targets != null) params.put("target_uins",targets);
        JSONObject result = action("qzone_post","send_qzone_msg",params,input,0,operatorID,
                "发 Qzone："+shortText(content,60));
        if (result.getBooleanValue("status")) {
            mark("qzone:post");
            JSONObject data = result.getJSONObject("data");
            String tid = data == null ? "" : safe(data.getString("tid"));
            storage().insert("INSERT INTO `"+QZONE_TABLE+"` "
                            + "(`tid`,`content`,`images`,`ugcRight`,`createTime`) VALUES (?,?,?,?,?)",
                    tid,content,(images == null ? 0 : images.size())+" 张图片",right,
                    System.currentTimeMillis());
        }
        return result;
    }

    private JSONObject qzoneDelete(JSONObject input) {
        if (!config.qzoneEnable) return error("Qzone 工具未启用");
        long operatorID = positive(input,"operatorID");
        if (!isOwner(operatorID)) return error("管理 Qzone 仅 owner 可用");
        String tid = safe(input.getString("tid")).trim();
        if (tid.isEmpty()) return error("缺少说说 tid");
        JSONObject params = new JSONObject(true);
        params.put("tid",tid);
        JSONObject result = action("qzone_delete","delete_qzone_msg",params,input,0,operatorID,
                "删除 Qzone 说说 "+tid);
        if (result.getBooleanValue("status")) {
            storage().update("DELETE FROM `"+QZONE_TABLE+"` WHERE `tid`=?",tid);
        }
        return result;
    }

    private JSONObject qzonePosts(JSONObject input) {
        if (!config.qzoneEnable) return error("Qzone 工具未启用");
        long operatorID = positive(input,"operatorID");
        if (!isOwner(operatorID)) return error("管理 Qzone 仅 owner 可用");
        int limit = clamp(input.getIntValue("limit"),1,50);
        List<JSONObject> rows = storage().query("SELECT * FROM `"+QZONE_TABLE+"` "
                + "ORDER BY `ID` DESC LIMIT "+limit);
        JSONArray posts = new JSONArray();
        if (rows != null) {
            for (JSONObject row : rows) {
                JSONObject item = new JSONObject(true);
                item.put("id",row.getLongValue("ID"));
                item.put("tid",safe(row.getString("tid")));
                item.put("content",safe(row.getString("content")));
                item.put("images",safe(row.getString("images")));
                item.put("ugcRight",row.getIntValue("ugcRight"));
                item.put("createTime",row.getLongValue("createTime"));
                posts.add(item);
            }
        }
        JSONObject result = ok("读取本机 Qzone 发布记录");
        result.put("posts",posts);
        return result;
    }

    /**
     * 保存好友申请和群邀请。好友申请可交给角色自动裁决，群邀请仍只记录。
     */
    public void recordRequest(org.moboxlab.moboxbot.API.Event.RequestEvent event) {
        if (event == null) return;
        String type = safe(event.getRequestType()).toLowerCase(Locale.ROOT);
        String flag = safe(event.getFlag()).trim();
        if (type.isEmpty() || flag.isEmpty()) return;
        boolean friend = "friend".equals(type);
        if (!config.adminToolsEnable && !(friend && config.friendRequestRoleDecisionEnable)) return;
        long now = System.currentTimeMillis();
        JSONObject exists = storage().queryOne(
                "SELECT `ID` FROM `"+REQUEST_TABLE+"` WHERE `requestType`=? AND `flag`=?",
                type,flag);
        if (exists == null) {
            storage().insert("INSERT INTO `"+REQUEST_TABLE+"` "
                            + "(`requestType`,`subType`,`userID`,`groupID`,`comment`,`flag`,`status`,"
                            + "`createTime`,`updateTime`) VALUES (?,?,?,?,?,?,?,?,?)",
                    type,safe(event.getSubType()).toLowerCase(Locale.ROOT),event.getUserID(),
                    event.getGroupID(),shortText(event.getComment(),500),flag,"pending",now,now);
        } else {
            storage().update("UPDATE `"+REQUEST_TABLE+"` SET `subType`=?,`userID`=?,`groupID`=?,"
                            + "`comment`=?,`status`='pending',`updateTime`=? WHERE `ID`=?",
                    safe(event.getSubType()).toLowerCase(Locale.ROOT),event.getUserID(),
                    event.getGroupID(),shortText(event.getComment(),500),now,
                    exists.getLongValue("ID"));
        }
        if (friend && config.friendRequestRoleDecisionEnable) {
            triggerFriendRoleDecision(event.getUserID());
        }
    }

    private void triggerFriendRoleDecision(long userID) {
        if (userID <= 0 || !config.friendRequestRoleDecisionEnable) return;
        if (!friendDecisionRunning.add(userID)) return;
        plugin.getServer().getPluginManager().runTask(plugin,
                () -> decideFriendRequestByRole(userID));
    }

    private void decideFriendRequestByRole(long userID) {
        try {
            List<JSONObject> pending = pendingFriendRequests(userID);
            if (pending.isEmpty()) return;
            PluginService roleplay = plugin.getServer().getPluginManager().getService("MBB-Roleplay");
            if (roleplay == null) {
                plugin.getLogger().sendWarn("[QQ] 好友申请 "+userID+" 等待角色裁决，但 MBB-Roleplay 未启用");
                scheduleFriendDecisionRetry(userID);
                return;
            }
            JSONObject params = new JSONObject(true);
            params.put("userID",userID);
            params.put("comment",requestComments(pending));
            params.put("subType",safe(pending.get(0).getString("subType")));
            JSONObject decision = roleplay.call("decide-friend-request",params);
            if (decision == null || !decision.getBooleanValue("status")) {
                plugin.getLogger().sendWarn("[QQ] 好友申请 "+userID+" 角色裁决失败："
                        +safe(decision == null ? "无返回" : decision.getString("message")));
                if (decision == null || !"disabled".equals(decision.getString("errorType"))) {
                    scheduleFriendDecisionRetry(userID);
                }
                return;
            }
            boolean approve = decision.getBooleanValue("approve");
            String reason = safe(decision.getString("reason"));
            String remark = safe(decision.getString("remark")).trim();
            plugin.getLogger().sendInfo("[QQ] 好友申请 "+userID+" 由角色决定为"
                    +(approve ? "通过" : "拒绝")+"："+shortText(reason,160));
            JSONObject result = processFriendRequestsForUser(userID,approve,remark,
                    "roleplay",reason);
            if (!result.getBooleanValue("status") && !pendingFriendRequests(userID).isEmpty()) {
                scheduleFriendDecisionRetry(userID);
            }
        } catch (Exception e) {
            plugin.getLogger().sendWarn("[QQ] 好友申请 "+userID+" 自动裁决异常："
                    +safe(e.getMessage()));
            scheduleFriendDecisionRetry(userID);
        } finally {
            friendDecisionRunning.remove(userID);
        }
    }

    private void scheduleFriendDecisionRetry(long userID) {
        plugin.getServer().getPluginManager().runTaskLater(plugin,
                () -> triggerFriendRoleDecision(userID),
                Math.max(30,config.friendRequestRoleDecisionRetrySecond));
    }

    private String requestComments(List<JSONObject> rows) {
        StringBuilder builder = new StringBuilder();
        int limit = Math.min(5,rows.size());
        for (int i = 0; i < limit; i++) {
            String comment = safe(rows.get(i).getString("comment")).trim();
            if (comment.isEmpty()) continue;
            if (builder.length() > 0) builder.append("\n");
            builder.append(comment);
            if (builder.length() >= 500) break;
        }
        return builder.length() <= 500 ? builder.toString() : builder.substring(0,500);
    }

    private JSONObject pendingRequests(JSONObject input) {
        if (!config.adminToolsEnable) return error("QQ 管理工具未启用");
        if (!isOwner(positive(input,"operatorID"))) return error("查看申请列表仅 owner 可用");
        int limit = clamp(input.getIntValue("limit"),1,50);
        List<JSONObject> rows = storage().query(
                "SELECT * FROM `"+REQUEST_TABLE+"` WHERE `status`='pending' "
                        + "ORDER BY `updateTime` DESC,`ID` DESC LIMIT ?",
                limit);
        JSONArray requests = new JSONArray();
        if (rows != null) {
            for (JSONObject row : rows) {
                JSONObject item = new JSONObject(true);
                item.put("id",row.getLongValue("ID"));
                item.put("requestType",safe(row.getString("requestType")));
                item.put("subType",safe(row.getString("subType")));
                item.put("userID",row.getLongValue("userID"));
                item.put("groupID",row.getLongValue("groupID"));
                item.put("comment",safe(row.getString("comment")));
                item.put("flag",safe(row.getString("flag")));
                item.put("createTime",row.getLongValue("createTime"));
                requests.add(item);
            }
        }
        JSONObject result = ok("读取待处理 QQ 申请");
        result.put("requests",requests);
        return result;
    }

    private JSONObject friendRequest(JSONObject input) {
        if (!config.adminToolsEnable) return error("QQ 管理工具未启用");
        if (!isOwner(positive(input,"operatorID"))) return error("处理好友申请仅 owner 可用");
        boolean approve = boolValue(input,"approve",false);
        String remark = safe(input.getString("remark")).trim();
        if (remark.length() > 60) return error("好友备注最多 60 个字符");
        return processFriendRequests(input,approve,remark,"owner","");
    }

    private JSONObject processFriendRequestsForUser(long userID,boolean approve,String remark,
                                                     String source,String reason) {
        JSONObject input = new JSONObject(true);
        input.put("userID",userID);
        input.put("approve",approve);
        if (remark != null && !remark.trim().isEmpty()) input.put("remark",remark.trim());
        return processFriendRequests(input,approve,safe(remark).trim(),source,reason);
    }

    private JSONObject processFriendRequests(JSONObject input,boolean approve,String remark,
                                              String source,String reason) {
        List<JSONObject> selected = friendRequestRows(input);
        if (selected.isEmpty()) return error("没有找到要处理的好友申请，请先读取 pending_requests");
        Map<Long,List<JSONObject>> grouped = new LinkedHashMap<>();
        for (JSONObject row : selected) {
            long userID = row.getLongValue("userID");
            if (userID <= 0) continue;
            List<JSONObject> rows = grouped.get(userID);
            if (rows == null) {
                rows = new ArrayList<>();
                grouped.put(userID,rows);
            }
            if (!rows.contains(row)) rows.add(row);
        }
        int success = 0;
        int skipped = 0;
        int failed = 0;
        JSONArray items = new JSONArray();
        for (Map.Entry<Long,List<JSONObject>> entry : grouped.entrySet()) {
            long userID = entry.getKey();
            List<JSONObject> pending = pendingFriendRequests(userID);
            JSONObject item = new JSONObject(true);
            item.put("targetQQ",userID);
            if (pending.isEmpty()) {
                item.put("status","skipped");
                item.put("message","该 QQ 的申请已处理或已失效");
                skipped++;
                items.add(item);
                continue;
            }
            JSONObject first = pending.get(0);
            String flag = safe(first.getString("flag")).trim();
            if (flag.isEmpty()) {
                item.put("status","failed");
                item.put("message","好友申请缺少 flag");
                failed++;
                items.add(item);
                continue;
            }
            JSONObject params = new JSONObject(true);
            params.put("flag",flag);
            params.put("approve",approve);
            if (approve) {
                if (!remark.isEmpty()) params.put("remark",remark);
            }
            JSONObject result = action("friend_request","set_friend_add_request",params,input,0,
                    userID,approve ? "同意好友申请 "+userID : "拒绝好友申请 "+userID);
            JSONArray requestIDs = new JSONArray();
            for (JSONObject row : pending) requestIDs.add(row.getLongValue("ID"));
            item.put("requestIDs",requestIDs);
            item.put("flag",flag);
            item.put("message",safe(result.getString("message")));
            if (result.getBooleanValue("status")) {
                markFriendRequestsForUser(userID,approve ? "approved" : "rejected",source,reason);
                item.put("status",approve ? "approved" : "rejected");
                success++;
            } else if (isMissingRequest(result.getString("message"))) {
                markFriendRequestsForUser(userID,"expired",source,reason);
                item.put("status","expired");
                item.put("message","QQ 服务端已不存在该申请，剩余重复申请一并收口");
                skipped++;
            } else {
                item.put("status","failed");
                failed++;
            }
            items.add(item);
        }
        JSONObject result = new JSONObject(true);
        result.put("status",success + skipped > 0);
        result.put("partial",failed > 0 && success + skipped > 0);
        result.put("message","好友申请批处理完成：成功 "+success+"，跳过 "+skipped
                +"，失败 "+failed);
        result.put("items",items);
        return result;
    }

    private JSONObject groupRequest(JSONObject input) {
        if (!config.adminToolsEnable) return error("QQ 管理工具未启用");
        if (!isOwner(positive(input,"operatorID"))) return error("处理群邀请仅 owner 可用");
        JSONObject row = requestRow(input,"group");
        String flag = requestFlag(input,row);
        if (flag.isEmpty()) return error("缺少群邀请 flag，请先读取 pending_requests");
        boolean approve = boolValue(input,"approve",false);
        JSONObject params = new JSONObject(true);
        params.put("flag",flag);
        params.put("approve",approve);
        if (row != null && !safe(row.getString("subType")).isEmpty()) {
            params.put("sub_type",safe(row.getString("subType")));
        }
        if (!approve) {
            String reason = safe(input.getString("reason")).trim();
            if (!reason.isEmpty()) params.put("reason",reason);
        }
        JSONObject result = action("group_request","set_group_add_request",params,input,
                row == null ? 0L : row.getLongValue("groupID"),
                row == null ? 0L : row.getLongValue("userID"),
                approve ? "同意群邀请" : "拒绝群邀请");
        if (result.getBooleanValue("status")) markRequest(row,flag,"group",
                approve ? "approved" : "rejected");
        return result;
    }

    private JSONObject groupBan(JSONObject input) {
        if (!config.adminToolsEnable) return error("QQ 管理工具未启用");
        long groupID = positive(input,"groupID");
        long operatorID = positive(input,"operatorID");
        long targetID = positive(input,"targetID");
        long selfID = positive(input,"selfID");
        if (groupID <= 0 || targetID <= 0) return error("缺少群号或目标 QQ");
        if (exampleSelf(selfID,targetID)) return error("不能禁言机器人自己");
        if (!isOwnerOrAdmin(groupID,operatorID,input)) {
            return error("禁言需要 owner 或当前群管理员权限");
        }
        int duration = input.containsKey("duration")
                ? input.getIntValue("duration") : 600;
        if (duration < 0 || duration > 30 * 86400) {
            return error("禁言时长必须在 0 到 30 天之间，0 表示解禁");
        }
        JSONObject params = new JSONObject(true);
        params.put("group_id",String.valueOf(groupID));
        params.put("user_id",String.valueOf(targetID));
        params.put("duration",duration);
        return action("group_ban","set_group_ban",params,input,groupID,targetID,
                duration == 0 ? "解除禁言 "+targetID : "禁言 "+targetID+" "+duration+"秒");
    }

    private JSONObject groupWholeBan(JSONObject input) {
        if (!config.adminToolsEnable) return error("QQ 管理工具未启用");
        long groupID = positive(input,"groupID");
        long operatorID = positive(input,"operatorID");
        if (groupID <= 0) return error("缺少群号");
        if (!isOwnerOrAdmin(groupID,operatorID,input)) {
            return error("全员禁言需要 owner 或当前群管理员权限");
        }
        boolean enable = boolValue(input,"enable",false);
        JSONObject params = new JSONObject(true);
        params.put("group_id",String.valueOf(groupID));
        params.put("enable",enable);
        return action("group_whole_ban","set_group_whole_ban",params,input,groupID,0,
                enable ? "开启全员禁言" : "关闭全员禁言");
    }

    private JSONObject deleteFriend(JSONObject input) {
        if (!config.adminToolsEnable) return error("QQ 管理工具未启用");
        if (!isOwner(positive(input,"operatorID"))) return error("删除好友仅 owner 可用");
        long targetID = positive(input,"targetID");
        if (targetID <= 0) return error("缺少目标 QQ");
        JSONObject params = new JSONObject(true);
        params.put("user_id",String.valueOf(targetID));
        if (input.containsKey("tempBlock")) params.put("temp_block",boolValue(input,"tempBlock",false));
        if (input.containsKey("tempBothDelete")) {
            params.put("temp_both_del",boolValue(input,"tempBothDelete",false));
        }
        return action("delete_friend","delete_friend",params,input,0,targetID,
                "删除好友 "+targetID);
    }

    private JSONObject setFriendRemark(JSONObject input) {
        if (!config.adminToolsEnable) return error("QQ 管理工具未启用");
        if (!isOwner(positive(input,"operatorID"))) return error("设置好友备注仅 owner 可用");
        long targetID = positive(input,"targetID");
        String remark = safe(input.getString("remark")).trim();
        if (targetID <= 0) return error("缺少目标 QQ");
        if (remark.isEmpty()) return error("好友备注不能为空");
        if (remark.length() > 60) return error("好友备注最多 60 个字符");
        JSONObject params = new JSONObject(true);
        params.put("user_id",String.valueOf(targetID));
        params.put("remark",remark);
        return action("set_friend_remark","set_friend_remark",params,input,0,targetID,
                "设置好友备注 "+targetID);
    }

    private JSONObject setAvatar(JSONObject input) {
        if (!config.adminToolsEnable) return error("QQ 管理工具未启用");
        if (!isOwner(positive(input,"operatorID"))) return error("设置头像仅 owner 可用");
        String file = safe(input.getString("file")).trim();
        if (file.isEmpty()) return error("缺少头像文件");
        if (!file.startsWith("base64://") && !file.startsWith("http://")
                && !file.startsWith("https://") && !validFileExtension(file,config.imageExtensions)) {
            return error("头像文件格式不支持");
        }
        if (!withinBase64Limit(file,config.imageMaxBytes)) return error("头像文件超过大小限制");
        JSONObject params = new JSONObject(true);
        params.put("file",file);
        return action("set_avatar","set_qq_avatar",params,input,0,0,"设置 QQ 头像");
    }

    private JSONObject setProfile(JSONObject input) {
        if (!config.adminToolsEnable) return error("QQ 管理工具未启用");
        if (!isOwner(positive(input,"operatorID"))) return error("设置资料卡仅 owner 可用");
        String nickname = safe(input.getString("nickname")).trim();
        if (nickname.isEmpty()) return error("昵称不能为空");
        if (nickname.length() > 30) return error("昵称最多 30 个字符");
        JSONObject params = new JSONObject(true);
        params.put("nickname",nickname);
        String personalNote = safe(input.getString("personalNote")).trim();
        if (personalNote.length() > 200) return error("个性签名最多 200 个字符");
        if (!personalNote.isEmpty()) params.put("personal_note",personalNote);
        if (input.containsKey("sex")) {
            int sex = input.getIntValue("sex");
            if (sex < 0 || sex > 2) return error("性别只能是 0、1 或 2");
            params.put("sex",sex);
        }
        return action("set_profile","set_qq_profile",params,input,0,0,
                "设置 QQ 资料卡："+shortText(nickname,30));
    }

    private JSONObject setSignature(JSONObject input) {
        if (!config.adminToolsEnable) return error("QQ 管理工具未启用");
        if (!isOwner(positive(input,"operatorID"))) return error("设置个性签名仅 owner 可用");
        String longNick = safe(input.getString("longNick")).trim();
        if (longNick.isEmpty()) return error("个性签名不能为空");
        if (longNick.length() > 200) return error("个性签名最多 200 个字符");
        JSONObject params = new JSONObject(true);
        params.put("longNick",longNick);
        return action("set_signature","set_self_longnick",params,input,0,0,
                "设置个性签名："+shortText(longNick,40));
    }

    private List<JSONObject> friendRequestRows(JSONObject input) {
        List<JSONObject> result = new ArrayList<>();
        JSONArray requestIDs = input.getJSONArray("requestIDs");
        if (requestIDs != null) {
            for (Object value : requestIDs) {
                long id = longValue(value);
                if (id <= 0) continue;
                JSONObject row = storage().queryOne("SELECT * FROM `"+REQUEST_TABLE+"` "
                        + "WHERE `ID`=? AND `requestType`='friend'",id);
                if (row != null && !result.contains(row)) result.add(row);
            }
        }
        if (result.isEmpty()) {
            JSONObject row = requestRow(input,"friend");
            if (row != null) result.add(row);
        }
        if (result.isEmpty()) {
            long userID = positive(input,"userID");
            if (userID > 0) {
                List<JSONObject> pending = pendingFriendRequests(userID);
                if (pending != null) result.addAll(pending);
            }
        }
        return result;
    }

    private List<JSONObject> pendingFriendRequests(long userID) {
        List<JSONObject> rows = storage().query("SELECT * FROM `"+REQUEST_TABLE+"` "
                + "WHERE `requestType`='friend' AND `userID`=? AND `status`='pending' "
                + "ORDER BY `ID` ASC",userID);
        return rows == null ? new ArrayList<>() : rows;
    }

    private void markFriendRequestsForUser(long userID,String status,String source,String reason) {
        storage().update("UPDATE `"+REQUEST_TABLE+"` SET `status`=?,`decisionSource`=?,"
                        + "`decisionReason`=?,`updateTime`=? "
                        + "WHERE `requestType`='friend' AND `userID`=? AND `status`='pending'",
                status,safe(source),shortText(reason,300),System.currentTimeMillis(),userID);
    }

    private boolean isMissingRequest(String message) {
        String text = safe(message).toLowerCase(Locale.ROOT);
        return text.contains("no such request") || text.contains("not found")
                || text.contains("不存在") || text.contains("已失效");
    }

    private long longValue(Object value) {
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception e) {
            return 0L;
        }
    }

    private JSONObject requestRow(JSONObject input,String requestType) {
        long id = positive(input,"requestID");
        if (id > 0) {
            return storage().queryOne("SELECT * FROM `"+REQUEST_TABLE+"` "
                    + "WHERE `ID`=? AND `requestType`=?",id,requestType);
        }
        String flag = safe(input.getString("flag")).trim();
        if (flag.isEmpty()) return null;
        return storage().queryOne("SELECT * FROM `"+REQUEST_TABLE+"` "
                + "WHERE `requestType`=? AND `flag`=?",requestType,flag);
    }

    private String requestFlag(JSONObject input,JSONObject row) {
        String flag = row == null ? "" : safe(row.getString("flag")).trim();
        if (flag.isEmpty()) flag = safe(input.getString("flag")).trim();
        return flag;
    }

    private void markRequest(JSONObject row,String flag,String type,String status) {
        if (row != null) {
            storage().update("UPDATE `"+REQUEST_TABLE+"` SET `status`=?,`updateTime`=? WHERE `ID`=?",
                    status,System.currentTimeMillis(),row.getLongValue("ID"));
        } else {
            storage().update("UPDATE `"+REQUEST_TABLE+"` SET `status`=?,`updateTime`=? "
                            + "WHERE `requestType`=? AND `flag`=?",
                    status,System.currentTimeMillis(),type,flag);
        }
    }

    private boolean exampleSelf(long selfID,long targetID) {
        return selfID > 0 && targetID == selfID;
    }

    private boolean boolValue(JSONObject input,String key,boolean defaultValue) {
        if (input == null || !input.containsKey(key)) return defaultValue;
        Object value = input.get(key);
        if (value instanceof Boolean) return ((Boolean)value).booleanValue();
        String text = safe(String.valueOf(value)).trim().toLowerCase(Locale.ROOT);
        return "true".equals(text) || "1".equals(text) || "yes".equals(text)
                || "approve".equals(text) || "on".equals(text);
    }

    private JSONObject auditList(JSONObject input) {
        int limit = clamp(input.getIntValue("limit"),1,50);
        long groupID = positive(input,"groupID");
        List<JSONObject> rows;
        if (groupID > 0) {
            rows = storage().query("SELECT * FROM `"+AUDIT_TABLE+"` WHERE `groupID`=? "
                    + "ORDER BY `ID` DESC LIMIT "+limit,groupID);
        } else {
            rows = storage().query("SELECT * FROM `"+AUDIT_TABLE+"` ORDER BY `ID` DESC LIMIT "+limit);
        }
        JSONArray items = new JSONArray();
        if (rows != null) {
            for (JSONObject row : rows) {
                JSONObject item = new JSONObject(true);
                item.put("id",row.getLongValue("ID"));
                item.put("action",safe(row.getString("action")));
                item.put("groupID",row.getLongValue("groupID"));
                item.put("operatorID",row.getLongValue("operatorID"));
                item.put("targetID",row.getLongValue("targetID"));
                item.put("summary",safe(row.getString("summary")));
                item.put("success",row.getIntValue("success") == 1);
                item.put("time",formatTime(row.getLongValue("createTime")));
                items.add(item);
            }
        }
        JSONObject result = ok("读取 QQ 操作审计");
        result.put("items",items);
        return result;
    }

    private JSONObject action(String auditName,String action,JSONObject params,JSONObject input,
                              long groupID,long targetID,String summary) {
        OneBotClient client = client();
        JSONObject raw = client.callAction(action,params);
        boolean success = success(raw);
        audit(auditName,groupID,positive(input,"operatorID"),targetID,summary,success);
        if (raw == null) return error(action+" 没有返回结果");
        JSONObject result = new JSONObject(true);
        result.put("status",success);
        result.put("action",action);
        Object data = raw.get("data");
        if (data instanceof JSONObject) {
            result.put("data",data);
        } else if (data instanceof JSONArray) {
            JSONObject wrapped = new JSONObject(true);
            wrapped.put("items",data);
            result.put("data",wrapped);
        } else {
            result.put("data",new JSONObject(true));
        }
        if (!success) result.put("message",failureMessage(raw));
        return result;
    }

    private JSONObject wrapSend(String actionName,JSONObject input,long groupID,long targetID,
                                String summary,JSONObject raw) {
        boolean success = success(raw);
        audit(actionName,groupID,positive(input,"operatorID"),targetID,summary,success);
        if (raw == null) return error("消息发送没有返回结果");
        JSONObject result = new JSONObject(true);
        result.put("status",success);
        result.put("data",raw.getJSONObject("data") == null
                ? new JSONObject(true) : raw.getJSONObject("data"));
        if (!success) result.put("message",failureMessage(raw));
        return result;
    }

    private JSONObject getMessage(String messageID) {
        JSONObject params = new JSONObject(true);
        params.put("message_id",messageID);
        JSONObject raw = client().callAction("get_msg",params);
        return raw == null ? null : raw.getJSONObject("data");
    }

    private boolean sameGroup(JSONObject message,long groupID) {
        long value = message == null ? 0 : message.getLongValue("group_id");
        return value == groupID;
    }

    private boolean isOwner(long userID) {
        List<Long> owners = plugin.getServer().getOwnerList();
        return owners != null && owners.contains(userID);
    }

    private boolean isOwnerOrAdmin(long groupID,long userID,JSONObject input) {
        if (isOwner(userID)) return true;
        JSONObject params = new JSONObject(true);
        params.put("group_id",String.valueOf(groupID));
        params.put("user_id",String.valueOf(userID));
        params.put("no_cache",true);
        JSONObject raw = client().callAction("get_group_member_info",params);
        JSONObject data = raw == null ? null : raw.getJSONObject("data");
        String role = data == null ? "" : safe(data.getString("role"));
        return "owner".equals(role) || "admin".equals(role);
    }

    private boolean cooldown(String key,int seconds) {
        String value = plugin.getServer().getStorage().get(plugin,"qqrate:"+key);
        if (value == null || value.isEmpty()) return true;
        try {
            return System.currentTimeMillis() - Long.parseLong(value) >= seconds * 1000L;
        } catch (Exception e) {
            return true;
        }
    }

    private void mark(String key) {
        plugin.getServer().getStorage().set(plugin,"qqrate:"+key,
                String.valueOf(System.currentTimeMillis()));
    }

    private OneBotClient client() {
        OneBotClient client = plugin.getServer().getOneBotClient();
        if (client == null || !client.isConnected()) {
            throw new IllegalStateException("OneBot 未连接");
        }
        return client;
    }

    private boolean success(JSONObject raw) {
        if (raw == null) return false;
        if (raw.containsKey("retcode")) return raw.getIntValue("retcode") == 0;
        return raw.getBooleanValue("status");
    }

    private String failureMessage(JSONObject raw) {
        if (raw == null) return "没有返回结果";
        String message = safe(raw.getString("message"));
        if (message.isEmpty()) message = safe(raw.getString("wording"));
        if (message.isEmpty() && raw.get("data") instanceof JSONObject) {
            message = safe(raw.getJSONObject("data").getString("message"));
        }
        return message.isEmpty() ? "OneBot 动作失败" : shortText(message,300);
    }

    private void audit(String action,long groupID,long operatorID,long targetID,
                       String summary,boolean success) {
        try {
            storage().insert("INSERT INTO `"+AUDIT_TABLE+"` "
                            + "(`action`,`groupID`,`operatorID`,`targetID`,`summary`,`success`,`createTime`) "
                            + "VALUES (?,?,?,?,?,?,?)",
                    action,groupID,operatorID,targetID,shortText(summary,300),
                    success ? 1 : 0,System.currentTimeMillis());
            if (System.currentTimeMillis() % 100 < 5) cleanupAudit();
        } catch (Exception e) {
            plugin.getLogger().sendWarn("[QQ] 审计写入失败："+safe(e.getMessage()));
        }
    }

    private void cleanupAudit() {
        long cutoff = System.currentTimeMillis() - config.auditKeepDays * 86400000L;
        storage().update("DELETE FROM `"+AUDIT_TABLE+"` WHERE `createTime`<?",cutoff);
        storage().update("DELETE FROM `"+AUDIT_TABLE+"` WHERE `ID` NOT IN "
                + "(SELECT `ID` FROM `"+AUDIT_TABLE+"` ORDER BY `ID` DESC LIMIT "
                +config.auditMaxRows+")");
    }

    private void cleanupRequests() {
        long cutoff = System.currentTimeMillis() - config.requestKeepDays * 86400000L;
        storage().update("DELETE FROM `"+REQUEST_TABLE+"` WHERE `updateTime`<?",cutoff);
    }

    private void ensureRequestColumn(String column,String definition) {
        List<JSONObject> columns = storage().query("PRAGMA table_info(`"+REQUEST_TABLE+"`)");
        if (columns != null) {
            for (JSONObject row : columns) {
                if (column.equalsIgnoreCase(row.getString("name"))) return;
            }
        }
        storage().update("ALTER TABLE `"+REQUEST_TABLE+"` ADD COLUMN `"+column+"` "+definition);
    }

    private boolean validUgcRight(int right) {
        return right == 1 || right == 4 || right == 16 || right == 64 || right == 128;
    }

    private boolean withinBase64Limit(String file,int maxBytes) {
        if (file == null || !file.startsWith("base64://")) return true;
        long encoded = Math.max(0,file.length() - "base64://".length());
        return encoded * 3L / 4L <= maxBytes;
    }

    private boolean validFileExtension(String file,String allowed) {
        String name = safe(file);
        int slash = Math.max(name.lastIndexOf('/'),name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot + 1 >= name.length()) return false;
        String extension = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        String[] extensions = safe(allowed).split(",");
        for (String item : extensions) {
            if (extension.equals(item.trim().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private long positive(JSONObject input,String key) {
        if (input == null) return 0L;
        long value = input.getLongValue(key);
        return value > 0 ? value : 0L;
    }

    private int clamp(int value,int min,int max) {
        if (value < min) return min;
        return Math.min(value,max);
    }

    private String formatTime(long time) {
        if (time <= 0) return "";
        return new SimpleDateFormat("MM-dd HH:mm:ss",Locale.CHINA).format(new Date(time));
    }

    private String shortText(String value,int max) {
        String text = safe(value).replaceAll("\\s+"," ").trim();
        return text.length() <= max ? text : text.substring(0,max)+"...";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private JSONObject ok(String message) {
        JSONObject result = new JSONObject(true);
        result.put("status",true);
        result.put("message",message);
        return result;
    }

    private JSONObject error(String message) {
        JSONObject result = new JSONObject(true);
        result.put("status",false);
        result.put("message",message);
        return result;
    }

    private org.moboxlab.moboxbot.API.Storage.StorageService storage() {
        return plugin.getServer().getStorage();
    }
}
