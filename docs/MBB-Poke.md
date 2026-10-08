# MBB-Poke

MoBoxBot 戳一戳插件。

被戳时从 `replies.yml` 随机选择一组回复序列，按顺序执行。

## 回复配置

`replies.yml`：

```yaml
replys:
  reply1:
    - message: "喵？"
  reply2:
    - message: "哈！"
  reply3:
    - message: "哈你喵！"
  reply4:
    - message: "不许戳喵！"
  reply5:
    - message: "杂鱼喵！"
  reply6:
    - message: "呜喵！"
  reply7:
    - message: "哼！"
  reply8:
    - message: "哈你喵！"
    - sleep: 0.5
    - message: "戳回去！"
    - poke_back: true
  reply9:
    - message: "杂鱼喵！"
    - sleep: 0.5
    - message: "哼！"
```

动作说明：

| 动作 | 说明 |
|---|---|
| `message` | 发送文本 |
| `image` | 发送图片；文件名从插件数据目录读取，例如 `haqi.jpg`；也支持 `base64://`、`http://`、`https://`、`file://` |
| `sleep` | 等待指定秒数 |
| `poke_back` | 戳回去，`true` 或留空即可；也可以直接写文本表示戳回去并发送 |
| `poke_back_message` | 戳回去并发送指定文本 |

插件配置 `config.yml`：

```yaml
maxSleepSecond: 5   #单个 sleep 动作最大等待秒数
```

## 构建

```powershell
.\build.ps1
```

产物：

```text
out\MBB-Poke.jar
```
