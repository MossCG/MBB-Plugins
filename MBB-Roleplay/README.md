# MBB-Roleplay

MoBoxBot 角色扮演插件，根据 `persona.json` 进行群聊扮演，并维护长期与短期记忆。

## 特性

- 群级开启，未开启的群不参与聊天
- 角色设定文件可替换
- 长期记忆：群友印象、群友信息、群内氛围、群梗、角色自己做过的事
- 短期记忆：近几天事件、群友日常、角色当前正在做的事
- 记忆游标按消息 ID 精确定位，AI 返回空短期记忆时也会正常推进，不会反复整理同一批消息
- 记忆整理 JSON 支持代码块、前后说明、尾逗号、注释和字符串换行容错，首次失败会自动严格重试
- 记忆整理按字符数限制单批输入，避免 300 条长消息一次性撑爆模型上下文
- 角色认为内容值得长期记住时，可以在回复末尾输出 `<remember>`，插件会剥离标记并单独触发一次记忆整理
- 不逐条回复，只回复角色感兴趣或被直接提及的消息
- 检测到另一个角色机器人时会显著降低接话概率，并限制双方无人插话时的连续往返次数
- 机器人互聊概率默认调整为 `0.50`，两轮内至少接话一次的概率约为 75%
- 回复前后会对照最近的角色发言，对高度重复的语义和固定开头进行拦截
- 小绿不再把“嗯”当作固定开场，连续使用会被重复检测拦截
- 每轮回复动态注入当前日期、时间、星期和时区，角色不会自行猜日期
- 支持自然语言定时提醒，到点主动艾特用户，并在控制台输出创建、恢复和触发日志
- 启动和重载时自动补全 `config.yml` 缺失项，并补充中文注释
- 群主和管理员视为老师，其他真人成员视为朋友，另一个角色机器人不按群权限归类
- 所有真人成员使用可配置的初始好感度，默认 `70/100`
- `/role memory` 使用图片展示短期记忆和分页后的长期记忆
- 明确艾特其他用户时不会误判为对爱丽丝说话
- 同一话题下允许多个群员继续参与，AI 会判断是否真正接续话题
- 高频群聊模式：秒级回复冷却，默认每小时可回复 180 次
- 输出纯文本聊天，不使用 Markdown
- 图片、图片表情包和 QQ 表情消息直接忽略，不记录、不进入记忆、不触发回复

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
| `/role forget` | `BOT_ADMIN` | 清空当前群记忆、聊天上下文并轮换 AI 会话 |
| `/role persona [文件名]` | `BOT_ADMIN` | 查看或切换角色设定文件 |
| `/role persona reset <文件名>` | `BOT_ADMIN` | 用内置版本覆盖指定角色设定文件 |

## 角色设定

首次运行释放：

```text
./MoBoxBot/plugins/MBB-Roleplay/persona.json
```

默认设定为《蔚蓝档案》的天童爱丽丝：游戏开发部、勇者见习生、RPG 爱好者，重视老师和伙伴，说话偶尔带游戏化表达。

另外内置两份角色设定：

- `persona-momoi.json`：才羽桃井，游戏开发部剧本作家，更活泼主动、情绪外露。
- `persona-midori.json`：才羽绿，游戏开发部美术，安静认真、冷幽默式短吐槽。

桃井补充了“又菜又爱玩”的特点，绿补充了“小绿”别名和“偷跑”等妹妹侧社区梗。

切换角色：

```text
/role persona persona-momoi.json
/role persona persona-midori.json
/role persona persona.json
```

桃井设定会识别“小桃”“王小兆”“优香大魔王”“给木给木”“苦呀西”等社区梗，但默认不会主动频繁使用。

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

需要安装并启用 `MBB-AI`。`MBB-AI` 负责模型调用、纯文本输出清洗和会话统计。

## 默认性能

```yaml
replyCooldownSecond: 5
maxRepliesPerHour: 180
initialAffinity: 70
reminderEnable: true
reminderMaxDays: 30
interestReplyChance: 0.65
conversationWindowSecond: 180
continuationReplyChance: 0.80
otherParticipantReplyChance: 0.45
otherRoleBotReplyChance: 0.50
maxConsecutiveOtherRoleMessages: 2
shortContextMessages: 80
memoryUpdateMessages: 50
memoryExtractMessages: 300
memoryExtractMaxChars: 16000
memoryExtractBatches: 3
memoryProfile: ""
timeZone: "Asia/Shanghai"
memoryMaxTokens: 12000
activeMemory: true
maxLongMemories: 150
recentReplyCheckCount: 8
repeatSimilarityThreshold: 0.72
repeatCheckMinChars: 6
repeatOpeningLimit: 2
minMessageLength: 2
```

非直接提及、非对话续接、非兴趣话题的消息不会参与回复。回复 prompt 要求尽量只输出一句话，不要反复纠缠同一个生活细节，也不要连续使用同一种开头或口癖。

`memoryProfile` 留空时记忆整理使用 `aiProfile`。如果主模型会产生大量 reasoning，建议单独配置一个非 reasoning 的 profile 给记忆整理使用；`memoryMaxTokens` 默认 `12000`，重试时会翻倍，最高 `32000`。

`timeZone` 决定角色理解的当前时间，默认 `Asia/Shanghai`。服务器使用 UTC 时也不会影响角色看到的本地日期和星期。

## 自然语言提醒

角色会识别常见的提醒表达，并把任务写入 SQLite，重启后仍会恢复：

```text
下午三点提醒我干活
明天早上八点提醒我开会
10分钟后提醒我喝水
晚上八点叫我交作业
```

到点后会主动发送：

```text
@用户 该干活了
```

控制台会输出：

```text
[提醒] 创建 #12 群xxx 用户xxx 时间=2026-10-04 15:00 内容=干活
[提醒] 触发 #12 群xxx 用户xxx 内容=干活
[提醒] 发送成功 #12 群xxx 用户xxx
```

`reminderEnable` 控制是否启用，`reminderMaxDays` 控制最长提前天数，默认 30 天。

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
