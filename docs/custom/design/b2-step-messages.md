# B2：step 级提交与失败投影（完整版草稿）

- 状态：B2 草稿，B1 落地后再修订评审
- 日期：2026-11-15
- 前置：先完成 [`b1-step-commits.md`](b1-step-commits.md)；本文档保留手动重试、错误卡片、C76、搜索过滤、Web/导出兜底等 B2 内容
- 对齐参考：DSH `dsh-v0.2.2-rc.1`（tag 指向 commit `66491b05e3f05ad35bcdd4f6cd7ed26041d9e3db`，2026-11-04；本地 clone `E:\MyWorkspace\deepseek-harness` 的 HEAD 是 `dsh-v0.2.1-rc.5`，本文档的 0.2.2 事实均用 `git show dsh-v0.2.2-rc.1:<path>` 读取）
- 前置：`docs/custom/roadmap.md` 的「失败恢复与上下文的返工」冻结记录

## 1. 背景与现状

- 一次 turn 只有一条 assistant 消息，多个 step 的正文、推理、工具调用和结果按顺序追加在这条消息的 parts 里。
- `usage`、`generationTimeMs`、`stepCount` 是整轮累计；`lastUsage` 是最后一次请求自己的 usage，不是累计值。
- 失败或取消时整条消息标 `FAILED` / `INTERRUPTED`。`filterModelVisible()` 对 `FAILED` 整条过滤，已经执行过的工具调用与结果对模型不可见，下一轮可能重复执行。
- 自动重试本来就在 step 级：`withRetry` 从本 step 的 `stepStart` 重放，之前的 step parts 不会被清掉。真正的问题是重试期间 preview 会覆盖当前 step 的展示，以及重试耗尽后整条消息被过滤；所以要先修投影与结算，再恢复重试。
- 存储：会话树在 `message_node` 表，一行一个 `MessageNode`，`messages` 列是 `List<UIMessage>` 的 JSON，`select_index` 是整轮候选下标；`ConversationEntity.nodes` 是遗留空列，固定写 `"[]"`；整会话保存会删除并重插全部节点；收藏记录节点 id；FTS 按消息索引；官方兼容出口保留行结构与 JSON 字段，但 `OfficialDatabaseAdapter` 会对 TEXT 列做附件路径改写（C30/C36）。
- 自动重试当前整体下线，C63 自动缩窗与 C73 发送前裁剪已撤销。在本文档通过评审并实现前，这块保持冻结。

## 2. 目标与非目标

目标：

1. 每个 step 有独立的提交状态与 attempt 记录，失败 attempt 不进入模型历史。
2. 失败时已经发生的工具事实对模型可见，工具调用与恢复结果成对。
3. 自动重试以 step 为单位恢复，只重放失败的那一步；之前的 step 不重放、不吞内容。
4. 一个 turn 仍是一条消息、一个节点；分支、压缩、FTS、搜索跳转、浮动窗口、导出语义不变。
5. 不新增 Room 表或列，不改 `Settings` 结构，官方兼容出口保留行结构。

非目标：

- 不做 DSH 的事件溯源日志、`request/header`、surface replacement、请求重建。
- 不做逐 token 续传。
- 不做 step 级分支（一个 turn 的候选仍是整轮候选）。
- 不改 provider 的请求与响应格式。
- 不持久化 attempt 的完整 stream。

## 3. DSH 0.2.2-rc.1 的参考与取舍

核对过的位置：

- `packages/core/session/src/types.ts`：`assistant/message`、`assistant/attempt`、`turn/end`、`step/start`、`step/end`。
- `packages/core/agent-loop/src/agent.ts` 与同目录 README：step 状态机、失败收尾、工具恢复。
- `packages/core/session/src/repair.ts`：`TOOL_NOT_STARTED` 与 `TOOL_OUTCOME_UNKNOWN` 的定义。
- `packages/core/session/README.md`：事件日志与模型历史的派生关系。
- `packages/llm/llm/src/retry-policy.ts`：provider 拥有的重试策略。

借的语义：

- 成功提交与失败尝试分离：只有成功的一步进入模型历史，失败、重试、取消的尝试单独记录。
- 取消的流把已交付的正文与推理前缀作为 `interrupted: true` 的消息提交。
- 失败步骤为未回答的工具调用补 `TOOL_NOT_STARTED` 或 `TOOL_OUTCOME_UNKNOWN`，保证后续请求的工具历史仍然配对。
- 重试单位是 step，由策略控制退避与码表；DSH 默认 normal 模式 5 次、码表 `EMPTY_RESPONSE`/`RATE_LIMIT`/`SERVER`/`TIMEOUT`/`TRANSPORT`、退避 500ms 起、10s 上限、0.1 抖动。

不借的结构：

- DSH 的 `assistant/message` 每步一条依赖事件溯源日志；RikkaHub 没有这层日志，拆节点会破坏第 1 节列出的存储与分支约束，所以只在一条消息内部记录 step，不复制它的存储形状。
- `request/header`、请求重建、fork seed、inbox 与本次目标无关。
- 默认重试参数不照搬，理由见第 8 节。

