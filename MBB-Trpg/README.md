# MBB-Trpg

MoBoxBot 的跑团主持插件：在一个 QQ 群里开一局 TRPG，提供 CoC 风格 d100 检定、局面状态管理、
剧本导入与 AI 主持。

- 规格总表：[docs/MBB-Trpg.md](../docs/MBB-Trpg.md)
- 呈现规范：[docs/MBB-Trpg-presentation.md](../docs/MBB-Trpg-presentation.md)
- 剧本格式：[docs/scripts/README.md](../docs/scripts/README.md)
- 验收记录：[docs/MBB-Trpg-acceptance.md](../docs/MBB-Trpg-acceptance.md)

## 能力概览

- **一局一团**：每群同时只跑一局，四态 `NONE` / `RECRUITING` / `RUNNING` / `ENDED`，
  招募超 24 小时、进行中超 7 天无局内活动时惰性归档。
- **人类 KP 主导**：`start` 建局者即 KP，掷骰、推进场景、代掷、对抗、SAN、代管与跳过都由 KP 发起，
  AI 主持只是可选的叙述员，不改变任何状态。
- **d100 检定**：三档难度、六档成功等级、大成功/大失败分界、奖励骰与惩罚骰、
  对抗判定、SAN 损失与临时疯狂、成长检定；规则集以 SPI 注册，配置只保存 `ruleset: d100`。
- **剧本**：内置示例《雨夜古宅》随 JAR 分发；支持分段粘贴导入、`/trpg script gen` 由 AI 生成、
  删除与替换带引用保护。
- **呈现**：群内单条 ≤ 500 字并按段落/句读拆成至多 3 条，情景卡与战报用主程序
  `ImageUtil.renderText` 出图，渲染失败回退纯文本；秘密信息只走私聊，绝不下沉到群内。
- **存储**：只用主程序 `StorageService`，五张表统一 `plugin_mbb_trpg_*` 前缀，
  无事务约束下的写入顺序按 ADR 0002 收敛。

## 指令

主名与中文别名等价，两种写法都能用。框架层 `permission()` 一律是 `EVERYONE`，
真正的权限、状态与冷却判定集中在 `SessionService` 的固定顺序：
**黑名单（静默）→ 是否群内 → 权限 → 状态 → 参数 → 冷却 → 执行**。

### `/trpg`

| 子指令 | 用法 | 权限 | 可用状态 |
|---|---|---|---|
| `start` / `开团` | `[剧本ID]` | `EVERYONE` | 无局 |
| `join` / `加入` | — | `EVERYONE` | 招募 |
| `leave` / `离开` | — | 本局玩家 | 招募 / 进行中 |
| `begin` / `开局` | — | KP | 招募 |
| `end` / `结束` | — | KP / `GROUP_ADMIN` | 招募 / 进行中（30 秒内二次确认） |
| `resume` / `恢复` | — | KP | 招募 / 进行中 |
| `status` / `状态` | — | `EVERYONE` | 任意 |
| `help` / `帮助` | — | `EVERYONE` | 任意（可私聊） |
| `pc` / `角色卡` | `[@玩家]` | 本人 / KP | 招募 / 进行中 |
| `pc reroll` / `重掷` | — | 玩家本人 | 招募 |
| `pc set` / `微调` | `<字段> <值>` | 玩家本人 | 招募 |
| `scene` / `场景` | `<标题>` | KP | 进行中 |
| `check` / `检定` | `[@玩家] <技能> [bp/pp [次数]]` | 玩家本人 / KP 代掷 | 进行中 |
| `opposed` / `对抗` | `<@A> <技能> vs <@B> <技能或目标值>` | KP | 进行中 |
| `san` / `理智` | `<@玩家> <X/Y>` | KP | 进行中 |
| `adjust` / `调整` | `<@玩家> <技能> <±值>` | KP | 招募 / 进行中 |
| `invite` / `邀请` | `<QQ>` | KP | 进行中 |
| `take` / `接管` | `<@玩家>` | KP | 进行中 |
| `skip` / `跳过` | `<@玩家> [原因]` | KP | 进行中 |
| `log` / `日志` | `[页码]` | 本局参与者 | 有局或 30 天内有归档 |
| `script` / `剧本` | `list/show/import/delete/gen` 或 `<剧本ID>` | `BOT_ADMIN` / `OWNER`（库）/ KP（换本局） | 库管理任意，换本局限招募 |
| `blacklist` / `黑名单` | `add/remove/list` | `GROUP_ADMIN`；`BOT_ADMIN` / `OWNER` 可私聊远程解封 | 任意 |
| `ai` / `AI 主持` | `on` / `off` | 开：KP / `BOT_ADMIN`；关：KP / `GROUP_ADMIN` / `BOT_ADMIN` | 招募 / 进行中 |

### `/r`、`/roll`

段相加表达式，支持 `NdM` 与最多 3 段加法（如 `1d100+5`），常数段不掷骰；
不落库、不做技能检定、群聊私聊都能用，冷却 2 秒。

```text
/r 3d6        -> 3d6 = 9
/r 1d100+5    -> 两段分别出值并给合计
```

### 私聊可用

只有 `help`、`script` 库管理、`/r`、`/roll` 与 `BOT_ADMIN` / `OWNER` 的远程解封能在私聊使用，
其余一律回「请在群内使用」。

## 一局怎么走

1. `start [剧本ID]`：建局进招募，省略剧本 ID 用内置《雨夜古宅》。
2. `join`：加入并随机生成八属性角色卡（3D6×5、SAN=POW、HP=(CON+SIZ)/10、MP=POW/5、
   物品取剧本起始物品）；招募期内可 `pc reroll` / `pc set` 微调。
