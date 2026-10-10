package org.moboxlab.mbb.antispam;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Event.GroupMessageEvent;
import org.moboxlab.moboxbot.API.OneBot.MessageUtil;
import org.moboxlab.moboxbot.API.Plugin;
import org.moboxlab.moboxbot.API.Storage.StorageService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 刷屏治理服务
 *
 * 维护每个群每个用户的滑动窗口，命中规则后按累计违规次数升级处置，
 * 事件写入 SQLite 供 /antispam log 查询，可选撤回消息、禁言和私信管理员。
 */
public class AntiSpamService {
    private static final String EVENT_TABLE = "plugin_mbb_antispam_event";
    private static final String GROUP_TABLE = "plugin_mbb_antispam_group";
    private static final long CLEANUP_INTERVAL_MILLIS = 60 * 1000L;

    /** 一个群成员的全部窗口数据 */
    private static class UserWindow {
        private final List<Long> messageTimes = new ArrayList<>();
        private final List<Long> mentionTimes = new ArrayList<>();
        private final Map<String,List<Long>> fingerprints = new java.util.HashMap<>();
        private final List<Long> violationTimes = new ArrayList<>();
        /** 上一次真正计数的时间，用于把同一波刷屏合并成一波 */
        private long lastCountedViolation = 0L;
        private long lastActive = 0L;
    }

    /** 一次命中后的处置依据 */
    private static class Handled {
        /** 计入后的累计违规次数，0 表示本轮只记录 */
        private int violationCount = 0;
        /** 本次是否发出了集体刷屏的整群提醒 */
        private boolean warnCollective = false;
    }

    private final Plugin plugin;
    private volatile AntiSpamConfig config;
    private final Map<String,UserWindow> windows = new ConcurrentHashMap<>();
    /** 内容指纹 -> 发过这句话的不同用户，用来区分个人复读和多人玩梗 */
    private final Map<String,Set<Long>> fingerprintUsers = new ConcurrentHashMap<>();
    /** 群ID|内容指纹 -> 本群所有人发过的时间戳，用于识别多人一起复读 */
    private final Map<String,List<Long>> groupFingerprints = new ConcurrentHashMap<>();
    /** 群ID|内容指纹 -> 上次整群提醒的时间，提醒后仍继续刷才对参与者处置 */
    private final Map<String,Long> collectiveWarnTimes = new ConcurrentHashMap<>();
    /** 已经提醒过的用户，避免反复打扰：groupID|userID -> 上次提醒时间 */
    private final Map<String,Long> noticeTimes = new ConcurrentHashMap<>();
    /**
     * 已处置过的消息 ID -> 处置时间，用于防止同一条消息被重复投递时重复处置
     *
     * 存时间而不是只存 ID，是为了能在清理时按时间回收，避免长期运行无限增长。
     */
    private final Map<Long,Long> handledMessages = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanup = new AtomicLong(0L);

