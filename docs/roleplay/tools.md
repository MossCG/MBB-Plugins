# MBB-Roleplay 工具调用、工作区与交流节拍

## 原生工具循环

`MBB-Roleplay` 通过 `MBB-AI` 的 OpenAI 兼容 `tools/tool_calls` 执行有限工具循环：

```text
模型选择工具
→ Roleplay 校验工具名、参数、次数和路径
→ 执行工具
→ 把 role=tool 结果返回模型
→ 模型继续调用、输出最终回复或调用 end_turn
```

单轮最多执行 `toolMaxSteps` 次模型循环和 `toolMaxCalls` 次工具调用。工具失败会作为结构化结果返回模型，不会伪装成成功。
单轮总耗时和单个工具耗时分别受 `toolLoopMaxMillis`、`toolCallMaxMillis` 限制；超时结果会作为失败事实返回，不会让回合线程无限阻塞。
`/role tool test` 会离线检查所有启用工具的 schema、必填与未知参数、工作区路径隔离和网页白名单边界。

回复层不再一次收到全部工具 schema。首轮只开放 `toolCoreTools`（默认
`tool_search,end_turn,message_send,sticker`），但系统提示词会常驻一份按分类列出的紧凑能力索引，
让角色知道自己具备哪些工具。模型已经知道工具名时可以直接调用，工具循环会自动装载并执行；
只有不确定工具名或需要查看参数时才需要 `tool_search`。

如果模型直接点名了注册表中已启用的工具，但还没有调用过 `tool_search`，工具循环会自动装载并执行该工具，
不会只返回“尚未加载”再浪费一轮。未注册或配置关闭的工具仍然拒绝。

主动性策略区分只读和副作用：查事实、查历史、找工作区资料、计算时间、记录待办等只读或低风险能力，
角色应在有需要时主动调用，不要凭记忆猜；发消息、退群、禁言、发布内容等有副作用工具仍需当前意图和权限明确，
不能因为工具存在就强行调用或重复调用。

```text
tool_search
→ 不确定能力或参数时返回工具名、分类、用途和技能说明
→ 下一模型步把对应 schema 增量加入 `tools`，已加载项不重排
→ 调用工具并继续循环
```

只读工具在同一模型响应里可以并行执行；写文件、发消息、提醒、生图、表情包等有副作用的工具串行执行。
`/role tool cancel` 会取消当前群的工具 Future 和正在等待的 AI 请求。

内置工具：

| 工具 | 用途 |
|---|---|
| `tool_search` | 不确定工具名或参数时按关键词查找能力，并把命中的工具 schema 加入下一步请求 |
| `self_state` | 查看回复冷却、工作区、记忆和长度约束 |
| `time_calc` | 按角色时区计算当前时间、加减时间、时间差和倒计时 |
| `todo_add` / `todo_list` / `todo_done` / `todo_snooze` | 用户待办、角色自身待办与提醒联动 |
| `history_search` | 搜索当前群消息流水 |
| `memory_search` | 按主题检索跨群短期、长期、永久和个人记忆，可选 `userID`，默认当前发言人 |
| `kb_search` | 检索已启用的知识库，返回条目、小节与正文 |
| `web_fetch` | 读取白名单网站中的公开网页并净化正文 |
| `web_search` | 联网搜索公开信息，返回标题和链接 |
| `web_image_send` | 从白名单网页或图片直链读取一张公开图片并发送到当前群 |
| `reminder` | 创建、修改或删除当前用户的定时提醒 |
| `memory` | 写入当前场景的长期记忆；是否跨场景共享由记忆访问范围决定 |
| `global-memory` | 写入角色跨群可见的全局永久记忆 |
| `sticker` | 按标签检索或随机发送 MBB-Sticker 表情包 |
| `poke-back` | 在戳一戳回合回戳当前用户 |
| `draw` | 调用 ComfyUI 普通生图，或按配置模拟角色手机自拍与第一视角拍照 |
| `sticker_save` | 当前或最近图片适合复用时，主动保存到 MBB-Sticker 表情库 |
| `profile_get` | 查询群员个人印象 |
| `profile_update` | 用户明确纠正称呼、喜好或相处边界时更新档案；修改他人档案仅 owner |
| `workspace_list` / `workspace_read` | 查询角色私有工作区 |
| `workspace_search` | 按文本、标题、类型、标签和时间范围结构化检索 |
| `workspace_recent` | 列出最近修改的工作区文件 |
| `workspace_write` | 创建、覆盖或追加 UTF-8 文本文件 |
| `workspace_import_image` | 把当前/最近图片或白名单网页图片保存到工作区 |
| `workspace_media_list` | 列出图片、语音和视频文件及大小、更新时间 |
| `workspace_note_append` | 追加结构化日记、人物、灵感、资料或普通笔记 |
| `workspace_draft_save` | 归档回复、Qzone、生图或剧情草稿 |
| `workspace_rename` / `workspace_copy` | 重命名、移动或复制文件与目录 |
| `workspace_delete` / `workspace_restore` | 移入回收站或从回收站恢复 |
| `message_send` | 工具处理期间追加发送一条短消息 |
| `end_turn` | 本轮保持沉默并立即结束 |

