# MBB-AI

MoBoxBot 公用 AI 服务插件，通过插件服务注册表向其他插件提供统一 AI 能力。

当前只实现 OpenAI 兼容协议，可接入 OpenAI、DeepSeek、通义兼容模式、Ollama `/v1` 等兼容接口。

默认模型配置为 `deepseek-v4.1-flash`，默认从环境变量 `OPENCODEGO_API_KEY` 读取密钥，默认地址为 opencodego 的 OpenAI 兼容端点。

opencodego 要求每个对话请求携带稳定的 `x-opencode-session` 请求头。`MBB-Chat` 会按用户 QQ 自动传入；其他插件调用 `chat` 时也应传入 `sessionId`。

## 服务

插件名：

```text
MBB-AI
```

服务接口：

```java
PluginService service = getServer().getPluginManager().getService("MBB-AI");
JSONObject result = service.call("chat",params);
```

支持的动作：

| action | 说明 |
|---|---|
| `chat` | 对话，传入 `messages` |
| `complete` | 单轮补全，传入 `prompt` |
| `embed` | 生成文本向量，传入 `input`，调用 `/embeddings` |
| `status` | 查看配置和服务状态 |
| `usage` | 查看请求、Token、耗时和趋势统计 |
| `reload` | 重载 AI 配置 |

### 原生工具调用

`chat` 参数可以透传 OpenAI 兼容工具协议：

```json
{
  "tools": [{"type":"function","function":{"name":"example","description":"...","parameters":{}}}],
  "tool_choice": "auto",
  "parallel_tool_calls": false
}
```

模型返回 `tool_calls` 时，结果会带上原始 `toolCalls` 数组。调用方需要把 assistant 的
`tool_calls` 和后续 `role=tool`、`tool_call_id` 消息原样追加到下一轮 `messages`。

部分兼容网关会把工具调用错误地写成正文里的 DSML `invoke/parameter` 标签。`MBB-AI` 会把这类
明确协议标签转换为标准 `toolCalls`，并清空正文；无法完整解析时会返回 `toolProtocolMalformed=true`，
调用方不得把原始标签作为聊天文本发送。

带 `tools` 的请求默认绕过 MBB-AI 本地缓存；普通文本请求仍按原配置缓存。

## 配置

`config.yml`：

```yaml
enable: true
defaultProfile: "default"
maxConcurrent: 2
cacheSecond: 300
retryCount: 1
logRequestContent: false
```

`profiles.json`：

```json
{
  "profiles": {
    "default": {
      "provider": "openai",
      "baseUrl": "https://opencode.ai/zen/go/v1",
      "apiKey": "env:OPENCODEGO_API_KEY",
      "model": "deepseek-v4.1-flash",
      "temperature": 0.7,
      "maxTokens": 1024,
      "timeoutSeconds": 60,
      "reasoningEffort": "",
      "insecureTls": false,
      "headers": {
        "User-Agent": "MoBoxBot/0.1 (+https://github.com/MossCG/MoBoxBot)",
        "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8"
      }
    }
  }
}
```

`apiKey` 可以直接填写，也可以使用环境变量：

```json
"apiKey": "env:OPENCODEGO_API_KEY"
```

`headers` 可以覆盖或追加请求头，适合不同服务端网关、代理或反向代理环境。`x-opencode-session` 由调用方传入 `sessionId` 后动态设置，优先级高于静态请求头。

`reasoningEffort` 控制思考强度，可选 `low`、`medium`、`high`，留空表示不向接口发送该字段。调用方也可以在 `chat` 参数里传 `reasoningEffort` 覆盖配置。如果模型或网关不认识这个字段并返回 HTTP 400 / 422，插件会自动去掉它重试一次，不会让整条回复失败。

`timeoutSeconds` 是 profile 的默认请求超时时间。调用方也可以在 `chat` 参数里单独传 `timeoutSeconds` 覆盖，适合记忆整理、长上下文总结这类耗时明显更长的请求；单次覆盖范围是 1 到 600 秒。调用方还可以传 `retryCount` 覆盖 `config.yml` 的默认重试次数，范围是 0 到 3；长耗时记忆整理建议传 `0`，避免超时后再等一整轮重试。

