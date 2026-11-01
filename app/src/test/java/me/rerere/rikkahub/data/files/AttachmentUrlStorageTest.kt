package me.rerere.rikkahub.data.files

import org.junit.Assert.assertEquals
import org.junit.Test

/** 消息库里附件地址的相对形态与还原。 */
class AttachmentUrlStorageTest {

    private val filesDirUri = "file:///data/user/0/top.lemoneko.rakkihub.debug/files"

    @Test
    fun `upload urls are stored relative`() {
        val json = """{"type":"image","url":"$filesDirUri/upload/abc.png"}"""

        assertEquals(
            """{"type":"image","url":"file:upload/abc.png"}""",
            AttachmentUrlStorage.toStored(json, filesDirUri),
        )
    }

    @Test
    fun `relative urls resolve back to the current install`() {
        val json = """{"type":"image","url":"file:upload/abc.png"}"""

        assertEquals(
            """{"type":"image","url":"$filesDirUri/upload/abc.png"}""",
            AttachmentUrlStorage.toMemory(json, filesDirUri),
        )
    }

    @Test
    fun `absolute urls outside the files dir are untouched`() {
        val json = """{"url":"file:///sdcard/Download/x.png"}"""

        assertEquals(json, AttachmentUrlStorage.toStored(json, filesDirUri))
        assertEquals(json, AttachmentUrlStorage.toMemory(json, filesDirUri))
    }

    @Test
    fun `only the upload folder is converted`() {
        val json = """{"url":"$filesDirUri/workspaces/abc"}"""

        assertEquals(json, AttachmentUrlStorage.toStored(json, filesDirUri))
        assertEquals(json, AttachmentUrlStorage.toMemory(json, filesDirUri))
    }

    @Test
    fun `round trip keeps the url`() {
        val json = """{"url":"$filesDirUri/upload/abc.png"}"""

        assertEquals(json, AttachmentUrlStorage.toMemory(AttachmentUrlStorage.toStored(json, filesDirUri), filesDirUri))
    }
}
