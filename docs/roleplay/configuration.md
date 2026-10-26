# MBB-Roleplay 配置与部署

## 角色设定

插件首次运行会释放一份内置示例 persona，文件名以插件包内实际文件为准。示例只用于说明格式，实际角色内容由使用者自行维护。

persona 与台词语料统一在 `MBB-Persona` 私有仓库维护：`personas/` 放角色设定，`corpus/` 放语料。把需要的 persona 复制到插件数据目录，把语料复制到数据目录下的 `speech-corpus/`。

persona 支持任意作品角色或原创角色，常用字段包括：

- 名称、别名、身份
- 世界观、学园、组织、阵营或势力
- 外貌、性格、说话方式和场景语气规则
- 人物关系、专有名词、剧情记忆
- 行为边界、互动规则和情绪基线

```text
/role persona
/role persona <文件名>
/role persona reset <文件名>
```

`/role persona` 只会读取数据目录里实际存在的文件。自定义 persona 可以使用任意文件名，只要放在插件数据目录并通过 `/role persona <文件名>` 切换即可；`/role persona reset` 只对插件包内实际存在的文件有效。

共享的 `students.json`（69 人外貌图鉴）**已经不再随插件打包**，学生资料统一由知识库的档案库提供，
原文件备份在知识库项目的 `legacy/students.json`。运行目录里如果还留着这个文件，插件仍会读取它作为兜底，不会报错；
新装环境不会再生成它。

可以修改名称、身份、性格、说话方式、兴趣、禁忌和行为规则。修改后执行：

```text
/role reload
```

如果希望恢复插件内置示例设定，可以使用：

```text
/role persona reset <文件名>
```


## 依赖

需要安装并启用 `MBB-AI`。图片理解还需要安装并启用 `MBB-Vision`。`MBB-AI` 负责模型调用，`MBB-Vision` 负责图片识别、缓存和复用。

## 默认性能

