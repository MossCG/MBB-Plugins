# MBB-Roleplay

MoBoxBot 角色扮演插件，根据角色设定文件进行群聊扮演，并维护长期与短期记忆。

## 特性

- 群级开启，未开启的群不参与聊天
- 角色设定文件可替换
- 长期记忆：群友印象、群友信息、群内氛围、群梗、角色自己做过的事
- 短期记忆：近几天事件、群友日常、角色当前正在做的事
- 记忆游标按消息 ID 精确定位，AI 返回空短期记忆时也会正常推进，不会反复整理同一批消息
- 记忆整理 JSON 支持代码块、前后说明、尾逗号、注释和字符串换行容错，首次失败会自动严格重试
- 记忆整理按字符数限制单批输入，避免 300 条长消息一次性撑爆模型上下文
- 记忆整理请求默认使用 300 秒超时且不自动重试超时，避免长上下文整理卡住
- 记忆输入和整理结果都会带事件发生时间，方便角色理解“昨天”“今晚”这类时间关系
- 角色认为内容值得长期记住时，可以在回复末尾输出 `<remember>`，插件会剥离标记并单独触发一次记忆整理
- 不逐条回复，只回复角色感兴趣或被直接提及的消息
- 同一群消息按到达顺序串行处理，旧消息不会在新消息插队后继续发出过期回复
- 检测到另一个角色机器人时会显著降低接话概率，并限制双方无人插话时的连续往返次数
- 其他角色机器人发送的图片和表情消息直接忽略，不进入识图和互聊链路
- 机器人互聊概率默认调整为 `0.50`，两轮内至少接话一次的概率约为 75%
- 回复前后会对照最近的角色发言，对高度重复的语义和固定开头进行拦截
- 小绿不再把“嗯”当作固定开场，连续使用会被重复检测拦截
- 每轮回复动态注入当前日期、时间、星期和时区，角色不会自行猜日期
- 支持自然语言定时提醒，到点主动艾特用户，并在控制台输出创建、恢复和触发日志
- 提醒识别只调用 AI，识别失败时安静忽略，不再回退到内置中文时间规则，也不会提示“没有识别出具体时间”
- 中文相对时间支持“一分钟后”“十分钟后”等写法，不再只支持阿拉伯数字
- 同一群多角色部署时，识别到消息明确指向其他角色会跳过，不争抢提醒创建
- 同时点名多个角色时，被点到的角色都会视为直接提及，不会只让句首角色响应
- 提醒创建确认和到点提醒正文都由当前角色 AI 生成，模板仅作为兜底
- AI 聊天过程中可以输出 `<reminder>` 标记，结合上下文创建定时任务，包括角色自己的提醒
- 支持所有群共享的永久记忆，学习来源可限制到白名单群，并支持查看和 JSON 备份
- 合并记忆前会把短期、长期、永久记忆一起快照到 `backup/`，支持按文件名恢复
- 支持角色台词语料检索，按当前消息和上下文注入少量参考台词，并拦截高度照抄
- 启动和重载时自动补全 `config.yml` 缺失项，并补充中文注释
- 群主和管理员视为老师，其他真人成员视为朋友，另一个角色机器人不按群权限归类
- 所有真人成员使用可配置的初始好感度，默认 `70/100`
- `/role memory` 使用图片展示短期记忆和分页后的长期记忆
- 明确艾特其他用户时不会误判为对爱丽丝说话
- 同一话题下允许多个群员继续参与，AI 会判断是否真正接续话题
- 高频群聊模式：秒级回复冷却，默认每小时可回复 180 次
- 单条回复优先控制在 12 字以内、硬上限 20 字，一条说不完最多分两段
- 输出纯文本聊天，不使用 Markdown
- QQ 表情消息直接忽略；安装 `MBB-Vision` 后，图片和表情包可以按配置理解并注入聊天上下文
- 图片理解默认只处理直接提及、连续对话和表情包，普通群图片不会全部送模型
- 用户先发送图片再追问时，会在时间窗口内把原图作为多模态上下文传给模型，图片本身不会单独触发回复
- 同一图片已经识别过时会复用摘要，后续追问不会重复调用识图
- 多个角色同时处理同一图片时，识图请求会按图片哈希合并，避免重复调用视觉模型
- 文字回合默认等待 5 秒，把用户随后补发的表情包语气合并进同一次回复；表情包先到也能被后续文字吸收
- 表情包已开始识别时会暂停回合，识别完成后立即合并；最多额外等待 15 秒，超时按无表情包继续
- 学生图鉴已升级为全身立绘外貌索引，69 个学生补齐下装、腿部、鞋履、尾巴与武器细节；角色识图时会把完整外貌参考传给 `MBB-Vision`
- 角色知道自己的外貌设定；被问起某位学生是谁或长什么样时，会结合共享图鉴的外貌、社团、性格和关系作答
- 学生图鉴不会整份塞进每条请求：常驻的「了解的学生」只保留一句话印象，被点名的学生才按需注入完整外貌，省下的上下文留给聊天本身
- 安装 `MBB-Sticker` 后，角色可以按当前真实标签集输出 `<sticker>tag</sticker>` 发送匹配表情包，不会调用不存在的标签
- 表情包是可选表达，提示词会要求低频自然使用，不会每句话都携带
- 响应戳一戳：被人戳时由角色自己决定是回一句话、戳回去、还是两者都做，戳回去对同一用户有冷却
- 正文出现“戳回去/回戳/戳你”时会自动补齐 `poke-back` 动作，避免只说不做
- 亲密、暧昧或有点过分的要求会以害羞、别扭、撒娇或转移话题回应，不输出露骨内容
- 拒绝 sexy 话题时可用无语、嫌弃、半开玩笑和“变态/hentai”式撒娇吐槽，表现并非讨厌而是拿你没办法
- 记忆整理和合并会注入当前角色身份，按角色视角区分自身经历、他人发言和对别人的印象
- 语气支持省略号、适度填充词、犹豫重复、反问互动，以及情绪化反话和潜台词
- 被艾特或被直接回复时自动引用原消息，模型也可以自己判断引用是否更自然
- 回复由执行层以结构化结果返回，正文和动作在同一次生成里决定，标签不会再漏到聊天里

