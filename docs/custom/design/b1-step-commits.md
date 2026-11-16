# B1：step 级提交与失败投影

- 状态：step 记录、Claude step 级请求投影、失败工具事实、审批/取消结算、最后节点审批与默认关闭的自动重试已落地；C76 的 attempt 摘要已持久化
- 日期：2026-11-15
- 范围：step 记录、失败投影、自动重试恢复，以及重试恢复后自动重现的 C76 attempt 卡片。手动 step 重试、搜索过滤、Web DTO 扩展与官方导出兜底归 B2；错误卡片重试永久取消
- 对齐参考：DSH `dsh-v0.2.2-rc.1`（tag 指向 commit `66491b05e3f05ad35bcdd4f6cd7ed26041d9e3db`，2026-11-04；本地 clone `E:\MyWorkspace\deepseek-harness` 的 HEAD 是 `dsh-v0.2.1-rc.5`，0.2.2 事实均用 `git show dsh-v0.2.2-rc.1:<path>` 读取）
- 前置：`docs/custom/roadmap.md` 的「失败恢复与上下文的返工」冻结记录

## 1. 背景与现状

- 一次 turn 只有一条 assistant 消息，多个 step 的正文、推理、工具调用和结果按顺序追加在这条消息的 parts 里。
- 失败或取消时整条消息标 `FAILED` / `INTERRUPTED`。`filterModelVisible()` 对 `FAILED` 整条过滤，前面成功 step 里的工具调用与结果对模型不可见，下一轮可能重复执行。
- 自动重试本来就在 step 级：`withRetry` 从本 step 的 `stepStart` 重放，之前的 step parts 不会被清掉。真正的问题是重试期间 preview 会覆盖当前 step 的展示，以及重试耗尽后整条消息被过滤。
- 存储：会话树在 `message_node` 表，一行一个 `MessageNode`，`messages` 列是 `List<UIMessage>` 的 JSON，`select_index` 是整轮候选下标；整会话保存会删除并重插全部节点；收藏记录节点 id；FTS 按消息索引。官方兼容出口保留行结构，`OfficialDatabaseAdapter` 对一致形状只改写 TEXT 列的附件路径，已登记分叉先经 `OfficialSchemaTrimmer` 裁剪，未登记分叉直接抛 `OfficialExportUnsupportedException`。
- 自动重试已恢复接线，默认关闭、用户显式开启后生效；C63 自动缩窗与 C73 发送前裁剪仍撤销。

## 2. 目标与非目标

目标：

1. 每个 step 有独立的提交状态与 part 范围，失败 attempt 不进入模型历史。
2. 失败 turn 里已经成功的前序 step 与其中的工具事实对模型可见，工具调用与结果成对。
3. 自动重试在聊天轮次恢复，只重放失败的那一步；之前的 step 不重放、不吞内容。
4. 一个 turn 仍是一条消息、一个节点；分支、压缩、FTS、搜索跳转、浮动窗口、导出语义不变。
5. 不新增 Room 表或列，不改 `Settings` 结构。

非目标（归 B2）：

- 手动 step 重试、错误卡片的 step 目标与 `ChatErrorStore` 字段。
- attempt 的持久化与 C76 卡片的持久化；B1 只复用现有内存态卡片。
- `conversation_search` 过滤、Web `MessageDto` 的 steps/status 展示。
- 官方导出的兜底投影、`contextLimitBoundaryIndex` 与投影对齐、C47 的改动。

## 3. DSH 0.2.2-rc.1 的参考与取舍

核对过的位置：

- `packages/core/session/src/types.ts`：`assistant/message`、`assistant/attempt`、`turn/end`、`step/start`、`step/end`。
- `packages/core/session/src/repair.ts`：`TOOL_NOT_STARTED`、`TOOL_OUTCOME_UNKNOWN`。
- `packages/core/agent-loop/src/agent.ts` 与同目录 README：step 状态机、失败收尾、工具恢复。
- `packages/llm/llm/src/retry-policy.ts`：provider 拥有的重试策略。

借的语义：

