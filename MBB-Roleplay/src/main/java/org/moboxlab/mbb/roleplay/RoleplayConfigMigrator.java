package org.moboxlab.mbb.roleplay;

import org.moboxlab.moboxbot.API.Plugin;
import org.moboxlab.moboxbot.API.Util.PluginConfig;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Roleplay 配置迁移与缺失项补全
 */
public class RoleplayConfigMigrator {
    private static final String CURRENT_VERSION = "3";

    private static class ConfigEntry {
        private final String key;
        private final String value;
        private final String comment;

        private ConfigEntry(String key,String value,String comment) {
            this.key = key;
            this.value = value;
            this.comment = comment;
        }
    }

    private static final List<ConfigEntry> DEFAULTS = Arrays.asList(
            new ConfigEntry("configVersion",CURRENT_VERSION,"配置结构版本，插件升级时自动维护"),
            new ConfigEntry("enable","true","是否启用角色扮演"),
            new ConfigEntry("aiProfile","default","AI 使用的模型配置名，对应 MBB-AI 的 profiles.json"),
            new ConfigEntry("memoryProfile","","记忆整理使用的模型配置名，留空则使用 aiProfile；可换成非 reasoning 模型"),
            new ConfigEntry("timeZone","Asia/Shanghai","角色理解当前时间使用的时区"),
            new ConfigEntry("replyCooldownSecond","5","同一群两次回复的最小间隔秒数"),
            new ConfigEntry("maxRepliesPerHour","180","同一群每小时最多回复次数"),
            new ConfigEntry("initialAffinity","70","角色对所有真人成员的初始好感度，0 到 100"),
            new ConfigEntry("reminderEnable","true","是否允许角色识别并创建自然语言定时提醒"),
            new ConfigEntry("reminderAiParse","true","是否优先调用 AI 识别提醒意图和时间，失败时回退到内置规则"),
            new ConfigEntry("reminderMaxDays","30","定时提醒最长可提前多少天"),
            new ConfigEntry("interestReplyChance","0.65","非直接提及消息命中兴趣关键词后的回复概率，0 到 1"),
            new ConfigEntry("conversationWindowSecond","180","最近与角色聊过后，延续对话的窗口秒数"),
            new ConfigEntry("continuationReplyChance","0.80","最近与角色聊过天后，延续对话的回复概率"),
            new ConfigEntry("otherParticipantReplyChance","0.45","其他群员接续当前话题时的回复概率"),
            new ConfigEntry("otherRoleBotReplyChance","0.50","检测到另一个角色机器人发言时，继续接话的概率"),
            new ConfigEntry("maxConsecutiveOtherRoleMessages","2","没有真人插话时，最多连续回应另一个角色机器人多少条"),
            new ConfigEntry("otherRoleBotNames",RoleplayConfig.DEFAULT_OTHER_ROLE_BOT_NAMES,
                    "识别其他角色机器人显示名的关键词，英文逗号分隔"),
            new ConfigEntry("otherRoleBotQQs","","其他角色机器人 QQ，多个用英文逗号分隔；为空时只按显示名识别"),
            new ConfigEntry("shortContextMessages","80","即时上下文消息条数"),
            new ConfigEntry("shortTermDays","3","短期记忆覆盖天数"),
            new ConfigEntry("memoryUpdateMessages","50","每累计多少条消息更新一次记忆"),
            new ConfigEntry("memoryExtractMessages","300","每次记忆整理最多读取的消息条数"),
            new ConfigEntry("memoryExtractMaxChars","16000","每次记忆整理最多送入模型的字符数，避免上下文过长"),
            new ConfigEntry("memoryExtractBatches","3","单次自动整理最多连续处理的批次，避免积压消息一次消耗过多"),
            new ConfigEntry("memoryMaxTokens","12000","记忆整理输出 Token 上限，reasoning 模型建议不低于 12000"),
            new ConfigEntry("globalMemoryEnable","true","是否启用所有群共享的永久记忆"),
            new ConfigEntry("globalMemoryLearnGroups","","允许从哪些群的上下文学习永久记忆，多个群号用英文逗号分隔；为空时暂不学习"),
            new ConfigEntry("globalMemoryMaxItems","300","最多加载多少条全局永久记忆"),
            new ConfigEntry("globalMemoryInjectItems","80","每次注入角色提示词的全局永久记忆条数"),
            new ConfigEntry("speechCorpusEnable","true","是否启用角色台词语料检索"),
            new ConfigEntry("speechCorpusDirectory","speech-corpus","台词语料目录，文件名为 speech-corpus-<角色>.jsonl"),
            new ConfigEntry("speechRetrievalCount","8","每次注入多少条参考台词"),
            new ConfigEntry("speechRetrievalMaxChars","1200","参考台词注入的最大字符数"),
            new ConfigEntry("speechRetrievalMinScore","0.35","台词检索最低分数"),
            new ConfigEntry("speechSimilarityThreshold","0.78","回复与台词语料相似度达到多少时视为照抄"),
            new ConfigEntry("speechSimilarityMinChars","6","少于多少字的回复不做照抄检测"),
            new ConfigEntry("activeMemory","true","是否允许角色通过回复末尾的 <remember> 主动触发记忆整理"),
            new ConfigEntry("maxLongMemories","150","最多加载多少条长期记忆"),
            new ConfigEntry("replyMaxTokens","1200","单次角色回复最大 Token"),
            new ConfigEntry("replySegmentMaxChars","160","单段回复最多字符数"),
            new ConfigEntry("replyMaxSegments","2","最多拆分发送多少段"),
            new ConfigEntry("recentReplyCheckCount","8","重复检测时参考最近多少条角色回复"),
            new ConfigEntry("repeatSimilarityThreshold","0.72","与最近角色回复相似度达到多少时跳过，0.3 到 1"),
            new ConfigEntry("repeatCheckMinChars","6","少于多少字的回复不进行重复检测"),
            new ConfigEntry("repeatOpeningLimit","2","同一开头在最近角色回复中出现多少次后禁止再次使用"),
            new ConfigEntry("commandPrefixes","/,!,＃,#","忽略以这些前缀开头的指令消息，英文逗号分隔"),
            new ConfigEntry("personaFile","persona-aris.json","角色设定文件：persona-aris.json（爱丽丝/Aris）/ persona-momoi.json（桃井）/ persona-midori.json（绿）"),
            new ConfigEntry("minMessageLength","2","忽略少于多少字的纯文本消息"));

