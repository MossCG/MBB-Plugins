package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Command.CommandPermission;
import org.moboxlab.moboxbot.API.Command.CommandSender;
import org.moboxlab.moboxbot.API.Util.ImageUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Roleplay 命令处理器：RoleplayMemberCommandHandler
 */
public class RoleplayMemberCommandHandler {
    private final RoleplayCommand command;

    public RoleplayMemberCommandHandler(RoleplayCommand command) {
        this.command = command;
    }

    void handleMember(CommandSender sender,String[] args) {
        long groupID = sender.getGroupID();
        if (groupID <= 0) {
            sender.sendMessage("请在群聊中使用。");
            return;
        }
        String action = args.length > 2 ? args[2].toLowerCase() : "";
        if ("clear".equals(action)) {
            if (args.length < 4) {
                sender.sendMessage("用法：/role member clear <QQ|@群友>");
                return;
            }
            long targetID = command.parseUserID(args[3]);
            if (targetID <= 0) {
                sender.sendMessage("QQ 格式不正确。");
                return;
            }
            sender.sendMessage(command.service().getMemberService().remove(groupID,targetID)
                    ? "已清空对 "+targetID+" 的个人印象。"
                    : "没有找到 "+targetID+" 的个人印象。");
            return;
        }
        long userID = sender.getUserID();
        if (args.length > 2 && !action.isEmpty()) {
            userID = command.parseUserID(args[2]);
            if (userID <= 0) {
                sender.sendMessage("QQ 格式不正确。");
                return;
            }
        }
        JSONObject profile = command.service().getMemberService().profile(groupID,userID);
        if (profile == null) {
            sender.sendMessage("还没有对 "+userID+" 的个人印象。");
            return;
        }
        sender.sendMessage("对 "+userID+"（"+profile.getString("userName")+"）的个人印象："
                +"\n称呼："+command.orDash(profile.getString("alias"))
                +"\n喜欢："+command.orDash(profile.getString("likes"))
                +"\n不喜欢："+command.orDash(profile.getString("dislikes"))
                +"\n相处方式："+command.orDash(profile.getString("notes")));
    }

    void handleHelp(CommandSender sender,String[] args) {
        String category = args.length > 2 ? args[2].toLowerCase() : "all";
        if ("basic".equals(category)) {
            sender.sendMessage("基础命令："
                    +"\n/role status"
                    +"\n/role random"
                    +"\n/role enable [群号]"
                    +"\n/role disable [群号]"
                    +"\n/role groups"
                    +"\n/role reload"
                    +"\n/role config [repair]");
            return;
        }
        if ("quiet".equals(category)) {
            sender.sendMessage("免打扰："
                    +"\n/role quiet add [QQ|@群友]"
                    +"\n/role quiet remove [QQ|@群友]"
                    +"\n/role quiet list"
                    +"\n/role quiet on|off"
                    +"\n/role quiet clear"
                    +"\nQQ 参数可以直接用群聊艾特代替。");
            return;
        }
        if ("blacklist".equals(category)) {
            sender.sendMessage("黑名单："
                    +"\n/role blacklist list"
                    +"\n/role blacklist add <QQ|@群友> [原因]"
                    +"\n/role blacklist remove <QQ|@群友>"
                    +"\n/role blacklist clear"
                    +"\nQQ 参数可以直接用群聊艾特代替。");
            return;
        }
        if ("emotion".equals(category)) {
            sender.sendMessage("情绪与关系："
                    +"\n/role mood [reset]"
                    +"\n/role emotion [QQ] [页码]"
                    +"\n/role emotion reset [QQ]"
                    +"\n/role emotion log [页码]"
                    +"\n/role emotion affinity set <QQ|@群友> <0-100>"
                    +"\n/role emotion reason set|clear <QQ|@群友> [原因]"
                    +"\nQQ 参数可以直接用群聊艾特代替。");
            return;
        }
        if ("memory".equals(category)) {
            sender.sendMessage("记忆："
                    +"\n/role memory [页码|merge|backup|backups|restore <文件名>]"
                    +"\n/role memory timeline [页码]"
                    +"\n/role memory conflicts [条数]"
                    +"\n/role gmemory [页码|backup|merge|group ...]"
                    +"\n/role forget"
                    +"\n/role review [now]");
            return;
        }
        if ("role".equals(category)) {
            sender.sendMessage("角色与机器人："
                    +"\n/role persona [文件名]"
                    +"\n/role persona reset <文件名>"
                    +"\n/role bot [status|chance|max|qq|name] ..."
                    +"\n/role bot qq 的 QQ 参数可以用群聊艾特代替。");
            return;
        }
        if ("content".equals(category)) {
            sender.sendMessage("语料与知识库："
                    +"\n/role speech [stats|reload|search <文本>|on|off]"
                    +"\n/role kb [list|reload|search <文本>|on|off]");
            return;
        }
        if ("feedback".equals(category)) {
            sender.sendMessage("反馈与表达："
                    +"\n/role feedback [reset]"
                    +"\n/role expression list [pending|approved|rejected]"
                    +"\n/role expression promote <ID>"
                    +"\n/role expression reject <ID>"
                    +"\n/role expression clear"
                    +"\n/role attention [reset]"
                    +"\n/role trace [条数]"
                    +"\n/role eval [list|run <id|all>]");
            return;
        }
        if ("tool".equals(category)) {
            sender.sendMessage("工具与检索："
                    +"\n/role tool stats          查看当前群工具调用统计"
                    +"\n/role tool test           检查工具 schema、参数校验、工作区和网页白名单"
                    +"\n/role tool trace [条数]   查看最近工具调用"
                    +"\n/role tool cancel         取消当前群正在执行的工具回合"
                    +"\n/role tool clear          清空当前群工具结果上下文");
            return;
        }
        if ("learn".equals(category) || "corpus".equals(category)) {
            sender.sendMessage("训练语料采集："
                    +"\n/role learn [备注]          回复某条消息再发，采集那条消息和前后文"
                    +"\n/role learn                不带引用时采集最近一条群消息"
                    +"\n/role learn stats          查看累计采集数量"
                    +"\n/role learn recent [条数]  查看最近采集的记录");
            return;
        }
        if ("train".equals(category)) {
            sender.sendMessage("本地小模型训练："
                    +"\n/role train                查看模型服务与训练状态"
                    +"\n/role train corpus [条数]  用上次截止到现在的新消息继续训练当前 adapter"
                    +"\n/role train fresh [条数]   从最早群消息开始，从底座新开 adapter"
                    +"\n/role train learn          用 /role learn 采集的特挑语料训练"
                    +"\n/role train cutoff [时间]  查看/设置/重置训练截止时间"
                    +"\n/role train reload         让 MBB-Model 重新加载 adapter");
            return;
        }
        sender.sendMessage("MBB-Roleplay 命令分类："
                +"\n/role help basic        基础与开关"
                +"\n/role help quiet        免打扰"
                +"\n/role help blacklist    黑名单"
                +"\n/role help emotion      情绪与关系"
                +"\n/role help memory       记忆"
                +"\n/role help role         角色与机器人"
                +"\n/role help content      语料与知识库"
                +"\n/role help feedback     反馈与表达"
                +"\n/role help tool         工具与检索"
                +"\n/role help learn        训练语料采集"
                +"\n/role help train        本地小模型训练"
                +"\n/role help all          查看全部命令");
        if ("all".equals(category)) {
            sender.sendMessage("完整命令表较长，请按上面的分类逐项查看。");
        }
    }
}
