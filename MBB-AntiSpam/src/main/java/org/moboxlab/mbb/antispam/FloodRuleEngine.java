package org.moboxlab.mbb.antispam;

import java.util.List;
import java.util.Map;

/**
 * 刷屏判定规则引擎
 *
 * 纯函数式实现：只根据窗口快照和配置给出判定结果，不访问存储、不发送消息，
 * 便于单独测试判定逻辑。
 */
public final class FloodRuleEngine {

    /** 一次判定所需的窗口快照 */
    public static class Snapshot {
        /** 该用户在本群的全部消息时间戳，升序 */
        public long[] messageTimes = new long[0];
        /** 该用户在本群的全部艾特次数时间戳，升序 */
        public long[] mentionTimes = new long[0];
        /** 内容指纹 -> 该内容全部出现时间戳，升序 */
        public Map<String,List<Long>> fingerprints;
        /** 本条消息指向的内容指纹，空串表示没有可统计的文本 */
        public String fingerprint = "";
        /** 本条消息的纯文本长度 */
        public int contentLength = 0;
        /** 本条消息艾特的人数 */
        public int mentionCount = 0;
        /** 本条消息是否包含 @全体成员 */
        public boolean mentionAll = false;
    }

    /** 判定结果 */
    public static class Result {
        public boolean violated = false;
        public String rule = "";
        public String label = "";
        public int score = 0;
        public int count = 0;
        public int limit = 0;
        public int windowSecond = 0;
        public String detail = "";

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
                && !snapshot.fingerprint.isEmpty() && snapshot.fingerprints != null) {
            List<Long> times = snapshot.fingerprints.get(snapshot.fingerprint);
            if (times != null) {
                int shortCount = (int) countIn(config.repeatWindowSecond,times,now);
                int longCount = (int) countIn(config.repeatLongWindowSecond,times,now);
                if (shortCount > config.repeatMaxCount) {
                    best = better(best,Result.hit("repeat","复读刷屏",65,shortCount,
                            config.repeatMaxCount,config.repeatWindowSecond));
                } else if (longCount > config.repeatLongMaxCount) {
                    best = better(best,Result.hit("repeat-long","持续复读",75,longCount,
                            config.repeatLongMaxCount,config.repeatLongWindowSecond));
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
     * 以权重高者优先取结果，避免复读与限流同时命中时只报出较轻的那个
     *
     * 注意这里不能改写 candidate 自身的 score：同一个结果还会被复用，
     * 改写会污染后续比较（早期版本因此让复读规则被误判成更高权重）。
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
