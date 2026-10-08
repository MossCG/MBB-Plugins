# MBB-Roleplay 记忆系统

## 群员个人记忆

除了好感、信任、厌烦这些数值，插件还按 `(群号, QQ)` 给每个群员维护一份**个人印象**，字段有四个：

| 字段 | 含义 |
|---|---|
| `alias` | 他希望被怎么称呼，或角色应该怎么称呼他 |
| `likes` | 他明确说过的喜好 |
| `dislikes` | 他明确说过的不喜欢 |
| `notes` | 应该怎么和他相处，例如能不能开玩笑、要不要少吐槽 |

这份印象由**情绪分析那一轮 AI 调用顺手产出**，不额外增加调用次数：分析提示词会要求模型在返回情绪增量的同时给出 `member` 字段，
只写这个群员自己说过或明确表现出来的内容，没有新信息就留空字符串，不猜、不复述旧印象，也不记录真实姓名住址等现实隐私。
空字段不会覆盖已有内容，只有非空字段才会更新。

命中的印象作为 `member.profile` 资料常驻注入（优先级 88，字符上限 600），排在长期记忆之前，
因为它是当前发言者专属的。`/role member [QQ]` 可以查看，`/role member clear <QQ>` 清空。
这份数据也会一起进 `/role memory backup` 的快照，恢复时一并还原。

群级短期情绪包含心情、精力和耐心，会随时间回落到角色人格基线。每个 persona 可以在 `emotionBaseline` 中配置自己的基线；未配置时使用通用默认值。

好感度达到 `intimacyCloseAffinity` 后会逐步接受轻微亲密互动，达到 `intimacyVeryCloseAffinity` 后可以自然接受抱抱、牵手、贴贴、靠肩、摸头和膝枕等日常亲密举动。botOwner 的亲密关系按“妈妈”处理，不进入恋爱方向。

情绪不会改写回复结构。执行层仍然只返回：

```json
{"text":"聊天正文","actions":[],"quote":false}
```

回复完成后，插件可以在后台异步调用 AI 分析语义原因。这个分析不阻塞当前回复，也不会把分析提示、数值或内部原因发送到 QQ。

```yaml
emotionEnable: true
memberMemoryEnable: true
memberFieldMaxChars: 120
initialAffinity: 65
initialTrust: 60
initialGroupAdminMultiplier: 1.05
initialBotAdminMultiplier: 1.10
initialBotOwnerMultiplier: 1.15
intimacyCloseAffinity: 75
intimacyVeryCloseAffinity: 90
emotionDecayMinute: 30
emotionEventCooldownSecond: 300
emotionAnalyzeEnable: true
emotionAnalyzeMode: "significant"
emotionAnalyzeCooldownSecond: 300
emotionReasonMaxChars: 120
emotionReasonMinStrength: 40
emotionReasonDecayDays: 30
```

管理命令：

```text
/role mood
/role emotion 123456789
/role emotion affinity set 123456789 95
/role emotion reason set 123456789 讨厌这个人，因为他在冒名顶替我
/role emotion reason clear 123456789
/role blacklist add 123456789 恶意刷屏
/role blacklist list
```

## 四份记忆的分工

| 记忆 | 粒度 | 记什么 | 更新时机 | 注入 |
|---|---|---|---|---|
| 短期记忆 | 每群一份 | 近几天的事件、群友日常、角色正在做的事（临时工作状态只作背景） | 每 `memoryUpdateMessages` 条消息整理一次，整段重写 | 常驻，优先级 80，上限 1500 字 |
| 长期记忆 | 每群按条目，带 `subjectID` | 群内氛围、群梗、角色行为、**群员做过的具体事情**（`user_event`），都带发生时间 | 同一次整理产出，单批最多 20 条 | 常驻，优先级 90，上限 5200 字，按相关性排序 |
| 永久记忆 | 跨群共享 | 角色自己学到的说话方式、语气、习惯、经验教训（`lesson`）、群梗 | 同一次整理产出，仅白名单群 | 常驻，优先级 85，上限 4200 字，按相关性排序 |
| 群员个人记忆 | 每群每 QQ 一份 | 称呼、喜好、不喜欢、相处方式 | 情绪分析那一轮顺手产出 | 常驻，优先级 88，上限 600 字 |

边界约定：

1. **人物属性归群员个人记忆**。群员的称呼、喜好、性格不写进长期记忆；长期记忆里提到某个群员时只记他做过的具体事情，类型用 `user_event`。记忆整理时会把本批涉及群员的既有印象一并喂给模型，避免重复记录；合并旧数据时，`user_impression` / `user_info` 里的具体事件改写成 `user_event` 保留，纯属性描述丢弃。
2. **官方设定归知识库**。游戏设定、专有名词、剧情事实由知识库负责，不写进永久记忆；永久记忆只记角色自己学到的东西，所以类型里的 `knowledge` 已经改成 `lesson`（经验教训）。
3. **短期是滚动窗口，长期是提炼结论**。短期每轮整段重写，自然遗忘；长期增量累积，超过 `maxLongMemories` 时合并压缩。
4. **群员印象是增量修订**。情绪分析会把该群员已有的印象一起喂给模型，让它补充或修正，而不是每次从零重写；空字段不会覆盖已有内容。
5. **临时工作状态不写成长期记忆**。例如“正在写第二幕”“改第几张画稿”“赶稿进度”最多作为短期背景，并注明只在相关话题出现时提及，不会变成每轮都注入的当前任务。


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
- `emotion`：群级情绪、用户关系、用户级情绪原因和情绪事件流水
- `blacklist`：按群用户黑名单和操作原因

恢复时先自动备份当前记忆，再用指定文件覆盖记忆和情绪关系状态。恢复操作仅 `OWNER` 可用。

永久记忆超过 `globalMemoryMaxItems` 时不会直接删除，而是先做相似排序，再按 `memoryMergeBatchSize` 分批调用 AI 合并。合并读取快照内的全部永久记忆，不会只取前 300 条；合并失败时保留原数据。

当前群的长期记忆超过 `maxLongMemories` 时也会异步做相似排序和分批 AI 合并，保留用户印象、群内氛围、群梗、角色行为和重要事件。合并开始时记录快照最大 ID，只删除快照内旧记忆；合并期间新增的记忆会保留。合并失败时保留原数据。

`/role memory merge` 和 `/role gmemory merge` 属于手动合并，即使当前数量没有超过上限，也会强制至少执行一轮分批合并。执行完成后会把原条数、合并后条数和合并期间新增保留数量反馈到命令发起所在的群或私聊；失败、无需合并或结果异常时也会返回对应原因。


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

