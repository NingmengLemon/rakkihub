package me.rerere.rikkahub.data.datastore.migration

import android.util.Log
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import me.rerere.rikkahub.utils.JsonInstant

private const val TAG = "SettingsJsonMigrator"

const val SETTINGS_VERSION_KEY = "settingsVersion"
const val CURRENT_SETTINGS_VERSION = 6

/**
 * 对备份文件中的 settings.json 应用与 DataStore migration 相同的迁移逻辑。
 *
 * DataStore migration 作用于分散的 key-value 存储，而备份文件中的 settings.json
 * 是整个 [me.rerere.rikkahub.data.datastore.Settings] 对象的序列化结果。
 * 此工具类负责在反序列化前对旧格式的 JSON 执行等价的迁移操作。
 */
object SettingsJsonMigrator {

    /**
     * 对 settings JSON 字符串依次应用所有版本的迁移。
     * 若发生异常则返回原始 JSON，不中断恢复流程。
     */
    fun migrate(settingsJson: String): String {
        return runCatching {
            val root = JsonInstant.parseToJsonElement(settingsJson).jsonObject.toMutableMap()

            // V1: 修复 mcpServers 中全限定类名的 type 字段
            root["mcpServers"]?.let { element ->
                val migrated = migrateMcpServersJson(JsonInstant.encodeToString(element))
                root["mcpServers"] = JsonInstant.parseToJsonElement(migrated)
            }

            // V2: 修复 assistants 中 UIMessagePart 的 type 字段
            root["assistants"]?.let { element ->
                val migrated = migrateAssistantsJson(JsonInstant.encodeToString(element))
                root["assistants"] = JsonInstant.parseToJsonElement(migrated)
            }

            // V3: 将 assistants 中内嵌的 quickMessages 提取为全局 quickMessages
            root["assistants"]?.let { element ->
                val (migratedAssistants, extractedQuickMessages) =
                    migrateAssistantsQuickMessages(JsonInstant.encodeToString(element))
                root["assistants"] = JsonInstant.parseToJsonElement(migratedAssistants)

                if (extractedQuickMessages.isNotEmpty()) {
                    val existing = root["quickMessages"]
                    val existingArray = existing?.let {
                        runCatching {
                            JsonInstant.parseToJsonElement(
                                JsonInstant.encodeToString(it)
                            ) as? JsonArray
                        }.getOrNull()
                    } ?: JsonArray(emptyList())
                    val existingIds = existingArray.mapNotNull {
                        (it as? JsonObject)?.get("id")?.toString()?.trim('"')
                    }.toSet()
                    val merged = JsonArray(
                        existingArray + extractedQuickMessages.filter { e ->
                            val id = (e as? JsonObject)?.get("id")?.toString()?.trim('"')
                            id != null && id !in existingIds
                        }
                    )
                    root["quickMessages"] = merged
                }
            }

            // V4: 移除 assistants 的 allowConversationPromptInjection 开关
            root["assistants"]?.let { element ->
                val migrated = migrateAssistantsConversationInjections(JsonInstant.encodeToString(element))
                root["assistants"] = JsonInstant.parseToJsonElement(migrated)
            }

            // V5: 提供商的余额查询 balanceOption 改成自定义额度查询 quotaQuery
            (root["providers"] as? JsonArray)?.let { providers ->
                root["providers"] = JsonArray(providers.map(::migrateProviderQuotaQuery))
            }

            // V6: 旧备份没有 schema 标记，按 legacy 处理，关闭默认开启的自动重试。
            // 新备份由 encodeSettings 写入 settingsVersion，保留用户显式开启的值。
            val version = (root[SETTINGS_VERSION_KEY] as? JsonPrimitive)?.intOrNull
            if (version == null || version < CURRENT_SETTINGS_VERSION) {
                (root["networkSetting"] as? JsonObject)?.let { network ->
                    root["networkSetting"] = JsonObject(network + ("enableAutoRetry" to JsonPrimitive(false)))
                }
            }
            root[SETTINGS_VERSION_KEY] = JsonPrimitive(CURRENT_SETTINGS_VERSION)

            JsonInstant.encodeToString(JsonObject(root))
        }.onFailure {
            Log.e(TAG, "migrate: Failed to migrate settings JSON, using original", it)
        }.getOrDefault(settingsJson)
    }
}