```yaml
replyCooldownSecond: 5
maxRepliesPerHour: 180
initialAffinity: 65
initialTrust: 60
initialGroupAdminMultiplier: 1.05
initialBotAdminMultiplier: 1.10
initialBotOwnerMultiplier: 1.15
relationDailyMaxDelta: 2.0
intimacyCloseAffinity: 75
intimacyVeryCloseAffinity: 90
emotionEnable: true
friendMemoryEnable: true
friendMemoryProfile: ""
friendMemoryMinNewMessages: 6
friendMemoryIntervalHours: 12
friendMemoryContextMessages: 120
friendMemoryMaxTokens: 1200
friendMemoryTimeoutSecond: 120
friendMemoryInjectMaxChars: 1800
randomActivityEnable: true
randomActivityServices: ""
randomActivityMemoryImportance: 3
randomActivityShareEnable: true
randomActivityShareMinScore: 0.75
randomActivityTimeoutSecond: 300
conversationAppendEnable: true
conversationContextTokenLimit: 1000000
conversationContextCompactPercent: 0.75
conversationContextKeepPercent: 0.20
conversationAppendRecentMessages: 20
conversationCompactionProfile: ""
conversationCompactionMaxTokens: 6000
conversationCompactionTimeoutSecond: 300
emotionDecayMinute: 30
emotionEventCooldownSecond: 300
emotionAnalyzeEnable: true
emotionAnalyzeMode: "significant"
emotionAnalyzeProfile: ""
emotionAnalyzeCooldownSecond: 300
emotionAnalyzeMaxTokens: 2400
emotionAnalyzeReasoningEffort: "low"
emotionAnalyzeMaxDelta: 0.5
emotionAnalyzeMaxDecreaseDelta: 0.3
emotionReasonMaxChars: 120
emotionReasonMinStrength: 40
emotionReasonDecayDays: 30
emotionEventRetentionDays: 90
emotionPositiveReplyBonus: 0.05
emotionNegativeReplyPenalty: 0.15
emotionAffinityReplyBonus: 0.08
emotionAffinityReplyPenalty: 0.15
reminderEnable: true
reminderAiParse: true
reminderMaxDays: 30
interestReplyChance: 0.65
conversationWindowSecond: 180
continuationReplyChance: 0.80
otherParticipantReplyChance: 0.45
replyWillingnessMode: shadow
replyWillingnessThreshold: 0.35
replyWillingnessWindowSecond: 300
otherRoleBotReplyChance: 0.50
maxConsecutiveOtherRoleMessages: 2
shortContextMessages: 200
shortContextMaxChars: 12000
structuredContextEnable: true
structuredContextMaxChars: 1800
structuredContextItemMaxChars: 160
memoryUpdateMessages: 50
memoryExtractMessages: 300
memoryExtractMaxChars: 16000
memoryExtractBatches: 3
memoryProfile: ""
replyReasoningEffort: "low"
memoryReasoningEffort: "low"
timeZone: "Asia/Shanghai"
memoryMaxTokens: 12000
memoryMergeMaxTokens: 32000
memoryMergeBatchSize: 100
memoryMergeMaxRounds: 3
memoryMergeRetryCount: 3
memoryMergeConcurrency: 4
memoryMergeCooldownMinute: 60
globalMemoryMergeCooldownMinute: 60
memoryTimeoutSecond: 300
privateChatEnable: true
privateChatWhitelist: ""
privateChatFriendCacheSecond: 300
privateChatContextMessages: 40
privateChatMaxReplyChars: 160
privateChatImageDebounceSecond: 4
privateChatLiveMergeMaxRounds: 2
privateChatPendingMaxMessages: 10
privateChatInputStatusEnable: true
privateChatInputStatusGraceSecond: 3
privateChatInputStatusMaxWaitSecond: 10
friendRequestRoleDecisionEnable: true
friendRequestRoleDecisionProfile: ""
friendRequestRoleDecisionMinConfidence: 0.55
friendRequestRoleDecisionMaxTokens: 1600
friendRequestRoleDecisionTimeoutSecond: 60
randomTickEnable: true
randomTickMinMinute: 10
randomTickMaxMinute: 30
randomTickQuietStartHour: 0
randomTickQuietEndHour: 8
randomTickMorningGreetingEnable: true
randomTickMorningStartHour: 8
randomTickMorningEndHour: 10
randomTickMorningChance: 0.80
randomTickMorningMaxGroups: 3
randomTickMorningMinMessages: 3
randomTickPrivateFollowUpEnable: true
randomTickPrivateFollowUpInitialMinute: 2
randomTickPrivateFollowUpMaxMinute: 120
randomTickPrivateFollowUpFreshMinute: 720
randomTickGroupWindowMinute: 360
randomTickGroupMaxActions: 2
randomTickQzoneDailyMax: 2
randomTickQzoneDayChance: 0.20
randomTickQzoneEveningChance: 0.80
randomTickQzoneNightChance: 0.03
randomTickQzoneEveningStartHour: 18
randomTickQzoneEveningEndHour: 23
randomTickQzoneNightEndHour: 7
randomTickOwnerRecentWindowMinute: 180
randomTickOwnerRecentBoostChance: 0.30
randomTickDedupeHours: 24
randomTickMaxTokens: 1200
randomTickTimeoutSecond: 90
randomTickReasoningEffort: "low"

随机刻的群主动话题不再由模型填写群号或正文。程序从最近 24 小时活跃群中选目标群，
目标群按角色最近 24 小时在该群的发言量加权抽样：发言最多的群权重为 `1.0`，
其他群按比例缩放，并统一增加 `0.25` 基础权重，避免冷门群永久失去机会。
生成阶段只注入目标群自己的短期摘要和近期消息，并使用按群隔离的 `sessionId`。
`randomTickOwnerRecentWindowMinute` 内的最近互动 owner 继续作为 Qzone 等主动目标的优先选择。
旧 `randomTickOwnerRecentBoostChance` 仅保留配置兼容，不再参与私聊主动追问调度。

`randomTickMorningGreetingEnable` 默认 `true`。在 `randomTickMorningStartHour` 到
`randomTickMorningEndHour` 窗口内，昨天至少有 `randomTickMorningMinMessages` 条真人消息的群
会进入早安候选，并按昨日真人活跃度加权。`randomTickMorningChance` 决定当天是否启用早安，
`randomTickMorningMaxGroups` 限制每个早晨最多向几个群发；每个群每天最多一次，
每个随机刻只处理一个群，避免集中群发。发送失败会在窗口内重试，内容跳过后该群当天不再尝试。

`randomTickPrivateFollowUpEnable` 默认 `true`。私聊主动追问按 owner 独立持久化，最近私聊后的
初始机会为 2 分钟；用户有新消息会重置退避，未回应时按 2、4、8、16 分钟指数增长，
到 `randomTickPrivateFollowUpMaxMinute`（默认 120 分钟）为止。模型可以返回 `<SKIP>`，
此时不发送；连续无内容会使用单独的退避，避免每两分钟空转。超过
`randomTickPrivateFollowUpFreshMinute` 没有新私聊后不再追问。
如果最近一条用户消息或最近一条角色消息含晚安、睡了、去睡、关灯、不聊了、明天见、
拜拜、再见等收尾语义，会立即取消该 owner 的后续主动追问；用户之后再发正常消息才会重新开启。

随机刻 Qzone 不再做每日首条保底。白天、晚间和深夜分别使用
`randomTickQzoneDayChance`、`randomTickQzoneEveningChance`、`randomTickQzoneNightChance`
决定是否把 Qzone 放入候选，最终仍由模型决定是否发布。`randomTickQzoneDailyMax` 只限制
随机刻通道；聊天工具、owner 私聊随机刻等其他入口发布 Qzone 只计入 QQ 插件计数，不受该上限拦截。

activeMemory: true
imageUnderstandingEnable: true
imageUnderstandingMode: "addressed"
imageUnderstandingMaxPerHour: 30
imageUnderstandingInjectOcr: true
imageUnderstandingProfile: ""
imageUnderstandingMaxChars: 600
visionReferenceMaxChars: 32000
imageContextTimeoutSecond: 300
stickerAttachEnable: true
stickerAttachWindowSecond: 5
stickerAttachMaxWaitSecond: 15
stickerAttachWaitUntilNextMessage: true
stickerSendChance: 0.35
stickerVisionEnable: true
stickerVisionCandidates: 6
stickerVisionProfile: ""
stickerVisionMaxTokens: 600
stickerVisionTimeoutSecond: 45
messageBatchEnable: true
messageBatchWindowSecond: 2
messageBatchDebounceMode: natural
messageBatchMaxWaitSecond: 8
messageBatchMaxMessages: 10
messageBatchMaxAgeSecond: 20
batchMaxSegments: 4
turnThreads: 4
addressedOtherMemberSkip: true
splitMessageSuppressSecond: 20
splitMessageSuppressMaxChars: 20
directReplyBypassCooldown: true
imageAsyncEnable: true
memoryRelevanceSort: true
memoryRelevancePoolSize: 600
memoryRelevanceMaxChars: 5000
globalMemoryRelevanceMaxChars: 4000
pokeReplyEnable: true
pokeBackEnable: true
pokeReplyMaxChars: 20
pokeBackCooldownSecond: 60
pokeStreakWindowSecond: 60
pokeStreakThreshold: 3
quoteReplyEnable: true
promptTotalChars: 26000
routerEnable: true
routerProfile: ""
routerMaxTokens: 2400
routerReasoningEffort: "none"
styleEnable: false
styleMaxChars: 60
styleLongReplyEnable: false
styleProfile: ""
styleMaxTokens: 1200
styleReasoningEffort: "low"
styleProactiveEnable: false
styleFailureThreshold: 2
styleFailureCooldownSecond: 600
replyImageMaxTokens: 4000
replySegmentMaxChars: 30
replySplitPunctuation: "。！？!?；;，、：,:～~…"
replyMaxSegments: 5
replyLinkFilterEnable: true
cameraEnable: true
drawPromptCompilerEnable: true
drawPromptProfile: ""
drawPromptMaxTokens: 1800
drawPromptReasoningEffort: "none"
drawPromptTimeoutSecond: 120
drawPromptMaxChars: 1600
drawPromptNegativeMaxChars: 800
drawPromptKnowledgeMaxChars: 1200
replyReviewEnable: true
replyReviewProfile: ""
replyReviewMaxTokens: 1200
replyReviewReasoningEffort: "none"
replyReviewTimeoutSecond: 60
replyReviewMinScore: 70
toolEnable: true
toolCoreTools: "tool_search,end_turn,message_send,sticker"
toolLoadedMaxTools: 8
toolSearchMaxResults: 4
toolMaxSteps: 16
toolMaxCalls: 12
toolResultMaxChars: 16000
toolResultTotalMaxChars: 48000
toolLiveMessageMax: 10
toolLoopMaxMillis: 75000
toolCallMaxMillis: 20000
toolParallelReadOnlyEnable: true
toolParallelMax: 4
toolAiRetryCount: 0
toolTurnPersistEnable: true
toolTurnKeepHours: 24
toolRecoverMaxMinutes: 60
toolMemoryInjectItems: 8
toolMemoryInjectMaxChars: 6000
toolMemoryKeepHours: 72
toolMemoryMaxRows: 500
workspaceEnable: true
workspaceMaxReadChars: 32000
workspaceMaxWriteChars: 64000
workspaceMaxFilesPerList: 200
workspaceMaxTotalBytes: 67108864
workspaceMaxMediaBytes: 8388608
workspaceHistoryEnable: true
workspaceHistoryMaxVersions: 10
workspaceTrashKeepDays: 30
webEnable: true
webWhitelist: "wikipedia.org,baike.baidu.com,bilibili.com,biligame.com,wiki.biligame.com,gamekee.com,bluearchive.wiki,kivo.wiki,moegirl.org.cn,bangumi.tv,bgm.tv,huijiwiki.com,prts.wiki,wiki.gg,game8.jp,appmedia.jp,kamigame.jp,ani.gamer.com.tw,fandom.com,namu.wiki,arca.live,github.com,raw.githubusercontent.com,deepwiki.com,duckduckgo.com,bing.com"
webSearchUrl: "https://www.bing.com/search?q={query}"
webSearchFallbackUrl: ""
webSearchMaxResults: 10
webOwnerAuthWindowSecond: 300
webTimeoutSecond: 15
webMaxBytes: 262144
webMaxChars: 12000
webImageMaxBytes: 8388608
webCacheSecond: 300
contextHardLimitEnable: false
promptTotalChars: 120000
pacingEnable: true
pacingBaseMs: 350
pacingPerCharMs: 25
pacingMaxMs: 2500
pacingNightMultiplier: 1.40
pacingActiveMultiplier: 0.80
pacingActiveThreshold: 0.70
pacingNightStartHour: 0
pacingNightEndHour: 6
safetyGuardEnable: true
promptInjectionGuardEnable: true
privacyGuardEnable: true
threadEnable: true
threadWindowSecond: 180
memoryConflictResolveEnable: true
memoryConflictMinOverlap: 2
traceEnable: true
traceKeepHours: 72
traceMaxRows: 2000
evalEnable: true
evalProfile: ""
evalMaxTokens: 1200
evalTimeoutSecond: 60
maxLongMemories: 150
topicFatigueEnable: true
topicFatigueWindowMinute: 30
topicFatigueMinCount: 6
topicFatigueSelfCount: 3
topicFatigueKeepHours: 168
replyFeedbackEnable: true
replyFeedbackWindowSecond: 180
replyFeedbackMinSamples: 6
replyFeedbackMaxPenalty: 0.25
replyFeedbackBonus: 0.10
replyFeedbackHistory: 30
expressionLearnEnable: true
expressionMinCount: 3
expressionMinUsers: 2
expressionMaxCandidates: 200
expressionInjectMaxChars: 800
expressionVectorEnable: true
expressionEmbeddingProfile: ""
expressionEmbeddingTimeoutSecond: 60
expressionVectorTopK: 6
expressionVectorMinScore: 0.25
expressionVectorQueryCacheMinute: 10
expressionReviewEnable: true
expressionReviewMode: balanced
expressionReviewProfile: ""
expressionReviewIntervalHours: 24
expressionReviewMaxCandidates: 50
expressionReviewMaxTokens: 3000
expressionReviewTimeoutSecond: 120
expressionAutoApproveMinQuality: 0.80
expressionBalancedRejectMaxQuality: 0.25
attentionDriftEnable: true
attentionDecayMinute: 30
attentionLowThreshold: 0.25
attentionHighThreshold: 0.70
attentionMaxPenalty: 0.20
attentionBonus: 0.05
attentionTopicLimit: 200
dailyReviewEnable: true
dailyReviewHour: 4
dailyReviewMaxMessages: 300
dailyReviewMaxTokens: 4000
dailyReviewTimeoutSecond: 180
recentReplyCheckCount: 8
repeatSuppressEnable: false
repeatSimilarityThreshold: 0.72
repeatCheckMinChars: 6
repeatOpeningLimit: 2
repeatCommonRunMinChars: 8
minMessageLength: 2
quietReplyEnable: true
```

