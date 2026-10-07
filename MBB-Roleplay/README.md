# MBB-Roleplay

MoBoxBot 角色扮演插件，根据角色设定文件进行群聊扮演，并维护长期与短期记忆。

## 特性

- 群级开启，未开启的群不参与聊天
- 角色设定文件可替换
- 长期记忆：群友印象、群友信息、群内氛围、群梗、角色自己做过的事
- 短期记忆：近几天事件、群友日常、角色当前正在做的事
- 记忆游标按消息 ID 精确定位，AI 返回空短期记忆时也会正常推进，不会反复整理同一批消息
- 记忆整理 JSON 支持代码块、前后说明、尾逗号、注释和字符串换行容错，首次失败会自动严格重试
- 记忆整理按字符数限制单批输入，避免 300 条长消息一次性撑爆模型上下文
- 记忆整理请求默认使用 300 秒超时且不自动重试超时，避免长上下文整理卡住
- 记忆输入和整理结果都会带事件发生时间，方便角色理解“昨天”“今晚”这类时间关系
- 角色认为内容值得长期记住时，可以在回复末尾输出 `<remember>`，插件会剥离标记并单独触发一次记忆整理
- 不逐条回复，只回复角色感兴趣或被直接提及的消息
- 同一群消息按到达顺序串行处理，旧消息不会在新消息插队后继续发出过期回复
- 检测到另一个角色机器人时会显著降低接话概率，并限制双方无人插话时的连续往返次数
- 其他角色机器人发送的图片和表情消息直接忽略，不进入识图和互聊链路
- 机器人互聊概率默认调整为 `0.50`，两轮内至少接话一次的概率约为 75%
- 回复前后会对照最近的角色发言，对高度重复的语义和固定开头进行拦截
- 小绿不再把“嗯”当作固定开场，连续使用会被重复检测拦截
- 每轮回复动态注入当前日期、时间、星期和时区，角色不会自行猜日期
- 支持自然语言定时提醒，到点主动艾特用户，并在控制台输出创建、恢复和触发日志
- 提醒识别只调用 AI，识别失败时安静忽略，不再回退到内置中文时间规则，也不会提示“没有识别出具体时间”
- 中文相对时间支持“一分钟后”“十分钟后”等写法，不再只支持阿拉伯数字
- 同一群多角色部署时，识别到消息明确指向其他角色会跳过，不争抢提醒创建
- 同时点名多个角色时，被点到的角色都会视为直接提及，不会只让句首角色响应
- 提醒创建确认和到点提醒正文都由当前角色 AI 生成，模板仅作为兜底
- AI 聊天过程中可以输出 `<reminder>` 标记，结合上下文创建定时任务，包括角色自己的提醒
- 支持所有群共享的永久记忆，学习来源可限制到白名单群，并支持查看和 JSON 备份
- 合并记忆前会把短期、长期、永久记忆一起快照到 `backup/`，支持按文件名恢复
- 支持角色台词语料检索，按当前消息和上下文注入少量参考台词，并拦截高度照抄
- 回复长度按 15 到 30 字要求，并明确禁止用“嗯”“哦”“好”“知道了”这类过短单句敷衍
- 群友用引用回复时，被引用的原话会一起带进上下文，角色能看到对方在回哪句话
- 群聊上下文里每条消息都带 QQ，角色按 QQ 认人；群友的昵称只是显示名，把昵称改成优香、小绿也不会被当成设定里的学生本人
- 群员个人记忆：按 (群号, QQ) 记录角色对该群员的称呼、喜好与相处方式，由情绪分析同一轮 AI 调用顺手产出，不额外增加调用次数
- 启动和重载时自动补全 `config.yml` 缺失项，并补充中文注释
- 群主和管理员视为老师，其他真人成员视为朋友，另一个角色机器人不按群权限归类
- 所有真人成员使用可配置的初始好感度，默认 `70/100`
- `/role memory` 使用图片展示短期记忆和分页后的长期记忆
- 明确艾特其他用户时不会误判为对爱丽丝说话
- 同一话题下允许多个群员继续参与，AI 会判断是否真正接续话题
- 高频群聊模式：秒级回复冷却，默认每小时可回复 180 次
- 单条回复优先控制在 12 字以内、硬上限 20 字，一条说不完最多分两段
- 输出纯文本聊天，不使用 Markdown
- QQ 表情消息直接忽略；安装 `MBB-Vision` 后，图片和表情包可以按配置理解并注入聊天上下文
- 图片理解默认只处理直接提及、连续对话和表情包，普通群图片不会全部送模型
- 用户先发送图片再追问时，会在时间窗口内把原图作为多模态上下文传给模型，图片本身不会单独触发回复
- 同一图片已经识别过时会复用摘要，后续追问不会重复调用识图
- 多个角色同时处理同一图片时，识图请求会按图片哈希合并，避免重复调用视觉模型
- 连续快速发言会按窗口合批，一个回合只做一次路由和一次生成，回复可以分成多段分别回应不同的人
- 表情包等待是事件驱动的：表情包先到、用户又发言都会立刻结束等待，只有都没出现时才等到窗口上限
- 表情包已开始识别时会暂停回合，识别完成后立即合并；最多额外等待 15 秒，超时按无表情包继续
- 明确艾特了其他群成员、又没有提到角色的消息会直接跳过，不再误以为是在和角色说话
- 路由层会收到"发言指向"说明（艾特角色本人 / 回复角色 / 艾特其他成员等），并结合上下文判断这句话是不是对角色说的
- 纯图片与表情消息的识图放到独立异步任务，不会卡住同一群后续消息的处理
- 长期记忆与永久记忆先按当前对话相关性排序再注入：检索 query 由当前消息和路由层判定的话题拼成，
  重要度、字符重合度（按 query 长度归一化）、同用户归属和时间新鲜度共同打分，
  长期记忆先取 `memoryRelevancePoolSize` 条候选池再截取，两者各有字符上限
