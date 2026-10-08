# MBB-Leave

MoBoxBot 退群命令插件。

owner 在群里执行 `/leave` 后，插件会先发送一条退群消息，再调用 OneBot `set_group_leave` 退出当前群。

## 指令

| 指令 | 权限 | 说明 |
|---|---|---|
| `/leave` | `OWNER` | 发送默认退群消息后退出当前群 |
| `/leave <消息>` | `OWNER` | 发送指定消息后退出当前群 |

## 使用方法

```text
/leave
/leave 大家再见，有缘再会。
```

发送消息失败时会取消退群；退群请求失败时会在群内提示检查控制台日志。

## 配置

`config.yml`：

```yaml
leaveText: "再见啦，有缘再见。"
```

## 构建

```powershell
.\build.ps1
```

产物：

```text
out\MBB-Leave.jar
```
