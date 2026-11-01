package me.rerere.rikkahub.data.files

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 附件清理的删除判据。
 *
 * 清理是「宁可少收垃圾也不能删错」的一侧：还能被引用到的、解析不了的地址指向的、以及刚写了一半
 * 的文件都必须留下。
 */
class AttachmentCleanerTest {

    private lateinit var directory: File
    private val now = 1_700_000_000_000L

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("attachment-cleaner-").toFile()
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun `a file that nothing references can go`() {
        val file = File(directory, "hash.jpg").apply { writeText("x") }

        assertTrue(isUnreferencedUploadFile(file, emptySet(), emptyList(), now))
    }

    @Test
    fun `a referenced file stays`() {
        val file = File(directory, "hash.jpg").apply { writeText("x") }

        assertFalse(isUnreferencedUploadFile(file, setOf(file.canonicalPath), emptyList(), now))
    }

    @Test
    fun `a pending file that is still being written stays`() {
        val file = File(directory, "${ContentAddressedFiles.PENDING_PREFIX}x").apply { writeText("x") }
        file.setLastModified(now - 60_000L)

        assertFalse(isUnreferencedUploadFile(file, emptySet(), emptyList(), now))
    }

    @Test
    fun `a pending file left by a crash can go`() {
        val file = File(directory, "${ContentAddressedFiles.PENDING_PREFIX}x").apply { writeText("x") }
        file.setLastModified(now - 2 * 60 * 60 * 1000L)

        assertTrue(isUnreferencedUploadFile(file, emptySet(), emptyList(), now))
    }

    @Test
    fun `a file mentioned by an unparsable url stays`() {
        val file = File(directory, "hash.jpg").apply { writeText("x") }

        assertFalse(isUnreferencedUploadFile(file, emptySet(), listOf("file:%zzhash.jpg"), now))
    }

    @Test
    fun `references from several sources are unified into canonical paths`() {
        val file = File(directory, "hash.jpg").apply { writeText("x") }
        val (referenced, unresolved) = collectReferencedPaths(
            listOf(
                file.toURI().toString(),
                file.toURI().toString(),
                "file:///%zz",
            ),
        )

        assertEquals(setOf(file.canonicalPath), referenced)
        assertEquals(listOf("file:///%zz"), unresolved)
    }

    @Test
    fun `an empty reference list makes every file unreferenced`() {
        val file = File(directory, "hash.jpg").apply { writeText("x") }
        val (referenced, unresolved) = collectReferencedPaths(emptyList())

        assertTrue(referenced.isEmpty())
        assertTrue(unresolved.isEmpty())
        assertTrue(isUnreferencedUploadFile(file, referenced, unresolved, now))
    }
}
