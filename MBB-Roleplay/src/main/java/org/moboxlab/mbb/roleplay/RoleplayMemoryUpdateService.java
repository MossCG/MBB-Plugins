package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.PluginService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 记忆更新与合并
 *
 * 负责短期/长期/永久记忆提取、长期记忆合并和永久记忆合并。
 * 运行态 Map 仍由 RoleplayService 持有，保证并发状态只有一个来源。
 */
public class RoleplayMemoryUpdateService {
    private static final Pattern EVENT_TIME = Pattern.compile("\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})\\]");
    private final RoleplayService service;

    public RoleplayMemoryUpdateService(RoleplayService service) {
        this.service = service;
    }
    void triggerMemory(long groupID,String reason) {
        triggerMemory(groupID,reason,"");
    }

    void triggerMemory(long groupID,String reason,String directMemory) {
        String memory = directMemory == null ? "" : directMemory.trim();
        synchronized (service.memoryUpdatingMap) {
            Boolean updating = service.memoryUpdatingMap.get(groupID);
            if (updating != null && updating) {
                service.pendingMemoryMap.put(groupID,mergeMemory(service.pendingMemoryMap.get(groupID),memory));
                return;
            }
            service.memoryUpdatingMap.put(groupID,true);
        }
        service.submitBackground(() -> runMemoryUpdate(groupID,reason,memory));
    }

    private void runMemoryUpdate(long groupID,String reason,String directMemory) {
        try {
            boolean directMemoryUsed = false;
            for (int batch = 0; batch < service.config().memoryExtractBatches; batch++) {
                RoleplayService.MemoryCursor cursor = service.memoryCursor(groupID);
                List<JSONObject> rows = service.storage().query(
                        "SELECT `ID`,`userID`,`userName`,`content`,`messageTime` FROM `"+RoleplayService.MSG_TABLE+"` "
                                + "WHERE `groupID`=? AND `userID` NOT IN "
                                + "(SELECT `userID` FROM `plugin_mbb_roleplay_blacklist` WHERE `groupID`=?) "
                                + "AND (`messageTime`>? OR (`messageTime`=? AND `ID`>?)) "
                                + "ORDER BY `ID` ASC LIMIT ?",
                        groupID,groupID,cursor.time,cursor.time,cursor.id,
                        service.config().memoryExtractMessages);
                if (rows == null) rows = new ArrayList<>();
                int queriedRows = rows.size();
                rows = service.limitMemoryRows(rows,service.config().memoryExtractMaxChars);
                if (rows.size() < queriedRows) {
                    service.plugin().getLogger().sendInfo("[记忆] 群"+groupID+" 本批输入按字符限制裁剪为 "
                            +rows.size()+" 条，原读取 "+queriedRows+" 条");
                }
                String batchMemory = batch == 0 ? directMemory : "";
                if (rows.isEmpty()) {
                    if (!directMemoryUsed && !batchMemory.isEmpty()) {
                        updateMemoryBatch(groupID,rows,service.contextToken(groupID),batchMemory);
                        directMemoryUsed = true;
                    }
                    break;
                }
                service.plugin().getLogger().sendInfo("[记忆] 群"+groupID+" 开始整理"
                        +(batch > 0 ? "下一批" : "")+"，消息 "+rows.size()+" 条，触发："+service.safe(reason));
                if (!updateMemoryBatch(groupID,rows,service.contextToken(groupID),batchMemory)) break;
                if (!batchMemory.isEmpty()) directMemoryUsed = true;
            }
        } finally {
            String pending;
            synchronized (service.memoryUpdatingMap) {
                service.memoryUpdatingMap.remove(groupID);
                pending = service.pendingMemoryMap.remove(groupID);
            }
            if (pending != null) triggerMemory(groupID,"待处理",pending);
        }
    }

    private JSONObject callMemoryAi(PluginService ai,long groupID,String source,String contextToken) {
        JSONObject parsed = callMemoryAiOnce(ai,groupID,source,contextToken,false);
        if (parsed != null) return parsed;
        String errorType = service.safe(service.memoryErrorTypeMap.get(groupID));
        if ("timeout".equals(errorType) || "network".equals(errorType) || "busy".equals(errorType)) {
            service.plugin().getLogger().sendWarn("[记忆] 群"+groupID+" 首次调用失败（"+errorType+"），不再重试");
            return null;
        }
        service.plugin().getLogger().sendWarn("[记忆] 群"+groupID+" 首次输出解析失败，使用严格 JSON 提示重试");
        return callMemoryAiOnce(ai,groupID,source,contextToken,true);
    }

