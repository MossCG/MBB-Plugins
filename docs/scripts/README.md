# 剧本格式 v1

状态：规格（实现前以此为准）。

本目录是剧本格式的唯一来源：

- [script-v1.schema.json](script-v1.schema.json)：结构、类型与单字段约束的机器可读版本（JSON Schema draft-07）。
- [coc-example.json](coc-example.json)：内置示例「雨夜古宅」，也是 start 省略剧本 ID 时的默认剧本；覆盖全部节点类型。

结构以 schema 为准；全局唯一性、引用存在性、保留字、文件来源与语义以本文为准。实现时内置示例必须作为 JAR 资源 scripts/coc-example.json 随插件发布，内容与本目录文件一致。

## 文件来源与加载

- 内置剧本：JAR 内 scripts/<id>.json，只读，随插件发布，不能删除或替换。当前只有 coc-example，且是默认剧本。
- 导入剧本：数据目录（Plugin.getDataFolder()）下 scripts/<id>.json。导入是唯一支持的写入方式，手工改文件后需要重启插件才生效。
- 文件编码 UTF-8，允许带 BOM（读取时剥离）；单文件不超过 65536 字节；文件名必须为 <id>.json，其他文件忽略并记日志。
- 插件 onEnable 时全量扫描内置与导入目录并校验，校验成功的剧本进入内存缓存；导入成功后立即更新缓存。
- 单个导入文件损坏不影响启动与其他剧本：跳过并记日志，list 只列可用剧本并提示失败文件数；show 一个未知或损坏的 id 回 E_NOT_FOUND。
- 内置与导入不允许同 id：导入时命中内置 id 回 E_BUILTIN；磁盘上手工放入同名文件时以内置为准并告警。
- 剧本正文不落库，session 行只存 scriptID（见 数据模型与存档）。因此剧本内容在「被未归档局引用」期间必须保持不变：delete 与 replace 都要先通过引用检查。
- 当前没有 reload 命令；管理员只能用 script import / delete / gen 维护剧本库。

## 指令与权限

以下指令属于 指令集 的 script 分支，权限为 BOT_ADMIN 或 OWNER，群内与私聊都可用：

- /trpg script list：列出可用剧本（内置与导入），带来源标记与节点数量。
- /trpg script show <剧本ID>：显示元信息、节点统计与场景标题列表；不输出完整 JSON。
- /trpg script import：开始新建导入（见下文）。
- /trpg script import replace：开始替换导入；目标 id 已存在时可覆盖，但仍受引用检查限制。
- /trpg script import end：结束分段粘贴并校验落盘。
- /trpg script import cancel：取消本次导入。
- /trpg script delete <剧本ID>：删除导入剧本；内置与引用中的剧本不可删。
- /trpg script gen <题材> [人数] [时长分钟]：调用 MBB-AI 生成剧本草稿并校验落盘。

KP 在招募期用 /trpg script <剧本ID> 更换本局剧本；start 省略剧本 ID 时用 coc-example。剧本 ID 不得为保留字（见下）。

## 顶层字段

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| schemaVersion | 整数 | 是 | 固定为 1；其他值回 E_VERSION，v1 不做迁移。 |
| id | 字符串 | 是 | 剧本 ID，正则 ^[a-z0-9][a-z0-9-]{0,31}$；全局唯一；不得是保留字；不得以 gen- 开头（该前缀保留给生成剧本）；不得与内置剧本同名。 |
| name | 字符串 | 是 | 展示名，1 到 40 字符；不要求唯一。 |
| author | 字符串 | 否 | 1 到 40 字符；缺省在 show 中显示未知。 |
| description | 字符串 | 否 | 1 到 300 字符；给管理员看的简介。 |
| ruleSet | 字符串 | 是 | 固定为 d100；其他值回 E_RULESET。未知规则集的恢复策略归 数据模型与存档。 |
| minPlayers | 整数 | 是 | 1 到 20。 |
| maxPlayers | 整数 | 是 | 1 到 20，且不小于 minPlayers。 |
| estimatedMinutes | 整数 | 是 | 15 到 600，仅用于展示与生成提示，不参与过期判定。 |
| tags | 字符串数组 | 否 | 最多 8 个，每个 1 到 16 字符，不重复。 |
| startingItems | 字符串数组 | 否 | 最多 20 个，每个 1 到 30 字符，不重复；每名玩家加入时复制到 cardJSON.items。 |
| scenes | 对象数组 | 是 | 1 到 20 个；数组顺序即场景顺序，第一个是开场；标题不得重复。 |
| clues | 对象数组 | 否 | 0 到 60 个。 |
| npcs | 对象数组 | 否 | 0 到 20 个。 |
| checks | 对象数组 | 否 | 0 到 40 个。 |
| triggers | 对象数组 | 否 | 0 到 40 个。 |
| endings | 对象数组 | 否 | 0 到 6 个；数组顺序即结局判定顺序。 |