    public AntiSpamService(Plugin plugin,AntiSpamConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void init() {
        storage().update("CREATE TABLE IF NOT EXISTS `"+EVENT_TABLE+"` ("
                + "`ID` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`groupID` INTEGER NOT NULL DEFAULT 0,"
                + "`userID` INTEGER NOT NULL DEFAULT 0,"
                + "`messageID` INTEGER NOT NULL DEFAULT 0,"
                + "`rule` TEXT NOT NULL DEFAULT '',"
                + "`score` INTEGER NOT NULL DEFAULT 0,"
                + "`count` INTEGER NOT NULL DEFAULT 0,"
                + "`violationCount` INTEGER NOT NULL DEFAULT 0,"
                + "`action` TEXT NOT NULL DEFAULT '',"
                + "`content` TEXT NOT NULL DEFAULT '',"
                + "`detail` TEXT NOT NULL DEFAULT '',"
                + "`eventTime` INTEGER NOT NULL DEFAULT 0"
                + ")");
        storage().update("CREATE INDEX IF NOT EXISTS `idx_plugin_mbb_antispam_event_group` "
                + "ON `"+EVENT_TABLE+"` (`groupID`,`eventTime`)");
        storage().update("CREATE TABLE IF NOT EXISTS `"+GROUP_TABLE+"` ("
                + "`ID` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`groupID` INTEGER NOT NULL DEFAULT 0 UNIQUE,"
                + "`enabled` INTEGER NOT NULL DEFAULT 0,"
                + "`updateTime` INTEGER NOT NULL DEFAULT 0"
                + ")");
    }

    public void reload(AntiSpamConfig config) {
        this.config = config == null ? this.config : config;
    }

    public AntiSpamConfig config() {
        return config;
    }

    /**
     * 处理一条群消息，命中刷屏规则时记录并按阶梯处置
     */
    /**
     * 处理一条群消息
     *
     * 这条逻辑运行在插件任务线程上，任何异常都不能往外抛，否则会把异常带进调度线程；
     * 出错时只记录日志，不影响机器人继续运行。
     */
    public void handle(GroupMessageEvent event) {
        try {
            handleInternal(event);
        } catch (Exception e) {
            plugin.getLogger().sendException(e);
        }
    }

    /**
     * 刷屏判定与处置的实际实现
     */
    private void handleInternal(GroupMessageEvent event) {
        AntiSpamConfig current = config;
        if (event == null || current == null || !current.enable) return;
        long groupID = event.getGroupID();
        long userID = event.getUserID();
        if (groupID <= 0 || userID <= 0) return;
        if (!isGroupEnabled(groupID)) return;
        if (isBypassed(userID)) return;
        long now = System.currentTimeMillis();
        cleanupIfNeeded(now);

        AntiSpamMessage.Content content = AntiSpamMessage.extract(event.getMessage());
        String fingerprint = AntiSpamMessage.fingerprint(content.text,current.repeatFingerprintChars);
        //过短内容不参与连续刷屏统计，避免把正常表情和单字回复误判
        if (content.text.length() < current.minMessageLength && !content.mentionAll) return;

        String key = groupID+"|"+userID;
        FloodRuleEngine.Result result;
        Handled handled;
        synchronized (key.intern()) {
            UserWindow window = windows.get(key);
            if (window == null) {
                window = new UserWindow();
                windows.put(key,window);
            }
            window.lastActive = now;
            //先取快照再落本次数据，判定的是"包含本条在内"的窗口
            FloodRuleEngine.Snapshot snapshot = snapshot(window,current,groupID,fingerprint,
                    content,now);
            result = FloodRuleEngine.evaluate(current,snapshot,now);
            append(window,fingerprint,now,content.mentionCount);
            appendFingerprintUser(groupID,userID,fingerprint,now);
            handled = result.violated
                    ? registerViolation(window,current,result,groupKey(groupID,fingerprint),now)
                    : new Handled();
        }
        if (!result.violated) return;
        //同一条消息可能被重复投递，按消息 ID 去重，避免重复处置
        long messageID = event.getMessageID();
        if (messageID > 0 && handledMessages.putIfAbsent(messageID,now) != null) return;
        //个人刷屏时在说明里写上发言人，便于管理员核对
        if ("personal".equals(result.scope) && result.detail != null && !result.detail.isEmpty()) {
            result.detail = result.detail + "，发言人 "+userID;
        }

        List<String> actions = new ArrayList<>();
        String action = current.defaultAction == null ? "log" : current.defaultAction.trim().toLowerCase();
        //只记录不升级的命中（纯图片表情、参与人数适中的玩梗、首次违规）：不入处置阶梯
        boolean forgiven = result.forgive || handled.violationCount <= 0;
        if (!forgiven && current.deleteAfterViolations > 0
                && handled.violationCount >= current.deleteAfterViolations) {
            if (deleteMessage(event)) actions.add("delete");
        }
        if (!forgiven && current.banAfterViolations > 0
                && handled.violationCount >= current.banAfterViolations) {
            //配置单位是分钟，OneBot 的 set_group_ban 用秒，这里换算
            long seconds = current.banDurationMinute * 60L;
            if (banUser(groupID,userID,seconds)) actions.add("ban");
        }
        //告警：群内提示由 alertCurrentGroup 决定，管理员私信由 alertAdminPrivate 决定
        boolean alert = config.alertCurrentGroup || config.alertAdminPrivate;
        if (alert && !"ignore".equals(action) && !forgiven) actions.add("alert");

        if (!"ignore".equals(action)) {
            plugin.getLogger().sendInfo("[刷屏] 群"+groupID+" 用户"+userID
                    +" 规则="+result.rule+" 说明="+result.detail
                    +(forgiven ? " 处置=仅记录" : " 累计违规="+handled.violationCount
                    +(actions.isEmpty() ? "" : " 处置="+join(actions))));
        }
        logEvent(groupID,userID,messageID,result,handled.violationCount,
                forgiven ? "forgiven" : join(actions),content.text,now);
        if (handled.warnCollective) collectiveWarn(groupID,userID,result,current);
        if (alert && !forgiven) {
            alert(groupID,userID,result,handled.violationCount);
        }
        if (!forgiven) notice(groupID,userID,result);
        purgeOldEvents(now);
    }

    private FloodRuleEngine.Snapshot snapshot(UserWindow window,AntiSpamConfig current,
                                              long groupID,String fingerprint,
                                              AntiSpamMessage.Content content,long now) {
        FloodRuleEngine.Snapshot snapshot = new FloodRuleEngine.Snapshot();
        snapshot.messageTimes = toArray(window.messageTimes);
        snapshot.mentionTimes = toArray(window.mentionTimes);
        snapshot.fingerprints = window.fingerprints;
        snapshot.groupFingerprints = groupFingerprintMap(groupID);
        snapshot.fingerprintUsers = distinctUsers(groupID);
        snapshot.fingerprint = fingerprint;
        snapshot.contentLength = content.text.length();
        snapshot.mentionCount = content.mentionCount;
        snapshot.mentionAll = content.mentionAll;
        snapshot.mediaOnly = content.mediaOnly;
        snapshot.mediaCount = content.mediaCount;
        return snapshot;
    }

    /**
     * 统计每个内容指纹在本群被多少个不同用户发过
     */
    private Map<String,Integer> distinctUsers(long groupID) {
        Map<String,Integer> result = new java.util.HashMap<>();
        String prefix = groupID+"|";
        for (Map.Entry<String,Set<Long>> entry : fingerprintUsers.entrySet()) {
            if (!entry.getKey().startsWith(prefix)) continue;
            String fingerprint = entry.getKey().substring(prefix.length());
            result.put(fingerprint,entry.getValue().size());
        }
        return result;
    }

    /**
     * 取出本群每个人的复读时间线，用于识别多人一起复读同一句话
     */
    private Map<String,List<Long>> groupFingerprintMap(long groupID) {
        Map<String,List<Long>> result = new java.util.HashMap<>();
        String prefix = groupID+"|";
        for (Map.Entry<String,List<Long>> entry : groupFingerprints.entrySet()) {
            if (!entry.getKey().startsWith(prefix)) continue;
            result.put(entry.getKey().substring(prefix.length()),entry.getValue());
        }
        return result;
    }

    private void append(UserWindow window,String fingerprint,long now,int mentionCount) {
        window.messageTimes.add(now);
        for (int i = 0; i < mentionCount; i++) window.mentionTimes.add(now);
        if (fingerprint != null && !fingerprint.isEmpty()) {
            List<Long> times = window.fingerprints.get(fingerprint);
            if (times == null) {
                times = new ArrayList<>();
                window.fingerprints.put(fingerprint,times);
            }
            times.add(now);
        }
        trim(window,now);
    }

    /**
     * 记录「这个内容在本群被哪些用户、在什么时间发过」
     *
     * 用户集合用来区分个人复读和多人玩梗，时间线用来识别多人一起复读同一句话。
     */
    private void appendFingerprintUser(long groupID,long userID,String fingerprint,long now) {
        if (fingerprint == null || fingerprint.isEmpty()) return;
        String key = groupID+"|"+fingerprint;
        Set<Long> users = fingerprintUsers.get(key);
        if (users == null) {
            users = Collections.newSetFromMap(new ConcurrentHashMap<Long,Boolean>());
            Set<Long> previous = fingerprintUsers.putIfAbsent(key,users);
            if (previous != null) users = previous;
        }
        users.add(userID);
        List<Long> times = groupFingerprints.get(key);
        if (times == null) {
            List<Long> created = Collections.synchronizedList(new ArrayList<Long>());
            List<Long> previous = groupFingerprints.putIfAbsent(key,created);
            times = previous == null ? created : previous;
        }
        synchronized (times) {
            times.add(now);
            //群级时间线只保留最长统计窗口内的数据
            AntiSpamConfig current = config;
            long keep = Math.max(current.repeatGroupLongWindowSecond,current.repeatLongWindowSecond) * 1000L;
            long from = now - keep;
            int remove = 0;
            while (remove < times.size() && times.get(remove) < from) remove++;
            if (remove > 0) times.subList(0,remove).clear();
        }
    }

    /**
     * 丢掉超出最大统计窗口的数据，控制单用户内存占用
     */
    private void trim(UserWindow window,long now) {
        AntiSpamConfig current = config;
        long keepMillis = Math.max(current.rateLongWindowSecond,
                Math.max(current.repeatLongWindowSecond,current.mentionWindowSecond)) * 1000L;
        trimTimes(window.messageTimes,now - keepMillis);
        trimTimes(window.mentionTimes,now - keepMillis);
        long fingerprintKeep = current.repeatLongWindowSecond * 1000L;
        java.util.Iterator<Map.Entry<String,List<Long>>> iterator = window.fingerprints.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String,List<Long>> entry = iterator.next();
            trimTimes(entry.getValue(),now - fingerprintKeep);
            if (entry.getValue().isEmpty()) iterator.remove();
        }
    }

