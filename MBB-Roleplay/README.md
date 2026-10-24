# MBB-Roleplay

MoBoxBot 角色扮演插件。插件读取 persona 文件，让角色以普通群成员的方式参与群聊，并维护记忆、情绪、用户关系和外部技能。

本页是总览。命令、记忆、情绪、集成、配置和多角色部署分别见分册。

## 功能分区

| 分区 | 主要能力 | 详细文档 |
|---|---|---|
| 会话与回复 | 群聊与 owner/好友制私聊、私聊稳定缓存前缀与识图、参与概率、冷却、消息合批、路由判断、引用回复、结构化输出 | [回复架构](../docs/roleplay/architecture.md) |
| 记忆系统 | 跨群短期、长期、永久、个人记忆；私聊私有长期记忆、来源保留、相关性注入、合并、备份与恢复 | [记忆系统](../docs/roleplay/memory.md) |
| 随机刻 | 10 到 30 分钟主动行为，夜间静默、去重、频率限制和 `NO_OP` | [配置与部署](../docs/roleplay/configuration.md) |
| 情绪与关系 | 群级情绪、好感、信任、厌烦、黑名单、用户关系原因 | [情绪与关系](../docs/roleplay/emotion.md) |
| 反馈与表达 | 主动发言反馈、注意力漂移、每日复盘、群内表达候选提取与管理员复核 | [回复架构](../docs/roleplay/architecture.md) |
| 资料与提示词 | persona、台词语料、知识库、提示词缓存前缀 | [回复架构](../docs/roleplay/architecture.md) / [技能与集成](../docs/roleplay/integrations.md) |
| 技能与外部插件 | 提醒、识图、表情包、ComfyUI 生图、戳回去 | [技能与集成](../docs/roleplay/integrations.md) |
| 工具与工作区 | 原生 tools/tool_calls、QQ 工具、表情包主动收藏、图片导入与媒体索引、结构化笔记、草稿归档、私有文件读写、工具期间新消息并入、连续消息节拍 | [工具与工作区](../docs/roleplay/tools.md) |
| 待办与时间 | 用户待办、角色自身待办、完成与延后、提醒联动、时区时间计算 | [工具与工作区](../docs/roleplay/tools.md) |
| 多角色部署 | 其他角色机器人 QQ、接话概率、连续往返限制、名称识别 | [多角色部署](../docs/roleplay/multi-role.md) |
| 免打扰 | 群员自助加入名单，角色不再主动搭话，只回应主动叫到自己的消息 | [命令](../docs/roleplay/commands.md) |
| 命令与配置 | 全部管理命令、配置项、默认性能、构建和升级 | [命令](../docs/roleplay/commands.md) / [配置与部署](../docs/roleplay/configuration.md) |

## 核心流程