- 成功提交与失败尝试分离：只有成功的一步进入模型历史。
- 取消的流把已交付的正文与推理前缀作为 `interrupted: true` 的消息提交。
- 失败步骤为未回答的工具调用补 `TOOL_NOT_STARTED` 或 `TOOL_OUTCOME_UNKNOWN`。
- 重试单位是 step；DSH 默认 normal 模式 5 次、码表 `EMPTY_RESPONSE`/`RATE_LIMIT`/`SERVER`/`TIMEOUT`/`TRANSPORT`、退避 500ms 起、10s 上限、0.1 抖动。

不借的结构：

- DSH 的每步一条 `assistant/message` 依赖事件溯源日志；RikkaHub 拆节点会破坏 `select_index` 分支、收藏、条数上限与官方导出，所以只在这条消息内部记录 step。
- 请求重建、request header、fork seed、inbox 与本次目标无关。
- 默认重试参数不照搬：保留 RikkaHub 现有的 3 次、1s/2s/4s、10% 抖动。

## 4. 数据模型

### 4.1 UIMessage 新增字段

```kotlin
val steps: List<MessageStep> = emptyList()
```

旧消息、旧备份以及导入时解码为 `emptyList()`。

```kotlin
@Serializable
data class MessageStep(
    val status: StepStatus,
    // 本步在 message.parts 里的范围，左闭右开
    val startPartIndex: Int,
    val endPartIndex: Int,
    val startedAt: Instant,
    val finishedAt: Instant? = null,
)

@Serializable
enum class StepStatus {
    @SerialName("running") RUNNING,
    @SerialName("tools") TOOLS,
    @SerialName("succeeded") SUCCEEDED,
    @SerialName("failed") FAILED,
    @SerialName("cancelled") CANCELLED,
}
```

`Instant` 用 `kotlin.time.Instant`，与 `UIMessagePart.Tool` 的现有约定一致；不要用 java.time。

`UIMessagePart.Tool` 增加带默认值的字段：

```kotlin
val callComplete: Boolean = true
```

`ToolCallStart` 创建时置 false；`ToolCallEnd` 按 `toolCallId` 找到对应 part 置 true；`Tool.merge` 对 `callComplete` 取逻辑或；旧数据默认 true。

约定：

- `steps` 只在生成与结算时追加或收尾；用户编辑、重新生成会创建新的候选消息，新消息没有 steps，按单 step 处理。
- 先执行 `prepareStep`，成功后才创建 step 与 base 消息（parts 为空，`modelId` 用 `params.model.id`），发 step-start 后发起请求；`prepareStep` 失败不创建节点、不落空消息，只出错误卡片。`createContext` 在进入 `runTurn` 生成前失败同理。
- 接管旧 assistant 消息继续生成时（空 steps、已有 parts），先补一条覆盖 `[0, 现有 parts.size)` 的前缀 step：消息里有任何未执行工具（Pending、Approved、Denied、Answered）时前缀 step 用 TOOLS，工具执行或结算完再置 SUCCEEDED；否则按消息状态映射，COMPLETE→SUCCEEDED、INTERRUPTED→CANCELLED。B1 不支持对旧 FAILED 消息接管生成（手动重试归 B2），FAILED 消息仍整条过滤。接管 INTERRUPTED 消息并成功完成新 step 后，消息必须复位为 COMPLETE；`markReplyStatus` 保持单向，新增一个只在接管续写时使用的复位入口。
- 消息 `status` 仍表示整轮结果：最后一步成功为 `COMPLETE`，失败为 `FAILED`，取消为 `INTERRUPTED`；审批暂停时消息是 `COMPLETE`，step 保持 `TOOLS`。
- `usage`、`generationTimeMs`、`stepCount` 继续按整轮累计；`lastUsage` 保持“最后一次请求自己的 usage”，不是累计值。
- 旧消息 `steps` 为空时按今天的规则整条过滤：`FAILED` 丢弃，`INTERRUPTED`/`COMPLETE` 保留；只有被接管继续生成时才补前缀 step。

### 4.2 step 的 part 范围

