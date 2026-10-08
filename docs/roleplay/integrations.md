# MBB-Roleplay 技能与集成

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

提醒消息会携带不可见标记。其他角色机器人识别到该标记时会跳过，避免两个角色互相抢答，但 QQ 消息中不会出现固定文字前缀。角色名识别只使用配置的 `otherRoleBotNames`，默认不内置具体作品角色名。

用户还可以通过 `/reminder` 管理自己的任务：

```text
/reminder list
/reminder show 12
/reminder edit 12 明天早上八点 开会
/reminder delete 12
/reminder clear
```

用户只能查看和修改自己在当前群的提醒；编辑会重新排期，旧定时器不会提前触发。

自我提醒会保存原始上下文，并在到点生成时明确“这是角色自己该做的事”，避免把角色自己的待办误写成提醒用户。


## 台词语料检索

语料文件放在插件数据目录：

```text
speech-corpus/speech-corpus-<persona>.jsonl
```

插件内置示例语料，首次运行会自动释放到上述目录；已有语料文件不会被覆盖。

每行一个 JSON 对象：

```json
{"id":"example-000001","role":"example","text":"示例台词","tags":["日常","任务"],"emotion":"curious","scene":"group_chat","source":"设定集","weight":1.0,"spoiler":0}
```

插件会使用当前消息和最近群聊上下文做本地字符 n-gram 检索，把少量参考台词注入角色提示词。参考示例只用于学习表达方式，生成后还会做相似度检测，避免直接照抄台词。

检索除了字面相似度，还会用到语料自带的元信息：

1. **情绪加成**：角色当前厌烦偏高或耐心偏低时偏好 `annoyed`/`serious`，心情好时偏好 `happy`/`excited`/`proud`，心情低时偏好 `sad`/`serious`。情绪只在明显偏离基线时才参与，因为语料里 `neutral` 占了四分之三。
2. **场景加成**：直接点名或引用回复偏好 `reply`，戳一戳偏好 `group_chat`，其余走 `daily`。
3. **同一出处只取一条**：避免整批示例来自同一段剧情，语气高度雷同。
4. **剧透过滤**：`spoiler` 超过 `speechSpoilerLevel` 的台词直接不参与检索，默认只排除严重剧透。
5. **参考示例不沿用话题**：台词语料只用于参考语气和表达方式，提示词会明确要求不要沿用示例里的具体话题、职业内容或项目进度。

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

知识库用来回答事实类问题，人设、口癖和关系仍然由 persona 负责。格式标准见 [KNOWLEDGE.md](../../MBB-Roleplay/KNOWLEDGE.md)。

推荐直接安装公开知识库：

[MossCG/MBB-Knowledge](https://github.com/MossCG/MBB-Knowledge)

安装步骤：

1. 克隆或下载知识库仓库。
2. 把仓库里的 `knowledge/` 目录复制到运行目录：

```text
./MoBoxBot/plugins/MBB-Roleplay/knowledge/
```

3. 在群里执行：

```text
/role kb reload
/role kb list
```

目录结构：

```text
knowledge/
├─ <library>/
│  ├─ _index.md
│  └─ example.md
└─ REPORT.md
```

每个条目一个 md 文件，文件头写 `id`、`name`、`summary` 等元信息，正文按 `##` 小节切分。插件启动时建立小节级索引，检索时先按 `name` 和 `aliases` 锁定实体，再按字符 n-gram 打分取小节，只注入命中的摘要和小节，不注入整篇档案。命中实体时只保留被锁定的条目，避免把 A 角色的属性套到 B 角色身上。

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