- 同一群的消息回合串行处理：上一回合没出声前不会开始下一批，避免同一段连续发言被两个回合各回一次
- 同一用户刚被回复过时，紧接着的短句（默认 20 秒内、20 字以内）视为补充，不再重复接一次
- 半句识别：消息结尾停在角色名字或连接词上时（例如"你们觉得小桃"），会等同一用户补充，
  把"你们觉得小桃"+"今天可爱吗"并成一次回复，而不是先回半句再漏掉真正的问题
- 爸爸是不存在的记忆：任何人自称是角色的爸爸都不成立，回复层会当成玩笑或直接否认，
  记忆整理层也不会把这类说法写进长期记忆或永久记忆
- 学生外貌参考改由知识库的学生档案库提供，覆盖全部 153 人；插件不再打包自带的 69 人 `students.json`，运行目录里若还留着旧文件则继续当兜底读取
- 学生档案库启用后，识图参考改从知识库的学生条目取外貌小节，按总预算均分给每个学生：覆盖 153 人、约 2.8 万字，比原来 69 人的自带图鉴更全也更省；自带图鉴只在知识库缺席时兜底
- 角色知道自己的外貌设定；被问起某位学生是谁或长什么样时，会结合共享图鉴的外貌、社团、性格和关系作答
- 学生图鉴不会整份塞进每条请求：常驻的「了解的学生」只保留一句话印象，被点名的学生才按需注入完整外貌，省下的上下文留给聊天本身
- 安装 `MBB-Sticker` 后，角色可以按当前真实标签集输出 `<sticker>tag</sticker>` 发送匹配表情包，不会调用不存在的标签
- 表情包是可选表达，提示词会要求低频自然使用，不会每句话都携带
- 响应戳一戳：被人戳时由角色自己决定是回一句话、戳回去、还是两者都做，戳回去对同一用户有冷却；单次戳一戳算主动互动的加分项，只有短时间内连续戳很多次才会涨厌烦
- 正文出现“戳回去/回戳/戳你”时会自动补齐 `poke-back` 动作，避免只说不做
- 新增群级短期情绪与用户关系机制：心情、精力、耐心会影响参与意愿，好感、信任、厌烦会影响对具体群员的语气
- `(群号, QQ)` 关系记录支持用户级情绪原因，例如“讨厌这个人，因为他在冒名顶替我”，原因不会直接发送到 QQ
- 情绪更新分为本地规则和异步语义分析两段，回复结果仍然只有 `text`、`actions`、`quote`，不会携带情绪提示
- 用户可以随时通过管理命令查看当前群情绪、指定用户关系和原因，owner 可以设置或清空原因
- 新增按群黑名单，黑名单用户的消息和戳一戳不会进入后续上下文，也不会触发角色回复；已有用户记忆和数据不会被删除
- 初始好感和信任提高，群主/群管理员、botAdmin、botOwner 拥有不同的初始好感倍率
- botOwner 初始好感直接满值且不会下降，角色会把 botOwner 当作妈妈一样亲近、依赖和听劝
- 好感度达到阈值后，角色会逐步接受抱抱、牵手、贴贴、摸头和撒娇式互动
- 戳一戳回复加入轮换策略，不再每次都机械地戳回去；连续戳触发骚扰判定时不再回戳，语气转为短促制止
- 路由、回复生成、戳一戳回合、记忆整理、情绪分析与识图都走插件自己的线程池，不再占用主程序共享的定时任务线程
- 提及其他角色不再作为硬跳过条件；明确在和小绿聊天时可以自然提到小桃
- 安装 `MBB-ComfyUI` 后开放 `draw` 技能；需求不完整时角色会自行补全背景、动作、风格和尺寸，并优先写完整角色名
- 生图完成后角色会基于 prompt 生成简短完成提醒，图片先发、提醒后发，不再二次识图
- 亲密、暧昧或有点过分的要求会以害羞、别扭、撒娇或转移话题回应，不输出露骨内容
- 拒绝 sexy 话题时可用无语、嫌弃、半开玩笑和“变态/hentai”式撒娇吐槽，表现并非讨厌而是拿你没办法
- 记忆整理和合并会注入当前角色身份，按角色视角区分自身经历、他人发言和对别人的印象
- 语气支持省略号、适度填充词、犹豫重复、反问互动，以及情绪化反话和潜台词
- 被艾特或被直接回复时自动引用原消息，模型也可以自己判断引用是否更自然
- 回复由执行层以结构化结果返回，正文和动作在同一次生成里决定，标签不会再漏到聊天里

## 指令

