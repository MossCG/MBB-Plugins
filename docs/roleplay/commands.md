# MBB-Roleplay 指令

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
| `/role memory merge` | `BOT_ADMIN` | 手动整理合并当前群长期记忆，完成后反馈结果 |
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
| `/role gmemory merge` | `BOT_ADMIN` | 手动整理合并全局永久记忆，启动时提示并完成后反馈结果 |
| `/role gmemory group ...` | `BOT_ADMIN` | 管理永久记忆学习白名单群 |
| `/role speech stats` | `BOT_ADMIN` | 查看台词语料加载状态 |
| `/role speech reload` | `BOT_ADMIN` | 重载台词语料 |
| `/role speech search <文本>` | `BOT_ADMIN` | 调试台词语料检索 |
| `/role speech on` / `off` | `BOT_ADMIN` | 开关台词语料检索 |

