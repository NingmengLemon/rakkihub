# stdio MCP：专用工作区与 rootfs 设计

## 状态

设计稿，尚未实现。本文只定义边界、配置形状、进程模型和验收方式；代码落地时再按阶段拆 C 项。

## 目标

- 支持通过本地 stdio 连接的 MCP Server，命令和子进程只运行在本地工作区的 PRoot rootfs 里。
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
- 一个 stdio Server 对应一个长期子进程，生命周期比一次 `workspace_shell` 长；必须由工作区终端/rootfs 的收尾逻辑统一管理，避免 rootfs 重装后旧进程继续持有已删除的目录。
- 专用工作区限制 blast radius：MCP Server 默认只能访问自己的工作区文件和应用挂载，不自动获得其它工作区。
- 现有 `WorkspaceManager` 已经提供 PRoot 参数、`--kill-on-exit`、bind mount 和环境清理；stdio 复用这些能力，而不是另造一套。

## 配置形状

在 `McpServerConfig` 增加一个子类：

```kotlin
@Serializable
@SerialName("stdio")
data class StdioServer(
    override val id: Uuid = Uuid.random(),
    override val commonOptions: McpCommonOptions = McpCommonOptions(),
    // 专用本地工作区的 id；必须是 WorkspaceEntity.id，不允许 SSH
    val workspaceId: String,
    // 可执行文件名或绝对路径，不做 shell 解析
    val command: String,
    val args: List<String> = emptyList(),
    // 逐项传给子进程；值可能含密钥，日志只记录 key
    val env: List<Pair<String, String>> = emptyList(),
    // rootfs 内的工作目录，默认 /workspace
    val cwd: String = "/workspace",
    val startupTimeoutMs: Long = 30_000,
    val idleTimeoutMs: Long = 10 * 60_000,
) : McpServerConfig()
```

约束：

- `command` 不能为空，不能包含换行和 NUL；不提供 `shellCommand` 之类的整串入口，避免注入和不可控的 shell 解释。
- `args` 逐项传给进程，不做拼接。
- `cwd` 必须是 rootfs 内的绝对路径或以 `/workspace` 开头的相对路径，解析后必须落在工作区内。
- `workspaceId` 必须指向 `WorkspaceType.LOCAL`、`shellStatus = READY`、`hasRootfs(root)` 为真的工作区。
- 同一工作区最多绑定一个 stdio Server；绑定冲突时配置保存失败，提示先换工作区或停用旧 Server。
- `commonOptions.enable` 控制启停；禁用时停止进程但保留配置。

## 工作区准备

用户流程：

1. 在扩展页创建一个本地工作区，命名为 `MCP: <服务器名>` 之类，明确它只给该 Server 使用。
2. 在工作区详情页安装 rootfs，等待 `READY`。
3. 在 MCP 配置里选择“stdio”，选择该工作区，填写命令、参数和 env。

app 不自动创建隐藏工作区，也不自动触发 rootfs 下载。这样用户能看见磁盘占用和 rootfs 状态；如果以后要提供“一键创建”，作为独立入口，仍复用 `WorkspaceRepository.create` 和 `installRootfs`。

删除 MCP Server 不删除工作区；删除工作区或重装 rootfs 时，必须先停止绑定到它的 stdio 进程。收尾挂在 `WorkspaceRepository` 的删除/重装入口和 `WorkspaceTerminalSessionManager.stopForRoot` 同类位置，不能只靠 `Process.destroy`。

## 进程启动与 IO

在 `workspace` 模块增加一个长期进程接口，和现有一次性 `executeCommand` 分开：

```kotlin
data class WorkspaceProcessSpec(
    val root: String,
    val command: String,
    val args: List<String>,
    val env: List<Pair<String, String>>,
    val cwd: String,
)

data class WorkspaceProcess(
    val process: Process,
    val stdin: OutputStream,   // 写给子进程
    val stdout: InputStream,   // MCP JSON-RPC 输入
    val stderr: InputStream,   // 日志/诊断
)
```

实现放在本地 PRoot 后端：

- 复用 `ProotShellRunner` 的 `prootArgs`、`ROOTFS_ENV`、rootfs shell 和 `--kill-on-exit`。
- `ProcessBuilder` 用 `context.filesDir` 作为宿主 cwd，子进程的 cwd 通过 rootfs shell 的 `-lc`/`-c` 设置；命令通过 `exec "$0" "$@"` 形式传入，参数逐项传递，不拼接 shell 字符串。
- `stdin/stdout/stderr` 全部保持 pipe，不做 PTY 转换；`stderr` 单独读取，最多保留 64 KiB 的环形缓冲，用于错误卡片和日志。
- 进程组终止复用 `terminateProot`/pid 文件逻辑；启动失败、启动超时、rootfs 不可用时返回明确错误。
- SSH 工作区直接拒绝，错误文案指向“请选择本地工作区”。

接口返回 `WorkspaceProcess` 而不是只返回 `Process`，让 MCP 层不需要知道 PRoot 参数；后续如果支持 SSH，在 backend 层补实现即可。

