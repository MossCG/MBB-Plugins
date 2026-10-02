# MBB-ChatStat

MoBoxBot 群聊内容统计插件。

## 指令

| 指令 | 权限 | 说明 |
|---|---|---|
| `/chatstat group [群号] [天数]` | `BOT_ADMIN` | 统计当前群或指定群今天/最近几天的聊天内容 |
| `/chatstat user <QQ> [天数]` | `BOT_ADMIN` | 统计某人在所有可见群中的聊天内容 |
| `/cstat ...` | `BOT_ADMIN` | `/chatstat` 别名 |

默认统计今天，天数范围为 1 到 30。

图片内容包括：

- 消息总数、活跃人数、图片数、@ 数
- 群统计：发言人排行、最近消息
- 用户统计：群排行、最近消息

## 配置

```yaml
enable: true
maxContentLength: 500
recentMessageCount: 10
topCount: 10
defaultDays: 1
```

数据保存在主程序 SQLite 的插件专属表中。插件只统计机器人实际收到的群消息。

## 构建

```powershell
.\build.ps1
```

产物：

```text
out\MBB-ChatStat.jar
```
