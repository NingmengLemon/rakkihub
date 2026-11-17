# DSH 对照审计

基线 `dsh-v0.2.1-rc.5`（`b830ec950f`）。下表路径都是 RikkaHub 侧位置。

## 对照检查记录

| 优先级 | 领域 | 现状与风险 | 建议 |
| --- | --- | --- | --- |
| P1 | 截断的 SSE 被当成正常完成（C59 已修） | C59 前 Claude 的 `onClosed` 无条件补 `Finish`，没有 `message_stop` 的半截流会被提交为完整回复；OpenAI/Google 的 `onClosed` 同样无条件调用 `decoder.finish()`。C59 起五个 decoder 未见终止事件时抛 `IOException`，provider `onClosed` 转成 `close(e)`，`onFailure` 空 body 不再静默正常关闭。`ClaudeProvider.kt`、`ClaudeStreamDecoder.kt`、`ChatCompletionsAPI.kt`、`ChatCompletionsStreamDecoder.kt`、`ResponseAPI.kt`、`ResponseApiStreamDecoder.kt`、`GoogleProvider.kt`、`GoogleStreamDecoder.kt`、`InteractionsAPI.kt`、`InteractionsStreamDecoder.kt`。 | 已按此实现；严格判定对不发终止事件的中转会变成可重试错误，按 provider 加宽松条件而不是全局关掉。 |
| P1 | 停止的 pending 结算不保证执行（C59 已修） | C59 前 `stopGeneration` 先 pause/cancelJobs，再 join，join 后才 `finishInterruptedPendingTools`，不在 `NonCancellable`；页面退出导致 join 取消时 pending 工具不结算。C59 起 join 与工具收尾放进 `withContext(NonCancellable)`，发送前的 `finishInterruptedPendingTools` 仍保留作崩溃补偿。`ChatService.kt:1481-1493`、`ChatVM.kt:386-390`。 | 已按此实现；NonCancellable 只包 join 和工具收尾，不包 UI 状态。 |
| P1 | 分支切换绕过审批门禁（C60 已修） | C60 前 `selectMessageNode` 只挡压缩，不挡审批；切走分支后 `isAwaitingToolApproval` 变 false，队列可派发，pending 调用留在未选中分支。C60 起 `isAwaitingToolApproval` 或 `pendingApprovalTurn` 非空时直接抛 `ConflictException`。`ChatService.selectMessageNode`、`Conversation.isAwaitingToolApproval`。 | 已按此实现；ChatVM 目前只记日志，Web 返回 409。 |
| P1 | 审批恢复重新解析配置（C60 已修） | C60 前审批期间 `setChatModel` 没有门禁，`handleToolApproval` 恢复时重新 `runTurn`/`createContext`，工具结果和工具 schema 会落到新模型/provider。C60 起 `ConversationSession.pendingApprovalTurn` 冻结本轮 `ChatTurnContext`，审批恢复复用它；配置变化从下一轮生效。`ChatService.runTurn`、`ConversationSession.pendingApprovalTurn`；`ChatVM.setChatModel` 没有改，允许改但只影响下一轮。 | 已按此实现；冻结只在内存，进程死亡后退化为当前配置。 |
| P1 | 没有流内 idle watchdog（C62 已加，含心跳与开关） | C62 前只有 OkHttp/Ktor 的 10 分钟 per-read timeout；无 chunk 无心跳会卡 10 分钟，有 SSE 心跳时 read timeout 被不断重置，可能永久停在生成中。C62 起 `AgentRequest.streamIdleTimeout` 默认 90 秒，`AgentLoop.stream` 用 `Flow.timeout` 在相邻 chunk 间超时；Claude 的 ping 解码成 `StreamChunk.Heartbeat` 重置看门狗，`NetworkSetting.streamIdleTimeoutEnabled` 可以整条关掉。`AgentLoop.kt`、`Agent.kt`、`ClaudeStreamDecoder.kt`。 | 已按此实现；自动重试关闭时超时直接出错误卡片，开启后按策略重试。 |
| P1 | 没有 error-driven overflow 恢复（C63 已撤销） | C63 曾用 `AgentHooks.onContextOverflow` 把窗口缩到最后一条 user 后重试，但它静默丢掉整段历史且每轮重演，已撤销：hook、`overflowWindow`、`shrinkActive` 与相关测试都删除，超限只按 `CONTEXT_WINDOW_EXCEEDED` 出错误卡片。`AgentLoop.kt`、`Agent.kt`、`ChatRequestFactory.kt`。 | 已按撤销处理；是否恢复等失败恢复与消息模型重做。 |
| P2 | 工具恢复语义失真（C65 已修恢复结果与超时设置） | C65 起按 `startedAt` 区分 `not_started` 与 `outcome_unknown`，`cancelInterruptedTools` 和 `withoutUnresumableToolCalls` 共用恢复结果，逐 part 保留文本与推理；工具错误只给模型摘要，堆栈只进日志（C78 起 `Log.e`）。工具声明 `timeoutMillis`，外层 `AgentSetting.toolTimeoutSeconds`（0 表示不限制）与它取较大值；`workspace_shell` 的默认与最大命令超时也进设置。output 的 NonCancellable 写入仍受 FlowCollector 跨协程发射限制，留后续。 | 已按恢复语义、超时设置与声明超时实现；日志分级已完成，取消写入待后续。 |
| P2 | `withoutUnresumableToolCalls` 删除整条消息（C65 已修） | C65 起逐 part 处理，只把不可恢复的 Tool part 换成恢复结果，保留文本、推理和其它 part，不再删除整条 assistant 消息。`Conversation.kt`。 | 已按此实现。 |
| P2 | 进程死亡丢失重试与收尾状态（C67 已恢复半截回复，C70 已持久化队列，C71 已持久化错误卡片） | 只周期保存回复节点，5/15/30 秒分档，小节点最迟 60 秒；C67 的 `isGenerating` 随消息落库，加载时把半截回复补成 `INTERRUPTED` 并补工具结果；C70 把未出队消息落到 `noBackupFilesDir`，恢复为暂停态；C71 把错误卡片落到 `noBackupFilesDir`，启动时恢复。实时 `Retrying (n/m)` 状态仍不跨进程：进程死亡后没有在途请求，半截回复按 `INTERRUPTED` 恢复，重试倒计时恢复出来只会误导；首次保存前死亡仍会丢。C50 在节点漂移或含 base64 图片时静默跳过。`MessageSaveLoop.kt`、`ConversationSession.kt`、`ChatService.kt`、`ChatErrorStore.kt`。 | 继续持久化 turn 状态或时间戳心跳；保存失败要上报；live retrying 不跨进程，重试 attempt 的 UI 并入 B1，用量仍按 C80 口径。 |
| P2 | 消息队列暂停后静默滞留（C66 已修派发与回插，C70 已持久化） | C66 前队列 paused 且非空时，新消息只 enqueue，`takeNext` 因 paused 返回 null，消息不发也不进正文；先 `takeNext` 出队再写历史，中间失败会丢消息。C66 起显式发送总是 resume，发送失败且未落库时回插队首。C70 起未出队消息按会话落到 `noBackupFilesDir`，恢复为暂停态，用队列 id 与已落库消息去重。`ChatService.kt`、`MessageQueue.kt`、`MessageQueueStore.kt`。 | 已按此实现派发恢复、失败回插和跨进程持久化；编辑占位与暂停状态不落盘。 |
| P2 | 上下文压力与压缩管理（C72/C74 已做，C73 已撤销） | C72 前发送前没有 token 估算或 usage 水位；摘要请求 `maxTokens = null`，截断即整次压缩失败；截断 keep=1 且末条是 assistant 时会丢掉产生它的 user。C72 起保留起点按 user 轮次对齐，摘要输出和输入都有保守 token 预算，超预算先提示截断/多保留，摘要被截断时退回部分摘要；C74 起当前窗口最近 usage 加尾部估算，达到 80% 在主聊天页底部显示压缩建议条，手动打开管理上下文，不自动压缩。C73 的发送前裁剪已撤销（静默改写请求、破坏消息段前缀缓存），超限只出错误卡片。`ChatRequestFactory.kt`、`CompressionPlan.kt`、`ContextPressure.kt`、`ChatPage.kt`、`ChatService.kt`。 | 自动压缩仍不做；C73 已撤销，C72/C74 的预算与水位保留。 |
| P2 | scratch 生命周期只靠调用点清理（C68 已清理崩溃对并过滤请求/导出/Web 快照/搜索） | 压缩失败或取消留下的 prompt 与半截回复只在发送、截断、再次压缩前清理；C68 起加载会话时删除回复仍为 `COMPLETE` 的崩溃对，失败/取消对标 `FAILED` 后跨 reload 保留，请求派生、导出、Web/SSE 的 `Conversation.toDto` 和 App/Web/agent 搜索结果会过滤；浮动窗口只预览当前回复，压缩轮次结束即清空，不列出孤儿对。`ChatService.kt`、`ConversationSession.kt`、`ConversationMessages.kt`、`ConversationRepository.kt`、`ai/ui/Message.kt`。 | 给 scratch 打逐 part 不可发送标记；C68 已做崩溃对载入清理、请求/导出/Web 快照/搜索过滤。 |

