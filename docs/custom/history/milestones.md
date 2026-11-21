# 落地记录

本文件归档已完成的定制、修复与独立化批次，按时间或主题分组。当前状态以 checklist 索引与明细为准，未来方向见 [roadmap.md](../roadmap.md)。

## 2026-11-22 上游 3.3.1 小批次

- 3.3.1 从 `358e2931` 到 `2435dbdf` 共 7 个提交。移植三个小修正为 C145：Google 模型列表 `displayName` 缺失时回退 `modelId`、模型选择列表名称最多两行、Markdown 导出在角色后写消息时间。
- 移植安全模式的导出与导入备份为 C146：崩溃后进不去主界面时也能导出 SELF 归档（设置、数据库、附件；不带工作区文件）；数据库迁移失败或降级安装导致 Room 打不开库时，导出回退到只读库文件快照；导入接受归档里的工作区条目，沿用 staged restore，重启后生效。
- 跳过 `2435dbdf`（只是版本号）、`b7cf74dd`（Phone Use 无障碍修复，本分支不做 Phone Use）与 `a2479770`（tag 发布工作流；本分支没有 tag 驱动的 Play 上传，发布走 Daily Build）。
- 测试：`ai` 420 条、`app` 906 条、`spotlessCheck` 通过。K60 上完整导入一个 282,506 字节、含 `workspaces/...` 条目的归档后，导入前放进 `upload/` 的标记文件消失，库回到 22 个会话、59 个节点；把库的 `user_version` 改成 999 后，安全模式导出回退到文件快照，生成 280,100 字节归档，库仍是 999、22 个会话，归档不含工作区条目；随后用安全快照恢复，主界面正常。

## 2026-11-22 安全模式清理超大设置

- 移植上游 `27976e4d`：`SettingsStore.largeStoredFieldsFlow` 只按盘上字符串长度列出达到 5,000,000 字符的字段，`resetStoredField` 经 `SettingsField.reset` 恢复默认值并落盘；安全模式新增「数据过大」分组与确认对话框。
- 同批调整安全模式的按钮顺序（进入应用主按钮在前，切换助手描边在后）与说明文案，不再暗示切换助手能解决问题。
- 上游硬编码英文改为 4 个 locale 的字符串资源；清理经 `normalized()`，引用该字段的关联值会一起收敛，并不是只改一个 key。单字段是本次修复的目标；同时存在多个超大字段时，第一次写盘仍可能因为重新编码其余字段而 OOM，这一限制已写进 C144。
- 测试：`SettingsStoreTest` 15 条全过；app 单测 906 条；K60 上注入 5.1M 字符 lorebooks 后安全模式列出并清除，设置文件 5,121,043 字节降到 20,931 字节，除 `launch_count` 外与恢复后的设置一致。

## 2026-11-22 WebUI 图标裁切统一

- `web-ui/public/icon.png` 与 favicon 由 `tools/gen-icons.py` 的 `visible_canvas()` 生成：先按 adaptive 72/108 安全区裁切，缩放与裁切对齐 launcher 的可视区；Web PNG 仍用 legacy 的 20.8% 圆角，adaptive 的遮罩由 OEM 决定，两者轮廓不完全相同。favicon 从 48/64/128/256 扩到 16/32/48/64/128/256 六帧。
- 新增 `web-ui/public/apple-touch-icon.png`（180，不透明方图，圆角交给 iOS），`root.tsx` 增加 PNG icon 与 apple-touch-icon 链接。
- 透明背景方案没有采用：插画本身带不透明品牌底色，去掉圆角裁切得不到透明底，要抠图；16px 下浅色头发与浅色主题对比也不足，所以保留 `#F4EEEF` 底色。
- 测试：icon 256 RGBA 四角透明，apple-touch 180 全不透明，favicon 六帧尺寸正确；launcher、`docs/icon.png`、`docs/readme-icon.png` 重跑后无 diff；`pnpm typecheck`、`pnpm build`、markdownlint 通过。K60 上重建 release 后启动 Web Server，三个文件都返回 200 且哈希与仓库文件一致，页面生成三条图标链接，空状态 logo 正常。

