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
    private static final String CURRENT_VERSION = "23";

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
            new ConfigEntry("replyReasoningEffort","low","角色回复的思考强度：low / medium / high，留空表示不向接口发送"),
            new ConfigEntry("memoryReasoningEffort","low","记忆整理的思考强度：low / medium / high，留空表示不向接口发送"),
            new ConfigEntry("timeZone","Asia/Shanghai","角色理解当前时间使用的时区"),
            new ConfigEntry("replyCooldownSecond","5","同一群两次回复的最小间隔秒数"),
            new ConfigEntry("maxRepliesPerHour","180","同一群每小时最多回复次数"),
            new ConfigEntry("initialAffinity","65","角色对所有真人成员的初始好感度，0 到 100"),
            new ConfigEntry("initialTrust","60","角色对所有真人成员的初始信任度，0 到 100"),
            new ConfigEntry("initialGroupAdminMultiplier","1.05","群主和群管理员的初始好感倍率"),
            new ConfigEntry("initialBotAdminMultiplier","1.10","botAdmin 的初始好感倍率"),
            new ConfigEntry("initialBotOwnerMultiplier","1.15","botOwner 的初始好感倍率"),
            new ConfigEntry("relationDailyMaxDelta","2","同一用户每天好感、信任和厌烦的最大累计变化值"),
            new ConfigEntry("intimacyCloseAffinity","75","达到该好感度时，常态允许轻微亲密互动"),
            new ConfigEntry("intimacyVeryCloseAffinity","90","达到该好感度时，允许明显的撒娇和日常亲密举动"),
            new ConfigEntry("emotionEnable","true","是否启用角色情绪与用户关系机制"),
            new ConfigEntry("emotionDecayMinute","30","情绪回落间隔，每隔多少分钟向角色人格基线靠近一次"),
            new ConfigEntry("emotionEventCooldownSecond","300","同一用户同一类情绪事件的规则更新冷却秒数"),
            new ConfigEntry("emotionAnalyzeEnable","true","是否异步调用 AI 分析语义情绪原因"),
            new ConfigEntry("emotionAnalyzeMode","significant","AI 情绪分析范围：off / direct / significant"),
            new ConfigEntry("emotionAnalyzeProfile","","情绪分析使用的模型配置名，留空则使用 aiProfile"),
            new ConfigEntry("emotionAnalyzeCooldownSecond","300","同一用户两次 AI 情绪分析的最小间隔秒数"),
            new ConfigEntry("emotionAnalyzeMaxTokens","1200","情绪分析输出 Token 上限"),
            new ConfigEntry("emotionAnalyzeReasoningEffort","low","情绪分析思考强度：low / medium / high，留空表示不发送"),
            new ConfigEntry("emotionAnalyzeMaxDelta","0.5","单次 AI 情绪分析对单项数值的最大提升幅度"),
            new ConfigEntry("emotionAnalyzeMaxDecreaseDelta","0.3","单次 AI 情绪分析对单项数值的最大降低幅度，不应大于提升幅度"),
            new ConfigEntry("emotionReasonMaxChars","120","用户级情绪原因的最大字符数"),
            new ConfigEntry("emotionReasonMinStrength","40","低于该强度时不再把情绪原因注入提示词"),
            new ConfigEntry("emotionReasonDecayDays","30","情绪原因在无新证据时完全衰减的固定参考天数"),
            new ConfigEntry("emotionEventRetentionDays","90","情绪事件流水保留天数"),
            new ConfigEntry("emotionPositiveReplyBonus","0.05","心情好时非直接消息的回复概率加成"),
            new ConfigEntry("emotionNegativeReplyPenalty","0.15","心情差或耐心低时非直接消息的回复概率扣减"),
            new ConfigEntry("emotionAffinityReplyBonus","0.08","对高好感用户非直接消息的回复概率加成"),
            new ConfigEntry("emotionAffinityReplyPenalty","0.15","对低好感或高厌烦用户非直接消息的回复概率扣减"),
            new ConfigEntry("reminderEnable","true","是否允许角色识别并创建自然语言定时提醒"),
            new ConfigEntry("reminderAiParse","true","是否用 AI 识别提醒意图和时间；关闭时使用内置规则解析"),
            new ConfigEntry("reminderMaxDays","30","定时提醒最长可提前多少天"),
            new ConfigEntry("interestReplyChance","0.65","非直接提及消息命中兴趣关键词后的回复概率，0 到 1"),
            new ConfigEntry("conversationWindowSecond","180","最近与角色聊过后，延续对话的窗口秒数"),
            new ConfigEntry("continuationReplyChance","0.80","最近与角色聊过天后，延续对话的回复概率"),
            new ConfigEntry("otherParticipantReplyChance","0.45","其他群员接续当前话题时的回复概率"),
            new ConfigEntry("otherRoleBotReplyChance","0.50","检测到另一个角色机器人发言时，继续接话的概率"),
            new ConfigEntry("maxConsecutiveOtherRoleMessages","2","没有真人插话时，最多连续回应另一个角色机器人多少条"),
            new ConfigEntry("otherRoleBotNames",RoleplayConfig.DEFAULT_OTHER_ROLE_BOT_NAMES,
                    "识别文本中提到的角色名，英文逗号分隔；昵称命中不再代表对方就是该角色"),
            new ConfigEntry("otherRoleBotQQs","","其他角色机器人 QQ，多个用英文逗号分隔；只有 QQ 命中才视为角色本人"),
            new ConfigEntry("addressedOtherMemberSkip","true","消息明确艾特其他成员且没有提到角色时是否跳过"),
            new ConfigEntry("splitMessageSuppressSecond","20",
                    "同一用户刚被回复后，多少秒内的短句视为补充不再回复，0 表示关闭"),
            new ConfigEntry("splitMessageSuppressMaxChars","20","触发补充抑制的最大字数"),
            new ConfigEntry("shortContextMessages","120","即时上下文消息条数"),
            new ConfigEntry("shortTermDays","3","短期记忆覆盖天数"),
            new ConfigEntry("memoryUpdateMessages","50","每累计多少条消息更新一次记忆"),
            new ConfigEntry("memoryExtractMessages","300","每次记忆整理最多读取的消息条数"),
            new ConfigEntry("memoryExtractMaxChars","16000","每次记忆整理最多送入模型的字符数，避免上下文过长"),
            new ConfigEntry("memoryExtractBatches","3","单次自动整理最多连续处理的批次，避免积压消息一次消耗过多"),
            new ConfigEntry("memoryMaxTokens","12000","记忆整理输出 Token 上限，reasoning 模型建议不低于 12000"),
            new ConfigEntry("memoryMergeMaxTokens","32000","长期记忆合并输出 Token 上限，reasoning 模型建议不低于 32000"),
            new ConfigEntry("memoryMergeBatchSize","60","记忆合并单批条数"),
            new ConfigEntry("memoryMergeMaxRounds","3","记忆合并最多执行多少轮分批压缩"),
            new ConfigEntry("memoryTimeoutSecond","300","记忆整理单次请求超时秒数，长上下文和 reasoning 模型建议不低于 300"),
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
            new ConfigEntry("knowledgeEnable","true","是否启用可选知识库"),
            new ConfigEntry("knowledgeDirectory","knowledge","知识库目录，相对插件数据目录"),
            new ConfigEntry("knowledgeStudentsLibrary","ba.students","学生档案库的库名；该库启用后不再注入插件自带的学生详细设定，留空表示不做这个替换"),
            new ConfigEntry("knowledgeMaxEntries","4","单次最多注入几个知识库条目"),
            new ConfigEntry("knowledgeMaxSectionsPerEntry","2","每个条目最多注入几个小节"),
            new ConfigEntry("knowledgeMinScore","1.0","知识库检索最低分，低于该分数不注入"),
            new ConfigEntry("knowledgeInjectMaxChars","2000","单次知识库注入的总字符上限"),
            new ConfigEntry("knowledgeSpoilerLevel","0","允许注入的最高剧透等级：0 只注入无剧透，1 允许轻度，2 全部"),
            new ConfigEntry("activeMemory","true","是否允许角色通过回复末尾的 <remember> 主动触发记忆整理"),
            new ConfigEntry("imageUnderstandingEnable","true","是否允许角色理解群聊图片和表情包"),
            new ConfigEntry("imageUnderstandingMode","addressed","图片理解模式：off 关闭 / addressed 只处理直接提及或连续对话 / all 处理全部图片"),
            new ConfigEntry("imageUnderstandingMaxPerHour","30","每个群每小时最多识图次数，0 表示不限制"),
            new ConfigEntry("imageUnderstandingInjectOcr","true","识图结果是否把 OCR 文字注入角色上下文"),
            new ConfigEntry("imageUnderstandingProfile","","图片理解使用的模型配置名，留空则使用 MBB-Vision 默认配置"),
            new ConfigEntry("imageUnderstandingMaxChars","600","图片理解结果注入角色的最大字符数"),
            new ConfigEntry("visionReferenceMaxChars","32000","识图时学生外貌参考的总字符预算，按学生数均分"),
            new ConfigEntry("imageContextTimeoutSecond","300","用户发送图片后，后续提问可复用原图作为上下文的时间窗口秒数"),
            new ConfigEntry("imageAsyncEnable","true","纯图片与表情消息的识图放到独立异步任务，不阻塞回复线程"),
            new ConfigEntry("stickerAttachEnable","true","是否等待用户补发表情包后再生成一次回复"),
            new ConfigEntry("stickerAttachWindowSecond","5","文字回合等待同用户补发表情包的秒数"),
            new ConfigEntry("stickerAttachMaxWaitSecond","15","表情包已开始识别时，最多额外等待多少秒"),
            new ConfigEntry("stickerAttachWaitUntilNextMessage","true",
                    "同一用户又发言或表情包已附带时立刻结束等待，不再干等到窗口结束"),
            new ConfigEntry("messageBatchEnable","true","是否启用群消息合批：连续快速发言会合并成一个回合处理"),
            new ConfigEntry("messageBatchWindowSecond","2","合批窗口秒数，窗口内到达的新消息会并入同一批"),
            new ConfigEntry("messageBatchMaxMessages","10","单个批次最多合并多少条消息，超出时丢弃最旧的非直接点名消息"),
            new ConfigEntry("messageBatchMaxAgeSecond","20","合批消息最多等待多少秒，超过且没有点名时直接丢弃，0 表示不丢弃"),
            new ConfigEntry("batchMaxSegments","4","合批回合最多回复几段，每段可以回应批内不同的消息"),
            new ConfigEntry("turnThreads","4","角色回合的执行线程数，路由与生成都在这个线程池里跑"),
            new ConfigEntry("pokeReplyEnable","true","是否响应戳一戳；MBB-Poke 启用时本插件自动跳过"),
            new ConfigEntry("pokeBackEnable","true","被戳时是否允许角色戳回去"),
            new ConfigEntry("pokeBackCooldownSecond","60","对同一用户戳回去的最小间隔秒数"),
            new ConfigEntry("pokeStreakWindowSecond","60","连续戳一戳的统计窗口秒数，窗口内超过阈值才算骚扰"),
            new ConfigEntry("pokeStreakThreshold","3","窗口内戳几次开始算骚扰，达到后才会涨厌烦"),
            new ConfigEntry("quoteReplyEnable","true","回复被艾特或被直接回复的消息时是否引用原消息"),
            new ConfigEntry("promptTotalChars","26000","注入执行层的资料总字符预算，超出部分按优先级截断"),
            new ConfigEntry("routerEnable","true","是否启用 AI 路由层判断要不要回复、挂哪些技能和资料"),
            new ConfigEntry("routerProfile","","路由层使用的模型配置名，留空则使用 aiProfile"),
            new ConfigEntry("routerMaxTokens","2400","路由层输出 Token 上限，reasoning 模型建议不低于 2400"),
            new ConfigEntry("routerReasoningEffort","none","路由层思考强度：none / low / medium / high，留空表示不发送"),
            new ConfigEntry("styleEnable","true","是否在回复带 AI 味时调用风格层改写"),
            new ConfigEntry("styleMaxChars","60","回复超过多少字触发风格层"),
            new ConfigEntry("styleProfile","","风格层使用的模型配置名，留空则使用 aiProfile"),
            new ConfigEntry("styleMaxTokens","400","风格层输出 Token 上限"),
            new ConfigEntry("styleReasoningEffort","low","风格层思考强度：low / medium / high，留空表示不发送"),
            new ConfigEntry("styleProactiveEnable","false","主动发言时是否也触发风格层"),
            new ConfigEntry("maxLongMemories","150","最多加载多少条长期记忆"),
            new ConfigEntry("memoryRelevanceSort","true","长期记忆与永久记忆是否按当前对话相关性排序后注入"),
            new ConfigEntry("memoryRelevancePoolSize","600","相关性排序前先加载多少条长期记忆作为候选池"),
            new ConfigEntry("memoryRelevanceMaxChars","5000","长期记忆按相关性注入的字符上限"),
            new ConfigEntry("globalMemoryRelevanceMaxChars","4000","永久记忆按相关性注入的字符上限"),
            new ConfigEntry("replyMaxTokens","1200","单次角色回复最大 Token"),
            new ConfigEntry("replyImageMaxTokens","4000","带图片上下文时单次角色回复最大 Token"),
            new ConfigEntry("replySegmentMaxChars","20","单段回复硬上限字符数，提示词默认按 12 字以内生成"),
            new ConfigEntry("replySplitPunctuation","。！？!?；;，、：,:～~","回复拆分时优先使用的断句符号"),
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
        boolean legacyStickerWindow = version < 6 && "7".equals(
                config.getString("stickerAttachWindowSecond",""));
        boolean legacyRouterMaxTokens = version < 9 && "400".equals(
                config.getString("routerMaxTokens",""));
        boolean legacyRouterMaxTokens1200 = version < 22 && "1200".equals(
                config.getString("routerMaxTokens",""));
        boolean legacyPromptTotalChars = version < 22 && "16000".equals(
                config.getString("promptTotalChars",""));
        boolean legacyMemoryRelevanceMaxChars = version < 22 && "1800".equals(
                config.getString("memoryRelevanceMaxChars",""));
        boolean legacyGlobalMemoryRelevanceMaxChars = version < 22 && "1200".equals(
                config.getString("globalMemoryRelevanceMaxChars",""));
        boolean legacyRouterReasoningEffort = version < 23 && "low".equals(
                config.getString("routerReasoningEffort",""));
        boolean legacyEmotionAnalyzeMaxDelta08 = version < 23 && "0.8".equals(
                config.getString("emotionAnalyzeMaxDelta",""));
        boolean legacyReplySegmentMaxChars = version < 12 && "160".equals(
                config.getString("replySegmentMaxChars",""));
        boolean legacyReplySplitPunctuation = version < 13
                && "。！？!?；;，、：,:".equals(config.getString("replySplitPunctuation",""));
        boolean legacyInitialAffinity = version < 16
                && ("70".equals(config.getString("initialAffinity",""))
                || "85".equals(config.getString("initialAffinity","")));
        boolean legacyInitialTrust = version < 16
                && "70".equals(config.getString("initialTrust",""));
        boolean legacyIntimacyClose = version < 16
                && "80".equals(config.getString("intimacyCloseAffinity",""));
        boolean legacyIntimacyVeryClose = version < 16
                && "92".equals(config.getString("intimacyVeryCloseAffinity",""));
        boolean legacyRelationDailyMax = version < 17
                && "5".equals(config.getString("relationDailyMaxDelta",""));
        boolean legacyEmotionEventCooldown = version < 17
                && "30".equals(config.getString("emotionEventCooldownSecond",""));
        boolean legacyEmotionAnalyzeCooldown = version < 17
                && "60".equals(config.getString("emotionAnalyzeCooldownSecond",""));
        boolean legacyEmotionAnalyzeMaxDelta = version < 18
                && ("12".equals(config.getString("emotionAnalyzeMaxDelta",""))
                || "6".equals(config.getString("emotionAnalyzeMaxDelta","")));
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
        if (version < 23) {
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
        if (legacyStickerWindow) {
            config.set("stickerAttachWindowSecond","5");
            changed++;
        }
        if (legacyRouterMaxTokens) {
            config.set("routerMaxTokens","2400");
            changed++;
        }
        if (legacyRouterMaxTokens1200) {
            config.set("routerMaxTokens","2400");
            changed++;
        }
        if (legacyPromptTotalChars) {
            config.set("promptTotalChars","26000");
            changed++;
        }
        if (legacyMemoryRelevanceMaxChars) {
            config.set("memoryRelevanceMaxChars","5000");
            changed++;
        }
        if (legacyGlobalMemoryRelevanceMaxChars) {
            config.set("globalMemoryRelevanceMaxChars","4000");
            changed++;
        }
        if (legacyReplySegmentMaxChars) {
            config.set("replySegmentMaxChars","20");
            changed++;
        }
        if (legacyReplySplitPunctuation) {
            config.set("replySplitPunctuation","。！？!?；;，、：,:～~");
            changed++;
        }
        if (legacyInitialAffinity) {
            config.set("initialAffinity","65");
            changed++;
        }
        if (legacyInitialTrust) {
            config.set("initialTrust","60");
            changed++;
        }
        if (legacyIntimacyClose) {
            config.set("intimacyCloseAffinity","75");
            changed++;
        }
        if (legacyIntimacyVeryClose) {
            config.set("intimacyVeryCloseAffinity","90");
            changed++;
        }
        if (legacyRelationDailyMax) {
            config.set("relationDailyMaxDelta","2");
            changed++;
        }
        if (legacyEmotionEventCooldown) {
            config.set("emotionEventCooldownSecond","300");
            changed++;
        }
        if (legacyEmotionAnalyzeCooldown) {
            config.set("emotionAnalyzeCooldownSecond","300");
            changed++;
        }
        if (legacyEmotionAnalyzeMaxDelta) {
            config.set("emotionAnalyzeMaxDelta","0.5");
            changed++;
        }
        if (legacyRouterReasoningEffort) {
            config.set("routerReasoningEffort","none");
            changed++;
        }
        if (legacyEmotionAnalyzeMaxDelta08) {
            config.set("emotionAnalyzeMaxDelta","0.5");
            changed++;
        }
        if (version < 23 || legacyRoleBotChance || legacyPersonaFile || legacyStickerWindow
                || legacyRouterMaxTokens || legacyRouterMaxTokens1200
                || legacyPromptTotalChars || legacyMemoryRelevanceMaxChars
                || legacyGlobalMemoryRelevanceMaxChars || legacyRouterReasoningEffort
                || legacyEmotionAnalyzeMaxDelta08 || legacyReplySegmentMaxChars
                || legacyReplySplitPunctuation || legacyInitialAffinity
                || legacyInitialTrust || legacyIntimacyClose || legacyIntimacyVeryClose
                || legacyRelationDailyMax || legacyEmotionEventCooldown
                || legacyEmotionAnalyzeCooldown || legacyEmotionAnalyzeMaxDelta) {
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
