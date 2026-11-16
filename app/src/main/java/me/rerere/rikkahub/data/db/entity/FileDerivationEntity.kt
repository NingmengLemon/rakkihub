package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 文件级派生文本：OCR 等结果挂在 [ManagedFileEntity] 上，而不是某一条消息的 metadata。
 *
 * 同一份上传内容只有一个 managed file 行，因此跨会话复用时也能直接命中；文件删除时级联清掉
 * 派生文本。旧消息里的 `OcrMetadata` 仍然作为兼容回退读取，新结果只写这张表。
 */
@Entity(
    tableName = "file_derivations",
    foreignKeys = [
        ForeignKey(
            entity = ManagedFileEntity::class,
            parentColumns = ["id"],
            childColumns = ["file_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["file_id", "kind", "producer"], unique = true),
    ],
)
data class FileDerivationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo("file_id")
    val fileId: Long,
    // 目前只有 "ocr"；保留字符串是为了以后接摘要、字幕等派生文本
    @ColumnInfo("kind")
    val kind: String,
    // 原始文本，不存展示用的标签；格式化在读取时做
    @ColumnInfo("text")
    val text: String,
    // 生成方式标识；OCR 为 ocr:v1:<prompt+model 指纹>，随 OCR 设置变化；旧 ocr:v1 行保留但不读
    @ColumnInfo("producer")
    val producer: String,
    // 手改入口写入；为 true 时自动 OCR 写入不覆盖
    @ColumnInfo("edited")
    val edited: Boolean = false,
    @ColumnInfo("created_at")
    val createdAt: Long,
    @ColumnInfo("updated_at")
    val updatedAt: Long,
)