未知字段（含嵌套对象里的未知字段）只告警 W_UNKNOWN_FIELD，不阻止导入；告警数在导入成功回执中显示。所有文本字段先去除首尾空白再做非空与长度校验，但落盘保留原文（只把 CRLF 统一为 LF）；不允许 null，可选字段直接省略。

## 节点字段

所有节点的 id 使用同一正则 ^[a-z0-9][a-z0-9-]{0,31}$，并且在整个剧本内全局唯一（跨 scenes / clues / npcs / checks / triggers / endings）。引用一律用 id。

### scene

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| id | 字符串 | 是 | 全局唯一。 |
| title | 字符串 | 是 | 1 到 40 字符，剧本内唯一（按去首尾空白后的原文比较）；/trpg scene <标题> 用它定位。 |
| summary | 字符串 | 否 | 1 到 200 字符，给 KP 与 status 的一行概括。 |
| narration | 字符串 | 是 | 1 到 1200 字符，非 AI 模式下的场景叙述。 |
| fallbackNarration | 字符串 | 否 | 1 到 1200 字符；AI 主持失败时的替补叙述，缺省用 narration。 |
| clueIds | 字符串数组 | 否 | 进入该场景时解锁的线索 id，引用必须存在。 |
| npcIds | 字符串数组 | 否 | 该场景相关的 NPC id，引用必须存在；仅作 KP 与 AI 上下文。 |
| checkIds | 字符串数组 | 否 | 该场景建议检定的 id，引用必须存在；仅作提示，不自动掷骰。 |

场景推进语义：/trpg scene <标题> 把当前场景设为该场景（写 session.sceneIndex 与 sceneTitle），并解锁该场景 clueIds 中尚未解锁的线索；已经解锁过的不重复写日志。允许回到之前的场景；场景顺序不被强制。线索只能通过场景推进解锁，v1 没有单独的线索指令。

### clue

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| id | 字符串 | 是 | 全局唯一；即线索键 clueKey。 |
| name | 字符串 | 是 | 1 到 40 字符。 |
| lockedSummary | 字符串 | 否 | 1 到 120 字符，未解锁时的模糊提示。 |
| unlockedText | 字符串 | 是 | 1 到 800 字符，解锁后展示的正文。 |

线索解锁记录用 text_log 的 clue 事件承载：eventType=clue，content=clue.id（clueKey），actorID=触发者。读取按 clueKey 去重；重复写入允许但读取侧去重（数据模型与存档）。clueKey 就是 clue.id，不另编码。

### npc

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| id | 字符串 | 是 | 全局唯一。 |
| name | 字符串 | 是 | 1 到 40 字符。 |
| role | 字符串 | 是 | 1 到 40 字符，身份或作用。 |
| public | 字符串 | 是 | 1 到 500 字符，可对玩家公开的信息。 |
| secret | 字符串 | 否 | 1 到 500 字符，只给 KP 与 AI 上下文，不向玩家展示。 |

v1 没有 NPC 指令；NPC 内容只作为 KP 叙述与 AI 上下文。secret 字段不得进入玩家可见回复或公开战报。

