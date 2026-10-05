# 分层 AI 设计

MBB-Roleplay 的分层处理设计。目标是把"要不要回、回什么、怎么说得像人"拆成独立环节，
让后续新增功能以注册表的形式接入，而不是继续往一个提示词里堆规则。

状态：第一阶段已落地，进度见文末「分阶段落地」。

## 已确认的取舍

| 项 | 决定 |
|---|---|
| 技能执行顺序 | 按类型分两段：状态类技能先执行，表达类技能由模型决定后执行 |
| 路由覆盖面 | 所有消息都过路由，不用关键词预筛 |
| 风格层目标 | 消除 AI 味，不是加强口癖 |
| 首批新技能 | 戳一戳回复 / 戳回去；对明确回复自己的消息使用引用回复 |

## 处理链路

```text
OneBot 事件（群消息 / 戳一戳）
  ↓
[0] 规则闸门        免费，可强制回复，也可强制忽略
  ↓
[1] 路由层 AI       输出 RouteDecision：是否回复、挂哪些前置技能、带哪些资料
  ↓
[2] 前置技能        状态类技能先跑，产出事实结果
  ↓
[3] 执行层 AI       结合事实、资料和技能片段，生成文本与表达动作
  ↓
[4] 后置动作        校验并执行戳回去、引用、表情包等表达动作
  ↓
[5] 风格层 AI       条件触发，只改文本，不动动作
  ↓
[6] 发送            文本与动作合并成消息段
```

第 3 层依赖第 2 层，第 5 层依赖第 3 层，串行不可避免。因此第 5 层必须条件触发，
否则每条消息两次串行调用，延迟会直接影响可用性。

## 三个共享数据结构

分层能否成立，取决于这三样东西是否先立起来。它们比"分成几层"更重要。

### ConversationState

按群维护，路由层、执行层、技能共用同一份，避免各处各自猜。

- 当前话题与最近参与者
- 角色是否已参与该话题、最后一次发言时间
- 悬空问题：有人直接叫过角色但还没回应
- 与另一个角色机器人的往返计数
- 冷却与频率窗口状态

### SkillResult

技能先执行的直接产物。因为它是"已经发生的事实"，模型无法凭空声称技能成功。

```java
class SkillResult {
    String skillId;
    boolean success;
    boolean relevant;      // 是否真的用上了，没用上就不进提示词
    String summary;        // 给执行层看的事实，例如"已创建提醒 #12，明天 08:00 提醒开会"
    JSONObject data;       // 结构化结果，供后续技能或日志使用
}
```

### RouteDecision

规则决策器和 AI 路由层产出同一种结构，两者可以互相替换或叠加。

```java
class RouteDecision {
    String addressed;      // direct / name / thread / ambient / none
    boolean reply;
    double confidence;
    List<SkillCall> skills;    // 带参数，例如 reminder 的时间与内容
    List<String> materials;    // 形如 students.detail:优香
    boolean quoteRequired;     // 由规则置位，模型不能取消
    boolean quoteSuggested;    // 模型建议
    String reason;             // 进日志，便于回溯误判
}
```

## 第 0 层：规则闸门

保持现在的规则判断，但明确它有两种权力：

- **强制回复**：被艾特、被回复、被点名。规则命中后 `reply=true` 且 `confidence=1`，
  路由层不能推翻。
- **强制忽略**：指令前缀、冷却中、频率超限、闭麦、另一个机器人的提醒标记。

这样做的原因是：漏掉一个直接艾特是用户立刻能感知的最差失败，不能交给概率判断。

## 第 1 层：路由

输入刻意保持小，不带人设正文：

- 当前消息、发送者、关系
- 最近若干条上下文（压缩成一行一条）
- 角色的兴趣摘要（兴趣词与梗的关键词，几十字）
- 可用技能清单（id + 一句话描述）
- 可用资料清单（只给 id，不展开内容）
- 对话状态摘要

输出为 RouteDecision 的 JSON 形式：

```json
{
  "addressed": "direct",
  "reply": true,
  "confidence": 0.82,
  "skills": [{"id": "reminder", "args": {"timeText": "明天八点", "task": "开会"}}],
  "materials": ["students.detail:优香", "memory.long"],
  "quoteSuggested": true,
  "reason": "直接艾特并给了具体时间"
}
```

约束：