与 DSH 的显式差异：

- DSH 取消时未派发的工具调用不出现在 `interrupted` 消息里，另走 synthetic `tool/call` 加 `ABORTED_BEFORE_DISPATCH` 结果。RikkaHub 的请求构建依赖同一条消息里的工具调用与结果配对，所以保留已经完整到达的未执行调用并补 `not_started` / `outcome_unknown` 结果，不引入 synthetic 调用；没有收到 `ToolCallEnd` 的半截调用直接丢弃。

## 4. 数据模型

### 4.1 UIMessage 新增字段

`UIMessage` 增加一个带默认值的序列化字段：

```kotlin
val steps: List<MessageStep> = emptyList()
```

旧消息、旧备份以及导出的旧 JSON 在导入时解码为 `emptyList()`，等价于“一个隐式 step”。

```kotlin
@Serializable
data class MessageStep(
    val id: Uuid = Uuid.random(),
    val status: StepStatus,
    // 本步在 message.parts 里的范围，左闭右开
    val startPartIndex: Int,
    val endPartIndex: Int,
    val attempts: List<MessageAttempt> = emptyList(),
    val failureCode: String? = null,
    val startedAt: Instant,
    val finishedAt: Instant? = null,
)

@Serializable
data class MessageAttempt(
    val attempt: Int,
    val maxRetries: Int,
    val code: String? = null,
    val message: String? = null,
    val delayMs: Long = 0,
    val durationMs: Long = 0,
    val recordedAt: Instant,
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

`UIMessagePart.Tool` 增加一个带默认值的字段：

```kotlin
val callComplete: Boolean = true
```

`ToolCallStart` 创建时置 false；`ToolCallEnd` 按 `toolCallId` 找到对应 part 置 true；`Tool.merge` 对 `callComplete` 取逻辑或，旧数据默认 true；`AgentLoop.executeTools` 只执行 `!isExecuted && callComplete` 的工具。结算时删除 `callComplete = false` 的 Tool part。它比往 metadata 写标记可靠，不会被后续 delta 的 metadata 覆盖。

约定：

- `steps` 只在生成与 step 生命周期维护时追加、收尾或删除（手动重试删除失败 step 及其后的记录）；用户编辑消息会创建新的候选 `UIMessage`，新消息没有 steps，按单 step 处理。
- 新的失败 turn 至少有一条 step 记录；空 steps 加 `FAILED` 只可能是旧 C58 消息、压缩失败回复（由 `withoutCompressionScratch` 先行移除）、或 step 开始前就失败的 turn，这些都没有可用的工具事实，按整条丢弃处理。
- `attempts` 只存摘要，最多 20 条，由共享常量 `MAX_RECORDED_ATTEMPTS = 20` 定义在 `ai/ui/Message.kt`，`ConversationSession.retryAttempts` 也改用这个常量；不存流内容。`recordedAt`、`delayMs`、`durationMs` 供历史展示；C76 的实时倒计时继续用 `ConversationSession.retryAttempts` 里的 `recordedAtMillis`。
- 时间字段用 `Instant`，与 `UIMessagePart.Tool` 的现有约定一致。
- 消息 `status` 仍表示整轮结果：最后一步成功为 `COMPLETE`，最后一步失败为 `FAILED`，用户取消为 `INTERRUPTED`；审批暂停时消息是 `COMPLETE`，step 保持 `TOOLS`。
- `usage`、`generationTimeMs`、`stepCount` 继续按整轮累计；`lastUsage` 保持“最后一次请求自己的 usage”，不是累计值。

### 4.2 字段语义映射

| 字段 | 旧含义 | 新含义 | 迁移 |
| --- | --- | --- | --- |
| `status` | 整轮结果；`FAILED` 整条不进模型 | 整轮结果不变；请求投影里 `FAILED` 的 Tool parts 可见，正文与推理丢弃；摘要投影整条丢弃 | 读时投影，不改数据 |
| `steps` | 无 | 本轮的 step 提交记录 | 旧 JSON 默认空，按单 step 处理 |
| `callComplete` | 无 | Tool 调用是否收到 `ToolCallEnd` | 旧 JSON 默认 true |
| `usage` / `generationTimeMs` / `stepCount` | 整轮累计 | 不变 | 无 |
| `lastUsage` | 最后一次请求的 usage | 不变 | 无 |
| `isGenerating` | 生成中标记 | 不变；加载时结合消息状态做恢复 | 无 |

没有需要重命名的字段；`status` 的差异只体现在读取投影。手动重试把消息状态从 `FAILED` 复位为 `COMPLETE` 是刻意的新入口，`markReplyStatus` 本身仍保持 C58 的单向语义。

### 4.3 step 的 part 范围

- 范围以最终持久化的 `message.parts` 为准。preview 的 `visualTransform` 结果会经 `AgentEvent.MessageUpdated` 写进会话并落库，取消或进程死亡时没有 `finishStep`，落库的正是 preview parts。
- step 开始时 `startPartIndex = parts.size`。
- 每次 preview、`AgentHooks.finishStep`、工具结果写入之后，由产生该消息的一方（`previewStep`、`finishStep` 或结算路径）把当前 step 的 `endPartIndex` 重算为持久化消息的 `parts.size`。
- 输出变换可以修改本步新增的 parts（例如 ThinkTag 插入 Reasoning），重算即可；不得修改 `startPartIndex` 之前的 parts，调试构建断言。
- 终态失败、用户取消、进程死亡加载这三种没有 `finishStep` 的路径，在结算或恢复时把保存下来的 step 范围重算为 `parts.size`，再关闭 step。
- AgentLoop 在失败或取消的最终更新里必须使用最后一次 attempt 的 parts，不能使用 `stepStart` 的 base 消息，否则会抹掉最后一次 attempt 已经收到的半截内容。
- step 记录在 step 开始时追加到消息；自动重试从 `stepStart` 重建 parts，但保留当前 step 记录与已经累计的 attempts，不重新生成 step id。
- “用户编辑消息”仍然新建候选消息，不做原位 parts 编辑；step 生命周期内的追加、截断（手动重试）和状态变更不算编辑，允许原位进行。

## 5. step 生命周期与提交规则

| 阶段 | step 状态 | 消息状态 | 写入 |
| --- | --- | --- | --- |
| 发起模型请求 | RUNNING | `isGenerating = true` | step 记录与 parts 更新，preview |
| 收到正文/推理/工具调用 | RUNNING | `isGenerating = true` | parts 追加，preview |
| 模型成功且没有工具调用 | SUCCEEDED | COMPLETE | 直接结束本轮 |
| 模型成功且有工具调用 | TOOLS | `isGenerating = true` | 提交文本、推理、工具调用，开始执行 |
| 工具等待审批 | TOOLS | COMPLETE | turn 以 `AwaitingApproval` 结束，等待批准或拒绝 |
| 工具全部完成 | SUCCEEDED | `isGenerating = true` | 追加结果，进入下一步 |
| 达到步数上限 | 保持 SUCCEEDED | COMPLETE | 本轮结束 |
| 模型请求最终失败 | FAILED | FAILED | 最后一次 attempt 的 parts（先过 finishStep 输出变换），attempts 与 usage 记录，收口工具 |
| 用户取消（生成中） | CANCELLED | INTERRUPTED | 目标节点的已交付前缀与已完成工具结果，收口工具 |
| 用户取消（审批等待，无 job） | CANCELLED | INTERRUPTED | 收口 Pending 工具，清理 pendingApprovalTurn |
| 进程死亡 | 只关闭可恢复的非终态 step | INTERRUPTED | 加载时扫描非终态 step，跳过审批暂停，收口工具并重算范围 |
| 审批等待时离开页面 | 保持 TOOLS | COMPLETE | 不取消，批准或拒绝仍可继续 |

补充规则：

- 审批等待时 UI 不显示停止按钮。服务层入口里，Web 的 `POST /api/conversations/{id}/stop` 仍可到达；前台服务超时在审批等待时不可达，因为 turn job 结束时已经 release。Web 停止按“用户取消（审批等待）”结算：收口 Pending 工具、清理 pendingApprovalTurn、把目标 step 置 CANCELLED、消息置 INTERRUPTED。
- 结算有明确的目标：`runTurn` 内部计算 `replyNodeId` / `replyMessageId`，所有结算只作用于这个节点与消息。重新生成中间 assistant 节点时，目标就是被重新生成的那个节点，不是会话末尾。审批恢复只可能出现在最后节点；没有 job 的审批等待停止，以当前最后一条 assistant 消息为准。
- `runTurn` 开头不再对带 steps 的消息做结算；`resumeApproval = true` 时对目标节点完全跳过恢复。没有 steps 的旧消息继续走 `withoutUnresumableToolCalls`，但 `resumeApproval = true` 时同样跳过目标节点。
- 审批只允许最后节点，也不允许中间节点的 turn 进入 `AwaitingApproval`：重新生成中间 assistant 节点时，如果该轮出现需要审批的调用，AgentLoop 在请求审批前检查本轮回复节点是否为最后节点，不是就把这些工具直接标记为 `not_started` 并结束本轮，不挂起等待审批。`isAwaitingToolApproval` 只看最后一条 assistant 的 Pending 工具，非末条 Pending 不计入阻塞，避免队列与压缩门永久等待。中间节点 turn 不会留下 Pending 工具，也就不存在审批死锁。
- 空回复按 `EMPTY_RESPONSE` 进入重试；重试耗尽时 step FAILED，可能没有任何 parts，消息 FAILED。
- 工具执行失败不终结 step：错误作为工具结果返回模型，循环继续；工具超时与中断按 C65 的 `not_started` / `outcome_unknown` 收尾。
- 审批暂停定义为可恢复的 TOOLS：`isGenerating` 为 false、消息 COMPLETE，加载时保留 Pending 工具；同一进程内 C60 继续使用冻结的 turn 上下文，进程重启后审批恢复按现有逻辑重建 turn 上下文。
- 结算只在两种终态触发：终态失败、用户取消。`runTurn` 的 NonCancellable 结算块先判断本轮结算原因，`AwaitingApproval` 不进入该块；取消时关闭目标 step 并 `settleToolParts`，再 `finishGeneration`。有 job 的 `stopGeneration` 只 cancel 并 join，不重复结算；没有 job 的审批等待停止由 `stopGeneration` 关闭最后节点并清理 `pendingApprovalTurn`，操作幂等（step 已终态则跳过）。加载恢复扫描非终态 step，跳过审批暂停的 TOOLS。
- 终态失败时，AgentLoop 把最后一次 attempt 提升到循环外，用它的 parts 过 `finishStep` 输出变换，再发一次 `MessageUpdated` 落库后抛错；`usage = base.usage + 本次 attempt 的 usage`，`generationTimeMs` 累加，`lastUsage` 取本次 attempt，`stepCount` 加一。
- `settleToolParts` 覆盖 Auto、Pending、Approved、Answered、Denied 五种状态；`callComplete = false` 的半截调用直接删除。删除只发生在目标消息的最后一个非终态 step 内部，之前的 step 范围不受影响，随后重算该 step 的 `endPartIndex`。
- `callComplete = false` 的 Tool 在 `finishStep` 时就地删除；AgentLoop 主循环的待工具判断、审批判断和 `executeTools` 都以 `isExecuted && callComplete` 为准，避免半截调用被标记 Pending 或造成无挂起点忙循环。
- 审批冲突契约：`handleToolApproval` 返回 `ApprovalResult`（Accepted / Conflict / Stale）；ChatVM、浮动窗口与 Web 路由按 Conflict 显示提示或 409，不抛到全局错误。
- 首 token 前取消：AgentLoop 在 step 开始、发起请求前发一次 step-start 的 `MessageUpdated`，确保消息与 step 记录先存在，取消时结算有真实目标。
- 取消时未派发的完整调用补 `not_started`；已派发但没拿到结果的补 `outcome_unknown`；`ServerTool` 里仍是 `IN_PROGRESS` 的调用在请求与摘要投影中丢弃，已经 COMPLETED 或 FAILED 的按第 6 节保留。
- 进程死亡时只关闭 RUNNING 或 TOOLS 的 step；审批暂停的 TOOLS（消息 COMPLETE、仍有 Pending 工具）保持原样；最后一步已经 SUCCEEDED 的保持成功，消息仍标 INTERRUPTED，表示这一轮没有正常走完。
- 每条消息仍由现有保存循环落库；step 的收尾更新在同一个节点行内完成，不新增行。

## 6. 模型投影

两个投影函数按 step 状态分别定义，替换原来只有一个 `filterModelVisible()` 的局面。

### 6.1 projectForRequest

| step 状态 | 保留内容 |
| --- | --- |
| SUCCEEDED | 全部 parts，`ServerTool` 只保留 COMPLETED/FAILED |
| FAILED | 已执行 Tool parts 与已完成的 ServerTool，正文与推理丢弃 |
| CANCELLED | 已交付正文、推理，已执行 Tool parts，已完成的 ServerTool |
| TOOLS | 正文、推理，已执行 Tool parts 与已完成的 ServerTool；未执行调用与 IN_PROGRESS ServerTool 丢弃 |
| RUNNING | 按 CANCELLED 处理 |

RUNNING 会出现在当前请求里：AgentLoop 把正在生成的 assistant 消息传给 `prepareStep`，`buildStep` 会读到它。投影必须能处理这种消息，不能假设它不存在。

### 6.2 projectForSummary

| step 状态 | 保留内容 |
| --- | --- |
| SUCCEEDED | 正文、推理、已完成的 ServerTool，以及已执行的 Tool parts（保持 C58 的现状） |
| FAILED | 完全丢弃，包括 Tool parts 与 ServerTool |
| CANCELLED | 已交付正文、推理、已完成的 ServerTool，以及已执行的 Tool parts |
| TOOLS | 正文、推理、已完成的 ServerTool，以及已执行的 Tool parts |
| RUNNING | 按 CANCELLED 处理 |

规则：

- 没有 steps 的旧消息：`FAILED` 整条丢弃；`INTERRUPTED` 与 `COMPLETE` 原样，但请求与摘要投影里 `IN_PROGRESS` 的 `ServerTool` 都要丢弃。旧 `FAILED` 是兼容例外，不套用单 step 的 Tool 保留规则。
- 空 steps 加 `FAILED` 的新 turn 只可能是 step 开始前失败，同样整条丢弃；新的失败 turn 只要开始过 step 就有记录。
- 投影后 `parts.isEmpty()` 的消息直接丢弃；判断用 `parts.isEmpty()`，不能用 `isBlank()` 或 `isEmptyUIMessage()`，因为历史截断标记是 `parts=[Text("")]` 的空文本消息，必须保留为 `limitContext` 的边界。
- `isContextCheckpoint` 与空文本截断标记不受丢空消息影响，始终保留。
- 投影顺序之后仍接 `withoutCompressionScratch()`（C68），再 `limitContext`，再输入变换，最后 `mergeAdjacentUserMessages`。
- 失败步骤的 Tool parts 缺结果时先 `settleToolParts` 再投影，保证配对。
- 多 step 消息的 FAILED 口径：请求投影按每步状态拼接，前面 SUCCEEDED step 的正文与工具事实保留，失败 step 只留 Tool parts；摘要投影与 `conversation_search` 对整条 `status = FAILED` 的消息丢弃，不再逐 step 判断。
- `conversation_search` 的工具边界过滤：结果可能跨多个会话与助手，必须先按结果里的 `conversationId` 分组再查状态，不能只按当前会话建映射。`ConversationTools` 对结果里出现的 `conversationId` 逐个取该会话的当前消息，建立 `messageId -> status` 映射（按会话缓存到本次工具调用结束），snippet 所属消息 `status = FAILED`、node 已删或分支已切走时整条丢弃；不放在共享 `ConversationRepository.searchMessages` 里，UI 与 Web 搜索仍能看到失败正文。多 step 消息只要最后一步失败就整条不返回。

各调用方的投影选择：

| 调用方 | 投影 | 理由 |
| --- | --- | --- |
| `ChatRequestFactory.buildStep` | `projectForRequest` | 失败 step 的工具事实要进请求 |
| `ContextPressure` 水位估算 | `projectForRequest` | 失败 step 的工具结果也占 token |
| `CompressionPlan` token 估算 | `projectForRequest` | 与实际压缩请求同一投影与同一窗口 |
| `ConversationMetaGenerator` 标题 | `projectForSummary` | 失败回复不参与标题 |
| `ConversationTools` 的 `dialogueMessages` | `projectForSummary` | 失败回复不提供给 `read_conversations` |
| `ConversationTools` 的 `conversation_search` | 工具边界按 status 过滤 | 失败正文不进入模型上下文，UI 搜索不受影响 |
| Web DTO | 不投影，新增 `status` 与 `steps` 字段 | 给人看的内容保留 |
| FTS、Markdown 导出、浮动窗口 | 不投影 | FTS 没有新增字段；导出与预览仍按消息处理 |

## 7. 上下文与压缩

- `contextMessageLimit` 按消息条数计数（user 与 assistant 都算），不按 turn 计数；一个 assistant 消息承载一轮的多个 step，消息条数与今天一致。
- `buildStep` 的顺序固定为：`projectForRequest`、丢弃 `parts.isEmpty()` 的消息（检查点与截断标记除外）、`withoutCompressionScratch`、`limitContext`、输入变换、`mergeAdjacentUserMessages`。
- 失败消息在投影后仍保留时，会占用 `contextMessageLimit` 的名额；纯文本失败的 step 投影为空后整条消息丢弃，不占名额。
- `contextLimitBoundaryIndex` 的判据：limit 为 0、原始 `currentMessages` 里存在压缩提示词、或没有发生截断时返回 null；压缩提示词的检查必须在 `withoutCompressionScratch` 之前做，否则提示词已经被删掉，判据永远不会成立。发生截断的判据是窗口起点严格晚于投影列表的尾部起点（检查点之后的第一条），等价于 `contextLimitStart(limit) > tailStart`；如果前面有被投影丢弃的消息，不能用节点下标和 `contextWindowStart()` 比较；先在消息列表空间判定截断，再按 id 映射回节点空间。顺序是先请求投影与丢空，再 `withoutCompressionScratch`，再执行“最后一条是 assistant 时先去掉它”，再 `contextLimitStart`，再按消息 id 映射回节点下标，映射不到返回 null。
- `keepRecent`、检查点、`planHistoryTruncation` 仍按 `messageNodes` 计数，不改；一个 turn 仍然只有一条 assistant 消息，压缩不会从 turn 中间切开。
- `CompressionPlan` 的估算窗口改为与 `buildStep` 相同：先投影并丢弃空消息，再 `withoutCompressionScratch`，再 `limitContext`。
- 压缩请求由 `buildStep` 构建，与估算共用 `projectForRequest`；摘要内容可能包含失败 step 的工具结果，但不包含失败正文。

## 8. 重试

- 自动重试恢复，但只接在聊天轮次上：`ChatRequestFactory.createAgent` 使用 `settings.retryPolicy()`；压缩摘要与记忆整理显式传 `RetryPolicy.NONE`；标题生成本来没有 `retry` 参数，不涉及。
- 默认策略沿用 RikkaHub 现有实现：默认 3 次，基础间隔 1s/2s/4s，10% 抖动，`Retry-After` 用 `maxDelay` 截断，码表与 C61 一致。DSH 的 5 次与 500ms 作为以后可调的候选，不在本次改变。
- attempt 由 AgentLoop 在每次重试时构造：`RetryAttempt` 增加 `code` 与 `durationMs`，`code` 从 `LlmFailureCode` 或异常映射得出，耗时按一次请求的起止计算；AgentLoop 在 step 结束时把 attempts 写进消息并发出最后一次 `MessageUpdated`，ChatService 负责落库；重试耗尽时同样先把带 FAILED step 与 attempts 的消息落库再抛错，最终消息使用最后一次 attempt 的 parts。
- attempt 记录最多 20 条；C76 历史卡片从 `MessageStep.attempts` 读 `attempt`、`maxRetries`、`code`、`delayMs`、`durationMs`，实时倒计时继续用会话内存 `retryAttempts` 的 `recordedAtMillis`。
- 每次重试开始时，preview 只重建当前 step 的 part 范围，之前的 step parts 不参与重置；消息 id 与 step id 在整个重试期间保持不变。
- 重试成功：本 step 提交成功，之前的 step 不动；失败 attempt 只留摘要。
- 重试耗尽：本 step FAILED，消息 FAILED；下一轮请求保留之前 SUCCEEDED step 的内容与工具事实，失败 step 只保留 Tool parts，正文与推理丢弃；摘要与 `conversation_search` 按整条消息丢弃。
- 手动 step 重试不通过错误卡片：C77 已永久取消，错误卡片只做信息提示。手动重试如果需要，入口放在消息操作或独立面板；`ChatError` 不增加 retry 字段。具体入口未定，落地前重新设计。
- 手动重试入口未定；若落地，目标节点必须仍是最后一个节点，目标消息仍是该节点当前候选，目标 step 仍存在；任一不满足就按陈旧处理。会话 `updateAt` 只用于辅助判断，不作为唯一判据。
- 重新生成整轮仍然新建候选消息；手动 step 重试不新建候选，这是 step 生命周期内的原位更新，并对消息状态做一次显式复位。
- 错误卡片没有重试语义，不再需要目标校验；手动 step 重试如果后续落地，再单独定义入口、目标与陈旧处理。

## 9. 会话操作与消费方

- 重新生成整轮：现有行为不变，创建新的候选 `UIMessage`，steps 为空，按单 step 生成。
- 编辑消息：现有行为不变，新候选 `UIMessage` 没有 steps。
- 删除消息：现有行为不变。
- 分支切换：现有行为不变；steps 跟随各自的候选消息。
- 搜索：FTS 仍按消息索引，索引里可能仍有失败正文；UI 与 Web 搜索保持现状，`conversation_search` 在工具边界整条过滤 `FAILED` 消息。
- 搜索跳转：仍按节点下标，消息数不变。
- Web/SSE：`MessageDto` 新增可选的 `status` 与 `steps`；`MessageDto.parts` 直接序列化 `UIMessagePart`，`callComplete` 会自动出现在 Web JSON 里，不需要额外字段。Web 的删除、分支、重新生成仍按节点操作；SSE 的 `node_update` 机制不变。
- 浮动窗口、Markdown 导出、长图、通知：仍按消息处理，不按 step 拆分。
- 官方兼容导出：行结构与 JSON 字段保留，`OfficialDatabaseAdapter` 的附件路径改写不变；`steps` 作为 `UIMessage` 的未知字段，容忍路径与现有 `status`、`isGenerating` 相同。上游 `ref` 的 `JsonInstant` 已经开了 `ignoreUnknownKeys`，验收仍要用官方 3.1.1 恢复普通消息与带 steps 的消息，样本记入 `docs/custom/backup/acceptance.md`。
- 官方兜底：如果官方客户端拒绝未知字段，不只会是 `steps`，`status`、`isGenerating`、`isCompressionPrompt` 这些分叉字段同样会被拒绝。兜底方案是在 `OfficialDatabaseAdapter` 对 `messages` JSON 做一次投影，剔除所有官方不认识的字段（至少 `status`、`isGenerating`、`isCompressionPrompt`、`steps`，以及 Tool part 里嵌套的 `callComplete`），保留 JSON 树里的官方未知键；字段清单按本分支对 `UIMessage` 与 `UIMessagePart` 的新增字段派生，不靠手工记忆；导入侧的 raw copy 不变量不改变，导出侧说明写进 C18 的登记与 `docs/custom/backup/acceptance.md`。
- 错误卡片（C71）：`ChatErrorStore` 的私有 `StoredError` 也要加 `retryNodeId`、`retryStepId`，旧 `chat-errors.json` 解码为 null，按陈旧卡片处理。
- 消息队列（C70）：只存 user 消息，不受 steps 影响。
- 进程死亡（C67）：只处理 `isGenerating = true` 的消息；审批暂停的消息不参与。
- 用量（C80）：仍按每次模型请求记录。

## 10. 持久化、迁移与降级

- 不新增 Room 表或列，不改 `Settings`；`message_node.messages` 的 JSON 多出带默认值的 `steps`，`UIMessagePart.Tool` 多出带默认值的 `callComplete`。
- 旧会话解码后 `steps` 为空，按单 step 投影；旧 `FAILED` 消息整条丢弃是兼容例外。
- 旧构建解码新会话同样得到空 steps，`callComplete` 回落到默认 true；旧构建保存时会把 steps、attempts 丢掉，并把半截 ToolCall 当成完整调用结算。这不会丢正文，但会丢 step 状态与重试目标，属于降级限制，写进 `docs/custom/README.md` 的兼容说明，并在同一批次更新。
- 旧构建测试方法：保留 B 落地前的最后一个 debug APK，versionName 形如 `3.1.1-custom.3349+f48eea2`，在设备上用 `adb install -r -d` 允许降级安装（或先卸载再装），打开含 steps 的会话再保存，确认正文可读、steps 丢失但不崩；JVM 侧用不带 `steps` 的旧 DTO 反序列化新 JSON 再编码，固定字段损失行为。
- 回滚：代码回滚即可，数据没有格式破坏。

## 11. 与现有定制的关系

- C47：`contextLimitBoundaryIndex` 的截断判据与投影顺序更新，`ConversationContextLimitTest` 同步。
- C58：`filterModelVisible()` 拆成请求投影与摘要投影；失败消息不再整条从请求里消失，正文仍然只给人看；旧 FAILED 消息保持整条丢弃；`markReplyStatus` 保持单向，手动重试的复位走独立入口。
- C61/C75：重试恢复；C61 的码表与退避实现保留，C75 的设置入口恢复，仅聊天轮次生效。
- C65：工具恢复继续使用；`settleToolParts` 覆盖 Auto/Pending/Approved/Answered/Denied，并删除半截调用。
- C60：审批冻结只作用于最后节点；中间节点 turn 不进入 AwaitingApproval；`handleToolApproval` 新增 Accepted/Conflict/Stale 返回。
- C67：只恢复可恢复的非终态 step；审批暂停保持 TOOLS；取消审批等待时由 runTurn 的 NonCancellable 结算或 stopGeneration 的无 job 分支关闭目标 step。
- C68：压缩提示词与回复仍是独立消息，投影后继续走 `withoutCompressionScratch`。
- C70：消息队列不受影响。
- C71：错误卡片存储增加 step 目标字段。
- C72：`contextMessageLimit` 仍按消息条数。
- C74：`ContextPressure` 按 token 水位，投影改为请求投影，不按条数。
- C76：attempt 卡片使用 step 里的 attempts 与 maxRetries；实时倒计时仍用会话内存。
- C77：永久取消，错误卡片只做信息提示。
- C80：用量仍按请求记录。
- C63/C73：保持撤销。
- 新增 C97：step 级提交与失败投影（B 项），索引行与明细在本批次登记；C18、C47、C58、C60、C61、C65、C67、C68、C70、C71、C72、C74、C75、C76、C80、C97 的明细同步更新；C77 永久取消。

## 12. 实施阶段

1. 模型与投影：`steps`、`callComplete`、两个投影函数、空消息与检查点规则、序列化与旧数据测试。
2. 生命周期与结算：step 记录、范围重算、失败/取消/审批/进程死亡的收尾、工具事实保留；仍不恢复自动重试。
3. 重试恢复：聊天轮次接回 `RetryPolicy`、C75 设置入口、C76 attempt 卡片；手动 step 重试入口待定；C77 不再恢复。
4. 消费方与验收：Web DTO、搜索过滤、导出与官方兼容验收、设备验收、`docs/custom/README.md`（文档表补 design/ 一行与降级说明）、`docs/custom/backup/importer.md`（导入 raw copy 不变量保持）、`docs/custom/backup/acceptance.md`（官方恢复样本）、`docs/custom/checklist.md`（含上节列出的 C 项与 C97 明细）、`docs/custom/roadmap.md` 同步。

阶段 1 到 4 作为一个批次推送，不发布中间态。

## 13. 测试与验收

- 序列化：旧 JSON 默认空 steps 与 `callComplete=true`；新 JSON 往返；attempts 截断到 20 条；`maxRetries` 与时间字段往返；StepStatus 的小写序列名。
- 投影：成功 step 全保留；失败 step 只留 Tool；纯文本失败投影为空后整条丢弃；取消 step 保留前缀；旧 FAILED 消息整条丢弃且不套用单 step 规则；空 steps 加 FAILED 的新 turn 整条丢弃；压缩失败留下的空 steps 回复先由 `withoutCompressionScratch` 移除；相邻 user 在空消息丢弃后仍能合并；空文本截断标记不被丢弃；TOOLS step 的请求与摘要投影；RUNNING 出现在当前请求时的投影；多 step FAILED 消息的 `conversation_search` 过滤且 UI 搜索仍可见；跨多个会话的 `conversation_search` 结果都按各自会话状态过滤；`ServerTool` 的 COMPLETED 与 IN_PROGRESS 在每张投影表下的处理。
- 生命周期：模型成功无工具直接完成；空回复重试耗尽；工具失败继续；审批暂停与恢复；审批恢复不被 runTurn 开头结算破坏；审批暂停后进程重启仍可恢复；重新生成中间 assistant 节点时出现需要审批的调用，turn 结束直接结算为 not_started、不进入 AwaitingApproval；Web（`/api/conversations/{id}/stop`）在审批等待时停止，step 关闭为 CANCELLED、消息 INTERRUPTED、pendingApprovalTurn 清理；前台服务超时在审批等待时不可达；重新生成中间 assistant 节点后停止，结算命中该节点而不是会话末尾；用户取消；工具执行一半；首 token 前取消仍有 step 可结算；生成中进程死亡恢复；跨 step 死亡时最后一个 SUCCEEDED 保持成功；终态失败时 Auto/Approved/Answered 工具收口；半截 ToolCall 被删除；`ToolCallEnd` 按 id 置位、`Tool.merge` 单调合并、`executeTools` 与主循环都过滤未完成调用；`handleToolApproval` 的 Accepted/Conflict/Stale 三种返回；输出变换插入 Reasoning 后 step 范围重算正确；取消时 preview 的视觉变换与 Reasoning 插入同时发生仍正确；最终失败消息保留最后一次 attempt 的 partial parts。
- 重试：只在聊天轮次生效；压缩与记忆传 NONE；attempt 摘要与 maxRetries；手动重试从失败 step 继续到本轮结束且拿到新 maxSteps；手动重试入口待定，不做错误卡片重试；`stepCount` 继续累计。
- 上下文：失败消息占用与不占用 `contextMessageLimit` 两种路径；边界下标分三组断言：没有截断时返回 null，有压缩 scratch 时返回 null，窗口前有被丢弃消息但确实发生截断时返回映射后的节点下标；截断时与请求投影一致；最后一条 assistant 去掉与丢空消息的先后顺序；`keepRecent` 不变的压缩计划。
- 压缩：估算与请求使用同一投影、同一窗口的用例。
- 消费方：Web DTO、FTS、搜索跳转、`conversation_search` 不返回失败正文、浮动窗口、Markdown 导出、官方兼容导出（普通消息空 steps 与带 steps 两种样本）。
- 降级：旧构建解码新会话；旧构建保存后 steps 丢失的已知限制。
- 设备：多 step 的生成、失败重试、取消、重启恢复、压缩、导出、官方恢复；带 steps 的官方兼容备份恢复。

## 14. 开放问题

1. 官方客户端对分叉字段的容忍需要官方 3.1.1 恢复验证；兜底方案已给出。

已定的实现约束：

- 审批等待的 UI 不提供停止，Web 的停止入口保留并按 CANCELLED/INTERRUPTED 结算；前台服务超时在审批等待时不可达。
- 手动 step 重试在同一个节点、同一条消息上原地重跑，不新建分支。
- attempt 最多保留 20 条，不存流内容。
- 压缩、记忆不参与自动重试。
- 官方导出不合并节点；兜底只剔除官方不认识的字段。

## 15. 合并注意

本次会改 `ai/` 的这些位置：`UIMessage` 增加 `steps` 与 `callComplete`（`ai/ui/Message.kt`、`ai/ui/UIMessagePart.kt`）、两个投影函数放在 `ai/ui/Message.kt`、`StreamChunkHandler` 维护 `callComplete`（`ai/ui/StreamChunkHandler.kt`）、`AgentLoop` 增加 step 记录、范围重算与最终结算消息（`ai/agent/AgentLoop.kt`）、`RetryAttempt` 增加 `code` 与 `durationMs`（`ai/agent/Retry.kt`），如果结算需要新的 hook 通知，还要改 `AgentHooks`（`ai/agent/Agent.kt`）。`settleToolParts` 放在 `app/` 的 `ConversationMessages.kt`，与现有工具恢复函数同文件，由 `ChatService`、取消收尾和加载恢复调用。移植上游改动时保留这些差异并按 C 项登记；`ai/` 里 C3/C96 的删除、C61 的失败分类、C65 的声明超时继续按原有约定移植。

## 16. 参考

- DSH：`dsh-v0.2.2-rc.1`，commit `66491b05e3f05ad35bcdd4f6cd7ed26041d9e3db`；`packages/core/session/src/types.ts`、`packages/core/agent-loop/src/agent.ts`、`packages/core/agent-loop/README.md`、`packages/core/session/README.md`、`packages/llm/llm/src/retry-policy.ts`。
- 本仓库：`ai/src/main/java/me/rerere/ai/ui/Message.kt`、`ai/src/main/java/me/rerere/ai/ui/UIMessagePart.kt`、`ai/src/main/java/me/rerere/ai/ui/StreamChunkHandler.kt`、`ai/src/main/java/me/rerere/ai/agent/AgentLoop.kt`、`ai/src/main/java/me/rerere/ai/agent/Retry.kt`、`ai/src/main/java/me/rerere/ai/agent/Agent.kt`、`app/src/main/java/me/rerere/rikkahub/data/repository/ConversationRepository.kt`、`app/src/main/java/me/rerere/rikkahub/data/db/entity/MessageNodeEntity.kt`、`app/src/main/java/me/rerere/rikkahub/service/ConversationSession.kt`、`app/src/main/java/me/rerere/rikkahub/service/ChatErrorStore.kt`、`app/src/main/java/me/rerere/rikkahub/data/ai/ChatRequestFactory.kt`、`app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt`、`app/src/main/java/me/rerere/rikkahub/data/model/ConversationMessages.kt`、`docs/custom/checklist.md` 的 C47、C58、C61、C65、C67、C68、C70、C71、C72、C74、C75、C76、C80；C77 永久取消。
