package me.rerere.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProotShellRunnerCommandTest {

    private val runner = ProotShellRunner(nativeLibraryDir = File("/native"))

    private fun context() = WorkspaceShellContext(
        root = "/root",
        command = "echo hi",
        cwd = "",
        filesDir = File("/files"),
        linuxDir = File("/linux"),
        tempDir = File("/tmp"),
        workingDir = File("/files"),
        timeoutMillis = 1_000,
    )

    @Test
    fun `foreground command writes shell and proot pids before running`() {
        val command = runner.buildCommand(
            context = context(),
            proot = File("/native/libproot_exec.so"),
            pidPath = "/tmp/rikkahub/exec/job.pid",
        )

        val script = command.single { it.contains("eval") }
        assertTrue(script.startsWith("echo \"\$\$ \$PPID\" > \"\$3\"; "))
        assertEquals("/tmp/rikkahub/exec/job.pid", command.last())
        assertEquals("rikkahub", command[command.lastIndex - 3])
        assertEquals("/workspace", command[command.lastIndex - 2])
    }

    @Test
    fun `command text is passed as a positional argument`() {
        val command = runner.buildCommand(
            context = context(),
            proot = File("/native/libproot_exec.so"),
            pidPath = "/tmp/rikkahub/exec/job.pid",
        )

        assertEquals("echo hi", command[command.lastIndex - 1])
    }
}