## 指令

| 指令 | 权限 | 说明 |
|---|---|---|
| `/role status` | `BOT_ADMIN` | 查看当前群状态 |
| `/role enable [群号]` | `BOT_ADMIN` | 开启当前群或指定群 |
| `/role disable [群号]` | `BOT_ADMIN` | 关闭当前群或指定群 |
| `/role groups` | `BOT_ADMIN` | 查看已开启群 |
| `/role reload` | `BOT_ADMIN` | 重载角色设定和配置 |
| `/role config` | `BOT_ADMIN` | 查看配置文件与补全状态 |
| `/role config repair` | `BOT_ADMIN` | 手动补全缺失配置项 |
| `/role bot` | `BOT_ADMIN` | 查看机器人互聊设置 |
| `/role bot chance <0.3-1>` | `BOT_ADMIN` | 设置其他角色机器人的接话概率 |
| `/role bot max <1-10>` | `BOT_ADMIN` | 设置无人插话时的连续回应上限 |
| `/role bot qq ...` | `BOT_ADMIN` | 查看、增删或清空其他角色机器人 QQ |
| `/role bot name ...` | `BOT_ADMIN` | 查看、增删、重置或清空名称识别关键词 |
| `/role memory [页码]` | `BOT_ADMIN` | 以图片查看当前群短期记忆和长期记忆 |
| `/role memory merge` | `BOT_ADMIN` | 手动整理合并当前群长期记忆 |
| `/role memory backup` | `BOT_ADMIN` | 备份全部群的短期、长期、永久记忆 |
| `/role memory backups` | `BOT_ADMIN` | 查看备份文件名列表 |
| `/role memory restore <文件名>` | `OWNER` | 从备份文件恢复全部记忆 |
| `/role forget` | `BOT_ADMIN` | 清空当前群记忆、聊天上下文并轮换 AI 会话 |
| `/role persona [文件名]` | `BOT_ADMIN` | 查看或切换角色设定文件 |
| `/role persona reset <文件名>` | `BOT_ADMIN` | 用内置版本覆盖指定角色设定文件 |
| `/reminder list [页码]` | `EVERYONE` | 查看自己在当前群的待触发提醒 |
| `/reminder show <ID>` | `EVERYONE` | 查看自己的某条提醒详情 |
| `/reminder edit <ID> <时间> <内容>` | `EVERYONE` | 修改自己的提醒时间和内容 |
| `/reminder delete <ID>` | `EVERYONE` | 取消自己的某条提醒 |
| `/reminder clear` | `EVERYONE` | 取消自己在当前群的全部待触发提醒 |
| `/role gmemory [页码]` | `BOT_ADMIN` | 图片查看全局永久记忆 |
| `/role gmemory backup` | `BOT_ADMIN` | 兼容入口，同样创建全部记忆快照 |
| `/role gmemory merge` | `BOT_ADMIN` | 手动整理合并全局永久记忆 |
| `/role gmemory group ...` | `BOT_ADMIN` | 管理永久记忆学习白名单群 |
| `/role speech stats` | `BOT_ADMIN` | 查看台词语料加载状态 |
| `/role speech reload` | `BOT_ADMIN` | 重载台词语料 |
| `/role speech search <文本>` | `BOT_ADMIN` | 调试台词语料检索 |
| `/role speech on` / `off` | `BOT_ADMIN` | 开关台词语料检索 |