    private JSONObject callMemoryAiOnce(PluginService ai,long groupID,String source,
                                        String contextToken,boolean retry) {
        JSONArray messages = new JSONArray();
        messages.add(service.message("system",memorySystemPrompt(groupID,retry)));
        messages.add(service.message("user",source));
        JSONObject params = new JSONObject(true);
        String profile = service.config().memoryProfile == null || service.config().memoryProfile.trim().isEmpty()
                ? service.config().aiProfile : service.config().memoryProfile.trim();
        int maxTokens = retry
                ? Math.min(32000,service.config().memoryMaxTokens * 2)
                : service.config().memoryMaxTokens;
        params.put("profile",profile);
        params.put("maxTokens",maxTokens);
        params.put("temperature",retry ? 0.0 : 0.2);
        params.put("reasoningEffort",service.config().memoryReasoningEffort);
        params.put("timeoutSeconds",service.config().memoryTimeoutSecond);
        params.put("retryCount",0);
        params.put("sessionId","roleplay-memory-"+groupID+"-"+contextToken+(retry ? "-retry" : ""));
        params.put("messages",messages);
        RoleplayAiMetadata.tagTemplate(params,"roleplay.memory.update",
                RoleplayMemoryUpdateService.class);
        long startTime = System.currentTimeMillis();
        JSONObject result = ai.call("chat",params);
        RoleplayAiLog.log(service.plugin().getLogger(),"记忆整理",groupID,result,System.currentTimeMillis() - startTime);
        if (result == null || !result.getBooleanValue("status")) {
            service.memoryErrorTypeMap.put(groupID,service.safe(result == null ? "empty" : result.getString("errorType")));
            service.plugin().getLogger().sendWarn("[记忆] 群"+groupID+" 整理失败："
                    +service.safe(result == null ? "" : result.getString("message")));
            return null;
        }
        String content = service.safe(result.getString("content"));
        String reasoning = service.safe(result.getString("reasoningContent"));
        String finishReason = service.safe(result.getString("finishReason"));
        JSONObject parsed = service.parseJson(content);
        if (parsed == null && !reasoning.isEmpty()) parsed = service.parseJson(reasoning);
        if (parsed == null) {
            service.memoryErrorTypeMap.put(groupID,"parse");
            service.plugin().getLogger().sendWarn("[记忆] 群"+groupID+" "
                    +(retry ? "重试" : "模型")+"没有返回合法 JSON，原始输出："
                    +service.shortText(content,240)
                    +(finishReason.isEmpty() ? "" : "，finishReason="+finishReason)
                    +(reasoning.isEmpty() ? "" : "，reasoning长度="+reasoning.length())
                    +"，profile="+profile+", maxTokens="+maxTokens);
        } else {
            service.memoryErrorTypeMap.remove(groupID);
        }
        return parsed;
    }

    private String memorySystemPrompt(long groupID,boolean retry) {
        return "你是角色扮演插件的记忆整理器。"
                +"当前角色："+service.persona().name
                +(service.persona().identity == null || service.persona().identity.trim().isEmpty()
                ? "" : "（"+service.persona().identity.trim()+"）")+"。"
                +"必须始终以"+service.persona().name+"的第一人称角色视角整理记忆，区分“角色自己做过/说过”、"
                +"“别人对角色做过/说过”和“角色对别人的印象”，不要把群友视角当成角色自己的经历。"
                +(retry ? "上一次输出无法解析。现在必须只输出一个合法 JSON 对象，"
                +"不要输出任何解释、标题、Markdown、代码块或思考过程。" : "只输出 JSON，不要 Markdown。")
                +"格式：{\"shortTerm\":\"近几天事件、群友日常、角色正在做的事\","
                +"\"context\":{\"topics\":[],\"background\":[],\"progress\":[],\"pending\":[],\"agreements\":[]},"
                +"\"longTerm\":["
                +"{\"type\":\"user_event|group_atmosphere|meme|self_action|topic\","
                +"\"subjectID\":0,\"content\":\"记忆内容\",\"importance\":1}]}。"
                +"群员的称呼、喜好、性格这类人物属性由单独的群员印象维护，不要写进 longTerm；"
                +"longTerm 里提到某个群员时只写他做过的具体事情（type 用 user_event），并带上发生时间。"
                +"整理时保留人名、组织名、术语、活动名等专有名词的全称，不要自造缩写；只有原始上下文里已经明确出现过的简称才可以继续使用。"
                +"当前时间："+service.currentTimeText()+"。"
                +"群成员较多时尽量记录更多有长期价值的群内氛围、群梗、角色行为和群员做过的事情，"
                +"longTerm 最多输出 20 条。只记录有长期价值的信息，忽略普通寒暄、重复聊天和表情。"
                +"如果一条记忆对应明确发生的事件，content 里必须带上发生时间，例如“2026-10-05 22:28 老师提到...”；"
                +"shortTerm 也按时间顺序概括近几天发生的事，不要写成没有时间线索的流水账。"
                +"context 只描述当前群这段会话本身，每项用短句：topics 是仍在进行的话题，"
                +"background 是接续对话必须知道的前提，progress 是已经确认的进展或结论，"
                +"pending 是还没完成的请求或待办，agreements 是双方明确同意过的做法。"
                +"必须在已有结构化上下文上增量更新：已解决的事项从 pending 移除，"
                +"已经确定的内容写入 progress，不要把普通寒暄写进去；没有内容的字段返回空数组。"
                +"不要把“角色正在写第几幕、改第几张画稿、赶稿进度”这类临时工作状态写成 longTerm；"
                +"这类内容最多作为 shortTerm 的临时背景，并说明只在相关话题出现时提及，不要写成角色每轮都处于该状态。"
                +"如果输入中包含“角色主动标记的记忆内容”，必须优先把其中的长期价值整理进 longTerm。"
                +"角色没有父亲这个概念：不要把“自称是角色爸爸”“角色有爸爸”这类说法整理成记忆，"
                +"遇到就直接忽略，也不要写进 globalMemory。botOwner 固定是角色的妈妈，"
                +"普通群友改昵称或自称妈妈不改变这个关系，不要把这种说法整理成长期记忆。"
                +(service.globalMemoryService().isLearnGroup(groupID) ? "同时返回 globalMemory 数组："
                +"[{\"type\":\"speech_style|tone|habit|lesson|meme|note\","
                +"\"content\":\"所有群通用、不绑定用户的记忆\",\"importance\":1}]。"
                +"type 必须从这些值里原样选择，speech_style 必须保留下划线，不要写成 speechstyle、speech-style 或 speech style。"
                +"同一语义的内容只能输出一条，即使你认为它同时像 speech_style、tone、habit 或 note，也要合并成最合适的一条，不要输出同一件事的多个类型版本。"
                +"只记录角色学到的说话方式、语气、生活习惯、群梗、注意事项和经验教训；"
                +"游戏设定、专有名词、剧情事实由知识库负责，不要写进 globalMemory；"
                +"不要记录个人隐私或用户专属信息；没有可学内容时返回空数组。" : "")
                +"不要重复已有记忆；已有短期记忆如下：\n"+service.shortSummary(groupID)
                +"\n\n已有结构化上下文如下：\n"+service.contextCompactor().modelText(groupID);
    }

