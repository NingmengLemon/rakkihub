package me.rerere.rikkahub.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

class ImportedUploadFilesTest {

    @Test
    fun contentNameUsesTheContentHashAndKeepsTheExtension() {
        val hash = "a".repeat(64)
        assertEquals("$hash.png", ImportedUploadFiles.contentName(hash, "capture.PNG"))
        assertEquals("$hash.jpeg", ImportedUploadFiles.contentName(hash, "photo.name.jpeg"))
        assertEquals("$hash.bin", ImportedUploadFiles.contentName(hash, "no-extension"))
        assertEquals("$hash.bin", ImportedUploadFiles.contentName(hash, "trailing-dot."))
    }

    @Test
    fun sameContentLandsOnOneFile() {
        val directory = Files.createTempDirectory("imported-upload-").toFile()
        try {
            val first = ImportedUploadFiles.write(
                directory,
                "aaaa-1.png",
                ByteArrayInputStream("image".toByteArray()),
            )
            val second = ImportedUploadFiles.write(
                directory,
                "bbbb-2.png",
                ByteArrayInputStream("image".toByteArray()),
            )

            assertEquals("同内容应当落到同一个名字", first, second)
            assertEquals("名字要按内容算", "image".toByteArray().sha256() + ".png", first)
            assertEquals(listOf(first), directory.listFiles().orEmpty().map { it.name })
            assertEquals("image", File(directory, first).readText())
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }

    @Test
    fun differentContentLandsOnDifferentFiles() {
        val directory = Files.createTempDirectory("imported-upload-").toFile()
        try {
            val first = ImportedUploadFiles.write(
                directory,
                "aaaa-1.png",
                ByteArrayInputStream("first".toByteArray()),
            )
            val second = ImportedUploadFiles.write(
                directory,
                "bbbb-2.png",
                ByteArrayInputStream("second".toByteArray()),
            )

            assertNotEquals(first, second)
            assertEquals(2, directory.listFiles().orEmpty().size)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rewriteOnlyTouchesNamesInTheMapping() {
        val renames = mapOf("aaaa-1.png" to "hash1.png", "bbbb-2.png" to "hash2.png")
        val text = """[{"url":"file:upload/aaaa-1.png"},""" +
            """{"url":"file:///data/user/0/me.rerere.rikkahub/files/upload/bbbb-2.png"},""" +
            """{"url":"file:upload/cccc-3.jpg"},""" +
            """{"text":"see upload/aaaa-1.png and upload/zzzz.png"}]"""

        val rewritten = ImportedUploadFiles.rewrite(text, renames)

        assertTrue("相对形态要改，实际是 $rewritten", rewritten.contains("\"file:upload/hash1.png\""))
        assertTrue("绝对形态只改名字，实际是 $rewritten", rewritten.contains("/files/upload/hash2.png"))
        assertTrue("没有映射的名字保持原样，实际是 $rewritten", rewritten.contains("file:upload/cccc-3.jpg"))
        assertTrue(
            "正文里的裸路径不参与改写，实际是 $rewritten",
            rewritten.contains("see upload/aaaa-1.png and upload/zzzz.png"),
        )
        assertFalse("旧名字不应残留在引用里，实际是 $rewritten", rewritten.contains("file:upload/aaaa-1.png"))
    }

    @Test
    fun emptyMappingLeavesTheTextAlone() {
        val text = """[{"url":"file:upload/aaaa-1.png"}]"""
        assertEquals(text, ImportedUploadFiles.rewrite(text, emptyMap()))
    }
}