## 2026-11-21 B2 消费方收尾

- `projectForRequest` 与 `projectForSummary` 之前对非 FAILED 消息原样返回，`IN_PROGRESS` 的 ServerTool 仍可能进请求或摘要；现在带 steps 的消息按 step 状态投影并重映射范围，未完成的 ServerTool 一律丢弃；没有 steps 的旧消息只经 `withoutUnfinishedServerTools` 兜底。失败消息的请求投影也保留已完成的 ServerTool 与成功 step 的正文。
- Web 聊天页开始消费 DTO 里已有的 `status` 与 `steps`：assistant 消息显示「生成失败」/「已中断」标签，assistant 消息带 attempts 时按 step 显示历史重试摘要（次数、原因、等待秒数）；补了 `MessageAttemptDto`、`MessageStepDto` 的 `attempts`/`failureCode`/`id` 与中英文案。
- 错误卡片原计划的 retry 目标字段随 C77 永久取消而作废，B2 设计文档与路线图同步收口。
- 测试：ai 417 条、app 905 条全过，`spotlessCheck` 通过；设备上把 Web Server 指向 K60，用构造的失败与中断消息确认两种标签、attempt 摘要与原因显示正常。

## 2026-11-21 U+FFFD 边界实验

- `tools/provider-mock` 新增 `mock-utf8-split` 与 `mock-utf8-broken` 两个场景：前者把合法多字节文本（中文与 emoji）按 7 字节切片写出，字符会跨 socket 写边界，每片延迟 1ms；后者把同样的文本按整帧写出，但先删掉一个 UTF-8 续字节，让线上字节非法。文本只放在 Claude 载荷里。
- 结论：`mock-utf8-broken` 能复现 U+FFFD，`mock-utf8-split` 经真实 ClaudeProvider 解码后与原文逐字相等。代码上这条链路之所以对切分不敏感，是因为 SSE 读取器把字节缓冲到完整事件后再统一解码，看不出拆分；集成用例的作用是把这条路径钉住，不让它以后被改坏，而不是单独证明解码正确。范围只覆盖 `core/ai` 的 Claude 流式路径；Web UI 的读取器、其它 provider、中继在行以上的改写、以及线上已经是 U+FFFD 的情况都没有覆盖，仍要在真实失败会话上抓原始字节。
- 测试：provider-mock 27 条、`ai` 410 条全过（新增 5 条：两个线上字节断言、两个 ClaudeProvider 端到端断言，以及一条证明切片确实切在多字节字符中间的正向对照），`spotlessCheck` 通过。

## 2026-11-21 Compose material3 与 ink 批次

- `material3` 1.5.0-beta02 → 1.5.0-rc01，`androidx.ink` 1.1.0-alpha10 → 1.1.0-alpha11；两处只改 `gradle/libs.versions.toml`，没有源码适配。
- 验证：JBR 21 下 app 905、ai 408、provider-mock 24 条全过；workspace 56 条里 25 条既有 Windows 失败不变；`spotlessCheck` 通过；release assemble 后装到 K60，设置页、模型选择、附件面板与对话框等 Material 3 界面正常；草图的墨迹绘制、画纸下方的工具栏（含画笔粗细）与确认后落成 `sketch.png` 附件都正常，没有崩溃。撤销重做、多指绘制与导出格式没有单独验。

## 2026-11-21 Gradle 依赖批次