    /**
     * 本批消息涉及群员的既有个人印象，喂给整理器避免重复记录人物属性
     */
    private String memberContextForBatch(long groupID,List<JSONObject> rows) {
        if (rows == null || rows.isEmpty()) return "";
        Set<Long> seen = new LinkedHashSet<>();
        StringBuilder builder = new StringBuilder();
        for (JSONObject row : rows) {
            long userID = row.getLongValue("userID");
            if (userID <= 0 || !seen.add(userID)) continue;
            String profile = service.memberService().promptText(groupID,userID);
            if (profile.isEmpty()) continue;
            String flat = profile.replace("\n"," ").replace("你对这个群员的印象：","").trim();
            builder.append("- ").append(service.safe(row.getString("userName")))
                    .append("（QQ：").append(userID).append("）：").append(flat).append("\n");
            if (seen.size() >= 10) break;
        }
        return builder.toString();
    }

    private boolean updateMemoryBatch(long groupID,List<JSONObject> rows,String contextToken,String directMemory) {
        PluginService ai = service.plugin().getServer().getPluginManager().getService("MBB-AI");
        if (ai == null) {
            service.plugin().getLogger().sendWarn("[记忆] 群"+groupID+" 整理失败：MBB-AI 未启用");
            return false;
        }
        StringBuilder source = new StringBuilder();
        if (directMemory != null && !directMemory.trim().isEmpty()) {
            source.append("角色主动标记的记忆内容：[").append(service.currentTimeText()).append("] ")
                    .append(directMemory.trim()).append("\n");
        }
        for (JSONObject row : rows) {
            source.append("[").append(service.formatMemoryTime(row.getLongValue("messageTime"))).append("] ")
                    .append(service.safe(row.getString("userName"))).append("：")
                    .append(service.safe(row.getString("content"))).append("\n");
        }
        //把本批涉及群员的既有印象一并给整理器，避免和群员个人记忆重复记录人物属性
        String memberContext = memberContextForBatch(groupID,rows);
        if (!memberContext.isEmpty()) {
            source.append("本批涉及群员的既有印象（人物属性已经在别处维护，这里不要重复记录）：\n")
                    .append(memberContext);
        }
        JSONObject parsed = callMemoryAi(ai,groupID,source.toString(),contextToken);
        if (parsed == null) {
            return false;
        }
        if (!contextToken.equals(service.contextToken(groupID))) {
            service.plugin().getLogger().sendInfo("[记忆] 群"+groupID+" 上下文已重置，放弃旧批次记忆");
            return false;
        }
        String shortTerm = service.safe(parsed.getString("shortTerm")).trim();
        int saved = 0;
        JSONArray longTerm = parsed.getJSONArray("longTerm");
        if (longTerm != null) {
            for (Object object : longTerm) {
                if (!(object instanceof JSONObject)) continue;
                if (saveLongMemory(groupID,(JSONObject) object)) saved++;
            }
        }
        int globalSaved = 0;
        JSONArray globalMemory = parsed.getJSONArray("globalMemory");
        if (globalMemory != null && service.globalMemoryService().isLearnGroup(groupID)) {
            for (Object object : globalMemory) {
                if (!(object instanceof JSONObject)) continue;
                JSONObject item = (JSONObject)object;
                if (service.globalMemoryService().save(service.safe(item.getString("type")),
                        service.safe(item.getString("content")),item.getIntValue("importance"),
                        groupID,0)) {
                    globalSaved++;
                }
            }
        }
        long lastMessageID = 0L;
        if (!rows.isEmpty()) {
            JSONObject lastRow = rows.get(rows.size() - 1);
            lastMessageID = lastRow.getLongValue("ID");
            service.saveMemoryState(groupID,shortTerm,lastRow.getLongValue("messageTime"),lastMessageID);
        }
        service.contextCompactor().saveFromModel(groupID,parsed,lastMessageID);
        service.plugin().getLogger().sendInfo("[记忆] 群"+groupID+" 整理完成：消息"+rows.size()
                +"条，长期记忆"+saved+"条，永久记忆"+globalSaved+"条，短期记忆"+shortTerm.length()+"字");
        return true;
    }

