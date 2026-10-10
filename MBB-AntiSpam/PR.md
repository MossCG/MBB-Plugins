# V0.5.21.0.2245 新增 MBB-AntiSpam 刷屏治理插件

## 这个 PR 做了什么

新增独立插件 `MBB-AntiSpam`：群聊刷屏治理，检测连发、复读、超长文本、图片表情连发与艾特刷屏，按配置阈值撤回消息或禁言，并提供 `BOT_ADMIN` 指令查看状态与记录。

只做刷屏，不涉及链接治理（链接治理由维护者已在做的部分负责），可以和 `MBB-AIGuard` 同时启用，互不冲突。

## 设计取向

- 默认只记录：`defaultAction: "log"`，开箱不会撤回、不会禁言，撤回与禁言必须显式配置阈值。
- 先提醒再处置：累计窗口内第一次命中只在群里艾特本人提醒一句（语气平和、带冷却），不计入处置阶梯。
- 同一波刷屏只算一次：`violationCountMode: session`，同一用户 60 秒内的连续命中合并成一波，避免一时的连发就被直接禁言。
- 参与人数适中时按玩梗处理：几个人一起复读同一句话只记录；低于下限按个人刷屏处置。
- 集体刷屏先提醒后处置：参与人数超过上限时先发一条不艾特任何参与者的整群提醒，提醒后仍在刷才对之后的参与者处置。
- 管理员默认豁免，每个群可单独开关；命中记录按 `retentionDays` 自动清理，内存里的滑动窗口、消息去重表与提醒记录都按时间回收。
- 机器人不是群管理员时撤回与禁言会被 QQ 拒绝：插件只记录处置失败、不中断运行，`/antispam status` 与 README 都写明了这个前提。

## 检测规则

| 规则 | 判定 | 默认阈值 | 权重 |
|---|---|---|---|
| `rate` | 短窗口内连发条数 | 10 秒内 >6 条 | 60 |
| `rate-long` | 长窗口持续刷屏 | 60 秒内 >20 条 | 70 |
| `repeat` | 自己反复发同一内容 | 10 秒内 >3 条 | 65 |
| `repeat-long` | 自己长时间复读 | 60 秒内 >5 条 | 75 |
| `repeat-group` | 同一句话在群里被反复发（统计所有人，不要求每个人都发过） | 10 秒内 >6 条 | 60 |
| `repeat-group-long` | 群内长时间复读 | 60 秒内 >10 条 | 62 |
| `long-text` | 单条消息过长 | >800 字符 | 50 |
| `mention` | 窗口内艾特人数过多 | 60 秒内 >8 人 | 55 |
| `mention-all` | 使用 `@全体成员` | 直接命中 | 80 |
| `media-flood` | 单条消息图片表情过多 | >5 个（只记录不处置） | 40 |

内容指纹只保留中文与字母、统一小写、忽略空白标点数字，因此 `哈哈哈哈` 与 `哈哈哈哈。`、`刷屏测试1` 与 `刷屏测试2` 视为同一内容，改标点或结尾数字不能绕过。多条规则同时命中时按权重取最重的一条记录。

过短内容（表情、单字回复）不参与连发与复读统计，减少误判。

## 复读的三档处置

按「发过同一句话的不同用户数」判定，而不是按条数：

| 参与人数 | 结果 |
|---|---|
| 1 人 | 走个人阈值 `repeat`，照常处置 |
| 2 人（低于下限） | 按个人刷屏处置 |
| 3~4 人（`repeatBanterMinUsers`~`repeatBanterMaxUsers`） | 按玩梗处理，只记录 |
| ≥5 人（超过上限） | 先整群提醒一次（无艾特）；提醒后仍继续刷，则对「提醒之后参与的人」逐条计数并处置，不管其提醒前是否参与过 |

细节：

- `repeatGroupCount` 不会低于个人阈值 `repeatMaxCount`（加载时自动抬升），避免一个人连刷先命中群级规则。
- `repeatBanterMinUsers` 最少为 2（1 个人谈不上一起玩梗），填更小的值会被抬到 2。
- 整群提醒按 `collectiveWarnCooldownSecond`（默认 300 秒）冷却，同一句话不会反复刷提醒；提醒前参与、提醒后不再发的人不会被追溯。
- 提醒期间的每条消息单独计数（集体刷屏的一波就是整群一起刷），所以提醒后继续刷会很快到达撤回与禁言阈值。
- `forgiveFirst` 给个人的「首次只提醒」不占用集体提醒的额度，保证参与者之间公平。

## 违规与处置

| 累计次数 | 处置 |
|---|---|
| 窗口内第 1 次 | 群里艾特本人提醒一句（`forgiveFirst`），不计入阶梯 |
| ≥ `deleteAfterViolations`（默认 2） | 撤回消息（需机器人为群管理员） |
| ≥ `banAfterViolations`（默认 3） | 禁言 `banDurationMinute` 分钟（默认 5，需机器人为群管理员） |

只记录不处置的命中（纯图片表情连发、参与人数适中的玩梗）不计入阶梯，事件表中处置记为 `forgiven`，`/antispam log` 渲染为「仅提醒」。

禁言以分钟为单位（`banDurationMinute`，QQ 禁言本身以分钟为基本单位），内部调用 OneBot 时乘 60 换算；旧的 `banDurationSecond` 仍然兼容，配置里写了哪个就以哪个为准，两个都写时优先用分钟值。两个阈值设 `0` 可分别关闭撤回与禁言。

## 指令

