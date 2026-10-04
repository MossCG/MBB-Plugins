# MBB-Vision

MoBoxBot 公用识图与本地缓存插件。

## 特性

- 对图片和表情包统一调用视觉模型
- 优先按 OneBot `file_unique` 查询缓存
- 没有平台唯一标识时按图片 SHA-256 查询缓存
- 识别结果写入 SQLite，后续同一图片不重复调用 AI
- 普通图片生成较完整的 2 到 4 句中文描述，表情包只生成情绪和聊天用途摘要
- 普通图片识图 Token 下限为 12000，模型返回 reasoning 或非 JSON 时会二次整理，不会把思考过程写入角色上下文
- 同一个图片哈希的并发请求会合并，第二个请求等待首个识别完成后直接读缓存
- 输出识别结果日志，便于调试
- 提供 `MBB-Vision` 公共服务

## 服务

```text
action: describe
params:
  fileUnique: 平台文件唯一标识，可选
  url: 图片 URL，可选
  file: OneBot 文件标识或本地路径，可选
  fileUri: file:// 本地路径，可选
  kind: sticker 或 image
  context: 聊天上下文，可选
  reference: 学生外貌参考文本，可选；会参与缓存键计算
  profile: 覆盖默认 AI 配置，可选
  force: true 时强制重新识别，可选
```

```text
action: dataUri
params:
  fileUnique / url / file / fileUri
```

`dataUri` 不调用 AI，只读取图片并返回 `data:image/...;base64,...`，供角色追问时把原图传给模型。

返回：

```json
{
  "status": true,
  "cached": false,
  "sha256": "...",
  "summary": "图片内容摘要",
  "ocr": "识别到的文字",
  "emotionTags": ["happy", "shy"],
  "visualTags": ["cat"],
  "description": "简短描述",
  "scene": "group_chat"
}
```

## 指令

| 指令 | 权限 | 说明 |
|---|---|---|
| `/vision stats` | `BOT_ADMIN` | 查看缓存数量 |
| `/vision clear` | `BOT_ADMIN` | 清空识图缓存 |
| `/vision reload` | `BOT_ADMIN` | 重载配置 |