| 指令 | 权限 | 说明 |
|---|---|---|
| `/role status` | `BOT_ADMIN` | 查看当前群状态 |
| `/role enable [群号]` | `BOT_ADMIN` | 开启当前群或指定群 |
| `/role disable [群号]` | `BOT_ADMIN` | 关闭当前群或指定群 |
| `/role groups` | `BOT_ADMIN` | 查看已开启群 |
| `/role reload` | `BOT_ADMIN` | 重载角色设定和配置 |
| `/role config` | `BOT_ADMIN` | 查看配置文件与补全状态 |
| `/role config repair` | `BOT_ADMIN` | 手动补全缺失配置项 |
| `/role blacklist list` | `BOT_ADMIN` | 查看当前群黑名单 |
| `/role blacklist add <QQ> [原因]` | `BOT_ADMIN` | 将用户加入当前群黑名单，后续过滤其消息和戳一戳，不删除已有用户记忆 |
| `/role blacklist remove <QQ>` | `BOT_ADMIN` | 将用户移出当前群黑名单 |
| `/role blacklist clear` | `BOT_ADMIN` | 清空当前群黑名单 |
| `/role member [QQ]` | `BOT_ADMIN` | 查看角色对某个群员的个人印象，不填 QQ 则看自己 |
| `/role member clear <QQ>` | `BOT_ADMIN` | 清空对某个群员的个人印象 |
| `/role mood [页码]` | `BOT_ADMIN` | 以图片查看当前群情绪和情绪事件 |
| `/role mood reset` | `OWNER` | 将当前群情绪重置到角色基线 |
| `/role emotion [QQ] [页码]` | `BOT_ADMIN` | 以图片查看指定用户的关系、情绪原因和变化记录 |
| `/role emotion reset [QQ]` | `BOT_ADMIN` | 重置自己或指定用户的关系状态，重置他人需要 owner |
| `/role emotion log [页码]` | `BOT_ADMIN` | 以图片查看当前群的情绪事件流水 |
| `/role emotion affinity set <QQ> <0-100>` | `BOT_ADMIN` | 强制设置指定用户的好感度 |
| `/role emotion reason set <QQ> <原因>` | `OWNER` | 手动设置指定用户的情绪原因 |
| `/role emotion reason clear <QQ>` | `OWNER` | 清空指定用户的情绪原因 |
| `/role bot` | `BOT_ADMIN` | 查看机器人互聊设置 |
| `/role bot chance <0.3-1>` | `BOT_ADMIN` | 设置其他角色机器人的接话概率 |
| `/role bot max <1-10>` | `BOT_ADMIN` | 设置无人插话时的连续回应上限 |
| `/role bot qq ...` | `BOT_ADMIN` | 查看、增删或清空其他角色机器人 QQ |
| `/role bot name ...` | `BOT_ADMIN` | 查看、增删、重置或清空名称识别关键词 |
| `/role memory [页码]` | `BOT_ADMIN` | 以图片查看当前群短期记忆和长期记忆 |
| `/role memory merge` | `BOT_ADMIN` | 手动整理合并当前群长期记忆 |
| `/role memory backup` | `BOT_ADMIN` | 备份全部群的短期、长期、永久记忆 |
| `/role memory backups` | `BOT_ADMIN` | 查看备份文件名列表 |
| `/role memory restore <文件名>` | `OWNER` | 从备份文件恢复全部记忆 |
| `/role forget` | `BOT_ADMIN` | 清空当前群记忆、聊天上下文并轮换 AI 会话 |
| `/role persona [文件名]` | `BOT_ADMIN` | 查看或切换角色设定文件 |
| `/role persona reset <文件名>` | `BOT_ADMIN` | 用内置版本覆盖指定角色设定文件 |
| `/reminder list [页码]` | `EVERYONE` | 查看自己在当前群的待触发提醒 |
| `/reminder show <ID>` | `EVERYONE` | 查看自己的某条提醒详情 |
| `/reminder edit <ID> <时间> <内容>` | `EVERYONE` | 修改自己的提醒时间和内容 |
| `/reminder delete <ID>` | `EVERYONE` | 取消自己的某条提醒 |
| `/reminder clear` | `EVERYONE` | 取消自己在当前群的全部待触发提醒 |
| `/role gmemory [页码]` | `BOT_ADMIN` | 图片查看全局永久记忆 |
| `/role gmemory backup` | `BOT_ADMIN` | 兼容入口，同样创建全部记忆快照 |
| `/role gmemory merge` | `BOT_ADMIN` | 手动整理合并全局永久记忆 |
| `/role gmemory group ...` | `BOT_ADMIN` | 管理永久记忆学习白名单群 |
| `/role speech stats` | `BOT_ADMIN` | 查看台词语料加载状态 |
| `/role speech reload` | `BOT_ADMIN` | 重载台词语料 |
| `/role speech search <文本>` | `BOT_ADMIN` | 调试台词语料检索 |
| `/role speech on` / `off` | `BOT_ADMIN` | 开关台词语料检索 |

## 角色设定

首次运行释放：

```text
./MoBoxBot/plugins/MBB-Roleplay/persona-aris.json
```

默认设定为《蔚蓝档案》的天童爱丽丝：游戏开发部、勇者见习生、RPG 爱好者，重视老师和伙伴，说话偶尔带游戏化表达。

另外内置两份角色设定：

- `persona-momoi.json`：才羽桃井，游戏开发部剧本作家，更活泼主动、情绪外露。
- `persona-midori.json`：才羽绿，游戏开发部美术，安静认真、冷幽默式短吐槽。

桃井补充了“不会主动说自己菜，被问游戏水平时会嘴硬装厉害，被揭穿会尴尬害羞”的特点。绿补充了“小绿”别名和轻度“偷跑/卑女”设定：表面安静，实际可能在亲近老师的事情上悄悄先一步，被点破会害羞否认，不会恶意损害伙伴关系。

绿还带有一层藏得更深的“姐控/骨科”设定：她对姐姐桃井抱有超出普通姐妹关系的好感，自己清楚这份心情并为此悄悄困惑心虚，平时藏得很好，只在桃井突然和别人亲近时安静吃味，被当面点破会慌张否认、转移话题。表达保持含蓄克制，不主动升级话题、不写露骨内容，也不会把每句话都绕回姐姐或因此冷落其他群友。桃井侧暂未改动，这段感情默认是绿单方面的隐藏心事。

切换角色：

```text
/role persona persona-momoi.json
/role persona persona-midori.json
/role persona persona-aris.json
```

桃井设定会识别“小桃”“王小兆”“优香大魔王”“给木给木”“苦呀西”等社区梗，但默认不会主动频繁使用。

三份角色设定都补充了外貌、世界观、所属学园、社团、其他学生、专有名词和剧情记忆字段，并额外加载共享的 `students.json` 学生设定表。`appearance` 字段是角色本人的完整自述，覆盖发色、瞳色、光环、服装、配饰、下装、腿部、鞋履、武器、配色与辨识点，角色对自己有清晰认知。旧版 `persona.json` 会在配置迁移时切换到 `persona-aris.json`。本机运行目录里的 `persona-midori.json` 若早于本次更新，可在群里执行 `/role persona reset persona-midori.json` 覆盖为最新默认设定；该操作会先备份旧文件。

共享的 `students.json`（69 人外貌图鉴）**已经不再随插件打包**，学生资料统一由知识库的档案库提供，
原文件备份在知识库项目的 `legacy/students.json`。运行目录里如果还留着这个文件，插件仍会读取它作为兜底，不会报错；
新装环境不会再生成它。

世界观中同时接受“奇普托斯”和“基沃托斯”两种称呼。桃井和绿明确认识凯伊，并知道她与爱丽丝的关系。