- 技能与资料的 id 必须来自当轮清单，未知 id 直接丢弃
- 技能最多 3 个，资料最多 6 项，防止路由把提示词重新撑爆
- 温度 0，输出上限 200 到 300，思考强度 low
- 调用失败或 JSON 不合法时回退到规则决策，绝不因为路由失败而沉默

### 提醒解析并入路由

现在提醒意图判断是一次独立调用（`parseWithAi`）。路由本来就要读消息内容，
可以让它直接输出 reminder 技能及其参数，省掉一次往返。

代价是提醒的失败策略要跟着改：现在是"识别失败安静忽略"，并入后变成"路由失败则整条消息
回退规则决策"，需要确认这是想要的行为。

## 第 2 层：技能

### 前置与后置

最初设想是"所有技能都先执行，再把结果喂给模型"。这个假设对状态类技能成立，
对表达类技能不成立。

原因：戳回去和引用是话语的一部分。"戳你哦！"和随之而来的那一下戳是一件事，
不能拆成"先决定戳，再让模型配一句话"。引用哪条消息同样取决于这句话要接谁。
如果强制先执行，模型只能被动地给已经发生的事配台词，角色感会明显变差。

因此按技能性质分两段：

| 类型 | 时机 | 例子 | 决策者 |
|---|---|---|---|
| 状态类 | 执行层之前 | 创建 / 修改 / 删除提醒、写入记忆、图片识别、查表情包 | 路由层 |
| 表达类 | 执行层之后 | 戳回去、引用回复、发表情包、艾特 | 执行层 |

状态类先执行的意义在于它产生的是**已经发生的事实**，模型无法凭空声称成功。
表达类由执行层决定的意义在于动作和文本是同一次表达，必须一起生成。

两段之间有硬边界：执行层不能触发状态类技能，路由层不能决定表达类动作。

### 注册表

```java
interface Skill {
    String id();
    String name();
    String description();          // 给路由看的一句话
    List<String> triggers();       // 免费预筛，用于日志与兜底
    CommandPermission permission(); // 权限门槛
    Phase phase();                 // PRE 状态类 / POST 表达类
    String promptFragment(SkillContext context);
    SkillResult execute(SkillContext context);  // PRE 由流水线调用，POST 由发送层调用
}
```

`SkillContext` 携带群号、用户、消息 ID、消息文本、关系、对话状态、角色设定和原始事件。

### 迁移范围

现有能力按技能重新组织：

| 现有实现 | 迁移后 | 阶段 |
|---|---|---|
| 正文里的 `<reminder>` 标签 | reminder 技能，先执行后回报事实 | PRE |
| 正文里的 `<sticker>` 标签 | sticker 技能，保留为表达动作 | POST |
| 正文里的 `<remember>` | memory 技能 | PRE |
| 正文里的 `<global_remember>` | global-memory 技能 | PRE |
| `parseWithAi` 独立调用 | 并入路由 | PRE |

### 戳一戳

新增 NoticeEvent 监听，走同一条链路，入口上下文从"消息文本"换成"有人戳了你"。

```java
// 识别
notice_type = notify, sub_type = poke, target_id == self_id

// 戳回去
callAction("group_poke", {group_id, user_id})
```

`poke-back` 是表达类技能，由执行层决定是否触发。执行层在生成"戳你哦！"的同时声明要
戳回去，两者一起发出。这样角色可以戳回去、可以只吐槽不戳、也可以完全不理会。

冲突处理：MBB-Poke 已经在处理戳一戳。Roleplay 在启动和重载时检查 MBB-Poke 是否已启用，
已启用则 Roleplay 跳过戳一戳事件，避免同一件事回两次。

### 引用回复

引用是发送时属性，属于表达类，和执行层生成的文本一起决定。

规则判定优先：消息段里存在指向机器人自己消息的 `reply`，或消息里艾特了机器人时，
置位 `quoteRequired`。

模型可以建议引用（`quoteSuggested`），但不能取消规则要求的引用，两者取或。

发送层统一入口：

```java
sendReply(groupID, messageID, quote, segments)
// quote 为真时，把 MessageUtil.reply(messageID) 放在消息段首位
```

## 第 3 层：执行

提示词按固定顺序拼装，不再是一整块：

1. 角色核心：身份、性格、说话方式、行为规则
2. 选中资料
3. 选中技能的提示片段
4. 前置技能的执行结果（事实）
5. 当前时间、关系、待触发提醒
6. 可用表达动作清单与输出格式约束

