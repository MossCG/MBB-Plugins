# MBB-ComfyUI

MoBoxBot 的 ComfyUI 生图插件，通过插件服务向 `MBB-Roleplay` 提供 `draw` 技能。

## 功能

- 连接局域网 ComfyUI，默认 `http://192.168.10.10:8188`
- 每个群独立 600 秒生图冷却
- 角色只需要提供 `prompt` 和尺寸
- 默认使用 `NoobAI-XL-v1.1.safetensors`
- 默认直接生成目标尺寸，不做放大和缩放
- 生成完成后自动发回群，并回调 `MBB-Roleplay` 让角色提醒发起者
- 生成图片统一备份到插件目录 `images/`，同时写入同名 JSON 元数据
- 支持尺寸上限、队列和超时控制

## 默认模型

```text
NoobAI-XL-v1.1.safetensors
```

该模型适合动漫、原创角色和群友生图。

## 服务接口

服务名：

```text
MBB-ComfyUI
```

`status`：

```json
{
  "groupID": 123456
}
```

返回：

```json
{
  "status": true,
  "ready": true,
  "cooldownRemaining": 0,
  "busy": false,
  "maxWidth": 1536,
  "maxHeight": 1536
}
```

`generate`：

```json
{
  "groupID": 123456,
  "userID": 789012,
  "messageID": 1234,
  "prompt": "一个金发双马尾少女在游戏机前笑",
  "size": "square"
}
```

尺寸也可以直接传：

```json
{
  "prompt": "雨夜，绿发少女在画板前，动漫风格",
  "width": 1024,
  "height": 1536
}
```

## 尺寸预设

```text
square     1280x1280
landscape  1536x1024
portrait   1024x1536
avatar     1024x1024
```

硬上限默认：

```text
1536x1536
最大像素 2360000
```

## 配置

配置在：

```text
./MoBoxBot/plugins/MBB-ComfyUI/config.yml
```

完整默认配置见插件资源文件。

## 图片备份

生成图片保存到：

```text
./MoBoxBot/plugins/MBB-ComfyUI/images/
```

每个图片旁边会生成同名 JSON，例如：

```text
comfyui-20261006-191000-xxxxxxxx.png
comfyui-20261006-191000-xxxxxxxx.json
```

JSON 里记录：

```text
prompt
negativePrompt
群号
用户号
消息 ID
模型
尺寸
steps
cfg
sampler
scheduler
promptId
```

这样可以直接人工检查是否有违规生成内容。

## 指令

| 指令 | 权限 | 说明 |
|---|---|---|
| `/comfyui status [群号]` | `BOT_ADMIN` | 查看服务状态和当前群冷却 |
| `/comfyui reload` | `BOT_ADMIN` | 重载配置 |
| `/comfyui test <prompt> [尺寸]` | `BOT_ADMIN` | 调试生图 |

## 角色接入

`MBB-Roleplay` 检测到 `MBB-ComfyUI` 服务后会开放 `draw` 技能。

角色收到生图需求时会先确认主体、风格、用途和尺寸。需求明确后才调用：

```json
{
  "type": "draw",
  "args": {
    "prompt": "一个金发双马尾少女在游戏机前笑",
    "size": "square"
  }
}
```

生图完成后由 `MBB-ComfyUI` 回调 `MBB-Roleplay`，角色会在群里提醒生图发起者。

## 构建

```powershell
.\build.ps1
```

产物：

```text
out\MBB-ComfyUI.jar
```
