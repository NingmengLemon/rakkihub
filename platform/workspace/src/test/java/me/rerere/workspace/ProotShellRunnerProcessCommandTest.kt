package me.rerere.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProotShellRunnerProcessCommandTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `process command uses rootfs cwd, env file and pid wrapper`() {
        val native = tempFolder.newFolder("native")
        val files = tempFolder.newFolder("files")
        val linux = tempFolder.newFolder("linux")
        val temp = tempFolder.newFolder("tmp")
        val runner = ProotShellRunner(native)
        val context = WorkspaceProcessContext(
            root = "workspace",
            id = "server-1",
            command = "node",
            args = listOf("server.js", "--flag"),
            env = listOf("TOKEN" to "secret"),
            cwd = "/workspace/app",
            filesDir = files,
            linuxDir = linux,
            tempDir = temp,
        )

        val artifacts = runner.nextProcessFiles(context)
        val args = runner.buildProcessArgs(context, artifacts)

        assertEquals(File(native, "libproot_exec.so").absolutePath, args.first())
        assertTrue(args.containsAll(listOf("-r", linux.absolutePath, "-w", "/workspace/app")))
        assertTrue("${files.absolutePath}:/workspace" in args)
        assertTrue(artifacts.envGuest in args)
        assertTrue(artifacts.pidGuest in args)
        assertTrue(
            args.contains("set -a; . \"\$1\" || exit 1; set +a; echo \"\$\$ \$PPID\" > \"\$2\"; shift 2; exec \"\$@\"")
        )
        assertTrue(args.takeLast(3).containsAll(listOf("node", "server.js", "--flag")))
        assertTrue("/usr/bin/env" in args)
        assertTrue("-i" in args)
    }

    @Test
    fun `each launch gets unique env and pid files for the same server id`() {
        val native = tempFolder.newFolder("native")
        val files = tempFolder.newFolder("files")
        val linux = tempFolder.newFolder("linux")
        val temp = tempFolder.newFolder("tmp")
        val runner = ProotShellRunner(native)
        val context = WorkspaceProcessContext(
            root = "workspace",
            id = "server-1",
            command = "node",
            args = emptyList(),
            env = emptyList(),
            cwd = "/workspace",
            filesDir = files,
            linuxDir = linux,
            tempDir = temp,
        )

        val first = runner.nextProcessFiles(context)
        val second = runner.nextProcessFiles(context)

        assertTrue(first.envHost != second.envHost)
        assertTrue(first.pidHost != second.pidHost)
        assertTrue(first.envHost.name.startsWith("server-1-"))
        assertTrue(first.pidHost.name.startsWith("server-1-"))
    }
}
