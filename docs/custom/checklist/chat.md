# 明细：聊天、上下文与模型

本文件是 [checklist.md](../checklist.md) 的明细，收录 C35、C43 至 C44、C46 至 C47、C49 至 C53、C57 至 C63、C65 至 C78、C81 至 C86、C97 至 C100、C107、C121、C122、C130、C131、C133、C134、C135、C137、C140、C141、C145、C147、C152。

<a id="c35"></a>

## C35 模型选择支持多关键词搜索

- 来源：上游 `5ad19405`（`feat(model): 模型选择面板支持多关键词搜索`），代码与该提交一致；`ModelFilterTest` 六条用例与 `ref` 里的那份相同，本分支相对它的差异只有 KDoc 与一个空行。
- 落点：`core/ai/src/main/java/me/rerere/ai/provider/Model.kt` 加 `List<Model>.filterByKeywords`（按空格拆关键词、忽略大小写、每个关键词命中 `modelId` 或 `displayName` 的一部分、不要求顺序，空查询返回原列表）；`ModelList.kt` 的聊天模型面板与 `SettingProviderDetailPage.kt` 的可用模型面板共用这一条规则；`ModelFilterTest` 六条 JVM 用例（与上游该提交一致）。
- 理由：聊天面板原来整串匹配且只看 `displayName`，多词或按模型 ID 搜不到；提供商详情的面板已经按空格拆词并同时匹配 `modelId` 与 `displayName`。这次抽成共用函数，两边用同一条规则，聊天面板也因此能按模型 ID 搜。
- 移植注意：`filterByKeywords` 在 `core/ai/`，该模块仍尽量跟随上游；以后跟随上游对 `Model.kt` 的改动时保留这个函数，两个 UI 调用点在 `app/`，合并成本让步已不适用。上游后续如果再改这块，按本行核对。

<a id="c43"></a>

## C43 错误卡片默认保留到手动关闭

- 落点：`DisplaySetting` 新增 `autoDismissErrorCards`（默认 `false`，即保留）；`ErrorCardsDisplay` 与 `ErrorCard` 增加 `autoDismiss` 参数，`LaunchedEffect` 只在开启时 `delay(5000)` 后关闭，`error.id` 与 `autoDismiss` 都进 key；`ChatList` 把 `settings.displaySetting.autoDismissErrorCards` 传进去；`SettingPreferencesUIPage` 的「显示 Token 和上下文统计」下方加一个开关；8 份 locale 新增 `setting_display_page_auto_dismiss_errors_title` 与 `_desc`。既然不再自动消失，同时把错误列表的会话口径补齐：`ChatError.conversationId` 与 `addError` 的入参都改成非空，`ChatVM.errors` 只放出当前会话的错误，压缩失败那处补上会话 id，删除会话时连带清掉它的错误；`ChatService.addError` 交给 `withNewError`（标题、会话、异常类型与消息都相同的算同一条，只留最新一份，每个会话各留 `MAX_RETAINED_ERRORS` 条），`clearErrors` 也只看当前会话，别的会话的卡片不会被清掉或挤掉；卡片栈限高 320dp 且可滚动，来了新错误滚到最新一条，「全部清除」移到最后。
- 理由：错误卡片原来固定 5 秒后收起。网络中断常常发生在用户离开手机的时候，回来看不到错误卡片，只看到半截回复，无法判断是模型断了还是自己停的。默认保留，靠卡片右侧的关闭按钮或多条时的「全部清除」收起；想恢复旧行为可以在 UI 设置里打开。卡片常驻之后，原来被 5 秒掩盖的两个问题会暴露出来：错误是应用级状态、聊天页没有按会话过滤，以及每轮自动起标题、工具能力提醒、用户重复同一个操作都会把同一类失败再报一遍，所以一起处理。新增的设置字段带默认值，改完按约定重跑了验收样本：`data.sync` 包的 51 条设备用例（含官方样本表数据、整包恢复、applied outcome）全部通过。
- 合并注意：默认值 `false` 与上游行为相反，上游改 `ErrorCard` 时要把 `autoDismiss` 参数一起合过来。会话过滤放在 `ChatVM` 而不是 `ChatList`：Web 端 SSE 与悬浮窗本来就是按 `conversationId` 过滤的，`ChatVM` 这一层补齐后聊天页与它们一致；`ChatError.conversationId` 非空，新加错误入口时不会再漏。滚动在最新一条变化时做一次（按 `errors.lastOrNull()?.id` 起效，等一帧布局完再滚），也就是来了新的一条或最新那条被关掉；关掉中间的卡片、旋转这类高度变化不再动滚动位置。这个开关只管聊天页的错误卡片，悬浮窗（`ChatFloatingContent`）用 `ChatError` 自己画状态徽标，不经过这里；`WorkspaceDetailPage` 里的 `ErrorCard(String)` 是另一个同名私有函数，不受影响。C71 起错误卡片在 add/dismiss/clear 时落盘到 `noBackupFilesDir`，启动恢复时过滤已删会话，卡片不再只活在内存里。

<a id="c44"></a>

## C44 上下文压缩的提示词可编辑

- 落点：压缩指令从 `CompressPrompt.kt` 里的硬编码文本改成 `Settings.compressPrompt`（默认 `DEFAULT_COMPRESS_PROMPT`），`buildCompressPrompt(prompt, additionalPrompt, locale)` 先用 `applyPlaceholders` 把 `{locale}` 换成系统语言，再追加本次的额外指令；`ChatService.startCompression` 的压缩轮次从设置里取模板；DataStore 侧新增 `SettingsStore.COMPRESS_PROMPT`（key `compress_prompt`）并登记到 `SETTINGS_FIELDS`，`SettingsFieldsTest` 的样本、盘上 key 集合与断言同步；`SettingModelPromptPage` 的 `PromptSlot` 增加 COMPRESS 一项（图标、变量说明、重置默认），模型设置页的「提示词」里自动多一条；压缩对话框在「额外指令」下面加一行提示基础指令的位置；8 份 locale 新增 `setting_model_page_prompt_compress`、`setting_model_page_compress_prompt_vars`、`chat_page_compress_prompt_hint` 三条文案。测试：`CompressPromptTest`（默认模板替换语言、追加额外指令、空白额外指令不追加）与 `SettingsFieldsTest`（字段契约、读写往返、盘上 key 集合）。
- 理由：总结结构不合适时只能改源码，入口只有每次压缩时的「额外指令」，一次性而且不好找。压缩用的模型、请求组装与正常对话一致，模板本身是可以按使用习惯调的，和标题、翻译、OCR 的提示词放在同一处管理更合理。新增的设置字段带默认值，改完按约定重跑了验收样本：`data.sync` 包的 51 条设备用例（含官方样本表数据、整包恢复、applied outcome）全部通过。
- 合并注意：`{locale}` 是唯一变量，默认模板与原来的硬编码内容等价（只把 Kotlin 插值换成占位符），旧设置解码时用默认值。模板不经过输入转换器（模板转换器会把花括号当模板语法），只在 `buildCompressPrompt` 里拼；压缩请求的前缀要与正常对话一致才能命中提示词缓存，所以模板只能作为末尾追加的那条 user 消息，不要塞进 `buildStep` 的转换链。`Settings` 新增字段必须同时登记到 `SETTINGS_FIELDS`，否则不会落盘：读写只走登记过的字段，`SettingsFieldsTest` 的第一条契约用例就是拦这个的。

<a id="c46"></a>

## C46 管理上下文：压缩与截断共用一个入口

- 落点：`FilesPicker` 的「压缩历史」入口改成「管理上下文」并永远显示（去掉 `contextMessageLimit <= 0` 的限制）；`CompressContextDialog` 换成 `ContextManagementDialog`，两个 tab「压缩」「截断」，两处「保留最近 N 条」都用数字输入框 + ±（压缩原来只有 ±）；新增 `ChatService.truncateHistory` 与 `ChatVM.handleTruncateHistory`，边界由 `Conversation.insertHistoryTruncation` 插入一条空的检查点消息（`isContextCheckpoint = true`，空文本会被 `isValidToUpload` 过滤，不发给模型）；`TemplateTransformer` 跳过空检查点，否则模板里的角色标签之类的字面文本会把它变成非空消息发出去；窗口起点抽成 `Conversation.contextWindowStart()`，压缩、截断与对话框都按它算（`limitContext` 在 ai 模块里按同样的规则取最后一个检查点）；`ChatMessageContextCheckpoint` 按空文本区分截断标记，只留删除（撤销）；截断面板实时显示窗口大小、保留条数与将丢弃的条数，第一条保留消息是 assistant 时给 `TriangleAlert` 警告（只提示，不阻止确认）；截断前先清掉失败压缩留下的 scratch 对，窗口按清理后的节点算，`FilesPicker` 的消息计数也不含这对；四份 locale 新增 18 条文案，删掉不再使用的 `chat_page_compress_context` 与 `chat_page_compress_context_title`。测试：`ConversationHistoryTruncationTest`（空标记的插入位置、不发给模型、`limitContext` 起点、窗口起点；锚点不存在时不插入；空检查点与摘要可区分；`Conversation.planHistoryTruncation` 的截断先清掉压缩对、keep 计数不含对、只剩对时 NothingToCut、keep 不小于窗口时 KeepTooMany）与 `TemplateTransformerTest`（空检查点不参与模板改写，摘要照常参与）。
- 理由：用户已经用正常对话让模型写好交接摘要时，再调一次模型压缩是多余的；截断零成本、零延迟，也允许用户自己挑边界。两个动作共用「保留最近 N 条」的概念，放在同一个入口更顺。空标记复用了检查点机制，没有新增序列化字段，官方兼容导出的消息 JSON 不变。
- 合并注意：截断计划抽成 `Conversation.planHistoryTruncation`，服务层只映射错误文案，测试直接打这个纯函数；截断必须先清掉压缩 scratch 对再算窗口和锚点，否则检查点会插进对中间，下一次清对只删得掉提示词；空文本是截断标记的唯一判据，这个不变式靠 `streamCompressionSummary` 里的 `ifBlank { error }` 兜底（`compressionSummaryOrNull` 本身只做 trim，单看它拦不住空串）；以后如果允许把检查点编辑成空白，语义会退化成截断，与它的效果一致。`limitContext` 只认最后一个检查点，连续截断两次时旧标记留在历史里但不再生效；在前一个压缩摘要之后做截断，那个摘要也不再是请求起点，所以删除标记的提示写的是「回到之前的边界」而不是「恢复全部历史」，旧检查点的卡片也会显示「已被更新的检查点取代」。`contextWindowStart`、`contextWindowSize` 同时给服务端与对话框用，别在两处各算一遍。Web 端已经有 `isContextCheckpoint`（WebDto），会把空标记渲染成一张空的摘要卡片并显示编辑；要处理时改 web-ui 的分支，Android 这边按空文本区分。

<a id="c47"></a>

## C47 上下文消息数上限的自动边界线

- 落点：`core/ai/ui/Message.kt` 新增 `contextLimitStart(limit)`，按与 `limitContext` 相同的取法（最后一个检查点之后、同一套滞回与 `alignContextStart` 对齐）算出第一条被保留的尾部消息下标（提供方之后还会按 `isValidToUpload` 过滤空消息），`limitContext` 本身不动；`Conversation.contextLimitBoundaryIndex(limit)` 先用 `projectForRequest()` 投影并丢掉空消息、去掉压缩 scratch，再按投影列表里的尾部起点判断是否真的截断，最后按消息 id 映射回节点下标；投影可能丢掉整条失败消息，所以不能再用节点下标和原始窗口起点直接比较。最后一条是助手回复时先去掉它再算（否则线会落到回复上方把最后一条 user 划出窗口），原始消息里有压缩提示词时返回 null。`ChatList` 用 `settings.getAssistantOf(conversation)`（与请求同一来源，助手被删也能退回当前助手）的 `contextMessageLimit` 取上限，在第一条被保留的消息上方画一条只读虚线，文案 `chat_page_context_limit_boundary`（四份 locale）说的是「请求窗口从这里开始」，因为线上方的检查点卡片（压缩摘要）仍会发送；不落盘，不加设置。测试：`MessageContextLimitTest` 五条（起点与 `limitContext` 的尾部一致、检查点之后有截断时的起点、未截断时停在检查点之后、检查点在末尾、按检查点之后算而不是按整个列表）与 `ConversationContextLimitTest` 九条（无上限或未截断时不返回下标、只有检查点边界不画线、无检查点截断时的起点、检查点之后的起点、最后一条助手回复不参与窗口、带回复时仍守检查点、带回复且不截断时不画、压缩 scratch 对不画、失败消息投影后按 id 映射回节点）。
- 理由：消息数上限只在组装请求时算，界面完全看不见；用户一直手动管理上下文，这条线能把自动被丢掉的那段标出来。它按滞回规则跳动、连续多轮不动，不破坏提示词缓存；最后一条是助手回复时按去掉它的列表算，标出产生这条回复的请求窗口。多步工具/续写轮次的最后一步请求可能包含这条助手消息，边界仍可能与那一步差一个滞回步幅；压缩 scratch 对是界面临时对，正常请求不用这对节点，压缩提示词存在时不画线。
- 合并注意：`contextLimitStart` 必须复用 `limitContext` 的取法，别另写一套条数逻辑；边界按投影后的消息 id 映射回节点定位，检查点卡片本身不算条数，也不能画在检查点上方（摘要会发送）。投影丢弃失败消息后，边界线仍落在真实节点上。上限为 0 或没有发生条数截断时不画。重新生成中间节点时请求只取到该节点之前，边界可能比该次请求多显示一段；重新生成最后一条时按去掉新回复算。Web 端不显示这条线。

<a id="c49"></a>

## C49 分支切换只写 select_index

- 落点：`MessageNodeDAO.updateSelectIndex(conversationId, nodeId, selectIndex)` 带 `conversation_id` 条件并返回受影响行数；`ConversationRepository.updateMessageSelectIndex`；`ConversationStore.updateNodeSelectIndex`；`ConversationSession.selectMessageNode(nodeId, selectIndex, expectedIncarnation)` 在写锁内只改内存，且只有最近一次成功写入后的持久化计数（`persistedMessageCounts`）显示该节点已有这个下标时才写列，否则留给下一次全量 `flush` 把消息和下标一起写；`ChatService.selectMessageNode` 改走它，保留 `dispatchNextQueuedMessage`；载入时把 `selectIndex` 夹紧到消息下标范围（`loadMessageNodes` 与 `conversationEntityToConversation` 两处）。测试：`ConversationSessionTest` 新增两条（持久化计数包含下标时只写列、随后 `flush` 不重写；计数不足时只改内存、下一次 `flush` 把消息和下标一起写）。
- 理由：分支切换原来走 `mutate` → 全量 `flush` → 删除并重插所有 `message_node`，会把巨长回复重新编码，OOM 就发生在这次编码上；切换分支实际只改一个整数列。守卫防止「节点行还是旧的分支数量」时写进越界下标（重新生成较早回复、Web 路由都能触发），否则下次加载 `currentMessage` 会抛异常。
- 合并注意：写列的前提是 `persistedMessageCounts[nodeId]` 大于目标下标（C50 之后由单节点写和全量写共同维护）；内存里有新分支但还没落库时只改内存，下一次全量写补上。写列成功后只在 `lastWritten === before` 时推进 `lastWritten`，避免把并发的未落库修改误标成已写。载入 clamp 是坏数据兜底。Web 路由共用同一个 `ChatService.selectMessageNode`。生成中定期落库见 C50。

<a id="c50"></a>

## C50 生成中定期保存回复节点

- 落点：`MessageSaveLoop` + `MessageSavePolicy`（5/15/30 秒档、64KB 增长、60 秒兜底、堆守卫 `max(16MB, 4×估算)`、估算超过 16MB 的节点跳过并每分钟记一次日志）；估算逐层累加 `JsonElement` 的 primitive 长度，不调 `toString()`；`估算字节 = 字符数 × 2 + 1MB`，是 UTF-8 的近似值（ASCII 偏保守，CJK 偏小），所以 16MB 上限是估算值，不是硬行大小；`ConversationSession.persistMessageNode` 在写锁内按追加规则写单个 `message_node`（新节点必须在末尾且前缀 id 一致，已有节点下标必须一致），副本先 `finishReasoning`，含递归 base64 图片则跳过，不推进 `lastWritten`，成功后更新 `persistedMessageCounts`；`persistedMessageCounts` 在载入、全量 flush、replace 时重建，C49 的 select_index 守卫改用它；`touchUpdateAt` 每轮第一次成功保存时同步内存与 `update_at` 列；`ConversationRepository.upsertMessageNode` 与 `ConversationDAO.updateUpdateAt`；`ChatService.runTurn` 用 `supervisorScope` 起 saver，生成结束的 `finally` 里 `NonCancellable { saver.cancelAndJoin(); finishGeneration() }`；翻译流式只改内存，结束后单节点写一次，清除翻译也走单节点写；编辑、分叉、删除在 `job != null || isAwaitingToolApproval` 时抛 `ConflictException`（Web 409），Android 页面预挡、VM 捕获。
- 理由：原来只在发送、审批、turn 结束和手动修改时全量落库；长回复生成期间崩溃会整条消失。定期保存只编码当前节点，配合 C49 把长回复的保存成本从「每次整条会话」降到「当前节点」。
- 合并注意：追加规则避免写到错误的节点下标；漂移、base64、堆不足或估算超过 16MB 时跳过，交给最终全量 flush。落库返回空（漂移/base64）和节点过大时会推进计时，堆守卫不足时只跳过本次判断、不拷贝节点。单节点写不推进 `lastWritten`，全量 flush 仍是最终校正。tick 整体兜异常，取消异常必须重抛；写失败后退避 10 秒。保存的内容可能缺少 `onGenerationFinish` 的最终转换，`modelSnapshot` 可能为空，均接受。工具输出里的 base64 图片比库的顶层检查更保守，会让该节点整个 turn 跳过定期保存，这是有意的取舍。`replyNodeId` 在连续 assistant 节点时可能指向前一个 assistant 节点，这是既有边界。发送、审批、停止、turn 结束、fork 时的全量写仍会编码巨长节点，可能 OOM；翻译结束、清除翻译的单节点写同样会编码所在节点；超过 16MB 估算的节点不做定期保存，崩溃会丢整条回复；待审批期间的压缩/截断、生成中删除会话也一样。saver 与生成协程的 `session.update` 之间没有锁，保存的是写锁内的快照，下一次全量 flush 校正。忙碌检查是 check-then-act，Android 的页面预挡是主防线，竞态下编辑输入可能丢失。Web UI 在生成中禁用按钮并提示 409 是后续项。
- 测试：`ConversationSessionTest` 40 条，新增追加、漂移、前缀不一致、计数守卫、正向分支写、base64（顶层与工具输出）、`finishReasoning`、`touchUpdateAt`、草稿跳过；`MessageSaveLoopTest` 15 条，覆盖档位（按字节而非字符）、增长、最长 60 秒、堆守卫、16MB 边界、空跳过不重试、超限日志节流、失败退避、估算异常后恢复、取消异常重抛、估算函数逐层求和。两个未自动化的缺口：saver 与 `finishGeneration` 的 join 顺序没有集成测试（靠代码审查确认），409 门禁没有 `ChatService` 层测试（Android 页面预挡与 VM 捕获已编译验证）。设备验收待做：巨长生成中杀进程后重启能看到已保存的部分。