## 角色设定

首次运行释放：

```text
./MoBoxBot/plugins/MBB-Roleplay/persona-aris.json
```

默认设定为《蔚蓝档案》的天童爱丽丝：游戏开发部、勇者见习生、RPG 爱好者，重视老师和伙伴，说话偶尔带游戏化表达。

另外内置两份角色设定：

- `persona-momoi.json`：才羽桃井，游戏开发部剧本作家，更活泼主动、情绪外露。
- `persona-midori.json`：才羽绿，游戏开发部美术，安静认真、冷幽默式短吐槽。

桃井补充了“不会主动说自己菜，被问游戏水平时会嘴硬装厉害，被揭穿会尴尬害羞”的特点，绿补充了“小绿”别名和“偷跑”等妹妹侧社区梗。

切换角色：

```text
/role persona persona-momoi.json
/role persona persona-midori.json
/role persona persona-aris.json
```

桃井设定会识别“小桃”“王小兆”“优香大魔王”“给木给木”“苦呀西”等社区梗，但默认不会主动频繁使用。

三份角色设定都补充了外貌、世界观、所属学园、社团、其他学生、专有名词和剧情记忆字段，并额外加载共享的 `students.json` 学生设定表。`appearance` 字段是角色本人的完整自述，覆盖发色、瞳色、光环、服装、配饰、下装、腿部、鞋履、武器、配色与辨识点，角色对自己有清晰认知。旧版 `persona.json` 会在配置迁移时切换到 `persona-aris.json`。

共享的 `students.json` 目前为全身立绘版（`version: 10`），69 名学生都带有下装、腿部、鞋履和武器细节。插件启动时会检查图鉴版本，低于 10 的旧文件会先备份再覆盖升级。

世界观中同时接受“奇普托斯”和“基沃托斯”两种称呼。桃井和绿明确认识凯伊，并知道她与爱丽丝的关系。

默认口癖包括“邦邦咔邦！”、“爱丽丝，了解！”、“光呀！”等，要求低频自然使用，不会每句话都变成游戏台词。

“邦邦咔邦”只会出现在回复句首，作为类似任务启动提示音使用，不会放在句中或句尾。

可以修改名称、身份、性格、说话方式、兴趣、禁忌和行为规则。修改后执行：

```text
/role reload
```

如果希望恢复插件内置设定，可以使用：

```text
/role persona reset persona-momoi.json
/role persona reset persona-midori.json
```

## 关系与好感

群主和管理员若不是其他角色机器人，统一视为老师；其他真人成员视为朋友；另一个角色机器人按角色设定中的同伴关系处理，不会因为群权限被误称为老师。

`initialAffinity` 控制所有真人成员的初始好感度，默认 `70/100`。该值会注入回复提示词，让角色默认保持善意、亲近、愿意接话和帮忙，但不会自动改变群权限或持久化逐人好感数值。

