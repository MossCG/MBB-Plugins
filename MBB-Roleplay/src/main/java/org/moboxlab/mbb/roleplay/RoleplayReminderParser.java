package org.moboxlab.mbb.roleplay;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自然语言提醒解析
 */
public class RoleplayReminderParser {
    private static final Pattern RELATIVE_PATTERN = Pattern.compile(
            "(?<!\\d)(\\d+)\\s*(分钟|个小时|小时|天|周)后");
    private static final Pattern DATE_TIME_PATTERN = Pattern.compile(
            "(今天|明天|后天|今晚|明晚)?\\s*(凌晨|早上|上午|中午|下午|傍晚|晚上)?\\s*"
                    +"([0-9一二三四五六七八九十两]+)\\s*(?:点|时)"
                    +"(半|整|一刻|三刻|([0-9一二三四五六七八九十]+)\\s*分)?");
    private static final Pattern COLON_PATTERN = Pattern.compile(
            "(今天|明天|后天|今晚|明晚)?\\s*(凌晨|早上|上午|中午|下午|傍晚|晚上)?\\s*"
                    +"([0-9]{1,2})\\s*[:：]\\s*([0-9]{1,2})");

    public static class Result {
        public boolean intent;
        public boolean valid;
        public long remindTime;
        public String task = "";
        public String error = "";
        public String source = "规则";

        public Result(boolean intent,boolean valid) {
            this.intent = intent;
            this.valid = valid;
        }
    }

    public static Result parse(String text,RoleplayConfig config,long nowMillis) {
        Result result = new Result(false,false);
        String content = cleanText(text);
        if (content.isEmpty()) return result;
        boolean intent = content.contains("提醒") || content.contains("叫我");
        if (!intent) return result;
        result.intent = true;

        TimeZone zone = resolveTimeZone(config == null ? null : config.timeZone);
        Matcher relative = RELATIVE_PATTERN.matcher(content);
        if (relative.find()) {
            long value = parseNumber(relative.group(1));
            String unit = relative.group(2);
            long seconds = value * unitSeconds(unit);
            if (value <= 0 || seconds <= 0) {
                result.error = "时间格式不正确。";
                return result;
            }
            result.remindTime = nowMillis + seconds * 1000L;
            result.task = cleanTask(content,relative.end(),relative.group(0));
            return finish(result,config,nowMillis);
        }

        Matcher dateTime = DATE_TIME_PATTERN.matcher(content);
        if (dateTime.find()) {
            return parseDateTime(result,config,nowMillis,zone,dateTime,
                    dateTime.group(1),dateTime.group(2),dateTime.group(3),
                    dateTime.group(4),dateTime.group(5),dateTime.end());
        }

        Matcher colon = COLON_PATTERN.matcher(content);
        if (colon.find()) {
            return parseDateTime(result,config,nowMillis,zone,colon,
                    colon.group(1),colon.group(2),colon.group(3),
                    colon.group(4),null,colon.end());
        }

        result.error = "没有识别出具体时间，可以试试“下午三点提醒我干活”。";
        return result;
    }

    private static Result parseDateTime(Result result,RoleplayConfig config,long nowMillis,TimeZone zone,
                                        Matcher matcher,String dateWord,String period,String hourText,
                                        String minuteText,String minuteNumber,int end) {
        int hour = parseNumber(hourText);
        int minute = parseMinute(minuteText,minuteNumber);
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            result.error = "时间格式不正确。";
            return result;
        }
        Calendar calendar = Calendar.getInstance(zone,Locale.CHINA);
        calendar.setTimeInMillis(nowMillis);
        int dayOffset = 0;
        if ("明天".equals(dateWord) || "明晚".equals(dateWord)) dayOffset = 1;
        else if ("后天".equals(dateWord)) dayOffset = 2;
        if ("今晚".equals(dateWord) || "明晚".equals(dateWord)) period = "晚上";