    void saveMemoryState(long groupID,String shortTerm,long lastTime,long lastID) {
        JSONObject row = service.storage().queryOne("SELECT `ID` FROM `"+RoleplayService.STATE_TABLE+"` WHERE `groupID`=?",groupID);
        long now = System.currentTimeMillis();
        String summary = shortTerm == null ? "" : shortTerm.trim();
        if (row == null) {
            service.storage().insert("INSERT INTO `"+RoleplayService.STATE_TABLE+"` "
                            + "(`groupID`,`shortSummary`,`lastMemoryTime`,`lastMemoryID`,`updateTime`) VALUES (?,?,?,?,?)",
                    groupID,summary,lastTime,lastID,now);
        } else if (summary.isEmpty()) {
            service.storage().update("UPDATE `"+RoleplayService.STATE_TABLE+"` SET `lastMemoryTime`=?,`lastMemoryID`=?,`updateTime`=? WHERE `groupID`=?",
                    lastTime,lastID,now,groupID);
        } else {
            service.storage().update("UPDATE `"+RoleplayService.STATE_TABLE+"` SET `shortSummary`=?,`lastMemoryTime`=?,`lastMemoryID`=?,`updateTime`=? WHERE `groupID`=?",
                    summary,lastTime,lastID,now,groupID);
        }
        saveGlobalMemoryState(groupID,summary,lastTime,lastID,now);
    }

    /**
     * 跨群短期记忆保存在 groupID=0 的全局状态行。
     * 原群状态行继续保留，既方便回看来源，也保证旧命令和旧数据仍能读取。
     */
    private void saveGlobalMemoryState(long sourceGroupID,String summary,long lastTime,long lastID,long now) {
        if (summary.isEmpty()) return;
        JSONObject row = service.storage().queryOne(
                "SELECT `ID` FROM `"+RoleplayService.STATE_TABLE+"` WHERE `groupID`=0");
        if (row == null) {
            service.storage().insert("INSERT INTO `"+RoleplayService.STATE_TABLE+"` "
                            + "(`groupID`,`shortSummary`,`lastMemoryTime`,`lastMemoryID`,`updateTime`) VALUES (?,?,?,?,?)",
                    0L,summary,lastTime,lastID,now);
        } else {
            service.storage().update("UPDATE `"+RoleplayService.STATE_TABLE+"` SET "
                            + "`shortSummary`=?,`lastMemoryTime`=?,`lastMemoryID`=?,`updateTime`=? WHERE `groupID`=0",
                    summary,lastTime,lastID,now);
        }
        if (sourceGroupID > 0) {
            service.plugin().getLogger().sendInfo("[记忆] 群"+sourceGroupID+" 短期记忆已同步到跨群记忆");
        }
    }

    private boolean saveLongMemory(long groupID,JSONObject json) {
        String type = service.safe(json.getString("type"));
        String content = service.safe(json.getString("content")).trim();
        if (type.isEmpty() || content.isEmpty()) return false;
        String unsafe = service.getSafetyService().unsafeMemoryReason(content);
        if (!unsafe.isEmpty()) {
            service.plugin().getLogger().sendWarn("[安全] 拒绝写入长期记忆："+unsafe
                    +" 内容="+service.shortText(content,80));
            return false;
        }
        long subjectID = json.getLongValue("subjectID");
        int importance = json.getIntValue("importance");
        long eventTime = eventTime(json.getLongValue("eventTime"),content,System.currentTimeMillis());
        if (importance < 1) importance = 1;
        if (importance > 5) importance = 5;
        JSONObject exists = service.storage().queryOne(
                "SELECT `ID` FROM `"+RoleplayService.MEMORY_TABLE+"` "
                        + "WHERE `memoryType`=? AND `subjectID`=? AND `content`=? AND `memoryStatus`='active'",
                type,subjectID,content);
        if (exists != null) {
            service.storage().update("UPDATE `"+RoleplayService.MEMORY_TABLE+"` SET `importance`=MAX(`importance`,?),`eventTime`=?,`updateTime`=? WHERE `ID`=?",
                    importance,eventTime,System.currentTimeMillis(),exists.getLongValue("ID"));
            mergeLongMemoryIfNeeded(groupID);
            return true;
        }
        service.storage().insert("INSERT INTO `"+RoleplayService.MEMORY_TABLE+"` "
                        + "(`groupID`,`scopeType`,`scopeID`,`sourceGroupID`,`sourceChannel`,`visibility`,"
                        + "`memoryType`,`subjectID`,`content`,`importance`,`eventTime`,`updateTime`) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                groupID,"global",0L,groupID,"group","shared",type,subjectID,content,
                importance,eventTime,System.currentTimeMillis());
        resolveConflicts(groupID,type,subjectID,content,eventTime);
        mergeLongMemoryIfNeeded(groupID);
        return true;
    }