| 指令 | 权限 | 说明 |
|---|---|---|
| `/antispam status` | `BOT_ADMIN` | 当前群状态、阈值、处置阶梯与人性化设置 |
| `/antispam on [群号]` / `off [群号]` | `BOT_ADMIN` | 开启或关闭当前群、指定群的治理 |
| `/antispam groups` | `BOT_ADMIN` | 已单独设置过的群 |
| `/antispam log [页码]` | `BOT_ADMIN` | 当前群最近的命中记录 |
| `/antispam clear` | `BOT_ADMIN` | 清空当前群记录 |
| `/antispam reset` | `BOT_ADMIN` | 重置全部群的统计窗口 |

## 文件

```text
MBB-AntiSpam/
├─ build.ps1 / build.bat          # 与其它插件同构（build.ps1 带 UTF-8 BOM，供 PowerShell 5.1 解析中文）
├─ README.md                      # 与 docs/MBB-AntiSpam.md 保持一致
└─ src/main/
   ├─ java/org/moboxlab/mbb/antispam/
   │  ├─ AntiSpamPlugin.java      # extends API.Plugin，注册监听器与指令
   │  ├─ AntiSpamListener.java    # 群消息事件入口
   │  ├─ AntiSpamService.java     # 窗口统计、处置阶梯、提醒与告警
   │  ├─ AntiSpamConfig.java      # 配置读取、兼容与夹取
   │  ├─ AntiSpamMessage.java     # 内容提取与指纹
   │  ├─ FloodRuleEngine.java     # 规则判定
   │  └─ AntiSpamCommand.java     # /antispam 指令
   └─ resources/{plugin.json,config.yml}
```

同时更新了根目录 `README.md`（插件列表加一行）、`docs/MBB-AntiSpam.md`、`update.md`、`version.txt`，以及 `.gitignore`（补上运行期数据 `MoBoxBot/`、`data/`、`logs/`、`*.db`、`*.sqlite`、`*.log`，避免误提交数据库与日志）。

数据落库为插件前缀表：`plugin_mbb_antispam_event`（命中事件）与 `plugin_mbb_antispam_group`（群开关）。

配置项共 48 个，全部带中文注释，见 `MBB-AntiSpam/src/main/resources/config.yml`；主要项：

```yaml
enable: true                      # 插件总开关
defaultGroupEnable: true          # 新群默认开关
defaultAction: "log"              # log 记录并输出控制台日志；ignore 静默（不写日志也不写事件表）
alertAdminPrivate: true           # 命中后私信管理员告警
alertCurrentGroup: false          # 是否在群里公开提示
bypassAdmin: true                 # 管理员与所有者豁免

rateWindowSecond: 10              # 限流短窗口
rateMaxMessages: 6
repeatWindowSecond: 10            # 个人复读
repeatMaxCount: 3
repeatGroupWindowSecond: 10       # 群内复读
repeatGroupCount: 6
repeatBanterMinUsers: 3           # 玩梗豁免人数下限（最少 2）
repeatBanterMaxUsers: 4           # 玩梗豁免人数上限
collectiveWarnCooldownSecond: 300 # 集体刷屏整群提醒冷却，0 表示不提醒直接处置

forgiveFirst: true                # 首次只提醒
noticeEnable: true                # 群内艾特提醒
noticeCooldownSecond: 600
violationCountMode: "session"     # 同一波只算一次；message 则每条都计数
violationCooldownSecond: 60
deleteAfterViolations: 2          # 累计几次开始撤回，0 关闭
banAfterViolations: 3             # 累计几次开始禁言，0 关闭
banDurationMinute: 5              # 单次禁言分钟数
retentionDays: 30                 # 命中记录保留天数
```

## 兼容性

- `plugin.json`：`apiVersion: "0.3"`，与当前 API 冻结版本一致。
- 只依赖 `org.moboxlab.moboxbot.API`（加 fastjson），未打包 MoBoxLib、fastjson、sqlite-jdbc、Java-WebSocket。
- 无 `depend` 与 `softDepend`，可与现有插件共存。
- 纯插件改动，未修改主程序版本号。

## 自检与验证

- `.\build-all.ps1 -Bot <MoBoxBot.jar>`：23 个插件全部构建成功；`out/MBB-AntiSpam.jar` 根目录含 `plugin.json`，JAR 内未打包任何外部依赖，主类 `extends org.moboxlab.moboxbot.API.Plugin`。
- 插件版本 `V0.1.6.0.2245`，根目录 `version.txt` 为 `V0.5.21.0.2245`。
- 真实主程序加载：`version=V0.1.6.0.2245`、`enabled=true`、监听器登记成功、`config.yml` 正常释放。
- 行为验证（桩客户端 + 真实 API）共 61 项全绿：规则引擎单测 12、配置夹取 11、长跑稳定性 7、离线集成 31。覆盖三档人数边界（3/4/5 人）、集体提醒只发一次且不带 at、清理后仍能正常判定与处置、无管理员权限时处置失败不影响运行。
- 配置一致性审计：`config.yml` 48 个键与配置类 48 个字段逐键对齐；`README.md` 与 `docs/MBB-AntiSpam.md` 内容一致。

## 复现验证

```powershell
.\build-all.ps1 -Bot "D:\CodeX\Projects\MoBoxBot\out\MoBoxBot.jar"
# 产物：out\MBB-AntiSpam.jar
```

装到 `MoBoxBot/plugins/` 后启动，在群里用 `/antispam status` 查看当前生效阈值即可。
