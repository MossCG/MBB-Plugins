# MBB-Roleplay

MoBoxBot 角色扮演插件，根据 `persona.json` 进行群聊扮演，并维护长期与短期记忆。

## 特性

- 群级开启，未开启的群不参与聊天
- 角色设定文件可替换
- 长期记忆：群友印象、群友信息、群内氛围、群梗、角色自己做过的事
- 短期记忆：近几天事件、群友日常、角色当前正在做的事
- 不逐条回复，只回复角色感兴趣或被直接提及的消息
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

## 角色设定

首次运行释放：

```text
./MoBoxBot/plugins/MBB-Roleplay/persona.json
```

默认设定为《蔚蓝档案》的天童爱丽丝：游戏开发部、勇者见习生、RPG 爱好者，重视老师和伙伴，说话偶尔带游戏化表达。

默认口癖包括“邦邦咔邦！”、“爱丽丝，了解！”、“光呀！”等，要求低频自然使用，不会每句话都变成游戏台词。

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
shortContextMessages: 80
memoryUpdateMessages: 50
memoryExtractMessages: 300
maxLongMemories: 150
minMessageLength: 2
```

非直接提及、非对话续接、非兴趣话题的消息不会参与回复。回复 prompt 要求尽量只输出一句话。
