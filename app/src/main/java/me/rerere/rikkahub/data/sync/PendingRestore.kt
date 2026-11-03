package me.rerere.rikkahub.data.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.UUID

/**
 * Staged restores live outside cache and are installed before any database/settings consumers start.
 * The journal and atomic moves allow an interrupted installation to resume on the next launch.
 */
internal class PendingRestore(
    private val root: File,
    private val databaseFile: File,
    private val filesDir: File,
) {
    private val pending get() = File(root, "pending")

    fun createStagingDirectory(): File {
        check(!pending.exists()) { "A backup restore is already pending. Restart the app first." }
        check(root.isDirectory || root.mkdirs()) { "Cannot create backup staging directory" }
        return Files.createTempDirectory(root.toPath(), "preparing-").toFile()
    }

    fun publish(staging: File) {
        check(!pending.exists()) { "A backup restore is already pending. Restart the app first." }
        move(staging, pending)
    }

    suspend fun apply(restoreSettings: suspend (String) -> Unit): Boolean {
        // Completed restores must never be replayed, even if cleanup was interrupted.
        root.listFiles()?.filter {
            it.name.startsWith("completed-") || it.name.startsWith("preparing-")
        }?.forEach { it.deleteRecursively() }
        if (!pending.isDirectory) return false

        val journal = File(pending, "journal.json")
        val entries = if (journal.exists()) {
            // An existing journal may describe an interrupted installation; keep it if reading fails.
            Json.decodeFromString<List<RestoreEntry>>(journal.readText())
        } else {
            try {
                buildEntries().also { writeDurably(journal, Json.encodeToString(it)) }
            } catch (e: Exception) {
                // No live files have been changed yet, so this restore can be safely rejected.
                move(pending, File(root, "failed-${UUID.randomUUID()}"))
                throw RestoreFailedException(e)
            }
        }
        try {
            entries.forEach { entry ->
                val target = targetFile(entry.path)
                val original = File(pending, "originals/${entry.path}")
                val source = File(pending, "payload/${entry.path}")
                if (entry.install && !source.exists()) {
                    // A previous process already moved this source into place.
                    check(target.isFile) { "Interrupted restore is missing ${entry.path}" }
                } else {
                    if (entry.hadOriginal && !original.exists()) move(target, original)
                    if (entry.install) move(source, target)
                }
            }
            val settings = File(pending, BackupManager.SETTINGS_ENTRY)
            if (settings.isFile) restoreSettings(settings.readText())
        } catch (e: Exception) {
            try {
                rollback(entries)
            } catch (rollbackError: Exception) {
                e.addSuppressed(rollbackError)
                // Do not start the app with a partially restored database. Keep the journal for retry.
                throw e
            }
            move(pending, File(root, "failed-${UUID.randomUUID()}"))
            throw RestoreFailedException(e)
        }

        // Commit by removing the pending name atomically before any live connection can be opened.
        // If this move fails, startup aborts and the next launch retries the same installation.
        val completed = File(root, "completed-${UUID.randomUUID()}")
        move(pending, completed)
        completed.deleteRecursively()
        return true
    }

    private fun buildEntries(): List<RestoreEntry> {
        val payload = File(pending, "payload")
        val paths = payload.walkTopDown().filter { it.isFile }.map {
            it.relativeTo(payload).invariantSeparatorsPath
        }.toList().sorted()
        val entries = paths.map { path ->
            RestoreEntry(path, install = true, hadOriginal = targetFile(path).exists())
        }.toMutableList()
        // 附件替换只在数据库也一起换掉时做。只换附件的话，现有库还引用着 live 上的文件，
        // 删掉它们等于制造悬空引用。导入现在必须带库（C40），这条判断只剩更早版本留下的
        // pending 目录会走到。
        if (paths.contains("database/${databaseFile.name}")) {
            entries.addAll(buildRemovalEntries(paths))
            // The new database is standalone. Keep the old DB's sidecars with the old DB only.
            for (suffix in listOf("-wal", "-shm", "-journal")) {
                val path = "database/${databaseFile.name}$suffix"
                entries.add(0, RestoreEntry(path, install = false, hadOriginal = targetFile(path).exists()))
            }
        }
        return entries
    }

    /**
     * 归档里的附件目录按替换语义处理：live 目录里多出来、payload 里没有的文件要删掉。
     *
     * 只针对归档里实际带了的目录。只带数据库的备份会得到空列表，什么都不删；[buildEntries]
     * 也只在 payload 同时带数据库时才调用这里。
     * 删除用 `install = false` 的条目表达，journal 与 rollback 原样复用：apply 会把目标移进
     * `originals/`，rollback 再移回来。做成文件级条目而不是目录级 rename，是因为 journal 与
     * rollback 都按路径记录单个文件，目录条目得为它们另加一套状态。
     */
    private fun buildRemovalEntries(payloadPaths: List<String>): List<RestoreEntry> {
        val presentFolders = payloadPaths
            .filter { it.startsWith("files/") }
            .map { it.removePrefix("files/").substringBefore('/') }
            .filter { it.isNotEmpty() && it in BackupManager.ATTACHMENT_FOLDERS }
            .toSet()
        val removals = ArrayList<RestoreEntry>()
        for (folder in presentFolders) {
            val live = File(filesDir, folder)
            if (!live.isDirectory) continue
            val prefix = "files/$folder/"
            val kept = payloadPaths.filter { it.startsWith(prefix) }.toSet()
            live.walkTopDown().filter { it.isFile }.forEach { file ->
                val path = prefix + file.relativeTo(live).invariantSeparatorsPath
                if (path !in kept) {
                    // 先过一遍 targetFile：不合格的 live 路径要在 journal 落地之前失败。
                    // 否则 apply 会在搬这条路径时抛 IllegalArgumentException，rollback 对同一条
                    // 路径再抛一次，最后留下一个每次启动都会崩的 journal。
                    targetFile(path)
                    removals.add(RestoreEntry(path, install = false, hadOriginal = true))
                }
            }
        }
        return removals
    }

    private fun rollback(entries: List<RestoreEntry>) {
        entries.asReversed().forEach { entry ->
            val target = targetFile(entry.path)
            val original = File(pending, "originals/${entry.path}")
            val source = File(pending, "payload/${entry.path}")
            if (entry.install && !source.exists() && target.exists()) move(target, source)
            if (original.exists()) move(original, target)
        }
    }

    private fun targetFile(path: String): File {
        return when {
            path.startsWith("database/") -> {
                val name = path.removePrefix("database/")
                require(name in listOf(
                    databaseFile.name,
                    databaseFile.name + "-wal",
                    databaseFile.name + "-shm",
                    databaseFile.name + "-journal"
                )) {
                    "Invalid restore database path"
                }
                File(databaseFile.parentFile, name)
            }
            path.startsWith("files/") -> resolveInside(filesDir, path.removePrefix("files/"))
            else -> error("Invalid restore path: $path")
        }
    }

    @Serializable
    private data class RestoreEntry(val path: String, val install: Boolean, val hadOriginal: Boolean)

    companion object {
        fun resolveInside(root: File, relativePath: String): File {
            require(relativePath.isNotBlank() && !relativePath.startsWith('/') &&
                '\\' !in relativePath && relativePath.split('/').none { it == ".." || it == "." }) {
                "Invalid backup file path: $relativePath"
            }
            val target = File(root, relativePath).canonicalFile
            require(target.path.startsWith(root.canonicalPath + File.separator)) {
                "Backup file is outside its target directory"
            }
            return target
        }

        fun writeDurably(file: File, text: String) {
            val temporary = File(file.parentFile, file.name + ".tmp")
            FileOutputStream(temporary).use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            move(temporary, file)
        }

        private fun move(source: File, target: File) {
            val parent = requireNotNull(target.parentFile)
            check(parent.isDirectory || parent.mkdirs()) { "Cannot create restore directory" }
            Files.move(source.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        }
    }
}

/** Only thrown when original files are intact and the failed import has been isolated. */
internal class RestoreFailedException(
    cause: Exception
) : Exception("Backup restore failed; original files restored", cause)