    private void trimTimes(List<Long> times,long from) {
        int remove = 0;
        while (remove < times.size() && times.get(remove) < from) remove++;
        if (remove > 0) times.subList(0,remove).clear();
    }

    /**
     * 计入一次违规，返回本轮处置依据
     *
     * 只记录不升级的命中（纯图片表情、参与人数适中的玩梗）直接返回 0，不进入处置阶梯。
     * forgiveFirst 打开时，窗口内第一次命中也不计数，等于先给一次提醒；但对集体刷屏
     * 不适用——集体刷屏的"第一次"是整群提醒（warnCollective），提醒之后仍继续刷的
     * 参与者才计数处置，而且每个参与者都不再单独享有"首次只提醒"。
     * violationCountMode=session（默认）时，同一波刷屏只算一次：间隔不足
     * violationCooldownSecond 秒的连续命中会被合并，避免一个人因为一时的连发
     * 就被迅速禁言；改成 message 则每条命中都计数。
     */
    private Handled registerViolation(UserWindow window,AntiSpamConfig current,
                                      FloodRuleEngine.Result result,String key,long now) {
        Handled handled = new Handled();
        if (result != null && result.forgive) return handled;
        boolean collective = result != null && "collective".equals(result.scope);
        if (collective && current.collectiveWarnCooldownSecond > 0
                && shouldWarnCollective(key,now)) {
            handled.warnCollective = true;
            return handled;
        }
        long from = now - current.violationWindowSecond * 1000L;
        trimTimes(window.violationTimes,from);
        //集体刷屏的"一波"就是整群一起刷，提醒之后每一条都要算，否则参与者永远停在 1 次
        if (!collective && !"message".equals(current.violationCountMode)
                && window.lastCountedViolation > 0
                && now - window.lastCountedViolation < current.violationCooldownSecond * 1000L) {
            //同一波刷屏：已经计过一次，这里只记录事件，不再累加
            handled.violationCount = window.violationTimes.size();
            return handled;
        }
        window.lastCountedViolation = now;
        if (current.forgiveFirst && !collective && window.violationTimes.isEmpty()) {
            window.violationTimes.add(now);
            return handled;
        }
        window.violationTimes.add(now);
        handled.violationCount = window.violationTimes.size();
        return handled;
    }