### check

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| id | 字符串 | 是 | 全局唯一。 |
| type | 字符串 | 是 | d100.skill 或 d100.san。 |
| skill | 字符串 | 条件 | d100.skill 必填，1 到 20 字符，技能展示名；d100.san 禁止出现。 |
| difficulty | 字符串 | 否 | regular / hard / extreme，缺省 regular；仅 d100.skill 有意义。 |
| bonusDice | 整数 | 否 | 0 到 2，缺省 0。 |
| penaltyDice | 整数 | 否 | 0 到 2，缺省 0；与 bonusDice 同时非零时按 规则引擎 抵消。 |
| loss | 字符串 | 条件 | d100.san 必填，形如 X/Y；X 与 Y 各是一个 DiceExpression：常数 0 到 999，或 NdM+K（N 1 到 20、M 1 到 1000、K 0 到 999）。d100.skill 禁止出现。 |
| prompt | 字符串 | 否 | 1 到 200 字符，给 KP 的检定情境提示。 |
| successHint | 字符串 | 否 | 1 到 200 字符。 |
| failureHint | 字符串 | 否 | 1 到 200 字符。 |

check 是「建议检定」，不是可执行绑定：v1 的 /trpg check 与 /trpg san 仍由 KP 按 指令集 的语法发起，剧本只提供技能名、难度、奖惩骰、损失表达式与提示，供 KP 参考和 AI 组装上下文。不得因为剧本里有 check 就自动掷骰或自动扣 SAN。

### trigger

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| id | 字符串 | 是 | 全局唯一。 |
| when | 字符串 | 是 | scene-enter、clue-unlocked、check-resolved 三选一。 |
| sceneId | 字符串 | 条件 | when=scene-enter 必填，其他情况禁止。 |
| clueId | 字符串 | 条件 | when=clue-unlocked 必填，其他情况禁止。 |
| checkId | 字符串 | 条件 | when=check-resolved 必填，其他情况禁止。 |
| result | 字符串 | 否 | success / failure / any，缺省 any；仅 when=check-resolved 允许。 |
| hint | 字符串 | 是 | 1 到 200 字符，KP 提示文本。 |

v1 的触发只展示 KP 提示，不改变任何状态、不自动发消息、不自动掷骰（与 局面状态机 无事件钩子一致）。何时展示、以私聊还是群内展示归 消息流原型 / 呈现规范；AI 主持启用时作为上下文片段（AI 主持）。提示不得直接发给玩家。

### ending

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| id | 字符串 | 是 | 全局唯一。 |
| title | 字符串 | 是 | 1 到 40 字符。 |
| requiredClueIds | 字符串数组 | 否 | 需要全部已解锁的线索 id，引用必须存在；缺省空数组。 |
| narration | 字符串 | 是 | 1 到 1200 字符。 |

结局判定只在进行中结束（T3 / T6 / 进行中 T7 / 强制结束）时执行：按数组顺序取第一个 requiredClueIds 全部已解锁的结局；都不满足时用插件内置的通用结局文本。结局的 title 与 narration 作为本次结束摘要正文（超过 2000 字符按 数据模型与存档 的摘要上限截断）；结束摘要写入 session.summary，不新增列。招募期取消（T4 / T5 / 招募期 T7）不判定结局。

## 校验规则与错误模型

校验顺序：文件大小 → JSON 解析 → 顶层类型与必填 → schemaVersion / ruleSet / id 格式与保留字 → 单字段类型与范围 → 全局唯一性 → 引用存在性 → 跨字段语义 → 来源冲突（创建/替换）。

错误对象包含 code、path、message 三部分：path 使用 $.scenes[2].clueIds[0] 形式；message 为中文短句；一次最多回 5 条，并追加「另有 N 条」。W_UNKNOWN_FIELD 只告警、不阻断，成功回执显示告警数。校验是全有或全无：任何一条 E_ 级错误都不落盘、不替换旧文件。

