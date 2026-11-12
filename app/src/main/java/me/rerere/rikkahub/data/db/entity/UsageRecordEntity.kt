package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一次模型请求的 token 用量流水，每次请求（多步对话的每一步、每次重试）各记一行。
 *
 * 和消息分开存：删掉会话或消息后流水还在，统计不受影响；标题、翻译这类不落到消息里的请求也记在这里。
 * 引用的模型、助手和会话都可能已经被删除，所以不加外键。
 *
 * [outcome] 区分这次请求是成功、失败、被取消还是旧数据无法判断；失败/取消尝试的 token 仍然
 * 计入成本，但统计页的缓存命中率只使用成功和未知行。
 */
@Entity(
    tableName = "usage_record",
    indices = [Index(value = ["created_at"])]
)
data class UsageRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo("created_at")
    val createdAt: Long,
    // UsageSource 的名称
    @ColumnInfo("source")
    val source: String,
    @ColumnInfo("provider_id")
    val providerId: String?,
    @ColumnInfo("model_id")
    val modelId: String?,
    // 模型的 API 名称快照，模型被删除后靠它分辨
    @ColumnInfo("model_name")
    val modelName: String?,
    @ColumnInfo("assistant_id")
    val assistantId: String?,
    @ColumnInfo("conversation_id")
    val conversationId: String?,
    @ColumnInfo("prompt_tokens")
    val promptTokens: Int,
    @ColumnInfo("completion_tokens")
    val completionTokens: Int,
    @ColumnInfo("cached_tokens")
    val cachedTokens: Int,
    @ColumnInfo("outcome", defaultValue = "'UNKNOWN'")
    val outcome: String = UsageOutcome.UNKNOWN.name,
)

enum class UsageOutcome {
    SUCCESS,
    FAILED,
    CANCELLED,

    /** 迁移前写入的旧行，无法判断成功还是失败。 */
    UNKNOWN,
}