    private String groupKey(long groupID,String fingerprint) {
        return groupID+"|"+fingerprint;
    }

    /**
     * 同一个群同一句话是否还需要整群提醒，需要时记录提醒时间
     */
    private boolean shouldWarnCollective(String key,long now) {
        Long last = collectiveWarnTimes.get(key);
        if (last != null && now - last < config.collectiveWarnCooldownSecond * 1000L) return false;
        collectiveWarnTimes.put(key,now);
        return true;
    }

    /**
     * 集体刷屏的整群提醒：不艾特任何参与者，避免把公屏变成点名现场
     */
    private void collectiveWarn(long groupID,long userID,FloodRuleEngine.Result result,
                                AntiSpamConfig current) {
        String warning = "这条消息发得有点多了（"+result.label+"），大家先停一下～"
                +"\n如果继续这样的话，后面参与的朋友可能会被撤回消息";
        if (current.banAfterViolations > 0) {
            warning = warning+"或禁言 "+current.banDurationMinute+" 分钟";
        }
        warning = warning+"。";
        try {
            JSONArray message = MessageUtil.message(MessageUtil.text(warning));
            plugin.getServer().getOneBotClient().sendGroupMessage(groupID,message);
        } catch (Exception e) {
            plugin.getLogger().sendException(e);
        }
        plugin.getLogger().sendInfo("[刷屏] 群"+groupID+" 集体刷屏已整群提醒（未艾特参与者）"
                +" 触发者="+userID+" 规则="+result.rule);
    }

