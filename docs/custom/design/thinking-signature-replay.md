# 跨协议思考签名与重放门禁

状态：调研完成，低优先级、未排期。目前跨协议切模型的需求不多，方案先放在待办末尾。本文记录厂商约束、社区与 DSH 的做法、RikkaHub 的现状，以及拟议中的重放门禁。代码尚未改动。

## 问题

会话中途从「不产生签名」的模型切到「要求签名」的模型时，历史消息里的 reasoning part 会被原样重放。目标协议不认这些块时，服务端返回 400。C133 已经允许用户手动清除 Google 签名，但只覆盖 Google，且解决不了 Claude、OpenAI Responses、OpenRouter 的目标侧问题。

## 现状

`core/ai/src/main/java/me/rerere/ai/ui/MessageMetadata.kt` 里各协议的签名都存在 part metadata：

| 协议 | metadata key | 来源 |
| --- | --- | --- |
| Google generateContent | `thoughtSignature` | `GoogleThoughtMetadata` |
| Google Interactions | `interactions_signature` | `GoogleInteractionsMetadata` |
| Claude | `signature`、`claude_redacted_data` | `ClaudeReasoningMetadata` |
| OpenAI Responses | `reasoning_id`、`encrypted_content` | `OpenAIReasoningMetadata` |
| OpenRouter | `openrouter_reasoning_details` | `OpenRouterReasoningMetadata` |

请求构造目前只看「有没有签名」，有就带上，没有就省略：

- `GoogleProvider.toGooglePart` 对 reasoning 输出 `thought: true`，有签名才加 `thoughtSignature`
- `ClaudeProvider` 对 reasoning 输出 `thinking`，有签名才加 `signature`
- `ResponseAPI` 与 OpenRouter 路径按各自 metadata 回放

也就是说，从 DeepSeek 切到 Claude 时，历史 reasoning 会变成没有签名的 thinking 块；切到 OpenAI Responses 时会变成没有 id 和加密内容的 reasoning item。C133 的 `ThoughtSignatureProtocol` 只收录 Google 两种协议，正是因为其余协议只删签名会留下非法块。

## 厂商约束

标注与调研稿一致：`[DOC]` 为官方文档或源码，`[OBS]` 为社区报告或实测。

### Anthropic

- `[DOC]` thinking 块的 `signature` 是完整推理的加密副本，多轮和工具调用时必须原样回传。没有签名的 thinking 块是 400：`Invalid signature in thinking block`，官方排障页明确说是签名被截断、篡改或空着传回。
- `[DOC]` 无法携带绑定 beta 时，官方给的补救是「一次性去掉历史里所有 thinking 和 redacted_thinking 块，之后也不再带」；保留的块必须来自原始序列的连续窗口。手动 thinking 模式下，只有正在进行的工具轮 assistant 消息必须以 thinking 块开头。
- `[DOC]` 同系列模型切换时，不可读的块会在该请求中被丢弃，不计费（`model_binding_mismatch`，200）。
- `[OBS]` 但跨模型重放存在反例：Anthropic SDK 维护者把「另一个模型生成的块被重放」列为非法，有中转路由直到固定产生模型才恢复。不可读不等于不可验证。

### Gemini generateContent

- `[DOC]` 收到 thought signature 必须原样传回；Gemini 4/3 在 function calling 场景必须带签名，否则 4xx，`minimal` thinking 也一样。
- `[DOC]` 严格校验只针对当前轮的 function call，历史轮不校验；并行调用只在第一个 `functionCall` part 上带签名。
- `[DOC]` 非 function call 的 part 不做强制校验，省略不会阻塞，只是效果可能下降。因此带 `thought: true` 但没有签名的文本 part 在契约上可接受。
- `[DOC]` 注入非本 API 产生的 function call、或从别的模型转移 trace 时，可以用 `skip_thought_signature_validator` 或 `context_engineering_is_the_way_to_go` 跳过校验；不要混合有签名和无签名的 part。
- `[OBS]` 社区报错文本是 `Function call is missing a thought_signature in functionCall parts... position N`。社区常说的 corrupted thought signature 不是 Google 公布的错误串。

### OpenAI Responses

- `[DOC]` reasoning item 是不透明数据，`store:false` 时默认带 `encrypted_content`，要求「保留并重放每一个返回的 reasoning item」。
- `[DOC]` 历史 reasoning item 可以留在 payload 里维持连续性，服务端不一定渲染；缺失的代价通常是连续性，不是必然报错，工具循环是最容易出问题的场景。
- `[OBS]` 畸形而非缺失才会报错：`reasoning item was provided without its required following item`、`encrypted content ... item_id did not match`（后者是代理改写了 id）。
- 其他 provider 的纯文本没有 `rs_` id 和加密内容，按 schema 推断不构成合法 reasoning item。

### OpenRouter

- `[DOC]` `reasoning_details` 必须按原顺序原样回放，不能重排或修改，工具调用尤其依赖顺序。
- `[OBS]` 丢掉后会触发 Gemini 的 thought_signature 400，或 Anthropic 的非法签名（流式片段未合并时回放）；只保留 details 而丢掉文本的包装层会静默退化成无签名文本。

## 社区与 DSH