- 范围以最终持久化的 `message.parts` 为准；preview 的 `visualTransform` 结果会落库，取消时没有 `finishStep`。
- step 开始时 `startPartIndex = parts.size`。
- 每次 preview、`finishStep`、工具结果写入后，由产生消息的一方把当前 step 的 `endPartIndex` 重算为持久化消息的 `parts.size`。
- 输出变换只能改本步新增的 parts；终态失败、取消、加载恢复时在结算路径重算最后一次非终态 step 的范围。
- 失败路径由 AgentLoop 用最后一次 attempt 的 parts 过 `finishStep` 后发 `MessageUpdated`。取消后协程不能再 emit，取消结算由 `runTurn` 的 NonCancellable 块基于会话里最后一次 preview 的内容完成。

## 5. 生命周期与结算

| 阶段 | step 状态 | 消息状态 | 写入 |
| --- | --- | --- | --- |
| 发起请求 | RUNNING | `isGenerating = true` | `prepareStep` 成功后写 step 并发 step-start，再请求 |
| 收到正文/推理/工具调用 | RUNNING | `isGenerating = true` | parts 追加，preview |
| 模型成功且无工具调用 | SUCCEEDED | COMPLETE | 发最终更新，结束本轮 |
| 模型成功且有工具调用 | TOOLS | `isGenerating = true` | 开始执行或审批 |
| 工具等待审批（最后节点） | TOOLS | COMPLETE | turn 以 `AwaitingApproval` 结束 |
| 工具全部完成 | SUCCEEDED | `isGenerating = true` | 发 step 更新，进入下一步 |
| 达到步数上限 | 保持 SUCCEEDED | COMPLETE | 发最终更新，结束本轮 |
| 模型请求最终失败 | FAILED | FAILED | 最后一次 attempt 的 parts，收口工具 |
| 用户取消（生成中） | CANCELLED | INTERRUPTED | 基于最后一次 preview，收口工具 |
| 用户取消（审批等待，无 job） | CANCELLED | INTERRUPTED | 收口 Pending，清理 pendingApprovalTurn |
| 中间节点需要审批 | SUCCEEDED | COMPLETE | 本批未执行工具全部 `not_started`，结束本轮 |
| 进程死亡 | 只关闭可恢复的非终态 step | 关闭的标 INTERRUPTED，审批暂停保持 COMPLETE | 加载时扫描落库 `isGenerating = true` 的消息 |
| `prepareStep` 失败 | 不创建 | 不创建 | 只出错误卡片，不落空消息 |

规则：