## 修复顺序

P1 分批：P1a 截断 SSE 与停止结算，P1b 审批分支与配置快照，P1c idle watchdog 与稳定错误码重试（overflow 兜底原先列在 P1c，已随 C63 撤销）。

1. P0 失败 attempt 隔离已由 C58 完成；多步工具调用的 per-step 细化并入 B1，工具恢复结果已由 C65 处理。
2. P1a 截断 SSE 与停止结算已由 C59 完成；P1b 审批分支与配置快照已由 C60 完成。
3. P1c 的稳定错误码重试已由 C61、idle watchdog 由 C62（含心跳与开关）完成；overflow 兜底（C63）与发送前裁剪（C73）已撤销，超限只出错误卡片；自动重试（C75）已恢复为默认关闭的可选项，P1 收尾后进入 P2。
4. P2 工具恢复结果、工具声明超时与超时设置由 C65 完成，队列派发与失败回插由 C66 完成，进程死亡半截回复由 C67 完成，scratch 加载清理与请求/导出/Web 快照/搜索过滤由 C68 完成，队列持久化由 C70 完成，错误卡片持久化由 C71 完成，压缩计划对齐、摘要预算、部分摘要和范围检查由 C72 完成，usage 水位建议条由 C74 完成；C76 的 attempt 卡片随重试下线、C77 的错误卡片重试撤销；B1 已恢复默认关闭的自动重试与 C76 attempt 卡片，C76 摘要已持久化，B2 做剩余消费方与手动 step 重试，C77 永久取消；剩余自动压缩（不做）、取消写入、失败 attempt 的 usage 区分、C58 的投影与新消息模型（B1/B2）。

## 范围说明

C59 已逐行核对五个 decoder 的终止判定和五个 provider 接线点的 `onClosed`，并用 `:ai:testDebugUnitTest` 全量验证；工具并发没有展开，当前 RikkaHub 按顺序执行，未发现正确性问题，只是并发效率较低。