默认口癖包括“邦邦咔邦！”、“爱丽丝，了解！”、“光呀！”等，要求低频自然使用，不会每句话都变成游戏台词。

绿的吐槽已收紧到**无语、无奈**那一类：可以用叹气、扶额、“……算了”“你开心就好”这类表达，
明确禁止挖苦、阴阳怪气、人身攻击和刻意贬低群友，也不允许为了吐槽而吐槽。

“邦邦咔邦”只会出现在回复句首，作为类似任务启动提示音使用，不会放在句中或句尾。

可以修改名称、身份、性格、说话方式、兴趣、禁忌和行为规则。修改后执行：

```text
/role reload
```

如果希望恢复插件内置设定，可以使用：

```text
/role persona reset persona-momoi.json
/role persona reset persona-midori.json
```

## 关系、好感与情绪

群主和管理员若不是其他角色机器人，统一视为老师；其他真人成员视为朋友；另一个角色机器人按角色设定中的同伴关系处理，不会因为群权限被误称为老师。

`initialAffinity` 控制所有真人成员的初始好感度，默认 `65/100`；`initialTrust` 控制初始信任度，默认 `60/100`。关系状态按 `(群号, QQ)` 持久化，包含好感、信任、厌烦、当前态度和用户级情绪原因。群主和群管理员、botAdmin、botOwner 会分别叠加初始好感倍率。botOwner 的每一项关系数值始终保持在最佳状态：好感 `100/100`、信任 `100/100`、厌烦 `0/100`，不会因为事件或时间下降，角色把 botOwner 当成妈妈一样亲近、依赖和听劝。

升级后，已经互动过的 botOwner 旧关系记录不需要手动重置；下一次读取关系状态、触发回复或执行关系命令时会自动归一化为最佳值，并清除旧的负面原因。

普通闲聊只会产生很低的临时变化，明显事件才会写入原因，例如：

```text
讨厌这个人，因为他在冒名顶替我
他刚才夸过角色的画
他连续冒犯角色
```

好感、信任和厌烦的变化频率已收紧：普通闲聊只影响群级情绪，不直接改关系；关系数值只由带明确原因的高信号事件、AI 语义事件或管理员命令改变。每次关系数值变化都会写入情绪事件流水，并保留变化原因；没有原因的关系变化会被直接忽略。每日单项累计变化默认限制为 `2` 点。

关系数值支持小数。每次事件通常只增加或减少 `0.1` 到 `0.5`；降低幅度单独限得更小（`emotionAnalyzeMaxDecreaseDelta`，默认 `0.3`，且不允许超过提升上限），保证关系数值跌得比涨得慢。同一天内的原始增量会持续累计，但当日实际值最多只能比当日开始值高或低 `relationDailyMaxDelta`。例如当日累计 `5.9`，上限是 `2`，长期生效值仍然只增加 `2`，超出的 `3.9` 会在跨日时丢弃。这样不会因为单次取整或短时间多次小幅波动而浪费变化量。

厌烦高时角色不会变得尖刻：提示词要求把厌烦表现成话变短、敷衍、回避话题或冷淡，禁止辱骂、人身攻击和命令式语气，避免模型把“讨厌”演成骂人。

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

群级短期情绪包含心情、精力和耐心，会随时间回落到角色人格基线。桃井、绿、爱丽丝的基线分别存放在各自 persona 文件的 `emotionBaseline` 中。

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
| 短期记忆 | 每群一份 | 近几天的事件、群友日常、角色正在做的事 | 每 `memoryUpdateMessages` 条消息整理一次，整段重写 | 常驻，优先级 80，上限 1500 字 |
| 长期记忆 | 每群按条目，带 `subjectID` | 群内氛围、群梗、角色行为、**群员做过的具体事情**（`user_event`），都带发生时间 | 同一次整理产出，单批最多 20 条 | 常驻，优先级 90，上限 5200 字，按相关性排序 |
| 永久记忆 | 跨群共享 | 角色自己学到的说话方式、语气、习惯、经验教训（`lesson`）、群梗 | 同一次整理产出，仅白名单群 | 常驻，优先级 85，上限 4200 字，按相关性排序 |
| 群员个人记忆 | 每群每 QQ 一份 | 称呼、喜好、不喜欢、相处方式 | 情绪分析那一轮顺手产出 | 常驻，优先级 88，上限 600 字 |

边界约定：

1. **人物属性归群员个人记忆**。群员的称呼、喜好、性格不写进长期记忆；长期记忆里提到某个群员时只记他做过的具体事情，类型用 `user_event`。记忆整理时会把本批涉及群员的既有印象一并喂给模型，避免重复记录；合并旧数据时，`user_impression` / `user_info` 里的具体事件改写成 `user_event` 保留，纯属性描述丢弃。
2. **官方设定归知识库**。游戏设定、专有名词、剧情事实由知识库负责，不写进永久记忆；永久记忆只记角色自己学到的东西，所以类型里的 `knowledge` 已经改成 `lesson`（经验教训）。
3. **短期是滚动窗口，长期是提炼结论**。短期每轮整段重写，自然遗忘；长期增量累积，超过 `maxLongMemories` 时合并压缩。
4. **群员印象是增量修订**。情绪分析会把该群员已有的印象一起喂给模型，让它补充或修正，而不是每次从零重写；空字段不会覆盖已有内容。

## 依赖

需要安装并启用 `MBB-AI`。图片理解还需要安装并启用 `MBB-Vision`。`MBB-AI` 负责模型调用，`MBB-Vision` 负责图片识别、缓存和复用。

## 默认性能

