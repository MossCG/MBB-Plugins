# MBB-ChatStat

MoBoxBot 群聊内容统计插件。

## 指令

| 指令 | 权限 | 说明 |
|---|---|---|
| `/chatstat group [群号] [天数] [ai]` | `BOT_ADMIN` | 统计当前群或指定群的聊天内容，末尾加 `ai` 生成 AI 总结 |
| `/chatstat user <QQ> [天数] [ai]` | `BOT_ADMIN` | 统计某人在所有可见群中的聊天内容，末尾加 `ai` 生成 AI 总结 |
| `/cstat ...` | `BOT_ADMIN` | `/chatstat` 别名 |

默认统计今天，天数范围为 1 到 30。

图片内容包括：

- 消息总数、活跃人数、图片数、@ 数
- 群统计：发言人排行、最近消息
- 用户统计：群排行、最近消息

## AI 总结

命令末尾加 `ai`：

```text
/chatstat group 7 ai
/chatstat user 123456 3 ai
```

统计图片会正常发送，随后异步调用 `MBB-AI`，再发送一张包含统计指标和总结正文的 AI 总结图片。AI 总结会读取最近消息，条数由 `aiSummaryMessageCount` 控制。

AI 总结要求纯文本输出，`MBB-AI` 也会在底层清洗 Markdown 语法。

总结 prompt 会优先提炼主要内容、核心话题、重要结论和主要参与者，忽略寒暄、重复灌水以及体量较小的零星讨论。

## 配置

```yaml
enable: true
maxContentLength: 500
recentMessageCount: 10
topCount: 10
defaultDays: 1
aiProfile: "default"
aiSummaryMessageCount: 0
aiSummaryMaxTokens: 8000
```

数据保存在主程序 SQLite 的插件专属表中。插件只统计机器人实际收到的群消息。

`aiSummaryMessageCount: 0` 表示全量读取统计时间范围内的文本消息。图片、表情、语音、视频、文件、卡片等非文本消息会在送入 AI 前剔除；如果文本和图片混在同一条消息里，只保留文本部分。

群聊一天有上千条消息时，默认会全部读取文本内容。需要控制上下文长度时，可以把 `aiSummaryMessageCount` 设置为正数，系统会在全时间段内均匀采样。总结失败重试会提高到最高 12000 Token。

`MBB-AI` 是软依赖：没有启用时，普通统计图片仍然可用，只有 AI 总结会提示服务不可用。

## 构建

```powershell
.\build.ps1
```

产物：

```text
out\MBB-ChatStat.jar
```