```mermaid
flowchart TD
    subgraph ENTRY["事件入口 RoleplayListener"]
        E1["群消息 GroupMessageEvent"]
        E2["戳一戳 NoticeEvent notice/poke"]
        E3["私聊 PrivateMessageEvent"]
    end

    E2 --> POKE{"MBB-Poke 已启用？"}
    POKE -->|是| POKE1["交给 MBB-Poke，本插件跳过"]
    POKE -->|否| T0["提交插件任务线程"]
    E1 --> T0
    E3 --> PCHAT["RoleplayPrivateChatService：稳定前缀 / 识图 / 私聊记忆"]

    T0 --> GATE{"插件开关 + 群级开关"}
    GATE -->|关闭| END0["本轮结束"]
    GATE -->|开启| BATCH["按群合批窗口（默认 2 秒）"]

    BATCH --> SERIAL["同群串行：同一时刻只有一个回合"]
    SERIAL --> PREP["准备阶段 prepareMessage()"]

    subgraph PREP_STAGE["准备阶段"]
        P1["黑名单 / 免打扰名单过滤"]
        P2["指令、空消息、其他角色机器人的图片与表情过滤"]
        P3["指向判定：艾特 > 引用 > 称呼 > 续聊"]
        P4["图片上下文记录（原图留给 MBB-Vision）"]
        P5["消息流水 + 话题疲劳 + 情绪观察"]
        P6["提醒识别 RoleplayReminderService"]
    end

    PREP --> P1 --> P2 --> P3 --> P4 --> P5 --> P6
    P6 --> RRATE["rateLimited()：回复冷却与每小时频控"]
    RRATE -->|冷却中| END1["保持沉默"]
    RRATE --> ROUTE["路由层 RoleplayRouter → MBB-AI"]

    ROUTE --> RR{"需要回复？"}
    RR -->|否| END2["保持沉默"]
    RR -->|是| CHANCE["概率判定 + 情绪修正 adjustReplyChance()"]
    CHANCE --> ASSEMBLE["资料装配：persona / 记忆 / 群员印象 / 语料 / 知识库"]
    ASSEMBLE --> GEN["生成层 → MBB-AI chat"]
    GEN --> PARSE["解析结构化结果 RoleplayReplyDraft"]
    PARSE --> STYLE["重复拦截 + 回复复核"]
    STYLE --> SEND["发送：文本 / 引用 / 分段"]

    SEND --> SKILL["技能执行 RoleplaySkillRegistry"]
    SKILL --> SK1["sticker → MBB-Sticker"]
    SKILL --> SK2["draw → MBB-ComfyUI"]
    SKILL --> SK3["reminder / memory / global-memory / poke-back"]

    SEND --> ASYNC["异步后处理"]
    ASYNC --> AS1["情绪分析：心情 / 精力 / 耐心"]
    ASYNC --> AS2["好感 / 信任 / 厌烦 + 群员个人记忆"]
    ASYNC --> AS3["记忆整理：短期 / 长期 / 永久"]
```

路由层只判断“要不要回复、带哪些技能、带哪些资料”，固定规则保留否决权。被直接艾特、被引用回复、冷却状态等硬规则不会被模型推翻。

## 记忆结构

```mermaid
flowchart TD
    MSG["已过滤的群聊消息"] --> EXTRACT["记忆整理：MBB-AI 分批提取"]
    EXTRACT --> ST["短期记忆：跨群滚动摘要，原群状态保留"]
    EXTRACT --> LT["长期记忆：跨群读取，保留来源和 subjectID"]
    EXTRACT --> GM["永久记忆：跨群共享，仅白名单群学习"]

    EMO["情绪分析（异步，MBB-AI）"] --> MM["群员个人记忆：每个 QQ 一份全局档案"]
    EMO --> REL["关系状态：好感 / 信任 / 厌烦 + 情绪原因"]

    ST --> ASSEMBLE["本轮资料装配"]
    LT --> ASSEMBLE
    GM --> ASSEMBLE
    MM --> ASSEMBLE
    REL --> ASSEMBLE
    ASSEMBLE --> PROMPT["角色系统提示词（动态块）"]

    LT -->|超过 maxLongMemories| MERGE["相似排序 + 分批 AI 合并"]
    GM -->|超过 globalMemoryMaxItems| GMERGE["分批 AI 合并"]
    ST --> BACKUP["/role memory backup 全量快照"]
    LT --> BACKUP
    GM --> BACKUP
    EMO --> BACKUP
```

记忆只作为背景资料注入，不等于每轮都要复述。短期记忆维护跨群滚动摘要，长期记忆保留来源后跨群读取，永久记忆继续跨群共享，群员个人记忆按 QQ 维护；其他群的原始流水不会直接注入当前群。

## 分层回复

```mermaid
flowchart LR
    A["当前消息或合批文本"] --> B["路由层：MBB-AI 小提示词"]
    B --> C{"reply = true？"}
    C -->|否| D["沉默"]
    C -->|是| E["资料装配：预算 promptTotalChars"]
    E --> F["生成层：MBB-AI chat"]
    F --> G["结构化结果 text / actions / quote / segments"]
    G --> H["执行层：RoleplaySkillRegistry"]
    H --> I["发送文本 / 引用 / 分段"]
    H --> J["技能：sticker / draw / reminder / memory / poke-back"]
    I --> K["情绪与记忆异步更新"]
    J --> K
```