    public static int ensure(Plugin plugin) {
        if (plugin == null) return 0;
        PluginConfig config = plugin.getConfig();
        File file = new File(plugin.getDataFolder(),"config.yml");
        if (!file.exists()) return 0;

        int version = config.getInt("configVersion",0);
        boolean versionMissing = !config.contains("configVersion");
        boolean legacyRoleBotChance = version < 2 && isLegacyRoleBotChance(
                config.getString("otherRoleBotReplyChance",""));
        boolean legacyPersonaFile = version < 3 && "persona.json".equals(
                config.getString("personaFile",""));
        int changed = 0;
        List<ConfigEntry> missing = new ArrayList<>();
        for (ConfigEntry entry : DEFAULTS) {
            if (!config.contains(entry.key)) missing.add(entry);
        }
        if (!missing.isEmpty()) {
            if (appendEntries(file,missing)) {
                changed += missing.size();
                config.load();
            } else {
                plugin.getLogger().sendWarn("自动补全 Roleplay 配置失败，请检查 config.yml 权限！");
            }
        }
        if (version < 3) {
            config.set("configVersion",CURRENT_VERSION);
            if (!versionMissing) changed++;
        }
        if (legacyRoleBotChance) {
            config.set("otherRoleBotReplyChance",
                    String.valueOf(RoleplayConfig.DEFAULT_OTHER_ROLE_BOT_REPLY_CHANCE));
            changed++;
        }
        if (legacyPersonaFile) {
            config.set("personaFile","persona-aris.json");
            changed++;
        }
        if (version < 3 || legacyRoleBotChance || legacyPersonaFile) {
            if (config.save()) {
                config.load();
            } else {
                plugin.getLogger().sendWarn("保存 Roleplay 配置迁移结果失败，请检查 config.yml 权限！");
            }
        }
        return changed;
    }

    private static boolean isLegacyRoleBotChance(String value) {
        if (value == null) return false;
        String text = value.trim();
        return "0.1".equals(text) || "0.10".equals(text);
    }

    private static boolean appendEntries(File file,List<ConfigEntry> entries) {
        try {
            List<String> lines = Files.exists(Paths.get(file.getAbsolutePath()))
                    ? Files.readAllLines(Paths.get(file.getAbsolutePath()),StandardCharsets.UTF_8)
                    : new ArrayList<>();
            lines.add("");
            lines.add("#以下配置由插件自动补全");
            for (ConfigEntry entry : entries) {
                lines.add("#"+entry.comment);
                lines.add(entry.key+": "+PluginConfig.formatValue(entry.value));
            }
            StringBuilder builder = new StringBuilder();
            for (String line : lines) builder.append(line).append("\n");
            Files.write(Paths.get(file.getAbsolutePath()),builder.toString().getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
