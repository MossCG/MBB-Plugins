# MBB-AntiSpam

MoBoxBot 群聊刷屏治理插件：检测连发、复读、超长文本与艾特刷屏，按累计违规次数逐级处置。

和 `MBB-AIGuard` 的分工：`MBB-AIGuard` 判断**内容风险**（诈骗、色情、广告、自残等），本插件只看**消息行为**（频率、重复、长度、艾特数量），两者互不依赖，可以同时启用。

## 设计原则

- 默认只记录和在控制台输出，**不会自动撤回或禁言**；撤回与禁言需要单独配置阈值。
- **先提醒再处置**：累计窗口内第一次命中只私信提醒本人，不计入处置阶梯。
- **同一波刷屏只算一次**：连续刷屏会被合并成一波违规，不会因为一时的连发就被迅速禁言。
- **多人一起复读不处罚**：全群一起刷同一句话按玩梗处理，只记录。
- **纯图片表情连发不处罚**：只记录，不撤回不禁言。
- 管理员与机器人所有者默认豁免，可用 `bypassAdmin` 关闭。
- 每个群可以单独开关，新群按 `defaultGroupEnable` 处理。
- 过短内容（表情、单字回复）不参与连发与复读统计，减少误判。
- 命中记录写入 SQLite，按 `retentionDays` 自动清理。

## 检测规则

| 规则 | 判定 | 默认阈值 |
|---|---|---|
| `rate` | 短窗口内连发条数 | 10 秒内允许 6 条，第 7 条命中 |
| `rate-long` | 长窗口内持续刷屏 | 60 秒内允许 20 条，第 21 条命中 |
| `repeat` | **自己**反复发同一内容 | 10 秒内允许 3 条，第 4 条命中 |
| `repeat-long` | 自己长时间复读 | 60 秒内允许 5 条，第 6 条命中 |
| `repeat-group` | **全群**一起发同一句话 | 10 秒内允许 6 条，超过即记录；多人参与按玩梗豁免 |
| `long-text` | 单条消息过长 | 超过 800 字符 |
| `mention` | 窗口内艾特人数过多 | 60 秒内允许 8 人，第 9 人命中 |
| `mention-all` | 使用 `@全体成员` | 直接命中 |
| `media-flood` | 单条消息图片表情过多 | 超过 5 个，只记录 |

自己的复读按个人阈值判定，并且不受「多人玩梗」豁免影响：只要一直是同一个人在刷，照常计数。

复读判定会先做内容指纹：只保留中文与字母并统一小写，忽略空白、标点与数字。因此 `哈哈哈哈哈`、`哈哈哈哈哈。`、`哈哈哈 哈哈` 视为同一内容，`刷屏测试1` 与 `刷屏测试2` 也视为同一内容，改结尾数字或标点不能绕过。

多条规则同时命中时，按权重取最重的一条记录：`@全体成员`（80）> 持续复读（75）> 持续刷屏（70）> 复读（65）> 群内持续复读（62）> 连发（60）> 群内复读（60）> 艾特刷屏（55）> 超长文本（50）> 图片表情连发（40）。

## 违规与处置

命中后先累计违规次数，再按次数决定处置：

| 累计次数 | 处置 |
|---|---|
| 窗口内第 1 次 | 只私信提醒本人（`forgiveFirst`），不计入阶梯 |
| ≥ `deleteAfterViolations`（默认 2） | 撤回消息 |
| ≥ `banAfterViolations`（默认 3） | 禁言 `banDurationSecond` 秒 |

违规计数默认按 `violationCountMode: session` 分波统计：同一用户在 `violationCooldownSecond`（默认 60 秒）内的连续命中算同一波，只计一次。也就是说，**一个人一时的连发最多让他被撤回一次，只有隔一段时间又继续刷才会升级到禁言**。改成 `message` 则每条命中都计数，升级更快。

只记录不处置的命中（`media-flood`、多人玩梗的 `repeat-group`）不计入阶梯，事件表里处置记为 `forgiven`。

把 `deleteAfterViolations` 或 `banAfterViolations` 设为 `0` 可以关闭对应处置。

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
repeatBanterMinUsers: 1
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
