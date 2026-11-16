package me.rerere.rikkahub.data.datastore.migration

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.utils.JsonInstant

class PreferenceStoreV6Migration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
        val version = currentData[SettingsStore.VERSION]
        return version == null || version < 6
    }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs = currentData.toMutablePreferences()
        prefs[SettingsStore.NETWORK_SETTING]?.let { json ->
            prefs[SettingsStore.NETWORK_SETTING] = migrateNetworkSettingAutoRetry(json)
        }
        prefs[SettingsStore.VERSION] = 6
        return prefs.toPreferences()
    }

    override suspend fun cleanUp() {}
}

/**
 * 自动重试从默认开启改为默认关闭。旧版本保存的 true 多数来自默认值而不是用户选择，
 * 升级时统一关掉；之后用户在网络设置里显式开启会写回 true，不会再被迁移覆盖。
 */
internal fun migrateNetworkSettingAutoRetry(json: String): String {
    return runCatching {
        val root = JsonInstant.parseToJsonElement(json) as? JsonObject ?: return@runCatching json
        JsonInstant.encodeToString(JsonObject(root + ("enableAutoRetry" to JsonPrimitive(false))))
    }.getOrDefault(json)
}
