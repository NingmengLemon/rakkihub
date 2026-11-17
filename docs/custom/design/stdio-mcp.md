# stdio MCP：专用工作区与 rootfs 设计

## 状态

设计稿已复审 PASS。实现状态与阶段见 [`checklist/workspace.md`](../checklist/workspace.md) 的 C111；开发机已验证 initialize、tools/list 和 Connected，callTool 的真实 UI 路径待补。

## 目标

- 支持本地 stdio MCP Server，命令和子进程只运行在本地工作区的 PRoot rootfs 里。
- stdio Server 的 tool 发现、tool 调用、取消和错误展示，与现有 SSE / Streamable HTTP MCP 使用同一套 `McpSessionRegistry` 和 `McpManager` 出口。
- 每个 stdio Server 绑定一个专用本地工作区；工作区必须有可用的 rootfs，不给 app 进程裸起子进程，也不在宿主机直接执行命令。
- 进程崩溃、rootfs 重装、工作区删除和服务禁用都有明确收尾，不留下孤儿进程。

## 非目标

- 不自动下载或安装 rootfs。用户先在工作区页面完成安装，再在 MCP 配置里选择该工作区。
- 不支持 SSH 工作区上的 stdio。SSH 的 stdin/stdout 通道和本地进程语义不同，后续单独设计。
- 不把 stdio Server 暴露给官方 RikkaHub 导出；官方客户端无法运行本机 MCP 进程。
- 不做多客户端共享一个 stdio 进程，也不做 MCP 协议代理。
- 不改现有 HTTP MCP 的配置和连接行为。

## 为什么必须用专用工作区

- 命令必须在 rootfs 里执行，才能使用用户安装的运行时、依赖和 `/workspace` 挂载；直接在 app 进程或宿主机 shell 起进程会绕过工作区隔离。
- 一个 stdio Server 对应一个长期子进程，生命周期比一次 `workspace_shell` 长；必须由 rootfs 后端统一收尾，避免 rootfs 重装后旧进程继续持有已删除的目录。
- 专用工作区把 MCP Server 和聊天工具的副作用分开，不和其它 stdio Server 共用一个 rootfs。它不是安全沙箱：绑定到同一 assistant 的工作区仍可被 `workspace_shell` 使用，界面要提示这一点。
- 复用 `ProotShellRunner` 的 PRoot 参数、`--kill-on-exit`、bind mount 和 rootfs shell，不另造一套 rootfs 启动逻辑。

## 配置与持久化

`McpServerConfig` 的持久化形态仍只含 HTTP 子类；运行时有非持久化的 `McpServerConfig.StdioServer`。stdio 配置单独放在 `Settings.stdioMcpServers`，用新的 DataStore preference key 持久化（不是把新子类塞进 `mcp_servers` 的 JSON）：

```kotlin
@Serializable
data class StdioMcpServer(
    val id: Uuid = Uuid.random(),
    val commonOptions: McpCommonOptions = McpCommonOptions(),
    // 专用本地工作区的 id；必须是 WorkspaceEntity.id
    val workspaceId: String,
    // 可执行文件名或绝对路径，不做 shell 解析
    val command: String,
    val args: List<String> = emptyList(),
    // 逐项传给子进程；值可能含密钥，toString 不打印值
    val env: List<Pair<String, String>> = emptyList(),
    // rootfs 内绝对路径，默认 /workspace
    val cwd: String = "/workspace",
    val startupTimeoutMs: Long = 30_000,
) {
    override fun toString(): String =
        "StdioMcpServer(id=$id, name=${commonOptions.name}, workspaceId=$workspaceId, " +
            "command=$command, args=${args.size}, envKeys=${env.map { it.first }}, cwd=$cwd)"
}
```

单独字段的原因：`McpServerConfig` 是带 `@SerialName` 的 sealed class，旧构建的 `ignoreUnknownKeys` 只能忽略未知字段，不能忽略未知的子类判别值。把 stdio 放进新的 `stdioMcpServers` 字段，旧构建不读这个 key，不会因为 `"stdio"` 反序列化失败而拒绝整份设置。设置按字段存储，已加 `SETTINGS_FIELDS` 条目和 `SettingsStore` 的 preference key，默认空列表；旧构建不认识这个 key，保存设置时也不会碰它。

