package me.rerere.rikkahub.data.sync.official

import me.rerere.ai.core.ReasoningLevel
import me.rerere.rikkahub.data.datastore.Settings

/**
 * 官方客户端不认识 `NOTSET` 这个枚举值，未知枚举不像未知字段那样能被 `ignoreUnknownKeys` 吞掉。
 * 官方兼容出口统一归一化成 `OFF`，保持官方能解码；自家备份不动。
 */
internal fun Settings.forOfficialExport(): Settings = copy(
    fastModelReasoningLevel = fastModelReasoningLevel.forOfficialExport(),
    memoryModelReasoningLevel = memoryModelReasoningLevel.forOfficialExport(),
    assistants = assistants.map { assistant ->
        assistant.copy(reasoningLevel = assistant.reasoningLevel.forOfficialExport())
    },
)

private fun ReasoningLevel.forOfficialExport(): ReasoningLevel =
    if (this == ReasoningLevel.NOTSET) ReasoningLevel.OFF else this
