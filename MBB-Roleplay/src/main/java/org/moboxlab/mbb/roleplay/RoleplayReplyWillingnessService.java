package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONObject;

/**
 * 本地回复意愿门控。
 *
 * 默认 shadow 只记录概率，不改变现有回复行为；切到 enforce 后才拦低意愿消息。
 */
public class RoleplayReplyWillingnessService {
    public static class Result {
        public final double probability;
        public final double threshold;
        public final boolean allowed;
        public final String mode;
        public final String reason;
        public final int recentMessages;
        public final double recentSelfRatio;

        private Result(double probability,double threshold,boolean allowed,String mode,
                       String reason,int recentMessages,double recentSelfRatio) {
            this.probability = probability;
            this.threshold = threshold;
            this.allowed = allowed;
            this.mode = mode;
            this.reason = reason == null ? "" : reason;
            this.recentMessages = recentMessages;
            this.recentSelfRatio = recentSelfRatio;
        }
    }

    private final RoleplayService service;

    public RoleplayReplyWillingnessService(RoleplayService service) {
        this.service = service;
    }

    public Result evaluate(long groupID,RoleplayDecisionEngine.Signals signals,String content,
                           boolean mediaOnly,int batchSize) {
        RoleplayConfig config = service.config();
        String mode = normalizeMode(config.replyWillingnessMode);
        if ("off".equals(mode)) {
            return new Result(1.0,config.replyWillingnessThreshold,true,mode,"关闭",0,0.0);
        }
        RecentStats stats = recentStats(groupID,config.replyWillingnessWindowSecond);
        boolean question = isQuestion(content);
        double attention = service.attentionService().score(groupID);
        Calculation calc = calculate(config,signals,question,mediaOnly,batchSize,
                stats.total,stats.selfRatio,attention);
        boolean allowed = signals != null && signals.direct
                || calc.probability >= config.replyWillingnessThreshold;
        Result result = new Result(calc.probability,config.replyWillingnessThreshold,allowed,
                mode,calc.reason,stats.total,stats.selfRatio);
        log(groupID,signals,result);
        return result;
    }

    static Calculation calculate(RoleplayConfig config,RoleplayDecisionEngine.Signals signals,
                                 boolean question,boolean mediaOnly,int batchSize,
                                 int recentMessages,double recentSelfRatio,double attentionScore) {
        double probability = 0.52;
        StringBuilder reason = new StringBuilder();
        if (signals != null) {
            if (signals.direct) {
                probability += 0.35;
                addReason(reason,"直接点名");
            } else if (signals.mentioningSelf || signals.quotingSelf) {
                probability += 0.22;
                addReason(reason,"引用或提到角色");
            }
            if (signals.sameUserContinuation) {
                probability += 0.08;
                addReason(reason,"同一用户续聊");
            }
            if (signals.interest) {
                probability += 0.07;
                addReason(reason,"命中兴趣");
            }
            if (signals.groupActive) {
                probability += 0.05;
                addReason(reason,"群对话活跃");
            }
            if (signals.topicFatigued && !signals.direct) {
                probability -= 0.30;
                addReason(reason,"话题疲劳");
            }
            if (signals.addressedToOtherMember || signals.addressedToOtherRole) {
                probability -= 0.24;
                addReason(reason,"指向其他人");
            }
            if (signals.otherRoleBot) {
                probability -= 0.12;
                addReason(reason,"机器人互聊");
            }
            if (signals.reminderNotification) {
                probability += 0.18;
                addReason(reason,"提醒触发");
            }
            if (signals.contentLength > 0 && signals.contentLength < config.splitMessageSuppressMaxChars) {
                probability -= 0.08;
                addReason(reason,"消息较短");
            }
        }
        if (question) {
            probability += 0.06;
            addReason(reason,"包含问题");
        }
        if (mediaOnly) {
            probability -= 0.10;
            addReason(reason,"纯媒体");
        }
        if (batchSize > 1) {
            probability += Math.min(0.08,(batchSize - 1) * 0.02);
            addReason(reason,"合批");
        }
        if (recentMessages >= 8) {
            probability += 0.04;
            addReason(reason,"近期活跃");
        }
        if (recentMessages >= 5 && recentSelfRatio > 0.35) {
            probability -= Math.min(0.18,(recentSelfRatio - 0.35) * 0.45);
            addReason(reason,"角色发言偏多");
        }
        probability += (attentionScore - 0.5) * 0.12;
        if (attentionScore < config.attentionLowThreshold) addReason(reason,"注意力偏低");
        probability = clamp(probability);
        return new Calculation(probability,reason.toString());
    }

