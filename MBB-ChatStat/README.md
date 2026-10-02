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

## 配置

```yaml
enable: true
maxContentLength: 500
recentMessageCount: 10
topCount: 10
defaultDays: 1
aiProfile: "default"
aiSummaryMessageCount: 50
aiSummaryMaxTokens: 1200
```

数据保存在主程序 SQLite 的插件专属表中。插件只统计机器人实际收到的群消息。

`MBB-AI` 是软依赖：没有启用时，普通统计图片仍然可用，只有 AI 总结会提示服务不可用。

## 构建

```powershell
.\build.ps1
```

产物：

```text
out\MBB-ChatStat.jar
```
