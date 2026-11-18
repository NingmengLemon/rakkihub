# 明细：工作区与 MCP

本文件是 [checklist.md](../checklist.md) 的明细，收录 C89、C102 至 C106、C111。

<a id="c89"></a>

## C89 workspace 前台命令超时后的孤儿进程清理

- 落点：新增 `workspace/src/main/java/me/rerere/workspace/ProotTermination.kt`（`terminateProot`）；`workspace/src/main/java/me/rerere/workspace/ProotShellRunner.kt` 在前台命令启动前把 shell 与 PRoot 的进程号写进 Rootfs 的 `/tmp/rikkahub/exec/<uuid>.pid`，超时或取消时按 pid 文件结束整组进程；`workspace/src/main/java/me/rerere/workspace/WorkspaceShellRunner.kt` 的 `readResult` 增加 `terminate` 回调，默认仍是 `destroyForcibly`；新增 `workspace/src/test/java/me/rerere/workspace/ProotShellRunnerCommandTest.kt`
- 理由：上游 #2225。前台命令超时或线程被中断时 `destroyForcibly()` 直接 SIGKILL PRoot，它跟踪的进程会脱离跟踪继续运行，命令本身和 `&` 启动的子进程都会残留。改成在 PRoot 还活着时扫 `/proc` 的 `TracerPid`，多轮 SIGKILL 它跟踪的所有进程，最后一个退出后 PRoot 自己退出；不能靠 PRoot 收到 SIGQUIT 清理，它从应用进程继承了屏蔽 SIGQUIT 的信号掩码
- 合并注意：只覆盖前台命令；上游的后台命令（`4ebfde22` 的 `workspace_shell` 后台运行、`workspace_shell_output`、`workspace_shell_kill`）没有移植，也没有对应的后台命令面板。`HostShellRunner` 仍用 `destroyForcibly`。pid 文件放在 Rootfs 的 `/tmp/rikkahub/exec` 下，结束时删除；刚启动时 shell 可能还没写入，`terminateProot` 最多等 1 秒再退回 `destroyForcibly`。workspace 模块有 25 条 Unix-only 测试在 Windows 上必然失败（`ExampleUnitTest` 4、`RootfsInstallerTest` 1、`SshWorkspaceBackendTest` 20；`/bin/sh` 不存在、路径要求绝对 Unix 路径），与本次改动无关
- 测试：新增 `ProotShellRunnerCommandTest` 2 条（启动脚本先写 `$$ $PPID`、命令文本作为位置参数传入）；workspace 模块测试在 Windows 上 25 条既有失败（新增 2 条通过）；app JVM 803 条、ai 345 条不受影响；assemble、spotless、markdownlint 通过。真机上「超时后进程确实不再残留」没有验证，判据是 pid 文件与 `/proc` 扫描逻辑和上游同款实现

<a id="c102"></a>

## C102 工作区编辑器返回时恢复滚动并收起 IME

- 落点：`WorkspaceFileEditorPage` 使用 `rememberScrollState()` 并把它传给 `TextField`；`ON_PAUSE` 保存 `ScrollState.value`、`clearFocus(force = true)` 并隐藏输入法；`ON_RESUME` 和加载完成后的 effect 恢复位置；`savedScroll` 用 `rememberSaveable`。
- 理由：从 Recents 切回时，焦点恢复会把页面滚回光标并自动弹出输入法，用户切出前的浏览位置丢失。
- 合并注意：恢复只在页面存活或重建时生效，不做草稿持久化；编辑器仍不能跨进程保存未提交文本，见 C106。不要再把 `ON_PAUSE` 的清焦点去掉，否则输入法会在返回时重新聚焦。
- 测试：开发机 200 行文件，无键盘和键盘打开两种切出场景，返回后滚动位置一致且不自动弹键盘。

<a id="c103"></a>

## C103 工作区写/编辑文件显示运行状态

- 落点：`WorkspaceToolUIs` 的 `WriteFileToolUI` / `EditFileToolUI` 标题按 `loading && callComplete && !isPending` 选择 Writing/Editing 文案；四个 locale 增加运行中和无路径默认文案。
- 理由：工具执行期间仍显示完成态的 “Write/Edit”，用户会以为卡在思考末尾；有明确运行态后至少能看出正在写文件。
- 合并注意：`callComplete=false` 表示参数还在流式生成，此时不冒充执行中；等待审批也不显示 Writing/Editing。Gemini 的 `functionCall` 是整块到达，参数生成阶段没有可展示的 part，进入执行后才显示运行态。
- 测试：编译与全量 JVM 通过；按工具执行路径检查标题分支，未单独构造 Gemini 真机工具样本。

