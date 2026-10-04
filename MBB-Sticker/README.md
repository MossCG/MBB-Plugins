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
- 提供 `MBB-Sticker` 公共服务供 Roleplay 调用

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

## 标签规则

默认使用小写英文 snake_case：

```text
^[a-z][a-z0-9_]{1,31}$
```

AI 只把情绪、态度和聊天用途放进 `tags`，画面、服装、发色等视觉信息放进 `description`。可用标签集由所有启用表情包的 tags 自动去重生成。