    private void resolveConflicts(long groupID,String type,long subjectID,String content,long eventTime) {
        if (!service.config().memoryConflictResolveEnable) return;
        List<JSONObject> rows = service.storage().query(
                "SELECT `ID`,`content`,`eventTime` FROM `"+RoleplayService.MEMORY_TABLE+"` "
                        + "WHERE `memoryType`=? AND `subjectID`=? "
                        + "AND `memoryStatus`='active' ORDER BY `ID` DESC LIMIT 20",
                type,subjectID);
        if (rows == null || rows.isEmpty()) return;
        JSONObject newest = service.storage().queryOne(
                "SELECT MAX(`ID`) AS `id` FROM `"+RoleplayService.MEMORY_TABLE+"` "
                        + "WHERE `memoryStatus`='active'");
        long newId = newest == null ? 0L : newest.getLongValue("id");
        for (JSONObject row : rows) {
            long oldId = row.getLongValue("ID");
            if (oldId == newId) continue;
            String oldContent = service.safe(row.getString("content"));
            if (!conflicts(oldContent,content)) continue;
            service.storage().update("UPDATE `"+RoleplayService.MEMORY_TABLE+"` "
                            + "SET `memoryStatus`='archived',`supersededBy`=?,`updateTime`=? WHERE `ID`=?",
                    newId,System.currentTimeMillis(),oldId);
            service.plugin().getLogger().sendInfo("[记忆] 群"+groupID+" 冲突记忆归档 "
                    +oldId+" -> "+newId+" 类型="+type);
        }
    }

    private boolean conflicts(String oldContent,String newContent) {
        String oldText = SpeechCorpusEntry.normalize(oldContent);
        String newText = SpeechCorpusEntry.normalize(newContent);
        if (oldText.isEmpty() || newText.isEmpty()) return false;
        Set<String> oldPolarity = polarity(oldText);
        Set<String> newPolarity = polarity(newText);
        boolean opposite = false;
        for (String left : oldPolarity) {
            for (String right : newPolarity) {
                if (isOpposite(left,right)) {
                    opposite = true;
                    break;
                }
            }
        }
        if (!opposite) return false;
        Set<String> oldBigrams = SpeechCorpusEntry.bigrams(oldText);
        Set<String> newBigrams = SpeechCorpusEntry.bigrams(newText);
        int overlap = 0;
        for (String item : oldBigrams) {
            if (newBigrams.contains(item)) overlap++;
        }
        return overlap >= service.config().memoryConflictMinOverlap;
    }

    private Set<String> polarity(String text) {
        Set<String> result = new LinkedHashSet<>();
        addPolarity(result,text,"不喜欢","喜欢");
        addPolarity(result,text,"不是","是");
        addPolarity(result,text,"没有","有");
        addPolarity(result,text,"不会","会");
        addPolarity(result,text,"不能","能");
        addPolarity(result,text,"不想","想");
        return result;
    }

    private void addPolarity(Set<String> result,String text,String negative,String positive) {
        if (text.contains(negative)) result.add(negative);
        else if (text.contains(positive)) result.add(positive);
    }

    private boolean isOpposite(String left,String right) {
        if (left.equals(right)) return false;
        return ("喜欢".equals(left) && "不喜欢".equals(right))
                || ("不喜欢".equals(left) && "喜欢".equals(right))
                || ("是".equals(left) && "不是".equals(right))
                || ("不是".equals(left) && "是".equals(right))
                || ("有".equals(left) && "没有".equals(right))
                || ("没有".equals(left) && "有".equals(right))
                || ("会".equals(left) && "不会".equals(right))
                || ("不会".equals(left) && "会".equals(right))
                || ("能".equals(left) && "不能".equals(right))
                || ("不能".equals(left) && "能".equals(right))
                || ("想".equals(left) && "不想".equals(right))
                || ("不想".equals(left) && "想".equals(right));
    }

    void mergeLongMemoryIfNeeded(long groupID) {
        if (service.memoryCount(groupID) <= service.config().maxLongMemories) return;
        long cooldownMs = Math.max(0,service.config().memoryMergeCooldownMinute) * 60000L;
        long lastMerge = memoryMergeLastTime(0L);
        if (cooldownMs > 0 && lastMerge > 0
                && System.currentTimeMillis() - lastMerge < cooldownMs) {
            return;
        }
        mergeLongMemoryNow(0L);
    }

    private long memoryMergeLastTime(long groupID) {
        JSONObject row = service.storage().queryOne(
                "SELECT `lastMergeTime` FROM `"+RoleplayService.STATE_TABLE+"` WHERE `groupID`=?",groupID);
        return row == null ? 0L : row.getLongValue("lastMergeTime");
    }

    private void markMemoryMergeTriggered(long groupID) {
        long now = System.currentTimeMillis();
        JSONObject row = service.storage().queryOne(
                "SELECT `ID` FROM `"+RoleplayService.STATE_TABLE+"` WHERE `groupID`=?",groupID);
        if (row == null) {
            service.storage().insert("INSERT INTO `"+RoleplayService.STATE_TABLE+"` "
                            + "(`groupID`,`shortSummary`,`lastMemoryTime`,`lastMemoryID`,`lastMergeTime`,`updateTime`) "
                            + "VALUES (?,?,?,?,?,?)",
                    groupID,"",0L,0L,now,now);
        } else {
            service.storage().update("UPDATE `"+RoleplayService.STATE_TABLE+"` SET `lastMergeTime`=?,`updateTime`=? WHERE `groupID`=?",
                    now,now,groupID);
        }
    }

    public boolean mergeLongMemoryNow(long groupID) {
        return mergeLongMemoryNow(groupID,false);
    }

    public boolean mergeLongMemoryNow(long groupID,boolean force) {
        return mergeLongMemoryNow(groupID,force,null);
    }

