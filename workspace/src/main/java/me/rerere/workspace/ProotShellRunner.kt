package me.rerere.workspace

import java.io.File
import java.util.UUID

data class WorkspaceBindMount(
    val source: File,
    val target: String,
    /** AI 写入该目录时免强制审批 */
    val writable: Boolean = false,
) {
    init {
        require(target.startsWith("/")) { "Bind mount target must be absolute: $target" }
    }
}

class ProotShellRunner(
    private val nativeLibraryDir: File,
    private val patcher: RootfsPatcher = RootfsPatcher(),
) : WorkspaceShellRunner {
    override fun execute(context: WorkspaceShellContext): WorkspaceCommandResult {
        if (!WorkspaceManager.isUsableRootfs(context.linuxDir)) {
            return WorkspaceCommandResult(
                exitCode = 127,
                stdout = "",
                stderr = "Rootfs is not installed",
            )
        }

        val proot = File(nativeLibraryDir, PROOT_EXEC)
        val loader = File(nativeLibraryDir, PROOT_LOADER)
        if (!proot.isFile) {
            return WorkspaceCommandResult(
                exitCode = 127,
                stdout = "",
                stderr = "proot executable not found: ${proot.absolutePath}",
            )
        }
        if (!loader.isFile) {
            return WorkspaceCommandResult(
                exitCode = 127,
                stdout = "",
                stderr = "proot loader not found: ${loader.absolutePath}",
            )
        }

        context.tempDir.mkdirs()
        patcher.patch(context.linuxDir)
        // 进程号写进 Rootfs 里的 pid 文件, 超时或取消时据此结束 PRoot 跟踪的整组进程
        val pidFile = File(context.linuxDir, "${EXEC_DIR.trimStart('/')}/${UUID.randomUUID()}.pid")
        pidFile.parentFile?.mkdirs()
        val process = ProcessBuilder(buildCommand(context, proot, "$EXEC_DIR/${pidFile.name}"))
            .directory(context.filesDir)
            .redirectErrorStream(false)
            .apply {
                if (context.shellCompatibilityMode) {
                    environment()["PROOT_NO_SECCOMP"] = "1"
                } else {
                    environment().remove("PROOT_NO_SECCOMP")
                }
                environment()["PROOT_LOADER"] = loader.absolutePath
                environment()["PROOT_TMP_DIR"] = context.tempDir.absolutePath
                environment()["TMPDIR"] = context.tempDir.absolutePath
            }
            .start()

        return try {
            // 超时或取消时直接结束, 不留宽限期: 调用方已经不再等这条命令
            process.readResult(context.timeoutMillis, context.stdin) { terminateProot(this, pidFile) }
        } finally {
            pidFile.delete()
        }
    }

    override fun terminalLaunch(context: WorkspaceTerminalContext): WorkspaceTerminalLaunch {
        val shell = WorkspaceManager.rootfsShell(context.linuxDir)
        val args = prootArgs(context.linuxDir, context.filesDir, WORKSPACE_DIR, context.bindMounts)
        args += ROOTFS_ENV
        args += listOf(
            "USER=root",
            "SHELL=$shell",
            shell,
        )

        val env = mutableListOf(
            "PROOT_LOADER=${File(nativeLibraryDir, PROOT_LOADER).absolutePath}",
            "PROOT_TMP_DIR=${context.tempDir.absolutePath}",
            "TMPDIR=${context.tempDir.absolutePath}",
        )
        if (context.shellCompatibilityMode) {
            env += "PROOT_NO_SECCOMP=1"
        }

        return WorkspaceTerminalLaunch(
            executable = File(nativeLibraryDir, PROOT_EXEC).absolutePath,
            cwd = context.filesDir.absolutePath,
            args = args,
            env = env,
        )
    }

    override fun launchProcess(context: WorkspaceProcessContext): WorkspaceProcess {
        check(WorkspaceManager.isUsableRootfs(context.linuxDir)) { "Rootfs is not installed" }
        val proot = File(nativeLibraryDir, PROOT_EXEC)
        val loader = File(nativeLibraryDir, PROOT_LOADER)
        check(proot.isFile) { "proot executable not found: ${proot.absolutePath}" }
        check(loader.isFile) { "proot loader not found: ${loader.absolutePath}" }

        context.tempDir.mkdirs()
        patcher.patch(context.linuxDir)
        context.bindMounts.forEach { it.source.mkdirs() }

        val envFile = File(context.linuxDir, "tmp/rikkahub/mcp/${context.id}.env")
        val pidFile = File(context.linuxDir, "tmp/rikkahub/exec/${context.id}.pid")
        envFile.parentFile?.mkdirs()
        pidFile.parentFile?.mkdirs()
        writeEnvFile(envFile, context.env)

        val args = buildProcessArgs(context)

        val process = ProcessBuilder(args)
            .directory(context.filesDir)
            .redirectErrorStream(false)
            .apply {
                if (context.shellCompatibilityMode) {
                    environment()["PROOT_NO_SECCOMP"] = "1"
                } else {
                    environment().remove("PROOT_NO_SECCOMP")
                }
                environment()["PROOT_LOADER"] = loader.absolutePath
                environment()["PROOT_TMP_DIR"] = context.tempDir.absolutePath
                environment()["TMPDIR"] = context.tempDir.absolutePath
            }
            .start()
        // 正常退出和崩溃都清掉临时环境文件；下次启动也会覆盖，不依赖这条线程
        Thread {
            process.waitFor()
            envFile.delete()
            pidFile.delete()
        }.apply {
            name = "rikkahub-mcp-cleanup-${context.id}"
            isDaemon = true
        }.start()
        return WorkspaceProcess(
            process = process,
            stdin = process.outputStream,
            stdout = process.inputStream,
            stderr = process.errorStream,
            terminate = { graceMillis -> terminateProot(process, pidFile, graceMillis) },
        )
    }

    internal fun buildProcessArgs(context: WorkspaceProcessContext): List<String> {
        val args = mutableListOf(File(nativeLibraryDir, PROOT_EXEC).absolutePath)
        args += prootArgs(context.linuxDir, context.filesDir, context.cwd, context.bindMounts)
        args += ROOTFS_ENV
        args += listOf(
            WorkspaceManager.rootfsShell(context.linuxDir),
            "-c",
            PROCESS_WRAPPER,
            "rikkahub",
            "/tmp/rikkahub/mcp/${context.id}.env",
            "/tmp/rikkahub/exec/${context.id}.pid",
            context.command,
        )
        args += context.args
        return args
    }

    private fun writeEnvFile(file: File, env: List<Pair<String, String>>) {
        for ((name, _) in env) {
            require(name.matches(ENV_NAME_REGEX)) { "Invalid env name: $name" }
        }
        val content = env.joinToString("\n") { (name, value) ->
            require('\u0000' !in value && '\n' !in value && '\r' !in value) {
                "Env value contains a forbidden character: $name"
            }
            "$name=${shellQuote(value)}"
        }
        file.writeText(if (content.isBlank()) "" else content + "\n")
        file.setReadable(false, false)
        file.setWritable(false, false)
        file.setReadable(true, true)
        file.setWritable(true, true)
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    internal fun buildCommand(
        context: WorkspaceShellContext,
        proot: File,
        pidPath: String,
    ): List<String> {
        val command = mutableListOf(proot.absolutePath)
        command += prootArgs(context.linuxDir, context.filesDir, context.prootCwd(), context.bindMounts)
        command += ROOTFS_ENV
        command += listOf(
            // 非交互执行约定, 抑制各类 CLI 的交互行为 (确认提示/分页器/颜色转义)
            "CI=true",
            "NO_COLOR=1",
            "PAGER=cat",
            WorkspaceManager.rootfsShell(context.linuxDir),
            "-l",
            "-c",
            // 命令通过位置参数传入, 避免任何转义; eval "$2" 对命令文本只求值一次, 等价于 bash -c "$cmd"
            TRACKED_SCRIPT,
            "rikkahub",
            context.prootCwd(),
            context.command,
            pidPath,
        )
        return command
    }

    /** PRoot 自身的参数: Rootfs、工作目录与挂载表, 命令执行和交互式终端共用 */
    private fun prootArgs(
        linuxDir: File,
        filesDir: File,
        cwd: String,
        bindMounts: List<WorkspaceBindMount>,
    ): MutableList<String> {
        val args = mutableListOf(
            "--root-id",
            "--link2symlink",
            "--kill-on-exit",
            "-r",
            linuxDir.absolutePath,
            "-w",
            cwd,
            "-b",
            "${filesDir.absolutePath}:$WORKSPACE_DIR",
        )

        bindMounts.forEach { mount ->
            if (mount.source.exists()) {
                args += "-b"
                args += "${mount.source.absolutePath}:${mount.target.trimEnd('/')}"
            }
        }

        WorkspaceManager.KERNEL_FS_MOUNTS.forEach { path ->
            if (File(path).exists()) {
                args += "-b"
                args += path
            }
        }
        return args
    }

    private fun WorkspaceShellContext.prootCwd(): String {
        val normalized = cwd.trim().trim('/')
        return if (normalized.isBlank()) {
            WORKSPACE_DIR
        } else {
            "$WORKSPACE_DIR/$normalized"
        }
    }

    private companion object {
        private const val PROOT_EXEC = "libproot_exec.so"
        private const val PROOT_LOADER = "libproot_loader.so"
        private val WORKSPACE_DIR = WorkspaceManager.ROOTFS_WORKSPACE_DIR

        /** Rootfs 内存放前台命令进程号的目录 */
        private const val EXEC_DIR = "/tmp/rikkahub/exec"

        // 先把 shell 与 PRoot 的进程号写入 $3, 终止时据此给整组进程发信号, 见 terminateProot
        private const val TRACKED_SCRIPT = "echo \"\$\$ \$PPID\" > \"\$3\"; cd -- \"\$1\" && eval \"\$2\""

        // 参数: 0=rikkahub, 1=env 文件, 2=pid 文件, 3=命令, 4..=参数
        private const val PROCESS_WRAPPER =
            "set -a; . \"\$1\"; set +a; echo \"\$\$ \$PPID\" > \"\$2\"; shift 2; exec \"\$@\""
        private val ENV_NAME_REGEX = Regex("[A-Za-z_][A-Za-z0-9_]*")

        /** 清空宿主机环境变量后 Rootfs 内的基础环境 */
        private val ROOTFS_ENV = listOf(
            "/usr/bin/env",
            "-i",
            "HOME=${WorkspaceManager.ROOTFS_HOME_DIR}",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TERM=xterm-256color",
            "LANG=C.UTF-8",
            "LC_ALL=C.UTF-8",
        )
    }
}