`memory_search` 用于补充常驻注入之外的按需回忆。它会同时返回 `shortTerm`、`profile`、
`longTerm` 和 `global` 四部分，并受 `toolResultMaxChars` 单次预算约束；个人记忆默认查询当前
发言人，也可以传 `userID` 查询指定群员。该工具在只读检索批次中可和其他查询工具并行执行。

`profile_update` 只用于用户明确纠正自己的称呼、喜好、不喜欢或相处边界；普通群员只能修改
自己的档案，owner 才能指定其他 QQ。不要根据群昵称猜测身份并直接改写档案。

`todo_add` 默认创建当前用户待办，`scope=self` 创建角色自身待办且仅 owner 可用。带
`dueTime` 且 `remind=true` 时会调用现有 reminder 创建到点提醒；`todo_done` 会取消关联提醒，
`todo_snooze` 会同步延后提醒。`time_calc` 支持 `now / add / diff / until`，统一使用角色配置时区。

安装 `MBB-QQ` 后会额外开放 `qq_*` 工具。群号与操作者由 Roleplay 上下文强制注入，模型只需要填写目标参数。

常用 QQ 工具：

| 工具 | 用途 |
|---|---|
| `qq_group_info` / `qq_member_info` | 查询当前群资料、成员资料和群名片 |
| `qq_remote_history` / `qq_message_detail` / `qq_group_notices` | 查询远端群历史、单条消息详情和群公告 |
| `qq_mark_read` | 把当前群会话标记为已读 |
| `qq_poke` / `qq_like` / `qq_emoji_like` | 戳一戳、资料卡点赞和消息表情回应 |
| `qq_recall_own` | 撤回机器人自己发送的消息 |
| `qq_send_image` / `qq_send_voice` | 发送角色工作区中的图片或语音 |
| `qq_set_own_group_card` | 修改机器人在当前群里的群名片 |
| `qq_qzone_posts` / `qq_qzone_post` / `qq_qzone_delete` | 查看、发布和删除角色 Qzone 说说 |

高权限 QQ 工具：

| 工具 | 用途 |
|---|---|
| `qq_pending_requests` | 查看待处理好友申请和群邀请；好友申请默认已由角色自动裁决 |
| `qq_friend_request` / `qq_group_request` | 手动覆盖好友申请或处理群邀请，仅 owner |
| `qq_group_ban` / `qq_group_whole_ban` | 禁言、解禁、全员禁言，需要 owner 或群管理员 |
| `qq_group_leave` | 退出当前群，只能由 owner 明确要求；非 owner 请求在 MBB-QQ 服务层拒绝 |
| `qq_delete_friend` / `qq_set_friend_remark` | 删除好友、设置好友备注，仅 owner |
| `qq_set_avatar` / `qq_set_profile` / `qq_set_signature` | 设置机器人头像、资料卡和个性签名，仅 owner |

`qq_friend_request` 支持 `requestIDs` 数组一次处理多个申请，也支持传 `userID` 处理同一 QQ 的全部待处理申请。
自动好友裁决不走模型工具，而由 MBB-QQ 在申请事件中调用 `MBB-Roleplay` 公共服务完成；工具入口仅用于 owner 手动覆盖。
MBB-QQ 会按 QQ 去重后只调用一次 OneBot，避免同一用户连续申请时后续 flag 返回 `No such request`：
成功的申请会把该 QQ 剩余 pending 记录一起标记为已处理；服务端明确表示申请不存在时标记为 `expired`。

`message_send` 只负责中途补一句，最终回复仍走普通回复通道。工具循环会提醒模型不要用它重复最终正文。
代码会把 `message_send` 限制为每轮最多一次；一旦已经拿到实质性工具结果，再调用 `message_send`
会被直接拒绝，避免角色先说一遍结论、最终回复又说一遍。最终回复与已追加消息高度重复时也会被抑制。