- 审批只允许最后节点。`AgentRequest` 增加 `allowToolApproval`，判定式：`regenerateNodeId == null || regenerateNodeId == messageNodes.last().id`。新 turn 的回复节点在 `createAgent` 时还没进列表，不能拿 `replyNodeId` 与末节点比较，必须按“新 turn 视为末节点、中部 regenerate 视为非末节点”来算。
- 不允许审批时，仅当本批存在 `needsApproval` 的工具才返回 `AgentFinishReason.ApprovalUnavailable`；本批全是 Auto 工具时照常执行。`ChatService` 收到 `ApprovalUnavailable` 后由 app 侧的 `settleToolParts` 把本批所有未执行工具（包括同批免审批的 Auto 调用）补 `not_started`，step 记 SUCCEEDED，消息 COMPLETE，flush 后结束。结算函数放在 app 的 `ConversationMessages.kt`，AgentLoop 只返回结束原因，不跨模块调用。
- `isAwaitingToolApproval` 改为只看最后一条 assistant 的 Pending 工具；`pendingToolCalls` 同步从 last-only 派生，悬浮窗不会对非末条显示审批。旧消息（steps 为空）仍扫描全部消息，兼容历史数据。
- 审批暂停：消息 COMPLETE、step TOOLS、`isGenerating = false`，同一进程内 C60 使用冻结上下文；进程重启后审批恢复重建 turn 上下文。
- 每一次 step 状态转移都要经 `MessageUpdated` 落进会话；工具轮成功进入下一步、达到步数上限、无工具成功结束时都要发最终消息。`Finished` 事件里的 message 与当前会话不一致时，ChatService 也要 upsert，避免最后一步停在 TOOLS。
- 终态失败：AgentLoop 把最后一次 attempt 提升到循环外，过 `finishStep`，再发 `MessageUpdated` 事件后抛错，由 ChatService 落库；`usage = base.usage + (attempt.usage ?: 0)`，`generationTimeMs` 流式用 `firstChunk` 的 elapsed 累加，非流式用整次请求耗时，`lastUsage = attempt.usage ?: base.lastUsage`，`stepCount` 加一。
- `settleToolParts` 先按结算前的真实执行状态决定 step 终态：RUNNING → CANCELLED；TOOLS 且还有未执行工具 → CANCELLED；TOOLS 但工具都已执行 → SUCCEEDED。状态定完后再删除 `callComplete = false` 并补结果，避免先补结果把 `isExecuted` 变成 true 后误判为 SUCCEEDED。
- `settleToolParts` 按状态补结果：Auto/Approved/Pending 未开始 → `not_started`；`startedAt` 非空且无结果 → `outcome_unknown`；Denied 与 Answered 复用 AgentLoop 已经使用的输出形状（Denied 的错误文本、Answered 的回答文本），不另造 JSON。
- 取消（生成中）：基于最后一次 preview，按真实状态定 step，消息 INTERRUPTED；未派发的完整调用补 `not_started`，已派发未完成的补 `outcome_unknown`；Denied/Answered 保留已经解析的结果，不覆盖。
- 取消（审批等待，无 job）：`stopGeneration` 先按需初始化会话（Web 冷会话），取最后一条 assistant，按真实状态把 step 置 CANCELLED，结算 Pending/Approved 与同批未执行的 Auto 工具，Denied/Answered 保留，消息 INTERRUPTED，清 `pendingApprovalTurn`，flush。有 job 的停止只 cancel 并 join，由 `runTurn` 的 NonCancellable 块结算。
- 加载恢复：只扫描落库 `isGenerating = true` 的消息并清零标记；原本 FAILED 的保留 FAILED；有 Pending 工具的 TOOLS 视为审批暂停，保持 COMPLETE/TOOLS，不结算；否则按真实状态关闭 step、结算未执行工具，消息 INTERRUPTED。旧消息 steps 为空时按现有 `recoverAbandonedGeneration` 的 isGenerating 处理。
- `withoutUnresumableToolCalls` 对带 steps 的消息不再直接补结果：改走 `settleToolParts` 并按真实状态关闭 step 后再进入生成；旧消息保持原行为。
- 旧消息接管：先补前缀 step（有未执行工具时 TOOLS，否则按消息状态映射）；如果原消息是 INTERRUPTED，在开始生成前通过专用复位入口改成 COMPLETE，Finished upsert 之后不再复位；新 step 进入审批暂停时消息仍是 COMPLETE。
- 半截 ToolCall：`finishStep` 删除后如果消息为空，按 `EMPTY_RESPONSE` 失败结算（step FAILED、消息 FAILED、错误卡片），不生成空 COMPLETE 消息；如果还有其它内容，正常继续。
- 取消或加载恢复的目标消息如果 `parts` 为空（首 token 前取消、prepareStep 成功后还没收到内容），删除该节点而不是留下空回复；错误卡片仍然显示。
- annotations 是消息级字段：请求投影在消息 `status = FAILED` 时整体丢弃，否则保留；媒体 part（包括 Tool.output 里的图片）在 FAILED/CANCELLED/TOOLS step 丢弃，SUCCEEDED 保留。摘要投影始终丢弃 annotations 与媒体。
- `ServerTool` 的 `IN_PROGRESS` 在请求与摘要投影中丢弃，已 COMPLETED/FAILED 的按投影表保留。
- 每条消息仍由现有保存循环落库；step 的收尾更新在同一个节点行内完成，不新增行。

## 6. 投影

### 6.1 projectForRequest

| step 状态 | 保留内容 |
| --- | --- |
| SUCCEEDED | 全部 parts，ServerTool 只保留 COMPLETED/FAILED |
| FAILED | 已执行 Tool parts 与已完成的 ServerTool；正文、推理、媒体 part、annotations 丢弃 |
| CANCELLED | 已交付正文、推理、已执行 Tool parts、已完成的 ServerTool；媒体 part 与 annotations 丢弃 |
| TOOLS | 正文、推理、已执行 Tool parts、已完成的 ServerTool；未执行调用、IN_PROGRESS ServerTool、媒体 part 与 annotations 丢弃 |
| RUNNING | 按 CANCELLED 处理 |