<a id="c104"></a>

## C104 工作区终端支持 IME 文本输入

- 落点：新增 `TerminalImeEditText`，用正常 `TYPE_CLASS_TEXT` 作为隐藏 IME 宿主；`commitText`、`finishComposingText` 把提交文本按 UTF-8 写给 `TerminalSession`，退格、Enter、Tab、控制键单独转发；`WorkspaceTerminalViewClient` 优先聚焦该宿主并 `restartInput`；`TerminalView` 保持可聚焦以支持长按选择/复制。
- 理由：`terminal-view` 0.118.0 的 `TerminalView` 把 inputType 设成无文本类，Gboard/Fcitx 的拼音组合没有正常 `InputConnection` 承接，候选提交会丢失。
- 合并注意：终端核心仍是上游 AAR，不要改 `terminal-view` 二进制；输入法宿主是 1dp 透明视图，设置 `importantForAccessibility=NO`。硬件键盘事件和 IME 的 `sendKeyEvent` 都要转发给终端，长按选择不能因为聚焦宿主而失效。升级 terminal-view 后要重测中文、日文、退格和 Enter。
- 测试：开发机装 Rootfs 后用 Fcitx5 提交“你好”、退格和执行；焦点恢复后重新点终端，拼音候选仍进入隐藏宿主，空格提交“你好”，组合中的退格没有误删终端字符；长按终端文本显示 Copy/More 选择工具栏。用户用 Gboard 中文/日文复核正常。

<a id="c105"></a>

## C105 工作区 Markdown 预览与源码切换

- 落点：`WorkspaceFileEditorPage` 对 `.md`/`.markdown` 默认显示 `MaterialMarkdown` 预览，顶栏在“预览/源码”间切换；超过 512,000 字符只显示过大提示并保留源码入口；HTML/SVG 继续走原 WebView 预览。
- 理由：Markdown 文件原来只有 raw 文本，工作区里查看说明文档不方便；静态 HTML/SVG 已有 WebView，不需要重复造。
- 合并注意：`MaterialMarkdown` 不加载工作区相对图片，公式和代码高亮也不完整；需要完整渲染时再换 `MarkdownBlock` 并接工作区资源解析。大文件必须有上限，否则一次组合整份文档会卡 UI。
- 测试：开发机用含标题、加粗、列表的 `.md` 验证默认渲染和源码切换；超限分支只做代码审查。

<a id="c106"></a>

## C106 工作区编辑器未保存退出保护

- 落点：`WorkspaceFileEditorPage` 记录加载基线 `loadedText` 和当前文本，`dirty` 只在可编辑且无加载错误时成立；`BackHandler` 和顶栏返回都调用 `requestExit()`；弹窗提供 Save/Discard/Cancel，Save 成功才 pop，Discard 直接 pop，Cancel 留在编辑页；`BackButton` 增加可选 `onClick`。
- 理由：编辑后直接返回会静默丢改动；至少要在离开前给一次保存机会。
- 合并注意：`textState` 故意不 `rememberSaveable`，旋转或进程死亡仍会丢未提交文本；这里只保护主动返回。保存失败时保持弹窗并显示错误 toast，不能 pop。新增文案在四份 locale。
- 测试：开发机验证未保存弹窗、Discard 返回；保存路径复用原有保存函数，未单独构造可写文件验证。全量 JVM、spotless、assemble 通过。

<a id="c111"></a>

## C111 stdio MCP 服务器

本项是一个功能，按 9 个阶段落地。早期登记用了 C111 至 C119 九个顶层编号，依次对应 C111.1 至 C111.9；C112 至 C119 保留，不再用作顶层编号，下一个新项从 C120 起。

### C111.1 配置与持久化

