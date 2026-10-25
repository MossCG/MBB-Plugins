# MBB-Sticker

MoBoxBot 表情包收录、标签检索与发送插件。

## 特性

- 平铺存储表情包文件，不使用分类目录
- 标签由 AI 按情绪、态度和聊天场景识别，可动态扩展
- AI 返回中文、非 JSON 或 Token 不足时，会自动二次整理为英文情绪标签
- 私聊 `/sticker receive` 快速收录图片
- 收录完成后引用原图片回复标签信息
- 下载图片按 URL 或响应类型保留 JPG、PNG、GIF、WebP 格式，收录完成后清理临时文件
- 按标签查找时只返回匹配项，不会回退发送无关表情包
- AI 多次整理仍失败时先用 `unlabeled` 标记，后续可以人工补标签
- 安装 `MBB-Vision` 后，表情包按 SHA-256 和 `file_unique` 去重，重复图片不会再次识别或入库
- 收录时会输出命中缓存或新增表情包的识别结果日志
- 提供 `MBB-Sticker` 公共服务供 Roleplay 调用
- `add` 服务动作接受 OneBot 图片段字段，可带标签和描述；缺少标签或描述时再调用识图补全
- `MBB-Roleplay` 发现当前或最近图片适合复用时，可以调用 `sticker_save` 主动收藏

## 随机化

随机发送时会避免最近使用的表情包，并按 `usePenalty`（默认 `0.5`）对已发送次数多的表情包降低权重，减少同一张图反复出现。

## 指令

| 指令 | 权限 | 说明 |
|---|---|---|
| `/sticker` | `EVERYONE` | 随机发送一张表情包 |
| `/sticker happy,cat` | `EVERYONE` | 按标签发送 |
| `/sticker receive` | `EVERYONE` | 私聊进入收录模式 |
| `/sticker receive stop` | `EVERYONE` | 结束收录 |
| `/sticker list` | `BOT_ADMIN` | 查看收录列表 |
| `/sticker stats` | `BOT_ADMIN` | 查看数量和全部可用标签 |
| `/sticker tag <ID> <标签>` | `BOT_ADMIN` | 修改标签 |
| `/sticker retag <ID>` | `BOT_ADMIN` | 用 AI 重新生成情绪标签 |
| `/sticker remove <ID>` | `BOT_ADMIN` | 删除表情包 |
| `/sticker reload` | `BOT_ADMIN` | 重载数据 |

## 存储

表情包图片放在插件目录的 `stickers/` 下，`stickers.json` 里存的是**相对路径**：

```json
{ "file": "stickers/sticker-20261019013247-0779.jpg" }
```

这样整个插件文件夹可以随便搬家，图片不会集体失效。目录外的文件仍然存 `file://` 绝对路径。
旧版本存的 `file://` 绝对路径在加载时自动转换；如果插件目录已经搬过家，会按文件名到
`stickers/` 里回找一份。对外的 `MBB-Sticker` 服务（`random` 等）始终返回可用的绝对
`file://` 地址，调用方不需要关心存储格式。

`stickers.json` 使用带换行缩进的 JSON 保存，方便人工查看和改动。

## 服务动作

| 动作 | 说明 |
|---|---|
| `tags` | 返回当前可用标签 |
| `random` | 按标签随机选择；`tags` 为空时从全部表情随机选择 |
| `candidates` | 按标签、最近使用规避和权重随机返回候选；支持 `limit`、`groupID`、`userID`，最多 12 条 |
| `pick` | 按 `id` 确认视觉选择，记录使用次数并返回可直接发送的 `file://` 地址 |
| `add` | 从 OneBot 图片段导入并去重，字段为 `file`、`fileUnique`、`url`、`tags`、`description`、`source` |
| `stats` / `reload` | 查看统计或重载表情库 |

## 标签规则

默认使用小写英文 snake_case：

```text
^[a-z][a-z0-9_]{1,31}$
```

AI 只把情绪、态度和聊天用途放进 `tags`，画面、服装、发色等视觉信息放进 `description`。可用标签集由所有启用表情包的 tags 自动去重生成。