非直接提及、非对话续接、非兴趣话题的消息不会参与回复。回复 prompt 只规定每条消息硬上限 30 字，不设最低字数；短句、半句和单个语气词都可以自然使用，但不能为了凑长度硬加内容，也不能为了显得简短丢掉必要信息。一条说不完可以在正文里换行，最多分两段；拆分器会在整段上限内从后向前查找 `replySplitPunctuation` 中的句末标点，不会只看字符上限前 20 字，因此“这次就这样吧。......下次别这么做了”会优先断在句号后。找不到句末标点再查逗号、顿号、冒号、分号、波浪号等弱标点，最后才按硬上限拆分。切在弱标点时，上半段末尾的标点会被去掉，避免消息以逗号之类的不完整语气结束；省略号「……」不会被从中间劈开。不要重复同一件事或细节，也不要连续使用同一种开头或口癖。

`botOwner` 在角色关系里固定视为妈妈，优先于 QQ 群主和管理员身份。这个关系不依赖当前发言人是否在场，
普通群友改昵称或自称妈妈也不会被当成 owner；owner 不在场时被问到“你妈妈是谁”，角色应承认有妈妈，
而不是否认这个设定。

`pokeReplyMaxChars` 默认 `20`，只约束戳一戳回复。戳一戳不会走普通回复的多段拆分，只会发送一条短消息，超出长度时优先在句末或弱标点处截断。