<a id="c51"></a>

## C51 压缩走可见生成轮次

- 落点：`ChatService.startCompression` 在 `synchronized(session)` 里检查 job/待审批并设置 `compressionActive`，失败返回 `Result.failure`，job 不启前台服务；`runCompressionTurn` 先用 `Conversation.planCompression` 在插入提示词之前钉死 `messagesToSend`/锚点，再插提示词和空回复并 flush，起 per-turn saver，成功用一次 `ConversationSession.flushWith(incarnation)` 完成「检查点 + 删提示词/回复 + updateAt」，失败/取消保留这对节点并 `finishGeneration`；`UIMessage.isCompressionPrompt`（带默认值的序列化字段），`editMessage` 保留标记，`removeCompressionPair(s)` 一次删一对，`sendQueuedMessage` 前和压缩计划前排除旧 scratch，prepare 再清最新状态；`gates`：压缩期间拒绝重新生成、工具审批、分支切换和起标题；`ChatRequestFactory.streamCompressionSummary` 与正常对话同前缀（保留工具定义，C72 起摘要 `maxTokens` 按上下文预算，不再为 null），提示词最后追加，`withRetry` 包住收集、每次重试重置同一 message id、捕获 `StreamChunk.Finish.finishReason`，C72 起截断改用部分摘要并提示；`ChatVM.handleCompressContext` 改为 `startCompression` 的 Result 处理，`deleteCompressionPair`；对话框 `onCompress` 改成 `() -> Unit` 并点开始即关；`ChatList` 跳过紧随提示词的回复节点，由 `CompressionCard` 一张卡承载折叠头/展开区/停止/重试/删除，失败默认展开；`ChatPage` 串起重试（删对后重开管理上下文对话框）；四份 locale 加卡片与取消/截断文案。旧的 `compressConversation` 与 `generateCompressionSummary` 删除。
- 理由：压缩原来是一次隐藏的非流式请求，用户看不到提示词和摘要怎么生成，也不能取消；改成可见轮次后流式、可取消、错误走现有卡片，失败不截断历史。请求前缀保持不变，提示词缓存仍然命中；工具定义保留，模型返回工具调用按失败处理，不执行工具。
- 合并注意：`planCompression` 必须在插入可见提示词之前算，请求只用计划里的消息，否则提示词会被发两遍或吞掉保留消息。检查点按锚点插在保留区之前，可见提示词的位置只是 UI；成功是 prepare + finalize 两次全量写，失败是 prepare + finishGeneration 两次，旧节点仍在树里，每次都会编码保留区。压缩期间的分支切换、发送、队列派发都被挡；发送、重新生成、截断、分支切换、标题生成目前各自判断能否开始（谓词不统一，统一留到下次改 ChatService，`CompressionPlan` 的待审批/可续跑用例已补）；失败后下次真实发送或截断会先清 scratch 对，不会把提示词当成普通历史。失败/取消留下的对会标 `FAILED`，跨 reload（含 5 秒 idle 回收）保留，用户可重试/删除；进程崩溃留下的 `COMPLETE` 对由 C68 在加载时删除，不会留下检查点，也无法重试。截断后的崩溃检查点已落库。临时换模型会改变请求前缀、失去缓存命中，入口留在对话框并说明。Web/SSE 没有压缩流，快照由 C68 过滤；浮动窗口没有消息列表，只预览 `replyMessageId` 对应回复，压缩期间显示摘要正文，轮次结束就清空，不会列出临时对；导出已由 C68 过滤。
- 测试：`CompressionPlanTest` 9 条（计划范围/空范围、检查点插入与删对、缺回复的清理、普通消息不误删、截断 finish reason 归一化、待审批工具、可续跑的已批准工具、待审批工具只在保留尾部时仍 Ready）；`ConversationSessionTest` 增加 `flushWith` 两条（变换落库并推进快照、incarnation 不匹配不写）；`ChatRequestFactory` 的流式请求依赖已有 `compressionSummaryOrNull` 的守卫。设备验收待做：真实压缩的流式显示、停止、失败重试/删除、成功后的检查点；`data.sync` 51 条不受影响的确认。

<a id="c52"></a>

## C52 Claude 缓存断点按官方标准收敛

- 上游引入：`6098e1d3`（2026-02-24）先在请求顶层加 `cache_control`；`aede7f45`（2026-02-26，#877/#880）改成 system、最后一个 tool、倒数第二条真实 user 三处手动断点；`d2e52106`（2026-05-27）又在手动断点上加顶层自动缓存，一共 4 个，顶满 Anthropic 的上限，没有余量。长思考或中转再补一个就报 `A maximum of 4 blocks with cache_control may be provided. Found 5.`，这是断点顶满上限的来源。
- 落点：`ClaudeProvider` 只保留顶层自动缓存和 system 最后一个文本块的断点；没有 system 时保留最后一个工具定义的断点；删掉 `insertMessagesCacheControl` 和倒数第二条 user 的断点（这两条描述的是 STANDARD；C53 起默认 AGGRESSIVE，激进模式会恢复它们，见 C53）；回放 server tool 的 raw call/result 时剥掉 `cache_control`；构造完请求统计断点，超过 4 去掉 messages 里的断点并记日志；更新 `ClaudePromptCacheRequestTest` 断言（正常 2 个断点），修 `ClaudeRequestMessageTest`/`ClaudeServerToolTest` 里 `buildMessages` 的反射签名。
- 理由：官方 prompt caching 文档的 agent loop 推荐组合是「system 显式断点 + 顶层自动缓存」；工具在 system 之前渲染，system 断点已经覆盖 tools + system，工具断点是冗余的；倒数第二条 user 的断点也被顶层自动断点覆盖。线性对话的增量缓存仍然成立，STANDARD 下缓存写入点从 4 个减到 2 个；分支/编辑历史时中段复用会略少，静态前缀不受影响。顶层自动缓存依赖平台支持，官方当前模型支持；代理或旧平台不支持时会直接报错，这是上游原来就有的前提。
- 合并注意：这是 `ai` 模块的自定义行为改动，上游改 `ClaudeProvider` 时按这个标准合；provider 的单元测试要跑 `:ai:testDebugUnitTest`，app 的测试任务不会覆盖它们。自定义 body 如果自带 `cache_control`，护栏只保证 messages 里的断点被去掉，其它位置需要用户自己控制。设备上用长思考场景复现，400 消失；该次测试中转后台显示 cache_read=0，缓存命中是否稳定待观察。C53 把请求构造改成按 provider 切换，默认 AGGRESSIVE；C52 的两断点是 STANDARD 选项。
- 测试：`ClaudePromptCacheRequestTest` 的标准策略 7 条（自动 + system、无 system 时用 tools、无 system 且无 tools 时只有自动断点、只有一条 user 时没有 messages 断点、1h TTL 在两处、messages 永远没有断点、promptCaching=false 没有断点；C53 补激进策略 6 条后共 13 条）；`ClaudeRequestMessageTest` 7 条、`ClaudeServerToolTest` 10 条全过；`ai` 模块的测试任务通过。

<a id="c53"></a>

## C53 Claude 缓存断点按提供商切换

- 落点：`ProviderSetting.Claude.promptCacheStrategy`（`ClaudePromptCacheStrategy.STANDARD`/`AGGRESSIVE`，默认 `AGGRESSIVE`）；`ClaudeProvider.buildMessageRequest` 在 STANDARD 下是顶层自动缓存加 system（无 system 时 tools），在 AGGRESSIVE 下是顶层加 system 加 tools 加倒数第二条真实 user，回到上游 `d2e52106` 的 4 个断点；`ProviderConfigure` 在提示词缓存开关下加策略选择，选项只显示标准/激进，策略标题行右侧的 (i) 打开说明弹窗，关闭复用 `common_confirm`；四份 locale 的选项改成标准/激进，并补说明和 info 文案；C52 的 `limitCacheControl` 与 server tool raw 剥离继续生效。
- 理由：上游原来的激进策略用了很久，只在个别中转补断点时遇到一次 Found 5；默认保留激进能继续拿到更多缓存断点，遇到 400 的 provider 手动切标准即可。标准是官方推荐的两断点组合，给中转补断点留余量。策略是 provider 级，不是助手级，切换后同一 provider 的所有 Claude 请求生效。
- 合并注意：新字段带默认值，旧设置反序列化为 AGGRESSIVE，升级后与 C52 之前的行为一致；STANDARD 是 C52 的结果，AGGRESSIVE 是上游 `d2e52106` 的原始组合；两个模式都保留顶层自动缓存，STANDARD 只有在没有 system 时才给 tools 断点；改上游 ClaudeProvider 时按这个分叉合。
- 测试：`ClaudePromptCacheRequestTest` 标准策略 7 条加激进策略 6 条（默认激进 4 断点、三条 user 时只标中间一条、无 system 时 3 断点、无 system 且无 tools 时 2 断点、只有一条 user 时 3 断点、1h TTL 在四个断点），共 13 条；`ClaudeRequestMessageTest` 7 条、`ClaudeServerToolTest` 10 条仍过。

<a id="c57"></a>

## C57 请求日志开关持久化

- 落点：新增 `RequestLoggingPreference`（SharedPreferences），`RikkaHubApp.onCreate` 读取并设置 `Logging`，`LogPage` 切换时写回。
- 理由：请求日志开关原来只在 `Logging` 的内存里，重启回到关闭，容易忘记打开；日志本身仍是内存环形缓冲，重启清空。
- 合并注意：用 SharedPreferences 而不是 Settings/DataStore，这是调试开关，不进应用内备份（Android 12+ 的 Auto Backup 可能带上这个布尔值）；不要把日志内容一起持久化，体积和隐私都不合适。日志开关在 `common` 的 `Logging` 对象里，持久化留在 app 侧。
- 测试：编译通过；手动验收：开关打开后重启仍为开启，日志列表清空。

<a id="c58"></a>

## C58 失败与中断回复状态

- 落点：`UIMessage` 增加 `status`，取值为 `COMPLETE`、`INTERRUPTED`、`FAILED`，默认 `COMPLETE`；`ChatService.runTurn` 通过 `resolveSettlementStatus` 在失败时标 `FAILED`、用户停止时标 `INTERRUPTED`，由 `finishGeneration` 一起落库；`ChatRequestFactory.buildStep` 组装请求前用 `filterModelVisible()` 过滤 `FAILED`，再用 `mergeAdjacentUserMessages()` 合并所有相邻的 user，不只失败过滤造成的那对，保留前一条的 id、createdAt、isSynthetic、isContextCheckpoint，只拼 parts 和 annotations，transforms 在合并之前；`ConversationMetaGenerator` 和 `dialogueMessages()`（记忆整理、`conversation_read`）同样过滤 `FAILED`；`ChatMessageAssistantAvatar` 在模型名旁显示失败/中断标签；新增四份 locale 的 `chat_message_status_failed`、`chat_message_status_interrupted`。
- 理由：失败或停止的半截回复原来作为普通 assistant 消息保存，下一次请求会把它当成完整回复带上。失败 attempt 只应给用户看，不应进入模型历史；用户主动停止的内容用户已经看过，可以继续参与后续对话，但需要和正常完成区分。过滤失败回复后前后两条 user 会相邻，部分 provider 要求 role 交替，所以在请求列表里合并，不改会话本身。
- 合并注意：`status` 是带默认值的序列化字段，旧消息、旧备份和官方兼容导出里的旧 JSON 解码为 `COMPLETE`；这是 `core/ai/ui/Message.kt` 的分叉，移植上游 `UIMessage` 改动时保留字段以及 `isModelVisible`、`filterModelVisible`、`mergeAdjacentUserMessages`。`runTurn` 在进入 `runAgent` 收集之前失败（包括 `createContext`/`createAgent` 内部失败）时没有本轮回复可标，审批续写会保留原消息状态并只出错误卡片；此时 `replyMessageId` 可能已指向旧回复，但没有结算路径读它。已收到 `AgentFinishReason` 后发生的取消保持 `COMPLETE`。删除、替换、cleanup 的内部取消也可能短暂标 `INTERRUPTED`，随后被替换或删除；C67 起进程死亡留下的半截回复也会在加载时标 `INTERRUPTED`。`markReplyStatus` 是单向的，不会把 `FAILED` 复位成 `COMPLETE`；当前正常路径不可达，若将来出现“Pending 工具 + FAILED”的续写，需要同时处理过滤与复位。目前按整条消息标记，多步工具调用在中途失败时会整条隔离；per-step 细化已按 B1 第一阶段的请求投影落地（见 C97）；标题与摘要已按 step 投影（见 C122），记忆与 `conversation_read` 仍整条过滤失败消息；工具恢复结果（`not_started`/`outcome_unknown`、`withoutUnresumableToolCalls`、`cancelInterruptedTools`）已由 C65 处理。压缩请求在 `buildStep` 之后单独追加 compress prompt，仍是 user+user，不走这次合并，这是压缩指令独立成轮的既有设计。Web DTO 已带 status/steps，Web 聊天页也显示失败/中断标签与历史 attempt 摘要；导出图片、Markdown 导出和浮动窗口没有 status；Android 标签在头像行内，模型已删除且没有快照、又未开助手头像时看不到。`contextLimitBoundaryIndex` 已改为先请求投影再按消息 id 映射，失败消息夹在中间时边界仍落在真实节点；`contextWindowSize` 与 `planCompression` 仍按未过滤消息计算。FTS 索引仍会索引失败正文，UI 与 Web 搜索继续可见；`conversation_search` 已在工具边界按会话当前分支的消息状态过滤，失败消息、已删除节点和已切走分支的 snippet 不进入模型上下文。
- 测试：`UIMessageSerializationTest` 覆盖状态序列化、旧 JSON 默认 `COMPLETE`、过滤只去掉 `FAILED`；`MessageMergeTest` 覆盖相邻 user 合并和过滤失败回复后的合并；`ConversationMessagesTest` 覆盖状态只改目标消息、无变化返回 null；`SettlementStatusTest` 覆盖完成、失败、取消和完成后再取消；`ConversationSessionTest` 覆盖 `markReplyStatus`；`DialogueMessagesTest` 覆盖失败回复不进入记忆和 `conversation_read`；`ConversationRecentModelMessagesTest` 覆盖标题上下文过滤；`SearchVisibilityTest` 覆盖工具搜索丢弃失败/删除/scratch、UI 搜索保留失败但隐藏 scratch；`WebDtoTest` 覆盖 Web 消息的 status/steps；`ConversationContextLimitTest` 覆盖投影后边界映射。命令：`:ai:testDebugUnitTest --tests "me.rerere.ai.ui.UIMessageSerializationTest" --tests "me.rerere.ai.ui.MessageMergeTest"`、`:app:testGithubDebugUnitTest --tests "me.rerere.rikkahub.data.model.ConversationMessagesTest" --tests "me.rerere.rikkahub.service.ConversationSessionTest" --tests "me.rerere.rikkahub.service.SettlementStatusTest" --tests "me.rerere.rikkahub.data.ai.tools.DialogueMessagesTest" --tests "me.rerere.rikkahub.data.ai.ConversationRecentModelMessagesTest" --tests "me.rerere.rikkahub.data.repository.SearchVisibilityTest" --tests "me.rerere.rikkahub.web.dto.WebDtoTest" --tests "me.rerere.rikkahub.data.model.ConversationContextLimitTest"`；`assembleGithubDebug` 通过。本轮没有重跑 README 要求的设备验收样本；`UIMessage` 只增加带默认值的字段，旧 JSON 解码测试通过，设备和样本重跑留到下一批。

<a id="c59"></a>

## C59 截断流识别与停止结算

