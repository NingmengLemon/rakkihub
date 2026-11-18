package me.rerere.rikkahub.data.sync.official

import me.rerere.ai.core.ReasoningLevel
import me.rerere.rikkahub.data.datastore.Settings

/**
 * 官方客户端不认识 `NOTSET` 这个枚举值，未知枚举不像未知字段那样能被 `ignoreUnknownKeys` 吞掉。
 * 官方兼容出口统一归一化成 `OFF`，保持官方能解码；自家备份不动。
 */
internal fun Settings.forOfficialExport(): Settings {
    val httpMcpIds = mcpServers.mapTo(HashSet()) { it.id }
    return copy(
        fastModelReasoningLevel = fastModelReasoningLevel.forOfficialExport(),
        memoryModelReasoningLevel = memoryModelReasoningLevel.forOfficialExport(),
        // 官方客户端无法运行 stdio MCP，同时清掉 assistant 对这些 id 的引用
        stdioMcpServers = emptyList(),
        stoppedMcpServerIds = emptySet(),
        assistants = assistants.map { assistant ->
            assistant.copy(
                reasoningLevel = assistant.reasoningLevel.forOfficialExport(),
                mcpServers = assistant.mcpServers.filterTo(mutableSetOf()) { id -> id in httpMcpIds },
            )
        },
    )
}

private fun ReasoningLevel.forOfficialExport(): ReasoningLevel =
    if (this == ReasoningLevel.NOTSET) ReasoningLevel.OFF else this