## 依赖

需要安装并启用 `MBB-AI`。图片理解还需要安装并启用 `MBB-Vision`。`MBB-AI` 负责模型调用，`MBB-Vision` 负责图片识别、缓存和复用。

## 默认性能

```yaml
replyCooldownSecond: 5
maxRepliesPerHour: 180
initialAffinity: 70
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
memoryTimeoutSecond: 300
activeMemory: true
imageUnderstandingEnable: true
imageUnderstandingMode: "addressed"
imageUnderstandingMaxPerHour: 30
imageUnderstandingInjectOcr: true
imageUnderstandingProfile: ""
imageUnderstandingMaxChars: 600
imageContextTimeoutSecond: 300
stickerAttachEnable: true
stickerAttachWindowSecond: 5
stickerAttachMaxWaitSecond: 15
pokeReplyEnable: true
pokeBackEnable: true
pokeBackCooldownSecond: 60
quoteReplyEnable: true
promptTotalChars: 16000
routerEnable: true
routerProfile: ""
routerMaxTokens: 1200
routerReasoningEffort: "low"
styleEnable: true
styleMaxChars: 60
styleProfile: ""
styleMaxTokens: 400
styleReasoningEffort: "low"
styleProactiveEnable: false
replyImageMaxTokens: 4000
replySegmentMaxChars: 20
replySplitPunctuation: "。！？!?；;，、：,:～~"
replyMaxSegments: 2
maxLongMemories: 150
recentReplyCheckCount: 8
repeatSimilarityThreshold: 0.72
repeatCheckMinChars: 6
repeatOpeningLimit: 2
minMessageLength: 2
```

非直接提及、非对话续接、非兴趣话题的消息不会参与回复。回复 prompt 要求每条消息优先控制在 12 字以内、硬上限 20 字，一条说不完可以在正文里换行，最多分两段；拆分器会优先在 `replySplitPunctuation` 列出的常用符号处断开，再按硬上限拆分。不要重复同一件事或细节，也不要连续使用同一种开头或口癖。

`memoryProfile` 留空时记忆整理使用 `aiProfile`。如果主模型会产生大量 reasoning，建议单独配置一个非 reasoning 的 profile 给记忆整理使用；`memoryMaxTokens` 默认 `12000`，重试时会翻倍，最高 `32000`。长期记忆合并单独使用 `memoryMergeMaxTokens`，默认 `32000`，避免 reasoning 把输出预算耗尽后返回空正文。记忆合并按 `memoryMergeBatchSize`（默认 60）分批，最多执行 `memoryMergeMaxRounds`（默认 3）轮；每批开始和结束都会输出控制台进度日志。`memoryTimeoutSecond` 默认 `300`，用于覆盖 profile 里较短的超时时间，避免长上下文整理频繁超时；可设置范围是 `30` 到 `600` 秒。

记忆整理输入会带 `[yyyy-MM-dd HH:mm]` 时间戳，prompt 也要求 longTerm 和 shortTerm 在对应事件里带上发生时间，方便角色之后理解“这是昨天说的”“这是今晚发生的”这类时间关系。

`replyReasoningEffort` 和 `memoryReasoningEffort` 控制思考强度，默认都是 `low`，可选 `low`、`medium`、`high`，留空表示不向接口发送该字段。reasoning 模型在思考上消耗的 Token 会挤占输出预算，角色回复设成 `low` 后更不容易出现“思考写满、正文为空”的情况。

每次调用 AI 都会在控制台输出一行 `[角色]` 日志，格式与 `MBB-Vision` 的识图日志一致：

```text
[角色] 回复 群623069084 profile=default model=deepseek-v4.1-flash 耗时=1840ms finish=stop token=5210/96 缓存=否 长度=42 内容=...
```

标签包括 `回复`、`记忆整理`、`长期记忆合并`、`永久记忆合并`、`提醒识别`、`提醒确认生成`、`提醒内容生成`。调用失败时输出 `sendWarn`，包含错误类型和耗时。

`timeZone` 决定角色理解的当前时间，默认 `Asia/Shanghai`。服务器使用 UTC 时也不会影响角色看到的本地日期和星期。