`insecureTls` 默认 `false`。置为 `true` 后跳过 HTTPS 证书链和主机名校验，只用于自建网关、TLS 拦截代理或证书与域名不匹配的服务器环境。遇到 `No name matching <域名> found` 这类报错时，优先改用证书实际覆盖的域名；确实没有别的办法再打开它，因为它会让中间人攻击无法被发现。

不要把真实密钥提交到仓库。

## 本地小模型 profile（实验）

配合 `MBB-Model`（本地 Qwen3-1.7B 推理与训练服务）使用时，加一个 `local` profile 指向它：

```json
"local": {
  "provider": "openai",
  "baseUrl": "http://127.0.0.1:8770/v1",
  "apiKey": "local",
  "model": "Qwen3-1.7B",
  "temperature": 0.7,
  "maxTokens": 256,
  "timeoutSeconds": 180,
  "reasoningEffort": "",
  "insecureTls": false,
  "headers": {
    "User-Agent": "MoBoxBot/0.1 (+https://github.com/MossCG/MoBoxBot)",
    "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8"
  }
}
```

几个字段的取值理由：

| 字段 | 取值 | 说明 |
|---|---|---|
| `baseUrl` | `http://127.0.0.1:8770/v1` | 插件会自动补 `/chat/completions`，正好对上 MBB-Model 的路由 |
| `apiKey` | `local` | **插件要求 apiKey 非空**，本地服务默认不校验，填个占位值即可。如果 MBB-Model 的 `server.token` 配了值，这里改成 `env:MBB_MODEL_TOKEN` |
| `model` | `Qwen3-1.7B` | 和 MBB-Model 实际加载的目录名一致，方便日志辨认 |
| `temperature` | `0.7` | 风格改写要稳定，比默认的 0.9 低 |
| `maxTokens` | `256` | 只改写一两句话，1024 纯属浪费 |
| `timeoutSeconds` | `180` | 首次调用要加载模型（约 23 秒），训练期间推理也会变慢 |
| `reasoningEffort` | `""` | Qwen3 的思考模式在服务端已经关掉，这里不要再发这个字段 |

**不要把云端密钥填进这个 profile**。本地服务不需要真密钥，把 opencode 之类的 key 放进来
既没用又容易跟着配置文件一起泄露。

本地 profile 仍可用于 MBB-Model 的推理和训练接口。`MBB-Roleplay` 的独立风格层已移除，
风格、链接和长句处理均合并到 `replyReviewEnable` 复核层；旧 `styleProfile`、
`styleMaxTokens` 和 `styleReasoningEffort` 配置不再参与回复处理。

其他层（路由、生成、复核、记忆、识图、提醒）继续使用各自配置的 profile。
本地服务是否启动不影响角色主回复链路。

## 调用约定

其他插件声明依赖：

```json
"depend": ["MBB-AI"]
```

`chat` 请求：

```json
{
  "profile": "default",
  "sessionId": "moboxbot-user-123456",
  "taskName": "roleplay.reply",
  "promptVersion": "a1b2c3d4e5f60708",
  "reasoningEffort": "low",
  "timeoutSeconds": 180,
  "retryCount": 0,
  "messages": [
    {"role": "system", "content": "你是一个 QQ 聊天助手。"},
    {"role": "user", "content": "你好"}
  ]
}
```

`taskName` 是调用点标签，未填写时回退为 `action`；`promptVersion` 是提示词模板指纹，
调用方可以把稳定模板内容、配置指纹或构建类指纹写进来。两者都会进入调用点统计表和本地缓存键；
同名调用点修改提示词后会自然分成两行，便于比较缓存率与耗时。

`embed` 请求：

```json
{
  "profile": "default",
  "taskName": "roleplay.expression.embed",
  "promptVersion": "expression-vector-v1",
  "input": ["箱子风道", "今晚吃咖喱"],
  "dimensions": 1024,
  "encoding_format": "float",
  "timeoutSeconds": 60,
  "retryCount": 0
}
```