- Gradle wrapper 9.6.0 → 9.9.1（`gradlew`、`gradlew.bat` 与 wrapper jar 一并重新生成；两个脚本的 diff 来自 9.9 的 wrapper 模板换代，不是手写），AGP 9.4.0 → 9.5.2、KSP 2.3.10 → 2.3.14、Ktor 3.5.2 → 3.6.1、spotless 8.10.3 → 8.11.0、kotlinx-serialization-json 1.12.0 → 1.13.0、haze 2.0.1 → 2.1.0、image-viewer 1.1.0-alpha.7 → 1.1.1-beta.3。
- haze 2.1.0 删掉了 `GlassStyle.Material3` 与 `GlassDefaults.optics`：`ChatInputHaze` 改成先 `GlassStyle.material3(containerColor, tint)`，再用 `then { }` 叠加外观、光学与形状，optics 用 `optics { copy(...) }` 从当前值变换，不再从已删除的默认值复制。
- 验证：app 905、ai 408、provider-mock 24 条全过；workspace 56 条里 25 条既有 Windows 失败不变；`spotlessCheck` 通过；release assemble 后装到 K60，开启输入栏背景效果（Blur 与 Glass 两条路径）聊天页正常渲染、无崩溃；应用内 Web Server（Ktor 3.6.1）首页、会话列表、SSE 流与消息加载正常；`image-viewer` 1.1.1-beta.3 的图片查看器能打开测试图片并正常返回，没有崩溃。

## 2026-11-21 web-ui 依赖批次

- web-ui 升到本轮 Dependabot 目标的大部分：react/react-dom 19.3.1、streamdown 2.8、zod 4.6、zustand 5.1、radix-ui 1.8、react-resizable-panels 4.24、tailwindcss 4.3、@types/react 19.3，以及 ky 2.2、i18next 27、react-i18next 17、file-type 22。katex 仍留在 0.16：rehype-katex 7.0.1 依赖 katex ^0.16，单独升到 0.19 会让 `\ce` 静默失效，等 rehype-katex 支持后再动。0.16.47 带着 GHSA-238p-pmpm-9mq7（低危，0.18.2 已修），origin/main 也是同一状态，不是这批引入的；如果确认 rehype-katex 的 `renderToString` 与 0.19 兼容，可以用 pnpm override 把它的 katex 也指到 0.19.1 再升。
- 三处破坏性改动按新 API 改写：ky 2 的 `prefixUrl` 换成 `prefix`，`beforeRequest` 钩子改成从状态对象取 `request`；ky 2 的 `HTTPError` 会读掉响应体，错误处理改用 `error.data`，否则服务端的错误文案会静默丢失；react-i18next 17 的 `TFunction` 签名收紧，三个只接受 `(key, options?)` 的辅助函数改成 `TFunction` / `TFunction<"message">` / `TFunction<"page">`。
- oxfmt 留在 0.32：新版格式化会重排大约 60 个文件，与依赖升级分开处理。oxlint 的 spec 没改，但锁文件解析到 1.94，`oxlint app` 仍是 0 个错误。
- 验证：`pnpm typecheck`、`pnpm build`、`pnpm check:markdown`、`pnpm install --frozen-lockfile` 通过。release assemble 后装到 K60，在应用内的 Web Server 上验了首页与会话列表、中英语言切换、Markdown（KaTeX 0.16、表格、代码高亮、Mermaid）与图片上传（`/api/files/upload` 201）；打开鉴权后用错误密码登录会显示服务端的 `Invalid password`，正确密码可以解锁并继续加载，走的就是 `error.data` 这条路径。`fmt:check` 在 origin/main 上有 16 个文件不通过，本批把动过的那两个文件格式化后仍是 16 个。上传与导航同时进行时见过一次指向 Mermaid chunk 的 console error，干净重载后不再出现。

## 2026-11-21 provider mock server

- C143：新增 `tools/provider-mock`，在 `127.0.0.1` 的临时端口上实现 OpenAI Chat Completions、OpenAI Responses、Anthropic Messages 与 Gemini generateContent 的流式与非流式响应，外加两个模型列表接口；按模型名或 `X-Mock-Scenario` 切换正常、推理、工具调用、429、500、截断、空与慢场景；请求记录器对凭据类请求头只留存在性与 scheme 前缀。`core/ai` 新增 `ProviderMockIntegrationTest`，用真实 provider 实现走完整 HTTP 路径并断言文本、工具参数、usage 与失败映射。模块不参与 app 构建。

## 2026-11-20 message_node 复合索引