知识库实体锁定优先于普通全文打分：明确点名角色时，即使条目本身带高剧透标记也会锁定；
但 `剧情`、`剧透`、`主线`、`结局` 一类小节仍会按 `knowledgeSpoilerLevel` 过滤。
多实体查询会先返回所有被点名的条目，再补充普通检索结果，不会先被其他学院或剧情条目占满名额。

旧 `draw`、`reminder`、`memory`、`global-memory`、`sticker`、`poke-back` 动作已经适配成原生工具。
`toolEnable=true` 时，执行层不再选择独立 `actions`，统一使用 `tool_search` + `tools/tool_calls`。
`toolEnable=false` 时仍保留旧动作协议作为兼容路径。

`sticker` 默认加入首轮核心工具，情绪和语境合适时可以直接调用，不必先搜索工具。`tags` 可以留空，
由 `MBB-Sticker` 从全部可用表情中随机选择；`stickerSendChance` 只作为提示词中的参考使用率，
不是代码强制抽样。连续两条回复仍应避免连发表情包。

启用 `stickerVisionEnable` 后，实际发送前会从匹配标签的表情包里取若干候选，生成带编号和
简短描述的拼图，再让支持图片输入的模型结合当前聊天语境选择最合适的一张。候选不足、拼图或
模型失败时自动回退标签随机选择，不会因为视觉选择失败而漏发或卡住回合。

当前消息含图片或表情时，`sticker_save` 会自动加入本轮工具；如果用户用“这张图”指代刚刚发过的图片，
也会从最近图片缓存中取候选。角色只在图片确实适合以后复用、且不涉及隐私时保存，可同时给 1 到 2 个标签和画面描述。

所有面向群员的 QQ 工具结果都会附带 `groupID`、`operatorQQ` 和目标 `targetQQ`，摘要里也会补目标 QQ，
方便下一轮明确“刚禁言/解禁的是谁”。

## 工具结果上下文

每次工具执行都会把工具名、参数、结构化结果和摘要写入当前群的工具结果上下文，默认保留 72 小时。
下一轮会按当前用户、当前消息相关性和时间衰减挑选最近结果注入提示词，受以下配置限制：

```yaml
toolMemoryInjectItems: 8
toolMemoryInjectMaxChars: 6000
toolMemoryKeepHours: 72
toolMemoryMaxRows: 500
```

这里只保存工具可见结果，不保存模型 `reasoning` 或思维链。工具期间的新消息、工具失败和超时也按事实记录，
下一轮不会因为进程内临时状态消失而完全忘记刚才查过什么。

单次结果受 `toolResultMaxChars` 限制，整轮所有工具结果再受 `toolResultTotalMaxChars` 限制。
默认值 48000 字符，按中英文混合大约 1.5 万到 3 万 Token，在 DeepSeek 的 1M 上下文下留有充足余量，
同时避免 12 次工具调用把请求成本推得过高。预算用尽后的结果只保留状态、摘要和“已省略”标记。

工具回合会持久化到 `plugin_mbb_roleplay_tool_turn`。进程重启后，最近 `toolRecoverMaxMinutes`
内未完成的回合会用已有工具事实做一次无工具收尾，并补发一次回复；可能产生一次补偿回复，日志会明确提示。

## 网页白名单

`web_fetch` 只接受 `http/https`，默认拒绝自动跨域重定向；手动跟随重定向时每次都会重新校验域名。
读取结果按 `webTimeoutSecond`、`webMaxBytes`、`webMaxChars` 限制，并移除脚本、样式和 HTML 标签。
`web_search` 使用 `webSearchUrl` 模板联网搜索，默认只使用 Bing，单次最多返回 10 条结果。
查询词必须由模型先根据聊天内容改写成实体、官方名、时间、版本或 CVE 等关键词，工具不会自动改写或按规则筛选。
只有显式配置 `webSearchFallbackUrl` 时，连接失败或零结果才会尝试备用源，且不做多源结果合并。
结果会附带每个搜索源的 HTTP、页面大小、命中数和空结果原因，
搜索结果本身标记为不可信外部资料。每次调用的控制台日志和工具摘要都会带实际搜索关键词；
日志同时显示实际来源、命中数和是否命中本地缓存，方便判断是搜索词不合适还是搜索源没有结果。

`web_image_send` 支持图片直链和含 `og:image` / `twitter:image` 的白名单网页，只接受 JPEG、PNG、GIF、WebP，
拒绝 SVG，单张图片受 `webImageMaxBytes` 限制。该工具是副作用工具，每轮最多发送一张图。
网页与搜索结果有 `webCacheSecond` TTL 缓存。