多 step 消息：请求投影按每步状态拼接，前面 SUCCEEDED step 的正文与工具事实保留，失败 step 只留 Tool parts；旧消息 `steps` 为空时 `FAILED` 整条丢弃。

### 6.2 projectForSummary

摘要投影先按整条消息判断：`status = FAILED` 整条丢弃，不按 step 拼接。其余消息按 step 规则：

| step 状态 | 保留内容 |
| --- | --- |
| SUCCEEDED | 正文、推理、已完成的 ServerTool，以及已执行的 Tool parts |
| CANCELLED | 已交付正文、推理、已完成的 ServerTool，以及已执行的 Tool parts |
| TOOLS | 正文、推理、已完成的 ServerTool，以及已执行的 Tool parts |
| RUNNING | 按 CANCELLED 处理 |

媒体 part 与 annotations 在摘要投影里一律丢弃，标题和记忆只保留文本语义；请求投影的 SUCCEEDED step 保留这些内容。两张表的差异是用途不同，不是矛盾。

规则：

- 投影后 `parts.isEmpty()` 的消息丢弃；检查点与 `parts=[Text("")]` 的截断标记保留。
- 投影后的消息把 `steps` 清空，请求与摘要都不需要原始范围；原始消息不受影响。
- `buildStep` 的顺序：`projectForRequest`、丢空、`withoutCompressionScratch`、`limitContext`、插入 system 消息、输入变换、`mergeAdjacentUserMessages`。空消息判断不包含 system 消息。
- `CompressionPlan` 的估算窗口同样先投影、丢空、`withoutCompressionScratch`、`limitContext`，与请求一致。
- 调用方：`ChatRequestFactory.buildStep`、`ContextPressure`、`CompressionPlan` 用请求投影；标题的 `recentModelMessages`、记忆的 `dialogueMessages`、`conversation_read` 用摘要投影。B1 不改 `contextLimitBoundaryIndex`、`conversation_search` 与 Web DTO，它们保持现状。
- 只有半截 ToolCall、被 `finishStep` 删除后消息为空时，按空消息处理，不把空 assistant 当有效回复。

## 7. 重试

- 聊天轮次恢复 `settings.retryPolicy()`；压缩摘要（`ChatRequestFactory.streamCompressionSummary`）与记忆整理（`MemoryConsolidator`）显式传 `RetryPolicy.NONE`；标题没有 retry 参数。
- 默认关闭；开启后默认 3 次、1s/2s/4s、10% 抖动、`Retry-After` 用 `maxDelay` 截断，码表与 C61 一致。
- 重试仍在 step 内由 `withRetry` 完成：`attempt = stepStart`，之前的 step parts 不动；step 记录与 id 保持不变。
- 重试会重新触发 `AgentEvent.Retrying`，现有 `ConversationSession.retryAttempts` 与 C76 内存态卡片会自动重现。B1 接受这一点：卡片只显示当前进程的尝试记录，不落盘、不改卡片代码；attempt 的持久化归 B2。
- 重试耗尽：step FAILED、消息 FAILED；之后请求保留前面 SUCCEEDED step 的正文与工具事实。

## 8. 持久化、降级与消费方

- `message_node.messages` 的 JSON 多出 `steps`，Tool 多出 `callComplete`；不新增 Room 列，不改 Settings。
- 旧会话 `steps` 为空，按单 step 处理；旧 `FAILED` 整条丢弃；旧消息被接管继续生成时补前缀 step。
- 旧构建解码新会话得到空 steps，`callComplete` 回落到 true，保存时丢掉 steps 并把半截调用当完整调用；这是已知降级限制，写进 `docs/custom/README.md`，同一个提交里更新。
- 旧构建测试：保留 B1 落地前的 debug APK（形如 `3.1.1-custom.3349+f48eea2`），`adb install -r -d` 降级安装，打开含 steps 的会话再保存，确认正文可读、steps 丢失但不崩。
- 官方兼容导出：行结构与 JSON 字段保留，附件路径改写不变；`steps` 与 `callComplete` 作为未知字段，官方容忍度仍待 3.1.1 带 messages 的恢复样本确认，验收清单里标为待验证。
- Web/SSE：`MessageDto` 不含 steps/status，但 parts 直接序列化 `UIMessagePart`，`callComplete` 会多一个未知字段，前端按未知字段忽略；B1 不改 Web 合约。
- FTS、浮动窗口、Markdown 导出、通知：B1 不改结构，失败正文仍按现状可见；步骤状态不展示。