- 路由层：判断指向、兴趣、技能和资料，保持轻量。
- 资料层：persona、记忆、群员印象、最近上下文、语料、知识库按预算注入。
- 生成层：生成纯文本回复和表达动作，不输出 Markdown 或思考过程。
- 执行层：发送文本、引用、戳回去、提醒、表情包或生图等技能。
- 本地风格层：暂时退出主链路，默认关闭；去 AI 味由执行层提示词和回复复核负责。

## 提示词装配

系统提示词分两段拼装：可缓存前缀块按稳定性从高到低排在前面，动态块排在后面，这样服务端的前缀缓存才能尽量命中。

```mermaid
flowchart TB
    subgraph PREFIX["可缓存前缀块（稳定性从高到低）"]
        P1["① 静态块<br/>persona 核心 / 外貌<br/>学生印象 / 固定规则"]
        P2["② 输出格式总则<br/>JSON 纪律 + 本回合 schema"]
    end

    subgraph DYNAMIC["动态块（每轮变化）"]
        D1["③ 本轮指令 / 可用技能<br/>关系 / 情绪 / 提醒"]
        D2["④ 长期与永久记忆 / 群员印象<br/>最近发言 / 台词 / 知识库"]
        D3["⑤ 输出约束<br/>引用要求 + 可用动作"]
        D4["user：当前时间 / 最近群聊背景 / 当前消息"]
    end

    P2 -->|"动态预算 = max(2000, promptTotalChars - 静态块长度)"| D1
```

- 前缀块两段连在一起构成缓存前缀：静态块只在 persona、知识库或配置变化时更新；输出格式总则只在合批回合与单回合之间切换。
- 短期记忆每 `memoryUpdateMessages`（默认 50）条消息更新，属于动态资料。
- 长期记忆与永久记忆默认按当前消息做相关性排序，属于每轮变化内容，必须留在动态块。
- 动态块按优先级从高到低填充并逐份截断；常驻资料一定参与，可选资料只有路由点名才参与，支持 `students.detail:角色名` 这种带参数的形式。
- 当前时间、当前发言者、最近群聊背景、当前消息或合批文本统一放在 `user` 消息，不算进系统提示词的缓存前缀。
- 单轮工具循环固定 `tools` schema，`tool_search` 只扩充本地可执行工具集合，不改变后续请求的工具定义前缀。
- 稳定规则、工具协议、动态指令、关系情绪、当前消息、私聊和复核提示词都按功能标题和空行分区，只限制单条长度上限，不设最低字数。
- 完整清单、每个资料 id 的优先级与字符上限见 [回复架构](../docs/roleplay/architecture.md)。
- 可人工编辑的完整提示词模板维护在 `D:\CodeX\Projects\MBB-Persona\prompts\mbb-roleplay-system.md`。

## 插件协作

Roleplay 自身不实现模型、识图和生图能力，而是通过插件服务名调用同仓库的其他 `MBB-*` 插件。下图标出当前实际的协作关系，可把它当作插件间集成的参考示例。

```mermaid
flowchart LR
    RP["MBB-Roleplay"]

    RP ==>|"chat：路由 / 回复 / 风格 / 记忆 / 情绪 / 提醒"| AI["MBB-AI（必需）"]
    RP ==>|"describe：图片与表情包识图 + 本地缓存"| VIS["MBB-Vision（可选）"]
    RP ==>|"tags / random / add：表情包发送与主动收藏"| ST["MBB-Sticker（可选）"]
    RP ==>|"status / generate：生图与群级冷却"| CU["MBB-ComfyUI（可选）"]
    RP -.->|"isEnabled：避免戳一戳重复响应"| POKE["MBB-Poke（可选）"]
    RP -.->|"复用同一套 AI profile 与并发限制"| CHAT["MBB-Chat（同生态参考）"]
    RP -.->|"内容风控交由独立插件，互不依赖"| GUARD["MBB-AIGuard（同生态参考）"]
```

协作要点：