运行时把两份配置合并：

```kotlin
fun Settings.allMcpServers(): List<McpServerConfig> =
    mcpServers + stdioMcpServers.map { it.toRuntimeConfig() }
```

`StdioMcpServer.toRuntimeConfig()` 生成 `McpServerConfig.StdioServer`（实现 `clone()`；`serverUrl` 返回空串，`connectionKey` 带 stdio 字段）。HTTP 的序列化列表不变，stdio 不参与 OAuth；UI 对 stdio 不显示授权入口。

`SettingsNormalization.validMcpServerIds` 已改为两份列表的 id 并集；之所以要并集，是因为每次设置写入都会按它过滤 assistant 的 stdio 引用。`ConversationConfig.getAssistantOf`、`SettingsRoutes`、`ConversationRoutes` 也按同一并集校验。

约束：

- `command` 不能为空，不能包含换行和 NUL；不提供 `shellCommand` 整串入口。
- `args` 逐项传给进程，不做拼接。
- `cwd` 是 rootfs 内的绝对路径，默认 `/workspace`，解析后必须落在 rootfs 内；不复用 `ProotShellRunner.prootCwd()` 的 `/workspace/` 相对映射。
- 工作区必须满足 `capabilities().rootfs`、`shellStatus == READY`、`hasRootfs(root)`；按能力判断，不直接比较 `WorkspaceType`。
- 同一工作区最多绑定一个 stdio Server；`SettingsNormalization` 和保存入口都要检查，备份恢复后的设置也要走同一检查。
- `commonOptions.enable` 控制启停；禁用时停止进程但保留配置。

## 工作区准备

用户流程：

1. 在扩展页创建一个本地工作区，命名如 `MCP: <服务器名>`，明确只给该 Server 使用。
2. 在工作区详情页安装 rootfs，等待 `READY`。
3. 在 MCP 配置里选择“stdio”，选择该工作区，填写命令、参数、env 和 cwd。

app 不自动创建隐藏工作区，也不自动触发 rootfs 下载。删除 MCP Server 不删除工作区。

删除工作区或重装 rootfs 时，必须先停止绑定到它的 stdio 进程。收尾放在数据层：`WorkspaceRepository.delete` 和 `WorkspaceRepository.installRootfs` 在删除/替换 `linuxDir` 之前调用 `McpManager.stopServersForWorkspace(workspace.id)`。`RootfsInstaller` 会 `deleteRecursively()` 再改名，不能只靠 UI 层关闭终端。MCP 侧不要注入 `WorkspaceRepository`，否则 `WorkspaceRepository → McpManager → WorkspaceRepository` 会形成 Koin 环；MCP 侧只依赖 `WorkspaceDAO` 和 `WorkspaceManager` 来解析 root、校验 READY/rootfs。

## 进程启动与 IO

在 `workspace` 模块增加长期进程接口，和一次性 `executeCommand` 分开：

```kotlin
data class WorkspaceProcessSpec(
    val root: String,
    val id: String,
    val command: String,
    val args: List<String>,
    val env: List<Pair<String, String>>,
    val cwd: String,
    val shellCompatibilityMode: Boolean = false,
)

data class WorkspaceProcess(
    val process: Process,
    val stdin: OutputStream,
    val stdout: InputStream,
    val stderr: InputStream,
    val terminate: (graceMillis: Long) -> Unit = { process.destroyForcibly() },
) {
    fun close(graceMillis: Long = 0) = terminate(graceMillis)
}

interface WorkspaceProcessLauncher {
    fun launch(spec: WorkspaceProcessSpec): WorkspaceProcess
}
```

实现放在本地 PRoot 后端：`ProotShellRunner.launchProcess` 负责进程，MCP 层通过 `WorkspaceManagerProcessLauncher` 调用 `WorkspaceManager.launchProcess`。

