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

        assertTrue(shouldDeleteUploadFile(file, emptySet(), emptyList(), now))
    }

    @Test
    fun `a referenced file stays`() {
        val file = File(directory, "hash.jpg").apply { writeText("x") }

        assertFalse(shouldDeleteUploadFile(file, setOf(file.canonicalPath), emptyList(), now))
    }

    @Test
    fun `a pending file that is still being written stays`() {
        val file = File(directory, "${ContentAddressedFiles.PENDING_PREFIX}x").apply { writeText("x") }
        file.setLastModified(now - 60_000L)

        assertFalse(shouldDeleteUploadFile(file, emptySet(), emptyList(), now))
    }

    @Test
    fun `a pending file left by a crash can go`() {
        val file = File(directory, "${ContentAddressedFiles.PENDING_PREFIX}x").apply { writeText("x") }
        file.setLastModified(now - 2 * 60 * 60 * 1000L)

        assertTrue(shouldDeleteUploadFile(file, emptySet(), emptyList(), now))
    }

    @Test
    fun `a file mentioned by an unparsable url stays`() {
        val file = File(directory, "hash.jpg").apply { writeText("x") }

        assertFalse(shouldDeleteUploadFile(file, emptySet(), listOf("file:%zzhash.jpg"), now))
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
    fun `stored relative upload refs resolve against the files dir`() {
        val (referenced, unresolved) = collectReferencedPaths(
            listOf("file:upload/hash.jpg"),
            filesDir = directory,
        )

        assertEquals(setOf(File(directory, "upload/hash.jpg").canonicalPath), referenced)
        assertTrue(unresolved.isEmpty())
    }

    @Test
    fun `an empty reference list makes every file unreferenced`() {
        val file = File(directory, "hash.jpg").apply { writeText("x") }
        val (referenced, unresolved) = collectReferencedPaths(emptyList())

        assertTrue(referenced.isEmpty())
        assertTrue(unresolved.isEmpty())
        assertTrue(shouldDeleteUploadFile(file, referenced, unresolved, now))
    }

    @Test
    fun `an old unreferenced file can go when cleaning by age`() {
        val file = File(directory, "hash.jpg").apply { writeText("x") }

        assertTrue(
            shouldDeleteUploadFile(
                file = file,
                referencedPaths = emptySet(),
                unresolvedUrls = emptyList(),
                now = now,
                cutoffMillis = now - 1_000,
                createdAtMillis = now - 10_000,
            ),
        )
    }

    @Test
    fun `a recent file stays when cleaning by age`() {
        val file = File(directory, "hash.jpg").apply { writeText("x") }

        assertFalse(
            shouldDeleteUploadFile(
                file = file,
                referencedPaths = emptySet(),
                unresolvedUrls = emptyList(),
                now = now,
                cutoffMillis = now - 1_000,
                createdAtMillis = now,
            ),
        )
    }

    @Test
    fun `an old but referenced file stays when cleaning by age`() {
        val file = File(directory, "hash.jpg").apply { writeText("x") }

        assertFalse(
            shouldDeleteUploadFile(
                file = file,
                referencedPaths = setOf(file.canonicalPath),
                unresolvedUrls = emptyList(),
                now = now,
                cutoffMillis = now - 1_000,
                createdAtMillis = now - 10_000,
            ),
        )
    }

    @Test
    fun `a file created exactly at the cutoff stays`() {
        val file = File(directory, "hash.jpg").apply { writeText("x") }

        assertFalse(
            shouldDeleteUploadFile(
                file = file,
                referencedPaths = emptySet(),
                unresolvedUrls = emptyList(),
                now = now,
                cutoffMillis = now - 1_000,
                createdAtMillis = now - 1_000,
            ),
        )
    }

    @Test
    fun `an unregistered file falls back to its modification time`() {
        val file = File(directory, "hash.jpg").apply { writeText("x") }
        file.setLastModified(now - 10_000)

        assertTrue(
            shouldDeleteUploadFile(
                file = file,
                referencedPaths = emptySet(),
                unresolvedUrls = emptyList(),
                now = now,
                cutoffMillis = now - 1_000,
            ),
        )
        assertFalse(
            shouldDeleteUploadFile(
                file = File(directory, "recent.jpg").apply { writeText("x") }.apply { setLastModified(now) },
                referencedPaths = emptySet(),
                unresolvedUrls = emptyList(),
                now = now,
                cutoffMillis = now - 1_000,
            ),
        )
    }
}