- 落点：`McpConfig.kt` 新增 `StdioMcpServer`（id、commonOptions、workspaceId、command、args、env、cwd、启动超时，`toString` 对 env 脱敏）；`Settings.stdioMcpServers` 默认空列表；`SettingsStore.STDIO_MCP_SERVERS` 新 DataStore key 和 `SETTINGS_FIELDS` 条目；`Settings.allMcpServerIds()` 统一 HTTP+stdio id，`SettingsNormalization`、`ConversationConfig.getAssistantOf`、`SettingsRoutes`、`ConversationRoutes` 都用它；`OfficialSettings.forOfficialExport` 清空 stdio 并过滤 assistant 引用。
- 理由：stdio 配置不能塞进 sealed `McpServerConfig` 的 `mcpServers` JSON，否则旧构建遇到未知 `"stdio"` 判别值会整份设置解码失败；独立 key 让旧构建忽略配置，新构建仍能识别 assistant 引用。
- 合并注意：旧构建能解码但无法运行 stdio，保存设置时不会删除新 key；旧构建的归一化只认 HTTP id，会清掉 assistant 的 stdio 引用，这是已知降级。官方导出不包含 stdio；自家备份保留。该阶段只落配置，进程启动和 registry 接线在后续阶段；workspace 唯一性、command/NUL/cwd 校验在保存入口和 launcher 阶段补齐。`ConversationConfig`、`SettingsRoutes`、`ConversationRoutes` 已改用 HTTP+stdio id 并集；`AssistantDetailVM` 的 MCP 列表和聊天附件面板的 MCP 入口在 C111.4 已切到 `allMcpServers()`。
- 测试：`SettingsStdioMcpTest` 覆盖序列化往返、旧 JSON 默认空、env `toString` 脱敏、归一化保留 stdio assistant 引用、官方导出清空 stdio 和引用；`ConversationConfigTest` 覆盖绑定会话保留 stdio 引用；`SettingsFieldsTest` 的字段清单和落盘 key 快照同步。

### C111.2 rootfs 进程 launcher

- 落点：`WorkspaceShellRunner` 增加 `launchProcess`；新增 `WorkspaceProcessSpec` / `WorkspaceProcessContext` / `WorkspaceProcess` / `WorkspaceProcessLauncher`；`WorkspaceManager.launchProcess` 负责 id/cwd 校验和上下文组装；`ProotShellRunner.launchProcess` 复用 `prootArgs`、`ROOTFS_ENV`、`--kill-on-exit` 和 pid 文件，使用非登录 `-c`，把 env 写成 rootfs `/tmp/rikkahub/mcp/<id>-<launch-token>.env`（每次 launch 唯一），wrapper 先 `set -a` 注入环境再 `exec`；`WorkspaceProcess.close(graceMillis)` 走 `terminateProot`，MCP 层不需要直接结束 PRoot；`WorkspaceManagerProcessLauncher` 给 MCP 层使用。
- 理由：stdio MCP 需要长期 stdin/stdout/stderr，不能走一次性 `executeCommand`；进程必须运行在 rootfs 里，并保留现有 PRoot 进程组终止和 rootfs 临时目录清理语义。
- 合并注意：cwd 按 rootfs 绝对路径处理，不复用 workspace 相对映射；env 文件不进入备份/WebDAV/文件浏览器，但同一工作区的 workspace 工具和 shell 仍可访问 rootfs `/tmp` 及运行中进程的 `/proc/<pid>/environ`，这是已声明的非沙箱边界；启动失败返回明确错误，HostShellRunner 不支持长期进程；registry 接线见 C111.3，配置 UI 见 C111.4。
- 测试：`ProotShellRunnerProcessCommandTest` 覆盖 proot 参数、rootfs cwd、唯一 env/pid 路径、env/pid 文件参数和 wrapper；`WorkspaceManagerProcessLauncherTest` 覆盖上下文转发、`close(graceMillis)` 转发、非法 id/cwd 拒绝和 HostShellRunner 不支持错误。Windows 上完整 `:workspace:test` 仍有既有的 `/bin/sh`/路径基线失败，新增定向测试通过。

### C111.3 registry 接线

