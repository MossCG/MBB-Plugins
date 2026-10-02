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
| `status` | 查看配置和服务状态 |
| `usage` | 查看请求、Token、耗时和趋势统计 |
| `reload` | 重载 AI 配置 |

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

不要把真实密钥提交到仓库。

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
  "messages": [
    {"role": "system", "content": "你是一个 QQ 聊天助手。"},
    {"role": "user", "content": "你好"}
  ]
}
```

成功返回：

```json
{
  "status": true,
  "action": "chat",
  "profile": "default",
  "model": "gpt-4o-mini",
  "content": "你好，很高兴见到你！",
  "cached": false,
  "usage": {
    "promptTokens": 10,
    "completionTokens": 12,
    "totalTokens": 22
  }
}
```

`content` 返回前会统一转换为纯文本，自动移除 Markdown 代码块、粗体、斜体、标题、引用和链接语法，避免 QQ 中出现大量星号。

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
```

统计维度：

- 日期
- 模型配置
- 模型名
- 动作
- 请求数、成功数、失败数
- 输入、输出、总 Token
- 总耗时与平均耗时
- 缓存命中数

`/ai usage [天数]` 会输出图片，默认展示最近 7 天，最长 30 天。

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
