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
        val ids = fileIds.toList()
        val generated = dao.listForFiles(ids, OCR_KIND, producer)
        // 手改行与 producer 无关：它在任何 OCR 配置下都覆盖自动识别结果
        val edited = dao.listAny(ids, OCR_KIND).filter { it.edited }
        return (generated + edited).associateBy { it.fileId }
    }

    /** 附件管理里的编辑入口用：优先拿手改行，否则拿最近的一条派生。 */
    suspend fun getAnyOcr(fileId: Long): FileDerivationEntity? = dao.getAny(fileId, OCR_KIND)

    /**
     * 写入手改文本；空文本删除手改行，让自动派生重新生效。
     * 手改行使用固定 producer，不随 OCR 提示词或模型变化。
     */
    suspend fun editOcr(fileId: Long, text: String, now: Long = System.currentTimeMillis()): Boolean {
        if (text.isBlank()) {
            dao.delete(fileId, OCR_KIND, OCR_MANUAL_PRODUCER)
            return true
        }
        val existing = dao.get(fileId, OCR_KIND, OCR_MANUAL_PRODUCER)
        if (existing != null) {
            if (existing.text == text && existing.edited) return true
            dao.update(existing.copy(text = text, edited = true, updatedAt = now))
            return true
        }
        return dao.insert(
            FileDerivationEntity(
                fileId = fileId,
                kind = OCR_KIND,
                text = text,
                producer = OCR_MANUAL_PRODUCER,
                edited = true,
                createdAt = now,
                updatedAt = now,
            )
        ) != -1L
    }

    /** 已有 edited 的行不覆盖；相同文本也不重复写。返回是否写入了新内容。 */
    suspend fun putOcr(
        fileId: Long,
        text: String,
        producer: String = OCR_PRODUCER,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        if (text.isBlank()) return false
        if (dao.getAny(fileId, OCR_KIND)?.edited == true) return false
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
        const val OCR_MANUAL_PRODUCER = "ocr:manual"

        /** OCR 提示词或模型变化时让旧派生失效；同配置下稳定。 */
        fun ocrProducer(prompt: String, modelId: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("$prompt\u0000$modelId".toByteArray(Charsets.UTF_8))
            val suffix = digest.take(8).joinToString("") { "%02x".format(it.toInt() and 0xff) }
            return "$OCR_PRODUCER:$suffix"
        }
    }
}