```yaml
replyCooldownSecond: 5
maxRepliesPerHour: 180
initialAffinity: 65
initialTrust: 60
initialGroupAdminMultiplier: 1.05
initialBotAdminMultiplier: 1.10
initialBotOwnerMultiplier: 1.15
relationDailyMaxDelta: 2.0
intimacyCloseAffinity: 75
intimacyVeryCloseAffinity: 90
emotionEnable: true
emotionDecayMinute: 30
emotionEventCooldownSecond: 300
emotionAnalyzeEnable: true
emotionAnalyzeMode: "significant"
emotionAnalyzeProfile: ""
emotionAnalyzeCooldownSecond: 300
emotionAnalyzeMaxTokens: 1200
emotionAnalyzeReasoningEffort: "low"
emotionAnalyzeMaxDelta: 0.5
emotionAnalyzeMaxDecreaseDelta: 0.3
emotionReasonMaxChars: 120
emotionReasonMinStrength: 40
emotionReasonDecayDays: 30
emotionEventRetentionDays: 90
emotionPositiveReplyBonus: 0.05
emotionNegativeReplyPenalty: 0.15
emotionAffinityReplyBonus: 0.08
emotionAffinityReplyPenalty: 0.15
reminderEnable: true
reminderAiParse: true
reminderMaxDays: 30
interestReplyChance: 0.65
conversationWindowSecond: 180
continuationReplyChance: 0.80
otherParticipantReplyChance: 0.45
otherRoleBotReplyChance: 0.50
maxConsecutiveOtherRoleMessages: 2
shortContextMessages: 120
memoryUpdateMessages: 50
memoryExtractMessages: 300
memoryExtractMaxChars: 16000
memoryExtractBatches: 3
memoryProfile: ""
replyReasoningEffort: "low"
memoryReasoningEffort: "low"
timeZone: "Asia/Shanghai"
memoryMaxTokens: 12000
memoryMergeMaxTokens: 32000
memoryMergeBatchSize: 60
memoryMergeMaxRounds: 3
memoryTimeoutSecond: 300
activeMemory: true
imageUnderstandingEnable: true
imageUnderstandingMode: "addressed"
imageUnderstandingMaxPerHour: 30
imageUnderstandingInjectOcr: true
imageUnderstandingProfile: ""
imageUnderstandingMaxChars: 600
visionReferenceMaxChars: 32000
imageContextTimeoutSecond: 300
stickerAttachEnable: true
stickerAttachWindowSecond: 5
stickerAttachMaxWaitSecond: 15
stickerAttachWaitUntilNextMessage: true
messageBatchEnable: true
messageBatchWindowSecond: 2
messageBatchMaxMessages: 10
messageBatchMaxAgeSecond: 20
batchMaxSegments: 4
turnThreads: 4
addressedOtherMemberSkip: true
splitMessageSuppressSecond: 20
splitMessageSuppressMaxChars: 20
imageAsyncEnable: true
memoryRelevanceSort: true
memoryRelevancePoolSize: 600
memoryRelevanceMaxChars: 5000
globalMemoryRelevanceMaxChars: 4000
pokeReplyEnable: true
pokeBackEnable: true
pokeBackCooldownSecond: 60
pokeStreakWindowSecond: 60
pokeStreakThreshold: 3
quoteReplyEnable: true
promptTotalChars: 26000
routerEnable: true
routerProfile: ""
routerMaxTokens: 2400
routerReasoningEffort: "none"
styleEnable: true
styleMaxChars: 60
styleProfile: ""
styleMaxTokens: 400
styleReasoningEffort: "low"
styleProactiveEnable: false
replyImageMaxTokens: 4000
replySegmentMaxChars: 30
replySplitPunctuation: "。！？!?；;，、：,:～~"
replyMaxSegments: 2
maxLongMemories: 150
recentReplyCheckCount: 8
repeatSimilarityThreshold: 0.72
repeatCheckMinChars: 6
repeatOpeningLimit: 2
repeatCommonRunMinChars: 8
minMessageLength: 2
```

非直接提及、非对话续接、非兴趣话题的消息不会参与回复。回复 prompt 要求每条消息优先控制在 12 字以内、硬上限 20 字，一条说不完可以在正文里换行，最多分两段；拆分器会优先在 `replySplitPunctuation` 列出的常用符号处断开，再按硬上限拆分。不要重复同一件事或细节，也不要连续使用同一种开头或口癖。

`memoryProfile` 留空时记忆整理使用 `aiProfile`。如果主模型会产生大量 reasoning，建议单独配置一个非 reasoning 的 profile 给记忆整理使用；`memoryMaxTokens` 默认 `12000`，重试时会翻倍，最高 `32000`。长期记忆合并单独使用 `memoryMergeMaxTokens`，默认 `32000`，避免 reasoning 把输出预算耗尽后返回空正文。记忆合并按 `memoryMergeBatchSize`（默认 60）分批，最多执行 `memoryMergeMaxRounds`（默认 3）轮；每批开始和结束都会输出控制台进度日志。`memoryTimeoutSecond` 默认 `300`，用于覆盖 profile 里较短的超时时间，避免长上下文整理频繁超时；可设置范围是 `30` 到 `600` 秒。

记忆整理输入会带 `[yyyy-MM-dd HH:mm]` 时间戳，prompt 也要求 longTerm 和 shortTerm 在对应事件里带上发生时间，方便角色之后理解“这是昨天说的”“这是今晚发生的”这类时间关系。

`replyReasoningEffort` 和 `memoryReasoningEffort` 控制思考强度，默认都是 `low`，可选 `low`、`medium`、`high`，留空表示不向接口发送该字段。reasoning 模型在思考上消耗的 Token 会挤占输出预算，角色回复设成 `low` 后更不容易出现“思考写满、正文为空”的情况。

每次调用 AI 都会在控制台输出一行 `[角色]` 日志，格式与 `MBB-Vision` 的识图日志一致：

```text
[角色] 回复 群623069084 profile=default model=deepseek-v4.1-flash 耗时=1840ms finish=stop token=5210/96 缓存=否 长度=42 内容=...
```

标签包括 `回复`、`记忆整理`、`长期记忆合并`、`永久记忆合并`、`提醒识别`、`提醒确认生成`、`提醒内容生成`、`情绪分析`。调用失败时输出 `sendWarn`，包含错误类型和耗时。

`timeZone` 决定角色理解的当前时间，默认 `Asia/Shanghai`。服务器使用 UTC 时也不会影响角色看到的本地日期和星期。

