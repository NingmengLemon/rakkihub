package me.rerere.rikkahub.data.repository

import me.rerere.rikkahub.data.db.dao.FileDerivationDAO
import me.rerere.rikkahub.data.db.entity.FileDerivationEntity
import java.security.MessageDigest

class FileDerivationRepository(
    private val dao: FileDerivationDAO,
) {
    suspend fun get(fileId: Long, producer: String = OCR_PRODUCER): FileDerivationEntity? =
        dao.get(fileId, OCR_KIND, producer)

    suspend fun listForFiles(
        fileIds: Collection<Long>,
        producer: String = OCR_PRODUCER,
    ): Map<Long, FileDerivationEntity> {
        if (fileIds.isEmpty()) return emptyMap()
        return dao.listForFiles(fileIds.toList(), OCR_KIND, producer)
            .associateBy { it.fileId }
    }

    /** 已有 edited 的行不覆盖；相同文本也不重复写。返回是否写入了新内容。 */
    suspend fun putOcr(
        fileId: Long,
        text: String,
        producer: String = OCR_PRODUCER,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        if (text.isBlank()) return false
        val existing = dao.get(fileId, OCR_KIND, producer)
        if (existing != null) {
            if (existing.edited || existing.text == text) return false
            dao.update(existing.copy(text = text, updatedAt = now))
            return true
        }
        val inserted = dao.insert(
            FileDerivationEntity(
                fileId = fileId,
                kind = OCR_KIND,
                text = text,
                producer = producer,
                createdAt = now,
                updatedAt = now,
            )
        )
        if (inserted != -1L) return true
        // 并发写入时 IGNORE 会返回 -1；重新读一次，按正常更新规则处理
        val raced = dao.get(fileId, OCR_KIND, producer) ?: return false
        if (raced.edited || raced.text == text) return false
        dao.update(raced.copy(text = text, updatedAt = now))
        return true
    }

    companion object {
        const val OCR_KIND = "ocr"
        const val OCR_PRODUCER = "ocr:v1"

        /** OCR 提示词或模型变化时让旧派生失效；同配置下稳定。 */
        fun ocrProducer(prompt: String, modelId: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("$prompt\u0000$modelId".toByteArray(Charsets.UTF_8))
            val suffix = digest.take(8).joinToString("") { "%02x".format(it.toInt() and 0xff) }
            return "$OCR_PRODUCER:$suffix"
        }
    }
}