`shortContextMessages` 控制注入的即时群聊条数，默认 `120`，上限 `300`。学生图鉴只在消息里出现具体学生名或别名时才追加「被提到的学生详细设定」，所以扩大这个窗口不会把整份图鉴重复带进每条请求。

## 分层处理

回复流程分成四段，详细设计见 [DESIGN-LAYERED-AI.md](DESIGN-LAYERED-AI.md)。

**路由层**：每条群消息都会先过一遍路由，判断要不要回复、要挂哪些技能、要带哪些资料。
提示词刻意保持小，不带人设正文。规则保留否决权：被直接艾特必须回复，冷却中必须沉默，
路由层不能推翻。路由输出被 reasoning 截断时自动提高 Token 重试一次，仍失败则回退到规则决策，
不会因为路由出错而漏掉艾特。另一个角色机器人的消息仍受 `otherRoleBotReplyChance`
概率控制，AI 路由不能绕过机器人互聊概率。

```yaml
routerEnable: true
routerProfile: ""            #留空则使用 aiProfile
routerMaxTokens: 1200
routerReasoningEffort: "low"
```

**技能注册表**：技能按声明注册，执行层只能调用已注册且在开放清单内的技能。当前注册了
`reminder`、`memory`、`global-memory`、`sticker`、`poke-back` 五个。旧的
`<reminder>` / `<remember>` / `<global_remember>` / `<sticker>` 标签仍然兼容，
会被转换成同一批技能调用。

**资料预算**：角色核心、外貌、学生名录、学生详细设定、长期/全局/短期记忆、最近发言、
台词示例各自独立成项，常驻项必带，其余按路由点名的 id 注入，总量受 `promptTotalChars`
限制。上限存在的意义是防止某一份资料把整条提示词撑爆。

```yaml
promptTotalChars: 16000
```

**风格层**：默认不调用，先用本地规则判断回复是否带 AI 味。命中超长、破折号、Markdown
残留、AI 味词、列点、成对引号时才改写一次；改写不改事实、不增删信息、不动表达动作，
失败或明显变长时直接用原文。

```yaml
styleEnable: true
styleMaxChars: 60
styleProactiveEnable: false   #主动发言时是否也触发风格层
```

## 戳一戳与引用

被人戳一戳时，插件走和群消息同一条处理链路，只是把当前消息换成「有人戳了你」。
角色可以只回一句话、只戳回去、两者都做，或者干脆不理会，由执行层自己判断。

```yaml
pokeReplyEnable: true       #是否响应戳一戳
pokeBackEnable: true        #是否允许戳回去
pokeBackCooldownSecond: 60  #对同一用户戳回去的最小间隔
```

`MBB-Poke` 已经在处理戳一戳，所以本插件检测到它处于启用状态时会自动跳过戳一戳事件，
避免同一件事回两次。

被艾特或被直接回复时，插件会引用原消息：

```yaml
quoteReplyEnable: true   #回复被艾特或被直接回复的消息时是否引用原消息
```

引用由规则强制，模型可以额外建议引用，但无法取消规则要求的引用。

## 结构化输出

执行层按约定返回结构化结果，正文和表达动作在同一次生成里决定：

```json
{"text":"戳你哦！","actions":[{"type":"poke_back"}],"quote":true}
```

`actions` 只能从本轮开放的动作里选，未开放的动作会被丢弃。解析失败时退化为「整段内容
当正文、不带任何动作」，不会因为格式问题丢掉回复。动作发出前还会校验冷却与目标，
校验失败只丢弃该动作，正文照常发送。

## 自然语言提醒

角色会调用 AI 识别提醒意图和时间，并把任务写入 SQLite，重启后仍会恢复。识别失败时安静忽略，不再回退到内置时间规则，也不会再发“没有识别出具体时间”的提示：

```text
下午三点提醒我干活
明天早上八点提醒我开会
10分钟后提醒我喝水
晚上八点叫我交作业
```

到点后会调用角色 AI，按当前人设生成提醒正文，再主动发送：

```text
@用户 该干活了
```

