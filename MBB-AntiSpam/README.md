# MBB-AntiSpam

MoBoxBot 群聊刷屏治理插件：检测连发、复读、超长文本与艾特刷屏，按累计违规次数逐级处置。

和 `MBB-AIGuard` 的分工：`MBB-AIGuard` 判断**内容风险**（诈骗、色情、广告、自残等），本插件只看**消息行为**（频率、重复、长度、艾特数量），两者互不依赖，可以同时启用。

## 设计原则

- 默认只记录和在控制台输出，**不会自动撤回或禁言**；撤回与禁言需要单独配置阈值。
- 管理员与机器人所有者默认豁免，可用 `bypassAdmin` 关闭。
- 每个群可以单独开关，新群按 `defaultGroupEnable` 处理。
- 过短内容（表情、单字回复）不参与连发与复读统计，减少误判。
- 命中记录写入 SQLite，按 `retentionDays` 自动清理。

## 检测规则

| 规则 | 判定 | 默认阈值 |
|---|---|---|
| `rate` | 短窗口内连发条数 | 10 秒内允许 6 条，第 7 条命中 |
| `rate-long` | 长窗口内持续刷屏 | 60 秒内允许 20 条，第 21 条命中 |
| `repeat` | 同一内容反复发送 | 10 秒内同一内容允许 3 条，第 4 条命中 |
| `repeat-long` | 长时间复读 | 60 秒内同一内容允许 5 条，第 6 条命中 |
| `long-text` | 单条消息过长 | 超过 800 字符 |
| `mention` | 窗口内艾特人数过多 | 60 秒内允许 8 人，第 9 人命中 |
| `mention-all` | 使用 `@全体成员` | 直接命中 |

复读判定会先做内容指纹：只保留中文与字母并统一小写，忽略空白、标点与数字。因此 `哈哈哈哈哈`、`哈哈哈哈哈。`、`哈哈哈 哈哈` 视为同一内容，`刷屏测试1` 与 `刷屏测试2` 也视为同一内容，改结尾数字或标点不能绕过。

多条规则同时命中时，按权重取最重的一条记录：`@全体成员`（80）> 持续复读（75）> 持续刷屏（70）> 复读（65）> 连发（60）> 艾特刷屏（55）> 超长文本（50）。

## 处置阶梯

同一条消息命中规则后，按 `violationWindowSecond`（默认 300 秒）窗口内的累计违规次数升级：

| 累计次数 | 处置 |
|---|---|
| 1 | 只记录（控制台日志 + 事件入库 + 管理员私信） |
| ≥ `deleteAfterViolations`（默认 2） | 撤回消息 |
| ≥ `banAfterViolations`（默认 3） | 禁言 `banDurationSecond` 秒 |

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
longTextEnable: true
longTextMaxChars: 800
mentionEnable: true
mentionWindowSecond: 60
mentionMaxCount: 8
mentionAllEnable: true
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