| 错误码 | 含义 |
| --- | --- |
| E_SIZE | 内容超过 65536 字节。 |
| E_PARSE | JSON 语法错误；分段导入时视为可能还没传完，保留暂存。 |
| E_ROOT | 顶层不是 JSON 对象。 |
| E_VERSION | schemaVersion 不是 1。 |
| E_RULESET | ruleSet 不是 d100。 |
| E_ID | id 不符合正则，或脚本 id 以 gen- 开头。 |
| E_RESERVED | 脚本 id 命中保留字。 |
| E_BUILTIN | 与内置剧本 id 冲突，或试图删除/替换内置剧本。 |
| E_EXISTS | 新建导入时目标 id 已存在。 |
| E_DUP | 节点 id 或场景标题重复。 |
| E_FIELD | 字段缺失、类型错误、出现 null、互斥字段同时出现。 |
| E_RANGE | 数值越界、数组数量越界、文本长度越界或去空白后为空。 |
| E_REF | 引用的节点 id 不存在，或引用类型不匹配。 |
| E_IO | 读写数据目录失败（创建目录、写临时文件、原子移动均失败）。 |
| E_IN_USE | 剧本被未归档局引用，禁止删除或替换。 |
| E_NOT_FOUND | show / delete / 换本局剧本时找不到剧本。 |
| E_IMPORT_STATE | 没有待导入会话、已有待导入会话、已超时或已超限。 |
| E_AI_DISABLED | MBB-AI 未安装或被禁用。 |
| E_AI_BUSY | 已有生成任务在跑，或 MBB-AI 并发已满。 |
| E_AI_TIMEOUT | 生成超时（MBB-AI 返回 interrupted 或超时错误）。 |
| E_AI_OUTPUT | AI 返回的不是可解析的 JSON 对象。 |
| E_AI_VALIDATION | AI 返回的 JSON 通过了解析但没通过剧本校验。 |
| E_AI_FAILED | MBB-AI 返回其他错误（provider、params、unknown）。 |
| W_UNKNOWN_FIELD | 未知字段，仅告警。 |

保留字（脚本 id 不得等于，大小写不敏感）：list、show、import、delete、gen、end、cancel。前五个是 script 分支的子命令关键字；end 与 cancel 是导入控制字。脚本 id 也不得以 gen- 开头。

额外语义检查：

- maxPlayers 不得小于 minPlayers。
- 所有节点 id 全局唯一；场景 title 唯一。
- scene 的 clueIds / npcIds / checkIds、trigger 的目标、ending 的 requiredClueIds 必须存在且类型正确。
- trigger 的 when 与目标字段严格对应，见上表。
- d100.san 的 loss 两侧必须是可被 规则引擎 DiceExpression 解析的表达式；解析失败回 E_FIELD。
- 文本字段去除首尾空白后不得为空；不允许 NUL 与其他 C0 控制字符（换行与制表符除外）。

校验清单（实现时作为测试用例）：

| 用例 | 期望 |
| --- | --- |
| coc-example.json | 通过，0 错误；节点覆盖 scene / clue / npc / check.skill / check.san / trigger 三种 when / ending。 |
| 非法 JSON 文本 | E_PARSE。 |
| 65537 字节内容 | E_SIZE。 |
| schemaVersion=2 | E_VERSION。 |
| ruleSet=coc7 | E_RULESET。 |
| id=MyScript | E_ID。 |
| id=import | E_RESERVED。 |
| scenes=[] | E_RANGE。 |
| 两个场景 title 相同 | E_DUP。 |
| scene.clueIds 引用不存在 | E_REF。 |
| ending.requiredClueIds 引用不存在 | E_REF。 |
| trigger when=scene-enter 但缺 sceneId | E_FIELD。 |
| d100.san 的 loss=1（不是 X/Y） | E_FIELD。 |
| d100.skill 同时带 loss | E_FIELD。 |
| narration 超过 1200 字符 | E_RANGE。 |
| 未知顶层字段 | 通过并给 W_UNKNOWN_FIELD。 |
| 新建导入已存在的 id | E_EXISTS。 |
| 替换导入被未归档局引用的剧本 | E_IN_USE。 |
| 删除内置剧本 | E_BUILTIN。 |

