package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.db.dao.FileDerivationDAO
import me.rerere.rikkahub.data.db.dao.ManagedFileDAO
import me.rerere.rikkahub.data.db.entity.FileDerivationEntity
import me.rerere.rikkahub.data.db.entity.ManagedFileEntity
import me.rerere.rikkahub.data.files.FileDerivationResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileDerivationRepositoryTest {
    private fun repository(): Pair<FileDerivationRepository, FakeFileDerivationDAO> {
        val dao = FakeFileDerivationDAO()
        return FileDerivationRepository(dao) to dao
    }

    @Test
    fun `ocr puts and reads a derivation`() = runBlocking {
        val (repository, _) = repository()
        assertTrue(repository.putOcr(7, "hello", now = 100))
        val row = repository.get(7)
        assertEquals("hello", row?.text)
        assertEquals(FileDerivationRepository.OCR_PRODUCER, row?.producer)
        assertFalse(repository.putOcr(7, "hello", now = 200))
        assertTrue(repository.putOcr(7, "changed", now = 300))
        assertEquals("changed", repository.get(7)?.text)
    }

    @Test
    fun `ocr producer changes with prompt or model and isolates rows`() = runBlocking {
        val (repository, _) = repository()
        val first = FileDerivationRepository.ocrProducer("prompt-a", "model-a")
        val second = FileDerivationRepository.ocrProducer("prompt-b", "model-a")
        val third = FileDerivationRepository.ocrProducer("prompt-a", "model-b")

        assertEquals(first, FileDerivationRepository.ocrProducer("prompt-a", "model-a"))
        assertTrue(first.substringAfterLast(':').matches(Regex("[0-9a-f]{16}")))
        assertNotEquals(first, second)
        assertNotEquals(first, third)
        assertTrue(repository.putOcr(7, "old", first, now = 100))
        assertTrue(repository.putOcr(7, "new", second, now = 200))

        assertEquals("old", repository.get(7, first)?.text)
        assertEquals("new", repository.get(7, second)?.text)
        assertEquals(setOf(7L), repository.listForFiles(listOf(7, 8), second).keys)
    }

    @Test
    fun `manual OCR text overrides generated derivations across producers`() = runBlocking {
        val (repository, _) = repository()
        val producer = FileDerivationRepository.ocrProducer("prompt-a", "model-a")
        val other = FileDerivationRepository.ocrProducer("prompt-b", "model-b")
        assertTrue(repository.putOcr(7, "generated", producer, now = 100))
        assertTrue(repository.editOcr(7, "manual", now = 200))

        assertEquals("manual", repository.getAnyOcr(7)?.text)
        assertEquals("manual", repository.listForFiles(listOf(7), producer).getValue(7).text)
        assertEquals("manual", repository.listForFiles(listOf(7), other).getValue(7).text)
        // 手改行存在时，任何 producer 的自动识别都不能覆盖它
        assertFalse(repository.putOcr(7, "new generated", producer, now = 300))
        assertFalse(repository.putOcr(7, "auto-b", other, now = 350))
        assertEquals("manual", repository.listForFiles(listOf(7), other).getValue(7).text)
        // 相同文本保存视为成功，不产生第二条手改行
        assertTrue(repository.editOcr(7, "manual", now = 360))

        // 空文本删掉手改，自动派生重新可见
        assertTrue(repository.editOcr(7, " ", now = 400))
        assertEquals("generated", repository.listForFiles(listOf(7), producer).getValue(7).text)
        assertEquals("generated", repository.getAnyOcr(7)?.text)
    }

    @Test
    fun `edited derivation is never overwritten`() = runBlocking {
        val (repository, dao) = repository()
        dao.rows += FileDerivationEntity(
            id = 1,
            fileId = 7,
            kind = FileDerivationRepository.OCR_KIND,
            text = "manual",
            producer = FileDerivationRepository.OCR_PRODUCER,
            edited = true,
            createdAt = 1,
            updatedAt = 1,
        )

        assertFalse(repository.putOcr(7, "model"))
        assertEquals("manual", repository.get(7)?.text)
    }

    @Test
    fun `blank text is ignored and list is capped to requested ids`() = runBlocking {
        val (repository, _) = repository()
        assertFalse(repository.putOcr(7, " "))
        assertTrue(repository.putOcr(7, "seven"))
        assertTrue(repository.putOcr(8, "eight"))

        assertEquals(setOf(7L, 8L), repository.listForFiles(listOf(7, 8, 9)).keys)
    }

    @Test
    fun `resolver maps relative and absolute upload urls and rejects outside files`() = runBlocking {
        val root = Files.createTempDirectory("ocr-resolver").toFile()
        try {
            val file = File(root, "upload/H1.png")
            file.parentFile?.mkdirs()
            file.writeBytes(ByteArray(3))
            val entity = ManagedFileEntity(
                id = 7,
                folder = "upload",
                relativePath = "upload/H1.png",
                displayName = "H1.png",
                mimeType = "image/png",
                sizeBytes = 3,
                createdAt = 1,
                updatedAt = 1,
            )
            val files = FilesRepository(FakeManagedFileDAO(listOf(entity)))

            val absolute = file.toURI().toString()
            val outside = File(root.parentFile, "outside.png").toURI().toString()
            val resolved = FileDerivationResolver.resolveAll(
                listOf("file:upload/H1.png", absolute, outside, "file:upload/missing.png"),
                root,
                files,
            )

            assertEquals(mapOf("file:upload/H1.png" to 7L, absolute to 7L), resolved.mapValues { it.value.id })
            assertFalse(resolved.containsKey(outside))
            assertFalse(resolved.containsKey("file:upload/missing.png"))
        } finally {
            root.deleteRecursively()
        }
    }
}

