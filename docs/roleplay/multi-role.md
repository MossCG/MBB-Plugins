# MBB-Roleplay 多角色部署

## 多角色部署

同一群部署多个角色机器人时，必须通过 `otherRoleBotQQs` 明确填写其他角色机器人的 QQ。只有 QQ 命中该列表时，插件才会把对方当作角色本人；昵称或群名片改成某个角色名不会自动获得角色身份。`otherRoleBotNames` 只用于补充识别文本里提到了哪些机器人名称，默认留空，不会内置任何具体作品的角色名。

`repeatSuppressEnable` 默认 `false`，代码层重复回复兜底检测暂时关闭；重复问题先由通用提示词里的“同一件事只回应一次、不要复述最近说过的话”等规则处理。需要重新启用代码兜底时把它改为 `true`。

默认接话概率为 `0.50`，在没有真人插话时最多连续处理 2 条对方消息。

常用管理命令：

```text
/role bot status
/role bot chance 0.5
/role bot max 2
/role bot qq add 1004331369,123456789
/role bot qq list
/role bot name reset
```

名称识别支持 `list`、`add`、`remove`、`set`、`clear` 和 `reset`。QQ 识别支持 `list`、`add`、`remove`、`set` 和 `clear`。

