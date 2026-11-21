# provider-mock

Provider 协议的 mock server，JVM 模块，project path `:tools:provider-mock`。它把应用真正会发的四种
流式协议在本地 HTTP 上实现一遍，供 `core/ai` 的 provider 测试和本地联调使用；不依赖任何真实
provider，也不需要 key。

覆盖的协议：

- OpenAI Chat Completions：`POST /v1/chat/completions`，请求体带 `stream` 时回 SSE，否则回普通 JSON
- OpenAI Responses：`POST /v1/responses`，SSE 带 `event:` 行，终止事件是 `response.completed`
- Anthropic Messages：`POST /v1/messages`，`message_start` / `content_block_*` / `message_delta` / `message_stop`
- Gemini：`POST /v1beta/models/{model}:streamGenerateContent?alt=sse`，以及 `:generateContent` 的非流式 JSON
- 模型列表：`GET /v1/models` 与 `GET /v1beta/models`

## 运行

```powershell
.\gradlew.bat :tools:provider-mock:run
```

启动后打印四个 base URL，把应用的 provider 地址指过去即可：

```text
OpenAI Chat Completions base: http://127.0.0.1:<port>/v1  (POST /chat/completions)
OpenAI Responses base:        http://127.0.0.1:<port>/v1  (POST /responses, useResponseApi)
Claude Messages base:         http://127.0.0.1:<port>/v1  (POST /messages)
Gemini base:                  http://127.0.0.1:<port>/v1beta  (POST /models/{model}:streamGenerateContent)
```

默认绑定 `127.0.0.1` 的临时端口（端口 `0`），测试之间不会抢端口。

### 设备联调

服务只监听宿主的 loopback，设备上的应用访问不到。用固定端口起服务，再把设备的 loopback 反向打到宿主：

```powershell
.\gradlew.bat :tools:provider-mock:run --args="--port 18080"
adb -s <序列号> reverse tcp:18080 tcp:18080
# 应用的 provider baseUrl 填 http://127.0.0.1:18080/v1，Gemini 用 http://127.0.0.1:18080/v1beta
```

用完执行 `adb -s <序列号> reverse --remove tcp:18080` 撤掉转发。

## 场景

模型 id 选择场景，也可以在请求头加 `X-Mock-Scenario` 覆盖：

| 模型 id | 场景 |
| --- | --- |
| `mock-normal` | 正常文本、usage 与协议终止事件 |
| `mock-reasoning` | 推理增量，用各协议真正使用的形状 |
| `mock-tool` | 一个工具调用，参数分批到达 |
| `mock-rate-limit` | HTTP 429，带 `Retry-After: 2` |
| `mock-server-error` | HTTP 500，HTML 响应体 |
| `mock-truncate` | 发一段增量后直接断开，没有终止事件 |
| `mock-empty` | 只有终止事件、没有内容 |
| `mock-slow` | 正常内容，chunk 之间加延迟 |
| `mock-utf8-split` | Claude 载荷换成中文与 emoji，按 7 字节切片写出，每片之间延迟 1ms，字符会跨写边界 |
| `mock-utf8-broken` | Claude 载荷同上，但整帧写出前删掉一个 UTF-8 续字节（不切片），线上字节非法 |

`mock-slow` 的延迟可用 `X-Mock-Delay-Ms` 请求头覆盖，默认 200ms；`mock-utf8-split` 固定每片 1ms；其它场景不延迟。

两个 `mock-utf8-*` 场景的文本只走 Claude 载荷，其它协议仍用各自正常文本；它们的文本是 ASCII，选择这两个场景时切分与删续字节不会命中字符，所以断言只放在 Claude 上。

模型 id 不认识时按 `mock-normal` 处理，所以随便填一个模型名也能联调。

## 测试用法

`ProviderMockServer` 构造时启动、`close()` 停止，`ProviderMockRule` 是现成的 JUnit 4 规则：

```kotlin
@get:Rule
val mock = ProviderMockRule()

@Test
fun example() = runTest {
    val provider = OpenAIProvider(OkHttpClient())
    val setting = ProviderSetting.OpenAI(apiKey = "test-key", baseUrl = mock.openAiBaseUrl)
    // 断言解码结果，并用 mock.recorder 检查实际发出的请求
}
```

`recorder` 记录方法、路径、query、请求头、原始请求体与时间戳。凭据类请求头只记录存在性与
`Authorization` 的 scheme 前缀，值是 `<redacted>`，不会在测试输出里出现真实 key。

模块本身不参与 `app` 构建，也不作为任何已发布产物的依赖。
