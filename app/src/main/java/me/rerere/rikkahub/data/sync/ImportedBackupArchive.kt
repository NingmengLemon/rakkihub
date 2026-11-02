package me.rerere.rikkahub.data.sync

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 导入成功的原始备份留底。
 *
 * 它是官方自己产出的自包含文件，随时能被官方吃回去，也是导入写坏数据时唯一能回退的东西。
 * 只在恢复成功之后调用：失败的恢复没有改动任何数据，不需要为它留底，留着只会堆积。
 */
internal object ImportedBackupArchive {
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")

    // 两次导入可能重叠：stageRestore 只串行到发布 pending 为止，归档发生在它返回之后。
    // 写入选名字与改名的这一段要串行，否则另一次导入会插在存在性判断与改名之间。
    private val writeLock = Any()

    /**
     * 复制 [source] 到 [directory]，返回落盘位置；失败时返回带原因的 [Result]，由调用方记日志。
     *
     * 先写临时文件再原子改名，进程中途被杀不会留下截断的归档；文件名带毫秒，同一秒内多次
     * 导入不会互相覆盖。同毫秒撞名时追加序号（Linux 上连续两次调用会撞上），不覆盖已有文件：
     * 宁可多出一份，也不要悄悄替换掉上一份。写入串行化，同一进程里的并发导入不会抢同一个
     * 文件名；进程外的并发不在保证范围内，这个目录只有本应用会写。失败时会尝试删掉临时
     * 文件，不在目录里留残渣（删除本身可能失败，是 best-effort）。
     *
     * [now] 只给用例固定时间用，生产调用取默认值。
     */
    fun store(
        source: File,
        directory: File,
        now: LocalDateTime = LocalDateTime.now(),
    ): Result<File> = runCatching {
        synchronized(writeLock) {
            check(directory.isDirectory || directory.mkdirs()) { "Cannot create archive directory" }
            val stampText = stamp.format(now)
            var target = File(directory, "imported_$stampText.zip")
            var suffix = 1
            while (target.exists()) {
                suffix += 1
                target = File(directory, "imported_${stampText}_$suffix.zip")
            }
            val temporary = File(directory, target.name + ".tmp")
            try {
                Files.copy(source.toPath(), temporary.toPath(), REPLACE_EXISTING)
                Files.move(temporary.toPath(), target.toPath(), ATOMIC_MOVE)
            } catch (e: Exception) {
                temporary.delete()
                throw e
            }
            target
        }
    }
}
