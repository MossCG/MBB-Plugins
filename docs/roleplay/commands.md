# MBB-Roleplay 指令

所有标注 `<QQ>` 的参数都可以直接在群里艾特目标成员代替，例如 `/role blacklist add @某位群友 刷屏`。

## 指令

| 指令 | 权限 | 说明 |
|---|---|---|
| `/role status` | `BOT_ADMIN` | 查看当前群状态 |
| `/role random` | `BOT_ADMIN` | 查看随机刻开关、下次时间、上次行为和原因 |
| `/role enable [群号]` | `BOT_ADMIN` | 开启当前群或指定群 |
| `/role disable [群号]` | `BOT_ADMIN` | 关闭当前群或指定群 |
| `/role groups` | `BOT_ADMIN` | 查看已开启群 |
| `/role reload` | `BOT_ADMIN` | 重载角色设定和配置 |
| `/role config` | `BOT_ADMIN` | 查看配置文件与补全状态 |
| `/role config repair` | `BOT_ADMIN` | 手动补全缺失配置项 |
| `/role blacklist list` | `BOT_ADMIN` | 查看当前群黑名单 |
| `/role blacklist add <QQ\|@群友> [原因]` | `BOT_ADMIN` | 将用户加入当前群黑名单，后续过滤其消息和戳一戳，不删除已有用户记忆 |
| `/role blacklist remove <QQ\|@群友>` | `BOT_ADMIN` | 将用户移出当前群黑名单 |
| `/role blacklist clear` | `BOT_ADMIN` | 清空当前群黑名单 |
| `/role quiet on` / `off` | `BOT_ADMIN` | 开关当前群免打扰名单，默认开启 |
| `/role quiet list` | `BOT_ADMIN` | 查看当前群免打扰名单 |
| `/role quiet add [QQ\|@群友]` | `EVERYONE` | 把自己加入当前群免打扰名单，指定他人需要 `BOT_ADMIN` |
| `/role quiet remove [QQ\|@群友]` | `EVERYONE` | 把自己移出当前群免打扰名单，指定他人需要 `BOT_ADMIN` |
| `/role quiet clear` | `BOT_ADMIN` | 清空当前群免打扰名单 |
| `/role help [分类]` | `EVERYONE` | 按分类查看完整命令，分类：basic / quiet / blacklist / emotion / memory / role / content / feedback / tool / learn / train |
| `/role feedback [reset]` | `BOT_ADMIN` | 查看主动发言被接话统计，reset 重置当前群统计 |
| `/role expression list [状态]` | `BOT_ADMIN` | 查看表达候选，状态：pending / approved / rejected |
| `/role expression promote <ID>` | `BOT_ADMIN` | 通过表达候选并允许注入提示词 |
| `/role expression reject <ID>` | `BOT_ADMIN` | 拒绝表达候选 |
| `/role expression clear` | `BOT_ADMIN` | 清空当前群表达候选 |
| `/role review [now]` | `BOT_ADMIN` | 手动触发当前群每日复盘 |
| `/role attention [reset]` | `BOT_ADMIN` | 查看或重置当前群注意力 |
| `/role trace [条数]` | `BOT_ADMIN` | 查看当前群最近的对话 trace，包含路由指向、技能、原因、回复和 token |
| `/role tool stats` | `BOT_ADMIN` | 查看当前群工具调用统计 |
| `/role tool test` | `BOT_ADMIN` | 检查工具 schema、参数校验、工作区路径隔离和网页白名单限制 |
| `/role tool trace [条数]` | `BOT_ADMIN` | 查看最近工具调用、用户和时间 |
| `/role tool cancel` | `BOT_ADMIN` | 取消当前群正在执行的工具与 AI 等待 |
| `/role tool clear` | `BOT_ADMIN` | 清空当前群持久化的工具结果上下文 |
| `/role eval list` | `BOT_ADMIN` | 查看内置评测用例 |
| `/role eval run <id\|all>` | `BOT_ADMIN` | 运行指定评测用例或全部评测 |
| `/role learn [备注]` | `BOT_ADMIN` | 回复某条消息再发，采集那条消息和前后文到训练语料；不带引用时采集最近一条群消息 |
| `/role learn stats` | `BOT_ADMIN` | 查看累计采集的语料数量 |
| `/role learn recent [条数]` | `BOT_ADMIN` | 查看最近采集的语料记录 |
| `/role train` | `BOT_ADMIN` | 查看本地模型服务与训练状态；本地风格层当前未接入回复主链路 |
| `/role train corpus [条数]` | `BOT_ADMIN` | 把上次截止到现在的新群消息导出，并基于当前 adapter 继续训练；成功后推进截止时间 |
| `/role train fresh [条数]` | `BOT_ADMIN` | 从最早群消息开始导出，丢弃 adapter 链并从底座新开一轮训练 |
| `/role train learn` | `BOT_ADMIN` | 用 `/role learn` 采集的特挑语料提交训练 |
| `/role train cutoff [时间\|reset]` | `BOT_ADMIN` | 查看、设置或重置训练截止时间 |
| `/role train reload` | `BOT_ADMIN` | 让 MBB-Model 重新加载 adapter |

