package me.rerere.rikkahub.data.files

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 内容寻址写入的判据。
 *
 * 同一份内容必须落到同一个文件上，不同内容不能互相覆盖；文件名要能看出内容指纹与后缀。
 */
class ContentAddressedFilesTest {

    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("content-addressed-").toFile().resolve("upload")
    }

    @After
    fun tearDown() {
        directory.parentFile?.deleteRecursively()
    }

    @Test
    fun `the same content lands on the same file`() {
        val first = ContentAddressedFiles.write(directory, "photo.png", null) {
            it.write(byteArrayOf(1, 2, 3))
        }
        val second = ContentAddressedFiles.write(directory, "copy.png", null) {
            it.write(byteArrayOf(1, 2, 3))
        }

        assertEquals(first, second)
        assertArrayEquals(byteArrayOf(1, 2, 3), first.readBytes())
        assertEquals(1, directory.listFiles().orEmpty().count { it.isFile })
    }

    @Test
    fun `different content does not overwrite the first file`() {
        val first = ContentAddressedFiles.write(directory, "photo.png", null) { it.write(byteArrayOf(1)) }
        val second = ContentAddressedFiles.write(directory, "photo.png", null) { it.write(byteArrayOf(2)) }

        assertNotEquals(first, second)
        assertArrayEquals(byteArrayOf(1), first.readBytes())
        assertArrayEquals(byteArrayOf(2), second.readBytes())
    }

    @Test
    fun `the file name carries the hash and the extension`() {
        val file = ContentAddressedFiles.write(directory, "photo.JPG", null) { it.write(byteArrayOf(1)) }

        assertEquals(64, file.name.substringBeforeLast('.').length)
        assertEquals("jpg", file.name.substringAfterLast('.'))
    }

    @Test
    fun `no pending file is left behind`() {
        ContentAddressedFiles.write(directory, "photo.png", null) { it.write(byteArrayOf(1)) }

        assertEquals(1, directory.listFiles().orEmpty().count { it.isFile })
        assertTrue(directory.listFiles().orEmpty().none { it.name.startsWith(ContentAddressedFiles.PENDING_PREFIX) })
    }
}
