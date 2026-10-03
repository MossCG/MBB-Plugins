# MBB-Roleplay

MoBoxBot 角色扮演插件，根据 `persona.json` 进行群聊扮演，并维护长期与短期记忆。

## 特性

- 群级开启，未开启的群不参与聊天
- 角色设定文件可替换
- 长期记忆：群友印象、群友信息、群内氛围、群梗、角色自己做过的事
- 短期记忆：近几天事件、群友日常、角色当前正在做的事
- 记忆游标按消息 ID 精确定位，AI 返回空短期记忆时也会正常推进，不会反复整理同一批消息
- 角色认为内容值得长期记住时，可以在回复末尾输出 `<remember>`，插件会剥离标记并单独触发一次记忆整理
- 不逐条回复，只回复角色感兴趣或被直接提及的消息
- 检测到另一个角色机器人时会显著降低接话概率，并限制双方无人插话时的连续往返次数
- 回复前后会对照最近的角色发言，对高度重复的语义和固定开头进行拦截
- 明确艾特其他用户时不会误判为对爱丽丝说话
- 同一话题下允许多个群员继续参与，AI 会判断是否真正接续话题
- 高频群聊模式：秒级回复冷却，默认每小时可回复 180 次
- 输出纯文本聊天，不使用 Markdown
- 图片、图片表情包和 QQ 表情消息直接忽略，不记录、不进入记忆、不触发回复

## 指令

| 指令 | 权限 | 说明 |
|---|---|---|
| `/role status` | `BOT_ADMIN` | 查看当前群状态 |
| `/role enable [群号]` | `BOT_ADMIN` | 开启当前群或指定群 |
| `/role disable [群号]` | `BOT_ADMIN` | 关闭当前群或指定群 |
| `/role groups` | `BOT_ADMIN` | 查看已开启群 |
| `/role reload` | `BOT_ADMIN` | 重载角色设定和配置 |
| `/role memory` | `BOT_ADMIN` | 查看当前群记忆概况 |
| `/role forget` | `BOT_ADMIN` | 清空当前群记忆 |
| `/role persona [文件名]` | `BOT_ADMIN` | 查看或切换角色设定文件 |

## 角色设定

首次运行释放：

```text
./MoBoxBot/plugins/MBB-Roleplay/persona.json
```

默认设定为《蔚蓝档案》的天童爱丽丝：游戏开发部、勇者见习生、RPG 爱好者，重视老师和伙伴，说话偶尔带游戏化表达。

另外内置两份角色设定：

- `persona-momoi.json`：才羽桃井，游戏开发部剧本作家，桃井式活泼吐槽。
- `persona-midori.json`：才羽绿，游戏开发部美术，安静认真、常吐槽桃井。

桃井补充了“又菜又爱玩”的特点，绿补充了“小绿”别名和“偷跑”等妹妹侧社区梗。

切换角色：

```text
/role persona persona-momoi.json
/role persona persona-midori.json
/role persona persona.json
```

桃井设定会识别“小桃”“王小兆”“优香大魔王”“给木给木”“苦呀西”等社区梗，但默认不会主动频繁使用。

默认口癖包括“邦邦咔邦！”、“爱丽丝，了解！”、“光呀！”等，要求低频自然使用，不会每句话都变成游戏台词。

“邦邦咔邦”只会出现在回复句首，作为类似任务启动提示音使用，不会放在句中或句尾。

可以修改名称、身份、性格、说话方式、兴趣、禁忌和行为规则。修改后执行：

```text
/role reload
```

## 依赖

需要安装并启用 `MBB-AI`。`MBB-AI` 负责模型调用、纯文本输出清洗和会话统计。

## 默认性能

```yaml
replyCooldownSecond: 5
maxRepliesPerHour: 180
interestReplyChance: 0.65
conversationWindowSecond: 180
continuationReplyChance: 0.80
otherParticipantReplyChance: 0.45
otherRoleBotReplyChance: 0.10
maxConsecutiveOtherRoleMessages: 2
shortContextMessages: 80
memoryUpdateMessages: 50
memoryExtractMessages: 300
memoryExtractBatches: 3
activeMemory: true
maxLongMemories: 150
recentReplyCheckCount: 8
repeatSimilarityThreshold: 0.72
repeatCheckMinChars: 6
repeatOpeningLimit: 2
minMessageLength: 2
```

非直接提及、非对话续接、非兴趣话题的消息不会参与回复。回复 prompt 要求尽量只输出一句话，不要反复纠缠同一个生活细节，也不要连续使用同一种开头或口癖。

## 多角色部署

同一群部署桃井、绿、爱丽丝等多个角色时，建议保留默认的 `otherRoleBotNames`，或通过 `otherRoleBotQQs` 明确填写其他角色机器人的 QQ。插件会降低角色之间的互聊概率，并在没有真人插话时截断连续往返。

## 记忆标记

默认开启 `activeMemory`。模型判断当前消息包含值得长期记住的人物信息、群梗或自身重要行为时，会在回复末尾输出：

```text
<remember>
```

标记不会发送到 QQ。插件会剥离标记，把实际回复正常发出，并以当前群最近未整理的消息执行一次记忆更新。

## 升级说明

`config.yml` 和角色文件只在文件不存在时释放。升级到本版本后，如果要使用新的默认开关和口癖说明，需要手动合并 `config.yml` 的新增配置，或删除旧文件后重新释放并按需恢复自定义内容。