1. Roleplay 只依赖服务名和 JSON 契约，`MBB-AI`、`MBB-Vision`、`MBB-Sticker`、`MBB-ComfyUI` 都是可插拔的：拿掉可选插件后，文本聊天、记忆、情绪和提醒仍然可用。
2. `MBB-AIGuard` 与 Roleplay 的分工是内容风险与角色扮演各管各的，二者互不依赖，可以同时启用；需要风控时不必改 Roleplay。
3. `MBB-Chat` 与 Roleplay 共用 `MBB-AI` 的 profile 与并发上限，多个 AI 插件同开时要注意 `MBB-AI` 的 `maxConcurrent` 需要覆盖它们的总并发。
4. `MBB-Poke` 与 Roleplay 的戳一戳能力互斥：检测到 `MBB-Poke` 启用时，Roleplay 主动让位，避免同一件事回两次。

## 快速开始

```text
/role enable
/role status
/role persona
/role reload
```

详细命令见 [命令分册](../docs/roleplay/commands.md)。

## 依赖与集成

| 插件 | 关系 | 用途 |
|---|---|---|
| `MBB-AI` | 必需 | 路由、回复、记忆、情绪、提醒识别等模型调用 |
| `MBB-Vision` | 可选 | 图片与表情包识图、缓存和原图上下文 |
| `MBB-Sticker` | 可选 | 表情包标签检索、随机发送与主动收藏 |
| `MBB-ComfyUI` | 可选 | 生图技能与生成完成回调 |

没有可选插件时，Roleplay 仍可使用文本聊天、记忆、情绪和提醒功能。

## 免打扰名单

名单内的群员不会被角色主动搭话：只有对方主动叫到角色（艾特、引用角色消息或用到角色名）时才回复，其他消息一律保持沉默，其他群员不受影响。与黑名单的区别是黑名单完全屏蔽该用户的消息和戳一戳，免打扰只关闭主动搭话。

```text
/role quiet add
/role quiet remove
/role quiet list
```

群员可以自己加入和退出名单；把他人加入名单、开关名单和清空名单需要机器人管理员。名单按群隔离，每个群的开关用 `/role quiet on|off` 单独保存；`quietReplyEnable` 只决定新群的默认开关。被免打扰的消息不进入上下文、不触发记忆整理和识图，角色不会记住这些内容。

推荐知识库：

[MossCG/MBB-Knowledge](https://github.com/MossCG/MBB-Knowledge)

把仓库中的 `knowledge/` 复制到 `plugins/MBB-Roleplay/knowledge/`，再执行 `/role kb reload` 即可使用。

persona 与台词语料由 `MBB-Persona` 私有仓库单独维护：`personas/` 复制到 `plugins/MBB-Roleplay/`，`corpus/` 复制到 `plugins/MBB-Roleplay/speech-corpus/`。

## 文档分册

| 文档 | 内容 |
|---|---|
| [回复架构](../docs/roleplay/architecture.md) | 路由、分层处理、消息合批、结构化输出、提示词缓存、重复拦截 |
| [MaiBot 对照](../docs/roleplay/maibot-reference.md) | 缓存诊断、Planner/Replyer 分工、拟人表达和提示词取舍 |
| [记忆系统](../docs/roleplay/memory.md) | 四份记忆的分工、永久记忆、记忆标记、备份与清空 |
| [情绪与关系](../docs/roleplay/emotion.md) | 情绪基线、好感、信任、厌烦、黑名单和关系命令 |
| [技能与集成](../docs/roleplay/integrations.md) | 戳一戳、引用、提醒、台词语料、知识库和外部插件 |
| [多角色部署](../docs/roleplay/multi-role.md) | 多个角色机器人同群的识别和接话控制 |
| [命令分册](../docs/roleplay/commands.md) | 全部 `/role`、`/reminder` 命令 |
| [配置与部署](../docs/roleplay/configuration.md) | persona、配置项、默认性能、依赖、构建和升级 |

## 构建

```powershell
.\build.ps1
```

产物：

```text
out\MBB-Roleplay.jar
```
