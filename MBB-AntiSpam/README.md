# MBB-AntiSpam

MoBoxBot 群聊刷屏治理插件：检测连发、复读、超长文本与艾特刷屏，按累计违规次数逐级处置。

和 `MBB-AIGuard` 的分工：`MBB-AIGuard` 判断**内容风险**（诈骗、色情、广告、自残等），本插件只看**消息行为**（频率、重复、长度、艾特数量），两者互不依赖，可以同时启用。

## 设计原则

- 默认只记录和在控制台输出，**不会自动撤回或禁言**；撤回与禁言需要单独配置阈值。
- **先提醒再处置**：累计窗口内第一次命中只在群里艾特本人提醒一句，不计入处置阶梯，也不私聊打扰。
- **同一波刷屏只算一次**：连续刷屏会被合并成一波违规，不会因为一时的连发就被迅速禁言。
- **参与人数适中时按玩梗处理**：发过同一句话的人数在阈值区间内（默认 3~4 人）时只记录；人太少按个人刷屏处置，人太多则先整群提醒一次，提醒后仍继续刷才对参与者处置。
- **集体刷屏提醒不点名**：整群提醒只在群里说一句，不艾特任何参与者，避免把公屏变成点名现场。
- **纯图片表情连发不处罚**：只记录，不撤回不禁言。
- 管理员与机器人所有者默认豁免，可用 `bypassAdmin` 关闭。
- 每个群可以单独开关，新群按 `defaultGroupEnable` 处理。
- 过短内容（表情、单字回复）不参与连发与复读统计，减少误判。
- 命中记录写入 SQLite，按 `retentionDays` 自动清理；内存里的滑动窗口、消息去重表与提醒记录都会按时间回收，长时间运行不会无限增长。

> 权限前提：撤回消息和禁言分别对应 OneBot 的 `delete_msg` 与 `set_group_ban`，**需要机器人是本群管理员**；机器人不是管理员时 QQ 会拒绝，插件会在控制台记录处置失败，不会中断运行。只记录、提醒和告警不受此限制。

## 检测规则

| 规则 | 判定 | 默认阈值 |
|---|---|---|
| `rate` | 短窗口内连发条数 | 10 秒内允许 6 条，第 7 条命中 |
| `rate-long` | 长窗口内持续刷屏 | 60 秒内允许 20 条，第 21 条命中 |
| `repeat` | **自己**反复发同一内容 | 10 秒内允许 3 条，第 4 条命中 |
| `repeat-long` | 自己长时间复读 | 60 秒内允许 5 条，第 6 条命中 |
| `repeat-group` | **同一句话**在群里被反复发（算所有人，不要求每个人都发过） | 10 秒内允许 6 条，超过即记录；参与人数在区间内按玩梗豁免，过少按个人、过多按集体处置 |
| `long-text` | 单条消息过长 | 超过 800 字符 |
| `mention` | 窗口内艾特人数过多 | 60 秒内允许 8 人，第 9 人命中 |
| `mention-all` | 使用 `@全体成员` | 直接命中 |
| `media-flood` | 单条消息图片表情过多 | 超过 5 个，只记录 |

自己的复读按个人阈值判定，并且不受「多人玩梗」豁免影响：只要一直是同一个人在刷，照常计数。

`repeat-group` 的计数口径需要特别说明：它统计的是**本群所有人的发言**，同一句话在窗口内被反复发就会累计，**不要求群里每个人都发过**——3 个人各发 2 次同样会把他算成 6 次。它和个人阈值 `repeatMaxCount` 的区别只是「算谁的」：个人阈值只数自己发的，群级阈值数全群发的。另外 `repeatGroupCount` 不会低于 `repeatMaxCount`（加载时会自动抬到不低于个人阈值），避免一个人连刷先命中群级规则。

至于「玩梗豁免」，判定依据是**参与人数**而不是条数：发过这句话的不同用户数落在 `repeatBanterMinUsers`（默认 `3`）到 `repeatBanterMaxUsers`（默认 `4`）之间时按玩梗只记录；**低于下限**说明只有自己在刷，按个人刷屏处置；**高于上限**说明是全群集体刷屏，进入"先提醒后处置"的流程。下限最少为 2——1 个人谈不上"一起玩梗"；填小于 2 的值会被抬到 2，填的旧默认 1 会被抬到 3。上限不会小于下限。

### 集体刷屏：先整群提醒，再处置

参与人数超过上限时不会直接动手，而是分两步：

1. **整群提醒**：在群里发一条提示（**不艾特任何参与者**），例如「这条消息发得有点多了（群内复读），大家先停一下～如果继续的话，后面参与的朋友可能会被撤回消息或禁言 5 分钟。」；
2. **提醒后仍继续**：`collectiveWarnCooldownSecond`（默认 `300` 秒）内同一句话还在被刷，就对**提醒之后参与的人**开始计入违规并按阶梯处置——不管这个人在提醒前有没有参与过；提醒前的参与者如果之后没再发，不会被追溯。

细节：

- 同一句话在同一群按冷却时间只提醒一次，不会反复刷提醒；
- 提醒期间每个参与者的每条消息都单独计数（集体刷屏的"一波"就是整群一起刷），所以提醒后继续刷很快会到达 `deleteAfterViolations` / `banAfterViolations`；
- 提醒本身不占用参与者的"首次只提醒"额度，集体场景下每个参与者都不再单独享有首次豁免，保证公平。

