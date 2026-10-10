package org.moboxlab.mbb.antispam;

import java.util.List;
import java.util.Map;

/**
 * 刷屏判定规则引擎
 *
 * 纯函数式实现：只根据窗口快照和配置给出判定结果，不访问存储、不发送消息，
 * 便于单独测试判定逻辑。
 *
 * 判定取向偏保守：纯图片或表情、以及多个人一起复读同一句话，都只记录不升级处置，
 * 避免把正常玩梗误伤成刷屏。
 */
public final class FloodRuleEngine {

    /** 一次判定所需的窗口快照 */
    public static class Snapshot {
        /** 该用户在本群的全部消息时间戳，升序 */
        public long[] messageTimes = new long[0];
        /** 该用户在本群的全部艾特次数时间戳，升序 */
        public long[] mentionTimes = new long[0];
        /** 内容指纹 -> 该用户自己发过的时间戳，升序 */
        public Map<String,List<Long>> fingerprints;
        /** 内容指纹 -> 本群所有人发过的时间戳，升序，用于识别多人复读 */
        public Map<String,List<Long>> groupFingerprints;
        /** 内容指纹 -> 发过该内容的不同用户数（含自己），用于区分个人复读和多人玩梗 */
        public Map<String,Integer> fingerprintUsers;
        /** 本条消息指向的内容指纹，空串表示没有可统计的文本 */
        public String fingerprint = "";
        /** 本条消息的纯文本长度 */
        public int contentLength = 0;
        /** 本条消息艾特的人数 */
        public int mentionCount = 0;
        /** 本条消息是否包含 @全体成员 */
        public boolean mentionAll = false;
        /** 本条消息是否只有图片、表情或语音，没有实际文字 */
        public boolean mediaOnly = false;
        /** 本条消息包含的图片、表情或语音数量 */
        public int mediaCount = 0;
    }

    /** 判定结果 */
    public static class Result {
        public boolean violated = false;
        /** 命中规则编号 */
        public String rule = "";
        /** 命中规则的展示名 */
        public String label = "";
        /** 权重，多条同时命中时取最高 */
        public int score = 0;
        /** 窗口内实际次数 */
        public int count = 0;
        /** 窗口内允许的次数 */
        public int limit = 0;
        /** 统计窗口秒数，0 表示与窗口无关 */
        public int windowSecond = 0;
        /** 中文说明，写进日志与事件表 */
        public String detail = "";
        /**
         * 触发范围：personal 个人刷屏，collective 集体刷屏
         *
         * 只用于说明文案，个人名字写进 detail 后置空，由服务层补上发言人 QQ。
         */
        public String scope = "personal";
        /**
         * 只记录、不升级处置
         *
         * 用于参与人数少的玩梗复读和纯图片表情刷屏这类无害情况：事件照常入库便于
         * 管理员了解情况，但不计入累计违规，因此不会撤回也不会禁言。
         */
        public boolean forgive = false;

        public static Result none() {
            return new Result();
        }

        static Result hit(String rule,String label,int score,int count,int limit,int windowSecond) {
            Result result = new Result();
            result.violated = true;
            result.rule = rule;
            result.label = label;
            result.score = score;
            result.count = count;
            result.limit = limit;
            result.windowSecond = windowSecond;
            result.detail = label+"：窗口 "+windowSecond+" 秒内 "+count+" 次，超过上限 "+limit;
            return result;
        }
    }

    private FloodRuleEngine() {
    }

