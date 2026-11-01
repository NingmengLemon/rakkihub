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
            AttachmentUrlStorage.toStoredJson(json, filesDirUri),
        )
    }

    @Test
    fun `relative urls resolve back to the current install`() {
        val json = """{"type":"image","url":"file:upload/abc.png"}"""

        assertEquals(
            """{"type":"image","url":"$filesDirUri/upload/abc.png"}""",
            AttachmentUrlStorage.toMemoryJson(json, filesDirUri),
        )
    }

    @Test
    fun `the legacy data data form is stored relative too`() {
        val json = """{"url":"file:///data/data/top.lemoneko.rakkihub.debug/files/upload/abc.png"}"""

        assertEquals(
            """{"url":"file:upload/abc.png"}""",
            AttachmentUrlStorage.toStoredJson(json, filesDirUri),
        )
    }

    @Test
    fun `absolute urls outside the files dir are untouched`() {
        val json = """{"url":"file:///sdcard/Download/x.png"}"""

        assertEquals(json, AttachmentUrlStorage.toStoredJson(json, filesDirUri))
        assertEquals(json, AttachmentUrlStorage.toMemoryJson(json, filesDirUri))
    }

    @Test
    fun `only upload urls are converted`() {
        val json = """{"url":"$filesDirUri/workspaces/abc"}"""

        assertEquals(json, AttachmentUrlStorage.toStoredJson(json, filesDirUri))
        assertEquals(json, AttachmentUrlStorage.toMemoryJson(json, filesDirUri))
    }

    @Test
    fun `text values starting with the stored prefix are untouched`() {
        val json = """{"text":"file:upload/a.png"}"""

        assertEquals(json, AttachmentUrlStorage.toStoredJson(json, filesDirUri))
        assertEquals(json, AttachmentUrlStorage.toMemoryJson(json, filesDirUri))
    }

    @Test
    fun `escaped quotes in text are untouched`() {
        val json = """{"text":"see \"file:upload/a.png\" here"}"""

        assertEquals(json, AttachmentUrlStorage.toMemoryJson(json, filesDirUri))
        assertEquals(json, AttachmentUrlStorage.toStoredJson(json, filesDirUri))
    }

    @Test
    fun `escaped quotes around an absolute url in text are untouched`() {
        val json = """{"text":"see \"$filesDirUri/upload/a.png\" here"}"""

        assertEquals(json, AttachmentUrlStorage.toStoredJson(json, filesDirUri))
    }

    @Test
    fun `several urls are converted together`() {
        val json = """{"parts":[{"url":"$filesDirUri/upload/a.png"},{"url":"$filesDirUri/upload/b.png"}]}"""

        assertEquals(
            """{"parts":[{"url":"file:upload/a.png"},{"url":"file:upload/b.png"}]}""",
            AttachmentUrlStorage.toStoredJson(json, filesDirUri),
        )
    }

    @Test
    fun `round trip keeps the url and is idempotent`() {
        val json = """{"url":"$filesDirUri/upload/abc.png"}"""
        val stored = AttachmentUrlStorage.toStoredJson(json, filesDirUri)

        assertEquals(stored, AttachmentUrlStorage.toStoredJson(stored, filesDirUri))
        assertEquals(json, AttachmentUrlStorage.toMemoryJson(stored, filesDirUri))
        assertEquals(json, AttachmentUrlStorage.toMemoryJson(json, filesDirUri))
    }
}