## 导入流程

API 没有文件上传入口，命令参数又会被主程序按空白切分（CommandRegistry 用 split("\\s+")），所以不能把 JSON 当单条命令参数粘贴。导入改为「开始 → 分段粘贴 → 结束」：

1. 管理员发 /trpg script import（新建）或 /trpg script import replace（替换）。插件回复操作指引并建立暂存。
2. 管理员把 JSON 原文分成一条或多条消息发到同一会话；插件把原文按发送顺序直接拼接，不插入分隔符。分段边界可以落在字符串中间，拼接后仍是原文。
3. 管理员发 /trpg script import end；插件解析、校验、落盘并回复结果。
4. 管理员发 /trpg script import cancel；插件丢弃暂存并回复已取消。

暂存规则：

- 暂存键是（会话, 用户）：群内按群号 + 用户，私聊按用户；同一会话同一用户同时只允许一个暂存。
- 有效期：最后一段之后 5 分钟无新内容，或从开始算起 30 分钟，自动失效；内容上限 65536 字节；段数上限 200。
- 暂存只在内存，插件重启即丢失；重启后管理员重新开始即可。
- 暂存期间，该用户在同一会话发送的**任何**消息都被当作 JSON 片段接收（包括其他 /trpg 指令），不会执行、不会回复；只有整条消息去首尾空白后恰好等于 /trpg script import end 或 /trpg script import cancel 时才按控制字处理。其他用户的消息不受影响。
- 控制字识别大小写不敏感；片段内容按原始文本（MessageEvent.getRawMessage）接收，不做 trim、不做 CQ 码转换。
- 解析失败（很可能是还没传完）时保留暂存并回复当前字节数与错误，管理员可继续发送或 cancel；语义校验失败时结束暂存、保存原文到 imports/rejected/ 并回复前 5 条错误，管理员修正后重来。
- 成功时原子写入数据目录 scripts/<id>.json：先写同目录临时文件，再 ATOMIC_MOVE + REPLACE_EXISTING；原子移动不支持时退化为 move + REPLACE_EXISTING 并记日志。成功后刷新缓存。
- 创建模式命中已有导入 id 回 E_EXISTS；替换模式要求 id 已存在，否则按新建处理；两种模式都禁止内置 id（E_BUILTIN）；替换与删除一样要先通过引用检查（E_IN_USE）。
- 失败与拒绝的原文保存到数据目录 imports/rejected/，文件名 <epoch>-<userID>.json，最多保留 20 份，超出删最旧；保存失败只记日志，不覆盖原始错误回复。
- 导入不校验作者权限之外的内容来源；管理员对粘贴内容负责；回复中不输出完整 JSON。

## AI 生成流程

指令：/trpg script gen <题材> [人数] [时长分钟]

- 权限 BOT_ADMIN 或 OWNER；群内与私聊都可用；每人 60 秒冷却。
- 题材是一个不含空白的词或短语（空格用连字符代替）；人数 1 到 20，缺省 3，同时作为 minPlayers 与 maxPlayers；时长 15 到 600 分钟，缺省 60。
- 插件内只有一个生成槽位：已有任务在跑时回 E_AI_BUSY，不排队。生成在插件自有的单线程 daemon executor 上执行，不占用主程序共享的 4 线程调度池；调用期间该线程阻塞，属于预期。
- 命令立即回复「已开始生成」，完成后在发起会话内回复结果；群内结果只 @ 发起人。
- 与 AI 主持 的 AI 主持解耦：生成是一次性调用，不进主持队列、不写 ai_trace、不受本局 aiEnabled 开关影响；但共用 MBB-AI 服务，因此可能遇到全局并发已满。
- 调用 MBB-AI 的 PluginService.call("chat", ...)：timeoutSeconds=90，maxTokens=6000，temperature 取低值；系统提示要求只输出一个 JSON 对象、不要 Markdown 代码围栏、不要注释、不要额外说明，并给出 schema 摘要、节点数量建议（3 到 5 个场景、3 到 6 条线索、1 到 3 个 NPC、2 到 4 个检定、1 到 3 个触发、1 到 3 个结局）、中文写作、public 字段不剧透、ruleSet=d100、id=gen-placeholder（由插件覆盖）。
- 返回处理：去掉首尾空白；若整体被一对 Markdown 代码围栏包住，剥掉这一层；解析 JSON；插件把 id 覆盖为 gen-<yyyyMMddHHmmss>-<4 位 36 进制随机>，author 缺失时填 MBB-AI；然后走与导入完全相同的校验与原子写入。
- 失败分类：
  - MBB-AI 未安装 / disabled → E_AI_DISABLED，提示可手动导入。
  - 并发满 / busy → E_AI_BUSY，提示稍后重试或手动导入。
  - 超时 / interrupted → E_AI_TIMEOUT。
  - 不可解析 → E_AI_OUTPUT。
  - 校验失败 → E_AI_VALIDATION，回复前 5 条错误。
  - 其他 → E_AI_FAILED。