行为规则按选中技能裁剪，例如没有选中 reminder 就不注入提醒相关规则。

### 结构化输出

执行层返回结构化结果，而不是正文加标签：

```json
{
  "text": "戳你哦！",
  "actions": [{"type": "poke_back"}],
  "quote": true,
  "reason": "对方在戳我，回戳一下"
}
```

这同时修掉了现有的一类问题：现在靠正文里的 `<reminder>`、`<global_remember>` 标签
传递动作，标签一旦写错就会漏到 QQ 消息里。

动作清单由当轮可用的表达类技能决定，模型不能发明。解析失败时退化为"整段内容当正文、
不带任何动作"，并剥离残留标签。

### 后置动作校验

动作发出前必须过一遍校验：

- 动作类型必须在当轮清单内
- 戳回去对同一用户有冷却，单条消息最多一次
- 引用目标必须是真实存在的消息 ID
- 表情包 tag 必须在当前真实标签集内

校验失败只丢弃该动作，不影响正文发送。

## 第 4 层：风格

目标是消除 AI 味。默认不调用，先本地判定再决定是否花这次调用。

### 参考

特征清单主要参考维基百科的 Wikipedia:Signs of AI writing
（https://en.wikipedia.org/wiki/Wikipedia:Signs_of_AI_writing）。
它原本是给维基编辑者识别 AI 文风用的，按内容、语言、格式三类归纳，比零散的经验贴系统。
其中和群聊最相关的几类：

- 过度强调意义与影响（"这不仅是……更是……"）
- 浅层分析（"这体现了……的重要性"）
- 大纲式收尾（"总的来说""综上所述"）
- 负向平行结构（"不是 X，而是 Y"）
- 三段式排比
- 破折号滥用
- 用 emoji 当格式
- 协作式口吻（"希望这对你有帮助""如果需要我可以……"）
- 直接输出 Markdown

该页面是英文语料归纳的，中文还要单独补一批它覆盖不到的特征：

- 四字词与成语堆砌
- 书面连接词（"首先""其次""值得注意的是""与此同时"）
- 空泛概括（"具有重要意义""在一定程度上"）
- 翻译腔（"作为一个……""关于这一点"）
- 对称排比与总分总结构

### 触发条件

本地检测命中任一条件才触发：

- 执行层输出被重复检测命中
- 输出长度超过阈值
- 出现 AI 味特征：总结式开头、列点符号、"首先 / 其次 / 总的来说"、
  书面语连接词、破折号、成对引号
- 主动发言（非被叫）

风格层输入：执行层原文、角色语气要点、3 到 5 条真实台词示例、禁止清单。

硬约束：

- 不改事实、不增删信息
- 不输出 Markdown
- 长度不超过原文的 1.2 倍
- 调用失败直接用原文

台词示例从现有语料检索取，这正是语料功能的用途。

## 资料与预算

```java
interface Material {
    String id();
    boolean alwaysOn();
    int priority();       // 越大越先保留
    int charBudget();     // 单项预算
    String load(MaterialContext context);
}
```

候选项：

| id | 常驻 | 说明 |
|---|---|---|
| persona.core | 是 | 身份、性格、说话方式、行为规则 |
| persona.appearance | 否 | 自己的外貌，被问到时才带 |
| persona.memes | 否 | 了解的梗 |
| persona.relationships | 否 | 角色关系 |
| persona.storyMemory | 否 | 剧情记忆 |
| students.brief | 是 | 学生一句话名录 |
| students.detail:名字 | 否 | 单个学生完整外貌 |
| memory.long | 是 | 长期记忆，按预算裁剪 |
| memory.global | 是 | 全局永久记忆 |
| memory.short | 是 | 短期摘要 |
| context.recent | 是 | 最近群聊 |
| speech.corpus | 是 | 台词示例，小额度 |
| reminder.pending | 否 | 待触发提醒 |

分配策略：先放 alwaysOn，再按 priority 从高到低填，直到总预算用尽。

总预算定为 16k 字符。当前执行层系统提示约 5.2k 字符，16k 给新增资料留出充足余量。
当前阶段的目标是先把功能做全、让角色像人，压缩成本放到后面单独做一轮。