| 参与人数（默认配置） | 结果 |
|---|---|
| 1 人 | 个人刷屏，走个人阈值 `repeat` 照常处置 |
| 2 人 | 低于下限，按个人刷屏处置 |
| 3~4 人 | 按玩梗处理，只记录 |
| 5 人及以上 | 先整群提醒一次；提醒后仍继续刷，则对之后的参与者处置 |

复读判定会先做内容指纹：只保留中文与字母并统一小写，忽略空白、标点与数字。因此 `哈哈哈哈哈`、`哈哈哈哈哈。`、`哈哈哈 哈哈` 视为同一内容，`刷屏测试1` 与 `刷屏测试2` 也视为同一内容，改结尾数字或标点不能绕过。

多条规则同时命中时，按权重取最重的一条记录：`@全体成员`（80）> 持续复读（75）> 持续刷屏（70）> 复读（65）> 群内持续复读（62）> 连发（60）> 群内复读（60）> 艾特刷屏（55）> 超长文本（50）> 图片表情连发（40）。

## 违规与处置

命中后先累计违规次数，再按次数决定处置：

| 累计次数 | 处置 |
|---|---|
| 窗口内第 1 次 | 群里艾特本人提醒一句（`forgiveFirst`），不计入阶梯 |
| ≥ `deleteAfterViolations`（默认 2） | 撤回消息（需要机器人是群管理员） |
| ≥ `banAfterViolations`（默认 3） | 禁言 `banDurationMinute` 分钟（默认 5，需要机器人是群管理员） |

违规计数默认按 `violationCountMode: session` 分波统计：同一用户在 `violationCooldownSecond`（默认 60 秒）内的连续命中算同一波，只计一次。也就是说，**一个人一时的连发最多让他被撤回一次，只有隔一段时间又继续刷才会升级到禁言**。改成 `message` 则每条命中都计数，升级更快。

只记录不处置的命中（`media-flood`、多人玩梗的 `repeat-group`）不计入阶梯，事件表里处置记为 `forgiven`。

把 `deleteAfterViolations` 或 `banAfterViolations` 设为 `0` 可以关闭对应处置。

### 禁言时长单位

QQ 的禁言以**分钟**为基本单位，所以配置项是 `banDurationMinute`（默认 `5`，即 5 分钟）。插件内部调用 OneBot 时按 `分钟 × 60` 换算成秒。

旧配置里的 `banDurationSecond` 仍然兼容：配置里**写了哪个就以哪个为准**，同时写了则优先用 `banDurationMinute`；只有旧的秒配置时按 `秒 ÷ 60` 向上取整换算，避免出现 0 分钟。`banDurationSecond` 会在加载时按分钟推导，保证展示与生效一致。

## 指令

| 指令 | 权限 | 说明 |
|---|---|---|
| `/antispam status` | `BOT_ADMIN` | 查看当前群状态、阈值与处置阶梯 |
| `/antispam on [群号]` | `BOT_ADMIN` | 开启当前群或指定群的刷屏治理 |
| `/antispam off [群号]` | `BOT_ADMIN` | 关闭当前群或指定群的刷屏治理 |
| `/antispam groups` | `BOT_ADMIN` | 查看单独开启过的群 |
| `/antispam log [页码]` | `BOT_ADMIN` | 查看当前群最近的刷屏记录 |
| `/antispam clear` | `BOT_ADMIN` | 清空当前群的刷屏记录 |
| `/antispam reset` | `BOT_ADMIN` | 重置全部群的内存统计窗口 |

## 配置

首次运行会释放 `config.yml`。主要开关：

```yaml
enable: true
defaultGroupEnable: true
defaultAction: "log"
alertAdminPrivate: true
alertCurrentGroup: false
bypassAdmin: true
bypassUsers: ""
whitelistUsers: ""
retentionDays: 30
rateEnable: true
rateWindowSecond: 10
rateMaxMessages: 6
repeatEnable: true
repeatWindowSecond: 10
repeatMaxCount: 3
repeatBanterForgive: true
repeatBanterMinUsers: 3
repeatBanterMaxUsers: 4
collectiveWarnCooldownSecond: 300
repeatGroupWindowSecond: 10
repeatGroupCount: 6
longTextEnable: true
longTextMaxChars: 800
mediaFloodEnable: true
mediaFloodMaxCount: 5
mentionEnable: true
mentionWindowSecond: 60
mentionMaxCount: 8
mentionAllEnable: true
forgiveFirst: true
noticeEnable: true
noticeCooldownSecond: 600
violationCountMode: "session"
violationCooldownSecond: 60
deleteAfterViolations: 2
banAfterViolations: 3
violationWindowSecond: 300
banDurationMinute: 5
banDurationSecond: 300
```

其他键见插件资源内的 `config.yml` 注释。

## 数据

事件表：

```text
plugin_mbb_antispam_event(groupID,userID,messageID,rule,score,count,
                          violationCount,action,content,detail,eventTime)
```

群开关表：

```text
plugin_mbb_antispam_group(groupID,enabled,updateTime)
```

## 构建

```powershell
.\build.ps1
```

产物：

```text
out\MBB-AntiSpam.jar
```

## 依赖

只依赖主程序插件 API，没有软依赖，可以和 `MBB-AIGuard` 同时启用。