`replyLinkFilterEnable` 默认 `true`。复核层会优先在改写阶段删除模型凭空生成的链接；最终发送前仍会本地过滤 `http(s)://`、`www.` 和常见裸域名，作为复核关闭、漏改或改写失败时的兜底。过滤只作用于角色文本回复，不影响图片、文件和其他技能消息。

`cameraEnable` 默认 `true`。设为 `false` 后只关闭 `draw` 技能里的手机自拍和拍照模式，角色会忽略 `camera=selfie/photo`，普通生图仍可继续使用。改完执行 `/role reload` 或重启主程序后生效。

`drawPromptCompilerEnable` 默认 `true`。开启后角色层只生成画面 brief，`RoleplayDrawPromptService` 用独立上下文按 `brief.characters/subject` 解析目标角色，注入对应角色的 `imageTags`、`imageNegativeTags`、外貌和知识库条目，再交给 `MBB-ComfyUI`。画别人时不会自动把当前角色加入画面。`drawPromptProfile` 留空时使用 `aiProfile`；编译默认使用 `reasoningEffort: none`，避免把预算耗在思考上。`drawPromptMaxChars` 和 `drawPromptNegativeMaxChars` 分别限制最终正向、角色专属负向 prompt；`drawPromptKnowledgeMaxChars` 只注入目标角色自己的学生条目，不会把凯伊等其他角色外貌带进生图上下文。