## MCP SDK 接入

`com.github.rikkahub.mcp-kotlin-sdk` 的 `StdioClientTransport` 已经接受三个 `kotlinx.io` 流，不需要 SDK 自己拉起进程：

```kotlin
val transport = StdioClientTransport(
    input = process.stdout.asSource(),
    output = process.stdin.asSink(),
    error = process.stderr.asSource(),
)
val client = Client(transport, clientInfo)
client.connect()
```

`McpSessionRegistry` 增加 stdio 分支：

- 按 `StdioServer.id` 维护 `StdioSession(process, transport, client, lastUsedAt)`。
- 首次需要工具或调用工具时懒启动；启动失败置 `McpStatus.Error`，不阻塞其它 Server。
- 工具列表走现有 `Client.listTools()` 映射到 `McpTool`，调用走 `Client.callTool()`；图片等结果继续交给现有 `FilesManager` 保存。
- 配置禁停、binding 变化、工作区删除/rootfs 重装、进程退出时关闭 transport 并清理进程；退出原因写入 `McpStatus`。
- 崩溃重启最多 3 次，指数退避，超过后停止并显示重启按钮；不无限重启。
- 空闲超时默认 10 分钟，可配置；有进行中的请求时不回收。

`McpManager` 的 `getClient` / `getAllAvailableTools` / `callTool` 保持现有签名，外部调用方不需要区分传输类型。

## UI

MCP 设置页新增传输类型“stdio”，字段：

- 工作区：只列出本地且 `READY` 的工作区；不满足时显示去工作区页安装的入口。
- 命令、参数（每行一个）、env（key=value）、cwd、启动超时、空闲超时。
- 状态：未启动、启动中、已连接、错误；显示“停止/重启”动作。
- 工作区绑定冲突时阻止保存。
- 删除 Server 提示“不会删除工作区”，删除工作区提示“会先停止绑定到它的 MCP Server”。

四份 locale 文案按现有 MCP 设置页前缀增加，不把命令和 env 值写进日志或导出诊断。

## 与现有功能的关系

- MCP 工具在 assistant 的 `mcpServers` 里按 id 选择，和 HTTP Server 完全混用。
- 工具审批沿用 `McpTool.needsApproval` 和会话审批状态；stdio 不绕过审批。
- 仅作为工具的 MCP Server 不进入聊天请求上下文；服务端 `notifications` 先只记日志，不做 UI 推送。
- 官方兼容导出在 `Settings.forOfficialExport()` 里过滤掉 stdio Server，避免官方客户端解码未知 transport 类型；自家备份保留完整配置。
- MCP OAuth 只作用于 HTTP；stdio 不显示 OAuth 字段。
- 日志只记 Server id、命令 basename、退出码和 stderr 摘要；不记录 env 值、完整命令行或协议正文。

## 失败处理

| 场景 | 行为 |
| --- | --- |
| 工作区不是 LOCAL / 没有 rootfs | 保存或启动失败，提示先安装 rootfs |
| 命令不存在 / 没有执行权限 | 启动失败，状态 Error，stderr 摘要 |
| 启动超时 | 终止进程，提示运行命令检查 |
| 进程运行中崩溃 | 置 Error，最多自动重启 3 次 |
| 工作区删除 / rootfs 重装 | 先停止进程，再执行删除/替换 |
| app 进程退出 | `--kill-on-exit` 和进程组清理兜底，不依赖下次启动 |
| 工具调用取消 | 取消请求；进程保留，不因单次取消重启 |
| 同一工作区第二个 Server | 保存失败，要求专用工作区 |

## 测试与验收

JVM：

- `StdioServer` 序列化、默认值、旧 settings 解码。
- 命令/参数/cwd 校验，SSH 工作区拒绝，同一工作区重复绑定拒绝。
- 官方导出过滤 stdio Server。
- `WorkspaceProcessLauncher` 的参数构造用 fake runner 验证，不依赖真实 PRoot。

开发机 instrumentation：

- 用本地工作区 rootfs 启动一个最小 stdio echo Server，完成 initialize、listTools、callTool、关闭。
- 启动失败、命令不存在、rootfs 未安装、工作区删除和 rootfs 重装的收尾。
- 进程崩溃后的重启上限和状态。
- 工具调用取消不影响后续调用。

设备人工验收：

- 创建专用工作区、安装 rootfs、添加 stdio Server、发现工具并在聊天里调用。
- 重开 app 后按配置懒启动；禁用 Server 后进程消失。
- 官方兼容导出不含 stdio Server，自家备份恢复后配置仍在。

## 待定

- 专用工作区是否允许自动创建：当前建议手动创建，避免后台下载 rootfs 和隐藏磁盘占用。
- 空闲超时的默认值和是否允许无限期常驻。
- stdio Server 的 `stderr` 是否要给用户完整日志入口，还是只显示错误摘要。
- 是否支持多个 stdio Server 共享同一个 rootfs（当前设计明确禁止，先保证生命周期简单）。