`shortContextMessages` 控制注入的即时群聊条数，默认 `120`，上限 `300`。学生图鉴只在消息里出现具体学生名或别名时才追加「被提到的学生详细设定」，所以扩大这个窗口不会把整份图鉴重复带进每条请求。

## 分层处理

回复流程分成四段，详细设计见 [DESIGN-LAYERED-AI.md](DESIGN-LAYERED-AI.md)。

**路由层**：每条群消息都会先过一遍路由，判断要不要回复、要挂哪些技能、要带哪些资料。
提示词刻意保持小，不带人设正文。规则保留否决权：被直接艾特必须回复，冷却中必须沉默，
路由层不能推翻。路由输出被 reasoning 截断时自动提高 Token 重试一次，仍失败则回退到规则决策，
不会因为路由出错而漏掉艾特。另一个角色机器人的消息仍受 `otherRoleBotReplyChance`
概率控制，AI 路由不能绕过机器人互聊概率。

```yaml
routerEnable: true
routerProfile: ""            #留空则使用 aiProfile
routerMaxTokens: 2400
routerReasoningEffort: "none"
```

**技能注册表**：技能按声明注册，执行层只能调用已注册且在开放清单内的技能。当前注册了
`reminder`、`memory`、`global-memory`、`sticker`、`poke-back` 五个。旧的
`<reminder>` / `<remember>` / `<global_remember>` / `<sticker>` 标签仍然兼容，
会被转换成同一批技能调用。

**资料预算**：角色核心、外貌、学生名录、学生详细设定、长期/全局/短期记忆、最近发言、
台词示例各自独立成项，常驻项必带，其余按路由点名的 id 注入，总量受 `promptTotalChars`
限制。上限存在的意义是防止某一份资料把整条提示词撑爆。

```yaml
promptTotalChars: 26000
```

**风格层**：默认不调用，先用本地规则判断回复是否带 AI 味。命中超长、破折号、Markdown
残留、AI 味词、列点、成对引号时才改写一次；改写不改事实、不增删信息、不动表达动作，
失败或明显变长时直接用原文。

```yaml
styleEnable: true
styleMaxChars: 60
styleProactiveEnable: false   #主动发言时是否也触发风格层
```

## 戳一戳与引用

被人戳一戳时，插件走和群消息同一条处理链路，只是把当前消息换成「有人戳了你」。
角色可以只回一句话、只戳回去、两者都做，或者干脆不理会，由执行层自己判断。

```yaml
pokeReplyEnable: true       #是否响应戳一戳
pokeBackEnable: true        #是否允许戳回去
pokeBackCooldownSecond: 60  #对同一用户戳回去的最小间隔
pokeStreakWindowSecond: 60  #连续戳一戳的统计窗口
pokeStreakThreshold: 3      #窗口内戳几次开始算骚扰
```

戳一戳默认是**主动互动的加分项**：单独戳一下只会小幅提升心情、好感和信任，不会涨厌烦。
只有在 `pokeStreakWindowSecond` 秒内被同一个人戳到 `pokeStreakThreshold` 次以上，才升级成骚扰事件，
开始涨厌烦并压低耐心；这一轮也不会再戳回去，角色会用一句短促的话让对方别戳，但不会辱骂或发火。

`MBB-Poke` 已经在处理戳一戳，所以本插件检测到它处于启用状态时会自动跳过戳一戳事件，
避免同一件事回两次。

群友用引用回复时，插件会把被引用的那条消息解析成 `[引用 某人：内容]` 前缀，跟在对方这次说的话前面一起交给模型，
否则角色只看到新消息、看不到对方在回应什么。解析顺序是先查本地消息流水（插件自己记录过的群消息），
查不到再调一次 OneBot 的 `get_msg`；两者都拿不到就退回普通消息。被引用内容截断到 60 字，避免长文撑爆上下文。

被艾特或被直接回复时，插件会引用原消息：

```yaml
quoteReplyEnable: true   #回复被艾特或被直接回复的消息时是否引用原消息
```

引用由规则强制，模型可以额外建议引用，但无法取消规则要求的引用。

## 消息合批

群里刷屏时不再逐条排队，而是先按窗口攒批：

- 同一群的消息先进入合批窗口，窗口内到达的新消息会并入同一批，默认窗口 2 秒
- 窗口结束后整批只做一次路由、一次生成；处理期间到达的消息进入下一批，
  等当前回合真正出声（含表情包等待结束）后立刻接手，不再额外等一个窗口
- 同一群同一时刻只有一个回合在跑，回复冷却的判定与时间戳写入加锁，避免并发回合同时通过冷却检查各回一次
- 一批最多合并 10 条消息，超出时优先丢掉最早的非点名消息，被艾特或引用角色的消息优先保留
- 等待超过 `messageBatchMaxAgeSecond`（默认 20 秒）且没有被点名的消息直接丢弃，避免回复一分钟前已经翻篇的话题
- 合批回合允许模型返回多段回复，每段用 `to` 指向批内某一条消息，可以分别回应不同的人
- 合批回合最多回复 `batchMaxSegments` 段，默认 4 段；单条消息仍按原来的最多 2 段拆分
- 段内换行和单回合一样会拆成多条消息发送，引用只挂在第一条上；整批最多发 `batchMaxSegments` 的两倍条数，避免刷屏
- `to` 越界、或者指向的消息与主消息不是同一个人时，该段降级为不引用，正文照常发送
- 批内消息会带上到达时间，提示词要求只回应批内列出的消息，最近群聊仅作为背景
- 关闭 `messageBatchEnable` 可以退回一条消息一个回合的旧行为

表情包等待同样改成了事件驱动：用户补发的表情包识别完成后立即合并，同一用户又发了新消息也会立刻结束等待，
只有这两种情况都没出现时才等到 `stickerAttachWindowSecond`；识别已经开始时最多再等 `stickerAttachMaxWaitSecond` 秒。

## 结构化输出

执行层按约定返回结构化结果，正文和表达动作在同一次生成里决定：

```json
{"text":"戳你哦！","actions":[{"type":"poke_back"}],"quote":true}
```