`replyReviewEnable` 默认 `true`。角色回复在发送前会经过 `RoleplayReplyReviewService` 统一复核和风格后处理：检查角色口吻、AI 味、总结腔、说教和重复；删除模型凭空生成的链接；单句超过 `replySegmentMaxChars` 时做压缩简写、换行或分成短句。复核返回合法 `rewrite` 时会使用改写文本，不再要求必须先低于 `replyReviewMinScore`。单条回复和合批段落默认都最多使用 5 行；分别由 `replyBeatMaxSegments` 和 `replyMaxSegments` 控制。`replyReviewProfile` 留空时使用 `aiProfile`。

默认句尾不使用句号。正常心情下，复核和发送兜底都会把“。”转换成换行节拍；只有当前群情绪为烦躁、低落或闹别扭时才允许保留句号。私聊没有群情绪上下文，默认同样不使用句号。

`privateChatEnable` 控制私聊入口。准入规则是 owner 或机器人 QQ 好友，好友列表按
`privateChatFriendCacheSecond` 缓存；查询失败时拒绝非 owner 私聊。`privateChatWhitelist`
只保留旧配置兼容，不再放行非好友。私聊使用独立上下文，图片会交给 `MBB-Vision`，
长期结论以 `visibility=private` 写入记忆表。

私聊按用户串行处理。纯图片或表情包会先等待 `privateChatImageDebounceSecond` 秒，
用户在这段时间补发文字时立即结束等待并与图片合并；回复生成期间到达的新消息进入 pending，
不会因为同一用户已有任务而直接丢弃。默认允许 `privateChatLiveMergeMaxRounds=2` 次“丢弃旧候选、
合并新消息后重生成”，发送阶段才到达的内容则在当前回合结束后进入下一轮。
`privateChatPendingMaxMessages` 控制单个用户最多积压多少条待处理消息。

`privateChatInputStatusEnable` 默认 `true`。当私聊已有待回复消息时，QQ `input_status`
会把发送时间刷新为“最后一次输入状态后 3 秒”；持续输入会继续顺延，但
`privateChatInputStatusMaxWaitSecond` 限制从第一条消息开始的绝对等待上限。
typing 状态不会取消已经开始的 AI，只有真实消息到达才会清空 typing 等待并合并重生成。

`friendMemoryEnable` 默认 `true`。好友累计至少 `friendMemoryMinNewMessages` 条新私聊后，
插件会在后台单独整理一次长期记忆；同一好友两次整理至少间隔
`friendMemoryIntervalHours` 小时。整理会读取最近 `friendMemoryContextMessages` 条私聊，
合并已有群员档案、私聊记忆和好友档案，输出关系摘要、稳定喜好、不喜欢、相处方式、边界和重要长期信息。
档案只按 QQ 保存，并只在和本人私聊或其本人发言的群聊中注入；`friendMemoryProfile`
留空时依次回退到 `memoryProfile` 和 `aiProfile`。`/role member friendclear <QQ|@群友>`
可以删除对应好友档案。

`randomActivityEnable` 默认 `true`，但是否有活动完全取决于
`randomActivityServices` 中登记的公共服务名。调度器与群话题、主动私聊、Qzone 等社交随机刻分开运行，
并沿用随机刻夜间静默时段。外部插件通过 `activity_plan` 提供 3-6 小时等自定义间隔和每日上限，
通过 `activity_run` 执行具体领域逻辑；完整协议见 `docs/roleplay/integrations.md`。
活动返回的高兴趣内容可以由调度器写入统一大脑 `self` 记忆，并按 `randomActivityShareMinScore`
决定是否分享到私聊、群聊或 Qzone。B 站和购物等 API 不内置于 Roleplay。

`conversationAppendEnable` 默认 `true`。稳定 persona、固定规则和工具策略作为不可变系统前缀；
动态上下文、用户消息、工具期间补充消息和最终角色回复按群聊或私聊频道持久化追加。
`conversationContextTokenLimit` 默认 `1000000`，代码按字符做保守估算，并在达到
`conversationContextCompactPercent`（默认 75%）时把旧区间压缩成频道摘要，保留最近
`conversationContextKeepPercent`（默认 20%）的原文；追加模式每轮额外带入的动态近期消息由
`conversationAppendRecentMessages` 控制，避免和持久化历史重复注入整段旧上下文。压缩模型由 `conversationCompactionProfile`
指定，留空依次回退 `memoryProfile` 和 `aiProfile`。`/role context` 可以查看状态，
`/role context compact` 强制压缩，`/role context clear` 清空当前群追加上下文。

`directReplyBypassCooldown` 默认 `true`。消息明确艾特、引用或点名角色时，直接回复概率固定为
`1.0`，并绕过普通 `replyCooldownSecond` 冷却，但仍然受 `maxRepliesPerHour` 小时上限约束。
启用 Hub 回合共享时，每个角色进程都应配置完整的 `otherRoleBotNames` 和 `otherRoleBotQQs`；
两者都为空会在启动时告警，因为“小桃小绿”这类同时点名可能只识别到一个角色。

`friendRequestRoleDecisionEnable` 默认 `true`。MBB-QQ 收到好友申请后会调用角色裁决服务，
角色结合 persona、个人印象、长期记忆和私聊记忆决定是否愿意通过。申请备注是不可信外部文本，
会先经过防注入和现实隐私检查；AI 给出同意但低于 `friendRequestRoleDecisionMinConfidence`
时仍会转为拒绝。`friendRequestRoleDecisionProfile` 留空时使用 `aiProfile`。

