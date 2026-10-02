# MBB-AIGuard

MoBoxBot AI 群聊内容风险审查插件。

插件通过规则预筛选和 `MBB-AI` 二次判定识别卖惨、卖穷、借钱、诈骗、广告、色情、隐私索取、非法交易和自残危机等内容。

## 设计原则

- 默认只记录和告警，不自动禁言或踢人。
- 普通消息不调用 AI，只有命中规则才进入审查。
- AI 会结合即时上下文和长期行为画像。
- 支持全局白名单和群级白名单。
- 自残危机内容单独标记，不按诈骗处理。

## 指令

| 指令 | 权限 | 说明 |
|---|---|---|
| `/guard status` | `BOT_ADMIN` | 查看当前群审查配置 |
| `/guard enable` / `/guard disable` | `BOT_ADMIN` | 切换当前群审查 |
| `/guard threshold <分数>` | `BOT_ADMIN` | 设置当前群告警阈值 |
| `/guard test <文本>` | `BOT_ADMIN` | 测试文本风险判定 |
| `/guard log [页码]` | `BOT_ADMIN` | 查看最近审查事件 |
| `/guard whitelist list [群号]` | `BOT_ADMIN` | 查看白名单 |
| `/guard whitelist add <QQ>` | `OWNER` | 添加全局白名单 |
| `/guard whitelist add <QQ> <群号>` | `BOT_ADMIN` | 添加群级白名单 |
| `/guard whitelist remove <QQ> [群号]` | `BOT_ADMIN` | 移除白名单 |

## 规则扩展

首次运行会释放：

```text
./MoBoxBot/plugins/MBB-AIGuard/rules.json
```

可以继续追加规则：

```json
{
  "category": "分类名",
  "risk": 60,
  "keywords": ["关键词"],
  "patterns": ["正则表达式"],
  "safety": false
}
```

修改后执行：

```text
plugin reload MBB-AIGuard
```

## 依赖

`MBB-AI` 是软依赖。没有启用时，插件只使用规则库进行基础判定。