- 落点：五个 `StreamChunkDecoder` 的 `onClosed()` 必须见过协议终止事件，否则抛 `IOException`：Claude 要 `message_stop` 或 `[DONE]`（`[DONE]` 是既有行为）；OpenAI ChatCompletions 要 `finish_reason` 或 `[DONE]`；OpenAI Responses 要 `response.completed`、`response.incomplete` 或 `[DONE]`；Google 要 `finishReason` 或 `[DONE]`；Google Interactions 要 `interaction.completed` 或 `[DONE]`。五个 provider 接线点（`ClaudeProvider`、`GoogleProvider`、`ChatCompletionsAPI`、`ResponseAPI`、`InteractionsAPI`）的 `onClosed` 捕获异常后 `close(e)`，`onFailure` 在 `t` 和响应体都为空时用 `IOException` 兜底，不再静默正常关闭。`ChatService.stopGeneration` 把 `jobs.join()` 和 `finishInterruptedPendingTools` 放进 `withContext(NonCancellable)`。
- 理由：传输层正常 EOF 不等于协议完成；原来 `onClosed` 无条件补 `Finish`，代理截断或半截 EOF 会被当成完整回复，不进重试也不报错。停止结算在 `viewModelScope` 取消后可能不执行，pending 工具收尾丢失，`isAwaitingToolApproval` 还可能挡住发送、编辑和删除。
- 合并注意：这是严格判定。中转或旧平台如果不发送终止事件，行为会从“静默成功”变成可重试的 `IOException`；确需兼容时按 provider 加宽松条件，不要全局关掉。重复收尾由各 decoder 的 `finished` 状态保证返回空；`finish_reason`/`finishReason` 是终止信号，不参与幂等。`onClosed` 捕获异常后必须 `close(e)`，不能只 `close()`。`stopGeneration` 的 NonCancellable 只包 join 和工具收尾，不包 UI 状态；发送前的 `finishInterruptedPendingTools` 仍保留作崩溃补偿。工具不响应取消时 join 会继续等待，UI 不阻塞但结算延后；`cancelJobs` 是快照，join 之前新起的 job 不在等待范围内。`spotlessApply` 对 `core/ai/` 既有格式做过一次重排，无语义变化，移植上游时按语义保留。C58 的失败/中断消息状态与本节独立。
- 测试：`TruncatedStreamTest` 覆盖五个 decoder 的早关抛错、真实终止标记、`[DONE]` 和 delta 后截断；`GoogleToolCombinationTest` 的流 fixture 补上 `finishReason: STOP`，避免半截 fixture 被新规则判成截断；`:ai:testDebugUnitTest` 全量通过。`:app:testGithubDebugUnitTest` 687 条里只有已知的 Windows 7 失败（`SettingsStoreTest` 6 条、`ProviderAccountManagerTest` 1 条），没有新增；`assembleGithubDebug` 通过。NonCancellable 接线没有 JVM 单测，`cancelInterruptedTools` 本身由 `ConversationMessagesTest` 覆盖；手动验收：生成中停止后立即离开页面，pending 工具仍被收尾，发送/编辑/删除不再被 `isAwaitingToolApproval` 锁住。

<a id="c60"></a>

## C60 审批期间冻结 turn 上下文

- 落点：`ConversationSession.pendingApprovalTurn` 只存在内存；`ChatService.runTurn` 增加 `resumeApproval` 参数，只有 `handleToolApproval` 在所有 pending 工具都处理完后传 true 并复用这份 `ChatTurnContext`，其他入口传 false 并丢弃 stale frozen；本轮以 `AgentFinishReason.AwaitingApproval` 结束时存回，其余结束清空。`selectMessageNode` 在 `isAwaitingToolApproval` 或 `pendingApprovalTurn` 非空时抛 `ConflictException`；`regenerateAtMessage` 在截断、清 scratch、清 frozen 之前用 `ConversationSession.canRegenerate()` 拒绝生成中、审批中和冻结恢复中的重新生成，并报错误卡片；冷 session 在 `ensureInitialized` 之后、清 scratch/截断之前再校验一次待审批和 frozen。`stopGeneration` 没有 job 但有待审批时也会清 frozen 并收尾，`ConversationSession.replace` 清空冻结上下文。
- 理由：审批恢复原来重新 `createContext`，审批期间改模型/provider 会把工具结果和工具 schema 发给新配置；切走后 `isAwaitingToolApproval` 变 false，pending 调用成孤儿，下一次 `withoutUnresumableToolCalls` 会补恢复结果。冻结后配置改动从下一轮生效。regenerate 如果先截断再发现被挡住，会留下半成功历史；用显式 `resumeApproval` 取代“末条消息有没有可恢复工具”的猜测，也避免审批恢复在非末条消息上误判。
- 合并注意：冻结只存内存，进程死亡或会话重建后丢失，审批恢复退化为当时的配置。`runTurn` 在 `createAgent` 之前失败时 frozen 已经被消费，批准状态已经写进消息，审批 UI 不再出现；下一次发送的 `finishInterruptedPendingTools` 会给这条已批准工具补 `not_started`/`outcome_unknown` 恢复结果，不是“用当前配置重跑”，这个边界不要当成可重试审批。分支切换被拒绝时 `ChatVM` 只记日志，Web 路由返回 409；如果要做可见提示，需要在 ChatVM 加文案。审批期间改配置不报错，只影响下一轮；这是与“直接拒绝”不同的取舍。`canRegenerate()` 同时拒绝“有 job 在跑时重新生成更早消息”，这是有意从原来的接管改成拒绝，避免审批恢复被新 job 顶掉。本批同时补了 C59 的 `[DONE]` 用例，登记在 C59，不算 C60 改 `core/ai/`。
- 测试：`ConversationSessionTest` 覆盖 `replace` 清空 frozen approval turn；`ConversationSession.canRegenerate()` 由同一测试类覆盖正常、待审批和冻结三种结果；`:app:testGithubDebugUnitTest` 687 条里只有已知 Windows 7 失败，`:ai:testDebugUnitTest` 全量通过；`assembleGithubDebug` 通过。手动验收：多工具部分审批、批准、拒绝、回答各走一遍；审批中改模型/provider 再批，工具结果仍发给原配置；审批中切分支被拒；审批中 regenerate 被拒且历史不变；停止（审批态 App 和浮窗都没有停止按钮，只有 Web `/stop` 可验证）后 frozen 清空且 pending 工具收尾。

<a id="c61"></a>

## C61 结构化失败与重试策略

- 落点：`ErrorParser.kt` 新增 `LlmFailureCode`、`LlmFailure`，`HttpException` 带上 code、HTTP status 和 `Retry-After`；`toHttpException`、`withResponse` 负责从响应归一化。五个聊天 provider 接线点（Claude、ChatCompletions、Responses、Google generateContent、Interactions）的非流式非 2xx 改抛 `HttpException`，流式 `onFailure` 的解析错误附加响应状态，空响应体也保留状态。`RetryPolicy` 改为按错误码允许列表重试，支持 `maxDelay`、10% jitter 和 `Retry-After`；`AgentLoop` 在流式和非流式路径把空回复包成 `EMPTY_RESPONSE`，进入同一重试循环。
- 理由：原来只按 `IOException` 判断，HTTP 429/5xx 的 JSON 错误大多不重试；空回复直接以完成状态落库；重试固定 1s/2s/4s，没有 jitter，也不看服务端 `Retry-After`。
- 合并注意：默认只重试 `RATE_LIMIT`、`SERVER`、`TIMEOUT`、`TRANSPORT`、`EMPTY_RESPONSE`；`AUTH`、`QUOTA`、`INVALID_REQUEST`、`CONTEXT_WINDOW_EXCEEDED` 不重试。C75 已恢复为默认关闭的可选项，`Settings.retryPolicy()` 只在用户开启后返回策略，压缩与记忆仍显式 NONE。`RetryPolicy` 的 `retryOn` 仍是可选的逃生口，自定义策略优先于码表。`Retry-After` 会被 `maxDelay` 截断，不做“服务端要求超过上限就放弃”的区分；jitter 固定 10%，测试用 `jitterRatio = 0`。空回复判定只把空白 Text/Reasoning 当空，Tool、图片、文档等 part 都算有内容；这会让某些本来静默成功的空回复变成重试后错误，是预期行为。结构化错误优先于 HTTP status，`insufficient_quota` 和嵌套 `QuotaFailure` 即使是 429 也归 `QUOTA` 且不重试；没有 `QuotaFailure` 的 Google `RESOURCE_EXHAUSTED` 429 仍是 `RATE_LIMIT`，可重试。非 JSON body 只按 HTTP status 分类，不拿 HTML 里的关键词做信号。流式 onFailure 解析失败时用响应状态兜底，不再把 HTML 502 当序列化异常。`core/ai/build.gradle.kts` 打开 `unitTests.isReturnDefaultValues` 只是为了 provider 接线测试能调用 `Log`，范围仅限 `ai` 单测。C3 的 `core/ai/` 小改动原则下，这是第八处分叉。
- 手动验收：2026-11-16 开发机用假 OpenAI provider 验证：429 带 `Retry-After: 2` 重试一次并成功、C76 显示等待 2 秒；500 HTML 重试一次并成功；空 SSE 以 `EMPTY_RESPONSE` 重试并成功；401、402、400 context 各只发一次、不重试。空回复在关闭时直接出错误卡片，开启时按 `EMPTY_RESPONSE` 重试。
- 测试：`RetryTest` 覆盖码表、`Retry-After` 原值和超过 `maxDelay` 的截断、jitter 归零后的固定间隔、`QUOTA` 不重试和取消；`ErrorParserTest` 覆盖 401/402/429/5xx/400 context 分类、结构化 `insufficient_quota`、嵌套 `QuotaFailure`、Gemini `RESOURCE_EXHAUSTED` 429 仍可重试、`withResponse` 保留结构化 code、HTTP date、非 JSON body 只按 status 分类和 `Retry-After` 解析；`AgentLoopTest` 覆盖流式和非流式空回复重试后成功；`ChatCompletionsErrorTest` 用假 interceptor 验证 429 `insufficient_quota` 到 `HttpException(status/code/Retry-After)` 的接线；`:ai:testDebugUnitTest` 全量通过；`:app:assembleGithubDebug` 通过。`:app:testGithubDebugUnitTest` 仍为已知 Windows 7 失败，本轮没有改 app。

<a id="c62"></a>

## C62 流式 idle watchdog

- 落点：`AgentRequest.streamIdleTimeout` 默认 90 秒；`AgentLoop.stream` 用 `Flow.timeout` 限制相邻 chunk 的等待，超时抛 `LlmFailure(TIMEOUT)`。Claude 的 `ping` 事件解码成 `StreamChunk.Heartbeat`，`AgentLoop` 收到后跳过、不触发 preview，但会重置看门狗；`NetworkSetting.streamIdleTimeoutEnabled`（默认 true）控制整条看门狗，关掉后在 `ChatRequestFactory.createAgent` 传 null。
- 理由：Anthropic 长思考时可能长时间没有内容 chunk，只有 ping；只按内容 chunk 计时会把正常请求切断。自动重试关闭时超时直接出错误卡片，开启后按策略重试。
- 合并注意：`streamIdleTimeout <= 0` 仍会立即超时，调用方只传正数或 null；非流式请求不经过看门狗。新增 provider 若有自己的保活事件，按同样方式产出 `StreamChunk.Heartbeat`，不要在 `AgentLoop` 里按 provider 特判。设置入口在网络偏好页（`setting_page_preferences_network_stream_idle_timeout`），四种语言同步；关掉后 `ChatRequestFactory` 传 null。代码里 90 秒的唯一来源是 `AgentRequest.DEFAULT_STREAM_IDLE_TIMEOUT`，四份 locale 文案与 NetworkSetting 的注释仍写死 90 秒，改常量时要一起改。
- 测试：`AgentLoopTest` 覆盖空闲超时、chunk 重置、心跳重置与心跳不触发 preview；`ClaudeStreamDecoderTest` 覆盖 ping 解码成 `StreamChunk.Heartbeat`；`SettingsTimeoutsTest` 覆盖看门狗开关与工具超时的映射；`AgentSettingTest` 覆盖 `NetworkSetting.streamIdleTimeoutEnabled` 的旧 JSON 默认值；`:ai:testDebugUnitTest` 通过。

<a id="c63"></a>

## C63 上下文超限自动缩窗

- 现状：已撤销（2026-11-14）；2026-11-15 用户确认不恢复，错误卡片只保留信息提示。`AgentHooks.onContextOverflow`、`overflowWindow`、`shrinkActive`、`contextOverflowRetried` 与相关测试都已删除；provider 返回上下文超限时只按 `CONTEXT_WINDOW_EXCEEDED` 出错误卡片，由用户自己压缩或截断，不再静默改请求。
- 理由：原实现把请求缩到最后一条 user 起，等于把整段历史从这一次请求里丢掉，而且每一轮都会重演，界面没有任何提示；这条又是在 DSH 审计里按重型 harness 的场景补的，和聊天客户端的定位不符。
- 合并注意：上游若在 `AgentHooks` 或 `AgentLoop` 里增加类似的 overflow 恢复入口，不要直接搬回来；是否恢复、以什么形式恢复，等失败恢复与消息模型重做时一起定，见 `roadmap.md` 的「失败恢复与上下文的返工」。
- 测试：`ChatRequestFactoryContextTest` 已删除，`AgentLoopTest` 的 4 条 overflow 用例已删除；`:ai:testDebugUnitTest` 通过。

<a id="c65"></a>

## C65 工具中断恢复结果

- 落点：`ConversationMessages.kt` 的 `recoverInterruptedTool` 按 `startedAt` 区分 `not_started` 与 `outcome_unknown`，`cancelInterruptedTools` 和 `Conversation.withoutUnresumableToolCalls` 共用它，逐 part 替换不可恢复的 Tool part，保留文本与推理。`Tool` 增加 `timeoutMillis` 声明；`AgentRequest.toolTimeout` 与工具有效超时取两者的较大值（`effectiveToolTimeout`），外层不能先于工具自身的超时触发。`AgentSetting.toolTimeoutSeconds`（默认 300，0 表示不限制）控制外层超时；`workspace_shell` 的默认与最大命令超时来自 `AgentSetting.shellDefaultTimeoutSeconds`（默认 30）与 `shellMaxTimeoutSeconds`（默认 600），并据此声明 `timeoutMillis`。
- 理由：原来所有中断都写成 `cancelled by user`，分不清工具是否已经产生副作用；工具错误把内部堆栈发给模型。超时固定 5 分钟又和 `workspace_shell` 自己允许的 600 秒冲突，模型要 600 秒会被外层先掐掉。
- 合并注意：`timeoutMillis` 是工具声明，不是执行参数；外层设为不限制时整条外层超时关闭，工具内部超时保持各自逻辑。声明值与外层相等时外层仍可能先触发计时，所以 `effectiveToolTimeout` 给声明值留 5 秒余量，让工具自己的计时先响。`withTimeoutOrNull` 是协作式取消，不挂起且不走 `runInterruptible` 的阻塞工具不会被按时打断。旧的 `toolTimeout <= 0` 立即超时的行为不再通过设置暴露，设置里 0 表示关闭。三个值在 `AgentSetting.normalized()` 里统一收口（`Settings.normalized()` 会调用），页面用收口后的值，避免导入或手改数据产生反向滑块区间。设置入口是新加的「工具」偏好页（`Screen.SettingPreferencesAgent`、`SettingPreferencesAgentPage`），四份 locale 同步。`createWorkspaceTools` 通过参数接收 `AgentSetting`，不再读 Koin 的全局设置。官方兼容导出会多出 `agentSetting` 与 `networkSetting.streamIdleTimeoutEnabled` 两个官方不认识的键，容忍方式与 C69/C75 相同；设备数据验收见 `backup/acceptance.md` 的 2026-11-14 记录。
- 测试：`AgentLoopTest` 覆盖工具失败摘要、内部超时、外层超时、声明超时优先于较短外层超时（含 5 秒余量），以及 `effectiveToolTimeout` 的合并规则；`AgentSettingTest` 覆盖默认值、序列化往返、范围收口与 `NetworkSetting.streamIdleTimeoutEnabled` 的旧 JSON 默认值；`SettingsFieldsTest` 覆盖 `agent_setting` 的 DataStore 往返与 key 表。

<a id="c66"></a>

## C66 消息队列恢复与回插

- 落点：`ChatService.sendMessage` 在入队前无条件 `messageQueue.resume()`，不再因为队列非空而把新消息静默留在 paused 队列；`MessageQueue.requeueFront` 把出队后发送失败的消息放回队首，并按 id 去重；`sendQueuedMessage` 在 `session.flush()` 成功前失败时回插，成功落库后失败不回插，避免重复用户消息。
- 理由：原来的条件是“队列为空才 resume”，paused 且非空时显式发送只 enqueue，`takeNext` 返回 null，消息不发也不进正文；先 `takeNext` 再写历史，中间失败会丢消息。
- 合并注意：`requeueFront` 只处理内存队列，C66 时队列本身仍不持久化，进程死亡后丢失；C70 已按会话落盘并恢复为暂停态。`messagePersisted` 在 `session.update` 之后、`flush()` 成功时置真；`flush()` 抛异常时内存已有消息但仍会回插，之后重派发会再追加一条，下一次 flush 成功时两条都落库，失败那次会在历史和队列面板各显示一份。正常失败由 `reportFailure` 暂停队列；取消分支 `reportFailure` 先 rethrow、不执行 pause，靠 `stopGeneration` 和删除路径先暂停兜住。显式发送会再次 resume。`MessageQueue.kt` 在本批触发整文件 spotless 清理，属既有违规的一次性格式化。`core/ai/` 无改动。
- 测试：`MessageQueueTest` 覆盖出队后回插队首和重复回插去重；`:app:testGithubDebugUnitTest` 对应测试通过。手动验收：在 paused 且有排队消息时发送新消息，队列恢复并按顺序派发；失败回插由 `MessageQueueTest` 和代码走查覆盖，ChatService 层没有稳定的初始化/保存失败注入入口，不做设备步骤。

<a id="c67"></a>

## C67 进程死亡回复恢复

- 落点：`UIMessage` 增加带默认值的 `isGenerating`；`ChatService` 收到生成中的 `MessageUpdated` 时置真，`ConversationSession.finishGeneration` 统一清假，C50 的周期保存把标记写进消息 JSON。`ConversationSession.initialize` 加载后调用 `Conversation.recoverAbandonedGeneration`：扫描所有节点和分支里 `isGenerating` 的消息，改成 `INTERRUPTED`（已经是 `FAILED` 就保留），清掉标记，并给未执行工具补 `not_started`/`outcome_unknown`；有变化时立即用 `store.prepareWrite`/`write` 落库。
- 理由：只靠消息形状和 `finishedAt` 推断会把旧消息误判，明确写一个随消息保存的生成标记；进程死亡后不能让半截回复一直显示为生成中，也不能让未执行 tool call 留在历史里。
- 合并注意：标记只覆盖已经被 C50 周期保存到的半截回复；进程在首次保存前死亡、live retrying 状态仍会丢，错误卡片已由 C71 落盘，队列消息已由 C70 持久化。加载时的恢复写库失败会让初始化抛错，这是有意的，避免内存显示已恢复但库里仍是生成中。旧 JSON 没有 `isGenerating`，默认 false。`core/ai/` 分叉计数因此变为十二处。
- 测试：`ConversationMessagesTest` 覆盖生成中回复转 `INTERRUPTED`、非末尾节点恢复、FAILED 保留、未执行工具补结果、正常完成回复不被改动；`ConversationSessionTest` 覆盖加载时恢复并落库；`:app:testGithubDebugUnitTest` 对应测试通过。手动验收：等 C50 至少保存过一次（小节点 60 秒或 64KB 增长才写一次）后再杀进程，重启打开会话应看到中断标记和工具恢复结果，不显示生成中；首次保存窗口内杀进程不会出现中断标记。