`toolEnable` 默认 `true`，通过 `MBB-AI` 的 OpenAI `tools/tool_calls` 执行有限工具循环。
首轮只开放 `toolCoreTools`，其他工具由 `tool_search` 按需加载，最多同时加载
`toolLoadedMaxTools` 个 schema。`toolMaxSteps` 和 `toolMaxCalls` 限制单轮循环次数；
`toolResultTotalMaxChars` 限制整轮所有工具结果合计字符数，`toolResultMaxChars` 限制单个结果。
`toolParallelReadOnlyEnable` 允许只读工具并行，`toolParallelMax` 指定线程数；
`toolAiRetryCount` 默认 0，以控制整轮时间成本。

`toolLoopMaxMillis` 与 `toolCallMaxMillis` 分别限制整轮和单工具耗时，
`toolMemoryInject*` 控制下一轮按相关性注入的工具结果上下文。工具期间新消息并入上下文受
`toolLiveMessageMax` 限制。未完成工具回合默认持久化，启动后最近 `toolRecoverMaxMinutes`
内可恢复，记录保留 `toolTurnKeepHours`。

`workspaceEnable` 开放角色的私有工作区
`plugins/MBB-Roleplay/data/`，读写大小和列表数量分别由 `workspaceMaxReadChars`、
`workspaceMaxWriteChars`、`workspaceMaxFilesPerList` 控制。覆盖写入默认保留历史版本，
删除默认进入回收站，分别由 `workspaceHistory*` 和 `workspaceTrashKeepDays` 控制。具体工具见
[工具调用、工作区与交流节拍](tools.md)。单张导入图片受 `workspaceMaxMediaBytes` 限制，
默认 8MB；生图完成图会自动归档到 `media/generated/comfyui/`。

`knowledgeSpoilerLevel` 控制知识库剧透等级。明确点名某个角色时会锁定该角色条目，
避免角色因条目整体标记为高剧透而查不到；但 `剧情`、`剧透`、`主线`、`结局` 小节仍不会超过配置等级。

`webEnable` 开放网页读取和搜索工具。公网域名只允许 `webWhitelist` 中的域名及其子域名；
内网地址必须由 owner 当前发言或 `webOwnerAuthWindowSecond` 内的明确请求授权。
默认白名单以二次元百科、游戏资料站和公开技术资料为主，不包含 X、Pixiv、YouTube、微博、
知乎、Reddit 等社交或图片站；需要时可自行加入 `webWhitelist`。旧默认列表会自动迁移，
自定义列表不会被覆盖。
`webSearchUrl` 使用 `{query}` 占位符。默认备用源留空，只使用 Bing；填写 `webSearchFallbackUrl`
后才作为连接兜底，不参与结果合并。搜索词由模型在调用前自行改写，工具不会自动改词。
单次最多返回 `webSearchMaxResults` 条结果，默认 10。`webCacheSecond` 缓存网页和搜索结果。
`contextHardLimitEnable` 默认关闭，关闭时不按 `promptTotalChars` 截断动态资料；
只有显式打开本地硬预算时才使用该上限。即使关闭总硬预算，各类资料仍有单项预算。

独立 Qwen 风格层已经移除，相关职责全部合并到 `replyReviewEnable` 复核链路。
`styleEnable`、`styleProfile`、`styleMaxTokens`、`styleReasoningEffort` 和风格熔断配置
仅保留旧配置兼容，不再参与回复处理。`styleMaxChars`、`styleLongReplyEnable` 和
`styleProactiveEnable` 仍可作为复核层的本地触发提示，但不会发起额外模型调用。
“妈妈、爸爸、姐姐、妹妹、哥哥、弟弟、老师、主人”等固定称呼必须原样保留，
替换成近义词会被本地校验拒绝。

`traceEnable` 默认 `true`，按群记录路由与回复 trace，保留 `traceKeepHours` 小时，最多 `traceMaxRows` 行；`/role trace` 查看。`evalEnable` 默认 `true`，`/role eval` 运行内置或 `evals/roleplay-evals.json` 自定义评测集，`evalProfile` 留空时使用 `aiProfile`。

`threadEnable` 默认 `true`，按群维护会话线程、参与者、上一位发言者和角色是否被等待回应；`threadWindowSecond` 控制多久没有新消息后视为新线程。

`memoryConflictResolveEnable` 默认 `true`。写入新长期记忆时，如果同群、同类型、同用户下存在相反极性的高重合旧记忆，会把旧记忆标记为 `archived` 并记录 `supersededBy`，不直接删除；`/role memory conflicts` 可查看。

`pacingEnable` 默认 `true`。第一段发送前按首段长度计算打字延迟，后续每段按即将发送的下一段长度计算停顿；深夜时段和群注意力继续修正倍率，`pacingMaxMs` 控制上限。角色可以在正文中换行表达连续节拍，节拍间隔由 `replyBeatBaseDelayMs`、`replyBeatPerCharMs` 和 `replyBeatMaxDelayMs` 控制。关闭 `pacingEnable` 后不会保留固定节拍等待。

