package me.rerere.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

class WorkspaceManagerProcessLauncherTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeRunner : WorkspaceShellRunner {
        var captured: WorkspaceProcessContext? = null
        var terminatedWith: Long? = null

        override fun execute(context: WorkspaceShellContext): WorkspaceCommandResult =
            error("not used")

        override fun launchProcess(context: WorkspaceProcessContext): WorkspaceProcess {
            captured = context
            val process = object : Process() {
                private val stdin = ByteArrayOutputStream()
                private val stdout = ByteArrayInputStream(ByteArray(0))
                private val stderr = ByteArrayInputStream(ByteArray(0))

                override fun getOutputStream(): OutputStream = stdin
                override fun getInputStream(): InputStream = stdout
                override fun getErrorStream(): InputStream = stderr
                override fun waitFor(): Int = 0
                override fun exitValue(): Int = 0
                override fun destroy() = Unit
            }
            return WorkspaceProcess(
                process = process,
                stdin = process.outputStream,
                stdout = process.inputStream,
                stderr = process.errorStream,
                terminate = { graceMillis -> terminatedWith = graceMillis },
            )
        }
    }

    @Test
    fun `launcher forwards process spec through workspace context`() {
        val runner = FakeRunner()
        val manager = WorkspaceManager(tempFolder.newFolder("workspaces"), shellRunner = runner)
        manager.ensureWorkspace("ws")
        val launcher = WorkspaceManagerProcessLauncher(manager)

        val process = launcher.launch(
            WorkspaceProcessSpec(
                root = "ws",
                id = "server-1",
                command = "node",
                args = listOf("server.js"),
                env = listOf("TOKEN" to "secret"),
                cwd = "/workspace/app",
            )
        )
        process.close(graceMillis = 2_500)

        val context = requireNotNull(runner.captured)
        assertEquals("ws", context.root)
        assertEquals("server-1", context.id)
        assertEquals("/workspace/app", context.cwd)
        assertEquals(listOf("TOKEN" to "secret"), context.env)
        assertEquals(manager.filesDir("ws").absolutePath, context.filesDir.absolutePath)
        assertEquals(manager.linuxDir("ws").absolutePath, context.linuxDir.absolutePath)
        assertEquals(2_500L, runner.terminatedWith)
    }

    @Test
    fun `launcher rejects invalid id and relative cwd before touching the backend`() {
        val runner = FakeRunner()
        val manager = WorkspaceManager(tempFolder.newFolder("workspaces"), shellRunner = runner)
        manager.ensureWorkspace("ws")
        val launcher = WorkspaceManagerProcessLauncher(manager)
        val base = WorkspaceProcessSpec(root = "ws", id = "server-1", command = "node")

        assertThrows(IllegalArgumentException::class.java) {
            launcher.launch(base.copy(id = "../escape"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            launcher.launch(base.copy(cwd = "relative"))
        }
        assertEquals(null, runner.captured)
    }

    @Test
    fun `default manager returns unsupported for host backend`() {
        val manager = WorkspaceManager(tempFolder.newFolder("workspaces"))
        manager.ensureWorkspace("ws")
        assertThrows(IllegalStateException::class.java) {
            manager.launchProcess(
                "ws",
                WorkspaceProcessSpec(root = "ws", id = "server-1", command = "node"),
            )
        }
    }
}