    private void log(long groupID,RoleplayDecisionEngine.Signals signals,Result result) {
        if ("off".equals(result.mode)) return;
        String verdict = result.allowed ? "通过" : ("enforce".equals(result.mode) ? "拦截" : "低意愿仅记录");
        service.plugin().getLogger().sendInfo("[角色] 群"+groupID+" 意愿"
                +" 概率="+format(result.probability)
                +" 阈值="+format(result.threshold)
                +" 模式="+result.mode
                +" 结果="+verdict
                +" 直接="+(signals != null && signals.direct ? "是" : "否")
                +" 近期="+result.recentMessages+"条"
                +" 自身占比="+format(result.recentSelfRatio)
                +" 原因="+result.reason);
        JSONObject trace = new JSONObject(true);
        trace.put("mode",result.mode);
        trace.put("probability",result.probability);
        trace.put("threshold",result.threshold);
        trace.put("allowed",result.allowed);
        trace.put("recentMessages",result.recentMessages);
        trace.put("recentSelfRatio",result.recentSelfRatio);
        trace.put("reason",result.reason);
        service.getTraceService().record(groupID,0L,0L,"willingness",trace);
    }

    private RecentStats recentStats(long groupID,int seconds) {
        if (groupID <= 0 || seconds <= 0) return new RecentStats(0,0.0);
        long since = System.currentTimeMillis() - seconds * 1000L;
        JSONObject row = service.storage().queryOne(
                "SELECT COUNT(*) AS `total`,"
                        + "SUM(CASE WHEN `isBot`=1 THEN 1 ELSE 0 END) AS `selfCount` "
                        + "FROM `"+RoleplayService.MSG_TABLE+"` WHERE `groupID`=? AND `messageTime`>=?",
                groupID,since);
        if (row == null) return new RecentStats(0,0.0);
        int total = row.getIntValue("total");
        int selfCount = row.getIntValue("selfCount");
        return new RecentStats(total,total <= 0 ? 0.0 : selfCount * 1.0 / total);
    }

    private String normalizeMode(String mode) {
        String value = mode == null ? "" : mode.trim().toLowerCase();
        if ("off".equals(value) || "enforce".equals(value)) return value;
        return "shadow";
    }

    private boolean isQuestion(String content) {
        String text = content == null ? "" : content;
        return text.contains("?") || text.contains("？")
                || text.contains("吗") || text.contains("怎么") || text.contains("为什么")
                || text.contains("什么") || text.contains("哪") || text.contains("能不能");
    }

    private static void addReason(StringBuilder reason,String value) {
        if (reason.length() > 0) reason.append("、");
        reason.append(value);
    }

    private static double clamp(double value) {
        if (value < 0.01) return 0.01;
        if (value > 0.99) return 0.99;
        return value;
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT,"%.3f",value);
    }

    static class Calculation {
        final double probability;
        final String reason;

        Calculation(double probability,String reason) {
            this.probability = probability;
            this.reason = reason;
        }
    }

    private static class RecentStats {
        final int total;
        final double selfRatio;

        RecentStats(int total,double selfRatio) {
            this.total = total;
            this.selfRatio = selfRatio;
        }
    }
}
