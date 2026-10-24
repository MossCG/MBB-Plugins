package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.OneBot.MessageUtil;
import org.moboxlab.moboxbot.API.OneBot.OneBotClient;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 消息发送与分段
 *
 * 负责回复分段、引用、链接过滤和发送后的发言状态登记，避免这些逻辑继续堆在 RoleplayService。
 */
public class RoleplayMessageSender {
    private static final String STRONG_SPLIT_PUNCTUATION = "。！？!?…";
    private static final Pattern REPLY_URL_PATTERN = Pattern.compile(
            "(?i)\\bhttps?://\\S+|\\bwww\\.\\S+|\\b[a-z0-9-]+\\.(com|cn|net|org|io|me|xyz|top)(/\\S*)?\\b");

    private final RoleplayService service;

    public RoleplayMessageSender(RoleplayService service) {
        this.service = service;
    }

    public void sendReply(OneBotClient client,long groupID,long selfID,long userID,String reply,
                          boolean quoteRequired,boolean quoteSuggested,long quoteMessageID) {
        reply = normalizeSentencePeriods(groupID,reply);
        List<String> segments = splitReply(reply);
        if (segments.isEmpty()) return;
        if (!service.getPacingService().sleep(groupID,userID,"",segments.get(0))) return;
        RoleplayConfig config = service.config();
        boolean quote = config.quoteReplyEnable
                && (quoteRequired || quoteSuggested) && quoteMessageID > 0;
        int count = Math.min(segments.size(),config.replyBeatMaxSegments);
        for (int i = 0; i < count; i++) {
            sendSingleMessage(client,groupID,selfID,userID,segments.get(i),quote && i == 0,
                    quoteMessageID);
            if (i + 1 < count && !service.sleepQuietly(
                    service.getPacingService().beatDelayMillis(groupID,segments.get(i + 1)))) return;
        }
    }

    public String normalizeSentencePeriods(long groupID,String text) {
        return normalizeSentencePeriods(text,service.emotionService().allowSentencePeriod(groupID));
    }

    static String normalizeSentencePeriods(String text,boolean allowPeriod) {
        String value = text == null ? "" : text.replace("\r\n","\n").replace('\r','\n').trim();
        if (value.isEmpty() || allowPeriod) return value;
        return value.replace("。","\n")
                .replaceAll("[ \\t]*\\n[ \\t]*","\n")
                .replaceAll("\\n{2,}","\n")
                .trim();
    }

    public boolean sendSingleMessage(OneBotClient client,long groupID,long selfID,long userID,
                                     String text,boolean quote,long quoteMessageID) {
        if (client == null || text == null || text.trim().isEmpty()) return false;
        text = filterReplyLinks(groupID,text);
        if (text.isEmpty()) return false;
        JSONArray message = quote && quoteMessageID > 0
                ? MessageUtil.message(MessageUtil.reply(quoteMessageID),MessageUtil.text(text))
                : MessageUtil.message(MessageUtil.text(text));
        JSONObject response = client.sendGroupMessage(groupID,message);
        if (response == null || response.getIntValue("retcode") != 0) return false;
        long messageID = response.getJSONObject("data") == null
                ? 0L : response.getJSONObject("data").getLongValue("message_id");
        RoleplayConversationState state = service.state(groupID);
        state.lastBotMessageID = messageID;
        state.lastReplyUser = userID;
        state.botStreak++;
        service.recordBotMessage(groupID,selfID,text,messageID);
        service.replyFeedbackService().onBotMessage(groupID,messageID,text);
        return true;
    }

    private String filterReplyLinks(long groupID,String text) {
        String value = service.safe(text);
        if (!service.config().replyLinkFilterEnable || value.isEmpty()) return value;
        String filtered = REPLY_URL_PATTERN.matcher(value).replaceAll(" ")
                .replaceAll("\\s+"," ").trim();
        if (!filtered.equals(value)) {
            service.plugin().getLogger().sendInfo("[角色] 群"+groupID+" 已过滤模型输出链接："
                    +service.shortText(value,120));
        }
        return filtered;
    }

    public List<String> splitReply(String text) {
        return splitReply(text,service.config().replySegmentMaxChars,
                service.config().replySplitPunctuation);
    }

    static List<String> splitReply(String text,int maxChars,String punctuation) {
        List<String> result = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return result;
        if (maxChars < 1) maxChars = 1;
        String normalized = text.replace("\\n","\n").replace("\r\n","\n").replace("\r","\n");
        String[] paragraphs = normalized.split("\n");
        for (String paragraph : paragraphs) {
            String value = paragraph.replaceAll("\\s+"," ").trim();
            if (value.isEmpty()) continue;
            if (value.length() <= maxChars) result.add(value);
            else addParagraph(result,value,maxChars,punctuation);
        }
        return result;
    }

    private static void addParagraph(List<String> result,String text,int maxChars,String punctuation) {
        while (text.length() > maxChars) {
            int cut = findSplitCut(text,maxChars,true,punctuation);
            if (cut <= 0) cut = findSplitCut(text,maxChars,false,punctuation);
            if (cut <= 0) cut = maxChars;
            cut = extendStrongPunctuationRun(text,cut,punctuation);
            String segment = trimWeakSplitPunctuation(text.substring(0,cut).trim(),punctuation);
            if (!segment.isEmpty()) result.add(segment);
            text = text.substring(cut).trim();
        }
        if (!text.isEmpty()) result.add(text);
    }

    public int findSplitCut(String text,int maxChars,boolean strong) {
        return findSplitCut(text,maxChars,strong,service.config().replySplitPunctuation);
    }

    private static int findSplitCut(String text,int maxChars,boolean strong,String punctuation) {
        //在整段上限内取最后一个符合等级的标点，避免句号明明在前文却不被采用。
        int start = 1;
        for (int i = Math.min(maxChars - 1,text.length() - 1); i >= start; i--) {
            char value = text.charAt(i);
            if (strong ? isStrongSplitPunctuation(value,punctuation)
                    : isWeakSplitPunctuation(value,punctuation)) return i + 1;
        }
        return -1;
    }

    private static int extendStrongPunctuationRun(String text,int cut,String punctuation) {
        if (cut <= 0 || cut > text.length()) return cut;
        if (text.charAt(cut - 1) != '…') return cut;
        int result = cut;
        while (result < text.length() && text.charAt(result) == '…') result++;
        if (result > cut && result < text.length() && text.charAt(result) == '。') result++;
        return result;
    }

    private static boolean isSplitPunctuation(char value,String punctuation) {
        return punctuation != null && punctuation.indexOf(value) >= 0;
    }

    private static boolean isStrongSplitPunctuation(char value,String punctuation) {
        return isSplitPunctuation(value,punctuation) && STRONG_SPLIT_PUNCTUATION.indexOf(value) >= 0;
    }

    boolean isWeakSplitPunctuation(char value) {
        return isWeakSplitPunctuation(value,service.config().replySplitPunctuation);
    }

    private static boolean isWeakSplitPunctuation(char value,String punctuation) {
        return isSplitPunctuation(value,punctuation)
                && !isStrongSplitPunctuation(value,punctuation);
    }

    String trimWeakSplitPunctuation(String text) {
        return trimWeakSplitPunctuation(text,service.config().replySplitPunctuation);
    }

    private static String trimWeakSplitPunctuation(String text,String punctuation) {
        String value = text == null ? "" : text.trim();
        while (!value.isEmpty()
                && isWeakSplitPunctuation(value.charAt(value.length() - 1),punctuation)) {
            value = value.substring(0,value.length() - 1).trim();
        }
        return value;
    }
}
