# 剧本格式 v1 采用严格校验的静态 JSON，导入分段粘贴、生成独立异步

状态：accepted（2026-10-22）

剧本是一局的内容数据，但插件只能通过公开 API 读写，没有文件上传入口，命令参数还会被主程序按空白切分。因此剧本不能作为命令参数传入，也不能依赖 QQ 文件上传；同时 数据模型与存档 已决定剧本正文不落库，session 只存 scriptID。v1 把剧本定义为一个版本化、可全量校验的静态 JSON 文档，内置示例随 JAR 发布，管理员通过「分段粘贴」导入，AI 生成走独立的异步任务。具体决策：

- **格式与来源**：schemaVersion 固定为 1，顶层含 id、name、author、description、ruleSet、minPlayers、maxPlayers、estimatedMinutes、tags、startingItems、scenes、clues、npcs、checks、triggers、endings。内置剧本放 JAR 资源 scripts/<id>.json，只读、不可删改；导入剧本放数据目录 scripts/<id>.json。两者同 id 时内置优先，导入命中内置 id 直接拒绝。文件 UTF-8（允许 BOM）、不超过 65536 字节，onEnable 全量校验并缓存；没有 reload 命令，手工改文件需重启。
- **严格校验、全有或全无**：结构用 JSON Schema draft-07 描述，跨字段语义用校验器补齐；id 统一正则 ^[a-z0-9][a-z0-9-]{0,31}$，元素 id 全剧本唯一，场景标题唯一，所有引用必须存在。错误带 code、JSON path 与中文消息，最多回 5 条；未知字段只告警不阻断。任何 E_ 级错误都不落盘、不覆盖旧文件，避免半份剧本进入缓存。
- **导入用三段式暂存**：/trpg script import [replace] 开始后，插件按（会话, 用户）建立内存暂存；该用户后续消息按原始文本逐条直接拼接，不插入分隔符，不执行也不回复，直到整条消息等于 /trpg script import end 或 cancel。暂存 5 分钟空闲或 30 分钟绝对超时，上限 65536 字节 / 200 段；解析失败视为可能没传完并保留，语义校验失败则清空暂存、把原文存入 imports/rejected/（最多 20 份）。这样既绕开参数切分，也不需要新增主程序接口。
- **生成与主持解耦**：/trpg script gen <题材> [人数] [时长分钟] 仅 BOT_ADMIN/OWNER 可用，人数与时长只用于提示词，缺省 3 人 / 60 分钟；60 秒冷却，全局单槽位，插件自有单线程 daemon executor 执行，避免占用主程序共享调度池。调用 MBB-AI 的 chat 服务，timeoutSeconds=90、maxTokens=6000，要求只输出 JSON；解析后覆盖 id 为 gen- 前缀并走与导入完全相同的校验与原子写入。失败按 disabled / busy / timeout / output / validation / failed 分类，一律不落盘，原文存 rejected，回复必须给出手动导入退路。生成不写 ai_trace、不进 AI 主持 的主持队列、不受本局 AI 开关影响。
- **删除与替换做引用检查**：内置剧本不可删改；导入剧本被任何未归档局（含重启恢复的局）引用时禁止删除或替换。检查复用 SessionService 内存中的未归档局索引，不新增 SessionStore 方法。为消除「检查后、插入前剧本被删」的窗口，ScriptCatalog 设库级读写锁：T1/T2 解析剧本并插入局期间持读锁，delete/replace 的检查加文件操作持写锁；锁序固定为先群锁后库读锁，delete/replace 不取群锁。读锁不覆盖网络发送。
- **剧本内容只作提示，不成为第二套状态机**：check 是建议检定，提供技能名、难度、奖惩骰、SAN 损失表达式与提示，但不自动掷骰、不绑定 /trpg check；trigger 只产出 KP 提示，不自动发消息、不改状态；scene 推进时解锁该场景尚未解锁的 clueIds，已解锁不重复写。结局在 RUNNING→ENDED 时按数组顺序取第一个 requiredClueIds 全部满足者，都不满足用插件通用文本，title 与 narration 进入 summary；招募期取消不判结局。startingItems 在玩家加入时复制到 cardJSON.items，items 的类型由此固定为字符串数组。

## 考虑过的选项

- **把 JSON 作为单条命令参数传入**：主程序 CommandRegistry 对整条命令做 split("\\s+") 后交给 execute，长 JSON 必然被切成多个参数，转义与引号也无法保留；不可行。
- **依赖 QQ 文件上传或主程序新增文件接口**：v1 没有文件上传能力，新增主程序接口又超出插件只依赖公开 API 的边界；放弃。
- **剧本正文入库**：可以省掉文件管理，但 数据模型与存档 已锁定「正文不落库、只存 scriptID」，且大文本会放大备份与迁移成本；不采用。
- **宽松校验、自动补默认值**：作者写错字段时会被静默修正，问题推迟到跑团中途才暴露；严格校验加明确错误路径更利于作者和 AI 生成。
- **AI 生成放进主持队列**：会把一次性创作与每回合主持耦合，主持开关、并发与降级都会互相牵连；独立任务更简单，也符合 AI 主持 的边界。
- **为引用检查新增 SessionStore 方法**：需要改已锁定的存储接口并补迁移；内存索引已足够，且与「启动加载未归档局」的既有行为一致。
- **用单线程或全局锁串行化所有剧本操作**：会阻塞正常开团与列表查询；读写锁只保护短临界区，代价更小。

## 后果

- 指令集 需要增补：新增 script gen 子命令；import 改为分段暂存语义并新增 end / cancel；保留字扩为 list/show/import/delete/gen/end/cancel；replace 与 delete 共用引用检查；scene 推进时解锁 clueIds；结束摘要采用 ending 判定结果；check 与 trigger 明确为提示而非自动执行。完整字段与文案见 docs/scripts/README.md。
- 数据模型与存档 的 cardJSON.items 固定为字符串数组，来源是剧本 startingItems；summary 采用 ending 的 title 加 narration（按既有 2000 字符上限截断）；sessions 仍只存 scriptID，不新增剧本统计方法。
- AI 主持 的 AI 主持继续负责回合内的排队、开关与降级；剧本生成是独立的一次性调用，只共享 MBB-AI 服务与全局并发上限。
- 实现切片至少包含 ScriptCatalog、ScriptValidator、ScriptImportService、ScriptGenerationService 与内置示例资源；测试覆盖 docs/scripts/README.md 的校验清单、导入状态机、生成失败分类与删除竞态。
- 已知限制：导入暂存只在内存，重启即丢；没有 reload，手工改文件需重启；rejected 目录最多 20 份；剧本被归档局引用后仍可删除，战报只能保留 scriptID。
- 安全边界：导入与生成的内容由管理员负责，插件只做结构校验；secret 与 hint 不得进入玩家可见输出，这一条由 AI 主持 与展示层共同保证。
