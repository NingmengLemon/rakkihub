# 落地记录

本文件归档已完成的定制、修复与独立化批次，按时间或主题分组。当前状态以 checklist 索引与明细为准，未来方向见 [roadmap.md](../roadmap.md)。

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
