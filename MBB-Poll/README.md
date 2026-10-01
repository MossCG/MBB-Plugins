# MBB-Poll

MoBoxBot 群投票插件。

| 指令 | 权限 | 说明 |
|---|---|---|
| `/poll <时长> <问题> [选项...]` | `BOT_ADMIN` | 发起限时投票 |
| `/vote <序号>` | `EVERYONE` | 参与当前投票 |

示例：

```text
/poll 5m 中午吃什么 火锅 烧烤 面
/vote 2
```

投票结束后会 @ 发起者并显示结果。