- 复用 `ProotShellRunner` 的 `prootArgs`、`ROOTFS_ENV`、rootfs shell 和 `--kill-on-exit`；这些目前是 private，落地时要提到可复用位置或抽成 internal builder，不能复制一份参数拼装。
- 先调用 `patcher.patch(linuxDir)`，再做进程启动；否则子进程可能没有 DNS 配置。
- 用非登录 `-c`，不用 `-lc`，避免 `/etc/profile` 或 rc 文件往 stdout 输出提示，污染 JSON-RPC。
- 命令通过 `rikkahub <envfile> <pidfile> <cmd> <args...>` 传给非登录 shell，参数逐项传递，不拼接 shell 字符串；cwd 按 rootfs 绝对路径直接使用。wrapper 是 `set -a; . "$1" || exit 1; set +a; echo "$$ $PPID" > "$2"; shift 2; exec "$@"`，env 读取失败直接退出，pid 文件在 exec 前写好。
- env 通过 rootfs 内的临时 env 文件注入，而不是 `ProcessBuilder.environment()`：`ROOTFS_ENV` 使用 `/usr/bin/env -i`，会清空继承环境，直接设 ProcessBuilder 环境变量到不了子进程。launcher 把 `KEY=VALUE` 做 shell 转义写到宿主的 `linuxDir(root)/tmp/rikkahub/mcp/<server-id>-<launch-token>.env`，子进程看到的是同名 rootfs `/tmp` 路径（模式 600），每次 launch 用新的 UUID 后缀，旧进程的 cleanup 不会删掉新进程的文件。不要放进 `/workspace`：那是 `filesDir(root)` 的 bind mount，会进自家备份、WebDAV 上传和文件浏览器。rootfs `/tmp` 不进这些导出，但同一工作区的 `workspace_read_file`/`workspace_write_file`/`workspace_shell` 仍能访问它和 `/proc/<pid>/environ`，这是设计声明的非沙箱边界，UI 要提示。`/tmp` 复用现有 `cleanupAllTempDirs` 的清理，rootfs 重装也会整目录消失，和 pid 文件同族。进程结束或下一次启动时清理 env 文件；这样 env 值不进入 `/proc/<pid>/cmdline`。`--kill-on-exit`、`PROOT_LOADER`、`PROOT_TMP_DIR`、`TMPDIR` 仍由 launcher 维护。
- `stdin/stdout/stderr` 全部保持 pipe，不做 PTY；stderr 交给 MCP transport 独占，不再另开一个 64 KiB reader，避免两个读者抢同一根 pipe。
- 进程组终止复用现有的 `terminateProot`/pid 文件逻辑；启动失败、rootfs 不可用、SSH 工作区直接返回明确错误。
- `RikkaHubApp` 启动时的 tempDir 清理必须在 `McpManager` 初始化之前完成，不能两个各自 `appScope.launch` 竞跑：把清理做成可等待的 startup step，或让 `McpManager` 等清理完成的信号。清理跳过 launcher 当前内存 pid 登记表中仍存活的进程的 `PROOT_TMP_DIR`/`TMPDIR`；上一次 app 运行留在磁盘上的 pid 文件已无效，`--kill-on-exit` 已保证旧 tracee 消失，不需要按旧 pid 文件保留目录。

## MCP SDK 接入

SDK `0.15.0-rikka.4` 的实际 API 是：

```kotlin
val transport = StdioClientTransport(
    input = process.stdout.asSource().buffered(),
    output = process.stdin.asSink().buffered(),
    error = process.stderr.asSource().buffered(),
)
val client = Client(
    clientInfo = Implementation(name = server.commonOptions.name, version = "1.0"),
)
client.connect(transport)
```