private class FakeFileDerivationDAO : FileDerivationDAO {
    val rows = mutableListOf<FileDerivationEntity>()
    private var nextId = 1L

    override suspend fun get(fileId: Long, kind: String, producer: String): FileDerivationEntity? =
        rows.firstOrNull { it.fileId == fileId && it.kind == kind && it.producer == producer }

    override suspend fun listForFiles(
        fileIds: List<Long>,
        kind: String,
        producer: String,
    ): List<FileDerivationEntity> = rows.filter { it.fileId in fileIds && it.kind == kind && it.producer == producer }

    override suspend fun getAny(fileId: Long, kind: String): FileDerivationEntity? =
        rows.filter { it.fileId == fileId && it.kind == kind }
            .maxWithOrNull(compareBy<FileDerivationEntity> { it.edited }.thenBy { it.updatedAt })

    override suspend fun listAny(fileIds: List<Long>, kind: String): List<FileDerivationEntity> =
        rows.filter { it.fileId in fileIds && it.kind == kind }
            .sortedWith(compareByDescending<FileDerivationEntity> { it.edited }.thenByDescending { it.updatedAt })

    override suspend fun delete(fileId: Long, kind: String, producer: String): Int =
        rows.removeAll { it.fileId == fileId && it.kind == kind && it.producer == producer }.let { if (it) 1 else 0 }

    override suspend fun insert(derivation: FileDerivationEntity): Long {
        if (get(derivation.fileId, derivation.kind, derivation.producer) != null) return -1
        val id = if (derivation.id == 0L) nextId++ else derivation.id
        rows += derivation.copy(id = id)
        return id
    }

    override suspend fun update(derivation: FileDerivationEntity) {
        val index = rows.indexOfFirst { it.id == derivation.id }
        if (index >= 0) rows[index] = derivation
    }

    override suspend fun clearGenerated(): Int {
        val before = rows.size
        rows.removeAll { !it.edited }
        return before - rows.size
    }
}

private class FakeManagedFileDAO(
    private val rows: List<ManagedFileEntity>,
) : ManagedFileDAO {
    override suspend fun insert(file: ManagedFileEntity): Long = error("not used")
    override suspend fun update(file: ManagedFileEntity) = Unit
    override suspend fun getById(id: Long): ManagedFileEntity? = rows.firstOrNull { it.id == id }
    override suspend fun getByPath(relativePath: String): ManagedFileEntity? =
        rows.firstOrNull { it.relativePath == relativePath }

    override suspend fun getByPaths(relativePaths: List<String>): List<ManagedFileEntity> =
        rows.filter { it.relativePath in relativePaths }

    override fun listByFolder(folder: String): Flow<List<ManagedFileEntity>> =
        flowOf(rows.filter { it.folder == folder })

    override suspend fun deleteById(id: Long): Int = 0
    override suspend fun deleteByPath(relativePath: String): Int = 0
    override suspend fun deleteByFolder(folder: String): Int = 0
}