## 9. 与现有定制的关系

- C47：B1 不改边界线，留 B2。
- C58：请求投影替代整条过滤；摘要投影保持失败消息整条丢弃；旧 FAILED 消息保持旧行为。
- C60：审批只允许最后节点；中间节点 turn 不进入 AwaitingApproval；`AgentRequest.allowToolApproval` 与 `isAwaitingToolApproval`/`pendingToolCalls` 的 last-only 归属 C60。
- C61/C75：重试恢复；C75 网络设置入口恢复，仅聊天轮次生效；C75/C77 旧理由里“整条重放”的措辞在同一提交里校正。
- C65：工具恢复复用；`settleToolParts` 覆盖 Auto/Pending/Approved/Answered/Denied 并处理半截调用。
- C67：加载恢复扫描非终态 step，跳过审批暂停但清 `isGenerating`。
- C68：压缩 scratch 过滤保持不变。
- C71：B1 不动，归 B2。
- C77：永久取消，错误卡片只做信息提示，B2 不再恢复。
- C76：卡片随重试恢复自动重现，B1 只接受内存态，不改持久化。
- C80：用量仍按请求记录。
- 新增 C97：step 级提交与失败投影（B1），索引行与明细在同一个提交里登记。计数口径按 ai/ 文件：现状 13 处，B1 动 `AgentLoop`、`Agent`、`Message`、`UIMessagePart`、`StreamChunkHandler` 五个文件，`Message.kt` 内新增两个投影函数不额外计文件，`filterModelVisible`/`isModelVisible` 保留所以不减少，C97 明细写 18 处。`docs/custom/README.md` 只加 design/ 文档行与降级说明，不承担 ai/ 计数。

## 10. 实施阶段

1. 模型与投影：`steps`、`callComplete`、两个投影函数、空消息与检查点规则、媒体/annotations、序列化与旧数据测试。
2. 生命周期与结算：step 记录、step-start、失败/取消/审批/中间节点回退/进程死亡/预请求失败的收尾、工具事实保留、Finished upsert。
3. 重试恢复：聊天轮次接回 `RetryPolicy`、C75 设置入口、压缩与记忆显式 NONE、`SettingsRetryPolicyTest` 更新、C76 内存态卡片回归。
4. 验收与登记：Web/FTS/导出回归、官方恢复样本、`docs/custom/backup/acceptance.md` 补 steps/callComplete 的待验证条目、README（design/ 行、降级说明）、checklist（C97 明细与计数、C51、C58、C60、C61、C62、C65、C67、C72、C74、C75、C76、C77 措辞；C61 的自动重试措辞、C62 的“超时直接出错误卡片”、C72/C74 的 filterModelVisible 描述同步改写）与 roadmap 的 :378、:386、:400、:415 行同步改写、roadmap 同步，全部放在同一个提交里。

阶段 1 到 4 作为一个批次推送。

## 11. 测试与验收