<a id="c68"></a>

## C68 压缩孤儿对加载清理与过滤

- 落点：`ConversationSession.initialize` 在 C67 的恢复之后对加载结果调用 `Conversation.removeCrashedCompressionPairs`，只把没有回复或回复状态仍为 `COMPLETE` 的崩溃对删掉，再和 C67 的恢复结果一次 `store.prepareWrite`/`write` 落库；压缩失败或取消时 `ChatService` 先把回复标成 `FAILED`，加载时保留这对节点，重试/删除入口仍在。`core/ai/ui/Message.kt` 增加 `List<UIMessage>.withoutCompressionScratch()`，`ChatRequestFactory` 两处、`ConversationMetaGenerator.recentModelMessages`、`Conversation.dialogueMessages` 和导出选中消息都先过滤提示词及紧随的 assistant 回复；`web/dto/WebDto.kt` 的 `Conversation.toDto` 过滤 Web 快照/SSE 的同一对节点，`WebDtoTest` 覆盖该转换；`ConversationMessages.scratchMessageIds` 和 `ConversationRepository.filterSearch` 过滤 App/Web/agent 的 FTS 搜索结果。
- 理由：压缩失败或取消留下的 prompt 与半截回复原来只在发送、重新生成、截断、再次压缩前清理，管理上下文对话框和文件选择器还会在内存里清；进程崩溃后没人再碰它们，Web/SSE 会把它们当正常消息。失败/取消对是 C51 明确要保留给用户重试的，不能和崩溃对一起删。
- 合并注意：只清理加载时能识别的、回复仍为 `COMPLETE` 的 prompt 对；活动中的压缩不会被误删，因为加载时没有活动任务。失败/取消对靠 `FAILED` 状态跨 reload 保留；如果普通失败也在同一会话里重试，先清旧对再插新对。请求派生、导出和 Web/SSE 的 `Conversation.toDto` 已过滤；SSE 在 DTO 无可见变化时直接跳过，不会因为压缩流每个 chunk 只改隐藏节点而反复推整份快照。浮动窗口没有消息列表，只预览 `replyMessageId` 对应回复；压缩期间显示摘要正文，轮次结束就清空，不会显示孤儿对。搜索层 `ConversationRepository.filterSearch` 按会话当前消息过滤 FTS 结果，scratch 集合与消息状态共用同一次会话加载，App、Web `/api/conversations/search` 和 agent 工具都不再返回提示词或压缩失败/取消留下的回复；FTS 索引里仍保留这些行，失败/取消对每次保存还会重写索引，删除该对或全量重建才会从索引消失，过滤是当前的唯一遮挡。消息模型层用 `isCompressionPrompt` 加列表 helper，没有逐 part 的不可发送标记。`core/ai/` 分叉计数因此变为十三处。
- 测试：`ConversationSessionTest` 覆盖加载时删除崩溃 prompt+半截回复并落库、`FAILED` 对保留；`MessageMergeTest` 覆盖提示词+回复成对删除和只有提示词；`ConversationRecentModelMessagesTest`、`DialogueMessagesTest` 覆盖标题和对话历史的过滤，`WebDtoTest` 覆盖 `Conversation.toDto` 的成对过滤；`ConversationMessagesTest` 覆盖 `scratchMessageIds` 的成对、无回复、后接 user、多 prompt 和无 prompt 边界；SSE 的无变化跳过和搜索过滤由代码走查确认；MessageFtsManagerTest 新增 limit 默认/钳制与 assistant 过滤参数顺序用例，instrumented 只编译，未在设备上跑。请求装配的两处过滤由代码走查确认，没有单独构造 `ChatRequestFactory` 用例。手动验收：压缩中杀进程，重启打开会话不应看到崩溃的压缩提示词或半截回复；压缩失败/取消后返回列表再进入，重试卡应还在；导出不包含临时提示词和半截回复。

<a id="c69"></a>

## C69 停止生成前确认

- 落点：`DisplaySetting` 新增带默认值的 `confirmStopGeneration = true`；`SettingPreferencesGeneralPage` 的通用分组加开关，四份 locale 补 `setting_display_page_confirm_stop_generation_*` 与 `chat_page_stop_generation_confirm_*`。用户能点的停止入口有三处：`ChatPage` 输入栏的发送按钮在输入为空且生成中时切成停止态，走 `requestStopGeneration`；压缩卡片在 streaming 时显示的停止同样走它；悬浮窗 `ChatFloatingPanel` 的停止按钮。开启确认时聊天页弹 `StopGenerationConfirmDialog`，悬浮窗在同一个 overlay window 里用 `StopGenerationConfirmCard` 加遮罩确认，因为 Compose 的 Dialog 需要 Activity 的 window token；关闭时直接 `vm.stopGeneration(job)` 或 `chatService.stopGeneration(conversationId, expectedJob)`。确认状态用 `StopConfirmState` 记下打开时的 job，队列换轮、生成结束、切换会话、收起面板或按返回键都会关闭；确认时 `ChatService.stopGeneration` 在会话锁内按 expectedJob 校验当前 job，不匹配就不停止，避免 stale composition 停到下一轮。Web UI 的停止按钮、Web `/api/.../stop` 与前台服务 `onTimeout` 仍直接停止。
- 理由：停止生成是即时、不可撤销的动作。输入栏的同一个按钮在输入为空时从发送切成停止，和用户习惯的发送位置重合；误触会丢掉本轮已经生成的内容并中断工具调用。默认确认，设置里可关。
- 合并注意：`confirmStopGeneration` 是 `DisplaySetting` 的序列化字段，旧 JSON 缺少时解码为 `true`；官方兼容导出会带上这个键。官方源码的 `JsonInstant` 开着 `ignoreUnknownKeys`，本轮也用官方 RikkaHub 3.1.1 实际恢复了两份带新字段的归档，库计数与归档一致、图片正常，不能只把它当成源码推断。关闭确认后仍然是立即停止；同一轮已请求停止时会忽略重复点击，避免重复调用，这一点与原来不同。确认只包住用户主动点停止的三个 UI 入口，程序化停止（Web API、前台服务超时、队列/删除等内部路径）不受影响；Web UI 的停止按钮仍直接 POST，本轮明确不覆盖。悬浮窗的卡片是同一窗口里的普通子节点，确认时窗口可以长到卡片高度，不会被面板高度裁掉；关闭确认不会收起面板，收起面板、切换会话或按返回键时确认状态一起复位。没有改 `core/ai/`。
- 测试：`StopConfirmStateTest` 覆盖打开、关闭、job 身份匹配，以及 `resolveStopAction` 的关闭确认直接停止、关闭确认时已请求停止被忽略、开启确认、已请求停止被忽略、换轮后重新允许五个分支；`DisplaySettingTest` 覆盖旧 JSON 默认 `true` 和关闭值往返；`FloatingChatUiStateTest` 覆盖 job 随状态传递。`:app:testGithubDebugUnitTest` 运行 723 条，新增用例通过，Windows 上 7 条既有文件占用失败未变；`assembleGithubDebug`、`spotlessCheck`、markdownlint 通过。手动验收已执行：设备上打开样本会话，发送后点输入栏停止，出现 `Stop generating?` 弹窗；Cancel 后生成继续（停止按钮仍在），再次停止并确认后回到发送态；把开关关闭后再生成并点停止，直接回到发送态、没有弹窗；设置页通用分组的 `Confirm before stopping` 默认勾选。压缩卡片停止、悬浮窗卡片、大字号滚动和旋转后的重复点击没有在设备上单独验。样本 provider 的 key 无效，同一轮出现鉴权错误卡片，这次没有拿到干净的 INTERRUPTED 样本，中断状态仍由 C58/C67 用例覆盖。
- 验收：`data.sync` 整个 instrumented 包 51 条通过（`BackupManagerTest` 26、`DatabaseBackupTest` 3、`OfficialBackupRestoreAcceptanceTest` 2、`RestoreAppliedOutcomeTest` 1、`OfficialDatabaseConverterTest` 2、`OfficialDatabaseReaderTest` 7、`OfficialDatabaseWriterTest` 4、`OfficialForeignAttachmentPathsTest` 4、`OfficialSampleAcceptanceTest` 2），覆盖构造 Convert、同安装自家恢复、高版本拒绝等 2/4/5 样本。v32 官方兼容出口 86 条目、89.7MB，settings 含 `confirmStopGeneration=true`；官方 RikkaHub 3.1.1 恢复后 168/673/107 处引用（64 节点），61 个被引用附件，图片正常，恢复库与导出库一致；工作数据本身是 168/673，历史 170/676 是更早样本状态。换包名 `.crosscheck` 恢复后 168/673，107 处引用改写，61 个附件，图片正常。5GB 导入 fork 后 1664/11483/682；官方兼容导出 707 条目、649MB、682 附件，settings 含新字段；官方 3.1.1 恢复后 1664/11483、9080 处引用（783 节点），图片正常。附件计数逐项：原始官方 780 = 682 个 5GB 内容名 + 98 个旧 UUID；v32 的 61 个与原集合重叠 25、新增 36，官方目录变 816；5GB 的 682 个全部已在原集合内、新增 0，目录仍 816，即 682 + 98 + 36。验收后官方从备份还原为 780（682+98），fork 为 168/61，两端启动正常。search 人工闸门未重跑（acceptance 未给固定查询集；官方恢复不重建 FTS，5GB 官方数据搜 iOS 为空是预期），crosscheck 的 `Rebuilt search indexes after restore` 日志未捕获，这两项作为已知缺口记录。

<a id="c70"></a>

## C70 消息队列持久化

- 落点：`MessageQueueStore`（`noBackupFilesDir/message-queues/<会话 id>.json`）保存未出队的 `QueuedMessage`；`QueuedMessage` 加 `@Serializable`，`isEditing` 标 `@Transient`。`MessageQueue.restore` 和初始化期间已经入队的消息按 id 合并，恢复为暂停态；`ConversationSession.initialize` 读回队列，按会话里已有 user 消息 id 过滤掉“已经落库但队列文件没来得及删”的项，然后启动一个消息列表变化就落盘的 collector；collector 启动时先把当前队列落一次，`replace` 也调用同一个幂等启动函数。`ChatService.sendQueuedMessage` 正常用 `queued.id` 作为新 user 消息的 id，供恢复时去重；如果 flush 失败后回插重试、旧消息还在，则换新 id 避免同 id 撞 FTS。`deleteConversation` 删队列文件，`RikkaHubApp` 启动时清理孤儿文件。`AttachmentCleaner` 扫描队列文件里的附件地址。队列面板不需要改，直接显示恢复后的暂停队列。
- 理由：C66 只修了内存队列的派发与失败回插，进程死亡后队列丢失，用户已经排好的消息会静默消失。落盘后重开能看到 `Queue paused · N`，由用户 Resume，避免自动发送。
- 合并注意：队列文件不进备份（`noBackupFilesDir`），不改 Room、Settings 或官方兼容导出，不触发数据层验收；恢复一律暂停；`isEditing` 不跨进程；文件损坏按空队列处理，collector 启动写回时覆盖坏文件，之前若被附件清理扫到仍会保守保留一次；写入是原子替换，collector 被会话回收取消时最后一次改动可能来不及写；消息已经出队、会话还没整份落库的窗口仍可能丢，这段包含 `ensureInitialized` 和一次会话写库，不是微秒级，C66 的失败回插只覆盖进程内失败；同一队列 id 既用于去重也作为消息 id，重试时若已存在同 id 会换新 id；`deleteOrphans` 只保留库里仍存在的会话，跨安装或删除后的遗留文件由下次启动清理；附件清理扫队列目录的原始文本，坏文件也会保附件。没有改 `core/ai/`。
- 测试：`MessageQueueStoreTest` 覆盖往返、编辑态不落盘、空队列删文件、坏文件忽略、附件引用、孤儿清理；`MessageQueueTest` 覆盖 restore 暂停且清编辑态；`ConversationSessionTest` 覆盖初始化恢复暂停队列、已有 user 消息去重、变化落盘、初始化期间入队的消息与落盘队列合并、`replace` 后仍启动队列落盘。设备手动：生成中发送第二条消息变为 `Queued · 1`，杀进程重开显示 `Queue paused · 1`，Resume 后队列文件删除并继续发送。C71 顺带把 collector 的取消从 `runCatching` 中放出来，`ConversationSessionTest` 52 条仍通过。`:app:testGithubDebugUnitTest` 735 条（C70 当时计数），7 条既有 Windows 失败未变；assemble、spotless、markdownlint 通过。

<a id="c71"></a>

## C71 错误卡片持久化

- 落点：`ChatErrorStore`（`noBackupFilesDir/chat-errors.json`）保存所有会话的错误卡片；`Throwable` 不直接序列化，只存卡片 id、标题、消息、会话 id、时间与 `ChatErrorSolution` 枚举名，恢复时用消息重建普通 `Throwable`。`ChatService` 启动时把文件里的卡片按仍存在的会话过滤后合并进 `_errors`，恢复全程持 `errorsPersistenceMutex`，`persistErrors` 只能排在读取之后；恢复合并用 `withRestoredErrors`，只按 id 去重并按会话上限裁剪，不按 live 的“同失败”规则合并。`addError`、`dismissError`、`clearErrors` 更新后写回整份可持久化列表，写失败只记日志；删除会话走 `clearErrors` 一并删卡片。通知类卡片（停止中不能重新生成、工具不可用、压缩门禁/取消、操作门禁）用 `persistable = false`，只活在内存。`restorePersistedErrors` 与 `persistErrors` 遇到 `CancellationException` 原样抛出，不把取消当 IO 失败记录；顺带把 C70 的 `ConversationSession` 队列 collector 取消路径也改成原样抛出，`ConversationSessionTest` 52 条仍通过。
- 理由：C43 把错误卡片改成默认保留到手动关闭，但卡片只在内存里，进程死亡后用户看不到上次失败的原因。恢复卡片让「失败只给人看」这条在重启后仍成立。实时 `Retrying (n/m)` 不落盘：进程死亡后没有在途请求，半截回复由 C67 标成 `INTERRUPTED`，把重试倒计时恢复出来只会误导；重试 attempt 的 UI 与用量仍归后续。通知类卡片也不落盘，避免重启后在没有生成/压缩的会话里出现「Stop the generation first」。
- 合并注意：文件不进备份，不改 Room、Settings、官方兼容导出或 `core/ai/`；恢复的异常是普通 `Throwable`，与新的同类失败不会按 `javaClass` 合并，可能出现一张旧卡片加一张新卡片；恢复卡片之间只按 id 去重，所以同文本不同异常类型的两张旧卡片都会保留。卡片消息可能很长，原样保留；文件损坏按空列表处理，下一次写入覆盖；没有对应会话的卡片过滤掉，但上次打开、还没落库的会话不在保留集合里，首次写库失败留下的卡片不会恢复。`dismiss`/`clear` 是异步落盘：成功路径会删文件，但进程在 IO 完成前被杀或删除失败时旧卡片会在下次启动复活，设备验收只覆盖成功路径。恢复读取失败时下一次写可能覆盖旧文件；启动清理没有处理写失败的 `.tmp` 临时文件。没有改 `core/ai/`。
- 测试：`ChatErrorStoreTest` 覆盖往返（标题、消息、会话、时间、solution、卡片 id）、空列表删文件、坏文件忽略、未知 solution 解码为空、双会话往返、null 消息兜底；`ChatErrorRetentionTest` 覆盖会话内上限与同失败合并，以及恢复时同文本不同异常类型都保留、按时间戳裁剪。设备手动：新会话用无效 provider 触发 `Message generation failed`，`chat-errors.json` 写入；force-stop 重开同一会话卡片恢复；Dismiss 后文件删除，再重开不再出现。`:app:testGithubDebugUnitTest` 743 条，7 条既有 Windows 失败未变；assemble、spotless、markdownlint 通过。

<a id="c72"></a>

## C72 上下文压缩按 token 预算

