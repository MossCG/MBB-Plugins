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
| `usage` | 查看请求与 Token 统计 |
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
      "timeoutSeconds": 60
    }
  }
}
```

`apiKey` 可以直接填写，也可以使用环境变量：

```json
"apiKey": "env:OPENCODEGO_API_KEY"
```

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

## 管理命令

| 指令 | 权限 | 说明 |
|---|---|---|
| `/ai status` | `OWNER` | 查看服务状态和模型配置 |
| `/ai usage` | `OWNER` | 查看请求与 Token 统计 |
| `/ai reload` | `OWNER` | 重载 AI 配置 |

## 构建

```powershell
.\build.ps1
```

产物：

```text
out\MBB-AI.jar
```