- C142：按会话加载消息改为 `(conversation_id, node_index)` 复合索引，Room 34→35 用只建/删索引的 `Migration_34_35`；`OfficialSchemaTrimmer` 导出时换回官方单列索引，官方客户端设备验收已在 2026-11-21 重跑（导出库 identity 与索引为官方 v32 形状，官方 3.1.1 恢复后计数一致、图片正常显示）。

## 2026-11-20 上游 3.3.0 小批次

- C138：补齐 SenseNova 提供商图标与 NVIDIA 匹配。
- C139：请求体日志只在 debug 构建序列化。
- C140：内置助手可删除，至少保留一个，失效的选中 id 自动回退。
- C141：消息概览用独立卡片标出上下文压缩检查点。
- bettertext 升级到 v0.1.6。

## 2026-11-20 依赖安全批次

- 按 Dependabot 的 gradle-minor-patch 组筛选安全与 patch 更新：okhttp 5.5.1、jsoup 1.24.1、slf4j 2.0.20、sshd 2.20.1、jmdns 3.6.5、pebble 4.1.3、quickjs 1.0.18、bouncycastle 1.87、uiautomator 2.4.1、slf4j-android 2.0.20-0；通过 `ai`/app 单测、`workspace` 单测（25 条既有 Windows 环境失败不变，其中 sshd 用例跑满 23 条）、release assemble 与安装。Gradle/AGP/KSP、Ktor、Compose 与 Web 侧 major 仍单独验证；uiautomator 只参与 baseline profile，未单独构建。

## 2026-11-20 测试连接弹窗

- C137：移植上游 `a75f3f9c`，测试连接弹窗的开关移到工具栏外，键盘弹出不再把弹窗一起销毁。

## 2026-11-20 PEP 723 脚本元数据

- C136：`tools` 下三个独立 Python 脚本补上 `# /// script`，声明解释器与依赖，`uv run <script>` 可直接执行；vendored 的 `.agents` 脚本不加，避免下次同步被覆盖。

## 2026-11-20 Web 正文 Mermaid

- C135：Web 正文的 mermaid 围栏真正渲染成图，复用 `@streamdown/mermaid` 的 strict 配置；流式期间保留源码，结束后出图。

## 2026-11-20 mark.html 离线

- C134：整页 WebView 预览的 markdown-it、KaTeX、highlight、DOMPurify、js-base64 打包进本地 IIFE，CSS 与 KaTeX 字体一并入库，mermaid 复用已有 IIFE；断网也能渲染。

## 2026-11-20 清除历史思考签名

- C133：会话列表可以按协议清除历史消息里的 Google 思考签名（generateContent 与 Interactions），默认只清 generateContent；Claude/OpenAI/OpenRouter 因重放需要原样签名，暂不提供。

## 2026-11-20 workspace SVG 相对资源

- C132：SVG 文件预览把 WebView 的 base 指向文件所在目录，并只对 SVG 打开文件访问、JS 仍关闭，同目录的 `<image href>` 预期可以加载（未做真机验证）；远程工作区回退原行为。

## 2026-11-19 定期保存失败提示

- C131：生成与压缩两条定期保存循环的失败和超限不再只写日志，聊天页底部浮层给出可关闭的警告卡；保存成功自动清除，警告不落盘。

## 2026-11-19 相邻推理块显示收尾

- C130：只有整条消息里最后一个推理 part 还可能显示为生成中；中转漏发 `content_block_stop` 时，前面的 thinking 块不再持续显示滑动高光。

## 2026-11-19 每日构建上传 Artifacts

- C129：每日构建在发布 nightly 之前把 APK 与 `mapping.txt` 传一份到 Actions Artifacts，名字带完整 SHA、保留 14 天，固定 tag 被覆盖后仍能按提交追溯。

## 2026-11-19 信任用户 CA

- C128：新增 network security config，`system` 与 `user` 证书都信任，保留明文许可；自建 CA 签发的 Provider、MCP、搜索端点可连，设备抓包也可用。

## 2026-11-19 Gradle 模块目录分组

