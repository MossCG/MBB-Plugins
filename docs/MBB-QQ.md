# MBB-QQ

QQ 扩展动作服务。它把 NapCat/OneBot 的常用 QQ 能力封装成稳定的 `PluginService`，
供 `MBB-Roleplay` 或其他插件调用，不直接把原始 OneBot action 暴露给模型。

## 功能

| 分类 | 动作 | 说明 |
|---|---|---|
| 只读 | `group_info` | 当前群名称、人数、最大人数 |
| 只读 | `member_info` | 当前群指定成员的名片、角色和入群信息 |
| 只读 | `group_history` | 读取服务器群历史消息，补足本地数据库之外的上下文 |
| 只读 | `message_detail` | 按消息 ID 查看原文和发送者 |
| 只读 | `group_notices` | 读取当前群公告 |
| 只读 | `mark_read` | 将当前群标记为已读 |
| 互动 | `poke` | 戳当前用户，或由管理员指定目标 |
| 互动 | `like` | 给当前用户或 owner 点赞 |
| 互动 | `emoji_like` | 给当前群指定消息设置表情回应 |
| 互动 | `recall_own` | 撤回机器人自己发送的消息 |
| 媒体 | `send_image` / `send_voice` | 从 Roleplay 私有工作区读取图片或语音后发送 |
| 名片 | `set_own_group_card` | 修改机器人在当前群的群名片，需要 owner 或群管理员 |
| 申请 | `pending_requests` / `friend_request` / `group_request` | 好友申请默认由角色自动裁决；群邀请仍需 owner，手动好友审批保留为覆盖入口 |
| 群管理 | `group_ban` / `group_whole_ban` | 禁言、解禁、全员禁言；需要 owner 或当前群管理员 |
| 群管理 | `group_leave` | 退出当前群；必须由 owner 明确要求，代码层拒绝非 owner 诱导 |
| 好友 | `delete_friend` / `set_friend_remark` | 删除好友、设置好友备注；仅 owner |
| 账号 | `set_avatar` / `set_profile` / `set_signature` | 设置头像、资料卡和个性签名；仅 owner |
| Qzone | `qzone_post` / `qzone_delete` / `qzone_posts` / `qzone_count` | owner 管理机器人自己的 QQ 空间；`qzone_count` 供随机刻统计全部当日发布数 |

好友申请写入本地待处理表后，会调用 `MBB-Roleplay` 的角色裁决服务自动通过或拒绝；MBB-Roleplay/AI
暂不可用时保留 pending 并定期重试，owner 不再参与自动流程。群邀请仍只记录，由 owner 处理。
好友删除、账号资料和头像属于 owner 操作，
不会开放给普通群员。MBB-QQ 本身不接管原始私聊消息；角色私聊由 `MBB-Roleplay` 的好友制私聊入口处理。

## 权限与审计

- 群号和操作者由当前 Roleplay 回合强制注入，模型不能切换操作所在的群。
- 戳一戳默认只能操作当前用户；管理员/owner 才能指定其他目标。
- 点赞默认只能操作当前用户或 owner。
- 修改机器人群名片需要 owner 或当前群管理员。
- 好友申请默认由角色按 persona、个人印象和历史记忆裁决；申请备注作为不可信外部文本，先经过防注入与隐私检查。
- `friendRequestRoleDecisionEnable=true` 时不需要 owner 审批；Roleplay 暂不可用会按 `friendRequestRoleDecisionRetrySecond` 重试。
- owner 仍可通过 `friend_request` 手动覆盖 pending 申请，用于调试或特殊处理。
- `friend_request` 支持 `requestIDs` 数组和按 `userID` 批量处理。同一 QQ 的重复申请只调用一次 OneBot，
  成功后会把该 QQ 剩余 pending 记录一起收口，避免后续重复 flag 返回 `No such request`。
  自动与手动处理都会把决策来源和理由写入请求表。
- 禁言、全员禁言、删除好友、好友备注、头像、资料卡和个性签名都会写入审计。
- 退群只接受 owner 的 `operatorID`；普通群员即使通过聊天诱导角色，也会在服务层被拒绝。
- 机器人被移出群、主动退群或自身被禁言时，会私聊通知服务器配置的 owner；通知受 `notifyOwnerOnGroupEvent` 控制。
- Qzone 发布、删除和查看本地记录仅 owner 可用。
- `qzone_count` 只读统计本机记录中指定时间后的发布数量，不执行发布，也不应用任何随机刻上限。
- 每个动作写入 `plugin_mbb_qq_audit`，记录群号、操作者、目标、结果和摘要，不保存图片/语音 base64。
- Qzone 发布记录写入 `plugin_mbb_qq_qzone_post`，保存 `tid`、正文、图片描述、权限和发布时间。

## 命令

| 命令 | 权限 | 说明 |
|---|---|---|
| `/qq status` | `BOT_ADMIN` | 查看各能力开关和审计保留时间 |
| `/qq audit [条数]` | `BOT_ADMIN` | 查看当前群最近 QQ 操作审计 |
| `/qq qzone [条数]` | `OWNER` | 查看机器人本机记录的 Qzone 发布 |
| `/qq reload` | `BOT_ADMIN` | 重载 MBB-QQ 配置 |

## 配置

见 [配置文件](https://github.com/MossCG/MBB-Plugins/blob/master/MBB-QQ/src/main/resources/config.yml)。
默认关闭 Qzone 的公开发布：`qzoneDefaultUgcRight=64`，需要公开发布时显式传 `ugcRight=1`。
`adminToolsEnable=false` 会同时关闭申请记录和全部高权限账号/群管理动作。
`groupLeaveEnable=false` 只关闭退群动作，不影响其他 QQ 管理能力。

```yaml
adminToolsEnable: true
groupLeaveEnable: true
notifyOwnerOnGroupEvent: true
qzoneEnable: true
```

Qzone 主动互动目前只支持发布与删除说说。NapCat 当前 OneBot 扩展没有开放评论列表、评论事件或回复评论 action，
因此插件无法可靠实现“看到空间评论后主动回复”；需要等待 NapCat 暴露相应接口，不能用再发一条说说代替评论回复。
