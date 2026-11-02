package me.rerere.rikkahub.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDateTime

class ImportedBackupArchiveTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun storesArchiveAndKeepsContents() {
        val source = File(temporary.root, "backup.zip").apply { writeText("official backup") }
        val directory = File(temporary.root, "imported-backups")
        val archived = ImportedBackupArchive.store(source, directory).getOrThrow()
        assertTrue(archived.isFile)
        assertEquals("official backup", archived.readText())
    }

    @Test fun keepsEveryArchiveWhenTwoImportsShareTheSameMillisecond() {
        val source = File(temporary.root, "backup.zip").apply { writeText("official backup") }
        val directory = File(temporary.root, "imported-backups")
        val now = LocalDateTime.of(2026, 1, 2, 3, 4, 5, 678_000_000)
        val first = ImportedBackupArchive.store(source, directory, now).getOrThrow()
        val second = ImportedBackupArchive.store(source, directory, now).getOrThrow()
        assertEquals("imported_20260102_030405_678.zip", first.name)
        assertEquals("imported_20260102_030405_678_2.zip", second.name)
        assertEquals("official backup", second.readText())
        assertEquals(2, directory.listFiles()!!.count { it.isFile && it.name.endsWith(".zip") })
    }

    @Test fun numbersPastArchivesThatAlreadyUseTheSameTimestamp() {
        val source = File(temporary.root, "backup.zip").apply { writeText("official backup") }
        val directory = File(temporary.root, "imported-backups").apply { mkdirs() }
        val now = LocalDateTime.of(2026, 1, 2, 3, 4, 5, 678_000_000)
        File(directory, "imported_20260102_030405_678.zip").writeText("older")
        File(directory, "imported_20260102_030405_678_2.zip").writeText("older")
        val archived = ImportedBackupArchive.store(source, directory, now).getOrThrow()
        assertEquals("imported_20260102_030405_678_3.zip", archived.name)
        assertEquals("older", File(directory, "imported_20260102_030405_678.zip").readText())
        assertEquals("older", File(directory, "imported_20260102_030405_678_2.zip").readText())
    }

    @Test fun leavesNoTemporaryFileBehind() {
        val source = File(temporary.root, "backup.zip").apply { writeText("official backup") }
        val directory = File(temporary.root, "imported-backups")
        ImportedBackupArchive.store(source, directory).getOrThrow()
        assertTrue(directory.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
    }

    @Test fun reportsFailureWithCauseWhenTheSourceIsMissing() {
        val directory = File(temporary.root, "imported-backups")
        val result = ImportedBackupArchive.store(File(temporary.root, "absent.zip"), directory)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is java.nio.file.NoSuchFileException)
    }
}