        hour = adjustHour(hour,period);
        if ("晚上".equals(period) && hour == 0 && "今晚".equals(dateWord)) dayOffset = 1;
        calendar.add(Calendar.DAY_OF_MONTH,dayOffset);
        calendar.set(Calendar.HOUR_OF_DAY,hour);
        calendar.set(Calendar.MINUTE,minute);
        calendar.set(Calendar.SECOND,0);
        calendar.set(Calendar.MILLISECOND,0);
        if (dateWord == null && calendar.getTimeInMillis() <= nowMillis + 1000L) {
            calendar.add(Calendar.DAY_OF_MONTH,1);
        }
        result.remindTime = calendar.getTimeInMillis();
        result.task = cleanTask(matcher.group(0),end,matcher.group(0));
        return finish(result,config,nowMillis);
    }

    private static Result finish(Result result,RoleplayConfig config,long nowMillis) {
        long maxDays = config == null ? 30L : config.reminderMaxDays;
        if (maxDays < 1) maxDays = 30L;
        long maxMillis = nowMillis + maxDays * 86400000L;
        if (result.remindTime <= nowMillis) {
            result.error = "这个时间已经过去了。";
            return result;
        }
        if (result.remindTime > maxMillis) {
            result.error = "提醒时间太远了，最多支持 "+maxDays+" 天。";
            return result;
        }
        if (result.task == null || result.task.trim().isEmpty()) result.task = "做这件事";
        result.valid = true;
        return result;
    }

    private static String cleanTask(String content,int timeEnd,String timeText) {
        String task = content.substring(Math.min(timeEnd,content.length())).trim();
        task = task.replaceFirst("^[@＠]\\s*\\d+\\s*","").trim();
        task = task.replaceFirst("^(提醒一下我|提醒我|提醒|叫我|让我)\\s*","").trim();
        if (task.isEmpty()) {
            int index = firstKeyword(content);
            if (index >= 0) {
                task = content.substring(index).replace(timeText,"").trim();
                task = task.replaceFirst("^(提醒一下我|提醒我|提醒|叫我|让我)\\s*","").trim();
            }
        }
        task = task.replaceFirst("^(该|要|去)\\s*","").trim();
        task = task.replaceAll("[。！？!?，,；;\\s]+$","").trim();
        if (task.length() > 80) task = task.substring(0,80)+"...";
        return task;
    }

    private static int firstKeyword(String text) {
        int index = text.indexOf("提醒一下我");
        if (index >= 0) return index + "提醒一下我".length();
        index = text.indexOf("提醒我");
        if (index >= 0) return index + "提醒我".length();
        index = text.indexOf("提醒");
        if (index >= 0) return index + "提醒".length();
        index = text.indexOf("叫我");
        if (index >= 0) return index + "叫我".length();
        return -1;
    }

    private static String cleanText(String text) {
        if (text == null) return "";
        String value = text.trim();
        value = value.replaceFirst("^[@＠]\\s*\\d+\\s*","").trim();
        return value;
    }

    private static TimeZone resolveTimeZone(String name) {
        String value = name == null || name.trim().isEmpty() ? "Asia/Shanghai" : name.trim();
        TimeZone zone = TimeZone.getTimeZone(value);
        if ("GMT".equals(zone.getID()) && !"GMT".equalsIgnoreCase(value)) {
            return TimeZone.getTimeZone("Asia/Shanghai");
        }
        return zone;
    }

    private static int parseMinute(String token,String numberText) {
        if (token == null || token.trim().isEmpty() || "整".equals(token.trim())) return 0;
        String value = token.trim();
        if ("半".equals(value)) return 30;
        if ("一刻".equals(value)) return 15;
        if ("三刻".equals(value)) return 45;
        return parseNumber(numberText == null ? value : numberText);
    }

    private static int adjustHour(int hour,String period) {
        if (period == null) return hour;
        if ("凌晨".equals(period)) {
            return hour == 12 ? 0 : hour;
        }
        if ("早上".equals(period) || "上午".equals(period)) return hour;
        if ("中午".equals(period)) {
            if (hour >= 1 && hour <= 5) return hour + 12;
            return hour;
        }
        if ("下午".equals(period) || "傍晚".equals(period) || "晚上".equals(period)) {
            if ("晚上".equals(period) && hour == 12) return 0;
            return hour < 12 ? hour + 12 : hour;
        }
        return hour;
    }

    private static int parseNumber(String text) {
        if (text == null || text.trim().isEmpty()) return -1;
        String value = text.trim();
        if (value.matches("\\d+")) {
            try {
                return Integer.parseInt(value);
            } catch (Exception e) {
                return -1;
            }
        }
        int ten = value.indexOf('十');
        if (ten >= 0) {
            int left = ten == 0 ? 1 : singleNumber(value.substring(0,ten));
            int right = ten == value.length() - 1 ? 0 : singleNumber(value.substring(ten + 1));
            if (left < 0 || right < 0) return -1;
            return left * 10 + right;
        }
        return singleNumber(value);
    }

    private static int singleNumber(String text) {
        if (text == null || text.length() != 1) return -1;
        char c = text.charAt(0);
        if (c >= '0' && c <= '9') return c - '0';
        if (c == '〇') return 0;
        if (c == '两') return 2;
        String value = "零一二三四五六七八九";
        return value.indexOf(c);
    }

    private static long unitSeconds(String unit) {
        if ("分钟".equals(unit)) return 60L;
        if ("个小时".equals(unit) || "小时".equals(unit)) return 3600L;
        if ("天".equals(unit)) return 86400L;
        if ("周".equals(unit)) return 604800L;
        return 0L;
    }
}