- 序列化：旧 JSON 默认空 steps 与 `callComplete=true`；新 JSON 往返；StepStatus 序列名；`kotlin.time.Instant`。
- 投影：成功 step 全保留；失败 step 只留 Tool/ServerTool；纯文本失败投影为空后整条丢弃；媒体与 annotations 在失败/取消/TOOLS 下丢弃；取消 step 保留前缀；旧 FAILED 整条丢弃；多 step FAILED 请求保留前面 SUCCEEDED；摘要对整条 FAILED 丢弃（多 step fixture 明确覆盖）；相邻 user 在丢空后合并；截断标记保留；TOOLS/RUNNING 投影；只有半截 ToolCall 时为空的处理。
- 生命周期：无工具成功；空回复重试耗尽；审批暂停与恢复；中间节点需要审批时全部 not_started、step SUCCEEDED、消息 COMPLETE；中间节点只有 Auto 工具时照常执行、不返回 ApprovalUnavailable；审批暂停后进程重启（含 `isGenerating` 清理）；Web 无 job 审批停止（冷会话）；用户取消；首 token 前取消；工具执行一半；生成中进程死亡；跨 step 死亡最后一个 SUCCEEDED 保持；工具轮成功结束后重载会话 step 不是 TOOLS；终态失败时 Auto/Approved/Answered/Denied 工具按状态收口；半截 ToolCall 在四条结算路径都被删除；只有半截 ToolCall 时本轮按空内容结束；`prepareStep` 抛错时 step FAILED 且 modelId 保留；`createContext` 失败时没有 step；旧消息无 steps 但 `isGenerating=true` 的加载恢复；旧 Pending 审批接管时前缀 step 为 TOOLS 并在执行后置 SUCCEEDED；旧 INTERRUPTED 消息接管成功后复位为 COMPLETE；TOOLS 但工具都已执行时取消关闭为 SUCCEEDED；输出变换插入 Reasoning 后范围重算；取消时 preview 变换与 Reasoning 插入同时发生。
- 重试：只在聊天轮次；压缩与记忆显式 NONE；重试成功不重放前序 step；耗尽后投影正确；C76 卡片内存态重现。
- 消费方回归：Web、FTS、浮动窗口、Markdown、压缩计划、上下文条数截断。
- 降级：旧构建保存丢 steps 的已知限制；旧消息接管后补前缀 step。
- 设备：多 step 生成、失败重试、取消、重启恢复、压缩、导出、官方恢复。

## 12. 合并注意

改 `ai/`：`UIMessage` 增加 `steps`（`ai/ui/Message.kt`）、`UIMessagePart.Tool` 增加 `callComplete`（`ai/ui/UIMessagePart.kt`）、`StreamChunkHandler` 维护 `callComplete`（`ai/ui/StreamChunkHandler.kt`）、`AgentLoop` 增加 step 记录/范围重算/最终结算消息与 `allowToolApproval` 检查（`ai/agent/AgentLoop.kt`）、`AgentRequest` 增加 `allowToolApproval`（默认 true）与 `AgentFinishReason.ApprovalUnavailable`（`ai/agent/Agent.kt`）。`MemoryConsolidator` 直接构造 `AgentRequest` 时显式传 `allowToolApproval = false`，内部调用不进入审批。`settleToolParts` 放在 `app/` 的 `ConversationMessages.kt`，AgentLoop 只返回结束原因。移植上游改动时保留这些差异并按 C97 登记；C3/C96 的删除、C61 的失败分类、C65 的声明超时继续按原约定移植。

## 13. 参考

- DSH：`dsh-v0.2.2-rc.1`（commit `66491b05e3f05ad35bcdd4f6cd7ed26041d9e3db`）；`packages/core/session/src/types.ts`、`packages/core/session/src/repair.ts`、`packages/core/agent-loop/src/agent.ts`、`packages/core/agent-loop/README.md`、`packages/llm/llm/src/retry-policy.ts`。
- 本仓库：`ai/src/main/java/me/rerere/ai/ui/Message.kt`、`ai/src/main/java/me/rerere/ai/ui/UIMessagePart.kt`、`ai/src/main/java/me/rerere/ai/ui/StreamChunkHandler.kt`、`ai/src/main/java/me/rerere/ai/agent/AgentLoop.kt`、`ai/src/main/java/me/rerere/ai/agent/Agent.kt`、`app/src/main/java/me/rerere/rikkahub/service/ConversationSession.kt`、`app/src/main/java/me/rerere/rikkahub/service/ChatService.kt`、`app/src/main/java/me/rerere/rikkahub/data/ai/ChatRequestFactory.kt`、`app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt`、`app/src/main/java/me/rerere/rikkahub/data/model/ConversationMessages.kt`、`app/src/main/java/me/rerere/rikkahub/data/model/CompressionPlan.kt`、`app/src/main/java/me/rerere/rikkahub/data/model/ContextPressure.kt`、`app/src/main/java/me/rerere/rikkahub/data/ai/tools/ConversationTools.kt`、`app/src/main/java/me/rerere/rikkahub/data/ai/ConversationMetaGenerator.kt`、`app/src/main/java/me/rerere/rikkahub/data/ai/MemoryConsolidator.kt`。