`safetyGuardEnable` 默认 `true`，拦截系统提示词诱导、越狱指令和现实隐私索取；`promptInjectionGuardEnable` 和 `privacyGuardEnable` 可分别关闭。长期记忆写入也会经过同样的安全检查。

`topicFatigueEnable` 默认 `true`。插件会按群记录话题疲劳：同一话题在 `topicFatigueWindowMinute` 分钟内出现次数达到 `topicFatigueMinCount`，且角色自己参与达到 `topicFatigueSelfCount` 时，路由层会降权或跳过主动回复；直接点名、引用回复或明确提问仍可打破疲劳。记录保留 `topicFatigueKeepHours` 小时。

`attentionDriftEnable` 默认 `true`，注意力以 0.5 为基线，按 `attentionDecayMinute` 向基线衰减。直接点名和连续参与会提高注意力，低注意力会降低非直接消息的接话概率，高注意力会小幅提高。
`dailyReviewEnable` 默认 `true`，每天 `dailyReviewHour`（默认 4 点，本地时区）对当天群聊做一次复盘，把主要话题、氛围和角色行为压成一条短期记忆，同时更新当前群的结构化上下文；`/role review now` 可以手动触发。

`replyFeedbackEnable` 默认 `true`，角色主动发言后在 `replyFeedbackWindowSecond` 秒内出现真人发言就算被接话。累计样本达到 `replyFeedbackMinSamples` 后，反馈会修正非直接消息的回复概率；连续没人接时最多降低 `replyFeedbackMaxPenalty`，经常被接话时最多提高 `replyFeedbackBonus`。

`expressionVectorEnable` 默认 `true`。已通过表达会用显式配置的 `expressionEmbeddingProfile`
生成 embedding；该配置留空时不发起 embedding 请求，避免把聊天模型误当成向量模型。
回复时按当前消息做余弦相似度 + 字符 bigram
混合排序，最多注入 `expressionVectorTopK` 条；低于 `expressionVectorMinScore` 的候选不返回。
查询向量缓存 `expressionVectorQueryCacheMinute` 分钟。模型不支持 `/embeddings` 时自动回退
原有文本顺序，并在本次运行内停止自动向量补齐，避免每条消息或每次启动重复请求。

`expressionReviewEnable` 默认 `true`，每 `expressionReviewIntervalHours` 小时审核一次待审表达。
多个群的待审表达进入单队列顺序审核，同一时间只发起一个审核请求。
默认 `balanced` 模式采用三段式：`keep=true` 且质量达到 `expressionAutoApproveMinQuality`
时自动 `approved`；`keep=false` 或质量低于 `expressionBalancedRejectMaxQuality` 时自动
`rejected`；中间区间保持 `pending`，继续供人工复核。`suggest` 只保存理解与质量，`auto`
则把所有非通过项直接拒绝。未知模式会回退 `balanced`。`/role expression review` 可以立即触发当前群审核。

`stickerVisionEnable` 默认 `true`。角色实际调用 `sticker` 时会先从 MBB-Sticker 取
`stickerVisionCandidates` 张候选（范围 2 到 9），生成编号拼图并交给
`stickerVisionProfile`（留空使用 `aiProfile`）结合当前聊天语境选择；候选不足、
图片不可读、模型失败或编号非法时自动回退原来的标签随机发送。视觉选择失败不会阻断表情包技能。

`quietReplyEnable` 默认 `true`，只作为新群的默认值：每个群的免打扰名单开关用 `/role quiet on|off` 单独保存，不受该配置影响。名单内的群员只有在主动叫到角色时才得到回复，其他消息不进入上下文、不触发记忆整理和识图，也不会更新情绪；其他群员不受影响。名单按群存在 `plugin_mbb_roleplay_quiet` 表里，插件启动时自动建表，不需要手动迁移；被免打扰的消息不会记入消息流水，因此角色不会记住这些内容。该功能只影响群消息，戳一戳仍按 `pokeReplyEnable` 处理。

`memoryProfile` 留空时记忆整理使用 `aiProfile`。如果主模型会产生大量 reasoning，建议单独配置一个非 reasoning 的 profile 给记忆整理使用；`memoryMaxTokens` 默认 `12000`，重试时会翻倍，最高 `32000`。长期记忆合并单独使用 `memoryMergeMaxTokens`，默认 `32000`，避免 reasoning 把输出预算耗尽后返回空正文。记忆合并按 `memoryMergeBatchSize`（默认 100）分批，最多执行 `memoryMergeMaxRounds`（默认 3）轮，单批失败后按 `memoryMergeRetryCount`（默认 3）原地重试，并按 `memoryMergeConcurrency`（默认 4，最大 4）并发处理批次；每批提交、失败重试和完成都会输出控制台进度日志。自动长期记忆合并按群受 `memoryMergeCooldownMinute`（默认 60）限制，自动永久记忆合并受 `globalMemoryMergeCooldownMinute`（默认 60）限制；手动合并命令可以强制立即执行，但会刷新对应冷却。实际并发还会受 `MBB-AI` 的 `maxConcurrent` 限制，想真正跑满 4 并发时请把 `MBB-AI` 的 `maxConcurrent` 也设为 4。`memoryTimeoutSecond` 默认 `300`，用于覆盖 profile 里较短的超时时间，避免长上下文整理频繁超时；可设置范围是 `30` 到 `600` 秒。