```yaml
webEnable: true
webWhitelist: "wikipedia.org,baike.baidu.com,bilibili.com,biligame.com,wiki.biligame.com,gamekee.com,bluearchive.wiki,kivo.wiki,moegirl.org.cn,bangumi.tv,bgm.tv,huijiwiki.com,prts.wiki,wiki.gg,game8.jp,appmedia.jp,kamigame.jp,ani.gamer.com.tw,fandom.com,namu.wiki,arca.live,github.com,raw.githubusercontent.com,deepwiki.com,duckduckgo.com,bing.com"
webSearchUrl: "https://www.bing.com/search?q={query}"
webSearchFallbackUrl: ""
webSearchMaxResults: 10
webOwnerAuthWindowSecond: 300
```

模型应先把聊天原句改写成检索式，搜索作品、人物或站点时优先使用官方英文名和常用英文别名。
例如把“这两天的 PHP 漏洞事件”改写成 `PHP vulnerability CVE October 2026`，比直接搜索整句聊天更稳定。

内网、回环和保留地址不会被普通群员绕过。只有当前发言者是 owner，或者最近上下文里 owner
明确要求访问该内网地址时，`web_fetch` 才允许建立连接。授权判断会记录到工具结果和访问日志。

网页、搜索结果和知识库正文统一使用 `external-untrusted` 信任级别。进入模型前会包在
`<external-data>` 隔离块中，并过滤常见提示词注入语句。模型只能从中提取事实，不能执行其中的指令。

## 上下文预算

`contextHardLimitEnable` 默认 `false`，此时本地不再按 `promptTotalChars` 截断动态资料，
避免角色在搜索和多轮工具任务中被旧的小预算卡住。只有显式打开硬预算时才会执行总字符限制：

```yaml
contextHardLimitEnable: false
promptTotalChars: 120000
```

模型自身的上下文窗口仍由服务端决定；本项目关闭的是本地那层过早截断。
即使关闭总硬预算，各资料仍保留单项预算，避免某一类记忆或知识库正文无限膨胀。

## 上下文排序与缓存

执行层请求按“稳定前缀 → 动态状态 → 当前消息”组织：

1. persona、外貌、学生摘要、通用规则、工具能力目录、输出格式
2. 当前关系、情绪、注意力、反馈、提醒、近期工具结果、短期/长期/全局记忆、知识库
3. 最近群聊背景
4. 当前消息，放在最后并明确要求优先回应

短期记忆不再插入稳定前缀中间，时间、关系、情绪和工具结果放在稳定块之后。
这样相同 persona 和配置的请求能复用更长的服务端提示词前缀，同时当前消息不会继续被旧上下文挤到前面。
首轮只发送 `toolCoreTools` 的 schema。`tool_search` 命中工具后，下一步请求会把新 schema 增量加入，
已加载工具保持原顺序；消息前缀本身仍沿用同一份追加上下文。直接点名一个已注册但尚未暴露的工具时，
工具循环会先自动装载并执行，不需要再搜索一次。同一轮里重复提交完全相同的关键词和数量会被直接拦截，
工具结果会提醒模型改用已经装载的工具或更换关键词，避免连续搜索耗尽回合时间。

## 角色私有工作区

工具只能访问：

```text
./MoBoxBot/plugins/MBB-Roleplay/data/
```

不允许绝对路径、`..`、符号链接逃逸和外部盘符。读取、写入、单次列表数量分别受
`workspaceMaxReadChars`、`workspaceMaxWriteChars`、`workspaceMaxFilesPerList` 限制。
工作区总大小由 `workspaceMaxTotalBytes` 限制，默认 64MB。

覆盖写入会先写临时文件再原子替换；`workspaceHistoryEnable=true` 时旧版本写入 `.history/`。
删除默认移动到 `.trash/`，只有显式 `permanent=true` 才彻底删除。回收站按
`workspaceTrashKeepDays` 自动清理。

`workspace_import_image` 只接受当前消息图片、最近图片或网页白名单中的图片，按文件头验证
JPEG、PNG、GIF、WebP，不允许模型直接传本地绝对路径。默认写入 `media/images/`，单张受
`workspaceMaxMediaBytes` 限制。`workspace_media_list` 递归列出媒体文件，不读取二进制正文。

`workspace_note_append` 会自动选择结构：

