package me.rerere.rikkahub.data.files

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import kotlin.uuid.Uuid

/**
 * 内容寻址的文件写入：文件名就是内容的指纹，同样的内容落在同一个文件上。
 *
 * 先写到同目录的临时文件，边写边算 SHA-256，写完落盘再按指纹改名。指纹要写完才知道，所以每次
 * 都会写一份临时文件；目标已经存在时直接复用，不会多出文件。两个写者同时写同一份内容时，后到
 * 的看到目标已存在也会复用。
 */
internal object ContentAddressedFiles {

    /** 写入过程中的临时文件前缀；附件清理看到它时按修改时间给一段宽限 */
    const val PENDING_PREFIX = ".pending-"

    fun write(
        directory: File,
        displayName: String?,
        mimeType: String?,
        content: (OutputStream) -> Unit,
    ): File {
        // 并发写时 mkdirs 可能返回 false（目录已经被另一个写者建好），看目录在不在而不是看返回值
        directory.mkdirs()
        check(directory.isDirectory) { "Cannot create the directory: $directory" }
        val pending = File(directory, "$PENDING_PREFIX${Uuid.random()}")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val output = FileOutputStream(pending)
            try {
                val digesting = DigestOutputStream(output, digest)
                content(digesting)
                digesting.flush()
                // 和草稿一样先落盘再改名：断电后不会留下短一截的文件，被后面的同内容写入复用
                output.fd.sync()
            } finally {
                output.close()
            }
            val target = File(directory, FileUtils.buildContentFileName(digest.digest().toHex(), displayName, mimeType))
            if (target.isFile) return target
            // 改名成功，或者目标已经被另一个写者放好（Windows 上 renameTo 不覆盖已存在的目标）
            if (pending.renameTo(target) || target.isFile) return target
            error("Cannot move the pending file into place: $target")
        } finally {
            pending.delete()
        }
    }

    private fun ByteArray.toHex(): String {
        val digits = "0123456789abcdef"
        val chars = CharArray(size * 2)
        forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xFF
            chars[index * 2] = digits[value ushr 4]
            chars[index * 2 + 1] = digits[value and 0x0F]
        }
        return String(chars)
    }
}