    public boolean mergeLongMemoryNow(long groupID,boolean force,Consumer<String> callback) {
        synchronized (service.memoryMergingMap) {
            //长期记忆已经跨群共享，所有来源群共用一把合并锁。
            Boolean merging = service.memoryMergingMap.get(0L);
            if (merging != null && merging) return false;
            service.memoryMergingMap.put(0L,true);
        }
        markMemoryMergeTriggered(0L);
        service.submitBackground(() -> {
            String result;
            try {
                result = mergeLongMemory(groupID,force);
            } catch (Exception e) {
                result = "长期记忆合并失败："+service.safe(e.getMessage());
                service.plugin().getLogger().sendWarn("[记忆] 群"+groupID+" 长期记忆合并异常："
                        +service.safe(e.getMessage()));
            } finally {
                service.memoryMergingMap.put(0L,false);
            }
            if (callback != null) callback.accept(result);
        });
        return true;
    }

    private String mergeLongMemory(long groupID,boolean force) {
        PluginService ai = service.plugin().getServer().getPluginManager().getService("MBB-AI");
        if (ai == null) {
            service.plugin().getLogger().sendWarn("[记忆] 群"+groupID+" 长期记忆合并跳过：MBB-AI 未启用");
            return "长期记忆合并失败：MBB-AI 未启用。";
        }
        long snapshotMaxId = maxLongMemoryId(groupID);
        if (snapshotMaxId <= 0) return "当前群没有可合并的长期记忆。";
        List<JSONObject> snapshot = exportLongMemoriesUpTo(groupID,snapshotMaxId);
        if (snapshot.size() < 2) return "当前群长期记忆不足 2 条，无需合并。";
        service.backupAllMemories("long-term-merge");
        List<JSONObject> current = new ArrayList<>(snapshot);
        int original = current.size();
        int target = service.config().maxLongMemories;
        int maxRounds = service.config().memoryMergeMaxRounds;
        int batchSize = service.config().memoryMergeBatchSize;
        boolean needMerge = force;
        int executedRounds = 0;
        for (int round = 1; round <= maxRounds; round++) {
            if (!needMerge && current.size() <= target) break;
            needMerge = false;
            executedRounds++;
            List<JSONObject> sorted = MemoryBatchSorter.sortForMerge(current);
            List<List<JSONObject>> batches = MemoryBatchSorter.batches(sorted,batchSize);
            service.plugin().getLogger().sendInfo("[记忆] 群"+groupID+" 长期记忆合并 第 "+round+"/"+maxRounds
                    +" 轮开始：输入 "+current.size()+" 条，共 "+batches.size()+" 批");
            List<JSONObject> mergedAll = new ArrayList<>();
            int concurrency = Math.max(1,Math.min(4,Math.min(service.config().memoryMergeConcurrency,
                    batches.size())));
            final int currentRound = round;
            ExecutorService executor = Executors.newFixedThreadPool(concurrency);
            try {
                List<Future<List<JSONObject>>> futures = new ArrayList<>();
                for (int i = 0; i < batches.size(); i++) {
                    List<JSONObject> batch = batches.get(i);
                    final int batchNo = i + 1;
                    service.plugin().getLogger().sendInfo("[记忆] 群"+groupID+" 长期记忆合并 第 "+round
                            +" 轮 批次 "+batchNo+"/"+batches.size()+" 提交：输入 "+batch.size()
                            +" 条，并发 "+concurrency);
                    futures.add(executor.submit(() -> mergeLongMemoryBatchWithRetry(
                            ai,groupID,batch,currentRound,batchNo,batches.size())));
                }
                for (int i = 0; i < futures.size(); i++) {
                    List<JSONObject> merged = futureResult(futures.get(i));
                    if (merged == null) {
                        service.plugin().getLogger().sendWarn("[记忆] 群"+groupID+" 长期记忆合并 第 "+round
                                +" 轮 批次 "+(i+1)+" 失败，放弃本次合并，原记忆保持不变");
                        return "长期记忆合并失败，原记忆保持不变。";
                    }
                    mergedAll.addAll(merged);
                    service.plugin().getLogger().sendInfo("[记忆] 群"+groupID+" 长期记忆合并 第 "+round
                            +" 轮 批次 "+(i+1)+"/"+batches.size()+" 完成："
                            +batches.get(i).size()+" -> "+merged.size()+" 条");
                }
            } finally {
                executor.shutdownNow();
            }
            current = MemoryBatchSorter.sortForMerge(mergedAll);
            service.plugin().getLogger().sendInfo("[记忆] 群"+groupID+" 长期记忆合并 第 "+round
                    +" 轮完成：结果 "+current.size()+" 条");
        }
        if (executedRounds == 0) {
            service.plugin().getLogger().sendInfo("[记忆] 群"+groupID+" 长期记忆未超过上限，跳过合并");
            return "长期记忆未超过上限，未执行合并。";
        }
        if (!isMergeResultSafe(original,current.size())) {
            service.plugin().getLogger().sendWarn("[记忆] 群"+groupID+" 长期记忆合并结果异常：原 "
                    +original+" 条，合并后仅 "+current.size()+" 条，放弃本次合并");
            return "长期记忆合并结果异常，已放弃，原记忆保持不变。";
        }
        int newRows = Math.max(0,service.memoryCount(groupID) - original);
        deleteLongMemoriesUpTo(groupID,snapshotMaxId);
        int saved = insertLongMemories(groupID,current);
        service.plugin().getLogger().sendInfo("[记忆] 群"+groupID+" 长期记忆整理合并完成：原 "+original
                +" 条，合并后 "+saved+" 条，合并期间新增保留 "+newRows+" 条");
        return "长期记忆整理合并完成：原 "+original+" 条，合并后 "+saved
                +" 条，合并期间新增保留 "+newRows+" 条。";
    }