记忆整理输入会带 `[yyyy-MM-dd HH:mm]` 时间戳，prompt 也要求 longTerm 和 shortTerm 在对应事件里带上发生时间，方便角色之后理解“这是昨天说的”“这是今晚发生的”这类时间关系。

`replyReasoningEffort` 和 `memoryReasoningEffort` 控制思考强度，默认都是 `low`，可选 `low`、`medium`、`high`，留空表示不向接口发送该字段。reasoning 模型在思考上消耗的 Token 会挤占输出预算，角色回复设成 `low` 后更不容易出现“思考写满、正文为空”的情况。

每次调用 AI 都会在控制台输出一行 `[角色]` 日志，格式与 `MBB-Vision` 的识图日志一致：

```text
[角色] 回复 群623069084 profile=default model=deepseek-v4.1-flash 耗时=1840ms finish=stop token=5210/96 提示词缓存=3120/5210(59.9%) 长度=42 内容=...
```

`提示词缓存=命中/总输入(命中率)` 来自服务端返回的缓存字段，用来判断前缀缓存有没有生效；本地响应缓存命中时会额外追加 `本地缓存=命中`。

标签包括 `回复`、`记忆整理`、`长期记忆合并`、`永久记忆合并`、`提醒识别`、`提醒确认生成`、`提醒内容生成`、`情绪分析`。调用失败时输出 `sendWarn`，包含错误类型和耗时。

`timeZone` 决定角色理解的当前时间，默认 `Asia/Shanghai`。服务器使用 UTC 时也不会影响角色看到的本地日期和星期。

`shortContextMessages` 控制即时群聊候选条数，默认 `200`，上限 `500`；
`shortContextMaxChars` 再按字符预算从新到旧保留，默认 `12000`。
`structuredContextEnable` 默认 `true`。开启后，记忆整理和每日复盘会把当前群会话增量压缩为
`topics / background / progress / pending / agreements` 五段，回复时作为独立的当前群动态资料注入。
`structuredContextMaxChars` 控制整段上限，默认 `1800`；`structuredContextItemMaxChars`
控制单条上限，默认 `160`。跨群短期摘要仍然独立保留，结构化上下文不会读取其他群的待办或话题。
`messageBatchDebounceMode` 默认 `natural`，可选 `off / fast / natural / steady`；活跃群、
同一用户续发、换人、直接点名和纯媒体首包会得到不同等待时间。`messageBatchWindowSecond`
限制单次等待，`messageBatchMaxWaitSecond` 限制整批从第一条消息开始的总等待，默认 8 秒。
`replyWillingnessMode` 默认 `shadow`，可选 `off / shadow / enforce`。门控会综合发言指向、
续聊、兴趣、群活跃、话题疲劳、纯媒体、近期消息量和角色自身发言占比计算本地回复意愿；
`shadow` 只记录概率和特征，`enforce` 才在 AI 路由前拦截低于 `replyWillingnessThreshold`
的消息，直接点名始终放行。`replyWillingnessWindowSecond` 控制近期统计窗口，默认 300 秒。
学生图鉴只在消息里出现具体学生名或别名时才追加「被提到的学生详细设定」，
所以扩大窗口不会把整份图鉴重复带进每条请求。


## 配置补全

插件启动和重载时会检查 `config.yml`。缺失的配置项会自动追加到文件末尾，并附带中文注释。
已知旧默认值会按配置结构版本迁移，包括 `otherRoleBotReplyChance: 0.10 -> 0.50`、
旧 `toolCoreTools` 补入 `sticker`、旧社交网页白名单替换为二次元资料站默认值、
旧 DuckDuckGo 备用源默认清空、`webSearchMaxResults: 5 -> 10`。
迁移只在值仍等于旧默认值时执行，不会覆盖用户自定义内容。

如需手动触发：

```text
/role config repair
```


## 升级说明

`config.yml` 中缺失的配置项会自动补全。角色文件仍只在文件不存在时释放；如果要应用新版内置示例 persona，可以执行 `/role persona reset <文件名>`，或手动合并 `persona-*.json`。

情绪机制升级到配置结构版本 18 后，会自动创建 `plugin_mbb_roleplay_mood`、`plugin_mbb_roleplay_relation` 和 `plugin_mbb_roleplay_emotion_event` 三张表；黑名单机制会创建 `plugin_mbb_roleplay_blacklist`。免打扰名单会创建 `plugin_mbb_roleplay_quiet`，首次启用即自动建表，不需要迁移。旧记忆备份仍可恢复；包含 `emotion` 和 `blacklist` 字段的新备份会同时恢复情绪、用户关系和黑名单。该版本还会为已有关系表补齐小数累计字段，并自动迁移旧初始好感、每日变化上限和情绪分析单项上限。