- 落点：`CompressionPlan` 的保留起点按 user 轮次对齐：`keepRecent` 落在 assistant/tool 消息上时向前找到产生它的 user，保留段只会变大，不会把唯一 user 压进摘要；范围为空时返回 `NothingToCompress`，找不到 user 时保留原起点（产生它的 user 已在窗口外）。新增本地估算 `estimateTokens`（CJK 按 1 token、其它非空白按 4 字符/token，图片/视频 1024，工具按内容，文档按固定 4096 加文件名），`compressionSummaryMaxTokens` 按模型 `contextLength` 的八分之一夹在 1024 到 4096，并尊重 assistant 的 `maxTokens`；`compressionInputBudget` 再预留 2048 给系统提示词、工具和格式开销。`planCompression` 增加 `maxInputTokens` 与 `contextMessageLimit`，预算从最后一个检查点（含）到 `endIndex`，按 `filterModelVisible` 和 `limitContext` 与摘要请求对齐，检查点之前的旧历史不计入；超过预算返回 `RangeTooLarge`，`runCompressionTurn` 提示先截断或多保留，不再把必然溢出的摘要请求发给 provider。`streamCompressionSummary` 用显式 `maxTokens` 替代原来的 `null`；finish reason 被截断时不再整次失败，改用已经拿到的部分摘要并弹非持久化通知。新增中英日繁中四条 `chat_page_compress_range_too_large`，并把 `chat_page_compress_truncated` 改成“已使用部分摘要”。
- 理由：原来的压缩只按条数决定保留，`keepRecent=1` 且末条是 assistant 时会把产生它的 user 压掉；摘要请求的 `maxTokens = null` 由 provider 默认值决定，输出一截断就整次失败；旧历史超过模型上下文时又会直接把摘要请求发出去。按 user 轮次对齐、给摘要输出和输入都设预算、截断退回部分摘要，能让压缩在真实长会话里可预期。
- 合并注意：不改 Room、Settings、官方兼容导出或 `core/ai/`；估算是保守近似，不是 provider tokenizer，`compressionInputBudget` 的 2048 预留也不精确覆盖所有系统提示词/工具/记忆；文档只按固定 4096 估算、不解析文件内容，大 PDF/OCR/提示词注入文本仍可能低估；C73 已撤销，请求装配不再做额外的消息改写；超大历史会被拒绝而不是分块摘要；部分摘要可能漏掉细节，但会明确提示；`keepRecent` 的 UI 含义仍是至少保留条数，实际会因 user 对齐多保留；摘要预算会受 assistant `maxTokens` 限制；4096 的默认上限是为了避免超过小输出模型的服务端上限，仍可能在更小的模型上失败；没有新增设置字段，不需要数据层验收。`chat_page_compress_truncated` 的含义从“历史未截断”变成“使用部分摘要”，四种语言同步。
- 测试：`CompressionPlanTest` 覆盖保留起点对齐 user、单轮不压、找不到 user、超预算拒绝、预算内通过、检查点前旧历史不计入、`keepRecent=0` 带检查点、摘要 token 上限、assistant 上限为 0、输入预算、CJK/ASCII 与 Document 估算；全量 `:app:testGithubDebugUnitTest` 752 条（C72 当时计数），7 条既有 Windows 失败未变；assemble、spotless、markdownlint 通过。计划/预算纯函数由 JVM 覆盖；`ChatService` 的截断→部分摘要分支和 `RangeTooLarge` 通知没有服务层测试；provider key 无效，也没有真实摘要样本。

<a id="c73"></a>

## C73 发送前上下文裁剪

- 现状：已撤销（2026-11-14）；2026-11-15 用户确认不恢复，错误卡片只保留信息提示。`ContextPruning.kt`、`buildStep` 里的调用、日志与 `ContextPruningTest` 都已删除；发送前只做 `limitContext` 的条数截断，超限交给错误卡片。
- 理由：静默改写模型输入且没有开关，改到较早的消息会破坏消息段的前缀缓存，最近几轮要整段重算；这条是从 DSH 审计借来的，真正的长期方案是压缩计划与显式压缩，不是替用户悄悄删历史。
- 合并注意：`buildStep` 的输入变换顺序恢复成 `limitContext` 之后直接进 `transforms`；不要再在请求装配里加隐式的消息改写。`DocumentAsPromptTransformer` 仍按原样在变换阶段展开文档。
- 测试：`ContextPruningTest` 已删除；`:app:testGithubDebugUnitTest` 全量通过。

<a id="c74"></a>

## C74 上下文水位与压缩建议

- 落点：新增 `ContextPressure`/`Conversation.contextPressure(limitTokens)`。只取当前可见窗口（`filterModelVisible().withoutCompressionScratch()`，从最后一个检查点起），窗口内最近一次真实 usage 必须晚于检查点的 `createdAt`，再加它之后消息的本地估算；没有可用 usage 时整窗估算；检查点之前的旧 usage 和保留段里创建更早的旧回复都不采用。比例达到 80% 时 `shouldSuggestCompression` 为真。`ChatPage` 底部栏在非预览、非生成/压缩时显示 `ContextPressureBanner`：百分比、used/limit、压缩按钮；按钮只打开管理上下文对话框，不自动压缩。文案加四份 locale。
- 理由：C72 解决的是压缩计划和摘要预算，但用户看不到上下文还剩多少；长会话要等到 provider overflow 才有反馈。用最近一次真实 usage 加尾部估算做建议条，既比本地估算准，也不需要新增设置或自动改历史。
- 合并注意：不改 Room、Settings、官方兼容导出或 `core/ai/`；不自动压缩，不写历史，只显示建议并打开现有对话框；最近一条 usage 为 0 时跳过，找不到可用 usage 才整窗估算；旧消息没有 `lastUsage` 时 `contextTokens()` 会退回累计 `usage`，比例可能偏高；last usage 必须在最后一个检查点之后且 `createdAt` 晚于检查点，否则压缩/截断保留段的旧回复会把建议条常亮；删除或编辑更早消息不会插检查点，最后一条 usage 仍可能偏高；比例是近似值，尾部用本地估算，整窗估算不含 system prompt 和工具定义；`currentChatModel` 没有 contextLength、生成/压缩中或预览模式时不显示；banner 只在 Android 主聊天页，Web、悬浮窗和导出没有；建议条没有 dismiss 状态，压力降下来或压缩/截断后自然消失。
- 测试：`ContextPressureTest` 覆盖真实 usage 加尾部、无 usage 回退估算、检查点前 usage 忽略、保留段创建更早的 usage 忽略、多个 usage 取最后一个、legacy 累计 usage、80% 阈值、ratio>1、失败回复与压缩 scratch 不计入、空会话、非正 contextLength；全量 `:app:testGithubDebugUnitTest` 773 条，7 条既有 Windows 失败未变；assemble、spotless、markdownlint 通过。没有设备验证：样本会话 usage 很小、模型 contextLength 默认 1M，banner 阈值只由 JVM 用例覆盖。

<a id="c75"></a>

## C75 自动重试次数可调

- 现状：已恢复为默认关闭的可选项（2026-11-16）。`NetworkSetting.enableAutoRetry` 默认 false，网络设置提供开关和 1..10 次滑块；`Settings.retryPolicy()` 只在开关开启且次数大于 0 时返回 `RetryPolicy.NETWORK.copy(maxRetries = 次数)`，压缩摘要与记忆整理仍显式传 `RetryPolicy.NONE`。V6 迁移把旧设置里的 `enableAutoRetry` 一次性改成 false，之后用户显式开启不会被覆盖。
- 理由：失败恢复与消息模型已经落地，自动重试可以重新作为 opt-in 提供；默认关闭避免新用户在没有预期时重复请求和重放当前 step。
- 合并注意：`maxRetries` 保留在 0..`NetworkSetting.MAX_RETRIES`；开关开启但次数为 0 时仍返回 NONE；旧 JSON 缺少字段默认 false，`enableAutoRetry` 为 true 的旧设置由 V6 迁移关掉。`Settings.retryPolicy()` 只接聊天轮次，标题、OCR、翻译、压缩与记忆不继承自动重试。备份恢复会看 `settings.json` 的 `settingsVersion`：缺失或小于 6 的旧备份与官方备份强制关掉，写入 6 的新备份保留用户显式开启值；`SettingsJsonMigrator` 与 DataStore 迁移保持同一套语义。
- 测试：`SettingsRetryPolicyTest` 覆盖默认关闭、开启后次数与上限、旧 JSON 默认值；`PreferenceStoreV6MigrationTest` 覆盖旧 true 被关闭、版本门禁与备份 marker；开发机验收记录见 `backup/acceptance.md` 的 2026-11-16 C75 一节。

<a id="c76"></a>

## C76 重试 attempt 可见化

- 现状：历史 step 的 attempt 摘要已持久化（2026-11-16）。`MessageStep.attempts` 保存 attempt、maxRetries、code、message、delayMs、durationMs、recordedAt，最多 20 条；AgentLoop 在每次重试和 step 收尾时写入，ChatService 随消息落库。实时倒计时继续用 `session.retryAttempts`，重启后从最后一个 assistant 的 steps 重建历史卡片。
- 理由：用户需要看到自动重试正在进行、失败原因和剩余等待时间；默认关闭时没有事件，卡片自然不出现。
- 合并注意：`ConversationSession.retryAttempts` 仍只服务本轮倒计时，随新一轮/`replace` 清空；`MessageStep.attempts` 只存摘要，不存流内容，旧 JSON 默认空列表。历史卡片从最后一个 assistant 的 steps 读取，实时卡片存在时不叠加历史，避免同一次重试显示两遍；用户在重试等待中停止时 step 收尾为 CANCELLED，等待中的 attempt 不落盘，设计只要求 FAILED step 保留摘要。
- 测试：`RetryAttemptsTest` 覆盖实时记录与清空；`AgentLoopTest` 断言流式重试后 step 的 attempts 摘要；`UIMessageSerializationTest` 覆盖 attempts 的 JSON 往返与旧消息空列表。
- 设备验证：2026-11-16 开发机 429 重试成功后，数据库 `message_node.messages[0].steps[-1].attempts` 含 `attempt=1`、`code=RATE_LIMIT`、`delayMs=2000`；force-stop 重启并重新打开会话后，历史卡片仍显示 `Attempt 1/3`、`rate limited`、`Waited 2s`。

<a id="c77"></a>

## C77 错误卡片重试本步

- 现状：已撤销（2026-11-14）；2026-11-15 用户确认不恢复，错误卡片只保留信息提示。`ChatError.retryable`、`retryMessageId`、`retryTargetMessageId`、`hasLaterUserTurn`、`ChatVM.retryError`、错误卡片上的 Retry 按钮、`ChatErrorStore` 的对应字段与 `ChatErrorStoreTest`/`RetryTargetMessageIdTest` 的相关用例都已删除；错误卡片只显示错误信息、复制与关闭。
- 理由：重试语义依赖整条消息重放，用户已习惯按轮判断后自己重新生成或让模型继续；C75 恢复的是默认关闭的自动重试，错误卡片仍不承载重试。
- 合并注意：旧错误文件里的 `retryable`/`retryMessageId` 字段由 `JsonInstant` 的 `ignoreUnknownKeys` 忽略，`ChatErrorStoreTest` 有一条旧文件用例钉住；C77 永久取消，B2 不再恢复错误卡片重试，也不要把旧字段搬回来。
- 测试：`ChatErrorStoreTest` 的旧文件用例通过；`:app:testGithubDebugUnitTest` 全量通过。

<a id="c78"></a>

## C78 工具失败日志分级

- 落点：`core/ai/agent/AgentLoop.kt` 的工具执行 catch 从 `e.printStackTrace()` 改成 `Log.e(TAG, "Tool ${tool.toolName} failed", e)`，工具返回给模型的文本仍是 `Tool execution failed: <message>`；堆栈不会进入模型结果，但异常消息原样透传，没有消息时回退类名，可能包含路径；`TAG = "AgentLoop"`。
- 理由：C65 已经把工具错误收成给模型的可读摘要，但堆栈还在用 `printStackTrace` 打到 stderr，和 app 的日志体系分开；统一到 `Log.e` 后 logcat 能按 tag/等级过滤，模型仍只看到摘要。
- 合并注意：只改 `core/ai/agent/AgentLoop.kt` 的失败日志，不改工具输出、恢复结果、超时或 UI；`returnDefaultValues` 下 JVM 单测里的 `Log.e` 是空实现，现有测试用带 message 的工具异常断言摘要不含堆栈和异常类名，未覆盖 null-message 回退。output 的取消安全写入后来按设计关闭：`executeTools` 逐个执行、每个工具跑完立即 `updateTool`，已完成结果不会因后续取消丢失；在 `withContext(NonCancellable)` 里直接 `emit` 仍会触发 FlowCollector 跨协程发射限制，不再走这条；移植上游 `AgentLoop.kt` 时保留 `Log.e` 取代 `printStackTrace`；`core/ai/provider` 里剩余的 `printStackTrace` 不属本项范围；不改 Room、Settings、备份。
- 测试：`:ai:testDebugUnitTest` 324 条通过，`AgentLoopTest` 29 条含工具失败摘要和“停止时保留已跑工具结果”；`:app:testGithubDebugUnitTest` 786 条，7 条既有 Windows 失败未变；assemble、spotless、markdownlint 通过。设备上没有单独构造工具失败日志；C65 的工具超时/恢复手动验收已覆盖行为，本轮只改日志通道。

<a id="c81"></a>

## C81 压缩面板：高级设置、临时模型与思考继承

- 落点：`ContextManagementDialog` 的压缩页保留原来的「额外指令 + 保留最近 N 条」主流程，把模型和思考强度收进默认折叠的「高级设置」，高级设置标题在有临时改动时显示模型/等级摘要；模型复用现有 `ModelSelector`/`ModelListSheet`（搜索、按 provider 分组、启用过滤和能力标记），思考强度用 `FilterChip`，默认多一项「继承 (当前会话模型的等级)」，选择具体等级才覆盖；计划预览常显，用和服务端共用的 `Conversation.planCompressionFor` 和 `compressionPlanCounts`，计算放到 `Dispatchers.Default`，加载中显示计算文案；`CompressionCard` 流式时显示 `GenerationPhase` 阶段、按已生成正文/思考估算并与最终 usage 取较大值的 tok/s、以及按 `reply.createdAt` 算的耗时；`ChatRequestFactory.createContext` 增加 `modelId`/`reasoningLevel` 覆盖和 `disablePromptCache`，`shouldUseConversationModel` 保证同模型仍走会话模型，`withoutClaudePromptCache` 只对 `ProviderSetting.Claude` 复制 `promptCaching=false`；`ChatService.startCompression`/`runCompressionTurn`、`ChatVM.handleCompressContext`、`FilesPicker.onCompressContext` 透传，思考强度为 null 时沿用会话助手；四份 locale 新增高级设置、继承和状态文案
- 理由：压缩用的模型、思考强度和实时进度原来都看会话当前设置，想临时换便宜模型或降低思考强度只能改会话配置，改完还会影响下一轮正常对话；压缩耗时和 tok/s 也不可见。高级设置默认折叠，避免把偶发调整塞进主流程；模型选择不落盘，思考强度默认继承会话，只有选了具体等级才覆盖。换模型后请求前缀与上一轮不同，继续写 Claude 缓存没有意义，所以这次请求显式去掉 `cache_control`
- 合并注意：面板和服务端必须用同一套 `planCompressionFor`/`compressionSummaryMaxTokens`/`compressionInputBudget`，否则预览和实际压缩范围会不一致；计划不是 `Ready` 时确认按钮禁用，服务端仍保留自己的门禁。临时模型和思考强度只在 `startCompression` 的参数里存在，不写 `ConversationConfig`/`Settings`；`shouldUseConversationModel` 让选中当前模型时仍走 `getChatModelOf`，保留会话 `builtInSearch` 快照，只有模型 id 不同才用 raw provider model 并关闭 Claude 缓存。模型选择复用 `ModelSelector`，不再维护一套自定义下拉；管理上下文是 AlertDialog，模型抽屉是嵌套的 ModalBottomSheet，设备上验证可用。思考强度的「继承」用 null 表示，服务端继续走 `assistant.reasoningLevel`；选具体等级才覆盖，不改会话助手。高级设置默认折叠，临时改动只在这次压缩生效；计划预览不随高级设置收起。C81 不碰自动压缩，C74 的 80% 建议条仍是手动入口；C80 的 usage outcome 和 C51 的可见压缩轮次不变。压缩卡片的 tok/s 是估算值（流式文本或最终 usage），耗时按 reply.createdAt 粗算，不是 provider 的精确计时
- 测试：`PromptCacheOverrideTest` 四条覆盖 Claude 去缓存、非 Claude 原样、同模型走会话模型且不关缓存、换模型关缓存；`CompressionPlanTest` 增加预览条数对齐/user 检查点排除、流式 token 估算三条；全量 `:app:testGithubDebugUnitTest` 798 条，7 条既有 Windows 失败未变；`data.sync` 53 条、数据库迁移/DAO/live 4 条不受影响；assemble、spotless、markdownlint 通过。设备上打开管理上下文，确认默认折叠的高级设置、模型抽屉嵌套可用、思考强度默认「继承 (Auto)」、预览/确认状态正常；真实压缩请求的状态行因当前 provider 网络不可达未在设备上截到，卡片计时/估算由 JVM 估算测试和现有流式路径覆盖

<a id="c82"></a>

## C82 消息 HTML 清洗与 Mermaid 版本统一