- 落点：`McpServerConfig` 新增运行时子类 `StdioServer`（不写回 `mcpServers` 持久化列表）；`StdioMcpServer.toRuntimeConfig()`；`Settings.allMcpServers()` 合并 HTTP 与 stdio；`McpManager` 用合并列表 reconcile、过滤工具；`McpSessionRegistry` 对 stdio 启动 `WorkspaceProcessLauncher`，用 `StdioClientTransport` 接 stdin/stdout/stderr，`withTimeout(startupTimeoutMs)` 包住 connect（工具同步仍走 SDK 默认超时），独立协程观察进程退出并触发重连，关闭时先关 client 再 `close(5s)`；成功连接后 60 秒稳定期才清零重连预算，避免握手后崩溃无限重启；HTTP 仍保持连接成功即清零；`syncTools` 把发现结果写回 `stdioMcpServers`；`McpConnectionKey` 把 workspace/command/args/env/cwd 纳入重连判断；DI 注入 `WorkspaceManagerProcessLauncher` 和 `WorkspaceDAO`。
- 理由：stdio 和 HTTP 共用现有 session 状态机、重连预算和工具同步；不能让运行时子类进入旧构建的 `mcpServers` JSON，也不能把 stdio 工具写回 HTTP 列表。
- 合并注意：stdio 配置从 C111.4 起有添加/编辑入口；`AssistantDetailVM` 和聊天附件面板的 MCP 入口都使用 `allMcpServers()`。OAuth 对 stdio 不生效；启动 tempDir 清理和 `McpManager` 初始化的顺序仍留到启动阶段。进程退出只把 exit code 写进状态，stderr 由 transport/SDK debug 输出，registry 暂无摘要或日志入口，后续再补。
- 测试：`McpConnectionKeyTest` 覆盖 stdio command/args/env/cwd/workspace 变化触发重连、工具元数据不触发；`SettingsStdioMcpTest` 覆盖 `allMcpServers()` 合并和 runtime 字段。真实 rootfs echo Server 的 initialize/listTools/callTool 已在设备上验收，见 C111.4。

### C111.4 配置 UI

- 落点：`SettingMcpPage` 列表合并 HTTP 与 stdio，Add 菜单分 HTTP/stdio，删除按类型写回对应列表；`SettingMcpDetailPage` 从 `allMcpServers()` 查找，stdio 走独立的 `StdioMcpConfigure`（启用/名称、专用工作区、命令、参数、env、cwd、启动超时），保存/删除/工具开关按类型写回 `mcpServers` 或 `stdioMcpServers`；`AssistantDetailVM.mcpServerConfigs` 也改用 `allMcpServers()`，助手 MCP 页才能选择和显示 stdio Server；工作区列表只显示 `capabilities().rootfs`、`READY` 且已有 rootfs 的本地工作区；保存按钮要求名称、工作区、命令、非相对 cwd 和不小于 1000ms 的启动超时；`AssistantDetailVM` 和聊天附件面板的 MCP 入口也改用 `allMcpServers()`。
- 理由：配置结构已经落地，但没有 UI 就无法创建或编辑 stdio Server；复用现有 MCP 详情页可以把工具开关和状态显示一起带过来。
- 合并注意：新增文案已走四份 locale；env 输入用密码视觉变换，多行参数/环境变量用本地文本状态，避免过滤空行后无法输入第二行；workspace 绑定唯一性由 C111.5 在保存入口补，C111.6 又在归一化层兜底；命令 NUL/换行和 rootfs 二次校验仍由启动阶段补。
- 测试：现有 C111 的 JVM 测试覆盖配置归一化和 runtime 合并；本阶段没有新增 Compose/JVM UI 测试，靠开发机手动验收添加、编辑、删除、助手 MCP 选择和聊天附件面板入口。本地 workspace rootfs 用 perl 写了一个最小 stdio MCP Server，验证 initialize、tools/list、工具写回和 Connected 状态；表单文案已走四份 locale，设备上切 zh-CN 复核标签。callTool 的真实 UI 路径用本地 OpenAI 兼容 mock 服务驱动 `mcp__Echo__echo`：模型发出工具调用，UI 显示 `Called tool`，Echo 返回 `ECHO: calltool-device`，工具结果回传后输出最终回复；在 `3.1.1-custom.3418+5235dce` 上复验。

### C111.5 UI 收尾

- 落点：`SettingMcpDetailPage` 的 stdio 基础页增加连接状态、重启/停止按钮、工作区冲突提示和 env 显示切换；保存按钮在 workspace 已被其它 stdio Server 占用时禁用；删除确认明确工作区不删除。停止/重启按钮经 `McpManager.stopClient`/`restartClient` 落到现有 session 生命周期；手动停止的保留语义见 C111.8。`AssistantMcpPage` 顶栏增加设置入口，已有 Server 时也能进入全局 MCP 配置页。
- 理由：C111.4 已经能配置和连接，但详情页看不到状态、无法手动停止/重启，两个 Server 也能静默绑定同一个工作区；已有 Server 后助手 MCP 页没有入口回全局配置，也会把状态/停止功能藏在不可达位置。
- 合并注意：停止只结束当前 session，配置和工作区保留；重启先移除旧 session 再重新 addClient，避免旧 client 仍在时提前返回；C111.8 起手动停止会阻止后续 reconcile 自动拉起，直到重启或配置被禁用；未保存的草稿不会生效；没有可用工作区时仍只显示提示，不跳转扩展页；CardGroup 形态仍未处理。
- 测试：`:app:compileGithubDebugKotlin`、`:app:testGithubDebugUnitTest`；开发机手动验收停止/重启、管理入口和 `Connected` 状态；工作区冲突提示按代码路径核对。callTool 的设备验收见 C111.4。