3. `begin`：除 KP 外至少 1 名成员即可开局（不检测在线）。
4. 进行中：KP 用 `scene` 推进幕次并解锁本幕线索（线索全文只私聊 KP，群内只发名称与钩子），
   用 `check` / `opposed` / `san` 结算，用 `invite` / `take` / `skip` / `adjust` 维护局内状态。
5. `end`：KP 或群管二次确认后收尾，结算成长并出战报图片与文字摘要。

## 剧本

剧本格式的权威定义在 [docs/scripts/README.md](../docs/scripts/README.md) 与
[docs/scripts/script-v1.schema.json](../docs/scripts/script-v1.schema.json)；JAR 内的
`script-v1.schema.json` 是后者的逐字节副本。示例见 `src/main/resources/scripts/coc-example.json`。

- 内置剧本随 JAR 分发、只读；导入剧本落在数据目录的 `scripts/<id>.json`，同 ID 以内置为准。
- 单份上限 65536 字节，`schemaVersion` 固定 1，`ruleSet` 只接受 `d100`；
  校验失败不落盘，回执最多列 5 条错误。
- 运行时**不**加载 JSON Schema 引擎：`ScriptValidator` 是与 schema 同构的手写结构检查
  加跨字段语义检查，schema 只作机器可读摘要。
- 导入走三段式暂存：`script import [replace]` → 分段粘贴 → `script import end` / `cancel`；
  5 分钟空闲或 30 分钟绝对超时，失败原文落到 `imports/rejected/`（最多 20 份）。
- 被任何未归档局引用的剧本禁止删除与替换（`E_IN_USE`），归档局不阻止。

## AI 主持

`aiHost.enabled=true` 时装配 AI 叙述引擎，默认关闭且**只在插件加载时读一次**，
改配置后需要重载插件。模型与密钥由 MBB-AI 侧配置，本插件通过 `PluginService.call("chat")` 调用。

- 触发：`begin` 开场、`scene` 推进、KP 代掷的 `check` / `opposed` / `san` 立即触发；
  玩家自由行动进 20 秒或 5 条先到的合并窗口。
- 并发：每群一条串行队列（待发上限 3），全局并发默认 2、等待上限 4；超限按 `queue_full` 降级，
  不挤出已在队的请求。
- 降级：忙碌、超时、网络、HTTP、输出校验、配置关闭等 12 类失败按规格表落审计与计数；
  可计类失败连续 3 次后挂起 10 分钟，挂起期零调用。
- 审计：每请求至多一行 `plugin_mbb_trpg_ai_trace`；全局关、未授权、窗口无内容不写，
  `traceEnabled=false` 不写。
- 权限：`ai on` 为 KP 或 `BOT_ADMIN`，`ai off` 另加 `GROUP_ADMIN`；全局未启用时 `ai on` 按
  「当前状态不可用」拒绝。

## 配置

配置写在 `config.yml`，按行解析、键名用点号分层、不支持 YAML 嵌套；缺失键回默认值，
未知键启动时告警。

| 分节 | 内容 |
|---|---|
| `ruleset` | 默认规则集，v1 为 `d100` |
| `session` | 招募过期、进行中过期、结束二次确认窗口 |
| `cooldown` | 开局、骰点、其余动作的冷却秒数 |
| `errorDedupeSeconds` / `activityThrottleSeconds` | 同类错误去重与局内活动节流 |
| `log` | 日志内容截断长度与分页大小 |
| `retention` | 已结束局的归档保留天数 |
| `presentation` | 输出风格、群聊/私聊长度上限、拆分上限、图片开关 |
| `script` | 剧本大小、导入超时、拒绝目录上限、生成冷却 |
| `aiHost` | AI 主持开关、超时、并发、队列、合并窗口、挂起与轨迹保留 |

完整默认值见 [config.yml](src/main/resources/config.yml) 与规格 §11。

## 数据目录

插件数据放在 `getDataFolder()` 下：

- SQLite 数据库，表名统一 `plugin_mbb_trpg_*`（会话、成员、文本日志、黑名单、AI 审计）。
- `scripts/`：导入的剧本文件，同 ID 内置剧本优先。
- `imports/rejected/`：导入失败的剧本副本，最多保留 20 份。

## 归档与清理

启动时立即执行一轮，之后每小时一次，取值来自 `retention.sessionDays` 与
`aiHost.traceKeepHours` / `aiHost.traceMaxRows`：

- 已结束且成长结算完成的局超过保留天数后按 日志 → 成员 → 主行 顺序删除；
  子表删除返回 -1 或预期有行却删 0 行时中止本局、保留主行、下一轮重试。
- 成长结算未完成的局永不清理；黑名单不随局清理。
- 审计先按小时数删过期行，再只保留最新 `traceMaxRows` 行。

## 构建

```powershell
.\build.ps1
```

可用 `-Bot` 指定主程序 JAR：

```powershell
.\build.ps1 -Bot "D:\CodeX\Projects\MoBoxBot\out\MoBoxBot.jar"
```

跑单元测试（首次会下载并校验 JUnit 4.13.2 / hamcrest 1.3）：

```powershell
.\build.ps1 -Bot "D:\CodeX\Projects\MoBoxBot\out\MoBoxBot.jar" -Test
```

产物：

```text
out\MBB-Trpg.jar
```

JAR 根目录只有 `plugin.json`、`config.yml`、`script-v1.schema.json`、`scripts/` 与本插件类，
不打包 MoBoxLib、fastjson、sqlite-jdbc、Java-WebSocket 等公共依赖。
