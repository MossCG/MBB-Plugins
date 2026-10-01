# AGENTS.md

## 前置约束

- 插件只依赖 `org.moboxlab.moboxbot.API`。
- 插件不要直接访问主程序内部类、`BasicInfo`、数据库或 OneBot 实现。
- 技术栈：Java 8 + fastjson + MoBoxBot API。
- 中文注释、中文日志，不用 emoji。

## 仓库边界

本仓库是独立插件仓库，路径：

```text
D:\CodeX\Projects\MBB-Plugins
```

主程序仓库：

```text
D:\CodeX\Projects\MoBoxBot
```

插件仓库改动不影响主程序时，不更新主程序版本号。

## 版本与提交

- 插件版本格式：`V大版本.小版本.小更新.小修正.四位时间戳`。
- 纯文档调整：`docs: 中文摘要`。
- 插件功能更新维护 `update.md`。
- 不提交 `out/`、日志、数据库、真实 Token 和个人信息。

## 构建

```powershell
.\build-all.ps1 -Bot "D:\CodeX\Projects\MoBoxBot\out\MoBoxBot.jar"
```

## 自检

1. 插件 JAR 根目录有 `plugin.json`。
2. 主类继承 `API.Plugin`。
3. 插件 JAR 不打包公共依赖。
4. 权限、冷却、说明完整。
5. 插件配置和 README 同步。
