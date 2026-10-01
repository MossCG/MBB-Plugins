# MBB-Plugins

MoBoxBot 独立插件仓库。

插件统一命名 `MBB-xxx`，每个插件独立构建、独立 `plugin.json`、独立版本号。

## 插件列表

| 插件 | 指令/触发 | 权限 | 说明 |
|---|---|---|---|
| `MBB-Admin` | `/admin` | `OWNER` | 增删查管理员 |
| `MBB-Help` | `/help` | `EVERYONE` | 按权限分区的命令帮助图片 |
| `MBB-PigHub` | `来只猪猪` | `EVERYONE` | 随机 PigHub 猪猪图片 |
| `MBB-Ping` | `/ping` | `BOT_ADMIN` | 测试机器人是否运行中 |
| `MBB-Plugins` | `/plugins` | `OWNER` | 插件列表与管理 |
| `MBB-Poke` | 戳一戳 | 无 | 多行回复序列、回戳、图片 |
| `MBB-Poll` | `/poll`、`/vote` | `BOT_ADMIN` / `EVERYONE` | 群投票 |
| `MBB-Random` | `/random` | `EVERYONE` | 指定范围随机数 |
| `MBB-Reload` | `/reload` | `OWNER` | 重载主程序配置 |
| `MBB-Remind` | `/remind` | `BOT_ADMIN` | 定时提醒 |
| `MBB-Status` | `/status` | `BOT_ADMIN` | 系统运行状态 |
| `MBB-Version` | `/version` | `BOT_ADMIN` | 版本信息图片 |
| `MBB-Welcome` | `/welcome` | `BOT_ADMIN` | 群进群退群消息 |

## 依赖

插件编译只依赖主程序 JAR：

```text
D:\CodeX\Projects\MoBoxBot\out\MoBoxBot.jar
```

插件运行时不要打包 MoBoxLib、fastjson、sqlite-jdbc、Java-WebSocket。

## 构建单个插件

```powershell
cd MBB-Ping
.\build.ps1
```

可用 `-Bot` 指定主程序 JAR：

```powershell
.\build.ps1 -Bot "D:\CodeX\Projects\MoBoxBot\out\MoBoxBot.jar"
```

产物：

```text
MBB-Ping\out\MBB-Ping.jar
```

## 构建全部插件

```powershell
.\build-all.ps1 -Bot "D:\CodeX\Projects\MoBoxBot\out\MoBoxBot.jar"
```

## 安装插件

复制到主程序运行目录：

```text
D:\CodeX\Projects\MoBoxBot\out\MoBoxBot\plugins\
```

新插件首次加载需要重启 MoBoxBot。已加载插件可以在控制台执行：

```text
plugin reload MBB-Ping
```

## 文档

- [CONTRIBUTING.md](CONTRIBUTING.md)：开发与提交规范
- [AGENTS.md](AGENTS.md)：协作约束
- [update.md](update.md)：插件仓库更新日志
- MoBoxBot 主程序文档：`D:\CodeX\Projects\MoBoxBot`

## 许可证

Apache License 2.0，见 [LICENSE](LICENSE)。

第三方声明见 [NOTICE](NOTICE)。