合批回合返回多段时改用 `segments`，每一段可以引用批内不同的原消息：

```json
{"segments":[{"text":"这句回你","to":2,"quote":true,"actions":[]},{"text":"那句回他","to":5,"quote":false,"actions":[]}]}
```

`to` 是批内消息序号（从 1 开始），越界或省略时回退到本轮主消息；每段的 `actions` 独立校验，
单段失败只丢弃该段，不影响其他段。

`actions` 只能从本轮开放的动作里选，未开放的动作会被丢弃。解析失败时退化为「整段内容
当正文、不带任何动作」，不会因为格式问题丢掉回复。动作发出前还会校验冷却与目标，
校验失败只丢弃该动作，正文照常发送。

## 自然语言提醒

角色会调用 AI 识别提醒意图和时间，并把任务写入 SQLite，重启后仍会恢复。识别失败时安静忽略，不再回退到内置时间规则，也不会再发“没有识别出具体时间”的提示：

```text
下午三点提醒我干活
明天早上八点提醒我开会
10分钟后提醒我喝水
晚上八点叫我交作业
```

到点后会调用角色 AI，按当前人设生成提醒正文，再主动发送：

```text
@用户 该干活了
```

AI 不可用或返回空内容时，会回退到固定模板。创建时保存的角色关系和任务信息也会一起提供给 AI。

控制台会输出：

```text
[提醒] AI识别成功 群xxx 用户xxx 时间=2026-10-04 15:00 内容=干活
[提醒] AI确认生成 #12 群xxx 用户xxx 内容=好，下午三点我会提醒你干活~
[提醒] 创建 #12 群xxx 用户xxx 时间=2026-10-04 15:00 内容=干活 识别=AI
[提醒] 触发 #12 群xxx 用户xxx 内容=干活
[提醒] AI生成 #12 群xxx 用户xxx 内容=该干活了
[提醒] 发送成功 #12 群xxx 用户xxx
```

`reminderEnable` 控制是否启用，`reminderAiParse` 控制是否使用 AI 识别（关闭时改用内置规则解析），`reminderMaxDays` 控制最长提前天数，默认 30 天。

提醒消息会携带不可见标记。其他角色机器人识别到该标记时会跳过，避免两个角色互相抢答，但 QQ 消息中不会出现固定文字前缀。角色名识别会同时参考配置和内置的桃井/绿/爱丽丝名称。

用户还可以通过 `/reminder` 管理自己的任务：

```text
/reminder list
/reminder show 12
/reminder edit 12 明天早上八点 开会
/reminder delete 12
/reminder clear
```

用户只能查看和修改自己在当前群的提醒；编辑会重新排期，旧定时器不会提前触发。

自我提醒会保存原始上下文，并在到点生成时明确“这是角色自己该做的事”，避免把“让小桃十点睡觉”误写成提醒用户。

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

`/role memory merge` 和 `/role gmemory merge` 属于手动合并，即使当前数量没有超过上限，也会强制至少执行一轮分批合并。

## 台词语料检索

语料文件放在插件数据目录：

```text
speech-corpus/speech-corpus-aris.jsonl
speech-corpus/speech-corpus-momoi.jsonl
speech-corpus/speech-corpus-midori.jsonl
```

插件内置了三份初始语料，首次运行会自动释放到上述目录；已有语料文件不会被覆盖。

每行一个 JSON 对象：

```json
{"id":"aris-000001","role":"aris","text":"爱丽丝明白了！","tags":["日常","任务"],"emotion":"curious","scene":"group_chat","source":"设定集","weight":1.0,"spoiler":0}
```

插件会使用当前消息和最近群聊上下文做本地字符 n-gram 检索，把少量参考台词注入角色提示词。参考示例只用于学习表达方式，生成后还会做相似度检测，避免直接照抄台词。

检索除了字面相似度，还会用到语料自带的元信息：

1. **情绪加成**：角色当前厌烦偏高或耐心偏低时偏好 `annoyed`/`serious`，心情好时偏好 `happy`/`excited`/`proud`，心情低时偏好 `sad`/`serious`。情绪只在明显偏离基线时才参与，因为语料里 `neutral` 占了四分之三。
2. **场景加成**：直接点名或引用回复偏好 `reply`，戳一戳偏好 `group_chat`，其余走 `daily`。
3. **同一出处只取一条**：避免整批示例来自同一段剧情，语气高度雷同。
4. **剧透过滤**：`spoiler` 超过 `speechSpoilerLevel` 的台词直接不参与检索，默认只排除严重剧透。

情绪与场景是加成而不是过滤条件：语料里 neutral 占大多数，硬过滤会把可用台词砍掉大半，加成只影响同类候选之间的排序。

相关配置：

```yaml
speechCorpusEnable: true
speechCorpusDirectory: "speech-corpus"
speechRetrievalCount: 8
speechRetrievalMaxChars: 1200
speechRetrievalMinScore: 0.35
speechSpoilerLevel: 1
speechSimilarityThreshold: 0.78
speechSimilarityMinChars: 6
```

## 可选知识库

知识库用来回答事实类问题，人设、口癖和关系仍然由 persona 负责。格式标准见 [KNOWLEDGE.md](KNOWLEDGE.md)。

目录结构：

```text
knowledge/
├─ ba.students/
│  ├─ _index.md
│  ├─ midori.md
│  └─ hina.md
└─ REPORT.md
```

每个条目一个 md 文件，文件头写 `id`、`name`、`summary` 等元信息，正文按 `##` 小节切分。插件启动时建立小节级索引，检索时先按 `name` 和 `aliases` 锁定实体，再按字符 n-gram 打分取小节，只注入命中的摘要和小节，不注入整篇档案。命中实体时只保留被锁定的条目，避免问小绿却把小绿的属性套到别人身上。

知识库由路由层按需点选，不是每条消息都注入；单条、单库和提示词总量三层预算同时生效。命中的库、条目数、小节数和占用字符会打印在控制台日志里。

