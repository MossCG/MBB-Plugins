# MBB-Roleplay 配置与部署

## 角色设定

插件首次运行会释放一份内置示例 persona，文件名以插件包内实际文件为准。示例只用于说明格式，实际角色内容由使用者自行维护。

persona 与台词语料统一在 `MBB-Persona` 私有仓库维护：`personas/` 放角色设定，`corpus/` 放语料。把需要的 persona 复制到插件数据目录，把语料复制到数据目录下的 `speech-corpus/`。

persona 支持任意作品角色或原创角色，常用字段包括：

- 名称、别名、身份
- 世界观、学园、组织、阵营或势力
- 外貌、性格、说话方式、口癖
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
otherRoleBotReplyChance: 0.50
maxConsecutiveOtherRoleMessages: 2
shortContextMessages: 120
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
memoryMergeBatchSize: 60
memoryMergeMaxRounds: 3
memoryMergeRetryCount: 1
memoryTimeoutSecond: 300
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
messageBatchEnable: true
messageBatchWindowSecond: 2
messageBatchMaxMessages: 10
messageBatchMaxAgeSecond: 20
batchMaxSegments: 4
turnThreads: 4
addressedOtherMemberSkip: true
splitMessageSuppressSecond: 20
splitMessageSuppressMaxChars: 20
imageAsyncEnable: true
memoryRelevanceSort: true
memoryRelevancePoolSize: 600
memoryRelevanceMaxChars: 5000
globalMemoryRelevanceMaxChars: 4000
pokeReplyEnable: true
pokeBackEnable: true
pokeBackCooldownSecond: 60
pokeStreakWindowSecond: 60
pokeStreakThreshold: 3
quoteReplyEnable: true
promptTotalChars: 26000
routerEnable: true
routerProfile: ""
routerMaxTokens: 2400
routerReasoningEffort: "none"
styleEnable: true
styleMaxChars: 60
styleProfile: ""
styleMaxTokens: 1200
styleReasoningEffort: "low"
styleProactiveEnable: false
replyImageMaxTokens: 4000
replySegmentMaxChars: 30
replySplitPunctuation: "。！？!?；;，、：,:～~…"
replyMaxSegments: 2
maxLongMemories: 150
recentReplyCheckCount: 8
repeatSuppressEnable: false
repeatSimilarityThreshold: 0.72
repeatCheckMinChars: 6
repeatOpeningLimit: 2
repeatCommonRunMinChars: 8
minMessageLength: 2
```

非直接提及、非对话续接、非兴趣话题的消息不会参与回复。回复 prompt 要求每条消息控制在 15 到 30 字之间、硬上限 30 字，一条说不完可以在正文里换行，最多分两段；拆分器会先在 `replySplitPunctuation` 中查找句末标点，找不到再查逗号、顿号、冒号、分号、波浪号等弱标点，最后才按硬上限拆分。切在弱标点时，上半段末尾的标点会被去掉，避免消息以逗号之类的不完整语气结束；省略号「……」不会被从中间劈开——如果断点正好落在它中间，整对会挪到下一段。不要重复同一件事或细节，也不要连续使用同一种开头或口癖。

`memoryProfile` 留空时记忆整理使用 `aiProfile`。如果主模型会产生大量 reasoning，建议单独配置一个非 reasoning 的 profile 给记忆整理使用；`memoryMaxTokens` 默认 `12000`，重试时会翻倍，最高 `32000`。长期记忆合并单独使用 `memoryMergeMaxTokens`，默认 `32000`，避免 reasoning 把输出预算耗尽后返回空正文。记忆合并按 `memoryMergeBatchSize`（默认 60）分批，最多执行 `memoryMergeMaxRounds`（默认 3）轮，单批失败后按 `memoryMergeRetryCount`（默认 1）原地重试；每批开始、失败重试和结束都会输出控制台进度日志。`memoryTimeoutSecond` 默认 `300`，用于覆盖 profile 里较短的超时时间，避免长上下文整理频繁超时；可设置范围是 `30` 到 `600` 秒。

记忆整理输入会带 `[yyyy-MM-dd HH:mm]` 时间戳，prompt 也要求 longTerm 和 shortTerm 在对应事件里带上发生时间，方便角色之后理解“这是昨天说的”“这是今晚发生的”这类时间关系。

`replyReasoningEffort` 和 `memoryReasoningEffort` 控制思考强度，默认都是 `low`，可选 `low`、`medium`、`high`，留空表示不向接口发送该字段。reasoning 模型在思考上消耗的 Token 会挤占输出预算，角色回复设成 `low` 后更不容易出现“思考写满、正文为空”的情况。

每次调用 AI 都会在控制台输出一行 `[角色]` 日志，格式与 `MBB-Vision` 的识图日志一致：

```text
[角色] 回复 群623069084 profile=default model=deepseek-v4.1-flash 耗时=1840ms finish=stop token=5210/96 提示词缓存=3120/5210(59.9%) 长度=42 内容=...
```

`提示词缓存=命中/总输入(命中率)` 来自服务端返回的缓存字段，用来判断前缀缓存有没有生效；本地响应缓存命中时会额外追加 `本地缓存=命中`。

标签包括 `回复`、`记忆整理`、`长期记忆合并`、`永久记忆合并`、`提醒识别`、`提醒确认生成`、`提醒内容生成`、`情绪分析`。调用失败时输出 `sendWarn`，包含错误类型和耗时。

`timeZone` 决定角色理解的当前时间，默认 `Asia/Shanghai`。服务器使用 UTC 时也不会影响角色看到的本地日期和星期。

`shortContextMessages` 控制注入的即时群聊条数，默认 `120`，上限 `300`。学生图鉴只在消息里出现具体学生名或别名时才追加「被提到的学生详细设定」，所以扩大这个窗口不会把整份图鉴重复带进每条请求。


## 配置补全

插件启动和重载时会检查 `config.yml`。缺失的配置项会自动追加到文件末尾，并附带中文注释。旧版本默认的 `otherRoleBotReplyChance: 0.10` 会一次性迁移为 `0.50`。

如需手动触发：

```text
/role config repair
```


## 升级说明

`config.yml` 中缺失的配置项会自动补全。角色文件仍只在文件不存在时释放；如果要应用新版内置示例 persona，可以执行 `/role persona reset <文件名>`，或手动合并 `persona-*.json`。

情绪机制升级到配置结构版本 18 后，会自动创建 `plugin_mbb_roleplay_mood`、`plugin_mbb_roleplay_relation` 和 `plugin_mbb_roleplay_emotion_event` 三张表；黑名单机制会创建 `plugin_mbb_roleplay_blacklist`。旧记忆备份仍可恢复；包含 `emotion` 和 `blacklist` 字段的新备份会同时恢复情绪、用户关系和黑名单。该版本还会为已有关系表补齐小数累计字段，并自动迁移旧初始好感、每日变化上限和情绪分析单项上限。
