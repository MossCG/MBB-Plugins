# MBB-Plugins

MoBoxBot 插件列表命令插件。

## 指令

| 指令 | 权限 | 说明 |
|---|---|---|
| `/plugins` | `OWNER` | 获取当前插件列表，以图片显示 |
| `/plugins check` | `OWNER` | 读取 `versions.json`，检查可更新、未安装、本地较新和清单缺失 |

## 版本清单

运行 `build-all.ps1` 时会在仓库根目录生成：

```text
out/versions.json
```

清单包含生成时间、插件最新版本、JAR 文件名和 SHA-256。部署时把该文件放到：

```text
./MoBoxBot/plugins/MBB-Plugins/versions.json
```

也可以直接放在 MBB-Plugins 插件数据目录下，`/plugins check` 会优先读取数据目录中的文件。
版本比较按 `V0.1.2.3.2359` 各数字段进行，不按字符串直接比较。

## 构建

```powershell
.\build.ps1
```

产物：

```text
out\MBB-Plugins.jar
```

批量构建还会额外生成 `out\versions.json`。