`knowledgeStudentsLibrary` 指向学生档案库的库名（默认 `ba.students`）。这个库启用后，插件会**停用自带的学生详细设定资料**：
`students.json` 里的 69 人只有外貌，而知识库的学生条目覆盖 153 人并且含性格、关系、梗与剧情，
两者同时存在会让同一份学生信息被注入两次。停用后路由层不再推荐 `students.detail`，问起学生时统一由知识库回答。
把这一项留空，或者知识库里没有该库，就会退回原来的自带名录，不会丢功能。

同一个开关还会接管另外两处学生资料：

1. **识图参考**：传给 `MBB-Vision` 的学生外貌参考改为从知识库条目的「外貌」小节生成，
   总预算 `visionReferenceMaxChars`（默认 `32000`）按学生数平均分配，保证 153 人都能进候选列表，
   不会因为截断丢掉后半批。实测约 2.8 万字，比原来 69 人的自带图鉴（约 3.9 万字）更小且覆盖更全。
   换参考文本会改变 `MBB-Vision` 的缓存键，所以升级后第一次识图会重新识别一次。
2. **一句话印象**：角色「了解的学生」那 9 条印象改为从知识库条目的 `summary` 取，
   避免人设里写死的印象和知识库说法不一致；名字匹配不上时保留人设原文。

首次启动时插件会在 `knowledge/` 下释放一个 `example/` 示例库（`enabled: false`，不参与检索），
用来演示目录与文件格式。这个示例只在知识库目录第一次创建时写入，**直接删掉 `example/` 后不会在重启时被补回来**；
只有把整个 `knowledge/` 目录删掉，插件才会重新释放一份示例。

```text
/role kb list                查看已加载的库、条目数、小节数与目录
/role kb reload              改完文件后重建索引，不用重启进程
/role kb search <文本>        查看命中的条目、小节和分数
/role kb on | off            开关知识库
```

相关配置：

```yaml
knowledgeEnable: true
knowledgeDirectory: "knowledge"
knowledgeStudentsLibrary: "ba.students"
knowledgeMaxEntries: 4
knowledgeMaxSectionsPerEntry: 2
knowledgeMinScore: 1.0
knowledgeInjectMaxChars: 2000
knowledgeSpoilerLevel: 0
```

角色 AI 可以在普通回复末尾输出结构化标记来主动创建任务：

```text
好，那我们玩到八点<reminder>{"time":"2026-10-04 20:00:00","task":"玩游戏到八点","target":"self"}</reminder>
```

标记及其内容不会发送到 QQ。`target` 为 `self` 时是角色自己的提醒，为 `user` 时提醒当前群友。到点后仍然会调用角色 AI 生成正文，并通过消息段艾特对应用户。

角色也可以直接查询、修改和删除当前用户在当前群的提醒：

```text
<reminder>{"action":"list"}</reminder>
<reminder>{"action":"delete","id":12}</reminder>
<reminder>{"action":"edit","id":12,"time":"2026-10-05 08:00:00","task":"开会"}</reminder>
```

角色提示词里会带当前用户的待触发提醒，但**分两种粒度**：只有对方确实在问提醒安排（消息里出现“提醒 / 几点 / 什么时候 / 安排 / 记了”）时才给完整清单，
其他情况只给“共几条 + 最近一条”，并附带一句“只是背景资料，不要在回复里逐条复述时间与内容”。
这样既能回答“我有哪些提醒”，又不会在普通闲聊里把待办当成素材念一遍。

## 重复回复拦截

生成回复后会跟最近的角色回复做两道比对，命中就整条丢弃：

1. **字面相似度**：bigram Dice 达到 `repeatSimilarityThreshold`（默认 0.72）
2. **长公共片段**：最长公共子串达到 `repeatCommonRunMinChars`（默认 8 字）

第 2 条是为“同一组信息换个尾巴”的复读准备的。实测“布丁明早验，咖喱七点，别糊锅”和“布丁明早验，咖喱七点，再不睡就凉了”的 bigram 相似度只有 0.516，够不着 0.72；
但两者的公共片段有 9 个字，会被第 2 条拦下。同一开头重复和“嗯”开头另有 `repeatOpeningLimit` 限制。

## 多角色部署

同一群部署桃井、绿、爱丽丝等多个角色时，必须通过 `otherRoleBotQQs` 明确填写其他角色机器人的 QQ。只有 QQ 命中该列表时，插件才会把对方当作角色本人；昵称或群名片叫“才羽桃井”“小绿”“爱丽丝”不会自动获得角色身份。`otherRoleBotNames` 现在只用于识别文本中提到了哪些角色名。

默认接话概率为 `0.50`，在没有真人插话时最多连续处理 2 条对方消息。

常用管理命令：

```text
/role bot status
/role bot chance 0.5
/role bot max 2
/role bot qq add 1004331369,123456789
/role bot qq list
/role bot name reset
```

名称识别支持 `list`、`add`、`remove`、`set`、`clear` 和 `reset`。QQ 识别支持 `list`、`add`、`remove`、`set` 和 `clear`。

## 配置补全

插件启动和重载时会检查 `config.yml`。缺失的配置项会自动追加到文件末尾，并附带中文注释。旧版本默认的 `otherRoleBotReplyChance: 0.10` 会一次性迁移为 `0.50`。

如需手动触发：

```text
/role config repair
```

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

## 升级说明

`config.yml` 中缺失的配置项会自动补全。角色文件仍只在文件不存在时释放；如果要应用新版桃井或绿设定，可以执行 `/role persona reset <文件名>`，或手动合并 `persona-*.json`。

情绪机制升级到配置结构版本 18 后，会自动创建 `plugin_mbb_roleplay_mood`、`plugin_mbb_roleplay_relation` 和 `plugin_mbb_roleplay_emotion_event` 三张表；黑名单机制会创建 `plugin_mbb_roleplay_blacklist`。旧记忆备份仍可恢复；包含 `emotion` 和 `blacklist` 字段的新备份会同时恢复情绪、用户关系和黑名单。该版本还会为已有关系表补齐小数累计字段，并自动迁移旧初始好感、每日变化上限和情绪分析单项上限。
