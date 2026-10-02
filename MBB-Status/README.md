# MBB-Status

MoBoxBot 运行状态插件。

## 指令

| 指令 | 权限 | 说明 |
|---|---|---|
| `/status` | `BOT_ADMIN` | 图片显示服务器配置、CPU、内存、磁盘、JVM、网络和运行时长 |

图片中的进度条显示 CPU、物理内存、磁盘和 JVM 堆内存占用比例。

服务器信息包括：

- 主机名
- 操作系统与版本
- 系统架构
- 处理器型号
- CPU 核心数
- Java 版本
- 物理内存与磁盘容量
- 运行时长与采样间隔

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