    public static Result evaluate(AntiSpamConfig config,Snapshot snapshot,long now) {
        if (config == null || snapshot == null) return Result.none();
        Result best = Result.none();

        //纯图片、表情或语音的连发：只记录，不升级处置
        if (config.mediaFloodEnable && snapshot.mediaOnly
                && snapshot.mediaCount > config.mediaFloodMaxCount) {
            Result result = Result.hit("media-flood","图片表情连发",40,
                    snapshot.mediaCount,config.mediaFloodMaxCount,0);
            result.detail = "图片表情连发：单条含 "+snapshot.mediaCount
                    +" 个图片或表情，只在控制台记录，不撤回不禁言";
            result.forgive = true;
            best = better(best,result);
        }

        if (config.longTextEnable && config.longTextMaxChars > 0
                && snapshot.contentLength > config.longTextMaxChars) {
            Result result = Result.hit("long-text","超长文本刷屏",50,
                    snapshot.contentLength,config.longTextMaxChars,0);
            result.detail = "超长文本刷屏：单条 "+snapshot.contentLength
                    +" 字符，超过上限 "+config.longTextMaxChars;
            best = better(best,result);
        }

        if (config.mentionAllEnable && snapshot.mentionAll) {
            Result result = Result.hit("mention-all","@全体成员",80,1,1,0);
            result.detail = "艾特刷屏：本条消息使用了 @全体成员";
            best = better(best,result);
        }

        if (config.mentionEnable && config.mentionMaxCount > 0) {
            int count = countIn(config.mentionWindowSecond,snapshot.mentionTimes,now)
                    + Math.max(0,snapshot.mentionCount);
            if (count > config.mentionMaxCount) {
                best = better(best,Result.hit("mention","艾特刷屏",55,count,
                        config.mentionMaxCount,config.mentionWindowSecond));
            }
        }

        if (config.repeatEnable && snapshot.fingerprint != null
                && !snapshot.fingerprint.isEmpty()) {
            String fingerprint = snapshot.fingerprint;
            List<Long> mine = snapshot.fingerprints == null
                    ? null : snapshot.fingerprints.get(fingerprint);
            List<Long> everyone = snapshot.groupFingerprints == null
                    ? null : snapshot.groupFingerprints.get(fingerprint);
            int users = distinctUsers(snapshot,fingerprint);
            //参与人数落在 [下限, 上限] 内属于几个人一起玩梗：只记录不升级处置
            boolean banter = config.repeatBanterForgive
                    && users >= config.repeatBanterMinUsers
                    && users <= config.repeatBanterMaxUsers;
            //超过上限说明是全群集体刷屏，点名说明一下
            boolean collective = users > config.repeatBanterMaxUsers;
            //个人自己的复读按"自己发过几次"判定，个人阈值更严
            if (mine != null) {
                int myShort = (int) countIn(config.repeatWindowSecond,mine,now);
                int myLong = (int) countIn(config.repeatLongWindowSecond,mine,now);
                if (myShort > config.repeatMaxCount) {
                    best = better(best,Result.hit("repeat","复读刷屏",65,myShort,
                            config.repeatMaxCount,config.repeatWindowSecond));
                } else if (myLong > config.repeatLongMaxCount) {
                    best = better(best,Result.hit("repeat-long","持续复读",75,myLong,
                            config.repeatLongMaxCount,config.repeatLongWindowSecond));
                }
            }
            //群内复读：统计本群所有人的发言，只要同一句话反复出现就算，不按发言人区分
            if (everyone != null && config.repeatGroupCount > 0) {
                int shortCount = (int) countIn(config.repeatGroupWindowSecond,everyone,now);
                int longCount = (int) countIn(config.repeatGroupLongWindowSecond,everyone,now);
                if (shortCount > config.repeatGroupCount) {
                    Result result = Result.hit("repeat-group","群内复读",60,shortCount,
                            config.repeatGroupCount,config.repeatGroupWindowSecond);
                    if (banter) {
                        result.forgive = true;
                        result.detail = "群内复读：窗口 "+config.repeatGroupWindowSecond+" 秒内同一句话出现 "
                                +shortCount+" 次，来自 "+users+" 个不同的人，按玩梗处理，只记录";
                    } else if (collective) {
                        result.scope = "collective";
                        result.detail = "群内复读：窗口 "+config.repeatGroupWindowSecond+" 秒内同一句话出现 "
                                +shortCount+" 次，来自 "+users+" 个不同的人（集体刷屏）";
                    } else {
                        result.label = "复读刷屏";
                        result.detail = "复读刷屏：窗口 "+config.repeatGroupWindowSecond+" 秒内同一句话出现 "
                                +shortCount+" 次，都来自同一个人（个人刷屏）";
                    }
                    best = better(best,result);
                } else if (longCount > config.repeatGroupLongCount) {
                    Result result = Result.hit("repeat-group-long","群内持续复读",62,longCount,
                            config.repeatGroupLongCount,config.repeatGroupLongWindowSecond);
                    if (banter) {
                        result.forgive = true;
                        result.detail = "群内持续复读：窗口 "+config.repeatGroupLongWindowSecond
                                +" 秒内同一句话出现 "+longCount+" 次，来自 "+users
                                +" 个不同的人，按玩梗处理，只记录";
                    } else if (collective) {
                        result.scope = "collective";
                        result.detail = "群内持续复读：窗口 "+config.repeatGroupLongWindowSecond
                                +" 秒内同一句话出现 "+longCount+" 次，来自 "+users
                                +" 个不同的人（集体刷屏）";
                    } else {
                        result.label = "复读刷屏";
                        result.detail = "复读刷屏：窗口 "+config.repeatGroupLongWindowSecond
                                +" 秒内同一句话出现 "+longCount+" 次，都来自同一个人（个人刷屏）";
                    }
                    best = better(best,result);
                }
            }
        }

        if (config.rateEnable) {
            int shortCount = (int) countIn(config.rateWindowSecond,snapshot.messageTimes,now);
            int longCount = (int) countIn(config.rateLongWindowSecond,snapshot.messageTimes,now);
            if (shortCount > config.rateMaxMessages) {
                best = better(best,Result.hit("rate","连发刷屏",60,shortCount,
                        config.rateMaxMessages,config.rateWindowSecond));
            } else if (longCount > config.rateLongMaxMessages) {
                best = better(best,Result.hit("rate-long","持续刷屏",70,longCount,
                        config.rateLongMaxMessages,config.rateLongWindowSecond));
            }
        }

        return best;
    }

    /**
     * 发过该内容的不同用户数，没有记录时按 1 人处理
     */
    private static int distinctUsers(Snapshot snapshot,String fingerprint) {
        if (snapshot.fingerprintUsers == null) return 1;
        Integer users = snapshot.fingerprintUsers.get(fingerprint);
        return users == null || users < 1 ? 1 : users;
    }

    /**
     * 以权重高者优先取结果，避免复读与限流同时命中时只报出较轻的那个
     */
    private static Result better(Result current,Result candidate) {
        if (candidate == null || !candidate.violated) return current;
        if (current == null || !current.violated) return candidate;
        return candidate.score > current.score ? candidate : current;
    }

    /**
     * 统计时间戳数组里落在窗口内的个数，时间戳要求升序
     */
    public static int countIn(int windowSecond,long[] times,long now) {
        if (times == null || times.length == 0 || windowSecond <= 0) return 0;
        long from = now - windowSecond * 1000L;
        int low = 0;
        int high = times.length;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (times[mid] < from) low = mid + 1;
            else high = mid;
        }
        return times.length - low;
    }

    /**
     * 统计列表里落在窗口内的个数，时间戳要求升序
     */
    public static int countIn(int windowSecond,List<Long> times,long now) {
        if (times == null || times.isEmpty() || windowSecond <= 0) return 0;
        long from = now - windowSecond * 1000L;
        int low = 0;
        int high = times.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (times.get(mid) < from) low = mid + 1;
            else high = mid;
        }
        return times.size() - low;
    }
}