    private List<JSONObject> futureResult(Future<List<JSONObject>> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            service.plugin().getLogger().sendWarn("[记忆] 长期记忆合并批次异常："
                    +service.safe(e.getCause() == null ? e.getMessage() : e.getCause().getMessage()));
            return null;
        }
    }

    private List<JSONObject> mergeLongMemoryBatchWithRetry(PluginService ai,long groupID,
                                                           List<JSONObject> batch,int round,
                                                           int batchNo,int batchCount) {
        int retries = Math.max(0,Math.min(5,service.config().memoryMergeRetryCount));
        for (int attempt = 0; attempt <= retries; attempt++) {
            List<JSONObject> merged = mergeLongMemoryBatch(ai,groupID,batch,round,batchNo,batchCount);
            if (merged != null) return merged;
            if (attempt >= retries) break;
            service.plugin().getLogger().sendWarn("[记忆] 群"+groupID+" 长期记忆合并 第 "+round
                    +" 轮 批次 "+batchNo+"/"+batchCount+" 第 "+(attempt+1)+" 次失败，"
                    +"准备重试（"+(attempt+2)+"/"+(retries+1)+"）");
            if (!service.sleepQuietly(2000L * (attempt + 1))) return null;
        }
        return null;
    }

    private long maxLongMemoryId(long groupID) {
        JSONObject row = service.storage().queryOne(
                "SELECT MAX(`ID`) AS `maxID` FROM `"+RoleplayService.MEMORY_TABLE+"` "
                        + "WHERE `memoryStatus`='active' AND `visibility`='shared'");
        return row == null ? 0L : row.getLongValue("maxID");
    }

    private List<JSONObject> exportLongMemoriesUpTo(long groupID,long maxId) {
        List<JSONObject> result = new ArrayList<>();
        List<JSONObject> rows = service.storage().query(
                "SELECT `ID`,`memoryType`,`subjectID`,`content`,`importance`,`eventTime`,"
                        + "`sourceGroupID`,`updateTime` "
                        + "FROM `"+RoleplayService.MEMORY_TABLE+"` "
                        + "WHERE `ID`<=? AND `memoryStatus`='active' AND `visibility`='shared' "
                        + "ORDER BY `ID` ASC",
                maxId);
        if (rows == null) return result;
        for (JSONObject row : rows) {
            JSONObject item = new JSONObject(true);
            item.put("id",row.getLongValue("ID"));
            item.put("type",row.getString("memoryType"));
            item.put("subjectID",row.getLongValue("subjectID"));
            item.put("content",row.getString("content"));
            item.put("importance",row.getIntValue("importance"));
            item.put("eventTime",row.getLongValue("eventTime"));
            item.put("sourceGroupID",row.getLongValue("sourceGroupID"));
            item.put("updateTime",row.getLongValue("updateTime"));
            result.add(item);
        }
        return result;
    }

    private void deleteLongMemoriesUpTo(long groupID,long maxId) {
        service.storage().update("DELETE FROM `"+RoleplayService.MEMORY_TABLE+"` "
                + "WHERE `ID`<=? AND `visibility`='shared'",maxId);
    }

    private int insertLongMemories(long groupID,List<JSONObject> memories) {
        if (memories == null) return 0;
        int saved = 0;
        long now = System.currentTimeMillis();
        for (JSONObject item : memories) {
            if (item == null) continue;
            String type = service.safe(item.getString("type")).trim();
            String content = service.safe(item.getString("content")).trim();
            if (content.isEmpty()) continue;
            if (type.isEmpty()) type = "topic";
            int importance = item.getIntValue("importance");
            if (importance < 1) importance = 1;
            if (importance > 5) importance = 5;
            long updateTime = item.getLongValue("updateTime");
            if (updateTime <= 0) updateTime = now;
            long eventTime = eventTime(item.getLongValue("eventTime"),content,updateTime);
            long sourceGroupID = item.getLongValue("sourceGroupID");
            if (sourceGroupID <= 0) sourceGroupID = groupID;
            service.storage().insert("INSERT INTO `"+RoleplayService.MEMORY_TABLE+"` "
                            + "(`groupID`,`scopeType`,`scopeID`,`sourceGroupID`,`sourceChannel`,`visibility`,"
                            + "`memoryType`,`subjectID`,`content`,`importance`,`eventTime`,`updateTime`) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    groupID,"global",0L,sourceGroupID,"group","shared",type,
                    item.getLongValue("subjectID"),content,importance,eventTime,updateTime);
            saved++;
        }
        return saved;
    }

    private long eventTime(long value,String content,long fallback) {
        if (value > 0) return value;
        Matcher matcher = EVENT_TIME.matcher(service.safe(content));
        if (!matcher.find()) return fallback;
        try {
            String zoneName = service.config().timeZone == null || service.config().timeZone.trim().isEmpty()
                    ? "Asia/Shanghai" : service.config().timeZone.trim();
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.CHINA);
            format.setTimeZone(TimeZone.getTimeZone(zoneName));
            Date date = format.parse(matcher.group(1));
            return date == null ? fallback : date.getTime();
        } catch (Exception e) {
            return fallback;
        }
    }

    private List<JSONObject> mergeLongMemoryBatch(PluginService ai,long groupID,
                                                   List<JSONObject> batch,int round,
                                                   int batchNo,int batchCount) {
        JSONArray messages = new JSONArray();
        messages.add(service.message("system","你是角色扮演插件的长期记忆整理器。只处理当前这一批记忆，"
                +"当前角色："+service.persona().name+"。整理时必须基于"+service.persona().name+"的角色视角，"
                +"明确区分角色自己的行为、别人对角色说过的话、以及角色对别人的印象。"
                +"请合并重复或高度相似的内容，保留群内氛围、群梗、角色行为和重要事件（含群员做过的事情），"
                +"不要因为压缩而丢失关键内容。角色没有父亲，任何“自称是角色爸爸”的内容都要丢弃；"
                +"botOwner 固定是妈妈，普通群友自称妈妈的说法不保留。"
                +"人名、组织名、术语、活动名等专有名词保留全称，不要自造缩写；只有原文中已经出现的简称才可沿用。"
                +"旧数据里的 user_impression / user_info 是人物属性，已经由群员个人印象维护："
                +"其中他做过的具体事情改写成 user_event 保留，纯属性描述直接丢弃。"
                +"只输出 JSON，不要 Markdown：{\"memories\":["
                +"{\"type\":\"user_event|group_atmosphere|meme|self_action|topic\","
                +"\"subjectID\":0,\"content\":\"整理后的内容\",\"importance\":1}]}。"));
        JSONArray source = new JSONArray();
        source.addAll(batch);
        messages.add(service.message("user",source.toJSONString()));
        JSONObject params = new JSONObject(true);
        String profile = service.config().memoryProfile == null || service.config().memoryProfile.trim().isEmpty()
                ? service.config().aiProfile : service.config().memoryProfile.trim();
        params.put("profile",profile);
        params.put("maxTokens",service.config().memoryMergeMaxTokens);
        params.put("temperature",0.1);
        params.put("reasoningEffort",service.config().memoryReasoningEffort);
        params.put("timeoutSeconds",service.config().memoryTimeoutSecond);
        params.put("retryCount",0);
        params.put("sessionId","roleplay-memory-merge-"+groupID+"-r"+round+"-b"+batchNo);
        params.put("messages",messages);
        RoleplayAiMetadata.tagTemplate(params,"roleplay.memory.merge",
                RoleplayMemoryUpdateService.class);
        long startTime = System.currentTimeMillis();
        JSONObject result = ai.call("chat",params);
        RoleplayAiLog.log(service.plugin().getLogger(),"长期记忆合并 第"+round+"轮 批次"+batchNo+"/"+batchCount,
                groupID,result,System.currentTimeMillis() - startTime);
        if (result == null || !result.getBooleanValue("status")) {
            service.plugin().getLogger().sendWarn("[记忆] 群"+groupID+" 合并批次失败："
                    +service.safe(result == null ? "" : result.getString("message")));
            return null;
        }
        if ("length".equalsIgnoreCase(service.safe(result.getString("finishReason")))) {
            service.plugin().getLogger().sendWarn("[记忆] 群"+groupID+" 合并批次输出被截断");
            return null;
        }
        JSONObject parsed = service.parseJson(result.getString("content"));
        if (parsed == null) parsed = service.parseJson(result.getString("reasoningContent"));
        JSONArray merged = parsed == null ? null : parsed.getJSONArray("memories");
        if (merged == null || merged.isEmpty()) {
            service.plugin().getLogger().sendWarn("[记忆] 群"+groupID+" 合并批次没有返回有效 memories");
            return null;
        }
        List<JSONObject> resultList = new ArrayList<>();
        long now = System.currentTimeMillis();
        long sourceGroupID = 0L;
        for (JSONObject sourceItem : batch) {
            if (sourceItem != null && sourceItem.getLongValue("sourceGroupID") > 0) {
                sourceGroupID = sourceItem.getLongValue("sourceGroupID");
                break;
            }
        }
        for (Object object : merged) {
            if (!(object instanceof JSONObject)) continue;
            JSONObject item = (JSONObject) object;
            String content = service.safe(item.getString("content")).trim();
            if (content.isEmpty()) continue;
            String type = service.safe(item.getString("type")).trim();
            if (type.isEmpty()) type = "topic";
            int importance = item.getIntValue("importance");
            if (importance < 1) importance = 1;
            if (importance > 5) importance = 5;
            JSONObject memory = new JSONObject(true);
            memory.put("type",type);
            memory.put("subjectID",item.getLongValue("subjectID"));
            memory.put("content",content);
            memory.put("importance",importance);
            memory.put("sourceGroupID",sourceGroupID);
            memory.put("updateTime",item.getLongValue("updateTime") <= 0
                    ? now : item.getLongValue("updateTime"));
            resultList.add(memory);
        }
        return resultList.isEmpty() ? null : resultList;
    }

    private boolean isMergeResultSafe(int original,int merged) {
        if (merged <= 0) return false;
        if (original >= 20 && merged < Math.max(2,original / 10)) return false;
        return true;
    }
    private String mergeMemory(String first,String second) {
        String left = first == null ? "" : first.trim();
        String right = second == null ? "" : second.trim();
        if (left.isEmpty()) return right;
        if (right.isEmpty()) return left;
        return left+"\n"+right;
    }
}