- 任何失败都不落盘、不覆盖已有剧本；原始返回（及错误信息）保存到 imports/rejected/，回复必须给出可走的手动退路：把生成内容修正后按上一节的分段导入流程重新导入，或换题材重试。原始返回若为空也要保存一条占位记录以便排查。
- 生成结果一律进入导入库（数据目录），不修改内置剧本。

## 删除、替换与引用检查

- 删除对象只能是导入剧本；内置剧本回 E_BUILTIN。
- 删除或替换前，ScriptCatalog 向 SessionService 查询该 scriptID 是否被任何未归档局（RECRUITING / RUNNING，含重启恢复出来的局）引用；被引用回 E_IN_USE。归档局（ENDED）不算引用，允许删除，但之后战报只能显示剧本 ID，无法再读取正文。
- 引用检查使用 SessionService 内存中的未归档局索引，不新增 SessionStore 方法（数据模型与存档 的方法列表不变）。
- 为避免「删除与开团并发」的检查后使用窗口，ScriptCatalog 提供库级读写锁：T1 / T2 解析剧本与插入局期间持读锁；delete / replace 的「检查引用 + 删除/替换文件」持写锁；锁顺序固定为先群锁后库读锁，delete / replace 不获取群锁，避免死锁。读锁只覆盖内存检查与文件操作，不覆盖网络发送。
- 删除成功后从缓存移除并删除文件；文件已不存在按 E_NOT_FOUND 处理（幂等语义由调用方决定）。

## 内置示例

coc-example「雨夜古宅」：2 到 4 人，约 60 分钟，4 个场景、4 条线索、2 个 NPC、3 个检定（2 个技能 + 1 个 SAN）、3 个触发（三种 when 各一）、3 个结局（按线索数量从具体到兜底）。它同时是默认剧本与格式验收样本。

覆盖点：场景顺序与开场、标题唯一、线索引用、NPC 公开/秘密、技能检定难度与奖惩骰字段、SAN 损失 X/Y、三种触发、结局按 requiredClueIds 顺序判定、fallbackNarration、startingItems、tags。

构建要求：实现时把本目录 coc-example.json 复制到插件资源 scripts/coc-example.json；测试至少断言资源存在、能通过完整校验、节点类型覆盖上述清单。

## 与其它规格的接口

- 指令集：新增 /trpg script gen；import 改为分段暂存；保留字扩为 list / show / import / delete / gen / end / cancel；replace 与 delete 共用引用检查；scene 推进时解锁 clueIds；结束摘要采用 ending 判定。
- 数据模型与存档：cardJSON.items 的类型固定为字符串数组，来源是剧本 startingItems；summary 采用 ending 的 title + narration；sessions 仍只存 scriptID。
- AI 主持：生成是独立的一次性 AI 调用，不属于 AI 主持队列、不写 ai_trace；主持的开关、排队、降级仍按 AI 主持。
- 实现切片：实现切片需包含 ScriptCatalog / ScriptValidator / ScriptImportService / ScriptGenerationService 与内置示例资源，测试用例见本文校验清单。