`Client` 构造只接受 `Implementation` 和可选的 `ClientOptions`；连接是 `sdkClient.connect(transport)`，不是构造时注入 transport。`kotlinx.io` 的 `asSource`/`asSink` 返回 `RawSource`/`RawSink`，必须 `.buffered()` 才能满足 `StdioClientTransport` 的 `Source`/`Sink` 参数。`kotlinx-io-core` 目前由 MCP SDK 传递提供；如果 compile classpath 不暴露它，需要在版本目录里显式加依赖。

`McpSessionRegistry` 增加 stdio 分支。凡是直接读 `settings.mcpServers` 的路径都要改成 `settings.allMcpServers()`：`McpManager.init` 的 `reconcile` 输入、`McpManager.getAllAvailableTools` 的过滤、`McpSessionRegistry.addClient` 按 id 查当前配置、`McpSessionRegistry.syncTools` 把发现结果写回；其中 `syncTools` 要按运行类型写回 `mcpServers` 或 `stdioMcpServers`，不能一律写回 HTTP 列表。C111.4 已把 `AssistantDetailVM.mcpServerConfigs` 和聊天附件面板的 MCP 入口切到 `allMcpServers()`；`ConversationConfig` 和两个 Web 路由的 id 校验已改成 HTTP+stdio 并集。

- `McpSessionRegistry` 在 stdio 分支直接构造上述 transport；`createTransport` 只处理 HTTP，遇到 stdio 会报错。`createSdkClient` 仍用 `Client(clientInfo = Implementation(...))`。
- 连接、重连、状态和工具同步复用现有 `connectSession` / `syncTools` / `MAX_RECONNECT_ATTEMPTS`（5 次）和 `calculateBackoffDelay`，不另造一套 3 次策略。
- `reconcile` 像 HTTP 一样对启用且名称非空的 stdio Server 主动 `addClient`；`syncTools` 在连接时拉 `listTools`，UI 的 `getClient` 和工具可用性沿现有路径工作。不做“懒启动”，否则工具从未发现，UI 会一直显示不可用。
- 增加 `StdioSession` 需要的进程句柄、`lastUsedAt` 和退出监控；`McpSession` 现有的 mutex、reconnectJob、statusStore 继续复用，不复制状态机。
- 进程退出由独立协程 `process.waitFor()` 观察，记录 exit code 后更新 `McpStatus`；stderr 由 transport 独占并走 SDK 的 debug 输出，registry 不另做摘要。`onClose`/`onError` 本身不带退出码，不能代替 wait。
- `McpConnectionKey` 增加 `workspaceId`、`command`、`args`、`env`、`cwd`；否则编辑这些字段不会触发重连，旧子进程会继续跑。
- 需要补的 exhaustive `when` 包括 `McpConfig.serverUrl`、`McpSessionRegistry.createTransport`、`connectionKey`、`SettingMcpDetailPage` 的传输展示和 `SettingMcpPage` 的服务器列表；`installTransportCallbacks` 是传输无关的闭合回调，不在这个列表里。
- 关闭顺序：close transport → close stdin → 等待 5 秒 → `terminateProot` 宽限终止 → SIGKILL。`--kill-on-exit` 只作为兜底，不作为正常关闭路径。
- 禁用、删除 Server、工作区删除、rootfs 重装都调用同一 `stopStdioSession`；Android 真实设备不会调用 `Application.onTerminate`，app 进程被杀时依赖 `--kill-on-exit` 和进程组清理，不把正常退出当保证路径。

## UI

MCP 设置页新增传输类型“stdio”，字段：

- 工作区：列出满足本地 rootfs 和 READY 的工作区；不满足时显示去工作区页安装的入口。
- 命令、参数（每行一个）、env（key=value）、cwd、启动超时。
- 状态：未启动、启动中、已连接、错误；显示“停止/重启”动作。
- 工作区绑定冲突时阻止保存。
- 删除 Server 提示“不会删除工作区”；删除工作区提示“会先停止绑定到它的 MCP Server”。
- 四份 locale 文案按现有 MCP 设置页前缀增加。

## 与现有功能的关系

