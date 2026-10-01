# MBB-Plugins

MoBoxBot 插件开发目录。

## 插件列表

| 插件 | 指令 | 权限 | 说明 |
|---|---|---|---|
| `MBB-Ping` | `/ping` | `BOT_ADMIN` | 测试机器人是否运行中 |
| `MBB-Plugins` | `/plugins` | `OWNER` | 获取当前插件列表 |
| `MBB-Status` | `/status` | `BOT_ADMIN` | 获取 CPU、内存、硬盘、网络等运行状态 |
| `MBB-Help` | `/help` | `EVERYONE` | 命令帮助图片 |
| `MBB-Version` | `/version` | `BOT_ADMIN` | 版本信息图片 |
| `MBB-Reload` | `/reload` | `OWNER` | 重载主程序配置 |
| `MBB-Remind` | `/remind` | `BOT_ADMIN` | 定时提醒 |
| `MBB-Random` | `/random` | `EVERYONE` | 指定范围随机数 |
| `MBB-Poke` | 无指令 | 无 | 被戳时回复短句，可戳回去 |
| `MBB-Admin` | `/admin` | `OWNER` | 增删查管理员 |
| `MBB-Welcome` | `/welcome` | `BOT_ADMIN` | 群进群退群消息开关 |
| `MBB-Poll` | `/poll`、`/vote` | `BOT_ADMIN` / `EVERYONE` | 群投票 |
| `MBB-PigHub` | 关键词 `来只猪猪` | `EVERYONE` | 随机猪猪图片 |

## 构建单个插件

```powershell
cd MBB-Ping
.\build.ps1
```

`build.ps1` 默认依赖：

```text
D:\CodeX\Projects\MoBoxBot\out\MoBoxBot.jar
```

也可以用 `-Bot` 指定主程序 JAR：

```powershell
.\build.ps1 -Bot "D:\CodeX\Projects\MoBoxBot\out\MoBoxBot.jar"
```

## 构建全部插件

```powershell
.\build-all.ps1
```

## 安装插件

把各插件 `out\MBB-xxx.jar` 复制到：

```text
D:\CodeX\Projects\MoBoxBot\out\MoBoxBot\plugins\
```

然后重启 MoBoxBot，或开发模式执行：

```text
plugin reload MBB-Ping
plugin reload MBB-Plugins
plugin reload MBB-Status
```