`/role quiet add` 和 `/role quiet remove` 允许普通群员自助使用；`/role` 的其他子命令仍然需要 `BOT_ADMIN`。

| `/role member [QQ\|@群友]` | `BOT_ADMIN` | 查看角色对某个群员的个人印象，不填参数则看自己 |
| `/role member clear <QQ\|@群友>` | `BOT_ADMIN` | 清空对某个群员的个人印象 |
| `/role mood [页码]` | `BOT_ADMIN` | 以图片查看当前群情绪和情绪事件 |
| `/role mood reset` | `OWNER` | 将当前群情绪重置到角色基线 |
| `/role emotion [QQ\|@群友] [页码]` | `BOT_ADMIN` | 以图片查看指定用户的关系、情绪原因和变化记录 |
| `/role emotion reset [QQ\|@群友]` | `BOT_ADMIN` | 重置自己或指定用户的关系状态，重置他人需要 owner |
| `/role emotion log [页码]` | `BOT_ADMIN` | 以图片查看当前群的情绪事件流水 |
| `/role emotion affinity set <QQ\|@群友> <0-100>` | `BOT_ADMIN` | 强制设置指定用户的好感度 |
| `/role emotion reason set <QQ\|@群友> <原因>` | `OWNER` | 手动设置指定用户的情绪原因 |
| `/role emotion reason clear <QQ\|@群友>` | `OWNER` | 清空指定用户的情绪原因 |
| `/role bot` | `BOT_ADMIN` | 查看机器人互聊设置 |
| `/role bot chance <0.3-1>` | `BOT_ADMIN` | 设置其他角色机器人的接话概率 |
| `/role bot max <1-10>` | `BOT_ADMIN` | 设置无人插话时的连续回应上限 |
| `/role bot qq ...` | `BOT_ADMIN` | 查看、增删或清空其他角色机器人 QQ，增删时可艾特机器人 |
| `/role bot name ...` | `BOT_ADMIN` | 查看、增删、重置或清空名称识别关键词 |
| `/role memory [页码]` | `BOT_ADMIN` | 以图片查看当前群短期记忆和长期记忆 |
| `/role memory merge` | `BOT_ADMIN` | 手动整理合并当前群长期记忆，完成后反馈结果 |
| `/role memory backup` | `BOT_ADMIN` | 备份全部群的短期、长期、永久记忆 |
| `/role memory backups` | `BOT_ADMIN` | 查看备份文件名列表 |
| `/role memory restore <文件名>` | `OWNER` | 从备份文件恢复全部记忆 |
| `/role memory timeline [页码]` | `BOT_ADMIN` | 按事件时间倒序查看长期记忆时间线 |
| `/role memory conflicts [条数]` | `BOT_ADMIN` | 查看因冲突被归档的长期记忆 |
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
| `/role gmemory merge` | `BOT_ADMIN` | 手动整理合并全局永久记忆，启动时提示并完成后反馈结果 |
| `/role gmemory group ...` | `BOT_ADMIN` | 管理永久记忆学习白名单群 |
| `/role speech stats` | `BOT_ADMIN` | 查看台词语料加载状态 |
| `/role speech reload` | `BOT_ADMIN` | 重载台词语料 |
| `/role speech search <文本>` | `BOT_ADMIN` | 调试台词语料检索 |
| `/role speech on` / `off` | `BOT_ADMIN` | 开关台词语料检索 |