- 落点：Web 正文改用显式 `rehypeRaw -> rehypeSanitize(svgSchema) -> rehypeSvgGuard -> rehypeKatex` 管线（`web-ui/app/components/markdown/` 下新增 `svg-schema.ts`、`rehype-svg-guard.ts`，`preprocess.ts` 收拢 LaTeX 归一化和 SVG 尺寸上限），`markdown.css` 给 `.markdown svg` 加 `max-width`，新增 `scripts/check-markdown-sanitize.ts` 与 `pnpm run check:markdown`；`mark.html` 的错误分支改用 `textContent`、Mermaid 围栏内容先 `escapeHtml` 再交给 mermaid、`mermaid.run` 加 `suppressErrors: true`；workbench 的 Mermaid 钉到 `esm.sh/mermaid@11.17.2`、`securityLevel: strict`、`htmlLabels: false`，iframe 去掉 `allow-same-origin`，SVG 预览加 CSP；`assets/html/mark.html` 引入 DOMPurify 3.4.17 清洗 `md.render` 结果、钉死 esm.sh 版本、Mermaid 改 strict、KaTeX 关 trust；workspace 的 `.svg` 预览在组合期间就把 `javaScriptEnabled` 置 false（AndroidView 的 update 会同步加载，放 SideEffect 会晚一帧）并限制 1M 字符；`assets/html/mermaid.min.js` 换成官方 11.17.2 IIFE（DOMPurify 3.4.12，SHA-256 记录在 `Mermaid.kt` 的 KDoc），Android 内嵌 Mermaid 加 strict/htmlLabels/maxTextSize/maxEdges
- 理由：Web 正文原来自己传 `rehypePlugins`，把 Streamdown 默认的 raw -> sanitize -> harden 整条顶掉，`<script>`、`onload`、`<foreignObject>`、`javascript:` 链接都能进 DOM；`mark.html` 和 workspace 的 SVG 预览没有清洗，其中 workspace 是顶层 SVG 文档，`<script>` 会真的执行；Mermaid 三处版本分别是 11.16.0 / 10.6.1 / 浮动 `@11`，前两者命中 2026-08-06 那批安全通告，且两处 `securityLevel: loose` 允许 HTML 标签和点击回调
- 合并注意：sanitize 必须排在 katex 之前，并且要放行 `span`/`div` 上以 `math` 开头的 class，否则公式不渲染；`hast-util-sanitize` 会把 `id` 加 `user-content-` 前缀，`rehypeSvgGuard` 负责把同文档的 `<use href="#x">` 以及 `fill`/`stroke`/`clip-path`/`mask`/`marker-*` 里的 `url(#x)`（含带引号和空白的写法）一起改前缀；SVG 只放静态元素，`style`/`foreignObject`/动画/滤镜和所有 `on*` 都不在 allowlist 里，`href` 允许 http/https/mailto/tel（`javascript:` 仍被拒），`xlink:href` 只允许 http/https；单块 SVG 超过 128 KiB（按字符数）或 1000 个元素时替换成提示文案，占位用 `span` 避免嵌在段落里产生非法嵌套。KaTeX 关掉 `trust` 后 `\href`/`\url` 会变成报错片段，这是有意的安全取舍。`style` 属性被移除会丢掉用户 HTML 的内联样式，这是刻意的。`mark.html` 为了 KaTeX 布局仍允许 `style` 属性，CSS 注入不在本轮范围；workspace 的 HTML 预览仍开 JavaScript（模型本来就能在工作区执行命令），SVG 改为静态。C135 起 Web 正文的 Mermaid 已启用；C134 起 `mark.html` 改为本地打包，不再依赖 esm.sh。
- 测试：`pnpm run check:markdown` 38 条，覆盖 allowlist（保留静态 SVG/math class/本地 `use`/mailto/tel，丢弃 script/style/foreignObject/事件属性/`javascript:`/`data:` 图片/style 属性/动画/iframe）、SVG 尺寸与代码块跳过、`use` 与 `url(#x)` 前缀改写、marker 属性、元素数上限与占位标签，以及完整 pipeline（标题、GFM 表格、行内代码、KaTeX 保留，脚本/事件/foreignObject 被清掉）；`pnpm run typecheck`、`pnpm run build` 通过；`:app:assembleGithubDebug` 与 `:app:testGithubDebugUnitTest` 798 条，7 条既有 Windows 失败未变；`data.sync` 53 条、数据库迁移/DAO/live 4 条不受影响；spotless、markdownlint 通过。设备上确认安装后能正常启动和打开聊天；Mermaid 图实际渲染、workspace SVG 的脚本不执行、`mark.html` 预览这三项没有在设备上逐项截图验证，原因是本轮环境里 provider 不可达、自动化输入和截图判读受限

<a id="c83"></a>

## C83 超长 diff 行崩溃修复

- 落点：`core/ui/src/main/java/me/rerere/ui/richtext/DiffView.kt` 新增 `MAX_DIFF_LINE_CHARS = 2000` 与 `truncateDiffLine`，渲染前截断单行并加省略号；新增 `core/ui/src/test/java/me/rerere/ui/richtext/DiffViewTest.kt` 三条
- 理由：`DiffView` 用 `Modifier.width(IntrinsicSize.Max)` 让每行背景铺满最宽的一行，横向滚动由外层提供。Intrinsic 宽度会变成 `Constraints` 的 maxWidth，而 Compose 的 Constraints 只有 18 位，超过 262143px 直接抛 `IllegalArgumentException`（崩溃日志里是 width 266179、height 0）。压缩过的 JS、base64、超长 JSON 单行很容易越过这个上限；小窗只是触发时机，根因是单行宽度没有上限
- 合并注意：只截断显示，复制与导出的内容不受影响；省略号表示被截断。2000 字符在 11sp 等宽字体、4x 密度、2x 字体缩放下仍远低于上限。`DiffView` 是上游代码，移植上游改动时保留这段截断；如果以后把 `IntrinsicSize.Max` 换成别的铺满方案，要重新评估这个上限是否还需要
- 测试：`DiffViewTest` 三条（短行不变、按上限截断并加省略号、50 万字符行仍在上限内）；`:ui:testDebugUnitTest` 通过；app JVM 798 条，7 条既有 Windows 失败未变；spotless、markdownlint 通过。崩溃现场（小窗 + 超长单行 diff）没有在设备上复现验证，判据是 Constraints 上限与截断后的宽度估算

<a id="c84"></a>

## C84 Gemini 独立 thoughtSignature 兼容

- 落点：`core/ai/src/main/java/me/rerere/ai/ui/MessageMetadata.kt` 新增 `standaloneGoogleThoughtSignature()` 与 `attachGoogleThoughtSignature()`（已有签名不覆盖）；`UIMessagePart.metadata` 从 `abstract val` 改成 `abstract var`，让基类扩展能直接挂签名；新增 `provider/providers/google/GoogleThoughtSignature.kt`，用 `GoogleThoughtSignatureQueue` + `parseGoogleParts` 统一处理相邻挂载，`GoogleProvider.parseMessageParts` 与 `GoogleStreamDecoder.parseMessage` 都改走它，后者把队列放在解码器实例上以跨 SSE 事件保留签名；新增 `GoogleThoughtSignatureTest` 六条与 `GoogleThoughtSignatureQueueTest` 五条
- 理由：上游 issue #2316。Gemini 思考模型和多轮工具调用会返回只含 `thoughtSignature` 的独立 part，两个解析器的 `when` 都落到 `else -> error("unknown message part type")`，整个聊天请求直接失败。签名要留着，回传历史时 Gemini 会校验
- 合并注意：挂到相邻 part 是启发式：优先挂前一个 part，前面还没有内容时排队给下一个；流式解码的队列挂在解码器实例上，签名可以跨事件保留，最终挂到事件里第一个出现的 part。part 已有签名时不覆盖。`metadata` 改成 `var` 只影响赋值能力，序列化形状不变。上游若重写 Google parts 解析，要保留这段判断，否则会退回崩溃
- 测试：`:ai:testDebugUnitTest` 335 条全过（新增 11 条：helper 6 条覆盖识别、带内容、空对象、合并、新建、不覆盖；队列 5 条覆盖签名在前/在后、纯签名不产内容、跨事件保留、非字符串签名）；app JVM 798 条，7 条既有 Windows 失败未变；assemble、spotless、markdownlint 通过。没有真实 Gemini 响应样本，判据是 issue 里的 JSON 形状和解析路径

<a id="c85"></a>

## C85 Claude 思考块签名与 redacted_thinking 回放

- 落点：`core/ai/src/main/java/me/rerere/ai/ui/MessageMetadata.kt` 的 `ClaudeReasoningMetadata` 增加 `redactedData`（序列化 key 为 `claude_redacted_data`）；`ClaudeProvider` 非流式解析把 `redacted_thinking` 存成带 `redactedData` 的 `Reasoning`（原来是 `println` 后丢弃），回传时遇到 `redactedData` 输出 `redacted_thinking` + `data`；`ClaudeStreamDecoder` 的 `content_block_start` 从 `data` 取 redacted 数据，`signature_delta` 接在同一块已收到的签名后面累加；`StreamChunkHandler.appendMessage` 不再把相邻推理块拼成一块；新增 `ClaudeThinkingReplayTest` 五条
- 理由：上游 #2206/#1629（提交 `e1667163` 标记 Fixes #2308）。思考块必须连同签名原样回传，之前有三处会改动它：非流式把相邻思考块拼成一块、只留下后一块的签名；流式 `signature_delta` 分片到达时只保留最后一片；`redacted_thinking` 非流式直接丢弃、流式存成没有签名的空思考块。签名校验失败会让续轮请求直接报错
- 合并注意：不再合并相邻推理块是有意的，每块的签名只对自己有效，代价是某些 provider 的非流式多步推理会分成多块显示。`claude_redacted_data` 是新 key，旧会话没有这个字段会反序列化成 null。流式签名累加依赖同一 `ClaudeStreamBlock` 的 metadata 更新，如果以后上游改成按 id 合并推理块，要保留累加逻辑
- 测试：`:ai:testDebugUnitTest` 340 条全过（新增 5 条：非流式相邻块各自保留签名、pause_turn 续轮不合并不同响应的思考块、非流式与流式 redacted_thinking 回放、流式签名分片累加）；app JVM 798 条，7 条既有 Windows 失败未变；assemble、spotless、markdownlint 通过。没有真实 Claude 响应样本，判据是上游同款用例和构造的 SSE 事件
- 追加（C97）：中转层按原生思考缓存校验时，历史 assistant 的 thinking 与当前 step 的 tool_use 拼在一条消息里会报 `native_thinking_replay_mismatch`。有 step 范围的消息现在按 step 拆分回放，thinking 只和同一 step 的 tool_use 配对；旧消息没有 step 范围时，自定义中转不再回放 thinking，官方域名保持原行为。测试见 C97

<a id="c86"></a>

## C86 思考强度 NOTSET 与 OFF 分离

- 落点：`ReasoningLevel` 增加 `NOTSET`（序列名 `notset`），表示请求里不发档位/强度参数（`include`、`thinking_summaries` 这类配套字段不在此列）；`OFF` 保留为明确关闭。`TextGenerationParams.reasoningLevel` 默认改为 `NOTSET`，OCR 和 Qwen MT 翻译这类不指定档位的内部调用不再带上 `OFF`。ChatCompletions、ResponseAPI、Claude、Google、Interactions、Grok 都先处理 `NOTSET` 为省略，ResponseAPI 的 reasoning summary 也一并省略；OFF 在支持显式关闭的 provider 上继续发 disabled/none，通用 OpenAI 兼容网关（含 `opencode.ai`）仍按 C86 原结论省略，避免 MIMO 只接受 low/medium/high 时 400。`ReasoningPicker` 与 Web reasoning picker 都增加默认档位与对应文案；`NOTSET` 的 budget 哨兵是 `-2`，`fromBudgetTokens` 先识别哨兵再排除它，翻译设置仍能选中默认档；标题/记忆直接存枚举，不受影响。
- 理由：上游 #1644。OCR 原来走默认 `OFF`，请求体带 `reasoning_effort: "none"`，OpenCodeGo 网关（MIMO2.5）直接 400。把“不设置”和“明确关闭”拆开后，内部调用不再猜 provider 的默认值，用户仍能显式关闭思考。
- 合并注意：`NOTSET` 是 `Settings` 和会话 config 都会遇到的枚举值，官方客户端不认识；官方兼容出口用 `Settings.forOfficialExport()` 把 assistant、fast、memory 三处归一化成 `OFF`，`OfficialDatabaseAdapter` 同时把 `conversationentity.config` 里的 `"reasoningLevel":"notset"` 替换成 `"off"`。自家备份不经过归一化。通用网关对 `OFF` 仍是省略，这是 C86 的既有取舍；要真正关闭默认开启思考的模型，仍需按 provider 在模型能力表里声明。`ResponseAPI` 的自定义 `reasoningEffort` 回调也排除 `NOTSET`。上游 `6188593c` 只处理了 else 分支，移植时保留本分支的六处 provider 判断和官方归一化。官方 DB 侧的 `conversationentity.config` 用紧凑 JSON 的 REPLACE 匹配，带空白/pretty JSON 不在现有写入路径上，尚无 androidTest。
- 测试：`ChatCompletionsReasoningEffortTest` 七条（opencode/generic 的 OFF 与 NOTSET 省略、MEDIUM/HIGH 保留、AUTO 不发、openrouter 的 NOTSET 无 reasoning 块且 OFF 发 none）；`ReasoningLevelTest` 覆盖 `-2` 哨兵、AUTO 与 OFF 的 budget 映射；`ChatGPTProviderTest` 覆盖 NOTSET 省略 reasoning 块；`OfficialSettingsCompatibilityTest` 增加 NOTSET 归一化成 OFF 的用例；`:ai:testDebugUnitTest`、`:app:testGithubDebugUnitTest`、assemble、spotless、markdownlint、`pnpm run typecheck` 通过。官方客户端对归一化后 JSON 的真实恢复仍按 `backup/acceptance.md` 的样本待验。

<a id="c97"></a>

## C97 Claude step 级请求投影与失败工具事实

- 落点：`core/ai/ui/Message.kt` 增加 `MessageStep`、`StepStatus` 与 `UIMessage.steps`；`AgentRequest.allowToolApproval` 与 `AgentFinishReason.ApprovalUnavailable` 让中间节点生成不进入审批，`ChatService` 用 `settleUnavailableTools()` 给未执行工具补恢复结果、丢掉 `callComplete=false` 的半截调用、把消息置 COMPLETE 并把 step 记 SUCCEEDED；无 job 的审批停止会先加载会话，按最后一条 assistant 的 id 标 INTERRUPTED 后一次 flush；`isAwaitingToolApproval`/`pendingToolCalls` 对有 steps 的消息只看最后一条 assistant，旧消息仍扫描全部；`withToolApproval`、聊天列表的审批回调与悬浮窗同步收口；`UIMessagePart.Tool` 增加 `callComplete`，流式 `ToolCallStart` 置 false、`ToolCallEnd` 置 true，`merge` 取逻辑或；`AgentLoop` 在每次请求成功后追加 step，记录 part 范围与 RUNNING/TOOLS/SUCCEEDED/FAILED 终态；`ClaudeProvider` 对带 step 的消息按 step 拆分 assistant/user 消息，thinking 只随同一 step 的 tool_use 回放，旧消息和带 `legacy` 前缀的消息在自定义中转下不回放 thinking；`projectForRequest()` 替代聊天请求里的 `filterModelVisible()`，带 steps 的消息按 step 状态投影并重映射 step 范围：成功 step 保留正文与工具事实，失败 step 只保留已执行 Tool parts 与已完成 ServerTool，TOOLS/CANCELLED/RUNNING 保留正文与已执行事实；没有 steps 的旧失败消息仍只保留工具事实；`ConversationMessages` 的取消/加载恢复关闭未终结 step，工具都已执行时收 SUCCEEDED，否则 CANCELLED
- 理由：一个 turn 仍是一条 assistant 消息，多个原生响应追加在同一条 parts 里。中转层按原生思考缓存校验时，前一步的 thinking 和后一步的 tool_use 拼在同一条消息里会被判成关联不匹配；失败消息整条过滤又会把已经执行的写入和查询从模型历史里抹掉。step 范围让请求重建能恢复原生配对，失败投影让“继续”还能看到失败前的工具事实
- 合并注意：`steps`、`callComplete` 都是带默认值的字段，旧消息解码为空 steps、true；`MessageStep.legacy` 只用于接管旧消息时补的前缀范围，带 legacy 的消息不做原生 pairing 回放。自定义中转（非 `api.anthropic.com`）的旧消息不回放 thinking；官方域名保持原行为。请求投影对所有带 steps 的消息保留重映射后的范围；摘要投影里失败消息清空 steps 并把状态改为 COMPLETE，非失败消息保留状态与重映射后的范围；原始消息不受影响。本阶段做 step 记录、请求投影、失败工具事实、审批/取消结算与最后节点审批；自动重试 C75 已默认关闭恢复，C76 attempt 摘要已持久化，剩余消费方收尾仍按 B2 设计处理；手动 step 重试见 C98；C58 的记忆过滤和 C67 的加载恢复语义不变；摘要过滤已由 C122 改为 step 级
- 测试：`AgentLoopTest` 覆盖中间节点 `ApprovalUnavailable`；`ConversationMessagesTest` 覆盖中间节点按 messageId 补结果、丢弃半截调用、置 COMPLETE 并收 SUCCEEDED；`ClaudeThinkingReplayTest` 覆盖 legacy 全剥离、step 内 thinking 与 tool_use 配对、跨 step 的纯文本 step 不把 thinking 带到下一步；`UIMessageSerializationTest` 覆盖旧 JSON 空 steps、steps/callComplete 往返、FAILED 请求投影保留已执行工具并丢掉正文、非 FAILED 消息丢弃 IN_PROGRESS ServerTool 且失败消息保留已完成 ServerTool、多 step 失败消息保留成功 step 正文并重映射范围、TOOLS step 丢弃未执行工具；`MessageSummaryProjectionTest` 覆盖摘要投影丢弃 IN_PROGRESS ServerTool、成功 step 的媒体与 annotations 剔除、RUNNING step 丢弃；`ConversationMessagesTest`、`ConversationToolCallsTest` 覆盖停止/加载恢复关闭 RUNNING/TOOLS step、已执行工具收 SUCCEEDED 和最后一条 assistant 的审批收口；`:ai:testDebugUnitTest`、`:app:testGithubDebugUnitTest`、`assembleGithubRelease`、spotless 通过。设备上长工具链连续 48 次请求、8510 条消息无 `native_thinking_replay_mismatch`，消息序列无连续 assistant、无 thinking 落在 tool_use 之后；官方 3.1.1 已导入带 `steps`、`callComplete`、`attempts` 的 2 step 工具会话并正常显示，也已导入去掉 `steps`（含其内 `attempts`）和 `callComplete` 的普通消息样本，官方库 15 个节点、助手消息没有 `steps` 字段且正文可读；U+FFFD 见 C100

<a id="c98"></a>

## C98 消息操作手动重试失败 step

