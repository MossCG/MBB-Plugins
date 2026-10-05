# MBB-Status

MoBoxBot 运行状态插件。

## 指令

| 指令 | 权限 | 说明 |
|---|---|---|
| `/status` | `BOT_ADMIN` | 图片显示服务器配置、CPU、内存、磁盘、JVM、显卡、显存、网络和运行时长 |

图片中的进度条显示 CPU、物理内存、磁盘、JVM 堆内存、显卡负载和显存占用比例。

服务器信息包括：

- 主机名
- 操作系统与版本
- 系统架构
- 处理器型号
- CPU 核心数
- Java 版本
- 显卡型号与温度
- 物理内存与磁盘容量
- 运行时长与采样间隔

## 显卡信息

显卡数据通过 `nvidia-smi` 采集，命令为：

```text
nvidia-smi --query-gpu=name,utilization.gpu,memory.used,memory.total,temperature.gpu --format=csv,noheader,nounits
```

多张显卡时，显卡负载取平均值，显存占用取总和，型号显示第一张并标注总数。

找不到 `nvidia-smi` 时会退回到系统显卡型号查询（Windows 使用 `Win32_VideoController`，Linux 使用 `lspci`），此时只显示型号，负载、显存和温度标记为不可用。

结果会缓存 5 秒，避免短时间内重复生成状态图时反复启动外部进程。

## 配置

`config.yml`：

```yaml
refreshSecond: 10   #网络采样间隔秒数，建议 5 到 30
nvidiaSmiPath: "nvidia-smi"   #显卡信息采集命令，找不到时可填写绝对路径
```

`nvidiaSmiPath` 在 Windows 上通常是 `C:\Windows\System32\nvidia-smi.exe`，Linux 上通常是 `/usr/bin/nvidia-smi`。留空则使用 `nvidia-smi`。

## 构建

```powershell
.\build.ps1
```

产物：

```text
out\MBB-Status.jar
```
