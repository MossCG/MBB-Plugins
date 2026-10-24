package org.moboxlab.mbb.trpg.testutil;

import org.moboxlab.moboxbot.API.Command.CommandPermission;
import org.moboxlab.moboxbot.API.Command.CommandSender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 指令来源假件：记录全部回执，权限按构造时给定的身份判定。 */
public final class FakeCommandSender implements CommandSender {

    private final boolean group;
    private final long groupId;
    private final long userId;
    private final String name;
    private final boolean groupAdmin;
    private final boolean botAdmin;
    private final List<String> replies = new ArrayList<String>();

    public FakeCommandSender(boolean group, long groupId, long userId, String name,
                             boolean groupAdmin, boolean botAdmin) {
        this.group = group;
        this.groupId = groupId;
        this.userId = userId;
        this.name = name;
        this.groupAdmin = groupAdmin;
        this.botAdmin = botAdmin;
    }

    /** 群内普通成员。 */
    public static FakeCommandSender group(long groupId, long userId, String name) {
        return new FakeCommandSender(true, groupId, userId, name, false, false);
    }

    /** 群管理员。 */
    public static FakeCommandSender groupAdmin(long groupId, long userId, String name) {
        return new FakeCommandSender(true, groupId, userId, name, true, false);
    }

    /** 群主：权限判定与群管理员同档，单独保留以便覆盖 GROUP_OWNER 分支。 */
    public static FakeCommandSender groupOwner(long groupId, long userId, String name) {
        return new FakeCommandSender(true, groupId, userId, name, true, false);
    }

    /** 私聊来源。 */
    public static FakeCommandSender privateSender(long userId, String name) {
        return new FakeCommandSender(false, 0L, userId, name, false, false);
    }

    /** 返回一个带机器人管理权限的副本。 */
    public FakeCommandSender botAdmin() {
        return new FakeCommandSender(group, groupId, userId, name, groupAdmin, true);
    }

    @Override
    public boolean isGroup() {
        return group;
    }

    @Override
    public long getGroupID() {
        return groupId;
    }

    @Override
    public long getUserID() {
        return userId;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getCommandPrefix() {
        return "/";
    }

    @Override
    public boolean hasPermission(CommandPermission permission) {
        if (permission == null) {
            return false;
        }
        switch (permission) {
            case OWNER:
            case BOT_ADMIN:
                return botAdmin;
            case GROUP_OWNER:
            case GROUP_ADMIN:
                return groupAdmin;
            default:
                return true;
        }
    }

    @Override
    public void sendMessage(String message) {
        replies.add(message);
    }

    @Override
    public void reply(String message) {
        replies.add(message);
    }

    /** 全部回执，按发生顺序。 */
    public List<String> replies() {
        return Collections.unmodifiableList(replies);
    }

    /** 最近一条回执，没有回执时返回空串。 */
    public String lastReply() {
        return replies.isEmpty() ? "" : replies.get(replies.size() - 1);
    }

    /** 清空回执。 */
    public void clear() {
        replies.clear();
    }
}
