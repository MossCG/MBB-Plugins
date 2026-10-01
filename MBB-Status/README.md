# MBB-Status

MoBoxBot 运行状态插件。

## 指令

| 指令 | 权限 | 说明 |
|---|---|---|
| `/status` | `BOT_ADMIN` | CPU、内存、硬盘、网络、JVM、运行时长 |

## 配置

`config.yml`：

```yaml
refreshSecond: 10   #网络采样间隔秒数，建议 5 到 30
```

## 构建

```powershell
.\build.ps1
```

产物：

```text
out\MBB-Status.jar
```