AI 不可用或返回空内容时，会回退到固定模板。创建时保存的角色关系和任务信息也会一起提供给 AI。

控制台会输出：

```text
[提醒] AI识别成功 群xxx 用户xxx 时间=2026-10-04 15:00 内容=干活
[提醒] AI确认生成 #12 群xxx 用户xxx 内容=好，下午三点我会提醒你干活~
[提醒] 创建 #12 群xxx 用户xxx 时间=2026-10-04 15:00 内容=干活 识别=AI
[提醒] 触发 #12 群xxx 用户xxx 内容=干活
[提醒] AI生成 #12 群xxx 用户xxx 内容=该干活了
[提醒] 发送成功 #12 群xxx 用户xxx
```

`reminderEnable` 控制是否启用，`reminderAiParse` 控制是否使用 AI 识别（关闭时改用内置规则解析），`reminderMaxDays` 控制最长提前天数，默认 30 天。

提醒消息会携带不可见标记。其他角色机器人识别到该标记时会跳过，避免两个角色互相抢答，但 QQ 消息中不会出现固定文字前缀。角色名识别会同时参考配置和内置的桃井/绿/爱丽丝名称。

用户还可以通过 `/reminder` 管理自己的任务：

```text
/reminder list
/reminder show 12
/reminder edit 12 明天早上八点 开会
/reminder delete 12
/reminder clear
```

用户只能查看和修改自己在当前群的提醒；编辑会重新排期，旧定时器不会提前触发。

自我提醒会保存原始上下文，并在到点生成时明确“这是角色自己该做的事”，避免把“让小桃十点睡觉”误写成提醒用户。

## 永久记忆

永久记忆存储在独立表中，所有群共享，不绑定具体用户。适合保存角色学到的说话方式、语气、生活习惯、知识、群梗和通用注意事项。

学习白名单：

```text
/role gmemory group list
/role gmemory group add 123456789,987654321
/role gmemory group remove 123456789
/role gmemory group clear
```

只有白名单群的消息会参与永久记忆自动整理。角色也可以在白名单群输出：

```text
<global_remember>角色学到的非用户绑定经验</global_remember>
```

解析器兼容 `globalremember`、`global-remember`、`global remember` 等大小写和分隔符变体，这些标签都会被剥离，不会发送到 QQ。

用户主动通过聊天诱导 `<remember>` 或 `<global_remember>` 时，只有 owner 可以生效；角色在正常聊天中自发触发的记忆不受此限制。诱导关键词包括“调用全局记忆”“全局记忆功能”“永久记忆”“记一下”“记住这个”“记忆一下”“记下来”。

查看与备份：

```text
/role gmemory
/role gmemory 2
/role gmemory backup
/role gmemory merge

/role memory backup
/role memory backups
/role memory restore memory-backup-20261005-234512-123.json
```

备份文件写入插件数据目录下的 `backup/`：

```text
./MoBoxBot/plugins/MBB-Roleplay/backup/memory-backup-yyyyMMdd-HHmmss-SSS.json
```

每次长期记忆合并或永久记忆合并前，都会自动生成一次全量快照。快照包含：

- `shortTerm`：所有群的短期记忆
- `longTerm`：所有群的长期记忆
- `globalMemory`：全局永久记忆

恢复时先自动备份当前记忆，再用指定文件覆盖三类记忆。恢复操作仅 `OWNER` 可用。

永久记忆超过 `globalMemoryMaxItems` 时不会直接删除，而是先做相似排序，再按 `memoryMergeBatchSize` 分批调用 AI 合并。合并读取快照内的全部永久记忆，不会只取前 300 条；合并失败时保留原数据。

当前群的长期记忆超过 `maxLongMemories` 时也会异步做相似排序和分批 AI 合并，保留用户印象、群内氛围、群梗、角色行为和重要事件。合并开始时记录快照最大 ID，只删除快照内旧记忆；合并期间新增的记忆会保留。合并失败时保留原数据。

`/role memory merge` 和 `/role gmemory merge` 属于手动合并，即使当前数量没有超过上限，也会强制至少执行一轮分批合并。

## 台词语料检索

语料文件放在插件数据目录：