- C127：库模块收进 `core/`、`integrations/`、`platform/`，`app` 与所有 project path 保持原样；`material3` submodule、`web-ui/copy.ts`、spotless、文档、i18n 和 `tools/trace` 路径同步。39 个 Kotlin 文件只有 ktlint 语义等价变化。

## 2026-11-19 stdio MCP 日志

- C126：tee stderr 到每个 Server 的 16K 字符尾部缓冲，设置页可展开、复制、清空；错误时自动展开，重连时清空。

## 2026-11-18 工作区编辑器与终端输入

- C124：Markdown 编辑器文件名 dirty 时显示 `*`，源码 clean 的 Save 禁用、预览 clean 隐藏、预览 dirty 可保存；输入法在切后台再回来时恢复。
- C125：终端默认传统直连，IME 开关按 tab 保存；sticky Ctrl/Alt 在直连路径也生效，硬件 Ctrl/Alt 仍交给 TerminalView；功能键栏补 PGUP/PGDN，是否显示 IME 开关进设置。

## 2026-11-18 MCP 停止态持久化

- C123：手动停止的 MCP Server ID 写入 Settings，app 重启后仍保持停止；点服务器重启、禁用或删除配置时清除。旧构建忽略新 key。

## 2026-11-18 B2 标题与摘要 step 投影

- C122：失败回复不再整条从标题和摘要消失，成功 step 的正文与工具事实保留，失败 step 只留已执行完成的工具；记忆仍整条隔离。

## 2026-11-18 聊天 SVG 内联预览

- C121：Android 正文检测到块级 `<svg>` 时在气泡内用 Coil `SvgDecoder` 渲染，默认折叠、点击展开一级；不安全的 SVG 或解码失败才回退到整条消息的 WebView 预览，Android 正文仍不做通用 HTML 渲染。

## 2026-11-18 MCP 工具失败重试元数据

- C120：工具卡片增加「可重试」「可重入」，`McpManager` 对标记工具最多尝试 2 次；未发出的失败只要求 `retryable`，已发出的异常、超时和 `isError` 还要求 `reentrant`。

## 2026-11-18 stdio MCP callTool 验收

- 设备上用本地 OpenAI 兼容 mock 服务驱动 `mcp__Echo__echo`：模型发出工具调用，UI 显示 `Called tool`，Echo 返回 `ECHO: calltool-device`，工具结果回传后输出最终回复；在 `3.1.1-custom.3418+5235dce` 上验过。

## 2026-11-16 用户反馈修复

- 消息操作里的“重试”和“重新生成”图标没有区分度：失败 step 的重试改用 `ArrowTurnBackward`，整轮重新生成保持 `Refresh03`，聊天快捷栏与更多菜单同步；两者语义仍分别对应重跑当前 step 和新建整轮候选。
- Gemini 协议下写文件/编辑文件时没有“正在写入/正在编辑”状态：`workspace_write_file` / `workspace_edit_file` 的标题在 `loading && callComplete && !isPending` 时改用“Writing/Editing”文案，执行完成后回到普通标题；Gemini 的 `functionCall` 仍是整块到达，因此参数生成阶段没有可展示的 part，进入工具执行后卡片和状态会立即出现。
- 工作区文件编辑器从多任务视图切回时自动弹出输入法并滚回光标位置，丢失切出前的浏览进度：编辑页在 `ON_PAUSE` 保存 `ScrollState`、清焦点并收起输入法，`ON_RESUME` 和加载完成后恢复滚动位置；`TextField` 现在持有同一个 `scrollState`，回来不再自动聚焦。设备上用 200 行文件验证了无键盘和键盘打开两种切出场景。
- 工作区终端不能输入其他语言：终端页增加一个隐藏的 `TerminalImeEditText` 作为正常文本输入宿主，IME 的 `commitText` 按 UTF-8 写给 `TerminalSession`，删除/Enter/Tab 和控制键单独转发，`TerminalView` 不再抢焦点。开发机装 Rootfs 后用 Fcitx5 拼音验证了“你好”提交、bash 执行和错误回显；用户随后在 Gboard 中文和日文下复核正常。
- 从工作区文件目录打开 Markdown 文件时，当时只有 raw 编辑视图：`.md`/`.markdown` 默认渲染预览，顶栏可在“预览/源码”间切换；静态 HTML/SVG 原本已有 WebView 预览，本次没有重复改。
- 工作区文件编辑器缺少未保存状态保护：编辑器跟踪加载基线和当前文本，返回时若有改动弹出保存/放弃/取消，保存成功才退出；系统返回键同样拦截。

