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
    private static final String CURRENT_VERSION = "50";
    private static final String LEGACY_WEB_WHITELIST =
            "wikipedia.org,baike.baidu.com,bilibili.com,gamekee.com,bluearchive.wiki,"
                    +"kivo.wiki,moegirl.org.cn,github.com,raw.githubusercontent.com,deepwiki.com,"
                    +"reddit.com,duckduckgo.com,bing.com,zhihu.com,weibo.com,fandom.com,namu.wiki,"
                    +"arca.live,pixiv.net,x.com,youtube.com,b23.tv";

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
            new ConfigEntry("memberMemoryEnable","true","是否启用群员个人记忆：记录角色对每个群员的称呼、喜好与相处方式"),
            new ConfigEntry("memberFieldMaxChars","120","群员个人记忆单项的最大字符数"),
            new ConfigEntry("emotionDecayMinute","30","情绪回落间隔，每隔多少分钟向角色人格基线靠近一次"),
            new ConfigEntry("emotionEventCooldownSecond","300","同一用户同一类情绪事件的规则更新冷却秒数"),
            new ConfigEntry("emotionAnalyzeEnable","true","是否异步调用 AI 分析语义情绪原因"),
            new ConfigEntry("emotionAnalyzeMode","significant","AI 情绪分析范围：off / direct / significant"),
            new ConfigEntry("emotionAnalyzeProfile","","情绪分析使用的模型配置名，留空则使用 aiProfile"),
            new ConfigEntry("emotionAnalyzeCooldownSecond","300","同一用户两次 AI 情绪分析的最小间隔秒数"),
            new ConfigEntry("emotionAnalyzeMaxTokens","2400","情绪分析输出 Token 上限，reasoning 模型需要留出思考预算"),
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
            new ConfigEntry("replyWillingnessMode","shadow",
                    "本地回复意愿门控模式：off / shadow / enforce"),
            new ConfigEntry("replyWillingnessThreshold","0.35","本地回复意愿低于该值时视为低意愿"),
            new ConfigEntry("replyWillingnessWindowSecond","300","计算近期群活跃度和自身发言占比的时间窗口"),
            new ConfigEntry("otherRoleBotReplyChance","0.50","检测到另一个角色机器人发言时，继续接话的概率"),
            new ConfigEntry("maxConsecutiveOtherRoleMessages","2","没有真人插话时，最多连续回应另一个角色机器人多少条"),
            new ConfigEntry("otherRoleBotNames",RoleplayConfig.DEFAULT_OTHER_ROLE_BOT_NAMES,
                    "其他角色机器人名称，多个用英文逗号分隔；留空时只按 QQ 识别"),
            new ConfigEntry("otherRoleBotQQs","","其他角色机器人 QQ，多个用英文逗号分隔；只有 QQ 命中才视为角色本人"),
            new ConfigEntry("addressedOtherMemberSkip","true","消息明确艾特其他成员且没有提到角色时是否跳过"),
            new ConfigEntry("splitMessageSuppressSecond","20",
                    "同一用户刚被回复后，多少秒内的短句视为补充不再回复，0 表示关闭"),
            new ConfigEntry("splitMessageSuppressMaxChars","20","触发补充抑制的最大字数"),
            new ConfigEntry("shortContextMessages","200","即时上下文消息条数"),
            new ConfigEntry("shortContextMaxChars","12000","即时上下文最多注入字符数"),
            new ConfigEntry("structuredContextEnable","true","是否压缩并注入群级结构化上下文"),
            new ConfigEntry("structuredContextMaxChars","1800","结构化上下文最多注入字符数"),
            new ConfigEntry("structuredContextItemMaxChars","160","结构化上下文单条最多字符数"),
            new ConfigEntry("shortTermDays","3","短期记忆覆盖天数"),
            new ConfigEntry("memoryUpdateMessages","50","每累计多少条消息更新一次记忆"),
            new ConfigEntry("memoryExtractMessages","300","每次记忆整理最多读取的消息条数"),
            new ConfigEntry("memoryExtractMaxChars","16000","每次记忆整理最多送入模型的字符数，避免上下文过长"),
            new ConfigEntry("memoryExtractBatches","3","单次自动整理最多连续处理的批次，避免积压消息一次消耗过多"),
            new ConfigEntry("memoryMaxTokens","12000","记忆整理输出 Token 上限，reasoning 模型建议不低于 12000"),
            new ConfigEntry("memoryMergeMaxTokens","32000","长期记忆合并输出 Token 上限，reasoning 模型建议不低于 32000"),
            new ConfigEntry("memoryMergeBatchSize","100","记忆合并单批条数"),
            new ConfigEntry("memoryMergeMaxRounds","3","记忆合并最多执行多少轮分批压缩"),
            new ConfigEntry("memoryMergeRetryCount","3","记忆合并单批失败后的最大重试次数，0 表示不重试"),
            new ConfigEntry("memoryMergeConcurrency","4","记忆合并最多同时处理多少批，范围 1 到 4"),
            new ConfigEntry("memoryMergeCooldownMinute","60","单个群两次自动长期记忆合并之间的冷却分钟数，0 表示不限制"),
            new ConfigEntry("globalMemoryMergeCooldownMinute","60","两次自动永久记忆合并之间的冷却分钟数，0 表示不限制"),
            new ConfigEntry("memoryTimeoutSecond","300","记忆整理单次请求超时秒数，长上下文和 reasoning 模型建议不低于 300"),
            new ConfigEntry("globalMemoryEnable","true","是否启用所有群共享的永久记忆"),
            new ConfigEntry("globalMemoryLearnGroups","","允许从哪些群的上下文学习永久记忆，多个群号用英文逗号分隔；为空时暂不学习"),
            new ConfigEntry("globalMemoryMaxItems","300","最多加载多少条全局永久记忆"),
            new ConfigEntry("globalMemoryInjectItems","80","每次注入角色提示词的全局永久记忆条数"),
            new ConfigEntry("speechCorpusEnable","true","是否启用角色台词语料检索"),
            new ConfigEntry("speechCorpusDirectory","speech-corpus","台词语料目录，文件名为 speech-corpus-<角色>.jsonl"),
            new ConfigEntry("speechRetrievalCount","2","每次注入多少条参考台词"),
            new ConfigEntry("speechRetrievalMaxChars","1200","参考台词注入的最大字符数"),
            new ConfigEntry("speechRetrievalMinScore","0.35","台词检索最低分数"),
            new ConfigEntry("speechSpoilerLevel","1","允许检索的台词剧透等级：0 只取无剧透，1 允许轻度，2 全部"),
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
            new ConfigEntry("hubEnable","true","是否与 MBB-BotHub 协作；关闭后完全退回单机行为"),
            new ConfigEntry("hubVisionShareEnable","true","是否共享识图结果，避免同群多个 bot 重复识图"),
            new ConfigEntry("hubVisionWaitMs","15000","等待其他 bot 识图结果的毫秒数，超时回退本地识图"),
            new ConfigEntry("hubTurnShareEnable","true","是否共享回合权，避免同群多个 bot 抢答同一条消息"),
            new ConfigEntry("hubTaskDedupEnable","true","是否跨进程去重任务创建（提醒等），避免两个 bot 建同一个任务"),
            new ConfigEntry("hubChatGuardEnable","true","是否用 hub 做机器人互聊熔断，避免两个 bot 旁若无人地刷屏"),
            new ConfigEntry("hubTypingBroadcastEnable","true","是否通过 hub 广播打字状态，让其他角色知道自己在生成"),
            new ConfigEntry("stickerAttachEnable","true","是否等待用户补发表情包后再生成一次回复"),
            new ConfigEntry("stickerAttachWindowSecond","5","文字回合等待同用户补发表情包的秒数"),
            new ConfigEntry("stickerAttachMaxWaitSecond","15","表情包已开始识别时，最多额外等待多少秒"),
            new ConfigEntry("stickerAttachWaitUntilNextMessage","true",
                    "同一用户又发言或表情包已附带时立刻结束等待，不再干等到窗口结束"),
            new ConfigEntry("stickerSendChance","0.35","提示词中的表情包参考使用率，0 到 1"),
            new ConfigEntry("stickerVisionEnable","true","是否用视觉模型从候选拼图中选择表情包"),
            new ConfigEntry("stickerVisionCandidates","6","视觉选择时最多生成多少张候选拼图"),
            new ConfigEntry("stickerVisionProfile","","表情包视觉选择使用的 AI profile，留空时使用 aiProfile"),
            new ConfigEntry("stickerVisionMaxTokens","600","表情包视觉选择输出 Token 上限"),
            new ConfigEntry("stickerVisionTimeoutSecond","45","表情包视觉选择请求超时秒数"),
            new ConfigEntry("messageBatchEnable","true","是否启用群消息合批：连续快速发言会合并成一个回合处理"),
            new ConfigEntry("messageBatchWindowSecond","2","合批窗口秒数，窗口内到达的新消息会并入同一批"),
            new ConfigEntry("messageBatchDebounceMode","natural",
                    "自适应防抖档位：off / fast / natural / steady"),
            new ConfigEntry("messageBatchMaxWaitSecond","8",
                    "单批从第一条消息开始的绝对等待上限秒数"),
            new ConfigEntry("messageBatchMaxMessages","10","单个批次最多合并多少条消息，超出时丢弃最旧的非直接点名消息"),
            new ConfigEntry("messageBatchMaxAgeSecond","20","合批消息最多等待多少秒，超过且没有点名时直接丢弃，0 表示不丢弃"),
            new ConfigEntry("learnEnable","true","是否允许用 /role learn 采集训练语料"),
            new ConfigEntry("learnDirectory","train-corpus","采集语料的存放目录，相对插件数据目录"),
            new ConfigEntry("learnContextBefore","6","采集时向前截取多少条上下文"),
            new ConfigEntry("learnContextAfter","3","采集时向后截取多少条上下文"),
            new ConfigEntry("modelServiceUrl","http://127.0.0.1:8770","MBB-Model 风格层小模型服务地址"),
            new ConfigEntry("modelServiceToken","","访问 MBB-Model 的 token，留空表示不校验"),
            new ConfigEntry("modelServiceTimeoutSecond","120","调用 MBB-Model 的超时秒数"),
            new ConfigEntry("trainEnable","true","是否允许用 /role train 提交训练"),
            new ConfigEntry("trainContextBefore","4","群聊语料训练时，每条样本带多少条前文"),
            new ConfigEntry("trainMaxSamplesPerRun","2000","单次训练最多导出多少条样本"),
            new ConfigEntry("trainMaxChars","200","超过这个长度的群消息不参与训练"),
            new ConfigEntry("trainIncludeBotMessages","false","是否把机器人自己的消息也算进训练语料"),
            new ConfigEntry("trainAutoStart","true","提交数据后是否自动开始训练"),
            new ConfigEntry("batchMaxSegments","4","合批回合最多回复几段，每段可以回应批内不同的消息"),
            new ConfigEntry("turnThreads","4","角色回合的执行线程数，路由与生成都在这个线程池里跑"),
            new ConfigEntry("pokeReplyEnable","true","是否响应戳一戳；MBB-Poke 启用时本插件自动跳过"),
            new ConfigEntry("pokeBackEnable","true","被戳时是否允许角色戳回去"),
            new ConfigEntry("pokeReplyMaxChars","20","戳一戳回复单条短消息的最大字符数"),
            new ConfigEntry("pokeBackCooldownSecond","60","对同一用户戳回去的最小间隔秒数"),
            new ConfigEntry("pokeStreakWindowSecond","60","连续戳一戳的统计窗口秒数，窗口内超过阈值才算骚扰"),
            new ConfigEntry("pokeStreakThreshold","3","窗口内戳几次开始算骚扰，达到后才会涨厌烦"),
            new ConfigEntry("quoteReplyEnable","true","回复被艾特或被直接回复的消息时是否引用原消息"),
            new ConfigEntry("contextHardLimitEnable","false","是否启用本地提示词硬字符预算"),
            new ConfigEntry("promptTotalChars","120000","启用本地硬预算时，单轮提示词总字符上限"),
            new ConfigEntry("routerEnable","true","是否启用 AI 路由层判断要不要回复、挂哪些技能和资料"),
            new ConfigEntry("routerProfile","","路由层使用的模型配置名，留空则使用 aiProfile"),
            new ConfigEntry("routerMaxTokens","2400","路由层输出 Token 上限，reasoning 模型建议不低于 2400"),
            new ConfigEntry("routerReasoningEffort","none","路由层思考强度：none / low / medium / high，留空表示不发送"),
            new ConfigEntry("styleEnable","false","独立风格层已移除；保留键位仅用于兼容旧配置"),
            new ConfigEntry("styleMaxChars","60","整段回复超过多少字时给复核层附加超长提示"),
            new ConfigEntry("styleLongReplyEnable","false","是否启用整段总长度超限提示；单句超长始终检测"),
            new ConfigEntry("styleProfile","","旧独立风格层模型配置，已不再使用"),
            new ConfigEntry("styleMaxTokens","1200","旧独立风格层 Token 配置，已不再使用"),
            new ConfigEntry("styleReasoningEffort","low","旧独立风格层思考强度，已不再使用"),
            new ConfigEntry("styleProactiveEnable","false","主动发言时是否给复核层附加风格处理提示"),
            new ConfigEntry("styleFailureThreshold","2","旧独立风格层熔断配置，已不再使用"),
            new ConfigEntry("styleFailureCooldownSecond","600","旧独立风格层熔断配置，已不再使用"),
            new ConfigEntry("maxLongMemories","150","最多加载多少条长期记忆"),
            new ConfigEntry("topicFatigueEnable","true","是否启用群级话题疲劳检测"),
            new ConfigEntry("topicFatigueWindowMinute","30","同一话题进入疲劳判定的时间窗口分钟数"),
            new ConfigEntry("topicFatigueMinCount","6","同一话题在窗口内达到多少次后判定为疲劳"),
            new ConfigEntry("topicFatigueSelfCount","3","角色自己在窗口内参与多少次后判定为疲劳"),
            new ConfigEntry("topicFatigueKeepHours","168","话题疲劳记录最多保留多少小时"),
            new ConfigEntry("threadEnable","true","是否维护会话线程和当前回合状态"),
            new ConfigEntry("threadWindowSecond","180","超过多少秒没有新消息就视为新线程"),
            new ConfigEntry("replyFeedbackEnable","true","是否根据主动发言是否被接话修正回复意愿"),
            new ConfigEntry("replyFeedbackWindowSecond","180","主动发言后多少秒内出现真人发言算被接话"),
            new ConfigEntry("replyFeedbackMinSamples","6","至少积累多少次主动发言反馈后才修正概率"),
            new ConfigEntry("replyFeedbackMaxPenalty","0.25","主动发言长期没人接时最多降低多少回复概率"),
            new ConfigEntry("replyFeedbackBonus","0.10","主动发言经常被接话时最多提高多少回复概率"),
            new ConfigEntry("replyFeedbackHistory","30","内存中保留最近多少次主动发言反馈"),
            new ConfigEntry("replyReviewEnable","true","是否在发送前复核角色回复，并处理风格、链接和长句分句"),
            new ConfigEntry("replyReviewProfile","","回复复核使用的模型配置名，留空使用 aiProfile"),
            new ConfigEntry("replyReviewMaxTokens","1200","回复复核输出 Token 上限"),
            new ConfigEntry("replyReviewReasoningEffort","none","回复复核思考强度"),
            new ConfigEntry("replyReviewTimeoutSecond","60","回复复核请求超时秒数"),
            new ConfigEntry("replyReviewMinScore","70","回复复核最低通过分；合法 rewrite 无论评分都会采用"),
            new ConfigEntry("toolEnable","true","是否允许角色通过原生工具调用查询信息或维护私有工作区"),
            new ConfigEntry("toolCoreTools","tool_search,end_turn,message_send,sticker","首轮直接开放的工具"),
            new ConfigEntry("toolLoadedMaxTools","8","单轮最多同时加载多少个工具 schema"),
            new ConfigEntry("toolSearchMaxResults","4","tool_search 单次最多返回多少个工具"),
            new ConfigEntry("toolMaxSteps","16","单轮最多进行多少次模型-工具循环"),
            new ConfigEntry("toolMaxCalls","12","单轮最多执行多少个工具调用"),
            new ConfigEntry("toolResultMaxChars","16000","单个工具结果注入上下文的最大字符数"),
            new ConfigEntry("toolResultTotalMaxChars","48000","单轮所有工具结果合计最大字符数"),
            new ConfigEntry("toolLiveMessageMax","10","工具执行期间最多并入多少条新群消息"),
            new ConfigEntry("toolLoopMaxMillis","75000","单轮工具循环总时间上限毫秒"),
            new ConfigEntry("toolCallMaxMillis","20000","单个工具调用时间上限毫秒"),
            new ConfigEntry("toolParallelReadOnlyEnable","true","只读工具是否允许并行执行"),
            new ConfigEntry("toolParallelMax","4","工具执行线程数上限"),
            new ConfigEntry("toolAiRetryCount","0","工具循环中的 AI 请求重试次数"),
            new ConfigEntry("toolTurnPersistEnable","true","是否持久化工具回合"),
            new ConfigEntry("toolTurnKeepHours","24","工具回合记录保留小时数"),
            new ConfigEntry("toolRecoverMaxMinutes","60","未完成工具回合允许恢复的最大分钟数"),
            new ConfigEntry("toolMemoryInjectItems","8","下一轮注入最近多少次工具调用结果"),
            new ConfigEntry("toolMemoryInjectMaxChars","6000","工具调用结果注入字符上限"),
            new ConfigEntry("toolMemoryKeepHours","72","工具调用记录保留小时数"),
            new ConfigEntry("toolMemoryMaxRows","500","工具调用记录最大行数"),
            new ConfigEntry("workspaceEnable","true","是否启用角色私有工作区 plugins/MBB-Roleplay/data"),
            new ConfigEntry("workspaceMaxReadChars","32000","工作区单次读取最大字符数"),
            new ConfigEntry("workspaceMaxWriteChars","64000","工作区单次写入最大字符数"),
            new ConfigEntry("workspaceMaxFilesPerList","200","工作区列表最多返回多少个文件"),
            new ConfigEntry("workspaceMaxTotalBytes","67108864","角色工作区总大小上限，默认 64MB"),
            new ConfigEntry("workspaceMaxMediaBytes","8388608","工作区单张导入图片最大字节数"),
            new ConfigEntry("workspaceHistoryEnable","true","覆盖写入前是否保留历史版本"),
            new ConfigEntry("workspaceHistoryMaxVersions","10","每个文件最多保留多少个历史版本"),
            new ConfigEntry("workspaceTrashKeepDays","30","工作区回收站保留天数"),
            new ConfigEntry("webEnable","true","是否开放受限网页浏览工具"),
            new ConfigEntry("webWhitelist",RoleplayConfig.DEFAULT_WEB_WHITELIST,
                    "网页允许域名，多个用英文逗号分隔"),
            new ConfigEntry("webSearchUrl","https://www.bing.com/search?q={query}","联网搜索地址模板"),
            new ConfigEntry("webSearchFallbackUrl","","可选联网搜索备用地址模板；默认留空，不启用第二搜索源"),
            new ConfigEntry("webSearchMaxResults","10","单次联网搜索最多返回多少条结果"),
            new ConfigEntry("webOwnerAuthWindowSecond","300","owner 内网访问授权窗口秒数"),
            new ConfigEntry("webTimeoutSecond","15","网页连接超时秒数"),
            new ConfigEntry("webMaxBytes","262144","网页最多读取字节数"),
            new ConfigEntry("webMaxChars","12000","网页净化后最多注入字符数"),
            new ConfigEntry("webImageMaxBytes","8388608","网页图片发送单张最多读取的字节数"),
            new ConfigEntry("webCacheSecond","300","网页和搜索结果缓存秒数"),
            new ConfigEntry("pacingEnable","true","是否在发送前加入拟人打字延迟"),
            new ConfigEntry("pacingBaseMs","350","拟人延迟基础毫秒"),
            new ConfigEntry("pacingPerCharMs","25","每个字符增加的延迟毫秒"),
            new ConfigEntry("pacingMaxMs","2500","单次拟人延迟上限毫秒"),
            new ConfigEntry("pacingNightMultiplier","1.40","深夜时段延迟倍率"),
            new ConfigEntry("pacingActiveMultiplier","0.80","群活跃时延迟倍率"),
            new ConfigEntry("pacingActiveThreshold","0.70","视为群活跃的注意力阈值"),
            new ConfigEntry("pacingNightStartHour","0","深夜时段开始小时"),
            new ConfigEntry("pacingNightEndHour","6","深夜时段结束小时"),
            new ConfigEntry("safetyGuardEnable","true","是否启用角色安全与防注入"),
            new ConfigEntry("promptInjectionGuardEnable","true","是否拦截系统提示词诱导和越狱指令"),
            new ConfigEntry("privacyGuardEnable","true","是否拦截现实隐私索取"),
            new ConfigEntry("expressionLearnEnable","true","是否从群聊提取表达候选供管理员复核"),
            new ConfigEntry("expressionMinCount","3","表达候选至少出现多少次才进入待审"),
            new ConfigEntry("expressionMinUsers","2","表达候选至少被多少不同群员使用才进入待审"),
            new ConfigEntry("expressionMaxCandidates","200","每个群最多注入多少条已通过表达"),
            new ConfigEntry("expressionInjectMaxChars","800","已通过表达注入提示词的最大字符数"),
            new ConfigEntry("expressionVectorEnable","true","是否使用 embedding 做表达候选向量检索"),
            new ConfigEntry("expressionEmbeddingProfile","","用于表达向量检索的 AI profile，留空时使用 aiProfile"),
            new ConfigEntry("expressionEmbeddingTimeoutSecond","60","表达 embedding 请求超时秒数"),
            new ConfigEntry("expressionVectorTopK","6","向量检索每次最多返回多少条表达候选"),
            new ConfigEntry("expressionVectorMinScore","0.25","表达向量检索最低混合相似度"),
            new ConfigEntry("expressionVectorQueryCacheMinute","10","表达查询 embedding 的本地缓存分钟数"),
            new ConfigEntry("expressionReviewEnable","true","是否定期用 AI 审核表达候选"),
            new ConfigEntry("expressionReviewMode","suggest","表达审核模式：suggest / auto"),
            new ConfigEntry("expressionReviewProfile","","表达审核使用的 AI profile，留空时使用 aiProfile"),
            new ConfigEntry("expressionReviewIntervalHours","24","表达审核定时任务间隔小时"),
            new ConfigEntry("expressionReviewMaxCandidates","50","单群单次最多审核多少条表达候选"),
            new ConfigEntry("expressionReviewMaxTokens","3000","表达审核输出 Token 上限"),
            new ConfigEntry("expressionReviewTimeoutSecond","120","表达审核请求超时秒数"),
            new ConfigEntry("expressionAutoApproveMinQuality","0.70","auto 模式自动通过的最低质量"),
            new ConfigEntry("attentionDriftEnable","true","是否启用注意力漂移"),
            new ConfigEntry("attentionDecayMinute","30","注意力向基线衰减的半衰期分钟数"),
            new ConfigEntry("attentionLowThreshold","0.25","低于该注意力时降低主动接话概率"),
            new ConfigEntry("attentionHighThreshold","0.70","高于该注意力时提高主动接话概率"),
            new ConfigEntry("attentionMaxPenalty","0.20","低注意力时最多降低多少回复概率"),
            new ConfigEntry("attentionBonus","0.05","高注意力时最多提高多少回复概率"),
            new ConfigEntry("attentionTopicLimit","200","每个群最多追踪多少话题的注意力"),
            new ConfigEntry("dailyReviewEnable","true","是否启用每日复盘"),
            new ConfigEntry("dailyReviewHour","4","每日复盘在本地时间的几点执行"),
            new ConfigEntry("dailyReviewMaxMessages","300","每日复盘最多读取多少条消息"),
            new ConfigEntry("dailyReviewMaxTokens","4000","每日复盘输出 Token 上限"),
            new ConfigEntry("dailyReviewTimeoutSecond","180","每日复盘请求超时秒数"),
            new ConfigEntry("traceEnable","true","是否记录每轮路由、资料和回复 trace"),
            new ConfigEntry("traceKeepHours","72","trace 保留小时数"),
            new ConfigEntry("traceMaxRows","2000","trace 最大保留行数"),
            new ConfigEntry("evalEnable","true","是否允许 /role eval 运行内置评测集"),
            new ConfigEntry("evalProfile","","评测使用的模型配置名，留空使用 aiProfile"),
            new ConfigEntry("evalMaxTokens","1200","评测输出 Token 上限"),
            new ConfigEntry("evalTimeoutSecond","60","评测请求超时秒数"),
            new ConfigEntry("memoryRelevanceSort","true","长期记忆与永久记忆是否按当前对话相关性排序后注入"),
            new ConfigEntry("memoryRelevancePoolSize","600","相关性排序前先加载多少条长期记忆作为候选池"),
            new ConfigEntry("memoryRelevanceMaxChars","12000","长期记忆按相关性注入的字符上限"),
            new ConfigEntry("memoryConflictResolveEnable","true","是否在写入长期记忆时检测并归档冲突旧记忆"),
            new ConfigEntry("memoryConflictMinOverlap","2","冲突判定所需的最小 bigram 重合数"),
            new ConfigEntry("globalMemoryRelevanceMaxChars","10000","永久记忆按相关性注入的字符上限"),
            new ConfigEntry("replyMaxTokens","4000","单次角色回复最大 Token"),
            new ConfigEntry("replyImageMaxTokens","4000","带图片上下文时单次角色回复最大 Token"),
            new ConfigEntry("replySegmentMaxChars","30","单段回复硬上限字符数，不设最低字数"),
            new ConfigEntry("replySplitPunctuation","。！？!?；;，、：,:～~…","回复拆分时优先使用的断句符号，先找句末标点，再找弱标点"),
            new ConfigEntry("replyMaxSegments","5","最多拆分发送多少段"),
            new ConfigEntry("replyBeatMaxSegments","5","普通聊天最多拆成多少个连续节拍"),
            new ConfigEntry("replyBeatBaseDelayMs","450","连续节拍基础间隔毫秒"),
            new ConfigEntry("replyBeatPerCharMs","35","节拍中每个字符增加的间隔毫秒"),
            new ConfigEntry("replyBeatMaxDelayMs","2200","连续节拍单次最大间隔毫秒"),
            new ConfigEntry("replyLinkFilterEnable","true","是否过滤模型回复中凭空输出的网址链接"),
            new ConfigEntry("cameraEnable","true","是否允许 draw 技能模拟角色手机自拍和拍照"),
            new ConfigEntry("drawPromptCompilerEnable","true","生图是否启用独立 prompt 编译层"),
            new ConfigEntry("drawPromptProfile","","生图 prompt 编译使用的模型配置名，留空使用 aiProfile"),
            new ConfigEntry("drawPromptMaxTokens","1800","生图 prompt 编译输出 Token 上限"),
            new ConfigEntry("drawPromptReasoningEffort","none","生图 prompt 编译思考强度"),
            new ConfigEntry("drawPromptTimeoutSecond","120","生图 prompt 编译请求超时秒数"),
            new ConfigEntry("drawPromptMaxChars","1600","最终正向 prompt 最大字符数"),
            new ConfigEntry("drawPromptNegativeMaxChars","800","最终角色专属负向 prompt 最大字符数"),
            new ConfigEntry("drawPromptKnowledgeMaxChars","1200","当前角色知识库外貌参考最大字符数"),
            new ConfigEntry("recentReplyCheckCount","8","重复检测时参考最近多少条角色回复"),
            new ConfigEntry("repeatSuppressEnable","false","是否启用代码层重复回复兜底检测；关闭后只在提示词中约束"),
            new ConfigEntry("repeatSimilarityThreshold","0.72","与最近角色回复相似度达到多少时跳过，0.3 到 1"),
            new ConfigEntry("repeatCheckMinChars","6","少于多少字的回复不进行重复检测"),
            new ConfigEntry("repeatOpeningLimit","2","同一开头在最近角色回复中出现多少次后禁止再次使用"),
            new ConfigEntry("repeatCommonRunMinChars","8","与最近回复的最长公共片段达到多少字时判为复读，抓同一组信息换尾巴的情况"),
            new ConfigEntry("commandPrefixes","/,!,＃,#","忽略以这些前缀开头的指令消息，英文逗号分隔"),
            new ConfigEntry("personaFile","persona-aris.json","角色设定文件；内置示例可用 /role persona 查看"),
            new ConfigEntry("quietReplyEnable","true","是否启用免打扰名单：名单内群员只在主动叫到角色时才回复"),
            new ConfigEntry("minMessageLength","2","忽略少于多少字的纯文本消息"),
            new ConfigEntry("privateChatEnable","true","是否允许私聊；默认只响应 owner 和机器人 QQ 好友"),
            new ConfigEntry("privateChatWhitelist","","旧版私聊白名单，仅保留配置兼容；好友制启用后不再放行非好友"),
            new ConfigEntry("privateChatFriendCacheSecond","300","QQ 好友列表缓存秒数"),
            new ConfigEntry("privateChatContextMessages","40","私聊上下文保留条数"),
            new ConfigEntry("privateChatMaxReplyChars","160","私聊单条回复最大字符数"),
            new ConfigEntry("privateChatImageDebounceSecond","4","私聊图片或表情包发出后等待补充文字的秒数"),
            new ConfigEntry("privateChatLiveMergeMaxRounds","2","私聊生成期间收到新消息时最多合并重生成几次"),
            new ConfigEntry("privateChatPendingMaxMessages","10","单个用户等待处理的私聊消息上限"),
            new ConfigEntry("privateChatInputStatusEnable","true","是否用 QQ input_status 延后私聊回复"),
            new ConfigEntry("privateChatInputStatusGraceSecond","3","最后一次输入状态后继续等待的秒数"),
            new ConfigEntry("privateChatInputStatusMaxWaitSecond","10","输入状态最多把私聊回复延后多少秒"),
            new ConfigEntry("friendRequestRoleDecisionEnable","true","是否由角色决定好友申请是否通过"),
            new ConfigEntry("friendRequestRoleDecisionProfile","","好友申请裁决使用的 AI profile，留空时使用 aiProfile"),
            new ConfigEntry("friendRequestRoleDecisionMinConfidence","0.55","角色同意好友申请所需的最低置信度"),
            new ConfigEntry("friendRequestRoleDecisionMaxTokens","1600","好友申请裁决输出 Token 上限"),
            new ConfigEntry("friendRequestRoleDecisionTimeoutSecond","60","好友申请裁决请求超时秒数"),
            new ConfigEntry("randomTickEnable","true","是否启用随机刻主动互动"),
            new ConfigEntry("randomTickMinMinute","10","随机刻最短间隔分钟数"),
            new ConfigEntry("randomTickMaxMinute","30","随机刻最长间隔分钟数"),
            new ConfigEntry("randomTickQuietStartHour","0","随机刻静默开始小时"),
            new ConfigEntry("randomTickQuietEndHour","8","随机刻静默结束小时"),
            new ConfigEntry("randomTickGroupWindowMinute","360","群主动话题统计窗口分钟数"),
            new ConfigEntry("randomTickGroupMaxActions","2","窗口内最多执行多少次群主动话题"),
            new ConfigEntry("randomTickQzoneDailyMax","2","每天最多发布多少条 Qzone 说说"),
            new ConfigEntry("randomTickOwnerRecentWindowMinute","180","owner 最近互动窗口分钟数"),
            new ConfigEntry("randomTickOwnerRecentBoostChance","0.30","最近互动 owner 触发主动私聊的概率加成"),
            new ConfigEntry("randomTickDedupeHours","24","相同主动行为指纹的去重小时数"),
            new ConfigEntry("randomTickMaxTokens","1200","随机刻决策输出 Token 上限"),
            new ConfigEntry("randomTickTimeoutSecond","90","随机刻请求超时秒数"),
            new ConfigEntry("randomTickReasoningEffort","low","随机刻思考强度：none / low / medium / high"));

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
        boolean legacyReplySegmentMaxChars20 = version < 48 && "20".equals(
                config.getString("replySegmentMaxChars",""));
        boolean legacyReplyMaxSegments = version < 49 && "2".equals(
                config.getString("replyMaxSegments",""));
        boolean legacyReplyBeatMaxSegments = version < 49 && "3".equals(
                config.getString("replyBeatMaxSegments",""));
        boolean legacyReplySegmentMaxChars = version < 12 && "160".equals(
                config.getString("replySegmentMaxChars",""));
        boolean legacyReplySplitPunctuation = version < 13
                && "。！？!?；;，、：,:".equals(config.getString("replySplitPunctuation",""));
        boolean legacyReplySplitPunctuationEllipsis = version < 25
                && "。！？!?；;，、：,:～~".equals(config.getString("replySplitPunctuation",""));
        boolean legacyEmotionAnalyzeMaxTokens = version < 26 && "1200".equals(
                config.getString("emotionAnalyzeMaxTokens",""));
        boolean legacyStyleMaxTokens = version < 26 && "400".equals(
                config.getString("styleMaxTokens",""));
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
        boolean legacyMemoryMergeRetryCount = version < 27
                && "1".equals(config.getString("memoryMergeRetryCount",""));
        boolean legacyMemoryMergeBatchSize = version < 28
                && "60".equals(config.getString("memoryMergeBatchSize",""));
        boolean legacySpeechRetrievalCount = version < 35
                && "8".equals(config.getString("speechRetrievalCount",""));
        boolean legacyShortContext = version < 37
                && ("80".equals(config.getString("shortContextMessages",""))
                || "120".equals(config.getString("shortContextMessages","")));
        boolean legacyPromptTotal = version < 37
                && "26000".equals(config.getString("promptTotalChars",""));
        boolean legacyMemoryMaxChars = version < 37
                && "5000".equals(config.getString("memoryRelevanceMaxChars",""));
        boolean legacyGlobalMemoryMaxChars = version < 37
                && "4000".equals(config.getString("globalMemoryRelevanceMaxChars",""));
        boolean legacyReplyMaxTokens = version < 37
                && "1200".equals(config.getString("replyMaxTokens",""));
        boolean legacyToolSteps = version < 37
                && "4".equals(config.getString("toolMaxSteps",""));
        boolean legacyToolCalls = version < 37
                && "8".equals(config.getString("toolMaxCalls",""));
        boolean legacyToolResultChars = version < 37
                && "8000".equals(config.getString("toolResultMaxChars",""));
        boolean legacyWebWhitelist = version < 38
                && ("wikipedia.org,baike.baidu.com,bilibili.com,gamekee.com,bluearchive.wiki,"
                +"kivo.wiki,moegirl.org.cn,github.com,raw.githubusercontent.com,deepwiki.com,reddit.com")
                .equals(config.getString("webWhitelist",""));
        boolean legacyWebSearchPrimary = version < 39
                && "https://html.duckduckgo.com/html/?q={query}"
                .equals(config.getString("webSearchUrl",""));
        boolean legacyToolStepBalance = version < 38
                && "8".equals(config.getString("toolMaxSteps",""));
        boolean legacyToolStepDefault = version < 41
                && "6".equals(config.getString("toolMaxSteps",""));
        boolean legacyRandomTickReasoning = version < 41
                && "none".equals(config.getString("randomTickReasoningEffort",""));
        boolean legacyToolCallBalance = version < 38
                && "16".equals(config.getString("toolMaxCalls",""));
        boolean legacyToolLoopBalance = version < 38
                && "90000".equals(config.getString("toolLoopMaxMillis",""));
        boolean legacyToolCoreSticker = version < 43
                && "tool_search,end_turn,message_send"
                .equals(config.getString("toolCoreTools",""));
        boolean legacySocialWebWhitelist = version < 44
                && LEGACY_WEB_WHITELIST.equals(config.getString("webWhitelist",""));
        boolean legacySearchFallback = version < 45
                && "https://html.duckduckgo.com/html/?q={query}"
                .equals(config.getString("webSearchFallbackUrl",""));
        boolean legacySearchMaxResults = version < 45
                && "5".equals(config.getString("webSearchMaxResults",""));
        boolean legacyStyleEnable = version < 48
                && "true".equals(config.getString("styleEnable",""));
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
        if (version < 50) {
            config.set("configVersion",CURRENT_VERSION);
            if (!versionMissing) changed++;
        }
        if (legacyReplyMaxSegments) {
            config.set("replyMaxSegments","5");
            changed++;
        }
        if (legacyReplyBeatMaxSegments) {
            config.set("replyBeatMaxSegments","5");
            changed++;
        }
        if (legacyStyleEnable) {
            config.set("styleEnable","false");
            changed++;
        }
        if (legacyMemoryMergeRetryCount) {
            config.set("memoryMergeRetryCount","3");
            changed++;
        }
        if (legacyMemoryMergeBatchSize) {
            config.set("memoryMergeBatchSize","100");
            changed++;
        }
        if (legacySpeechRetrievalCount) {
            config.set("speechRetrievalCount","2");
            changed++;
        }
        if (legacyShortContext) {
            config.set("shortContextMessages","200");
            changed++;
        }
        if (legacyPromptTotal) {
            config.set("promptTotalChars","120000");
            changed++;
        }
        if (legacyMemoryMaxChars) {
            config.set("memoryRelevanceMaxChars","12000");
            changed++;
        }
        if (legacyGlobalMemoryMaxChars) {
            config.set("globalMemoryRelevanceMaxChars","10000");
            changed++;
        }
        if (legacyReplyMaxTokens) {
            config.set("replyMaxTokens","4000");
            changed++;
        }
        if (legacyToolSteps) {
            config.set("toolMaxSteps","16");
            changed++;
        }
        if (legacyToolCalls) {
            config.set("toolMaxCalls","12");
            changed++;
        }
        if (legacyToolResultChars) {
            config.set("toolResultMaxChars","16000");
            changed++;
        }
        if (legacyWebWhitelist) {
            config.set("webWhitelist",RoleplayConfig.DEFAULT_WEB_WHITELIST);
            changed++;
        }
        if (legacyWebSearchPrimary) {
            config.set("webSearchUrl","https://www.bing.com/search?q={query}");
            config.set("webSearchFallbackUrl","");
            changed += 2;
        }
        if (legacyToolStepBalance) {
            config.set("toolMaxSteps","16");
            changed++;
        }
        if (legacyToolStepDefault) {
            config.set("toolMaxSteps","16");
            changed++;
        }
        if (legacyRandomTickReasoning) {
            config.set("randomTickReasoningEffort","low");
            changed++;
        }
        if (legacyToolCallBalance) {
            config.set("toolMaxCalls","12");
            changed++;
        }
        if (legacyToolLoopBalance) {
            config.set("toolLoopMaxMillis","75000");
            changed++;
        }
        if (legacyToolCoreSticker) {
            config.set("toolCoreTools","tool_search,end_turn,message_send,sticker");
            changed++;
        }
        if (legacySocialWebWhitelist) {
            config.set("webWhitelist",RoleplayConfig.DEFAULT_WEB_WHITELIST);
            changed++;
        }
        if (legacySearchFallback) {
            config.set("webSearchFallbackUrl","");
            changed++;
        }
        if (legacySearchMaxResults) {
            config.set("webSearchMaxResults","10");
            changed++;
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
            config.set("replySegmentMaxChars","30");
            changed++;
        }
        if (legacyReplySplitPunctuation) {
            config.set("replySplitPunctuation","。！？!?；;，、：,:～~…");
            changed++;
        }
        if (legacyReplySplitPunctuationEllipsis) {
            config.set("replySplitPunctuation","。！？!?；;，、：,:～~…");
            changed++;
        }
        if (legacyEmotionAnalyzeMaxTokens) {
            config.set("emotionAnalyzeMaxTokens","2400");
            changed++;
        }
        if (legacyStyleMaxTokens) {
            config.set("styleMaxTokens","1200");
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
        if (legacyReplySegmentMaxChars20) {
            config.set("replySegmentMaxChars","30");
            changed++;
        }
        if (version < 45 || legacyRoleBotChance || legacyPersonaFile || legacyStickerWindow
                || legacyRouterMaxTokens || legacyRouterMaxTokens1200
                || legacyPromptTotalChars || legacyMemoryRelevanceMaxChars
                || legacyGlobalMemoryRelevanceMaxChars || legacyRouterReasoningEffort
                || legacyEmotionAnalyzeMaxDelta08 || legacyReplySegmentMaxChars20
                || legacyReplySegmentMaxChars || legacyReplySplitPunctuationEllipsis
                || legacyEmotionAnalyzeMaxTokens || legacyStyleMaxTokens
                || legacyReplySplitPunctuation || legacyInitialAffinity
                || legacyInitialTrust || legacyIntimacyClose || legacyIntimacyVeryClose
                || legacyRelationDailyMax || legacyEmotionEventCooldown
                || legacyEmotionAnalyzeCooldown || legacyEmotionAnalyzeMaxDelta
                || legacyToolCoreSticker || legacySocialWebWhitelist
                || legacySearchFallback || legacySearchMaxResults) {
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