    private long[] toArray(List<Long> values) {
        long[] result = new long[values.size()];
        for (int i = 0; i < result.length; i++) result[i] = values.get(i);
        return result;
    }

    private boolean deleteMessage(GroupMessageEvent event) {
        if (event.getMessageID() <= 0) return false;
        try {
            JSONObject response = plugin.getServer().getOneBotClient().deleteMessage(event.getMessageID());
            return response == null || response.getIntValue("retcode") == 0;
        } catch (Exception e) {
            plugin.getLogger().sendException(e);
            return false;
        }
    }

    private boolean banUser(long groupID,long userID,long durationSecond) {
        try {
            JSONObject response = plugin.getServer().getOneBotClient()
                    .setGroupBan(groupID,userID,durationSecond);
            return response == null || response.getIntValue("retcode") == 0;
        } catch (Exception e) {
            plugin.getLogger().sendException(e);
            return false;
        }
    }

    private void alert(long groupID,long userID,FloodRuleEngine.Result result,int violationCount) {
        AntiSpamConfig current = config;
        String text = "[刷屏告警] 群"+groupID+" 用户"+userID
                +"\n规则："+result.label
                +"\n说明："+result.detail
                +"\n累计违规："+violationCount+" 次";
        if (current.alertCurrentGroup) {
            try {
                JSONArray message = MessageUtil.message(MessageUtil.text(text));
                plugin.getServer().getOneBotClient().sendGroupMessage(groupID,message);
            } catch (Exception e) {
                plugin.getLogger().sendException(e);
            }
        }
        if (!current.alertAdminPrivate) return;
        for (Long admin : plugin.getServer().getAdminList()) {
            if (admin == null || admin <= 0) continue;
            try {
                JSONArray message = MessageUtil.message(MessageUtil.text(text));
                plugin.getServer().getOneBotClient().sendPrivateMessage(admin,message);
            } catch (Exception e) {
                plugin.getLogger().sendException(e);
            }
        }
    }

    /**
     * 在群里艾特本人提醒一句
     *
     * 不私聊打扰：提醒只发在当前群，并能被本人看到即可，也避免陌生私聊造成的困扰。
     * 语气保持平和，只说明情况和后续后果，不训人；同一个用户按冷却时间最多提醒一次，
     * 避免变成新的骚扰源。关闭 noticeEnable 后完全不提醒。
     */
    private void notice(long groupID,long userID,FloodRuleEngine.Result result) {
        AntiSpamConfig current = config;
        if (!current.noticeEnable) return;
        String key = groupID+"|"+userID;
        long now = System.currentTimeMillis();
        Long last = noticeTimes.get(key);
        if (last != null && now - last < current.noticeCooldownSecond * 1000L) return;
        noticeTimes.put(key,now);
        StringBuilder builder = new StringBuilder();
        builder.append("只是提个醒：刚才的消息发得有点密（").append(result.label).append("），没事忽略就好～");
        builder.append("\n继续这样的话可能会被撤回消息");
        if (current.banAfterViolations > 0) {
            builder.append("，再严重会禁言 ").append(current.banDurationMinute).append(" 分钟");
        }
        builder.append("。");
        try {
            JSONArray message = MessageUtil.message(MessageUtil.at(userID),
                    MessageUtil.text(" "+builder.toString()));
            plugin.getServer().getOneBotClient().sendGroupMessage(groupID,message);
        } catch (Exception e) {
            plugin.getLogger().sendException(e);
        }
    }

    private void logEvent(long groupID,long userID,long messageID,FloodRuleEngine.Result result,
                          int violationCount,String action,String contentText,long now) {
        String actionText = action == null || action.isEmpty() ? "log" : action;
        storage().insert("INSERT INTO `"+EVENT_TABLE+"` "
                        + "(`groupID`,`userID`,`messageID`,`rule`,`score`,`count`,"
                        + "`violationCount`,`action`,`content`,`detail`,`eventTime`) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                groupID,userID,messageID,result.rule,result.score,result.count,
                violationCount,actionText,shortText(contentText,200),shortText(result.detail,300),now);
    }

