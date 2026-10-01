# 参与 MBB-Plugins 开发

## 前置

- Java 8
- PowerShell 5.1 或 PowerShell 7
- 已构建 MoBoxBot 主程序 `out\MoBoxBot.jar`

## 插件结构

每个插件独立目录：

```text
MBB-Xxx/
├─ build.ps1
├─ build.bat
├─ README.md
├─ src/main/java/org/moboxlab/mbb/xxx/
└─ src/main/resources/
   ├─ plugin.json
   └─ config.yml
```

## 构建

```powershell
.\build-all.ps1 -Bot "D:\CodeX\Projects\MoBoxBot\out\MoBoxBot.jar"
```

## 提交规则

插件仓库和主程序仓库独立提交。

1. 只改插件时，不修改主程序版本号。
2. 同时改主程序 API 时，主程序和插件仓库分别提交。
3. 正式插件版本遵循 `V大版本.小版本.小更新.小修正.四位时间戳`。
4. 纯文档调整使用 `docs: 中文摘要`。
5. 每轮功能更新维护 `update.md`。
6. 插件仓库发布版本写在根目录 `version.txt`。
7. 推送 `master` 后 GitHub Actions 自动构建并发布 Release。

## 代码规范

- Java 8
- 中文注释、中文日志
- 插件只依赖 `org.moboxlab.moboxbot.API`
- 不打包 MoBoxLib、fastjson、sqlite-jdbc、Java-WebSocket
- 权限明确，默认从 `BOT_ADMIN` 起步
- 外部 API Key 放插件 `config.yml`
- 插件资源读取用 `readResource` / `readResourceText`

## 提交前检查

1. `build-all.ps1` 编译通过。
2. `plugin.json` 有 `name`、`main`、`apiVersion`。
3. 不在仓库里提交 `out/`、日志、数据库、Token 或个人信息。
4. README 与 `config.yml` 同步。
