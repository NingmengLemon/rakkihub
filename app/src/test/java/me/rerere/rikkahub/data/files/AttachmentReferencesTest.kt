package me.rerere.rikkahub.data.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** 附件地址的摘取与归一：清理时靠它判断文件还有没有引用。 */
class AttachmentReferencesTest {

    @Test
    fun `local file urls are extracted from json`() {
        val json = """{"url":"file:///data/user/0/pkg/files/upload/a.png","other":"https://example.com/b.png"}"""

        assertEquals(setOf("file:///data/user/0/pkg/files/upload/a.png"), extractFileUrls(json))
    }

    @Test
    fun `a text with several urls yields all of them`() {
        val text = "see file:///a.png and file:///b.txt"

        assertEquals(setOf("file:///a.png", "file:///b.txt"), extractFileUrls(text))
    }

    @Test
    fun `text without urls yields nothing`() {
        assertTrue(extractFileUrls("no attachments here, just words").isEmpty())
    }

    @Test
    fun `a file url maps to its canonical path`() {
        val directory = Files.createTempDirectory("attachment-references-").toFile()
        try {
            val file = File(directory, "a.png").apply { writeText("x") }

            assertEquals(file.canonicalPath, fileUrlToCanonicalPath(file.toURI().toString()))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `an unparsable url yields no path`() {
        assertNull(fileUrlToCanonicalPath("file:///%zz"))
    }
}