## 2026-11-14 失败恢复第一轮

- 自动重试整体下线：`Settings.retryPolicy()` 恒返回 `RetryPolicy.NONE`；网络设置去掉自动重试开关与次数滑块；`NetworkSetting.maxRetries` 与已保存 JSON 保留；C76 的 attempt 卡片失去触发来源（代码保留）；C77 的错误卡片 Retry 撤销，错误卡片只提示错误信息。
- C63 撤销：`AgentHooks.onContextOverflow`、`overflowWindow`、`shrinkActive`、`contextOverflowRetried` 与测试删除，超限只出错误卡片。
- C73 撤销：`ContextPruning.kt`、`buildStep` 的裁剪调用与测试删除，请求装配不再隐式改写消息。
- C62 补心跳与开关：Claude 的 ping 产出 `StreamChunk.Heartbeat` 重置看门狗，`NetworkSetting.streamIdleTimeoutEnabled` 可整条关闭。
- C65 补设置：`AgentSetting` 增加工具超时（0 表示不限制）与 `workspace_shell` 默认/最大命令超时；工具声明 `timeoutMillis`，外层取较大值，不会先于工具自己的超时触发。

## 独立化与清理

- `tools/i18n` 重写成 Python（C93）：用 `uv run --project tools/i18n i18n <command>` 运行，config、术语表、缺条目与未使用检查都保留；本机没有 `.env`，自动翻译仍需配 key。
- 仓库改名为 `rakkihub`：GitHub 仓库名和本地 remote 已改，`docs/custom/README.md` 的 remote 表同步；上游链接不动。
- 更换应用图标（C90、C92、C94、C101）：launcher 各密度用关于页同一张插画生成，monochrome 层去掉（插画剪影会变成黑块），README 使用安全区裁切的 `docs/readme-icon.png`（`docs/icon.png` 保留为通用图标）；`web-ui/public/favicon.ico` 统一用插画（48/64/128/256 四帧，16px 标签页由浏览器缩），Web UI 新会话空状态改用 `web-ui/public/icon.png`，没有引用的 `docs/img/` 整个目录已删除。通知栏小图标 `ic_stat_rikkahub` 是有意保留的兔子头剪影：插画在 24dp 下是一团色块，这枚单色剪影反而清楚。
- RikkaHub 提供商残留清理（C95）：上游 `0533acde` 删掉默认提供商后，本分支删掉 `assets/icons/rikkahub.svg`、`AIIconMatcher` 的 `rikka|auto` 分支、四份 `rikkahub_provider_description`，以及只给它用的空 `RikkaHubAPI` 与指向 `api.rikka-ai.com` 的 Retrofit 单例；`DEFAULT_AUTO_MODEL_ID` 改名 `DEFAULT_MODEL_ID` 并注明当前预置里没有对应模型（老设置里那个提供商的 auto 模型仍是这个 id）。搜索服务 `RikkaHubSearchService` 仍保留，但它的选项里 `displayName` 是 `RikkaHub`，同样走这个 matcher，图标回退成首字母。
- 发给 OpenRouter 的来源标识头移除（C96）：`configureReferHeaders` 只在 host 是 `openrouter.ai` 时加 `X-Title` 与 `HTTP-Referer`，Referer 指官方站点，等于把本分支的调用算成官方 app；函数、十一处调用与 `mediagen` 生图那两行一并删掉，用户自定义请求头不受影响。
- 错误请求日志打出完整响应体（C99）：失败响应的 body 按块写入 logcat 的 `ProviderError`，只记录 path 与状态码。