- MCP 工具在 assistant 的 `mcpServers` 里按 id 选择，stdio 和 HTTP Server 混用；assistant 的 `mcpServers` 不再只校验 HTTP 列表。
- 工具审批沿用 `McpTool.needsApproval` 和会话审批状态；stdio 不绕过审批。
- 仅作为工具的 MCP Server 不进入聊天请求上下文；服务端 `notifications` 先只记日志，不做 UI 推送。
- 官方兼容导出过滤 `stdioMcpServers` 和引用它们的 assistant id，避免官方客户端解码未知配置；自家备份保留完整配置。
- 旧构建不认识新的 DataStore key，会忽略 `stdioMcpServers`，能正常解码设置；该配置本身在旧构建保存设置时不会被删除，但旧构建的 `SettingsNormalization` 只把 HTTP id 视为有效，会从 assistant 的 `mcpServers` 里清掉 stdio 引用。旧构建也无法运行这些 Server，这是已知降级限制，已写进 README。
- 日志只记 Server id、命令 basename 和退出码；stderr 由 SDK debug 输出，registry 暂不汇总；不记录 env 值、完整命令行或协议正文。`StdioMcpServer.toString()` 已对 env 做 key 级脱敏。

## 失败处理

| 场景 | 行为 |
| --- | --- |
| 工作区不是 rootfs 工作区 / 没有 rootfs | 保存或启动失败，提示先安装 rootfs |
| 命令不存在 / 没有执行权限 | 启动失败，状态 Error，stderr 由 SDK debug 输出 |
| 启动超时 | 终止进程并等待退出监控收尾，提示运行命令检查 |
| 进程运行中崩溃 | 置 Error，最多按现有 5 次预算和退避重启 |
| 工作区删除 / rootfs 重装 | 先在数据层停止进程，再执行删除/替换 |
| app 进程被系统回收 | `--kill-on-exit` 和进程组清理兜底；下次启动重新按配置启动 |
| 工具调用取消 | 取消请求；进程保留，不因单次取消重启 |
| 同一工作区第二个 Server | 保存按钮拒绝；加载/恢复/导入时归一化保留第一条、静默丢弃后出现的重复项，assistant 对丢弃项的引用同轮清掉 |

## 测试与验收

JVM：

- `StdioMcpServer` 序列化、默认值、env `toString` 脱敏、旧 settings 解码。
- 命令/参数/cwd 校验，SSH/无 rootfs 工作区拒绝，同一工作区重复绑定在保存按钮拒绝、在归一化层保留第一条。
- 官方导出过滤 stdio 配置和 assistant id。
- `WorkspaceManagerProcessLauncher` 的上下文转发和 `ProotShellRunner.buildProcessArgs` 的参数构造用 fake/定向测试验证，不依赖真实 PRoot；同 id 连续 launch 的 env/pid 路径必须不同。
- `McpConnectionKey` 对 command/args/env/cwd/workspaceId 变化敏感。
- 旧构建降级：用本设计落地前 APK 解码含 `stdioMcpServers` 的设置，确认不崩、新配置仍在、assistant 的 stdio 引用按旧构建归一化被清掉；JVM 侧用不带该字段的旧 DTO 反序列化新 JSON。

开发机 instrumentation：

- 本地工作区 rootfs 启动一个最小 stdio echo Server，完成 initialize、listTools、callTool、关闭。
- 启动失败、命令不存在、rootfs 未安装、工作区删除和 rootfs 重装的收尾。
- 进程崩溃后的重连预算和状态。
- 工具调用取消不影响后续调用。

设备人工验收：

- 创建专用工作区、安装 rootfs、添加 stdio Server、发现工具并在聊天里调用。
- 重开 app 后按配置重新启动；禁用 Server 后进程消失。
- 官方兼容导出不含 stdio Server，自家备份恢复后配置仍在。

## 后续可选

- stdio Server 的 `stderr` 是否提供完整日志查看入口，还是只显示错误摘要。
- 是否支持 SSH 工作区上的 stdio 进程。
- Android cached-process 长期存活时是否要主动空闲回收；当前按配置常驻，禁用或工作区收尾时停止。
