# MCP 工具失败重试元数据

## 状态

已实现，登记为 C120；实现说明与合并注意见 [`checklist/workspace.md`](../checklist/workspace.md) 的 C120。

## 目标

- MCP 工具可以逐项标注「可重试」与「可重入」；两项默认关闭，未标注的工具保持现有行为。
- 只对用户标记过的工具自动重试，非幂等工具默认不重试。
- 重试发生在 MCP 工具调用层，不改变 LLM 请求的重试策略，也不新增错误卡片。

## 非目标

- 不重试 LLM 请求本身；`RetryPolicy` 继续只管 provider 请求。
- 不自动推断工具是否幂等，也不提供全局开关。
- 不重试用户拒绝、工具不存在、参数 JSON 非法这类调用前的本地错误。
- 不做跨会话或跨进程的重试队列。

## 数据模型

`McpTool` 增加两个带默认值的字段：

```kotlin
val retryable: Boolean = false,
val reentrant: Boolean = false,
```

- `retryable`：允许在失败后自动重试。
- `reentrant`：第一次调用可能已经在服务端执行过，仍允许再调用。
- 两项都是 `false` 时完全不重试；只开 `retryable` 时只重试「确定没有发出去」的失败。
- 字段随 `McpCommonOptions.tools` 一起序列化，旧配置解码为 `false`；不涉及 Room。

## 运行时

`McpManager.callTool` 在一次工具调用内最多尝试 2 次（初次加 1 次重试），间隔 500 ms。失败分两类：

- 未发出：工具请求还没有发出去。`McpClientUnavailableException` 只在取 session/client 时抛出；这之前可能已经做过 OAuth 刷新或重连，但工具本身没有执行，重试不会重复副作用。只要 `retryable` 就允许重试。
- 已发出：其他异常、超时，或 `CallToolResult.isError == true`。这类失败可能已经执行过，只有 `retryable && reentrant` 才允许重试。

取消（`CancellationException`）直接向上抛，不重试。重试耗尽后保留最后一次失败的现有处理方式：未发出仍返回 `Failed to execute MCP tool: ...` 文本，其他异常交给 `AgentLoop` 生成工具错误结果，`isError` 结果按原内容返回。

重试只记录普通日志，不产生 UI 状态；当前调用已经拿到的工具快照不变。

## UI

MCP 工具卡片在「需要审批」「启用」之外增加「可重试」「可重入」两个小开关。文案走四份 locale；没有描述文字，语义由本节和 checklist 说明。

## 兼容性

- 新增字段有默认值，旧设置、旧备份和旧构建都能解码；旧构建保存时丢掉这两个字段，属于已知降级。
- 工具发现写回（`mergeTools`）按名字保留已有配置，新增字段不会在 sync 时丢失。
- 保存工具开关时复制 `enable`、`needsApproval`、`retryable`、`reentrant`；`description` 和 `inputSchema` 取最新存储值，不能只复制 `enable`/`needsApproval`。

## 测试

- 未标记工具不重试。
- 只开 `retryable` 时，未发出失败重试，已发出失败不重试。
- `retryable && reentrant` 时，异常和 `isError` 结果都重试。
- 未发出和已发出的异常在耗尽预算时都停止，并抛最后一次异常。
- 最多 2 次尝试；取消不重试。
- 字段默认 false，旧 JSON 解码和往返保持。
- 工具卡片的开关由 `onToolChange` 复制并持久化。