上限存在的意义不是省钱，而是防止重演学生图鉴把每条请求撑到 2.7 万字的情况。

## 可观测性

每次处理生成关联 ID：`群号-消息ID-时间戳`。

```text
[角色] 路由 群xxx 相关=xxx-123-456 reply=true addressed=direct 技能=[reminder] 资料=[memory.long] 耗时=1200ms
[角色] 技能 reminder 群xxx 相关=xxx-123-456 成功 摘要=已创建提醒 #12 明天 08:00
[角色] 执行 群xxx 相关=xxx-123-456 耗时=2100ms finish=stop token=2600/80 长度=42
[角色] 风格 群xxx 相关=xxx-123-456 触发=重复 耗时=900ms
```

`MBB-AI` 的动作名按层区分：`roleplay-route` / `roleplay-reply` / `roleplay-style`，
这样 `/ai usage` 能直接看出每一层各花了多少。

## 失败策略

| 层 | 失败时 |
|---|---|
| 规则 | 不适用 |
| 路由 | 输出被 reasoning 截断时提高 Token 重试一次；仍失败则回退规则决策，绝不因路由失败漏掉艾特 |
| 前置技能 | 把失败作为事实回报，执行层用角色语气说明；纯读取类失败则静默跳过 |
| 执行 | 放弃本次回复并记录日志 |
| 后置动作 | 丢弃该动作，正文照常发送 |
| 风格 | 直接使用执行层原文 |

任何一层都不允许阻塞发送。

## 成本与延迟预算

按当前状态估算（执行层系统提示约 5.2k 字符，回复上限 1200 token）：

| 层 | 输入 | 输出 | 调用 |
|---|---|---|---|
| 路由 | 约 600 到 900 token | 默认 1200，截断时最高重试 4000 | 每条消息 |
| 技能 | 本地为主 | — | 选中时 |
| 执行 | 约 2000 到 3000 token | ≤1200 | 需要回复时 |

提醒解析并入路由后，原本独立的那次调用被吸收，可以抵消一部分新增成本。

延迟是比成本更硬的上限：路由与执行串行，风格层串行。因此风格层必须条件触发。
另外可以在路由执行期间并行预取 alwaysOn 资料，减少等待。

## 分阶段落地

每一步都能独立验证，不需要一次性重写。

1. **抽象**（已完成）：RouteDecision、DecisionEngine、ReplyDraft、ConversationState 均已落地。
   原先散在各处的 lastReply / lastBotMessage / otherRoleStreak 等状态合并进 ConversationState，
   由路由层、执行层和技能共用。
2. **技能注册表**（已完成）：RoleplaySkill 接口 + RoleplaySkillRegistry，注册 reminder、
   memory、global-memory、sticker、poke-back 五个技能。旧的 `<reminder>` / `<remember>` /
   `<global_remember>` / `<sticker>` 标签仍兼容，会转换成同一批技能调用。
3. **资料注册表**（已完成）：RoleplayMaterial + RoleplayMaterialBudget，persona 核心、外貌、
   学生名录、学生详细设定、长期/全局/短期记忆、最近发言、台词示例各自独立成项，
   总量受 `promptTotalChars` 限制。
4. **AI 路由**（已完成）：RoleplayRouter 替换概率门，规则保留否决权；路由失败回退规则决策。
5. **风格层**（已完成）：RoleplayStyleDetector 本地判定 + RoleplayStyler 条件改写，
   默认只在超长、破折号、Markdown 残留、AI 味词、列点、成对引号时触发。

## 后续可选项

- 目前所有技能都是表达类（POST）。提醒时间解析仍走独立调用，等哪天要做"路由先建提醒、
  执行层只负责转述"时，把 reminder 改成 PRE 技能即可，接口已经预留。
- 路由层已经能返回 skills 与 materials，但 skills 目前只进日志，因为还没有 PRE 技能。

## 已定与待定

已定：

- 戳一戳：MBB-Poke 启用时 Roleplay 跳过该事件
- 资料总预算：16k 字符
- 风格层触发：沿用默认值（重复命中 / 超长 / AI 味特征 / 主动发言）
- 表情包等待窗口：5 秒
- 技能分两段：状态类先执行，表达类由执行层决定
- 提醒时间解析保持独立调用，不并入路由层，避免路由失败影响提醒识别

待定：

- 暂无。下一阶段开始前如果出现新的取舍，追加到这里。
