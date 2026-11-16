package me.rerere.rikkahub.data.ai.transformers

/** 单张图片的 OCR 文本来源选择：derivation 优先，旧消息 metadata 作为回退。 */
internal data class OcrTextChoice(
    val text: String?,
    val backfillFileId: Long?,
)

internal fun chooseOcrText(
    fileId: Long?,
    derivationText: String?,
    metadataText: String?,
    useMetadata: Boolean = true,
): OcrTextChoice {
    val derivation = derivationText?.takeIf { it.isNotBlank() }
    val metadata = metadataText?.takeIf { useMetadata && it.isNotBlank() }
    val text = derivation ?: metadata
    return OcrTextChoice(
        text = text,
        // metadata 命中且还没有文件级记录时，懒回填一次；指纹 producer 下不回填旧文本
        backfillFileId = fileId?.takeIf { derivation == null && metadata != null },
    )
}