- Open WebUI：把 reasoning 当文本转发、丢弃不透明签名；Gemini 工具循环不重放 `extra_content.google.thought_signature`，上游 400 被吞掉，只能改用 Legacy function calling；跨工具步骤还会丢 `reasoning_details`。
- LangChain：只有当 producer 是 Anthropic 时才把 canonical reasoning 转成带签名的 thinking；OpenRouter 适配器会合并流式片段，并记录了非法签名问题。
- SillyTavern：逐块保存 `reasoning_signature`，并在 OpenAI/Claude 路径回放签名。
- LobeChat、LibreChat、Cherry Studio：本轮检索没有找到明确的签名回放实现，不能当作「它们丢弃签名」的证据。
- DSH：按 adapter 粒度 gate。历史消息记录 `source:{provider, model, replayState}`；只有历史 provider 的 adapter 与目标 adapter 是同一个实例时才保留 `replayState`，跨 adapter family 直接删掉。Anthropic 额外做前缀摘要绑定，签名对不上就移除该消息的 thinking，保留正在进行的工具轮。降级只写 `warn`/`debug` 日志，不做「剥签名后重试」。DSH 的边界是 adapter 而不是协议，跨协议的安全性依赖序列化器和 README 的约定。

## 推荐规则

> 只有当 reasoning part 由目标协议产生时才重放；否则把整块从请求里去掉，存储与 UI 保留。

不要保留正文只删签名。这是唯一在所有 provider 上都可能非法的形态。

按目标协议的策略：

| 目标 | 有本协议签名 | 无签名或外来 reasoning |
| --- | --- | --- |
| Google generateContent | 重放，保留 `thoughtSignature` | 可重放文本，不带签名；function call 另见边界 |
| Google Interactions | 重放 | 可重放文本；工具步骤的原始签名另算 |
| Claude | 重放 thinking 与 redacted 块 | 整块跳过，不发无签名 thinking |
| OpenAI Responses | 重放 reasoning item | 整块跳过，不发无 id/加密内容的 item |
| OpenRouter | 只重放本协议 `reasoning_details` | 整块跳过 |
| DeepSeek chat thinking | 无签名也重放 `reasoning_content` | 这是反例，必须保留 |

## 边界情况

1. 工具轮。Gemini 当前轮 function call 必须有签名；Anthropic 手动 thinking 要求正在进行的工具轮以 thinking 开头。丢 reasoning 不能解决外来工具调用本身的问题，需要单独处理。
2. Google 能接受无签名文本重放，所以 C133 的 Google 清除仍然成立；外来工具调用历史可以用文档给的 `skip_thought_signature_validator` 兜底。
3. redacted thinking 只有加密数据，要么整块带，要么整块丢，不能留下空文本块。
4. Anthropic 前缀绑定和 OpenRouter 顺序要求决定必须稳定：同一条会话里不能一会儿保留一会儿丢弃，丢弃后也不能把某块加回来。
5. DeepSeek chat 是明确反例，thinking 模式的工具调用消息要求回传无签名的 `reasoning_content`，所以门禁必须按协议判断，不能一刀切。
6. 丢弃会损失推理质量和前缀缓存命中；转发会造成重复的硬 400。丢弃更安全，但必须记录日志，不能静默。

## 拟议实施（未编号）

### Phase A：识别与文本 reasoning 门禁

1. 在 core 里把 `ThoughtSignatureProtocol` 扩展成「识别用」的完整枚举，新增 Claude、OpenAI Responses、OpenRouter、DeepSeek。现有枚举只用于「允许清除」的集合，两者分开，避免把可清除和可识别混在一起。
2. 新增 `reasoningOrigin(metadata)`：按 metadata key 判断来源；没有任何签名的归为「未知/无签名」。
3. 在各 provider 的请求构造处过滤：目标协议与来源不一致时跳过整个 reasoning part。优先改 `GoogleProvider`、`ClaudeProvider`、`ResponseAPI`、OpenRouter 路径。
4. 未知/无签名的策略按目标区分：Google 可保留文本，Claude、OpenAI Responses、OpenRouter 跳过，DeepSeek 保留。
5. 日志记录被跳过的块数量与协议，便于用户排查。

### Phase B：工具轮

1. Gemini 当前轮外来 function call 加 `skip_thought_signature_validator`，或按文档建议先丢弃外来工具历史。
2. Anthropic 手动 thinking 下，保证正在进行的工具轮仍以 thinking 开头。
3. 补请求 JSON 快照测试，覆盖有签名、无签名、外来签名、混合历史。

### Phase C：可选提示

在切模型时若检测到将被跳过的 reasoning 块，给一次轻量提示或设置项，避免用户以为模型「忘了上下文」。

### 验收

- 同一会话从 DeepSeek 切到 Claude、Gemini、OpenAI Responses、OpenRouter，请求里不出现目标协议不认的 reasoning 块或裸签名。
- 反向从带签名模型切回无签名模型，行为不变。
- DeepSeek thinking 模式的工具调用仍能续轮。
- 现有 C133 的 Google 清除、C85 的 Claude 签名回放、OpenAI 加密推理回放测试不回归。

### 未决

- 是否给新消息显式记录 `producerProtocol`，而不是从 metadata 推断。记录能覆盖无签名来源，但要为旧消息补默认值。
- 是否提供「仍然发送」的用户覆盖开关。
- 门禁按协议还是按 provider/model 更细的粒度。

## 参考

- Anthropic Thinking、Extended thinking、Thinking troubleshooting 文档
- Gemini Thought signatures 文档与 Cloud 镜像
- OpenAI Responses reasoning 与 migrate 文档
- OpenRouter Reasoning tokens 文档
- 社区：open-webui#32765、#19328，windmill#7679，agno#8794，litellm#48401，oh-my-pi#290，anthropic-sdk-python#1598，UncommonRoute#20
- DSH 源码：`packages/llm/llm/src/message.ts`、`packages/llm/llm-pi-ai/src/replay.ts`、`prefix-binding.ts`、`README.md`