    /**
     * 按保留天数清理历史事件
     */
    private void purgeOldEvents(long now) {
        AntiSpamConfig current = config;
        long from = now - current.retentionDays * 24L * 60L * 60L * 1000L;
        storage().update("DELETE FROM `"+EVENT_TABLE+"` WHERE `eventTime` < ?",from);
    }

    /**
     * 周期性清理：丢弃不活跃用户的窗口，并限制内存中追踪的用户数量
     */
    private void cleanupIfNeeded(long now) {
        long last = lastCleanup.get();
        if (now - last < CLEANUP_INTERVAL_MILLIS) return;
        if (!lastCleanup.compareAndSet(last,now)) return;
        AntiSpamConfig current = config;
        long idleFrom = now - Math.max(current.violationWindowSecond,
                Math.max(current.rateLongWindowSecond,current.repeatLongWindowSecond)) * 1000L;
        java.util.Iterator<Map.Entry<String,UserWindow>> iterator = windows.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String,UserWindow> entry = iterator.next();
            if (entry.getValue().lastActive < idleFrom) iterator.remove();
        }
        //指纹用户集合按时间回收：只保留仍在群级时间线里的指纹
        long fingerprintFrom = now - Math.max(current.repeatGroupLongWindowSecond,
                current.repeatLongWindowSecond) * 1000L;
        java.util.Iterator<Map.Entry<String,Set<Long>>> userIterator =
                fingerprintUsers.entrySet().iterator();
        while (userIterator.hasNext()) {
            Map.Entry<String,Set<Long>> entry = userIterator.next();
            List<Long> timeline = groupFingerprints.get(entry.getKey());
            boolean empty = entry.getValue().isEmpty();
            boolean stale = timeline == null || timeline.isEmpty()
                    || timeline.get(timeline.size() - 1) < fingerprintFrom;
            if (empty || stale) {
                userIterator.remove();
                if (stale) groupFingerprints.remove(entry.getKey());
            }
        }
        long noticeFrom = now - Math.max(current.noticeCooldownSecond,600) * 1000L;
        java.util.Iterator<Map.Entry<String,Long>> noticeIterator = noticeTimes.entrySet().iterator();
        while (noticeIterator.hasNext()) {
            if (noticeIterator.next().getValue() < noticeFrom) noticeIterator.remove();
        }
        //集体刷屏的整群提醒记录同样按冷却时间回收
        long warnFrom = now - Math.max(current.collectiveWarnCooldownSecond,600) * 1000L;
        java.util.Iterator<Map.Entry<String,Long>> warnIterator =
                collectiveWarnTimes.entrySet().iterator();
        while (warnIterator.hasNext()) {
            if (warnIterator.next().getValue() < warnFrom) warnIterator.remove();
        }
        //消息去重记录只在短时间内有意义，按时长回收，避免长期运行无限增长
        long handledFrom = now - Math.max(current.violationWindowSecond,
                Math.max(current.rateLongWindowSecond,600)) * 1000L;
        java.util.Iterator<Map.Entry<Long,Long>> handledIterator =
                handledMessages.entrySet().iterator();
        while (handledIterator.hasNext()) {
            if (handledIterator.next().getValue() < handledFrom) handledIterator.remove();
        }
        if (windows.size() <= current.maxTrackedUsers) return;
        //仍然超限时按最久未活动优先淘汰
        List<Map.Entry<String,UserWindow>> entries = new ArrayList<>(windows.entrySet());
        Collections.sort(entries,(left,right) ->
                Long.compare(left.getValue().lastActive,right.getValue().lastActive));
        int remove = windows.size() - current.maxTrackedUsers;
        for (int i = 0; i < remove && i < entries.size(); i++) {
            windows.remove(entries.get(i).getKey());
        }
    }

    /**
     * 当前群是否启用刷屏治理，没有记录时取配置默认值
     */
    public boolean isGroupEnabled(long groupID) {
        if (groupID <= 0) return false;
        JSONObject row = storage().queryOne("SELECT `enabled` FROM `"+GROUP_TABLE
                +"` WHERE `groupID`=?",groupID);
        if (row == null) return config.defaultGroupEnable;
        return row.getIntValue("enabled") > 0;
    }

    public void setGroupEnabled(long groupID,boolean enabled) {
        if (groupID <= 0) return;
        long now = System.currentTimeMillis();
        storage().insert("INSERT OR REPLACE INTO `"+GROUP_TABLE+"` "
                        + "(`groupID`,`enabled`,`updateTime`) VALUES (?,?,?)",
                groupID,enabled ? 1 : 0,now);
    }

    public JSONArray listEnabledGroups() {
        JSONArray result = new JSONArray();
        List<JSONObject> rows = storage().query("SELECT `groupID`,`updateTime` FROM `"+GROUP_TABLE
                +"` WHERE `enabled`>0 ORDER BY `groupID` ASC");
        if (rows == null) return result;
        for (JSONObject row : rows) {
            JSONObject item = new JSONObject(true);
            item.put("groupID",row.getLongValue("groupID"));
            item.put("updateTime",row.getLongValue("updateTime"));
            result.add(item);
        }
        return result;
    }

    public JSONArray recentEvents(long groupID,int limit) {
        JSONArray result = new JSONArray();
        if (groupID <= 0) return result;
        if (limit < 1) limit = 1;
        if (limit > 50) limit = 50;
        List<JSONObject> rows = storage().query("SELECT * FROM `"+EVENT_TABLE
                +"` WHERE `groupID`=? ORDER BY `eventTime` DESC,`ID` DESC LIMIT "+limit,groupID);
        if (rows == null) return result;
        for (JSONObject row : rows) {
            JSONObject item = new JSONObject(true);
            item.put("userID",row.getLongValue("userID"));
            item.put("rule",row.getString("rule"));
            item.put("score",row.getIntValue("score"));
            item.put("count",row.getIntValue("count"));
            item.put("violationCount",row.getIntValue("violationCount"));
            item.put("action",row.getString("action"));
            item.put("content",row.getString("content"));
            item.put("detail",row.getString("detail"));
            item.put("eventTime",row.getLongValue("eventTime"));
            result.add(item);
        }
        return result;
    }

    public int countEvents(long groupID) {
        if (groupID <= 0) return 0;
        JSONObject row = storage().queryOne("SELECT COUNT(*) AS `count` FROM `"+EVENT_TABLE
                +"` WHERE `groupID`=?",groupID);
        return row == null ? 0 : row.getIntValue("count");
    }

    /**
     * 清空当前群的刷屏记录，返回删除条数
     */
    public int clearEvents(long groupID) {
        if (groupID <= 0) return 0;
        return storage().update("DELETE FROM `"+EVENT_TABLE+"` WHERE `groupID`=?",groupID);
    }

    /**
     * 清空所有群窗口数据，用于 /antispam reset
     */
    public void resetWindows() {
        windows.clear();
        handledMessages.clear();
        fingerprintUsers.clear();
        groupFingerprints.clear();
        collectiveWarnTimes.clear();
        noticeTimes.clear();
    }

    public int trackedUsers() {
        return windows.size();
    }

    private boolean isBypassed(long userID) {
        AntiSpamConfig current = config;
        if (containsId(current.bypassUsers,userID)) return true;
        if (containsId(current.whitelistUsers,userID)) return true;
        if (!current.bypassAdmin) return false;
        if (containsId(joinIds(plugin.getServer().getOwnerList()),userID)) return true;
        return containsId(joinIds(plugin.getServer().getAdminList()),userID);
    }

    private String joinIds(List<Long> values) {
        if (values == null || values.isEmpty()) return "";
        StringBuilder builder = new StringBuilder();
        for (Long value : values) {
            if (builder.length() > 0) builder.append(",");
            builder.append(value);
        }
        return builder.toString();
    }

    private boolean containsId(String csv,long userID) {
        if (csv == null || csv.trim().isEmpty() || userID <= 0) return false;
        for (String item : csv.split("[,，]")) {
            String value = item.trim();
            if (value.isEmpty()) continue;
            try {
                if (Long.parseLong(value) == userID) return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private String shortText(String text,int maxChars) {
        if (text == null) return "";
        String trimmed = text.trim();
        return trimmed.length() <= maxChars ? trimmed : trimmed.substring(0,maxChars);
    }

    private String join(List<String> actions) {
        StringBuilder builder = new StringBuilder();
        for (String action : actions) {
            if (builder.length() > 0) builder.append("+");
            builder.append(action);
        }
        return builder.toString();
    }

    private StorageService storage() {
        return plugin.getServer().getStorage();
    }
}