- 落点：`UIMessage.retryTarget()` 在消息为 FAILED/INTERRUPTED 时返回两种目标。最后还有 FAILED、CANCELLED、RUNNING 或 TOOLS step 且该 step 没执行过工具时返回 `Step`，截断到该 step 的 `startPartIndex`，删掉该 step 及之后的记录；没有失败 step、所有 step 都 SUCCEEDED 且所有工具都已执行时返回 `Continue`，保留现有 parts 与 steps，只复位消息状态并从末尾继续。两种情况都要求目标仍是最后节点与当前候选，并用当前会话模型同步 `modelId`/`modelSnapshot`；`ChatService.retryMessageStep` 在 `canRegenerate()` 通过后加载会话、二次检查审批/frozen，落库后复用 `runTurn(session)` 从同一条 assistant 消息继续，不新建候选。聊天消息快捷按钮与更多菜单只在最后一条 `retryTarget()` 非空的 assistant 上出现，文案 `chat_message_retry_step`（四份 locale）。
- 理由：C77 的错误卡片重试永久取消，失败或中断的 step 需要一个人工入口；原位续跑保留此前 SUCCEEDED step 的正文与工具事实，不把整轮回复重放，也不产生分支。重试 step 与整轮重新生成在快捷栏和更多菜单都使用不同图标（`ArrowTurnBackward` / `Refresh03`），避免两个圆形箭头混淆。
- 合并注意：只处理最后节点，中间节点或已切走分支返回 null；可截断的 step 已执行过工具时入口隐藏，防止丢掉有副作用的工具事实；`Continue` 只保留全部已执行工具和已完成 step，未执行工具存在时返回 null；`runTurn` 开头的 `withoutUnresumableToolCalls` 仍会清理前缀里无法恢复的工具，`Step` 目标已经被截掉，`Continue` 目标保持原样；usage/stepCount 不重置，新 step 继续累计。`ChatError` 不增加 retry 字段，入口只依赖消息本身；自动重试默认关闭，但用户可在网络设置开启。UI 只在 `lastMessage` 且 `retryTarget()` 非空时传回调，避免在旧消息上露出无效入口。`Continue` 覆盖“下一步请求在第一个流事件之前失败”的情况：此时失败没有落成新 step，旧实现会因为没有可截断的 step 而完全隐藏入口。
- 测试：`ConversationMessagesTest` 覆盖截断到失败 step、保留前缀 step、复位状态、非最后节点、已完成消息、已执行工具 step 返回 null，所有 step 已成功时保留内容从末尾继续、INTERRUPTED 的继续与模型快照同步、存在未执行工具时不继续；`:ai:testDebugUnitTest`、`:app:testGithubDebugUnitTest`、assemble、spotless 通过；手动重试的 `ChatService` 任务接线没有 JVM 单测，留设备验证。

<a id="c99"></a>

## C99 失败请求记录完整 provider 响应体

- 落点：`core/ai/util/ErrorParser.kt` 新增 `Response.logFailureBody(body)`，只记录请求方法、`encodedPath` 和 HTTP 状态码，不记录 query、header 或 URL 中的 key；响应体按 1000 个 code point 分块写 logcat，标记 `ProviderError`，避免单条日志被截断和拆坏代理对。
- 接线：`fetchBody()` 的非 2xx 路径记录后抛错；Claude、Google generateContent、OpenAI Chat Completions、OpenAI Responses、Google Interactions 的非流式非 2xx 在抛错前记录，流式 `onFailure` 在解析错误体前各记录一次；`toHttpException()` 不再隐式记录，避免同一条响应出现重复或空 body。成功响应不记录。
- 理由：provider 的 400/401/429 等错误经常只在响应体里给出具体原因，例如 OpenCode Go 的 `Requests ending with a model turn are not supported`；只把解析后的 message 放进错误卡片会丢掉其余字段。logcat 长行有上限，分块才能保留完整正文。
- 注意：响应体可能包含用户内容或回显的请求片段，日志只留在本机 logcat；不要把日志片段贴到外部。请求 URL 只取 path，防止 query 里的 key 落盘。
- 测试：编译通过；用 `adb logcat -s ProviderError` 或 logcat Extreme 抓取，验证一次非 2xx 请求出现完整分块。暂无 JVM 单测，日志辅助路径留设备验证。

<a id="c100"></a>

## C100 U+FFFD 替换字符与官方 Claude 行为对齐

- 现状：待调研，低优先级，尚未排期（2026-11-16 重新登记）。经中转使用 Claude 时，回复偶尔出现 U+FFFD 替换字符；用户侧对照显示同一中转下官方 Claude Code 正常，中转方称不是服务端问题。目前没有确认问题落在 SSE 分块、UTF-8 边界、流式文本合并、客户端解码还是中转改写，不能先按“中转坏了”或“客户端坏了”下结论。
- 计划：等 B2 的剩余消费方和 C87 之后再排。用同一 prompt 对照官方 Claude Code、官方 API 与中转，保留原始 SSE 字节和客户端解码结果，确认第一个 U+FFFD 出现在哪一层，再决定改 `ai` 的 Claude decoder/UTF-8 处理，还是只记录兼容性说明。官方 Claude Code 只作为行为参照，不直接移植其实现。
- 合并注意：这是独立的行为对齐项，不阻塞 C75/C76；不要为它引入全局的替换字符清洗，避免掩盖真正的编码错误。C97 的长工具链设备验证已把 U+FFFD 单独分开，本次只是重新登记。
- 测试：暂无；需要可复现样本、原始 SSE 字节和官方 Claude Code 的对照结果。

<a id="c107"></a>

## C107 B2 step 稳定标识与失败码

- 落点：`core/ai/ui/Message.kt` 的 `MessageStep` 增加带默认值的 `id` 与 `failureCode`；`AgentLoop` 在失败结算时按 `LlmFailure` 错误码填 `failureCode`，未知异常填 `UNKNOWN`，成功/取消保持 null。
- 理由：step 后续要作为错误卡片、Web DTO 和日志的稳定单位，不能再靠 part 下标或消息 id 猜；失败码也让消费方不必重新解析异常类型。
- 合并注意：字段都有默认值，旧 JSON 解码时 `id` 会新生成，`failureCode` 为 null，不会破坏旧消息；新字段只能追加在构造参数末尾，不能插到现有的前四个必填字段之前，否则位置参数调用点会错位。重试决策仍只看 `LlmFailureCode`，不要把 `failureCode` 当作重试开关。旧构建保存新会话时会丢掉这两个字段，属于 B2 降级限制。
- 测试：`UIMessageSerializationTest` 覆盖 id/failureCode 往返；`AgentLoopTest` 覆盖失败 step 的 `UNKNOWN` 错误码；`ai` 与 app 的 JVM 测试通过。

<a id="c121"></a>

## C121 聊天正文的 SVG 内联预览

- 落点：`splitSvgText` 只拆用空行隔开、独占一行的块级 SVG；`InlineSvgBlock` 用 Coil 的 `AsyncImage`（全局 `SvgDecoder`）在气泡内直接渲染，默认最高 240dp，点卡片展开到 480dp；文本段各自保留 `SelectableContent`，SVG 卡片放在选择容器外，避免点击被吃掉。不安全的 SVG、超大 SVG 或解码失败时，才在该位置回退到 `Render with WebView` 整条消息预览。
- 理由：Android 正文不渲染 SVG，BetterText 直接丢弃；之前只给气泡底部加 WebView 入口，位置和交互都不自然。内联渲染让 SVG 停在原文位置，展开一级即可看细节。
- 合并注意：只处理已完成的文本 part，生成中仍走原 Markdown；只接受独占一行且前后为空行的 SVG，inline code、blockquote、缩进代码块和 fenced/nested code 里的 `<svg>` 不拆；内联安全限制是 64K 字符，拒绝 `<script>`、事件属性、`<foreignObject>`、`<image>`、`<use>`、`<style>`、`href`/`xlink:href` 和外部 CSS `url()`；允许 `url(#id)`、`url('#id')`、`url( #id )` 这类本地渐变；不新增文案，回退复用 `render_with_webview`；不改 Markdown 渲染器，也不在气泡里常驻 WebView。
- 测试：`SvgTextTest` 19 项覆盖独占块拆分、无空行/强调/链接/列表/表格不拆、inline code、fenced/language/nested/blockquote/indented code、CRLF、未闭合、多 SVG、安全限制和 64K 边界；`:app:testGithubDebugUnitTest --tests "me.rerere.rikkahub.ui.components.message.SvgTextTest"` 通过；设备上在 `3.1.1-custom.3431+34e9ca8` 验收：mock 返回含 `<svg>` 的文本时气泡内出现圆形，点卡片后高度展开、箭头翻转。

<a id="c122"></a>

## C122 B2 标题与摘要的 step 级投影

- 落点：`core/ai/ui/Message.kt` 的 `projectForSummary()` 对带 steps 的消息按 step 重建 parts（失败消息是其中一种）：`SUCCEEDED` 和 `TOOLS` 保留正文、推理、已执行完成的 Tool 和已完成 ServerTool，`FAILED`/`CANCELLED` 只保留已执行完成的 Tool 和已完成 ServerTool，`RUNNING` 丢弃；媒体与 annotations 一律丢弃；失败消息投影后状态改为 `COMPLETE` 并清空 steps，非失败消息保留原状态与重映射后的 steps。标题的 `Conversation.recentModelMessages` 和压缩摘要的 `ChatRequestFactory.buildStep(forSummary = true)` 使用它；`CompressionPlan` 的 token 估算同步改用同一投影和同一丢空/scratch 过滤。记忆的 `dialogueMessages` 仍走 `filterModelVisible`，保持整条隔离。
- 理由：一条失败回复里可能已有成功的 step，整条丢弃会把成功 step 的正文和工具事实一起丢掉；但失败 step 的正文可能没写完或说错，不能直接当事实。标题和摘要是一次性或请求级的，先按 step 取成功部分；记忆是长期保存的，继续整条隔离。
- 合并注意：带 steps 的消息都走这套投影，不再只限 FAILED；没有 steps 的旧失败消息仍整条丢弃，旧的非失败消息只丢弃 `IN_PROGRESS` ServerTool，避免把兼容例外带进新投影。投影只读，不改库；普通聊天请求用 `projectForRequest`，两者不能混用。`TOOLS` 保留正文、推理和已执行完成的工具，`FAILED`/`CANCELLED` 只保留工具事实；`ServerTool` 只保留 `COMPLETED`/`FAILED`，`IN_PROGRESS` 丢弃；媒体和 annotations 不进摘要投影，`Tool.output` 里的媒体也会被剔除，只剩媒体的工具整条丢弃；`RUNNING` step 整段丢弃；投影为空时整条丢弃。
- 测试：`MessageSummaryProjectionTest` 18 项覆盖成功 step、失败/取消/运行中 step、纯文本失败、工具执行完成与 `callComplete` 边界、`Tool.output` 媒体剔除、ServerTool 状态、媒体与 annotations、越界 part 和非失败纯文本消息保持原样；`ConversationRecentModelMessagesTest` 4 项覆盖失败回复带成功 step 时进入标题上下文、投影为空时仍排除以及 scratch 过滤。`:ai:testDebugUnitTest --tests "me.rerere.ai.ui.MessageSummaryProjectionTest"` 与 `:app:testGithubDebugUnitTest --tests "me.rerere.rikkahub.data.ai.ConversationRecentModelMessagesTest"` 通过。

<a id="c130"></a>

## C130 相邻推理块重复显示为生成中

- 落点：`ChatMessageReasoningStep` 新增 `canBeLoading` 参数，`rememberReasoningState` 的 `loading` 由 `reasoning.finishedAt == null && canBeLoading` 计算；`MessagePartsBlock` 用 `parts.lastOrNull { it is UIMessagePart.Reasoning }` 取出整条消息里最后一个推理 part，只有它的身份与当前 step 相同时才允许 `loading`。这与文本块 `loading && block.index == parts.lastIndex` 是同一个思路，但推理块后面常跟文本块，所以不能直接复用 `parts.lastIndex`。
- 理由：渲染层原来只看 `finishedAt == null`。Claude 协议里一条响应可以出现多个 thinking 块；中转漏发前一个块的 `content_block_stop` 时，这个 part 不会立刻收到 `ReasoningEnd`（解码器的 `finish()` 要等到 `message_stop` 才给残留块补 End），`finishedAt` 在整段流式期间都为空，于是两个块同时显示"思考了 xx 秒"的滑动高光，而实际只有最后一个还在接收内容。`finishReasoning()` 也只在每步流结束时统一补 `finishedAt`，所以这个错误状态会持续整个生成过程。
- 合并注意：这是显示层兜底，不改协议解析；一个响应里 thinking 块是顺序产生的，因此"只有最后一个可能仍在产出"成立。更早的未收尾 part 仍留在消息里，它的 `finishedAt` 由每步流结束时的 `finishReasoning()` 补上；不要顺手过滤 `reasoning` 为空的 Reasoning part，`redacted_thinking` 就是空正文加 metadata，C85 要靠它回放。`content_block_stop` 之后迟到的 delta 不是本项的成因：块已从 `blocks` 移除，解码器会直接报 `Unknown content block index` 并让这条流失败。
- 测试：`:app:compileGithubDebugKotlin` 通过；设备上用同一个 Anthropic 中转复现双 thinking 块时，只有最后一块继续显示滑动高光并计时，前一块立即变成静态的"思考了 xx 秒"，正文与折叠行为不变。

<a id="c131"></a>

## C131 定期保存失败的用户提示

- 落点：新增 `SaveWarningStore` 按会话保存 `MessageSaveWarning`（`WRITE_FAILED`、`TOO_LARGE`），`ChatService` 暴露 `saveWarnings` 与 `dismissSaveWarning`，并把生成与压缩两条 `MessageSaveLoop` 的 `onError`/`onTooLarge` 接到它，定期保存成功、生成结束的 `finishGeneration()`、压缩完整落库和发送前 `session.flush()` 时清除；`ChatVM` 用 `saveWarning` 只投影当前会话；`ChatPage` 透传，`ChatList` 底部浮层新增 `MessageSaveWarningCard`，关闭按钮带无障碍标签；四份 locale 加 `chat_page_save_warning_write_failed` 与 `chat_page_save_warning_too_large`。
- 理由：两条保存循环原来只 `Log.w`，用户完全看不到。生成到一半存储失败或节点过大跳过保存时，界面一切正常，最后可能整段回复没落盘。错误卡片（C71）表达的是生成失败，语义不同，所以单独做一条轻量警告。
- 合并注意：警告不落盘，进程重启后重新判断；不只是定期保存成功会清除，生成结束的 `finishGeneration()`、压缩成功/失败的完整落库、发送前的 `session.flush()` 也都会清，否则一次瞬时失败会让卡片在整轮结束甚至下一轮里一直挂着；用户手动关闭同样清除，不自动超时；不要混进 `ChatError` 通道，两者语义和生命周期不同；生成与压缩两条循环都要接，否则压缩摘要的保存失败仍然静默；节点超过 16MB 时 `MessageSaveLoop` 本来就会跳过并回调 `onTooLarge`，这里只负责展示，不再重试；`persistMessageNode` 因节点漂移或含 base64 图片返回 null 时走的是 C50 的静默跳过，不触发本警告，那部分界面仍显示正常是有意为之。
- 测试：`SaveWarningStoreTest` 4 条覆盖按会话记录、原因覆盖、只清指定会话、未知会话清除无副作用；`:app:compileGithubDebugKotlin` 通过。写失败与超限都没有在真机上主动注入触发，警告卡的实际展示未做设备验证。

<a id="c133"></a>

## C133 按协议清除历史思考签名

- 落点：`ThoughtSignatureProtocol` 只收录可以安全清除的两种 Google 协议，并各自列出 metadata key 与嵌套 key（generateContent 的 `thoughtSignature`；Interactions 的 `interactions_signature` 与原始块里的 `signature`）；`UIMessagePart.stripThoughtSignatures(protocols)` 返回新 part，删除顶层 metadata key，并对 `server_tool_call`/`server_tool_result` 里的原始 JSON 递归清理；`UIMessage`/`Conversation` 同样返回新对象；`ChatService.stripThoughtSignatures(conversationId, protocols)` 生成中拒绝并整会话落库，`ChatVM` 捕获 `ApiException` 后提示先停止生成；会话列表长按菜单新增「清除思考签名」，弹窗按协议勾选（默认 Google generateContent），四份 locale 加标题、说明、确认和两个协议标签。
- 理由：Gemini 的思考签名会过期，openrouter 上的 Gemini 隔几天再用同一会话会直接报 400 corrupted thought signature。只提供 Google 两种协议，是因为 Claude、OpenAI Responses、OpenRouter 的签名块在重放时必须原样带回，单删签名字段会造出非法块，换一种 400；那几个协议要等 provider 侧重放能跳过无签名块后再开放。
- 合并注意：必须返回新 part，不能原地改 `metadata`；原地改会让新旧 `Conversation` 相等，`StateFlow` 的相等性判断会跳过更新，`flush` 不会写库，重启后签名又回来；`server_tool_*` 里保存的是 provider 原始块，签名可能嵌在里面，只删顶层 key 对服务端工具会话无效；只删 metadata，推理正文与节点结构不动，因此不可恢复，必须弹窗确认；生成中或等待审批时 `ensureNotGenerating` 抛 `ConflictException`，`ChatVM` 捕获后提示，不能裸 `launch`；协议 key 与嵌套 key 必须和 `MessageMetadata.kt`、GoogleProvider/InteractionsAPI 的原始块结构保持一致，上游改字段时同步。菜单图标用 `HugeIcons.BrushCleaning`：HugeIcons 没有扫帚图标，原来的 `Brush` 是画笔，容易被读成“绘制”；`BrushCleaning` 是清洁刷，语义更接近清理签名。
- 测试：`ThoughtSignatureStripTest` 5 条覆盖只删所选协议、原 part 不被修改、server tool 嵌套签名清理、空选择返回同一实例、消息整体变成不同的值；`ThoughtSignaturePersistenceTest` 1 条用 `ConversationSession` + `FakeConversationStore` 确认剥离后快照确实不同、`flush` 真的写库、落库后的 part 已无签名且原快照未被改；`:ai:testDebugUnitTest --tests "me.rerere.ai.ui.ThoughtSignatureStripTest"`、`:app:testGithubDebugUnitTest --tests "me.rerere.rikkahub.service.ThoughtSignaturePersistenceTest"` 与 `:app:compileGithubDebugKotlin` 通过。设备上从会话列表长按菜单打开弹窗，确认两个协议选项、默认只勾 Google（generateContent）、文案与 Cancel/Clear 按钮正常；为避免改动真实历史，没有实际执行清除。

<a id="c134"></a>

## C134 mark.html 离线渲染