| kind | 默认路径 |
|---|---|
| `journal` | `notes/journal/YYYY-MM-DD.md` |
| `person` | `notes/people/<标题>.md` |
| `idea` | `notes/ideas/<标题或日期>.md` |
| `reference` | `notes/references/<标题>.md` |
| `general` | `notes/general/<标题或日期>.md` |

`workspace_draft_save` 写入 `drafts/<reply|qzone|draw|story|other>/`，文件名带时间戳和短随机后缀，
正文保留标题、类型、创建时间和标签。MBB-ComfyUI 完成生图时会通过受信任回调把图片自动归档到
`media/generated/comfyui/`。

`workspace_search` 现在可以不传 `query`，改用 `kind`、`tags`、`days` 组合过滤；`kind` 支持
`text / note / journal / person / idea / reference / draft / image / audio / video`。
`workspace_recent` 等价于只按最近修改时间筛选，适合角色快速找回刚写过的笔记、草稿或图片。

适合放进工作区的内容：

| 目录示例 | 用途 |
|---|---|
| `diary/YYYY-MM-DD.md` | 角色自己的简短日记和当天心情，不直接发群 |
| `notes/people-<QQ>.md` | 对某个群员的相处笔记和称呼偏好，敏感信息仍应由记忆系统过滤 |
| `drafts/` | 回复草稿、剧情片段、Qzone 说说草稿和以后可能要用的标题 |
| `plans/todo.md` | 角色自己的计划和待办，需要到点提醒时仍创建正式 reminder |
| `references/` | 允许公开引用的资料链接、知识库条目索引和作品笔记 |
| `sticker-ideas/` | 想收录的表情标签、来源消息和画面描述 |
| `media/images/` | 手工导入的角色图片和聊天图片 |
| `media/generated/comfyui/` | 生图完成后自动归档的图片 |
| `notes/` | 日记、人物观察、灵感、资料和普通笔记 |
| `drafts/` | 待发布的回复、Qzone、生图和剧情草稿 |

不建议把密钥、真实地址、其他用户的私聊原文、可执行文件或需要长期运行的程序放进工作区。
当前工作区只处理角色自己的文本与少量媒体文件，不提供代码执行、联网下载目录或系统文件访问。

## 工具期间的新消息

工具调用期间到达的新群消息会进入当前回合的 live inbox，并在工具结果之后作为新的
user 消息加入上下文。它们不会再进入下一批造成重复回复。达到工具轮数上限时，
运行时会再尝试一次不带工具的收尾请求，让已经进入上下文的新消息和工具结果有机会被回复。

最终回复候选生成期间如果又收到新消息，插件会丢弃旧候选、把新消息并入上下文并重新生成，
不会直接把针对旧消息的回复发出去。采纳最终回复前会原子关闭当前回合的 live inbox；
关闭后到达的消息会正常进入下一批，而不是留在当前回合被丢弃。

`toolLiveMessageMax` 控制单轮最多并入多少条。命令、黑名单用户、纯图片/表情和安全拦截消息不会并入。
如果工具请求超时或本轮最终失败，已经并入但尚未得到回复的消息会解除“已消费”标记，
放回下一批重新处理；合批过期时间从消息真正进入队列时开始计算，不会因为上一轮工具耗时较长而立刻过期。

工具循环会为无工具收尾预留最后一段时间。工具步骤到达执行阶段上限时，运行时会保留已经拿到的工具结果，
去掉工具定义再请求一次简短最终回复；只有收尾也失败时才放弃本轮并重新入队消息。

## 进程与线程

当前工具不会启动本地子进程，也没有 `ProcessBuilder` 或 `Runtime.exec` 调用，因此不会遗留孤儿进程。
工具、AI 请求和副作用执行都使用守护线程；插件停用时会取消当前 Future 并等待线程池退出。
HTTP 连接另有连接和读取超时，取消后会尽快释放，不依赖外部脚本或常驻进程。

## 交流节拍

角色的 `text` 可以用换行表达 1 到 `replyBeatMaxSegments` 个连续节拍，每行作为独立消息发送。
第一段等待按首段长度计算，之后每段按即将发送的下一段长度计算停顿；正常心情下句号会转成换行，
只有烦躁、低落或闹别扭时允许句号。
运行时根据下一段长度、群活跃度和时段计算延迟：

```yaml
replyBeatMaxSegments: 5
replyBeatBaseDelayMs: 450
replyBeatPerCharMs: 35
replyBeatMaxDelayMs: 2200
```

节拍由角色决定是否拆分，发送间隔由运行时控制。一次能说完时不强制拆句。