### C111.6 workspace 唯一性与列表提示

- 落点：`SettingsNormalization` 在按 id 去重后再按 `workspaceId` 去重，空 workspace 用自身 id 作为键，保留第一条；`SettingMcpPage` 的删除确认对 stdio 明确“专用工作区和 rootfs 不会被删除”，与详情页一致。
- 理由：C111.5 的唯一性只在保存按钮上，恢复/导入的重复配置仍会启动两个 Server；列表页删除提示还沿用通用文案，和详情页不一致。
- 合并注意：归一化会静默丢弃后出现的重复 workspace 配置，只保留第一条；UI 保存入口的冲突提示仍在，防止用户新建重复配置。停止在 C111.5 时只是 session 级；C111.8 起手动停止由 `manuallyStopped` 保留，后续 `allMcpServers()` 写入不会再经 reconcile 自动拉起；标记只在内存，app 重启后恢复启用。
- 测试：`SettingsStdioMcpTest` 新增同 workspace 去重、不同 workspace 保留；`:app:testGithubDebugUnitTest`。

### C111.7 归一化引用收口

- 落点：`SettingsNormalization` 先算出按 workspace 去重后的 `stdioMcpServers`，再用这份列表构造 `validMcpServerIds`，使被丢弃的重复项在同一个归一化轮次里也从 assistant 引用中移除；补 test 覆盖“丢弃项的 assistant 引用同轮清掉”和“空 workspace 按 id 保留”。
- 理由：C111.6 的 `validMcpServerIds` 在去重前计算，引用被丢弃项会残留一轮，虽然运行时解析不到、下次设置写入会自愈，但状态不一致。
- 合并注意：归一化仍然静默保留第一条；被丢弃项的 assistant 选择不会迁移到保留项，只会在同一轮被清掉，这是有意的。
- 测试：`SettingsStdioMcpTest` 覆盖同 workspace 去重、不同 workspace 保留、空 workspace 不同 id 保留、丢弃项引用同轮移除。

### C111.8 手动停止持久化

- 落点：`McpSessionRegistry` 维护运行时 `manuallyStopped` 集合；`stopClient` 先标记再移除 session，`reconcile` 对已标记的配置不再自动拉起；`restartClient` 清除标记后 remove+add；配置被禁用/删除时集合自动清理。`McpManager.getAllAvailableTools` 经 `availableMcpTools` 过滤已标记停止的 Server，停止期间不再把工具提供给模型。
- 理由：C111.5 的停止只是移除当前 session，之后任何 `allMcpServers()` 写入（工具开关、另一个 Server 的 syncTools 回写）都会经 reconcile 重新启动，用户会看到“停止后自己又连上”；停止后仍把工具挂给模型，模型只会调用到一个运行时不可用的工具。
- 合并注意：`manuallyStopped` 只在内存里，app 重启后会恢复为启用状态；停止期间配置编辑仍保留，点重启才恢复；配置被禁用、删除或暂时无效（如名称为空）时标记清除，重新有效后会再启动；没有新增持久化字段。工具过滤只剔除手动停止的 Server，不按临时断线状态剔除，避免重连期间工具列表抖动。
- 测试：`McpToolAvailabilityTest` 覆盖已绑定 Server、手动停止、未绑定、Server 禁用、工具禁用和全部停止时为空；设备上停止 Echo 后 mock 端只收到 1 个非 MCP 工具，重启后恢复为 2 个并完成 `mcp__Echo__echo` 调用。

### C111.9 无工作区导航

- 落点：`StdioMcpConfigure` 的工作区下拉在没有 eligible 项时，点击直达 `Screen.Workspaces`，提示文案改为“去工作区页安装 rootfs”。
- 理由：C111.4 的空状态下拉只关闭菜单，用户需要自己找到扩展页；这是配置流程的断点。
- 合并注意：仍然只列本地、READY 且有 rootfs 的工作区；到工作区页后用户需自行创建/安装，再回来选中。
- 测试：编译；设备上可在无 eligible 工作区时点击该项验证导航。