```text
speech-corpus/speech-corpus-aris.jsonl
speech-corpus/speech-corpus-momoi.jsonl
speech-corpus/speech-corpus-midori.jsonl
```

插件内置了三份初始语料，首次运行会自动释放到上述目录；已有语料文件不会被覆盖。

每行一个 JSON 对象：

```json
{"id":"aris-000001","role":"aris","text":"爱丽丝明白了！","tags":["日常","任务"],"emotion":"curious","scene":"group_chat","source":"设定集","weight":1.0,"spoiler":0}
```

插件会使用当前消息和最近群聊上下文做本地字符 n-gram 检索，把少量参考台词注入角色提示词。参考示例只用于学习表达方式，生成后还会做相似度检测，避免直接照抄台词。

相关配置：

```yaml
speechCorpusEnable: true
speechCorpusDirectory: "speech-corpus"
speechRetrievalCount: 8
speechRetrievalMaxChars: 1200
speechRetrievalMinScore: 0.35
speechSimilarityThreshold: 0.78
speechSimilarityMinChars: 6
```

角色 AI 可以在普通回复末尾输出结构化标记来主动创建任务：

```text
好，那我们玩到八点<reminder>{"time":"2026-10-04 20:00:00","task":"玩游戏到八点","target":"self"}</reminder>
```

标记及其内容不会发送到 QQ。`target` 为 `self` 时是角色自己的提醒，为 `user` 时提醒当前群友。到点后仍然会调用角色 AI 生成正文，并通过消息段艾特对应用户。

角色也可以直接查询、修改和删除当前用户在当前群的提醒：

```text
<reminder>{"action":"list"}</reminder>
<reminder>{"action":"delete","id":12}</reminder>
<reminder>{"action":"edit","id":12,"time":"2026-10-05 08:00:00","task":"开会"}</reminder>
```

角色提示词中会包含当前用户的待触发提醒列表，因此可以直接回答“我有哪些提醒”，不需要额外查询命令。

## 多角色部署

同一群部署桃井、绿、爱丽丝等多个角色时，建议保留默认的 `otherRoleBotNames`，或通过 `otherRoleBotQQs` 明确填写其他角色机器人的 QQ。默认接话概率为 `0.50`，在没有真人插话时最多连续处理 2 条对方消息。

常用管理命令：

```text
/role bot status
/role bot chance 0.5
/role bot max 2
/role bot qq add 1004331369,123456789
/role bot qq list
/role bot name reset
```

名称识别支持 `list`、`add`、`remove`、`set`、`clear` 和 `reset`。QQ 识别支持 `list`、`add`、`remove`、`set` 和 `clear`。

## 配置补全

插件启动和重载时会检查 `config.yml`。缺失的配置项会自动追加到文件末尾，并附带中文注释。旧版本默认的 `otherRoleBotReplyChance: 0.10` 会一次性迁移为 `0.50`。

如需手动触发：

```text
/role config repair
```

## 记忆标记

默认开启 `activeMemory`。模型判断当前消息包含值得长期记住的人物信息、群梗或自身重要行为时，会把聊天正文放在标签前，把要记忆的内容放在标签后：

```text
周末我也有空<remember>用户周末要参加活动
```

`<remember>` 标签及其后的内容不会发送到 QQ。插件只发送标签前的聊天正文，并把标签后的内容作为主动记忆输入；如果标签后没有内容，则使用当前群最近未整理的消息。

## 记忆图片

`/role memory` 会输出记忆图片，顶部显示角色、群号和长期记忆总数，中部显示短期记忆，下方按每页 12 条展示长期记忆的类型、对象 QQ、重要度和内容。

```text
/role memory
/role memory 2
```

## 清空上下文

`/role forget` 会同时清空当前群的长期记忆、短期记忆、消息上下文和运行态回复状态，并轮换持久化 AI 会话标识。轮换后即使模型服务端按 `sessionId` 保留会话，也会从新的空上下文开始。

## 升级说明

`config.yml` 中缺失的配置项会自动补全。角色文件仍只在文件不存在时释放；如果要应用新版桃井或绿设定，可以执行 `/role persona reset <文件名>`，或手动合并 `persona-*.json`。
