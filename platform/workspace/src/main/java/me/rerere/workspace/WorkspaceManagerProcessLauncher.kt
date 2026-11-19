package me.rerere.workspace

/** 把长期 stdio 进程的启动委托给 [WorkspaceManager]，让 MCP 层不直接碰 PRoot 参数。 */
class WorkspaceManagerProcessLauncher(
    private val manager: WorkspaceManager,
) : WorkspaceProcessLauncher {
    override fun launch(spec: WorkspaceProcessSpec): WorkspaceProcess =
        manager.launchProcess(spec.root, spec)
}