成功返回会保留每个输入的顺序索引：

```json
{
  "status": true,
  "action": "embed",
  "profile": "default",
  "model": "text-embedding-3-small",
  "embeddings": [
    {"index": 0, "embedding": [0.1, -0.2, 0.3]},
    {"index": 1, "embedding": [0.4, 0.5, -0.6]}
  ],
  "usage": {"promptTokens": 8, "completionTokens": 0, "totalTokens": 8}
}
```

`input` 支持单个字符串或字符串数组。`dimensions` 和 `encoding_format` 只在调用方需要时透传。
`embed` 不使用 MBB-AI 的本地对话缓存；调用方可自行按查询文本缓存向量。

成功返回：

```json
{
  "status": true,
  "action": "chat",
  "profile": "default",
  "model": "gpt-4o-mini",
  "content": "你好，很高兴见到你！",
  "reasoningContent": "",
  "finishReason": "stop",
  "cached": false,
  "usage": {
    "promptTokens": 10,
    "completionTokens": 12,
    "totalTokens": 22
  }
}
```

`content` 返回前会统一转换为纯文本，自动移除 Markdown 代码块、粗体、斜体、标题、引用和链接语法，避免 QQ 中出现大量星号。

`reasoningContent` 保留模型返回的思考内容字段，`finishReason` 用于判断是否因为 `length` 等原因提前结束。普通聊天仍只使用 `content`。

失败返回：

```json
{
  "status": false,
  "message": "AI 服务请求超时！",
  "errorType": "timeout",
  "retryable": true
}
```

AI 调用是同步网络请求，调用方必须放入插件任务中执行，不要阻塞事件监听线程。

## 统计

`MBB-AI` 会把统计写入 SQLite：

```text
plugin_mbb_ai_usage
plugin_mbb_ai_usage_task
```

统计维度：

- 日期、模型配置、模型名、动作：汇总到 `plugin_mbb_ai_usage`
- 日期、模型配置、模型名、调用点、Prompt 版本：汇总到 `plugin_mbb_ai_usage_task`
- 请求数、成功数、失败数
- 输入、输出、总 Token
- 总耗时与平均耗时
- 服务端提示词缓存命中的输入 Token

`/ai usage [天数]` 会输出图片，默认展示最近 7 天，最长 30 天。图片除了原有的
Token 趋势和模型配置统计，还会展示“调用点统计”，方便直接比较路由、回复、复核、
记忆整理等任务各自的 Token、缓存率和成功率。目前不保存完整请求快照，也不提供重放；
这部分留给后续 UI。

**两种缓存不是一回事**，统计图里的缓存率指的是后者：

1. **本地缓存**：`MBB-AI` 自己按「profile + sessionId + 参数 + 完整 messages」做精确去重，命中就直接返回上一次的结果，`cacheSecond` 默认 300 秒。
   它的 key 包含完整 messages，所以角色扮演这种每轮提示词都不同的场景几乎不可能命中，`cachedHits` 长期为 0 是正常的。
2. **服务端提示词缓存**：由模型服务商提供的上下文缓存，命中时只按缓存价计费。统计读取 `usage.prompt_cache_hit_tokens`（DeepSeek）、
   `usage.prompt_tokens_details.cached_tokens`（OpenAI）或 `usage.cached_tokens`（部分网关），
   命中率按「命中的输入 Token ÷ 全部输入 Token」计算。这个数才是省钱的那一个。

统计图里的「累计缓存率」统计全部历史数据，「今日缓存命中率」只看今天，趋势图旁的「近 N 天缓存率」只看当前选择的天数。本地缓存仍用于请求去重，但不再展示命中次数。

## 管理命令

| 指令 | 权限 | 说明 |
|---|---|---|
| `/ai status` | `OWNER` | 图片查看服务状态和模型配置 |
| `/ai usage [天数]` | `OWNER` | 图片查看请求、Token、耗时和趋势统计 |
| `/ai reload` | `OWNER` | 重载 AI 配置并输出状态图片 |

## 构建

```powershell
.\build.ps1
```

产物：

```text
out\MBB-AI.jar
```