- 落点：新增 `tools/mark-assets/`（`package.json` 固定版本、`build.mjs`、README），用 esbuild 把 markdown-it、@vscode/markdown-it-katex、markdown-it-task-lists、katex、highlight.js、js-base64、dompurify 打成单个 IIFE（`window.MarkAssets`），输出 `app/src/main/assets/html/mark.bundle.js`，并把 KaTeX 与 highlight 的 CSS、KaTeX 的 60 个字体文件复制到同目录；打包时从 esbuild metafile 收集所有被打包依赖的许可证，生成 `THIRD_PARTY_LICENSES.txt` 随 APK 分发；mermaid 的 MIT 文本 vendored 在 `tools/mark-assets/third-party/mermaid-11.17.2.LICENSE`，缺失时构建直接失败；`mark.html` 改成经典 script 加载本地 bundle，mermaid 继续用仓库已有的 `mermaid.min.js` IIFE；模板里用 `{{ASSET_BASE}}` 占位，`MarkdownWeb.kt` 替换成 `WEB_VIEW_ASSET_URL/html/`，经 `WebViewLocalAssets` 映射到 APK assets。
- 理由：页面原来从 esm.sh 与 jsdelivr 动态 import，断网或 CDN 不可达时整块 WebView 预览是空白。打成 IIFE 而不是 ESM：页面由 `loadDataWithBaseURL` 以虚拟域名加载，经典 script 加全局不需要处理模块解析差异，模板也只需替换资源前缀。
- 合并注意：生成的 `mark.bundle.js`、CSS、字体、许可证文件全部入库，CI 不运行打包；升级任一库时改 `package.json`、重跑 `pnpm run build` 并提交产物。`mark.html` 里所有静态资源都必须走 `{{ASSET_BASE}}`，漏一个就回到线上依赖；KaTeX 的 CSS 保持相对 `fonts/`，由虚拟资源路径解析，不要改成绝对 file 路径。mermaid 不在 bundle 里，避免与 `mermaid.min.js` 出现两份版本。新增资产约 2.86 MiB（bundle、CSS、字体、许可证），加上原有的 `mermaid.min.js` 后整个 `assets/html` 约 6.27 MiB。
- 测试：本地 `file://` 打开注入样例的页面，标题、行内公式、mhchem 块公式、highlight.js 代码块、mermaid 图、任务列表均渲染，`document.fonts.check('12px KaTeX_Main')` 为真，控制台无错误，页面无任何 http(s) 资源；APK 里确认 66 个 `assets/html` 文件、共约 6.27 MiB，其中新增部分约 2.86 MiB。没有在真机 WebView 里逐项截图验证，`WebViewLocalAssets` 的拦截路径只做了静态核对。

<a id="c135"></a>

## C135 Web 正文 Mermaid

- 落点：`web-ui` 新增 `@streamdown/mermaid@1.0.4`；`mermaid-diagram.tsx` 作为 `React.lazy` 模块，内部再动态 `import("@streamdown/mermaid")`，用 `createMermaidPlugin` 固定 `startOnLoad: false`、`securityLevel: "strict"`、`htmlLabels: false`、`fontFamily: "inherit"`，主题随 `<html>` 的 `light`/`dark` class 切换；渲染前用 `sanitizeMermaidSource` 归一化 CR 换行、剥掉闭合或未闭合的 `%%{...}` 指令和带缩进的 YAML frontmatter；`markdown.tsx` 的自定义 `code` 分支在语言为 `mermaid` 且消息不再流式时交给该模块，用插件实例的 `getMermaid().render` 生成 SVG，带 loading、错误回退和唯一 id；代码块其余语言仍走原来的 `CodeBlock`。
- 理由：Web 正文此前只显示 mermaid 源码。Streamdown 自带的图渲染器在它的 `code` 组件内部，而自定义 `code` 为了代码预览覆盖了它，所以这里直接复用同一个插件实例渲染，避免引入第二套配置。
- 合并注意：引擎在独立的懒加载 chunk 里（约 0.68 MB，gzip 162 KiB），第一次出现 mermaid 围栏时才请求；聊天路由本身还有一个约 1.36 MB（gzip 403 KiB）的 route/streamdown/mermaid UI chunk，随页面加载，但不含引擎。`securityLevel` 与 `htmlLabels` 只约束 mermaid 的标签清洗，不等于 CSS 安全：围栏里的 `themeCSS` 可以通过闭合或未闭合的 init 指令、单引号、CR 换行或缩进 frontmatter 注入外链，所以必须在渲染前整段剥离，不能只依赖 strict。SVG 仍通过 `dangerouslySetInnerHTML` 注入，自定义渲染器绕过了仓库的 `svgSchema`/`rehypeSvgGuard`；`rehypeSanitize` 在 mermaid 之前，围栏内容对它是代码文本。流式期间保留源码，等 `isAnimating` 结束再渲染；图的控件没有启用，错误时展示源码而不是空白。
- 测试：`pnpm run typecheck`、`pnpm run check:markdown`、`pnpm run build` 通过；用 esbuild 把真实 `Markdown` 组件打进临时页面，覆盖两张普通图、闭合指令、未闭合指令、CR-only frontmatter、缩进 frontmatter、单引号 themeCSS、指令+恶意 frontmatter、双 frontmatter、指令+双 frontmatter 三种组合，以及 9 段 benign 加第 10 段恶意 frontmatter、非法围栏和流式消息，并断言 sanitize 对组合输入和超过旧上限的多段 frontmatter 都幂等：所有恶意围栏都能出图，但生成的 `<style>` 与 fill/style 属性里没有 attacker.example，也没有对应网络请求；非法围栏显示错误回退；`isAnimating=true` 时保留代码块，切成 false 后出现 SVG；切 `dark` class 后 3 个 SVG 仍正常，控制台无错误。没有测试渲染中切换主题的取消，也没有在完整 Web UI 路由与真机上逐项验证。

<a id="c137"></a>

## C137 测试连接弹窗不再被键盘关闭

- 落点：`ProviderConnectionTester` 拆成 `ProviderConnectionTestDialog`，只保留弹窗内容与 `onDismissRequest`；`showTestDialog` 状态移到 `SettingProviderConfigPage`，工具栏里的按钮只负责置位，`AlertDialog` 放在工具栏和外层 `Box` 之外。来源是上游 `a75f3f9c`。
- 理由：测试连接入口在悬浮工具栏里，键盘弹出时工具栏会移出 composition；弹窗如果挂在这个按钮下，点模型搜索框弹键盘会连同弹窗一起销毁，看起来像自动关闭。
- 合并注意：开关必须由调用方持有，不要在工具栏内部 `remember`；`ProviderConnectionTestDialog` 不要再包一层入口按钮。后续上游如果重排这个页面，保持「弹窗在工具栏外」这个结构。
- 测试：`:app:compileGithubDebugKotlin`、`spotlessCheck` 通过；设备上打开 OpenAI 提供商的测试连接弹窗，点模型选择器再点搜索框（弹出键盘），关掉选择层后弹窗仍在，未被销毁。

<a id="c140"></a>

## C140 内置助手可删除

- 落点：`SettingsNormalization` 不再按 id 补齐内置助手，只在列表为空时补回 `DEFAULT_ASSISTANTS`；`withoutInvalidReferences` 在选中的助手失效时回退到第一个；`AssistantVM.removeAssistant` 改到 `AppScope`，在同一次更新里按最新列表检查，至少保留一个，确实删除后才清理记忆与会话。来源是上游 `ce0e3607`。
- 理由：此前内置助手删不掉，删完后 `assistantId` 还可能悬空；连续删除时旧实现可能把最后一个助手也删掉。
- 合并注意：本分支没有上游的 `cleanupAssistantFiles`/`FilesManager` 清理路径，移植时去掉了该调用，只保留记忆和会话清理；删除仍走设置更新，不直接改列表。
- 测试：`:app:compileGithubDebugKotlin` 与 `SettingsStoreTest` 通过；未在设备上实际删除默认助手。

<a id="c141"></a>

## C141 消息概览标出压缩检查点

- 落点：`ChatList.kt` 的预览列表遇到 `isContextCheckpoint` 消息时，渲染独立的 `CheckpointPreviewCard`（Package01 图标、secondaryContainer、整行可点跳转）；按 `isHistoryTruncationMarker` 区分历史截断与上下文压缩，分别使用 `chat_page_truncate_checkpoint_title` 和 `chat_page_compress_checkpoint_title`。来源是上游 `63a5192d`。
- 理由：预览里看不出上下文压缩边界；单独卡片能把检查点和普通消息区分开。
- 合并注意：本分支没有上游的 `ChatListPreview.kt`（已并入 `ChatList.kt` 的消息列表），所以只移植卡片语义，不做上游的 round 分组；检查点消息仍占一个原始 index，跳转逻辑不变；`isContextCheckpoint` 同时覆盖空的历史截断标记，标题必须按 `isHistoryTruncationMarker` 分流，不能一律写「已压缩为摘要」。
- 测试：`:app:compileGithubDebugKotlin` 通过；未在设备上打开带压缩检查点的会话验证。

<a id="c145"></a>

## C145 上游 3.3.1 的模型名与导出修正

- 落点：`GoogleProvider` 的模型列表把 `displayName` 改成可缺省，缺失或空白时回退到 `modelId`（上游 `fbfb9926`）；`ModelList` 的模型名 `maxLines` 从 1 改为 2（`48a68f87`）；Markdown 导出在角色后写消息时间，`kotlinx.datetime.LocalDateTime` 先转成 java time 再走 `toLocalString()`（`e4646a77`）。同一个 3.3.1 的安全模式备份见 C146。
- 理由：兼容 Gemini 格式的接口不一定返回 `displayName`，原来的 `!!` 会让整张模型列表解析失败；模型名只显示一行时，长前缀会把真正可区分的后缀截掉；导出时间方便回顾对话。
- 合并注意：`displayName` 空串也回退，别只判 null；`jsonPrimitiveOrNull` 在 `me.rerere.common.http` 已有；ModelList 只改选择列表的标题，其它 `maxLines = 1` 不要跟着改；导出的时间格式跟随系统 locale，和聊天页的时间展示共用 `toLocalString()`，不是固定 `yyyy-MM-dd`。
- 测试：`:ai:testDebugUnitTest` 420 条、`:app:testGithubDebugUnitTest` 906 条、`spotlessCheck` 通过；Google 兼容接口缺 `displayName`、两行模型名与导出时间都没有在设备上单独验。

<a id="c147"></a>

## C147 Markdown 导出可省略工具调用详情

- 落点：`ConversationExport.kt` 的 `ExportOptions` 增加 `includeToolDetails`，默认 `true`；导出选项里加「包含工具调用详情」开关，说明只影响 Markdown。工具分支改用 `internal fun toolMarkdown(tool, includeDetails)`：开启时按原样写工具名、Call ID、Input 与 Output（含输出媒体），关闭时只写 `**Tool**: 名称` 并留一个空行。新增两条字符串并在 values、values-zh、values-zh-rTW、values-ja 同步。Web 端本次不改。
- 理由：带工具调用的会话导出成 Markdown 后，完整参数与结果会把正文冲得很散；分享或存档时经常只需要知道调用过哪个工具。默认保持完整输出，避免已有导出结果变化。
- 合并注意：开关只作用于 Markdown，图片导出仍按原逻辑渲染工具；关闭时连 Call ID、参数、结果和工具输出里的媒体一起省略，不会写出半截结构。Web 的 `convertMessageToMarkdown` 目前完全不输出工具 part，要两端一致需要先补工具名摘要，不能只把开关搬过去。`toolMarkdown` 是 `internal`，只给导出与测试用，不要当通用 Markdown 序列化器。
- 测试：新增 `ConversationExportToolMarkdownTest` 2 条，分别验证开启时写全、关闭时只剩工具名且不出现 Call ID/Input/结果；`:app:testGithubDebugUnitTest` 908 条、`spotlessCheck` 通过。没有在设备上验证导出对话框与真实导出文件，验证只到单元测试与编译。

<a id="c152"></a>

## C152 聊天页文案打磨

- 落点：聊天上下文管理与截断、重试记录、压缩状态、工具调用导出开关、设置路径提示共 15 条英文重写，四份 locale 同步。主要改动：`Manage Context` 改成句首大写的 `Manage context`；`No chat model` 改成 `No model selected`；截断说明从 `Drop older messages from later requests` 改成「后续请求跳过旧消息」并说明不调用模型；窗口消息数提示补成完整句；「以助手消息开头」改成 `Window starts with...` 并说明要保留首轮用户消息；检查点提示改成删除标记即可恢复上一个边界；上下文上限边界、最近消息加减的可访问性文案补全；重试记录从 `Attempt 1/3` 改成 `Attempt 1 of 3`，`Unknown error` 改成 `Reason not recorded`；压缩摘要截断提示去掉重复的 compression；工具详情开关说明改成两句并保留默认开启；设置路径提示补句号。
- 理由：聊天页是停留时间最长的页面，截断与压缩的错误提示会直接影响用户是否敢继续操作；原来的 `Drop`/`floor`/`Attempt 1/3` 更像开发者日志，改成动作和结果明确的句子。
- 合并注意：`Attempt 1 of 3` 与 Web 的 `Attempt 1 of 3` 保持一致；`Reason not recorded` 不改成 `Unknown error`，避免暗示系统知道原因；截断的 `window` 指当前请求窗口，不是整个会话；`chat_page_compress_prompt_hint` 的设置路径必须跟实际页面标题「Default Model and Prompt」一致，改设置页标题时同步。主题/搜索详情、MCP 的 OAuth 中文硬编码属于上游或后续批次，不在本项。
- 测试：`:app:processGithubDebugResources`、`:app:testGithubDebugUnitTest` 910 条、`spotlessCheck`、markdownlint 通过；没有在设备上逐页截图核对长文案换行。

<a id="c154"></a>

## C154 conversation_search 按当前助手过滤

- 落点：`ConversationTools.kt` 构造聊天工具时，`conversation_search` 现在把 `assistantId` 传给 `ConversationRepository.searchMessagesForModel`（第三个参数本来就有，默认 null 表示不限）。工具描述、FTS 查询与结果投影都不变。
- 理由：上游 `#2372`。`recent_chats` 与后台记忆的 `createMemoryConversationTools` 都已按助手过滤，只有聊天侧的 `conversation_search` 漏了；助手 A 能搜到助手 B 的对话内容，属于跨助手内容泄露。修复只改接线，不新增查询路径。
- 合并注意：`searchMessagesForModel` 的 `assistantId` 默认值保留，别改成必填；后台记忆那条路径用的是 `searchMessagesOfAssistantsForModel`（多助手集合），两者不要合并。搜索范围仍包含当前会话。副作用：助手被删除后 `ConversationConfig` 会把会话配置退回当前助手，这类孤儿会话的 `assistant_id` 仍是旧值，按当前助手过滤时搜不到；目前接受这个范围收紧，不为它加回退。
- 测试：新增 `ConversationToolsTest`（`app/src/androidTest`）用临时 Room 库建两个不同助手的会话，执行 `conversation_search` 后断言只返回当前助手的会话；已在 K60 上跑通（1 test / 0 failures）。CI 只跑 JVM 测试，androidTest 需手动跑；FTS 层过滤由既有 `MessageFtsManagerTest.searchAppliesAssistantFilterAndLimit` 覆盖。app 单测 910 条、`spotlessCheck` 通过。

<a id="c156"></a>

## C156 月之暗面思考参数按模型分派

- 落点：`ChatCompletionsAPI` 的月之暗面分支从只认 `api.moonshot.cn` 扩到 `api.moonshot.cn`、`api.moonshot.ai` 与 `api.kimi.com`（Kimi Code 网关），并按模型分派：K3 系列（`KIMI_K3_FAMILY`，含 `kimi-k3*`、裸 `k3`、`k3-256k`）不发 `thinking`，改发顶层 `reasoning_effort`，OFF/LOW 映射 low、MEDIUM/HIGH 映射 high、XHIGH/MAX 映射 max，NOTSET 与 AUTO 不发；K2.7 与网关 coding 系列（`KIMI_ALWAYS_THINKING`，含 `kimi-k2.7*` 与 `kimi-for-coding*`）始终推理、不发任何思考参数；其余 Kimi（K2.5、K2.6 等）沿用 `thinking` 开关，K2.6 开启时仍带 `keep: "all"`。`ModelRegistry` 新增 `KIMI_ALWAYS_THINKING`、`KIMI_K3_FAMILY`、`KIMI_FOR_CODING`、`KIMI_K3_256K`，后两个进 `ALL_MODELS` 以解析能力（fallback 下 `kimi-for-coding` 按 vision 处理，与随包 models.dev 数据一致）。
- 理由：上游 `#1573`。原来对 `api.moonshot.cn` 的所有模型一刀切发 K2.x 的 `thinking`：K3 的档位因此失效，关闭推理时还会发 `disabled`；`kimi-k2.7-code` 传 `disabled` 必 400；国际站与 Kimi Code 网关整条不命中，会落到 OpenAI 的 `reasoning_effort`（含 K3 不接受的 medium/xhigh）。映射参考上游 PR `#1585`（closed 未合并）。
- 合并注意：C86 的 NOTSET 语义是「不发参数」；AUTO 只在 K3 与 `KIMI_ALWAYS_THINKING` 分支不发，K2.x 沿用旧行为（AUTO 视为开启，K2.6 会带 `keep: "all"`）。K3 无法真正关闭推理，OFF 映射成 low，`KIMI_ALWAYS_THINKING` 的 OFF 表现为始终推理。K2.6 的 `keep: "all"`（#1586）保留。不要动其它 host 分支；月之暗面 K2.5+ 的 temperature/top_p 限制（上游 `#1574`）不在本项，`k3-256k`、`kimi-for-coding` 与 `kimi-k2.7-code` 目前仍会带 `temperature`/`top_p`，等 `#1574` 一并按新 matcher 收口。
- 测试：`ChatCompletionsMoonshotRequestTest` 覆盖 K3 六档映射、NOTSET/AUTO 语义（K3 与 K2.6 分别断言）、裸 `k3` 与 `k3-256k`、K2.7 与网关 coding 的四个 id、国际站 host、非月之暗面 host 的负向用例，以及 K2.6 的 keep；`ModelRegistryTest` 覆盖新 matcher、`kimi-k2.7-code` 的能力来源与 `kimi-for-coding` 的模态。`:ai:testDebugUnitTest` 428 条全绿、`spotlessCheck` 通过；改动是请求体构造，另有两个网关 id 的能力/模态解析，未上真机。
